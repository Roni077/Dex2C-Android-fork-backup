package com.example.dex

import com.android.tools.smali.baksmali.Adaptors.ClassDefinition
import com.android.tools.smali.baksmali.Adaptors.MethodDefinition
import com.android.tools.smali.baksmali.BaksmaliOptions
import com.android.tools.smali.baksmali.formatter.BaksmaliWriter
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.dexbacked.DexBackedClassDef
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import com.android.tools.smali.dexlib2.dexbacked.DexBackedField
import com.android.tools.smali.dexlib2.dexbacked.DexBackedMethod
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import java.io.StringWriter
import java.nio.ByteBuffer

data class SmaliMethodSpan(
  val name: String,
  val signature: String,
  val startLineIndex: Int,
  val endLineIndex: Int,
)

data class SmaliDisassemblyResult(
  val smaliText: String,
  val lines: List<String>,
  val lineCount: Int,
  val methodCount: Int,
  val fieldCount: Int,
  val durationMs: Long,
  val methodSpans: List<SmaliMethodSpan> = emptyList(),
  val engineLabel: String = "smali-dexlib2 + baksmali",
)

/**
 * In-memory class-level and method-level Smali disassembler and `dexlib2` class indexer powered by
 * Google's official `com.android.tools.smali:smali-dexlib2` and `com.android.tools.smali:smali-baksmali`.
 */
object SmaliClassDisassembler {

  private const val MAX_CACHED_DEX_FILES = 6

  private val dexCache =
    object : LinkedHashMap<String, DexBackedDexFile>(MAX_CACHED_DEX_FILES + 1, 0.75f, true) {
      override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, DexBackedDexFile>?): Boolean =
        size > MAX_CACHED_DEX_FILES
    }

  fun clearCache() {
    synchronized(dexCache) {
      dexCache.clear()
    }
  }

  /**
   * Uses `DexBackedDexFile` (`smali-dexlib2`) to index classes, fields, and methods from the DEX
   * binary (mirroring `springmusk026/Dex2C-Android`'s `ApkAnalyzerRepository` while preserving raw
   * `code_item` offsets and structural metadata for the C transpiler and CFG viewer).
   */
  fun indexClassesWithDexlib2(
    rawBytes: ByteArray,
    buf: ByteBuffer,
    version: String,
    sha1Hex: String,
    entryName: String,
    classDefsSize: Int,
    classDefsOff: Int,
    types: List<String>,
    fields: List<DexFieldRef>,
    methods: List<DexMethodRef>,
    readCodeMeta: (codeOff: Int) -> DexCodeMeta?,
  ): List<DexClassInfo>? {
    return runCatching {
      val apiLevel = resolveApiLevelForDexVersion(version)
      val dexBackedFile = getOrParseDexBackedFile(entryName, sha1Hex, rawBytes, apiLevel)

      val typeIndexByDescriptor = HashMap<String, Int>(types.size * 2)
      types.forEachIndexed { idx, desc -> typeIndexByDescriptor.putIfAbsent(desc, idx) }

      val fieldIndexByKey = HashMap<String, Int>(fields.size * 2)
      fields.forEachIndexed { idx, f ->
        fieldIndexByKey.putIfAbsent("${f.definingClass}->${f.name}:${f.type}", idx)
      }

      val methodIndexByKey = HashMap<String, Int>(methods.size * 2)
      methods.forEachIndexed { idx, m ->
        val paramsSig = m.proto.parameterTypes.joinToString("")
        methodIndexByKey.putIfAbsent("${m.definingClass}->${m.name}($paramsSig)${m.proto.returnType}", idx)
      }

      // Map class descriptor -> raw class_def offsets (annotationsOff, classDataOff, staticValuesOff)
      data class RawClassOffsets(
        val classIdx: Int,
        val annotationsOff: Int,
        val classDataOff: Int,
        val staticValuesOff: Int,
        val directCodeOffsets: List<Int>,
        val virtualCodeOffsets: List<Int>,
      )

      val rawOffsetsByDescriptor = HashMap<String, RawClassOffsets>(classDefsSize * 2)
      for (i in 0 until classDefsSize) {
        val base = classDefsOff + i * 32
        if (base + 32 > rawBytes.size) break
        val classIdx = buf.getInt(base)
        val annotationsOff = buf.getInt(base + 20)
        val classDataOff = buf.getInt(base + 24)
        val staticValuesOff = buf.getInt(base + 28)
        val desc = types.getOrElse(classIdx) { "Lunknown/Class$i;" }

        val directCodeOffs = mutableListOf<Int>()
        val virtualCodeOffs = mutableListOf<Int>()
        if (classDataOff != 0 && classDataOff < rawBytes.size) {
          val reader = DexHelper.LebReader(rawBytes, classDataOff)
          val sfSize = reader.readUleb128()
          val ifSize = reader.readUleb128()
          val dmSize = reader.readUleb128()
          val vmSize = reader.readUleb128()
          repeat(sfSize) {
            reader.readUleb128()
            reader.readUleb128()
          }
          repeat(ifSize) {
            reader.readUleb128()
            reader.readUleb128()
          }
          repeat(dmSize) {
            reader.readUleb128()
            reader.readUleb128()
            directCodeOffs += reader.readUleb128()
          }
          repeat(vmSize) {
            reader.readUleb128()
            reader.readUleb128()
            virtualCodeOffs += reader.readUleb128()
          }
        }
        rawOffsetsByDescriptor[desc] =
          RawClassOffsets(
            classIdx = classIdx,
            annotationsOff = annotationsOff,
            classDataOff = classDataOff,
            staticValuesOff = staticValuesOff,
            directCodeOffsets = directCodeOffs,
            virtualCodeOffsets = virtualCodeOffs,
          )
      }

      dexBackedFile.classes.mapIndexed { idx, classDef ->
        val descriptor = classDef.type
        val fullName = descriptorToJavaName(descriptor)
        val lastDot = fullName.lastIndexOf('.')
        val pkgName = if (lastDot > 0) fullName.substring(0, lastDot) else "(default)"
        val simpleName = if (lastDot > 0) fullName.substring(lastDot + 1) else fullName
        val rawOffsets = rawOffsetsByDescriptor[descriptor]
        val classIdx = rawOffsets?.classIdx ?: typeIndexByDescriptor[descriptor] ?: idx

        val staticFields =
          classDef.staticFields.mapIndexed { fOrd, field ->
            toDexEncodedField(field, fOrd, isStatic = true, fieldIndexByKey = fieldIndexByKey)
          }
        val instanceFields =
          classDef.instanceFields.mapIndexed { fOrd, field ->
            toDexEncodedField(field, fOrd, isStatic = false, fieldIndexByKey = fieldIndexByKey)
          }

        val directMethods =
          classDef.directMethods.mapIndexed { mOrd, method ->
            val codeOff = rawOffsets?.directCodeOffsets?.getOrNull(mOrd) ?: 0
            toDexEncodedMethod(
              method = method,
              ordinal = mOrd,
              isDirect = true,
              entryName = entryName,
              methodIndexByKey = methodIndexByKey,
              codeMeta = readCodeMeta(codeOff),
            )
          }
        val virtualMethods =
          classDef.virtualMethods.mapIndexed { mOrd, method ->
            val codeOff = rawOffsets?.virtualCodeOffsets?.getOrNull(mOrd) ?: 0
            toDexEncodedMethod(
              method = method,
              ordinal = mOrd,
              isDirect = false,
              entryName = entryName,
              methodIndexByKey = methodIndexByKey,
              codeMeta = readCodeMeta(codeOff),
            )
          }

        DexClassInfo(
          classIndex = classIdx,
          descriptor = descriptor,
          fullName = fullName,
          simpleName = simpleName,
          packageName = pkgName,
          superclass = classDef.superclass,
          interfaces = classDef.interfaces.toList(),
          sourceFile = classDef.sourceFile,
          accessFlags = classDef.accessFlags,
          staticFields = staticFields,
          instanceFields = instanceFields,
          directMethods = directMethods,
          virtualMethods = virtualMethods,
          annotationsOffset = rawOffsets?.annotationsOff ?: 0,
          staticValuesOffset = rawOffsets?.staticValuesOff ?: 0,
          classDataOffset = rawOffsets?.classDataOff ?: 0,
          sourceEntryName = entryName,
        )
      }
    }.getOrNull()
  }

  private fun toDexEncodedField(
    field: com.android.tools.smali.dexlib2.iface.Field,
    ordinal: Int,
    isStatic: Boolean,
    fieldIndexByKey: Map<String, Int>,
  ): DexEncodedField {
    val key = "${field.definingClass}->${field.name}:${field.type}"
    val fieldIdx =
      (field as? DexBackedField)?.fieldIndex
        ?: fieldIndexByKey[key]
        ?: ordinal
    return DexEncodedField(
      fieldIndex = fieldIdx,
      fieldRef =
        DexFieldRef(
          definingClass = field.definingClass,
          type = field.type,
          name = field.name,
        ),
      accessFlags = field.accessFlags,
      isStatic = isStatic,
    )
  }

  private fun toDexEncodedMethod(
    method: Method,
    ordinal: Int,
    isDirect: Boolean,
    entryName: String,
    methodIndexByKey: Map<String, Int>,
    codeMeta: DexCodeMeta?,
  ): DexEncodedMethod {
    val paramTypes = method.parameterTypes.map { it.toString() }
    val returnType = method.returnType
    val key = "${method.definingClass}->${method.name}(${paramTypes.joinToString("")})$returnType"
    val methodIdx =
      (method as? DexBackedMethod)?.methodIndex
        ?: methodIndexByKey[key]
        ?: ordinal
    val shorty = buildShorty(returnType, paramTypes)
    val impl = method.implementation
    val resolvedCodeMeta =
      codeMeta
        ?: if (impl != null) {
          DexCodeMeta(
            registersSize = impl.registerCount,
            insSize = computeParameterRegisterCount(paramTypes, isStatic = (method.accessFlags and 0x0008) != 0),
            outsSize = 0,
            triesSize = impl.tryBlocks.size,
            insnsCount = impl.instructions.sumOf { it.codeUnits },
            insnsOffsetBytes = 0,
          )
        } else {
          null
        }
    return DexEncodedMethod(
      id = "$entryName::${method.definingClass}#$methodIdx",
      methodIndex = methodIdx,
      methodRef =
        DexMethodRef(
          definingClass = method.definingClass,
          proto = DexProto(shorty = shorty, returnType = returnType, parameterTypes = paramTypes),
          name = method.name,
        ),
      accessFlags = method.accessFlags,
      isDirect = isDirect,
      codeMeta = resolvedCodeMeta,
      sourceEntryName = entryName,
    )
  }

  private fun buildShorty(returnType: String, paramTypes: List<String>): String {
    fun shortyChar(desc: String): Char =
      if (desc.startsWith("L") || desc.startsWith("[")) 'L' else desc.firstOrNull() ?: 'V'
    return buildString(1 + paramTypes.size) {
      append(shortyChar(returnType))
      paramTypes.forEach { append(shortyChar(it)) }
    }
  }

  private fun computeParameterRegisterCount(paramTypes: List<String>, isStatic: Boolean): Int {
    var regs = if (isStatic) 0 else 1
    for (p in paramTypes) {
      regs += if (p == "J" || p == "D") 2 else 1
    }
    return regs
  }

  fun disassembleClass(
    dex: DexParsedFile,
    dexClass: DexClassInfo,
  ): SmaliDisassemblyResult {
    val startNanos = System.nanoTime()
    val apiLevel = resolveApiLevelForDexVersion(dex.header.version)
    val dexBackedFile = getOrParseDexBackedFile(dex.entryName, dex.header.sha1Hex, dex.rawBytes, apiLevel)
    val classDef: ClassDef =
      dexBackedFile.classes.firstOrNull { it.type == dexClass.descriptor }
        ?: throw IllegalArgumentException(
          "Class ${dexClass.descriptor} was not found in ${dex.entryName}."
        )

    val options = createDefaultBaksmaliOptions(apiLevel)
    val stringWriter = StringWriter()
    BaksmaliWriter(stringWriter, null).use { writer ->
      ClassDefinition(options, classDef).writeTo(writer)
      writer.flush()
    }

    val smaliText = stringWriter.toString().trimEnd('\n', '\r')
    val lines = if (smaliText.isEmpty()) emptyList() else smaliText.lines()
    val methodSpans = extractMethodSpans(lines)
    val methodCount = classDef.directMethods.count() + classDef.virtualMethods.count()
    val fieldCount = classDef.staticFields.count() + classDef.instanceFields.count()
    val elapsedMs = ((System.nanoTime() - startNanos) / 1_000_000L).coerceAtLeast(1L)

    return SmaliDisassemblyResult(
      smaliText = smaliText,
      lines = lines,
      lineCount = lines.size,
      methodCount = methodCount,
      fieldCount = fieldCount,
      durationMs = elapsedMs,
      methodSpans = methodSpans,
    )
  }

  fun extractMethodSpans(lines: List<String>): List<SmaliMethodSpan> {
    if (lines.isEmpty()) return emptyList()
    val spans = mutableListOf<SmaliMethodSpan>()
    var activeStart = -1
    var activeName = ""
    var activeSig = ""
    for (idx in lines.indices) {
      val trimmed = lines[idx].trim()
      if (trimmed.startsWith(".method ")) {
        activeStart = idx
        val sigToken = trimmed.substringAfterLast(' ')
        val parenIdx = sigToken.indexOf('(')
        activeName = if (parenIdx > 0) sigToken.substring(0, parenIdx) else sigToken
        activeSig = sigToken
      } else if (trimmed.startsWith(".end method") && activeStart >= 0) {
        spans +=
          SmaliMethodSpan(
            name = activeName,
            signature = activeSig,
            startLineIndex = activeStart,
            endLineIndex = idx,
          )
        activeStart = -1
      }
    }
    return spans
  }

  /**
   * Disassembles a single `DexEncodedMethod` into formatted `.method ... .end method` Smali text
   * using `baksmali`'s `MethodDefinition` adaptor (or falls back to a synthesized Smali block if
   * the method is not backed by an on-disk `ClassDef` entry).
   */
  fun disassembleMethod(
    dex: DexParsedFile,
    dexClass: DexClassInfo,
    method: DexEncodedMethod,
  ): String {
    val apiLevel = resolveApiLevelForDexVersion(dex.header.version)
    val baksmaliResult =
      runCatching {
        val dexBackedFile = getOrParseDexBackedFile(dex.entryName, dex.header.sha1Hex, dex.rawBytes, apiLevel)
        val classDef: ClassDef =
          dexBackedFile.classes.firstOrNull { it.type == dexClass.descriptor }
            ?: return@runCatching null
        val expectedParams = method.methodRef.proto.parameterTypes
        val targetMethod: Method =
          (classDef.directMethods + classDef.virtualMethods).firstOrNull { candidate ->
            val candidateIdx = (candidate as? DexBackedMethod)?.methodIndex
            (candidateIdx != null && candidateIdx == method.methodIndex) ||
              (candidate.name == method.methodRef.name &&
                candidate.returnType == method.methodRef.proto.returnType &&
                candidate.parameterTypes.map { it.toString() } == expectedParams)
          } ?: return@runCatching null

        val options = createDefaultBaksmaliOptions(apiLevel)
        val classDefinition = ClassDefinition(options, classDef)
        val stringWriter = StringWriter()
        BaksmaliWriter(stringWriter, classDef.type).use { writer ->
          val impl = targetMethod.implementation
          if (impl != null) {
            val methodDef = MethodDefinition(classDefinition, targetMethod, impl)
            methodDef.writeTo(writer)
          } else {
            MethodDefinition.writeEmptyMethodTo(writer, targetMethod, classDefinition)
          }
          writer.flush()
        }
        stringWriter.toString().trimEnd('\n', '\r')
      }.getOrNull()

    if (!baksmaliResult.isNullOrBlank()) {
      return baksmaliResult
    }

    // Fallback Smali formatting when synthetic method tests bypass class_defs table
    val flags = formatAccessFlags(method.accessFlags, isMethod = true)
    val flagPart = if (flags.isBlank()) "" else "$flags "
    val sig = "${method.methodRef.name}${method.methodRef.jniSignature()}"
    val insns = DalvikDisassembler.disassemble(dex, method)
    return buildString {
      appendLine(".method $flagPart$sig")
      method.codeMeta?.let { meta ->
        appendLine("    .registers ${meta.registersSize}")
      }
      if (insns.isNotEmpty()) {
        appendLine()
        for (insn in insns) {
          val ops = if (insn.operandsText.isBlank()) "" else " ${insn.operandsText}"
          appendLine("    ${insn.mnemonic}$ops")
        }
      }
      append(".end method")
    }
  }

  private fun createDefaultBaksmaliOptions(apiLevel: Int): BaksmaliOptions =
    BaksmaliOptions().apply {
      this.apiLevel = apiLevel
      this.parameterRegisters = true
      this.localsDirective = false
      this.sequentialLabels = true
      this.debugInfo = true
      this.codeOffsets = false
      this.accessorComments = true
      this.implicitReferences = false
    }

  private fun getOrParseDexBackedFile(
    entryName: String,
    sha1Hex: String,
    rawBytes: ByteArray,
    apiLevel: Int,
  ): DexBackedDexFile {
    val cacheKey = "$entryName:$sha1Hex:${rawBytes.size}:$apiLevel"
    synchronized(dexCache) {
      dexCache[cacheKey]?.let { return it }
    }
    val parsed = DexBackedDexFile(Opcodes.forApi(apiLevel), rawBytes)
    synchronized(dexCache) {
      dexCache[cacheKey] = parsed
    }
    return parsed
  }

  private fun resolveApiLevelForDexVersion(version: String): Int =
    when (version.trim()) {
      "035" -> 23
      "037" -> 25
      "038" -> 27
      "039" -> 29
      "040" -> 34
      "041" -> 35
      else -> 35
    }
}
