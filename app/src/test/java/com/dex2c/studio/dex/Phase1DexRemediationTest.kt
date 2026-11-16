package com.dex2c.studio.dex

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase1DexRemediationTest {

  @Test
  fun testMemoryDataStoreNonLinearWriteAndRead() {
    val store = MemoryDataStore()

    // Non-linear writes: write at 100, then at 0, then at 50
    val out100 = store.outputAt(100)
    out100.write(byteArrayOf(1, 2, 3, 4))
    out100.flush()

    val out0 = store.outputAt(0)
    out0.write(byteArrayOf(10, 20))
    out0.flush()

    val out50 = store.outputAt(50)
    out50.write(byteArrayOf(99))
    out50.flush()

    val bytes = store.toByteArray()
    assertEquals(104, store.size)
    assertEquals(104, bytes.size)

    assertEquals(10.toByte(), bytes[0])
    assertEquals(20.toByte(), bytes[1])
    assertEquals(0.toByte(), bytes[2]) // unwritten gaps are 0

    assertEquals(99.toByte(), bytes[50])

    assertEquals(1.toByte(), bytes[100])
    assertEquals(2.toByte(), bytes[101])
    assertEquals(3.toByte(), bytes[102])
    assertEquals(4.toByte(), bytes[103])

    // Verify readAt
    val inStream = store.readAt(100)
    assertEquals(1, inStream.read())
    assertEquals(2, inStream.read())
  }

  @Test
  fun testUlebReaderDefensiveCapPreventsInfiniteLoop() {
    // Array of 50 bytes all with MSB set (0x80)
    val endlessBytes = ByteArray(50) { 0x80.toByte() }
    val readerClass = Class.forName("com.dex2c.studio.dex.DexParser\$UlebReader")
    val constructor = readerClass.getDeclaredConstructor(ByteArray::class.java, Int::class.javaPrimitiveType)
    constructor.isAccessible = true
    val readerInstance = constructor.newInstance(endlessBytes, 0)
    val readMethod = readerClass.getDeclaredMethod("readUleb128")
    readMethod.isAccessible = true

    val posField = readerClass.getDeclaredField("pos")
    posField.isAccessible = true

    // Must terminate within 5 bytes
    val result = readMethod.invoke(readerInstance) as Int
    val posAfter = posField.getInt(readerInstance)

    assertEquals(5, posAfter)
    assertEquals(0, result)
  }

  @Test
  fun testCompileAndReplaceClassPreservesTargetClassAndUntouchedClasses() {
    val originalDexBytes = buildSyntheticDex039()
    val parsedDex = DexParser.parseDex(originalDexBytes, "test.dex", "classes.dex")
    assertEquals(1, parsedDex.classes.size)
    val originalClass = parsedDex.classes.first()
    assertEquals("Lcom/example/Demo;", originalClass.descriptor)

    // Assemble modified Smali text with an updated constant
    val editedSmali = """
      .class public Lcom/example/Demo;
      .super Ljava/lang/Object;
      .source "Main.kt"

      # direct methods
      .method public static run(I)I
          .registers 2
          .param p0, "x"
          const/4 v0, 0x7
          return v0
      .end method
    """.trimIndent()

    val compileResult = SmaliDexCompiler.compileAndReplaceClass(
      dex = parsedDex,
      targetClassDescriptor = "Lcom/example/Demo;",
      editedSmaliText = editedSmali,
    )

    assertTrue("Compile failed: $compileResult", compileResult is DexCompileResult.Success)
    val success = compileResult as DexCompileResult.Success
    assertTrue(success.durationMs >= 1L)

    // Verify the target class is NOT deleted
    assertEquals(1, success.updatedDex.classes.size)
    val updatedClass = success.updatedDex.classes.first()
    assertEquals("Lcom/example/Demo;", updatedClass.descriptor)
    assertEquals(1, updatedClass.directMethods.size)
    assertEquals("run", updatedClass.directMethods.first().methodRef.name)

    // Disassemble the updated class and verify bytecode reflects const/4 v0, 0x7
    val disassembled = SmaliClassDisassembler.disassembleClass(success.updatedDex, updatedClass)
    assertTrue("Disassembly should contain const/4 v0, 0x7:\n${disassembled.smaliText}", disassembled.smaliText.contains("const/4 v0, 0x7"))
  }

  @Test
  fun testCompileAndReplaceClassReportsSyntaxErrorWithLine() {
    val originalDexBytes = buildSyntheticDex039()
    val parsedDex = DexParser.parseDex(originalDexBytes, "test.dex", "classes.dex")

    val malformedSmali = """
      .class public Lcom/example/Demo;
      .super Ljava/lang/Object;
      .method public badSyntax(
    """.trimIndent()

    val compileResult = SmaliDexCompiler.compileAndReplaceClass(
      dex = parsedDex,
      targetClassDescriptor = "Lcom/example/Demo;",
      editedSmaliText = malformedSmali,
    )

    assertTrue("Expected compile error for malformed Smali, but got: $compileResult", compileResult is DexCompileResult.Error)
    val error = compileResult as DexCompileResult.Error
    assertNotNull(error.message)
    assertTrue("Message was: ${error.message}", error.message.isNotEmpty())
  }

  @Test
  fun testReplaceStringLiteralsInDex() {
    val originalDexBytes = buildSyntheticDex039()
    val parsedDex = DexParser.parseDex(originalDexBytes, "test.dex", "classes.dex")

    // In buildSyntheticDex039, Main.kt is in the strings table
    assertTrue(parsedDex.strings.contains("Main.kt"))

    // Replace "Main.kt" with "Modified.kt"
    val (result, count) = SmaliDexCompiler.replaceStringLiteralsInDex(
      dex = parsedDex,
      searchQuery = "Main.kt",
      replacement = "Modified.kt",
      ignoreCase = false,
    )

    assertTrue("Replace failed: $result", result is DexCompileResult.Success)
    val success = result as DexCompileResult.Success
    assertNotNull(success.updatedDex)

    // Test with a query that has no match
    val (noMatchResult, noMatchCount) = SmaliDexCompiler.replaceStringLiteralsInDex(
      dex = parsedDex,
      searchQuery = "NonExistentString12345",
      replacement = "Replacement",
      ignoreCase = false,
    )
    assertTrue(noMatchResult is DexCompileResult.Success)
    assertEquals(0, noMatchCount)
  }

  @Test
  fun testRepackApkStripsSignaturesAndEnforcesPageAlignment() {
    // Construct synthetic APK containing META-INF signature files, resources.arsc, a .so library, and classes.dex
    val apkOut = ByteArrayOutputStream()
    ZipOutputStream(apkOut).use { zos ->
      // 1. Signature files
      zos.putNextEntry(ZipEntry("META-INF/MANIFEST.MF"))
      zos.write("Manifest-Version: 1.0\r\n".toByteArray())
      zos.closeEntry()

      zos.putNextEntry(ZipEntry("META-INF/CERT.SF"))
      zos.write("Signature-Version: 1.0\r\n".toByteArray())
      zos.closeEntry()

      zos.putNextEntry(ZipEntry("META-INF/CERT.RSA"))
      zos.write(byteArrayOf(0x30, 0x82.toByte(), 0x01))
      zos.closeEntry()

      // 2. resources.arsc
      zos.putNextEntry(ZipEntry("resources.arsc"))
      zos.write("dummy_arsc_content".toByteArray())
      zos.closeEntry()

      // 3. Native .so library
      zos.putNextEntry(ZipEntry("lib/arm64-v8a/libdex2c_test.so"))
      zos.write(ByteArray(256) { 0x7F.toByte() })
      zos.closeEntry()

      // 4. classes.dex
      zos.putNextEntry(ZipEntry("classes.dex"))
      zos.write("old_dex_content".toByteArray())
      zos.closeEntry()
    }

    val originalApkBytes = apkOut.toByteArray()
    val repackedApkOut = ByteArrayOutputStream()

    val patchedDex = "patched_new_dex_binary".toByteArray()
    SmaliDexCompiler.repackApkWithPatchedDexes(
      originalApkStream = ByteArrayInputStream(originalApkBytes),
      patchedDexByEntryName = mapOf("classes.dex" to patchedDex),
      outputStream = repackedApkOut,
    )

    val repackedBytes = repackedApkOut.toByteArray()
    assertTrue(repackedBytes.isNotEmpty())

    // Read back repacked ZIP entries and verify signatures are stripped
    val entryNames = mutableListOf<String>()
    ZipInputStream(ByteArrayInputStream(repackedBytes)).use { zis ->
      var entry = zis.nextEntry
      while (entry != null) {
        entryNames.add(entry.name)
        if (entry.name == "classes.dex") {
          val content = zis.readBytes()
          assertEquals("patched_new_dex_binary", String(content))
        }
        entry = zis.nextEntry
      }
    }

    // Verify all signature entries were stripped
    assertFalse(entryNames.any { SmaliDexCompiler.isSignatureOrManifestEntry(it) })
    assertTrue(entryNames.contains("resources.arsc"))
    assertTrue(entryNames.contains("lib/arm64-v8a/libdex2c_test.so"))
    assertTrue(entryNames.contains("classes.dex"))

    // Verify exact data alignments in the raw repacked ZIP binary
    // Local File Header signature: 0x04034b50
    var offset = 0
    while (offset + 30 <= repackedBytes.size) {
      if (repackedBytes[offset] == 0x50.toByte() &&
        repackedBytes[offset + 1] == 0x4B.toByte() &&
        repackedBytes[offset + 2] == 0x03.toByte() &&
        repackedBytes[offset + 3] == 0x04.toByte()
      ) {
        val bb = ByteBuffer.wrap(repackedBytes, offset, repackedBytes.size - offset).order(ByteOrder.LITTLE_ENDIAN)
        val nameLen = bb.getShort(26).toInt() and 0xFFFF
        val extraLen = bb.getShort(28).toInt() and 0xFFFF
        val nameBytes = ByteArray(nameLen)
        System.arraycopy(repackedBytes, offset + 30, nameBytes, 0, nameLen)
        val entryName = String(nameBytes)

        val dataOffset = offset + 30 + nameLen + extraLen

        if (entryName == "resources.arsc") {
          assertEquals("resources.arsc must be 4-byte aligned (dataOffset=$dataOffset)", 0, dataOffset % 4)
        } else if (entryName == "lib/arm64-v8a/libdex2c_test.so") {
          assertEquals("libdex2c_test.so must be 16KB aligned (dataOffset=$dataOffset)", 0, dataOffset % 16384)
        }

        offset += 30 + nameLen + extraLen
      } else {
        offset++
      }
    }
  }

  private fun buildSyntheticDex039(): ByteArray {
    val strings = listOf(
      "I",
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
    buf.putInt(0x40, 4) // type_ids_size
    buf.putInt(0x44, 0x90) // type_ids_off
    buf.putInt(0x48, 1) // proto_ids_size
    buf.putInt(0x4C, 0xA0) // proto_ids_off
    buf.putInt(0x50, 0) // field_ids_size
    buf.putInt(0x54, 0)
    buf.putInt(0x58, 1) // method_ids_size
    buf.putInt(0x5C, 0xAC) // method_ids_off
    buf.putInt(0x60, 1) // class_defs_size
    buf.putInt(0x64, 0xB4) // class_defs_off

    // string_ids @ 0x70
    strOffsets.forEachIndexed { idx, off -> buf.putInt(0x70 + idx * 4, off) }

    // type_ids @ 0x90
    buf.putInt(0x90, 2) // Keep
    buf.putInt(0x94, 3) // Demo
    buf.putInt(0x98, 4) // Object
    buf.putInt(0x9C, 0) // I

    // proto_ids @ 0xA0 (shorty=0, returnType=3(I), params=0)
    buf.putInt(0xA0, 0)
    buf.putInt(0xA4, 3)
    buf.putInt(0xA8, 0)

    // method_ids @ 0xAC (class=1(Demo), proto=0, name=6(run))
    buf.putShort(0xAC, 1.toShort())
    buf.putShort(0xAE, 0.toShort())
    buf.putInt(0xB0, 6)

    // class_defs @ 0xB4
    buf.putInt(0xB4, 1) // class_idx = 1 (Demo)
    buf.putInt(0xB8, 1) // access_flags = public
    buf.putInt(0xBC, 2) // superclass = 2 (Object)
    buf.putInt(0xC0, 0) // interfaces_off = 0
    buf.putInt(0xC4, 5) // source_file_idx = 5 (Main.kt)
    buf.putInt(0xC8, 0) // annotations_off = 0
    buf.putInt(0xCC, 0xD4) // class_data_off = 0xD4
    buf.putInt(0xD0, 0) // static_values_off = 0

    // class_data @ 0xD4
    // ULEB128: static_fields_size=0, instance_fields_size=0, direct_methods_size=1, virtual_methods_size=0
    buf.put(0xD4, 0.toByte())
    buf.put(0xD5, 0.toByte())
    buf.put(0xD6, 1.toByte())
    buf.put(0xD7, 0.toByte())
    // method: method_idx_diff=0, access_flags=1, code_off=0xE0
    buf.put(0xD8, 0.toByte())
    buf.put(0xD9, 1.toByte())
    buf.put(0xDA, 0xE0.toByte())
    buf.put(0xDB, 0x01.toByte()) // 0xE0 as uleb128 = 0x60, 0x01 -> offset 0xE0

    // code_item @ 0xE0
    buf.putShort(0xE0, 2.toShort()) // registers_size = 2
    buf.putShort(0xE2, 1.toShort()) // ins_size = 1
    buf.putShort(0xE4, 0.toShort()) // outs_size = 0
    buf.putShort(0xE6, 0.toShort()) // tries_size = 0
    buf.putInt(0xE8, 0) // debug_info_off = 0
    buf.putInt(0xEC, 2) // insns_size = 2
    // instructions: const/4 v0, 0 (0x0012), return v0 (0x000F)
    buf.putShort(0xF0, 0x0012.toShort())
    buf.putShort(0xF2, 0x000F.toShort())

    // string data @ 0x260
    System.arraycopy(strDataStream.toByteArray(), 0, buf.array(), strBase, strDataStream.size())

    return buf.array()
  }
}
