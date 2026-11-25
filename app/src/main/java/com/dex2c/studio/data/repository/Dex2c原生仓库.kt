package com.dex2c.studio.data.repository

import com.dex2c.studio.dex.DexClassInfo
import com.dex2c.studio.nativeengine.DexNativeEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * High-performance repository interfacing between ViewModel state machines and
 * the Rust native backend engine (`librust_dex2c.so`).
 * Guarantees zero main-thread blocking by dispatching all compute-heavy operations onto Dispatchers.IO.
 */
class Dex2cNativeRepository {

  val isAvailable: Boolean
    get() = DexNativeEngine.isAvailable

  val version: String?
    get() = DexNativeEngine.nativeEngineVersion

  suspend fun parseClasses(
    dexBytes: ByteArray,
    sourceEntryName: String = "classes.dex",
  ): List<DexClassInfo>? = withContext(Dispatchers.IO) {
    DexNativeEngine.parseClassesFast(dexBytes, sourceEntryName)
  }

  suspend fun evaluateFilterRules(
    dexBytes: ByteArray,
    rules: List<String>,
    allowInit: Boolean = false,
    skipSynthetic: Boolean = true,
  ): DexNativeEngine.NativeFilterResult? = withContext(Dispatchers.IO) {
    DexNativeEngine.evaluateFilterRulesFast(dexBytes, rules, allowInit, skipSynthetic)
  }

  suspend fun disassembleSmali(
    dexBytes: ByteArray,
    classDefIdx: Int,
  ): String? = withContext(Dispatchers.IO) {
    DexNativeEngine.disassembleSmaliFast(dexBytes, classDefIdx)
  }

  suspend fun transpileMethod(
    dexBytes: ByteArray,
    classDefIdx: Int,
    methodIdx: Int,
  ): String? = withContext(Dispatchers.IO) {
    DexNativeEngine.transpileMethodFast(dexBytes, classDefIdx, methodIdx)
  }

  suspend fun patchDex(
    dexBytes: ByteArray,
    methodIndices: Set<Int>,
  ): ByteArray? = withContext(Dispatchers.IO) {
    DexNativeEngine.patchDexMethodsFast(dexBytes, methodIndices)
  }
}
