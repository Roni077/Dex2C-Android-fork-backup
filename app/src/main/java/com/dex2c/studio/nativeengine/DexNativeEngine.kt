package com.dex2c.studio.nativeengine

import android.util.Log

/**
 * JNI bridge facade to the high-performance Rust native engine (`librust_dex2c.so`).
 * Provides transparent, zero-friction fallback to Kotlin implementations when the native
 * shared library is not compiled or loaded on the host platform.
 */
object DexNativeEngine {

  private const val TAG = "DexNativeEngine"
  private const val LIB_NAME = "rust_dex2c"

  val isAvailable: Boolean =
    try {
      System.loadLibrary(LIB_NAME)
      true
    } catch (e: Throwable) {
      safeLog(TAG, "Native Rust engine ($LIB_NAME) not loaded; utilizing Kotlin fallback: ${e.message}")
      false
    }

  val nativeEngineVersion: String? by lazy {
    if (isAvailable) {
      runCatching { nativeGetVersion() }.getOrNull()
    } else {
      null
    }
  }

  // --- External JNI Declarations ---

  @JvmStatic
  external fun nativeOpenDex(bytes: ByteArray, size: Int): Long

  @JvmStatic
  external fun nativeCloseDex(handle: Long)

  @JvmStatic
  external fun nativeGetClassCount(handle: Long): Int

  @JvmStatic
  external fun nativeGetStringCount(handle: Long): Int

  @JvmStatic
  external fun nativeFilterMethods(
    handle: Long,
    rules: Array<String>,
    allowInit: Boolean,
    skipSynthetic: Boolean,
  ): IntArray?

  @JvmStatic
  external fun nativeParseDex(handle: Long): java.nio.ByteBuffer?

  @JvmStatic
  external fun nativeGetVersion(): String?

  // --- Safe Kotlin Helper APIs ---

  /**
   * Fast DEX class indexing via the Rust native engine with direct ByteBuffer decoding.
   * Returns a list of [com.dex2c.studio.dex.DexClassInfo] instances, or null if the native engine is unavailable,
   * encounters corrupted structures, or is running on a platform without native libraries.
   */
  fun parseClassesFast(
    dexBytes: ByteArray,
    sourceEntryName: String = "classes.dex",
  ): List<com.dex2c.studio.dex.DexClassInfo>? {
    if (!isAvailable || dexBytes.isEmpty()) return null

    val handle = runCatching { nativeOpenDex(dexBytes, dexBytes.size) }.getOrDefault(0L)
    if (handle == 0L) return null

    return try {
      val byteBuffer = nativeParseDex(handle) ?: return null
      com.dex2c.studio.dex.D2cnBinaryDecoder.decodeClasses(byteBuffer, sourceEntryName)
    } catch (e: Throwable) {
      safeLog(TAG, "Native D2CN direct buffer decoding failed, falling back to Kotlin", e)
      null
    } finally {
      runCatching { nativeCloseDex(handle) }
    }
  }

  /**
   * Evaluates filter rules over a raw DEX binary using the Rust SIMD RegexSet engine.
   * Returns a set of matching method indices, or null if the native engine is unavailable.
   */
  fun evaluateFilterRules(
    dexBytes: ByteArray,
    rules: List<String>,
    allowInit: Boolean = false,
    skipSynthetic: Boolean = true,
  ): Set<Int>? {
    if (!isAvailable || dexBytes.isEmpty()) return null

    val handle = runCatching { nativeOpenDex(dexBytes, dexBytes.size) }.getOrDefault(0L)
    if (handle == 0L) return null

    return try {
      val matchedArray =
        nativeFilterMethods(
          handle = handle,
          rules = rules.toTypedArray(),
          allowInit = allowInit,
          skipSynthetic = skipSynthetic,
        )
      matchedArray?.toSet()
    } catch (e: Throwable) {
      safeLog(TAG, "Native filter evaluation failed, falling back to Kotlin", e)
      null
    } finally {
      runCatching { nativeCloseDex(handle) }
    }
  }

  private fun safeLog(tag: String, msg: String, tr: Throwable? = null) {
    runCatching {
      if (tr != null) Log.w(tag, msg, tr) else Log.d(tag, msg)
    }
  }
}
