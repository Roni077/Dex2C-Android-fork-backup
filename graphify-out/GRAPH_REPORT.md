# Graph Report - Dex2c-Android  (2026-11-16)

## Corpus Check
- 87 files · ~479,900 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 999 nodes · 1857 edges · 63 communities (44 shown, 19 thin omitted)
- Extraction: 82% EXTRACTED · 18% INFERRED · 0% AMBIGUOUS · INFERRED: 340 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `fc79697b`
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
- [[_COMMUNITY_Community 51|Community 51]]
- [[_COMMUNITY_Community 52|Community 52]]
- [[_COMMUNITY_Community 54|Community 54]]
- [[_COMMUNITY_Community 55|Community 55]]
- [[_COMMUNITY_Community 56|Community 56]]
- [[_COMMUNITY_Community 57|Community 57]]
- [[_COMMUNITY_Community 58|Community 58]]
- [[_COMMUNITY_Community 64|Community 64]]

## God Nodes (most connected - your core abstractions)
1. `Dex2cViewModel` - 102 edges
2. `TagPill()` - 40 edges
3. `Dex2cTranspiler` - 35 edges
4. `GitHubApiClient` - 24 edges
5. `TranspilerConfig` - 22 edges
6. `DexParser` - 19 edges
7. `SmaliClassScreen()` - 18 edges
8. `ApkMetadataInspector` - 17 edges
9. `DalvikInstruction` - 16 edges
10. `SmaliClassDisassembler` - 16 edges

## Surprising Connections (you probably didn't know these)
- `Dex2cApp()` --calls--> `HomeScreen()`  [INFERRED]
  app/src/main/java/com/dex2c/studio/MainActivity.kt → app/src/main/java/com/dex2c/studio/ui/screens/HomeScreen.kt
- `Dex2cApp()` --calls--> `ClassesScreen()`  [INFERRED]
  app/src/main/java/com/dex2c/studio/MainActivity.kt → app/src/main/java/com/dex2c/studio/ui/screens/ClassesScreen.kt
- `Dex2cApp()` --calls--> `BuildHubScreen()`  [INFERRED]
  app/src/main/java/com/dex2c/studio/MainActivity.kt → app/src/main/java/com/dex2c/studio/ui/screens/BuildHubScreen.kt
- `Dex2cApp()` --calls--> `CloudBuildScreen()`  [INFERRED]
  app/src/main/java/com/dex2c/studio/MainActivity.kt → app/src/main/java/com/dex2c/studio/ui/screens/CloudBuildScreen.kt
- `Dex2cApp()` --calls--> `MethodDetailScreen()`  [INFERRED]
  app/src/main/java/com/dex2c/studio/MainActivity.kt → app/src/main/java/com/dex2c/studio/ui/screens/MethodDetailScreen.kt

## Communities (63 total, 19 thin omitted)

### Community 0 - "Community 0"
Cohesion: 0.10
Nodes (10): descriptorToJavaName(), ArrayMeta, Dex2cTranspiler, NativeRegRecord, StreamedFileBuilder, GeneratedBundle, GeneratedFile, JniBindingStyle (+2 more)

### Community 3 - "Community 3"
Cohesion: 0.11
Nodes (5): JsonMini, Parser, SmaliClassDisassembler, SmaliDisassemblyResult, SmaliMethodSpan

### Community 4 - "Community 4"
Cohesion: 0.09
Nodes (33): applySearchHighlights(), extractLineInteractiveTokens(), extractQuickMethodTokens(), findCategoryTokenRanges(), findEnclosingMethodName(), findLineMatchRanges(), findSmaliCommentStart(), findSmaliSearchMatches() (+25 more)

### Community 5 - "Community 5"
Cohesion: 0.11
Nodes (7): DccConfigCodec, FilterMatchSummary, FilterPresetItem, FilterRuleEngine, FilterRuleType, SimpleJsonParser, StructuredFilterRule

### Community 6 - "Community 6"
Cohesion: 0.06
Nodes (19): CloudSettings, GitHubWorkflowStep, AccentColorPickerSection(), AccentSwatchDot(), CustomAccentHexDialog(), ExpressiveMotion, expressivePressScale(), ExpressiveTonalCard() (+11 more)

### Community 7 - "Community 7"
Cohesion: 0.16
Nodes (24): OnboardingBalancedFooterLayout(), OnboardingHorizontalPager(), OnboardingPagerDots(), ChecklistReviewRow(), CloudStepTimelineRow(), CodePeekToggleChip(), OnboardingCloudStepsCard(), OnboardingDalvikCppPeekCard() (+16 more)

### Community 8 - "Community 8"
Cohesion: 0.11
Nodes (9): AppScopeFilter, BinarySourceType, InstalledAppItem, PickerSortOrder, PickerTab, RecentBinaryItem, RecentSourceFilter, InstalledAppRepository (+1 more)

### Community 9 - "Community 9"
Cohesion: 0.09
Nodes (21): 1. High-Level System Architecture & Data Pipeline, 2. Phase 1: Binary Ingestion & Multi-DEX Streaming, 3. Phase 2: DEX Explorer (Classes, Methods, Fields, Strings & Rules), 4. Phase 3: Interactive Smali Editor, Java Decompiler & In-Memory Assembly, 5. Phase 4: Method Inspection, Control Flow Graph (CFG) & C++17 Transpiler, 6. Phase 5: Filter Rules (`filter.txt`) & Pipeline Configuration (`dcc.cfg`), 7. Phase 6: Output Export, Patched DEX/APK & GitHub Cloud CI, 8. Phase 7: Performance & Memory Optimization Architecture (+13 more)

### Community 10 - "Community 10"
Cohesion: 0.09
Nodes (21): 1. High-Level System Architecture & Data Pipeline, 2. Phase 1: Binary Ingestion & Multi-DEX Streaming, 3. Phase 2: DEX Explorer (Classes, Methods, Fields, Strings & Rules), 4. Phase 3: Interactive Smali Editor, Java Decompiler & In-Memory Assembly, 5. Phase 4: Method Inspection, Control Flow Graph (CFG) & C++17 Transpiler, 6. Phase 5: Filter Rules (`filter.txt`) & Pipeline Configuration (`dcc.cfg`), 7. Phase 6: Output Export, Patched DEX/APK & GitHub Cloud CI, 8. Phase 7: Performance & Memory Optimization Architecture (+13 more)

### Community 11 - "Community 11"
Cohesion: 0.10
Nodes (20): Authenticated, CloudBuildRunState, CloudErrorAction, CloudSigningMode, Completed, DeviceAuthState, DeviceCodeResponse, Dispatching (+12 more)

### Community 12 - "Community 12"
Cohesion: 0.23
Nodes (3): ApkMetadataInspector, ArchiveMeta, ApkStagingPreview

### Community 13 - "Community 13"
Cohesion: 0.17
Nodes (18): DexFieldsExplorerTab(), DexMethodsExplorerTab(), DexStringsExplorerTab(), normalizePackageName(), buildNestedPackageTree(), ClassesScreen(), ClassesSubTab, ClassesViewMode (+10 more)

### Community 14 - "Community 14"
Cohesion: 0.21
Nodes (5): DexHelper, LebReader, DexAnnotationEntry, DexDebugLineEntry, DexMethodStructuralMeta

### Community 15 - "Community 15"
Cohesion: 0.06
Nodes (25): DexEditVerifierTest, ApkDexEntry, buildBytecodeOverviewSummary(), BytecodeOverviewSummary, ClassDefinitionSummary, DalvikPayload, descriptorToSimpleName(), DexCallSiteInfo (+17 more)

### Community 16 - "Community 16"
Cohesion: 0.40
Nodes (13): SettingsDetailHost(), AboutRow(), LiveThemePreviewCard(), SettingsAboutSubScreen(), SettingsAppearanceSubScreen(), SettingsCodeEditorSubScreen(), SettingsDexParserSubScreen(), SettingsGitHubCloudSubScreen() (+5 more)

### Community 17 - "Community 17"
Cohesion: 0.27
Nodes (12): BuildHubScreen(), BuildHubTab, ConfigSectionCard(), FilterRulesCard(), JniBindingCard(), LoaderFlagsCard(), PipelineCategory, PipelineCategoryPill() (+4 more)

### Community 18 - "Community 18"
Cohesion: 0.27
Nodes (9): BytecodeOverviewCard(), OverviewStatBox(), TopClassDefinitionRow(), AnnotationBadgeRow(), DexMapListCard(), DexMethodMetaViewer(), HeaderKeyValueRow(), MetricTile() (+1 more)

### Community 19 - "Community 19"
Cohesion: 0.07
Nodes (20): DalvikDisassembler, Fmt, LebReader, OpSpec, RefKind, reg(), DalvikInstruction, DexCatchHandler (+12 more)

### Community 20 - "Community 20"
Cohesion: 0.09
Nodes (8): MemoryDataStore, Phase1DexRemediationTest, CountingOutputStream, CustomDexDataStore, DexCompileResult, Error, SmaliDexCompiler, Success

### Community 21 - "Community 21"
Cohesion: 0.08
Nodes (10): defaultClient(), isIpv4Literal(), isNumericIpLiteral(), NetworkResilience, parseDohARecords(), prioritizeIpv4(), ResilientGitHubDns, KeystoreTokenEncryptor (+2 more)

### Community 22 - "Community 22"
Cohesion: 0.32
Nodes (11): CompactHeaderCell(), DexBinaryHeaderCard(), DexContainersStrip(), EmptyWorkspaceCard(), formatBytes(), HeroHomeBanner(), HomeMetricsGrid(), HomeScreen() (+3 more)

### Community 23 - "Community 23"
Cohesion: 0.29
Nodes (3): Error, Loading, Ready

### Community 24 - "Community 24"
Cohesion: 0.13
Nodes (10): ApkStagingState, ClassFilterState, ClassSortOrder, ClassTypeFilter, CloudApkChoice, DexLoadState, Empty, MethodInspectionState (+2 more)

### Community 25 - "Community 25"
Cohesion: 0.11
Nodes (5): AwaitingUser, CloudTimeUtil, DexEditSession, DexEditUndoEntry, DexEditSessionTest

### Community 28 - "Community 28"
Cohesion: 0.18
Nodes (4): CloudBuildMonitorScheduler, CloudBuildMonitorWorker, CloudPreferences, Keys

### Community 29 - "Community 29"
Cohesion: 0.25
Nodes (8): DalvikInstructionViewer(), highlightCLine(), highlightDalvikOperands(), SyntaxCodeViewer(), formatAccessFlags(), MethodDetailScreen(), formatOutputBytes(), OutputExportScreen()

### Community 30 - "Community 30"
Cohesion: 0.36
Nodes (8): ApkPickerSheet(), formatApkSize(), InstalledAppRow(), InstalledAppsTab(), RecentBinariesTab(), StoragePickerTab(), ApkPreviewSheet(), PreviewReadyContent()

### Community 31 - "Community 31"
Cohesion: 0.17
Nodes (11): code:c (#define DEX2C_CHECK_NULL(env, ref) do { \), code:xml (<exclude domain="sharedpref" path="dex2c_cloud_prefs.prefere), Dex2C Studio — Master Engineering Remediation Roadmap, Master Defect Ledger, Overview & Codebase Audit Summary, Phase 0: Emergency Safety Interlocks & Blast-Radius Mitigation, Phase 1: In-Memory Smali Assembly, MemoryDataStore & DEX Repacking, Phase 2: Transpiler JNI Runtime Safety & Dalvik Instruction Parity (+3 more)

### Community 32 - "Community 32"
Cohesion: 0.27
Nodes (6): BasicBlockInfo, BlockTerminatorKind, CfgEdge, CfgEdgeKind, ControlFlowGraphBuilder, MethodControlFlowGraph

### Community 33 - "Community 33"
Cohesion: 0.36
Nodes (6): Dex2cApp(), Dex2cFloatingBottomBar(), Dex2cThemedSnackbarHost(), isErrorStatusMessage(), MainActivity, NavDestinationItem

### Community 34 - "Community 34"
Cohesion: 0.24
Nodes (8): Denied, Expired, Failure, GitHubRepoInfo, Pending, SlowDown, Success, TokenPollResult

### Community 37 - "Community 37"
Cohesion: 0.48
Nodes (6): sectionBadge(), sectionSummary(), SettingsHubList(), SettingsHubRow(), SettingsScreen(), SettingsSection

### Community 40 - "Community 40"
Cohesion: 0.47
Nodes (5): FilterEditorSubMode, FilterRuleDialogSheet(), HomeFilterFocusCard(), InteractiveFilterRuleBuilderPanel(), StructuredRuleItemCard()

### Community 41 - "Community 41"
Cohesion: 0.36
Nodes (5): WorkflowDocumentationRepository, WorkflowGuideDialog(), WorkflowSection, WorkflowSectionCard(), WorkflowViewMode

### Community 43 - "Community 43"
Cohesion: 0.29
Nodes (5): CloudDeepLinkTarget, CloudLiveNotificationContent, CloudLivePhase, CloudNotificationContent, NotificationThemeColors

### Community 47 - "Community 47"
Cohesion: 0.70
Nodes (4): BasicBlockCard(), CfgEdgePill(), DalvikCfgViewer(), PayloadDetailCard()

### Community 49 - "Community 49"
Cohesion: 0.40
Nodes (4): description, majorCapabilities, name, requestFramePermissions

### Community 64 - "Community 64"
Cohesion: 0.19
Nodes (16): BuildMonitorCard(), formatArtifactBytes(), MonitorGaugeBlock(), StepProgressList(), TransferProgressPanel(), DeviceAuthStateBody(), GitHubDeviceAuthCard(), InlineRepoSetupStatusRow() (+8 more)

## Knowledge Gaps
- **115 isolated node(s):** `name`, `description`, `requestFramePermissions`, `majorCapabilities`, `BinarySourceType` (+110 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **19 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `Dex2cViewModel` connect `Community 1` to `Community 69`, `Community 5`, `Community 6`, `Community 39`, `Community 45`, `Community 15`, `Community 23`, `Community 24`, `Community 27`?**
  _High betweenness centrality (0.232) - this node is a cross-community bridge._
- **Why does `CloudSettings` connect `Community 6` to `Community 11`, `Community 21`?**
  _High betweenness centrality (0.104) - this node is a cross-community bridge._
- **Why does `TagPill()` connect `Community 18` to `Community 64`, `Community 4`, `Community 37`, `Community 7`, `Community 40`, `Community 47`, `Community 16`, `Community 22`, `Community 29`, `Community 30`?**
  _High betweenness centrality (0.088) - this node is a cross-community bridge._
- **Are the 2 inferred relationships involving `Dex2cViewModel` (e.g. with `.`github sign in completion automatically initializes private repo and commits workflow despite transient dns error`()` and `.`build monitor survives transient UnknownHostException during active poll and recovers to completed`()`) actually correct?**
  _`Dex2cViewModel` has 2 INFERRED edges - model-reasoned connections that need verification._
- **Are the 39 inferred relationships involving `TagPill()` (e.g. with `StoragePickerTab()` and `InstalledAppRow()`) actually correct?**
  _`TagPill()` has 39 INFERRED edges - model-reasoned connections that need verification._
- **Are the 5 inferred relationships involving `GitHubApiClient` (e.g. with `.doWork()` and `.`github sign in completion automatically initializes private repo and commits workflow despite transient dns error`()`) actually correct?**
  _`GitHubApiClient` has 5 INFERRED edges - model-reasoned connections that need verification._
- **Are the 21 inferred relationships involving `TranspilerConfig` (e.g. with `.resetAllSettingsToDefaults()` and `.parseDisassembleAndTranspileSyntheticDex()`) actually correct?**
  _`TranspilerConfig` has 21 INFERRED edges - model-reasoned connections that need verification._