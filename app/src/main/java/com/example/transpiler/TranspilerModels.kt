package com.example.transpiler

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

const val DEFAULT_FILTER_RULES =
  """# Kirlif/d2c & codehasan/dex2c filter.txt rules
# Lines starting with ! exclude matching classes/methods
!^L(androidx|android|kotlin|kotlinx|java|javax|com/google)/.*
!.*->(<clinit>)\(.*
.*"""

data class TranspilerConfig(
  val jniStyle: JniBindingStyle = JniBindingStyle.REGISTER_NATIVES,
  val targetLanguage: TargetCLanguage = TargetCLanguage.CPP17,
  val stringEncryption: Boolean = true,
  val controlFlowFlattening: Boolean = false,
  val nullCheckGuards: Boolean = true,
  val allowInit: Boolean = false,
  val loaderMode: LoaderMode = LoaderMode.AUTO_APP_OR_CUSTOM,
  val customLoaderClass: String = "amimo.dcc.DccApplication",
  val targetAbis: Set<String> = setOf("arm64-v8a", "armeabi-v7a"),
  val filterRulesText: String = DEFAULT_FILTER_RULES,
  val moduleName: String = "dex2c_native",
)

data class GeneratedFile(
  val fileName: String,
  val zipPath: String,
  val description: String,
  val content: String,
)

data class GeneratedBundle(
  val sourceFile: GeneratedFile,
  val headerFile: GeneratedFile,
  val androidMkFile: GeneratedFile,
  val applicationMkFile: GeneratedFile,
  val cmakeFile: GeneratedFile,
  val dccCfgFile: GeneratedFile,
  val filterTxtFile: GeneratedFile,
  val loaderStubFile: GeneratedFile,
  val transpiledMethodCount: Int,
  val instructionCount: Int,
  val warnings: List<String>,
) {
  val allFiles: List<GeneratedFile>
    get() =
      listOf(
        sourceFile,
        headerFile,
        androidMkFile,
        applicationMkFile,
        cmakeFile,
        dccCfgFile,
        filterTxtFile,
        loaderStubFile,
      )
}
