package com.dex2c.studio.nativeengine

import com.dex2c.studio.dex.D2cnBinaryDecoder
import com.dex2c.studio.dex.D2cnHeader
import com.dex2c.studio.dex.DexParser
import com.dex2c.studio.transpiler.FilterRuleEngine
import com.dex2c.studio.transpiler.TranspilerConfig
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DexNativeEngineTest {

  @Test
  fun testEngineFallbackGraceful() {
    // If native library is not on the test host path, isAvailable is false and calling
    // evaluateFilterRules returns null without throwing unhandled exceptions.
    if (!DexNativeEngine.isAvailable) {
      val result = DexNativeEngine.evaluateFilterRules(ByteArray(0), listOf(".*"))
      assertEquals(null, result)
    } else {
      assertNotNull(DexNativeEngine.nativeEngineVersion)
    }
  }

  @Test
  fun testFilterRuleEngineParityWithFallback() {
    val dexBytes = buildMinimalTestDex()
    val parsed = DexParser.parseDex(dexBytes, "test.dex", "classes.dex")

    // 1. Match everything
    val matchAllConfig = TranspilerConfig(filterRulesText = ".*")
    val summaryAll = FilterRuleEngine.evaluateAll(listOf(parsed), matchAllConfig)
    assertEquals(1, summaryAll.matchedMethodIds.size)
    assertEquals(1, summaryAll.matchedClassCount)

    // 2. Exclude the SecretCalc class
    val excludeConfig = TranspilerConfig(filterRulesText = "!^Lcom/example/SecretCalc;.*")
    val summaryExclude = FilterRuleEngine.evaluateAll(listOf(parsed), excludeConfig)
    assertEquals(0, summaryExclude.matchedMethodIds.size)
    assertEquals(0, summaryExclude.matchedClassCount)

    // 3. Match specifically compute method
    val specificConfig = TranspilerConfig(filterRulesText = ".*->compute\\(.*")
    val summarySpecific = FilterRuleEngine.evaluateAll(listOf(parsed), specificConfig)
    assertEquals(1, summarySpecific.matchedMethodIds.size)
    assertEquals(1, summarySpecific.matchedClassCount)
  }

  @Test
  fun testCorruptedDexBufferSafety() {
    // Ensure that malformed or truncated DEX buffers do not crash the engine
    val emptyBytes = ByteArray(0)
    val invalidBytes = byteArrayOf(0x64, 0x65, 0x78, 0x0a, 0x00, 0x00) // truncated header

    val res1 = DexNativeEngine.evaluateFilterRules(emptyBytes, listOf(".*"))
    val res2 = DexNativeEngine.evaluateFilterRules(invalidBytes, listOf(".*"))

    assertEquals(null, res1)
    assertEquals(null, res2)
  }

  @Test
  fun testD2cnBinaryDecoderSynthesizedBuffer() {
    val stringPool = listOf(
      "Lcom/example/TestClass;",
      "com.example.TestClass",
      "com.example",
      "TestClass",
      "Ljava/lang/Object;",
      "TestClass.java",
      "Ljava/lang/Runnable;",
      "myField",
      "I",
      "testMethod",
      "V"
    )

    val out = ByteArrayOutputStream()
    // 1. Header (32 bytes placeholder)
    val headerBytes = ByteArray(32)
    out.write(headerBytes)

    val stringPoolOff = out.size()
    stringPool.forEach { s ->
      val bytes = s.toByteArray(Charsets.UTF_8)
      out.write(bytes.size and 0xFF)
      out.write((bytes.size ushr 8) and 0xFF)
      out.write(bytes)
    }

    val classesOff = out.size()
    val buf = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN)
    // Class record:
    buf.putInt(0) // class_def_idx
    buf.putInt(1) // access_flags (public)
    buf.putInt(0) // descriptor_idx -> "Lcom/example/TestClass;"
    buf.putInt(1) // full_name_idx -> "com.example.TestClass"
    buf.putInt(2) // pkg_name_idx -> "com.example"
    buf.putInt(3) // simple_name_idx -> "TestClass"
    buf.putInt(4) // superclass_idx -> "Ljava/lang/Object;"
    buf.putInt(5) // source_file_idx -> "TestClass.java"
    buf.putInt(0x100) // annotations_off
    buf.putInt(0x200) // static_values_off
    buf.putInt(0x300) // class_data_off

    // Interfaces: 1
    buf.putShort(1)
    buf.putInt(6) // "Ljava/lang/Runnable;"

    // Static fields: 0
    buf.putShort(0)

    // Instance fields: 1
    buf.putShort(1)
    buf.putInt(10) // field_idx
    buf.putInt(2)  // access_flags (private)
    buf.putInt(7)  // name_idx -> "myField"
    buf.putInt(8)  // type_idx -> "I"

    // Direct methods: 1
    buf.putShort(1)
    buf.putInt(20) // method_idx
    buf.putInt(1)  // access_flags (public)
    buf.putInt(9)  // name_idx -> "testMethod"
    buf.putInt(10) // shorty_idx -> "V"
    buf.putInt(10) // ret_idx -> "V"
    buf.putShort(0) // param_count: 0
    buf.put(1.toByte()) // has_code = 1
    buf.putShort(4) // regs
    buf.putShort(1) // ins
    buf.putShort(2) // outs
    buf.putShort(0) // tries
    buf.putInt(3)   // insns_count
    buf.putInt(0x400) // insns_offset_bytes
    buf.putInt(0x380) // code_item_offset
    buf.putInt(0x500) // debug_info_offset

    // Virtual methods: 0
    buf.putShort(0)

    out.write(buf.array(), 0, buf.position())

    val totalBytes = out.toByteArray()
    // Patch header at offset 0
    val headerBuf = ByteBuffer.wrap(totalBytes).order(ByteOrder.LITTLE_ENDIAN)
    headerBuf.putInt(0, D2cnHeader.MAGIC)
    headerBuf.putShort(4, 1.toShort()) // version
    headerBuf.putShort(6, 0.toShort()) // flags
    headerBuf.putInt(8, 32) // header_size
    headerBuf.putInt(12, stringPool.size)
    headerBuf.putInt(16, 1) // class_count
    headerBuf.putInt(20, 1) // method_count
    headerBuf.putInt(24, stringPoolOff)
    headerBuf.putInt(28, classesOff)

    val classes = D2cnBinaryDecoder.decodeClasses(ByteBuffer.wrap(totalBytes))
    assertEquals(1, classes.size)
    val cls = classes.first()
    assertEquals("Lcom/example/TestClass;", cls.descriptor)
    assertEquals("com.example.TestClass", cls.fullName)
    assertEquals("com.example", cls.packageName)
    assertEquals("TestClass", cls.simpleName)
    assertEquals("Ljava/lang/Object;", cls.superclass)
    assertEquals("TestClass.java", cls.sourceFile)
    assertEquals(listOf("Ljava/lang/Runnable;"), cls.interfaces)
    assertEquals(1, cls.instanceFields.size)
    assertEquals("myField", cls.instanceFields[0].fieldRef.name)
    assertEquals("I", cls.instanceFields[0].fieldRef.type)
    assertEquals(1, cls.directMethods.size)
    val m = cls.directMethods.first()
    assertEquals("testMethod", m.methodRef.name)
    assertNotNull(m.codeMeta)
    assertEquals(4, m.codeMeta?.registersSize)
    assertEquals(3, m.codeMeta?.insnsCount)
  }

  private fun buildMinimalTestDex(): ByteArray {
    val strings = listOf("I", "III", "Lcom/example/SecretCalc;", "Ljava/lang/Object;", "compute")
    val strDataStream = ByteArrayOutputStream()
    val strDataOffsets = mutableListOf<Int>()

    val stringDataBase = 0x100
    strings.forEach { s ->
      strDataOffsets += stringDataBase + strDataStream.size()
      val utf8 = s.toByteArray(Charsets.UTF_8)
      strDataStream.write(utf8.size)
      strDataStream.write(utf8)
      strDataStream.write(0)
    }

    val totalSize = stringDataBase + strDataStream.size()
    val buf = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)

    buf.position(0)
    buf.put("dex\n035\u0000".toByteArray(Charsets.US_ASCII))
    buf.putInt(0x20, totalSize)
    buf.putInt(0x24, 0x70)
    buf.putInt(0x28, 0x12345678)
    buf.putInt(0x38, strings.size)
    buf.putInt(0x3C, 0x70)
    buf.putInt(0x40, 2)
    buf.putInt(0x44, 0x84)
    buf.putInt(0x48, 1)
    buf.putInt(0x4C, 0x8C)
    buf.putInt(0x50, 0)
    buf.putInt(0x54, 0)
    buf.putInt(0x58, 1)
    buf.putInt(0x5C, 0x98)
    buf.putInt(0x60, 1)
    buf.putInt(0x64, 0xA0)

    strDataOffsets.forEachIndexed { idx, off -> buf.putInt(0x70 + idx * 4, off) }
    buf.putInt(0x84, 0)
    buf.putInt(0x88, 2)
    buf.putInt(0x8C, 1)
    buf.putInt(0x90, 0)
    buf.putInt(0x94, 0xC0)
    buf.putShort(0x98, 1)
    buf.putShort(0x9A, 0)
    buf.putInt(0x9C, 4)
    buf.putInt(0xA0, 1)
    buf.putInt(0xA4, 1)
    buf.putInt(0xA8, -1)
    buf.putInt(0xAC, 0)
    buf.putInt(0xB0, -1)
    buf.putInt(0xB4, 0)
    buf.putInt(0xB8, 0xE0)
    buf.putInt(0xBC, 0)
    buf.putInt(0xC0, 2)
    buf.putShort(0xC4, 0)
    buf.putShort(0xC6, 0)

    buf.putShort(0xC8, 3)
    buf.putShort(0xCA, 2)
    buf.putShort(0xCC, 0)
    buf.putShort(0xCE, 0)
    buf.putInt(0xD0, 0)
    buf.putInt(0xD4, 4)
    buf.putShort(0xD8, 0x5012.toShort())
    buf.putShort(0xDA, 0x0090.toShort())
    buf.putShort(0xDC, 0x0201.toShort())
    buf.putShort(0xDE, 0x000F.toShort())

    var p = 0xE0
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
    putUleb(0)
    putUleb(9)
    putUleb(0xC8)

    val strBytes = strDataStream.toByteArray()
    buf.position(stringDataBase)
    buf.put(strBytes)

    return buf.array()
  }
}
