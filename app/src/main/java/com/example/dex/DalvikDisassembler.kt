package com.example.dex

import java.nio.ByteBuffer
import java.nio.ByteOrder

object DalvikDisassembler {

  private enum class RefKind {
    NONE,
    STRING,
    TYPE,
    FIELD,
    METHOD,
  }

  private enum class Fmt {
    F10X,
    F12X,
    F11N,
    F11X,
    F10T,
    F20T,
    F22X,
    F21T,
    F21S,
    F21H,
    F21C,
    F23X,
    F22B,
    F22T,
    F22S,
    F22C,
    F30T,
    F32X,
    F31I,
    F31T,
    F31C,
    F35C,
    F3RC,
    F51L,
    F45CC,
    F4RCC,
    UNKNOWN,
  }

  private data class OpSpec(
    val mnemonic: String,
    val fmt: Fmt,
    val cat: OpCategory,
    val ref: RefKind = RefKind.NONE,
  )

  private val opcodeTable: Array<OpSpec?> =
    arrayOfNulls<OpSpec>(256).apply {
      fun reg(op: Int, m: String, f: Fmt, c: OpCategory, r: RefKind = RefKind.NONE) {
        this[op] = OpSpec(m, f, c, r)
      }

      reg(0x00, "nop", Fmt.F10X, OpCategory.NOP)
      reg(0x01, "move", Fmt.F12X, OpCategory.MOVE)
      reg(0x02, "move/from16", Fmt.F22X, OpCategory.MOVE)
      reg(0x03, "move/16", Fmt.F32X, OpCategory.MOVE)
      reg(0x04, "move-wide", Fmt.F12X, OpCategory.MOVE)
      reg(0x05, "move-wide/from16", Fmt.F22X, OpCategory.MOVE)
      reg(0x06, "move-wide/16", Fmt.F32X, OpCategory.MOVE)
      reg(0x07, "move-object", Fmt.F12X, OpCategory.MOVE)
      reg(0x08, "move-object/from16", Fmt.F22X, OpCategory.MOVE)
      reg(0x09, "move-object/16", Fmt.F32X, OpCategory.MOVE)
      reg(0x0A, "move-result", Fmt.F11X, OpCategory.MOVE)
      reg(0x0B, "move-result-wide", Fmt.F11X, OpCategory.MOVE)
      reg(0x0C, "move-result-object", Fmt.F11X, OpCategory.MOVE)
      reg(0x0D, "move-exception", Fmt.F11X, OpCategory.MOVE)

      reg(0x0E, "return-void", Fmt.F10X, OpCategory.RETURN)
      reg(0x0F, "return", Fmt.F11X, OpCategory.RETURN)
      reg(0x10, "return-wide", Fmt.F11X, OpCategory.RETURN)
      reg(0x11, "return-object", Fmt.F11X, OpCategory.RETURN)

      reg(0x12, "const/4", Fmt.F11N, OpCategory.CONST)
      reg(0x13, "const/16", Fmt.F21S, OpCategory.CONST)
      reg(0x14, "const", Fmt.F31I, OpCategory.CONST)
      reg(0x15, "const/high16", Fmt.F21H, OpCategory.CONST)
      reg(0x16, "const-wide/16", Fmt.F21S, OpCategory.CONST)
      reg(0x17, "const-wide/32", Fmt.F31I, OpCategory.CONST)
      reg(0x18, "const-wide", Fmt.F51L, OpCategory.CONST)
      reg(0x19, "const-wide/high16", Fmt.F21H, OpCategory.CONST)
      reg(0x1A, "const-string", Fmt.F21C, OpCategory.CONST, RefKind.STRING)
      reg(0x1B, "const-string/jumbo", Fmt.F31C, OpCategory.CONST, RefKind.STRING)
      reg(0x1C, "const-class", Fmt.F21C, OpCategory.CONST, RefKind.TYPE)

      reg(0x1D, "monitor-enter", Fmt.F11X, OpCategory.MONITOR)
      reg(0x1E, "monitor-exit", Fmt.F11X, OpCategory.MONITOR)
      reg(0x1F, "check-cast", Fmt.F21C, OpCategory.CHECK, RefKind.TYPE)
      reg(0x20, "instance-of", Fmt.F22C, OpCategory.CHECK, RefKind.TYPE)
      reg(0x21, "array-length", Fmt.F12X, OpCategory.ARRAY)
      reg(0x22, "new-instance", Fmt.F21C, OpCategory.NEW, RefKind.TYPE)
      reg(0x23, "new-array", Fmt.F22C, OpCategory.NEW, RefKind.TYPE)
      reg(0x24, "filled-new-array", Fmt.F35C, OpCategory.NEW, RefKind.TYPE)
      reg(0x25, "filled-new-array/range", Fmt.F3RC, OpCategory.NEW, RefKind.TYPE)
      reg(0x26, "fill-array-data", Fmt.F31T, OpCategory.ARRAY)
      reg(0x27, "throw", Fmt.F11X, OpCategory.THROW)

      reg(0x28, "goto", Fmt.F10T, OpCategory.BRANCH)
      reg(0x29, "goto/16", Fmt.F20T, OpCategory.BRANCH)
      reg(0x2A, "goto/32", Fmt.F30T, OpCategory.BRANCH)
      reg(0x2B, "packed-switch", Fmt.F31T, OpCategory.BRANCH)
      reg(0x2C, "sparse-switch", Fmt.F31T, OpCategory.BRANCH)

      reg(0x2D, "cmpl-float", Fmt.F23X, OpCategory.COMPARE)
      reg(0x2E, "cmpg-float", Fmt.F23X, OpCategory.COMPARE)
      reg(0x2F, "cmpl-double", Fmt.F23X, OpCategory.COMPARE)
      reg(0x30, "cmpg-double", Fmt.F23X, OpCategory.COMPARE)
      reg(0x31, "cmp-long", Fmt.F23X, OpCategory.COMPARE)

      reg(0x32, "if-eq", Fmt.F22T, OpCategory.BRANCH)
      reg(0x33, "if-ne", Fmt.F22T, OpCategory.BRANCH)
      reg(0x34, "if-lt", Fmt.F22T, OpCategory.BRANCH)
      reg(0x35, "if-ge", Fmt.F22T, OpCategory.BRANCH)
      reg(0x36, "if-gt", Fmt.F22T, OpCategory.BRANCH)
      reg(0x37, "if-le", Fmt.F22T, OpCategory.BRANCH)

      reg(0x38, "if-eqz", Fmt.F21T, OpCategory.BRANCH)
      reg(0x39, "if-nez", Fmt.F21T, OpCategory.BRANCH)
      reg(0x3A, "if-ltz", Fmt.F21T, OpCategory.BRANCH)
      reg(0x3B, "if-gez", Fmt.F21T, OpCategory.BRANCH)
      reg(0x3C, "if-gtz", Fmt.F21T, OpCategory.BRANCH)
      reg(0x3D, "if-lez", Fmt.F21T, OpCategory.BRANCH)

      val agetNames = listOf("aget", "aget-wide", "aget-object", "aget-boolean", "aget-byte", "aget-char", "aget-short")
      agetNames.forEachIndexed { idx, name -> reg(0x44 + idx, name, Fmt.F23X, OpCategory.ARRAY) }
      val aputNames = listOf("aput", "aput-wide", "aput-object", "aput-boolean", "aput-byte", "aput-char", "aput-short")
      aputNames.forEachIndexed { idx, name -> reg(0x4B + idx, name, Fmt.F23X, OpCategory.ARRAY) }

      val igetNames = listOf("iget", "iget-wide", "iget-object", "iget-boolean", "iget-byte", "iget-char", "iget-short")
      igetNames.forEachIndexed { idx, name -> reg(0x52 + idx, name, Fmt.F22C, OpCategory.FIELD, RefKind.FIELD) }
      val iputNames = listOf("iput", "iput-wide", "iput-object", "iput-boolean", "iput-byte", "iput-char", "iput-short")
      iputNames.forEachIndexed { idx, name -> reg(0x59 + idx, name, Fmt.F22C, OpCategory.FIELD, RefKind.FIELD) }
      val sgetNames = listOf("sget", "sget-wide", "sget-object", "sget-boolean", "sget-byte", "sget-char", "sget-short")
      sgetNames.forEachIndexed { idx, name -> reg(0x60 + idx, name, Fmt.F21C, OpCategory.FIELD, RefKind.FIELD) }
      val sputNames = listOf("sput", "sput-wide", "sput-object", "sput-boolean", "sput-byte", "sput-char", "sput-short")
      sputNames.forEachIndexed { idx, name -> reg(0x67 + idx, name, Fmt.F21C, OpCategory.FIELD, RefKind.FIELD) }

      reg(0x6E, "invoke-virtual", Fmt.F35C, OpCategory.INVOKE, RefKind.METHOD)
      reg(0x6F, "invoke-super", Fmt.F35C, OpCategory.INVOKE, RefKind.METHOD)
      reg(0x70, "invoke-direct", Fmt.F35C, OpCategory.INVOKE, RefKind.METHOD)
      reg(0x71, "invoke-static", Fmt.F35C, OpCategory.INVOKE, RefKind.METHOD)
      reg(0x72, "invoke-interface", Fmt.F35C, OpCategory.INVOKE, RefKind.METHOD)

      reg(0x74, "invoke-virtual/range", Fmt.F3RC, OpCategory.INVOKE, RefKind.METHOD)
      reg(0x75, "invoke-super/range", Fmt.F3RC, OpCategory.INVOKE, RefKind.METHOD)
      reg(0x76, "invoke-direct/range", Fmt.F3RC, OpCategory.INVOKE, RefKind.METHOD)
      reg(0x77, "invoke-static/range", Fmt.F3RC, OpCategory.INVOKE, RefKind.METHOD)
      reg(0x78, "invoke-interface/range", Fmt.F3RC, OpCategory.INVOKE, RefKind.METHOD)

      val unaryOps =
        listOf(
          0x7B to ("neg-int" to OpCategory.MATH),
          0x7C to ("not-int" to OpCategory.MATH),
          0x7D to ("neg-long" to OpCategory.MATH),
          0x7E to ("not-long" to OpCategory.MATH),
          0x7F to ("neg-float" to OpCategory.MATH),
          0x80 to ("neg-double" to OpCategory.MATH),
          0x81 to ("int-to-long" to OpCategory.CONVERT),
          0x82 to ("int-to-float" to OpCategory.CONVERT),
          0x83 to ("int-to-double" to OpCategory.CONVERT),
          0x84 to ("long-to-int" to OpCategory.CONVERT),
          0x85 to ("long-to-float" to OpCategory.CONVERT),
          0x86 to ("long-to-double" to OpCategory.CONVERT),
          0x87 to ("float-to-int" to OpCategory.CONVERT),
          0x88 to ("float-to-long" to OpCategory.CONVERT),
          0x89 to ("float-to-double" to OpCategory.CONVERT),
          0x8A to ("double-to-int" to OpCategory.CONVERT),
          0x8B to ("double-to-long" to OpCategory.CONVERT),
          0x8C to ("double-to-float" to OpCategory.CONVERT),
          0x8D to ("int-to-byte" to OpCategory.CONVERT),
          0x8E to ("int-to-char" to OpCategory.CONVERT),
          0x8F to ("int-to-short" to OpCategory.CONVERT),
        )
      unaryOps.forEach { (op, pair) -> reg(op, pair.first, Fmt.F12X, pair.second) }

      val bin3xNames =
        listOf(
          "add-int", "sub-int", "mul-int", "div-int", "rem-int", "and-int", "or-int", "xor-int", "shl-int", "shr-int", "ushr-int",
          "add-long", "sub-long", "mul-long", "div-long", "rem-long", "and-long", "or-long", "xor-long", "shl-long", "shr-long", "ushr-long",
          "add-float", "sub-float", "mul-float", "div-float", "rem-float",
          "add-double", "sub-double", "mul-double", "div-double", "rem-double",
        )
      bin3xNames.forEachIndexed { idx, name -> reg(0x90 + idx, name, Fmt.F23X, OpCategory.MATH) }
      bin3xNames.forEachIndexed { idx, name -> reg(0xB0 + idx, "$name/2addr", Fmt.F12X, OpCategory.MATH) }

      val litOps = listOf("add-int", "rsub-int", "mul-int", "div-int", "rem-int", "and-int", "or-int", "xor-int")
      litOps.forEachIndexed { idx, name -> reg(0xD0 + idx, "$name/lit16", Fmt.F22S, OpCategory.MATH) }

      val lit8Ops =
        listOf("add-int", "rsub-int", "mul-int", "div-int", "rem-int", "and-int", "or-int", "xor-int", "shl-int", "shr-int", "ushr-int")
      lit8Ops.forEachIndexed { idx, name -> reg(0xD8 + idx, "$name/lit8", Fmt.F22B, OpCategory.MATH) }

      reg(0xFA, "invoke-polymorphic", Fmt.F45CC, OpCategory.INVOKE, RefKind.METHOD)
      reg(0xFB, "invoke-polymorphic/range", Fmt.F4RCC, OpCategory.INVOKE, RefKind.METHOD)
      reg(0xFC, "invoke-custom", Fmt.F35C, OpCategory.INVOKE)
      reg(0xFD, "invoke-custom/range", Fmt.F3RC, OpCategory.INVOKE)
      reg(0xFE, "const-method-handle", Fmt.F21C, OpCategory.CONST)
      reg(0xFF, "const-method-type", Fmt.F21C, OpCategory.CONST)
    }

  fun disassemble(dex: DexParsedFile, method: DexEncodedMethod): List<DalvikInstruction> {
    val meta = method.codeMeta ?: return emptyList()
    if (meta.insnsCount <= 0) return emptyList()

    val buf = ByteBuffer.wrap(dex.rawBytes).order(ByteOrder.LITTLE_ENDIAN)
    val words = IntArray(meta.insnsCount)
    for (i in 0 until meta.insnsCount) {
      val pos = meta.insnsOffsetBytes + i * 2
      if (pos + 1 >= dex.rawBytes.size) break
      words[i] = buf.getShort(pos).toInt() and 0xFFFF
    }

    val out = mutableListOf<DalvikInstruction>()
    var pc = 0
    while (pc < words.size) {
      val w0 = words[pc]
      val op = w0 and 0xFF

      // Check special pseudo-instructions / switch & array payloads
      if (w0 == 0x0100 && pc + 1 < words.size) {
        val size = words[pc + 1]
        val width = (size * 2) + 4
        val slice = words.slice(pc until minOf(pc + width, words.size))
        out +=
          DalvikInstruction(
            offsetWords = pc,
            widthWords = slice.size,
            opcode = w0,
            mnemonic = "packed-switch-payload",
            format = "payload",
            operandsText = "$size targets",
            rawWords = slice.take(4),
            category = OpCategory.PAYLOAD,
          )
        pc += maxOf(1, width)
        continue
      }
      if (w0 == 0x0200 && pc + 1 < words.size) {
        val size = words[pc + 1]
        val width = (size * 4) + 2
        val slice = words.slice(pc until minOf(pc + width, words.size))
        out +=
          DalvikInstruction(
            offsetWords = pc,
            widthWords = slice.size,
            opcode = w0,
            mnemonic = "sparse-switch-payload",
            format = "payload",
            operandsText = "$size cases",
            rawWords = slice.take(4),
            category = OpCategory.PAYLOAD,
          )
        pc += maxOf(1, width)
        continue
      }
      if (w0 == 0x0300 && pc + 3 < words.size) {
        val elemWidth = words[pc + 1]
        val size = words[pc + 2] or (words[pc + 3] shl 16)
        val dataWords = ((size.toLong() * elemWidth.toLong()) + 1L) / 2L
        val width = (4 + dataWords).toInt().coerceAtLeast(4)
        val slice = words.slice(pc until minOf(pc + width, words.size))
        out +=
          DalvikInstruction(
            offsetWords = pc,
            widthWords = slice.size,
            opcode = w0,
            mnemonic = "fill-array-data-payload",
            format = "payload",
            operandsText = "${elemWidth}B x $size",
            rawWords = slice.take(4),
            category = OpCategory.PAYLOAD,
          )
        pc += maxOf(1, width)
        continue
      }

      val spec = opcodeTable[op]
      if (spec == null) {
        out +=
          DalvikInstruction(
            offsetWords = pc,
            widthWords = 1,
            opcode = op,
            mnemonic = "op_0x%02x".format(op),
            format = "10x",
            operandsText = "",
            rawWords = listOf(w0),
            category = OpCategory.UNKNOWN,
          )
        pc += 1
        continue
      }

      val width =
        when (spec.fmt) {
          Fmt.F10X, Fmt.F12X, Fmt.F11N, Fmt.F11X, Fmt.F10T -> 1
          Fmt.F20T, Fmt.F22X, Fmt.F21T, Fmt.F21S, Fmt.F21H, Fmt.F21C, Fmt.F23X, Fmt.F22B, Fmt.F22T, Fmt.F22S, Fmt.F22C -> 2
          Fmt.F30T, Fmt.F32X, Fmt.F31I, Fmt.F31T, Fmt.F31C, Fmt.F35C, Fmt.F3RC -> 3
          Fmt.F45CC, Fmt.F4RCC -> 4
          Fmt.F51L -> 5
          Fmt.UNKNOWN -> 1
        }
      if (pc + width > words.size) break

      val raw = words.slice(pc until pc + width)
      val w1 = if (width >= 2) words[pc + 1] else 0
      val w2 = if (width >= 3) words[pc + 2] else 0
      val w3 = if (width >= 4) words[pc + 3] else 0
      val w4 = if (width >= 5) words[pc + 4] else 0

      var regA: Int? = null
      var regB: Int? = null
      var regC: Int? = null
      val regList = mutableListOf<Int>()
      var isRange = false
      var literal: Long? = null
      var targetOffset: Int? = null
      var strRef: String? = null
      var typeRef: String? = null
      var fieldRef: DexFieldRef? = null
      var methodRef: DexMethodRef? = null

      fun resolveRef(index: Int): String {
        return when (spec.ref) {
          RefKind.STRING -> {
            strRef = dex.strings.getOrNull(index)
            val escaped =
              (strRef ?: "<str#$index>")
                .replace("\\", "\\\\")
                .replace("\n", "\\n")
                .replace("\"", "\\\"")
                .let { if (it.length > 48) it.take(45) + "..." else it }
            "\"$escaped\""
          }
          RefKind.TYPE -> {
            typeRef = dex.types.getOrNull(index)
            typeRef ?: "<type#$index>"
          }
          RefKind.FIELD -> {
            fieldRef = dex.fields.getOrNull(index)
            val f = fieldRef
            if (f != null) "${descriptorToSimpleName(f.definingClass)}.${f.name}:${descriptorToSimpleName(f.type)}"
            else "<field#$index>"
          }
          RefKind.METHOD -> {
            methodRef = dex.methods.getOrNull(index)
            val m = methodRef
            if (m != null) "${descriptorToSimpleName(m.definingClass)}.${m.prettySignature()}"
            else "<method#$index>"
          }
          RefKind.NONE -> "#$index"
        }
      }

      val operands =
        when (spec.fmt) {
          Fmt.F10X -> ""
          Fmt.F12X -> {
            regA = (w0 ushr 8) and 0xF
            regB = (w0 ushr 12) and 0xF
            "v$regA, v$regB"
          }
          Fmt.F11N -> {
            regA = (w0 ushr 8) and 0xF
            val nibble = signExtend4((w0 ushr 12) and 0xF)
            literal = nibble.toLong()
            "v$regA, #$nibble"
          }
          Fmt.F11X -> {
            regA = (w0 ushr 8) and 0xFF
            "v$regA"
          }
          Fmt.F10T -> {
            val delta = ((w0 ushr 8) and 0xFF).toByte().toInt()
            targetOffset = pc + delta
            "L_%04x".format(targetOffset)
          }
          Fmt.F20T -> {
            val delta = w1.toShort().toInt()
            targetOffset = pc + delta
            "L_%04x".format(targetOffset)
          }
          Fmt.F22X -> {
            regA = (w0 ushr 8) and 0xFF
            regB = w1
            "v$regA, v$regB"
          }
          Fmt.F21T -> {
            regA = (w0 ushr 8) and 0xFF
            val delta = w1.toShort().toInt()
            targetOffset = pc + delta
            "v$regA, L_%04x".format(targetOffset)
          }
          Fmt.F21S -> {
            regA = (w0 ushr 8) and 0xFF
            val imm = w1.toShort().toLong()
            literal = imm
            "v$regA, #$imm"
          }
          Fmt.F21H -> {
            regA = (w0 ushr 8) and 0xFF
            val imm =
              if (op == 0x19) w1.toLong() shl 48 else (w1.toLong() shl 16)
            literal = imm
            "v$regA, #0x${java.lang.Long.toHexString(imm)}"
          }
          Fmt.F21C -> {
            regA = (w0 ushr 8) and 0xFF
            "v$regA, ${resolveRef(w1)}"
          }
          Fmt.F23X -> {
            regA = (w0 ushr 8) and 0xFF
            regB = w1 and 0xFF
            regC = (w1 ushr 8) and 0xFF
            "v$regA, v$regB, v$regC"
          }
          Fmt.F22B -> {
            regA = (w0 ushr 8) and 0xFF
            regB = w1 and 0xFF
            val imm = ((w1 ushr 8) and 0xFF).toByte().toLong()
            literal = imm
            "v$regA, v$regB, #$imm"
          }
          Fmt.F22T -> {
            regA = (w0 ushr 8) and 0xF
            regB = (w0 ushr 12) and 0xF
            val delta = w1.toShort().toInt()
            targetOffset = pc + delta
            "v$regA, v$regB, L_%04x".format(targetOffset)
          }
          Fmt.F22S -> {
            regA = (w0 ushr 8) and 0xF
            regB = (w0 ushr 12) and 0xF
            val imm = w1.toShort().toLong()
            literal = imm
            "v$regA, v$regB, #$imm"
          }
          Fmt.F22C -> {
            regA = (w0 ushr 8) and 0xF
            regB = (w0 ushr 12) and 0xF
            "v$regA, v$regB, ${resolveRef(w1)}"
          }
          Fmt.F30T -> {
            val delta = w1 or (w2 shl 16)
            targetOffset = pc + delta
            "L_%04x".format(targetOffset)
          }
          Fmt.F32X -> {
            regA = w1
            regB = w2
            "v$regA, v$regB"
          }
          Fmt.F31I -> {
            regA = (w0 ushr 8) and 0xFF
            val imm = (w1 or (w2 shl 16)).toLong()
            literal = imm
            "v$regA, #$imm"
          }
          Fmt.F31T -> {
            regA = (w0 ushr 8) and 0xFF
            val delta = w1 or (w2 shl 16)
            targetOffset = pc + delta
            "v$regA, L_%04x".format(targetOffset)
          }
          Fmt.F31C -> {
            regA = (w0 ushr 8) and 0xFF
            val idx = w1 or (w2 shl 16)
            "v$regA, ${resolveRef(idx)}"
          }
          Fmt.F35C, Fmt.F45CC -> {
            val count = (w0 ushr 12) and 0xF
            val g = (w0 ushr 8) and 0xF
            val c = w2 and 0xF
            val d = (w2 ushr 4) and 0xF
            val e = (w2 ushr 8) and 0xF
            val f = (w2 ushr 12) and 0xF
            val candidates = listOf(c, d, e, f, g)
            for (k in 0 until minOf(count, 5)) regList += candidates[k]
            val regsStr = regList.joinToString(", ") { "v$it" }
            "{$regsStr}, ${resolveRef(w1)}"
          }
          Fmt.F3RC, Fmt.F4RCC -> {
            isRange = true
            val count = (w0 ushr 8) and 0xFF
            val startReg = w2
            for (k in 0 until count) regList += (startReg + k)
            val endReg = if (count > 0) startReg + count - 1 else startReg
            "{v$startReg .. v$endReg}, ${resolveRef(w1)}"
          }
          Fmt.F51L -> {
            regA = (w0 ushr 8) and 0xFF
            val imm =
              (w1.toLong() and 0xFFFFL) or
                ((w2.toLong() and 0xFFFFL) shl 16) or
                ((w3.toLong() and 0xFFFFL) shl 32) or
                ((w4.toLong() and 0xFFFFL) shl 48)
            literal = imm
            "v$regA, #$imm"
          }
          Fmt.UNKNOWN -> ""
        }

      out +=
        DalvikInstruction(
          offsetWords = pc,
          widthWords = width,
          opcode = op,
          mnemonic = spec.mnemonic,
          format = spec.fmt.name.removePrefix("F").lowercase(),
          operandsText = operands,
          rawWords = raw,
          category = spec.cat,
          regA = regA,
          regB = regB,
          regC = regC,
          regList = regList,
          isRangeInvoke = isRange,
          literal = literal,
          targetOffset = targetOffset,
          stringRef = strRef,
          typeRef = typeRef,
          fieldRef = fieldRef,
          methodRef = methodRef,
        )
      pc += width
    }

    val payloadReferrerOffset = mutableMapOf<Int, Int>()
    out.forEach { insn ->
      if (insn.opcode == 0x26 || insn.opcode == 0x2B || insn.opcode == 0x2C) {
        insn.targetOffset?.let { pOff ->
          payloadReferrerOffset.putIfAbsent(pOff, insn.offsetWords)
        }
      }
    }

    val decodedPayloadByOffset = mutableMapOf<Int, DalvikPayload>()
    val decodedOperandsByOffset = mutableMapOf<Int, String>()
    out.forEach { insn ->
      if (insn.category != OpCategory.PAYLOAD) return@forEach
      val pOff = insn.offsetWords
      val basePc = payloadReferrerOffset[pOff] ?: pOff
      when (insn.opcode) {
        0x0100 -> {
          val size = words.getOrElse(pOff + 1) { 0 }
          val firstKey = readWordPairInt(words, pOff + 2) ?: 0
          val targets =
            (0 until size).mapNotNull { idx ->
              val rel = readWordPairInt(words, pOff + 4 + idx * 2) ?: return@mapNotNull null
              basePc + rel
            }
          val payload =
            DalvikPayload.PackedSwitch(
              offsetWords = pOff,
              wordCount = insn.widthWords,
              firstKey = firstKey,
              targetOffsets = targets,
            )
          decodedPayloadByOffset[pOff] = payload
          val preview =
            targets.take(4).mapIndexed { idx, tgt -> "${firstKey + idx}->L_%04x".format(tgt) }
              .joinToString(", ")
          val ellipsis = if (targets.size > 4) ", …" else ""
          decodedOperandsByOffset[pOff] =
            if (targets.isEmpty()) "0 targets"
            else "first=$firstKey [${preview}${ellipsis}]"
        }
        0x0200 -> {
          val size = words.getOrElse(pOff + 1) { 0 }
          val cases =
            (0 until size).mapNotNull { idx ->
              val key = readWordPairInt(words, pOff + 2 + idx * 2) ?: return@mapNotNull null
              val rel = readWordPairInt(words, pOff + 2 + size * 2 + idx * 2) ?: return@mapNotNull null
              key to (basePc + rel)
            }
          val payload =
            DalvikPayload.SparseSwitch(
              offsetWords = pOff,
              wordCount = insn.widthWords,
              cases = cases,
            )
          decodedPayloadByOffset[pOff] = payload
          val preview =
            cases.take(4).joinToString(", ") { (k, tgt) -> "$k->L_%04x".format(tgt) }
          val ellipsis = if (cases.size > 4) ", …" else ""
          decodedOperandsByOffset[pOff] =
            if (cases.isEmpty()) "0 cases"
            else "${cases.size} cases [$preview$ellipsis]"
        }
        0x0300 -> {
          val elemWidth = words.getOrElse(pOff + 1) { 1 }.coerceAtLeast(1)
          val count = (readWordPairInt(words, pOff + 2) ?: 0).coerceAtLeast(0)
          val dataStartByte = meta.insnsOffsetBytes + (pOff + 4) * 2
          val totalBytes = (count.toLong() * elemWidth.toLong()).coerceAtMost(4096L).toInt()
          val availableBytes = (dex.rawBytes.size - dataStartByte).coerceAtLeast(0)
          val safeByteCount = minOf(totalBytes, availableBytes)
          val previewElements = mutableListOf<String>()
          val maxElementsToDecode = minOf(count, 12)
          for (i in 0 until maxElementsToDecode) {
            val elemOff = dataStartByte + i * elemWidth
            if (elemOff + elemWidth > dex.rawBytes.size) break
            val formatted =
              when (elemWidth) {
                1 -> dex.rawBytes[elemOff].toInt().toString()
                2 -> buf.getShort(elemOff).toInt().toString()
                4 -> buf.getInt(elemOff).toString()
                8 -> buf.getLong(elemOff).toString()
                else -> "0x%02X".format(dex.rawBytes[elemOff].toInt() and 0xFF)
              }
            previewElements += formatted
          }
          val hexCount = minOf(safeByteCount, 16)
          val hexPreview =
            (0 until hexCount).joinToString(" ") { idx ->
              "%02X".format(dex.rawBytes[dataStartByte + idx].toInt() and 0xFF)
            } + if (safeByteCount > 16) " …" else ""
          val payload =
            DalvikPayload.FillArrayData(
              offsetWords = pOff,
              wordCount = insn.widthWords,
              elementWidthBytes = elemWidth,
              elementCount = count,
              previewElements = previewElements,
              hexPreview = hexPreview,
            )
          decodedPayloadByOffset[pOff] = payload
          val vals = previewElements.take(6).joinToString(", ")
          val ellipsis = if (count > 6) ", …" else ""
          decodedOperandsByOffset[pOff] =
            if (previewElements.isEmpty()) "${elemWidth}B × $count"
            else "${elemWidth}B × $count [$vals$ellipsis]"
        }
      }
    }

    return out.map { insn ->
      when {
        insn.category == OpCategory.PAYLOAD -> {
          val decoded = decodedPayloadByOffset[insn.offsetWords]
          val opText = decodedOperandsByOffset[insn.offsetWords] ?: insn.operandsText
          insn.copy(payload = decoded, operandsText = opText)
        }
        insn.opcode == 0x26 || insn.opcode == 0x2B || insn.opcode == 0x2C -> {
          val linked = insn.targetOffset?.let { decodedPayloadByOffset[it] }
          insn.copy(payload = linked)
        }
        else -> insn
      }
    }
  }

  private fun readWordPairInt(words: IntArray, index: Int): Int? {
    if (index < 0 || index + 1 >= words.size) return null
    return (words[index] and 0xFFFF) or ((words[index + 1] and 0xFFFF) shl 16)
  }

  fun parseTryBlocks(dex: DexParsedFile, method: DexEncodedMethod): List<DexTryBlock> {
    val meta = method.codeMeta ?: return emptyList()
    if (meta.triesSize <= 0 || meta.insnsCount <= 0) return emptyList()

    val bytes = dex.rawBytes
    val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    val padding = if ((meta.insnsCount and 1) != 0) 2 else 0
    val triesOff = meta.insnsOffsetBytes + meta.insnsCount * 2 + padding
    val handlersBaseOff = triesOff + meta.triesSize * 8
    if (handlersBaseOff >= bytes.size) return emptyList()

    val blocks = mutableListOf<DexTryBlock>()
    for (i in 0 until meta.triesSize) {
      val entryOff = triesOff + i * 8
      if (entryOff + 8 > bytes.size) break
      val startAddr = buf.getInt(entryOff)
      val insnCount = buf.getShort(entryOff + 4).toInt() and 0xFFFF
      val handlerOff = buf.getShort(entryOff + 6).toInt() and 0xFFFF
      val absHandlerOff = handlersBaseOff + handlerOff
      if (absHandlerOff >= bytes.size) continue

      val reader = LebReader(bytes, absHandlerOff)
      val size = reader.readSleb128()
      val count = kotlin.math.abs(size)
      val handlers = mutableListOf<DexCatchHandler>()
      repeat(count) {
        val typeIdx = reader.readUleb128()
        val addr = reader.readUleb128()
        handlers += DexCatchHandler(dex.types.getOrNull(typeIdx), addr)
      }
      if (size <= 0) {
        val catchAllAddr = reader.readUleb128()
        handlers += DexCatchHandler(null, catchAllAddr)
      }
      blocks += DexTryBlock(startAddr, insnCount, handlers)
    }
    return blocks
  }

  private fun signExtend4(nibble: Int): Int = if ((nibble and 0x8) != 0) nibble or -16 else nibble

  private class LebReader(private val bytes: ByteArray, private var pos: Int) {
    fun readUleb128(): Int {
      var result = 0
      var shift = 0
      while (pos < bytes.size) {
        val b = bytes[pos++].toInt() and 0xFF
        result = result or ((b and 0x7F) shl shift)
        if ((b and 0x80) == 0) break
        shift += 7
      }
      return result
    }

    fun readSleb128(): Int {
      var result = 0
      var shift = 0
      var b: Int
      do {
        if (pos >= bytes.size) break
        b = bytes[pos++].toInt() and 0xFF
        result = result or ((b and 0x7F) shl shift)
        shift += 7
      } while ((b and 0x80) != 0)
      if (shift < 32 && (result and (1 shl (shift - 1))) != 0) {
        result = result or (-1 shl shift)
      }
      return result
    }
  }
}
