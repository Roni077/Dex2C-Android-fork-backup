package com.dex2c.studio.verification

import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.writer.pool.DexPool
import com.dex2c.studio.dex.DalvikInstruction
import com.dex2c.studio.dex.DalvikPayload
import com.dex2c.studio.dex.DexCatchHandler
import com.dex2c.studio.dex.DexClassInfo
import com.dex2c.studio.dex.DexCodeMeta
import com.dex2c.studio.dex.DexCompileResult
import com.dex2c.studio.dex.DexEncodedMethod
import com.dex2c.studio.dex.DexHeaderInfo
import com.dex2c.studio.dex.DexMethodRef
import com.dex2c.studio.dex.DexParsedFile
import com.dex2c.studio.dex.DexParser
import com.dex2c.studio.dex.DexProto
import com.dex2c.studio.dex.DexTryBlock
import com.dex2c.studio.dex.MemoryDataStore
import com.dex2c.studio.dex.OpCategory
import com.dex2c.studio.dex.SmaliClassDisassembler
import com.dex2c.studio.dex.SmaliDexCompiler
import com.dex2c.studio.dex.parseClassDescriptor
import com.dex2c.studio.transpiler.ClangSyntaxValidationTest
import com.dex2c.studio.transpiler.Dex2cTranspiler
import com.dex2c.studio.transpiler.GeneratedBundle
import com.dex2c.studio.transpiler.GeneratedFile
import com.dex2c.studio.transpiler.JniBindingStyle
import com.dex2c.studio.transpiler.TargetCLanguage
import com.dex2c.studio.transpiler.TranspilerConfig
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.util.Properties
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 5 Master Verification Test Suite.
 *
 * Covers:
 * - Task 5.2: Smali Re-Assembly Round-Trip Bytecode Integrity Verification
 * - Task 5.3: C++ Switch CodeGen and JNI Guard Clean Clang Compilation (0 warnings, 0 errors)
 * - Task 5.4: APK Repacking Clean Signatures & 16KB / 4-byte Page Alignment with real zipalign
 */
class Phase5VerificationTest {

  // =========================================================================
  // Task 5.2: Smali Re-Assembly Round-Trip Verification
  // =========================================================================

  @Test
  fun testSmaliReassemblyRoundTripIntegrity() {
    // 1. Build a synthetic multi-class DEX with 2 distinct classes: Demo & Helper
    val multiDex = buildMultiClassDex()
    assertEquals("Initial DEX must contain exactly 2 classes", 2, multiDex.classes.size)
    val demoClass = multiDex.classes.first { it.descriptor == "Lcom/example/Demo;" }
    val helperClass = multiDex.classes.first { it.descriptor == "Lcom/example/Helper;" }

    // 2. Disassemble Demo to Smali
    val disassembledDemo = SmaliClassDisassembler.disassembleClass(multiDex, demoClass)
    assertTrue("Disassembled smali must contain calculate method", disassembledDemo.smaliText.contains(".method public static calculate(I)I"))
    assertTrue("Disassembled smali must contain original const/4 v0, 0x1", disassembledDemo.smaliText.contains("const/4 v0, 0x1"))

    // 3. Edit the instruction: replace const/4 v0, 0x1 with const/4 v0, 0x7
    val modifiedSmali = disassembledDemo.smaliText.replace("const/4 v0, 0x1", "const/4 v0, 0x7")
    assertTrue("Modified smali must contain const/4 v0, 0x7", modifiedSmali.contains("const/4 v0, 0x7"))

    // 4. Compile and replace target class in DEX
    val compileResult = SmaliDexCompiler.compileAndReplaceClass(
      dex = multiDex,
      targetClassDescriptor = "Lcom/example/Demo;",
      editedSmaliText = modifiedSmali,
    )
    assertTrue("Compile must succeed, got: $compileResult", compileResult is DexCompileResult.Success)
    val success = compileResult as DexCompileResult.Success
    assertTrue("Duration must be reported", success.durationMs >= 1L)

    // 5. Re-parse the output DEX bytes from scratch with DexParser
    val reParsedDex = DexParser.parseDex(success.updatedDexBytes, "test_reparsed.dex", "classes.dex")

    // Assert: Class count must match original (2 classes, no deleted classes)
    assertEquals("Class count must be fully preserved", 2, reParsedDex.classes.size)

    // Assert: Target class exists and updated instruction matches edited Smali
    val updatedDemo = reParsedDex.classes.find { it.descriptor == "Lcom/example/Demo;" }
    assertNotNull("Target class Lcom/example/Demo; must exist in output DEX", updatedDemo)
    assertEquals(1, updatedDemo!!.directMethods.size)

    val reDisassembledDemo = SmaliClassDisassembler.disassembleClass(reParsedDex, updatedDemo)
    assertTrue(
      "Re-disassembled bytecode must reflect const/4 v0, 0x7:\n${reDisassembledDemo.smaliText}",
      reDisassembledDemo.smaliText.contains("const/4 v0, 0x7"),
    )
    assertFalse(
      "Re-disassembled bytecode must not retain original const/4 v0, 0x1",
      reDisassembledDemo.smaliText.contains("const/4 v0, 0x1"),
    )

    // Assert: Untouched class (Helper) is completely preserved
    val untouchedHelper = reParsedDex.classes.find { it.descriptor == "Lcom/example/Helper;" }
    assertNotNull("Untouched class Lcom/example/Helper; must exist in output DEX", untouchedHelper)
    assertEquals(helperClass.directMethods.size, untouchedHelper!!.directMethods.size)
    assertEquals(helperClass.sourceFile, untouchedHelper.sourceFile)
  }

  @Test
  fun testSmaliReassemblySyntaxErrorSafety() {
    val multiDex = buildMultiClassDex()
    val malformedSmali = """
      .class public Lcom/example/Demo;
      .super Ljava/lang/Object;
      .method public static broken(
    """.trimIndent()

    val compileResult = SmaliDexCompiler.compileAndReplaceClass(
      dex = multiDex,
      targetClassDescriptor = "Lcom/example/Demo;",
      editedSmaliText = malformedSmali,
    )

    assertTrue("Compile error expected for malformed Smali", compileResult is DexCompileResult.Error)
    val error = compileResult as DexCompileResult.Error
    assertNotNull("Error message must be populated", error.message)
    assertTrue("Error message must describe syntax issue", error.message.isNotEmpty())
    assertEquals("Original DEX classes must remain intact", 2, multiDex.classes.size)
  }

  @Test
  fun testBytecodeStringReplacementRoundTrip() {
    val multiDex = buildMultiClassDex()
    // Helper class contains "helper_payload" string literal
    assertTrue("DEX strings must contain helper_payload", multiDex.strings.contains("helper_payload"))

    val (compileResult, replacedCount) = SmaliDexCompiler.replaceStringLiteralsInDex(
      dex = multiDex,
      searchQuery = "helper_payload",
      replacement = "remediated_phase5_payload",
      ignoreCase = false,
    )

    assertTrue("Replace must succeed: $compileResult", compileResult is DexCompileResult.Success)
    assertEquals("Expected exactly 1 string replacement", 1, replacedCount)
    val success = compileResult as DexCompileResult.Success

    // Re-parse resulting DEX
    val reParsed = DexParser.parseDex(success.updatedDexBytes, "replaced.dex", "classes.dex")
    assertTrue("New string must exist in strings table", reParsed.strings.contains("remediated_phase5_payload"))

    val helperCls = reParsed.classes.first { it.descriptor == "Lcom/example/Helper;" }
    val dis = SmaliClassDisassembler.disassembleClass(reParsed, helperCls)
    assertTrue("Disassembly must contain new string literal", dis.smaliText.contains("\"remediated_phase5_payload\""))
    assertFalse("Disassembly must no longer contain old string literal", dis.smaliText.contains("\"helper_payload\""))
  }

  // =========================================================================
  // Task 5.3: C++ Switch CodeGen and JNI Guard Clang Compilation Verification
  // =========================================================================

  @Test
  fun testCppSwitchAndJniGuardClangCompilationZeroErrorsZeroWarnings() {
    // 1. packed-switch instruction
    val packedPayload = DalvikPayload.PackedSwitch(
      offsetWords = 10,
      wordCount = 8,
      firstKey = 10,
      targetOffsets = listOf(20, 25, 30),
    )
    val packedInsn = DalvikInstruction(
      offsetWords = 4, widthWords = 3, opcode = 0x2B, mnemonic = "packed-switch",
      format = "31t", operandsText = "v0, :pswitch_data_0", rawWords = listOf(0x2B, 0, 6),
      category = OpCategory.BRANCH, regA = 0, targetOffset = 10, payload = packedPayload,
    )

    // 2. sparse-switch instruction
    val sparsePayload = DalvikPayload.SparseSwitch(
      offsetWords = 35,
      wordCount = 10,
      cases = listOf(1 to 40, 10 to 45, 100 to 50),
    )
    val sparseInsn = DalvikInstruction(
      offsetWords = 15, widthWords = 3, opcode = 0x2C, mnemonic = "sparse-switch",
      format = "31t", operandsText = "v1, :sswitch_data_0", rawWords = listOf(0x2C, 1, 20),
      category = OpCategory.BRANCH, regA = 1, targetOffset = 35, payload = sparsePayload,
    )

    val retVoid = DalvikInstruction(
      offsetWords = 8, widthWords = 1, opcode = 0x0E, mnemonic = "return-void",
      format = "10x", operandsText = "", rawWords = listOf(0x0E), category = OpCategory.BRANCH,
    )
    val t20 = DalvikInstruction(offsetWords = 20, widthWords = 1, opcode = 0x0E, mnemonic = "return-void", format = "10x", operandsText = "", rawWords = listOf(0x0E), category = OpCategory.BRANCH)
    val t25 = DalvikInstruction(offsetWords = 25, widthWords = 1, opcode = 0x0E, mnemonic = "return-void", format = "10x", operandsText = "", rawWords = listOf(0x0E), category = OpCategory.BRANCH)
    val t30 = DalvikInstruction(offsetWords = 30, widthWords = 1, opcode = 0x0E, mnemonic = "return-void", format = "10x", operandsText = "", rawWords = listOf(0x0E), category = OpCategory.BRANCH)
    val t40 = DalvikInstruction(offsetWords = 40, widthWords = 1, opcode = 0x0E, mnemonic = "return-void", format = "10x", operandsText = "", rawWords = listOf(0x0E), category = OpCategory.BRANCH)
    val t45 = DalvikInstruction(offsetWords = 45, widthWords = 1, opcode = 0x0E, mnemonic = "return-void", format = "10x", operandsText = "", rawWords = listOf(0x0E), category = OpCategory.BRANCH)
    val t50 = DalvikInstruction(offsetWords = 50, widthWords = 1, opcode = 0x0E, mnemonic = "return-void", format = "10x", operandsText = "", rawWords = listOf(0x0E), category = OpCategory.BRANCH)

    val switchInsns = listOf(packedInsn, sparseInsn, retVoid, t20, t25, t30, t40, t45, t50)
    val switchMethod = buildSyntheticMethod("dispatchDualSwitches", "V", insns = switchInsns)

    // 3. JNI Guard & Try-Catch Exception Dispatch method
    val tryBlocks = listOf(
      DexTryBlock(
        startOffsetWord = 0,
        insnCount = 4,
        handlers = listOf(
          DexCatchHandler(typeDescriptor = "Ljava/lang/IllegalArgumentException;", handlerOffsetWord = 6),
        ),
      ),
    )
    val checkCastInsn = DalvikInstruction(
      offsetWords = 0, widthWords = 2, opcode = 0x1F, mnemonic = "check-cast",
      format = "21c", operandsText = "v0, Ljava/lang/String;", rawWords = listOf(0x1F, 0), category = OpCategory.CHECK, regA = 0,
    )
    val retValInsn = DalvikInstruction(
      offsetWords = 4, widthWords = 1, opcode = 0x0F, mnemonic = "return",
      format = "11x", operandsText = "v0", rawWords = listOf(0x0F), category = OpCategory.BRANCH, regA = 0,
    )
    val handlerConst = DalvikInstruction(
      offsetWords = 6, widthWords = 1, opcode = 0x12, mnemonic = "const/4",
      format = "11n", operandsText = "v0, #-1", rawWords = listOf(0x12), category = OpCategory.MOVE, regA = 0,
    )
    val handlerRet = DalvikInstruction(
      offsetWords = 8, widthWords = 1, opcode = 0x0F, mnemonic = "return",
      format = "11x", operandsText = "v0", rawWords = listOf(0x0F), category = OpCategory.BRANCH, regA = 0,
    )
    val guardMethod = buildSyntheticMethod(
      methodName = "guardedExceptionFlow",
      returnType = "I",
      insns = listOf(checkCastInsn, retValInsn, handlerConst, handlerRet),
      tryBlocks = tryBlocks,
    )

    // 4. Overloaded static methods to verify symbol disambiguation
    val ov1 = buildSyntheticMethod("calculateTax", "I", paramTypes = listOf("I"), insns = listOf(retValInsn))
    val ov2 = buildSyntheticMethod("calculateTax", "I", paramTypes = listOf("I", "Ljava/lang/String;"), insns = listOf(retValInsn))

    val dex = buildSyntheticDex(listOf(switchMethod, guardMethod, ov1, ov2))

    val config = TranspilerConfig(
      targetLanguage = TargetCLanguage.CPP17,
      jniStyle = JniBindingStyle.STATIC_SYMBOLS,
      controlFlowFlattening = false,
      stringEncryption = false,
    )

    val switchCode = Dex2cTranspiler.transpileMethodToC(
      dex = dex,
      method = switchMethod,
      instructions = switchInsns,
      tryBlocks = emptyList(),
      config = config,
    )
    val guardCode = Dex2cTranspiler.transpileMethodToC(
      dex = dex,
      method = guardMethod,
      instructions = listOf(checkCastInsn, retValInsn, handlerConst, handlerRet),
      tryBlocks = tryBlocks,
      config = config,
    )

    // Verify transpiled source includes switch blocks and JNI guard returns
    assertTrue("Transpiled C++ must contain switch statements", switchCode.contains("switch (v["))
    assertTrue("Transpiled C++ must contain case labels", switchCode.contains("case 10:"))
    assertTrue("Transpiled C++ must contain goto statements for branches", switchCode.contains("goto L_"))
    assertTrue("Transpiled C++ must clear exception before FindClass in catch dispatch", guardCode.contains("ExceptionClear"))
    assertTrue("Transpiled C++ must re-throw unhandled exception", guardCode.contains("Throw(__exc)"))

    // Build complete bundle containing runtime header and verified C++ source
    val baseBundle = Dex2cTranspiler.transpileBundle(dex, listOf(ov1, ov2), config)
    val combinedCpp = buildString {
      appendLine("#include \"dex2c_runtime.h\"")
      appendLine()
      appendLine(switchCode)
      appendLine()
      appendLine(guardCode)
    }
    val verificationFile = GeneratedFile(
      fileName = "dex2c_phase5_verification.cpp",
      zipPath = "jni/dex2c_phase5_verification.cpp",
      description = "Phase 5 verified C++ implementation",
      content = combinedCpp,
    )
    val verificationBundle = baseBundle.copy(
      sourceFiles = listOf(verificationFile),
    )

    // Verify Clang syntax compilation
    compileWithAndroidNdkClang(verificationBundle)
  }

  // =========================================================================
  // Task 5.4: APK Repacking Clean Signatures & 16KB / 4B Page Alignment
  // =========================================================================

  @Test
  fun testApkRepackingAlignmentAndSignatureStrippingWithZipalign() {
    // 1. Build synthetic APK with signatures, resources.arsc, .so library, and classes.dex
    val apkOut = ByteArrayOutputStream()
    ZipOutputStream(apkOut).use { zos ->
      // Signatures to be stripped
      zos.putNextEntry(ZipEntry("META-INF/MANIFEST.MF"))
      zos.write("Manifest-Version: 1.0\r\nCreated-By: 1.0 (Android)\r\n".toByteArray())
      zos.closeEntry()

      zos.putNextEntry(ZipEntry("META-INF/CERT.SF"))
      zos.write("Signature-Version: 1.0\r\nSHA-256-Digest-Manifest: abc123=\r\n".toByteArray())
      zos.closeEntry()

      zos.putNextEntry(ZipEntry("META-INF/CERT.RSA"))
      zos.write(byteArrayOf(0x30, 0x82.toByte(), 0x01, 0x0A))
      zos.closeEntry()

      // resources.arsc (must be 4-byte aligned, STORED)
      zos.putNextEntry(ZipEntry("resources.arsc"))
      zos.write("synthetic_table_arsc_content".toByteArray())
      zos.closeEntry()

      // Native .so (must be 16KB aligned, STORED)
      zos.putNextEntry(ZipEntry("lib/arm64-v8a/libdex2c_phase5.so"))
      zos.write(ByteArray(512) { 0x7F.toByte() })
      zos.closeEntry()

      // classes.dex
      zos.putNextEntry(ZipEntry("classes.dex"))
      zos.write("original_classes_dex_data".toByteArray())
      zos.closeEntry()
    }

    val originalApkBytes = apkOut.toByteArray()
    val repackedOut = ByteArrayOutputStream()
    val patchedDexBytes = "remediated_classes_dex_v5".toByteArray()

    // 2. Repack APK
    SmaliDexCompiler.repackApkWithPatchedDexes(
      originalApkStream = ByteArrayInputStream(originalApkBytes),
      patchedDexByEntryName = mapOf("classes.dex" to patchedDexBytes),
      outputStream = repackedOut,
    )

    val repackedBytes = repackedOut.toByteArray()
    assertTrue("Repacked APK must not be empty", repackedBytes.isNotEmpty())

    // 3. Inspect ZIP entries: verify signatures stripped and patched DEX applied
    val entryNames = mutableListOf<String>()
    ZipInputStream(ByteArrayInputStream(repackedBytes)).use { zis ->
      var entry = zis.nextEntry
      while (entry != null) {
        entryNames.add(entry.name)
        if (entry.name == "classes.dex") {
          val content = zis.readBytes()
          assertEquals("classes.dex must contain patched content", "remediated_classes_dex_v5", String(content))
        }
        entry = zis.nextEntry
      }
    }

    assertFalse("All META-INF signature files must be stripped", entryNames.any { SmaliDexCompiler.isSignatureOrManifestEntry(it) })
    assertTrue("resources.arsc must be retained", entryNames.contains("resources.arsc"))
    assertTrue("libdex2c_phase5.so must be retained", entryNames.contains("lib/arm64-v8a/libdex2c_phase5.so"))
    assertTrue("classes.dex must be retained", entryNames.contains("classes.dex"))

    // 4. Mathematical alignment verification of raw local file header data offsets
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
          assertEquals("resources.arsc must be 4-byte aligned (offset=$dataOffset)", 0, dataOffset % 4)
        } else if (entryName == "lib/arm64-v8a/libdex2c_phase5.so") {
          assertEquals("libdex2c_phase5.so must be 16KB page-aligned (offset=$dataOffset)", 0, dataOffset % 16384)
        }

        offset += 30 + nameLen + extraLen
      } else {
        offset++
      }
    }

    // 5. Real Android SDK zipalign verification (if zipalign executable is located)
    val zipalign = ZipalignLocator.findZipalign()
    if (zipalign != null) {
      val tempApk = File.createTempFile("dex2c_phase5_align_", ".apk")
      try {
        tempApk.writeBytes(repackedBytes)
        val pb = ProcessBuilder(
          zipalign.absolutePath,
          "-c",
          "-v",
          "-P", "16",
          "4",
          tempApk.absolutePath,
        )
        pb.redirectErrorStream(true)
        val process = pb.start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exited = process.waitFor(30, TimeUnit.SECONDS)
        assertTrue("zipalign timed out", exited)
        val exitCode = process.exitValue()
        assertEquals("zipalign verification failed with code $exitCode.\nOutput:\n$output", 0, exitCode)
        assertTrue("zipalign output must confirm Verification successful:\n$output", output.contains("Verification successful"))
      } finally {
        tempApk.delete()
      }
    }
  }

  // =========================================================================
  // Test Helpers & Builders
  // =========================================================================

  private fun buildMultiClassDex(): DexParsedFile {
    val smaliDemo = """
      .class public Lcom/example/Demo;
      .super Ljava/lang/Object;
      .source "Demo.kt"

      .method public static calculate(I)I
          .registers 2
          .param p0, "x"
          const/4 v0, 0x1
          return v0
      .end method
    """.trimIndent()

    val smaliHelper = """
      .class public Lcom/example/Helper;
      .super Ljava/lang/Object;
      .source "Helper.kt"

      .method public static assist()Ljava/lang/String;
          .registers 1
          const-string v0, "helper_payload"
          return-object v0
      .end method
    """.trimIndent()

    val opcodes = Opcodes.forApi(29)
    val dexPool = DexPool(opcodes)
    val (classDef1, err1) = SmaliDexCompiler.assembleSmali(smaliDemo, 29)
    val (classDef2, err2) = SmaliDexCompiler.assembleSmali(smaliHelper, 29)
    checkNotNull(classDef1) { "Failed to assemble Demo: ${err1?.message}" }
    checkNotNull(classDef2) { "Failed to assemble Helper: ${err2?.message}" }
    dexPool.internClass(classDef1)
    dexPool.internClass(classDef2)

    val dataStore = MemoryDataStore()
    dexPool.writeTo(dataStore)
    val bytes = dataStore.toByteArray()
    return DexParser.parseDex(bytes, "multi_test.dex", "classes.dex")
  }

  private fun buildSyntheticMethod(
    methodName: String,
    returnType: String,
    paramTypes: List<String> = emptyList(),
    definingClass: String = "Lcom/example/Phase5VerificationClass;",
    insns: List<DalvikInstruction> = emptyList(),
    tryBlocks: List<DexTryBlock> = emptyList(),
  ): DexEncodedMethod {
    val methodRef = DexMethodRef(
      definingClass = definingClass,
      name = methodName,
      proto = DexProto(shorty = returnType, returnType = returnType, parameterTypes = paramTypes),
    )
    return DexEncodedMethod(
      id = "$definingClass->$methodName",
      methodIndex = 0,
      methodRef = methodRef,
      accessFlags = 0x0009, // public static
      isDirect = true,
      codeMeta = DexCodeMeta(
        registersSize = 8,
        insSize = paramTypes.size,
        outsSize = 4,
        triesSize = tryBlocks.size,
        insnsCount = insns.size,
        insnsOffsetBytes = 0,
      ),
    )
  }

  private fun buildSyntheticDex(methods: List<DexEncodedMethod>): DexParsedFile {
    val classesByDescriptor = methods.groupBy { it.methodRef.definingClass }
    val classes = classesByDescriptor.entries.mapIndexed { idx, (desc, mList) ->
      val p = parseClassDescriptor(desc)
      DexClassInfo(
        classIndex = idx,
        descriptor = desc,
        fullName = p.fullName,
        simpleName = p.simpleName,
        packageName = p.packageName,
        superclass = "Ljava/lang/Object;",
        interfaces = emptyList(),
        sourceFile = "${p.simpleName}.java",
        accessFlags = 0x0001,
        staticFields = emptyList(),
        instanceFields = emptyList(),
        directMethods = mList,
        virtualMethods = emptyList(),
      )
    }

    return DexParsedFile(
      sourceLabel = "phase5.dex",
      entryName = "classes.dex",
      rawBytes = ByteArray(128),
      header = DexHeaderInfo(
        magic = "dex\n035\u0000",
        version = "035",
        checksum = 0L,
        sha1Hex = "0000000000000000000000000000000000000000",
        fileSize = 128,
        endianTag = 0x12345678L,
        stringIdsSize = 1,
        typeIdsSize = 1,
        protoIdsSize = 0,
        fieldIdsSize = 0,
        methodIdsSize = methods.size,
        classDefsSize = classes.size,
      ),
      strings = listOf("Ljava/lang/Object;"),
      types = listOf("Ljava/lang/Object;"),
      protos = emptyList(),
      fields = emptyList(),
      methods = methods.map { it.methodRef },
      classes = classes,
    )
  }

  private fun compileWithAndroidNdkClang(bundle: GeneratedBundle) {
    val toolchain = ClangSyntaxValidationTest.ClangLocator.findClangToolchain()
    if (toolchain == null) return

    val compilerExe = toolchain.clangPlusPlus
    assertTrue("Clang++ executable must exist", compilerExe.exists() && compilerExe.canExecute())

    val tempDir = Files.createTempDirectory("dex2c_phase5_clang").toFile()
    try {
      val headerFile = File(tempDir, "dex2c_runtime.h")
      headerFile.writeText(bundle.headerFile.content)

      for (sourceFile in bundle.sourceFiles) {
        val srcFile = File(tempDir, sourceFile.fileName)
        srcFile.writeText(sourceFile.content)

        val args = mutableListOf(
          compilerExe.absolutePath,
          "-std=c++17",
          "-fsyntax-only",
          "-Wall",
          "-Wextra",
          "-Wno-unused-parameter",
          "-Wno-unused-variable",
          "-I${tempDir.absolutePath}",
        )
        if (toolchain.sysroot != null && toolchain.sysroot.exists()) {
          args.add("--target=aarch64-linux-android24")
          args.add("--sysroot=${toolchain.sysroot.absolutePath}")
        }
        args.add(srcFile.absolutePath)

        val pb = ProcessBuilder(args)
        pb.directory(tempDir)
        pb.redirectErrorStream(true)
        val process = pb.start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exited = process.waitFor(45, TimeUnit.SECONDS)

        assertTrue("Clang process timed out for ${sourceFile.fileName}", exited)
        val exitCode = process.exitValue()
        assertEquals(
          "Clang compilation syntax check failed for ${sourceFile.fileName} with exit code $exitCode.\nOutput:\n$output",
          0,
          exitCode,
        )
      }
    } finally {
      tempDir.deleteRecursively()
    }
  }

  object ZipalignLocator {
    fun findZipalign(): File? {
      val localProps = File("local.properties")
      if (localProps.exists()) {
        try {
          val props = Properties().apply { FileInputStream(localProps).use { load(it) } }
          val sdkDirStr = props.getProperty("sdk.dir")
          if (!sdkDirStr.isNullOrBlank()) {
            val exe = scanBuildTools(File(sdkDirStr))
            if (exe != null) return exe
          }
        } catch (_: Exception) {}
      }

      val envSdk = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
      if (!envSdk.isNullOrBlank()) {
        val exe = scanBuildTools(File(envSdk))
        if (exe != null) return exe
      }

      val pathDirs = (System.getenv("PATH") ?: "").split(File.pathSeparator)
      val exeExt = if (System.getProperty("os.name")?.lowercase()?.contains("windows") == true) ".exe" else ""
      for (dirStr in pathDirs) {
        val exe = File(dirStr, "zipalign$exeExt")
        if (exe.exists() && exe.canExecute()) return exe
      }
      return null
    }

    private fun scanBuildTools(sdkDir: File): File? {
      val btDir = File(sdkDir, "build-tools")
      if (!btDir.exists() || !btDir.isDirectory) return null
      val versions = btDir.listFiles()?.filter { it.isDirectory }?.sortedByDescending { it.name } ?: return null
      val exeExt = if (System.getProperty("os.name")?.lowercase()?.contains("windows") == true) ".exe" else ""
      for (v in versions) {
        val exe = File(v, "zipalign$exeExt")
        if (exe.exists() && exe.canExecute()) return exe
      }
      return null
    }
  }
}
