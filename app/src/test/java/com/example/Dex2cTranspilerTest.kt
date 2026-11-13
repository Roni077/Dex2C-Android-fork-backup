package com.example

import com.example.dex.DalvikDisassembler
import com.example.dex.DexParser
import com.example.transpiler.DccConfigCodec
import com.example.transpiler.Dex2cTranspiler
import com.example.transpiler.FilterRuleEngine
import com.example.transpiler.JniBindingStyle
import com.example.transpiler.LoaderMode
import com.example.transpiler.TargetCLanguage
import com.example.transpiler.TranspilerConfig
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
        moduleName = "kirlif_shield",
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

    assertEquals("kirlif_shield", decoded.moduleName)
    assertTrue(decoded.allowInit)
    assertEquals(JniBindingStyle.STATIC_SYMBOLS, decoded.jniStyle)
    assertEquals(TargetCLanguage.C11, decoded.targetLanguage)
    assertEquals(LoaderMode.CUSTOM_LOADER, decoded.loaderMode)
    assertEquals("com.custom.MyLoader", decoded.customLoaderClass)
    assertTrue(decoded.forceCustomLoader)
    assertFalse("x86" in decoded.targetAbis)
    assertTrue("arm64-v8a" in decoded.targetAbis)
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
