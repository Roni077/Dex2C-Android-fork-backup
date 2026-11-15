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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.example.ui.screens.BuildHubScreen
import com.example.ui.screens.BuildHubTab
import com.example.ui.screens.ClassesScreen
import com.example.ui.screens.ClassesSubTab
import com.example.ui.screens.CloudBuildScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MethodDetailScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SmaliClassScreen
import com.example.ui.theme.Dex2cTheme
import com.example.ui.theme.EditorDisplaySettings
import kotlinx.coroutines.launch

const val ROUTE_HOME = "home"
const val ROUTE_CLASSES = "classes"
const val ROUTE_BUILD = "build"
const val ROUTE_CLOUD = "cloud"
const val ROUTE_METHOD_DETAIL = "method_detail"
const val ROUTE_SMALI_CLASS = "smali_class"
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
            smaliSyntaxTheme = appSettings.smaliSyntaxTheme,
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
  val transpileProgress by viewModel.transpileProgress.collectAsStateWithLifecycle()
  val isBundleDirty by viewModel.isBundleDirty.collectAsStateWithLifecycle()
  val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()

  val isPickerOpen by viewModel.isPickerOpen.collectAsStateWithLifecycle()
  val activePickerTab by viewModel.activePickerTab.collectAsStateWithLifecycle()
  val filteredApps by viewModel.filteredInstalledApps.collectAsStateWithLifecycle()
  val isLoadingApps by viewModel.isLoadingApps.collectAsStateWithLifecycle()
  val appSearchQuery by viewModel.appSearchQuery.collectAsStateWithLifecycle()
  val recentFiles by viewModel.recentFiles.collectAsStateWithLifecycle()
  val lastBytecodeSummary by viewModel.lastBytecodeSummary.collectAsStateWithLifecycle()
  val stagingState by viewModel.stagingState.collectAsStateWithLifecycle()
  val filterMatchSummary by viewModel.filterMatchSummary.collectAsStateWithLifecycle()
  val smaliClassState by viewModel.smaliClassState.collectAsStateWithLifecycle()
  var pendingSmaliExportText by remember { mutableStateOf<String?>(null) }

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
    rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
      if (uris.isNotEmpty()) {
        viewModel.stageFromSafUris(uris)
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

  val exportSmaliLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
      val text = pendingSmaliExportText
      pendingSmaliExportText = null
      if (uri != null && text != null) {
        viewModel.exportSmaliToUri(uri, text)
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

  var buildHubTab by rememberSaveable { mutableStateOf(BuildHubTab.PIPELINE) }
  var classesSubTab by rememberSaveable { mutableStateOf(ClassesSubTab.EXPLORER) }
  LaunchedEffect(appSettings.showOutputScreen) {
    if (!appSettings.showOutputScreen && buildHubTab == BuildHubTab.OUTPUT) {
      buildHubTab = BuildHubTab.PIPELINE
    }
  }

  val navItems =
    listOf(
      NavDestinationItem(ROUTE_HOME, R.string.nav_home, Icons.Rounded.Home, Icons.Outlined.Home, "nav_home"),
      NavDestinationItem(ROUTE_CLASSES, R.string.nav_classes, Icons.Rounded.DataObject, Icons.Outlined.DataObject, "nav_classes"),
      NavDestinationItem(ROUTE_BUILD, R.string.nav_build, Icons.Rounded.Code, Icons.Outlined.Code, "nav_build"),
      NavDestinationItem(ROUTE_CLOUD, R.string.nav_cloud, Icons.Rounded.CloudQueue, Icons.Outlined.CloudQueue, "nav_cloud"),
    )

  val backStackEntry by navController.currentBackStackEntryAsState()
  val currentRoute = backStackEntry?.destination?.route ?: ROUTE_HOME
  val showTopBarAndNav =
    appSettingsHydrated &&
      currentRoute != ROUTE_METHOD_DETAIL &&
      currentRoute != ROUTE_SMALI_CLASS &&
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
          Dex2cFloatingBottomBar(
            navItems = navItems,
            currentRoute = currentRoute,
            queuedMethodCount = selectedMethodIds.size,
            onNavigate = ::navigateTopLevel,
          )
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
                  if (item.route == ROUTE_BUILD && selectedMethodIds.isNotEmpty()) {
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
                if (appSettings.hasCompletedOnboarding) ROUTE_HOME else ROUTE_ONBOARDING
              }
            val routeOrder = listOf(ROUTE_ONBOARDING, ROUTE_HOME, ROUTE_CLASSES, ROUTE_BUILD, ROUTE_CLOUD, ROUTE_METHOD_DETAIL, ROUTE_SMALI_CLASS, ROUTE_SETTINGS)
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
            composable(ROUTE_HOME) {
              HomeScreen(
                loadState = loadState,
                selectedMethodCount = selectedMethodIds.size,
                recentFiles = recentFiles,
                onOpenPicker = { tab -> viewModel.openPicker(tab) },
                onInspectSelfApk = { viewModel.inspectLiveAppApk() },
                onSelectRecent = { viewModel.stageFromRecentItem(it) },
                onSelectDexEntry = { viewModel.switchDexEntry(it) },
                onAutoSelectAppMethods = { viewModel.autoSelectAppMethods() },
                onClearSelection = { viewModel.clearSelection() },
                onNavigateToClasses = {
                  classesSubTab = ClassesSubTab.EXPLORER
                  navigateTopLevel(ROUTE_CLASSES)
                },
                onNavigateToExport = {
                  if (appSettings.showOutputScreen) {
                    buildHubTab = BuildHubTab.OUTPUT
                    navigateTopLevel(ROUTE_BUILD)
                  }
                },
                onFocusHeaderDex = { viewModel.focusHeaderDexEntry(it) },
                lastBytecodeSummary = lastBytecodeSummary,
                showOutputScreen = appSettings.showOutputScreen,
                config = config,
                filterMatchSummary = filterMatchSummary,
                onTogglePresetRule = { viewModel.togglePresetRuleLine(it) },
                onApplyFilterRules = { viewModel.applyFilterRules() },
                onOpenRuleBuilder = {
                  classesSubTab = ClassesSubTab.FILTER_RULES
                  navigateTopLevel(ROUTE_CLASSES)
                },
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
                onSortOrderChange = { viewModel.setClassSortOrder(it) },
                onClassTypeFilterChange = { viewModel.setClassTypeFilter(it) },
                onToggleHideInnerClasses = { viewModel.toggleHideInnerClasses() },
                onMethodScopeFilterChange = { viewModel.setMethodScopeFilter(it) },
                onToggleNonEmptyPackages = { viewModel.toggleNonEmptyPackagesOnly() },
                onResetExtraFilters = { viewModel.resetExtraClassFilters() },
                onTogglePackageSelection = { viewModel.togglePackageSelection(it) },
                onDexSourceFilterChange = { viewModel.setDexSourceFilter(it) },
                onToggleClassSelection = { viewModel.toggleClassSelection(it) },
                onToggleMethodSelection = { viewModel.toggleMethodSelection(it) },
                onInspectMethod = { cls, method ->
                  viewModel.inspectMethod(cls, method)
                  navController.navigate(ROUTE_METHOD_DETAIL)
                },
                onInspectSelfApk = { viewModel.inspectLiveAppApk() },
                onInspectClassSmali = { cls ->
                  viewModel.inspectClassSmali(cls)
                  navController.navigate(ROUTE_SMALI_CLASS)
                },
                config = config,
                filterMatchSummary = filterMatchSummary,
                onUpdateConfig = { viewModel.updateConfig(it) },
                onSyncSelectionToWildcardRules = { isBlacklist ->
                  viewModel.syncSelectionToWildcardRules(isBlacklist)
                },
                onApplyFilterRules = { viewModel.applyFilterRules() },
                onToggleOnlyMatchingFilterRules = { viewModel.toggleOnlyMatchingFilterRules() },
                onQuickPackageRule = { pkg, isBlacklist ->
                  viewModel.addQuickPackageRule(pkg, isBlacklist)
                },
                onQuickClassRule = { cls, isBlacklist ->
                  viewModel.addQuickClassRule(cls, isBlacklist)
                },
                initialSubTab = classesSubTab,
              )
            }

            composable(ROUTE_BUILD) {
              BuildHubScreen(
                activeTab = buildHubTab,
                onSelectTab = { tab ->
                  if (appSettings.showOutputScreen || tab == BuildHubTab.PIPELINE) {
                    buildHubTab = tab
                  }
                },
                selectedMethodCount = selectedMethodIds.size,
                config = config,
                hasLoadedDex = loadState is DexLoadState.Ready,
                filterMatchSummary = filterMatchSummary,
                onUpdateConfig = { viewModel.updateConfig(it) },
                onImportFilterTxt = { importFilterLauncher.launch(arrayOf("text/*", "*/*")) },
                onImportDccCfg = { importDccCfgLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
                onApplyFilterRules = {
                  viewModel.applyFilterRules()
                  if (appSettings.showOutputScreen) {
                    buildHubTab = BuildHubTab.OUTPUT
                  }
                },
                bundle = generatedBundle,
                isTranspiling = isTranspiling,
                onCopyFile = { copyText(it) },
                onExportZip = {
                  val name = "${config.moduleName.ifBlank { "dex2c_native" }}_jni.zip"
                  exportZipLauncher.launch(name)
                },
                onGoToClasses = { navigateTopLevel(ROUTE_CLASSES) },
                transpileProgress = transpileProgress,
                isBundleDirty = isBundleDirty,
                onEnsureBundleBuilt = { viewModel.ensureBundleBuilt() },
                showOutputScreen = appSettings.showOutputScreen,
                onSyncSelectionToWildcardRules = { isBlacklist ->
                  viewModel.syncSelectionToWildcardRules(isBlacklist)
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

            composable(ROUTE_METHOD_DETAIL) {
              val currentInspection = inspection
              if (currentInspection != null) {
                MethodDetailScreen(
                  inspection = currentInspection,
                  isSelected = currentInspection.method.id in selectedMethodIds,
                  onToggleSelect = { viewModel.toggleMethodSelection(currentInspection.method.id) },
                  onCopyCCode = { copyText(it) },
                  onBack = { navController.popBackStack() },
                  onOpenClassSmali = {
                    viewModel.inspectClassSmali(
                      currentInspection.dexClass,
                      focusMethodName = currentInspection.method.methodRef.name,
                    )
                    navController.navigate(ROUTE_SMALI_CLASS)
                  },
                )
              }
            }

            composable(ROUTE_SMALI_CLASS) {
              val currentSmali = smaliClassState
              if (currentSmali != null) {
                SmaliClassScreen(
                  state = currentSmali,
                  onCopySmali = { copyText(it) },
                  onExportSmali = { fileName, smaliText ->
                    pendingSmaliExportText = smaliText
                    exportSmaliLauncher.launch(fileName)
                  },
                  onBack = { navController.popBackStack() },
                  selectedMethodIds = selectedMethodIds,
                  onToggleMethodSelection = { viewModel.toggleMethodSelection(it) },
                  onInspectMethodC = { method ->
                    viewModel.inspectMethod(currentSmali.dexClass, method)
                    navController.navigate(ROUTE_METHOD_DETAIL)
                  },
                  smaliSyntaxTheme = appSettings.smaliSyntaxTheme,
                  onSelectSmaliSyntaxTheme = { selectedTheme ->
                    viewModel.updateAppSettings { it.copy(smaliSyntaxTheme = selectedTheme) }
                  },
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
                  buildHubTab = BuildHubTab.PIPELINE
                  navigateTopLevel(ROUTE_BUILD)
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
              val isReplay = navController.previousBackStackEntry != null
              LaunchedEffect(appSettings.hasCompletedOnboarding, isReplay) {
                if (appSettings.hasCompletedOnboarding && !isReplay) {
                  navController.navigate(ROUTE_HOME) {
                    popUpTo(ROUTE_ONBOARDING) { inclusive = true }
                    launchSingleTop = true
                  }
                }
              }
              OnboardingScreen(
                isReplayMode = isReplay || appSettings.hasCompletedOnboarding,
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
                  navController.navigate(ROUTE_HOME) {
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
      onSelectDexEntry = { viewModel.toggleStagedDexEntry(it) },
      onSelectAllDexEntries = { viewModel.selectAllStagedDexEntries() },
      onSelectPrimaryDexEntry = { viewModel.selectPrimaryStagedDexEntry() },
      onConfirmLoad = { viewModel.commitStagedDex() },
      onDismiss = { viewModel.dismissStagingPreview() },
    )
  }
}

@Composable
private fun Dex2cFloatingBottomBar(
  navItems: List<NavDestinationItem>,
  currentRoute: String,
  queuedMethodCount: Int,
  onNavigate: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier =
      modifier
        .fillMaxWidth()
        .windowInsetsPadding(WindowInsets.navigationBars)
        .padding(horizontal = 16.dp, vertical = 8.dp),
    contentAlignment = Alignment.Center,
  ) {
    Surface(
      modifier =
        Modifier
          .widthIn(max = 500.dp)
          .fillMaxWidth()
          .testTag("dex2c_floating_bottom_bar"),
      shape = RoundedCornerShape(28.dp),
      color = MaterialTheme.colorScheme.surfaceContainerHigh,
      tonalElevation = 6.dp,
      shadowElevation = 8.dp,
      border =
        BorderStroke(
          width = 1.dp,
          color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
        ),
    ) {
      Row(
        modifier =
          Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        navItems.forEach { item ->
          val selected = currentRoute == item.route
          val pillColor by
            animateColorAsState(
              targetValue =
                if (selected) {
                  MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.88f)
                } else {
                  Color.Transparent
                },
              animationSpec = ExpressiveMotion.effectsSpring(),
              label = "navPillColor_${item.route}",
            )
          val contentColor by
            animateColorAsState(
              targetValue =
                if (selected) {
                  MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                  MaterialTheme.colorScheme.onSurfaceVariant
                },
              animationSpec = ExpressiveMotion.effectsSpring(),
              label = "navContentColor_${item.route}",
            )
          val iconScale by
            animateFloatAsState(
              targetValue = if (selected) 1.08f else 1.0f,
              animationSpec = ExpressiveMotion.spatialSpring(),
              label = "navIconScale_${item.route}",
            )
          val dotAlpha by
            animateFloatAsState(
              targetValue = if (selected) 1f else 0f,
              animationSpec = ExpressiveMotion.effectsSpring(),
              label = "navDotAlpha_${item.route}",
            )

          val label = stringResource(item.labelRes)
          Column(
            modifier =
              Modifier
                .weight(1f)
                .minimumInteractiveComponentSize()
                .clip(RoundedCornerShape(22.dp))
                .background(pillColor, RoundedCornerShape(22.dp))
                .selectable(
                  selected = selected,
                  onClick = { onNavigate(item.route) },
                  role = Role.Tab,
                )
                .padding(horizontal = 6.dp, vertical = 6.dp)
                .testTag(item.testTag),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
          ) {
            Box(
              modifier = Modifier.graphicsLayer {
                scaleX = iconScale
                scaleY = iconScale
              },
              contentAlignment = Alignment.Center,
            ) {
              val iconVector = if (selected) item.selectedIcon else item.unselectedIcon
              if (item.route == ROUTE_BUILD && queuedMethodCount > 0) {
                BadgedBox(
                  badge = {
                    Badge(
                      containerColor = MaterialTheme.colorScheme.primary,
                      contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) {
                      Text(
                        text = if (queuedMethodCount > 999) "999+" else "$queuedMethodCount",
                        style = MaterialTheme.typography.labelSmall,
                      )
                    }
                  }
                ) {
                  Icon(
                    imageVector = iconVector,
                    contentDescription = label,
                    tint = contentColor,
                    modifier = Modifier.size(20.dp),
                  )
                }
              } else {
                Icon(
                  imageVector = iconVector,
                  contentDescription = label,
                  tint = contentColor,
                  modifier = Modifier.size(20.dp),
                )
              }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = label,
              style = MaterialTheme.typography.labelSmall,
              fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
              color = contentColor,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Box(
              modifier =
                Modifier
                  .size(width = if (selected) 14.dp else 4.dp, height = 3.dp)
                  .graphicsLayer { alpha = dotAlpha }
                  .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = CircleShape,
                  )
            )
          }
        }
      }
    }
  }
}

