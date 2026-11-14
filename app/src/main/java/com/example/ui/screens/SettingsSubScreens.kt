package com.example.ui.screens

import android.os.Build
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.R
import com.example.cloud.CloudSettings
import com.example.cloud.CloudSigningMode
import com.example.cloud.Dex2cWorkflowGenerator
import com.example.cloud.SUPPORTED_JDK_VERSIONS
import com.example.cloud.SUPPORTED_NDK_VERSIONS
import com.example.data.AppSettings
import com.example.data.AppThemeMode
import com.example.dex.NativeDexParserBridge
import com.example.transpiler.TranspilerConfig
import com.example.ui.components.AccentColorPickerSection
import com.example.ui.components.ExpressiveMotion
import com.example.ui.components.SyntaxCodeViewer
import com.example.ui.components.TagPill
import com.example.ui.theme.JetBrainsMonoFontFamily
import kotlin.math.roundToInt

@Composable
fun SettingsAppearanceSubScreen(
  appSettings: AppSettings,
  onUpdateAppSettings: ((AppSettings) -> AppSettings) -> Unit,
) {
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    item {
      SubScreenCard(title = "Theme Posture") {
        AppThemeMode.entries.forEach { mode ->
          Row(
            modifier =
              Modifier.fillMaxWidth()
                .clickable { onUpdateAppSettings { it.copy(themeMode = mode) } }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            RadioButton(
              selected = appSettings.themeMode == mode,
              onClick = { onUpdateAppSettings { it.copy(themeMode = mode) } },
              modifier = Modifier.minimumInteractiveComponentSize().testTag("settings_theme_${mode.name}"),
            )
            Column(modifier = Modifier.weight(1f)) {
              Text(mode.label, style = MaterialTheme.typography.titleSmall)
              Text(
                text = mode.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    }

    item {
      SubScreenCard(title = "Dynamic Wallpaper Colors") {
        val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        SettingsToggleRow(
          title = "Wallpaper-Based Palette",
          subtitle =
            if (supportsDynamic) "Extract personalized Material 3 tonal roles from your Android wallpaper (works with AMOLED Black too)"
            else "Available on Android 12+ (custom accent seed used below)",
          checked = appSettings.dynamicColor,
          enabled = supportsDynamic,
          onCheckedChange = { enabled -> onUpdateAppSettings { it.copy(dynamicColor = enabled) } },
          testTag = "settings_dynamic_color_switch",
        )
      }
    }

    item {
      SubScreenCard(title = "Custom Accent Color") {
        AccentColorPickerSection(
          selectedHex = appSettings.accentSeedHex,
          enabled = !appSettings.dynamicColor || Build.VERSION.SDK_INT < Build.VERSION_CODES.S,
          onSelectHex = { hex -> onUpdateAppSettings { it.copy(accentSeedHex = hex, dynamicColor = false) } },
        )
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsCodeEditorSubScreen(
  appSettings: AppSettings,
  onUpdateAppSettings: ((AppSettings) -> AppSettings) -> Unit,
) {
  val sampleSnippet =
    """
    |// Live C/JNI & Dalvik viewer preview
    |JNIEXPORT jint JNICALL
    |Java_com_example_Auth_verify(JNIEnv* env, jobject thiz, jstring token) {
    |  const char* raw = (*env)->GetStringUTFChars(env, token, NULL);
    |  return raw != NULL ? 1 : 0;
    |}
    """.trimMargin()

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    item {
      SubScreenCard(title = "Typography & Line Gutter") {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("Monospace Font Size", style = MaterialTheme.typography.titleSmall)
          TagPill(text = "${appSettings.codeFontSizeSp} sp")
        }
        Slider(
          value = appSettings.codeFontSizeSp.toFloat(),
          onValueChange = { v -> onUpdateAppSettings { it.copy(codeFontSizeSp = v.roundToInt()) } },
          valueRange = 10f..18f,
          steps = 7,
          modifier = Modifier.fillMaxWidth().testTag("settings_code_font_slider"),
        )
        Spacer(Modifier.height(6.dp))
        SettingsToggleRow(
          title = "Show Line Numbers & Hex Offsets",
          subtitle = "Display left gutter line indices in C/C++ and Dalvik instruction viewers",
          checked = appSettings.showLineNumbers,
          onCheckedChange = { show -> onUpdateAppSettings { it.copy(showLineNumbers = show) } },
          testTag = "settings_show_line_numbers_switch",
        )
        Spacer(Modifier.height(10.dp))
        Text(
          text = "Code Viewer Line Cap",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          listOf(1000, 2500, 5000).forEach { cap ->
            FilterChip(
              selected = appSettings.maxCodePreviewLines == cap,
              onClick = { onUpdateAppSettings { it.copy(maxCodePreviewLines = cap) } },
              label = { Text("$cap lines") },
              modifier = Modifier.testTag("settings_code_line_cap_$cap"),
            )
          }
        }
      }
    }

    item {
      SubScreenCard(title = "Live Code Preview") {
        SyntaxCodeViewer(
          code = sampleSnippet,
          modifier = Modifier.fillMaxWidth().height(160.dp),
        )
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsDexParserSubScreen(
  appSettings: AppSettings,
  transpilerConfig: TranspilerConfig,
  onUpdateAppSettings: ((AppSettings) -> AppSettings) -> Unit,
  onGoToPipelineConfig: () -> Unit,
  onOpenRustBridge: () -> Unit,
) {
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    item {
      SubScreenCard(title = "Default Class Explorer Filters") {
        SettingsToggleRow(
          title = "Hide Android & Support Frameworks",
          subtitle = "Automatically filter androidx.*, android.*, java.* packages on DEX load",
          checked = appSettings.defaultHideFramework,
          onCheckedChange = { hide -> onUpdateAppSettings { it.copy(defaultHideFramework = hide) } },
          testTag = "settings_default_hide_framework_switch",
        )
        Spacer(Modifier.height(8.dp))
        SettingsToggleRow(
          title = "Only Methods with Dalvik Bytecode",
          subtitle = "Hide abstract and native stubs lacking DEX code_item payloads",
          checked = appSettings.defaultOnlyBytecode,
          onCheckedChange = { only -> onUpdateAppSettings { it.copy(defaultOnlyBytecode = only) } },
          testTag = "settings_default_only_bytecode_switch",
        )
        Spacer(Modifier.height(12.dp))
        Text(
          text = "Auto-Select App Methods Cap",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          listOf(16, 32, 64, 96).forEach { limit ->
            FilterChip(
              selected = appSettings.autoSelectMethodLimit == limit,
              onClick = { onUpdateAppSettings { it.copy(autoSelectMethodLimit = limit) } },
              label = { Text("$limit methods") },
              modifier = Modifier.testTag("settings_autoselect_cap_$limit"),
            )
          }
        }
      }
    }

    item {
      SubScreenCard(title = "Native Parser & Kirlif/d2c Integration") {
        SettingsToggleRow(
          title = "Prefer Rust dexparser-rs JNI Bridge",
          subtitle = "Current runtime: ${NativeDexParserBridge.activeEngineLabel()}",
          checked = appSettings.preferRustJniBridge,
          onCheckedChange = { pref -> onUpdateAppSettings { it.copy(preferRustJniBridge = pref) } },
          testTag = "settings_prefer_rust_switch",
        )
        Spacer(Modifier.height(10.dp))
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          TagPill(text = "allow-init: ${transpilerConfig.allowInit}")
          TagPill(text = "Loader: ${transpilerConfig.customLoaderClass.substringAfterLast('.')}")
          TagPill(text = "ABIs: ${transpilerConfig.targetAbis.joinToString(",")}")
        }
        Spacer(Modifier.height(12.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedButton(
            onClick = onGoToPipelineConfig,
            modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("settings_open_pipeline_btn"),
          ) {
            Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Pipeline Config")
          }
          OutlinedButton(
            onClick = onOpenRustBridge,
            modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("settings_open_rust_bridge_btn"),
          ) {
            Icon(Icons.Rounded.Build, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Rust JNI Bridge")
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsGitHubCloudSubScreen(
  cloudSettings: CloudSettings,
  transpilerConfig: TranspilerConfig,
  onUpdateCloudSettings: ((CloudSettings) -> CloudSettings) -> Unit,
  onSyncWorkflowToRepo: () -> Unit,
  onStartDeviceLogin: () -> Unit,
  onSignOutGitHub: () -> Unit,
  onOpenGitHubAppSettings: () -> Unit,
  onCopyText: (String) -> Unit,
) {
  var showYamlPreview by remember { mutableStateOf(false) }
  val generatedYaml =
    remember(cloudSettings, transpilerConfig) {
      Dex2cWorkflowGenerator.generateDex2cYaml(cloudSettings, transpilerConfig)
    }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    item {
      SubScreenCard(title = "GitHub OAuth Device Flow (github.com/login/device)") {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "Account Session",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TagPill(
              text = if (cloudSettings.usingCustomClientId) "Custom OAuth ID" else "gh CLI Default",
              containerColor = MaterialTheme.colorScheme.secondaryContainer,
              contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            if (cloudSettings.isAuthenticated) {
              TagPill(
                text = "@${cloudSettings.githubUsername}",
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
              )
            } else {
              TagPill(text = "Signed Out")
            }
          }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
          value = cloudSettings.githubClientId,
          onValueChange = { id -> onUpdateCloudSettings { it.copy(githubClientId = id.trim()) } },
          label = { Text("Custom GitHub OAuth Client ID (optional)") },
          placeholder = { Text(com.example.cloud.GITHUB_CLI_CLIENT_ID) },
          leadingIcon = { Icon(Icons.Rounded.Key, contentDescription = null) },
          singleLine = true,
          textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = JetBrainsMonoFontFamily),
          modifier = Modifier.fillMaxWidth().testTag("settings_client_id_input"),
        )
        Spacer(Modifier.height(6.dp))
        Text(
          text = "Leave blank to use GitHub CLI's built-in Client ID (${com.example.cloud.GITHUB_CLI_CLIENT_ID}), or enter your own GitHub OAuth App Client ID.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          if (!cloudSettings.isAuthenticated) {
            Button(
              onClick = onStartDeviceLogin,
              shape = RoundedCornerShape(20.dp),
              modifier = Modifier.minimumInteractiveComponentSize().testTag("settings_start_device_login_btn"),
            ) {
              Icon(Icons.Rounded.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(6.dp))
              Text(stringResource(R.string.action_continue_with_github))
            }
          } else {
            OutlinedButton(
              onClick = onSignOutGitHub,
              shape = RoundedCornerShape(20.dp),
              modifier = Modifier.minimumInteractiveComponentSize().testTag("settings_sign_out_btn"),
            ) {
              Text("Sign Out (@${cloudSettings.githubUsername})")
            }
          }
          OutlinedButton(
            onClick = onOpenGitHubAppSettings,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.minimumInteractiveComponentSize().testTag("settings_open_github_dev_btn"),
          ) {
            Icon(Icons.Rounded.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("GitHub OAuth Settings")
          }
        }
      }
    }

    item {
      SubScreenCard(title = "Private Workspace Repository & dex2c.yml") {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedTextField(
            value = cloudSettings.repoOwner,
            onValueChange = { v -> onUpdateCloudSettings { it.copy(repoOwner = v.trim()) } },
            label = { Text("Owner") },
            placeholder = { Text(cloudSettings.githubUsername.ifBlank { "username" }) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
            modifier = Modifier.weight(1f).testTag("settings_repo_owner_input"),
          )
          OutlinedTextField(
            value = cloudSettings.repoName,
            onValueChange = { v -> onUpdateCloudSettings { it.copy(repoName = v.trim()) } },
            label = { Text("Repository") },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
            modifier = Modifier.weight(1.4f).testTag("settings_repo_name_input"),
          )
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
          value = cloudSettings.defaultBranch,
          onValueChange = { v -> onUpdateCloudSettings { it.copy(defaultBranch = v.trim()) } },
          label = { Text("Workflow Target Branch") },
          singleLine = true,
          textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
          modifier = Modifier.fillMaxWidth().testTag("settings_repo_branch_input"),
        )
        Spacer(Modifier.height(10.dp))
        SettingsToggleRow(
          title = "Create as Private Repository",
          subtitle = "Keeps uploaded APKs, dcc.cfg, and filter.txt private to your account",
          checked = cloudSettings.privateRepo,
          onCheckedChange = { priv -> onUpdateCloudSettings { it.copy(privateRepo = priv) } },
          testTag = "settings_private_repo_switch",
        )
        Spacer(Modifier.height(10.dp))
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Button(
            onClick = onSyncWorkflowToRepo,
            enabled = cloudSettings.isAuthenticated,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("settings_sync_workflow_btn"),
          ) {
            Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Init / Sync dex2c.yml")
          }
          OutlinedButton(
            onClick = { showYamlPreview = !showYamlPreview },
            modifier = Modifier.minimumInteractiveComponentSize().testTag("settings_toggle_yaml_preview_btn"),
          ) {
            Icon(Icons.Rounded.Code, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (showYamlPreview) "Hide dex2c.yml" else "Preview dex2c.yml")
          }
          if (showYamlPreview) {
            TextButton(
              onClick = { onCopyText(generatedYaml) },
              modifier = Modifier.minimumInteractiveComponentSize().testTag("settings_copy_yaml_btn"),
            ) {
              Text("Copy YAML")
            }
          }
        }
        if (showYamlPreview) {
          Spacer(Modifier.height(10.dp))
          SyntaxCodeViewer(
            code = generatedYaml,
            modifier = Modifier.fillMaxWidth().height(260.dp),
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsNdkSigningSubScreen(
  cloudSettings: CloudSettings,
  onUpdateCloudSettings: ((CloudSettings) -> CloudSettings) -> Unit,
) {
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    item {
      SubScreenCard(title = "Android NDK & JDK Runners") {
        Text(
          text = "Android NDK Revision (ubuntu-latest)",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          SUPPORTED_NDK_VERSIONS.forEach { ndk ->
            FilterChip(
              selected = cloudSettings.ndkVersion == ndk,
              onClick = { onUpdateCloudSettings { it.copy(ndkVersion = ndk) } },
              label = { Text(ndk, fontFamily = JetBrainsMonoFontFamily) },
              modifier = Modifier.testTag("settings_ndk_$ndk"),
            )
          }
        }
        Spacer(Modifier.height(12.dp))
        Text(
          text = "Temurin JDK Version",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          SUPPORTED_JDK_VERSIONS.forEach { jdk ->
            FilterChip(
              selected = cloudSettings.jdkVersion == jdk,
              onClick = { onUpdateCloudSettings { it.copy(jdkVersion = jdk) } },
              label = { Text("JDK $jdk") },
              modifier = Modifier.testTag("settings_jdk_$jdk"),
            )
          }
        }
      }
    }

    item {
      SubScreenCard(title = "APK Signing & Upload Policy") {
        CloudSigningMode.entries.forEach { mode ->
          Row(
            modifier =
              Modifier.fillMaxWidth()
                .clickable { onUpdateCloudSettings { it.copy(signingMode = mode) } }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            RadioButton(
              selected = cloudSettings.signingMode == mode,
              onClick = { onUpdateCloudSettings { it.copy(signingMode = mode) } },
              modifier = Modifier.minimumInteractiveComponentSize().testTag("settings_signing_${mode.name}"),
            )
            Column(modifier = Modifier.weight(1f)) {
              Text(mode.label, style = MaterialTheme.typography.titleSmall)
              Text(
                text = mode.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
        Spacer(Modifier.height(12.dp))
        Text(
          text = "Direct Repo APK Upload Threshold",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          listOf(25, 45, 75).forEach { mb ->
            FilterChip(
              selected = cloudSettings.maxDirectUploadMb == mb,
              onClick = { onUpdateCloudSettings { it.copy(maxDirectUploadMb = mb) } },
              label = { Text("$mb MB") },
              modifier = Modifier.testTag("settings_upload_limit_$mb"),
            )
          }
        }
        Spacer(Modifier.height(12.dp))
        Text(
          text = "Workflow Poll Interval",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          listOf(5, 6, 10, 15).forEach { sec ->
            FilterChip(
              selected = cloudSettings.pollIntervalSeconds == sec,
              onClick = { onUpdateCloudSettings { it.copy(pollIntervalSeconds = sec) } },
              label = { Text("${sec}s") },
              modifier = Modifier.testTag("settings_poll_interval_$sec"),
            )
          }
        }
      }
    }
  }
}

@Composable
fun SettingsNotificationsSubScreen(
  cloudSettings: CloudSettings,
  hasNotificationPermission: Boolean,
  onToggleNotifications: (Boolean) -> Unit,
  onRequestPermission: () -> Unit,
  onSendTestNotification: () -> Unit,
) {
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    item {
      SubScreenCard(title = "Background Cloud Build Push Alerts") {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Text("Build Completion Alerts", style = MaterialTheme.typography.titleSmall)
              TagPill(
                text = if (hasNotificationPermission) "Allowed" else "Permission Needed",
                containerColor =
                  if (hasNotificationPermission) MaterialTheme.colorScheme.primaryContainer
                  else MaterialTheme.colorScheme.secondaryContainer,
                contentColor =
                  if (hasNotificationPermission) MaterialTheme.colorScheme.onPrimaryContainer
                  else MaterialTheme.colorScheme.onSecondaryContainer,
              )
            }
            Spacer(Modifier.height(4.dp))
            Text(
              text = "Uses WorkManager with network constraints to alert you when cloud APK builds finish or fail, even while backgrounded.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Switch(
            checked = cloudSettings.notificationsEnabled,
            onCheckedChange = onToggleNotifications,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("settings_notifications_switch"),
          )
        }

        Spacer(Modifier.height(12.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          if (!hasNotificationPermission) {
            Button(
              onClick = onRequestPermission,
              modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("settings_request_notif_btn"),
            ) {
              Icon(Icons.Rounded.Notifications, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(6.dp))
              Text("Allow Permission")
            }
          }
          OutlinedButton(
            onClick = onSendTestNotification,
            enabled = hasNotificationPermission,
            modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("settings_test_notif_btn"),
          ) {
            Icon(Icons.Rounded.Notifications, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Send Test Alert")
          }
        }
      }
    }
  }
}

@Composable
fun SettingsStorageResetSubScreen(
  recentHistoryCount: Int,
  isGitHubSignedIn: Boolean,
  onClearRecentHistory: () -> Unit,
  onResetAllSettings: (clearGitHubSession: Boolean) -> Unit,
) {
  var showClearHistoryDialog by remember { mutableStateOf(false) }
  var showResetDialog by remember { mutableStateOf(false) }
  var alsoSignOutOnReset by remember { mutableStateOf(false) }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    item {
      SubScreenCard(title = "Workspace History & Binary Cache") {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Recent APK & DEX Entries", style = MaterialTheme.typography.titleSmall)
            Text(
              text = "$recentHistoryCount saved quick-open entries in local DataStore",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          TagPill(text = "$recentHistoryCount items")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
          onClick = { showClearHistoryDialog = true },
          enabled = recentHistoryCount > 0,
          modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("settings_clear_history_btn"),
        ) {
          Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text("Clear Recent File History")
        }
      }
    }

    item {
      SubScreenCard(title = "Restore Defaults") {
        Text(
          text = "Reset Appearance, Code Editor formatting, DEX Parser defaults, Transpiler Pipeline options, and NDK/Signing settings.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Button(
          onClick = {
            alsoSignOutOnReset = false
            showResetDialog = true
          },
          colors =
            ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.errorContainer,
              contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
          modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("settings_reset_all_btn"),
        ) {
          Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text("Reset All Settings to Defaults")
        }
      }
    }
  }

  if (showClearHistoryDialog) {
    AlertDialog(
      onDismissRequest = { showClearHistoryDialog = false },
      shape = RoundedCornerShape(28.dp),
      title = { Text("Clear Recent File History?") },
      text = {
        Text("Removes all $recentHistoryCount recent APK/DEX shortcuts from Dex2C Studio. Your APK files on device storage remain untouched.")
      },
      confirmButton = {
        Button(
          onClick = {
            showClearHistoryDialog = false
            onClearRecentHistory()
          },
          modifier = Modifier.testTag("confirm_clear_history_btn"),
        ) {
          Text("Clear History")
        }
      },
      dismissButton = {
        TextButton(onClick = { showClearHistoryDialog = false }) { Text("Cancel") }
      },
    )
  }

  if (showResetDialog) {
    AlertDialog(
      onDismissRequest = { showResetDialog = false },
      shape = RoundedCornerShape(28.dp),
      title = { Text("Reset All Settings?") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text("Restores theme, code viewer, DEX parser, transpiler pipeline, and NDK settings to factory defaults.")
          if (isGitHubSignedIn) {
            SettingsToggleRow(
              title = "Also sign out of GitHub",
              subtitle = "Clears saved OAuth token & workspace repo link",
              checked = alsoSignOutOnReset,
              onCheckedChange = { alsoSignOutOnReset = it },
              testTag = "reset_also_sign_out_switch",
            )
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            showResetDialog = false
            onResetAllSettings(alsoSignOutOnReset)
          },
          colors =
            ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.error,
              contentColor = MaterialTheme.colorScheme.onError,
            ),
          modifier = Modifier.testTag("confirm_reset_all_btn"),
        ) {
          Text("Reset Defaults")
        }
      },
      dismissButton = {
        TextButton(onClick = { showResetDialog = false }) { Text("Cancel") }
      },
    )
  }
}

@Composable
fun SettingsAboutSubScreen(
  transpilerConfig: TranspilerConfig,
  onReplayOnboarding: () -> Unit,
) {
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    item {
      SubScreenCard(title = "Dex2C Studio") {
        AboutRow("App Name", stringResource(R.string.app_name))
        AboutRow("Version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        AboutRow("Application ID", BuildConfig.APPLICATION_ID)
        AboutRow("Active DEX Engine", NativeDexParserBridge.activeEngineLabel())
        AboutRow("Supported DEX Formats", "035, 037, 038, 039, 040, 041")
        AboutRow("Default Module Name", transpilerConfig.moduleName)
      }
    }

    item {
      SubScreenCard(title = "Getting Started & Guided Setup") {
        Text(
          text = "Revisit the 4-step Dex2C Studio tour, live theme & code editor setup, GitHub Cloud OAuth wizard, and notification setup.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
          onClick = onReplayOnboarding,
          modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("settings_replay_onboarding_btn"),
        ) {
          Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text(stringResource(R.string.settings_replay_onboarding))
        }
      }
    }

    item {
      SubScreenCard(title = "Bundled Toolchains & Integrations") {
        Text(
          text = "• Kirlif/d2c & codehasan/dex2c transpiler parity (dcc.cfg, filter.txt, allow-init, custom loader)\n" +
            "• androguard/dex-parser structural map_list + Rust dexparser-rs JNI bridge scaffolding\n" +
            "• GitHub OAuth Device Flow + Private Workspace dex2c.yml Actions + WorkManager push alerts",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun AboutRow(label: String, value: String) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(
      text = value,
      style = MaterialTheme.typography.bodySmall,
      fontFamily = JetBrainsMonoFontFamily,
      color = MaterialTheme.colorScheme.onSurface,
    )
  }
}

@Composable
fun SubScreenCard(
  title: String,
  content: @Composable () -> Unit,
) {
  Surface(
    modifier = Modifier.fillMaxWidth().animateContentSize(ExpressiveMotion.spatialSpring()),
    shape = RoundedCornerShape(24.dp),
    color = MaterialTheme.colorScheme.surfaceContainer,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
  ) {
    Column(modifier = Modifier.padding(18.dp)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(
          imageVector = Icons.Rounded.Lock,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(16.dp),
        )
        Text(
          text = title,
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.primary,
        )
      }
      Spacer(Modifier.height(12.dp))
      content()
    }
  }
}

@Composable
private fun SettingsToggleRow(
  title: String,
  subtitle: String,
  checked: Boolean,
  enabled: Boolean = true,
  onCheckedChange: (Boolean) -> Unit,
  testTag: String,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
      Text(title, style = MaterialTheme.typography.titleSmall)
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      enabled = enabled,
      modifier = Modifier.minimumInteractiveComponentSize().testTag(testTag),
    )
  }
}
