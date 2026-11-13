package com.example

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.apk.PickerTab
import com.example.cloud.CloudBuildNotifier
import com.example.cloud.CloudDeepLinkTarget
import com.example.data.AppThemeMode
import com.example.ui.Dex2cViewModel
import com.example.ui.DexLoadState
import com.example.ui.components.ApkPickerSheet
import com.example.ui.components.ApkPreviewSheet
import com.example.ui.components.ExpressiveMotion
import com.example.ui.components.RustJniBridgeSheet
import com.example.ui.screens.ClassesScreen
import com.example.ui.screens.CloudBuildScreen
import com.example.ui.screens.MethodDetailScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.OutputExportScreen
import com.example.ui.screens.PipelineConfigScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.WorkbenchScreen
import com.example.ui.theme.Dex2cTheme
import com.example.ui.theme.EditorDisplaySettings
import kotlinx.coroutines.launch

const val ROUTE_WORKBENCH = "workbench"
const val ROUTE_CLASSES = "classes"
const val ROUTE_CONFIG = "config"
const val ROUTE_CLOUD = "cloud"
const val ROUTE_EXPORT = "export"
const val ROUTE_METHOD_DETAIL = "method_detail"
const val ROUTE_SETTINGS = "settings"
const val ROUTE_ONBOARDING = "onboarding"

private data class NavDestinationItem(
  val route: String,
  val labelRes: Int,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
  val testTag: String,
)

class MainActivity : ComponentActivity() {
  private var pendingDeepLink by mutableStateOf<CloudDeepLinkTarget?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    CloudBuildNotifier.ensureNotificationChannel(this)
    pendingDeepLink = CloudBuildNotifier.extractDeepLinkTarget(intent)
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
    )
    setContent {
      val appViewModel: Dex2cViewModel = viewModel()
      val appSettings by appViewModel.appSettings.collectAsStateWithLifecycle()
      val systemDark = isSystemInDarkTheme()
      val isDarkTheme =
        when (appSettings.themeMode) {
          AppThemeMode.DARK, AppThemeMode.AMOLED -> true
          AppThemeMode.LIGHT -> false
          AppThemeMode.SYSTEM -> systemDark
        }
      DisposableEffect(isDarkTheme) {
        enableEdgeToEdge(
          statusBarStyle =
            if (isDarkTheme) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
          navigationBarStyle =
            if (isDarkTheme) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        onDispose {}
      }
      Dex2cTheme(
        themeMode = appSettings.themeMode,
        dynamicColor = appSettings.dynamicColor,
        accentSeedHex = appSettings.accentSeedHex,
        editorSettings =
          EditorDisplaySettings(
            fontSizeSp = appSettings.codeFontSizeSp,
            showLineNumbers = appSettings.showLineNumbers,
            maxLinesLimit = appSettings.maxCodePreviewLines,
          ),
      ) {
        Dex2cApp(
          deepLinkTarget = pendingDeepLink,
          onDeepLinkConsumed = { pendingDeepLink = null },
          viewModel = appViewModel,
        )
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    pendingDeepLink = CloudBuildNotifier.extractDeepLinkTarget(intent)
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Dex2cApp(
  deepLinkTarget: CloudDeepLinkTarget? = null,
  onDeepLinkConsumed: () -> Unit = {},
  viewModel: Dex2cViewModel = viewModel(),
) {
  val context = LocalContext.current
  val navController = rememberNavController()
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()

  val loadState by viewModel.loadState.collectAsStateWithLifecycle()
  val filteredClasses by viewModel.filteredClasses.collectAsStateWithLifecycle()
  val filterState by viewModel.filterState.collectAsStateWithLifecycle()
  val selectedMethodIds by viewModel.selectedMethodIds.collectAsStateWithLifecycle()
  val config by viewModel.config.collectAsStateWithLifecycle()
  val inspection by viewModel.inspection.collectAsStateWithLifecycle()
  val generatedBundle by viewModel.generatedBundle.collectAsStateWithLifecycle()
  val isTranspiling by viewModel.isTranspiling.collectAsStateWithLifecycle()
  val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()

  val isPickerOpen by viewModel.isPickerOpen.collectAsStateWithLifecycle()
  val activePickerTab by viewModel.activePickerTab.collectAsStateWithLifecycle()
  val filteredApps by viewModel.filteredInstalledApps.collectAsStateWithLifecycle()
  val isLoadingApps by viewModel.isLoadingApps.collectAsStateWithLifecycle()
  val appSearchQuery by viewModel.appSearchQuery.collectAsStateWithLifecycle()
  val recentFiles by viewModel.recentFiles.collectAsStateWithLifecycle()
  val stagingState by viewModel.stagingState.collectAsStateWithLifecycle()
  val filterMatchSummary by viewModel.filterMatchSummary.collectAsStateWithLifecycle()
  val isRustBridgeSheetOpen by viewModel.isRustBridgeSheetOpen.collectAsStateWithLifecycle()

  val appSettings by viewModel.appSettings.collectAsStateWithLifecycle()
  val appSettingsHydrated by viewModel.appSettingsHydrated.collectAsStateWithLifecycle()
  val cloudSettings by viewModel.cloudSettings.collectAsStateWithLifecycle()
  val deviceAuthState by viewModel.deviceAuthState.collectAsStateWithLifecycle()
  val repoSetupState by viewModel.repoSetupState.collectAsStateWithLifecycle()
  val cloudRunState by viewModel.cloudRunState.collectAsStateWithLifecycle()
  val isAutoPollEnabled by viewModel.isAutoPollEnabled.collectAsStateWithLifecycle()
  val recentCloudRuns by viewModel.recentCloudRuns.collectAsStateWithLifecycle()
  val cloudApkChoice by viewModel.cloudApkChoice.collectAsStateWithLifecycle()
  val cloudApkUrl by viewModel.cloudApkUrl.collectAsStateWithLifecycle()
  val pendingCloudApkSave by viewModel.pendingCloudApkSave.collectAsStateWithLifecycle()

  val openDocLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) {
        viewModel.stageFromSafUri(uri)
      }
    }

  val pickCloudApkLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) {
        viewModel.selectCloudApkFromSaf(uri)
      }
    }

  var hasNotificationPermission by remember {
    mutableStateOf(CloudBuildNotifier.hasNotificationPermission(context))
  }

  val notificationPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      hasNotificationPermission = granted || CloudBuildNotifier.hasNotificationPermission(context)
    }

  fun requestNotificationPermissionIfNeeded() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
      !CloudBuildNotifier.hasNotificationPermission(context)
    ) {
      notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
  }

  val saveCloudApkLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
      if (uri != null) {
        viewModel.savePendingCloudApkToUri(uri)
      } else {
        viewModel.clearPendingCloudApkSave()
      }
    }

  val importFilterLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) {
        viewModel.importFilterTxtFromUri(uri)
      }
    }

  val importDccCfgLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) {
        viewModel.importDccCfgFromUri(uri)
      }
    }

  val exportZipLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
      if (uri != null) {
        viewModel.exportBundleToZip(uri)
      }
    }

  val exportRustZipLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
      if (uri != null) {
        viewModel.exportRustBridgeZip(uri)
      }
    }

  val copiedMsg = stringResource(R.string.msg_copied_clipboard)
  fun copyText(text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Dex2C Code", text))
    scope.launch { snackbarHostState.showSnackbar(copiedMsg) }
  }

  fun openBrowserUrl(url: String) {
    runCatching {
      val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
      context.startActivity(intent)
    }.onFailure {
      scope.launch { snackbarHostState.showSnackbar("Unable to open browser: ${it.message}") }
    }
  }

  LaunchedEffect(statusBanner) {
    val msg = statusBanner ?: return@LaunchedEffect
    snackbarHostState.showSnackbar(msg)
    viewModel.clearStatusBanner()
  }

  LaunchedEffect(pendingCloudApkSave) {
    val pending = pendingCloudApkSave ?: return@LaunchedEffect
    saveCloudApkLauncher.launch(pending.fileName)
  }

  val navItems =
    listOf(
      NavDestinationItem(ROUTE_WORKBENCH, R.string.nav_workbench, Icons.Rounded.Build, Icons.Rounded.Build, "nav_workbench"),
      NavDestinationItem(ROUTE_CLASSES, R.string.nav_classes, Icons.Rounded.DataObject, Icons.Rounded.DataObject, "nav_classes"),
      NavDestinationItem(ROUTE_CONFIG, R.string.nav_config, Icons.Rounded.Tune, Icons.Rounded.Tune, "nav_config"),
      NavDestinationItem(ROUTE_CLOUD, R.string.nav_cloud, Icons.Rounded.CloudQueue, Icons.Rounded.CloudQueue, "nav_cloud"),
      NavDestinationItem(ROUTE_EXPORT, R.string.nav_export, Icons.Rounded.Code, Icons.Rounded.Code, "nav_export"),
    )

  val backStackEntry by navController.currentBackStackEntryAsState()
  val currentRoute = backStackEntry?.destination?.route ?: ROUTE_WORKBENCH
  val showTopBarAndNav =
    appSettingsHydrated &&
      currentRoute != ROUTE_METHOD_DETAIL &&
      currentRoute != ROUTE_SETTINGS &&
      currentRoute != ROUTE_ONBOARDING

  fun navigateTopLevel(route: String) {
    navController.navigate(route) {
      popUpTo(navController.graph.findStartDestination().id) { saveState = true }
      launchSingleTop = true
      restoreState = true
    }
  }

  LaunchedEffect(deepLinkTarget, appSettingsHydrated) {
    val target = deepLinkTarget ?: return@LaunchedEffect
    if (!appSettingsHydrated) return@LaunchedEffect
    viewModel.completeOnboarding()
    navigateTopLevel(target.route)
    viewModel.attachToCloudRun(target.runId)
    onDeepLinkConsumed()
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useRail = maxWidth >= 720.dp

    Scaffold(
      modifier = Modifier.fillMaxSize(),
      contentWindowInsets = WindowInsets.safeDrawing,
      snackbarHost = { SnackbarHost(snackbarHostState) },
      topBar = {
        if (showTopBarAndNav) {
          TopAppBar(
            title = {
              Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
              )
            },
            actions = {
              IconButton(
                onClick = { viewModel.openPicker(PickerTab.STORAGE) },
                modifier = Modifier.testTag("topbar_pick_apk_button"),
              ) {
                Icon(
                  imageVector = Icons.Rounded.FolderOpen,
                  contentDescription = stringResource(R.string.action_open_file),
                )
              }
              IconButton(
                onClick = { navController.navigate(ROUTE_SETTINGS) },
                modifier = Modifier.testTag("topbar_settings_button"),
              ) {
                Icon(
                  imageVector = Icons.Rounded.Settings,
                  contentDescription = stringResource(R.string.nav_settings),
                )
              }
            },
            colors =
              TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
              ),
          )
        }
      },
      bottomBar = {
        if (showTopBarAndNav && !useRail) {
          NavigationBar(
            windowInsets = WindowInsets.navigationBars,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
          ) {
            navItems.forEach { item ->
              val selected = currentRoute == item.route
              NavigationBarItem(
                selected = selected,
                onClick = { navigateTopLevel(item.route) },
                modifier = Modifier.testTag(item.testTag),
                icon = {
                  if (item.route == ROUTE_EXPORT && selectedMethodIds.isNotEmpty()) {
                    BadgedBox(badge = { Badge { Text("${selectedMethodIds.size}") } }) {
                      Icon(
                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = stringResource(item.labelRes),
                      )
                    }
                  } else {
                    Icon(
                      imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                      contentDescription = stringResource(item.labelRes),
                    )
                  }
                },
                label = { Text(stringResource(item.labelRes)) },
              )
            }
          }
        }
      },
    ) { innerPadding ->
      Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        if (showTopBarAndNav && useRail) {
          NavigationRail(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
          ) {
            navItems.forEach { item ->
              val selected = currentRoute == item.route
              NavigationRailItem(
                selected = selected,
                onClick = { navigateTopLevel(item.route) },
                modifier = Modifier.testTag(item.testTag),
                icon = {
                  if (item.route == ROUTE_EXPORT && selectedMethodIds.isNotEmpty()) {
                    BadgedBox(badge = { Badge { Text("${selectedMethodIds.size}") } }) {
                      Icon(
                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = stringResource(item.labelRes),
                      )
                    }
                  } else {
                    Icon(
                      imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                      contentDescription = stringResource(item.labelRes),
                    )
                  }
                },
                label = { Text(stringResource(item.labelRes)) },
              )
            }
          }
        }

        Box(modifier = Modifier.weight(1f).fillMaxSize()) {
          if (appSettingsHydrated) {
            val startDestination =
              remember(appSettingsHydrated) {
                if (appSettings.hasCompletedOnboarding) ROUTE_WORKBENCH else ROUTE_ONBOARDING
              }
            val routeOrder = listOf(ROUTE_ONBOARDING, ROUTE_WORKBENCH, ROUTE_CLASSES, ROUTE_CONFIG, ROUTE_CLOUD, ROUTE_EXPORT, ROUTE_METHOD_DETAIL, ROUTE_SETTINGS)
            NavHost(
              navController = navController,
              startDestination = startDestination,
              enterTransition = {
                if (initialState.destination.route == ROUTE_ONBOARDING || targetState.destination.route == ROUTE_ONBOARDING) {
                  EnterTransition.None
                } else {
                  val fromIdx = routeOrder.indexOf(initialState.destination.route).coerceAtLeast(0)
                  val toIdx = routeOrder.indexOf(targetState.destination.route).coerceAtLeast(0)
                  ExpressiveMotion.navEnterTransition(forward = toIdx >= fromIdx)
                }
              },
              exitTransition = {
                if (initialState.destination.route == ROUTE_ONBOARDING || targetState.destination.route == ROUTE_ONBOARDING) {
                  ExitTransition.None
                } else {
                  val fromIdx = routeOrder.indexOf(initialState.destination.route).coerceAtLeast(0)
                  val toIdx = routeOrder.indexOf(targetState.destination.route).coerceAtLeast(0)
                  ExpressiveMotion.navExitTransition(forward = toIdx >= fromIdx)
                }
              },
              popEnterTransition = {
                if (initialState.destination.route == ROUTE_ONBOARDING || targetState.destination.route == ROUTE_ONBOARDING) {
                  EnterTransition.None
                } else {
                  val fromIdx = routeOrder.indexOf(initialState.destination.route).coerceAtLeast(0)
                  val toIdx = routeOrder.indexOf(targetState.destination.route).coerceAtLeast(0)
                  ExpressiveMotion.navEnterTransition(forward = toIdx >= fromIdx)
                }
              },
              popExitTransition = {
                if (initialState.destination.route == ROUTE_ONBOARDING || targetState.destination.route == ROUTE_ONBOARDING) {
                  ExitTransition.None
                } else {
                  val fromIdx = routeOrder.indexOf(initialState.destination.route).coerceAtLeast(0)
                  val toIdx = routeOrder.indexOf(targetState.destination.route).coerceAtLeast(0)
                  ExpressiveMotion.navExitTransition(forward = toIdx >= fromIdx)
                }
              },
            ) {
            composable(ROUTE_WORKBENCH) {
              WorkbenchScreen(
                loadState = loadState,
                selectedMethodCount = selectedMethodIds.size,
                recentFiles = recentFiles,
                onOpenPicker = { tab -> viewModel.openPicker(tab) },
                onInspectSelfApk = { viewModel.inspectLiveAppApk() },
                onSelectRecent = { viewModel.stageFromRecentItem(it) },
                onSelectDexEntry = { viewModel.switchDexEntry(it) },
                onAutoSelectAppMethods = { viewModel.autoSelectAppMethods() },
                onClearSelection = { viewModel.clearSelection() },
                onOpenRustBridge = { viewModel.openRustBridgeSheet() },
                onNavigateToClasses = { navigateTopLevel(ROUTE_CLASSES) },
                onNavigateToExport = { navigateTopLevel(ROUTE_EXPORT) },
              )
            }

            composable(ROUTE_CLASSES) {
              ClassesScreen(
                loadState = loadState,
                classes = filteredClasses,
                filterState = filterState,
                selectedMethodIds = selectedMethodIds,
                allowInit = config.allowInit,
                onSearchChange = { viewModel.updateSearchQuery(it) },
                onToggleHideFramework = { viewModel.toggleHideFramework() },
                onToggleOnlyBytecode = { viewModel.toggleOnlyWithBytecode() },
                onToggleOnlySelected = { viewModel.toggleOnlySelected() },
                onToggleClassSelection = { viewModel.toggleClassSelection(it) },
                onToggleMethodSelection = { viewModel.toggleMethodSelection(it) },
                onInspectMethod = { cls, method ->
                  viewModel.inspectMethod(cls, method)
                  navController.navigate(ROUTE_METHOD_DETAIL)
                },
                onInspectSelfApk = { viewModel.inspectLiveAppApk() },
              )
            }

            composable(ROUTE_CONFIG) {
              PipelineConfigScreen(
                config = config,
                hasLoadedDex = loadState is DexLoadState.Ready,
                filterMatchSummary = filterMatchSummary,
                onUpdateConfig = { viewModel.updateConfig(it) },
                onImportFilterTxt = { importFilterLauncher.launch(arrayOf("text/*", "*/*")) },
                onImportDccCfg = { importDccCfgLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
                onApplyFilterRules = {
                  viewModel.applyFilterRules()
                  navigateTopLevel(ROUTE_EXPORT)
                },
              )
            }

            composable(ROUTE_CLOUD) {
              CloudBuildScreen(
                cloudSettings = cloudSettings,
                transpilerConfig = config,
                deviceAuthState = deviceAuthState,
                repoSetupState = repoSetupState,
                cloudRunState = cloudRunState,
                isAutoPollEnabled = isAutoPollEnabled,
                recentRuns = recentCloudRuns,
                apkChoice = cloudApkChoice,
                apkUrlFallback = cloudApkUrl,
                onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
                onStartDeviceLogin = { viewModel.startDeviceFlowLogin() },
                onCancelDeviceLogin = { viewModel.cancelDeviceFlowLogin() },
                onSignOut = { viewModel.signOutGitHub() },
                onCopyCode = { copyText(it) },
                onOpenBrowserUrl = { openBrowserUrl(it) },
                onUpdateRepoName = { name -> viewModel.updateCloudSettings { it.copy(repoName = name.trim()) } },
                onInitializeRepoAndWorkflow = { viewModel.initializePrivateRepoAndWorkflow() },
                onVerifyRepo = { viewModel.verifyOrLoadRepoState() },
                onPickApkFile = {
                  pickCloudApkLauncher.launch(
                    arrayOf("application/vnd.android.package-archive", "application/octet-stream", "*/*")
                  )
                },
                onUseLiveApk = { viewModel.useLiveApkForCloud() },
                onApkUrlChange = { viewModel.updateCloudApkUrl(it) },
                onDispatchBuild = {
                  if (cloudSettings.notificationsEnabled) {
                    requestNotificationPermissionIfNeeded()
                  }
                  viewModel.dispatchCloudDex2cBuild()
                },
                onToggleAutoPoll = { viewModel.toggleAutoPoll() },
                onRefreshMonitor = { viewModel.refreshActiveCloudRun() },
                onRetryMonitor = { viewModel.retryCloudMonitorError() },
                onRefreshRuns = { viewModel.refreshCloudRuns() },
                onAttachToRun = { viewModel.attachToCloudRun(it) },
                onDownloadArtifact = { viewModel.downloadArtifactAndPromptSave(it) },
              )
            }

            composable(ROUTE_EXPORT) {
              OutputExportScreen(
                bundle = generatedBundle,
                isTranspiling = isTranspiling,
                onCopyFile = { copyText(it) },
                onExportZip = {
                  val name = "${config.moduleName.ifBlank { "dex2c_native" }}_jni.zip"
                  exportZipLauncher.launch(name)
                },
                onGoToClasses = { navigateTopLevel(ROUTE_CLASSES) },
              )
            }

            composable(ROUTE_METHOD_DETAIL) {
              val currentInspection = inspection
              if (currentInspection != null) {
                MethodDetailScreen(
                  inspection = currentInspection,
                  isSelected = currentInspection.method.id in selectedMethodIds,
                  onToggleSelect = { viewModel.toggleMethodSelection(currentInspection.method.id) },
                  onCopyCCode = { copyText(it) },
                  onBack = { navController.popBackStack() },
                )
              }
            }

            composable(ROUTE_SETTINGS) {
              SettingsScreen(
                appSettings = appSettings,
                cloudSettings = cloudSettings,
                transpilerConfig = config,
                recentHistoryCount = recentFiles.size,
                hasNotificationPermission = hasNotificationPermission,
                onUpdateAppSettings = { viewModel.updateAppSettings(it) },
                onUpdateCloudSettings = { viewModel.updateCloudSettings(it) },
                onToggleNotifications = { enabled ->
                  viewModel.setNotificationsEnabled(enabled)
                  if (enabled) {
                    requestNotificationPermissionIfNeeded()
                  }
                },
                onRequestNotificationPermission = { requestNotificationPermissionIfNeeded() },
                onSendTestNotification = { viewModel.sendTestBuildNotification() },
                onSyncWorkflowToRepo = {
                  viewModel.initializePrivateRepoAndWorkflow()
                  navController.popBackStack()
                  navigateTopLevel(ROUTE_CLOUD)
                },
                onStartDeviceLogin = {
                  viewModel.startDeviceFlowLogin()
                  navController.popBackStack()
                  navigateTopLevel(ROUTE_CLOUD)
                },
                onSignOutGitHub = { viewModel.signOutGitHub() },
                onOpenGitHubAppSettings = { openBrowserUrl("https://github.com/settings/developers") },
                onGoToPipelineConfig = {
                  navController.popBackStack()
                  navigateTopLevel(ROUTE_CONFIG)
                },
                onOpenRustBridge = {
                  navController.popBackStack()
                  viewModel.openRustBridgeSheet()
                },
                onClearRecentHistory = { viewModel.clearRecentHistory() },
                onResetAllSettings = { clearGitHub -> viewModel.resetAllSettingsToDefaults(clearGitHub) },
                onReplayOnboarding = {
                  navController.navigate(ROUTE_ONBOARDING) { launchSingleTop = true }
                },
                onCopyText = { copyText(it) },
                onBack = { navController.popBackStack() },
              )
            }

            composable(ROUTE_ONBOARDING) {
              OnboardingScreen(
                isReplayMode = appSettings.hasCompletedOnboarding,
                appSettings = appSettings,
                cloudSettings = cloudSettings,
                deviceAuthState = deviceAuthState,
                repoSetupState = repoSetupState,
                hasNotificationPermission = hasNotificationPermission,
                onUpdateAppSettings = { viewModel.updateAppSettings(it) },
                onUpdateCloudSettings = { viewModel.updateCloudSettings(it) },
                onStartDeviceLogin = { viewModel.startDeviceFlowLogin() },
                onCancelDeviceLogin = { viewModel.cancelDeviceFlowLogin() },
                onCopyCode = { copyText(it) },
                onOpenBrowserUrl = { openBrowserUrl(it) },
                onRetryRepoSetup = { viewModel.initializePrivateRepoAndWorkflow() },
                onToggleNotifications = { enabled ->
                  viewModel.setNotificationsEnabled(enabled)
                  if (enabled) {
                    requestNotificationPermissionIfNeeded()
                  }
                },
                onRequestNotificationPermission = { requestNotificationPermissionIfNeeded() },
                onFinish = {
                  viewModel.completeOnboarding()
                  navController.navigate(ROUTE_WORKBENCH) {
                    popUpTo(ROUTE_ONBOARDING) { inclusive = true }
                    launchSingleTop = true
                  }
                },
                onDismissReplay = { navController.popBackStack() },
              )
            }
          }
          }
        }
      }
    }
  }

  if (isPickerOpen) {
    ApkPickerSheet(
      activeTab = activePickerTab,
      installedApps = filteredApps,
      isLoadingApps = isLoadingApps,
      appSearchQuery = appSearchQuery,
      recentFiles = recentFiles,
      onSelectTab = { viewModel.setPickerTab(it) },
      onBrowseStorage = {
        openDocLauncher.launch(
          arrayOf(
            "application/vnd.android.package-archive",
            "application/octet-stream",
            "*/*",
          )
        )
      },
      onInspectSelfApk = { viewModel.inspectLiveAppApk() },
      onAppSearchChange = { viewModel.updateAppSearchQuery(it) },
      onSelectInstalledApp = { viewModel.stageFromInstalledApp(it) },
      onSelectRecent = { viewModel.stageFromRecentItem(it) },
      onRemoveRecent = { viewModel.removeRecentItem(it) },
      onClearRecent = { viewModel.clearRecentHistory() },
      onDismiss = { viewModel.closePicker() },
    )
  }

  val activeStaging = stagingState
  if (activeStaging != null) {
    ApkPreviewSheet(
      stagingState = activeStaging,
      onSelectDexEntry = { viewModel.selectStagedDexEntry(it) },
      onConfirmLoad = { viewModel.commitStagedDex() },
      onDismiss = { viewModel.dismissStagingPreview() },
    )
  }

  if (isRustBridgeSheetOpen) {
    RustJniBridgeSheet(
      onCopyCode = { copyText(it) },
      onExportZip = { exportRustZipLauncher.launch("dexparser_jni_rust.zip") },
      onDismiss = { viewModel.closeRustBridgeSheet() },
    )
  }
}
