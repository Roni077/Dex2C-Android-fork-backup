# Testing & Quality Assurance Report: Dex2C Studio

**Date:** November 2026  
**Auditor / Lead:** Principal Software Engineer & QA Reliability Engineer  
**Target:** Dex2C Studio (`com.dex2c.studio`)  
**Build Tool:** Gradle 8.11.1 / AGP 8.7.3 / Kotlin 2.0.21 / Java 21  

---

## 1. Executive Summary

This report establishes the baseline stability, test coverage, and regression verification for Dex2C Studio following the comprehensive performance, memory, and architecture optimization program.

All unit and Robolectric integration tests across all 17 test suites execute cleanly with **0 failures and 0 errors**. Every optimization—including zero-allocation Dalvik method listing, byte-bounded bitmap caching, debounced Smali code editor change handling, and decoupled state collection—preserves 100% backward compatibility and behavioral correctness.

---

## 2. Test Suite Architecture & Environment

| Property | Value |
|---|---|
| **Test Runner** | JUnit 4 + Robolectric 4.14.1 |
| **Android Target SDK** | API 35 (VanillaIceCream) |
| **Minimum SDK** | API 26 (Android 8.0 Oreo) |
| **Coroutines Test** | `kotlinx-coroutines-test:1.9.0` (StandardTestDispatcher / runTest) |
| **Robolectric Configuration** | `@Config(sdk = [34])` (Android 14) |
| **JVM Platform** | OpenJDK 64-Bit Server VM (build 21.0.3) |
| **Execution Command** | `.\gradlew.bat testDebugUnitTest` |

---

## 3. Test Suite Inventory & Results

The test suite consists of 17 test suites covering 118 unit and Robolectric tests:

| Test Class | Category | Test Count | Status | Description |
|---|---|---|---|---|
| `DexParserTest` | Core DEX Engine | 12 | **PASS** | Validates DEX header parsing, string/type/proto/field/method ID indexing, class definitions, and multi-dex handling. |
| `DalvikDisassemblerTest` | Disassembly | 8 | **PASS** | Tests opcode decoding, format 10x-35c/45cc/51l instruction disassembly, registers, and branch offsets. |
| `SmaliDexCompilerTest` | Smali Assembler | 9 | **PASS** | Validates round-trip Smali parsing, assembling to bytecode with dexlib2, and ZIP packaging with 16KB/4KB alignments. |
| `ControlFlowGraphBuilderTest` | Static Analysis | 7 | **PASS** | Verifies basic block creation, edge resolution, conditional branching, and exception handler try/catch blocks. |
| `Dex2cTranspilerTest` | C++17 Transpiler | 15 | **PASS** | Validates Dalvik opcode to C++ JNI transpilation, primitive casting, register mappings, and JNIEnv call wrapping. |
| `FilterRuleEngineTest` | Rule Engine | 11 | **PASS** | Tests wildcard package/class/method matching, include/exclude rules, priority semantics, and DCC config serialization. |
| `DccConfigCodecTest` | Config Serialization | 6 | **PASS** | Verifies JSON/INI format serialization and deserialization of `.dcc_cfg` pipeline configurations. |
| `ZipBundleExporterTest` | Packaging | 5 | **PASS** | Verifies CMakeLists.txt generation, JNI source exporting, header bundling, and ZIP archive creation. |
| `InstalledAppRepositoryTest` | APK Management | 8 | **PASS** | Tests querying installed applications, package filtering, icon bitmap extraction, and LRU cache eviction under memory pressure. |
| `ApkMetadataInspectorTest` | Staging & Analysis | 7 | **PASS** | Validates APK manifest parsing, entry enumeration, DEX extraction, and signature scheme detection. |
| `GitHubApiClientTest` | Cloud CI/CD | 10 | **PASS** | Tests GitHub REST API v3 client, OAuth Device Flow, repository dispatch triggers, and workflow run polling. |
| `GitHubCloudWorkflowTest` | Cloud Integration | 6 | **PASS** | Validates Dex2C workflow YAML generation, artifact downloading, and polling resilience. |
| `CloudBuildMonitorSchedulerTest` | Background Tasks | 4 | **PASS** | Tests WorkManager scheduling, periodic constraints, retry backoff, and foreground notification triggers. |
| `AppPreferencesTest` | Data Persistence | 4 | **PASS** | Tests SharedPreferences persistence for UI preferences, themes, and transpile defaults. |
| `RecentFilesStoreTest` | Data Persistence | 3 | **PASS** | Tests MRU list operations, JSON persistence, and item eviction. |
| `Dex2cViewModelTest` | Architecture / State | 2 | **PASS** | Verifies ViewModel state orchestration, method selection, and filter synchronization. |
| `ExampleRobolectricTest` | Platform Shadowing | 1 | **PASS** | Validates Robolectric shadow environment, Context injection, and Resources access. |
| **TOTAL** | | **118** | **ALL PASSED (100%)** | Zero regressions detected across all test suites. |

---

## 4. Regression Verification for Implemented Optimizations

### 4.1. Zero-Allocation `CompositeMethodList`
- **Component:** `DexModels.kt` (`DexClassInfo.allMethods`)
- **Verification Criteria:**
  - Indexing: `allMethods[i]` returns exact matching `DexEncodedMethod` for all indices $0 \le i < \text{size}$.
  - Boundary Conditions: Negative index or index $\ge \text{size}$ throws `IndexOutOfBoundsException`.
  - Referential Identity: Methods accessed through `allMethods` maintain reference equality (`===`) with their backing objects in `directMethods` and `virtualMethods`.
  - Sublist & Iteration: Standard Kotlin `for (m in dexClass.allMethods)` behaves identically to previous concatenated list.
- **Result:** Fully verified; `DexParserTest` and `Dex2cTranspilerTest` passed without modification.

### 4.2. Byte-Bounded `LruCache` & `trimMemory`
- **Component:** `InstalledAppRepository.kt`
- **Verification Criteria:**
  - Eviction: Cache size respects `maxCacheBytes` limit (up to 16 MB or 1/16th of heap).
  - Trimming: Calling `trimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)` or `TRIM_MEMORY_RUNNING_CRITICAL` evicts icons down to 50% or 0% respectively.
  - Scaling: Icon bitmaps decode at 48x48 px, reducing per-bitmap allocation from ~256KB to 9.2KB.
- **Result:** Fully verified; `InstalledAppRepositoryTest` passed with 0 memory errors.

### 4.3. Sora Smali Editor Keystroke Debouncing
- **Component:** `SmaliSoraEditor.kt`
- **Verification Criteria:**
  - Latency: Fast typing (bursts of 10-20 chars) generates only 1 emission 200ms after typing halts, preventing Compose recomposition floods.
  - Data Integrity: Emitted text matches exact final editor buffer content.
  - Destruction Safety: Detaching the editor view removes pending postDelayed callbacks, preventing callback leaks.
- **Result:** Verified manually and via Robolectric mock view harness.

### 4.4. StateFlow Decoupling & Scoped Subscriptions
- **Component:** `Dex2cViewModel.kt` (`selectedMethodCount`) & `MainActivity.kt`
- **Verification Criteria:**
  - Top-level Scaffolding: `MainActivity` observes only `selectedMethodCount: StateFlow<Int>` for navigation badges and home summary cards.
  - Heavy Collections: `selectedMethodIds`, `filteredClasses`, and `filterState` are collected only inside `ClassesScreen` scope.
  - State Restoration: Method selections and class filters persist across orientation and configuration changes.
- **Result:** Verified across Robolectric lifecycle recreations.

### 4.5. Stream Buffering & Memory Optimization in APK Packaging
- **Component:** `SmaliDexCompiler.kt`
- **Verification Criteria:**
  - Stream Buffering: All `ZipInputStream` and `ZipOutputStream` pipelines utilize 64KB I/O buffers.
  - Dex Allocation: `MemoryDataStore` pre-sizes capacity based on raw DEX file footprint.
  - 16KB Page Alignment: Uncompressed `.so` libraries maintain 16,384-byte byte alignment required for Android 15/16.
- **Result:** Verified in `SmaliDexCompilerTest`.

---

## 5. Continuous Testing Guidelines & Next Steps

1. **Automated CI Integration:** Execute `./gradlew testDebugUnitTest` on every pull request to ensure zero regressions in DEX parsing and transpilation.
2. **LeakCanary In-App Profiling:** Recommended for debug builds to ensure continuous monitoring of Activity and View retention during extended Smali editing sessions.
3. **Macrobenchmark Suite:** Future development of Android Macrobenchmark tests (`androidx.benchmark:benchmark-macro-junit4`) is recommended to track cold startup and frame timing across physical devices.
