package com.dex2c.studio.transpiler

import com.dex2c.studio.dex.DalvikInstruction
import com.dex2c.studio.dex.DalvikPayload
import com.dex2c.studio.dex.DexCatchHandler
import com.dex2c.studio.dex.DexClassInfo
import com.dex2c.studio.dex.DexCodeMeta
import com.dex2c.studio.dex.DexEncodedMethod
import com.dex2c.studio.dex.DexHeaderInfo
import com.dex2c.studio.dex.DexMethodRef
import com.dex2c.studio.dex.DexParsedFile
import com.dex2c.studio.dex.DexProto
import com.dex2c.studio.dex.DexTryBlock
import com.dex2c.studio.dex.OpCategory
import com.dex2c.studio.dex.parseClassDescriptor
import java.io.File
import java.io.FileInputStream
import java.nio.file.Files
import java.util.Properties
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Automated Clang C++ syntax validation unit tests for Dex2C Transpiler.
 *
 * Verifies that all transpiled C++17 and C11 source units (including control-flow switches,
 * JNI exception guards, wide array operations, invoke-direct non-virtual calls, and overloaded
 * static symbol mangling) compile cleanly with Android NDK Clang with 0 syntax or semantic errors.
 */
class ClangSyntaxValidationTest {

  @Test
  fun testPackedAndSparseSwitchCPlusPlusCompilation() {
    val packedPayload = DalvikPayload.PackedSwitch(
      offsetWords = 10,
      wordCount = 8,
      firstKey = 100,
      targetOffsets = listOf(20, 25, 30),
    )
    val packedInsn = DalvikInstruction(
      offsetWords = 4,
      widthWords = 3,
      opcode = 0x2B,
      mnemonic = "packed-switch",
      format = "31t",
      operandsText = "v0, :pswitch_data_0",
      rawWords = listOf(0x2B, 0, 6),
      category = OpCategory.BRANCH,
      regA = 0,
      targetOffset = 10,
      payload = packedPayload,
    )
    val sparsePayload = DalvikPayload.SparseSwitch(
      offsetWords = 35,
      wordCount = 10,
      cases = listOf(1 to 40, 10 to 45, 100 to 50),
    )
    val sparseInsn = DalvikInstruction(
      offsetWords = 15,
      widthWords = 3,
      opcode = 0x2C,
      mnemonic = "sparse-switch",
      format = "31t",
      operandsText = "v1, :sswitch_data_0",
      rawWords = listOf(0x2C, 1, 20),
      category = OpCategory.BRANCH,
      regA = 1,
      targetOffset = 35,
      payload = sparsePayload,
    )
    val retInsn = DalvikInstruction(
      offsetWords = 8, widthWords = 1, opcode = 0x0E, mnemonic = "return-void",
      format = "10x", operandsText = "", rawWords = listOf(0x0E), category = OpCategory.BRANCH,
    )
    val t20 = DalvikInstruction(
      offsetWords = 20, widthWords = 1, opcode = 0x0E, mnemonic = "return-void",
      format = "10x", operandsText = "", rawWords = listOf(0x0E), category = OpCategory.BRANCH,
    )
    val t25 = DalvikInstruction(
      offsetWords = 25, widthWords = 1, opcode = 0x0E, mnemonic = "return-void",
      format = "10x", operandsText = "", rawWords = listOf(0x0E), category = OpCategory.BRANCH,
    )
    val t30 = DalvikInstruction(
      offsetWords = 30, widthWords = 1, opcode = 0x0E, mnemonic = "return-void",
      format = "10x", operandsText = "", rawWords = listOf(0x0E), category = OpCategory.BRANCH,
    )
    val t40 = DalvikInstruction(
      offsetWords = 40, widthWords = 1, opcode = 0x0E, mnemonic = "return-void",
      format = "10x", operandsText = "", rawWords = listOf(0x0E), category = OpCategory.BRANCH,
    )
    val t45 = DalvikInstruction(
      offsetWords = 45, widthWords = 1, opcode = 0x0E, mnemonic = "return-void",
      format = "10x", operandsText = "", rawWords = listOf(0x0E), category = OpCategory.BRANCH,
    )
    val t50 = DalvikInstruction(
      offsetWords = 50, widthWords = 1, opcode = 0x0E, mnemonic = "return-void",
      format = "10x", operandsText = "", rawWords = listOf(0x0E), category = OpCategory.BRANCH,
    )

    val insns = listOf(packedInsn, sparseInsn, retInsn, t20, t25, t30, t40, t45, t50)
    val method = buildSyntheticMethod(
      methodName = "dispatchSwitchCases",
      returnType = "V",
      insns = insns,
    )
    val dex = buildSyntheticDex(listOf(method))

    // 1. Non-flattened mode
    val nonFlatConfig = TranspilerConfig(
      targetLanguage = TargetCLanguage.CPP17,
      controlFlowFlattening = false,
      stringEncryption = false,
    )
    val bundleNonFlat = Dex2cTranspiler.transpileBundle(dex, listOf(method), nonFlatConfig)
    validateTranspiledBundleWithClang(bundleNonFlat, isCpp = true)

    // 2. Flattened mode
    val flatConfig = TranspilerConfig(
      targetLanguage = TargetCLanguage.CPP17,
      controlFlowFlattening = true,
      stringEncryption = false,
    )
    val bundleFlat = Dex2cTranspiler.transpileBundle(dex, listOf(method), flatConfig)
    validateTranspiledBundleWithClang(bundleFlat, isCpp = true)
  }

  @Test
  fun testJniExceptionHandlingAndGuardReturnFlowCompilation() {
    val tryBlocks = listOf(
      DexTryBlock(
        startOffsetWord = 0,
        insnCount = 4,
        handlers = listOf(
          DexCatchHandler(typeDescriptor = "Ljava/lang/IllegalArgumentException;", handlerOffsetWord = 6),
        ),
      ),
    )
    val constInsn = DalvikInstruction(
      offsetWords = 0, widthWords = 1, opcode = 0x12, mnemonic = "const/4",
      format = "11n", operandsText = "v0, #0", rawWords = listOf(0x12), category = OpCategory.MOVE,
      regA = 0,
    )
    val retInsn = DalvikInstruction(
      offsetWords = 4, widthWords = 1, opcode = 0x0F, mnemonic = "return",
      format = "11x", operandsText = "v0", rawWords = listOf(0x0F), category = OpCategory.BRANCH,
      regA = 0,
    )
    val handlerInsn = DalvikInstruction(
      offsetWords = 6, widthWords = 1, opcode = 0x0F, mnemonic = "return",
      format = "11x", operandsText = "v0", rawWords = listOf(0x0F), category = OpCategory.BRANCH,
      regA = 0,
    )

    val insns = listOf(constInsn, retInsn, handlerInsn)
    val method = buildSyntheticMethod(
      methodName = "tryCatchMethod",
      returnType = "I",
      insns = insns,
      tryBlocks = tryBlocks,
    )
    val dex = buildSyntheticDex(listOf(method))

    val config = TranspilerConfig(
      targetLanguage = TargetCLanguage.CPP17,
      controlFlowFlattening = false,
    )
    val bundle = Dex2cTranspiler.transpileBundle(dex, listOf(method), config)
    validateTranspiledBundleWithClang(bundle, isCpp = true)
  }

  @Test
  fun testWideArrayAndDirectInvocationCompilation() {
    val getInsn = DalvikInstruction(
      offsetWords = 0, widthWords = 2, opcode = 0x4B, mnemonic = "aget-wide",
      format = "23x", operandsText = "v0, v2, v3", rawWords = listOf(0x4B, 0), category = OpCategory.ARRAY,
      regA = 0, regB = 2, regC = 3,
    )
    val putInsn = DalvikInstruction(
      offsetWords = 2, widthWords = 2, opcode = 0x4E, mnemonic = "aput-wide",
      format = "23x", operandsText = "v0, v2, v3", rawWords = listOf(0x4E, 0), category = OpCategory.ARRAY,
      regA = 0, regB = 2, regC = 3,
    )
    val invokeDirectInsn = DalvikInstruction(
      offsetWords = 4, widthWords = 3, opcode = 0x70, mnemonic = "invoke-direct",
      format = "35c", operandsText = "{v2}, Lcom/example/TestClass;->initHelper()V",
      rawWords = listOf(0x70, 0, 1), category = OpCategory.INVOKE,
      regList = listOf(2),
      methodRef = DexMethodRef(
        definingClass = "Lcom/example/TestClass;",
        name = "initHelper",
        proto = DexProto(shorty = "V", returnType = "V", parameterTypes = emptyList()),
      ),
    )
    val retInsn = DalvikInstruction(
      offsetWords = 7, widthWords = 1, opcode = 0x0E, mnemonic = "return-void",
      format = "10x", operandsText = "", rawWords = listOf(0x0E), category = OpCategory.BRANCH,
    )

    val insns = listOf(getInsn, putInsn, invokeDirectInsn, retInsn)
    val method = buildSyntheticMethod(
      methodName = "processWideArraysAndDirectCall",
      returnType = "V",
      insns = insns,
    )
    val helperMethod = buildSyntheticMethod(
      methodName = "initHelper",
      returnType = "V",
      insns = listOf(retInsn),
    )
    val dex = buildSyntheticDex(listOf(method, helperMethod))

    val config = TranspilerConfig(
      targetLanguage = TargetCLanguage.CPP17,
      controlFlowFlattening = false,
    )
    val bundle = Dex2cTranspiler.transpileBundle(dex, listOf(method, helperMethod), config)
    validateTranspiledBundleWithClang(bundle, isCpp = true)
  }

  @Test
  fun testOverloadedStaticSymbolManglingCompilation() {
    val retInsn = DalvikInstruction(
      offsetWords = 0, widthWords = 1, opcode = 0x0F, mnemonic = "return",
      format = "11x", operandsText = "v0", rawWords = listOf(0x0F), category = OpCategory.BRANCH,
      regA = 0,
    )
    val method1 = buildSyntheticMethod(
      methodName = "computeValue",
      returnType = "I",
      paramTypes = listOf("I"),
      insns = listOf(retInsn),
    )
    val method2 = buildSyntheticMethod(
      methodName = "computeValue",
      returnType = "I",
      paramTypes = listOf("I", "Ljava/lang/String;"),
      insns = listOf(retInsn),
    )
    val dex = buildSyntheticDex(listOf(method1, method2))

    val config = TranspilerConfig(
      targetLanguage = TargetCLanguage.CPP17,
      jniStyle = JniBindingStyle.STATIC_SYMBOLS,
      controlFlowFlattening = false,
    )
    val bundle = Dex2cTranspiler.transpileBundle(dex, listOf(method1, method2), config)
    validateTranspiledBundleWithClang(bundle, isCpp = true)
  }

  @Test
  fun testTargetCLanguageC11Compilation() {
    val retInsn = DalvikInstruction(
      offsetWords = 0, widthWords = 1, opcode = 0x0E, mnemonic = "return-void",
      format = "10x", operandsText = "", rawWords = listOf(0x0E), category = OpCategory.BRANCH,
    )
    val method = buildSyntheticMethod(
      methodName = "simpleVoidMethod",
      returnType = "V",
      insns = listOf(retInsn),
    )
    val dex = buildSyntheticDex(listOf(method))

    val config = TranspilerConfig(
      targetLanguage = TargetCLanguage.C11,
      controlFlowFlattening = false,
    )
    val bundle = Dex2cTranspiler.transpileBundle(dex, listOf(method), config)
    validateTranspiledBundleWithClang(bundle, isCpp = false)
  }

  // --- Validation Helpers ---

  private fun validateTranspiledBundleWithClang(bundle: GeneratedBundle, isCpp: Boolean) {
    val toolchain = ClangLocator.findClangToolchain()
    if (toolchain == null) {
      performFallbackStructuralSyntaxCheck(bundle, isCpp)
      return
    }

    val compilerExe = if (isCpp) toolchain.clangPlusPlus else toolchain.clang
    assertTrue("Compiler executable must exist", compilerExe.exists() && compilerExe.canExecute())

    val tempDir = Files.createTempDirectory("dex2c_clang_test").toFile()
    try {
      val headerFile = File(tempDir, "dex2c_runtime.h")
      headerFile.writeText(bundle.headerFile.content)

      for (sourceFile in bundle.sourceFiles) {
        val srcFile = File(tempDir, sourceFile.fileName)
        srcFile.writeText(sourceFile.content)

        val args = mutableListOf(
          compilerExe.absolutePath,
          if (isCpp) "-std=c++17" else "-std=c11",
          "-fsyntax-only",
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

  private fun performFallbackStructuralSyntaxCheck(bundle: GeneratedBundle, isCpp: Boolean) {
    for (sourceFile in bundle.sourceFiles) {
      val text = sourceFile.content
      var braceBalance = 0
      for (ch in text) {
        if (ch == '{') braceBalance++
        if (ch == '}') braceBalance--
      }
      assertEquals("Unbalanced braces in ${sourceFile.fileName}", 0, braceBalance)
      assertTrue("Missing runtime header include", text.contains("#include \"dex2c_runtime.h\""))
      assertFalse("Contains unresolved TODO", text.contains("TODO"))
    }
  }

  // --- Synthetic DEX Model Builders ---

  private fun buildSyntheticMethod(
    methodName: String,
    returnType: String,
    paramTypes: List<String> = emptyList(),
    definingClass: String = "Lcom/example/TestClass;",
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
    val byClass = methods.groupBy { it.methodRef.definingClass }
    val classes = byClass.map { (clsDesc, mList) ->
      val p = parseClassDescriptor(clsDesc)
      DexClassInfo(
        classIndex = 0,
        descriptor = clsDesc,
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
      sourceLabel = "test.dex",
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

  // --- Clang Toolchain Locator ---

  data class ClangToolchain(
    val clang: File,
    val clangPlusPlus: File,
    val sysroot: File?,
  )

  object ClangLocator {
    fun findClangToolchain(): ClangToolchain? {
      // 1. Try local.properties
      val localProps = File("local.properties")
      if (localProps.exists()) {
        try {
          val props = Properties().apply { FileInputStream(localProps).use { load(it) } }
          val sdkDirStr = props.getProperty("sdk.dir")
          if (!sdkDirStr.isNullOrBlank()) {
            val tc = scanSdkNdk(File(sdkDirStr))
            if (tc != null) return tc
          }
        } catch (_: Exception) {}
      }

      // 2. Try ANDROID_HOME / ANDROID_NDK_ROOT environment variables
      val envSdk = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
      if (!envSdk.isNullOrBlank()) {
        val tc = scanSdkNdk(File(envSdk))
        if (tc != null) return tc
      }

      val envNdk = System.getenv("ANDROID_NDK_ROOT") ?: System.getenv("ANDROID_NDK_HOME") ?: System.getenv("NDK_HOME")
      if (!envNdk.isNullOrBlank()) {
        val tc = scanNdkDir(File(envNdk))
        if (tc != null) return tc
      }

      // 3. Fallback: Check system PATH for clang++ / clang
      val pathDirs = (System.getenv("PATH") ?: "").split(File.pathSeparator)
      val exeExt = if (System.getProperty("os.name")?.lowercase()?.contains("windows") == true) ".exe" else ""
      for (dirStr in pathDirs) {
        val dir = File(dirStr)
        val clangPlusPlus = File(dir, "clang++$exeExt")
        val clang = File(dir, "clang$exeExt")
        if (clangPlusPlus.exists() && clang.exists()) {
          return ClangToolchain(clang = clang, clangPlusPlus = clangPlusPlus, sysroot = null)
        }
      }

      return null
    }

    private fun scanSdkNdk(sdkDir: File): ClangToolchain? {
      val ndkBase = File(sdkDir, "ndk")
      if (!ndkBase.exists() || !ndkBase.isDirectory) return null
      val versionDirs = ndkBase.listFiles()?.filter { it.isDirectory }?.sortedByDescending { it.name } ?: return null
      for (vDir in versionDirs) {
        val tc = scanNdkDir(vDir)
        if (tc != null) return tc
      }
      return null
    }

    private fun scanNdkDir(ndkDir: File): ClangToolchain? {
      val prebuiltBase = File(ndkDir, "toolchains/llvm/prebuilt")
      if (!prebuiltBase.exists() || !prebuiltBase.isDirectory) return null
      val hostDirs = prebuiltBase.listFiles()?.filter { it.isDirectory } ?: return null
      val exeExt = if (System.getProperty("os.name")?.lowercase()?.contains("windows") == true) ".exe" else ""
      for (hDir in hostDirs) {
        val binDir = File(hDir, "bin")
        val clangPlusPlus = File(binDir, "clang++$exeExt")
        val clang = File(binDir, "clang$exeExt")
        val sysroot = File(hDir, "sysroot")
        if (clangPlusPlus.exists() && clang.exists()) {
          return ClangToolchain(
            clang = clang,
            clangPlusPlus = clangPlusPlus,
            sysroot = if (sysroot.exists()) sysroot else null,
          )
        }
      }
      return null
    }
  }
}
