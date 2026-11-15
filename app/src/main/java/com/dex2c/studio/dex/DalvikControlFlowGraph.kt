package com.dex2c.studio.dex

enum class CfgEdgeKind(val shortLabel: String) {
  FALLTHROUGH("Fallthrough"),
  CONDITIONAL_BRANCH("Branch"),
  UNCONDITIONAL_JUMP("Jump"),
  SWITCH_CASE("Case"),
  EXCEPTION_HANDLER("Catch"),
}

enum class BlockTerminatorKind(val badgeLabel: String) {
  ENTRY("ENTRY"),
  FALLTHROUGH("FALLTHROUGH"),
  CONDITIONAL_BRANCH("COND_BRANCH"),
  UNCONDITIONAL_JUMP("GOTO"),
  SWITCH("SWITCH"),
  RETURN("RETURN"),
  THROW("THROW"),
  EXCEPTION_HANDLER("CATCH"),
}

data class CfgEdge(
  val fromBlockId: String,
  val toBlockId: String,
  val toOffsetWords: Int,
  val kind: CfgEdgeKind,
  val label: String,
)

data class BasicBlockInfo(
  val id: String,
  val index: Int,
  val startOffsetWords: Int,
  val endOffsetWords: Int,
  val instructions: List<DalvikInstruction>,
  val terminatorKind: BlockTerminatorKind,
  val isEntry: Boolean,
  val isExceptionHandler: Boolean,
  val predecessors: List<String>,
  val outgoingEdges: List<CfgEdge>,
)

data class MethodControlFlowGraph(
  val blocks: List<BasicBlockInfo>,
  val payloads: List<DalvikPayload>,
  val edgeCount: Int,
  val branchCount: Int,
  val exceptionEdgeCount: Int,
  val engineLabel: String,
)

/**
 * Pure Kotlin Control Flow Graph & Basic Block engine:
 * - Segments linear-sweep Dalvik instructions into sorted basic blocks at branch/switch targets,
 *   post-branch fall-through points, and try/catch boundaries.
 * - Links `packed-switch`, `sparse-switch`, and `try/catch` exception handler edges.
 */
object ControlFlowGraphBuilder {

  fun build(
    instructions: List<DalvikInstruction>,
    tryBlocks: List<DexTryBlock> = emptyList(),
  ): MethodControlFlowGraph {
    val payloads = instructions.mapNotNull { it.payload }.distinctBy { it.offsetWords }
    val execInsns = instructions.filter { it.category != OpCategory.PAYLOAD }
    if (execInsns.isEmpty()) {
      return MethodControlFlowGraph(
        blocks = emptyList(),
        payloads = payloads,
        edgeCount = 0,
        branchCount = 0,
        exceptionEdgeCount = 0,
        engineLabel = "Kotlin CFG Engine",
      )
    }

    val execOffsets = execInsns.map { it.offsetWords }.toSet()
    val indexByOffset = HashMap<Int, Int>(execInsns.size)
    execInsns.forEachIndexed { idx, insn -> indexByOffset[insn.offsetWords] = idx }

    val leaders = sortedSetOf<Int>()
    leaders += execInsns.first().offsetWords

    execInsns.forEachIndexed { idx, insn ->
      val nextInsnOffset = execInsns.getOrNull(idx + 1)?.offsetWords
      when {
        isUnconditionalJump(insn) -> {
          insn.targetOffset?.takeIf { it in execOffsets }?.let { leaders += it }
          if (nextInsnOffset != null) leaders += nextInsnOffset
        }
        isConditionalBranch(insn) -> {
          insn.targetOffset?.takeIf { it in execOffsets }?.let { leaders += it }
          if (nextInsnOffset != null) leaders += nextInsnOffset
        }
        isSwitchInstruction(insn) -> {
          when (val p = insn.payload) {
            is DalvikPayload.PackedSwitch -> p.targetOffsets.filter { it in execOffsets }.forEach { leaders += it }
            is DalvikPayload.SparseSwitch -> p.cases.map { it.second }.filter { it in execOffsets }.forEach { leaders += it }
            else -> Unit
          }
          if (nextInsnOffset != null) leaders += nextInsnOffset
        }
        insn.category == OpCategory.RETURN || insn.category == OpCategory.THROW -> {
          if (nextInsnOffset != null) leaders += nextInsnOffset
        }
      }
    }

    val catchHandlerOffsets = mutableSetOf<Int>()
    tryBlocks.forEach { tb ->
      if (tb.startOffsetWord in execOffsets) leaders += tb.startOffsetWord
      if (tb.endOffsetWord in execOffsets) leaders += tb.endOffsetWord
      tb.handlers.forEach { handler ->
        if (handler.handlerOffsetWord in execOffsets) {
          leaders += handler.handlerOffsetWord
          catchHandlerOffsets += handler.handlerOffsetWord
        }
      }
    }

    val sortedLeaders = leaders.toList()
    val blockIdByStart = sortedLeaders.mapIndexed { idx, start -> start to "BB_$idx" }.toMap()

    // Partition executable instructions into basic blocks
    val blockSlices = mutableListOf<Pair<Int, List<DalvikInstruction>>>()
    for (i in sortedLeaders.indices) {
      val startOff = sortedLeaders[i]
      val nextLeader = sortedLeaders.getOrNull(i + 1)
      val slice =
        execInsns.filter { insn ->
          insn.offsetWords >= startOff && (nextLeader == null || insn.offsetWords < nextLeader)
        }
      if (slice.isNotEmpty()) {
        blockSlices += startOff to slice
      }
    }

    val outgoingMap = LinkedHashMap<String, MutableList<CfgEdge>>(blockSlices.size)
    val predecessorsMap = LinkedHashMap<String, MutableSet<String>>(blockSlices.size)
    blockSlices.forEachIndexed { idx, _ ->
      val id = "BB_$idx"
      outgoingMap[id] = mutableListOf()
      predecessorsMap[id] = linkedSetOf()
    }

    val terminators = Array(blockSlices.size) { BlockTerminatorKind.FALLTHROUGH }
    var branchCount = 0
    var exceptionEdgeCount = 0

    for (i in blockSlices.indices) {
      val blockId = "BB_$i"
      val (_, insns) = blockSlices[i]
      val last = insns.last()
      val nextBlockStart = blockSlices.getOrNull(i + 1)?.first
      val nextBlockId = nextBlockStart?.let { "BB_${i + 1}" }
      val edges = outgoingMap[blockId]!!

      fun addEdge(toOffset: Int, kind: CfgEdgeKind, label: String) {
        val targetBlockId = blockIdByStart[toOffset] ?: return
        val candidate = CfgEdge(blockId, targetBlockId, toOffset, kind, label)
        if (candidate !in edges) {
          edges += candidate
          predecessorsMap[targetBlockId]?.add(blockId)
          if (kind == CfgEdgeKind.EXCEPTION_HANDLER) exceptionEdgeCount++
        }
      }

      when {
        isUnconditionalJump(last) -> {
          terminators[i] = BlockTerminatorKind.UNCONDITIONAL_JUMP
          branchCount++
          last.targetOffset?.let { target ->
            addEdge(target, CfgEdgeKind.UNCONDITIONAL_JUMP, "Goto")
          }
        }
        isConditionalBranch(last) -> {
          terminators[i] = BlockTerminatorKind.CONDITIONAL_BRANCH
          branchCount++
          last.targetOffset?.let { target ->
            addEdge(target, CfgEdgeKind.CONDITIONAL_BRANCH, "True")
          }
          if (nextBlockStart != null && nextBlockId != null) {
            addEdge(nextBlockStart, CfgEdgeKind.FALLTHROUGH, "Fallthrough")
          }
        }
        isSwitchInstruction(last) -> {
          terminators[i] = BlockTerminatorKind.SWITCH
          branchCount++
          when (val p = last.payload) {
            is DalvikPayload.PackedSwitch -> {
              p.targetOffsets.forEachIndexed { caseIdx, targetOff ->
                addEdge(targetOff, CfgEdgeKind.SWITCH_CASE, "case ${p.firstKey + caseIdx}")
              }
            }
            is DalvikPayload.SparseSwitch -> {
              p.cases.forEach { (key, targetOff) ->
                addEdge(targetOff, CfgEdgeKind.SWITCH_CASE, "case $key")
              }
            }
            else -> Unit
          }
          if (nextBlockStart != null) {
            addEdge(nextBlockStart, CfgEdgeKind.FALLTHROUGH, "Default")
          }
        }
        last.category == OpCategory.RETURN -> {
          terminators[i] = BlockTerminatorKind.RETURN
        }
        last.category == OpCategory.THROW -> {
          terminators[i] = BlockTerminatorKind.THROW
        }
        else -> {
          terminators[i] = BlockTerminatorKind.FALLTHROUGH
          if (nextBlockStart != null) {
            addEdge(nextBlockStart, CfgEdgeKind.FALLTHROUGH, "Fallthrough")
          }
        }
      }

      // Attach try/catch exception handler edges overlapping any instruction in this block
      val blockStart = insns.first().offsetWords
      val blockLastOff = insns.last().offsetWords
      tryBlocks.forEach { tb ->
        val overlaps = blockLastOff >= tb.startOffsetWord && blockStart < tb.endOffsetWord
        if (overlaps) {
          tb.handlers.forEach { handler ->
            val catchType = handler.typeDescriptor?.let(::descriptorToSimpleName) ?: "<all>"
            addEdge(handler.handlerOffsetWord, CfgEdgeKind.EXCEPTION_HANDLER, "Catch $catchType")
          }
        }
      }
    }

    val blocks =
      blockSlices.mapIndexed { idx, (startOff, insns) ->
        val id = "BB_$idx"
        val lastInsn = insns.last()
        val endWords = lastInsn.offsetWords + lastInsn.widthWords
        BasicBlockInfo(
          id = id,
          index = idx,
          startOffsetWords = startOff,
          endOffsetWords = endWords,
          instructions = insns,
          terminatorKind = terminators[idx],
          isEntry = idx == 0,
          isExceptionHandler = startOff in catchHandlerOffsets,
          predecessors = predecessorsMap[id]?.toList().orEmpty(),
          outgoingEdges = outgoingMap[id]?.toList().orEmpty(),
        )
      }

    val totalEdges = blocks.sumOf { it.outgoingEdges.size }
    return MethodControlFlowGraph(
      blocks = blocks,
      payloads = payloads,
      edgeCount = totalEdges,
      branchCount = branchCount,
      exceptionEdgeCount = exceptionEdgeCount,
      engineLabel = "Kotlin CFG Engine",
    )
  }

  private fun isUnconditionalJump(insn: DalvikInstruction): Boolean =
    insn.opcode == 0x28 || insn.opcode == 0x29 || insn.opcode == 0x2A

  private fun isConditionalBranch(insn: DalvikInstruction): Boolean =
    insn.opcode in 0x32..0x3D

  private fun isSwitchInstruction(insn: DalvikInstruction): Boolean =
    insn.opcode == 0x2B || insn.opcode == 0x2C
}
