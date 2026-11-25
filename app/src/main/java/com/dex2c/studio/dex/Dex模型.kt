package com.dex2c.studio.dex

enum class OpCategory {
  NOP,
  MOVE,
  RETURN,
  CONST,
  MONITOR,
  CHECK,
  NEW,
  ARRAY,
  THROW,
  BRANCH,
  COMPARE,
  FIELD,
  INVOKE,
  MATH,
  CONVERT,
  PAYLOAD,
  UNKNOWN,
}

data class DexHeaderInfo(
  val magic: String,
  val version: String,
  val checksum: Long,
  val sha1Hex: String,
  val fileSize: Int,
  val endianTag: Long,
  val stringIdsSize: Int,
  val typeIdsSize: Int,
  val protoIdsSize: Int,
  val fieldIdsSize: Int,
  val methodIdsSize: Int,
  val classDefsSize: Int,
  val linkSize: Int = 0,
  val linkOff: Int = 0,
  val mapOff: Int = 0,
  val dataSize: Int = 0,
  val dataOff: Int = 0,
)

data class DexMapItem(
  val typeCode: Int,
  val typeName: String,
  val count: Int,
  val offset: Int,
) {
  val typeHex: String get() = "0x%04X".format(typeCode)
  val offsetHex: String get() = "0x%08X".format(offset)
}

data class DexMethodHandleInfo(
  val index: Int,
  val kindCode: Int,
  val kindLabel: String,
  val memberIndex: Int,
  val memberSummary: String,
)

data class DexCallSiteInfo(
  val index: Int,
  val encodedArrayOffset: Int,
  val bootstrapHandleIndex: Int?,
  val methodName: String?,
  val methodDescriptor: String?,
  val extraArgsCount: Int,
)

data class DexHiddenApiSummary(
  val sectionOffset: Int,
  val totalByteSize: Int,
  val classEntryCount: Int,
)

data class DexAnnotationEntry(
  val visibilityLabel: String,
  val typeDescriptor: String,
  val elementsPreview: List<String>,
)

data class DexDebugLineEntry(
  val addressWord: Int,
  val lineNumber: Int,
)

data class DexMethodStructuralMeta(
  val methodIndex: Int,
  val codeItemOffset: Int,
  val debugInfoOffset: Int,
  val classAnnotationsOffset: Int,
  val staticValuesOffset: Int,
  val parameterNames: List<String>,
  val debugLines: List<DexDebugLineEntry>,
  val classAnnotations: List<DexAnnotationEntry>,
  val methodAnnotations: List<DexAnnotationEntry>,
  val staticValuesPreview: List<String>,
  val sourceFile: String?,
)

data class DexProto(
  val shorty: String,
  val returnType: String,
  val parameterTypes: List<String>,
)

data class DexFieldRef(
  val definingClass: String,
  val type: String,
  val name: String,
)

data class DexMethodRef(
  val definingClass: String,
  val proto: DexProto,
  val name: String,
) {
  fun prettySignature(): String {
    val params = proto.parameterTypes.joinToString(", ") { descriptorToSimpleName(it) }
    val ret = descriptorToSimpleName(proto.returnType)
    return "$name($params): $ret"
  }

  fun jniSignature(): String {
    return "(${proto.parameterTypes.joinToString("")})${proto.returnType}"
  }

  fun dalvikSignature(): String {
    return "${definingClass}->$name${jniSignature()}"
  }
}

data class DexCatchHandler(
  val typeDescriptor: String?, // null = catch-all (<all>)
  val handlerOffsetWord: Int,
)

data class DexTryBlock(
  val startOffsetWord: Int,
  val insnCount: Int,
  val handlers: List<DexCatchHandler>,
) {
  val endOffsetWord: Int get() = startOffsetWord + insnCount
}

sealed interface DalvikPayload {
  val offsetWords: Int
  val wordCount: Int
  val summaryLabel: String

  data class PackedSwitch(
    override val offsetWords: Int,
    override val wordCount: Int,
    val firstKey: Int,
    val targetOffsets: List<Int>,
  ) : DalvikPayload {
    override val summaryLabel: String
      get() {
        val lastKey = if (targetOffsets.isEmpty()) firstKey else firstKey + targetOffsets.size - 1
        return "packed-switch ($firstKey..$lastKey, ${targetOffsets.size} targets)"
      }
  }

  data class SparseSwitch(
    override val offsetWords: Int,
    override val wordCount: Int,
    val cases: List<Pair<Int, Int>>,
  ) : DalvikPayload {
    override val summaryLabel: String
      get() = "sparse-switch (${cases.size} cases)"
  }

  data class FillArrayData(
    override val offsetWords: Int,
    override val wordCount: Int,
    val elementWidthBytes: Int,
    val elementCount: Int,
    val previewElements: List<String>,
    val hexPreview: String,
  ) : DalvikPayload {
    override val summaryLabel: String
      get() = "fill-array-data (${elementWidthBytes}B × $elementCount)"
  }
}

data class DalvikInstruction(
  val offsetWords: Int,
  val widthWords: Int,
  val opcode: Int,
  val mnemonic: String,
  val format: String,
  val operandsText: String,
  val rawWords: List<Int>,
  val category: OpCategory,
  val regA: Int? = null,
  val regB: Int? = null,
  val regC: Int? = null,
  val regList: List<Int> = emptyList(),
  val isRangeInvoke: Boolean = false,
  val literal: Long? = null,
  val targetOffset: Int? = null,
  val stringRef: String? = null,
  val typeRef: String? = null,
  val fieldRef: DexFieldRef? = null,
  val methodRef: DexMethodRef? = null,
  val payload: DalvikPayload? = null,
) {
  fun formatHexWords(): String = rawWords.joinToString(" ") { "%04X".format(it and 0xFFFF) }
}

data class DexCodeMeta(
  val registersSize: Int,
  val insSize: Int,
  val outsSize: Int,
  val triesSize: Int,
  val insnsCount: Int,
  val insnsOffsetBytes: Int,
  val codeItemOffset: Int = 0,
  val debugInfoOffset: Int = 0,
)

data class DexEncodedField(
  val fieldIndex: Int,
  val fieldRef: DexFieldRef,
  val accessFlags: Int,
  val isStatic: Boolean,
)

data class DexEncodedMethod(
  val id: String,
  val methodIndex: Int,
  val methodRef: DexMethodRef,
  val accessFlags: Int,
  val isDirect: Boolean,
  val codeMeta: DexCodeMeta?,
  val sourceEntryName: String = "classes.dex",
) {
  val isStatic: Boolean = (accessFlags and 0x0008) != 0
  val isNative: Boolean = (accessFlags and 0x0100) != 0
  val isAbstract: Boolean = (accessFlags and 0x0400) != 0
  val isSynthetic: Boolean = (accessFlags and 0x1000) != 0 || (accessFlags and 0x0040) != 0
  val isInitConstructor: Boolean = methodRef.name == "<init>"
  val isConstructor: Boolean = isInitConstructor || (accessFlags and 0x10000) != 0
  val isCoroutineOrSyntheticArtifact: Boolean =
    isSynthetic ||
      methodRef.name == "invokeSuspend" ||
      methodRef.name == "<clinit>" ||
      methodRef.definingClass.contains("\$\$ExternalSyntheticLambda") ||
      methodRef.definingClass.contains("ComposableSingletons\$") ||
      methodRef.definingClass.startsWith("L_COROUTINE/")
  val hasBytecode: Boolean = codeMeta != null && codeMeta.insnsCount > 0
}

data class DexClassInfo(
  val classIndex: Int,
  val descriptor: String,
  val fullName: String,
  val simpleName: String,
  val packageName: String,
  val superclass: String?,
  val interfaces: List<String>,
  val sourceFile: String?,
  val accessFlags: Int,
  val staticFields: List<DexEncodedField>,
  val instanceFields: List<DexEncodedField>,
  val directMethods: List<DexEncodedMethod>,
  val virtualMethods: List<DexEncodedMethod>,
  val annotationsOffset: Int = 0,
  val staticValuesOffset: Int = 0,
  val classDataOffset: Int = 0,
  val sourceEntryName: String = "classes.dex",
) {
  val uniqueKey: String = "$sourceEntryName::$classIndex::$descriptor"

  val allMethods: List<DexEncodedMethod> =
    if (directMethods.isEmpty()) virtualMethods
    else if (virtualMethods.isEmpty()) directMethods
    else CompositeMethodList(directMethods, virtualMethods)

  val bytecodeMethodCount: Int =
    directMethods.count { it.hasBytecode } + virtualMethods.count { it.hasBytecode }

  val isFrameworkClass: Boolean =
    packageName.startsWith("androidx.") ||
      packageName.startsWith("android.") ||
      packageName.startsWith("kotlin.") ||
      packageName.startsWith("kotlinx.") ||
      packageName.startsWith("java.") ||
      packageName.startsWith("javax.") ||
      packageName.startsWith("com.google.") ||
      packageName.startsWith("okhttp3") ||
      packageName.startsWith("okio") ||
      packageName.startsWith("_COROUTINE") ||
      packageName.startsWith("org.intellij.") ||
      packageName.startsWith("org.jetbrains.")

  fun isFrameworkOrLibraryClass(apkPackageName: String? = null): Boolean {
    val cleanApkPkg = apkPackageName?.trim()?.takeIf { it.isNotEmpty() && it != "(default)" }
    if (cleanApkPkg != null && (packageName == cleanApkPkg || packageName.startsWith("$cleanApkPkg."))) {
      return false
    }
    return isFrameworkClass
  }
}

/**
 * Lightweight, zero-allocation composite view over direct and virtual method lists.
 * Avoids creating intermediate ArrayList allocations on class traversal and filtering.
 */
internal class CompositeMethodList(
  private val first: List<DexEncodedMethod>,
  private val second: List<DexEncodedMethod>,
) : AbstractList<DexEncodedMethod>(), java.util.RandomAccess, java.io.Serializable {
  override val size: Int get() = first.size + second.size
  override fun get(index: Int): DexEncodedMethod =
    if (index < first.size) first[index] else second[index - first.size]
}

data class ParsedClassDescriptor(
  val fullName: String,
  val packageName: String,
  val simpleName: String,
)

fun normalizePackageName(rawPackageName: String, defaultPackageLabel: String = "(default)"): String {
  val trimmed = rawPackageName.trim()
  if (trimmed.isEmpty() || trimmed == "(default)" || trimmed == defaultPackageLabel) {
    return defaultPackageLabel
  }
  val unwrapped =
    if (trimmed.startsWith("L") && trimmed.endsWith(";") && trimmed.length > 2) {
      trimmed.substring(1, trimmed.length - 1)
    } else {
      trimmed
    }
  val segments = unwrapped.split('/', '.').map { it.trim() }.filter { it.isNotEmpty() }
  return if (segments.isEmpty()) defaultPackageLabel else segments.joinToString(".")
}

fun parseClassDescriptor(
  descriptor: String,
  defaultPackageLabel: String = "(default)",
): ParsedClassDescriptor {
  val trimmed = descriptor.trim()
  val unwrapped =
    if (trimmed.startsWith("L") && trimmed.endsWith(";") && trimmed.length > 2) {
      trimmed.substring(1, trimmed.length - 1)
    } else {
      descriptorToJavaName(trimmed)
    }
  val segments = unwrapped.split('/', '.').map { it.trim() }.filter { it.isNotEmpty() }
  if (segments.isEmpty()) {
    return ParsedClassDescriptor(
      fullName = trimmed,
      packageName = defaultPackageLabel,
      simpleName = trimmed,
    )
  }
  if (segments.size == 1) {
    val simple = segments.first()
    return ParsedClassDescriptor(
      fullName = simple,
      packageName = defaultPackageLabel,
      simpleName = simple,
    )
  }
  val pkg = segments.dropLast(1).joinToString(".")
  val simple = segments.last()
  return ParsedClassDescriptor(
    fullName = "$pkg.$simple",
    packageName = pkg,
    simpleName = simple,
  )
}

fun inferDominantDexPackageName(classes: List<DexClassInfo>): String? {
  if (classes.isEmpty()) return null
  val nonFramework =
    classes.filter {
      !it.isFrameworkClass &&
        it.packageName.isNotBlank() &&
        it.packageName != "(default)"
    }
  val pool =
    if (nonFramework.isNotEmpty()) nonFramework
    else classes.filter { it.packageName.isNotBlank() && it.packageName != "(default)" }
  if (pool.isEmpty()) return null

  // Prefer the package of MainActivity / Application if present in non-framework classes
  val entryPointClass =
    nonFramework.firstOrNull {
      it.simpleName == "MainActivity" ||
        it.simpleName.endsWith("Application") ||
        it.simpleName == "App"
    }
  if (entryPointClass != null) {
    val parts = entryPointClass.packageName.split('.').filter { it.isNotBlank() }
    val rootCandidate =
      if (parts.size >= 3 && parts.last() in setOf("ui", "activity", "activities", "main", "view", "screens")) {
        parts.dropLast(1).joinToString(".")
      } else {
        entryPointClass.packageName
      }
    if (rootCandidate.isNotBlank()) return rootCandidate
  }

  // Otherwise score package prefixes by how many app classes reside under them
  val exactCounts = pool.groupingBy { it.packageName }.eachCount()
  val prefixScores = linkedMapOf<String, Int>()
  for ((pkg, count) in exactCounts) {
    val parts = pkg.split('.').filter { it.isNotBlank() }
    if (parts.isEmpty()) continue
    val prefixDepth = parts.size.coerceAtMost(3)
    val candidate = parts.take(prefixDepth).joinToString(".")
    prefixScores[candidate] = (prefixScores[candidate] ?: 0) + count
    if (parts.size >= 2) {
      val twoSeg = parts.take(2).joinToString(".")
      prefixScores[twoSeg] = (prefixScores[twoSeg] ?: 0) + count
    }
  }

  // If a 2-segment root (e.g., com.example) covers >= 70% of non-framework classes and has multiple subpackages,
  // check whether a 3-segment package is the true app root or the 2-segment root is.
  val totalPool = pool.size
  val bestThreeSeg =
    prefixScores.entries
      .filter { it.key.count { ch -> ch == '.' } >= 2 }
      .maxWithOrNull(compareBy<Map.Entry<String, Int>> { it.value }.thenBy { it.key.length })
  if (bestThreeSeg != null && bestThreeSeg.value * 2 >= totalPool) {
    return bestThreeSeg.key
  }
  return prefixScores.entries
    .maxWithOrNull(compareBy<Map.Entry<String, Int>> { it.value }.thenBy { it.key.count { ch -> ch == '.' } })
    ?.key
}

data class ApkDex条目(
  val entryName: String,
  val byteSize: Int,
)
typealias ApkDexEntry = ApkDex条目

data class DexParsedFile(
  val sourceLabel: String,
  val entryName: String,
  val rawBytes: ByteArray,
  val header: DexHeaderInfo,
  val strings: List<String> = emptyList(),
  val types: List<String> = emptyList(),
  val protos: List<DexProto> = emptyList(),
  val fields: List<DexFieldRef> = emptyList(),
  val methods: List<DexMethodRef> = emptyList(),
  val classes: List<DexClassInfo>,
  val mapItems: List<DexMapItem> = emptyList(),
  val methodHandles: List<DexMethodHandleInfo> = emptyList(),
  val callSites: List<DexCallSiteInfo> = emptyList(),
  val hiddenApiSummary: DexHiddenApiSummary? = null,
  val parseDurationMs: Long = 0L,
  val parserEngine: String = "Kotlin DexFile/DexHelper",
) {
  val areTransientPoolsReleased: Boolean
    get() = strings.isEmpty() && types.isEmpty() && protos.isEmpty() && fields.isEmpty() && methods.isEmpty()

  fun stringAt(index: Int): String? {
    if (index < 0) return null
    if (index in strings.indices) return strings[index]
    return DexParser.readStringAt(rawBytes, index)
  }

  fun typeAt(index: Int): String? {
    if (index < 0) return null
    if (index in types.indices) return types[index]
    return DexParser.readTypeAt(rawBytes, index)
  }

  fun protoAt(index: Int): DexProto? {
    if (index < 0) return null
    if (index in protos.indices) return protos[index]
    return DexParser.readProtoAt(rawBytes, index)
  }

  fun fieldAt(index: Int): DexFieldRef? {
    if (index < 0) return null
    if (index in fields.indices) return fields[index]
    return DexParser.readFieldAt(rawBytes, index)
  }

  fun methodAt(index: Int): DexMethodRef? {
    if (index < 0) return null
    if (index in methods.indices) return methods[index]
    return DexParser.readMethodAt(rawBytes, index)
  }

  /**
   * Releases the bulk `strings`, `types`, `protos`, `fields`, and `methods` constant-pool lists
   * once class definitions have been indexed. Subsequent disassembler or metadata queries resolve
   * individual pool entries on demand directly from [rawBytes] via [stringAt], [typeAt], etc.
   */
  fun releaseTransientPools(): DexParsedFile {
    if (areTransientPoolsReleased) return this
    return copy(
      strings = emptyList(),
      types = emptyList(),
      protos = emptyList(),
      fields = emptyList(),
      methods = emptyList(),
    )
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is DexParsedFile) return false
    return sourceLabel == other.sourceLabel &&
      entryName == other.entryName &&
      header == other.header
  }

  override fun hashCode(): Int {
    var result = sourceLabel.hashCode()
    result = 31 * result + entryName.hashCode()
    result = 31 * result + header.hashCode()
    return result
  }
}

fun descriptorToJavaName(descriptor: String): String {
  if (descriptor.startsWith("[")) {
    return "${descriptorToJavaName(descriptor.substring(1))}[]"
  }
  return when (descriptor) {
    "V" -> "void"
    "Z" -> "boolean"
    "B" -> "byte"
    "S" -> "short"
    "C" -> "char"
    "I" -> "int"
    "J" -> "long"
    "F" -> "float"
    "D" -> "double"
    else ->
      if (descriptor.startsWith("L") && descriptor.endsWith(";")) {
        val raw = descriptor.substring(1, descriptor.length - 1)
        val segments = raw.split('/', '.').map { it.trim() }.filter { it.isNotEmpty() }
        if (segments.isNotEmpty()) segments.joinToString(".") else raw.replace('/', '.')
      } else {
        descriptor
      }
  }
}

fun descriptorToSimpleName(descriptor: String): String {
  val full = descriptorToJavaName(descriptor)
  val dot = full.lastIndexOf('.')
  return if (dot >= 0) full.substring(dot + 1) else full
}

fun formatAccessFlags(flags: Int, isMethod: Boolean = false): String {
  val parts = mutableListOf<String>()
  if ((flags and 0x0001) != 0) parts += "public"
  if ((flags and 0x0002) != 0) parts += "private"
  if ((flags and 0x0004) != 0) parts += "protected"
  if ((flags and 0x0008) != 0) parts += "static"
  if ((flags and 0x0010) != 0) parts += "final"
  if ((flags and 0x0020) != 0) parts += if (isMethod) "synchronized" else ""
  if (isMethod && (flags and 0x0100) != 0) parts += "native"
  if (!isMethod && (flags and 0x0200) != 0) parts += "interface"
  if ((flags and 0x0400) != 0) parts += "abstract"
  if (isMethod && (flags and 0x10000) != 0) parts += "constructor"
  return parts.filter { it.isNotEmpty() }.joinToString(" ")
}

data class ClassDefinitionSummary(
  val simpleName: String,
  val packageName: String,
  val methodCount: Int,
  val bytecodeMethodCount: Int,
  val isFramework: Boolean,
)

data class BytecodeOverviewSummary(
  val sourceLabel: String,
  val entryName: String,
  val dexCount: Int,
  val dexVersion: String,
  val totalMethods: Int,
  val bytecodeMethods: Int,
  val totalClassDefinitions: Int,
  val appClassDefinitions: Int,
  val totalStrings: Int,
  val analyzedAtMillis: Long,
  val topClasses: List<ClassDefinitionSummary>,
  val packageName: String? = null,
)

fun buildBytecodeOverviewSummary(
  sourceLabel: String,
  activeEntryName: String,
  dexList: List<DexParsedFile>,
  analyzedAtMillis: Long = System.currentTimeMillis(),
  packageName: String? = null,
): BytecodeOverviewSummary {
  val primary = dexList.firstOrNull { it.entryName == activeEntryName } ?: dexList.firstOrNull()
  val allClasses = dexList.flatMap { it.classes }
  val resolvedPkg = packageName?.takeIf { it.isNotBlank() } ?: inferDominantDexPackageName(allClasses)
  val encodedMethodCount = allClasses.sumOf { it.allMethods.size }
  val headerMethodCount = dexList.sumOf { it.header.methodIdsSize }
  val headerClassCount = dexList.sumOf { it.header.classDefsSize }
  val bytecodeMethodCount = allClasses.sumOf { it.bytecodeMethodCount }
  val appClassCount = allClasses.count { !it.isFrameworkOrLibraryClass(resolvedPkg) }
  val stringCount = dexList.sumOf { it.header.stringIdsSize }

  val hasAppClasses = allClasses.any { !it.isFrameworkOrLibraryClass(resolvedPkg) }
  val rankedPool =
    if (hasAppClasses) allClasses.filter { !it.isFrameworkOrLibraryClass(resolvedPkg) }
    else allClasses
  val topClasses =
    rankedPool
      .sortedWith(
        compareByDescending<DexClassInfo> { it.bytecodeMethodCount }
          .thenByDescending { it.allMethods.size }
          .thenBy { it.simpleName }
      )
      .take(3)
      .map { cls ->
        ClassDefinitionSummary(
          simpleName = cls.simpleName.ifBlank { cls.descriptor },
          packageName = cls.packageName,
          methodCount = cls.allMethods.size,
          bytecodeMethodCount = cls.bytecodeMethodCount,
          isFramework = cls.isFrameworkOrLibraryClass(resolvedPkg),
        )
      }

  return BytecodeOverviewSummary(
    sourceLabel = sourceLabel,
    entryName = primary?.entryName ?: activeEntryName,
    dexCount = dexList.size.coerceAtLeast(1),
    dexVersion = primary?.header?.version?.ifBlank { "035" } ?: "035",
    totalMethods = if (headerMethodCount > 0) headerMethodCount else encodedMethodCount,
    bytecodeMethods = bytecodeMethodCount,
    totalClassDefinitions = if (headerClassCount > 0) headerClassCount else allClasses.size,
    appClassDefinitions = appClassCount,
    totalStrings = stringCount,
    analyzedAtMillis = analyzedAtMillis,
    topClasses = topClasses,
    packageName = resolvedPkg,
  )
}

