package com.dex2c.studio.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dex2c.studio.BuildConfig
import com.dex2c.studio.apk.ApkMetadataInspector
import com.dex2c.studio.apk.ApkStagingPreview
import com.dex2c.studio.apk.BinarySourceType
import com.dex2c.studio.apk.InstalledAppItem
import com.dex2c.studio.apk.InstalledAppRepository
import com.dex2c.studio.apk.PickerTab
import com.dex2c.studio.apk.RecentBinaryItem
import com.dex2c.studio.cloud.CloudBuildMonitorScheduler
import com.dex2c.studio.cloud.CloudBuildNotifier
import com.dex2c.studio.cloud.CloudBuildRunState
import com.dex2c.studio.cloud.CloudDownloadedApk
import com.dex2c.studio.cloud.CloudErrorAction
import com.dex2c.studio.cloud.CloudLiveNotificationContent
import com.dex2c.studio.cloud.CloudSettings
import com.dex2c.studio.cloud.CloudTimeUtil
import com.dex2c.studio.cloud.Dex2cWorkflowGenerator
import com.dex2c.studio.cloud.DeviceAuthState
import com.dex2c.studio.cloud.GitHubApiClient
import com.dex2c.studio.cloud.GitHubArtifactItem
import com.dex2c.studio.cloud.GitHubWorkflowRunSummary
import com.dex2c.studio.cloud.NetworkResilience
import com.dex2c.studio.cloud.RepoSetupState
import com.dex2c.studio.cloud.TokenPollResult
import com.dex2c.studio.data.AppPreferences
import com.dex2c.studio.data.AppSettings
import com.dex2c.studio.data.CloudPreferences
import com.dex2c.studio.data.RecentFilesStore
import com.dex2c.studio.data.TranspilerPreferences
import com.dex2c.studio.dex.ApkDexEntry
import com.dex2c.studio.dex.BytecodeOverviewSummary
import com.dex2c.studio.dex.ControlFlowGraphBuilder
import com.dex2c.studio.dex.DalvikDisassembler
import com.dex2c.studio.dex.DalvikInstruction
import com.dex2c.studio.dex.DexClassInfo
import com.dex2c.studio.dex.DexCompileResult
import com.dex2c.studio.dex.DexEditSession
import com.dex2c.studio.dex.DexEditUndoEntry
import com.dex2c.studio.dex.DexEditUndoPrompt
import com.dex2c.studio.dex.DexEditVerifier
import com.dex2c.studio.dex.DexEncodedMethod
import com.dex2c.studio.dex.DexHelper
import com.dex2c.studio.dex.DexMethodStructuralMeta
import com.dex2c.studio.dex.DexParsedFile
import com.dex2c.studio.dex.DexParser
import com.dex2c.studio.dex.buildBytecodeOverviewSummary
import com.dex2c.studio.dex.inferDominantDexPackageName
import com.dex2c.studio.dex.DexTryBlock
import com.dex2c.studio.dex.MethodControlFlowGraph
import com.dex2c.studio.dex.SmaliClassDisassembler
import com.dex2c.studio.dex.SmaliDexCompiler
import com.dex2c.studio.dex.SmaliJavaDecompiler
import com.dex2c.studio.dex.SmaliMethodSpan
import com.dex2c.studio.transpiler.DccConfigCodec
import com.dex2c.studio.transpiler.Dex2cTranspiler
import com.dex2c.studio.transpiler.FilterMatchSummary
import com.dex2c.studio.transpiler.FilterRuleEngine
import com.dex2c.studio.transpiler.FilterRuleType
import com.dex2c.studio.transpiler.GeneratedBundle
import com.dex2c.studio.transpiler.StructuredFilterRule
import com.dex2c.studio.transpiler.TranspileProgress
import com.dex2c.studio.transpiler.TranspilerConfig
import com.dex2c.studio.transpiler.ZipBundleExporter
import java.io.File
import java.io.FileInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface DexLoadState {
  data object Empty : DexLoadState
  data class Loading(val label: String) : DexLoadState
  data class Ready(
    val sourceLabel: String,
    val dexEntries: List<ApkDexEntry>,
    val activeEntryName: String,
    val parsedDex: DexParsedFile,
    val loadedDexes: Map<String, DexParsedFile> = mapOf(parsedDex.entryName to parsedDex),
    val apkPackageName: String? = null,
  ) : DexLoadState {
    val mergedClasses: List<DexClassInfo> =
      if (loadedDexes.size <= 1) parsedDex.classes
      else loadedDexes.values.flatMap { it.classes }

    val loadedEntryNames: List<String> = loadedDexes.keys.toList()

    val resolvedPackageName: String? =
      apkPackageName?.takeIf { it.isNotBlank() } ?: inferDominantDexPackageName(mergedClasses)
  }
  data class Error(val message: String) : DexLoadState
}

sealed interface ApkStagingState {
  data class Loading(val label: String) : ApkStagingState
  data class Ready(
    val preview: ApkStagingPreview,
    val selectedEntryNames: Set<String> = preview.dexEntries.firstOrNull()?.let { setOf(it.entryName) } ?: emptySet(),
  ) : ApkStagingState {
    val selectedEntryName: String
      get() = selectedEntryNames.firstOrNull().orEmpty()
  }
  data class Error(val message: String) : ApkStagingState
}

data class MethodInspectionState(
  val method: DexEncodedMethod,
  val dexClass: DexClassInfo,
  val instructions: List<DalvikInstruction>,
  val tryBlocks: List<DexTryBlock>,
  val transpiledC: String,
  val structuralMeta: DexMethodStructuralMeta,
  val cfg: MethodControlFlowGraph = ControlFlowGraphBuilder.build(instructions, tryBlocks),
  val methodSmali: String = "",
  val decompiledJava: String = "",
)

sealed interface SmaliClassUiState {
  val dexClass: DexClassInfo

  data class Loading(override val dexClass: DexClassInfo) : SmaliClassUiState

  data class Ready(
    override val dexClass: DexClassInfo,
    val lines: List<String>,
    val lineCount: Int,
    val methodCount: Int,
    val fieldCount: Int,
    val durationMs: Long,
    val initialFocusMethodName: String? = null,
    val methodSpans: List<SmaliMethodSpan> = SmaliClassDisassembler.extractMethodSpans(lines),
    val decompiledJava: String = "",
  ) : SmaliClassUiState {
    val smaliText: String
      get() = lines.joinToString("\n")

    constructor(
      dexClass: DexClassInfo,
      smaliText: String,
      lines: List<String> = if (smaliText.isEmpty()) emptyList() else smaliText.lines(),
      lineCount: Int = lines.size,
      methodCount: Int,
      fieldCount: Int,
      durationMs: Long,
      initialFocusMethodName: String? = null,
      methodSpans: List<SmaliMethodSpan> = SmaliClassDisassembler.extractMethodSpans(lines),
      decompiledJava: String = "",
    ) : this(
      dexClass = dexClass,
      lines = lines,
      lineCount = lineCount,
      methodCount = methodCount,
      fieldCount = fieldCount,
      durationMs = durationMs,
      initialFocusMethodName = initialFocusMethodName,
      methodSpans = methodSpans,
      decompiledJava = decompiledJava,
    )
  }

  data class Error(
    override val dexClass: DexClassInfo,
    val message: String,
  ) : SmaliClassUiState
}

enum class ClassSortOrder(val label: String) {
  NAME_ASC("Name A–Z"),
  NAME_DESC("Name Z–A"),
  BYTECODE_DESC("Most Bytecode"),
  SELECTED_FIRST("Selected First"),
  DEX_ORDER("DEX Order"),
}

enum class ClassTypeFilter(val label: String) {
  ALL("All Classes"),
  CONCRETE("Concrete"),
  INTERFACE_OR_ENUM("Interface/Enum"),
}

enum class MethodScopeFilter(val label: String) {
  ALL("All Methods"),
  PUBLIC_ONLY("Public"),
  STATIC_ONLY("Static"),
  NON_INIT("No <init>"),
}

fun matchesMethodScope(method: DexEncodedMethod, scope: MethodScopeFilter): Boolean =
  when (scope) {
    MethodScopeFilter.ALL -> true
    MethodScopeFilter.PUBLIC_ONLY -> (method.accessFlags and 0x0001) != 0
    MethodScopeFilter.STATIC_ONLY -> method.isStatic
    MethodScopeFilter.NON_INIT -> !method.isInitConstructor
  }

data class ClassFilterState(
  val searchQuery: String = "",
  val hideFramework: Boolean = true,
  val onlyWithBytecode: Boolean = true,
  val onlySelected: Boolean = false,
  val onlyMatchingFilterRules: Boolean = false,
  val dexSourceFilter: String? = null,
  val sortOrder: ClassSortOrder = ClassSortOrder.NAME_ASC,
  val classTypeFilter: ClassTypeFilter = ClassTypeFilter.ALL,
  val hideInnerClasses: Boolean = false,
  val methodScopeFilter: MethodScopeFilter = MethodScopeFilter.ALL,
  val nonEmptyPackagesOnly: Boolean = false,
) {
  val extraFilterCount: Int
    get() =
      (if (classTypeFilter != ClassTypeFilter.ALL) 1 else 0) +
        (if (hideInnerClasses) 1 else 0) +
        (if (methodScopeFilter != MethodScopeFilter.ALL) 1 else 0) +
        (if (nonEmptyPackagesOnly) 1 else 0) +
        (if (onlyMatchingFilterRules) 1 else 0)
}

data class CloudApkChoice(
  val label: String,
  val byteSize: Long,
  val uri: Uri? = null,
  val filePath: String? = null,
)

class Dex2cViewModel @JvmOverloads constructor(
  application: Application,
  private val gitHubClient: GitHubApiClient = GitHubApiClient(),
) : AndroidViewModel(application) {

  private val prefs = TranspilerPreferences(application)
  private val appPrefs = AppPreferences(application)
  private val cloudPrefs = CloudPreferences(application)
  private val recentStore = RecentFilesStore(application)

  private val _loadState = MutableStateFlow<DexLoadState>(DexLoadState.Empty)
  val loadState: StateFlow<DexLoadState> = _loadState.asStateFlow()

  private val _selectedMethodIds = MutableStateFlow<Set<String>>(emptySet())
  val selectedMethodIds: StateFlow<Set<String>> = _selectedMethodIds.asStateFlow()

  private val _filterState = MutableStateFlow(ClassFilterState())
  val filterState: StateFlow<ClassFilterState> = _filterState.asStateFlow()

  private val _config = MutableStateFlow(TranspilerConfig())
  val config: StateFlow<TranspilerConfig> = _config.asStateFlow()

  private val _inspection = MutableStateFlow<MethodInspectionState?>(null)
  val inspection: StateFlow<MethodInspectionState?> = _inspection.asStateFlow()

  private val _generatedBundle = MutableStateFlow<GeneratedBundle?>(null)
  val generatedBundle: StateFlow<GeneratedBundle?> = _generatedBundle.asStateFlow()

  private val _isTranspiling = MutableStateFlow(false)
  val isTranspiling: StateFlow<Boolean> = _isTranspiling.asStateFlow()

  private val _transpileProgress = MutableStateFlow<TranspileProgress?>(null)
  val transpileProgress: StateFlow<TranspileProgress?> = _transpileProgress.asStateFlow()

  private val _isBundleDirty = MutableStateFlow(false)
  val isBundleDirty: StateFlow<Boolean> = _isBundleDirty.asStateFlow()

  private val _statusBanner = MutableStateFlow<String?>(null)
  val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

  // Multi-source APK/DEX picker & staging preview states
  private val _isPickerOpen = MutableStateFlow(false)
  val isPickerOpen: StateFlow<Boolean> = _isPickerOpen.asStateFlow()

  private val _activePickerTab = MutableStateFlow(PickerTab.STORAGE)
  val activePickerTab: StateFlow<PickerTab> = _activePickerTab.asStateFlow()

  private val _installedApps = MutableStateFlow<List<InstalledAppItem>>(emptyList())
  private val _isLoadingApps = MutableStateFlow(false)
  val isLoadingApps: StateFlow<Boolean> = _isLoadingApps.asStateFlow()

  private val _appSearchQuery = MutableStateFlow("")
  val appSearchQuery: StateFlow<String> = _appSearchQuery.asStateFlow()

  val filteredInstalledApps: StateFlow<List<InstalledAppItem>> =
    combine(_installedApps, _appSearchQuery) { apps, query ->
      val q = query.trim()
      if (q.isEmpty()) apps
      else apps.filter { it.appLabel.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true) }
    }
      .flowOn(Dispatchers.Default)
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  val recentFiles: StateFlow<List<RecentBinaryItem>> =
    recentStore.recentItemsFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  val lastBytecodeSummary: StateFlow<BytecodeOverviewSummary?> =
    recentStore.lastBytecodeSummaryFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

  private val _stagingState = MutableStateFlow<ApkStagingState?>(null)
  val stagingState: StateFlow<ApkStagingState?> = _stagingState.asStateFlow()

  private val _smaliClassState = MutableStateFlow<SmaliClassUiState?>(null)
  val smaliClassState: StateFlow<SmaliClassUiState?> = _smaliClassState.asStateFlow()

  // App personalization, editor & DEX parser preferences
  private val _appSettings = MutableStateFlow(AppSettings())
  val appSettings: StateFlow<AppSettings> = _appSettings.asStateFlow()

  private val _appSettingsHydrated = MutableStateFlow(false)
  val appSettingsHydrated: StateFlow<Boolean> = _appSettingsHydrated.asStateFlow()

  // GitHub Device Flow & Cloud Build orchestration states
  private val _cloudSettings = MutableStateFlow(CloudSettings())
  val cloudSettings: StateFlow<CloudSettings> = _cloudSettings.asStateFlow()

  private val _deviceAuthState = MutableStateFlow<DeviceAuthState>(DeviceAuthState.Idle)
  val deviceAuthState: StateFlow<DeviceAuthState> = _deviceAuthState.asStateFlow()

  private val _repoSetupState = MutableStateFlow<RepoSetupState>(RepoSetupState.NotConfigured)
  val repoSetupState: StateFlow<RepoSetupState> = _repoSetupState.asStateFlow()

  private val _cloudRunState = MutableStateFlow<CloudBuildRunState>(CloudBuildRunState.Idle)
  val cloudRunState: StateFlow<CloudBuildRunState> = _cloudRunState.asStateFlow()

  private val _isAutoPollEnabled = MutableStateFlow(true)
  val isAutoPollEnabled: StateFlow<Boolean> = _isAutoPollEnabled.asStateFlow()

  private val _recentCloudRuns = MutableStateFlow<List<GitHubWorkflowRunSummary>>(emptyList())
  val recentCloudRuns: StateFlow<List<GitHubWorkflowRunSummary>> = _recentCloudRuns.asStateFlow()

  private val _cloudApkChoice = MutableStateFlow<CloudApkChoice?>(null)
  val cloudApkChoice: StateFlow<CloudApkChoice?> = _cloudApkChoice.asStateFlow()

  private val _cloudApkUrl = MutableStateFlow("")
  val cloudApkUrl: StateFlow<String> = _cloudApkUrl.asStateFlow()

  private val _pendingCloudApkSave = MutableStateFlow<CloudDownloadedApk?>(null)
  val pendingCloudApkSave: StateFlow<CloudDownloadedApk?> = _pendingCloudApkSave.asStateFlow()

  private val _isCompilingDex = MutableStateFlow(false)
  val isCompilingDex: StateFlow<Boolean> = _isCompilingDex.asStateFlow()

  private val _lastCompileError = MutableStateFlow<String?>(null)
  val lastCompileError: StateFlow<String?> = _lastCompileError.asStateFlow()

  // Session-scoped bounded DEX byte undo buffer
  private val dexEditSession = DexEditSession(maxEntries = 3)
  private val _dexEditUndoPrompt = MutableStateFlow<DexEditUndoPrompt?>(null)
  val dexEditUndoPrompt: StateFlow<DexEditUndoPrompt?> = _dexEditUndoPrompt.asStateFlow()

  private val _canUndoDexEdit = MutableStateFlow(false)
  val canUndoDexEdit: StateFlow<Boolean> = _canUndoDexEdit.asStateFlow()

  private var dexByteCache: Map<String, ByteArray> = emptyMap()
  private var activeSourceApkUri: Uri? = null
  private var activeSourceApkFilePath: String? = null
  private var currentSourceLabel: String = ""
  private var bundleJob: Job? = null
  private var smaliJob: Job? = null
  private var devicePollJob: Job? = null
  private var repoSetupJob: Job? = null
  private var cloudRunPollJob: Job? = null

  val filterMatchSummary: StateFlow<FilterMatchSummary?> =
    combine(_loadState, _config) { load, cfg ->
      val ready = load as? DexLoadState.Ready ?: return@combine null
      withContext(Dispatchers.Default) { FilterRuleEngine.evaluateAll(ready.loadedDexes.values, cfg) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

  val filteredClasses: StateFlow<List<DexClassInfo>> =
    combine(_loadState, _filterState, _selectedMethodIds, filterMatchSummary) { load, filter, selected, ruleMatch ->
      val ready = load as? DexLoadState.Ready ?: return@combine emptyList()
      val activeDexFilter =
        filter.dexSourceFilter?.takeIf { it in ready.loadedDexes }
      val query = filter.searchQuery.trim()
      val ruleMatchedIds = ruleMatch?.matchedMethodIds
      val resolvedApkPkg = ready.resolvedPackageName
      val filtered =
        ready.mergedClasses.filter { cls ->
          if (activeDexFilter != null && cls.sourceEntryName != activeDexFilter) return@filter false
          if (filter.hideFramework && cls.isFrameworkOrLibraryClass(resolvedApkPkg)) return@filter false
          if (filter.onlyWithBytecode && cls.bytecodeMethodCount == 0) return@filter false
          if (filter.onlySelected && cls.allMethods.none { it.id in selected }) return@filter false
          if (filter.onlyMatchingFilterRules && ruleMatchedIds != null) {
            if (cls.allMethods.none { it.id in ruleMatchedIds }) return@filter false
          }
          val isInterfaceOrEnum = (cls.accessFlags and 0x4200) != 0
          when (filter.classTypeFilter) {
            ClassTypeFilter.ALL -> Unit
            ClassTypeFilter.CONCRETE -> if (isInterfaceOrEnum) return@filter false
            ClassTypeFilter.INTERFACE_OR_ENUM -> if (!isInterfaceOrEnum) return@filter false
          }
          if (filter.hideInnerClasses && ('$' in cls.simpleName || (cls.accessFlags and 0x1000) != 0)) {
            return@filter false
          }
          if (filter.methodScopeFilter != MethodScopeFilter.ALL) {
            if (cls.allMethods.none { matchesMethodScope(it, filter.methodScopeFilter) }) {
              return@filter false
            }
          }
          if (query.isNotEmpty()) {
            val matchesClass =
              cls.fullName.contains(query, ignoreCase = true) ||
                cls.descriptor.contains(query, ignoreCase = true)
            val matchesMethod =
              cls.allMethods.any {
                matchesMethodScope(it, filter.methodScopeFilter) &&
                  it.methodRef.name.contains(query, ignoreCase = true)
              }
            if (!matchesClass && !matchesMethod) return@filter false
          }
          true
        }

      val packageFiltered =
        if (filter.nonEmptyPackagesOnly) {
          val nonEmptyPkgs =
            filtered
              .groupBy { it.packageName }
              .filterValues { pkgClasses -> pkgClasses.sumOf { it.bytecodeMethodCount } > 0 }
              .keys
          filtered.filter { it.packageName in nonEmptyPkgs }
        } else {
          filtered
        }

      when (filter.sortOrder) {
        ClassSortOrder.NAME_ASC ->
          packageFiltered.sortedWith(
            compareBy<DexClassInfo, String>(String.CASE_INSENSITIVE_ORDER) { it.simpleName }
              .thenBy(String.CASE_INSENSITIVE_ORDER) { it.packageName }
              .thenBy { it.classIndex }
          )
        ClassSortOrder.NAME_DESC ->
          packageFiltered.sortedWith(
            compareByDescending<DexClassInfo, String>(String.CASE_INSENSITIVE_ORDER) { it.simpleName }
              .thenBy(String.CASE_INSENSITIVE_ORDER) { it.packageName }
              .thenBy { it.classIndex }
          )
        ClassSortOrder.BYTECODE_DESC ->
          packageFiltered.sortedWith(
            compareByDescending<DexClassInfo> { it.bytecodeMethodCount }
              .thenBy(String.CASE_INSENSITIVE_ORDER) { it.simpleName }
              .thenBy { it.classIndex }
          )
        ClassSortOrder.SELECTED_FIRST -> {
          val selectedCounts =
            if (selected.isEmpty()) emptyMap()
            else packageFiltered.associate { cls ->
              cls.uniqueKey to cls.allMethods.count { it.id in selected }
            }
          packageFiltered.sortedWith(
            compareByDescending<DexClassInfo> { selectedCounts[it.uniqueKey] ?: 0 }
              .thenByDescending { it.bytecodeMethodCount }
              .thenBy(String.CASE_INSENSITIVE_ORDER) { it.simpleName }
              .thenBy { it.classIndex }
          )
        }
        ClassSortOrder.DEX_ORDER -> packageFiltered
      }
    }
      .flowOn(Dispatchers.Default)
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  private var hasInitializedFilterDefaults = false
  private var pendingAppSettingsWrites = 0

  init {
    viewModelScope.launch {
      appPrefs.settingsFlow.collect { savedApp ->
        if (pendingAppSettingsWrites == 0) {
          _appSettings.value = savedApp
          _appSettingsHydrated.value = true
          if (!hasInitializedFilterDefaults) {
            hasInitializedFilterDefaults = true
            _filterState.update {
              it.copy(
                hideFramework = savedApp.defaultHideFramework,
                onlyWithBytecode = savedApp.defaultOnlyBytecode,
              )
            }
          }
        }
      }
    }
    viewModelScope.launch {
      prefs.configFlow.collect { saved ->
        _config.value = saved
        refreshInspectionIfActive()
        markBundleDirty()
      }
    }
    viewModelScope.launch {
      cloudPrefs.settingsFlow.collect { savedCloud ->
        _cloudSettings.value = savedCloud
        if (savedCloud.isAuthenticated && _deviceAuthState.value !is DeviceAuthState.AwaitingUser) {
          _deviceAuthState.value = DeviceAuthState.Authenticated(savedCloud.githubUsername)
        } else if (!savedCloud.isAuthenticated && _deviceAuthState.value is DeviceAuthState.Authenticated) {
          _deviceAuthState.value = DeviceAuthState.Idle
        }
        if (savedCloud.isRepoConfigured && savedCloud.workflowInitialized && _repoSetupState.value is RepoSetupState.NotConfigured) {
          _repoSetupState.value =
            RepoSetupState.Ready(
              owner = savedCloud.effectiveOwner,
              repo = savedCloud.repoName,
              htmlUrl = "https://github.com/${savedCloud.effectiveOwner}/${savedCloud.repoName}",
              isPrivate = savedCloud.privateRepo,
              hasWorkflow = true,
            )
        }
      }
    }
  }

  fun openPicker(tab: PickerTab = PickerTab.STORAGE) {
    _activePickerTab.value = tab
    _isPickerOpen.value = true
    if (tab == PickerTab.APPS && _installedApps.value.isEmpty()) {
      loadInstalledApps()
    }
  }

  fun closePicker() {
    _isPickerOpen.value = false
  }

  fun setPickerTab(tab: PickerTab) {
    _activePickerTab.value = tab
    if (tab == PickerTab.APPS && _installedApps.value.isEmpty() && !_isLoadingApps.value) {
      loadInstalledApps()
    }
  }

  fun updateAppSearchQuery(query: String) {
    _appSearchQuery.value = query
  }

  private fun loadInstalledApps() {
    val context = getApplication<Application>()
    viewModelScope.launch {
      _isLoadingApps.value = true
      val apps = withContext(Dispatchers.IO) { InstalledAppRepository.queryLaunchableApps(context) }
      _installedApps.value = apps
      _isLoadingApps.value = false
    }
  }

  fun stageFromSafUri(uri: Uri) {
    stageFromSafUris(listOf(uri))
  }

  fun stageFromSafUris(uris: List<Uri>) {
    if (uris.isEmpty()) return
    val context = getApplication<Application>()
    val items =
      uris.mapIndexed { idx, uri ->
        runCatching {
          context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val fallback = if (uris.size > 1) "classes${if (idx == 0) "" else idx + 1}.dex" else "selected_binary.apk"
        uri to (queryDisplayName(uri) ?: fallback)
      }

    _isPickerOpen.value = false
    val loadingLabel = if (items.size == 1) items.first().second else "${items.size} selected files"
    _stagingState.value = ApkStagingState.Loading(loadingLabel)

    viewModelScope.launch {
      try {
        val preview =
          withContext(Dispatchers.IO) {
            ApkMetadataInspector.inspectMultipleUris(context, items)
          }
        if (items.size == 1) {
          recordRecent(preview)
        }
        val initialSelected =
          if (items.size > 1) preview.dexEntries.map { it.entryName }.toSet()
          else setOf(preview.dexEntries.first().entryName)
        _stagingState.value = ApkStagingState.Ready(preview, initialSelected)
      } catch (t: Throwable) {
        _stagingState.value = ApkStagingState.Error(t.message ?: "Failed to inspect selected file(s).")
      }
    }
  }

  fun stageFromInstalledApp(app: InstalledAppItem) {
    val context = getApplication<Application>()
    _isPickerOpen.value = false
    _stagingState.value = ApkStagingState.Loading(app.appLabel)

    viewModelScope.launch {
      try {
        val preview =
          withContext(Dispatchers.IO) {
            ApkMetadataInspector.inspectFromInstalledApp(context, app)
          }
        recordRecent(preview)
        _stagingState.value = ApkStagingState.Ready(preview, setOf(preview.dexEntries.first().entryName))
      } catch (t: Throwable) {
        _stagingState.value = ApkStagingState.Error(t.message ?: "Failed to inspect ${app.appLabel}.")
      }
    }
  }

  fun stageFromRecentItem(item: RecentBinaryItem) {
    val context = getApplication<Application>()
    _isPickerOpen.value = false
    _stagingState.value = ApkStagingState.Loading(item.displayName)

    viewModelScope.launch {
      try {
        val preview =
          withContext(Dispatchers.IO) {
            when (item.sourceType) {
              BinarySourceType.SAF_URI ->
                ApkMetadataInspector.inspectFromUri(context, Uri.parse(item.uriOrPath), item.displayName)
              BinarySourceType.INSTALLED_APK ->
                ApkMetadataInspector.inspectFromFilePath(context, item.uriOrPath, item.displayName)
            }
          }
        recordRecent(preview)
        _stagingState.value = ApkStagingState.Ready(preview, setOf(preview.dexEntries.first().entryName))
      } catch (t: Throwable) {
        _stagingState.value =
          ApkStagingState.Error(
            "Unable to reopen \"${item.displayName}\": ${t.message ?: "access expired or file moved."}"
          )
      }
    }
  }

  fun inspectLiveAppApk() {
    val context = getApplication<Application>()
    _isPickerOpen.value = false
    _stagingState.value = ApkStagingState.Loading("Dex2C Studio APK")
    viewModelScope.launch {
      try {
        val preview =
          withContext(Dispatchers.IO) {
            val selfFile = File(context.applicationInfo.sourceDir)
            ApkMetadataInspector.inspectFromFilePath(context, selfFile.absolutePath, "Dex2C Studio (base.apk)")
          }
        recordRecent(preview)
        _stagingState.value = ApkStagingState.Ready(preview, setOf(preview.dexEntries.first().entryName))
      } catch (t: Throwable) {
        _stagingState.value = ApkStagingState.Error(t.message ?: "Unable to read live APK.")
      }
    }
  }

  fun toggleStagedDexEntry(entryName: String) {
    val ready = _stagingState.value as? ApkStagingState.Ready ?: return
    val updated =
      if (entryName in ready.selectedEntryNames) ready.selectedEntryNames - entryName
      else ready.selectedEntryNames + entryName
    _stagingState.value = ready.copy(selectedEntryNames = updated)
  }

  fun selectStagedDexEntry(entryName: String) {
    toggleStagedDexEntry(entryName)
  }

  fun selectAllStagedDexEntries() {
    val ready = _stagingState.value as? ApkStagingState.Ready ?: return
    _stagingState.value = ready.copy(selectedEntryNames = ready.preview.dexEntries.map { it.entryName }.toSet())
  }

  fun selectPrimaryStagedDexEntry() {
    val ready = _stagingState.value as? ApkStagingState.Ready ?: return
    val primary = ready.preview.dexEntries.firstOrNull()?.entryName?.let { setOf(it) } ?: emptySet()
    _stagingState.value = ready.copy(selectedEntryNames = primary)
  }

  fun commitStagedDex() {
    val ready = _stagingState.value as? ApkStagingState.Ready ?: return
    val preview = ready.preview
    val chosenEntries = preview.dexEntries.map { it.entryName }.filter { it in ready.selectedEntryNames }
    if (chosenEntries.isEmpty()) return

    // Automatically track the selected APK for Cloud Build dispatch and patched APK export
    if (preview.sourceLabel.endsWith(".apk", ignoreCase = true) || preview.sourceType == BinarySourceType.INSTALLED_APK) {
      activeSourceApkUri = if (preview.sourceType == BinarySourceType.SAF_URI) Uri.parse(preview.sourceRef) else null
      activeSourceApkFilePath = if (preview.sourceType == BinarySourceType.INSTALLED_APK) preview.sourceRef else null
      _cloudApkChoice.value =
        CloudApkChoice(
          label = preview.appLabel?.takeIf { it.isNotBlank() } ?: preview.sourceLabel,
          byteSize = preview.totalByteSize,
          uri = activeSourceApkUri,
          filePath = activeSourceApkFilePath,
        )
    } else {
      activeSourceApkUri = null
      activeSourceApkFilePath = null
    }

    _stagingState.value = null
    val loadingSummary =
      if (chosenEntries.size == 1) chosenEntries.first() else "${chosenEntries.size} DEX files"
    _loadState.value = DexLoadState.Loading("${preview.appLabel ?: preview.sourceLabel} ($loadingSummary)")

    viewModelScope.launch {
      try {
        val label = preview.appLabel?.takeIf { it.isNotBlank() } ?: preview.sourceLabel
        SmaliClassDisassembler.clearCache()
        val parsedList =
          coroutineScope {
            chosenEntries
              .mapNotNull { entryName -> preview.dexByteMap[entryName]?.let { entryName to it } }
              .map { (entryName, bytes) ->
                async(Dispatchers.Default) {
                  DexParser.parseDex(
                    rawBytes = bytes,
                    sourceLabel = label,
                    entryName = entryName,
                  ).releaseTransientPools()
                }
              }
              .awaitAll()
          }
        if (parsedList.isEmpty()) {
          _loadState.value = DexLoadState.Error("Selected DEX binaries could not be read.")
          return@launch
        }
        SmaliClassDisassembler.trimCache(maxEntries = 1)
        val dexMap = linkedMapOf<String, DexParsedFile>()
        parsedList.forEach { dexMap[it.entryName] = it }
        applyParsedDexes(label, preview.dexEntries, preview.dexByteMap, dexMap, preview.packageName)
      } catch (t: Throwable) {
        _loadState.value = DexLoadState.Error(t.message ?: "Failed to parse selected DEX files.")
      }
    }
  }

  fun dismissStagingPreview() {
    _stagingState.value = null
  }

  fun removeRecentItem(id: String) {
    viewModelScope.launch { recentStore.removeItem(id) }
  }

  fun clearRecentHistory() {
    viewModelScope.launch { recentStore.clearAll() }
  }

  private suspend fun recordRecent(preview: ApkStagingPreview) {
    recentStore.recordOpened(
      RecentBinaryItem(
        id = "${preview.sourceType.name}:${preview.sourceRef}",
        displayName = preview.appLabel?.takeIf { it.isNotBlank() } ?: preview.sourceLabel,
        sourceType = preview.sourceType,
        uriOrPath = preview.sourceRef,
        packageName = preview.packageName,
        byteSize = preview.totalByteSize,
        lastOpenedMillis = System.currentTimeMillis(),
      )
    )
  }

  fun switchDexEntry(entryName: String) {
    toggleLoadedDexEntry(entryName)
  }

  fun toggleLoadedDexEntry(entryName: String) {
    val current = _loadState.value as? DexLoadState.Ready ?: return
    if (entryName in current.loadedDexes) {
      if (current.loadedDexes.size <= 1) {
        focusHeaderDexEntry(entryName)
        return
      }
      val removedMethodIds =
        current.loadedDexes[entryName]?.classes?.flatMap { it.allMethods }?.map { it.id }?.toSet() ?: emptySet()
      val updatedMap = linkedMapOf<String, DexParsedFile>()
      current.loadedDexes.forEach { (k, v) -> if (k != entryName) updatedMap[k] = v }
      val nextActive =
        if (current.activeEntryName == entryName) updatedMap.keys.first() else current.activeEntryName
      val nextParsed = updatedMap[nextActive] ?: updatedMap.values.first()
      if (removedMethodIds.isNotEmpty()) {
        _selectedMethodIds.update { it - removedMethodIds }
      }
      if (_filterState.value.dexSourceFilter == entryName) {
        _filterState.update { it.copy(dexSourceFilter = null) }
      }
      if (_inspection.value?.dexClass?.sourceEntryName == entryName) {
        _inspection.value = null
      }
      if (_smaliClassState.value?.dexClass?.sourceEntryName == entryName) {
        _smaliClassState.value = null
      }
      _loadState.value =
        current.copy(
          activeEntryName = nextActive,
          parsedDex = nextParsed,
          loadedDexes = updatedMap,
        )
      markBundleDirty()
      return
    }

    val bytes = dexByteCache[entryName] ?: return
    viewModelScope.launch {
      try {
        val parsed =
          withContext(Dispatchers.Default) {
            DexParser.parseDex(
              rawBytes = bytes,
              sourceLabel = currentSourceLabel,
              entryName = entryName,
            ).releaseTransientPools()
          }
        SmaliClassDisassembler.trimCache(maxEntries = 1)
        val latest = _loadState.value as? DexLoadState.Ready ?: return@launch
        val orderedMap = linkedMapOf<String, DexParsedFile>()
        latest.dexEntries.forEach { entry ->
          when {
            entry.entryName == entryName -> orderedMap[entry.entryName] = parsed
            entry.entryName in latest.loadedDexes -> orderedMap[entry.entryName] = latest.loadedDexes.getValue(entry.entryName)
          }
        }
        if (entryName !in orderedMap) {
          latest.loadedDexes.forEach { (k, v) -> orderedMap[k] = v }
          orderedMap[entryName] = parsed
        }
        _loadState.value =
          latest.copy(
            activeEntryName = entryName,
            parsedDex = parsed,
            loadedDexes = orderedMap,
          )
        _statusBanner.value = "Loaded $entryName (${orderedMap.size} DEXes active)."
      } catch (t: Throwable) {
        _statusBanner.value = "Failed to parse $entryName: ${t.message}"
      }
    }
  }

  fun focusHeaderDexEntry(entryName: String) {
    val current = _loadState.value as? DexLoadState.Ready ?: return
    val target = current.loadedDexes[entryName] ?: return
    _loadState.value = current.copy(activeEntryName = entryName, parsedDex = target)
  }

  private fun applyParsedDexes(
    label: String,
    entries: List<ApkDexEntry>,
    byteMap: Map<String, ByteArray>,
    dexMap: Map<String, DexParsedFile>,
    apkPackageName: String? = null,
  ) {
    currentSourceLabel = label
    dexByteCache = byteMap
    dexEditSession.clear()
    _dexEditUndoPrompt.value = null
    _canUndoDexEdit.value = false
    bundleJob?.cancel()
    smaliJob?.cancel()
    _isTranspiling.value = false
    _transpileProgress.value = null
    _isBundleDirty.value = false
    _selectedMethodIds.value = emptySet()
    _inspection.value = null
    _smaliClassState.value = null
    _generatedBundle.value = null
    _filterState.update { it.copy(dexSourceFilter = null) }
    val primary = dexMap.values.first()
    val resolvedPkg =
      apkPackageName?.takeIf { it.isNotBlank() }
        ?: inferDominantDexPackageName(dexMap.values.flatMap { it.classes })
    _loadState.value = DexLoadState.Ready(label, entries, primary.entryName, primary, dexMap, resolvedPkg)
    val overviewSummary =
      buildBytecodeOverviewSummary(
        sourceLabel = label,
        activeEntryName = primary.entryName,
        dexList = dexMap.values.toList(),
        packageName = resolvedPkg,
      )
    viewModelScope.launch { recentStore.saveLastBytecodeSummary(overviewSummary) }
  }

  fun updateSearchQuery(query: String) {
    _filterState.update { it.copy(searchQuery = query) }
  }

  fun toggleHideFramework() {
    _filterState.update { it.copy(hideFramework = !it.hideFramework) }
  }

  fun toggleOnlyWithBytecode() {
    _filterState.update { it.copy(onlyWithBytecode = !it.onlyWithBytecode) }
  }

  fun toggleOnlySelected() {
    _filterState.update { it.copy(onlySelected = !it.onlySelected) }
  }

  fun toggleOnlyMatchingFilterRules() {
    _filterState.update { it.copy(onlyMatchingFilterRules = !it.onlyMatchingFilterRules) }
  }

  fun setClassSortOrder(sortOrder: ClassSortOrder) {
    _filterState.update { it.copy(sortOrder = sortOrder) }
  }

  fun setClassTypeFilter(classTypeFilter: ClassTypeFilter) {
    _filterState.update { it.copy(classTypeFilter = classTypeFilter) }
  }

  fun toggleHideInnerClasses() {
    _filterState.update { it.copy(hideInnerClasses = !it.hideInnerClasses) }
  }

  fun setMethodScopeFilter(methodScopeFilter: MethodScopeFilter) {
    _filterState.update { it.copy(methodScopeFilter = methodScopeFilter) }
  }

  fun toggleNonEmptyPackagesOnly() {
    _filterState.update { it.copy(nonEmptyPackagesOnly = !it.nonEmptyPackagesOnly) }
  }

  fun setDexSourceFilter(entryName: String?) {
    _filterState.update { it.copy(dexSourceFilter = entryName) }
  }

  fun resetExtraClassFilters() {
    _filterState.update {
      it.copy(
        classTypeFilter = ClassTypeFilter.ALL,
        hideInnerClasses = false,
        methodScopeFilter = MethodScopeFilter.ALL,
        nonEmptyPackagesOnly = false,
        onlyMatchingFilterRules = false,
      )
    }
  }

  fun togglePresetRuleLine(ruleLine: String) {
    updateConfig { current ->
      current.copy(filterRulesText = FilterRuleEngine.toggleRuleLine(current.filterRulesText, ruleLine))
    }
  }

  fun addQuickPackageRule(packageName: String, isBlacklist: Boolean) {
    val slashPkg =
      if (packageName == "(default)" || packageName.isBlank()) ""
      else packageName.replace('.', '/')
    val rule =
      StructuredFilterRule(
        packageName = slashPkg,
        className = "",
        methodName = "",
        isBlacklist = isBlacklist,
        ruleType = FilterRuleType.PACKAGE_ALL,
      )
    updateConfig { current ->
      current.copy(filterRulesText = FilterRuleEngine.upsertStructuredRule(current.filterRulesText, rule))
    }
    _statusBanner.value =
      if (isBlacklist) "Added Blacklist rule for package $packageName"
      else "Added Whitelist rule for package $packageName"
  }

  fun addQuickClassRule(dexClass: DexClassInfo, isBlacklist: Boolean) {
    val slashPkg =
      if (dexClass.packageName == "(default)" || dexClass.packageName.isBlank()) ""
      else dexClass.packageName.replace('.', '/')
    val rule =
      StructuredFilterRule(
        packageName = slashPkg,
        className = dexClass.simpleName,
        methodName = "",
        isBlacklist = isBlacklist,
        ruleType = FilterRuleType.ALL_CLASS_METHODS,
      )
    updateConfig { current ->
      current.copy(filterRulesText = FilterRuleEngine.upsertStructuredRule(current.filterRulesText, rule))
    }
    _statusBanner.value =
      if (isBlacklist) "Added Blacklist rule for ${dexClass.simpleName}"
      else "Added Whitelist rule for ${dexClass.simpleName}"
  }

  private fun markBundleDirty() {
    bundleJob?.cancel()
    _isTranspiling.value = false
    _transpileProgress.value = null
    if (_selectedMethodIds.value.isEmpty()) {
      _generatedBundle.value = null
      _isBundleDirty.value = false
    } else {
      _isBundleDirty.value = true
    }
  }

  fun toggleMethodSelection(methodId: String) {
    _selectedMethodIds.update { current ->
      if (methodId in current) current - methodId else current + methodId
    }
    markBundleDirty()
  }

  fun toggleClassSelection(dexClass: DexClassInfo) {
    val cfg = _config.value
    val allowInit = cfg.allowInit
    val skipSynth = cfg.skipSynthetic
    val scope = _filterState.value.methodScopeFilter
    val scopedMethods = dexClass.allMethods.filter { matchesMethodScope(it, scope) }
    val bytecodeIds =
      scopedMethods
        .filter {
          it.hasBytecode &&
            (allowInit || !it.isInitConstructor) &&
            (!skipSynth || !it.isCoroutineOrSyntheticArtifact)
        }
        .map { it.id }
    val targetIds = bytecodeIds.ifEmpty { scopedMethods.map { it.id } }
    _selectedMethodIds.update { current ->
      val allSelected = targetIds.isNotEmpty() && targetIds.all { it in current }
      if (allSelected) current - targetIds.toSet() else current + targetIds
    }
    markBundleDirty()
  }

  fun togglePackageSelection(packageClasses: List<DexClassInfo>) {
    val cfg = _config.value
    val allowInit = cfg.allowInit
    val skipSynth = cfg.skipSynthetic
    val scope = _filterState.value.methodScopeFilter
    val bytecodeIds = LinkedHashSet<String>()
    val fallbackIds = LinkedHashSet<String>()
    for (cls in packageClasses) {
      for (m in cls.directMethods) {
        if (matchesMethodScope(m, scope)) {
          fallbackIds.add(m.id)
          if (
            m.hasBytecode &&
              (allowInit || !m.isInitConstructor) &&
              (!skipSynth || !m.isCoroutineOrSyntheticArtifact)
          ) {
            bytecodeIds.add(m.id)
          }
        }
      }
      for (m in cls.virtualMethods) {
        if (matchesMethodScope(m, scope)) {
          fallbackIds.add(m.id)
          if (
            m.hasBytecode &&
              (allowInit || !m.isInitConstructor) &&
              (!skipSynth || !m.isCoroutineOrSyntheticArtifact)
          ) {
            bytecodeIds.add(m.id)
          }
        }
      }
    }
    val targetIds: Set<String> = bytecodeIds.ifEmpty { fallbackIds }
    if (targetIds.isEmpty()) return
    _selectedMethodIds.update { current ->
      val allSelected = targetIds.all { it in current }
      if (allSelected) current - targetIds else current + targetIds
    }
    markBundleDirty()
  }

  fun autoSelectAppMethods() {
    val ready = _loadState.value as? DexLoadState.Ready ?: return
    val cfg = _config.value
    val allowInit = cfg.allowInit
    val skipSynth = cfg.skipSynthetic
    val limit = _appSettings.value.autoSelectMethodLimit.coerceIn(8, 256)
    val allClasses = ready.mergedClasses
    val appMethods =
      allClasses
        .asSequence()
        .filter { !it.isFrameworkClass }
        .flatMap { it.allMethods.asSequence() }
        .filter {
          it.hasBytecode &&
            (allowInit || !it.isInitConstructor) &&
            (!skipSynth || !it.isCoroutineOrSyntheticArtifact)
        }
        .take(limit)
        .map { it.id }
        .toSet()
    val fallback =
      if (appMethods.isNotEmpty()) {
        appMethods
      } else {
        allClasses
          .asSequence()
          .flatMap { it.allMethods.asSequence() }
          .filter {
            it.hasBytecode &&
              (allowInit || !it.isInitConstructor) &&
              (!skipSynth || !it.isCoroutineOrSyntheticArtifact)
          }
          .take((limit / 2).coerceAtLeast(8))
          .map { it.id }
          .toSet()
      }
    _selectedMethodIds.value = fallback
    _statusBanner.value = "Selected ${fallback.size} bytecode methods for C transpilation."
    markBundleDirty()
  }

  fun applyFilterRules() {
    val ready = _loadState.value as? DexLoadState.Ready ?: return
    val cfg = _config.value
    viewModelScope.launch {
      val summary = withContext(Dispatchers.Default) { FilterRuleEngine.evaluateAll(ready.loadedDexes.values, cfg) }
      val matched = summary.matchedMethodIds
      _selectedMethodIds.value = matched
      _statusBanner.value = "filter.txt matched ${matched.size} methods."
      markBundleDirty()
      ensureBundleBuilt(force = true)
    }
  }

  fun importFilterTxtFromUri(uri: Uri) {
    val context = getApplication<Application>()
    viewModelScope.launch {
      try {
        val content =
          withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
              ?: throw IllegalArgumentException("Unable to read filter.txt")
          }
        updateConfig { it.copy(filterRulesText = content) }
        _statusBanner.value = "Imported filter.txt rules."
      } catch (t: Throwable) {
        _statusBanner.value = "Import failed: ${t.message}"
      }
    }
  }

  fun importDccCfgFromUri(uri: Uri) {
    val context = getApplication<Application>()
    viewModelScope.launch {
      try {
        val content =
          withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
              ?: throw IllegalArgumentException("Unable to read dcc.cfg")
          }
        updateConfig { current -> DccConfigCodec.decodeDccCfg(content, current) }
        _statusBanner.value = "Imported dcc.cfg configuration."
      } catch (t: Throwable) {
        _statusBanner.value = "Invalid dcc.cfg: ${t.message}"
      }
    }
  }

  fun clearSelection() {
    bundleJob?.cancel()
    _isTranspiling.value = false
    _transpileProgress.value = null
    _isBundleDirty.value = false
    _selectedMethodIds.value = emptySet()
    _generatedBundle.value = null
  }

  fun inspectClassSmali(dexClass: DexClassInfo, focusMethodName: String? = null) {
    val ready = _loadState.value as? DexLoadState.Ready ?: return
    val targetDex =
      ready.loadedDexes[dexClass.sourceEntryName]
        ?: ready.parsedDex
    smaliJob?.cancel()
    _lastCompileError.value = null
    _smaliClassState.value = SmaliClassUiState.Loading(dexClass)
    smaliJob =
      viewModelScope.launch {
        try {
          val (result, javaCode) =
            withContext(Dispatchers.Default) {
              val dis = SmaliClassDisassembler.disassembleClass(targetDex, dexClass)
              val java = SmaliJavaDecompiler.decompileClassToJava(targetDex, dexClass)
              dis to java
            }
          _smaliClassState.value =
            SmaliClassUiState.Ready(
              dexClass = dexClass,
              lines = result.lines,
              lineCount = result.lineCount,
              methodCount = result.methodCount,
              fieldCount = result.fieldCount,
              durationMs = result.durationMs,
              initialFocusMethodName = focusMethodName,
              methodSpans = result.methodSpans,
              decompiledJava = javaCode,
            )
        } catch (t: Throwable) {
          if (t is CancellationException) throw t
          _smaliClassState.value =
            SmaliClassUiState.Error(
              dexClass = dexClass,
              message = t.message ?: "Unable to disassemble ${dexClass.fullName} into Smali.",
            )
        }
      }
  }

  fun clearSmaliInspection() {
    smaliJob?.cancel()
    smaliJob = null
    _lastCompileError.value = null
    _smaliClassState.value = null
    SmaliClassDisassembler.trimCache(maxEntries = 1)
  }

  fun clearCompileError() {
    _lastCompileError.value = null
  }

  fun clearDexEditUndoPrompt() {
    _dexEditUndoPrompt.value = null
  }

  fun undoLastDexModification() {
    if (_isCompilingDex.value) return
    val current = _loadState.value as? DexLoadState.Ready ?: return
    val entry = dexEditSession.pop() ?: return
    _canUndoDexEdit.value = dexEditSession.canUndo

    viewModelScope.launch {
      try {
        val restoredParsed = withContext(Dispatchers.Default) {
          DexParser.parseDex(
            rawBytes = entry.beforeBytes,
            sourceLabel = currentSourceLabel,
            entryName = entry.entryName,
          )
        }
        SmaliClassDisassembler.clearCache()
        applyReplacedDexEntry(entry.entryName, entry.beforeBytes, restoredParsed)
        val nextUndo = dexEditSession.peek()
        _dexEditUndoPrompt.value = nextUndo?.let {
          DexEditUndoPrompt(it.id, "Undo available: ${it.label}", it.entryName)
        }
        _statusBanner.value = "Restored previous DEX state for ${entry.entryName} (${entry.label})."
      } catch (t: Throwable) {
        if (t is CancellationException) throw t
        _statusBanner.value = "Failed to restore DEX state: ${t.message}"
      }
    }
  }

  fun compileEditedSmaliClass(
    dexClass: DexClassInfo,
    editedSmaliText: String,
    onSuccess: () -> Unit = {},
  ) {
    if (!BuildConfig.ENABLE_DEX_EDITING) {
      _statusBanner.value = "Smali re-assembly is disabled in this build."
      _lastCompileError.value = "Smali re-assembly is temporarily disabled while the compiler is being upgraded."
      return
    }
    val ready = _loadState.value as? DexLoadState.Ready ?: return
    val entryName = dexClass.sourceEntryName.ifBlank { ready.activeEntryName }
    val targetDex = ready.loadedDexes[entryName] ?: ready.parsedDex
    if (_isCompilingDex.value) return
    _isCompilingDex.value = true
    _lastCompileError.value = null

    val beforeBytes = targetDex.rawBytes.copyOf()

    viewModelScope.launch {
      try {
        val result =
          withContext(Dispatchers.Default) {
            SmaliDexCompiler.compileAndReplaceClass(
              dex = targetDex,
              targetClassDescriptor = dexClass.descriptor,
              editedSmaliText = editedSmaliText,
            )
          }
        when (result) {
          is DexCompileResult.Success -> {
            val updatedParsed = result.updatedDex.releaseTransientPools()
            val verifyError = DexEditVerifier.verifyClassPresent(updatedParsed, dexClass.descriptor)
            if (verifyError != null) {
              _lastCompileError.value = verifyError
              _statusBanner.value = "Smali compile error: $verifyError"
              return@launch
            }

            val updatedClass =
              updatedParsed.classes.firstOrNull { it.descriptor == dexClass.descriptor }
                ?: updatedParsed.classes.firstOrNull { it.fullName == dexClass.fullName }
                ?: dexClass

            val (disassembled, javaCode) =
              withContext(Dispatchers.Default) {
                val dis = SmaliClassDisassembler.disassembleClass(updatedParsed, updatedClass)
                val java = SmaliJavaDecompiler.decompileClassToJava(updatedParsed, updatedClass)
                dis to java
              }

            SmaliClassDisassembler.clearCache()
            applyReplacedDexEntry(entryName, result.updatedDexBytes, updatedParsed)
            val undoEntry = dexEditSession.record("Smali edit ${updatedClass.simpleName}", entryName, beforeBytes)
            _canUndoDexEdit.value = dexEditSession.canUndo
            _dexEditUndoPrompt.value = DexEditUndoPrompt(undoEntry.id, "Assembled ${updatedClass.simpleName} into $entryName", entryName)

            _smaliClassState.value =
              SmaliClassUiState.Ready(
                dexClass = updatedClass,
                lines = disassembled.lines,
                lineCount = disassembled.lineCount,
                methodCount = disassembled.methodCount,
                fieldCount = disassembled.fieldCount,
                durationMs = disassembled.durationMs,
                methodSpans = disassembled.methodSpans,
                decompiledJava = javaCode,
              )
            _statusBanner.value =
              "Assembled ${updatedClass.simpleName} into $entryName (${result.durationMs} ms)."
            markBundleDirty()
            onSuccess()
          }
          is DexCompileResult.Error -> {
            val linePrefix = result.line?.let { "Line $it: " }.orEmpty()
            _lastCompileError.value = "$linePrefix${result.message}"
            _statusBanner.value = "Smali compile error: $linePrefix${result.message}"
          }
        }
      } catch (t: Throwable) {
        if (t is CancellationException) throw t
        _lastCompileError.value = t.message ?: "Failed to assemble Smali."
        _statusBanner.value = "Smali compile failed: ${t.message}"
      } finally {
        _isCompilingDex.value = false
      }
    }
  }

  fun deleteDexClasses(descriptors: Set<String>, entryName: String? = null) {
    if (descriptors.isEmpty()) return
    val ready = _loadState.value as? DexLoadState.Ready ?: return
    val targetEntry = entryName ?: ready.activeEntryName
    val targetDex = ready.loadedDexes[targetEntry] ?: ready.parsedDex
    if (_isCompilingDex.value) return
    _isCompilingDex.value = true

    val beforeBytes = targetDex.rawBytes.copyOf()

    viewModelScope.launch {
      try {
        val result =
          withContext(Dispatchers.Default) {
            SmaliDexCompiler.deleteClassesFromDex(targetDex, descriptors)
          }
        when (result) {
          is DexCompileResult.Success -> {
            val updatedParsed = result.updatedDex.releaseTransientPools()
            val verifyError = DexEditVerifier.verifyClassesDeleted(updatedParsed, descriptors)
            if (verifyError != null) {
              _statusBanner.value = "Delete class failed: $verifyError"
              return@launch
            }
            SmaliClassDisassembler.clearCache()
            applyReplacedDexEntry(targetEntry, result.updatedDexBytes, updatedParsed)
            val undoEntry = dexEditSession.record("Delete ${descriptors.size} class(es)", targetEntry, beforeBytes)
            _canUndoDexEdit.value = dexEditSession.canUndo
            _dexEditUndoPrompt.value = DexEditUndoPrompt(undoEntry.id, "Deleted ${descriptors.size} class(es) from $targetEntry", targetEntry)
            _statusBanner.value =
              "Removed ${descriptors.size} class(es) from $targetEntry (${result.durationMs} ms)."
            markBundleDirty()
          }
          is DexCompileResult.Error -> {
            _statusBanner.value = "Delete class failed: ${result.message}"
          }
        }
      } catch (t: Throwable) {
        if (t is CancellationException) throw t
        _statusBanner.value = "Delete class failed: ${t.message}"
      } finally {
        _isCompilingDex.value = false
      }
    }
  }

  fun replaceStringInActiveDex(
    searchQuery: String,
    replacement: String,
    ignoreCase: Boolean,
  ) {
    if (!BuildConfig.ENABLE_DEX_EDITING) {
      _statusBanner.value = "DEX string replacement is disabled in this build."
      return
    }
    if (searchQuery.isEmpty()) return
    val ready = _loadState.value as? DexLoadState.Ready ?: return
    val targetEntry = ready.activeEntryName
    val targetDex = ready.loadedDexes[targetEntry] ?: ready.parsedDex
    if (_isCompilingDex.value) return
    _isCompilingDex.value = true

    val beforeBytes = targetDex.rawBytes.copyOf()

    viewModelScope.launch {
      try {
        val (result, replacedCount) =
          withContext(Dispatchers.Default) {
            SmaliDexCompiler.replaceStringLiteralsInDex(
              dex = targetDex,
              searchQuery = searchQuery,
              replacement = replacement,
              ignoreCase = ignoreCase,
            )
          }
        val countError = DexEditVerifier.verifyReplacementCount(replacedCount, searchQuery)
        if (countError != null) {
          _statusBanner.value = countError
          return@launch
        }
        when (result) {
          is DexCompileResult.Success -> {
            SmaliClassDisassembler.clearCache()
            val updatedParsed = result.updatedDex.releaseTransientPools()
            applyReplacedDexEntry(targetEntry, result.updatedDexBytes, updatedParsed)
            val undoEntry = dexEditSession.record("Replace string '$searchQuery'", targetEntry, beforeBytes)
            _canUndoDexEdit.value = dexEditSession.canUndo
            _dexEditUndoPrompt.value = DexEditUndoPrompt(undoEntry.id, "Replaced $replacedCount string(s) in $targetEntry", targetEntry)
            _statusBanner.value =
              "Replaced $replacedCount string literal(s) in $targetEntry (${result.durationMs} ms)."
            markBundleDirty()
          }
          is DexCompileResult.Error -> {
            _statusBanner.value = result.message
          }
        }
      } catch (t: Throwable) {
        if (t is CancellationException) throw t
        _statusBanner.value = "String replacement failed: ${t.message}"
      } finally {
        _isCompilingDex.value = false
      }
    }
  }

  private fun applyReplacedDexEntry(
    entryName: String,
    updatedBytes: ByteArray,
    updatedParsed: DexParsedFile,
  ) {
    val current = _loadState.value as? DexLoadState.Ready ?: return
    val newByteCache = LinkedHashMap(dexByteCache)
    newByteCache[entryName] = updatedBytes
    dexByteCache = newByteCache

    val updatedLoaded = LinkedHashMap<String, DexParsedFile>()
    current.loadedDexes.forEach { (k, v) ->
      updatedLoaded[k] = if (k == entryName) updatedParsed else v
    }
    if (entryName !in updatedLoaded) {
      updatedLoaded[entryName] = updatedParsed
    }
    val updatedEntries =
      current.dexEntries.map { entry ->
        if (entry.entryName == entryName) entry.copy(byteSize = updatedBytes.size) else entry
      }
    val nextParsed =
      if (current.activeEntryName == entryName) updatedParsed else current.parsedDex
    _loadState.value =
      current.copy(
        dexEntries = updatedEntries,
        parsedDex = nextParsed,
        loadedDexes = updatedLoaded,
      )
  }

  fun exportPatchedDexToUri(uri: Uri, entryName: String? = null) {
    val ready = _loadState.value as? DexLoadState.Ready ?: return
    val targetEntry = entryName ?: ready.activeEntryName
    val bytes =
      dexByteCache[targetEntry]
        ?: ready.loadedDexes[targetEntry]?.rawBytes
        ?: ready.parsedDex.rawBytes
    val context = getApplication<Application>()
    viewModelScope.launch {
      try {
        withContext(Dispatchers.IO) {
          context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(bytes)
          } ?: throw IllegalStateException("Unable to open output stream for $targetEntry")
        }
        _statusBanner.value = "Exported patched $targetEntry (${bytes.size / 1024} KB)."
      } catch (t: Throwable) {
        _statusBanner.value = "DEX export failed: ${t.message}"
      }
    }
  }

  fun exportPatchedApkToUri(uri: Uri) {
    val ready = _loadState.value as? DexLoadState.Ready ?: return
    val context = getApplication<Application>()
    val patchedMap = LinkedHashMap<String, ByteArray>()
    ready.dexEntries.forEach { entry ->
      val bytes = dexByteCache[entry.entryName] ?: ready.loadedDexes[entry.entryName]?.rawBytes
      if (bytes != null) patchedMap[entry.entryName] = bytes
    }
    if (patchedMap.isEmpty()) {
      patchedMap[ready.activeEntryName] = ready.parsedDex.rawBytes
    }
    val sourceUri = activeSourceApkUri
    val sourceFilePath = activeSourceApkFilePath

    viewModelScope.launch {
      try {
        withContext(Dispatchers.IO) {
          context.contentResolver.openOutputStream(uri)?.use { out ->
            val srcStream =
              when {
                sourceUri != null -> context.contentResolver.openInputStream(sourceUri)
                !sourceFilePath.isNullOrBlank() && File(sourceFilePath).exists() ->
                  FileInputStream(File(sourceFilePath))
                else -> null
              }
            if (srcStream != null) {
              srcStream.use { input ->
                SmaliDexCompiler.repackApkWithPatchedDexes(
                  originalApkStream = input,
                  patchedDexByEntryName = patchedMap,
                  outputStream = out,
                )
              }
            } else {
              val zipBytes = SmaliDexCompiler.buildDexContainerZip(patchedMap)
              out.write(zipBytes)
            }
          } ?: throw IllegalStateException("Unable to open destination stream.")
        }
        _statusBanner.value =
          "Exported patched archive (${patchedMap.size} DEX file(s)). Note: Re-sign APK with apksigner before installing."
      } catch (t: Throwable) {
        _statusBanner.value = "Patched APK export failed: ${t.message}"
      }
    }
  }

  fun clearMethodInspection() {
    _inspection.value = null
  }

  /**
   * Reclaims transient caches and non-visible buffers on navigation or system memory pressure.
   */
  fun onTrimMemory(level: Int = android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
    SmaliClassDisassembler.clearCache()
    if (!_isPickerOpen.value && _installedApps.value.isNotEmpty()) {
      _installedApps.value = emptyList()
    }
    val current = _loadState.value as? DexLoadState.Ready
    if (current != null) {
      val anyUnreleased = current.loadedDexes.values.any { !it.areTransientPoolsReleased }
      if (anyUnreleased) {
        val trimmedMap = linkedMapOf<String, DexParsedFile>()
        current.loadedDexes.forEach { (k, v) -> trimmedMap[k] = v.releaseTransientPools() }
        val trimmedActive = trimmedMap[current.activeEntryName] ?: trimmedMap.values.first()
        _loadState.value =
          current.copy(
            parsedDex = trimmedActive,
            loadedDexes = trimmedMap,
          )
      }
      if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN && dexByteCache.size > current.loadedDexes.size) {
        dexByteCache = dexByteCache.filterKeys { it in current.loadedDexes }
      }
    }
  }

  override fun onCleared() {
    super.onCleared()
    SmaliClassDisassembler.clearCache()
    dexByteCache = emptyMap()
  }

  fun exportSmaliToUri(uri: Uri, smaliText: String) {
    val context = getApplication<Application>()
    viewModelScope.launch {
      try {
        withContext(Dispatchers.IO) {
          context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(smaliText.toByteArray(Charsets.UTF_8))
          } ?: throw IllegalStateException("Unable to open output stream for .smali file")
        }
        _statusBanner.value = "Saved .smali class file."
      } catch (t: Throwable) {
        _statusBanner.value = "Export failed: ${t.message}"
      }
    }
  }

  fun inspectMethod(dexClass: DexClassInfo, method: DexEncodedMethod) {
    val ready = _loadState.value as? DexLoadState.Ready ?: return
    val targetDex =
      ready.loadedDexes[dexClass.sourceEntryName]
        ?: ready.loadedDexes[method.sourceEntryName]
        ?: ready.parsedDex
    val cfg = _config.value
    viewModelScope.launch {
      val state =
        withContext(Dispatchers.Default) {
          val insns = DalvikDisassembler.disassemble(targetDex, method)
          val tries = DalvikDisassembler.parseTryBlocks(targetDex, method)
          val methodCfg = ControlFlowGraphBuilder.build(insns, tries)
          val cCode = Dex2cTranspiler.transpileMethodToC(targetDex, method, cfg)
          val meta = DexHelper.inspectMethodMetadata(targetDex, dexClass, method)
          val methodSmali = SmaliClassDisassembler.disassembleMethod(targetDex, dexClass, method)
          val javaCode = SmaliJavaDecompiler.decompileMethodToJava(targetDex, dexClass, method, insns, tries)
          MethodInspectionState(
            method = method,
            dexClass = dexClass,
            instructions = insns,
            tryBlocks = tries,
            transpiledC = cCode,
            structuralMeta = meta,
            cfg = methodCfg,
            methodSmali = methodSmali,
            decompiledJava = javaCode,
          )
        }
      _inspection.value = state
    }
  }

  fun syncSelectionToWildcardRules(isBlacklist: Boolean = false) {
    val ready = _loadState.value as? DexLoadState.Ready ?: return
    val selectedIds = _selectedMethodIds.value
    if (selectedIds.isEmpty()) {
      _statusBanner.value = "Select at least one package, class, or method in Classes first."
      return
    }
    val generatedRules =
      FilterRuleEngine.generateWildcardRulesFromSelection(
        classes = ready.mergedClasses,
        selectedMethodIds = selectedIds,
        allowInit = _config.value.allowInit,
        isBlacklist = isBlacklist,
      )
    if (generatedRules.isEmpty()) return
    updateConfig { current ->
      val existingRules = FilterRuleEngine.parseStructuredRules(current.filterRulesText)
      val existingLines = existingRules.map { it.toFilterLine() }.toSet()
      val merged = existingRules + generatedRules.filter { it.toFilterLine() !in existingLines }
      current.copy(filterRulesText = FilterRuleEngine.serializeStructuredRules(merged))
    }
    _statusBanner.value = "Synced ${generatedRules.size} wildcard filter rule(s) from selection."
  }

  fun updateConfig(transform: (TranspilerConfig) -> TranspilerConfig) {
    val updated = transform(_config.value)
    _config.value = updated
    viewModelScope.launch { prefs.saveConfig(updated) }
    refreshInspectionIfActive()
    markBundleDirty()
  }

  private fun refreshInspectionIfActive() {
    val current = _inspection.value ?: return
    val ready = _loadState.value as? DexLoadState.Ready ?: return
    val targetDex =
      ready.loadedDexes[current.dexClass.sourceEntryName]
        ?: ready.loadedDexes[current.method.sourceEntryName]
        ?: ready.parsedDex
    val cfg = _config.value
    viewModelScope.launch {
      try {
        val newCode =
          withContext(Dispatchers.Default) {
            Dex2cTranspiler.transpileMethodToC(targetDex, current.method, cfg)
          }
        _inspection.value = current.copy(transpiledC = newCode)
      } catch (t: Throwable) {
        if (t is CancellationException) throw t
        _statusBanner.value = "C preview failed: ${t.message ?: t::class.simpleName}"
      }
    }
  }

  fun ensureBundleBuilt(force: Boolean = false) {
    val ready = _loadState.value as? DexLoadState.Ready ?: return
    val selectedIds = _selectedMethodIds.value
    if (selectedIds.isEmpty()) {
      bundleJob?.cancel()
      _isTranspiling.value = false
      _transpileProgress.value = null
      _isBundleDirty.value = false
      _generatedBundle.value = null
      return
    }
    if (!force && !_isBundleDirty.value && (_generatedBundle.value != null || _isTranspiling.value)) {
      return
    }
    rebuildBundle()
  }

  private fun collectSelectedEntries(
    ready: DexLoadState.Ready,
    selectedIds: Set<String>,
  ): List<Pair<DexParsedFile, DexEncodedMethod>> {
    val pairs = ArrayList<Pair<DexParsedFile, DexEncodedMethod>>(selectedIds.size)
    for (dex in ready.loadedDexes.values) {
      for (cls in dex.classes) {
        for (m in cls.directMethods) {
          if (m.id in selectedIds) pairs.add(dex to m)
        }
        for (m in cls.virtualMethods) {
          if (m.id in selectedIds) pairs.add(dex to m)
        }
      }
    }
    return pairs
  }

  fun rebuildBundle() {
    val ready = _loadState.value as? DexLoadState.Ready ?: return
    val selectedIds = _selectedMethodIds.value
    if (selectedIds.isEmpty()) {
      _generatedBundle.value = null
      _isBundleDirty.value = false
      _transpileProgress.value = null
      return
    }
    val cfg = _config.value
    val bundleCacheDir = File(getApplication<Application>().cacheDir, "dex2c_bundle")
    bundleJob?.cancel()
    bundleJob =
      viewModelScope.launch {
        _isTranspiling.value = true
        _transpileProgress.value =
          TranspileProgress(
            completedMethods = 0,
            totalMethods = selectedIds.size,
            activeChunkIndex = 1,
            totalChunks = ((selectedIds.size - 1) / Dex2cTranspiler.DEFAULT_METHODS_PER_CHUNK) + 1,
          )
        try {
          val bundle =
            withContext(Dispatchers.Default) {
              val dexMethodPairs = collectSelectedEntries(ready, selectedIds)
              Dex2cTranspiler.transpileBundleMultiDex(
                entries = dexMethodPairs,
                config = cfg,
                sourceLabelHint = ready.sourceLabel,
                outputDir = bundleCacheDir,
                onProgress = { progress -> _transpileProgress.value = progress },
              )
            }
          _generatedBundle.value = bundle
          _isBundleDirty.value = false
        } catch (t: Throwable) {
          if (t is CancellationException) throw t
          _statusBanner.value = "Transpilation failed: ${t.message ?: t::class.simpleName}"
        } finally {
          _isTranspiling.value = false
          _transpileProgress.value = null
        }
      }
  }

  fun exportBundleToZip(uri: Uri) {
    val ready = _loadState.value as? DexLoadState.Ready
    val selectedIds = _selectedMethodIds.value
    if (_generatedBundle.value == null && (ready == null || selectedIds.isEmpty())) return
    val context = getApplication<Application>()
    viewModelScope.launch {
      try {
        if (_generatedBundle.value == null || _isBundleDirty.value) {
          ensureBundleBuilt(force = false)
          bundleJob?.join()
        }
        val bundle = _generatedBundle.value ?: return@launch
        withContext(Dispatchers.IO) { ZipBundleExporter.exportToUri(context, uri, bundle) }
        _statusBanner.value = "Exported ${bundle.allFiles.size} NDK files to ZIP."
      } catch (t: Throwable) {
        if (t is CancellationException) throw t
        _statusBanner.value = "Export failed: ${t.message}"
      }
    }
  }

  fun clearStatusBanner() {
    _statusBanner.value = null
  }

  // --- Detailed Settings & GitHub Cloud Dex2C Workflow ---

  fun updateAppSettings(transform: (AppSettings) -> AppSettings) {
    val updated = transform(_appSettings.value)
    _appSettings.value = updated
    hasInitializedFilterDefaults = true
    _filterState.update {
      it.copy(
        hideFramework = updated.defaultHideFramework,
        onlyWithBytecode = updated.defaultOnlyBytecode,
      )
    }
    _appSettingsHydrated.value = true
    pendingAppSettingsWrites++
    viewModelScope.launch {
      try {
        val persisted = appPrefs.updateSettings(transform)
        pendingAppSettingsWrites = (pendingAppSettingsWrites - 1).coerceAtLeast(0)
        if (pendingAppSettingsWrites == 0) {
          _appSettings.value = persisted
          _appSettingsHydrated.value = true
        }
      } catch (t: Throwable) {
        pendingAppSettingsWrites = (pendingAppSettingsWrites - 1).coerceAtLeast(0)
        throw t
      }
    }
  }

  fun completeOnboarding() {
    if (_appSettings.value.hasCompletedOnboarding && pendingAppSettingsWrites == 0) return
    updateAppSettings { it.copy(hasCompletedOnboarding = true) }
  }

  fun sendTestBuildNotification() {
    val sent = CloudBuildNotifier.sendTestNotification(getApplication(), _appSettings.value)
    _statusBanner.value =
      if (sent) "Test build alert sent to notification shade."
      else "Grant Notification permission first to preview alerts."
  }

  fun resetAllSettingsToDefaults(clearGitHubSession: Boolean = false) {
    viewModelScope.launch {
      appPrefs.resetToDefaults()
      val defaultConfig = TranspilerConfig()
      _config.value = defaultConfig
      prefs.saveConfig(defaultConfig)
      val currentCloud = _cloudSettings.value
      val resetCloud =
        if (clearGitHubSession) {
          CloudBuildMonitorScheduler.cancelMonitor(getApplication())
          _deviceAuthState.value = DeviceAuthState.Idle
          _repoSetupState.value = RepoSetupState.NotConfigured
          _cloudRunState.value = CloudBuildRunState.Idle
          _recentCloudRuns.value = emptyList()
          CloudSettings()
        } else {
          CloudSettings(
            githubClientId = currentCloud.githubClientId,
            accessToken = currentCloud.accessToken,
            githubUsername = currentCloud.githubUsername,
            repoOwner = currentCloud.repoOwner,
            repoName = currentCloud.repoName,
            defaultBranch = currentCloud.defaultBranch,
            privateRepo = currentCloud.privateRepo,
            workflowInitialized = currentCloud.workflowInitialized,
          )
        }
      _cloudSettings.value = resetCloud
      cloudPrefs.saveSettings(resetCloud)
      recentStore.clearLastBytecodeSummary()
      _statusBanner.value = "All settings restored to factory defaults."
    }
  }

  fun updateCloudSettings(transform: (CloudSettings) -> CloudSettings) {
    val updated = transform(_cloudSettings.value)
    _cloudSettings.value = updated
    viewModelScope.launch { cloudPrefs.saveSettings(updated) }
  }

  fun setNotificationsEnabled(enabled: Boolean) {
    updateCloudSettings { it.copy(notificationsEnabled = enabled) }
    val app = getApplication<Application>()
    if (!enabled) {
      CloudBuildMonitorScheduler.cancelMonitor(app)
    } else {
      val polling = _cloudRunState.value as? CloudBuildRunState.Polling
      if (polling != null) {
        CloudBuildMonitorScheduler.scheduleMonitor(app, polling.runId)
      }
    }
  }

  fun updateCloudApkUrl(url: String) {
    _cloudApkUrl.value = url
  }

  fun selectCloudApkFromSaf(uri: Uri) {
    val context = getApplication<Application>()
    val name = queryDisplayName(uri) ?: "target.apk"
    viewModelScope.launch {
      val size =
        withContext(Dispatchers.IO) {
          runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L
          }.getOrDefault(0L)
        }
      _cloudApkChoice.value = CloudApkChoice(label = name, byteSize = size.coerceAtLeast(0L), uri = uri)
      _statusBanner.value = "Queued \"$name\" for Cloud Dex2C workflow."
    }
  }

  fun useLiveApkForCloud() {
    val context = getApplication<Application>()
    val apkFile = File(context.applicationInfo.sourceDir)
    _cloudApkChoice.value =
      CloudApkChoice(
        label = "Dex2C Studio (base.apk)",
        byteSize = apkFile.length(),
        filePath = apkFile.absolutePath,
      )
    _statusBanner.value = "Selected live base.apk for Cloud Dex2C."
  }

  fun startDeviceFlowLogin() {
    val clientId = _cloudSettings.value.effectiveClientId
    devicePollJob?.cancel()
    _deviceAuthState.value = DeviceAuthState.RequestingCode

    devicePollJob =
      viewModelScope.launch {
        try {
          val codeResp =
            NetworkResilience.retryTransient(maxAttempts = 2) {
              withContext(Dispatchers.IO) { gitHubClient.requestDeviceCode(clientId) }
            }
          var intervalSec = codeResp.intervalSec
          val safeExpiresInSec = codeResp.expiresInSec.coerceAtLeast(60)
          val deadlineMs = System.currentTimeMillis() + safeExpiresInSec * 1000L
          val defaultStatusNote = "Waiting for authorization at github.com/login/device…"

          _deviceAuthState.value =
            DeviceAuthState.AwaitingUser(
              userCode = codeResp.userCode,
              verificationUri = codeResp.verificationUri,
              expiresInSec = safeExpiresInSec,
              intervalSec = intervalSec,
              expiresAtMillis = deadlineMs,
              statusNote = defaultStatusNote,
            )

          while (isActive && System.currentTimeMillis() < deadlineMs) {
            delay(intervalSec * 1000L)
            if (System.currentTimeMillis() >= deadlineMs) break
            val pollResult =
              try {
                withContext(Dispatchers.IO) {
                  gitHubClient.pollAccessTokenOnce(clientId, codeResp.deviceCode, intervalSec)
                }
              } catch (t: Throwable) {
                if (t is CancellationException) throw t
                if (NetworkResilience.isTransientNetworkError(t)) {
                  val cur = _deviceAuthState.value as? DeviceAuthState.AwaitingUser
                  if (cur != null) {
                    _deviceAuthState.value =
                      cur.copy(statusNote = "Network unreachable — retrying authorization check…")
                  }
                  continue
                }
                throw t
              }
            when (pollResult) {
              is TokenPollResult.Pending -> {
                val cur = _deviceAuthState.value as? DeviceAuthState.AwaitingUser
                if (cur != null && cur.statusNote != defaultStatusNote) {
                  _deviceAuthState.value = cur.copy(statusNote = defaultStatusNote)
                }
              }
              is TokenPollResult.SlowDown -> {
                intervalSec = pollResult.nextIntervalSec
                val cur = _deviceAuthState.value as? DeviceAuthState.AwaitingUser
                if (cur != null) {
                  _deviceAuthState.value =
                    cur.copy(intervalSec = intervalSec, statusNote = defaultStatusNote)
                }
              }
              is TokenPollResult.Expired -> {
                _deviceAuthState.value = DeviceAuthState.Error("Device code expired after 15 minutes. Tap Continue with GitHub account to generate a new code.")
                return@launch
              }
              is TokenPollResult.Denied -> {
                _deviceAuthState.value = DeviceAuthState.Error("Authorization was denied on GitHub.")
                return@launch
              }
              is TokenPollResult.Failure -> {
                _deviceAuthState.value = DeviceAuthState.Error(pollResult.message)
                return@launch
              }
              is TokenPollResult.Success -> {
                val username =
                  NetworkResilience.retryTransient(maxAttempts = 3) {
                    withContext(Dispatchers.IO) {
                      gitHubClient.getAuthenticatedUsername(pollResult.accessToken)
                    }
                  }
                val updated =
                  _cloudSettings.value.copy(
                    accessToken = pollResult.accessToken,
                    githubUsername = username,
                    repoOwner = _cloudSettings.value.repoOwner.ifBlank { username },
                  )
                _cloudSettings.value = updated
                cloudPrefs.saveSettings(updated)
                _deviceAuthState.value = DeviceAuthState.Authenticated(username)
                _statusBanner.value = "Signed in as @$username — setting up workspace repo…"
                initializePrivateRepoAndWorkflow(autoTriggered = true)
                return@launch
              }
            }
          }
          _deviceAuthState.value = DeviceAuthState.Error("Device code expired (15:00 limit reached). Start login again.")
        } catch (t: Throwable) {
          if (t is CancellationException) throw t
          _deviceAuthState.value =
            DeviceAuthState.Error(
              NetworkResilience.formatUserFacingError(
                t = t,
                isOnline = NetworkResilience.isNetworkAvailable(getApplication()),
              )
            )
        }
      }
  }

  fun cancelDeviceFlowLogin() {
    devicePollJob?.cancel()
    val cur = _cloudSettings.value
    _deviceAuthState.value =
      if (cur.isAuthenticated) DeviceAuthState.Authenticated(cur.githubUsername)
      else DeviceAuthState.Idle
  }

  fun signOutGitHub() {
    devicePollJob?.cancel()
    repoSetupJob?.cancel()
    cloudRunPollJob?.cancel()
    CloudBuildMonitorScheduler.cancelMonitor(getApplication())
    CloudBuildNotifier.cancelLiveProgressNotification(getApplication())
    viewModelScope.launch {
      cloudPrefs.clearAuthSession()
      _deviceAuthState.value = DeviceAuthState.Idle
      _repoSetupState.value = RepoSetupState.NotConfigured
      _cloudRunState.value = CloudBuildRunState.Idle
      _recentCloudRuns.value = emptyList()
      _statusBanner.value = "Signed out of GitHub."
    }
  }

  fun verifyOrLoadRepoState() {
    val cfg = _cloudSettings.value
    if (!cfg.isAuthenticated) return
    viewModelScope.launch {
      try {
        val owner = cfg.effectiveOwner
        val repoInfo =
          NetworkResilience.retryTransient(maxAttempts = 2) {
            withContext(Dispatchers.IO) {
              gitHubClient.getRepository(cfg.accessToken, owner, cfg.repoName)
            }
          }
        if (repoInfo != null) {
          val wfSha =
            NetworkResilience.retryTransient(maxAttempts = 2) {
              withContext(Dispatchers.IO) {
                gitHubClient.getContentSha(
                  cfg.accessToken,
                  repoInfo.owner,
                  repoInfo.name,
                  Dex2cWorkflowGenerator.WORKFLOW_PATH,
                  repoInfo.defaultBranch,
                )
              }
            }
          val hasWf = !wfSha.isNullOrBlank()
          updateCloudSettings {
            it.copy(
              repoOwner = repoInfo.owner,
              defaultBranch = repoInfo.defaultBranch,
              workflowInitialized = hasWf,
            )
          }
          _repoSetupState.value =
            RepoSetupState.Ready(
              owner = repoInfo.owner,
              repo = repoInfo.name,
              htmlUrl = repoInfo.htmlUrl,
              isPrivate = repoInfo.isPrivate,
              hasWorkflow = hasWf,
            )
          if (hasWf) refreshCloudRuns()
        } else {
          _repoSetupState.value = RepoSetupState.NotConfigured
        }
      } catch (t: Throwable) {
        if (t is CancellationException) throw t
        _repoSetupState.value =
          RepoSetupState.Error(
            NetworkResilience.formatUserFacingError(
              t = t,
              isOnline = NetworkResilience.isNetworkAvailable(getApplication()),
            )
          )
      }
    }
  }

  private fun buildEffectiveCloudFilterText(transCfg: TranspilerConfig): String {
    val ready = _loadState.value as? DexLoadState.Ready
    val selectedIds = _selectedMethodIds.value
    val selectedMethods =
      if (ready != null && selectedIds.isNotEmpty()) {
        collectSelectedEntries(ready, selectedIds).map { it.second }
      } else {
        emptyList()
      }
    return FilterRuleEngine.buildCloudFilterText(transCfg, selectedMethods).trimEnd() + "\n"
  }

  fun initializePrivateRepoAndWorkflow(autoTriggered: Boolean = false) {
    val settings = _cloudSettings.value
    if (!settings.isAuthenticated) {
      _repoSetupState.value = RepoSetupState.Error("Sign in via GitHub Device Flow first.")
      return
    }
    val repoName = settings.repoName.trim().ifBlank { "dex2c-cloud-workspace" }
    repoSetupJob?.cancel()
    repoSetupJob =
      viewModelScope.launch {
        try {
          _repoSetupState.value =
            RepoSetupState.Working(
              if (autoTriggered) "Auto-initializing private repo \"$repoName\"…"
              else "Checking / creating private repo \"$repoName\"…"
            )
          val owner = settings.effectiveOwner
          val existing =
            NetworkResilience.retryTransient(maxAttempts = 3) {
              withContext(Dispatchers.IO) {
                gitHubClient.getRepository(settings.accessToken, owner, repoName)
              }
            }
          val repoInfo =
            existing
              ?: try {
                NetworkResilience.retryTransient(maxAttempts = 3) {
                  withContext(Dispatchers.IO) {
                    gitHubClient.createRepository(
                      token = settings.accessToken,
                      name = repoName,
                      isPrivate = settings.privateRepo,
                    )
                  }
                }
              } catch (createErr: Throwable) {
                if (createErr is CancellationException) throw createErr
                // Guard against race where repository was created concurrently (HTTP 422)
                val recovered =
                  runCatching {
                    withContext(Dispatchers.IO) {
                      gitHubClient.getRepository(settings.accessToken, owner, repoName)
                    }
                  }.getOrNull()
                recovered ?: throw createErr
              }

          val branch = repoInfo.defaultBranch.ifBlank { settings.defaultBranch.ifBlank { "main" } }
          _repoSetupState.value =
            RepoSetupState.Working("Syncing dex2c.yml, dcc.cfg & filter.txt…")

          val transCfg = _config.value
          val yaml = Dex2cWorkflowGenerator.generateDex2cYaml(settings, transCfg)
          val dccCfg = DccConfigCodec.encodeDccCfg(transCfg)
          val filterTxt = buildEffectiveCloudFilterText(transCfg)

          NetworkResilience.retryTransient(maxAttempts = 3) {
            withContext(Dispatchers.IO) {
              gitHubClient.upsertRepoFile(
                token = settings.accessToken,
                owner = repoInfo.owner,
                repo = repoInfo.name,
                path = Dex2cWorkflowGenerator.WORKFLOW_PATH,
                branch = branch,
                commitMessage = "Initialize Dex2C Studio cloud workflow (dex2c.yml)",
                contentBytes = yaml.toByteArray(Charsets.UTF_8),
              )
            }
          }
          NetworkResilience.retryTransient(maxAttempts = 3) {
            withContext(Dispatchers.IO) {
              gitHubClient.upsertRepoFile(
                token = settings.accessToken,
                owner = repoInfo.owner,
                repo = repoInfo.name,
                path = Dex2cWorkflowGenerator.DCC_CFG_PATH,
                branch = branch,
                commitMessage = "Sync dcc.cfg transpiler configuration",
                contentBytes = dccCfg.toByteArray(Charsets.UTF_8),
              )
            }
          }
          NetworkResilience.retryTransient(maxAttempts = 3) {
            withContext(Dispatchers.IO) {
              gitHubClient.upsertRepoFile(
                token = settings.accessToken,
                owner = repoInfo.owner,
                repo = repoInfo.name,
                path = Dex2cWorkflowGenerator.FILTER_TXT_PATH,
                branch = branch,
                commitMessage = "Sync filter.txt rules",
                contentBytes = filterTxt.toByteArray(Charsets.UTF_8),
              )
            }
          }

          val savedSettings =
            _cloudSettings.value.copy(
              repoOwner = repoInfo.owner,
              repoName = repoInfo.name,
              defaultBranch = branch,
              privateRepo = repoInfo.isPrivate,
              workflowInitialized = true,
            )
          _cloudSettings.value = savedSettings
          cloudPrefs.saveSettings(savedSettings)

          _repoSetupState.value =
            RepoSetupState.Ready(
              owner = repoInfo.owner,
              repo = repoInfo.name,
              htmlUrl = repoInfo.htmlUrl,
              isPrivate = repoInfo.isPrivate,
              hasWorkflow = true,
            )
          _statusBanner.value =
            if (autoTriggered) "GitHub connected & ${repoInfo.fullName} initialized!"
            else "Initialized ${repoInfo.fullName} with dex2c.yml!"
          refreshCloudRuns()
        } catch (t: Throwable) {
          if (t is CancellationException) throw t
          _repoSetupState.value =
            RepoSetupState.Error(
              NetworkResilience.formatUserFacingError(
                t = t,
                isOnline = NetworkResilience.isNetworkAvailable(getApplication()),
              )
            )
        }
      }
  }

  fun dispatchCloudDex2cBuild() {
    val settings = _cloudSettings.value
    if (!settings.isAuthenticated) {
      _cloudRunState.value = CloudBuildRunState.Error("Sign in via GitHub Device Flow first.")
      return
    }
    if (!settings.workflowInitialized) {
      _cloudRunState.value = CloudBuildRunState.Error("Initialize your private repo and dex2c.yml first.")
      return
    }

    val urlFallback = _cloudApkUrl.value.trim()
    val apkChoice = _cloudApkChoice.value
    if (urlFallback.isEmpty() && apkChoice == null) {
      _cloudRunState.value =
        CloudBuildRunState.Error("Pick an APK file or provide an APK URL fallback first.")
      return
    }

    val context = getApplication<Application>()
    cloudRunPollJob?.cancel()
    cloudRunPollJob =
      viewModelScope.launch {
        try {
          val owner = settings.effectiveOwner
          val repo = settings.repoName
          val branch = settings.defaultBranch.ifBlank { "main" }
          val transCfg = _config.value

          fun postLiveUploadNotification(
            stageLabel: String,
            fraction: Float?,
            uploaded: Long = 0L,
            total: Long = 0L,
          ) {
            postLiveProgressNotification(
              CloudBuildNotifier.buildUploadProgressNotification(
                stageLabel = stageLabel,
                bytesUploaded = uploaded,
                totalBytes = total,
                progressFraction = fraction,
              )
            )
          }

          _cloudRunState.value =
            CloudBuildRunState.UploadingInputs(
              message = "Auto-syncing dex2c.yml (1/3)…",
              progressFraction = 0.05f,
            )
          postLiveUploadNotification("Syncing dex2c.yml…", 0.05f)
          NetworkResilience.retryTransient(maxAttempts = 3) {
            withContext(Dispatchers.IO) {
              gitHubClient.upsertRepoFile(
                token = settings.accessToken,
                owner = owner,
                repo = repo,
                path = Dex2cWorkflowGenerator.WORKFLOW_PATH,
                branch = branch,
                commitMessage = "Update dex2c.yml workflow parameters",
                contentBytes = Dex2cWorkflowGenerator.generateDex2cYaml(settings, transCfg).toByteArray(Charsets.UTF_8),
              )
            }
          }

          _cloudRunState.value =
            CloudBuildRunState.UploadingInputs(
              message = "Auto-syncing dcc.cfg (2/3)…",
              progressFraction = 0.12f,
            )
          postLiveUploadNotification("Syncing dcc.cfg…", 0.12f)
          NetworkResilience.retryTransient(maxAttempts = 3) {
            withContext(Dispatchers.IO) {
              gitHubClient.upsertRepoFile(
                token = settings.accessToken,
                owner = owner,
                repo = repo,
                path = Dex2cWorkflowGenerator.DCC_CFG_PATH,
                branch = branch,
                commitMessage = "Update dcc.cfg for cloud run",
                contentBytes = DccConfigCodec.encodeDccCfg(transCfg).toByteArray(Charsets.UTF_8),
              )
            }
          }

          _cloudRunState.value =
            CloudBuildRunState.UploadingInputs(
              message = "Auto-syncing filter.txt (3/3)…",
              progressFraction = 0.20f,
            )
          postLiveUploadNotification("Syncing filter.txt…", 0.20f)
          val effectiveFilterTxt = buildEffectiveCloudFilterText(transCfg)
          NetworkResilience.retryTransient(maxAttempts = 3) {
            withContext(Dispatchers.IO) {
              gitHubClient.upsertRepoFile(
                token = settings.accessToken,
                owner = owner,
                repo = repo,
                path = Dex2cWorkflowGenerator.FILTER_TXT_PATH,
                branch = branch,
                commitMessage = "Update filter.txt for cloud run",
                contentBytes = effectiveFilterTxt.toByteArray(Charsets.UTF_8),
              )
            }
          }

          if (urlFallback.isEmpty() && apkChoice != null) {
            val apkBytes =
              withContext(Dispatchers.IO) {
                when {
                  apkChoice.uri != null ->
                    context.contentResolver.openInputStream(apkChoice.uri)?.use { it.readBytes() }
                      ?: throw IllegalArgumentException("Unable to read selected APK URI.")
                  apkChoice.filePath != null -> File(apkChoice.filePath).readBytes()
                  else -> throw IllegalArgumentException("No APK source readable.")
                }
              }
            val limitBytes = settings.maxDirectUploadMb.coerceIn(5, 95) * 1024 * 1024
            if (apkBytes.size > limitBytes) {
              CloudBuildNotifier.cancelLiveProgressNotification(getApplication())
              _cloudRunState.value =
                CloudBuildRunState.Error(
                  "APK (${apkBytes.size / (1024 * 1024)} MB) exceeds ${settings.maxDirectUploadMb} MB direct commit limit. Paste a direct APK URL below instead."
                )
              return@launch
            }
            val apkTotalBytes = apkBytes.size.toLong()
            _cloudRunState.value =
              CloudBuildRunState.UploadingInputs(
                message = "Uploading ${apkChoice.label}…",
                progressFraction = 0.22f,
                bytesUploaded = 0L,
                totalBytes = apkTotalBytes,
              )
            postLiveUploadNotification("Uploading ${apkChoice.label}", 0.22f, 0L, apkTotalBytes)

            var lastNotifyAtMs = 0L
            NetworkResilience.retryTransient(maxAttempts = 3) {
              withContext(Dispatchers.IO) {
                gitHubClient.upsertRepoFile(
                  token = settings.accessToken,
                  owner = owner,
                  repo = repo,
                  path = Dex2cWorkflowGenerator.DEFAULT_APK_REPO_PATH,
                  branch = branch,
                  commitMessage = "Upload target APK (${apkChoice.label}) for Dex2C build",
                  contentBytes = apkBytes,
                  onUploadProgress = { written, wireTotal ->
                    val wireRatio =
                      if (wireTotal > 0L) (written.toFloat() / wireTotal.toFloat()).coerceIn(0f, 1f)
                      else 0f
                    val displayUploaded = (wireRatio * apkTotalBytes).toLong().coerceIn(0L, apkTotalBytes)
                    val overallFraction = (0.22f + wireRatio * 0.74f).coerceIn(0.22f, 0.96f)
                    _cloudRunState.value =
                      CloudBuildRunState.UploadingInputs(
                        message = "Uploading ${apkChoice.label}…",
                        progressFraction = overallFraction,
                        bytesUploaded = displayUploaded,
                        totalBytes = apkTotalBytes,
                      )
                    val now = System.currentTimeMillis()
                    if (written >= wireTotal || now - lastNotifyAtMs >= 250L) {
                      lastNotifyAtMs = now
                      postLiveUploadNotification(
                        stageLabel = "Uploading ${apkChoice.label}",
                        fraction = overallFraction,
                        uploaded = displayUploaded,
                        total = apkTotalBytes,
                      )
                    }
                  },
                )
              }
            }
          }

          val prevTopRunId =
            withContext(Dispatchers.IO) {
              runCatching { gitHubClient.listWorkflowRuns(settings.accessToken, owner, repo).firstOrNull()?.id }
                .getOrNull()
            }

          _cloudRunState.value = CloudBuildRunState.Dispatching
          postLiveUploadNotification("Triggering dex2c.yml workflow…", 0.98f)
          NetworkResilience.retryTransient(maxAttempts = 3) {
            withContext(Dispatchers.IO) {
              gitHubClient.dispatchWorkflow(
                token = settings.accessToken,
                owner = owner,
                repo = repo,
                ref = branch,
                inputs =
                  mapOf(
                    "apk_path" to Dex2cWorkflowGenerator.DEFAULT_APK_REPO_PATH,
                    "apk_url" to urlFallback,
                    "allow_init" to transCfg.allowInit.toString(),
                    "skip_synthetic" to transCfg.skipSynthetic.toString(),
                    "custom_loader" to transCfg.customLoaderClass.ifBlank { "amimo.dcc.DccApplication" },
                    "force_custom_loader" to transCfg.forceCustomLoader.toString(),
                    "obfuscate" to (transCfg.stringEncryption || transCfg.controlFlowFlattening).toString(),
                    "dynamic_register" to (transCfg.jniStyle == com.dex2c.studio.transpiler.JniBindingStyle.REGISTER_NATIVES).toString(),
                    "signing_mode" to settings.signingMode.name,
                  ),
              )
            }
          }

          // Wait briefly for GitHub to register the new workflow run, tolerating transient DNS/IO blips
          var activeRun: GitHubWorkflowRunSummary? = null
          var lastDiscoveryError: Throwable? = null
          repeat(8) {
            delay(2500L)
            val runsResult =
              runCatching {
                withContext(Dispatchers.IO) {
                  gitHubClient.listWorkflowRuns(settings.accessToken, owner, repo)
                }
              }
            runsResult.onSuccess { runs ->
              lastDiscoveryError = null
              _recentCloudRuns.value = runs
              val candidate = runs.firstOrNull { it.id != prevTopRunId } ?: runs.firstOrNull()
              if (candidate != null && (prevTopRunId == null || candidate.id != prevTopRunId)) {
                activeRun = candidate
              }
            }.onFailure { err ->
              if (err is CancellationException) throw err
              lastDiscoveryError = err
              if (!NetworkResilience.isTransientNetworkError(err)) throw err
            }
            if (activeRun != null) return@repeat
          }

          val initialRun = activeRun ?: _recentCloudRuns.value.firstOrNull()
          if (initialRun == null) {
            if (_cloudSettings.value.notificationsEnabled) {
              CloudBuildMonitorScheduler.scheduleMonitor(getApplication(), -1L, initialDelaySec = 10L)
            }
            val msg =
              lastDiscoveryError?.let {
                NetworkResilience.formatUserFacingError(it, NetworkResilience.isNetworkAvailable(getApplication()))
              } ?: "Workflow dispatched; tap Retry or refresh runs shortly to track progress."
            _cloudRunState.value =
              CloudBuildRunState.Error(
                message = msg,
                retryAction = CloudErrorAction.RETRY_POLL,
              )
            return@launch
          }
          if (_cloudSettings.value.notificationsEnabled) {
            CloudBuildMonitorScheduler.scheduleMonitor(getApplication(), initialRun.id)
          }
          pollWorkflowRunUntilDone(settings, owner, repo, initialRun.id, notifyIfCompleted = true)
        } catch (t: Throwable) {
          if (t is CancellationException) throw t
          CloudBuildNotifier.cancelLiveProgressNotification(getApplication())
          _cloudRunState.value =
            CloudBuildRunState.Error(
              message =
                NetworkResilience.formatUserFacingError(
                  t = t,
                  isOnline = NetworkResilience.isNetworkAvailable(getApplication()),
                ),
              retryAction = CloudErrorAction.RETRY_DISPATCH,
            )
        }
      }
  }

  fun attachToCloudRun(runId: Long) {
    val settings = _cloudSettings.value
    if (!settings.isRepoConfigured) return
    cloudRunPollJob?.cancel()
    cloudRunPollJob =
      viewModelScope.launch {
        pollWorkflowRunUntilDone(
          settings = settings,
          owner = settings.effectiveOwner,
          repo = settings.repoName,
          runId = runId,
          notifyIfCompleted = false,
        )
      }
  }

  fun toggleAutoPoll() {
    val next = !_isAutoPollEnabled.value
    _isAutoPollEnabled.value = next
    if (!next) {
      cloudRunPollJob?.cancel()
    } else {
      val polling = _cloudRunState.value as? CloudBuildRunState.Polling
      val errState = _cloudRunState.value as? CloudBuildRunState.Error
      val runId =
        polling?.runId
          ?: errState?.failedRunId
          ?: _recentCloudRuns.value.firstOrNull { it.status != "completed" }?.id
      if (runId != null) {
        attachToCloudRun(runId)
      }
    }
  }

  fun retryCloudMonitorError() {
    val err = _cloudRunState.value as? CloudBuildRunState.Error
    when {
      err?.retryAction == CloudErrorAction.RETRY_DISPATCH -> dispatchCloudDex2cBuild()
      err?.failedRunId != null -> attachToCloudRun(err.failedRunId)
      else -> refreshActiveCloudRun()
    }
  }

  fun refreshActiveCloudRun() {
    val settings = _cloudSettings.value
    if (!settings.isRepoConfigured) return
    val currentState = _cloudRunState.value
    if (currentState is CloudBuildRunState.Error && currentState.retryAction == CloudErrorAction.RETRY_DISPATCH) {
      dispatchCloudDex2cBuild()
      return
    }
    val activeRunId =
      when (currentState) {
        is CloudBuildRunState.Polling -> currentState.runId
        is CloudBuildRunState.Completed -> currentState.runId
        is CloudBuildRunState.Error -> currentState.failedRunId
        else -> null
      }
    val wasPolling = currentState is CloudBuildRunState.Polling ||
      (currentState is CloudBuildRunState.Error && currentState.retryAction == CloudErrorAction.RETRY_POLL)
    viewModelScope.launch {
      runCatching {
        NetworkResilience.retryTransient(maxAttempts = 2) {
          fetchAndApplyRunSnapshot(
            settings = settings,
            owner = settings.effectiveOwner,
            repo = settings.repoName,
            targetRunId = activeRunId,
            notifyOnCompletion = wasPolling,
          )
        }
      }.onFailure { t ->
        if (t is CancellationException) throw t
        _statusBanner.value =
          NetworkResilience.formatUserFacingError(
            t = t,
            isOnline = NetworkResilience.isNetworkAvailable(getApplication()),
          )
      }
    }
  }

  private suspend fun pollWorkflowRunUntilDone(
    settings: CloudSettings,
    owner: String,
    repo: String,
    runId: Long,
    notifyIfCompleted: Boolean,
  ) {
    val pollMs = settings.pollIntervalSeconds.coerceIn(4, 30) * 1000L
    var shouldNotifyWhenDone = notifyIfCompleted
    var consecutiveTransientFailures = 0

    while (true) {
      val pollSucceeded =
        try {
          val isDone =
            fetchAndApplyRunSnapshot(
              settings = settings,
              owner = owner,
              repo = repo,
              targetRunId = runId,
              notifyOnCompletion = shouldNotifyWhenDone,
            )
          consecutiveTransientFailures = 0
          if (isDone) return
          true
        } catch (t: Throwable) {
          if (t is CancellationException) throw t
          if (NetworkResilience.isTransientNetworkError(t) && consecutiveTransientFailures < 12) {
            consecutiveTransientFailures++
            val curPolling = _cloudRunState.value as? CloudBuildRunState.Polling
            if (curPolling != null) {
              val note =
                if (NetworkResilience.isNetworkAvailable(getApplication())) {
                  "Temporary DNS/network issue — retrying automatically (attempt $consecutiveTransientFailures)…"
                } else {
                  "Waiting for internet connection — retrying automatically (attempt $consecutiveTransientFailures)…"
                }
              _cloudRunState.value = curPolling.copy(reconnectingNote = note)
            }
            false
          } else {
            _cloudRunState.value =
              CloudBuildRunState.Error(
                message =
                  NetworkResilience.formatUserFacingError(
                    t = t,
                    isOnline = NetworkResilience.isNetworkAvailable(getApplication()),
                  ),
                retryAction = CloudErrorAction.RETRY_POLL,
                failedRunId = runId,
              )
            return
          }
        }

      if (pollSucceeded) {
        // Once observed active in-progress/queued, schedule background monitor & allow completion alert
        shouldNotifyWhenDone = true
        if (_cloudSettings.value.notificationsEnabled) {
          CloudBuildMonitorScheduler.scheduleMonitor(getApplication(), runId)
        }
      }
      if (!_isAutoPollEnabled.value) return
      val waitMs =
        if (consecutiveTransientFailures > 0) {
          (pollMs * (1L shl (consecutiveTransientFailures - 1).coerceAtMost(2))).coerceAtMost(30_000L)
        } else {
          pollMs
        }
      delay(waitMs)
    }
  }

  private suspend fun fetchAndApplyRunSnapshot(
    settings: CloudSettings,
    owner: String,
    repo: String,
    targetRunId: Long?,
    notifyOnCompletion: Boolean,
  ): Boolean {
    val runs =
      withContext(Dispatchers.IO) {
        gitHubClient.listWorkflowRuns(settings.accessToken, owner, repo)
      }
    _recentCloudRuns.value = runs
    val run =
      if (targetRunId != null) runs.firstOrNull { it.id == targetRunId } ?: runs.firstOrNull()
      else runs.firstOrNull()
    if (run == null) return false

    val job =
      withContext(Dispatchers.IO) {
        gitHubClient.getRunJobTelemetry(settings.accessToken, owner, repo, run.id)
      }
    val startedMs = job.startedAtMillis ?: run.runStartedAtMillis
    val nowMs = System.currentTimeMillis()

    return if (run.status == "completed") {
      val artifacts =
        withContext(Dispatchers.IO) {
          gitHubClient.listRunArtifacts(settings.accessToken, owner, repo, run.id)
        }
      val completedMs = job.completedAtMillis ?: run.updatedAtMillis ?: nowMs
      val conclusion = run.conclusion ?: "unknown"
      _cloudRunState.value =
        CloudBuildRunState.Completed(
          runId = run.id,
          runNumber = run.runNumber,
          conclusion = conclusion,
          htmlUrl = run.htmlUrl,
          artifacts = artifacts,
          steps = job.steps,
          startedAtMillis = startedMs,
          completedAtMillis = completedMs,
        )
      CloudBuildMonitorScheduler.cancelMonitor(getApplication())
      if (notifyOnCompletion && _cloudSettings.value.notificationsEnabled) {
        val durationMs = startedMs?.let { (completedMs - it).coerceAtLeast(0L) }
        CloudBuildNotifier.notifyBuildOutcomeIfNeeded(
          context = getApplication(),
          runId = run.id,
          runNumber = run.runNumber,
          conclusion = conclusion,
          durationMs = durationMs,
          hasArtifacts = artifacts.any { !it.expired },
          appSettings = _appSettings.value,
        )
      } else {
        CloudBuildNotifier.cancelLiveProgressNotification(getApplication())
      }
      true
    } else {
      _cloudRunState.value =
        CloudBuildRunState.Polling(
          runId = run.id,
          runNumber = run.runNumber,
          jobName = job.jobName,
          status = run.status,
          conclusion = run.conclusion,
          htmlUrl = run.htmlUrl,
          steps = job.steps,
          startedAtMillis = startedMs,
          lastPolledAtMillis = nowMs,
        )
      val doneSteps = job.steps.count { it.status == "completed" }
      val activeStep =
        job.steps.firstOrNull { it.status == "in_progress" }?.name
          ?: job.steps.lastOrNull { it.status == "completed" }?.name
      postLiveProgressNotification(
        content =
          CloudBuildNotifier.buildWorkflowProgressNotification(
            runNumber = run.runNumber,
            status = run.status,
            activeStepName = activeStep,
            completedSteps = doneSteps,
            totalSteps = job.steps.size,
          ),
        runId = run.id,
      )
      false
    }
  }

  fun refreshCloudRuns() {
    val settings = _cloudSettings.value
    if (!settings.isRepoConfigured) return
    viewModelScope.launch {
      runCatching {
        val runs =
          withContext(Dispatchers.IO) {
            gitHubClient.listWorkflowRuns(settings.accessToken, settings.effectiveOwner, settings.repoName)
          }
        _recentCloudRuns.value = runs
        val active = runs.firstOrNull { it.status != "completed" }
        if (active != null && _cloudRunState.value is CloudBuildRunState.Idle && _isAutoPollEnabled.value) {
          attachToCloudRun(active.id)
        }
      }
    }
  }

  fun downloadArtifactAndPromptSave(artifact: GitHubArtifactItem) {
    val settings = _cloudSettings.value
    if (!settings.isAuthenticated) return
    val prevState = _cloudRunState.value
    val completedSnapshot = prevState as? CloudBuildRunState.Completed
    val expectedBytes = artifact.sizeInBytes
    viewModelScope.launch {
      try {
        val initialFraction = if (expectedBytes > 0L) 0f else null
        _cloudRunState.value =
          CloudBuildRunState.SavingArtifact(
            label = "Downloading ${artifact.name}…",
            bytesDownloaded = 0L,
            totalBytes = expectedBytes,
            progressFraction = initialFraction,
            completedSnapshot = completedSnapshot,
          )
        postLiveProgressNotification(
          content =
            CloudBuildNotifier.buildDownloadProgressNotification(
              artifactName = artifact.name,
              bytesDownloaded = 0L,
              totalBytes = expectedBytes,
              progressFraction = initialFraction,
            ),
          runId = completedSnapshot?.runId,
        )

        var lastNotifyAtMs = 0L
        val extracted =
          withContext(Dispatchers.IO) {
            val zipBytes =
              gitHubClient.downloadArtifactZip(
                token = settings.accessToken,
                archiveDownloadUrl = artifact.archiveDownloadUrl,
                expectedTotalBytes = expectedBytes,
                onDownloadProgress = { read, total ->
                  val effectiveTotal = if (total > 0L) total else expectedBytes
                  val fraction =
                    if (effectiveTotal > 0L) (read.toFloat() / effectiveTotal.toFloat()).coerceIn(0f, 1f)
                    else null
                  _cloudRunState.value =
                    CloudBuildRunState.SavingArtifact(
                      label = "Downloading ${artifact.name}…",
                      bytesDownloaded = read,
                      totalBytes = effectiveTotal,
                      progressFraction = fraction,
                      completedSnapshot = completedSnapshot,
                    )
                  val now = System.currentTimeMillis()
                  if ((effectiveTotal > 0L && read >= effectiveTotal) || now - lastNotifyAtMs >= 250L) {
                    lastNotifyAtMs = now
                    postLiveProgressNotification(
                      content =
                        CloudBuildNotifier.buildDownloadProgressNotification(
                          artifactName = artifact.name,
                          bytesDownloaded = read,
                          totalBytes = effectiveTotal,
                          progressFraction = fraction,
                        ),
                      runId = completedSnapshot?.runId,
                    )
                  }
                },
              )
            gitHubClient.extractApkFromArtifactZip(zipBytes)
          }
        _cloudRunState.value = prevState
        _pendingCloudApkSave.value = extracted
        CloudBuildNotifier.notifyDownloadCompleted(
          context = getApplication(),
          fileName = extracted.fileName,
          isError = false,
          notificationsEnabled = _cloudSettings.value.notificationsEnabled,
          bodyOverride = "Downloaded ${extracted.fileName} (${CloudTimeUtil.formatTransferBytes(extracted.bytes.size.toLong())}). Choose save location.",
          appSettings = _appSettings.value,
        )
      } catch (t: Throwable) {
        if (t is CancellationException) throw t
        _cloudRunState.value = prevState
        _statusBanner.value = "Artifact download failed: ${t.message}"
        CloudBuildNotifier.notifyDownloadCompleted(
          context = getApplication(),
          fileName = artifact.name,
          isError = true,
          notificationsEnabled = _cloudSettings.value.notificationsEnabled,
          appSettings = _appSettings.value,
        )
      }
    }
  }

  fun savePendingCloudApkToUri(uri: Uri) {
    val pending = _pendingCloudApkSave.value ?: return
    val context = getApplication<Application>()
    viewModelScope.launch {
      try {
        withContext(Dispatchers.IO) {
          context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(pending.bytes)
          } ?: throw IllegalStateException("Unable to open destination file.")
        }
        _pendingCloudApkSave.value = null
        _statusBanner.value = "Saved ${pending.fileName}!"
        CloudBuildNotifier.notifyDownloadCompleted(
          context = context,
          fileName = pending.fileName,
          isError = false,
          notificationsEnabled = _cloudSettings.value.notificationsEnabled,
          bodyOverride = "Saved ${pending.fileName} (${CloudTimeUtil.formatTransferBytes(pending.bytes.size.toLong())}) to device.",
          appSettings = _appSettings.value,
        )
      } catch (t: Throwable) {
        _statusBanner.value = "Save failed: ${t.message}"
      }
    }
  }

  fun clearPendingCloudApkSave() {
    _pendingCloudApkSave.value = null
  }

  private fun postLiveProgressNotification(
    content: CloudLiveNotificationContent,
    runId: Long? = null,
  ) {
    if (!_cloudSettings.value.notificationsEnabled) return
    CloudBuildNotifier.updateLiveProgressNotification(
      context = getApplication(),
      content = content,
      runId = runId,
      notificationsEnabled = true,
      appSettings = _appSettings.value,
    )
  }

  private fun queryDisplayName(uri: Uri): String? {
    val context = getApplication<Application>()
    return runCatching {
      context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
      }
    }.getOrNull()
  }
}
