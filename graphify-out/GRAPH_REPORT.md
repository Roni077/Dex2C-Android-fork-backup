# Graph Report - Dex2c-Android  (2026-11-18)

## Corpus Check
- 106 files · ~523,960 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 1230 nodes · 2196 edges · 83 communities (58 shown, 25 thin omitted)
- Extraction: 81% EXTRACTED · 19% INFERRED · 0% AMBIGUOUS · INFERRED: 418 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `97ee6794`
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
- [[_COMMUNITY_Community 74|Community 74]]
- [[_COMMUNITY_Community 75|Community 75]]
- [[_COMMUNITY_Community 76|Community 76]]
- [[_COMMUNITY_Community 77|Community 77]]
- [[_COMMUNITY_Community 78|Community 78]]
- [[_COMMUNITY_Community 79|Community 79]]
- [[_COMMUNITY_Community 80|Community 80]]
- [[_COMMUNITY_Community 82|Community 82]]
- [[_COMMUNITY_Community 83|Community 83]]
- [[_COMMUNITY_Community 84|Community 84]]

## God Nodes (most connected - your core abstractions)
1. `Dex2cViewModel` - 115 edges
2. `TagPill()` - 41 edges
3. `Dex2cTranspiler` - 35 edges
4. `SmaliClassScreen()` - 26 edges
5. `TranspilerConfig` - 25 edges
6. `GitHubApiClient` - 24 edges
7. `ClassesScreen()` - 24 edges
8. `DexParser` - 19 edges
9. `Dex2cTheme()` - 18 edges
10. `Dex2cApp()` - 17 edges

## Surprising Connections (you probably didn't know these)
- `ClassesScreen()` --calls--> `ClassesFilterTray()`  [INFERRED]
  app/src/main/java/com/dex2c/studio/ui/screens/ClassesScreen.kt → app/src/main/java/com/dex2c/studio/ui/components/ClassesFilterTray.kt
- `ClassesScreen()` --calls--> `ClassesFilterSheet()`  [INFERRED]
  app/src/main/java/com/dex2c/studio/ui/screens/ClassesScreen.kt → app/src/main/java/com/dex2c/studio/ui/components/ClassesFilterTray.kt
- `Dex2cApp()` --calls--> `rememberWindowAdaptiveInfo()`  [INFERRED]
  app/src/main/java/com/dex2c/studio/MainActivity.kt → app/src/main/java/com/dex2c/studio/ui/AdaptiveLayout.kt
- `Dex2cApp()` --calls--> `HomeScreen()`  [INFERRED]
  app/src/main/java/com/dex2c/studio/MainActivity.kt → app/src/main/java/com/dex2c/studio/ui/screens/HomeScreen.kt
- `Dex2cApp()` --calls--> `ClassesScreen()`  [INFERRED]
  app/src/main/java/com/dex2c/studio/MainActivity.kt → app/src/main/java/com/dex2c/studio/ui/screens/ClassesScreen.kt

## Communities (83 total, 25 thin omitted)

### Community 0 - "Community 0"
Cohesion: 0.10
Nodes (10): descriptorToJavaName(), ArrayMeta, Dex2cTranspiler, NativeRegRecord, StreamedFileBuilder, GeneratedBundle, GeneratedFile, JniBindingStyle (+2 more)

### Community 2 - "Community 2"
Cohesion: 0.17
Nodes (3): GitHubJobTelemetry, GitHubWorkflowStep, ExampleRobolectricTest

### Community 3 - "Community 3"
Cohesion: 0.10
Nodes (20): 0. Anti-Pattern: Performative Reasoning, 10. Final Answer Protocol, 1. Problem Extraction, 2. Assumption Firewall, 3. Solution Generation, 4. Verification Layer, 5. Adversarial Attack, 6. Confidence Calibration (+12 more)

### Community 4 - "Community 4"
Cohesion: 0.06
Nodes (39): ClassMethodsSelectorSheet(), MethodSelectorItem, applySearchHighlights(), extractLineInteractiveTokens(), extractQuickMethodTokens(), findCategoryTokenRanges(), findEnclosingMethodName(), findLineMatchRanges() (+31 more)

### Community 5 - "Community 5"
Cohesion: 0.10
Nodes (7): DccConfigCodec, FilterMatchSummary, FilterPresetItem, FilterRuleEngine, FilterRuleType, SimpleJsonParser, StructuredFilterRule

### Community 6 - "Community 6"
Cohesion: 0.10
Nodes (14): AccentColorPickerSection(), AccentSwatchDot(), CustomAccentHexDialog(), ExpressiveMotion, expressivePressScale(), ExpressiveTonalCard(), AccentPreset, AppPreferences (+6 more)

### Community 7 - "Community 7"
Cohesion: 0.16
Nodes (24): OnboardingBalancedFooterLayout(), OnboardingHorizontalPager(), OnboardingPagerDots(), ChecklistReviewRow(), CloudStepTimelineRow(), CodePeekToggleChip(), OnboardingCloudStepsCard(), OnboardingDalvikCppPeekCard() (+16 more)

### Community 8 - "Community 8"
Cohesion: 0.12
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

### Community 13 - "Community 13"
Cohesion: 0.15
Nodes (19): DexFieldsExplorerTab(), DexMethodsExplorerTab(), SelectedClassesFilterDialog(), normalizePackageName(), buildNestedPackageTree(), ClassesDetailPane(), ClassesScreen(), ClassesSubTab (+11 more)

### Community 14 - "Community 14"
Cohesion: 0.25
Nodes (3): DexHelper, LebReader, DexMethodStructuralMeta

### Community 15 - "Community 15"
Cohesion: 0.13
Nodes (14): buildBytecodeOverviewSummary(), BytecodeOverviewSummary, ClassDefinitionSummary, DalvikPayload, DexAnnotationEntry, DexCallSiteInfo, DexDebugLineEntry, DexFieldRef (+6 more)

### Community 17 - "Community 17"
Cohesion: 0.40
Nodes (10): ConfigSectionCard(), FilterRulesCard(), JniBindingCard(), LoaderFlagsCard(), PipelineCategory, PipelineCategoryPill(), PipelineConfigScreen(), ProtectionPassesCard() (+2 more)

### Community 18 - "Community 18"
Cohesion: 0.10
Nodes (7): JsonMini, Parser, ClassViewerTabBar(), DexEncodedField, SmaliClassDisassembler, SmaliDisassemblyResult, SmaliMethodSpan

### Community 19 - "Community 19"
Cohesion: 0.07
Nodes (20): DalvikDisassembler, Fmt, LebReader, OpSpec, RefKind, reg(), DalvikInstruction, DexCatchHandler (+12 more)

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
Cohesion: 0.17
Nodes (5): RecentBinaryItem, CloudApkChoice, Error, Loading, Ready

### Community 24 - "Community 24"
Cohesion: 0.20
Nodes (8): ApkStagingState, ClassSortOrder, ClassTypeFilter, DexLoadState, Empty, MethodInspectionState, MethodScopeFilter, SmaliClassUiState

### Community 25 - "Community 25"
Cohesion: 0.11
Nodes (5): AwaitingUser, CloudTimeUtil, DexEditSession, DexEditUndoEntry, DexEditSessionTest

### Community 28 - "Community 28"
Cohesion: 0.18
Nodes (4): CloudBuildMonitorScheduler, CloudBuildMonitorWorker, CloudPreferences, Keys

### Community 29 - "Community 29"
Cohesion: 0.30
Nodes (3): ApkDexEntry, DexMapItem, DexParser

### Community 30 - "Community 30"
Cohesion: 0.40
Nodes (13): SettingsDetailHost(), AboutRow(), LiveThemePreviewCard(), SettingsAboutSubScreen(), SettingsAppearanceSubScreen(), SettingsCodeEditorSubScreen(), SettingsDexParserSubScreen(), SettingsGitHubCloudSubScreen() (+5 more)

### Community 31 - "Community 31"
Cohesion: 0.17
Nodes (11): code:c (#define DEX2C_CHECK_NULL(env, ref) do { \), code:xml (<exclude domain="sharedpref" path="dex2c_cloud_prefs.prefere), Dex2C Studio — Master Engineering Remediation Roadmap, Master Defect Ledger, Overview & Codebase Audit Summary, Phase 0: Emergency Safety Interlocks & Blast-Radius Mitigation, Phase 1: In-Memory Smali Assembly, MemoryDataStore & DEX Repacking, Phase 2: Transpiler JNI Runtime Safety & Dalvik Instruction Parity (+3 more)

### Community 32 - "Community 32"
Cohesion: 0.27
Nodes (6): BasicBlockInfo, BlockTerminatorKind, CfgEdge, CfgEdgeKind, ControlFlowGraphBuilder, MethodControlFlowGraph

### Community 33 - "Community 33"
Cohesion: 0.19
Nodes (14): BytecodeOverviewCard(), OverviewStatBox(), TopClassDefinitionRow(), BasicBlockCard(), CfgEdgePill(), DalvikCfgViewer(), PayloadDetailCard(), AnnotationBadgeRow() (+6 more)

### Community 34 - "Community 34"
Cohesion: 0.16
Nodes (9): Denied, Expired, Failure, GitHubRepoInfo, Pending, ProgressByteRequestBody, SlowDown, Success (+1 more)

### Community 37 - "Community 37"
Cohesion: 0.32
Nodes (11): CompactHeaderCell(), DexBinaryHeaderCard(), DexContainersStrip(), EmptyWorkspaceCard(), formatBytes(), HeroHomeBanner(), HomeMetricsGrid(), HomeScreen() (+3 more)

### Community 41 - "Community 41"
Cohesion: 0.15
Nodes (5): DexHeaderInfo, DexParsedFile, parseClassDescriptor(), ParsedClassDescriptor, UlebReader

### Community 43 - "Community 43"
Cohesion: 0.29
Nodes (5): CloudDeepLinkTarget, CloudLiveNotificationContent, CloudLivePhase, CloudNotificationContent, NotificationThemeColors

### Community 47 - "Community 47"
Cohesion: 0.36
Nodes (5): WorkflowDocumentationRepository, WorkflowGuideDialog(), WorkflowSection, WorkflowSectionCard(), WorkflowViewMode

### Community 48 - "Community 48"
Cohesion: 0.21
Nodes (5): ClassPickerSheet(), ClassSelectionHeader(), SymbolQuickBar(), Dex2cTheme(), ClassSelectionTabsRobolectricTest

### Community 49 - "Community 49"
Cohesion: 0.40
Nodes (4): description, majorCapabilities, name, requestFramePermissions

### Community 50 - "Community 50"
Cohesion: 0.83
Nodes (3): DeviceAuthStateBody(), GitHubDeviceAuthCard(), InlineRepoSetupStatusRow()

### Community 64 - "Community 64"
Cohesion: 0.36
Nodes (3): DexClassInfo, AdaptiveLayoutRobolectricTest, ClassFilterState

### Community 66 - "Community 66"
Cohesion: 0.24
Nodes (8): DalvikInstructionViewer(), highlightCLine(), highlightDalvikOperands(), SyntaxCodeViewer(), BuildHubScreen(), BuildHubTab, formatOutputBytes(), OutputExportScreen()

### Community 68 - "Community 68"
Cohesion: 0.18
Nodes (10): colors, editor.background, editor.foreground, editor.lineHighlightBackground, editor.selectionBackground, editorLineNumber.activeForeground, editorLineNumber.foreground, name (+2 more)

### Community 69 - "Community 69"
Cohesion: 0.18
Nodes (10): colors, editor.background, editor.foreground, editor.lineHighlightBackground, editor.selectionBackground, editorLineNumber.activeForeground, editorLineNumber.foreground, name (+2 more)

### Community 70 - "Community 70"
Cohesion: 0.36
Nodes (8): ApkPickerSheet(), formatApkSize(), InstalledAppRow(), InstalledAppsTab(), RecentBinariesTab(), StoragePickerTab(), ApkPreviewSheet(), PreviewReadyContent()

### Community 71 - "Community 71"
Cohesion: 0.20
Nodes (9): autoClosingPairs, brackets, comments, lineComment, folding, markers, end, start (+1 more)

### Community 74 - "Community 74"
Cohesion: 0.33
Nodes (4): rememberWindowAdaptiveInfo(), WindowAdaptiveInfo, WindowHeightSizeClass, WindowWidthSizeClass

### Community 76 - "Community 76"
Cohesion: 0.57
Nodes (6): BatchActionBar(), ClassTypeBadge(), CompactClassRow(), InlineBadge(), PackageFolderRow(), QuickRuleBadgeButton()

### Community 77 - "Community 77"
Cohesion: 0.29
Nodes (8): Dex2cApp(), Dex2cFloatingBottomBar(), Dex2cFloatingBottomBarItem(), Dex2cThemedSnackbarHost(), isErrorStatusMessage(), MainActivity, NavDestinationItem, EditorDisplaySettings

### Community 79 - "Community 79"
Cohesion: 0.47
Nodes (8): CloudBuildScreen(), CloudStage, CloudStepCard(), formatFileSize(), RecentCloudRunsCard(), SetupAuthCard(), SetupRepoCard(), StageStatusIcon()

### Community 80 - "Community 80"
Cohesion: 0.47
Nodes (5): FilterEditorSubMode, FilterRuleDialogSheet(), HomeFilterFocusCard(), InteractiveFilterRuleBuilderPanel(), StructuredRuleItemCard()

### Community 82 - "Community 82"
Cohesion: 0.48
Nodes (6): sectionBadge(), sectionSummary(), SettingsHubList(), SettingsHubRow(), SettingsScreen(), SettingsSection

### Community 83 - "Community 83"
Cohesion: 0.60
Nodes (5): BuildMonitorCard(), formatArtifactBytes(), MonitorGaugeBlock(), StepProgressList(), TransferProgressPanel()

### Community 84 - "Community 84"
Cohesion: 0.50
Nodes (4): ActiveFilterPill(), ActiveFilterSummaryStrip(), ClassesFilterSheet(), ClassesFilterTray()

## Knowledge Gaps
- **201 isolated node(s):** `name`, `description`, `requestFramePermissions`, `majorCapabilities`, `lineComment` (+196 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **25 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `Dex2cViewModel` connect `Community 1` to `Community 65`, `Community 2`, `Community 5`, `Community 39`, `Community 73`, `Community 45`, `Community 46`, `Community 15`, `Community 23`, `Community 24`, `Community 27`?**
  _High betweenness centrality (0.164) - this node is a cross-community bridge._
- **Why does `TranspilerConfig` connect `Community 19` to `Community 0`, `Community 64`, `Community 46`?**
  _High betweenness centrality (0.091) - this node is a cross-community bridge._
- **Why does `SmaliClassScreen()` connect `Community 4` to `Community 33`, `Community 66`, `Community 77`, `Community 48`, `Community 18`, `Community 53`?**
  _High betweenness centrality (0.085) - this node is a cross-community bridge._
- **Are the 2 inferred relationships involving `Dex2cViewModel` (e.g. with `.`github sign in completion automatically initializes private repo and commits workflow despite transient dns error`()` and `.`build monitor survives transient UnknownHostException during active poll and recovers to completed`()`) actually correct?**
  _`Dex2cViewModel` has 2 INFERRED edges - model-reasoned connections that need verification._
- **Are the 40 inferred relationships involving `TagPill()` (e.g. with `StoragePickerTab()` and `InstalledAppRow()`) actually correct?**
  _`TagPill()` has 40 INFERRED edges - model-reasoned connections that need verification._
- **Are the 20 inferred relationships involving `SmaliClassScreen()` (e.g. with `Dex2cApp()` and `formatAccessFlags()`) actually correct?**
  _`SmaliClassScreen()` has 20 INFERRED edges - model-reasoned connections that need verification._
- **Are the 24 inferred relationships involving `TranspilerConfig` (e.g. with `.resetAllSettingsToDefaults()` and `.parseDisassembleAndTranspileSyntheticDex()`) actually correct?**
  _`TranspilerConfig` has 24 INFERRED edges - model-reasoned connections that need verification._