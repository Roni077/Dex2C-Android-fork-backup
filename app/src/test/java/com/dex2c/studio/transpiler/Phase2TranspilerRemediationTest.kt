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
import com.dex2c.studio.dex.DexParser
import com.dex2c.studio.dex.DexProto
import com.dex2c.studio.dex.DexTryBlock
import com.dex2c.studio.dex.OpCategory
import com.dex2c.studio.dex.SmaliJavaDecompiler
import com.dex2c.studio.dex.parseClassDescriptor
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase2TranspilerRemediationTest {

  @Test
  fun testPackedSwitchGenerationNonFlattenedAndFlattened() {
    val payload = DalvikPayload.PackedSwitch(
      offsetWords = 10,
      wordCount = 8,
      firstKey = 1,
      targetOffsets = listOf(20, 25, 30),
    )
    val insn = DalvikInstruction(
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
      payload = payload,
    )
    val returnInsn = DalvikInstruction(
      offsetWords = 7, widthWords = 1, opcode = 0x0E, mnemonic = "return-void",
      format = "10x", operandsText = "", rawWords = listOf(0x0E), category = OpCategory.BRANCH,
    )
    val target20 = DalvikInstruction(
      offsetWords = 20, widthWords = 1, opcode = 0x0E, mnemonic = "return-void",
      format = "10x", operandsText = "", rawWords = listOf(0x0E), category = OpCategory.BRANCH,
    )
    val target25 = DalvikInstruction(
      offsetWords = 25, widthWords = 1, opcode = 0x0E, mnemonic = "return-void",
      format = "10x", operandsText = "", rawWords = listOf(0x0E), category = OpCategory.BRANCH,
    )
    val target30 = DalvikInstruction(
      offsetWords = 30, widthWords = 1, opcode = 0x0E, mnemonic = "return-void",
      format = "10x", operandsText = "", rawWords = listOf(0x0E), category = OpCategory.BRANCH,
    )

    val insnsList = listOf(insn, returnInsn, target20, target25, target30)
    val method = buildSyntheticMethod(
      methodName = "testSwitch",
      returnType = "V",
      insns = insnsList,
    )
    val parsed = buildSyntheticDex(listOf(method))

    // Non-flattened mode:
    val nonFlatCode = Dex2cTranspiler.transpileMethodToC(
      dex = parsed,
      method = method,
      instructions = insnsList,
      config = TranspilerConfig(controlFlowFlattening = false),
    )
    assertTrue("Should contain switch on register v[0].i", nonFlatCode.contains("switch (v[0].i) {"))
    assertTrue("Should branch to L_0014 for case 1", nonFlatCode.contains("case 1: goto L_0014;"))
    assertTrue("Should branch to L_0019 for case 2", nonFlatCode.contains("case 2: goto L_0019;"))
    assertTrue("Should branch to L_001e for case 3", nonFlatCode.contains("case 3: goto L_001e;"))
    assertTrue("Should include target labels in branchTargets", nonFlatCode.contains("L_0014:"))
    assertTrue("Should include target labels in branchTargets", nonFlatCode.contains("L_0019:"))
    assertTrue("Should include target labels in branchTargets", nonFlatCode.contains("L_001e:"))
    assertTrue("Should have default break", nonFlatCode.contains("default: break;"))

    // Flattened mode:
    val flatCode = Dex2cTranspiler.transpileMethodToC(
      dex = parsed,
      method = method,
      instructions = insnsList,
      config = TranspilerConfig(controlFlowFlattening = true),
    )
    assertTrue("Flattened mode should have __cf_continue label", flatCode.contains("__cf_continue:"))
    assertTrue("Flattened mode case 1 should update __cf_pc and jump to __cf_continue",
      flatCode.contains("case 1: { __cf_pc = 0x0014u; goto __cf_continue; }"))
    assertTrue("Flattened mode case 2 should update __cf_pc and jump to __cf_continue",
      flatCode.contains("case 2: { __cf_pc = 0x0019u; goto __cf_continue; }"))
    assertTrue("Flattened mode case 3 should update __cf_pc and jump to __cf_continue",
      flatCode.contains("case 3: { __cf_pc = 0x001Eu; goto __cf_continue; }"))
  }

  @Test
  fun testSparseSwitchGeneration() {
    val payload = DalvikPayload.SparseSwitch(
      offsetWords = 10,
      wordCount = 8,
      cases = listOf(10 to 40, 25 to 50, 100 to 60),
    )
    val insn = DalvikInstruction(
      offsetWords = 4,
      widthWords = 3,
      opcode = 0x2C,
      mnemonic = "sparse-switch",
      format = "31t",
      operandsText = "v1, :sswitch_data_0",
      rawWords = listOf(0x2C, 1, 6),
      category = OpCategory.BRANCH,
      regA = 1,
      targetOffset = 10,
      payload = payload,
    )
    val returnInsn = DalvikInstruction(
      offsetWords = 7, widthWords = 1, opcode = 0x0E, mnemonic = "return-void",
      format = "10x", operandsText = "", rawWords = listOf(0x0E), category = OpCategory.BRANCH,
    )
    val target40 = DalvikInstruction(
      offsetWords = 40, widthWords = 1, opcode = 0x0E, mnemonic = "return-void",
      format = "10x", operandsText = "", rawWords = listOf(0x0E), category = OpCategory.BRANCH,
    )
    val target50 = DalvikInstruction(
      offsetWords = 50, widthWords = 1, opcode = 0x0E, mnemonic = "return-void",
      format = "10x", operandsText = "", rawWords = listOf(0x0E), category = OpCategory.BRANCH,
    )
    val target60 = DalvikInstruction(
      offsetWords = 60, widthWords = 1, opcode = 0x0E, mnemonic = "return-void",
      format = "10x", operandsText = "", rawWords = listOf(0x0E), category = OpCategory.BRANCH,
    )

    val insnsList = listOf(insn, returnInsn, target40, target50, target60)
    val method = buildSyntheticMethod(
      methodName = "testSparseSwitch",
      returnType = "V",
      insns = insnsList,
    )
    val parsed = buildSyntheticDex(listOf(method))
    val code = Dex2cTranspiler.transpileMethodToC(
      dex = parsed,
      method = method,
      instructions = insnsList,
      config = TranspilerConfig(controlFlowFlattening = false),
    )

    assertTrue(code.contains("switch (v[1].i) {"))
    assertTrue(code.contains("case 10: goto L_0028;"))
    assertTrue(code.contains("case 25: goto L_0032;"))
    assertTrue(code.contains("case 100: goto L_003c;"))
    assertTrue(code.contains("L_0028:"))
    assertTrue(code.contains("L_0032:"))
    assertTrue(code.contains("L_003c:"))
  }

  @Test
  fun testJniExceptionGuardMacrosReturnFlow() {
    val bundle = Dex2cTranspiler.transpileBundle(
      buildSyntheticDex(emptyList()),
      emptyList(),
      TranspilerConfig(nullCheckGuards = true),
    )
    val header = bundle.headerFile.content

    // Macro definitions must pop frame and return appropriately
    assertTrue(header.contains("#define DEX2C_CHECK_NULL(env, ref) do {"))
    assertTrue(header.contains("return 0;"))
    assertTrue(header.contains("#define DEX2C_CHECK_NULL_VOID(env, ref) do {"))
    assertTrue(header.contains("return;"))
    assertTrue(header.contains("#define DEX2C_THROW_CCE(env) do {"))
    assertTrue(header.contains("#define DEX2C_THROW_CCE_VOID(env) do {"))
    assertTrue(header.contains("DEX2C_POP_FRAME(env, NULL);"))

    // Test emission in void vs non-void methods
    val voidInsns = listOf(
      DalvikInstruction(
        offsetWords = 0, widthWords = 2, opcode = 0x21, mnemonic = "array-length",
        format = "12x", operandsText = "v0, v1", rawWords = listOf(0x21), category = OpCategory.ARRAY,
        regA = 0, regB = 1,
      ),
      DalvikInstruction(
        offsetWords = 2, widthWords = 1, opcode = 0x0E, mnemonic = "return-void",
        format = "10x", operandsText = "", rawWords = listOf(0x0E), category = OpCategory.BRANCH,
      ),
    )
    val voidMethod = buildSyntheticMethod(
      methodName = "voidMethod",
      returnType = "V",
      insns = voidInsns,
    )

    val intInsns = listOf(
      DalvikInstruction(
        offsetWords = 0, widthWords = 2, opcode = 0x21, mnemonic = "array-length",
        format = "12x", operandsText = "v0, v1", rawWords = listOf(0x21), category = OpCategory.ARRAY,
        regA = 0, regB = 1,
      ),
      DalvikInstruction(
        offsetWords = 2, widthWords = 2, opcode = 0x0F, mnemonic = "return",
        format = "11x", operandsText = "v0", rawWords = listOf(0x0F), category = OpCategory.BRANCH,
        regA = 0,
      ),
    )
    val intMethod = buildSyntheticMethod(
      methodName = "intMethod",
      returnType = "I",
      insns = intInsns,
    )
    val parsed = buildSyntheticDex(listOf(voidMethod, intMethod))

    val voidCode = Dex2cTranspiler.transpileMethodToC(
      dex = parsed,
      method = voidMethod,
      instructions = voidInsns,
      config = TranspilerConfig(nullCheckGuards = true),
    )
    assertTrue("Void method must use DEX2C_CHECK_NULL_VOID", voidCode.contains("DEX2C_CHECK_NULL_VOID(env, v[1].l);"))

    val intCode = Dex2cTranspiler.transpileMethodToC(
      dex = parsed,
      method = intMethod,
      instructions = intInsns,
      config = TranspilerConfig(nullCheckGuards = true),
    )
    assertTrue("Non-void method must use DEX2C_CHECK_NULL", intCode.contains("DEX2C_CHECK_NULL(env, v[1].l);"))
  }

  @Test
  fun testExceptionCatchDispatchJniCompliance() {
    val invokeInsn = DalvikInstruction(
      offsetWords = 2, widthWords = 3, opcode = 0x6E, mnemonic = "invoke-virtual",
      format = "35c", operandsText = "{v0}, Ljava/lang/Object;->toString()Ljava/lang/String;",
      rawWords = listOf(0x6E, 0, 0), category = OpCategory.INVOKE,
      regList = listOf(0),
      methodRef = DexMethodRef(
        definingClass = "Ljava/lang/Object;", name = "toString",
        proto = DexProto(shorty = "L", returnType = "Ljava/lang/String;", parameterTypes = emptyList()),
      ),
    )
    val moveExcInsn = DalvikInstruction(
      offsetWords = 5, widthWords = 1, opcode = 0x0D, mnemonic = "move-exception",
      format = "11x", operandsText = "v1", rawWords = listOf(0x0D), category = OpCategory.MOVE,
      regA = 1,
    )
    val returnInsn = DalvikInstruction(
      offsetWords = 6, widthWords = 1, opcode = 0x0E, mnemonic = "return-void",
      format = "10x", operandsText = "", rawWords = listOf(0x0E), category = OpCategory.BRANCH,
    )

    val tryBlock = DexTryBlock(
      startOffsetWord = 0,
      insnCount = 5,
      handlers = listOf(
        DexCatchHandler(typeDescriptor = "Ljava/io/IOException;", handlerOffsetWord = 5),
      ),
    )

    val insnsList = listOf(invokeInsn, moveExcInsn, returnInsn)
    val method = buildSyntheticMethod(
      methodName = "testCatch",
      returnType = "V",
      insns = insnsList,
      tryBlocks = listOf(tryBlock),
    )
    val parsed = buildSyntheticDex(listOf(method))
    val code = Dex2cTranspiler.transpileMethodToC(
      dex = parsed,
      method = method,
      instructions = insnsList,
      tryBlocks = listOf(tryBlock),
      config = TranspilerConfig(),
    )

    // JNI Spec check: ExceptionClear() MUST be called BEFORE FindClass for catch handler
    val excOccurredIdx = code.indexOf("ExceptionOccurred")
    val excClearIdx = code.indexOf("ExceptionClear")
    val findCatchClassIdx = code.indexOf("FindClass(\"java/io/IOException\")")
    val throwIdx = code.indexOf("Throw(__exc)")

    assertTrue("ExceptionOccurred must be present", excOccurredIdx >= 0)
    assertTrue("ExceptionClear must be present", excClearIdx >= 0)
    assertTrue("FindClass for catch handler must be present", findCatchClassIdx >= 0)
    assertTrue("Throw must re-throw uncaught exception", throwIdx >= 0)
    assertTrue("ExceptionClear must precede catch FindClass", excClearIdx < findCatchClassIdx)
    assertTrue("move-exception must assign __exc", code.contains("v[1].l = __exc;"))
  }

  @Test
  fun testInvokeDirectUsesCallNonvirtualMethod() {
    val initMethodRef = DexMethodRef(
      definingClass = "Ljava/lang/StringBuilder;", name = "<init>",
      proto = DexProto(shorty = "V", returnType = "V", parameterTypes = emptyList()),
    )
    val invokeDirect = DalvikInstruction(
      offsetWords = 2, widthWords = 3, opcode = 0x70, mnemonic = "invoke-direct",
      format = "35c", operandsText = "{v0}, Ljava/lang/StringBuilder;-><init>()V",
      rawWords = listOf(0x70, 0, 0), category = OpCategory.INVOKE,
      regList = listOf(0), methodRef = initMethodRef,
    )
    val returnInsn = DalvikInstruction(
      offsetWords = 5, widthWords = 1, opcode = 0x0E, mnemonic = "return-void",
      format = "10x", operandsText = "", rawWords = listOf(0x0E), category = OpCategory.BRANCH,
    )

    val insnsList = listOf(invokeDirect, returnInsn)
    val method = buildSyntheticMethod(
      methodName = "callConstructor", returnType = "V",
      insns = insnsList,
    )
    val parsed = buildSyntheticDex(listOf(method))
    val code = Dex2cTranspiler.transpileMethodToC(
      dex = parsed,
      method = method,
      instructions = insnsList,
      config = TranspilerConfig(targetLanguage = TargetCLanguage.CPP17),
    )

    assertTrue("invoke-direct must use CallNonvirtualVoidMethod", code.contains("CallNonvirtualVoidMethod"))
    assertFalse("invoke-direct must NOT use virtual CallVoidMethod", code.contains("env->CallVoidMethod"))
    assertTrue("Must pass receiver and __mcls to non-virtual call", code.contains("v[0].l, __mcls, __mid"))
  }

  @Test
  fun testWideArrayAccessDoubleVsLongDifferentiation() {
    val agetWideDouble = DalvikInstruction(
      offsetWords = 2, widthWords = 2, opcode = 0x45, mnemonic = "aget-wide",
      format = "23x", operandsText = "v0, v1, v2", rawWords = listOf(0x45), category = OpCategory.ARRAY,
      regA = 0, regB = 1, regC = 2,
    )
    val addDouble = DalvikInstruction(
      offsetWords = 4, widthWords = 2, opcode = 0xAB, mnemonic = "add-double",
      format = "23x", operandsText = "v0, v0, v0", rawWords = listOf(0xAB), category = OpCategory.MATH,
      regA = 0, regB = 0, regC = 0,
    )
    val returnWideDouble = DalvikInstruction(
      offsetWords = 6, widthWords = 2, opcode = 0x10, mnemonic = "return-wide",
      format = "11x", operandsText = "v0", rawWords = listOf(0x10), category = OpCategory.BRANCH,
      regA = 0,
    )

    val doubleInsns = listOf(agetWideDouble, addDouble, returnWideDouble)
    val doubleMethod = buildSyntheticMethod(
      methodName = "readDoubleArray", returnType = "D",
      paramTypes = listOf("[D", "I"),
      insns = doubleInsns,
    )

    val agetWideLong = DalvikInstruction(
      offsetWords = 2, widthWords = 2, opcode = 0x45, mnemonic = "aget-wide",
      format = "23x", operandsText = "v0, v1, v2", rawWords = listOf(0x45), category = OpCategory.ARRAY,
      regA = 0, regB = 1, regC = 2,
    )
    val addLong = DalvikInstruction(
      offsetWords = 4, widthWords = 2, opcode = 0x9B, mnemonic = "add-long",
      format = "23x", operandsText = "v0, v0, v0", rawWords = listOf(0x9B), category = OpCategory.MATH,
      regA = 0, regB = 0, regC = 0,
    )
    val returnWideLong = DalvikInstruction(
      offsetWords = 6, widthWords = 2, opcode = 0x10, mnemonic = "return-wide",
      format = "11x", operandsText = "v0", rawWords = listOf(0x10), category = OpCategory.BRANCH,
      regA = 0,
    )

    val longInsns = listOf(agetWideLong, addLong, returnWideLong)
    val longMethod = buildSyntheticMethod(
      methodName = "readLongArray", returnType = "J",
      paramTypes = listOf("[J", "I"),
      insns = longInsns,
    )

    val parsed = buildSyntheticDex(listOf(doubleMethod, longMethod))

    val doubleCode = Dex2cTranspiler.transpileMethodToC(
      dex = parsed,
      method = doubleMethod,
      instructions = doubleInsns,
      config = TranspilerConfig(),
    )
    assertTrue("double[] array must use GetDoubleArrayRegion", doubleCode.contains("GetDoubleArrayRegion"))
    assertTrue("double[] array must use v[0].d", doubleCode.contains("v[0].d = __tmp;"))

    val longCode = Dex2cTranspiler.transpileMethodToC(
      dex = parsed,
      method = longMethod,
      instructions = longInsns,
      config = TranspilerConfig(),
    )
    assertTrue("long[] array must use GetLongArrayRegion", longCode.contains("GetLongArrayRegion"))
    assertTrue("long[] array must use v[0].j", longCode.contains("v[0].j = __tmp;"))
  }

  @Test
  fun testStaticJniSymbolManglingForOverloads() {
    val m1Insns = listOf(DalvikInstruction(offsetWords = 0, widthWords = 1, opcode = 0x0E, mnemonic = "return-void", format = "10x", operandsText = "", rawWords = listOf(0x0E), category = OpCategory.BRANCH))
    val m1 = buildSyntheticMethod(
      methodName = "process", returnType = "I", paramTypes = listOf("I"),
      definingClass = "Lcom/example/Processor;",
      insns = m1Insns,
    )
    val m2Insns = listOf(DalvikInstruction(offsetWords = 0, widthWords = 1, opcode = 0x0E, mnemonic = "return-void", format = "10x", operandsText = "", rawWords = listOf(0x0E), category = OpCategory.BRANCH))
    val m2 = buildSyntheticMethod(
      methodName = "process", returnType = "I", paramTypes = listOf("I", "Ljava/lang/String;"),
      definingClass = "Lcom/example/Processor;",
      insns = m2Insns,
    )
    val m3Insns = listOf(DalvikInstruction(offsetWords = 0, widthWords = 1, opcode = 0x0E, mnemonic = "return-void", format = "10x", operandsText = "", rawWords = listOf(0x0E), category = OpCategory.BRANCH))
    val m3 = buildSyntheticMethod(
      methodName = "process", returnType = "I", paramTypes = listOf("[B"),
      definingClass = "Lcom/example/Processor;",
      insns = m3Insns,
    )
    val nonOverloadedInsns = listOf(DalvikInstruction(offsetWords = 0, widthWords = 1, opcode = 0x0E, mnemonic = "return-void", format = "10x", operandsText = "", rawWords = listOf(0x0E), category = OpCategory.BRANCH))
    val nonOverloaded = buildSyntheticMethod(
      methodName = "uniqueName", returnType = "V", paramTypes = listOf("I"),
      definingClass = "Lcom/example/Processor;",
      insns = nonOverloadedInsns,
    )

    val parsed = buildSyntheticDex(listOf(m1, m2, m3, nonOverloaded))
    val cfg = TranspilerConfig(jniStyle = JniBindingStyle.STATIC_SYMBOLS)

    val c1 = Dex2cTranspiler.transpileMethodToC(parsed, m1, m1Insns, emptyList(), cfg)
    assertTrue("Overloaded m1 must mangle argument signature __I", c1.contains("Java_com_example_Processor_process__I"))

    val c2 = Dex2cTranspiler.transpileMethodToC(parsed, m2, m2Insns, emptyList(), cfg)
    assertTrue("Overloaded m2 must mangle argument signature __ILjava_lang_String_2",
      c2.contains("Java_com_example_Processor_process__ILjava_lang_String_2"))

    val c3 = Dex2cTranspiler.transpileMethodToC(parsed, m3, m3Insns, emptyList(), cfg)
    assertTrue("Overloaded m3 with byte[] must mangle to ___3B",
      c3.contains("Java_com_example_Processor_process___3B"))

    val cNon = Dex2cTranspiler.transpileMethodToC(parsed, nonOverloaded, nonOverloadedInsns, emptyList(), cfg)
    assertTrue("Non-overloaded method must NOT append signature",
      cNon.contains("Java_com_example_Processor_uniqueName("))
    assertFalse("Non-overloaded method must NOT have __",
      cNon.contains("Java_com_example_Processor_uniqueName__"))
  }

  @Test
  fun testLocalReferenceFrameScoping() {
    val insnsList = listOf(
      DalvikInstruction(
        offsetWords = 0, widthWords = 2, opcode = 0x11, mnemonic = "return-object",
        format = "11x", operandsText = "v0", rawWords = listOf(0x11), category = OpCategory.BRANCH,
        regA = 0,
      ),
    )
    val returnObjectMethod = buildSyntheticMethod(
      methodName = "getObject", returnType = "Ljava/lang/String;",
      insns = insnsList,
    )
    val parsed = buildSyntheticDex(listOf(returnObjectMethod))
    val code = Dex2cTranspiler.transpileMethodToC(
      dex = parsed,
      method = returnObjectMethod,
      instructions = insnsList,
      config = TranspilerConfig(),
    )

    assertTrue("Must call PushLocalFrame(64) at entry", code.contains("PushLocalFrame(64)"))
    assertTrue("return-object must return PopLocalFrame(v[0].l)", code.contains("PopLocalFrame(v[0].l)"))
  }

  @Test
  fun testSmaliJavaDecompilerPreviewContainsBytecode() {
    val dexBytes = buildMinimalDexBytes()
    val parsed = DexParser.parseDex(dexBytes, "unit_test.dex", "classes.dex")
    val cls = parsed.classes.first()

    val decompiledJava = SmaliJavaDecompiler.decompileClassToJava(parsed, cls)
    assertFalse("Java preview must NOT output dummy placeholder", decompiledJava.contains("// TODO: method body"))
    assertTrue("Java preview must include disassembled Dalvik comment", decompiledJava.contains("// [0x0000]"))
  }

  // --- Synthetic Test Builders ---

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
    }.ifEmpty {
      val p = parseClassDescriptor("Lcom/example/Empty;")
      listOf(
        DexClassInfo(
          classIndex = 0,
          descriptor = "Lcom/example/Empty;",
          fullName = p.fullName,
          simpleName = p.simpleName,
          packageName = p.packageName,
          superclass = "Ljava/lang/Object;",
          interfaces = emptyList(),
          sourceFile = "Empty.java",
          accessFlags = 0x0001,
          staticFields = emptyList(),
          instanceFields = emptyList(),
          directMethods = emptyList(),
          virtualMethods = emptyList(),
        )
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

  private fun buildMinimalDexBytes(): ByteArray {
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
