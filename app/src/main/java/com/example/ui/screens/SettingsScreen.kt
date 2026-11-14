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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.R
import com.example.cloud.CloudSettings
import com.example.data.AppSettings
import com.example.transpiler.TranspilerConfig
import com.example.ui.components.ExpressiveMotion
import com.example.ui.components.TagPill
import com.example.ui.components.expressivePressScale

enum class SettingsSection(
  val title: String,
  val shortTitle: String,
  val groupName: String,
  val icon: ImageVector,
) {
  APPEARANCE("Appearance & Theme", "Appearance", "General & Editor", Icons.Rounded.Palette),
  CODE_EDITOR("Code Editor & Viewer", "Code Viewer", "General & Editor", Icons.Rounded.Code),
  DEX_PARSER("DEX Parser & Workbench", "DEX Parser", "Analysis & Pipeline", Icons.Rounded.Memory),
  GITHUB_CLOUD("GitHub Cloud & Workspace Repo", "GitHub Cloud", "Cloud Build & Alerts", Icons.Rounded.CloudQueue),
  NDK_SIGNING("NDK Toolchain & APK Signing", "NDK & Signing", "Cloud Build & Alerts", Icons.Rounded.Build),
  NOTIFICATIONS("Notifications & Alerts", "Build Alerts", "Cloud Build & Alerts", Icons.Rounded.NotificationsActive),
  STORAGE_RESET("Storage, Cache & Reset", "Storage & Reset", "Maintenance & Info", Icons.Rounded.Storage),
  ABOUT("About & Diagnostics", "About Studio", "Maintenance & Info", Icons.Rounded.Info),
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
          Box(modifier = Modifier.width(380.dp).fillMaxHeight()) {
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

@OptIn(ExperimentalLayoutApi::class)
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
  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val scrollState = rememberScrollState()
    val needsVerticalScroll = maxHeight < 520.dp
    val containerModifier =
      Modifier
        .fillMaxSize()
        .then(if (needsVerticalScroll) Modifier.verticalScroll(scrollState) else Modifier)
        .padding(horizontal = 16.dp, vertical = 6.dp)

    Column(
      modifier = containerModifier,
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      // Quick Status Strip + Pipeline Config Shortcut
      Surface(
        modifier = Modifier.fillMaxWidth().testTag("settings_quick_status_strip"),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            FlowRow(
              modifier = Modifier.weight(1f),
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              TagPill(
                text = "Theme: ${appSettings.themeMode.label}",
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f),
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
              )
              TagPill(text = "${appSettings.codeFontSizeSp}sp")
              TagPill(
                text =
                  if (cloudSettings.isAuthenticated) "@${cloudSettings.githubUsername}"
                  else "GitHub: Signed Out",
                containerColor =
                  if (cloudSettings.isAuthenticated) MaterialTheme.colorScheme.secondaryContainer
                  else MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor =
                  if (cloudSettings.isAuthenticated) MaterialTheme.colorScheme.onSecondaryContainer
                  else MaterialTheme.colorScheme.onSurfaceVariant,
              )
              TagPill(text = "NDK ${cloudSettings.ndkVersion}")
            }
            Spacer(Modifier.width(8.dp))
            Surface(
              shape = RoundedCornerShape(14.dp),
              color = MaterialTheme.colorScheme.primaryContainer,
              modifier =
                Modifier
                  .clickable(onClick = onGoToPipelineConfig)
                  .testTag("settings_hub_row_pipeline_shortcut"),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Icon(
                  imageVector = Icons.Rounded.Tune,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onPrimaryContainer,
                  modifier = Modifier.size(14.dp),
                )
                Text(
                  text = "Pipeline",
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.SemiBold,
                  color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
              }
            }
          }
        }
      }

      // Row 1: General & Editor (2-column grid)
      SettingsGroupHeader("General & Editor")
      Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        SettingsBentoTile(
          title = SettingsSection.APPEARANCE.shortTitle,
          icon = SettingsSection.APPEARANCE.icon,
          summary = sectionSummary(SettingsSection.APPEARANCE, appSettings, cloudSettings, recentHistoryCount, hasNotificationPermission),
          badgeText = null,
          selected = showSelectionHighlight && selectedSection == SettingsSection.APPEARANCE,
          testTag = "settings_hub_row_${SettingsSection.APPEARANCE.name.lowercase()}",
          onClick = { onSelectSection(SettingsSection.APPEARANCE) },
          modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        SettingsBentoTile(
          title = SettingsSection.CODE_EDITOR.shortTitle,
          icon = SettingsSection.CODE_EDITOR.icon,
          summary = sectionSummary(SettingsSection.CODE_EDITOR, appSettings, cloudSettings, recentHistoryCount, hasNotificationPermission),
          badgeText = null,
          selected = showSelectionHighlight && selectedSection == SettingsSection.CODE_EDITOR,
          testTag = "settings_hub_row_${SettingsSection.CODE_EDITOR.name.lowercase()}",
          onClick = { onSelectSection(SettingsSection.CODE_EDITOR) },
          modifier = Modifier.weight(1f).fillMaxHeight(),
        )
      }

      // Row 2: Analysis & Toolchain (2-column grid)
      SettingsGroupHeader("Analysis & Pipeline")
      Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        SettingsBentoTile(
          title = SettingsSection.DEX_PARSER.shortTitle,
          icon = SettingsSection.DEX_PARSER.icon,
          summary = sectionSummary(SettingsSection.DEX_PARSER, appSettings, cloudSettings, recentHistoryCount, hasNotificationPermission),
          badgeText = null,
          selected = showSelectionHighlight && selectedSection == SettingsSection.DEX_PARSER,
          testTag = "settings_hub_row_${SettingsSection.DEX_PARSER.name.lowercase()}",
          onClick = { onSelectSection(SettingsSection.DEX_PARSER) },
          modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        SettingsBentoTile(
          title = SettingsSection.NDK_SIGNING.shortTitle,
          icon = SettingsSection.NDK_SIGNING.icon,
          summary = sectionSummary(SettingsSection.NDK_SIGNING, appSettings, cloudSettings, recentHistoryCount, hasNotificationPermission),
          badgeText = null,
          selected = showSelectionHighlight && selectedSection == SettingsSection.NDK_SIGNING,
          testTag = "settings_hub_row_${SettingsSection.NDK_SIGNING.name.lowercase()}",
          onClick = { onSelectSection(SettingsSection.NDK_SIGNING) },
          modifier = Modifier.weight(1f).fillMaxHeight(),
        )
      }

      // Row 3: Cloud Build & Alerts (2-column grid)
      SettingsGroupHeader("Cloud Build & Alerts")
      Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        SettingsBentoTile(
          title = SettingsSection.GITHUB_CLOUD.shortTitle,
          icon = SettingsSection.GITHUB_CLOUD.icon,
          summary = sectionSummary(SettingsSection.GITHUB_CLOUD, appSettings, cloudSettings, recentHistoryCount, hasNotificationPermission),
          badgeText = sectionBadge(SettingsSection.GITHUB_CLOUD, cloudSettings, hasNotificationPermission),
          selected = showSelectionHighlight && selectedSection == SettingsSection.GITHUB_CLOUD,
          testTag = "settings_hub_row_${SettingsSection.GITHUB_CLOUD.name.lowercase()}",
          onClick = { onSelectSection(SettingsSection.GITHUB_CLOUD) },
          modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        SettingsBentoTile(
          title = SettingsSection.NOTIFICATIONS.shortTitle,
          icon = SettingsSection.NOTIFICATIONS.icon,
          summary = sectionSummary(SettingsSection.NOTIFICATIONS, appSettings, cloudSettings, recentHistoryCount, hasNotificationPermission),
          badgeText = sectionBadge(SettingsSection.NOTIFICATIONS, cloudSettings, hasNotificationPermission),
          selected = showSelectionHighlight && selectedSection == SettingsSection.NOTIFICATIONS,
          testTag = "settings_hub_row_${SettingsSection.NOTIFICATIONS.name.lowercase()}",
          onClick = { onSelectSection(SettingsSection.NOTIFICATIONS) },
          modifier = Modifier.weight(1f).fillMaxHeight(),
        )
      }

      // Row 4: Maintenance & Info (2-column grid)
      SettingsGroupHeader("Maintenance & Info")
      Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        SettingsBentoTile(
          title = SettingsSection.STORAGE_RESET.shortTitle,
          icon = SettingsSection.STORAGE_RESET.icon,
          summary = sectionSummary(SettingsSection.STORAGE_RESET, appSettings, cloudSettings, recentHistoryCount, hasNotificationPermission),
          badgeText = null,
          selected = showSelectionHighlight && selectedSection == SettingsSection.STORAGE_RESET,
          testTag = "settings_hub_row_${SettingsSection.STORAGE_RESET.name.lowercase()}",
          onClick = { onSelectSection(SettingsSection.STORAGE_RESET) },
          modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        SettingsBentoTile(
          title = SettingsSection.ABOUT.shortTitle,
          icon = SettingsSection.ABOUT.icon,
          summary = sectionSummary(SettingsSection.ABOUT, appSettings, cloudSettings, recentHistoryCount, hasNotificationPermission),
          badgeText = null,
          selected = showSelectionHighlight && selectedSection == SettingsSection.ABOUT,
          testTag = "settings_hub_row_${SettingsSection.ABOUT.name.lowercase()}",
          onClick = { onSelectSection(SettingsSection.ABOUT) },
          modifier = Modifier.weight(1f).fillMaxHeight(),
        )
      }
    }
  }
}

@Composable
private fun SettingsGroupHeader(title: String) {
  Text(
    text = title,
    style = MaterialTheme.typography.labelMedium,
    fontWeight = FontWeight.SemiBold,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(horizontal = 4.dp),
  )
}

@Composable
private fun SettingsBentoTile(
  title: String,
  icon: ImageVector,
  summary: String,
  badgeText: String?,
  selected: Boolean,
  testTag: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val interactionSource = remember { MutableInteractionSource() }
  Surface(
    shape = RoundedCornerShape(20.dp),
    color =
      if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
      else MaterialTheme.colorScheme.surfaceContainer,
    border =
      BorderStroke(
        width = 1.dp,
        color =
          if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
          else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
      ),
    modifier =
      modifier
        .minimumInteractiveComponentSize()
        .expressivePressScale(interactionSource, 0.985f)
        .clickable(
          interactionSource = interactionSource,
          indication = androidx.compose.material3.ripple(),
          onClick = onClick,
        )
        .testTag(testTag),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.weight(1f),
        ) {
          Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(19.dp),
          )
          Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          if (badgeText != null) {
            TagPill(text = badgeText)
          }
          Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = "Open $title",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
          )
        }
      }
      Text(
        text = summary,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
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
    SettingsSection.APPEARANCE ->
      "${appSettings.themeMode.label}${if (appSettings.dynamicColor) " • Wallpaper" else " • ${appSettings.accentSeedHex}"}"
    SettingsSection.CODE_EDITOR ->
      "${appSettings.codeFontSizeSp}sp • ${if (appSettings.showLineNumbers) "Line numbers on" else "Line numbers off"} • ${appSettings.maxCodePreviewLines} lines"
    SettingsSection.DEX_PARSER ->
      "${if (appSettings.defaultHideFramework) "App-only default" else "All classes"} • Auto-select ${appSettings.autoSelectMethodLimit}"
    SettingsSection.GITHUB_CLOUD ->
      if (cloudSettings.isAuthenticated) "@${cloudSettings.githubUsername} • ${cloudSettings.repoName}"
      else "Device Flow OAuth & private dex2c.yml workspace"
    SettingsSection.NDK_SIGNING ->
      "NDK ${cloudSettings.ndkVersion} • JDK ${cloudSettings.jdkVersion} • ${cloudSettings.signingMode.label}"
    SettingsSection.NOTIFICATIONS ->
      if (!cloudSettings.notificationsEnabled) "Build alerts disabled"
      else if (hasNotificationPermission) "Enabled • ${cloudSettings.pollIntervalSeconds}s background poll"
      else "Enabled • Notification permission needed"
    SettingsSection.STORAGE_RESET ->
      "$recentHistoryCount recent entries • Clear cache & factory reset"
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
