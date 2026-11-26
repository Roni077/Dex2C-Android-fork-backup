package com.dex2c.studio.dex

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * High-performance binary decoder for the packed native DEX buffer ('D2CN').
 * Operates directly on off-heap Direct ByteBuffers from JNI with zero unnecessary
 * intermediate collections or duplicate string allocations.
 */
object D2cnBinaryDecoder {

  private val PRIMITIVE_DESCRIPTOR_MAP = arrayOfNulls<String>(128).apply {
    this['V'.code] = "V"
    this['Z'.code] = "Z"
    this['B'.code] = "B"
    this['S'.code] = "S"
    this['C'.code] = "C"
    this['I'.code] = "I"
    this['J'.code] = "J"
    this['F'.code] = "F"
    this['D'.code] = "D"
  }

  /**
   * Decodes class definitions from the packed D2CN direct [buffer].
   * Returns empty list or throws IllegalArgumentException on corrupted data.
   */
  fun decodeClasses(
    buffer: ByteBuffer,
    sourceEntryName: String = "classes.dex",
  ): List<DexClassInfo> {
    val buf = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
    buf.position(0)

    val header = decodeHeader(buf)
    if (header.classCount <= 0) return emptyList()

    // 1. Unpack String Pool with zero duplicate allocations
    buf.position(header.stringPoolOffset)
    val stringPool = unpackStringPool(buf, header.stringCount)

    // 2. Unpack Class Definitions
    buf.position(header.classesOffset)
    val classes = ArrayList<DexClassInfo>(header.classCount)
    val protoCache = HashMap<Long, DexProto>(128)

    for (cIdx in 0 until header.classCount) {
      val classIndex = buf.getInt()
      val accessFlags = buf.getInt()
      val descriptorIdx = buf.getInt()
      val fullNameIdx = buf.getInt()
      val pkgNameIdx = buf.getInt()
      val simpleNameIdx = buf.getInt()
      val superIdx = buf.getInt()
      val sourceFileIdx = buf.getInt()
      val annotationsOff = buf.getInt()
      val staticValuesOff = buf.getInt()
      val classDataOff = buf.getInt()

      val descriptor = stringPool.getOrElse(descriptorIdx) { "Lunknown/Class$cIdx;" }
      val fullName: String
      val packageName: String
      val simpleName: String

      if (fullNameIdx >= 0 && pkgNameIdx >= 0 && simpleNameIdx >= 0 &&
          fullNameIdx < stringPool.size && pkgNameIdx < stringPool.size && simpleNameIdx < stringPool.size) {
        fullName = stringPool[fullNameIdx]
        packageName = stringPool[pkgNameIdx]
        simpleName = stringPool[simpleNameIdx]
      } else {
        val parsed = parseClassDescriptor(descriptor)
        fullName = parsed.fullName
        packageName = parsed.packageName
        simpleName = parsed.simpleName
      }

      val superclass = if (superIdx in 0 until stringPool.size) stringPool[superIdx] else null
      val sourceFile = if (sourceFileIdx in 0 until stringPool.size) stringPool[sourceFileIdx] else null

      // Interfaces
      val ifaceCount = buf.getShort().toInt() and 0xFFFF
      val interfaces =
        if (ifaceCount == 0) {
          emptyList()
        } else {
          val list = ArrayList<String>(ifaceCount)
          for (i in 0 until ifaceCount) {
            val tIdx = buf.getInt()
            list.add(stringPool.getOrElse(tIdx) { "Ljava/lang/Object;" })
          }
          list
        }

      // Static Fields
      val staticFieldsCount = buf.getShort().toInt() and 0xFFFF
      val staticFields =
        if (staticFieldsCount == 0) {
          emptyList()
        } else {
          val list = ArrayList<DexEncodedField>(staticFieldsCount)
          for (f in 0 until staticFieldsCount) {
            val fIdx = buf.getInt()
            val fFlags = buf.getInt()
            val nameIdx = buf.getInt()
            val typeIdx = buf.getInt()
            val ref = DexFieldRef(
              definingClass = descriptor,
              type = stringPool.getOrElse(typeIdx) { "Ljava/lang/Object;" },
              name = stringPool.getOrElse(nameIdx) { "field_$fIdx" },
            )
            list.add(DexEncodedField(fIdx, ref, fFlags, isStatic = true))
          }
          list
        }

      // Instance Fields
      val instanceFieldsCount = buf.getShort().toInt() and 0xFFFF
      val instanceFields =
        if (instanceFieldsCount == 0) {
          emptyList()
        } else {
          val list = ArrayList<DexEncodedField>(instanceFieldsCount)
          for (f in 0 until instanceFieldsCount) {
            val fIdx = buf.getInt()
            val fFlags = buf.getInt()
            val nameIdx = buf.getInt()
            val typeIdx = buf.getInt()
            val ref = DexFieldRef(
              definingClass = descriptor,
              type = stringPool.getOrElse(typeIdx) { "Ljava/lang/Object;" },
              name = stringPool.getOrElse(nameIdx) { "field_$fIdx" },
            )
            list.add(DexEncodedField(fIdx, ref, fFlags, isStatic = false))
          }
          list
        }

      // Direct Methods
      val directMethodsCount = buf.getShort().toInt() and 0xFFFF
      val directMethods =
        if (directMethodsCount == 0) {
          emptyList()
        } else {
          val list = ArrayList<DexEncodedMethod>(directMethodsCount)
          for (m in 0 until directMethodsCount) {
            list.add(
              decodeMethodRecord(
                buf = buf,
                stringPool = stringPool,
                protoCache = protoCache,
                classDescriptor = descriptor,
                sourceEntryName = sourceEntryName,
                isDirect = true,
              )
            )
          }
          list
        }

      // Virtual Methods
      val virtualMethodsCount = buf.getShort().toInt() and 0xFFFF
      val virtualMethods =
        if (virtualMethodsCount == 0) {
          emptyList()
        } else {
          val list = ArrayList<DexEncodedMethod>(virtualMethodsCount)
          for (m in 0 until virtualMethodsCount) {
            list.add(
              decodeMethodRecord(
                buf = buf,
                stringPool = stringPool,
                protoCache = protoCache,
                classDescriptor = descriptor,
                sourceEntryName = sourceEntryName,
                isDirect = false,
              )
            )
          }
          list
        }

      classes.add(
        DexClassInfo(
          classIndex = classIndex,
          descriptor = descriptor,
          fullName = fullName,
          simpleName = simpleName,
          packageName = packageName,
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
          sourceEntryName = sourceEntryName,
        )
      )
    }

    return classes
  }

  fun decodeHeader(buf: ByteBuffer): D2cnHeader {
    val magic = buf.getInt()
    if (magic != D2cnHeader.MAGIC) {
      throw IllegalArgumentException("Invalid D2CN magic header: 0x%08X".format(magic))
    }
    val version = buf.getShort()
    if (version > D2cnHeader.CURRENT_VERSION) {
      throw IllegalArgumentException("Unsupported D2CN version $version (expected <= ${D2cnHeader.CURRENT_VERSION})")
    }
    val flags = buf.getShort()
    val headerSize = buf.getInt()
    val stringCount = buf.getInt()
    val classCount = buf.getInt()
    val methodCount = buf.getInt()
    val stringPoolOff = buf.getInt()
    val classesOff = buf.getInt()

    return D2cnHeader(
      magic = magic,
      version = version,
      flags = flags,
      headerSize = headerSize,
      stringCount = stringCount,
      classCount = classCount,
      methodCount = methodCount,
      stringPoolOffset = stringPoolOff,
      classesOffset = classesOff,
    )
  }

  private fun unpackStringPool(buf: ByteBuffer, stringCount: Int): Array<String> {
    val pool = arrayOfNulls<String>(stringCount)
    var scratch = ByteArray(1024)

    for (i in 0 until stringCount) {
      val len = buf.getShort().toInt() and 0xFFFF
      if (len == 0) {
        pool[i] = ""
        continue
      }
      if (len == 1) {
        val b = buf.get().toInt() and 0xFF
        val cached = if (b < 128) PRIMITIVE_DESCRIPTOR_MAP[b] else null
        pool[i] = cached ?: b.toChar().toString()
        continue
      }

      if (len > scratch.size) {
        scratch = ByteArray(len.coerceAtLeast(scratch.size * 2))
      }
      buf.get(scratch, 0, len)
      val s = String(scratch, 0, len, Charsets.UTF_8)
      pool[i] = when (s) {
        "<init>" -> "<init>"
        "<clinit>" -> "<clinit>"
        "Ljava/lang/Object;" -> "Ljava/lang/Object;"
        "Ljava/lang/String;" -> "Ljava/lang/String;"
        "Ljava/lang/Class;" -> "Ljava/lang/Class;"
        else -> s
      }
    }

    @Suppress("UNCHECKED_CAST")
    return pool as Array<String>
  }

  private fun decodeMethodRecord(
    buf: ByteBuffer,
    stringPool: Array<String>,
    protoCache: MutableMap<Long, DexProto>,
    classDescriptor: String,
    sourceEntryName: String,
    isDirect: Boolean,
  ): DexEncodedMethod {
    val methodIndex = buf.getInt()
    val accessFlags = buf.getInt()
    val nameIdx = buf.getInt()
    val shortyIdx = buf.getInt()
    val retTypeIdx = buf.getInt()
    val paramCount = buf.getShort().toInt() and 0xFFFF

    val shorty = stringPool.getOrElse(shortyIdx) { "V" }
    val returnType = stringPool.getOrElse(retTypeIdx) { "V" }

    // Deduplicate Prototypes
    val protoKey = (shortyIdx.toLong() shl 32) or (retTypeIdx.toLong() and 0xFFFFFFFFL)
    var proto = if (paramCount == 0) protoCache[protoKey] else null

    if (proto == null) {
      val params =
        if (paramCount == 0) {
          emptyList()
        } else if (paramCount == 1) {
          val pTypeIdx = buf.getInt()
          java.util.Collections.singletonList(stringPool.getOrElse(pTypeIdx) { "Ljava/lang/Object;" })
        } else {
          val list = ArrayList<String>(paramCount)
          for (p in 0 until paramCount) {
            val pTypeIdx = buf.getInt()
            list.add(stringPool.getOrElse(pTypeIdx) { "Ljava/lang/Object;" })
          }
          list
        }

      proto = DexProto(shorty = shorty, returnType = returnType, parameterTypes = params)
      if (paramCount == 0) {
        protoCache[protoKey] = proto
      }
    } else {
      if (paramCount > 0) {
        buf.position(buf.position() + paramCount * 4)
      }
    }

    val methodName = stringPool.getOrElse(nameIdx) { "method_$methodIndex" }
    val methodRef = DexMethodRef(
      definingClass = classDescriptor,
      proto = proto,
      name = methodName,
    )

    val hasCode = buf.get().toInt() != 0
    val codeMeta =
      if (hasCode) {
        val regs = buf.getShort().toInt() and 0xFFFF
        val ins = buf.getShort().toInt() and 0xFFFF
        val outs = buf.getShort().toInt() and 0xFFFF
        val tries = buf.getShort().toInt() and 0xFFFF
        val insnsCount = buf.getInt()
        val insnsOffBytes = buf.getInt()
        val codeItemOff = buf.getInt()
        val debugInfoOff = buf.getInt()
        DexCodeMeta(
          registersSize = regs,
          insSize = ins,
          outsSize = outs,
          triesSize = tries,
          insnsCount = insnsCount,
          insnsOffsetBytes = insnsOffBytes,
          codeItemOffset = codeItemOff,
          debugInfoOffset = debugInfoOff,
        )
      } else {
        null
      }

    return DexEncodedMethod(
      id = "$sourceEntryName::$classDescriptor#$methodIndex",
      methodIndex = methodIndex,
      methodRef = methodRef,
      accessFlags = accessFlags,
      isDirect = isDirect,
      codeMeta = codeMeta,
      sourceEntryName = sourceEntryName,
    )
  }
}
