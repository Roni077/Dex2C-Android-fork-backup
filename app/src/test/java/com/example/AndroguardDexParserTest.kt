package com.example

import com.example.dex.DexHelper
import com.example.dex.DexParser
import com.example.dex.NativeDexParserBridge
import com.example.dex.RustJniScaffolding
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroguardDexParserTest {

  @Test
  fun parseDex039MapListDebugInfoAnnotationsAndHandles() {
    val bytes = buildSyntheticDex039()
    val parsed = DexParser.parseDex(bytes, "dex039_test.dex", "classes.dex")

    assertEquals("039", parsed.header.version)
    assertTrue(parsed.parseDurationMs >= 1L)
    assertFalse(NativeDexParserBridge.isNativeLoaded)
    assertEquals("Kotlin DexFile/DexHelper", parsed.parserEngine)

    // map_list assertions
    assertTrue(parsed.mapItems.any { it.typeCode == 0x0000 && it.typeName == "HEADER_ITEM" })
    assertTrue(parsed.mapItems.any { it.typeCode == 0x1000 && it.typeName == "MAP_LIST" })
    assertTrue(parsed.mapItems.any { it.typeCode == 0x0008 && it.typeName == "METHOD_HANDLE_ITEM" })
    assertTrue(parsed.mapItems.any { it.typeCode == 0x0007 && it.typeName == "CALL_SITE_ID_ITEM" })
    assertTrue(parsed.mapItems.any { it.typeCode == 0x2003 && it.typeName == "DEBUG_INFO_ITEM" })
    assertTrue(parsed.mapItems.any { it.typeCode == 0x2006 && it.typeName == "ANNOTATIONS_DIRECTORY_ITEM" })
    assertTrue(parsed.mapItems.any { it.typeCode == 0xF000 && it.typeName == "HIDDENAPI_CLASS_DATA_ITEM" })

    // DEX 038-041 method handle & call site & hiddenapi assertions
    assertEquals(1, parsed.methodHandles.size)
    assertEquals("INVOKE_STATIC", parsed.methodHandles.first().kindLabel)
    assertEquals(1, parsed.callSites.size)
    assertEquals("run", parsed.callSites.first().methodName)
    assertNotNull(parsed.hiddenApiSummary)
    assertEquals(1, parsed.hiddenApiSummary?.classEntryCount)

    // DexHelper structural inspection
    val cls = DexHelper.getClasses(parsed).first()
    val method = cls.directMethods.first()
    val meta = DexHelper.inspectMethodMetadata(parsed, cls, method)

    assertEquals(listOf("x"), meta.parameterNames)
    assertTrue(meta.debugLines.any { it.lineNumber == 42 })
    assertEquals(1, meta.methodAnnotations.size)
    assertEquals("RUNTIME", meta.methodAnnotations.first().visibilityLabel)
    assertEquals("Landroidx/annotation/Keep;", meta.methodAnnotations.first().typeDescriptor)
    assertEquals("Main.kt", meta.sourceFile)
  }

  @Test
  fun rustJniScaffoldingContainsAndroguardDexParserBindings() {
    val files = RustJniScaffolding.generateBridgeFiles()
    assertEquals(3, files.size)
    val cargo = files.first { it.fileName == "Cargo.toml" }.content
    val libRs = files.first { it.fileName == "lib.rs" }.content
    val buildSh = files.first { it.fileName == "build_jni.sh" }.content

    assertTrue(cargo.contains("https://github.com/androguard/dex-parser.git"))
    assertTrue(libRs.contains("Java_com_example_dex_NativeDexParserBridge_nativeInspectHeaderJson"))
    assertTrue(libRs.contains("DexFile::from_bytes"))
    assertTrue(libRs.contains("DexHelper::new"))
    assertTrue(buildSh.contains("cargo ndk"))
  }

  private fun buildSyntheticDex039(): ByteArray {
    // Strings: 0=II, 1=Landroidx/annotation/Keep;, 2=Lcom/example/Demo;, 3=Ljava/lang/Object;, 4=Main.kt, 5=run, 6=x
    val strings =
      listOf(
        "II",
        "Landroidx/annotation/Keep;",
        "Lcom/example/Demo;",
        "Ljava/lang/Object;",
        "Main.kt",
        "run",
        "x",
      )
    val strDataStream = ByteArrayOutputStream()
    val strOffsets = mutableListOf<Int>()
    val strBase = 0x260
    strings.forEach { s ->
      strOffsets += strBase + strDataStream.size()
      val utf8 = s.toByteArray(Charsets.UTF_8)
      strDataStream.write(utf8.size)
      strDataStream.write(utf8)
      strDataStream.write(0)
    }

    val totalSize = 0x400
    val buf = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)

    // Header (dex 039)
    buf.position(0)
    buf.put("dex\n039\u0000".toByteArray(Charsets.US_ASCII))
    buf.putInt(0x20, totalSize)
    buf.putInt(0x24, 0x70)
    buf.putInt(0x28, 0x12345678)
    buf.putInt(0x34, 0x1A0) // map_off
    buf.putInt(0x38, strings.size)
    buf.putInt(0x3C, 0x70) // string_ids_off
    buf.putInt(0x40, 4) // type_ids_size (0=I? wait types use string idx)
    buf.putInt(0x44, 0x8C)
    buf.putInt(0x48, 1) // proto_ids_size
    buf.putInt(0x4C, 0x9C)
    buf.putInt(0x50, 0) // field_ids_size
    buf.putInt(0x54, 0)
    buf.putInt(0x58, 1) // method_ids_size
    buf.putInt(0x5C, 0xA8)
    buf.putInt(0x60, 1) // class_defs_size
    buf.putInt(0x64, 0xB0)

    // string_ids @ 0x70
    strOffsets.forEachIndexed { idx, off -> buf.putInt(0x70 + idx * 4, off) }

    // type_ids @ 0x8C: 0->Keep(1), 1->Demo(2), 2->Object(3), 3->I? Wait let's use str 0="II" for int? Let's make type 3 point to str 0, doesn't matter for descriptor
    buf.putInt(0x8C, 1) // Keep
    buf.putInt(0x90, 2) // Demo
    buf.putInt(0x94, 3) // Object
    buf.putInt(0x98, 0) // II (used as return/param)

    // proto_ids @ 0x9C: shorty=0 ("II"), return=3, paramsOff=0xD0
    buf.putInt(0x9C, 0)
    buf.putInt(0xA0, 3)
    buf.putInt(0xA4, 0xD0)

    // type_list @ 0xD0: 1 param = type 3
    buf.putInt(0xD0, 1)
    buf.putShort(0xD4, 3)

    // method_ids @ 0xA8: class=1(Demo), proto=0, name=5("run")
    buf.putShort(0xA8, 1)
    buf.putShort(0xAA, 0)
    buf.putInt(0xAC, 5)

    // class_defs @ 0xB0: class=1, flags=1, super=2, interfaces=0, sourceFile=4, annotations=0x150, classData=0x220, staticValues=0
    buf.putInt(0xB0, 1)
    buf.putInt(0xB4, 1)
    buf.putInt(0xB8, 2)
    buf.putInt(0xBC, 0)
    buf.putInt(0xC0, 4)
    buf.putInt(0xC4, 0x150) // annotations_off
    buf.putInt(0xC8, 0x220) // class_data_off
    buf.putInt(0xCC, 0)

    // code_item @ 0xE0: regs=2, ins=1, outs=0, tries=0, debug_info_off=0x100, insns_size=2 (return v0)
    buf.putShort(0xE0, 2)
    buf.putShort(0xE2, 1)
    buf.putShort(0xE4, 0)
    buf.putShort(0xE6, 0)
    buf.putInt(0xE8, 0x100) // debug_info_off
    buf.putInt(0xEC, 2)
    buf.putShort(0xF0, 0x0012.toShort()) // const/4 v0, 0
    buf.putShort(0xF2, 0x000F.toShort()) // return v0

    // debug_info_item @ 0x100: line_start=42, params_size=1, param[0]="x" (str 6 -> uleb128p1 = 7), special opcode 0x0A (line+= -4 + 0 -> wait special opcode 0x0E = 0x0A + 4 => line+=0, addr+=0), DBG_END_SEQUENCE=0x00
    var dp = 0x100
    buf.put(dp++, 42.toByte()) // line_start = 42
    buf.put(dp++, 1.toByte()) // parameters_size = 1
    buf.put(dp++, 7.toByte()) // uleb128p1(6) = 7 ("x")
    buf.put(dp++, 0x0E.toByte()) // special opcode: addr+=0, line+=0 -> emits (0x0000, 42)
    buf.put(dp, 0x00.toByte()) // DBG_END_SEQUENCE

    // method_handle_item @ 0x120: kind=4 (INVOKE_STATIC), member=0 ("run")
    buf.putShort(0x120, 4)
    buf.putShort(0x122, 0)
    buf.putShort(0x124, 0)
    buf.putShort(0x126, 0)

    // call_site_id_item @ 0x130 -> encoded_array @ 0x138
    buf.putInt(0x130, 0x138)
    // encoded_array @ 0x138: size=3; [0]=METHOD_HANDLE(0x16, idx=0); [1]=STRING(0x17, idx=5="run"); [2]=METHOD_TYPE(0x15, idx=0)
    var cp = 0x138
    buf.put(cp++, 3.toByte())
    buf.put(cp++, 0x16.toByte()) // VALUE_METHOD_HANDLE (1 byte)
    buf.put(cp++, 0x00.toByte())
    buf.put(cp++, 0x17.toByte()) // VALUE_STRING (1 byte)
    buf.put(cp++, 5.toByte())
    buf.put(cp++, 0x15.toByte()) // VALUE_METHOD_TYPE (1 byte)
    buf.put(cp, 0x00.toByte())

    // annotations_directory_item @ 0x150: classOff=0, fields=0, methods=1, params=0; method[0]=(0, 0x170)
    buf.putInt(0x150, 0)
    buf.putInt(0x154, 0)
    buf.putInt(0x158, 1)
    buf.putInt(0x15C, 0)
    buf.putInt(0x160, 0) // method_idx 0
    buf.putInt(0x164, 0x170) // annotation_set_off

    // annotation_set_item @ 0x170: count=1, entry[0]=0x180
    buf.putInt(0x170, 1)
    buf.putInt(0x174, 0x180)

    // annotation_item @ 0x180: visibility=1 (RUNTIME), type_idx=0 (Keep), size=0
    buf.put(0x180, 1.toByte())
    buf.put(0x181, 0.toByte())
    buf.put(0x182, 0.toByte())

    // hiddenapi_class_data_item @ 0x190: size=12, class0_off=8
    buf.putInt(0x190, 12)
    buf.putInt(0x194, 8)

    // map_list @ 0x1A0: 7 items
    val mapEntries =
      listOf(
        Triple(0x0000, 1, 0x000),
        Triple(0x0007, 1, 0x130),
        Triple(0x0008, 1, 0x120),
        Triple(0x1000, 7, 0x1A0),
        Triple(0x2003, 1, 0x100),
        Triple(0x2006, 1, 0x150),
        Triple(0xF000, 1, 0x190),
      )
    buf.putInt(0x1A0, mapEntries.size)
    mapEntries.forEachIndexed { idx, (type, size, off) ->
      val mp = 0x1A4 + idx * 12
      buf.putShort(mp, type.toShort())
      buf.putShort(mp + 2, 0)
      buf.putInt(mp + 4, size)
      buf.putInt(mp + 8, off)
    }

    // class_data_item @ 0x220: staticFields=0, instFields=0, directMethods=1, virtualMethods=0
    var p = 0x220
    fun putUleb(v: Int) {
      var x = v
      do {
        var b = x and 0x7F
        x = x ushr 7
        if (x != 0) b = b or 0x80
        buf.put(p++, b.toByte())
      } while (x != 0)
    }
    putUleb(0)
    putUleb(0)
    putUleb(1)
    putUleb(0)
    putUleb(0) // method_idx 0
    putUleb(9) // public static
    putUleb(0xE0) // code_off = 0xE0

    buf.position(strBase)
    buf.put(strDataStream.toByteArray())

    return buf.array()
  }
}
