# Dex2C Studio — End-to-End Application Workflow & Technical Architecture

**Dex2C Studio** is an all-in-one Android reverse engineering, DEX/Smali modification, Java decompilation, and Dalvik-to-C++17 native transpilation IDE. It combines the capabilities of **Kirlif/d2c & codehasan/dex2c** native method transpilation with **Dex-Editor-Android**'s in-memory Smali editing, Java decompilation, global string/field/method inspection, and patched DEX/APK export.

---

## 1. High-Level System Architecture & Data Pipeline

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                        1. BINARY INGESTION & PARSING                         │
│  [SAF Picker / Installed Apps / Self APK] ──► [ApkDexExtractor (Streaming)]  │
│        ──► [DexParser (Header, Map, Strings, Fields, Methods, Bytecode)]     │
└──────────────────────────────────────┬───────────────────────────────────────┘
                                       ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│                     2. DEX EXPLORER & GLOBAL PATCHING                        │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐  ┌────────────┐  ┌─────────┐ │
│  │  CLASSES   │  │  METHODS   │  │   FIELDS   │  │  STRINGS   │  │  RULES  │ │
│  │ Tree/Flat  │  │ Signatures │  │ Types/Refs │  │ Bulk Patch │  │ Builder │ │
│  └─────┬──────┘  └─────┬──────┘  └────────────┘  └─────┬──────┘  └────┬────┘ │
└────────┼───────────────┼───────────────────────────────┼──────────────┼──────┘
         ▼               ▼                               ▼              ▼
┌───────────────────────────────────┐         ┌────────────────────────────────┐
│   3. SMALI EDITOR & DECOMPILERS   │         │   4. DEX2C TRANSPILER ENGINE   │
│ • View / Edit Smali (AutoComplete)│         │ • FilterRuleEngine (W/B Regex) │
│ • Label Jump (:cond, :goto, :try) │         │ • Method CFG & Basic Blocks    │
│ • SmaliJavaDecompiler (Java View) │         │ • Dalvik-to-C++17 Transpiler   │
│ • SmaliDexCompiler (DexPool)      │         │ • JNI RegisterNatives Gen      │
└────────────────┬──────────────────┘         └────────────────┬───────────────┘
                 ▼                                             ▼
┌───────────────────────────────────┐         ┌────────────────────────────────┐
│   5A. PATCHED DEX / APK EXPORT    │         │  5B. LOCAL & CLOUD CI BUILD    │
│ • In-Memory CustomDexDataStore    │         │ • Local Zip (C++/CMake/Android)│
│ • Repackage Multi-DEX into APK    │         │ • GitHub OAuth + Actions CI    │
│ • Export via Android SAF          │         │ • WorkManager Push Alerts      │
└───────────────────────────────────┘         └────────────────────────────────┘
```

---

## 2. Phase 1: Binary Ingestion & Multi-DEX Streaming

### User Workflow
1. Launch **Dex2C Studio** and navigate to the **Home** screen.
2. Select a binary input source via:
   - **Device Storage (SAF Picker)**: Load any `.apk` or standalone `.dex` file (`classes.dex`, `classes2.dex`, etc.).
   - **Installed Applications**: Pick any user or system package installed on the device.
   - **Self-APK Inspection**: Load Dex2C Studio's own compiled APK for immediate testing.
   - **Recent History**: Resume inspection from cached recent binaries with quick source filtering (`ALL`, `STORAGE`, `APPS`).
3. Switch between **Overview** (metrics, bytecode summary, quick filter focus) and **Header & Map** (raw DEX header magic `035`–`041`, SHA-1 signature, Alder32 checksum, and `map_list` offsets).
4. For multi-DEX APKs, select any specific `classesN.dex` entry or inspect the merged multi-DEX summary.

### Technical Architecture
- **`ApkDexExtractor`**: Streams ZIP entries (`classes*.dex` and `AndroidManifest.xml`) from APK files without extracting temporary files to disk.
- **`DexParser`**: Parses the 112-byte DEX header, string IDs, type IDs, proto IDs, field IDs, method IDs, class definitions (`class_def_item`), and Dalvik bytecode payloads (`code_item`).
- **Memory Optimization**: Uses lazy pool resolution and releases unused multi-DEX byte buffers when switching active DEX entries.

---

## 3. Phase 2: DEX Explorer (Classes, Methods, Fields, Strings & Rules)

### User Workflow
Navigate to the **Classes** tab to access five dedicated sub-tabs:
1. **CLASSES (`EXPLORER`)**:
   - Toggle between hierarchical **Package Tree View** (with middle-package compaction and APK package auto-detection badges) and **Flat Class List**.
   - Filter by framework classes (`Hide Framework`), bytecode presence (`Only Bytecode`), selected methods, or active `filter.txt` rules.
   - Enable **Select Mode** to batch-select classes/methods for C++ transpilation, apply one-tap `+W` (Whitelist) or `!B` (Blacklist) package/class rules, or **Delete Class** directly from the active DEX.
2. **METHODS**:
   - Browse and search all method signatures across the active DEX (`Lpackage/Class;->method(Args)Ret`), filter by access flags (`public`, `native`, `static`), and tap any method to inspect its bytecode/Java/C++ translation.
3. **FIELDS**:
   - Search static and instance fields across all classes by field name, declaring class, or type descriptor.
4. **STRINGS**:
   - Inspect the entire DEX string constant pool (`string_ids`) and perform **Global Search & Replace** (case-sensitive or case-insensitive) to patch hardcoded URLs, keys, or labels across all classes in memory.
5. **RULES (`FILTER_RULES`)**:
   - Visually build Whitelist and Blacklist regex rules or edit raw `filter.txt` lines with real-time match counts.

### Technical Architecture
- **Debounced Background Filtering**: Search inputs are debounced and evaluated on background coroutine dispatchers (`Dispatchers.Default`) to maintain 60–120fps scrolling even on APKs with 10,000+ classes.

---

## 4. Phase 3: Interactive Smali Editor, Java Decompiler & In-Memory Assembly

### User Workflow
Tap any class in the Explorer to open the **Smali Class Screen**, which supports three synchronized modes:
1. **View Mode (Interactive Disassembly)**:
   - Syntax-highlighted Smali viewer with collapsible method spans, sticky method range header, register/label token highlighting, and interactive **Label Jump** (tap `:cond_*`, `:goto_*`, `:try_start_*`, or `:switch_*` to jump directly to target lines).
   - Includes a full-featured search bar supporting **Whole Word**, **Regex**, and category filters (**Opcodes**, **Strings**, **Methods**, **Registers**).
2. **Edit Mode (Live Smali Editor)**:
   - Modify Smali instructions line-by-line with **Auto-Complete Suggestion Chips** for Dalvik opcodes (`const-string`, `invoke-virtual`, `if-eqz`, `return-void`), registers (`v0`–`v15`, `p0`–`p4`), and class descriptors.
   - Includes multi-step **Undo / Redo**, **Search & Replace**, and one-tap **Compile to DEX** (`Assemble with smali-dexlib2`).
3. **Java Mode (Decompiler Preview)**:
   - View reconstructed Java source code for the entire class (`SmaliJavaDecompiler`), including package declarations, fields, method signatures, control flow constructs, and reconstructed expressions.

### Technical Architecture
- **`SmaliClassDisassembler`**: Uses Google's `baksmali` (`ClassDefinition` & `BaksmaliOptions`) to disassemble `DexBackedClassDef` instances into structured Smali lines and method spans.
- **`SmaliEditorEngine`**: Provides context-aware opcode/register auto-completion, label target resolution, and regex/literal search-replace.
- **`SmaliDexCompiler`**: Uses `smali` lexer/parser (`smaliFlexLexer`, `smaliParser`, `smaliTreeWalker`) to assemble edited Smali source into a `ClassDef` and repacks the entire DEX in memory via `DexPool` and `CustomDexDataStore` (`ByteArrayOutputStream`).

---

## 5. Phase 4: Method Inspection, Control Flow Graph (CFG) & C++17 Transpiler

### User Workflow
Tap any method in a class or the Methods Explorer to open the **Method Detail Screen**:
1. **C++17 / JNI Tab**:
   - Inspect the generated native C++ function (`JNIEXPORT ... JNICALL`), JNI `RegisterNatives` table entry, local register variables (`v0`, `v1`, `p0`), exception checks (` env->ExceptionCheck() `), and instruction-level comments.
2. **Java Decompiler Tab**:
   - Preview the method's reconstructed Java statements alongside the C++ output for rapid logic verification.
3. **Smali Tab**:
   - View the method's isolated Smali disassembly with interactive token inspection.
4. **Instructions & Basic Blocks (CFG) Tab**:
   - Inspect decoded Dalvik instructions (offsets, opcodes, register operands) and the **Control Flow Graph (CFG)** showing Basic Blocks (`BB_0`, `BB_1`, ...), branch edges, exception handler ranges, and `packed-switch` / `sparse-switch` / `fill-array-data` payloads.

### Technical Architecture
- **`Dex2cTranspiler`**: Translates Dalvik 16-bit code units into C++17 or C99 JNI calls (`CallObjectMethodV`, `GetObjectField`, `NewStringUTF`, arithmetic/bitwise operations, and `goto` basic-block labels).
- **`MethodCfgBuilder`**: Computes basic block boundaries from branch targets, fallthrough paths, `try/catch` handlers, and switch payload tables.

---

## 6. Phase 5: Filter Rules (`filter.txt`) & Pipeline Configuration (`dcc.cfg`)

### User Workflow
Configure how Dex2C Studio selects and transpiles methods via the **Rules** tab or **Pipeline Config**:
1. **Structured & Raw Filter Rules (`filter.txt`)**:
   - **Whitelist Rules**: `com/example/security/.*;.*` (transpile all methods in package).
   - **Blacklist Rules**: `!.*<clinit>.*` or `!androidx/.*;.*` (exclude matching classes/methods).
   - **One-Tap Presets**: Quickly toggle common rules such as `onCreate`, `checkSignature`, `verifyToken`, or framework exclusions.
2. **Transpiler Pipeline Options (`dcc.cfg` Parity)**:
   - **JNI Binding Style**: Choose `RegisterNatives` (`JNI_OnLoad` dynamic registration) or static JNI symbol export (`Java_pkg_Class_method`).
   - **C/C++ Dialect**: Toggle between `C++17 (env->)` and `C99 ((*env)->)`.
   - **Constructor Support (`--allow-init`)**: Optionally allow transpiling `<init>` constructors.
   - **Custom Native Loader**: Configure the `System.loadLibrary("<moduleName>")` bootstrap class and target ABIs (`arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`).

---

## 7. Phase 6: Output Export, Patched DEX/APK & GitHub Cloud CI

### User Workflow
Dex2C Studio supports three distinct export and build pathways:
1. **Export Patched DEX or Patched APK (SAF)**:
   - From the **Classes** screen top bar menu, export the in-memory modified DEX (`patched_classes.dex`) or repackage the modified DEX directly into a complete `.apk` archive (`patched_app.apk`) via Android's Storage Access Framework.
2. **Local Native C++/JNI Bundle Export (`OutputExportScreen`)**:
   - Preview all generated artifacts (`dex2c_generated.cpp`, `dex2c_generated.h`, `CMakeLists.txt`, `Android.mk`, `Application.mk`, `dcc.cfg`, `filter.txt`) and export them as a ready-to-compile NDK `.zip` archive.
3. **GitHub Cloud CI Build (`CloudBuildScreen`)**:
   - Authenticate via **GitHub OAuth Device Flow** (`github.com/login/device`), link a private workspace repository, and dispatch an automated `.github/workflows/dex2c.yml` workflow that compiles the C++ sources with the Android NDK, patches the APK, signs it, and delivers background completion alerts via `WorkManager`.

---

## 8. Phase 7: Performance & Memory Optimization Architecture

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                     MEMORY & CONCURRENCY SAFEGUARDS                          │
│  • Bounded LRU Caches: Smali disassembly, Java decompilation, & CFG caches   │
│  • Navigation Auto-Trim: Trims transient caches on screen transitions        │
│  • Low-Memory Callbacks: ComponentCallbacks2 clears non-essential pools      │
│  • Zero Disk Leaks: 100% in-memory CustomDexDataStore & streaming ZIP I/O    │
└──────────────────────────────────────────────────────────────────────────────┘
```

- **Auto-Trimming Caches**: Disassembled Smali classes, decompiled Java previews, and transpiled C++ snippets are stored in bounded caches that automatically trim when navigating back to the Explorer or when the OS signals `onTrimMemory`.
- **Fast Spring Motion**: Screen transitions use physics-based spatial springs (`ExpressiveMotion.spatialSpring()`) with subtle fades for responsive UI feedback.
