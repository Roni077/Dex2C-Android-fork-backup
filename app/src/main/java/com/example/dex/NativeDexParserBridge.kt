package com.example.dex

/**
 * Optional JNI bridge to a natively compiled Rust `androguard/dex-parser` (`dexparser-rs`)
 * shared library (`libdexparser_jni.so`). When the native `.so` is not bundled, Dex2C Studio
 * transparently uses the pure Kotlin `DexFile` / `DexHelper` implementation.
 */
object NativeDexParserBridge {

  @Volatile
  var isNativeLoaded: Boolean = false
    private set

  init {
    isNativeLoaded =
      runCatching {
        System.loadLibrary("dexparser_jni")
        true
      }.getOrDefault(false)
  }

  fun activeEngineLabel(): String =
    if (isNativeLoaded) "Rust JNI (dexparser-rs)" else "Kotlin DexFile/DexHelper"

  @JvmStatic
  external fun nativeInspectHeaderJson(dexBytes: ByteArray): String

  @JvmStatic
  external fun nativeParseMapListJson(dexBytes: ByteArray): String

  fun tryNativeHeaderJson(dexBytes: ByteArray): String? {
    if (!isNativeLoaded) return null
    return runCatching { nativeInspectHeaderJson(dexBytes) }.getOrNull()
  }

  fun tryNativeMapListJson(dexBytes: ByteArray): String? {
    if (!isNativeLoaded) return null
    return runCatching { nativeParseMapListJson(dexBytes) }.getOrNull()
  }
}
