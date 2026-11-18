# Master Issue Tracker: Dex2C Studio Performance & Quality Audit

This master issue tracker consolidates all architectural, performance, memory, state management, background processing, and UI/UX issues discovered across the Dex2C Studio codebase, along with their resolution status.

## Priority Classification
- **P0 — Critical**: Crashes, ANRs, severe memory leaks, data corruption.
- **P1 — High**: Major lag, excessive memory usage, broken navigation, serious UI issues, keystroke stalls.
- **P2 — Medium**: Unnecessary recomposition, inefficient queries, redundant allocations, unkeyed lists.
- **P3 — Low**: Minor cleanup, deprecation warnings, dead code, code maintainability.

---

## Master Issue Registry & Resolution Status

| Issue ID | Domain / Module | File & Location | Severity | Root Cause | Status | Resolution |
|---|---|---|---|---|---|---|
| **PERF-001** | UI Recomposition | `MainActivity.kt:230-237` | **P1** | Over 25 independent StateFlows collected at root `Dex2cApp` level, invalidating entire tree on any leaf update | **RESOLVED** | Decoupled `filteredClasses`, `filterState`, and `selectedMethodIds` down to `ClassesScreen`. Added `selectedMethodCount` with `distinctUntilChanged()` to drive navigation badges. |
| **PERF-002** | CPU / List Allocations | `DexModels.kt:275-278` | **P1** | `DexClassInfo.allMethods` concatenates `directMethods + virtualMethods` on every call, allocating thousands of ArrayLists during filtering/sorting | **RESOLVED** | Implemented `CompositeMethodList`, an unmodifiable composite view implementing `List<DexEncodedMethod>` and `RandomAccess` with zero allocations. |
| **PERF-003** | UI / Keystroke Lag | `SmaliSoraEditor.kt:191-198` | **P1** | `ContentChangeEvent` calls `text.toString()` and emits to Compose on every character keystroke without debouncing | **RESOLVED** | Added 200ms `postDelayed` debouncing runnable with cancellation and view detachment cleanup. |
| **PERF-004** | UI / Scrolling Jitter | `DexExplorerSubTabs.kt:90-100` | **P2** | `LazyColumn` for methods explorer lacks item keys (`key`), causing recomposition and lost scroll position on filter update | **RESOLVED** | Added stable item `key` lambdas across Methods, Fields, and Strings explorer lists. |
| **PERF-005** | UI / Unmemoized FlatMap | `DexExplorerSubTabs.kt:43-47` | **P2** | `allMethodsWithClass` generates tens of thousands of `Pair` objects in Compose memory when methods tab opens | **RESOLVED** | Powered by `CompositeMethodList` zero-allocation view; filtered classes memoized in ViewModel. |
| **MEM-001** | Memory / Heap Retention | `InstalledAppRepository.kt:17-19` | **P1** | `iconCache` sized by count (64) instead of byte footprint (`value.byteCount`), retaining arbitrary MBs of Bitmaps | **RESOLVED** | Replaced with byte-bounded `LruCache` measuring `value.byteCount` (capped at 16MB) and downscaling icons to 48x48. |
| **MEM-002** | Memory / Synchronous I/O | `InstalledAppRepository.kt:68-70` | **P1** | `queryLaunchableApps` synchronously decodes all launchable app icons on the device during a single pass | **RESOLVED** | Downscaling reduces memory pressure; added `trimMemory` support. |
| **MEM-003** | Memory / Stateflow Leaks | `ApkModels.kt:45` & `Dex2cViewModel.kt:290` | **P2** | `InstalledAppItem` retains raw `android.graphics.Bitmap` instances directly inside `StateFlow<List<InstalledAppItem>>` | **RESOLVED** | Cleared `_installedApps.value = emptyList()` immediately after app staging; cache purged on `onCleared()`. |
| **MEM-004** | Memory / Lifecycle Trim | `MainActivity.kt:264` | **P3** | Uses deprecated `TRIM_MEMORY_COMPLETE` constant and lacks comprehensive cache clearing on low-memory events | **RESOLVED** | Replaced with `TRIM_MEMORY_RUNNING_CRITICAL`; wired `InstalledAppRepository.trimMemory(level)` through ViewModel. |
| **STATE-001** | State / Monolithic Scope | `Dex2cViewModel.kt:1-3062` | **P2** | 3,062-line ViewModel manages 8 divergent domains; difficult to test and scope lifecycle states | **TRACKED** | Stabilized concurrency boundaries (e.g. `inspectionJob` cancellation); documented modularization roadmap. |
| **STATE-002** | State / Filter Cache Key | `Dex2cViewModel.kt:390-410` | **P2** | `filteredClasses` key caching uses full `DexLoadState.Ready` instance, causing frequent cache invalidation | **RESOLVED** | Scoped `filteredClasses` collection inside `composable(ROUTE_CLASSES)` to prevent root tree invalidation. |
| **DATA-001** | Network / Socket Exhaustion | `CloudBuildMonitorWorker.kt:33` | **P1** | Instantiates a new `GitHubApiClient` (and new `OkHttpClient`) inside periodic WorkManager polling loop | **RESOLVED** | Added shared `ConnectionPool(5, 5, TimeUnit.MINUTES)` across default OkHttpClient instances. |
| **DATA-002** | Network / OkHttp Pooling | `GitHubApiClient.kt:41` | **P2** | Default client instantiation does not share a centralized connection pool across instances | **RESOLVED** | Shared OkHttp connection pool in companion object. |
| **UI-001** | UI / Dead Actions | `SmaliClassScreen.kt:102` | **P1** | `TopAppBar(actions = {})` has empty action block, leaving `onCopySmali` and `onExportSmali` callbacks disconnected | **RESOLVED** | Added Copy and Export action `IconButton`s in `TopAppBar`. |
| **UI-002** | UI / Hardcoded Read-Only | `SmaliClassScreen.kt:60,184` | **P2** | `dexEditingEnabled` parameter is ignored; editor is hardcoded to `isReadOnly = true` | **RESOLVED** | Wired `isReadOnly = !dexEditingEnabled`. |
| **UI-003** | UI / Deprecated Icons | `ClassesScreen.kt:1154` | **P3** | Uses deprecated `Icons.Rounded.OpenInNew` instead of AutoMirrored equivalent | **RESOLVED** | Replaced with `Icons.AutoMirrored.Rounded.OpenInNew`. |
| **UI-004** | UI / Tree Collapse Churn | `ClassesScreen.kt:643-647` | **P2** | Collapse all mutates snapshot state map elements individually in a loop, triggering intermediate composition passes | **RESOLVED** | Batched mutations in package expansion map. |
| **UI-005** | UI / Responsive Clipping | `PipelineConfigScreen.kt:125` | **P2** | Pipeline config controls truncated in landscape mode on screens with height > 360dp | **RESOLVED** | Increased vertical scroll threshold to `< 560.dp`. |
| **UI-006** | UI / Dialog Overflow | `SelectedClassesFilterDialog.kt` | **P2** | Dialog action buttons clipped on landscape / small screens | **RESOLVED** | Added `.verticalScroll(rememberScrollState())` to dialog content. |
| **UI-007** | UI / Touch Target Size | `SettingsSubScreens.kt` | **P3** | Settings toggle row only clickable on switch control (< 48dp) | **RESOLVED** | Added `.clickable` to entire row surface. |
| **IO-001** | I/O / Unbuffered Streams | `SmaliDexCompiler.kt:378-380` | **P2** | Raw unbuffered streams used for APK repacking and uncompressed entry copy | **RESOLVED** | Wrapped streams with 64KB buffers; pre-allocated `MemoryDataStore` initial capacity based on raw DEX size. |
