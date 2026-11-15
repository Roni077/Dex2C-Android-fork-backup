package com.example

import com.example.dex.CfgEdgeKind
import com.example.dex.ControlFlowGraphBuilder
import com.example.dex.DalvikDisassembler
import com.example.dex.DalvikPayload
import com.example.dex.DexCatchHandler
import com.example.dex.DexCodeMeta
import com.example.dex.DexEncodedMethod
import com.example.dex.DexHelper
import com.example.dex.DexMethodRef
import com.example.dex.DexParser
import com.example.dex.DexProto
import com.example.dex.DexTryBlock
import com.example.dex.SmaliClassDisassembler
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DexDisassemblerAndSmaliTest {

  @Test
  fun parseDex039MapListDebugInfoAnnotationsAndHandles() {
    val bytes = buildSyntheticDex039()
    val parsed = DexParser.parseDex(bytes, "dex039_test.dex", "classes.dex")

    assertEquals("039", parsed.header.version)
    assertTrue(parsed.parseDurationMs >= 1L)
    assertEquals("Kotlin DexFile + smali-dexlib2", parsed.parserEngine)

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
  fun smaliClassDisassemblerGeneratesValidSmaliUsingDexlib2AndBaksmali() {
    val bytes = buildSyntheticDex039()
    val parsed = DexParser.parseDex(bytes, "smali_test.dex", "classes.dex")
    val cls = DexHelper.getClasses(parsed).first()

    val result = SmaliClassDisassembler.disassembleClass(parsed, cls)
    assertEquals("smali-dexlib2 + baksmali", result.engineLabel)
    assertTrue(result.lineCount > 5)
    assertEquals(1, result.methodCount)
    assertTrue("Unexpected smali:\n${result.smaliText}", result.smaliText.contains(".class public Lcom/example/Demo;"))
    assertTrue("Unexpected smali:\n${result.smaliText}", result.smaliText.contains(".super Ljava/lang/Object;"))
    assertTrue("Unexpected smali:\n${result.smaliText}", result.smaliText.contains(".source \"Main.kt\""))
    assertTrue("Unexpected smali:\n${result.smaliText}", result.smaliText.contains(".method public static whitelist run(I)I"))
    assertTrue("Unexpected smali:\n${result.smaliText}", result.smaliText.contains(".param p0, \"x\""))
    assertTrue("Unexpected smali:\n${result.smaliText}", result.smaliText.contains(".annotation runtime Landroidx/annotation/Keep;"))
    assertTrue("Unexpected smali:\n${result.smaliText}", result.smaliText.contains(".line 42"))
    assertTrue("Unexpected smali:\n${result.smaliText}", result.smaliText.contains("const/4 v0, 0x0"))
    assertTrue("Unexpected smali:\n${result.smaliText}", result.smaliText.contains("return v0"))
    assertTrue("Unexpected smali:\n${result.smaliText}", result.smaliText.contains(".end method"))

    // Single-method Smali disassembly via baksmali MethodDefinition
    val method = cls.directMethods.first()
    val methodSmali = SmaliClassDisassembler.disassembleMethod(parsed, cls, method)
    assertTrue(methodSmali.contains(".method public static whitelist run(I)I"))
    assertTrue(methodSmali.contains("const/4 v0, 0x0"))
    assertTrue(methodSmali.contains("return v0"))
    assertTrue(methodSmali.contains(".end method"))

    // Wildcard rule generation from selection (Dex2C-Android style)
    val wildcardRules =
      com.example.transpiler.FilterRuleEngine.generateWildcardRulesFromSelection(
        classes = listOf(cls),
        selectedMethodIds = setOf(method.id),
        allowInit = false,
        isBlacklist = false,
      )
    assertEquals(1, wildcardRules.size)
    assertEquals("com/example/Demo;.*", wildcardRules.first().toFilterLine())
  }

  @Test
  fun dalvikDisassemblerDecodesPayloadsAndControlFlowGraphBuildsBlocksAndEdges() {
    val baseDex = DexParser.parseDex(buildSyntheticDex039(), "cfg_test.dex", "classes.dex")

    // Packed-switch + if-eqz + goto + return bytecode stream (18 code units = 36 bytes)
    val words =
      shortArrayOf(
        0x0038.toShort(), 0x0005.toShort(), // 0x00: if-eqz v0, L_0005
        0x012B.toShort(), 0x0008.toShort(), 0x0000.toShort(), // 0x02: packed-switch v1, L_000a
        0x1012.toShort(), // 0x05: const/4 v0, #1
        0x000F.toShort(), // 0x06: return v0
        0x2012.toShort(), // 0x07: const/4 v0, #2
        0x000F.toShort(), // 0x08: return v0
        0xFD28.toShort(), // 0x09: goto L_0006 (-3)
        // 0x0A: packed-switch-payload (size=2, firstKey=10, targets rel to 0x02: +3 -> L_0005, +5 -> L_0007)
        0x0100.toShort(), 0x0002.toShort(),
        0x000A.toShort(), 0x0000.toShort(),
        0x0003.toShort(), 0x0000.toShort(),
        0x0005.toShort(), 0x0000.toShort(),
      )

    val insnsOffset = baseDex.rawBytes.size
    val bb = ByteBuffer.allocate(words.size * 2).order(ByteOrder.LITTLE_ENDIAN)
    words.forEach { bb.putShort(it) }
    val dexWithSwitch = baseDex.copy(rawBytes = baseDex.rawBytes + bb.array())
    val method =
      DexEncodedMethod(
        id = "m_switch",
        methodIndex = 0,
        methodRef = DexMethodRef("Lcom/example/Demo;", DexProto("II", "I", listOf("I")), "dispatch"),
        accessFlags = 0x0009,
        isDirect = true,
        codeMeta =
          DexCodeMeta(
            registersSize = 2,
            insSize = 1,
            outsSize = 0,
            triesSize = 0,
            insnsCount = words.size,
            insnsOffsetBytes = insnsOffset,
          ),
      )

    val insns = DalvikDisassembler.disassemble(dexWithSwitch, method)
    val payloadInsn = insns.first { it.mnemonic == "packed-switch-payload" }
    val packedPayload = payloadInsn.payload as? DalvikPayload.PackedSwitch
    assertNotNull(packedPayload)
    assertEquals(10, packedPayload!!.firstKey)
    assertEquals(listOf(5, 7), packedPayload.targetOffsets)
    assertTrue(payloadInsn.operandsText.contains("10->L_0005"))
    assertTrue(payloadInsn.operandsText.contains("11->L_0007"))

    val tryBlocks =
      listOf(
        DexTryBlock(
          startOffsetWord = 0,
          insnCount = 5,
          handlers = listOf(DexCatchHandler("Ljava/lang/RuntimeException;", 7)),
        )
      )
    val cfg = ControlFlowGraphBuilder.build(insns, tryBlocks)
    assertEquals(1, cfg.payloads.size)
    assertTrue(cfg.blocks.size >= 4)
    assertTrue(cfg.blocks.first().isEntry)
    assertTrue(cfg.blocks.any { b -> b.outgoingEdges.any { it.kind == CfgEdgeKind.CONDITIONAL_BRANCH && it.toOffsetWords == 5 } })
    assertTrue(cfg.blocks.any { b -> b.outgoingEdges.any { it.kind == CfgEdgeKind.SWITCH_CASE && it.label == "case 10" && it.toOffsetWords == 5 } })
    assertTrue(cfg.blocks.any { b -> b.outgoingEdges.any { it.kind == CfgEdgeKind.SWITCH_CASE && it.label == "case 11" && it.toOffsetWords == 7 } })
    assertTrue(cfg.blocks.any { b -> b.outgoingEdges.any { it.kind == CfgEdgeKind.EXCEPTION_HANDLER && it.toOffsetWords == 7 } })
    assertTrue(cfg.blocks.first { it.startOffsetWords == 7 }.isExceptionHandler)
    assertTrue(cfg.exceptionEdgeCount > 0)
  }

  @Test
  fun dalvikDisassemblerDecodesSparseSwitchAndFillArrayDataPayloads() {
    val baseDex = DexParser.parseDex(buildSyntheticDex039(), "payload_test.dex", "classes.dex")
    // 0x00: sparse-switch v0, L_0005 (+5)
    // 0x03: fill-array-data v1, L_000d (+10)
    // 0x05: sparse-switch-payload (size=1, key=42, target rel to 0x00: +3 -> L_0003) -> 6 words (0x05..0x0a)
    // 0x0b: return-void
    // 0x0c: nop
    // 0x0d: fill-array-data-payload (width=4, count=2, values=[100, -5]) -> 8 words (0x0d..0x14)
    val words =
      shortArrayOf(
        0x002C.toShort(), 0x0005.toShort(), 0x0000.toShort(), // 0x00
        0x0126.toShort(), 0x000A.toShort(), 0x0000.toShort(), // 0x03: 3 + 10 = 13 (0x0d)
        0x0200.toShort(), 0x0001.toShort(), // 0x06? wait! sparse starts at 0x06! Let's fix sparse offset = +6!
        0x000E.toShort(), // wait: let's keep offsets exact below
      )
    val exactWords =
      shortArrayOf(
        0x002C.toShort(), 0x0006.toShort(), 0x0000.toShort(), // 0x00..0x02: target = 0 + 6 = 0x06
        0x0126.toShort(), 0x000B.toShort(), 0x0000.toShort(), // 0x03..0x05: target = 3 + 11 = 14 (0x0e)
        // 0x06..0x0b: sparse-switch-payload (size=1, key=42, target rel=3 -> L_0003)
        0x0200.toShort(), 0x0001.toShort(),
        0x002A.toShort(), 0x0000.toShort(),
        0x0003.toShort(), 0x0000.toShort(),
        0x000E.toShort(), // 0x0c: return-void
        0x0000.toShort(), // 0x0d: nop
        // 0x0e..0x15: fill-array-data-payload (width=4B, count=2, values=[100, -5])
        0x0300.toShort(), 0x0004.toShort(),
        0x0002.toShort(), 0x0000.toShort(),
        0x0064.toShort(), 0x0000.toShort(), // 100
        0xFFFB.toShort(), 0xFFFF.toShort(), // -5
      )
    check(words.isNotEmpty())

    val insnsOffset = baseDex.rawBytes.size
    val bb = ByteBuffer.allocate(exactWords.size * 2).order(ByteOrder.LITTLE_ENDIAN)
    exactWords.forEach { bb.putShort(it) }
    val dexWithPayloads = baseDex.copy(rawBytes = baseDex.rawBytes + bb.array())
    val method =
      DexEncodedMethod(
        id = "m_payloads",
        methodIndex = 0,
        methodRef = DexMethodRef("Lcom/example/Demo;", DexProto("V", "V", emptyList()), "payloadDemo"),
        accessFlags = 0x0009,
        isDirect = true,
        codeMeta =
          DexCodeMeta(
            registersSize = 2,
            insSize = 0,
            outsSize = 0,
            triesSize = 0,
            insnsCount = exactWords.size,
            insnsOffsetBytes = insnsOffset,
          ),
      )

    val insns = DalvikDisassembler.disassemble(dexWithPayloads, method)
    val sparse = insns.first { it.mnemonic == "sparse-switch-payload" }.payload as? DalvikPayload.SparseSwitch
    assertNotNull(sparse)
    assertEquals(listOf(42 to 3), sparse!!.cases)

    val arrayPayload = insns.first { it.mnemonic == "fill-array-data-payload" }.payload as? DalvikPayload.FillArrayData
    assertNotNull(arrayPayload)
    assertEquals(4, arrayPayload!!.elementWidthBytes)
    assertEquals(2, arrayPayload.elementCount)
    assertEquals(listOf("100", "-5"), arrayPayload.previewElements)
    assertTrue(arrayPayload.hexPreview.startsWith("64 00 00 00"))
  }

  private fun buildSyntheticDex039(): ByteArray {
    // Strings: 0=I, 1=II, 2=Landroidx/annotation/Keep;, 3=Lcom/example/Demo;, 4=Ljava/lang/Object;, 5=Main.kt, 6=run, 7=x
    val strings =
      listOf(
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

    // string_ids @ 0x70 (8 * 4 = 32 bytes -> 0x70..0x8F)
    strOffsets.forEachIndexed { idx, off -> buf.putInt(0x70 + idx * 4, off) }

    // type_ids @ 0x90 (4 * 4 = 16 bytes -> 0x90..0x9F)
    buf.putInt(0x90, 2) // Keep
    buf.putInt(0x94, 3) // Demo
    buf.putInt(0x98, 4) // Object
    buf.putInt(0x9C, 0) // I

    // proto_ids @ 0xA0 (12 bytes -> 0xA0..0xAB): shorty=1("II"), return=3("I"), paramsOff=0xD4
    buf.putInt(0xA0, 1)
    buf.putInt(0xA4, 3)
    buf.putInt(0xA8, 0xD4)

    // method_ids @ 0xAC (8 bytes -> 0xAC..0xB3): class=1(Demo), proto=0, name=6("run")
    buf.putShort(0xAC, 1)
    buf.putShort(0xAE, 0)
    buf.putInt(0xB0, 6)

    // class_defs @ 0xB4 (32 bytes -> 0xB4..0xD3): class=1, flags=1, super=2, interfaces=0, sourceFile=5("Main.kt"), annotations=0x150, classData=0x220, staticValues=0
    buf.putInt(0xB4, 1)
    buf.putInt(0xB8, 1)
    buf.putInt(0xBC, 2)
    buf.putInt(0xC0, 0)
    buf.putInt(0xC4, 5)
    buf.putInt(0xC8, 0x150) // annotations_off
    buf.putInt(0xCC, 0x220) // class_data_off
    buf.putInt(0xD0, 0)

    // type_list @ 0xD4: 1 param = type 3("I")
    buf.putInt(0xD4, 1)
    buf.putShort(0xD8, 3)

    // code_item @ 0xE0: regs=2, ins=1, outs=0, tries=0, debug_info_off=0x100, insns_size=2 (return v0)
    buf.putShort(0xE0, 2)
    buf.putShort(0xE2, 1)
    buf.putShort(0xE4, 0)
    buf.putShort(0xE6, 0)
    buf.putInt(0xE8, 0x100) // debug_info_off
    buf.putInt(0xEC, 2)
    buf.putShort(0xF0, 0x0012.toShort()) // const/4 v0, 0
    buf.putShort(0xF2, 0x000F.toShort()) // return v0

    // debug_info_item @ 0x100: line_start=42, params_size=1, param[0]="x" (str 7 -> uleb128p1 = 8), special opcode 0x0E
    var dp = 0x100
    buf.put(dp++, 42.toByte()) // line_start = 42
    buf.put(dp++, 1.toByte()) // parameters_size = 1
    buf.put(dp++, 8.toByte()) // uleb128p1(7) = 8 ("x")
    buf.put(dp++, 0x0E.toByte()) // special opcode: addr+=0, line+=0 -> emits (0x0000, 42)
    buf.put(dp, 0x00.toByte()) // DBG_END_SEQUENCE

    // method_handle_item @ 0x120: kind=4 (INVOKE_STATIC), member=0 ("run")
    buf.putShort(0x120, 4)
    buf.putShort(0x122, 0)
    buf.putShort(0x124, 0)
    buf.putShort(0x126, 0)

    // call_site_id_item @ 0x130 -> encoded_array @ 0x138
    buf.putInt(0x130, 0x138)
    // encoded_array @ 0x138: size=3; [0]=METHOD_HANDLE(0x16, idx=0); [1]=STRING(0x17, idx=6="run"); [2]=METHOD_TYPE(0x15, idx=0)
    var cp = 0x138
    buf.put(cp++, 3.toByte())
    buf.put(cp++, 0x16.toByte()) // VALUE_METHOD_HANDLE (1 byte)
    buf.put(cp++, 0x00.toByte())
    buf.put(cp++, 0x17.toByte()) // VALUE_STRING (1 byte)
    buf.put(cp++, 6.toByte())
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
