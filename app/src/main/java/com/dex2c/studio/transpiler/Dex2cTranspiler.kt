package com.dex2c.studio.transpiler

import com.dex2c.studio.dex.DalvikDisassembler
import com.dex2c.studio.dex.DalvikInstruction
import com.dex2c.studio.dex.DalvikPayload
import com.dex2c.studio.dex.DexEncodedMethod
import com.dex2c.studio.dex.DexParsedFile
import com.dex2c.studio.dex.DexTryBlock
import com.dex2c.studio.dex.descriptorToJavaName
import java.io.BufferedWriter
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.util.Locale

object Dex2cTranspiler {

  const val DEFAULT_METHODS_PER_CHUNK = 250
  private const val MAX_PREVIEW_LINES = 450
  private const val MAX_PREVIEW_CHARS = 48 * 1024
  private const val MAX_WARNINGS = 150

  private data class NativeRegRecord(
    val definingClass: String,
    val methodName: String,
    val jniSignature: String,
    val cFuncName: String,
    val forwardPrototype: String,
  )

  private class StreamedFileBuilder(
    val backingFile: File,
    val fileName: String,
    val zipPath: String,
    val description: String,
    private val maxPreviewLines: Int = MAX_PREVIEW_LINES,
    private val maxPreviewChars: Int = MAX_PREVIEW_CHARS,
  ) : Appendable, Closeable {
    private val writer =
      BufferedWriter(
        OutputStreamWriter(FileOutputStream(backingFile), Charsets.UTF_8),
        16 * 1024,
      )
    private val preview = StringBuilder(minOf(maxPreviewChars, 8192))
    private var lineCount = 0
    private var previewLines = 0
    private var lastCharWasNewline = true
    private var hasAnyChar = false
    var isPreviewTruncated: Boolean = false
      private set

    override fun append(csq: CharSequence?): Appendable {
      val text = csq ?: "null"
      return append(text, 0, text.length)
    }

    override fun append(csq: CharSequence?, start: Int, end: Int): Appendable {
      val text = csq ?: "null"
      writer.write(text.toString(), start, end)
      for (i in start until end) {
        val ch = text[i]
        hasAnyChar = true
        if (ch == '\n') {
          lineCount++
          lastCharWasNewline = true
        } else {
          lastCharWasNewline = false
        }
        if (!isPreviewTruncated) {
          if (previewLines < maxPreviewLines && preview.length < maxPreviewChars) {
            preview.append(ch)
            if (ch == '\n') {
              previewLines++
              if (previewLines >= maxPreviewLines) {
                isPreviewTruncated = true
              }
            }
          } else {
            isPreviewTruncated = true
          }
        }
      }
      return this
    }

    override fun append(c: Char): Appendable {
      writer.write(c.code)
      hasAnyChar = true
      if (c == '\n') {
        lineCount++
        lastCharWasNewline = true
      } else {
        lastCharWasNewline = false
      }
      if (!isPreviewTruncated) {
        if (previewLines < maxPreviewLines && preview.length < maxPreviewChars) {
          preview.append(c)
          if (c == '\n') {
            previewLines++
            if (previewLines >= maxPreviewLines) {
              isPreviewTruncated = true
            }
          }
        } else {
          isPreviewTruncated = true
        }
      }
      return this
    }

    private var closed = false

    override fun close() {
      if (closed) return
      closed = true
      writer.flush()
      writer.close()
    }

    fun finish(): GeneratedFile {
      close()
      val totalLines =
        if (!hasAnyChar) 0
        else if (lastCharWasNewline) lineCount
        else lineCount + 1
      return GeneratedFile(
        fileName = fileName,
        zipPath = zipPath,
        description = description,
        content = preview.toString(),
        backingFile = backingFile,
        totalByteSize = backingFile.length(),
        totalLineCount = totalLines,
        isPreviewTruncated = isPreviewTruncated,
      )
    }
  }

  fun transpileMethodToC(
    dex: DexParsedFile,
    method: DexEncodedMethod,
    config: TranspilerConfig,
  ): String {
    val instructions = DalvikDisassembler.disassemble(dex, method)
    val tryBlocks = DalvikDisassembler.parseTryBlocks(dex, method)
    return transpileMethodToC(dex, method, instructions, tryBlocks, config)
  }

  fun transpileMethodToC(
    dex: DexParsedFile,
    method: DexEncodedMethod,
    instructions: List<DalvikInstruction>,
    tryBlocks: List<DexTryBlock> = emptyList(),
    config: TranspilerConfig,
  ): String {
    val isOverloaded =
      dex.classes.find { it.descriptor == method.methodRef.definingClass }
        ?.let { cls -> (cls.directMethods + cls.virtualMethods).count { it.methodRef.name == method.methodRef.name } > 1 }
        ?: false
    val sb = StringBuilder()
    emitMethodBody(
      out = sb,
      method = method,
      insns = instructions,
      tryBlocks = tryBlocks,
      config = config,
      methodIndexHint = 0,
      crossUnitLinkage = false,
      isOverloaded = isOverloaded,
    )
    return sb.toString()
  }

  fun transpileBundle(
    dex: DexParsedFile,
    selectedMethods: List<DexEncodedMethod>,
    config: TranspilerConfig,
    outputDir: File? = null,
    methodsPerChunk: Int = DEFAULT_METHODS_PER_CHUNK,
    onProgress: ((TranspileProgress) -> Unit)? = null,
  ): GeneratedBundle =
    transpileBundleMultiDex(
      entries = selectedMethods.map { dex to it },
      config = config,
      sourceLabelHint = dex.sourceLabel,
      outputDir = outputDir,
      methodsPerChunk = methodsPerChunk,
      onProgress = onProgress,
    )

  fun transpileBundleMultiDex(
    entries: List<Pair<DexParsedFile, DexEncodedMethod>>,
    config: TranspilerConfig,
    sourceLabelHint: String? = null,
    outputDir: File? = null,
    methodsPerChunk: Int = DEFAULT_METHODS_PER_CHUNK,
    onProgress: ((TranspileProgress) -> Unit)? = null,
  ): GeneratedBundle {
    val safeChunkSize = methodsPerChunk.coerceAtLeast(1)
    val bundleDir = resolveBundleOutputDir(outputDir)
    val moduleName = sanitizeModuleName(config.moduleName)
    val sourceExt = config.targetLanguage.extension
    val warnings = mutableListOf<String>()
    var suppressedWarnings = 0
    var totalInstructions = 0

    fun recordWarning(msg: String) {
      if (warnings.size < MAX_WARNINGS) {
        warnings += msg
      } else {
        suppressedWarnings++
      }
    }

    val filteredEntries =
      if (config.allowInit) {
        entries
      } else {
        var skipped = 0
        val list = ArrayList<Pair<DexParsedFile, DexEncodedMethod>>(entries.size)
        for (pair in entries) {
          if (pair.second.isInitConstructor) {
            skipped++
          } else {
            list.add(pair)
          }
        }
        if (skipped > 0) {
          recordWarning("Skipped $skipped <init> constructor(s) because --allow-init is disabled.")
        }
        list
      }

    val distinctDexSummary =
      entries
        .asSequence()
        .map { it.first }
        .distinctBy { it.entryName }
        .joinToString(", ") { "${it.entryName} (v${it.header.version})" }
        .ifBlank { "none" }
    val resolvedSourceLabel = sourceLabelHint ?: entries.firstOrNull()?.first?.sourceLabel ?: "multi-dex"

    val totalMethods = filteredEntries.size
    val isMultiChunk = totalMethods > safeChunkSize
    val chunks =
      if (totalMethods == 0) listOf(emptyList())
      else filteredEntries.chunked(safeChunkSize)
    val totalChunks = chunks.size.coerceAtLeast(1)

    onProgress?.invoke(
      TranspileProgress(
        completedMethods = 0,
        totalMethods = totalMethods,
        activeChunkIndex = 1,
        totalChunks = totalChunks,
      )
    )

    val registrationRecords = ArrayList<NativeRegRecord>(totalMethods)
    var globalMethodCounter = 0

    val generatedSourceFiles = mutableListOf<GeneratedFile>()

    if (!isMultiChunk) {
      val singleFileName = "dex2c_bridge.$sourceExt"
      val builder =
        StreamedFileBuilder(
          backingFile = File(bundleDir, singleFileName),
          fileName = singleFileName,
          zipPath = "jni/$singleFileName",
          description = "Transpiled C/C++ methods & JNI registration",
        )
      builder.use { out ->
        out.appendLine("// Generated by Dex2C Studio (codehasan/dex2c compatible)")
        out.appendLine("// Source DEX: $resolvedSourceLabel [$distinctDexSummary]")
        out.appendLine("// Flags: allow_init=${config.allowInit}, dynamic_register=${config.jniStyle == JniBindingStyle.REGISTER_NATIVES}, lib=$moduleName")
        out.appendLine("// Methods transpiled: $totalMethods")
        out.appendLine()
        out.appendLine("#include \"dex2c_runtime.h\"")
        out.appendLine()

        filteredEntries.forEachIndexed { idx, (dex, method) ->
          if (!method.hasBytecode) {
            recordWarning("${descriptorToJavaName(method.methodRef.definingClass)}.${method.methodRef.name} has no Dalvik code_item (abstract/native).")
          }
          val insns = DalvikDisassembler.disassemble(dex, method)
          val tries = DalvikDisassembler.parseTryBlocks(dex, method)
          totalInstructions += insns.size
          val isOverloaded =
            dex.classes.find { it.descriptor == method.methodRef.definingClass }
              ?.let { cls -> (cls.directMethods + cls.virtualMethods).count { it.methodRef.name == method.methodRef.name } > 1 }
              ?: (filteredEntries.count { it.second.methodRef.definingClass == method.methodRef.definingClass && it.second.methodRef.name == method.methodRef.name } > 1)
          val regRecord =
            emitMethodBody(
              out = out,
              method = method,
              insns = insns,
              tryBlocks = tries,
              config = config,
              methodIndexHint = globalMethodCounter++,
              crossUnitLinkage = false,
              isOverloaded = isOverloaded,
            )
          out.appendLine()
          registrationRecords.add(regRecord)
          if ((idx + 1) % 25 == 0 || idx + 1 == totalMethods) {
            onProgress?.invoke(
              TranspileProgress(
                completedMethods = idx + 1,
                totalMethods = totalMethods,
                activeChunkIndex = 1,
                totalChunks = 1,
              )
            )
          }
        }

        if (config.jniStyle == JniBindingStyle.REGISTER_NATIVES) {
          emitJniOnLoadRegistration(out, registrationRecords, config, emitForwardPrototypes = false)
        }
      }
      generatedSourceFiles += builder.finish()
    } else {
      val partFiles = mutableListOf<GeneratedFile>()
      chunks.forEachIndexed { chunkIdx, chunkEntries ->
        val partNumber = chunkIdx + 1
        val partFileName = String.format(Locale.US, "dex2c_bridge_part_%02d.%s", partNumber, sourceExt)
        val firstIndex = chunkIdx * safeChunkSize + 1
        val lastIndex = firstIndex + chunkEntries.size - 1
        val partBuilder =
          StreamedFileBuilder(
            backingFile = File(bundleDir, partFileName),
            fileName = partFileName,
            zipPath = "jni/$partFileName",
            description = "Modular C/C++ translation unit #$partNumber (methods $firstIndex–$lastIndex of $totalMethods)",
          )
        partBuilder.use { out ->
          out.appendLine("// Generated by Dex2C Studio — Modular Part $partNumber / $totalChunks")
          out.appendLine("// Source DEX: $resolvedSourceLabel [$distinctDexSummary]")
          out.appendLine("// Methods in unit: ${chunkEntries.size} (range $firstIndex..$lastIndex of $totalMethods)")
          out.appendLine()
          out.appendLine("#include \"dex2c_runtime.h\"")
          out.appendLine()

          chunkEntries.forEach { (dex, method) ->
            if (!method.hasBytecode) {
              recordWarning("${descriptorToJavaName(method.methodRef.definingClass)}.${method.methodRef.name} has no Dalvik code_item (abstract/native).")
            }
            val insns = DalvikDisassembler.disassemble(dex, method)
            val tries = DalvikDisassembler.parseTryBlocks(dex, method)
            totalInstructions += insns.size
            val isOverloaded =
              dex.classes.find { it.descriptor == method.methodRef.definingClass }
                ?.let { cls -> (cls.directMethods + cls.virtualMethods).count { it.methodRef.name == method.methodRef.name } > 1 }
                ?: (filteredEntries.count { it.second.methodRef.definingClass == method.methodRef.definingClass && it.second.methodRef.name == method.methodRef.name } > 1)
            val regRecord =
              emitMethodBody(
                out = out,
                method = method,
                insns = insns,
                tryBlocks = tries,
                config = config,
                methodIndexHint = globalMethodCounter++,
                crossUnitLinkage = true,
                isOverloaded = isOverloaded,
              )
            out.appendLine()
            registrationRecords.add(regRecord)
            if (globalMethodCounter % 32 == 0 || globalMethodCounter == totalMethods) {
              onProgress?.invoke(
                TranspileProgress(
                  completedMethods = globalMethodCounter,
                  totalMethods = totalMethods,
                  activeChunkIndex = partNumber,
                  totalChunks = totalChunks,
                )
              )
            }
          }
        }
        partFiles += partBuilder.finish()
      }

      val coordinatorName = "dex2c_bridge.$sourceExt"
      val coordinatorBuilder =
        StreamedFileBuilder(
          backingFile = File(bundleDir, coordinatorName),
          fileName = coordinatorName,
          zipPath = "jni/$coordinatorName",
          description = "JNI coordinator & RegisterNatives dispatch across $totalChunks C/C++ units",
        )
      coordinatorBuilder.use { out ->
        out.appendLine("// Generated by Dex2C Studio (codehasan/dex2c compatible)")
        out.appendLine("// Source DEX: $resolvedSourceLabel [$distinctDexSummary]")
        out.appendLine("// Flags: allow_init=${config.allowInit}, dynamic_register=${config.jniStyle == JniBindingStyle.REGISTER_NATIVES}, lib=$moduleName")
        out.appendLine("// Methods transpiled: $totalMethods across $totalChunks modular C/C++ translation units")
        out.appendLine()
        out.appendLine("#include \"dex2c_runtime.h\"")
        out.appendLine()

        if (config.jniStyle == JniBindingStyle.REGISTER_NATIVES) {
          emitJniOnLoadRegistration(out, registrationRecords, config, emitForwardPrototypes = true)
        } else {
          out.appendLine("// Static JNI symbols are exported across ${partFiles.size} part files:")
          partFiles.forEach { pf ->
            out.appendLine("//   - ${pf.fileName}")
          }
        }
      }
      generatedSourceFiles += coordinatorBuilder.finish()
      generatedSourceFiles.addAll(partFiles)
    }

    if (suppressedWarnings > 0) {
      warnings += "...and $suppressedWarnings additional warning(s) omitted for memory safety."
    }

    val allSourceFileNames = generatedSourceFiles.map { it.fileName }
    val header = buildRuntimeHeader(moduleName)
    val androidMk = buildAndroidMk(moduleName, allSourceFileNames, sourceExt, config)
    val applicationMk = buildApplicationMk(config)
    val cmake = buildCMake(moduleName, allSourceFileNames)
    val dccCfg = DccConfigCodec.encodeDccCfg(config)
    val filterTxt = config.filterRulesText.trimEnd() + "\n"
    val loaderStubFile = streamLoaderStubFile(bundleDir, config, moduleName, filteredEntries)

    return GeneratedBundle(
      sourceFile = generatedSourceFiles.first(),
      headerFile = GeneratedFile("dex2c_runtime.h", "jni/dex2c_runtime.h", "JNI runtime macros & string decoder", header),
      androidMkFile = GeneratedFile("Android.mk", "jni/Android.mk", "codehasan/dex2c ndk-build Android.mk script", androidMk),
      applicationMkFile = GeneratedFile("Application.mk", "jni/Application.mk", "NDK ABI & STL target configuration", applicationMk),
      cmakeFile = GeneratedFile("CMakeLists.txt", "CMakeLists.txt", "Android NDK CMake build script", cmake),
      dccCfgFile = GeneratedFile("dcc.cfg", "dcc.cfg", "d2c / dex2c JSON compiler configuration", dccCfg),
      filterTxtFile = GeneratedFile("filter.txt", "filter.txt", "Method & class inclusion/exclusion rules", filterTxt),
      loaderStubFile = loaderStubFile,
      transpiledMethodCount = totalMethods,
      instructionCount = totalInstructions,
      warnings = warnings,
      sourceFiles = generatedSourceFiles,
    )
  }

  private fun resolveBundleOutputDir(explicitDir: File?): File {
    val dir =
      explicitDir
        ?: File(System.getProperty("java.io.tmpdir", "."), "dex2c_bundle_stream")
    if (!dir.exists()) {
      dir.mkdirs()
    }
    return dir
  }

  private fun emitJniOnLoadRegistration(
    out: Appendable,
    registrationRecords: List<NativeRegRecord>,
    config: TranspilerConfig,
    emitForwardPrototypes: Boolean,
  ) {
    if (emitForwardPrototypes && registrationRecords.isNotEmpty()) {
      out.appendLine("// Forward prototypes for modular translation units")
      registrationRecords.forEach { rec ->
        out.appendLine(rec.forwardPrototype)
      }
      out.appendLine()
    }

    val merged = linkedMapOf<String, MutableList<NativeRegRecord>>()
    for (rec in registrationRecords) {
      merged.getOrPut(rec.definingClass) { mutableListOf() }.add(rec)
    }

    val tableNames = mutableListOf<Triple<String, String, Int>>()
    merged.entries.forEachIndexed { idx, (clsDesc, records) ->
      val jniCls = clsDesc.removePrefix("L").removeSuffix(";")
      val tableVar = "g_dex2c_methods_$idx"
      out.appendLine("static const JNINativeMethod $tableVar[] = {")
      records.forEach { rec ->
        out.appendLine("    {\"${escapeC(rec.methodName)}\", \"${escapeC(rec.jniSignature)}\", (void *)${rec.cFuncName}},")
      }
      out.appendLine("};")
      out.appendLine()
      tableNames += Triple(jniCls, tableVar, records.size)
    }

    out.appendLine("JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {")
    out.appendLine("    (void)reserved;")
    out.appendLine("    JNIEnv *env = NULL;")
    if (config.targetLanguage == TargetCLanguage.C11) {
      out.appendLine("    if ((*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_6) != JNI_OK || env == NULL) {")
    } else {
      out.appendLine("    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK || env == NULL) {")
    }
    out.appendLine("        return JNI_ERR;")
    out.appendLine("    }")
    tableNames.forEach { (jniCls, tableVar, count) ->
      out.appendLine("    {")
      out.appendLine("        jclass cls = ${jniCall(config, "FindClass", "\"${escapeC(jniCls)}\"")};")
      out.appendLine("        if (cls == NULL) return JNI_ERR;")
      out.appendLine("        if (${jniCall(config, "RegisterNatives", "cls, $tableVar, $count")} < 0) {")
      out.appendLine("            ${jniCall(config, "DeleteLocalRef", "cls")};")
      out.appendLine("            return JNI_ERR;")
      out.appendLine("        }")
      out.appendLine("        ${jniCall(config, "DeleteLocalRef", "cls")};")
      out.appendLine("    }")
    }
    out.appendLine("    return JNI_VERSION_1_6;")
    out.appendLine("}")
  }

  private fun emitMethodBody(
    out: Appendable,
    method: DexEncodedMethod,
    insns: List<DalvikInstruction>,
    tryBlocks: List<DexTryBlock>,
    config: TranspilerConfig,
    methodIndexHint: Int,
    crossUnitLinkage: Boolean,
    isOverloaded: Boolean = false,
  ): NativeRegRecord {
    val ref = method.methodRef
    val returnJniType = descriptorToJniType(ref.proto.returnType)
    val paramJniTypes = ref.proto.parameterTypes.map { descriptorToJniType(it) }
    val cFuncName =
      if (config.jniStyle == JniBindingStyle.STATIC_SYMBOLS) {
        mangleStaticJniSymbol(ref.definingClass, ref.name, ref.proto.parameterTypes, isOverloaded)
      } else {
        "dex2c_${sanitizeIdent(descriptorToJavaName(ref.definingClass))}_${sanitizeIdent(ref.name)}_$methodIndexHint"
      }

    val signatureParams = buildList {
      add("JNIEnv *env")
      add(if (method.isStatic) "jclass clazz" else "jobject thiz")
      paramJniTypes.forEachIndexed { idx, t -> add("$t arg$idx") }
    }
    val paramsJoined = signatureParams.joinToString(", ")

    val externPrefix =
      if (config.targetLanguage == TargetCLanguage.CPP17 &&
        (config.jniStyle == JniBindingStyle.STATIC_SYMBOLS || crossUnitLinkage)
      ) {
        "extern \"C\" "
      } else {
        ""
      }

    val linkage =
      when {
        config.jniStyle == JniBindingStyle.STATIC_SYMBOLS -> "JNIEXPORT $returnJniType JNICALL"
        crossUnitLinkage -> returnJniType
        else -> "static $returnJniType"
      }

    val forwardProto = "${externPrefix}$returnJniType $cFuncName($paramsJoined);"

    out.appendLine("// ${descriptorToJavaName(ref.definingClass)}.${ref.prettySignature()}")
    if (method.isInitConstructor) {
      out.appendLine("// NOTE: Constructor (<init>) transpiled via --allow-init")
    }
    out.append(externPrefix)
    out.appendLine("$linkage $cFuncName($paramsJoined) {")

    val meta = method.codeMeta
    if (meta == null || insns.isEmpty()) {
      out.appendLine("    // No Dalvik bytecode present in DEX (abstract/native stub)")
      if (!method.isStatic) out.appendLine("    (void)thiz;") else out.appendLine("    (void)clazz;")
      paramJniTypes.indices.forEach { out.appendLine("    (void)arg$it;") }
      out.appendLine(defaultReturnStmt(ref.proto.returnType))
      out.appendLine("}")
      return NativeRegRecord(
        definingClass = ref.definingClass,
        methodName = ref.name,
        jniSignature = ref.jniSignature(),
        cFuncName = cFuncName,
        forwardPrototype = forwardProto,
      )
    }

    val regCount = maxOf(meta.registersSize, 1)
    out.appendLine("    jvalue v[$regCount];")
    out.appendLine("    memset(v, 0, sizeof(v));")
    out.appendLine("    jvalue __res;")
    out.appendLine("    memset(&__res, 0, sizeof(__res));")
    out.appendLine("    jthrowable __exc = NULL;")
    out.appendLine("    (void)__exc;")
    if (method.isStatic) out.appendLine("    (void)clazz;")

    var regCursor = (meta.registersSize - meta.insSize).coerceAtLeast(0)
    if (!method.isStatic && regCursor < regCount) {
      out.appendLine("    v[$regCursor].l = thiz;")
      regCursor++
    }
    ref.proto.parameterTypes.forEachIndexed { idx, typeDesc ->
      if (regCursor < regCount) {
        val field = jvalueFieldForDescriptor(typeDesc)
        out.appendLine("    v[$regCursor].$field = arg$idx;")
        regCursor += if (typeDesc == "J" || typeDesc == "D") 2 else 1
      }
    }
    out.appendLine()
    out.appendLine("    if (${jniCall(config, "PushLocalFrame", "64")} != 0) {")
    out.appendLine("    ${defaultReturnStmt(ref.proto.returnType)}")
    out.appendLine("    }")

    val branchTargets = buildSet {
      insns.mapNotNullTo(this) { it.targetOffset }
      tryBlocks.forEach { tb -> tb.handlers.forEach { add(it.handlerOffsetWord) } }
      insns.forEach { insn ->
        when (val p = insn.payload) {
          is DalvikPayload.PackedSwitch -> addAll(p.targetOffsets)
          is DalvikPayload.SparseSwitch -> addAll(p.cases.map { it.second })
          else -> {}
        }
      }
    }

    if (config.controlFlowFlattening && insns.isNotEmpty()) {
      val firstOffset = insns.first().offsetWords
      out.appendLine("    uint32_t __cf_pc = ${formatHex4u(firstOffset)};")
      out.appendLine("    while (1) {")
      out.appendLine("__cf_continue:")
      out.appendLine("        switch (__cf_pc) {")
      insns.forEachIndexed { idx, insn ->
        val nextOffset = insns.getOrNull(idx + 1)?.offsetWords ?: -1
        val commentText = sanitizeCComment("${insn.mnemonic} ${insn.operandsText}".trim())
        out.appendLine("        case ${formatHex4u(insn.offsetWords)}: { /* $commentText */")
        emitInstructionC(
          out = out,
          insn = insn,
          allInsns = insns,
          method = method,
          config = config,
          indent = "            ",
          flattened = true,
          returnType = ref.proto.returnType,
        )
        emitTryCatchDispatchIfApplicable(
          out = out,
          insn = insn,
          tryBlocks = tryBlocks,
          config = config,
          indent = "            ",
          flattened = true,
          returnType = ref.proto.returnType,
        )
        if (insn.mnemonic != "return-void" && !insn.mnemonic.startsWith("return") && insn.mnemonic != "throw" && !insn.mnemonic.startsWith("goto")) {
          if (nextOffset >= 0) {
            out.appendLine("            __cf_pc = ${formatHex4u(nextOffset)};")
            out.appendLine("            break;")
          } else {
            out.appendLine("            goto __dex2c_exit;")
          }
        } else if (insn.mnemonic.startsWith("goto")) {
          out.appendLine("            break;")
        }
        out.appendLine("        }")
      }
      out.appendLine("        default:")
      out.appendLine("            goto __dex2c_exit;")
      out.appendLine("        }")
      out.appendLine("    }")
      out.appendLine("__dex2c_exit:")
      out.appendLine("    ${jniCall(config, "PopLocalFrame", "NULL")};")
      out.appendLine(defaultReturnStmt(ref.proto.returnType))
    } else {
      insns.forEach { insn ->
        if (insn.offsetWords in branchTargets) {
          out.appendLine("${formatLabelOffset(insn.offsetWords)}:")
        }
        emitInstructionC(
          out = out,
          insn = insn,
          allInsns = insns,
          method = method,
          config = config,
          indent = "    ",
          flattened = false,
          returnType = ref.proto.returnType,
        )
        emitTryCatchDispatchIfApplicable(
          out = out,
          insn = insn,
          tryBlocks = tryBlocks,
          config = config,
          indent = "    ",
          flattened = false,
          returnType = ref.proto.returnType,
        )
      }
      if (insns.none { it.mnemonic.startsWith("return") || it.mnemonic == "throw" }) {
        out.appendLine("    ${jniCall(config, "PopLocalFrame", "NULL")};")
        out.appendLine(defaultReturnStmt(ref.proto.returnType))
      }
    }

    out.appendLine("}")
    return NativeRegRecord(
      definingClass = ref.definingClass,
      methodName = ref.name,
      jniSignature = ref.jniSignature(),
      cFuncName = cFuncName,
      forwardPrototype = forwardProto,
    )
  }

  private fun emitTryCatchDispatchIfApplicable(
    out: Appendable,
    insn: DalvikInstruction,
    tryBlocks: List<DexTryBlock>,
    config: TranspilerConfig,
    indent: String,
    flattened: Boolean,
    returnType: String,
  ) {
    val canThrow =
      insn.mnemonic.startsWith("invoke-") ||
        insn.mnemonic == "throw" ||
        insn.mnemonic == "check-cast" ||
        insn.mnemonic == "new-instance" ||
        insn.mnemonic.startsWith("aget") ||
        insn.mnemonic.startsWith("aput") ||
        insn.mnemonic.startsWith("iget") ||
        insn.mnemonic.startsWith("iput")
    if (!canThrow) return

    val activeTries =
      tryBlocks.filter { insn.offsetWords >= it.startOffsetWord && insn.offsetWords < it.endOffsetWord }
    if (activeTries.isEmpty()) return

    out.appendLine("${indent}if (${jniCall(config, "ExceptionCheck", "")}) {")
    out.appendLine("${indent}    __exc = ${jniCall(config, "ExceptionOccurred", "")};")
    out.appendLine("${indent}    ${jniCall(config, "ExceptionClear", "")};")
    activeTries.flatMap { it.handlers }.forEach { handler ->
      val jump =
        if (flattened) "{ __cf_pc = ${formatHex4u(handler.handlerOffsetWord)}; goto __cf_continue; }"
        else "goto ${formatLabelOffset(handler.handlerOffsetWord)};"
      if (handler.typeDescriptor == null) {
        out.appendLine("${indent}    $jump /* catch-all */")
      } else {
        val jniCls = handler.typeDescriptor.removePrefix("L").removeSuffix(";")
        out.appendLine("${indent}    { jclass __hc = ${jniCall(config, "FindClass", "\"${escapeC(jniCls)}\"")}; jboolean __match = (__hc != NULL && ${jniCall(config, "IsInstanceOf", "__exc, __hc")}); if (__hc != NULL) ${jniCall(config, "DeleteLocalRef", "__hc")}; if (__match) $jump }")
      }
    }
    out.appendLine("${indent}    ${jniCall(config, "Throw", "__exc")};")
    if (flattened) {
      out.appendLine("${indent}    goto __dex2c_exit;")
    } else {
      out.appendLine("${indent}    ${jniCall(config, "PopLocalFrame", "NULL")};")
      out.appendLine("${indent}    ${defaultReturnStmt(returnType).trim()}")
    }
    out.appendLine("${indent}}")
  }

  private fun emitInstructionC(
    out: Appendable,
    insn: DalvikInstruction,
    allInsns: List<DalvikInstruction>,
    method: DexEncodedMethod,
    config: TranspilerConfig,
    indent: String,
    flattened: Boolean,
    returnType: String,
  ) {
    val a = insn.regA ?: 0
    val b = insn.regB ?: 0
    val c = insn.regC ?: 0
    val lit = insn.literal ?: 0L

    fun jumpTo(target: Int?): String {
      val off = target ?: 0
      return if (flattened) "{ __cf_pc = ${formatHex4u(off)}; break; }" else "goto ${formatLabelOffset(off)};"
    }

    when (insn.mnemonic) {
      "nop" -> out.appendLine("${indent}/* nop */")
      "move", "move/from16", "move/16" -> out.appendLine("${indent}v[$a].i = v[$b].i;")
      "move-wide", "move-wide/from16", "move-wide/16" -> out.appendLine("${indent}v[$a].j = v[$b].j;")
      "move-object", "move-object/from16", "move-object/16" -> out.appendLine("${indent}v[$a].l = v[$b].l;")
      "move-result" -> out.appendLine("${indent}v[$a].i = __res.i;")
      "move-result-wide" -> out.appendLine("${indent}v[$a].j = __res.j;")
      "move-result-object" -> out.appendLine("${indent}v[$a].l = __res.l;")
      "move-exception" -> {
        out.appendLine("${indent}if (__exc == NULL) { __exc = ${jniCall(config, "ExceptionOccurred", "")}; ${jniCall(config, "ExceptionClear", "")}; }")
        out.appendLine("${indent}v[$a].l = __exc;")
        out.appendLine("${indent}__exc = NULL;")
      }

      "return-void" -> {
        out.appendLine("${indent}${jniCall(config, "PopLocalFrame", "NULL")};")
        out.appendLine("${indent}return;")
      }
      "return" -> {
        val cast =
          when (returnType) {
            "Z" -> "(jboolean)v[$a].i"
            "B" -> "(jbyte)v[$a].i"
            "S" -> "(jshort)v[$a].i"
            "C" -> "(jchar)v[$a].i"
            "F" -> "v[$a].f"
            else -> "v[$a].i"
          }
        out.appendLine("${indent}${jniCall(config, "PopLocalFrame", "NULL")};")
        out.appendLine("${indent}return $cast;")
      }
      "return-wide" -> {
        val cast = if (returnType == "D") "v[$a].d" else "v[$a].j"
        out.appendLine("${indent}${jniCall(config, "PopLocalFrame", "NULL")};")
        out.appendLine("${indent}return $cast;")
      }
      "return-object" -> {
        out.appendLine("${indent}return (${descriptorToJniType(returnType)})${jniCall(config, "PopLocalFrame", "v[$a].l")};")
      }

      "const/4", "const/16", "const", "const/high16" ->
        out.appendLine("${indent}v[$a].i = (jint)(${lit}LL);")
      "const-wide/16", "const-wide/32", "const-wide", "const-wide/high16" ->
        out.appendLine("${indent}v[$a].j = (jlong)(${lit}LL);")

      "const-string", "const-string/jumbo" -> {
        val rawStr = insn.stringRef ?: ""
        if (config.stringEncryption) {
          val bytes = rawStr.toByteArray(Charsets.UTF_8)
          val key = 0x5A
          val encHex = bytes.joinToString(", ") { "0x%02X".format((it.toInt() and 0xFF) xor key) }
          out.appendLine("${indent}{")
          if (bytes.isEmpty()) {
            out.appendLine("${indent}    v[$a].l = ${jniCall(config, "NewStringUTF", "\"\"")};")
          } else {
            out.appendLine("${indent}    static const uint8_t __enc_${insn.offsetWords}[] = {$encHex};")
            out.appendLine("${indent}    char __dec_${insn.offsetWords}[${bytes.size + 1}];")
            out.appendLine("${indent}    dex2c_xor_decode(__dec_${insn.offsetWords}, __enc_${insn.offsetWords}, ${bytes.size}, 0x%02X);".format(key))
            out.appendLine("${indent}    v[$a].l = ${jniCall(config, "NewStringUTF", "__dec_${insn.offsetWords}")};")
          }
          out.appendLine("${indent}}")
        } else {
          out.appendLine("${indent}v[$a].l = ${jniCall(config, "NewStringUTF", "\"${escapeC(rawStr)}\"")};")
        }
      }

      "const-class" -> {
        val jniCls = (insn.typeRef ?: "Ljava/lang/Object;").removePrefix("L").removeSuffix(";")
        out.appendLine("${indent}v[$a].l = ${jniCall(config, "FindClass", "\"${escapeC(jniCls)}\"")};")
      }

      "monitor-enter" -> out.appendLine("${indent}${jniCall(config, "MonitorEnter", "v[$a].l")};")
      "monitor-exit" -> out.appendLine("${indent}${jniCall(config, "MonitorExit", "v[$a].l")};")
      "throw" -> {
        out.appendLine("${indent}${jniCall(config, "Throw", "(jthrowable)v[$a].l")};")
        out.appendLine("${indent}${jniCall(config, "PopLocalFrame", "NULL")};")
        out.appendLine("${indent}${defaultReturnStmt(returnType).trim()}")
      }

      "check-cast" -> {
        val jniCls = (insn.typeRef ?: "Ljava/lang/Object;").removePrefix("L").removeSuffix(";")
        val throwCceMacro = if (returnType == "V") "DEX2C_THROW_CCE_VOID" else "DEX2C_THROW_CCE"
        out.appendLine("${indent}{ jclass __t = ${jniCall(config, "FindClass", "\"${escapeC(jniCls)}\"")}; if (v[$a].l != NULL && !${jniCall(config, "IsInstanceOf", "v[$a].l, __t")}) { $throwCceMacro(env); } ${jniCall(config, "DeleteLocalRef", "__t")}; }")
      }
      "instance-of" -> {
        val jniCls = (insn.typeRef ?: "Ljava/lang/Object;").removePrefix("L").removeSuffix(";")
        out.appendLine("${indent}{ jclass __t = ${jniCall(config, "FindClass", "\"${escapeC(jniCls)}\"")}; v[$a].i = ${jniCall(config, "IsInstanceOf", "v[$b].l, __t")}; ${jniCall(config, "DeleteLocalRef", "__t")}; }")
      }

      "array-length" -> {
        val checkNullMacro = if (returnType == "V") "DEX2C_CHECK_NULL_VOID" else "DEX2C_CHECK_NULL"
        if (config.nullCheckGuards) out.appendLine("${indent}$checkNullMacro(env, v[$b].l);")
        out.appendLine("${indent}v[$a].i = ${jniCall(config, "GetArrayLength", "(jarray)v[$b].l")};")
      }

      "new-instance" -> {
        val jniCls = (insn.typeRef ?: "Ljava/lang/Object;").removePrefix("L").removeSuffix(";")
        out.appendLine("${indent}{ jclass __c = ${jniCall(config, "FindClass", "\"${escapeC(jniCls)}\"")}; v[$a].l = ${jniCall(config, "AllocObject", "__c")}; ${jniCall(config, "DeleteLocalRef", "__c")}; }")
      }

      "new-array" -> {
        val elemDesc = (insn.typeRef ?: "[Ljava/lang/Object;").removePrefix("[")
        val fn = newArrayJniFunc(elemDesc)
        if (fn != null) {
          out.appendLine("${indent}v[$a].l = ${jniCall(config, fn, "v[$b].i")};")
        } else {
          val jniCls = elemDesc.removePrefix("L").removeSuffix(";")
          out.appendLine("${indent}{ jclass __c = ${jniCall(config, "FindClass", "\"${escapeC(jniCls)}\"")}; v[$a].l = ${jniCall(config, "NewObjectArray", "v[$b].i, __c, NULL")}; ${jniCall(config, "DeleteLocalRef", "__c")}; }")
        }
      }

      "packed-switch" -> {
        val p = insn.payload as? DalvikPayload.PackedSwitch
          ?: allInsns.find { it.offsetWords == insn.targetOffset }?.payload as? DalvikPayload.PackedSwitch
        if (p != null) {
          out.appendLine("${indent}switch (v[$a].i) {")
          p.targetOffsets.forEachIndexed { idx, tgt ->
            val key = p.firstKey + idx
            val jump = if (flattened) "{ __cf_pc = ${formatHex4u(tgt)}; goto __cf_continue; }" else "goto ${formatLabelOffset(tgt)};"
            out.appendLine("${indent}    case $key: $jump")
          }
          out.appendLine("${indent}    default: break;")
          out.appendLine("${indent}}")
        } else {
          val commentText = sanitizeCComment("${insn.mnemonic} ${insn.operandsText}".trim())
          out.appendLine("${indent}/* $commentText (unresolved payload) */")
        }
      }

      "sparse-switch" -> {
        val p = insn.payload as? DalvikPayload.SparseSwitch
          ?: allInsns.find { it.offsetWords == insn.targetOffset }?.payload as? DalvikPayload.SparseSwitch
        if (p != null) {
          out.appendLine("${indent}switch (v[$a].i) {")
          p.cases.forEach { (key, tgt) ->
            val jump = if (flattened) "{ __cf_pc = ${formatHex4u(tgt)}; goto __cf_continue; }" else "goto ${formatLabelOffset(tgt)};"
            out.appendLine("${indent}    case $key: $jump")
          }
          out.appendLine("${indent}    default: break;")
          out.appendLine("${indent}}")
        } else {
          val commentText = sanitizeCComment("${insn.mnemonic} ${insn.operandsText}".trim())
          out.appendLine("${indent}/* $commentText (unresolved payload) */")
        }
      }

      "goto", "goto/16", "goto/32" -> out.appendLine("${indent}${jumpTo(insn.targetOffset)}")
      "if-eq" -> out.appendLine("${indent}if (v[$a].i == v[$b].i) ${jumpTo(insn.targetOffset)}")
      "if-ne" -> out.appendLine("${indent}if (v[$a].i != v[$b].i) ${jumpTo(insn.targetOffset)}")
      "if-lt" -> out.appendLine("${indent}if (v[$a].i < v[$b].i) ${jumpTo(insn.targetOffset)}")
      "if-ge" -> out.appendLine("${indent}if (v[$a].i >= v[$b].i) ${jumpTo(insn.targetOffset)}")
      "if-gt" -> out.appendLine("${indent}if (v[$a].i > v[$b].i) ${jumpTo(insn.targetOffset)}")
      "if-le" -> out.appendLine("${indent}if (v[$a].i <= v[$b].i) ${jumpTo(insn.targetOffset)}")
      "if-eqz" -> out.appendLine("${indent}if (v[$a].i == 0) ${jumpTo(insn.targetOffset)}")
      "if-nez" -> out.appendLine("${indent}if (v[$a].i != 0) ${jumpTo(insn.targetOffset)}")
      "if-ltz" -> out.appendLine("${indent}if (v[$a].i < 0) ${jumpTo(insn.targetOffset)}")
      "if-gez" -> out.appendLine("${indent}if (v[$a].i >= 0) ${jumpTo(insn.targetOffset)}")
      "if-gtz" -> out.appendLine("${indent}if (v[$a].i > 0) ${jumpTo(insn.targetOffset)}")
      "if-lez" -> out.appendLine("${indent}if (v[$a].i <= 0) ${jumpTo(insn.targetOffset)}")

      "cmp-long" -> out.appendLine("${indent}v[$a].i = (v[$b].j > v[$c].j) ? 1 : ((v[$b].j < v[$c].j) ? -1 : 0);")
      "cmpl-float", "cmpg-float" -> out.appendLine("${indent}v[$a].i = (v[$b].f > v[$c].f) ? 1 : ((v[$b].f < v[$c].f) ? -1 : 0);")
      "cmpl-double", "cmpg-double" -> out.appendLine("${indent}v[$a].i = (v[$b].d > v[$c].d) ? 1 : ((v[$b].d < v[$c].d) ? -1 : 0);")

      "neg-int" -> out.appendLine("${indent}v[$a].i = -v[$b].i;")
      "not-int" -> out.appendLine("${indent}v[$a].i = ~v[$b].i;")
      "neg-long" -> out.appendLine("${indent}v[$a].j = -v[$b].j;")
      "not-long" -> out.appendLine("${indent}v[$a].j = ~v[$b].j;")
      "neg-float" -> out.appendLine("${indent}v[$a].f = -v[$b].f;")
      "neg-double" -> out.appendLine("${indent}v[$a].d = -v[$b].d;")
      "int-to-long" -> out.appendLine("${indent}v[$a].j = (jlong)v[$b].i;")
      "int-to-float" -> out.appendLine("${indent}v[$a].f = (jfloat)v[$b].i;")
      "int-to-double" -> out.appendLine("${indent}v[$a].d = (jdouble)v[$b].i;")
      "long-to-int" -> out.appendLine("${indent}v[$a].i = (jint)v[$b].j;")
      "long-to-float" -> out.appendLine("${indent}v[$a].f = (jfloat)v[$b].j;")
      "long-to-double" -> out.appendLine("${indent}v[$a].d = (jdouble)v[$b].j;")
      "float-to-int" -> out.appendLine("${indent}v[$a].i = (jint)v[$b].f;")
      "float-to-long" -> out.appendLine("${indent}v[$a].j = (jlong)v[$b].f;")
      "float-to-double" -> out.appendLine("${indent}v[$a].d = (jdouble)v[$b].f;")
      "double-to-int" -> out.appendLine("${indent}v[$a].i = (jint)v[$b].d;")
      "double-to-long" -> out.appendLine("${indent}v[$a].j = (jlong)v[$b].d;")
      "double-to-float" -> out.appendLine("${indent}v[$a].f = (jfloat)v[$b].d;")
      "int-to-byte" -> out.appendLine("${indent}v[$a].i = (jbyte)v[$b].i;")
      "int-to-char" -> out.appendLine("${indent}v[$a].i = (jchar)v[$b].i;")
      "int-to-short" -> out.appendLine("${indent}v[$a].i = (jshort)v[$b].i;")

      else -> {
        when {
          emitMathInstruction(out, insn, indent, a, b, c, lit) -> Unit
          emitArrayAccessInstruction(out, insn, allInsns, method, config, indent, a, b, c, returnType) -> Unit
          emitFieldInstruction(out, insn, config, indent, a, b, returnType) -> Unit
          emitInvokeInstruction(out, insn, config, indent, returnType) -> Unit
          else -> {
            val commentText = sanitizeCComment("${insn.mnemonic} ${insn.operandsText}".trim())
            out.appendLine("${indent}/* $commentText */")
          }
        }
      }
    }
  }

  private fun emitMathInstruction(
    out: Appendable,
    insn: DalvikInstruction,
    indent: String,
    a: Int,
    b: Int,
    c: Int,
    lit: Long,
  ): Boolean {
    val m = insn.mnemonic
    val is2Addr = m.endsWith("/2addr")
    val isLit = m.endsWith("/lit16") || m.endsWith("/lit8")
    val base = m.removeSuffix("/2addr").removeSuffix("/lit16").removeSuffix("/lit8")

    val lhs = if (is2Addr) "v[$a]" else "v[$b]"
    val rhs =
      when {
        isLit -> "$lit"
        is2Addr -> "v[$b]"
        else -> "v[$c]"
      }

    val stmt =
      when (base) {
        "add-int" -> "v[$a].i = ${lhs}.i + ${if (isLit) "(jint)($rhs)" else "${rhs}.i"};"
        "sub-int" -> "v[$a].i = ${lhs}.i - ${if (isLit) "(jint)($rhs)" else "${rhs}.i"};"
        "rsub-int" -> "v[$a].i = (jint)($rhs) - ${lhs}.i;"
        "mul-int" -> "v[$a].i = ${lhs}.i * ${if (isLit) "(jint)($rhs)" else "${rhs}.i"};"
        "div-int" -> "v[$a].i = ${lhs}.i / ${if (isLit) "(jint)($rhs)" else "${rhs}.i"};"
        "rem-int" -> "v[$a].i = ${lhs}.i % ${if (isLit) "(jint)($rhs)" else "${rhs}.i"};"
        "and-int" -> "v[$a].i = ${lhs}.i & ${if (isLit) "(jint)($rhs)" else "${rhs}.i"};"
        "or-int" -> "v[$a].i = ${lhs}.i | ${if (isLit) "(jint)($rhs)" else "${rhs}.i"};"
        "xor-int" -> "v[$a].i = ${lhs}.i ^ ${if (isLit) "(jint)($rhs)" else "${rhs}.i"};"
        "shl-int" -> "v[$a].i = ${lhs}.i << (${if (isLit) "$rhs" else "${rhs}.i"} & 0x1F);"
        "shr-int" -> "v[$a].i = ${lhs}.i >> (${if (isLit) "$rhs" else "${rhs}.i"} & 0x1F);"
        "ushr-int" -> "v[$a].i = (jint)((uint32_t)${lhs}.i >> (${if (isLit) "$rhs" else "${rhs}.i"} & 0x1F));"

        "add-long" -> "v[$a].j = ${lhs}.j + ${rhs}.j;"
        "sub-long" -> "v[$a].j = ${lhs}.j - ${rhs}.j;"
        "mul-long" -> "v[$a].j = ${lhs}.j * ${rhs}.j;"
        "div-long" -> "v[$a].j = ${lhs}.j / ${rhs}.j;"
        "rem-long" -> "v[$a].j = ${lhs}.j % ${rhs}.j;"
        "and-long" -> "v[$a].j = ${lhs}.j & ${rhs}.j;"
        "or-long" -> "v[$a].j = ${lhs}.j | ${rhs}.j;"
        "xor-long" -> "v[$a].j = ${lhs}.j ^ ${rhs}.j;"
        "shl-long" -> "v[$a].j = ${lhs}.j << (${rhs}.i & 0x3F);"
        "shr-long" -> "v[$a].j = ${lhs}.j >> (${rhs}.i & 0x3F);"
        "ushr-long" -> "v[$a].j = (jlong)((uint64_t)${lhs}.j >> (${rhs}.i & 0x3F));"

        "add-float" -> "v[$a].f = ${lhs}.f + ${rhs}.f;"
        "sub-float" -> "v[$a].f = ${lhs}.f - ${rhs}.f;"
        "mul-float" -> "v[$a].f = ${lhs}.f * ${rhs}.f;"
        "div-float" -> "v[$a].f = ${lhs}.f / ${rhs}.f;"
        "rem-float" -> "v[$a].f = fmodf(${lhs}.f, ${rhs}.f);"

        "add-double" -> "v[$a].d = ${lhs}.d + ${rhs}.d;"
        "sub-double" -> "v[$a].d = ${lhs}.d - ${rhs}.d;"
        "mul-double" -> "v[$a].d = ${lhs}.d * ${rhs}.d;"
        "div-double" -> "v[$a].d = ${lhs}.d / ${rhs}.d;"
        "rem-double" -> "v[$a].d = fmod(${lhs}.d, ${rhs}.d);"
        else -> return false
      }
    out.appendLine("$indent$stmt")
    return true
  }

  private fun inferArrayElementType(
    insns: List<DalvikInstruction>,
    arrayReg: Int,
    accessInsn: DalvikInstruction,
    method: DexEncodedMethod,
  ): String? {
    val meta = method.codeMeta
    if (meta != null) {
      var regCursor = (meta.registersSize - meta.insSize).coerceAtLeast(0)
      if (!method.isStatic) regCursor++
      for (pType in method.methodRef.proto.parameterTypes) {
        if (regCursor == arrayReg) {
          if (pType == "[D") return "D"
          if (pType == "[J") return "J"
        }
        regCursor += if (pType == "J" || pType == "D") 2 else 1
      }
    }

    for (insn in insns) {
      if (insn.offsetWords >= accessInsn.offsetWords) break
      when (insn.mnemonic) {
        "new-array" -> {
          if (insn.regA == arrayReg) {
            val type = insn.typeRef ?: ""
            if (type.contains("D")) return "D"
            if (type.contains("J")) return "J"
          }
        }
        "check-cast" -> {
          if (insn.regA == arrayReg) {
            val type = insn.typeRef ?: ""
            if (type == "[D") return "D"
            if (type == "[J") return "J"
          }
        }
        "iget-object", "sget-object" -> {
          if (insn.regA == arrayReg) {
            val type = insn.fieldRef?.type ?: ""
            if (type == "[D") return "D"
            if (type == "[J") return "J"
          }
        }
      }
    }

    val valReg = accessInsn.regA ?: return null
    for (insn in insns) {
      val m = insn.mnemonic
      if (m.endsWith("-double") || m.startsWith("double-to-") || m.endsWith("-to-double")) {
        if (insn.regA == valReg || insn.regB == valReg || insn.regC == valReg) return "D"
      }
      if (m.endsWith("-long") || m.startsWith("long-to-") || m.endsWith("-to-long")) {
        if (insn.regA == valReg || insn.regB == valReg || insn.regC == valReg) return "J"
      }
    }

    if (method.methodRef.proto.returnType == "D") return "D"
    if (method.methodRef.proto.returnType == "J") return "J"

    return null
  }

  private fun emitArrayAccessInstruction(
    out: Appendable,
    insn: DalvikInstruction,
    allInsns: List<DalvikInstruction>,
    method: DexEncodedMethod,
    config: TranspilerConfig,
    indent: String,
    a: Int,
    b: Int,
    c: Int,
    returnType: String,
  ): Boolean {
    val m = insn.mnemonic
    if (!m.startsWith("aget") && !m.startsWith("aput")) return false
    val checkNullMacro = if (returnType == "V") "DEX2C_CHECK_NULL_VOID" else "DEX2C_CHECK_NULL"
    if (config.nullCheckGuards) out.appendLine("${indent}$checkNullMacro(env, v[$b].l);")
    when (m) {
      "aget-object" ->
        out.appendLine("${indent}v[$a].l = ${jniCall(config, "GetObjectArrayElement", "(jobjectArray)v[$b].l, v[$c].i")};")
      "aput-object" ->
        out.appendLine("${indent}${jniCall(config, "SetObjectArrayElement", "(jobjectArray)v[$b].l, v[$c].i, v[$a].l")};")
      "aget-wide" -> {
        val elemType = inferArrayElementType(allInsns, b, insn, method)
        when (elemType) {
          "D" -> out.appendLine("${indent}{ jdouble __tmp; ${jniCall(config, "GetDoubleArrayRegion", "(jdoubleArray)v[$b].l, v[$c].i, 1, &__tmp")}; v[$a].d = __tmp; }")
          "J" -> out.appendLine("${indent}{ jlong __tmp; ${jniCall(config, "GetLongArrayRegion", "(jlongArray)v[$b].l, v[$c].i, 1, &__tmp")}; v[$a].j = __tmp; }")
          else -> out.appendLine("${indent}dex2c_get_wide_elem(env, (jobject)v[$b].l, v[$c].i, &v[$a]);")
        }
      }
      "aput-wide" -> {
        val elemType = inferArrayElementType(allInsns, b, insn, method)
        when (elemType) {
          "D" -> out.appendLine("${indent}{ jdouble __tmp = v[$a].d; ${jniCall(config, "SetDoubleArrayRegion", "(jdoubleArray)v[$b].l, v[$c].i, 1, &__tmp")}; }")
          "J" -> out.appendLine("${indent}{ jlong __tmp = (jlong)v[$a].j; ${jniCall(config, "SetLongArrayRegion", "(jlongArray)v[$b].l, v[$c].i, 1, &__tmp")}; }")
          else -> out.appendLine("${indent}dex2c_set_wide_elem(env, (jobject)v[$b].l, v[$c].i, v[$a]);")
        }
      }
      "aget", "aget-boolean", "aget-byte", "aget-char", "aget-short" -> {
        val (arrType, cType, jniGet, field) = arrayMeta(m)
        out.appendLine("${indent}{ $cType __tmp; ${jniCall(config, jniGet, "($arrType)v[$b].l, v[$c].i, 1, &__tmp")}; v[$a].$field = __tmp; }")
      }
      "aput", "aput-boolean", "aput-byte", "aput-char", "aput-short" -> {
        val (arrType, cType, jniSet, field) = arrayMeta(m, isPut = true)
        out.appendLine("${indent}{ $cType __tmp = ($cType)v[$a].$field; ${jniCall(config, jniSet, "($arrType)v[$b].l, v[$c].i, 1, &__tmp")}; }")
      }
    }
    return true
  }

  private fun emitFieldInstruction(
    out: Appendable,
    insn: DalvikInstruction,
    config: TranspilerConfig,
    indent: String,
    a: Int,
    b: Int,
    returnType: String,
  ): Boolean {
    val f = insn.fieldRef ?: return false
    val m = insn.mnemonic
    val isGet = m.startsWith("iget") || m.startsWith("sget")
    val isPut = m.startsWith("iput") || m.startsWith("sput")
    if (!isGet && !isPut) return false

    val isStatic = m.startsWith("s")
    val jniCls = f.definingClass.removePrefix("L").removeSuffix(";")
    val (suffix, valField) = jniTypeSuffixAndField(f.type)

    out.appendLine("${indent}{")
    if (!isStatic && config.nullCheckGuards) {
      val checkNullMacro = if (returnType == "V") "DEX2C_CHECK_NULL_VOID" else "DEX2C_CHECK_NULL"
      out.appendLine("${indent}    $checkNullMacro(env, v[$b].l);")
    }
    out.appendLine("${indent}    jclass __fcls = ${jniCall(config, "FindClass", "\"${escapeC(jniCls)}\"")};")
    val idCall = if (isStatic) "GetStaticFieldID" else "GetFieldID"
    out.appendLine("${indent}    jfieldID __fid = ${jniCall(config, idCall, "__fcls, \"${escapeC(f.name)}\", \"${escapeC(f.type)}\"")};")

    if (isGet) {
      val opCall = if (isStatic) "GetStatic${suffix}Field" else "Get${suffix}Field"
      val target = if (isStatic) "__fcls" else "v[$b].l"
      out.appendLine("${indent}    v[$a].$valField = ${jniCall(config, opCall, "$target, __fid")};")
    } else {
      val opCall = if (isStatic) "SetStatic${suffix}Field" else "Set${suffix}Field"
      val target = if (isStatic) "__fcls" else "v[$b].l"
      val cast = if (suffix == "Object") "v[$a].l" else "(${descriptorToJniType(f.type)})v[$a].$valField"
      out.appendLine("${indent}    ${jniCall(config, opCall, "$target, __fid, $cast")};")
    }
    out.appendLine("${indent}    ${jniCall(config, "DeleteLocalRef", "__fcls")};")
    out.appendLine("${indent}}")
    return true
  }

  private fun emitInvokeInstruction(
    out: Appendable,
    insn: DalvikInstruction,
    config: TranspilerConfig,
    indent: String,
    returnType: String,
  ): Boolean {
    val ref = insn.methodRef ?: return false
    val m = insn.mnemonic
    if (!m.startsWith("invoke-")) return false

    val isStatic = m.startsWith("invoke-static")
    val isSuper = m.startsWith("invoke-super")
    val isDirect = m.startsWith("invoke-direct")
    val jniCls = ref.definingClass.removePrefix("L").removeSuffix(";")
    val (retSuffix, retField) = jniTypeSuffixAndField(ref.proto.returnType)

    val args = mutableListOf<String>()
    var regIdx = if (isStatic) 0 else 1
    ref.proto.parameterTypes.forEach { pType ->
      val r = insn.regList.getOrNull(regIdx) ?: 0
      val f = jvalueFieldForDescriptor(pType)
      val cType = descriptorToJniType(pType)
      args += "($cType)v[$r].$f"
      regIdx += if (pType == "J" || pType == "D") 2 else 1
    }

    out.appendLine("${indent}{")
    if (!isStatic && config.nullCheckGuards) {
      val recv = insn.regList.firstOrNull() ?: 0
      val checkNullMacro = if (returnType == "V") "DEX2C_CHECK_NULL_VOID" else "DEX2C_CHECK_NULL"
      out.appendLine("${indent}    $checkNullMacro(env, v[$recv].l);")
    }
    out.appendLine("${indent}    jclass __mcls = ${jniCall(config, "FindClass", "\"${escapeC(jniCls)}\"")};")
    val getMid = if (isStatic) "GetStaticMethodID" else "GetMethodID"
    out.appendLine("${indent}    jmethodID __mid = ${jniCall(config, getMid, "__mcls, \"${escapeC(ref.name)}\", \"${escapeC(ref.jniSignature())}\"")};")

    val argsTail = if (args.isEmpty()) "" else ", ${args.joinToString(", ")}"
    val recvReg = insn.regList.firstOrNull() ?: 0

    val callExpr =
      when {
        isStatic -> jniCall(config, "CallStatic${retSuffix}Method", "__mcls, __mid$argsTail")
        isSuper || isDirect -> jniCall(config, "CallNonvirtual${retSuffix}Method", "v[$recvReg].l, __mcls, __mid$argsTail")
        else -> jniCall(config, "Call${retSuffix}Method", "v[$recvReg].l, __mid$argsTail")
      }

    if (ref.proto.returnType == "V") {
      out.appendLine("${indent}    $callExpr;")
    } else {
      out.appendLine("${indent}    __res.$retField = $callExpr;")
    }
    out.appendLine("${indent}    ${jniCall(config, "DeleteLocalRef", "__mcls")};")
    out.appendLine("${indent}}")
    return true
  }

  private fun jniCall(config: TranspilerConfig, fn: String, args: String): String {
    return if (config.targetLanguage == TargetCLanguage.C11) {
      if (args.isEmpty()) "(*env)->$fn(env)" else "(*env)->$fn(env, $args)"
    } else {
      "env->$fn($args)"
    }
  }

  private fun buildRuntimeHeader(moduleName: String): String =
    """
    |#ifndef DEX2C_RUNTIME_H
    |#define DEX2C_RUNTIME_H
    |
    |#include <jni.h>
    |#include <stdint.h>
    |#include <string.h>
    |#include <math.h>
    |
    |#define DEX2C_MODULE_NAME "$moduleName"
    |
    |#ifdef __cplusplus
    |#define DEX2C_FIND_CLASS(env, name) (env)->FindClass(name)
    |#define DEX2C_THROW_NEW(env, cls, msg) (env)->ThrowNew(cls, msg)
    |#define DEX2C_POP_FRAME(env, res) (env)->PopLocalFrame(res)
    |#define DEX2C_GET_OBJECT_CLASS(env, obj) (env)->GetObjectClass(obj)
    |#define DEX2C_IS_SAME_OBJECT(env, ref1, ref2) (env)->IsSameObject(ref1, ref2)
    |#define DEX2C_DELETE_LOCAL_REF(env, ref) (env)->DeleteLocalRef(ref)
    |#define DEX2C_GET_DOUBLE_ARRAY_REGION(env, arr, start, len, buf) (env)->GetDoubleArrayRegion(arr, start, len, buf)
    |#define DEX2C_SET_DOUBLE_ARRAY_REGION(env, arr, start, len, buf) (env)->SetDoubleArrayRegion(arr, start, len, buf)
    |#define DEX2C_GET_LONG_ARRAY_REGION(env, arr, start, len, buf) (env)->GetLongArrayRegion(arr, start, len, buf)
    |#define DEX2C_SET_LONG_ARRAY_REGION(env, arr, start, len, buf) (env)->SetLongArrayRegion(arr, start, len, buf)
    |#else
    |#define DEX2C_FIND_CLASS(env, name) (*(env))->FindClass(env, name)
    |#define DEX2C_THROW_NEW(env, cls, msg) (*(env))->ThrowNew(env, cls, msg)
    |#define DEX2C_POP_FRAME(env, res) (*(env))->PopLocalFrame(env, res)
    |#define DEX2C_GET_OBJECT_CLASS(env, obj) (*(env))->GetObjectClass(env, obj)
    |#define DEX2C_IS_SAME_OBJECT(env, ref1, ref2) (*(env))->IsSameObject(env, ref1, ref2)
    |#define DEX2C_DELETE_LOCAL_REF(env, ref) (*(env))->DeleteLocalRef(env, ref)
    |#define DEX2C_GET_DOUBLE_ARRAY_REGION(env, arr, start, len, buf) (*(env))->GetDoubleArrayRegion(env, arr, start, len, buf)
    |#define DEX2C_SET_DOUBLE_ARRAY_REGION(env, arr, start, len, buf) (*(env))->SetDoubleArrayRegion(env, arr, start, len, buf)
    |#define DEX2C_GET_LONG_ARRAY_REGION(env, arr, start, len, buf) (*(env))->GetLongArrayRegion(env, arr, start, len, buf)
    |#define DEX2C_SET_LONG_ARRAY_REGION(env, arr, start, len, buf) (*(env))->SetLongArrayRegion(env, arr, start, len, buf)
    |#endif
    |
    |#define DEX2C_THROW_CCE(env) do { \
    |    jclass __ex = DEX2C_FIND_CLASS(env, "java/lang/ClassCastException"); \
    |    if (__ex != NULL) DEX2C_THROW_NEW(env, __ex, "Dex2C cast error"); \
    |    DEX2C_POP_FRAME(env, NULL); \
    |    return 0; \
    |} while (0)
    |
    |#define DEX2C_THROW_CCE_VOID(env) do { \
    |    jclass __ex = DEX2C_FIND_CLASS(env, "java/lang/ClassCastException"); \
    |    if (__ex != NULL) DEX2C_THROW_NEW(env, __ex, "Dex2C cast error"); \
    |    DEX2C_POP_FRAME(env, NULL); \
    |    return; \
    |} while (0)
    |
    |#define DEX2C_CHECK_NULL(env, ref) do { \
    |    if ((ref) == NULL) { \
    |        jclass __npe = DEX2C_FIND_CLASS(env, "java/lang/NullPointerException"); \
    |        if (__npe != NULL) DEX2C_THROW_NEW(env, __npe, "Dex2C null reference"); \
    |        DEX2C_POP_FRAME(env, NULL); \
    |        return 0; \
    |    } \
    |} while (0)
    |
    |#define DEX2C_CHECK_NULL_VOID(env, ref) do { \
    |    if ((ref) == NULL) { \
    |        jclass __npe = DEX2C_FIND_CLASS(env, "java/lang/NullPointerException"); \
    |        if (__npe != NULL) DEX2C_THROW_NEW(env, __npe, "Dex2C null reference"); \
    |        DEX2C_POP_FRAME(env, NULL); \
    |        return; \
    |    } \
    |} while (0)
    |
    |static inline void dex2c_get_wide_elem(JNIEnv *env, jobject arr, jint idx, jvalue *out_val) {
    |    if (arr == NULL || out_val == NULL) return;
    |    jclass cls = DEX2C_GET_OBJECT_CLASS(env, arr);
    |    jclass dCls = DEX2C_FIND_CLASS(env, "[D");
    |    if (cls != NULL && dCls != NULL && DEX2C_IS_SAME_OBJECT(env, cls, dCls)) {
    |        jdouble d = 0.0;
    |        DEX2C_GET_DOUBLE_ARRAY_REGION(env, (jdoubleArray)arr, idx, 1, &d);
    |        out_val->d = d;
    |    } else {
    |        jlong j = 0;
    |        DEX2C_GET_LONG_ARRAY_REGION(env, (jlongArray)arr, idx, 1, &j);
    |        out_val->j = j;
    |    }
    |    if (cls != NULL) DEX2C_DELETE_LOCAL_REF(env, cls);
    |    if (dCls != NULL) DEX2C_DELETE_LOCAL_REF(env, dCls);
    |}
    |
    |static inline void dex2c_set_wide_elem(JNIEnv *env, jobject arr, jint idx, jvalue in_val) {
    |    if (arr == NULL) return;
    |    jclass cls = DEX2C_GET_OBJECT_CLASS(env, arr);
    |    jclass dCls = DEX2C_FIND_CLASS(env, "[D");
    |    if (cls != NULL && dCls != NULL && DEX2C_IS_SAME_OBJECT(env, cls, dCls)) {
    |        jdouble d = in_val.d;
    |        DEX2C_SET_DOUBLE_ARRAY_REGION(env, (jdoubleArray)arr, idx, 1, &d);
    |    } else {
    |        jlong j = in_val.j;
    |        DEX2C_SET_LONG_ARRAY_REGION(env, (jlongArray)arr, idx, 1, &j);
    |    }
    |    if (cls != NULL) DEX2C_DELETE_LOCAL_REF(env, cls);
    |    if (dCls != NULL) DEX2C_DELETE_LOCAL_REF(env, dCls);
    |}
    |
    |static inline void dex2c_xor_decode(char *dst, const uint8_t *src, size_t len, uint8_t key) {
    |    for (size_t i = 0; i < len; ++i) {
    |        dst[i] = (char)(src[i] ^ key);
    |    }
    |    dst[len] = '\0';
    |}
    |
    |#endif // DEX2C_RUNTIME_H
    """.trimMargin() + "\n"

  private fun buildCMake(moduleName: String, sourceFileNames: List<String>): String {
    val sourcesLines = sourceFileNames.joinToString("\n    ") { "jni/$it" }
    return """
    |cmake_minimum_required(VERSION 3.22.1)
    |project($moduleName)
    |
    |add_library(
    |    $moduleName
    |    SHARED
    |    $sourcesLines
    |)
    |
    |find_library(log-lib log)
    |
    |target_link_libraries(
    |    $moduleName
    |    ${'$'}{log-lib}
    |    m
    |)
    """.trimMargin() + "\n"
  }

  private fun buildAndroidMk(
    moduleName: String,
    sourceFileNames: List<String>,
    ext: String,
    config: TranspilerConfig,
  ): String {
    val visibilityFlag =
      if (config.jniStyle == JniBindingStyle.REGISTER_NATIVES) "-fvisibility=hidden -O2"
      else "-O2"
    val ollvmFlags = if (config.ollvm.enabled) " " + config.ollvm.buildClangFlags() else ""
    val flagsLine =
      if (config.targetLanguage == TargetCLanguage.CPP17) "LOCAL_CPPFLAGS  := -std=c++17 $visibilityFlag$ollvmFlags"
      else "LOCAL_CFLAGS    := -std=c11 $visibilityFlag$ollvmFlags"
    val align16KbLine =
      if (config.ollvm.align16KbPages) "\nLOCAL_LDFLAGS   += \"-Wl,-z,max-page-size=16384\""
      else ""
    val srcFilesJoined =
      if (sourceFileNames.isEmpty()) "dex2c_bridge.$ext"
      else sourceFileNames.joinToString(" \\\n                   ")
    return """
      |LOCAL_PATH := $(call my-dir)
      |include $(CLEAR_VARS)
      |
      |LOCAL_MODULE    := $moduleName
      |LOCAL_SRC_FILES := $srcFilesJoined
      |$flagsLine$align16KbLine
      |LOCAL_LDLIBS    := -llog -lm
      |
      |include $(BUILD_SHARED_LIBRARY)
      """.trimMargin() + "\n"
  }

  private fun buildApplicationMk(config: TranspilerConfig): String {
    val abis =
      config.targetAbis
        .ifEmpty { setOf("arm64-v8a", "armeabi-v7a") }
        .sorted()
        .joinToString(" ")
    val stl = if (config.targetLanguage == TargetCLanguage.CPP17) "c++_static" else "none"
    return """
      |# Generated for codehasan/dex2c ndk-build
      |APP_ABI      := $abis
      |APP_PLATFORM := android-21
      |APP_STL      := $stl
      |APP_OPTIM    := release
      """.trimMargin() + "\n"
  }

  private fun streamLoaderStubFile(
    bundleDir: File,
    config: TranspilerConfig,
    moduleName: String,
    filteredEntries: List<Pair<DexParsedFile, DexEncodedMethod>>,
  ): GeneratedFile {
    val loaderFull = config.customLoaderClass.ifBlank { "amimo.dcc.DccApplication" }
    val lastDot = loaderFull.lastIndexOf('.')
    val pkg = if (lastDot > 0) loaderFull.substring(0, lastDot) else "amimo.dcc"
    val clsName = if (lastDot > 0) loaderFull.substring(lastDot + 1) else loaderFull

    val builder =
      StreamedFileBuilder(
        backingFile = File(bundleDir, "DccApplicationLoader.java"),
        fileName = "DccApplicationLoader.java",
        zipPath = "loader/DccApplicationLoader.java",
        description = "Application loader & native declarations",
      )
    builder.use { out ->
      out.appendLine("// Loader Strategy: ${config.loaderMode.label}")
      out.appendLine("package $pkg;")
      out.appendLine()
      out.appendLine("import android.app.Application;")
      out.appendLine()
      out.appendLine("public class $clsName extends Application {")
      out.appendLine("    static {")
      out.appendLine("        System.loadLibrary(\"$moduleName\");")
      out.appendLine("    }")
      out.appendLine("}")
      out.appendLine()
      out.appendLine("/* Protected method native declarations:")
      val byClass = linkedMapOf<String, MutableList<DexEncodedMethod>>()
      for ((_, m) in filteredEntries) {
        byClass.getOrPut(m.methodRef.definingClass) { mutableListOf() }.add(m)
      }
      byClass.forEach { (clsDesc, methods) ->
        out.appendLine(" * Class: ${descriptorToJavaName(clsDesc)}")
        methods.forEach { m ->
          val params =
            m.methodRef.proto.parameterTypes.mapIndexed { idx, p ->
              "${descriptorToJavaType(p)} p$idx"
            }.joinToString(", ")
          val ret = descriptorToJavaType(m.methodRef.proto.returnType)
          val staticMod = if (m.isStatic) "static " else ""
          out.appendLine(" *   public ${staticMod}native $ret ${m.methodRef.name}($params);")
        }
      }
      out.appendLine(" */")
    }
    return builder.finish()
  }

  private fun arrayMeta(mnemonic: String, isPut: Boolean = false): ArrayMeta {
    val op = if (isPut) "Set" else "Get"
    return when (mnemonic.removePrefix("aget-").removePrefix("aput-")) {
      "wide" -> ArrayMeta("jlongArray", "jlong", "${op}LongArrayRegion", "j")
      "boolean" -> ArrayMeta("jbooleanArray", "jboolean", "${op}BooleanArrayRegion", "z")
      "byte" -> ArrayMeta("jbyteArray", "jbyte", "${op}ByteArrayRegion", "b")
      "char" -> ArrayMeta("jcharArray", "jchar", "${op}CharArrayRegion", "c")
      "short" -> ArrayMeta("jshortArray", "jshort", "${op}ShortArrayRegion", "s")
      else -> ArrayMeta("jintArray", "jint", "${op}IntArrayRegion", "i")
    }
  }

  private data class ArrayMeta(val arrayType: String, val cType: String, val jniFn: String, val jvalueField: String)

  private fun newArrayJniFunc(elemDesc: String): String? =
    when (elemDesc) {
      "Z" -> "NewBooleanArray"
      "B" -> "NewByteArray"
      "S" -> "NewShortArray"
      "C" -> "NewCharArray"
      "I" -> "NewIntArray"
      "J" -> "NewLongArray"
      "F" -> "NewFloatArray"
      "D" -> "NewDoubleArray"
      else -> null
    }

  private fun descriptorToJniType(desc: String): String =
    when (desc) {
      "V" -> "void"
      "Z" -> "jboolean"
      "B" -> "jbyte"
      "S" -> "jshort"
      "C" -> "jchar"
      "I" -> "jint"
      "J" -> "jlong"
      "F" -> "jfloat"
      "D" -> "jdouble"
      "Ljava/lang/String;" -> "jstring"
      "Ljava/lang/Class;" -> "jclass"
      "Ljava/lang/Throwable;" -> "jthrowable"
      else -> if (desc.startsWith("[")) "jarray" else "jobject"
    }

  private fun descriptorToJavaType(desc: String): String = descriptorToJavaName(desc)

  private fun jvalueFieldForDescriptor(desc: String): String =
    when (desc) {
      "Z" -> "z"
      "B" -> "b"
      "S" -> "s"
      "C" -> "c"
      "I" -> "i"
      "J" -> "j"
      "F" -> "f"
      "D" -> "d"
      else -> "l"
    }

  private fun jniTypeSuffixAndField(desc: String): Pair<String, String> =
    when (desc) {
      "V" -> "Void" to "i"
      "Z" -> "Boolean" to "z"
      "B" -> "Byte" to "b"
      "S" -> "Short" to "s"
      "C" -> "Char" to "c"
      "I" -> "Int" to "i"
      "J" -> "Long" to "j"
      "F" -> "Float" to "f"
      "D" -> "Double" to "d"
      else -> "Object" to "l"
    }

  private fun defaultReturnStmt(returnType: String): String =
    when (returnType) {
      "V" -> "    return;"
      "Z", "B", "S", "C", "I", "J" -> "    return 0;"
      "F" -> "    return 0.0f;"
      "D" -> "    return 0.0;"
      else -> "    return NULL;"
    }

  private fun mangleStaticJniSymbol(
    clsDesc: String,
    methodName: String,
    paramTypes: List<String> = emptyList(),
    isOverloaded: Boolean = false,
  ): String {
    val clsPart = clsDesc.removePrefix("L").removeSuffix(";").replace("_", "_1").replace('/', '_')
    val namePart = methodName.replace("_", "_1").replace("<", "_0003c").replace(">", "_0003e")
    if (!isOverloaded) {
      return "Java_${clsPart}_$namePart"
    }
    val sigPart = paramTypes.joinToString("") { mangleJniType(it) }
    return "Java_${clsPart}_${namePart}__$sigPart"
  }

  private fun mangleJniType(desc: String): String =
    desc
      .replace("_", "_1")
      .replace(";", "_2")
      .replace("[", "_3")
      .replace('/', '_')

  private fun sanitizeIdent(raw: String): String =
    raw.replace(Regex("[^A-Za-z0-9_]"), "_").take(48)

  private fun sanitizeModuleName(raw: String): String =
    raw.trim().replace(Regex("[^A-Za-z0-9_]"), "_").ifEmpty { "dex2c_native" }

  private fun formatHex4u(offset: Int): String = String.format(Locale.US, "0x%04Xu", offset)

  private fun formatLabelOffset(offset: Int): String = String.format(Locale.US, "L_%04x", offset)

  private fun sanitizeCComment(raw: String): String =
    raw
      .replace("/*", "/ *")
      .replace("*/", "* /")
      .replace("\r", " ")
      .replace("\n", " ")

  private fun escapeC(raw: String): String =
    raw
      .replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\n", "\\n")
      .replace("\r", "\\r")
      .replace("\t", "\\t")
}
