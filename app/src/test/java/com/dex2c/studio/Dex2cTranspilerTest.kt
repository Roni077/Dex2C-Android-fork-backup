package com.dex2c.studio

import com.dex2c.studio.dex.DalvikDisassembler
import com.dex2c.studio.dex.DexParser
import com.dex2c.studio.transpiler.DccConfigCodec
import com.dex2c.studio.transpiler.Dex2cTranspiler
import com.dex2c.studio.transpiler.FilterRuleEngine
import com.dex2c.studio.transpiler.JniBindingStyle
import com.dex2c.studio.transpiler.LoaderMode
import com.dex2c.studio.transpiler.TargetCLanguage
import com.dex2c.studio.transpiler.TranspilerConfig
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Dex2cTranspilerTest {

  @Test
  fun parseDisassembleAndTranspileSyntheticDex() {
    val dexBytes = buildMinimalTestDex()
    val parsed = DexParser.parseDex(dexBytes, "unit_test.dex", "classes.dex")

    assertEquals("035", parsed.header.version)
    assertEquals(1, parsed.classes.size)
    val cls = parsed.classes.first()
    assertEquals("com.example.SecretCalc", cls.fullName)
    assertEquals(1, cls.directMethods.size)

    val method = cls.directMethods.first()
    assertEquals("compute", method.methodRef.name)

    val insns = DalvikDisassembler.disassemble(parsed, method)
    assertTrue(insns.any { it.mnemonic == "const/4" })
    assertTrue(insns.any { it.mnemonic == "add-int" })
    assertTrue(insns.any { it.mnemonic == "return" })

    val cfg =
      TranspilerConfig(
        jniStyle = JniBindingStyle.REGISTER_NATIVES,
        targetLanguage = TargetCLanguage.CPP17,
        stringEncryption = true,
        controlFlowFlattening = true,
        allowInit = false,
        targetAbis = setOf("arm64-v8a", "x86_64"),
        customLoaderClass = "amimo.dcc.CustomDccApp",
      )

    val bundle = Dex2cTranspiler.transpileBundle(parsed, listOf(method), cfg)

    assertEquals(1, bundle.transpiledMethodCount)
    assertEquals(8, bundle.allFiles.size)
    assertTrue(bundle.sourceFile.content.contains("JNI_OnLoad"))
    assertTrue(bundle.sourceFile.content.contains("RegisterNatives"))
    assertTrue(bundle.sourceFile.content.contains("switch (__cf_pc)"))
    assertTrue(bundle.sourceFile.content.contains("v[0].i = v[1].i + v[2].i;"))
    assertTrue(bundle.applicationMkFile.content.contains("APP_ABI      := arm64-v8a x86_64"))
    assertTrue(bundle.androidMkFile.content.contains("-fvisibility=hidden"))
    assertTrue(bundle.dccCfgFile.content.contains("\"allow_init\": false"))
    assertTrue(bundle.loaderStubFile.content.contains("class CustomDccApp extends Application"))
  }

  @Test
  fun filterRuleEngineAndDccCfgRoundTrip() {
    val dexBytes = buildMinimalTestDex()
    val parsed = DexParser.parseDex(dexBytes, "unit_test.dex", "classes.dex")

    val includeCfg =
      TranspilerConfig(
        filterRulesText = "Lcom/example/SecretCalc;->compute.*",
        allowInit = false,
      )
    val summaryMatch = FilterRuleEngine.evaluate(parsed, includeCfg)
    assertEquals(1, summaryMatch.matchedMethodIds.size)

    val excludeCfg =
      includeCfg.copy(filterRulesText = ".*\n!.*->compute.*")
    val summaryExcluded = FilterRuleEngine.evaluate(parsed, excludeCfg)
    assertEquals(0, summaryExcluded.matchedMethodIds.size)

    val cfg =
      TranspilerConfig(
        moduleName = "codehasan_shield",
        allowInit = true,
        jniStyle = JniBindingStyle.STATIC_SYMBOLS,
        targetLanguage = TargetCLanguage.C11,
        loaderMode = LoaderMode.CUSTOM_LOADER,
        customLoaderClass = "com.custom.MyLoader",
        forceCustomLoader = true,
        targetAbis = setOf("arm64-v8a"),
      )
    val encoded = DccConfigCodec.encodeDccCfg(cfg)
    val decoded = DccConfigCodec.decodeDccCfg(encoded, TranspilerConfig())

    assertEquals("codehasan_shield", decoded.moduleName)
    assertTrue(decoded.allowInit)
    assertEquals(JniBindingStyle.STATIC_SYMBOLS, decoded.jniStyle)
    assertEquals(TargetCLanguage.C11, decoded.targetLanguage)
    assertEquals(LoaderMode.CUSTOM_LOADER, decoded.loaderMode)
    assertEquals("com.custom.MyLoader", decoded.customLoaderClass)
    assertTrue(decoded.forceCustomLoader)
    assertFalse("x86" in decoded.targetAbis)
    assertTrue("arm64-v8a" in decoded.targetAbis)
  }

  @Test
  fun dccConfigCodec_encodesAndDecodesOllvmAnd16KbAlignment() {
    val ollvmConfig =
      com.dex2c.studio.transpiler.OllvmConfig(
        enabled = true,
        controlFlowFlattening = true,
        instructionSubstitution = true,
        subLoop = 4,
        bogusControlFlow = true,
        bcfLoop = 3,
        bcfProb = 80,
        basicBlockSplit = true,
        splitNum = 4,
        stringObfuscation = true,
        align16KbPages = true,
        customFlags = "-mllvm -customOpt",
      )
    val flags = ollvmConfig.buildClangFlags()
    assertTrue(flags.contains("-mllvm -fla"))
    assertTrue(flags.contains("-mllvm -sub"))
    assertTrue(flags.contains("-mllvm -sub_loop=4"))
    assertTrue(flags.contains("-mllvm -bcf"))
    assertTrue(flags.contains("-mllvm -bcf_prob=80"))
    assertTrue(flags.contains("-mllvm -split"))
    assertTrue(flags.contains("-mllvm -split_num=4"))
    assertTrue(flags.contains("-mllvm -sobf"))
    assertTrue(flags.contains("-mllvm -customOpt"))

    val cfg = TranspilerConfig(moduleName = "ollvm_test_module", ollvm = ollvmConfig)
    val encoded = DccConfigCodec.encodeDccCfg(cfg)
    assertTrue(encoded.contains("\"ollvm\":"))
    assertTrue(encoded.contains("\"enable\": true"))
    assertTrue(encoded.contains("-mllvm -fla"))

    val decoded = DccConfigCodec.decodeDccCfg(encoded, TranspilerConfig())
    assertTrue(decoded.ollvm.enabled)
    assertTrue(decoded.ollvm.controlFlowFlattening)
    assertTrue(decoded.ollvm.instructionSubstitution)
    assertTrue(decoded.ollvm.bogusControlFlow)
    assertTrue(decoded.ollvm.basicBlockSplit)
    assertTrue(decoded.ollvm.stringObfuscation)
  }

  @Test
  fun ollvmPresetProfiles_applyExpectedPassConfigurations() {
    val defaultCfg = com.dex2c.studio.transpiler.OllvmConfig()
    val balanced = defaultCfg.applyPreset(com.dex2c.studio.transpiler.OllvmPreset.BALANCED)
    assertTrue(balanced.enabled)
    assertTrue(balanced.controlFlowFlattening)
    assertTrue(balanced.instructionSubstitution)
    assertFalse(balanced.bogusControlFlow)
    assertTrue(balanced.align16KbPages)

    val maximum = defaultCfg.applyPreset(com.dex2c.studio.transpiler.OllvmPreset.MAXIMUM)
    assertTrue(maximum.enabled)
    assertTrue(maximum.controlFlowFlattening)
    assertTrue(maximum.instructionSubstitution)
    assertTrue(maximum.bogusControlFlow)
    assertEquals(100, maximum.bcfProb)
    assertTrue(maximum.stringObfuscation)

    val fast = defaultCfg.applyPreset(com.dex2c.studio.transpiler.OllvmPreset.FAST)
    assertFalse(fast.enabled)
    assertFalse(fast.controlFlowFlattening)
    assertTrue(fast.align16KbPages)
  }

  @Test
  fun transpileMethodWithPercentFormatSpecifiersAndCommentCloseDoesNotCrash() {
    val formatLiteral = "Count=%d %s %20 100% /* */"
    val parsed = DexParser.parseDex(buildMinimalTestDex(), "unit_test.dex", "classes.dex")
    val method = parsed.classes.first().directMethods.first()

    // Append synthetic Dalvik instructions at the end of rawBytes:
    //   0000: const-string v0, string@stringIdx   (0x001A, stringIdx)
    //   0002: return v0                             (0x000F)
    val stringIdx = parsed.strings.size
    val extraInsns =
      ByteBuffer.allocate(6)
        .order(ByteOrder.LITTLE_ENDIAN)
        .putShort(0x001A.toShort())
        .putShort(stringIdx.toShort())
        .putShort(0x000F.toShort())
        .array()
    val newInsnsOffset = parsed.rawBytes.size
    val dexWithString =
      parsed.copy(
        rawBytes = parsed.rawBytes + extraInsns,
        strings = parsed.strings + formatLiteral,
      )
    val modifiedMethod =
      method.copy(
        codeMeta =
          method.codeMeta!!.copy(
            insnsCount = 3,
            insnsOffsetBytes = newInsnsOffset,
          )
      )

    val flattenedPlainCfg =
      TranspilerConfig(
        controlFlowFlattening = true,
        stringEncryption = false,
      )
    val plainC = Dex2cTranspiler.transpileMethodToC(dexWithString, modifiedMethod, flattenedPlainCfg)
    assertTrue(plainC.contains("case 0x0000u:"))
    assertTrue(plainC.contains("case 0x0002u:"))
    assertTrue(plainC.contains("Count=%d %s %20 100%"))
    assertFalse(plainC.contains("/* */ */"))

    val flattenedEncCfg = flattenedPlainCfg.copy(stringEncryption = true)
    val bundle = Dex2cTranspiler.transpileBundle(dexWithString, listOf(modifiedMethod), flattenedEncCfg)
    assertEquals(1, bundle.transpiledMethodCount)
    assertTrue(bundle.sourceFile.content.contains("dex2c_xor_decode"))
  }

  @Test
  fun multiDexTranspileAndFilterEvaluateAllCombineBothDexes() {
    val dex1 = DexParser.parseDex(buildMinimalTestDex(), "app.apk", "classes.dex")
    val dex2 = DexParser.parseDex(buildMinimalTestDex(), "app.apk", "classes2.dex")
    val m1 = dex1.classes.first().directMethods.first()
    val m2 = dex2.classes.first().directMethods.first()
    // Method IDs are namespaced by source DEX entryName so they never collide
    assertFalse(m1.id == m2.id)

    val cfg =
      TranspilerConfig(
        jniStyle = JniBindingStyle.REGISTER_NATIVES,
        allowInit = true,
        filterRulesText = "com\\.example\\.SecretCalc.*",
      )
    val summary = FilterRuleEngine.evaluateAll(listOf(dex1, dex2), cfg)
    assertEquals(setOf(m1.id, m2.id), summary.matchedMethodIds)
    assertEquals(2, summary.matchedClassCount)

    val multiBundle =
      Dex2cTranspiler.transpileBundleMultiDex(
        entries = listOf(dex1 to m1, dex2 to m2),
        config = cfg,
        sourceLabelHint = "app.apk",
      )
    assertEquals(2, multiBundle.transpiledMethodCount)
    assertTrue(multiBundle.sourceFile.content.contains("classes.dex"))
    assertTrue(multiBundle.sourceFile.content.contains("classes2.dex"))
    assertTrue(multiBundle.sourceFile.content.contains("JNI_OnLoad"))
  }

  @Test
  fun multiChunkStreamingSplitsLargeMethodSelectionsAndStreamsToDisk() {
    val baseDex = DexParser.parseDex(buildMinimalTestDex(), "huge.apk", "classes.dex")
    val baseMethod = baseDex.classes.first().directMethods.first()

    val syntheticEntries =
      (0 until 60).map { idx ->
        val m =
          baseMethod.copy(
            id = "classes.dex::Lcom/example/SecretCalc;#$idx",
            methodIndex = idx,
            methodRef = baseMethod.methodRef.copy(name = "compute_$idx"),
          )
        baseDex to m
      }

    val cfg =
      TranspilerConfig(
        jniStyle = JniBindingStyle.REGISTER_NATIVES,
        targetLanguage = TargetCLanguage.CPP17,
        controlFlowFlattening = true,
      )
    var lastProgressTotal = 0
    var lastProgressChunks = 0
    val bundle =
      Dex2cTranspiler.transpileBundleMultiDex(
        entries = syntheticEntries,
        config = cfg,
        sourceLabelHint = "huge.apk",
        methodsPerChunk = 20,
        onProgress = { progress ->
          lastProgressTotal = progress.completedMethods
          lastProgressChunks = progress.totalChunks
        },
      )

    assertEquals(60, bundle.transpiledMethodCount)
    assertEquals(60, lastProgressTotal)
    assertEquals(3, lastProgressChunks)
    // 1 coordinator (dex2c_bridge.cpp) + 3 modular parts (part_01..part_03) = 4 sourceFiles
    assertEquals(4, bundle.sourceFiles.size)
    assertEquals("dex2c_bridge.cpp", bundle.sourceFile.fileName)
    assertEquals("dex2c_bridge_part_01.cpp", bundle.sourceFiles[1].fileName)
    assertEquals("dex2c_bridge_part_02.cpp", bundle.sourceFiles[2].fileName)
    assertEquals("dex2c_bridge_part_03.cpp", bundle.sourceFiles[3].fileName)

    // Coordinator contains forward prototypes and JNI_OnLoad
    val coordinatorDiskText = bundle.sourceFile.openInputStream().bufferedReader().readText()
    assertTrue(coordinatorDiskText.contains("Forward prototypes for modular translation units"))
    assertTrue(coordinatorDiskText.contains("JNI_OnLoad"))
    assertTrue(coordinatorDiskText.contains("compute_0"))
    assertTrue(coordinatorDiskText.contains("compute_59"))

    // Build scripts include all modular part files
    assertTrue(bundle.androidMkFile.content.contains("dex2c_bridge_part_01.cpp"))
    assertTrue(bundle.androidMkFile.content.contains("dex2c_bridge_part_03.cpp"))
    assertTrue(bundle.cmakeFile.content.contains("jni/dex2c_bridge_part_01.cpp"))
    assertTrue(bundle.cmakeFile.content.contains("jni/dex2c_bridge_part_03.cpp"))

    // Each source part is backed by an on-disk file with non-zero length
    bundle.sourceFiles.forEach { sf ->
      assertTrue(sf.backingFile != null && sf.backingFile!!.exists())
      assertTrue(sf.totalByteSize > 0L)
    }
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
