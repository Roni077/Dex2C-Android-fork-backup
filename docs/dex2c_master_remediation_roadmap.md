# Dex2C Studio — Master Engineering Remediation Roadmap

## Overview & Codebase Audit Summary

**Dex2C Studio** (`com.dex2c.studio`) is an Android on-device Dalvik bytecode analyzer, in-memory Smali editor, Dalvik-to-C++17/C11 JNI transpiler, and GitHub Actions Cloud CI orchestrator.

This master document consolidates all issues, architectural bugs, security vulnerabilities, and Dalvik/ART runtime traps discovered during the deep analysis of the codebase (59 production Kotlin files / 30,725 LOC and 7 test files / 4,347 LOC). Every issue is organized into an actionable, phased execution roadmap with progress tracking checkboxes.

---

## Master Defect Ledger

| ID | Phase | Severity | Subsystem | Target File & Lines | Defect Summary | Status |
| :---: | :---: | :---: | :---: | :--- | :--- | :---: |
| **#01** | Phase 0 | **Critical** | UI / Engine | `SmaliClassScreen.kt:294-315` | "Assemble DEX" button executes destructive class deletion without pre-flight checks | `- [x]` |
| **#02** | Phase 0 | **Major** | UI / Engine | `ClassesScreen.kt:410-440` | "Replace all" string substitution executes without validation or diff preview | `- [x]` |
| **#03** | Phase 0 | **Critical** | UI / Engine | `ClassesScreen.kt:520-560` | Delete class action permanently destroys bytecode without undo history or confirmation | `- [x]` |
| **#04** | Phase 0 | **Critical** | UI / ViewModel | `Dex2cViewModel.kt:1243-1250` | Unchecked disassembly after compile triggers unhandled crash on missing class | `- [x]` |
| **#05** | Phase 1 | **Critical** | DEX Compiler | `SmaliDexCompiler.kt:59-63` | `compileAndReplaceClass` omits target class from DexPool, deleting the class | `- [x]` |
| **#06** | Phase 1 | **Critical** | DEX Compiler | `SmaliDexCompiler.kt:14-30` | `CustomDexDataStore` ignores random-access write offsets, corrupting DEX binary output | `- [x]` |
| **#07** | Phase 1 | **Major** | DEX Compiler | `SmaliDexCompiler.kt:124-161` | `replaceStringLiteralsInDex` is a 0-op stub returning 0 without replacing strings | `- [x]` |
| **#08** | Phase 1 | **Critical** | APK Repacking | `SmaliDexCompiler.kt:168-193` | Modified APK repacking preserves stale `META-INF` cryptographic signatures | `- [x]` |
| **#09** | Phase 1 | **Major** | APK Repacking | `SmaliDexCompiler.kt:168-193` | Repacking compresses `.so`/`resources.arsc` without 4-byte / 16KB page alignment | `- [x]` |
| **#10** | Phase 1 | **Medium** | DEX Parser | `DexParser.kt:705-715` | ULEB128 decoding has no iteration bound, vulnerable to infinite loop on corrupted DEX | `- [x]` |
| **#11** | Phase 2 | **Critical** | Transpiler | `Dex2cTranspiler.kt:836-840` | `packed-switch` & `sparse-switch` are dropped to C comments, breaking control flow | `- [x]` |
| **#12** | Phase 2 | **Critical** | Transpiler | `Dex2cTranspiler.kt:1068-1073` | `DEX2C_CHECK_NULL` lacks `return`, causing native kernel `SIGSEGV` on ART | `- [x]` |
| **#13** | Phase 2 | **Critical** | Transpiler | `Dex2cTranspiler.kt:655-666` | Catch dispatch calls `FindClass` during pending exception, violating JNI specification | `- [x]` |
| **#14** | Phase 2 | **Major** | Transpiler | `Dex2cTranspiler.kt:1020-1023` | `invoke-direct` uses virtual `CallMethod` instead of `CallNonvirtualMethod` | `- [x]` |
| **#15** | Phase 2 | **Major** | Transpiler | `Dex2cTranspiler.kt:1205-1212` | `aget-wide` & `aput-wide` treat `double[]` as `jlongArray`, failing ART CheckJNI | `- [x]` |
| **#16** | Phase 2 | **Major** | Transpiler | `Dex2cTranspiler.kt:1285-1289` | Static JNI symbol naming collides on overloaded methods (`Java_pkg_Cls_method`) | `- [x]` |
| **#17** | Phase 2 | **Major** | Transpiler | `Dex2cTranspiler.kt:1006-1031` | JNI local reference exhaustion on long methods (exceeds ART 512 table limit) | `- [x]` |
| **#18** | Phase 2 | **Minor** | Decompiler | `SmaliJavaDecompiler.kt:99-108` | Class Java preview outputs dummy method bodies (`// TODO: method body`, `return 0;`) | `- [x]` |
| **#19** | Phase 3 | **Critical** | Security | `CloudPreferences.kt:21` | GitHub personal OAuth access token stored in plaintext inside DataStore | `- [x]` |
| **#20** | Phase 3 | **Critical** | Security | `AndroidManifest.xml:17` | `allowBackup="true"` with blank rules leaks OAuth token to Google Cloud Auto-Backup | `- [x]` |
| **#21** | Phase 3 | **Medium** | Network | `NetworkResilience.kt:73-100` | `ResilientGitHubDns` bypasses VPN/system DNS via hardcoded DoH without user toggle | `- [x]` |
| **#22** | Phase 4 | **Major** | Tooling | Repository Root | Missing `gradlew`, `gradlew.bat`, and `gradle-wrapper.jar` breaks CLI automation | `- [ ]` |
| **#23** | Phase 4 | **Major** | Tooling | Repository Root | Missing `local.properties` breaks command-line builds without `ANDROID_HOME` | `- [ ]` |
| **#24** | Phase 4 | **Medium** | Tooling / Test | `app/src/test/` | Test suite mocks compiler outputs; lacks Clang C++ compilation verification test | `- [ ]` |
| **#25** | Phase 5 | **Major** | Verification | Whole System | Full regression test suite and round-trip bytecode integrity verification | `- [ ]` |

---

## Phase 0: Emergency Safety Interlocks & Blast-Radius Mitigation

**Goal**: Prevent data destruction, bytecode corruption, and app crashes until underlying engines are rebuilt.

- [x] **Task 0.1: Add Safety Interlock Feature Flag**
  - **File**: `app/build.gradle.kts`
  - **Action**: Add `buildConfigField("boolean", "ENABLE_DEX_EDITING", "false")`.
  - **Impact**: Hard-gates destructive byte writes until Phase 1 assembly is fully verified.

- [x] **Task 0.2: Gate "Assemble DEX" Button in Smali Editor**
  - **File**: [`app/src/main/java/com/dex2c/studio/ui/screens/SmaliClassScreen.kt`](file:///C:/Users/roni2/Downloads/Github-Projects/Dex2c-Android/app/src/main/java/com/dex2c/studio/ui/screens/SmaliClassScreen.kt)
  - **Action**: Check `BuildConfig.ENABLE_DEX_EDITING`. If `false`, display an info banner: *"Smali re-assembly is temporarily disabled while the in-memory compiler is being upgraded."*

- [x] **Task 0.3: Add Confirmation Dialog for Destructive Actions**
  - **Files**: 
    - [`app/src/main/java/com/dex2c/studio/ui/screens/ClassesScreen.kt`](file:///C:/Users/roni2/Downloads/Github-Projects/Dex2c-Android/app/src/main/java/com/dex2c/studio/ui/screens/ClassesScreen.kt)
    - [`app/src/main/java/com/dex2c/studio/ui/screens/SmaliClassScreen.kt`](file:///C:/Users/roni2/Downloads/Github-Projects/Dex2c-Android/app/src/main/java/com/dex2c/studio/ui/screens/SmaliClassScreen.kt)
  - **Action**: Intercept "Replace all" and "Delete Class" actions with an `AlertDialog` displaying affected class count and requiring user confirmation.

- [x] **Task 0.4: Implement Session-Scoped DEX Byte Undo Buffer**
  - **File**: [`app/src/main/java/com/dex2c/studio/ui/Dex2cViewModel.kt`](file:///C:/Users/roni2/Downloads/Github-Projects/Dex2c-Android/app/src/main/java/com/dex2c/studio/ui/Dex2cViewModel.kt)
  - **Action**: Preserve `previousDexBytes: ByteArray?` before modifying any DEX binary in-memory. Add `undoLastDexModification()` to revert on error.

- [x] **Task 0.5: Guard Post-Compile Disassembly Against Missing Classes**
  - **File**: [`app/src/main/java/com/dex2c/studio/ui/Dex2cViewModel.kt`](file:///C:/Users/roni2/Downloads/Github-Projects/Dex2c-Android/app/src/main/java/com/dex2c/studio/ui/Dex2cViewModel.kt)
  - **Action**: In `compileAndReplaceClass()`, assert that `updatedParsed.classes.any { it.descriptor == targetClassDescriptor }` is true before triggering `SmaliClassDisassembler.disassembleClass()`. If missing, emit an error state instead of crashing.

---

## Phase 1: In-Memory Smali Assembly, MemoryDataStore & DEX Repacking

**Goal**: Restore real Smali compilation and produce valid, bootable, signed DEX and APK binaries.

- [x] **Task 1.1: Implement Random-Access `MemoryDataStore`**
  - **File**: `app/src/main/java/com/dex2c/studio/dex/MemoryDataStore.kt` [NEW]
  - **Action**: Implement `com.android.tools.smali.dexlib2.writer.io.DexDataStore` using an expanding byte buffer supporting non-linear seeking in `outputAt(fileOffset: Int)`. Replace `CustomDexDataStore`.

- [x] **Task 1.2: Implement Real Smali Assembly in `compileAndReplaceClass`**
  - **File**: [`app/src/main/java/com/dex2c/studio/dex/SmaliDexCompiler.kt`](file:///C:/Users/roni2/Downloads/Github-Projects/Dex2c-Android/app/src/main/java/com/dex2c/studio/dex/SmaliDexCompiler.kt)
  - **Action**: Parse `editedSmaliText` into a `ClassDef` using the `smali` assembler. Intern all untouched classes *plus* the freshly assembled class into `DexPool`. Write out via `MemoryDataStore`.

- [x] **Task 1.3: Implement Bytecode-Level String Replacement**
  - **File**: [`app/src/main/java/com/dex2c/studio/dex/SmaliDexCompiler.kt`](file:///C:/Users/roni2/Downloads/Github-Projects/Dex2c-Android/app/src/main/java/com/dex2c/studio/dex/SmaliDexCompiler.kt)
  - **Action**: Traverse instructions in `ClassDef`, replace target string literals in `const-string` and `const-string/jumbo` instructions, track replacement count, and rebuild the DEX pool.

- [x] **Task 1.4: Fix APK Repacking Cryptographic Signatures**
  - **File**: [`app/src/main/java/com/dex2c/studio/dex/SmaliDexCompiler.kt`](file:///C:/Users/roni2/Downloads/Github-Projects/Dex2c-Android/app/src/main/java/com/dex2c/studio/dex/SmaliDexCompiler.kt)
  - **Action**: In `repackApkWithPatchedDexes()`, filter out `META-INF/*.SF`, `META-INF/*.RSA`, `META-INF/*.DSA`, and `META-INF/MANIFEST.MF` so the output APK can be properly re-signed.

- [x] **Task 1.5: Enforce 4-Byte / 16KB Page Alignment on Repacked APKs**
  - **File**: [`app/src/main/java/com/dex2c/studio/dex/SmaliDexCompiler.kt`](file:///C:/Users/roni2/Downloads/Github-Projects/Dex2c-Android/app/src/main/java/com/dex2c/studio/dex/SmaliDexCompiler.kt)
  - **Action**: Store `resources.arsc` uncompressed with 4-byte alignment, and native `.so` libraries with 16KB alignment (Android 15/16 page-size compatibility).

- [x] **Task 1.6: Add Defensive Bound Check to ULEB128 Reader**
  - **File**: [`app/src/main/java/com/dex2c/studio/dex/DexParser.kt`](file:///C:/Users/roni2/Downloads/Github-Projects/Dex2c-Android/app/src/main/java/com/dex2c/studio/dex/DexParser.kt)
  - **Action**: In `UlebReader.readUleb128()`, cap iterations at 5 (35 bits) to prevent CPU starvation on corrupted DEX files.

---

## Phase 2: Transpiler JNI Runtime Safety & Dalvik Instruction Parity

**Goal**: Eliminate native kernel `SIGSEGV` crashes and ensure 100% specification-compliant JNI C++ code generation.

- [x] **Task 2.1: Implement C++ Switch Statement Generation**
  - **File**: [`app/src/main/java/com/dex2c/studio/transpiler/Dex2cTranspiler.kt`](file:///C:/Users/roni2/Downloads/Github-Projects/Dex2c-Android/app/src/main/java/com/dex2c/studio/transpiler/Dex2cTranspiler.kt)
  - **Action**: In `emitInstructionC()`, decode `packed-switch` and `sparse-switch` payloads from `insn.payload`. Generate C++ `switch (v[vx].i) { case K: goto L_target; }` blocks with fall-through to default label.

- [x] **Task 2.2: Add Missing Control Flow Returns to JNI Exception Guard Macros**
  - **File**: [`app/src/main/java/com/dex2c/studio/transpiler/Dex2cTranspiler.kt`](file:///C:/Users/roni2/Downloads/Github-Projects/Dex2c-Android/app/src/main/java/com/dex2c/studio/transpiler/Dex2cTranspiler.kt)
  - **Action**: Update `DEX2C_CHECK_NULL` and `DEX2C_THROW_CCE` in `buildRuntimeHeader()` to include a `return` statement:
    ```c
    #define DEX2C_CHECK_NULL(env, ref) do { \
        if ((ref) == NULL) { \
            jclass __npe = DEX2C_FIND_CLASS(env, "java/lang/NullPointerException"); \
            if (__npe != NULL) DEX2C_THROW_NEW(env, __npe, "Dex2C null reference"); \
            return 0; \
        } \
    } while (0)
    ```

- [x] **Task 2.3: Fix Exception Catch Dispatch (JNI Spec Compliance)**
  - **File**: [`app/src/main/java/com/dex2c/studio/transpiler/Dex2cTranspiler.kt`](file:///C:/Users/roni2/Downloads/Github-Projects/Dex2c-Android/app/src/main/java/com/dex2c/studio/transpiler/Dex2cTranspiler.kt)
  - **Action**: In `emitTryCatchDispatchIfApplicable()`, clear the pending exception via `ExceptionClear()` before calling `FindClass()`. Test `IsInstanceOf()`. If no handler matches, re-throw the original exception with `Throw(__exc)`.

- [x] **Task 2.4: Fix Non-Virtual Dispatch for `invoke-direct`**
  - **File**: [`app/src/main/java/com/dex2c/studio/transpiler/Dex2cTranspiler.kt`](file:///C:/Users/roni2/Downloads/Github-Projects/Dex2c-Android/app/src/main/java/com/dex2c/studio/transpiler/Dex2cTranspiler.kt)
  - **Action**: In `emitInvokeInstruction()`, detect `invoke-direct` (private methods and `<init>`) and emit `CallNonvirtual<Type>Method` passing `__mcls` instead of standard `Call<Type>Method`.

- [x] **Task 2.5: Fix Wide Array Access for `double[]` Types**
  - **File**: [`app/src/main/java/com/dex2c/studio/transpiler/Dex2cTranspiler.kt`](file:///C:/Users/roni2/Downloads/Github-Projects/Dex2c-Android/app/src/main/java/com/dex2c/studio/transpiler/Dex2cTranspiler.kt)
  - **Action**: In `emitArrayAccessInstruction()`, differentiate between `long[]` and `double[]` arrays to call `GetDoubleArrayRegion` instead of forcing `jlongArray`, preventing ART CheckJNI aborts.

- [x] **Task 2.6: Fix Static JNI Symbol Name Mangling for Overloads**
  - **File**: [`app/src/main/java/com/dex2c/studio/transpiler/Dex2cTranspiler.kt`](file:///C:/Users/roni2/Downloads/Github-Projects/Dex2c-Android/app/src/main/java/com/dex2c/studio/transpiler/Dex2cTranspiler.kt)
  - **Action**: In `mangleStaticJniSymbol()`, check if the method is overloaded in its declaring class. If so, append `__` and JNI type-mangled parameter signatures (e.g. `Java_pkg_Cls_foo__ILjava_lang_String_2`) to prevent C compiler duplicate symbol errors.

- [x] **Task 2.7: Scope JNI Local References to Prevent ART 512 Overflow**
  - **File**: [`app/src/main/java/com/dex2c/studio/transpiler/Dex2cTranspiler.kt`](file:///C:/Users/roni2/Downloads/Github-Projects/Dex2c-Android/app/src/main/java/com/dex2c/studio/transpiler/Dex2cTranspiler.kt)
  - **Action**: Wrap transpiled native function bodies with `env->PushLocalFrame(64)` and `env->PopLocalFrame(NULL)` to guarantee local reference table reclamation.

- [x] **Task 2.8: Enhance Java Skeletal Decompiler Preview**
  - **File**: [`app/src/main/java/com/dex2c/studio/dex/SmaliJavaDecompiler.kt`](file:///C:/Users/roni2/Downloads/Github-Projects/Dex2c-Android/app/src/main/java/com/dex2c/studio/dex/SmaliJavaDecompiler.kt)
  - **Action**: Replace `// TODO: method body` placeholders with disassembled Dalvik instruction comments or pseudo-Java statements.

---

## Phase 3: Security Hardening & Android Auto-Backup Exclusion

**Goal**: Seal credential leakage pathways and ensure compliance with Android security best practices.

- [x] **Task 3.1: Exclude Cloud Preferences from Android Auto-Backup**
  - **Files**:
    - [`app/src/main/res/xml/backup_rules.xml`](file:///C:/Users/roni2/Downloads/Github-Projects/Dex2c-Android/app/src/main/res/xml/backup_rules.xml)
    - [`app/src/main/res/xml/data_extraction_rules.xml`](file:///C:/Users/roni2/Downloads/Github-Projects/Dex2c-Android/app/src/main/res/xml/data_extraction_rules.xml)
  - **Action**: Explicitly exclude `dex2c_cloud_prefs.preferences_pb` from cloud backups and device transfers:
    ```xml
    <exclude domain="sharedpref" path="dex2c_cloud_prefs.preferences_pb" />
    ```

- [x] **Task 3.2: Encrypt Sensitive GitHub OAuth Tokens at Rest**
  - **File**: [`app/src/main/java/com/dex2c/studio/data/CloudPreferences.kt`](file:///C:/Users/roni2/Downloads/Github-Projects/Dex2c-Android/app/src/main/java/com/dex2c/studio/data/CloudPreferences.kt)
  - **Action**: Integrate Android Keystore or AES-GCM encryption for `github_access_token` so credentials are never stored in raw plaintext on disk.

- [x] **Task 3.3: Add Settings Toggle for DNS-over-HTTPS (DoH) Fallback**
  - **Files**:
    - [`app/src/main/java/com/dex2c/studio/cloud/NetworkResilience.kt`](file:///C:/Users/roni2/Downloads/Github-Projects/Dex2c-Android/app/src/main/java/com/dex2c/studio/cloud/NetworkResilience.kt)
    - [`app/src/main/java/com/dex2c/studio/ui/screens/SettingsScreen.kt`](file:///C:/Users/roni2/Downloads/Github-Projects/Dex2c-Android/app/src/main/java/com/dex2c/studio/ui/screens/SettingsScreen.kt)
  - **Action**: Make DoH queries to `1.1.1.1` and `8.8.8.8` opt-in via a user toggle (default disabled) to respect corporate and private VPN routing. Remove `Thread.sleep(150L)` from DNS lookup.

---

## Phase 4: Build Tooling, Gradle Wrapper & Environment Setup

**Goal**: Ensure reproducible, headless CLI compilation and developer tooling compatibility.

- [ ] **Task 4.1: Generate Standard Gradle 9.4.1 Wrapper Scripts**
  - **Files**:
    - `gradlew` [NEW]
    - `gradlew.bat` [NEW]
    - `gradle/wrapper/gradle-wrapper.jar` [NEW]
  - **Action**: Place standard wrapper scripts in repository root so `android describe`, CI runners, and local developer terminals can invoke builds without system-wide Gradle.

- [ ] **Task 4.2: Add Local SDK Configuration Template**
  - **File**: `local.properties` [NEW]
  - **Action**: Create `local.properties` pointing to `sdk.dir=C:\\Users\\roni2\\AppData\\Local\\Android\\Sdk` to unblock local CLI builds.

- [ ] **Task 4.3: Add Automated Clang C++ Syntax Validation Unit Test**
  - **File**: `app/src/test/java/com/dex2c/studio/transpiler/ClangSyntaxValidationTest.kt` [NEW]
  - **Action**: Add unit test that transpiles representative bytecode methods (switch, catch blocks, wide arrays) and validates generated C++ syntax against Clang headers.

---

## Phase 5: Automated Verification & Test Suite Execution

**Goal**: Full verification of patched components, zero regressions, and complete end-to-end testing.

- [ ] **Task 5.1: Execute Full Unit Test Suite**
  - **Command**: `gradle :app:testDebugUnitTest`
  - **Expected Result**: 100% tests passing across all 7 test files.

- [ ] **Task 5.2: Verify Smali Re-Assembly Round-Trip**
  - **Action**: Parse a DEX, edit an instruction in Smali, compile via `SmaliDexCompiler`, and assert:
    - Target class exists in output DEX.
    - Updated instruction bytecode matches edited Smali.
    - Class count matches original DEX class count.

- [ ] **Task 5.3: Verify C++ Switch CodeGen and JNI Guard Execution**
  - **Action**: Transpile a class with `packed-switch` and `sparse-switch`, compile generated C++ with Android NDK Clang, and verify zero compiler warnings or errors.

- [ ] **Task 5.4: Verify APK Repacking Alignment & Clean Signatures**
  - **Action**: Repack an APK, verify that `META-INF` digests are removed, and assert `zipalign -c 4` and `16KB` alignment succeed.

- [ ] **Task 5.5: Update Safety Flag (`ENABLE_DEX_EDITING = true`)**
  - **File**: `app/build.gradle.kts`
  - **Action**: Enable `ENABLE_DEX_EDITING` once Phase 1–4 validations pass.
