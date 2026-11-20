# Graph Report - Dex2c-Android  (2026-11-20)

## Corpus Check
- 130 files · ~734,273 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 1531 nodes · 2606 edges · 106 communities (74 shown, 32 thin omitted)
- Extraction: 83% EXTRACTED · 17% INFERRED · 0% AMBIGUOUS · INFERRED: 442 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `f65338cd`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- [[_COMMUNITY_Community 0|Community 0]]
- [[_COMMUNITY_Community 1|Community 1]]
- [[_COMMUNITY_Community 2|Community 2]]
- [[_COMMUNITY_Community 3|Community 3]]
- [[_COMMUNITY_Community 4|Community 4]]
- [[_COMMUNITY_Community 5|Community 5]]
- [[_COMMUNITY_Community 6|Community 6]]
- [[_COMMUNITY_Community 7|Community 7]]
- [[_COMMUNITY_Community 8|Community 8]]
- [[_COMMUNITY_Community 9|Community 9]]
- [[_COMMUNITY_Community 10|Community 10]]
- [[_COMMUNITY_Community 11|Community 11]]
- [[_COMMUNITY_Community 12|Community 12]]
- [[_COMMUNITY_Community 13|Community 13]]
- [[_COMMUNITY_Community 14|Community 14]]
- [[_COMMUNITY_Community 15|Community 15]]
- [[_COMMUNITY_Community 16|Community 16]]
- [[_COMMUNITY_Community 17|Community 17]]
- [[_COMMUNITY_Community 18|Community 18]]
- [[_COMMUNITY_Community 19|Community 19]]
- [[_COMMUNITY_Community 20|Community 20]]
- [[_COMMUNITY_Community 21|Community 21]]
- [[_COMMUNITY_Community 22|Community 22]]
- [[_COMMUNITY_Community 23|Community 23]]
- [[_COMMUNITY_Community 24|Community 24]]
- [[_COMMUNITY_Community 25|Community 25]]
- [[_COMMUNITY_Community 26|Community 26]]
- [[_COMMUNITY_Community 27|Community 27]]
- [[_COMMUNITY_Community 28|Community 28]]
- [[_COMMUNITY_Community 29|Community 29]]
- [[_COMMUNITY_Community 30|Community 30]]
- [[_COMMUNITY_Community 31|Community 31]]
- [[_COMMUNITY_Community 32|Community 32]]
- [[_COMMUNITY_Community 33|Community 33]]
- [[_COMMUNITY_Community 34|Community 34]]
- [[_COMMUNITY_Community 35|Community 35]]
- [[_COMMUNITY_Community 36|Community 36]]
- [[_COMMUNITY_Community 37|Community 37]]
- [[_COMMUNITY_Community 38|Community 38]]
- [[_COMMUNITY_Community 40|Community 40]]
- [[_COMMUNITY_Community 41|Community 41]]
- [[_COMMUNITY_Community 42|Community 42]]
- [[_COMMUNITY_Community 43|Community 43]]
- [[_COMMUNITY_Community 44|Community 44]]
- [[_COMMUNITY_Community 45|Community 45]]
- [[_COMMUNITY_Community 46|Community 46]]
- [[_COMMUNITY_Community 47|Community 47]]
- [[_COMMUNITY_Community 48|Community 48]]
- [[_COMMUNITY_Community 49|Community 49]]
- [[_COMMUNITY_Community 50|Community 50]]
- [[_COMMUNITY_Community 51|Community 51]]
- [[_COMMUNITY_Community 52|Community 52]]
- [[_COMMUNITY_Community 53|Community 53]]
- [[_COMMUNITY_Community 54|Community 54]]
- [[_COMMUNITY_Community 55|Community 55]]
- [[_COMMUNITY_Community 56|Community 56]]
- [[_COMMUNITY_Community 57|Community 57]]
- [[_COMMUNITY_Community 58|Community 58]]
- [[_COMMUNITY_Community 59|Community 59]]
- [[_COMMUNITY_Community 64|Community 64]]
- [[_COMMUNITY_Community 65|Community 65]]
- [[_COMMUNITY_Community 66|Community 66]]
- [[_COMMUNITY_Community 67|Community 67]]
- [[_COMMUNITY_Community 68|Community 68]]
- [[_COMMUNITY_Community 69|Community 69]]
- [[_COMMUNITY_Community 70|Community 70]]
- [[_COMMUNITY_Community 71|Community 71]]
- [[_COMMUNITY_Community 72|Community 72]]
- [[_COMMUNITY_Community 74|Community 74]]
- [[_COMMUNITY_Community 75|Community 75]]
- [[_COMMUNITY_Community 76|Community 76]]
- [[_COMMUNITY_Community 77|Community 77]]
- [[_COMMUNITY_Community 78|Community 78]]
- [[_COMMUNITY_Community 79|Community 79]]
- [[_COMMUNITY_Community 80|Community 80]]
- [[_COMMUNITY_Community 81|Community 81]]
- [[_COMMUNITY_Community 82|Community 82]]
- [[_COMMUNITY_Community 83|Community 83]]
- [[_COMMUNITY_Community 84|Community 84]]
- [[_COMMUNITY_Community 85|Community 85]]
- [[_COMMUNITY_Community 86|Community 86]]
- [[_COMMUNITY_Community 87|Community 87]]
- [[_COMMUNITY_Community 88|Community 88]]
- [[_COMMUNITY_Community 89|Community 89]]
- [[_COMMUNITY_Community 90|Community 90]]
- [[_COMMUNITY_Community 91|Community 91]]
- [[_COMMUNITY_Community 92|Community 92]]
- [[_COMMUNITY_Community 93|Community 93]]
- [[_COMMUNITY_Community 94|Community 94]]
- [[_COMMUNITY_Community 95|Community 95]]
- [[_COMMUNITY_Community 96|Community 96]]
- [[_COMMUNITY_Community 97|Community 97]]
- [[_COMMUNITY_Community 98|Community 98]]
- [[_COMMUNITY_Community 99|Community 99]]
- [[_COMMUNITY_Community 100|Community 100]]
- [[_COMMUNITY_Community 101|Community 101]]
- [[_COMMUNITY_Community 102|Community 102]]
- [[_COMMUNITY_Community 103|Community 103]]
- [[_COMMUNITY_Community 107|Community 107]]

## God Nodes (most connected - your core abstractions)
1. `Dex2cViewModel` - 117 edges
2. `TagPill()` - 42 edges
3. `Dex2cTranspiler` - 35 edges
4. `TranspilerConfig` - 28 edges
5. `GitHubApiClient` - 26 edges
6. `SmaliClassScreen()` - 26 edges
7. `ClassesScreen()` - 25 edges
8. `DexMethodRef` - 19 edges
9. `DexParser` - 19 edges
10. `DexParsedFile` - 18 edges

## Surprising Connections (you probably didn't know these)
- `Dex2cApp()` --calls--> `rememberWindowAdaptiveInfo()`  [INFERRED]
  app/src/main/java/com/dex2c/studio/MainActivity.kt → app/src/main/java/com/dex2c/studio/ui/AdaptiveLayout.kt
- `Dex2cApp()` --calls--> `HomeScreen()`  [INFERRED]
  app/src/main/java/com/dex2c/studio/MainActivity.kt → app/src/main/java/com/dex2c/studio/ui/screens/HomeScreen.kt
- `Dex2cApp()` --calls--> `ClassesScreen()`  [INFERRED]
  app/src/main/java/com/dex2c/studio/MainActivity.kt → app/src/main/java/com/dex2c/studio/ui/screens/ClassesScreen.kt
- `Dex2cApp()` --calls--> `PipelineConfigScreen()`  [INFERRED]
  app/src/main/java/com/dex2c/studio/MainActivity.kt → app/src/main/java/com/dex2c/studio/ui/screens/PipelineConfigScreen.kt
- `Dex2cApp()` --calls--> `CloudBuildScreen()`  [INFERRED]
  app/src/main/java/com/dex2c/studio/MainActivity.kt → app/src/main/java/com/dex2c/studio/ui/screens/CloudBuildScreen.kt

## Communities (106 total, 32 thin omitted)

### Community 0 - "Community 0"
Cohesion: 0.10
Nodes (10): descriptorToJavaName(), ArrayMeta, Dex2cTranspiler, NativeRegRecord, StreamedFileBuilder, GeneratedBundle, GeneratedFile, JniBindingStyle (+2 more)

### Community 2 - "Community 2"
Cohesion: 0.15
Nodes (3): CloudSettings, GitHubWorkflowStep, ExampleRobolectricTest

### Community 3 - "Community 3"
Cohesion: 0.10
Nodes (20): 0. Anti-Pattern: Performative Reasoning, 10. Final Answer Protocol, 1. Problem Extraction, 2. Assumption Firewall, 3. Solution Generation, 4. Verification Layer, 5. Adversarial Attack, 6. Confidence Calibration (+12 more)

### Community 4 - "Community 4"
Cohesion: 0.06
Nodes (40): ClassMethodsSelectorSheet(), MethodSelectorItem, applySearchHighlights(), extractLineInteractiveTokens(), extractQuickMethodTokens(), findCategoryTokenRanges(), findEnclosingMethodName(), findLineMatchRanges() (+32 more)

### Community 5 - "Community 5"
Cohesion: 0.10
Nodes (7): DccConfigCodec, FilterMatchSummary, FilterPresetItem, FilterRuleEngine, FilterRuleType, SimpleJsonParser, StructuredFilterRule

### Community 6 - "Community 6"
Cohesion: 0.10
Nodes (15): AccentColorPickerSection(), AccentSwatchDot(), CustomAccentHexDialog(), ExpressiveMotion, expressivePressScale(), ExpressiveTonalCard(), AccentPreset, AppIconStyle (+7 more)

### Community 7 - "Community 7"
Cohesion: 0.16
Nodes (24): OnboardingBalancedFooterLayout(), OnboardingHorizontalPager(), OnboardingPagerDots(), ChecklistReviewRow(), CloudStepTimelineRow(), CodePeekToggleChip(), OnboardingCloudStepsCard(), OnboardingDalvikCppPeekCard() (+16 more)

### Community 8 - "Community 8"
Cohesion: 0.09
Nodes (10): ApkMetadataInspector, ArchiveMeta, ApkStagingPreview, AppScopeFilter, BinarySourceType, InstalledAppItem, PickerSortOrder, PickerTab (+2 more)

### Community 9 - "Community 9"
Cohesion: 0.09
Nodes (22): 1. High-Level System Architecture & Data Pipeline, 2. Phase 1: Binary Ingestion & Multi-DEX Streaming, 3. Phase 2: DEX Explorer (Classes, Methods, Fields, Strings & Rules), 4. Phase 3: Interactive Smali Editor, Java Decompiler & In-Memory Assembly, 5. Phase 4: Method Inspection, Control Flow Graph (CFG) & C++17 Transpiler, 6. Phase 5: Filter Rules (`filter.txt`) & Pipeline Configuration (`dcc.cfg`), 7. Phase 6: Output Export, Patched DEX/APK & GitHub Cloud CI, 7. Phase 6: Patched DEX/APK Export & GitHub Cloud CI (+14 more)

### Community 10 - "Community 10"
Cohesion: 0.09
Nodes (22): 1. High-Level System Architecture & Data Pipeline, 2. Phase 1: Binary Ingestion & Multi-DEX Streaming, 3. Phase 2: DEX Explorer (Classes, Methods, Fields, Strings & Rules), 4. Phase 3: Interactive Smali Editor, Java Decompiler & In-Memory Assembly, 5. Phase 4: Method Inspection, Control Flow Graph (CFG) & C++17 Transpiler, 6. Phase 5: Filter Rules (`filter.txt`) & Pipeline Configuration (`dcc.cfg`), 7. Phase 6: Output Export, Patched DEX/APK & GitHub Cloud CI, 7. Phase 6: Patched DEX/APK Export & GitHub Cloud CI (+14 more)

### Community 11 - "Community 11"
Cohesion: 0.10
Nodes (20): Authenticated, CloudBuildRunState, CloudErrorAction, CloudSigningMode, Completed, DeviceAuthState, DeviceCodeResponse, Dispatching (+12 more)

### Community 12 - "Community 12"
Cohesion: 0.08
Nodes (25): patterns, patterns, patterns, patterns, patterns, name, patterns, patterns (+17 more)

### Community 15 - "Community 15"
Cohesion: 0.10
Nodes (19): buildBytecodeOverviewSummary(), BytecodeOverviewSummary, ClassDefinitionSummary, CompositeMethodList, DalvikPayload, descriptorToSimpleName(), DexAnnotationEntry, DexCallSiteInfo (+11 more)

### Community 17 - "Community 17"
Cohesion: 0.40
Nodes (10): ConfigSectionCard(), FilterRulesCard(), JniBindingCard(), LoaderFlagsCard(), PipelineCategory, PipelineCategoryPill(), PipelineConfigScreen(), ProtectionPassesCard() (+2 more)

### Community 18 - "Community 18"
Cohesion: 0.18
Nodes (3): SmaliClassDisassembler, SmaliDisassemblyResult, SmaliMethodSpan

### Community 19 - "Community 19"
Cohesion: 0.06
Nodes (21): DalvikDisassembler, Fmt, LebReader, OpSpec, RefKind, reg(), DalvikInstruction, DexCatchHandler (+13 more)

### Community 20 - "Community 20"
Cohesion: 0.07
Nodes (10): MemoryDataStore, Phase1DexRemediationTest, CountingOutputStream, CustomDexDataStore, DexCompileResult, Error, SmaliDexCompiler, Success (+2 more)

### Community 21 - "Community 21"
Cohesion: 0.08
Nodes (10): defaultClient(), isIpv4Literal(), isNumericIpLiteral(), NetworkResilience, parseDohARecords(), prioritizeIpv4(), ResilientGitHubDns, KeystoreTokenEncryptor (+2 more)

### Community 22 - "Community 22"
Cohesion: 0.05
Nodes (41): 1. High-Level System Architecture & In-Memory Data Pipeline, 2. User Navigation & Screen State Machine, 3. Deep-Dive Operational Workflows (Step-by-Step), 4. Real-World Practical Usage Recipes, 5. Performance & Memory Safeguards Summary, code:mermaid (flowchart TD), code:mermaid (stateDiagram-v2), code:mermaid (sequenceDiagram) (+33 more)

### Community 23 - "Community 23"
Cohesion: 0.27
Nodes (4): RecentBinaryItem, Error, Loading, Ready

### Community 24 - "Community 24"
Cohesion: 0.14
Nodes (9): ApkStagingState, ClassSortOrder, ClassTypeFilter, CloudApkChoice, DexLoadState, Empty, MethodInspectionState, MethodScopeFilter (+1 more)

### Community 25 - "Community 25"
Cohesion: 0.11
Nodes (5): AwaitingUser, CloudTimeUtil, DexEditSession, DexEditUndoEntry, DexEditSessionTest

### Community 28 - "Community 28"
Cohesion: 0.18
Nodes (4): CloudBuildMonitorScheduler, CloudBuildMonitorWorker, CloudPreferences, Keys

### Community 29 - "Community 29"
Cohesion: 0.11
Nodes (18): 1. Summary of Modified Files, 2.10. `app/src/main/java/com/dex2c/studio/dex/SmaliDexCompiler.kt`, 2.10. `app/src/main/java/com/dex2c/studio/MainActivity.kt` & `app/src/main/java/com/dex2c/studio/ui/Dex2cViewModel.kt`, 2.11. `app/src/main/java/com/dex2c/studio/dex/SmaliDexCompiler.kt`, 2.1. `app/src/main/java/com/dex2c/studio/dex/DexModels.kt`, 2.2. `app/src/main/java/com/dex2c/studio/apk/InstalledAppRepository.kt`, 2.3. `app/src/main/java/com/dex2c/studio/ui/components/SmaliSoraEditor.kt`, 2.4. `app/src/main/java/com/dex2c/studio/ui/screens/SmaliClassScreen.kt` (+10 more)

### Community 30 - "Community 30"
Cohesion: 0.60
Nodes (5): BuildMonitorCard(), formatArtifactBytes(), MonitorGaugeBlock(), StepProgressList(), TransferProgressPanel()

### Community 31 - "Community 31"
Cohesion: 0.17
Nodes (11): code:c (#define DEX2C_CHECK_NULL(env, ref) do { \), code:xml (<exclude domain="sharedpref" path="dex2c_cloud_prefs.prefere), Dex2C Studio — Master Engineering Remediation Roadmap, Master Defect Ledger, Overview & Codebase Audit Summary, Phase 0: Emergency Safety Interlocks & Blast-Radius Mitigation, Phase 1: In-Memory Smali Assembly, MemoryDataStore & DEX Repacking, Phase 2: Transpiler JNI Runtime Safety & Dalvik Instruction Parity (+3 more)

### Community 32 - "Community 32"
Cohesion: 0.27
Nodes (6): BasicBlockInfo, BlockTerminatorKind, CfgEdge, CfgEdgeKind, ControlFlowGraphBuilder, MethodControlFlowGraph

### Community 33 - "Community 33"
Cohesion: 0.24
Nodes (11): BasicBlockCard(), CfgEdgePill(), DalvikCfgViewer(), PayloadDetailCard(), AnnotationBadgeRow(), DexMapListCard(), DexMethodMetaViewer(), HeaderKeyValueRow() (+3 more)

### Community 34 - "Community 34"
Cohesion: 0.16
Nodes (9): Denied, Expired, Failure, GitHubRepoInfo, Pending, ProgressByteRequestBody, SlowDown, Success (+1 more)

### Community 37 - "Community 37"
Cohesion: 0.35
Nodes (10): CompactHeaderCell(), DexBinaryHeaderCard(), DexContainersStrip(), EmptyWorkspaceCard(), formatBytes(), HeroHomeBanner(), HomeMetricsGrid(), HomeScreen() (+2 more)

### Community 40 - "Community 40"
Cohesion: 0.17
Nodes (11): 1. Executive Summary, 2. Test Suite Architecture & Environment, 3. Test Suite Inventory & Results, 4.1. Zero-Allocation `CompositeMethodList`, 4.2. Byte-Bounded `LruCache` & `trimMemory`, 4.3. Sora Smali Editor Keystroke Debouncing, 4.4. StateFlow Decoupling & Scoped Subscriptions, 4.5. Stream Buffering & Memory Optimization in APK Packaging (+3 more)

### Community 41 - "Community 41"
Cohesion: 0.18
Nodes (10): 1. Executive Program Overview, 2. Before vs. After Quantitative Scorecard, 3.1. Zero-Allocation Collections (`CompositeMethodList`), 3.2. Byte-Bounded Memory Management & System Trimming, 3.3. Compose Stateflow Decoupling, 3.4. Debounced Code Editor & Enhanced UX, 3. Key Architectural & System Improvements, 4. Documentation Suite Index (+2 more)

### Community 43 - "Community 43"
Cohesion: 0.25
Nodes (5): CloudDeepLinkTarget, CloudLiveNotificationContent, CloudLivePhase, CloudNotificationContent, NotificationThemeColors

### Community 47 - "Community 47"
Cohesion: 0.36
Nodes (5): WorkflowDocumentationRepository, WorkflowGuideDialog(), WorkflowSection, WorkflowSectionCard(), WorkflowViewMode

### Community 48 - "Community 48"
Cohesion: 0.17
Nodes (15): normalizePackageName(), buildNestedPackageTree(), ClassesSubTab, ClassesViewMode, ClassRow, CompactClassRow(), ExplorerRowItem, InlineBadge() (+7 more)

### Community 49 - "Community 49"
Cohesion: 0.40
Nodes (4): description, majorCapabilities, name, requestFramePermissions

### Community 50 - "Community 50"
Cohesion: 0.83
Nodes (3): DeviceAuthStateBody(), GitHubDeviceAuthCard(), InlineRepoSetupStatusRow()

### Community 53 - "Community 53"
Cohesion: 0.07
Nodes (29): code:powershell (# 1. Install Rust via winget), code:bash (cargo ndk -t arm64-v8a -t x86_64 -o app/src/main/jniLibs bui), Dex2C Studio: Rust Native Backend Engine Implementation Phases, 🎯 Executive Summary & Objectives, Goals, Goals, Goals, Goals (+21 more)

### Community 59 - "Community 59"
Cohesion: 0.09
Nodes (22): BufferWriter, build_package_tree(), ClassMethodSummary, DexClassDefFull, DexCodeMeta, DexFieldMeta, DexFile, DexFile<'a> (+14 more)

### Community 65 - "Community 65"
Cohesion: 0.30
Nodes (3): ApkDexEntry, DexMapItem, DexParser

### Community 66 - "Community 66"
Cohesion: 0.15
Nodes (10): CompiledFilter, DexFilterMatchResult, evaluate_dex_methods(), evaluate_dex_methods_and_classes(), to_java_name(), bytemuck_cast(), DexHolder, Java_com_dex2c_studio_nativeengine_DexNativeEngine_nativeFilterClassesAndMethods() (+2 more)

### Community 68 - "Community 68"
Cohesion: 0.18
Nodes (10): colors, editor.background, editor.foreground, editor.lineHighlightBackground, editor.selectionBackground, editorLineNumber.activeForeground, editorLineNumber.foreground, name (+2 more)

### Community 69 - "Community 69"
Cohesion: 0.18
Nodes (10): colors, editor.background, editor.foreground, editor.lineHighlightBackground, editor.selectionBackground, editorLineNumber.activeForeground, editorLineNumber.foreground, name (+2 more)

### Community 70 - "Community 70"
Cohesion: 0.44
Nodes (8): ApkPickerSheet(), formatApkSize(), InstalledAppRow(), InstalledAppsTab(), RecentBinariesTab(), RecentItemIcon(), StoragePickerTab(), RecentQuickStrip()

### Community 71 - "Community 71"
Cohesion: 0.20
Nodes (9): autoClosingPairs, brackets, comments, lineComment, folding, markers, end, start (+1 more)

### Community 72 - "Community 72"
Cohesion: 0.15
Nodes (4): DexClassInfo, DexHeaderInfo, DexParsedFile, UlebReader

### Community 74 - "Community 74"
Cohesion: 0.40
Nodes (13): SettingsDetailHost(), AboutRow(), LiveThemePreviewCard(), SettingsAboutSubScreen(), SettingsAppearanceSubScreen(), SettingsCodeEditorSubScreen(), SettingsDexParserSubScreen(), SettingsGitHubCloudSubScreen() (+5 more)

### Community 76 - "Community 76"
Cohesion: 0.20
Nodes (5): ClassPickerSheet(), ClassViewerTabBar(), SymbolQuickBar(), Dex2cTheme(), ClassSelectionTabsRobolectricTest

### Community 77 - "Community 77"
Cohesion: 0.23
Nodes (8): DalvikInstructionViewer(), highlightCLine(), highlightDalvikOperands(), SyntaxCodeViewer(), BuildHubScreen(), BuildHubTab, formatOutputBytes(), OutputExportScreen()

### Community 79 - "Community 79"
Cohesion: 0.47
Nodes (8): CloudBuildScreen(), CloudStage, CloudStepCard(), formatFileSize(), RecentCloudRunsCard(), SetupAuthCard(), SetupRepoCard(), StageStatusIcon()

### Community 80 - "Community 80"
Cohesion: 0.47
Nodes (5): FilterEditorSubMode, FilterRuleDialogSheet(), HomeFilterFocusCard(), InteractiveFilterRuleBuilderPanel(), StructuredRuleItemCard()

### Community 85 - "Community 85"
Cohesion: 0.15
Nodes (12): 1. Executive Summary, 2. Detailed Memory Vulnerability Analysis, 3. Memory Optimization Verification, code:kotlin (private val iconCache = object : android.util.LruCache<Strin), code:kotlin (private val maxBytes = (Runtime.getRuntime().maxMemory() / 1), code:kotlin (val iconBitmap = iconCache.get(pkgName) ?: runCatching {), code:kotlin (override fun onLowMemory() {), Key Memory Findings (+4 more)

### Community 86 - "Community 86"
Cohesion: 0.15
Nodes (12): 1. Executive Summary & Profiling Baseline, 2. In-Depth Root Cause Analysis, 3. Prioritized Performance Implementation Plan, Baseline Benchmark, code:kotlin (val allMethods: List<DexEncodedMethod> =), code:kotlin (subscribeAlways(ContentChangeEvent::class.java) { _ ->), PERF-001: Root-Level Recomposition Cascade, PERF-002: Repeated Allocation in `DexClassInfo.allMethods` (+4 more)

### Community 88 - "Community 88"
Cohesion: 0.20
Nodes (9): 1. Executive Summary, 2. In-Depth Technical Analysis, 3. Recommended Data-Layer Improvements, code:kotlin (val client = GitHubApiClient(isDohEnabled = { settings.dohFa), DATA-001: OkHttpClient Reuse & Connection Pooling, DATA-002: Token Security & Encryption, DATA-003: In-Memory Storage & ZIP Export, Data Layer, Networking & Background Processing Audit: Dex2C Studio (+1 more)

### Community 89 - "Community 89"
Cohesion: 0.83
Nodes (3): BytecodeOverviewCard(), OverviewStatBox(), TopClassDefinitionRow()

### Community 90 - "Community 90"
Cohesion: 0.22
Nodes (8): 1.1 Repository Inventory & Metrics, 1. Executive Summary & Project Inventory, 2.1 Package Breakdown, 2. Package & Module Architecture Map, 3. Dependency Injection & Service Initialization, 4. Key Architectural Deficiencies & Technical Debt Identified, code:mermaid (graph TD), Complete Codebase Audit: Dex2C Studio (`com.dex2c.studio`)

### Community 91 - "Community 91"
Cohesion: 0.22
Nodes (8): 1. Executive Summary, 2.1 Malformed DEX Input Handling, 2.2 Network & DNS Failure Resilience, 2.3 Token Security, 2. Crash Risks & Exception Boundaries, 3. Reliability Improvement Targets, Key Reliability Metrics, Security, Crash Prevention & Reliability Audit: Dex2C Studio

### Community 92 - "Community 92"
Cohesion: 0.22
Nodes (8): 1. Executive Summary, 2.1 Recomposition Isolation Strategy, 2. State Ingestion & Recomposition Scoping, 3. Concurrency & Asynchronous Flow Safety, 4. State Management Action Plan, code:mermaid (flowchart TD), Key Findings, State Management & Lifecycle Architecture Report: Dex2C Studio

### Community 93 - "Community 93"
Cohesion: 0.22
Nodes (7): 1. Executive Summary, 2. Screen-by-Screen Quality Matrix, 3. Detailed UI/UX Findings & Corrections, Screen-by-Screen UI/UX Quality & Accessibility Audit: Dex2C Studio, UI-001: Stranded Copy and Export Actions in `SmaliClassScreen`, UI-002: Hardcoded Read-Only in `SmaliClassScreen`, UI-003: Deprecated Icon Reference

### Community 94 - "Community 94"
Cohesion: 0.33
Nodes (4): rememberWindowAdaptiveInfo(), WindowAdaptiveInfo, WindowHeightSizeClass, WindowWidthSizeClass

### Community 95 - "Community 95"
Cohesion: 0.57
Nodes (6): BatchActionBar(), ClassTypeBadge(), CompactClassRow(), InlineBadge(), PackageFolderRow(), QuickRuleBadgeButton()

### Community 96 - "Community 96"
Cohesion: 0.40
Nodes (4): Master Issue Registry, Master Issue Registry & Resolution Status, Master Issue Tracker: Dex2C Studio Performance & Quality Audit, Priority Classification

### Community 97 - "Community 97"
Cohesion: 0.48
Nodes (6): sectionBadge(), sectionSummary(), SettingsHubList(), SettingsHubRow(), SettingsScreen(), SettingsSection

### Community 99 - "Community 99"
Cohesion: 0.25
Nodes (8): ActiveFilterPill(), ActiveFilterSummaryStrip(), ClassesFilterSheet(), ClassesFilterTray(), DexFieldsExplorerTab(), DexMethodsExplorerTab(), SelectedClassesFilterDialog(), ClassesScreen()

### Community 101 - "Community 101"
Cohesion: 0.23
Nodes (4): D2cnBinaryDecoder, D2cnHeader, DexEncodedField, DexFieldRef

### Community 102 - "Community 102"
Cohesion: 0.17
Nodes (11): Adaptive Showcase (Pure Foreground Floating Over Continuous Background), Component 1: Foreground Drawables & Asset Generation, Component 2: Material You Dynamic Theming (Monochrome Vectors), Component 3: Adaptive Icon XML Descriptors in `mipmap-anydpi-v26/`, Component 4: Multi-Density Mipmap Suite (40 PNG files), Dex2C True Adaptive Icon Architecture & Implementation Plan, Goal Description, Proposed Changes (+3 more)

### Community 103 - "Community 103"
Cohesion: 0.24
Nodes (9): ApkPreviewSheet(), PreviewReadyContent(), Dex2cApp(), Dex2cFloatingBottomBar(), Dex2cFloatingBottomBarItem(), Dex2cThemedSnackbarHost(), isErrorStatusMessage(), MainActivity (+1 more)

## Knowledge Gaps
- **313 isolated node(s):** `name`, `description`, `requestFramePermissions`, `majorCapabilities`, `lineComment` (+308 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **32 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `Dex2cViewModel` connect `Community 1` to `Community 2`, `Community 5`, `Community 39`, `Community 73`, `Community 45`, `Community 13`, `Community 15`, `Community 23`, `Community 24`, `Community 27`?**
  _High betweenness centrality (0.102) - this node is a cross-community bridge._
- **Why does `CloudSettings` connect `Community 2` to `Community 11`, `Community 21`?**
  _High betweenness centrality (0.059) - this node is a cross-community bridge._
- **Why does `TagPill()` connect `Community 33` to `Community 97`, `Community 98`, `Community 4`, `Community 37`, `Community 70`, `Community 103`, `Community 7`, `Community 74`, `Community 77`, `Community 79`, `Community 80`, `Community 50`, `Community 89`, `Community 30`?**
  _High betweenness centrality (0.059) - this node is a cross-community bridge._
- **Are the 2 inferred relationships involving `Dex2cViewModel` (e.g. with `.`github sign in completion automatically initializes private repo and commits workflow despite transient dns error`()` and `.`build monitor survives transient UnknownHostException during active poll and recovers to completed`()`) actually correct?**
  _`Dex2cViewModel` has 2 INFERRED edges - model-reasoned connections that need verification._
- **Are the 41 inferred relationships involving `TagPill()` (e.g. with `StoragePickerTab()` and `RecentBinariesTab()`) actually correct?**
  _`TagPill()` has 41 INFERRED edges - model-reasoned connections that need verification._
- **Are the 27 inferred relationships involving `TranspilerConfig` (e.g. with `.resetAllSettingsToDefaults()` and `.parseDisassembleAndTranspileSyntheticDex()`) actually correct?**
  _`TranspilerConfig` has 27 INFERRED edges - model-reasoned connections that need verification._
- **Are the 6 inferred relationships involving `GitHubApiClient` (e.g. with `.doWork()` and `.`github sign in completion automatically initializes private repo and commits workflow despite transient dns error`()`) actually correct?**
  _`GitHubApiClient` has 6 INFERRED edges - model-reasoned connections that need verification._