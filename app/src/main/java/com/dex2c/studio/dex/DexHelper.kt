package com.dex2c.studio.dex

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Pure Kotlin structural query facade and decoders for DEX `035`–`041` sections
 * (`debug_info_item`, `annotations_directory_item`, `encoded_array_item`).
 */
object DexHelper {

  fun getClasses(dex: DexParsedFile): List<DexClassInfo> = dex.classes

  fun getMethods(dex: DexParsedFile): List<DexMethodRef> = dex.methods

  fun getFields(dex: DexParsedFile): List<DexFieldRef> = dex.fields

  fun getMapItem(dex: DexParsedFile, typeCode: Int): DexMapItem? =
    dex.mapItems.firstOrNull { it.typeCode == typeCode }

  fun inspectMethodMetadata(
    dex: DexParsedFile,
    dexClass: DexClassInfo,
    method: DexEncodedMethod,
  ): DexMethodStructuralMeta {
    val raw = dex.rawBytes
    val order =
      if (dex.header.endianTag == 0x78563412L) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN
    val buf = ByteBuffer.wrap(raw).order(order)

    val codeItemOff = method.codeMeta?.codeItemOffset ?: 0
    val debugInfoOff = method.codeMeta?.debugInfoOffset ?: 0

    val (paramNames, debugLines) =
      if (debugInfoOff > 0 && debugInfoOff < raw.size) {
        parseDebugInfoItem(dex, debugInfoOff)
      } else {
        emptyList<String>() to emptyList()
      }

    val (classAnns, methodAnns) =
      if (dexClass.annotationsOffset > 0 && dexClass.annotationsOffset + 16 <= raw.size) {
        parseClassAndMethodAnnotations(dex, buf, dexClass.annotationsOffset, method.methodIndex)
      } else {
        emptyList<DexAnnotationEntry>() to emptyList()
      }

    val staticValues =
      if (dexClass.staticValuesOffset > 0 && dexClass.staticValuesOffset < raw.size) {
        parseEncodedArrayPreview(dex, dexClass.staticValuesOffset)
      } else {
        emptyList()
      }

    return DexMethodStructuralMeta(
      methodIndex = method.methodIndex,
      codeItemOffset = codeItemOff,
      debugInfoOffset = debugInfoOff,
      classAnnotationsOffset = dexClass.annotationsOffset,
      staticValuesOffset = dexClass.staticValuesOffset,
      parameterNames = paramNames,
      debugLines = debugLines,
      classAnnotations = classAnns,
      methodAnnotations = methodAnns,
      staticValuesPreview = staticValues,
      sourceFile = dexClass.sourceFile,
    )
  }

  private fun parseDebugInfoItem(
    dex: DexParsedFile,
    offset: Int,
  ): Pair<List<String>, List<DexDebugLineEntry>> {
    val raw = dex.rawBytes
    if (offset <= 0 || offset >= raw.size) return emptyList<String>() to emptyList()
    val reader = LebReader(raw, offset)

    var line = reader.readUleb128()
    val paramsSize = reader.readUleb128().coerceIn(0, 64)
    val paramNames =
      List(paramsSize) { idx ->
        val nameIdx = reader.readUleb128p1()
        if (nameIdx >= 0) dex.strings.getOrElse(nameIdx) { "p$idx" } else "p$idx"
      }

    var address = 0
    val entries = mutableListOf<DexDebugLineEntry>()
    var guard = 0
    while (reader.pos < raw.size && guard < 2048) {
      guard++
      val opcode = raw[reader.pos++].toInt() and 0xFF
      when (opcode) {
        0x00 -> break // DBG_END_SEQUENCE
        0x01 -> address += reader.readUleb128() // DBG_ADVANCE_PC
        0x02 -> line += reader.readSleb128() // DBG_ADVANCE_LINE
        0x03 -> { // DBG_START_LOCAL
          reader.readUleb128()
          reader.readUleb128p1()
          reader.readUleb128p1()
        }
        0x04 -> { // DBG_START_LOCAL_EXTENDED
          reader.readUleb128()
          reader.readUleb128p1()
          reader.readUleb128p1()
          reader.readUleb128p1()
        }
        0x05 -> reader.readUleb128() // DBG_END_LOCAL
        0x06 -> reader.readUleb128() // DBG_RESTART_LOCAL
        0x07, 0x08 -> Unit // DBG_SET_PROLOGUE_END / DBG_SET_EPILOGUE_BEGIN
        0x09 -> reader.readUleb128p1() // DBG_SET_FILE
        else -> {
          val adjusted = opcode - 0x0A
          line += -4 + (adjusted % 15)
          address += adjusted / 15
          entries += DexDebugLineEntry(addressWord = address, lineNumber = line)
        }
      }
    }
    return paramNames to entries
  }

  private fun parseClassAndMethodAnnotations(
    dex: DexParsedFile,
    buf: ByteBuffer,
    dirOff: Int,
    targetMethodIndex: Int,
  ): Pair<List<DexAnnotationEntry>, List<DexAnnotationEntry>> {
    val rawLen = dex.rawBytes.size
    if (dirOff <= 0 || dirOff + 16 > rawLen) return emptyList<DexAnnotationEntry>() to emptyList()

    val classAnnsOff = buf.getInt(dirOff)
    val fieldsSize = buf.getInt(dirOff + 4).coerceAtLeast(0)
    val methodsSize = buf.getInt(dirOff + 8).coerceAtLeast(0)

    val classAnns =
      if (classAnnsOff > 0 && classAnnsOff < rawLen) {
        parseAnnotationSet(dex, buf, classAnnsOff)
      } else {
        emptyList()
      }

    var cursor = dirOff + 16 + fieldsSize * 8
    var methodAnnsOff = 0
    if (cursor >= 0 && cursor + methodsSize.toLong() * 8L <= rawLen) {
      for (i in 0 until methodsSize) {
        val mIdx = buf.getInt(cursor)
        val annOff = buf.getInt(cursor + 4)
        cursor += 8
        if (mIdx == targetMethodIndex) {
          methodAnnsOff = annOff
          break
        }
      }
    }

    val methodAnns =
      if (methodAnnsOff > 0 && methodAnnsOff < rawLen) {
        parseAnnotationSet(dex, buf, methodAnnsOff)
      } else {
        emptyList()
      }

    return classAnns to methodAnns
  }

  private fun parseAnnotationSet(
    dex: DexParsedFile,
    buf: ByteBuffer,
    setOff: Int,
  ): List<DexAnnotationEntry> {
    val raw = dex.rawBytes
    if (setOff <= 0 || setOff + 4 > raw.size) return emptyList()
    val count = buf.getInt(setOff).coerceIn(0, 64)
    val entries = mutableListOf<DexAnnotationEntry>()
    for (i in 0 until count) {
      val ptrOff = setOff + 4 + i * 4
      if (ptrOff + 4 > raw.size) break
      val itemOff = buf.getInt(ptrOff)
      if (itemOff <= 0 || itemOff + 2 > raw.size) continue
      val visCode = raw[itemOff].toInt() and 0xFF
      val visLabel =
        when (visCode) {
          0 -> "BUILD"
          1 -> "RUNTIME"
          2 -> "SYSTEM"
          else -> "VIS_$visCode"
        }
      val reader = LebReader(raw, itemOff + 1)
      val typeIdx = reader.readUleb128()
      val elemCount = reader.readUleb128().coerceIn(0, 16)
      val typeDesc = dex.types.getOrElse(typeIdx) { "Lunknown/Annotation;" }
      val elems = mutableListOf<String>()
      repeat(elemCount) {
        if (reader.pos >= raw.size) return@repeat
        val nameIdx = reader.readUleb128()
        val elemName = dex.strings.getOrElse(nameIdx) { "arg" }
        val elemVal = readEncodedValuePreview(dex, reader, depth = 0)
        elems += "$elemName = $elemVal"
      }
      entries +=
        DexAnnotationEntry(
          visibilityLabel = visLabel,
          typeDescriptor = typeDesc,
          elementsPreview = elems,
        )
    }
    return entries
  }

  fun parseEncodedArrayPreview(dex: DexParsedFile, offset: Int): List<String> {
    val raw = dex.rawBytes
    if (offset <= 0 || offset >= raw.size) return emptyList()
    val reader = LebReader(raw, offset)
    val count = reader.readUleb128().coerceIn(0, 32)
    return List(count) { readEncodedValuePreview(dex, reader, depth = 0) }
  }

  internal fun readEncodedValuePreview(
    dex: DexParsedFile,
    reader: LebReader,
    depth: Int,
  ): String {
    if (reader.pos >= dex.rawBytes.size || depth > 4) return "?"
    val header = dex.rawBytes[reader.pos++].toInt() and 0xFF
    val valueType = header and 0x1F
    val valueArg = (header ushr 5) and 0x07
    val byteCount = valueArg + 1

    return when (valueType) {
      0x00 -> reader.readSignedBytes(1).toString() // BYTE
      0x02 -> reader.readSignedBytes(byteCount).toString() // SHORT
      0x03 -> "'${reader.readUnsignedBytes(byteCount).toInt().toChar()}'" // CHAR
      0x04 -> reader.readSignedBytes(byteCount).toString() // INT
      0x06 -> "${reader.readSignedBytes(byteCount)}L" // LONG
      0x10 -> "float(${reader.readUnsignedBytes(byteCount)})" // FLOAT
      0x11 -> "double(${reader.readUnsignedBytes(byteCount)})" // DOUBLE
      0x15 -> { // METHOD_TYPE
        val idx = reader.readUnsignedBytes(byteCount).toInt()
        dex.protos.getOrNull(idx)?.shorty ?: "proto#$idx"
      }
      0x16 -> { // METHOD_HANDLE
        val idx = reader.readUnsignedBytes(byteCount).toInt()
        dex.methodHandles.getOrNull(idx)?.let { "${it.kindLabel}:${it.memberSummary}" } ?: "handle#$idx"
      }
      0x17 -> { // STRING
        val idx = reader.readUnsignedBytes(byteCount).toInt()
        "\"${dex.strings.getOrElse(idx) { "str#$idx" }}\""
      }
      0x18 -> { // TYPE
        val idx = reader.readUnsignedBytes(byteCount).toInt()
        descriptorToSimpleName(dex.types.getOrElse(idx) { "type#$idx" })
      }
      0x19, 0x1B -> { // FIELD / ENUM
        val idx = reader.readUnsignedBytes(byteCount).toInt()
        dex.fields.getOrNull(idx)?.let { "${descriptorToSimpleName(it.definingClass)}.${it.name}" }
          ?: "field#$idx"
      }
      0x1A -> { // METHOD
        val idx = reader.readUnsignedBytes(byteCount).toInt()
        dex.methods.getOrNull(idx)?.prettySignature() ?: "method#$idx"
      }
      0x1C -> { // ARRAY
        val size = reader.readUleb128().coerceIn(0, 16)
        val items = List(size) { readEncodedValuePreview(dex, reader, depth + 1) }
        "[${items.joinToString(", ")}]"
      }
      0x1D -> { // ANNOTATION
        val typeIdx = reader.readUleb128()
        val size = reader.readUleb128().coerceIn(0, 8)
        repeat(size) {
          reader.readUleb128()
          readEncodedValuePreview(dex, reader, depth + 1)
        }
        "@${descriptorToSimpleName(dex.types.getOrElse(typeIdx) { "Annotation" })}"
      }
      0x1E -> "null" // NULL
      0x1F -> (valueArg != 0).toString() // BOOLEAN
      else -> "val(0x%02X)".format(valueType)
    }
  }

  internal class LebReader(private val bytes: ByteArray, var pos: Int) {
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

    fun readUleb128p1(): Int = readUleb128() - 1

    fun readSleb128(): Int {
      var result = 0
      var shift = 0
      var b = 0
      while (pos < bytes.size) {
        b = bytes[pos++].toInt() and 0xFF
        result = result or ((b and 0x7F) shl shift)
        shift += 7
        if ((b and 0x80) == 0) break
      }
      if (shift < 32 && (b and 0x40) != 0) {
        result = result or (-1 shl shift)
      }
      return result
    }

    fun readUnsignedBytes(count: Int): Long {
      var value = 0L
      for (i in 0 until count.coerceIn(1, 8)) {
        if (pos >= bytes.size) break
        value = value or ((bytes[pos++].toLong() and 0xFFL) shl (i * 8))
      }
      return value
    }

    fun readSignedBytes(count: Int): Long {
      val n = count.coerceIn(1, 8)
      var value = 0L
      var lastByte = 0
      for (i in 0 until n) {
        if (pos >= bytes.size) break
        lastByte = bytes[pos++].toInt() and 0xFF
        value = value or (lastByte.toLong() shl (i * 8))
      }
      if (n < 8 && (lastByte and 0x80) != 0) {
        value = value or (-1L shl (n * 8))
      }
      return value
    }
  }
}
