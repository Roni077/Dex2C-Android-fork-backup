package com.example.dex

import android.content.Context
import android.net.Uri
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipInputStream

object DexParser {

  fun inspectApkOrDexFromUri(context: Context, uri: Uri, displayName: String): Pair<List<ApkDexEntry>, Map<String, ByteArray>> {
    val bytes =
      context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        ?: throw IllegalArgumentException("Unable to read selected file.")
    return extractDexContainers(bytes, displayName)
  }

  fun inspectSelfApk(context: Context): Pair<List<ApkDexEntry>, Map<String, ByteArray>> {
    val sourceApk = File(context.applicationInfo.sourceDir)
    val bytes = FileInputStream(sourceApk).use { it.readBytes() }
    return extractDexContainers(bytes, sourceApk.name)
  }

  fun extractDexContainers(bytes: ByteArray, fileName: String): Pair<List<ApkDexEntry>, Map<String, ByteArray>> {
    if (bytes.size >= 4 && bytes[0] == 'd'.code.toByte() && bytes[1] == 'e'.code.toByte() && bytes[2] == 'x'.code.toByte() && bytes[3] == '\n'.code.toByte()) {
      val name = if (fileName.endsWith(".dex", ignoreCase = true)) fileName else "classes.dex"
      return listOf(ApkDexEntry(name, bytes.size)) to mapOf(name to bytes)
    }

    val entries = mutableListOf<ApkDexEntry>()
    val map = linkedMapOf<String, ByteArray>()
    ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
      while (true) {
        val entry = zis.nextEntry ?: break
        if (!entry.isDirectory && entry.name.endsWith(".dex", ignoreCase = true)) {
          val dexBytes = zis.readBytes()
          entries += ApkDexEntry(entry.name, dexBytes.size)
          map[entry.name] = dexBytes
        }
        zis.closeEntry()
      }
    }

    if (entries.isEmpty()) {
      throw IllegalArgumentException("No DEX binary or classes*.dex entries found inside \"$fileName\".")
    }
    val sorted = entries.sortedWith(compareBy({ it.entryName.length }, { it.entryName }))
    return sorted to map
  }

  fun parseDex(
    rawBytes: ByteArray,
    sourceLabel: String,
    entryName: String,
  ): DexParsedFile {
    val startNanos = System.nanoTime()
    if (rawBytes.size < 0x70) {
      throw IllegalArgumentException("File too small to be a valid DEX binary (${rawBytes.size} bytes).")
    }
    val magic = String(rawBytes, 0, 8, Charsets.US_ASCII)
    if (!magic.startsWith("dex\n")) {
      throw IllegalArgumentException("Invalid DEX magic header.")
    }
    val version = magic.substring(4, 7)

    val endianTagLe =
      ByteBuffer.wrap(rawBytes, 0x28, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
    val order =
      if (endianTagLe == 0x78563412L) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN
    val buf = ByteBuffer.wrap(rawBytes).order(order)

    val checksum = buf.getInt(0x08).toLong() and 0xFFFFFFFFL
    val sha1Hex = (0x0C until 0x20).joinToString("") { "%02x".format(rawBytes[it].toInt() and 0xFF) }
    val fileSize = buf.getInt(0x20)
    val endianTag = buf.getInt(0x28).toLong() and 0xFFFFFFFFL
    val linkSize = buf.getInt(0x2C)
    val linkOff = buf.getInt(0x30)
    val mapOff = buf.getInt(0x34)

    val stringIdsSize = buf.getInt(0x38)
    val stringIdsOff = buf.getInt(0x3C)
    val typeIdsSize = buf.getInt(0x40)
    val typeIdsOff = buf.getInt(0x44)
    val protoIdsSize = buf.getInt(0x48)
    val protoIdsOff = buf.getInt(0x4C)
    val fieldIdsSize = buf.getInt(0x50)
    val fieldIdsOff = buf.getInt(0x54)
    val methodIdsSize = buf.getInt(0x58)
    val methodIdsOff = buf.getInt(0x5C)
    val classDefsSize = buf.getInt(0x60)
    val classDefsOff = buf.getInt(0x64)
    val dataSize = buf.getInt(0x68)
    val dataOff = buf.getInt(0x6C)

    val header =
      DexHeaderInfo(
        magic = magic.replace("\n", "\\n").replace("\u0000", "\\0"),
        version = version,
        checksum = checksum,
        sha1Hex = sha1Hex,
        fileSize = fileSize,
        endianTag = endianTag,
        stringIdsSize = stringIdsSize,
        typeIdsSize = typeIdsSize,
        protoIdsSize = protoIdsSize,
        fieldIdsSize = fieldIdsSize,
        methodIdsSize = methodIdsSize,
        classDefsSize = classDefsSize,
        linkSize = linkSize,
        linkOff = linkOff,
        mapOff = mapOff,
        dataSize = dataSize,
        dataOff = dataOff,
      )

    // 1. Strings
    val strings =
      List(stringIdsSize) { i ->
        val strOff = buf.getInt(stringIdsOff + i * 4)
        readMutf8String(rawBytes, strOff)
      }

    // 2. Types
    val types =
      List(typeIdsSize) { i ->
        val descIdx = buf.getInt(typeIdsOff + i * 4)
        strings.getOrElse(descIdx) { "Lunknown/Type$i;" }
      }

    // 3. Protos
    val protos =
      List(protoIdsSize) { i ->
        val base = protoIdsOff + i * 12
        val shortyIdx = buf.getInt(base)
        val returnIdx = buf.getInt(base + 4)
        val paramsOff = buf.getInt(base + 8)
        val params =
          if (paramsOff == 0) {
            emptyList()
          } else {
            val count = buf.getInt(paramsOff)
            List(count) { p ->
              val tIdx = buf.getShort(paramsOff + 4 + p * 2).toInt() and 0xFFFF
              types.getOrElse(tIdx) { "Ljava/lang/Object;" }
            }
          }
        DexProto(
          shorty = strings.getOrElse(shortyIdx) { "V" },
          returnType = types.getOrElse(returnIdx) { "V" },
          parameterTypes = params,
        )
      }

    // 4. Fields
    val fields =
      List(fieldIdsSize) { i ->
        val base = fieldIdsOff + i * 8
        val classIdx = buf.getShort(base).toInt() and 0xFFFF
        val typeIdx = buf.getShort(base + 2).toInt() and 0xFFFF
        val nameIdx = buf.getInt(base + 4)
        DexFieldRef(
          definingClass = types.getOrElse(classIdx) { "Ljava/lang/Object;" },
          type = types.getOrElse(typeIdx) { "Ljava/lang/Object;" },
          name = strings.getOrElse(nameIdx) { "field_$i" },
        )
      }

    // 5. Methods
    val methods =
      List(methodIdsSize) { i ->
        val base = methodIdsOff + i * 8
        val classIdx = buf.getShort(base).toInt() and 0xFFFF
        val protoIdx = buf.getShort(base + 2).toInt() and 0xFFFF
        val nameIdx = buf.getInt(base + 4)
        DexMethodRef(
          definingClass = types.getOrElse(classIdx) { "Ljava/lang/Object;" },
          proto = protos.getOrElse(protoIdx) { DexProto("V", "V", emptyList()) },
          name = strings.getOrElse(nameIdx) { "method_$i" },
        )
      }

    // 6. map_list structural directory
    val mapItems = parseMapList(buf, rawBytes.size, mapOff)

    // 7. DEX 038-041 structural items (method_handle_item, call_site_id_item, hiddenapi)
    val methodHandles = parseMethodHandles(buf, rawBytes.size, mapItems, fields, methods)
    val callSites = parseCallSites(buf, rawBytes, mapItems, strings, types, protos, fields, methods, methodHandles)
    val hiddenApiSummary = parseHiddenApiSummary(buf, rawBytes.size, mapItems, classDefsSize)

    // 8. Class Defs (via smali-dexlib2 DexBackedDexFile with raw offset hydration)
    val dexlib2Classes =
      SmaliClassDisassembler.indexClassesWithDexlib2(
        rawBytes = rawBytes,
        buf = buf,
        version = version,
        sha1Hex = sha1Hex,
        entryName = entryName,
        classDefsSize = classDefsSize,
        classDefsOff = classDefsOff,
        types = types,
        fields = fields,
        methods = methods,
        readCodeMeta = { codeOff -> readCodeMeta(buf, rawBytes.size, codeOff) },
      )
    val classes =
      dexlib2Classes
        ?: List(classDefsSize) { i ->
          val base = classDefsOff + i * 32
          val classIdx = buf.getInt(base)
          val accessFlags = buf.getInt(base + 4)
        val superIdx = buf.getInt(base + 8)
        val interfacesOff = buf.getInt(base + 12)
        val sourceFileIdx = buf.getInt(base + 16)
        val annotationsOff = buf.getInt(base + 20)
        val classDataOff = buf.getInt(base + 24)
        val staticValuesOff = buf.getInt(base + 28)

        val descriptor = types.getOrElse(classIdx) { "Lunknown/Class$i;" }
        val fullName = descriptorToJavaName(descriptor)
        val lastDot = fullName.lastIndexOf('.')
        val pkgName = if (lastDot > 0) fullName.substring(0, lastDot) else "(default)"
        val simpleName = if (lastDot > 0) fullName.substring(lastDot + 1) else fullName

        val superclass =
          if (superIdx == -1 || superIdx >= types.size) null else types[superIdx]
        val sourceFile =
          if (sourceFileIdx == -1 || sourceFileIdx >= strings.size) null else strings[sourceFileIdx]

        val interfaces =
          if (interfacesOff == 0) {
            emptyList()
          } else {
            val count = buf.getInt(interfacesOff)
            List(count) { k ->
              val tIdx = buf.getShort(interfacesOff + 4 + k * 2).toInt() and 0xFFFF
              types.getOrElse(tIdx) { "Ljava/lang/Object;" }
            }
          }

        val staticFields = mutableListOf<DexEncodedField>()
        val instanceFields = mutableListOf<DexEncodedField>()
        val directMethods = mutableListOf<DexEncodedMethod>()
        val virtualMethods = mutableListOf<DexEncodedMethod>()

        if (classDataOff != 0 && classDataOff < rawBytes.size) {
          val reader = UlebReader(rawBytes, classDataOff)
          val staticFieldsSize = reader.readUleb128()
          val instanceFieldsSize = reader.readUleb128()
          val directMethodsSize = reader.readUleb128()
          val virtualMethodsSize = reader.readUleb128()

          var fieldIdx = 0
          repeat(staticFieldsSize) {
            fieldIdx += reader.readUleb128()
            val flags = reader.readUleb128()
            val ref = fields.getOrNull(fieldIdx)
            if (ref != null) staticFields += DexEncodedField(fieldIdx, ref, flags, isStatic = true)
          }

          fieldIdx = 0
          repeat(instanceFieldsSize) {
            fieldIdx += reader.readUleb128()
            val flags = reader.readUleb128()
            val ref = fields.getOrNull(fieldIdx)
            if (ref != null) instanceFields += DexEncodedField(fieldIdx, ref, flags, isStatic = false)
          }

          var methodIdx = 0
          repeat(directMethodsSize) {
            methodIdx += reader.readUleb128()
            val flags = reader.readUleb128()
            val codeOff = reader.readUleb128()
            val ref = methods.getOrNull(methodIdx)
            if (ref != null) {
              directMethods +=
                DexEncodedMethod(
                  id = "$entryName::$descriptor#$methodIdx",
                  methodIndex = methodIdx,
                  methodRef = ref,
                  accessFlags = flags,
                  isDirect = true,
                  codeMeta = readCodeMeta(buf, rawBytes.size, codeOff),
                  sourceEntryName = entryName,
                )
            }
          }

          methodIdx = 0
          repeat(virtualMethodsSize) {
            methodIdx += reader.readUleb128()
            val flags = reader.readUleb128()
            val codeOff = reader.readUleb128()
            val ref = methods.getOrNull(methodIdx)
            if (ref != null) {
              virtualMethods +=
                DexEncodedMethod(
                  id = "$entryName::$descriptor#$methodIdx",
                  methodIndex = methodIdx,
                  methodRef = ref,
                  accessFlags = flags,
                  isDirect = false,
                  codeMeta = readCodeMeta(buf, rawBytes.size, codeOff),
                  sourceEntryName = entryName,
                )
            }
          }
        }

        DexClassInfo(
          classIndex = classIdx,
          descriptor = descriptor,
          fullName = fullName,
          simpleName = simpleName,
          packageName = pkgName,
          superclass = superclass,
          interfaces = interfaces,
          sourceFile = sourceFile,
          accessFlags = accessFlags,
          staticFields = staticFields,
          instanceFields = instanceFields,
          directMethods = directMethods,
          virtualMethods = virtualMethods,
          annotationsOffset = annotationsOff,
          staticValuesOffset = staticValuesOff,
          classDataOffset = classDataOff,
          sourceEntryName = entryName,
        )
      }

    val elapsedMs = ((System.nanoTime() - startNanos) / 1_000_000L).coerceAtLeast(1L)

    return DexParsedFile(
      sourceLabel = sourceLabel,
      entryName = entryName,
      rawBytes = rawBytes,
      header = header,
      strings = strings,
      types = types,
      protos = protos,
      fields = fields,
      methods = methods,
      classes = classes,
      mapItems = mapItems,
      methodHandles = methodHandles,
      callSites = callSites,
      hiddenApiSummary = hiddenApiSummary,
      parseDurationMs = elapsedMs,
      parserEngine = "Kotlin DexFile + smali-dexlib2",
    )
  }

  private fun parseMapList(buf: ByteBuffer, fileLen: Int, mapOff: Int): List<DexMapItem> {
    if (mapOff <= 0 || mapOff + 4 > fileLen) return emptyList()
    val count = buf.getInt(mapOff).coerceIn(0, 128)
    val items = mutableListOf<DexMapItem>()
    for (i in 0 until count) {
      val base = mapOff + 4 + i * 12
      if (base + 12 > fileLen) break
      val typeCode = buf.getShort(base).toInt() and 0xFFFF
      val itemSize = buf.getInt(base + 4)
      val itemOff = buf.getInt(base + 8)
      items +=
        DexMapItem(
          typeCode = typeCode,
          typeName = mapItemTypeName(typeCode),
          count = itemSize,
          offset = itemOff,
        )
    }
    return items
  }

  private fun mapItemTypeName(typeCode: Int): String =
    when (typeCode) {
      0x0000 -> "HEADER_ITEM"
      0x0001 -> "STRING_ID_ITEM"
      0x0002 -> "TYPE_ID_ITEM"
      0x0003 -> "PROTO_ID_ITEM"
      0x0004 -> "FIELD_ID_ITEM"
      0x0005 -> "METHOD_ID_ITEM"
      0x0006 -> "CLASS_DEF_ITEM"
      0x0007 -> "CALL_SITE_ID_ITEM"
      0x0008 -> "METHOD_HANDLE_ITEM"
      0x1000 -> "MAP_LIST"
      0x1001 -> "TYPE_LIST"
      0x1002 -> "ANNOTATION_SET_REF_LIST"
      0x1003 -> "ANNOTATION_SET_ITEM"
      0x2000 -> "CLASS_DATA_ITEM"
      0x2001 -> "CODE_ITEM"
      0x2002 -> "STRING_DATA_ITEM"
      0x2003 -> "DEBUG_INFO_ITEM"
      0x2004 -> "ANNOTATION_ITEM"
      0x2005 -> "ENCODED_ARRAY_ITEM"
      0x2006 -> "ANNOTATIONS_DIRECTORY_ITEM"
      0xF000 -> "HIDDENAPI_CLASS_DATA_ITEM"
      else -> "UNKNOWN_0x%04X".format(typeCode)
    }

  private fun parseMethodHandles(
    buf: ByteBuffer,
    fileLen: Int,
    mapItems: List<DexMapItem>,
    fields: List<DexFieldRef>,
    methods: List<DexMethodRef>,
  ): List<DexMethodHandleInfo> {
    val mapEntry = mapItems.firstOrNull { it.typeCode == 0x0008 } ?: return emptyList()
    if (mapEntry.offset <= 0 || mapEntry.count <= 0) return emptyList()
    val capped = mapEntry.count.coerceAtMost(256)
    val result = mutableListOf<DexMethodHandleInfo>()
    for (i in 0 until capped) {
      val base = mapEntry.offset + i * 8
      if (base + 8 > fileLen) break
      val kindCode = buf.getShort(base).toInt() and 0xFFFF
      val memberIdx = buf.getShort(base + 4).toInt() and 0xFFFF
      val (kindLabel, isField) =
        when (kindCode) {
          0x00 -> "STATIC_PUT" to true
          0x01 -> "STATIC_GET" to true
          0x02 -> "INSTANCE_PUT" to true
          0x03 -> "INSTANCE_GET" to true
          0x04 -> "INVOKE_STATIC" to false
          0x05 -> "INVOKE_INSTANCE" to false
          0x06 -> "INVOKE_CONSTRUCTOR" to false
          0x07 -> "INVOKE_DIRECT" to false
          0x08 -> "INVOKE_INTERFACE" to false
          else -> "KIND_$kindCode" to false
        }
      val summary =
        if (isField) {
          fields.getOrNull(memberIdx)?.let { "${descriptorToSimpleName(it.definingClass)}.${it.name}" }
            ?: "field#$memberIdx"
        } else {
          methods.getOrNull(memberIdx)?.let { "${descriptorToSimpleName(it.definingClass)}.${it.name}" }
            ?: "method#$memberIdx"
        }
      result += DexMethodHandleInfo(i, kindCode, kindLabel, memberIdx, summary)
    }
    return result
  }

  private fun parseCallSites(
    buf: ByteBuffer,
    rawBytes: ByteArray,
    mapItems: List<DexMapItem>,
    strings: List<String>,
    types: List<String>,
    protos: List<DexProto>,
    fields: List<DexFieldRef>,
    methods: List<DexMethodRef>,
    methodHandles: List<DexMethodHandleInfo>,
  ): List<DexCallSiteInfo> {
    val mapEntry = mapItems.firstOrNull { it.typeCode == 0x0007 } ?: return emptyList()
    if (mapEntry.offset <= 0 || mapEntry.count <= 0) return emptyList()
    val stubDex =
      DexParsedFile(
        sourceLabel = "",
        entryName = "",
        rawBytes = rawBytes,
        header = DexHeaderInfo("", "", 0L, "", rawBytes.size, 0L, 0, 0, 0, 0, 0, 0),
        strings = strings,
        types = types,
        protos = protos,
        fields = fields,
        methods = methods,
        classes = emptyList(),
        methodHandles = methodHandles,
      )

    val capped = mapEntry.count.coerceAtMost(256)
    val result = mutableListOf<DexCallSiteInfo>()
    for (i in 0 until capped) {
      val ptrOff = mapEntry.offset + i * 4
      if (ptrOff + 4 > rawBytes.size) break
      val arrayOff = buf.getInt(ptrOff)
      if (arrayOff <= 0 || arrayOff >= rawBytes.size) {
        result += DexCallSiteInfo(i, arrayOff, null, null, null, 0)
        continue
      }
      val reader = DexHelper.LebReader(rawBytes, arrayOff)
      val elemCount = reader.readUleb128().coerceIn(0, 16)
      val items = List(elemCount) { DexHelper.readEncodedValuePreview(stubDex, reader, 0) }
      val handleIdx =
        if (elemCount >= 1) items[0].substringAfter('#', "").toIntOrNull() else null
      val methodName = items.getOrNull(1)?.removeSurrounding("\"")
      val methodDesc = items.getOrNull(2)
      result +=
        DexCallSiteInfo(
          index = i,
          encodedArrayOffset = arrayOff,
          bootstrapHandleIndex = handleIdx,
          methodName = methodName,
          methodDescriptor = methodDesc,
          extraArgsCount = (elemCount - 3).coerceAtLeast(0),
        )
    }
    return result
  }

  private fun parseHiddenApiSummary(
    buf: ByteBuffer,
    fileLen: Int,
    mapItems: List<DexMapItem>,
    classDefsSize: Int,
  ): DexHiddenApiSummary? {
    val entry = mapItems.firstOrNull { it.typeCode == 0xF000 } ?: return null
    if (entry.offset <= 0 || entry.offset + 4 > fileLen) return null
    val totalByteSize = buf.getInt(entry.offset)
    var populatedClasses = 0
    val tableStart = entry.offset + 4
    val maxCheck = classDefsSize.coerceAtMost((fileLen - tableStart).coerceAtLeast(0) / 4)
    for (i in 0 until maxCheck) {
      if (buf.getInt(tableStart + i * 4) != 0) populatedClasses++
    }
    return DexHiddenApiSummary(
      sectionOffset = entry.offset,
      totalByteSize = totalByteSize,
      classEntryCount = populatedClasses,
    )
  }

  private fun readCodeMeta(buf: ByteBuffer, fileLen: Int, codeOff: Int): DexCodeMeta? {
    if (codeOff <= 0 || codeOff + 16 > fileLen) return null
    val regs = buf.getShort(codeOff).toInt() and 0xFFFF
    val ins = buf.getShort(codeOff + 2).toInt() and 0xFFFF
    val outs = buf.getShort(codeOff + 4).toInt() and 0xFFFF
    val tries = buf.getShort(codeOff + 6).toInt() and 0xFFFF
    val debugInfoOff = buf.getInt(codeOff + 8)
    val insnsSize = buf.getInt(codeOff + 12)
    return DexCodeMeta(
      registersSize = regs,
      insSize = ins,
      outsSize = outs,
      triesSize = tries,
      insnsCount = insnsSize,
      insnsOffsetBytes = codeOff + 16,
      codeItemOffset = codeOff,
      debugInfoOffset = debugInfoOff,
    )
  }

  private fun readMutf8String(bytes: ByteArray, offset: Int): String {
    if (offset < 0 || offset >= bytes.size) return ""
    val reader = UlebReader(bytes, offset)
    val utf16Len = reader.readUleb128()
    var pos = reader.pos
    val out = StringBuilder(utf16Len.coerceAtMost(256))
    while (pos < bytes.size) {
      val a = bytes[pos++].toInt() and 0xFF
      if (a == 0) break
      if (a < 0x80) {
        out.append(a.toChar())
      } else if ((a and 0xE0) == 0xC0 && pos < bytes.size) {
        val b = bytes[pos++].toInt() and 0xFF
        out.append((((a and 0x1F) shl 6) or (b and 0x3F)).toChar())
      } else if ((a and 0xF0) == 0xE0 && pos + 1 < bytes.size) {
        val b = bytes[pos++].toInt() and 0xFF
        val c = bytes[pos++].toInt() and 0xFF
        out.append((((a and 0x0F) shl 12) or ((b and 0x3F) shl 6) or (c and 0x3F)).toChar())
      } else {
        out.append('?')
      }
    }
    return out.toString()
  }

  private class UlebReader(private val bytes: ByteArray, var pos: Int) {
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
  }
}
