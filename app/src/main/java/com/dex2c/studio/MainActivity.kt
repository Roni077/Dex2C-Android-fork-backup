package com.dex2c.studio

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
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import com.dex2c.studio.ui.WindowHeightSizeClass
import com.dex2c.studio.ui.rememberWindowAdaptiveInfo
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import com.dex2c.studio.apk.PickerTab
import com.dex2c.studio.cloud.CloudBuildNotifier
import com.dex2c.studio.cloud.CloudDeepLinkTarget
import com.dex2c.studio.data.AppThemeMode
import com.dex2c.studio.ui.Dex2cViewModel
import com.dex2c.studio.ui.DexLoadState
import com.dex2c.studio.ui.components.ApkPickerSheet
import com.dex2c.studio.ui.components.ApkPreviewSheet
import com.dex2c.studio.ui.components.ExpressiveMotion
import com.dex2c.studio.ui.screens.ClassesScreen
import com.dex2c.studio.ui.screens.CloudBuildScreen
import com.dex2c.studio.ui.screens.HomeScreen
import com.dex2c.studio.ui.screens.MethodDetailScreen
import com.dex2c.studio.ui.screens.OnboardingScreen
import com.dex2c.studio.ui.screens.PipelineConfigScreen
import com.dex2c.studio.ui.screens.SettingsScreen
import com.dex2c.studio.ui.screens.SmaliClassScreen
import com.dex2c.studio.ui.theme.Dex2cTheme
import com.dex2c.studio.ui.theme.EditorDisplaySettings
import com.dex2c.studio.ui.editor.SmaliEditorManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val ROUTE_HOME = "home"
const val ROUTE_CLASSES = "classes"
const val ROUTE_BUILD = "build"
const val ROUTE_CLOUD = "cloud"
const val ROUTE_METHOD_DETAIL = "method_detail"
const val ROUTE_SMALI_CLASS = "smali_class"
const val ROUTE_SETTINGS = "settings"
const val ROUTE_ONBOARDING = "onboarding"

val TOP_LEVEL_ROUTES = setOf(ROUTE_HOME, ROUTE_CLASSES, ROUTE_BUILD, ROUTE_CLOUD)

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
          onDeepLinkConsumed = {
            pendingDeepLink = null
            intent?.removeExtra(CloudBuildNotifier.EXTRA_NAVIGATE_ROUTE)
            intent?.removeExtra(CloudBuildNotifier.EXTRA_CLOUD_RUN_ID)
          },
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
  val selectedMethodCount by viewModel.selectedMethodCount.collectAsStateWithLifecycle()
  val config by viewModel.config.collectAsStateWithLifecycle()
  val inspection by viewModel.inspection.collectAsStateWithLifecycle()
  val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()

  val isPickerOpen by viewModel.isPickerOpen.collectAsStateWithLifecycle()
  val activePickerTab by viewModel.activePickerTab.collectAsStateWithLifecycle()
  val filteredApps by viewModel.filteredInstalledApps.collectAsStateWithLifecycle()
  val isLoadingApps by viewModel.isLoadingApps.collectAsStateWithLifecycle()
  val appSearchQuery by viewModel.appSearchQuery.collectAsStateWithLifecycle()
  val recentFiles by viewModel.recentFiles.collectAsStateWithLifecycle()
  val stagingState by viewModel.stagingState.collectAsStateWithLifecycle()
  val filterMatchSummary by viewModel.filterMatchSummary.collectAsStateWithLifecycle()
  val smaliClassState by viewModel.smaliClassState.collectAsStateWithLifecycle()
  val isCompilingDex by viewModel.isCompilingDex.collectAsStateWithLifecycle()
  val lastCompileError by viewModel.lastCompileError.collectAsStateWithLifecycle()
  val dexEditUndoPrompt by viewModel.dexEditUndoPrompt.collectAsStateWithLifecycle()
  var pendingSmaliExportText by remember { mutableStateOf<String?>(null) }

  val appSettings by viewModel.appSettings.collectAsStateWithLifecycle()
  val appSettingsHydrated by viewModel.appSettingsHydrated.collectAsStateWithLifecycle()
  LaunchedEffect(appSettings) {
    CloudBuildNotifier.ensureNotificationChannel(context, appSettings)
  }
  LaunchedEffect(Unit) {
    withContext(Dispatchers.IO) {
      SmaliEditorManager.ensureInitialized(context)
    }
  }
  DisposableEffect(context, viewModel) {
    val callbacks =
      object : android.content.ComponentCallbacks2 {
        override fun onConfigurationChanged(newConfig: android.content.res.Configuration) = Unit

        override fun onLowMemory() {
          viewModel.onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)
        }

        override fun onTrimMemory(level: Int) {
          viewModel.onTrimMemory(level)
        }
      }
    context.applicationContext.registerComponentCallbacks(callbacks)
    onDispose {
      context.applicationContext.unregisterComponentCallbacks(callbacks)
    }
  }
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

  val exportSmaliLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
      val text = pendingSmaliExportText
      pendingSmaliExportText = null
      if (uri != null && text != null) {
        viewModel.exportSmaliToUri(uri, text)
      }
    }

  val exportPatchedDexLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
      if (uri != null) {
        viewModel.exportPatchedDexToUri(uri)
      }
    }

  val exportPatchedApkLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")) { uri ->
      if (uri != null) {
        viewModel.exportPatchedApkToUri(uri)
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

  LaunchedEffect(dexEditUndoPrompt) {
    val prompt = dexEditUndoPrompt ?: return@LaunchedEffect
    val result =
      snackbarHostState.showSnackbar(
        message = prompt.message,
        actionLabel = "Undo",
        duration = SnackbarDuration.Long,
      )
    if (result == SnackbarResult.ActionPerformed) {
      viewModel.undoLastDexModification()
    }
    viewModel.clearDexEditUndoPrompt()
  }

  LaunchedEffect(pendingCloudApkSave) {
    val pending = pendingCloudApkSave ?: return@LaunchedEffect
    saveCloudApkLauncher.launch(pending.fileName)
  }

  val navItems =
    remember {
      listOf(
        NavDestinationItem(ROUTE_HOME, R.string.nav_home, Icons.Rounded.Home, Icons.Outlined.Home, "nav_home"),
        NavDestinationItem(ROUTE_CLASSES, R.string.nav_classes, Icons.Rounded.DataObject, Icons.Outlined.DataObject, "nav_classes"),
        NavDestinationItem(ROUTE_BUILD, R.string.nav_build, Icons.Rounded.Code, Icons.Outlined.Code, "nav_build"),
        NavDestinationItem(ROUTE_CLOUD, R.string.nav_cloud, Icons.Rounded.CloudQueue, Icons.Outlined.CloudQueue, "nav_cloud"),
      )
    }

  val backStackEntry by navController.currentBackStackEntryAsState()
  val currentRoute = backStackEntry?.destination?.route ?: ROUTE_HOME
  val showTopBarAndNav =
    appSettingsHydrated &&
      currentRoute != ROUTE_METHOD_DETAIL &&
      currentRoute != ROUTE_SMALI_CLASS &&
      currentRoute != ROUTE_SETTINGS &&
      currentRoute != ROUTE_ONBOARDING

  fun navigateTopLevel(route: String) {
    if (navController.currentDestination?.route == route) return
    navController.navigate(route) {
      popUpTo(navController.graph.findStartDestination().id) { saveState = true }
      launchSingleTop = true
      restoreState = true
    }
  }

  val onNavigateTopLevel = remember<(String) -> Unit>(navController) {
    { route -> navigateTopLevel(route) }
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
    val adaptiveInfo = rememberWindowAdaptiveInfo(maxWidth, maxHeight)
    val useRail = adaptiveInfo.useNavRail

    Scaffold(
      modifier = Modifier.fillMaxSize(),
      contentWindowInsets = WindowInsets.safeDrawing,
      snackbarHost = { Dex2cThemedSnackbarHost(snackbarHostState) },
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
            queuedMethodCount = selectedMethodCount,
            onNavigate = onNavigateTopLevel,
          )
        }
      },
    ) { innerPadding ->
      Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        if (showTopBarAndNav && useRail) {
          val isCompactLandscape = adaptiveInfo.isLandscape && adaptiveInfo.heightSizeClass == WindowHeightSizeClass.COMPACT
          NavigationRail(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxHeight(),
          ) {
            navItems.forEach { item ->
              val selected = currentRoute == item.route
              NavigationRailItem(
                selected = selected,
                onClick = { onNavigateTopLevel(item.route) },
                modifier = Modifier.testTag(item.testTag),
                alwaysShowLabel = !isCompactLandscape,
                icon = {
                  if (item.route == ROUTE_BUILD && selectedMethodCount > 0) {
                    BadgedBox(badge = { Badge { Text("$selectedMethodCount") } }) {
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
                label = if (!isCompactLandscape) { { Text(stringResource(item.labelRes)) } } else null,
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
                val fromRoute = initialState.destination.route
                val toRoute = targetState.destination.route
                if (fromRoute == ROUTE_ONBOARDING || toRoute == ROUTE_ONBOARDING) {
                  EnterTransition.None
                } else if (fromRoute in TOP_LEVEL_ROUTES && toRoute in TOP_LEVEL_ROUTES) {
                  ExpressiveMotion.topLevelFadeThroughEnter()
                } else {
                  val fromIdx = routeOrder.indexOf(fromRoute).coerceAtLeast(0)
                  val toIdx = routeOrder.indexOf(toRoute).coerceAtLeast(0)
                  ExpressiveMotion.navEnterTransition(forward = toIdx >= fromIdx)
                }
              },
              exitTransition = {
                val fromRoute = initialState.destination.route
                val toRoute = targetState.destination.route
                if (fromRoute == ROUTE_ONBOARDING || toRoute == ROUTE_ONBOARDING) {
                  ExitTransition.None
                } else if (fromRoute in TOP_LEVEL_ROUTES && toRoute in TOP_LEVEL_ROUTES) {
                  ExpressiveMotion.topLevelFadeThroughExit()
                } else {
                  val fromIdx = routeOrder.indexOf(fromRoute).coerceAtLeast(0)
                  val toIdx = routeOrder.indexOf(toRoute).coerceAtLeast(0)
                  ExpressiveMotion.navExitTransition(forward = toIdx >= fromIdx)
                }
              },
              popEnterTransition = {
                val fromRoute = initialState.destination.route
                val toRoute = targetState.destination.route
                if (fromRoute == ROUTE_ONBOARDING || toRoute == ROUTE_ONBOARDING) {
                  EnterTransition.None
                } else if (fromRoute in TOP_LEVEL_ROUTES && toRoute in TOP_LEVEL_ROUTES) {
                  ExpressiveMotion.topLevelFadeThroughEnter()
                } else {
                  val fromIdx = routeOrder.indexOf(fromRoute).coerceAtLeast(0)
                  val toIdx = routeOrder.indexOf(toRoute).coerceAtLeast(0)
                  ExpressiveMotion.navEnterTransition(forward = toIdx >= fromIdx)
                }
              },
              popExitTransition = {
                val fromRoute = initialState.destination.route
                val toRoute = targetState.destination.route
                if (fromRoute == ROUTE_ONBOARDING || toRoute == ROUTE_ONBOARDING) {
                  ExitTransition.None
                } else if (fromRoute in TOP_LEVEL_ROUTES && toRoute in TOP_LEVEL_ROUTES) {
                  ExpressiveMotion.topLevelFadeThroughExit()
                } else {
                  val fromIdx = routeOrder.indexOf(fromRoute).coerceAtLeast(0)
                  val toIdx = routeOrder.indexOf(toRoute).coerceAtLeast(0)
                  ExpressiveMotion.navExitTransition(forward = toIdx >= fromIdx)
                }
              },
            ) {
            composable(ROUTE_HOME) {
              HomeScreen(
                loadState = loadState,
                selectedMethodCount = selectedMethodCount,
                recentFiles = recentFiles,
                onOpenPicker = { tab -> viewModel.openPicker(tab) },
                onSelectRecent = { viewModel.stageFromRecentItem(it) },
                onAutoSelectAppMethods = { viewModel.autoSelectAppMethods() },
                onClearSelection = { viewModel.clearSelection() },
                onNavigateToClasses = {
                  navigateTopLevel(ROUTE_CLASSES)
                },
              )
            }

            composable(ROUTE_CLASSES) {
              val filteredClasses by viewModel.filteredClasses.collectAsStateWithLifecycle()
              val filterState by viewModel.filterState.collectAsStateWithLifecycle()
              val selectedMethodIds by viewModel.selectedMethodIds.collectAsStateWithLifecycle()
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
                onInspectClassSmali = { cls ->
                  viewModel.openClassInViewer(cls)
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
                onExportPatchedDex = {
                  val activeEntry = (loadState as? DexLoadState.Ready)?.activeEntryName ?: "classes.dex"
                  exportPatchedDexLauncher.launch("patched_$activeEntry")
                },
                onExportPatchedApk = {
                  val baseName =
                    ((loadState as? DexLoadState.Ready)?.sourceLabel ?: "app")
                      .removeSuffix(".apk")
                      .removeSuffix(".dex")
                      .replace(Regex("[^A-Za-z0-9._-]"), "_")
                  exportPatchedApkLauncher.launch("${baseName}_patched_unsigned.apk")
                },
                onDeleteDexClass = { cls ->
                  viewModel.deleteDexClasses(setOf(cls.descriptor), cls.sourceEntryName)
                },
                onSelectAllClasses = { viewModel.selectAllClasses(it) },
                onDeselectAllClasses = { viewModel.deselectAllClasses(it) },
                onInvertClassSelection = { viewModel.invertClassSelection(it) },
                smaliClassState = smaliClassState,
                smaliSyntaxTheme = appSettings.smaliSyntaxTheme,
                onSelectClassInTwoPane = { cls -> viewModel.openClassInViewer(cls) },
              )
            }

            composable(ROUTE_BUILD) {
              PipelineConfigScreen(
                config = config,
                hasLoadedDex = loadState is DexLoadState.Ready,
                filterMatchSummary = filterMatchSummary,
                onUpdateConfig = { viewModel.updateConfig(it) },
                onImportFilterTxt = { importFilterLauncher.launch(arrayOf("text/*", "*/*")) },
                onImportDccCfg = { importDccCfgLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
                onApplyFilterRules = { viewModel.applyFilterRules() },
                selectedMethodCount = selectedMethodCount,
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
              LaunchedEffect(currentInspection) {
                if (currentInspection != null) {
                  viewModel.openClassInViewer(
                    currentInspection.dexClass,
                    focusMethodName = currentInspection.method.methodRef.name,
                  )
                  navController.navigate(ROUTE_SMALI_CLASS) {
                    popUpTo(ROUTE_METHOD_DETAIL) { inclusive = true }
                  }
                } else {
                  navController.popBackStack()
                }
              }
            }

            composable(ROUTE_SMALI_CLASS) {
              DisposableEffect(Unit) {
                onDispose {
                  val activeRoutes = navController.currentBackStack.value.mapNotNull { it.destination.route }
                  if (ROUTE_SMALI_CLASS !in activeRoutes) {
                    viewModel.clearSmaliInspection()
                  }
                }
              }
              val currentSmali = smaliClassState
              LaunchedEffect(currentSmali) {
                if (currentSmali == null && navController.currentDestination?.route == ROUTE_SMALI_CLASS) {
                  navController.popBackStack()
                }
              }
              if (currentSmali != null) {
                SmaliClassScreen(
                  state = currentSmali,
                  onBack = {
                    viewModel.clearSmaliInspection()
                    navController.popBackStack()
                  },
                  smaliSyntaxTheme = appSettings.smaliSyntaxTheme,
                  onCopySmali = { copyText(it) },
                  onExportSmali = { fileName, smaliText ->
                    pendingSmaliExportText = smaliText
                    exportSmaliLauncher.launch(fileName)
                  },
                  dexEditingEnabled = BuildConfig.ENABLE_DEX_EDITING,
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
          Dex2cFloatingBottomBarItem(
            item = item,
            selected = currentRoute == item.route,
            queuedMethodCount = queuedMethodCount,
            onNavigate = onNavigate,
          )
        }
      }
    }
  }
}

@Composable
private fun RowScope.Dex2cFloatingBottomBarItem(
  item: NavDestinationItem,
  selected: Boolean,
  queuedMethodCount: Int,
  onNavigate: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
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
  val dotWidth by
    animateDpAsState(
      targetValue = if (selected) 14.dp else 0.dp,
      animationSpec = ExpressiveMotion.spatialSpring(),
      label = "navDotWidth_${item.route}",
    )

  val label = stringResource(item.labelRes)
  Column(
    modifier =
      modifier
        .weight(1f)
        .minimumInteractiveComponentSize()
        .clip(RoundedCornerShape(22.dp))
        .drawBehind {
          if (pillColor.alpha > 0f) {
            drawRoundRect(
              color = pillColor,
              cornerRadius = CornerRadius(22.dp.toPx(), 22.dp.toPx()),
            )
          }
        }
        .selectable(
          selected = selected,
          onClick = {
            if (!selected) {
              onNavigate(item.route)
            }
          },
          role = Role.Tab,
        )
        .padding(horizontal = 6.dp, vertical = 6.dp)
        .testTag(item.testTag),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Box(
      modifier =
        Modifier.graphicsLayer {
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
          .size(width = dotWidth, height = 3.dp)
          .graphicsLayer { alpha = dotAlpha }
          .background(
            color = MaterialTheme.colorScheme.primary,
            shape = CircleShape,
          )
    )
  }
}

fun isErrorStatusMessage(message: String): Boolean {
  val lower = message.lowercase()
  return lower.contains("failed") ||
    lower.contains("error") ||
    lower.contains("unable") ||
    lower.contains("invalid") ||
    lower.contains("denied") ||
    lower.contains("grant notification permission")
}

@Composable
fun Dex2cThemedSnackbarHost(
  hostState: SnackbarHostState,
  modifier: Modifier = Modifier,
) {
  SnackbarHost(
    hostState = hostState,
    modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp),
  ) { snackbarData ->
    val message = snackbarData.visuals.message
    val isError = remember(message) { isErrorStatusMessage(message) }
    val containerColor =
      if (isError) MaterialTheme.colorScheme.errorContainer
      else MaterialTheme.colorScheme.surfaceContainerHigh
    val contentColor =
      if (isError) MaterialTheme.colorScheme.onErrorContainer
      else MaterialTheme.colorScheme.onSurface
    val accentColor =
      if (isError) MaterialTheme.colorScheme.error
      else MaterialTheme.colorScheme.primary
    val iconBadgeColor =
      if (isError) MaterialTheme.colorScheme.error.copy(alpha = 0.18f)
      else MaterialTheme.colorScheme.primaryContainer

    Surface(
      shape = RoundedCornerShape(18.dp),
      color = containerColor,
      contentColor = contentColor,
      tonalElevation = 6.dp,
      shadowElevation = 6.dp,
      border = BorderStroke(1.dp, accentColor.copy(alpha = 0.65f)),
      modifier =
        Modifier.fillMaxWidth()
          .widthIn(max = 560.dp)
          .testTag("dex2c_themed_snackbar"),
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Box(
          modifier =
            Modifier.size(32.dp)
              .clip(CircleShape)
              .background(iconBadgeColor),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector =
              if (isError) Icons.Rounded.ErrorOutline else Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(18.dp),
          )
        }
        Text(
          text = message,
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Medium,
          color = contentColor,
          modifier = Modifier.weight(1f),
        )
        val actionLabel = snackbarData.visuals.actionLabel
        if (!actionLabel.isNullOrBlank()) {
          Spacer(Modifier.width(4.dp))
          androidx.compose.material3.TextButton(
            onClick = { snackbarData.performAction() }
          ) {
            Text(
              text = actionLabel,
              color = accentColor,
              fontWeight = FontWeight.Bold,
            )
          }
        }
      }
    }
  }
}


