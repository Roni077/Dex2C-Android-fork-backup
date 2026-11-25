package com.dex2c.studio.transpiler

import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

enum class JniBindingStyle(val label: String, val summary: String) {
  REGISTER_NATIVES("RegisterNatives (JNI_OnLoad)", "Binds native tables dynamically at load time—hides symbol names from ELF exports"),
  STATIC_SYMBOLS("Static Java_* Symbols", "Exports standard Java_pkg_Class_method C symbols directly"),
}

enum class TargetCLanguage(val label: String, val extension: String) {
  C11("C11 (<jni.h>)", "c"),
  CPP17("C++17 (env->)", "cpp"),
}

enum class LoaderMode(val label: String, val description: String) {
  AUTO_APP_OR_CUSTOM("Auto App / Fallback Loader", "Uses existing Application class when present, otherwise injects custom loader"),
  CUSTOM_LOADER("Force Custom Loader", "Always generate and bind the configured DccApplication loader class"),
  MANUAL_LOAD_LIBRARY("Manual loadLibrary()", "Emit companion System.loadLibrary() stubs for direct class integration"),
}

val ALL_NDK_ABIS = listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")

const val LEGACY_DEFAULT_FILTER_RULES =
  """# codehasan/dex2c filter.txt rules
# Lines starting with ! exclude matching classes/methods
!^L(androidx|android|kotlin|kotlinx|java|javax|com/google)/.*
!.*->(<clinit>)\(.*
.*"""

val MANDATORY_CLOUD_SAFETY_EXCLUSIONS =
  listOf(
    "!^L(androidx|android|kotlin|kotlinx|java|javax|com/google|okhttp3|okio|org/jetbrains|org/intellij|_COROUTINE)/.*",
    "!.*(\\\$\\\$ExternalSyntheticLambda|ComposableSingletons\\\$|/R\\\$|/R\$|/BuildConfig\$).*",
    "!.*;(invokeSuspend|<clinit>).*",
    "!.*->(invokeSuspend|<clinit>)\\(.*",
  )

const val DEFAULT_FILTER_RULES = ".*"

enum class OllvmPreset(val label: String, val summary: String) {
  BALANCED("Balanced", "Recommended: Control flow flattening, instruction substitution, basic block split & 16KB alignment"),
  MAXIMUM("Maximum", "Heavy defense: All passes active with maximum iteration loops & bogus control flow"),
  FAST("Fast / Zero-Overhead", "Lightweight: String XOR & 16KB alignment without LLVM IR bloat"),
  CUSTOM("Custom", "Fine-tune individual passes, loop counts, and custom Clang flags"),
}

data class OllvmConfig(
  val enabled: Boolean = false,
  val preset: OllvmPreset = OllvmPreset.BALANCED,
  val controlFlowFlattening: Boolean = true,     // -mllvm -fla
  val instructionSubstitution: Boolean = true,  // -mllvm -sub
  val subLoop: Int = 3,                         // -mllvm -sub_loop=3
  val bogusControlFlow: Boolean = false,        // -mllvm -bcf
  val bcfLoop: Int = 3,                         // -mllvm -bcf_loop=3
  val bcfProb: Int = 50,                        // -mllvm -bcf_prob=50
  val basicBlockSplit: Boolean = true,          // -mllvm -split
  val splitNum: Int = 3,                        // -mllvm -split_num=3
  val stringObfuscation: Boolean = false,       // -mllvm -sobf
  val align16KbPages: Boolean = true,           // -Wl,-z,max-page-size=16384
  val customFlags: String = "",
) {
  fun buildClangFlags(): String {
    if (!enabled) return ""
    val flags = mutableListOf("-fvisibility=hidden")
    if (controlFlowFlattening) flags += "-mllvm -fla"
    if (basicBlockSplit) {
      flags += "-mllvm -split"
      if (splitNum > 1) flags += "-mllvm -split_num=$splitNum"
    }
    if (instructionSubstitution) {
      flags += "-mllvm -sub"
      if (subLoop > 1) flags += "-mllvm -sub_loop=$subLoop"
    }
    if (stringObfuscation) flags += "-mllvm -sobf"
    if (bogusControlFlow) {
      flags += "-mllvm -bcf"
      if (bcfLoop > 1) flags += "-mllvm -bcf_loop=$bcfLoop"
      if (bcfProb in 1..100) flags += "-mllvm -bcf_prob=$bcfProb"
    }
    if (customFlags.isNotBlank()) flags += customFlags.trim()
    return flags.joinToString(" ")
  }

  fun activePassCount(): Int {
    if (!enabled) return 0
    var count = 0
    if (controlFlowFlattening) count++
    if (instructionSubstitution) count++
    if (bogusControlFlow) count++
    if (basicBlockSplit) count++
    if (stringObfuscation) count++
    if (align16KbPages) count++
    if (customFlags.isNotBlank()) count++
    return count
  }

  fun applyPreset(newPreset: OllvmPreset): OllvmConfig =
    when (newPreset) {
      OllvmPreset.BALANCED -> copy(
        enabled = true,
        preset = newPreset,
        controlFlowFlattening = true,
        instructionSubstitution = true,
        subLoop = 3,
        bogusControlFlow = false,
        basicBlockSplit = true,
        splitNum = 3,
        stringObfuscation = false,
        align16KbPages = true,
      )
      OllvmPreset.MAXIMUM -> copy(
        enabled = true,
        preset = newPreset,
        controlFlowFlattening = true,
        instructionSubstitution = true,
        subLoop = 5,
        bogusControlFlow = true,
        bcfLoop = 5,
        bcfProb = 100,
        basicBlockSplit = true,
        splitNum = 5,
        stringObfuscation = true,
        align16KbPages = true,
      )
      OllvmPreset.FAST -> copy(
        enabled = false,
        preset = newPreset,
        controlFlowFlattening = false,
        instructionSubstitution = false,
        bogusControlFlow = false,
        basicBlockSplit = false,
        stringObfuscation = false,
        align16KbPages = true,
      )
      OllvmPreset.CUSTOM -> copy(
        preset = newPreset,
      )
    }
}

data class DccSignatureConfig(
  val keystorePath: String = "keystore/debug.keystore",
  val alias: String = "androiddebugkey",
  val keystorePass: String = "android",
  val storePass: String = "android",
  val v1Enabled: Boolean = true,
  val v2Enabled: Boolean = true,
  val v3Enabled: Boolean = true,
)

data class 转译器配置(
  val jniStyle: JniBindingStyle = JniBindingStyle.REGISTER_NATIVES,
  val targetLanguage: TargetCLanguage = TargetCLanguage.CPP17,
  val stringEncryption: Boolean = true,
  val controlFlowFlattening: Boolean = false,
  val nullCheckGuards: Boolean = true,
  val allowInit: Boolean = false,
  val skipSynthetic: Boolean = true,
  val loaderMode: LoaderMode = LoaderMode.AUTO_APP_OR_CUSTOM,
  val customLoaderClass: String = "amimo.dcc.DccApplication",
  val forceCustomLoader: Boolean = false,
  val targetAbis: Set<String> = setOf("arm64-v8a", "armeabi-v7a"),
  val filterRulesText: String = DEFAULT_FILTER_RULES,
  val moduleName: String = "dex2c_native",
  val ollvm: OllvmConfig = OllvmConfig(),
  val signature: DccSignatureConfig = DccSignatureConfig(),
)
typealias TranspilerConfig = 转译器配置

data class 转译进度(
  val completedMethods: Int = 0,
  val totalMethods: Int = 0,
  val activeChunkIndex: Int = 1,
  val totalChunks: Int = 1,
) {
  val fraction: Float
    get() =
      if (totalMethods <= 0) 0f
      else (completedMethods.toFloat() / totalMethods.toFloat()).coerceIn(0f, 1f)
}
typealias TranspileProgress = 转译进度

data class 生成文件(
  val fileName: String,
  val zipPath: String,
  val description: String,
  val content: String,
  val backingFile: File? = null,
  val totalByteSize: Long = backingFile?.takeIf { it.exists() }?.length() ?: content.length.toLong(),
  val totalLineCount: Int =
    if (content.isEmpty()) 0
    else content.count { it == '\n' } + (if (content.endsWith('\n')) 0 else 1),
  val isPreviewTruncated: Boolean = false,
) {
  fun openInputStream(): InputStream =
    if (backingFile != null && backingFile.exists()) {
      FileInputStream(backingFile)
    } else {
      ByteArrayInputStream(content.toByteArray(Charsets.UTF_8))
    }
}
typealias GeneratedFile = 生成文件

data class 生成文件包(
  val sourceFile: 生成文件,
  val headerFile: 生成文件,
  val androidMkFile: 生成文件,
  val applicationMkFile: 生成文件,
  val cmakeFile: 生成文件,
  val dccCfgFile: 生成文件,
  val filterTxtFile: 生成文件,
  val loaderStubFile: 生成文件,
  val transpiledMethodCount: Int,
  val instructionCount: Int,
  val warnings: List<String>,
  val sourceFiles: List<生成文件> = listOf(sourceFile),
  val loaderSmaliFile: 生成文件? = null,
) {
  val allFiles: List<生成文件>
    get() =
      buildList {
        addAll(sourceFiles)
        add(headerFile)
        add(androidMkFile)
        add(applicationMkFile)
        add(cmakeFile)
        add(dccCfgFile)
        add(filterTxtFile)
        add(loaderStubFile)
        if (loaderSmaliFile != null) add(loaderSmaliFile)
      }
}
typealias GeneratedBundle = 生成文件包
