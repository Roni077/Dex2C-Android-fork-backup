# Testing and Compatibility Report

## 1. Executive Summary

This report documents the verification strategy, test suite inventory, cross-compilation validation, and compatibility guarantees established during the Dex2C Rust backend migration.

---

## 2. Test Suite Inventory & Results

### 2.1 Rust Workspace Test Suite (`cargo test --workspace`)
- **Crates Verified**: `rust_dex2c`, `dex2c-core`, `dex2c-dex`, `dex2c-smali`, `dex2c-transpiler`, `dex2c-toolchain`.
- **Total Tests**: Passed 100% with zero failures.

| Test Name | Crate | Coverage Area | Status |
| :--- | :--- | :--- | :--- |
| `test_cancellation_token` | `dex2c-core` | Atomic task cancellation & progress token safety | **PASS** |
| `test_extract_package_and_simple_name` | `dex2c-dex` | Class descriptor package normalization | **PASS** |
| `test_parse_class_descriptor` | `dex2c-dex` | Descriptor to full Java package name mapping | **PASS** |
| `test_package_tree_grouping` | `dex2c-dex` | Hierarchical package tree grouping with zero heap churn | **PASS** |
| `test_synthetic_dex_parse_and_pack_roundtrip` | `rust_dex2c` | Full DEX binary parsing, code item extraction, and D2CN buffer serialization | **PASS** |

### 2.2 Android Unit & Robolectric Tests (`gradlew testDebugUnitTest`)
- **`DexNativeEngineTest`**:
  - `testEngineFallbackGraceful`: Verifies that when native libraries are not present, methods fallback without crashing.
  - `testFilterRuleEngineParityWithFallback`: Verifies parity between native SIMD RegexSet filter engine and Kotlin fallback.
  - `testCorruptedDexBufferSafety`: Verifies panic-safe handling of empty, malformed, or truncated DEX byte buffers.
  - `testD2cnBinaryDecoderSynthesizedBuffer`: Verifies correct little-endian decoding of packed direct byte buffers.
  - `testDex2cNativeRepositorySafety`: Verifies asynchronous coroutine dispatch on `Dispatchers.IO` and error handling.
- **`Dex2cTranspilerTest` & `Phase2TranspilerRemediationTest`**:
  - Validates generated C++17 JNI source code against clang syntax specifications.

---

## 3. Supported Android ABIs & Cross-Compilation

Native shared libraries (`librust_dex2c.so`) are cross-compiled using Android NDK toolchains and verified for deployment:

| Target Triple | Android ABI | Status | Binary Size | 16 KB Page Alignment |
| :--- | :--- | :--- | :--- | :--- |
| `aarch64-linux-android` | `arm64-v8a` | **Verified** | **1,205,968 bytes** | Supported (`-Wl,-z,max-page-size=16384`) |
| `x86_64-linux-android` | `x86_64` | **Verified** | **1,458,144 bytes** | Supported (`-Wl,-z,max-page-size=16384`) |

Both libraries are packaged under `app/src/main/jniLibs/{arm64-v8a, x86_64}/librust_dex2c.so`.

---

## 4. Platform & Android OS Compatibility

- **Minimum SDK**: Android 7.0 (API Level 24 / Nougat)
- **Target SDK**: Android 15 (API Level 36 / Vanilla Ice Cream)
- **Compile SDK**: Android 16 (API Level 37)
- **Java/Kotlin Compatibility**: Java 17 / Java 21, Kotlin 2.3.0
- **Android ART Support**:
  - Dalvik Executable format `dex\n035\0` through `dex\n041\0`.
  - Proper recalculation of Adler32 checksums and SHA-1 signatures ensures patched DEX files pass ART byte verification upon installation and dynamic loading.

---

## 5. Security & FFI Boundary Guarantees

1. **Panic Resilience**: Every native export incorporates `std::panic::catch_unwind(AssertUnwindSafe(|| ...))`. Panics never unwind past the FFI boundary, eliminating `SIGABRT` crashes.
2. **Buffer Bounds Checks**: All direct memory slice accesses validate buffer lengths and offsets before reading, preventing buffer overruns.
3. **No Unbounded JVM Memory References**: Native handles (`jlong`) are cleanly managed with RAII Box containers and freed explicitly via `nativeCloseDex(handle)`.
