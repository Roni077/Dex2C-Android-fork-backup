package com.example.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.apk.ApkMetadataInspector
import com.example.apk.ApkStagingPreview
import com.example.apk.BinarySourceType
import com.example.apk.InstalledAppItem
import com.example.apk.InstalledAppRepository
import com.example.apk.PickerTab
import com.example.apk.RecentBinaryItem
import com.example.cloud.CloudBuildMonitorScheduler
import com.example.cloud.CloudBuildNotifier
import com.example.cloud.CloudBuildRunState
import com.example.cloud.CloudDownloadedApk
import com.example.cloud.CloudSettings
import com.example.cloud.Dex2cWorkflowGenerator
import com.example.cloud.DeviceAuthState
import com.example.cloud.GitHubApiClient
import com.example.cloud.GitHubArtifactItem
import com.example.cloud.GitHubWorkflowRunSummary
import com.example.cloud.RepoSetupState
import com.example.cloud.TokenPollResult
import com.example.data.AppPreferences
import com.example.data.AppSettings
import com.example.data.CloudPreferences
import com.example.data.RecentFilesStore
import com.example.data.TranspilerPreferences
import com.example.dex.ApkDexEntry
import com.example.dex.DalvikDisassembler
import com.example.dex.DalvikInstruction
import com.example.dex.DexClassInfo
import com.example.dex.DexEncodedMethod
import com.example.dex.DexHelper
import com.example.dex.DexMethodStructuralMeta
import com.example.dex.DexParsedFile
import com.example.dex.DexParser
import com.example.dex.DexTryBlock
import com.example.dex.RustJniScaffolding
import java.io.BufferedOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import com.example.transpiler.DccConfigCodec
import com.example.transpiler.Dex2cTranspiler
import com.example.transpiler.FilterMatchSummary
import com.example.transpiler.FilterRuleEngine
import com.example.transpiler.GeneratedBundle
import com.example.transpiler.TranspilerConfig
import com.example.transpiler.ZipBundleExporter
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
  ) : DexLoadState
  data class Error(val message: String) : DexLoadState
}

sealed interface ApkStagingState {
  data class Loading(val label: String) : ApkStagingState
  data class Ready(val preview: ApkStagingPreview, val selectedEntryName: String) : ApkStagingState
  data class Error(val message: String) : ApkStagingState
}

data class MethodInspectionState(
  val method: DexEncodedMethod,
  val dexClass: DexClassInfo,
  val instructions: List<DalvikInstruction>,
  val tryBlocks: List<DexTryBlock>,
  val transpiledC: String,
  val structuralMeta: DexMethodStructuralMeta,
)

data class ClassFilterState(
  val searchQuery: String = "",
  val hideFramework: Boolean = true,
  val onlyWithBytecode: Boolean = true,
  val onlySelected: Boolean = false,
)

data class CloudApkChoice(
  val label: String,
  val byteSize: Long,
  val uri: Uri? = null,
  val filePath: String? = null,
)

class Dex2cViewModel(application: Application) : AndroidViewModel(application) {

  private val prefs = TranspilerPreferences(application)
  private val appPrefs = AppPreferences(application)
  private val cloudPrefs = CloudPreferences(application)
  private val recentStore = RecentFilesStore(application)
  private val gitHubClient = GitHubApiClient()

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
      val q = query.trim().lowercase()
      if (q.isEmpty()) apps
      else apps.filter { it.appLabel.lowercase().contains(q) || it.packageName.lowercase().contains(q) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  val recentFiles: StateFlow<List<RecentBinaryItem>> =
    recentStore.recentItemsFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  private val _stagingState = MutableStateFlow<ApkStagingState?>(null)
  val stagingState: StateFlow<ApkStagingState?> = _stagingState.asStateFlow()

  private val _isRustBridgeSheetOpen = MutableStateFlow(false)
  val isRustBridgeSheetOpen: StateFlow<Boolean> = _isRustBridgeSheetOpen.asStateFlow()

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

  private var dexByteCache: Map<String, ByteArray> = emptyMap()
  private var currentSourceLabel: String = ""
  private var bundleJob: Job? = null
  private var devicePollJob: Job? = null
  private var cloudRunPollJob: Job? = null

  val filteredClasses: StateFlow<List<DexClassInfo>> =
    combine(_loadState, _filterState, _selectedMethodIds) { load, filter, selected ->
      val ready = load as? DexLoadState.Ready ?: return@combine emptyList()
      val query = filter.searchQuery.trim().lowercase()
      ready.parsedDex.classes.filter { cls ->
        if (filter.hideFramework && cls.isFrameworkClass) return@filter false
        if (filter.onlyWithBytecode && cls.bytecodeMethodCount == 0) return@filter false
        if (filter.onlySelected && cls.allMethods.none { it.id in selected }) return@filter false
        if (query.isNotEmpty()) {
          val matchesClass = cls.fullName.lowercase().contains(query) || cls.descriptor.lowercase().contains(query)
          val matchesMethod = cls.allMethods.any { it.methodRef.name.lowercase().contains(query) }
          if (!matchesClass && !matchesMethod) return@filter false
        }
        true
      }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  val filterMatchSummary: StateFlow<FilterMatchSummary?> =
    combine(_loadState, _config) { load, cfg ->
      val ready = load as? DexLoadState.Ready ?: return@combine null
      withContext(Dispatchers.Default) { FilterRuleEngine.evaluate(ready.parsedDex, cfg) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

  private var hasInitializedFilterDefaults = false

  init {
    viewModelScope.launch {
      appPrefs.settingsFlow.collect { savedApp ->
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
    viewModelScope.launch {
      prefs.configFlow.collect { saved ->
        _config.value = saved
        refreshInspectionIfActive()
        rebuildBundle()
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
    val context = getApplication<Application>()
    // Try taking persistable read permission so recent history can reopen this file later
    runCatching {
      context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val name = queryDisplayName(uri) ?: "selected_binary.apk"
    _isPickerOpen.value = false
    _stagingState.value = ApkStagingState.Loading(name)

    viewModelScope.launch {
      try {
        val preview =
          withContext(Dispatchers.IO) {
            ApkMetadataInspector.inspectFromUri(context, uri, name)
          }
        recordRecent(preview)
        _stagingState.value = ApkStagingState.Ready(preview, preview.dexEntries.first().entryName)
      } catch (t: Throwable) {
        _stagingState.value = ApkStagingState.Error(t.message ?: "Failed to inspect selected file.")
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
        _stagingState.value = ApkStagingState.Ready(preview, preview.dexEntries.first().entryName)
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
        _stagingState.value = ApkStagingState.Ready(preview, preview.dexEntries.first().entryName)
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
        _stagingState.value = ApkStagingState.Ready(preview, preview.dexEntries.first().entryName)
      } catch (t: Throwable) {
        _stagingState.value = ApkStagingState.Error(t.message ?: "Unable to read live APK.")
      }
    }
  }

  fun selectStagedDexEntry(entryName: String) {
    val ready = _stagingState.value as? ApkStagingState.Ready ?: return
    _stagingState.value = ready.copy(selectedEntryName = entryName)
  }

  fun commitStagedDex() {
    val ready = _stagingState.value as? ApkStagingState.Ready ?: return
    val preview = ready.preview
    val chosenEntry = ready.selectedEntryName
    val dexBytes = preview.dexByteMap[chosenEntry] ?: return

    // Automatically track the selected APK for Cloud Build dispatch too
    if (preview.sourceLabel.endsWith(".apk", ignoreCase = true) || preview.sourceType == BinarySourceType.INSTALLED_APK) {
      _cloudApkChoice.value =
        CloudApkChoice(
          label = preview.appLabel?.takeIf { it.isNotBlank() } ?: preview.sourceLabel,
          byteSize = preview.totalByteSize,
          uri = if (preview.sourceType == BinarySourceType.SAF_URI) Uri.parse(preview.sourceRef) else null,
          filePath = if (preview.sourceType == BinarySourceType.INSTALLED_APK) preview.sourceRef else null,
        )
    }

    _stagingState.value = null
    _loadState.value = DexLoadState.Loading("${preview.appLabel ?: preview.sourceLabel} ($chosenEntry)")

    viewModelScope.launch {
      try {
        val label = preview.appLabel?.takeIf { it.isNotBlank() } ?: preview.sourceLabel
        val parsed =
          withContext(Dispatchers.Default) {
            DexParser.parseDex(
              rawBytes = dexBytes,
              sourceLabel = label,
              entryName = chosenEntry,
              preferNativeBridge = _appSettings.value.preferRustJniBridge,
            )
          }
        applyParsedDex(label, preview.dexEntries, preview.dexByteMap, parsed)
      } catch (t: Throwable) {
        _loadState.value = DexLoadState.Error(t.message ?: "Failed to parse $chosenEntry.")
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
    val bytes = dexByteCache[entryName] ?: return
    val currentEntries = (_loadState.value as? DexLoadState.Ready)?.dexEntries ?: return
    viewModelScope.launch {
      _loadState.value = DexLoadState.Loading(entryName)
      try {
        val parsed =
          withContext(Dispatchers.Default) {
            DexParser.parseDex(
              rawBytes = bytes,
              sourceLabel = currentSourceLabel,
              entryName = entryName,
              preferNativeBridge = _appSettings.value.preferRustJniBridge,
            )
          }
        _selectedMethodIds.value = emptySet()
        _inspection.value = null
        _generatedBundle.value = null
        _loadState.value = DexLoadState.Ready(currentSourceLabel, currentEntries, entryName, parsed)
      } catch (t: Throwable) {
        _loadState.value = DexLoadState.Error(t.message ?: "Failed to parse $entryName.")
      }
    }
  }

  private fun applyParsedDex(
    label: String,
    entries: List<ApkDexEntry>,
    byteMap: Map<String, ByteArray>,
    parsed: DexParsedFile,
  ) {
    currentSourceLabel = label
    dexByteCache = byteMap
    _selectedMethodIds.value = emptySet()
    _inspection.value = null
    _generatedBundle.value = null
    _loadState.value = DexLoadState.Ready(label, entries, parsed.entryName, parsed)
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

  fun toggleMethodSelection(methodId: String) {
    _selectedMethodIds.update { current ->
      if (methodId in current) current - methodId else current + methodId
    }
    rebuildBundle()
  }

  fun toggleClassSelection(dexClass: DexClassInfo) {
    val allowInit = _config.value.allowInit
    val bytecodeIds =
      dexClass.allMethods
        .filter { it.hasBytecode && (allowInit || !it.isInitConstructor) }
        .map { it.id }
    val targetIds = bytecodeIds.ifEmpty { dexClass.allMethods.map { it.id } }
    _selectedMethodIds.update { current ->
      val allSelected = targetIds.isNotEmpty() && targetIds.all { it in current }
      if (allSelected) current - targetIds.toSet() else current + targetIds
    }
    rebuildBundle()
  }

  fun autoSelectAppMethods() {
    val ready = _loadState.value as? DexLoadState.Ready ?: return
    val allowInit = _config.value.allowInit
    val limit = _appSettings.value.autoSelectMethodLimit.coerceIn(8, 256)
    val appMethods =
      ready.parsedDex.classes
        .filter { !it.isFrameworkClass }
        .flatMap { it.allMethods }
        .filter { it.hasBytecode && (allowInit || !it.isInitConstructor) }
        .take(limit)
        .map { it.id }
        .toSet()
    val fallback =
      if (appMethods.isNotEmpty()) {
        appMethods
      } else {
        ready.parsedDex.classes
          .flatMap { it.allMethods }
          .filter { it.hasBytecode && (allowInit || !it.isInitConstructor) }
          .take((limit / 2).coerceAtLeast(8))
          .map { it.id }
          .toSet()
      }
    _selectedMethodIds.value = fallback
    _statusBanner.value = "Selected ${fallback.size} bytecode methods for C transpilation."
    rebuildBundle()
  }

  fun applyFilterRules() {
    val ready = _loadState.value as? DexLoadState.Ready ?: return
    val cfg = _config.value
    viewModelScope.launch {
      val summary = withContext(Dispatchers.Default) { FilterRuleEngine.evaluate(ready.parsedDex, cfg) }
      val capped = summary.matchedMethodIds.take(96).toSet()
      _selectedMethodIds.value = capped
      val extra =
        if (summary.matchedMethodIds.size > 96) " (capped at 96 for preview)"
        else ""
      _statusBanner.value = "filter.txt matched ${summary.matchedMethodIds.size} methods$extra."
      rebuildBundle()
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
    _selectedMethodIds.value = emptySet()
    _generatedBundle.value = null
  }

  fun openRustBridgeSheet() {
    _isRustBridgeSheetOpen.value = true
  }

  fun closeRustBridgeSheet() {
    _isRustBridgeSheetOpen.value = false
  }

  fun exportRustBridgeZip(uri: Uri) {
    val context = getApplication<Application>()
    viewModelScope.launch {
      try {
        val files = RustJniScaffolding.generateBridgeFiles()
        withContext(Dispatchers.IO) {
          context.contentResolver.openOutputStream(uri)?.use { out ->
            ZipOutputStream(BufferedOutputStream(out)).use { zos ->
              files.forEach { file ->
                zos.putNextEntry(ZipEntry(file.relativePath))
                zos.write(file.content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
              }
            }
          } ?: throw IllegalStateException("Unable to open output stream for ZIP")
        }
        _statusBanner.value = "Exported Rust dexparser-rs JNI crate to ZIP."
      } catch (t: Throwable) {
        _statusBanner.value = "Export failed: ${t.message}"
      }
    }
  }

  fun inspectMethod(dexClass: DexClassInfo, method: DexEncodedMethod) {
    val ready = _loadState.value as? DexLoadState.Ready ?: return
    val cfg = _config.value
    viewModelScope.launch {
      val state =
        withContext(Dispatchers.Default) {
          val insns = DalvikDisassembler.disassemble(ready.parsedDex, method)
          val tries = DalvikDisassembler.parseTryBlocks(ready.parsedDex, method)
          val cCode = Dex2cTranspiler.transpileMethodToC(ready.parsedDex, method, cfg)
          val meta = DexHelper.inspectMethodMetadata(ready.parsedDex, dexClass, method)
          MethodInspectionState(method, dexClass, insns, tries, cCode, meta)
        }
      _inspection.value = state
    }
  }

  fun updateConfig(transform: (TranspilerConfig) -> TranspilerConfig) {
    val updated = transform(_config.value)
    _config.value = updated
    viewModelScope.launch { prefs.saveConfig(updated) }
    refreshInspectionIfActive()
    rebuildBundle()
  }

  private fun refreshInspectionIfActive() {
    val current = _inspection.value ?: return
    val ready = _loadState.value as? DexLoadState.Ready ?: return
    val cfg = _config.value
    viewModelScope.launch {
      val newCode =
        withContext(Dispatchers.Default) {
          Dex2cTranspiler.transpileMethodToC(ready.parsedDex, current.method, cfg)
        }
      _inspection.value = current.copy(transpiledC = newCode)
    }
  }

  fun rebuildBundle() {
    val ready = _loadState.value as? DexLoadState.Ready ?: return
    val selectedIds = _selectedMethodIds.value
    if (selectedIds.isEmpty()) {
      _generatedBundle.value = null
      return
    }
    val cfg = _config.value
    bundleJob?.cancel()
    bundleJob =
      viewModelScope.launch {
        _isTranspiling.value = true
        val bundle =
          withContext(Dispatchers.Default) {
            val methods =
              ready.parsedDex.classes
                .flatMap { it.allMethods }
                .filter { it.id in selectedIds }
            Dex2cTranspiler.transpileBundle(ready.parsedDex, methods, cfg)
          }
        _generatedBundle.value = bundle
        _isTranspiling.value = false
      }
  }

  fun exportBundleToZip(uri: Uri) {
    val bundle = _generatedBundle.value ?: return
    val context = getApplication<Application>()
    viewModelScope.launch {
      try {
        withContext(Dispatchers.IO) { ZipBundleExporter.exportToUri(context, uri, bundle) }
        _statusBanner.value = "Exported ${bundle.allFiles.size} NDK files to ZIP."
      } catch (t: Throwable) {
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
    _filterState.update {
      it.copy(
        hideFramework = updated.defaultHideFramework,
        onlyWithBytecode = updated.defaultOnlyBytecode,
      )
    }
    viewModelScope.launch { appPrefs.saveSettings(updated) }
  }

  fun completeOnboarding() {
    if (_appSettings.value.hasCompletedOnboarding) return
    updateAppSettings { it.copy(hasCompletedOnboarding = true) }
  }

  fun sendTestBuildNotification() {
    val sent = CloudBuildNotifier.sendTestNotification(getApplication())
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
    val clientId = _cloudSettings.value.githubClientId.trim()
    if (clientId.isEmpty()) {
      _deviceAuthState.value =
        DeviceAuthState.Error("Enter your GitHub OAuth App Client ID in Detailed Settings first.")
      return
    }
    devicePollJob?.cancel()
    _deviceAuthState.value = DeviceAuthState.RequestingCode

    devicePollJob =
      viewModelScope.launch {
        try {
          val codeResp = withContext(Dispatchers.IO) { gitHubClient.requestDeviceCode(clientId) }
          var intervalSec = codeResp.intervalSec
          val deadlineMs = System.currentTimeMillis() + codeResp.expiresInSec * 1000L

          _deviceAuthState.value =
            DeviceAuthState.AwaitingUser(
              userCode = codeResp.userCode,
              verificationUri = codeResp.verificationUri,
              expiresInSec = codeResp.expiresInSec,
              intervalSec = intervalSec,
            )

          while (isActive && System.currentTimeMillis() < deadlineMs) {
            delay(intervalSec * 1000L)
            val pollResult =
              withContext(Dispatchers.IO) {
                gitHubClient.pollAccessTokenOnce(clientId, codeResp.deviceCode, intervalSec)
              }
            when (pollResult) {
              is TokenPollResult.Pending -> Unit
              is TokenPollResult.SlowDown -> {
                intervalSec = pollResult.nextIntervalSec
                val cur = _deviceAuthState.value as? DeviceAuthState.AwaitingUser
                if (cur != null) {
                  _deviceAuthState.value = cur.copy(intervalSec = intervalSec)
                }
              }
              is TokenPollResult.Expired -> {
                _deviceAuthState.value = DeviceAuthState.Error("Device code expired. Start login again.")
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
                  withContext(Dispatchers.IO) {
                    gitHubClient.getAuthenticatedUsername(pollResult.accessToken)
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
                _statusBanner.value = "Signed in to GitHub as @$username"
                verifyOrLoadRepoState()
                return@launch
              }
            }
          }
          _deviceAuthState.value = DeviceAuthState.Error("Device code timed out.")
        } catch (t: Throwable) {
          _deviceAuthState.value =
            DeviceAuthState.Error(t.message ?: "Failed to initiate GitHub Device Flow.")
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
    cloudRunPollJob?.cancel()
    CloudBuildMonitorScheduler.cancelMonitor(getApplication())
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
          withContext(Dispatchers.IO) {
            gitHubClient.getRepository(cfg.accessToken, owner, cfg.repoName)
          }
        if (repoInfo != null) {
          val wfSha =
            withContext(Dispatchers.IO) {
              gitHubClient.getContentSha(
                cfg.accessToken,
                repoInfo.owner,
                repoInfo.name,
                Dex2cWorkflowGenerator.WORKFLOW_PATH,
                repoInfo.defaultBranch,
              )
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
        _repoSetupState.value = RepoSetupState.Error(t.message ?: "Repo verification failed.")
      }
    }
  }

  fun initializePrivateRepoAndWorkflow() {
    val settings = _cloudSettings.value
    if (!settings.isAuthenticated) {
      _repoSetupState.value = RepoSetupState.Error("Sign in via GitHub Device Flow first.")
      return
    }
    val repoName = settings.repoName.trim().ifBlank { "dex2c-cloud-workspace" }
    viewModelScope.launch {
      try {
        _repoSetupState.value = RepoSetupState.Working("Checking / creating private repo \"$repoName\"…")
        val owner = settings.effectiveOwner
        val existing =
          withContext(Dispatchers.IO) {
            gitHubClient.getRepository(settings.accessToken, owner, repoName)
          }
        val repoInfo =
          existing
            ?: withContext(Dispatchers.IO) {
              gitHubClient.createRepository(
                token = settings.accessToken,
                name = repoName,
                isPrivate = settings.privateRepo,
              )
            }

        val branch = repoInfo.defaultBranch.ifBlank { settings.defaultBranch.ifBlank { "main" } }
        _repoSetupState.value =
          RepoSetupState.Working("Committing dex2c.yml, dcc.cfg & filter.txt…")

        val transCfg = _config.value
        val yaml = Dex2cWorkflowGenerator.generateDex2cYaml(settings, transCfg)
        val dccCfg = DccConfigCodec.encodeDccCfg(transCfg)
        val filterTxt = transCfg.filterRulesText.trimEnd() + "\n"

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
          gitHubClient.upsertRepoFile(
            token = settings.accessToken,
            owner = repoInfo.owner,
            repo = repoInfo.name,
            path = Dex2cWorkflowGenerator.DCC_CFG_PATH,
            branch = branch,
            commitMessage = "Sync dcc.cfg transpiler configuration",
            contentBytes = dccCfg.toByteArray(Charsets.UTF_8),
          )
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

        val savedSettings =
          settings.copy(
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
        _statusBanner.value = "Initialized ${repoInfo.fullName} with dex2c.yml!"
        refreshCloudRuns()
      } catch (t: Throwable) {
        _repoSetupState.value =
          RepoSetupState.Error(t.message ?: "Failed to setup repository & dex2c.yml.")
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

          _cloudRunState.value = CloudBuildRunState.UploadingInputs("Syncing dex2c.yml, dcc.cfg & filter.txt…")
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
            gitHubClient.upsertRepoFile(
              token = settings.accessToken,
              owner = owner,
              repo = repo,
              path = Dex2cWorkflowGenerator.DCC_CFG_PATH,
              branch = branch,
              commitMessage = "Update dcc.cfg for cloud run",
              contentBytes = DccConfigCodec.encodeDccCfg(transCfg).toByteArray(Charsets.UTF_8),
            )
            gitHubClient.upsertRepoFile(
              token = settings.accessToken,
              owner = owner,
              repo = repo,
              path = Dex2cWorkflowGenerator.FILTER_TXT_PATH,
              branch = branch,
              commitMessage = "Update filter.txt for cloud run",
              contentBytes = (transCfg.filterRulesText.trimEnd() + "\n").toByteArray(Charsets.UTF_8),
            )
          }

          if (urlFallback.isEmpty() && apkChoice != null) {
            _cloudRunState.value =
              CloudBuildRunState.UploadingInputs("Uploading ${apkChoice.label} to private repo…")
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
              _cloudRunState.value =
                CloudBuildRunState.Error(
                  "APK (${apkBytes.size / (1024 * 1024)} MB) exceeds ${settings.maxDirectUploadMb} MB direct commit limit. Paste a direct APK URL below instead."
                )
              return@launch
            }
            withContext(Dispatchers.IO) {
              gitHubClient.upsertRepoFile(
                token = settings.accessToken,
                owner = owner,
                repo = repo,
                path = Dex2cWorkflowGenerator.DEFAULT_APK_REPO_PATH,
                branch = branch,
                commitMessage = "Upload target APK (${apkChoice.label}) for Dex2C build",
                contentBytes = apkBytes,
              )
            }
          }

          val prevTopRunId =
            withContext(Dispatchers.IO) {
              runCatching { gitHubClient.listWorkflowRuns(settings.accessToken, owner, repo).firstOrNull()?.id }
                .getOrNull()
            }

          _cloudRunState.value = CloudBuildRunState.Dispatching
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
                  "custom_loader" to transCfg.customLoaderClass.ifBlank { "amimo.dcc.DccApplication" },
                  "obfuscate" to (transCfg.stringEncryption || transCfg.controlFlowFlattening).toString(),
                  "dynamic_register" to (transCfg.jniStyle == com.example.transpiler.JniBindingStyle.REGISTER_NATIVES).toString(),
                  "signing_mode" to settings.signingMode.name,
                ),
            )
          }

          // Wait briefly for GitHub to register the new workflow run
          var activeRun: GitHubWorkflowRunSummary? = null
          repeat(8) {
            delay(2500L)
            val runs =
              withContext(Dispatchers.IO) {
                gitHubClient.listWorkflowRuns(settings.accessToken, owner, repo)
              }
            _recentCloudRuns.value = runs
            val candidate = runs.firstOrNull { it.id != prevTopRunId } ?: runs.firstOrNull()
            if (candidate != null && (prevTopRunId == null || candidate.id != prevTopRunId)) {
              activeRun = candidate
              return@repeat
            }
          }

          val initialRun = activeRun ?: _recentCloudRuns.value.firstOrNull()
          if (initialRun == null) {
            if (_cloudSettings.value.notificationsEnabled) {
              CloudBuildMonitorScheduler.scheduleMonitor(getApplication(), -1L, initialDelaySec = 10L)
            }
            _cloudRunState.value =
              CloudBuildRunState.Error("Workflow dispatched; refresh runs shortly to track progress.")
            return@launch
          }
          if (_cloudSettings.value.notificationsEnabled) {
            CloudBuildMonitorScheduler.scheduleMonitor(getApplication(), initialRun.id)
          }
          pollWorkflowRunUntilDone(settings, owner, repo, initialRun.id, notifyIfCompleted = true)
        } catch (t: Throwable) {
          _cloudRunState.value =
            CloudBuildRunState.Error(t.message ?: "Cloud Dex2C workflow dispatch failed.")
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
      val runId = polling?.runId ?: _recentCloudRuns.value.firstOrNull { it.status != "completed" }?.id
      if (runId != null) {
        attachToCloudRun(runId)
      }
    }
  }

  fun refreshActiveCloudRun() {
    val settings = _cloudSettings.value
    if (!settings.isRepoConfigured) return
    val activeRunId =
      when (val state = _cloudRunState.value) {
        is CloudBuildRunState.Polling -> state.runId
        is CloudBuildRunState.Completed -> state.runId
        else -> null
      }
    val wasPolling = _cloudRunState.value is CloudBuildRunState.Polling
    viewModelScope.launch {
      runCatching {
        fetchAndApplyRunSnapshot(
          settings = settings,
          owner = settings.effectiveOwner,
          repo = settings.repoName,
          targetRunId = activeRunId,
          notifyOnCompletion = wasPolling,
        )
      }.onFailure { t ->
        _statusBanner.value = "Monitor refresh failed: ${t.message}"
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
    while (true) {
      val isDone =
        fetchAndApplyRunSnapshot(
          settings = settings,
          owner = owner,
          repo = repo,
          targetRunId = runId,
          notifyOnCompletion = shouldNotifyWhenDone,
        )
      if (isDone) return
      // Once observed active in-progress/queued, schedule background monitor & allow completion alert
      shouldNotifyWhenDone = true
      if (_cloudSettings.value.notificationsEnabled) {
        CloudBuildMonitorScheduler.scheduleMonitor(getApplication(), runId)
      }
      if (!_isAutoPollEnabled.value) return
      delay(pollMs)
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
        )
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
    viewModelScope.launch {
      try {
        _cloudRunState.value = CloudBuildRunState.SavingArtifact("Downloading ${artifact.name}…")
        val extracted =
          withContext(Dispatchers.IO) {
            val zipBytes = gitHubClient.downloadArtifactZip(settings.accessToken, artifact.archiveDownloadUrl)
            gitHubClient.extractApkFromArtifactZip(zipBytes)
          }
        _cloudRunState.value = prevState
        _pendingCloudApkSave.value = extracted
      } catch (t: Throwable) {
        _cloudRunState.value = prevState
        _statusBanner.value = "Artifact download failed: ${t.message}"
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
      } catch (t: Throwable) {
        _statusBanner.value = "Save failed: ${t.message}"
      }
    }
  }

  fun clearPendingCloudApkSave() {
    _pendingCloudApkSave.value = null
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
