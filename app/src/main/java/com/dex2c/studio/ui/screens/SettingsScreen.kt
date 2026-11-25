package com.dex2c.studio.ui.screens

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
import com.dex2c.studio.BuildConfig
import com.dex2c.studio.R
import com.dex2c.studio.cloud.CloudSettings
import com.dex2c.studio.data.AppLanguage
import com.dex2c.studio.data.AppSettings
import com.dex2c.studio.data.CURATED_ACCENT_PRESETS
import com.dex2c.studio.data.normalizeHexColor
import com.dex2c.studio.transpiler.JniBindingStyle
import com.dex2c.studio.transpiler.TranspilerConfig
import com.dex2c.studio.ui.components.ExpressiveMotion
import com.dex2c.studio.ui.components.TagPill
import com.dex2c.studio.ui.components.expressivePressScale

enum class SettingsSection(
  val titleRes: Int,
  val groupNameRes: Int,
  val icon: ImageVector,
  val title: String,
  val groupName: String,
) {
  APPEARANCE(
    titleRes = R.string.settings_section_appearance,
    groupNameRes = R.string.settings_group_general_editor,
    icon = Icons.Rounded.Palette,
    title = "Appearance",
    groupName = "General & Editor",
  ),
  CODE_EDITOR(
    titleRes = R.string.settings_section_code_editor,
    groupNameRes = R.string.settings_group_general_editor,
    icon = Icons.Rounded.Code,
    title = "Code Editor & Viewer",
    groupName = "General & Editor",
  ),
  DEX_PARSER(
    titleRes = R.string.settings_section_dex_parser,
    groupNameRes = R.string.settings_group_analysis_pipeline,
    icon = Icons.Rounded.Memory,
    title = "DEX Parser & Home",
    groupName = "Analysis & Pipeline",
  ),
  GITHUB_CLOUD(
    titleRes = R.string.settings_section_github_cloud,
    groupNameRes = R.string.settings_group_cloud_alerts,
    icon = Icons.Rounded.CloudQueue,
    title = "GitHub Cloud & Workspace Repo",
    groupName = "Cloud Build & Alerts",
  ),
  NDK_SIGNING(
    titleRes = R.string.settings_section_ndk_signing,
    groupNameRes = R.string.settings_group_cloud_alerts,
    icon = Icons.Rounded.Build,
    title = "NDK Toolchain & APK Signing",
    groupName = "Cloud Build & Alerts",
  ),
  NOTIFICATIONS(
    titleRes = R.string.settings_section_notifications,
    groupNameRes = R.string.settings_group_cloud_alerts,
    icon = Icons.Rounded.NotificationsActive,
    title = "Notifications & Alerts",
    groupName = "Cloud Build & Alerts",
  ),
  STORAGE_RESET(
    titleRes = R.string.settings_section_storage_reset,
    groupNameRes = R.string.settings_group_maintenance_info,
    icon = Icons.Rounded.Storage,
    title = "Storage, Cache & Reset",
    groupName = "Maintenance & Info",
  ),
  ABOUT(
    titleRes = R.string.settings_section_about,
    groupNameRes = R.string.settings_group_maintenance_info,
    icon = Icons.Rounded.Info,
    title = "About & Diagnostics",
    groupName = "Maintenance & Info",
  ),
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
  onSyncWorkflowToRepo: () -> Unit,
  onStartDeviceLogin: () -> Unit,
  onSignOutGitHub: () -> Unit,
  onOpenGitHubAppSettings: () -> Unit,
  onGoToPipelineConfig: () -> Unit,
  onClearRecentHistory: () -> Unit,
  onResetAllSettings: (clearGitHubSession: Boolean) -> Unit,
  onTrimMemory: () -> Unit = {},
  onClearDiskCache: () -> Unit = {},
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
                onSyncWorkflowToRepo = onSyncWorkflowToRepo,
                onStartDeviceLogin = onStartDeviceLogin,
                onSignOutGitHub = onSignOutGitHub,
                onOpenGitHubAppSettings = onOpenGitHubAppSettings,
                onGoToPipelineConfig = onGoToPipelineConfig,
                onClearRecentHistory = onClearRecentHistory,
                onResetAllSettings = onResetAllSettings,
                onTrimMemory = onTrimMemory,
                onClearDiskCache = onClearDiskCache,
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
                  onSyncWorkflowToRepo = onSyncWorkflowToRepo,
                  onStartDeviceLogin = onStartDeviceLogin,
                  onSignOutGitHub = onSignOutGitHub,
                  onOpenGitHubAppSettings = onOpenGitHubAppSettings,
                  onGoToPipelineConfig = onGoToPipelineConfig,
                  onClearRecentHistory = onClearRecentHistory,
                  onResetAllSettings = onResetAllSettings,
                  onTrimMemory = onTrimMemory,
                  onClearDiskCache = onClearDiskCache,
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
  val groupedSections = SettingsSection.entries.groupBy { it.groupNameRes }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    groupedSections.forEach { (groupTitleRes, sections) ->
      item(key = groupTitleRes) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = stringResource(groupTitleRes),
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
                  title = stringResource(section.titleRes),
                  icon = section.icon,
                  summary = summary,
                  badgeText = sectionBadge(section, cloudSettings, hasNotificationPermission),
                  selected = isSelected,
                  testTag = "settings_hub_row_${section.name.lowercase()}",
                  onClick = { onSelectSection(section) },
                )
                if (idx < sections.lastIndex || groupTitleRes == R.string.settings_group_analysis_pipeline) {
                  HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    modifier = Modifier.padding(horizontal = 18.dp),
                  )
                }
              }
              if (groupTitleRes == R.string.settings_group_analysis_pipeline) {
                val jniLabel =
                  if (transpilerConfig.jniStyle == JniBindingStyle.REGISTER_NATIVES) "RegisterNatives"
                  else "Java_* Exports"
                val langLabel = transpilerConfig.targetLanguage.label
                SettingsHubRow(
                  title = stringResource(R.string.settings_card_pipeline_shortcut),
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
  onSyncWorkflowToRepo: () -> Unit,
  onStartDeviceLogin: () -> Unit,
  onSignOutGitHub: () -> Unit,
  onOpenGitHubAppSettings: () -> Unit,
  onGoToPipelineConfig: () -> Unit,
  onClearRecentHistory: () -> Unit,
  onResetAllSettings: (clearGitHubSession: Boolean) -> Unit,
  onTrimMemory: () -> Unit = {},
  onClearDiskCache: () -> Unit = {},
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
        appSettings = appSettings,
      )
    SettingsSection.STORAGE_RESET ->
      SettingsStorageResetSubScreen(
        recentHistoryCount = recentHistoryCount,
        isGitHubSignedIn = cloudSettings.isAuthenticated,
        onClearRecentHistory = onClearRecentHistory,
        onResetAllSettings = onResetAllSettings,
        onTrimMemory = onTrimMemory,
        onClearDiskCache = onClearDiskCache,
      )
    SettingsSection.ABOUT ->
      SettingsAboutSubScreen(
        appSettings = appSettings,
        transpilerConfig = transpilerConfig,
        onCopyText = onCopyText,
      )
  }
}

@Composable
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
          stringResource(R.string.onboarding_dynamic_wallpaper)
        } else {
          CURATED_ACCENT_PRESETS.firstOrNull {
            it.hex.equals(appSettings.accentSeedHex, ignoreCase = true)
          }?.name ?: normalizeHexColor(appSettings.accentSeedHex)
        }
      val langLabel =
        when (appSettings.appLanguage) {
          AppLanguage.SYSTEM -> stringResource(R.string.language_system)
          AppLanguage.ENGLISH -> stringResource(R.string.language_english)
        }
      "${stringResource(appSettings.themeMode.labelRes)} • $paletteLabel • $langLabel • ${stringResource(appSettings.appIconStyle.labelRes)}"
    }
    SettingsSection.CODE_EDITOR ->
      "${appSettings.codeFontSizeSp}sp • ${stringResource(appSettings.smaliSyntaxTheme.shortLabelRes)} • ${if (appSettings.showLineNumbers) stringResource(R.string.settings_show_line_numbers) else "..."} • ${appSettings.maxCodePreviewLines} lines"
    SettingsSection.DEX_PARSER ->
      "${if (appSettings.defaultHideFramework) stringResource(R.string.chip_hide_framework) else stringResource(R.string.picker_tab_recent)} • ${stringResource(R.string.settings_auto_select_limit)} ${appSettings.autoSelectMethodLimit}"
    SettingsSection.GITHUB_CLOUD ->
      if (cloudSettings.isAuthenticated) "@${cloudSettings.githubUsername} • ${cloudSettings.repoName}"
      else stringResource(R.string.settings_card_github_oauth)
    SettingsSection.NDK_SIGNING ->
      "NDK ${cloudSettings.ndkVersion} • JDK ${cloudSettings.jdkVersion} • ${stringResource(cloudSettings.signingMode.labelRes)}"
    SettingsSection.NOTIFICATIONS ->
      if (!cloudSettings.notificationsEnabled) stringResource(R.string.settings_card_push_alerts)
      else if (hasNotificationPermission) "Enabled • ${cloudSettings.pollIntervalSeconds}s"
      else stringResource(R.string.settings_allow_permission)
    SettingsSection.STORAGE_RESET ->
      "$recentHistoryCount • ${stringResource(R.string.settings_card_workspace_history)}"
    SettingsSection.ABOUT ->
      "v${BuildConfig.VERSION_NAME} • DEX 035–041 • codehasan/dex2c Cloud"
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
