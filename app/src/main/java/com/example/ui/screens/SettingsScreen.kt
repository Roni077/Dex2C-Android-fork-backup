package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.os.Build
import com.example.BuildConfig
import com.example.R
import com.example.cloud.CloudSettings
import com.example.data.AppSettings
import com.example.data.CURATED_ACCENT_PRESETS
import com.example.data.normalizeHexColor
import com.example.transpiler.JniBindingStyle
import com.example.transpiler.TranspilerConfig
import com.example.ui.components.ExpressiveMotion
import com.example.ui.components.TagPill
import com.example.ui.components.expressivePressScale

enum class SettingsSection(
  val title: String,
  val groupName: String,
  val icon: ImageVector,
) {
  APPEARANCE("Appearance & Theme", "General & Editor", Icons.Rounded.Palette),
  CODE_EDITOR("Code Editor & Viewer", "General & Editor", Icons.Rounded.Code),
  DEX_PARSER("DEX Parser & Workbench", "Analysis & Pipeline", Icons.Rounded.Memory),
  GITHUB_CLOUD("GitHub Cloud & Workspace Repo", "Cloud Build & Alerts", Icons.Rounded.CloudQueue),
  NDK_SIGNING("NDK Toolchain & APK Signing", "Cloud Build & Alerts", Icons.Rounded.Build),
  NOTIFICATIONS("Notifications & Alerts", "Cloud Build & Alerts", Icons.Rounded.NotificationsActive),
  STORAGE_RESET("Storage, Cache & Reset", "Maintenance & Info", Icons.Rounded.Storage),
  ABOUT("About & Diagnostics", "Maintenance & Info", Icons.Rounded.Info),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
  appSettings: AppSettings,
  cloudSettings: CloudSettings,
  transpilerConfig: TranspilerConfig,
  recentHistoryCount: Int,
  hasNotificationPermission: Boolean,
  onUpdateAppSettings: ((AppSettings) -> AppSettings) -> Unit,
  onUpdateCloudSettings: ((CloudSettings) -> CloudSettings) -> Unit,
  onToggleNotifications: (Boolean) -> Unit,
  onRequestNotificationPermission: () -> Unit,
  onSendTestNotification: () -> Unit,
  onSyncWorkflowToRepo: () -> Unit,
  onStartDeviceLogin: () -> Unit,
  onSignOutGitHub: () -> Unit,
  onOpenGitHubAppSettings: () -> Unit,
  onGoToPipelineConfig: () -> Unit,
  onOpenRustBridge: () -> Unit,
  onClearRecentHistory: () -> Unit,
  onResetAllSettings: (clearGitHubSession: Boolean) -> Unit,
  onReplayOnboarding: () -> Unit,
  onCopyText: (String) -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var activeSubScreen by rememberSaveable { mutableStateOf<SettingsSection?>(null) }

  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val isTwoPane = maxWidth >= 840.dp
    val detailSection = activeSubScreen ?: SettingsSection.APPEARANCE

    BackHandler {
      if (!isTwoPane && activeSubScreen != null) {
        activeSubScreen = null
      } else {
        onBack()
      }
    }

    val topBarTitle =
      if (!isTwoPane && activeSubScreen != null) activeSubScreen!!.title
      else stringResource(R.string.settings_title)
    val topBarSubtitle =
      if (!isTwoPane && activeSubScreen != null) activeSubScreen!!.groupName
      else stringResource(R.string.settings_subtitle)

    Scaffold(
      modifier = Modifier.fillMaxSize(),
      contentWindowInsets = WindowInsets(0, 0, 0, 0),
      topBar = {
        TopAppBar(
          windowInsets = WindowInsets(0, 0, 0, 0),
          title = {
            Column {
              Text(
                text = topBarTitle,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text = topBarSubtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          },
          navigationIcon = {
            IconButton(
              onClick = {
                if (!isTwoPane && activeSubScreen != null) {
                  activeSubScreen = null
                } else {
                  onBack()
                }
              },
              modifier = Modifier.minimumInteractiveComponentSize().testTag("settings_back_btn"),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
              )
            }
          },
          colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
      },
    ) { innerPadding ->
      if (isTwoPane) {
        Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
          Box(modifier = Modifier.width(320.dp).fillMaxHeight()) {
            SettingsHubList(
              appSettings = appSettings,
              cloudSettings = cloudSettings,
              transpilerConfig = transpilerConfig,
              recentHistoryCount = recentHistoryCount,
              hasNotificationPermission = hasNotificationPermission,
              selectedSection = detailSection,
              showSelectionHighlight = true,
              onSelectSection = { activeSubScreen = it },
              onGoToPipelineConfig = onGoToPipelineConfig,
            )
          }
          VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
          Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            AnimatedContent(
              targetState = detailSection,
              transitionSpec = { ExpressiveMotion.sharedAxisHorizontal(true) },
              label = "settingsTabletDetail",
            ) { currentDetail ->
              SettingsDetailHost(
                section = currentDetail,
                appSettings = appSettings,
                cloudSettings = cloudSettings,
                transpilerConfig = transpilerConfig,
                recentHistoryCount = recentHistoryCount,
                hasNotificationPermission = hasNotificationPermission,
                onUpdateAppSettings = onUpdateAppSettings,
                onUpdateCloudSettings = onUpdateCloudSettings,
                onToggleNotifications = onToggleNotifications,
                onRequestNotificationPermission = onRequestNotificationPermission,
                onSendTestNotification = onSendTestNotification,
                onSyncWorkflowToRepo = onSyncWorkflowToRepo,
                onStartDeviceLogin = onStartDeviceLogin,
                onSignOutGitHub = onSignOutGitHub,
                onOpenGitHubAppSettings = onOpenGitHubAppSettings,
                onGoToPipelineConfig = onGoToPipelineConfig,
                onOpenRustBridge = onOpenRustBridge,
                onClearRecentHistory = onClearRecentHistory,
                onResetAllSettings = onResetAllSettings,
                onReplayOnboarding = onReplayOnboarding,
                onCopyText = onCopyText,
              )
            }
          }
        }
      } else {
        Box(
          modifier = Modifier.fillMaxSize().padding(innerPadding),
          contentAlignment = Alignment.TopCenter,
        ) {
          Box(modifier = Modifier.fillMaxSize().widthIn(max = 840.dp)) {
            AnimatedContent(
              targetState = activeSubScreen,
              transitionSpec = { ExpressiveMotion.sharedAxisHorizontal(targetState != null) },
              label = "settingsCompactTransition",
            ) { currentSub ->
              if (currentSub == null) {
                SettingsHubList(
                  appSettings = appSettings,
                  cloudSettings = cloudSettings,
                  transpilerConfig = transpilerConfig,
                  recentHistoryCount = recentHistoryCount,
                  hasNotificationPermission = hasNotificationPermission,
                  selectedSection = null,
                  showSelectionHighlight = false,
                  onSelectSection = { activeSubScreen = it },
                  onGoToPipelineConfig = onGoToPipelineConfig,
                )
              } else {
                SettingsDetailHost(
                  section = currentSub,
                  appSettings = appSettings,
                  cloudSettings = cloudSettings,
                  transpilerConfig = transpilerConfig,
                  recentHistoryCount = recentHistoryCount,
                  hasNotificationPermission = hasNotificationPermission,
                  onUpdateAppSettings = onUpdateAppSettings,
                  onUpdateCloudSettings = onUpdateCloudSettings,
                  onToggleNotifications = onToggleNotifications,
                  onRequestNotificationPermission = onRequestNotificationPermission,
                  onSendTestNotification = onSendTestNotification,
                  onSyncWorkflowToRepo = onSyncWorkflowToRepo,
                  onStartDeviceLogin = onStartDeviceLogin,
                  onSignOutGitHub = onSignOutGitHub,
                  onOpenGitHubAppSettings = onOpenGitHubAppSettings,
                  onGoToPipelineConfig = onGoToPipelineConfig,
                  onOpenRustBridge = onOpenRustBridge,
                  onClearRecentHistory = onClearRecentHistory,
                  onResetAllSettings = onResetAllSettings,
                  onReplayOnboarding = onReplayOnboarding,
                  onCopyText = onCopyText,
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun SettingsHubList(
  appSettings: AppSettings,
  cloudSettings: CloudSettings,
  transpilerConfig: TranspilerConfig,
  recentHistoryCount: Int,
  hasNotificationPermission: Boolean,
  selectedSection: SettingsSection?,
  showSelectionHighlight: Boolean,
  onSelectSection: (SettingsSection) -> Unit,
  onGoToPipelineConfig: () -> Unit,
) {
  val groupedSections = SettingsSection.entries.groupBy { it.groupName }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    groupedSections.forEach { (groupTitle, sections) ->
      item(key = groupTitle) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = groupTitle,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 4.dp),
          )
          Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
          ) {
            Column {
              sections.forEachIndexed { idx, section ->
                val isSelected = showSelectionHighlight && selectedSection == section
                val summary =
                  sectionSummary(
                    section = section,
                    appSettings = appSettings,
                    cloudSettings = cloudSettings,
                    recentHistoryCount = recentHistoryCount,
                    hasNotificationPermission = hasNotificationPermission,
                  )
                SettingsHubRow(
                  title = section.title,
                  icon = section.icon,
                  summary = summary,
                  badgeText = sectionBadge(section, cloudSettings, hasNotificationPermission),
                  selected = isSelected,
                  testTag = "settings_hub_row_${section.name.lowercase()}",
                  onClick = { onSelectSection(section) },
                )
                if (idx < sections.lastIndex || groupTitle == "Analysis & Pipeline") {
                  HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    modifier = Modifier.padding(horizontal = 18.dp),
                  )
                }
              }
              if (groupTitle == "Analysis & Pipeline") {
                val jniLabel =
                  if (transpilerConfig.jniStyle == JniBindingStyle.REGISTER_NATIVES) "RegisterNatives"
                  else "Java_* Exports"
                val langLabel = transpilerConfig.targetLanguage.label
                SettingsHubRow(
                  title = "Transpiler Pipeline Shortcut",
                  icon = Icons.Rounded.Tune,
                  summary = "${transpilerConfig.moduleName} • $jniLabel • $langLabel",
                  badgeText = "Open",
                  selected = false,
                  testTag = "settings_hub_row_pipeline_shortcut",
                  onClick = onGoToPipelineConfig,
                )
              }
            }
          }
        }
      }
    }
    item { Spacer(Modifier.height(24.dp)) }
  }
}

@Composable
private fun SettingsHubRow(
  title: String,
  icon: ImageVector,
  summary: String,
  badgeText: String?,
  selected: Boolean,
  testTag: String,
  onClick: () -> Unit,
) {
  val interactionSource = remember { MutableInteractionSource() }
  Surface(
    color =
      if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f)
      else MaterialTheme.colorScheme.surfaceContainer,
    modifier =
      Modifier.fillMaxWidth()
        .expressivePressScale(interactionSource, 0.985f)
        .clickable(interactionSource = interactionSource, indication = androidx.compose.material3.ripple(), onClick = onClick)
        .testTag(testTag),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Surface(
        shape = RoundedCornerShape(12.dp),
        color =
          if (selected) MaterialTheme.colorScheme.primaryContainer
          else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.size(40.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
          )
        }
      }
      Column(modifier = Modifier.weight(1f)) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          if (badgeText != null) {
            TagPill(text = badgeText)
          }
        }
        Spacer(Modifier.height(2.dp))
        Text(
          text = summary,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Icon(
        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
        contentDescription = "Open $title",
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(20.dp),
      )
    }
  }
}

@Composable
private fun SettingsDetailHost(
  section: SettingsSection,
  appSettings: AppSettings,
  cloudSettings: CloudSettings,
  transpilerConfig: TranspilerConfig,
  recentHistoryCount: Int,
  hasNotificationPermission: Boolean,
  onUpdateAppSettings: ((AppSettings) -> AppSettings) -> Unit,
  onUpdateCloudSettings: ((CloudSettings) -> CloudSettings) -> Unit,
  onToggleNotifications: (Boolean) -> Unit,
  onRequestNotificationPermission: () -> Unit,
  onSendTestNotification: () -> Unit,
  onSyncWorkflowToRepo: () -> Unit,
  onStartDeviceLogin: () -> Unit,
  onSignOutGitHub: () -> Unit,
  onOpenGitHubAppSettings: () -> Unit,
  onGoToPipelineConfig: () -> Unit,
  onOpenRustBridge: () -> Unit,
  onClearRecentHistory: () -> Unit,
  onResetAllSettings: (clearGitHubSession: Boolean) -> Unit,
  onReplayOnboarding: () -> Unit,
  onCopyText: (String) -> Unit,
) {
  when (section) {
    SettingsSection.APPEARANCE ->
      SettingsAppearanceSubScreen(
        appSettings = appSettings,
        onUpdateAppSettings = onUpdateAppSettings,
      )
    SettingsSection.CODE_EDITOR ->
      SettingsCodeEditorSubScreen(
        appSettings = appSettings,
        onUpdateAppSettings = onUpdateAppSettings,
      )
    SettingsSection.DEX_PARSER ->
      SettingsDexParserSubScreen(
        appSettings = appSettings,
        transpilerConfig = transpilerConfig,
        onUpdateAppSettings = onUpdateAppSettings,
        onGoToPipelineConfig = onGoToPipelineConfig,
        onOpenRustBridge = onOpenRustBridge,
      )
    SettingsSection.GITHUB_CLOUD ->
      SettingsGitHubCloudSubScreen(
        cloudSettings = cloudSettings,
        transpilerConfig = transpilerConfig,
        onUpdateCloudSettings = onUpdateCloudSettings,
        onSyncWorkflowToRepo = onSyncWorkflowToRepo,
        onStartDeviceLogin = onStartDeviceLogin,
        onSignOutGitHub = onSignOutGitHub,
        onOpenGitHubAppSettings = onOpenGitHubAppSettings,
        onCopyText = onCopyText,
      )
    SettingsSection.NDK_SIGNING ->
      SettingsNdkSigningSubScreen(
        cloudSettings = cloudSettings,
        onUpdateCloudSettings = onUpdateCloudSettings,
      )
    SettingsSection.NOTIFICATIONS ->
      SettingsNotificationsSubScreen(
        cloudSettings = cloudSettings,
        hasNotificationPermission = hasNotificationPermission,
        onToggleNotifications = onToggleNotifications,
        onRequestPermission = onRequestNotificationPermission,
        onSendTestNotification = onSendTestNotification,
      )
    SettingsSection.STORAGE_RESET ->
      SettingsStorageResetSubScreen(
        recentHistoryCount = recentHistoryCount,
        isGitHubSignedIn = cloudSettings.isAuthenticated,
        onClearRecentHistory = onClearRecentHistory,
        onResetAllSettings = onResetAllSettings,
      )
    SettingsSection.ABOUT ->
      SettingsAboutSubScreen(
        transpilerConfig = transpilerConfig,
        onReplayOnboarding = onReplayOnboarding,
      )
  }
}

private fun sectionSummary(
  section: SettingsSection,
  appSettings: AppSettings,
  cloudSettings: CloudSettings,
  recentHistoryCount: Int,
  hasNotificationPermission: Boolean,
): String =
  when (section) {
    SettingsSection.APPEARANCE -> {
      val paletteLabel =
        if (appSettings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          "Dynamic"
        } else {
          CURATED_ACCENT_PRESETS.firstOrNull {
            it.hex.equals(appSettings.accentSeedHex, ignoreCase = true)
          }?.name ?: normalizeHexColor(appSettings.accentSeedHex)
        }
      "${appSettings.themeMode.label} • $paletteLabel"
    }
    SettingsSection.CODE_EDITOR ->
      "${appSettings.codeFontSizeSp}sp • ${if (appSettings.showLineNumbers) "Line numbers on" else "Line numbers off"} • ${appSettings.maxCodePreviewLines} lines"
    SettingsSection.DEX_PARSER ->
      "${if (appSettings.defaultHideFramework) "App-only default" else "All classes"} • Auto-select ${appSettings.autoSelectMethodLimit} • ${if (appSettings.showOutputScreen) "Output ON" else "Output OFF"}"
    SettingsSection.GITHUB_CLOUD ->
      if (cloudSettings.isAuthenticated) "@${cloudSettings.githubUsername} • ${cloudSettings.repoName}"
      else "Device Flow OAuth & private dex2c.yml workspace"
    SettingsSection.NDK_SIGNING ->
      "NDK ${cloudSettings.ndkVersion} • JDK ${cloudSettings.jdkVersion} • ${cloudSettings.signingMode.label}"
    SettingsSection.NOTIFICATIONS ->
      if (!cloudSettings.notificationsEnabled) "Build alerts disabled"
      else if (hasNotificationPermission) "Enabled • ${cloudSettings.pollIntervalSeconds}s background poll"
      else "Enabled • Notification permission needed"
    SettingsSection.STORAGE_RESET -> {
      val entryWord = if (recentHistoryCount == 1) "entry" else "entries"
      "$recentHistoryCount recent $entryWord • Clear cache & reset"
    }
    SettingsSection.ABOUT ->
      "v${BuildConfig.VERSION_NAME} • DEX 035–041 • Kirlif/d2c Cloud"
  }

private fun sectionBadge(
  section: SettingsSection,
  cloudSettings: CloudSettings,
  hasNotificationPermission: Boolean,
): String? =
  when (section) {
    SettingsSection.GITHUB_CLOUD -> if (cloudSettings.isAuthenticated) "Signed In" else null
    SettingsSection.NOTIFICATIONS ->
      if (cloudSettings.notificationsEnabled && !hasNotificationPermission) "Action" else null
    else -> null
  }
