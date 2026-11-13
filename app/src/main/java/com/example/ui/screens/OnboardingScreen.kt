package com.example.ui.screens

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.cloud.CloudSettings
import com.example.cloud.DeviceAuthState
import com.example.cloud.GITHUB_CLI_CLIENT_ID
import com.example.cloud.GITHUB_DEVICE_VERIFICATION_URL
import com.example.data.AppSettings
import com.example.data.AppThemeMode
import com.example.data.CURATED_ACCENT_PRESETS
import com.example.ui.components.GitHubDeviceAuthCard
import com.example.ui.components.OnboardingHorizontalPager
import com.example.ui.components.TagPill
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.M3TonalColorEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val ONBOARDING_PAGE_COUNT = 5

@Composable
fun OnboardingScreen(
  isReplayMode: Boolean,
  appSettings: AppSettings,
  cloudSettings: CloudSettings,
  deviceAuthState: DeviceAuthState,
  hasNotificationPermission: Boolean,
  onUpdateAppSettings: ((AppSettings) -> AppSettings) -> Unit,
  onUpdateCloudSettings: ((CloudSettings) -> CloudSettings) -> Unit,
  onStartDeviceLogin: () -> Unit,
  onCancelDeviceLogin: () -> Unit,
  onCopyCode: (String) -> Unit,
  onOpenBrowserUrl: (String) -> Unit,
  onToggleNotifications: (Boolean) -> Unit,
  onRequestNotificationPermission: () -> Unit,
  onFinish: () -> Unit,
  onDismissReplay: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val pagerState = rememberPagerState(pageCount = { ONBOARDING_PAGE_COUNT })
  val scope = rememberCoroutineScope()
  var lastAutoOpenedUserCode by rememberSaveable { mutableStateOf<String?>(null) }

  // Show the 8-digit code & 15:00 timer first, then launch https://github.com/login/device once per code
  LaunchedEffect(deviceAuthState) {
    when (deviceAuthState) {
      is DeviceAuthState.AwaitingUser -> {
        val code = deviceAuthState.userCode
        if (code.isNotBlank() && code != lastAutoOpenedUserCode) {
          delay(650L)
          lastAutoOpenedUserCode = code
          onOpenBrowserUrl(deviceAuthState.verificationUri.ifBlank { GITHUB_DEVICE_VERIFICATION_URL })
        }
      }
      is DeviceAuthState.Idle,
      is DeviceAuthState.RequestingCode,
      is DeviceAuthState.Authenticated -> {
        lastAutoOpenedUserCode = null
      }
      is DeviceAuthState.Error -> Unit
    }
  }

  BackHandler {
    when {
      pagerState.currentPage > 0 ->
        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
      isReplayMode -> onDismissReplay()
      else -> onFinish()
    }
  }

  OnboardingHorizontalPager(
    pagerState = pagerState,
    pageCount = ONBOARDING_PAGE_COUNT,
    isReplayMode = isReplayMode,
    onDismissReplay = onDismissReplay,
    onFinish = onFinish,
    modifier = modifier,
  ) { page ->
    when (page) {
      0 -> OnboardingIntroPage()
      1 -> OnboardingAboutPage()
      2 ->
        OnboardingGitHubPage(
          cloudSettings = cloudSettings,
          deviceAuthState = deviceAuthState,
          onUpdateCloudSettings = onUpdateCloudSettings,
          onStartDeviceLogin = onStartDeviceLogin,
          onCancelDeviceLogin = onCancelDeviceLogin,
          onCopyCode = onCopyCode,
          onOpenBrowserUrl = onOpenBrowserUrl,
        )
      3 ->
        OnboardingThemePage(
          appSettings = appSettings,
          onUpdateAppSettings = onUpdateAppSettings,
        )
      else ->
        OnboardingAlertsPage(
          appSettings = appSettings,
          cloudSettings = cloudSettings,
          hasNotificationPermission = hasNotificationPermission,
          onToggleNotifications = onToggleNotifications,
          onRequestNotificationPermission = onRequestNotificationPermission,
        )
    }
  }
}

@Composable
private fun OnboardingBalancedPage(
  verticalSpacing: Dp = 18.dp,
  content: @Composable ColumnScope.(availableHeight: Dp) -> Unit,
) {
  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val availableHeight = maxHeight
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .heightIn(min = availableHeight)
          .padding(vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(verticalSpacing, Alignment.CenterVertically),
    ) {
      content(availableHeight)
    }
  }
}

@Composable
private fun OnboardingIntroPage() {
  OnboardingBalancedPage(verticalSpacing = 20.dp) { availableHeight ->
    val heroHeight = (availableHeight * 0.42f).coerceIn(170.dp, 260.dp)
    Surface(
      modifier = Modifier.fillMaxWidth().height(heroHeight),
      shape = RoundedCornerShape(26.dp),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
    ) {
      Box(modifier = Modifier.fillMaxSize()) {
        Image(
          painter = painterResource(id = R.drawable.img_onboarding_tour),
          contentDescription = stringResource(R.string.onboarding_tour_desc),
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize(),
        )
        Box(
          modifier =
            Modifier
              .fillMaxSize()
              .background(
                Brush.verticalGradient(
                  listOf(Color.Transparent, Color(0xCC070C14)),
                )
              )
        )
        Row(
          modifier = Modifier.align(Alignment.BottomStart).padding(14.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          TagPill(text = "DEX 035–041")
          TagPill(text = "JNI C/C++")
          TagPill(text = "Cloud NDK")
        }
      }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(
        text = stringResource(R.string.app_name),
        style = MaterialTheme.typography.headlineLarge,
      )
      Text(
        text = "Inspect Android DEX bytecode, transpile methods into native JNI C/C++, and compile NDK libraries in the cloud.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    Surface(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(20.dp),
      color = MaterialTheme.colorScheme.surfaceContainer,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
    ) {
      Text(
        text = "Swipe left to see what Dex2C Studio does, connect GitHub, pick your theme, and enable alerts.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
      )
    }
  }
}

@Composable
private fun OnboardingAboutPage() {
  OnboardingBalancedPage(verticalSpacing = 14.dp) {
    OnboardingPageHeader(
      icon = Icons.Rounded.Info,
      title = "About Dex2C Studio",
      subtitle = "Three focused tools in one mobile reverse-engineering and native build studio.",
    )

    OnboardingHighlightCard(
      icon = Icons.Rounded.DataObject,
      title = "DEX Inspector",
      description = "Browse multi-dex classes, Dalvik opcodes, debug info, and binary map tables.",
    )
    OnboardingHighlightCard(
      icon = Icons.Rounded.Code,
      title = "JNI Transpiler",
      description = "Convert methods into C/C++ with dcc.cfg rules, XOR strings, and CMake/Android.mk export.",
    )
    OnboardingHighlightCard(
      icon = Icons.Rounded.CloudQueue,
      title = "Cloud NDK",
      description = "Dispatch remote GitHub Actions native builds and receive instant completion alerts.",
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OnboardingGitHubPage(
  cloudSettings: CloudSettings,
  deviceAuthState: DeviceAuthState,
  onUpdateCloudSettings: ((CloudSettings) -> CloudSettings) -> Unit,
  onStartDeviceLogin: () -> Unit,
  onCancelDeviceLogin: () -> Unit,
  onCopyCode: (String) -> Unit,
  onOpenBrowserUrl: (String) -> Unit,
) {
  var showCustomClientId by rememberSaveable { mutableStateOf(cloudSettings.usingCustomClientId) }

  OnboardingBalancedPage(verticalSpacing = 16.dp) {
    OnboardingPageHeader(
      icon = Icons.Rounded.CloudQueue,
      title = "GitHub Cloud CI",
      subtitle = "Optional remote NDK compilation via GitHub Actions, or stay local.",
    )

    GitHubDeviceAuthCard(
      cloudSettings = cloudSettings,
      deviceAuthState = deviceAuthState,
      onStartDeviceLogin = onStartDeviceLogin,
      onCancelDeviceLogin = onCancelDeviceLogin,
      onCopyCode = onCopyCode,
      onOpenBrowserUrl = onOpenBrowserUrl,
      tagPrefix = "onboarding",
      compact = false,
      animateTransitions = false,
    )

    if (!cloudSettings.isAuthenticated) {
      OnboardingSectionSurface {
        Row(
          modifier =
            Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(14.dp))
              .clickable { showCustomClientId = !showCustomClientId }
              .minimumInteractiveComponentSize()
              .testTag("onboarding_custom_client_id_toggle"),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text("Custom OAuth Client ID", style = MaterialTheme.typography.titleSmall)
            Text(
              text =
                if (cloudSettings.usingCustomClientId) "Override: ${cloudSettings.effectiveClientId}"
                else "Default: built-in gh CLI ID ($GITHUB_CLI_CLIENT_ID)",
              style = MaterialTheme.typography.bodySmall,
              fontFamily = JetBrainsMonoFontFamily,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          Icon(
            imageVector = if (showCustomClientId) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
            contentDescription = if (showCustomClientId) "Collapse" else "Expand",
          )
        }

        if (showCustomClientId) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            OutlinedTextField(
              value = cloudSettings.githubClientId,
              onValueChange = { cid -> onUpdateCloudSettings { it.copy(githubClientId = cid.trim()) } },
              label = { Text("Custom OAuth Client ID (optional)") },
              placeholder = { Text(GITHUB_CLI_CLIENT_ID) },
              singleLine = true,
              textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
              modifier = Modifier.fillMaxWidth().testTag("onboarding_github_client_id"),
            )
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              if (cloudSettings.usingCustomClientId) {
                OutlinedButton(
                  onClick = { onUpdateCloudSettings { it.copy(githubClientId = "") } },
                  shape = RoundedCornerShape(20.dp),
                  modifier = Modifier.minimumInteractiveComponentSize().testTag("onboarding_reset_client_id_btn"),
                ) {
                  Text("Use Built-in gh CLI ID")
                }
              }
              OutlinedButton(
                onClick = { onOpenBrowserUrl("https://github.com/settings/developers") },
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.minimumInteractiveComponentSize().testTag("onboarding_github_dev_settings_btn"),
              ) {
                Icon(Icons.Rounded.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Create OAuth App")
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
private fun OnboardingThemePage(
  appSettings: AppSettings,
  onUpdateAppSettings: ((AppSettings) -> AppSettings) -> Unit,
) {
  OnboardingBalancedPage(verticalSpacing = 16.dp) {
    OnboardingPageHeader(
      icon = Icons.Rounded.Palette,
      title = "Theme & Palette",
      subtitle = "Choose your studio contrast and accent color. Applies immediately.",
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
      AppThemeMode.entries.chunked(2).forEach { pair ->
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          pair.forEach { mode ->
            val selected = appSettings.themeMode == mode
            val modeIcon =
              when (mode) {
                AppThemeMode.SYSTEM -> Icons.Rounded.BrightnessAuto
                AppThemeMode.LIGHT -> Icons.Rounded.LightMode
                AppThemeMode.DARK -> Icons.Rounded.DarkMode
                AppThemeMode.AMOLED -> Icons.Rounded.Contrast
              }
            Surface(
              modifier =
                Modifier
                  .weight(1f)
                  .clip(RoundedCornerShape(20.dp))
                  .clickable { onUpdateAppSettings { it.copy(themeMode = mode) } }
                  .minimumInteractiveComponentSize()
                  .testTag("onboarding_theme_${mode.name}"),
              shape = RoundedCornerShape(20.dp),
              color =
                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                else MaterialTheme.colorScheme.surfaceContainer,
              border =
                BorderStroke(
                  width = if (selected) 1.5.dp else 1.dp,
                  color =
                    if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                ),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Icon(
                  imageVector = modeIcon,
                  contentDescription = null,
                  tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(20.dp),
                )
                Text(
                  text = mode.label,
                  style = MaterialTheme.typography.titleSmall,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
          }
        }
      }
    }

    OnboardingSectionSurface {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text("Dynamic Wallpaper Color", style = MaterialTheme.typography.titleSmall)
            Text(
              text = "Match your Android Material You palette",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Switch(
            checked = appSettings.dynamicColor,
            onCheckedChange = { enabled -> onUpdateAppSettings { it.copy(dynamicColor = enabled) } },
            modifier = Modifier.minimumInteractiveComponentSize().testTag("onboarding_dynamic_color_switch"),
          )
        }
        Spacer(Modifier.height(12.dp))
      }

      Text("Accent Seed", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
      Spacer(Modifier.height(10.dp))
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        val swatchesEnabled = !appSettings.dynamicColor || Build.VERSION.SDK_INT < Build.VERSION_CODES.S
        CURATED_ACCENT_PRESETS.forEach { preset ->
          val swatchColor = M3TonalColorEngine.parseHexOrDefault(preset.hex)
          val selected = appSettings.accentSeedHex.equals(preset.hex, ignoreCase = true)
          Surface(
            shape = CircleShape,
            color = if (swatchesEnabled) swatchColor else swatchColor.copy(alpha = 0.35f),
            border =
              BorderStroke(
                width = if (selected) 2.5.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
              ),
            modifier =
              Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(enabled = swatchesEnabled) {
                  onUpdateAppSettings { it.copy(accentSeedHex = preset.hex, dynamicColor = false) }
                }
                .testTag("onboarding_accent_${preset.name.lowercase().replace(' ', '_')}"),
          ) {
            if (selected) {
              Box(contentAlignment = Alignment.Center) {
                val iconTint =
                  if (swatchColor.red * 0.299f + swatchColor.green * 0.587f + swatchColor.blue * 0.114f > 0.55f) {
                    Color.Black
                  } else {
                    Color.White
                  }
                Icon(
                  imageVector = Icons.Rounded.Check,
                  contentDescription = preset.name,
                  tint = iconTint,
                  modifier = Modifier.size(18.dp),
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
private fun OnboardingAlertsPage(
  appSettings: AppSettings,
  cloudSettings: CloudSettings,
  hasNotificationPermission: Boolean,
  onToggleNotifications: (Boolean) -> Unit,
  onRequestNotificationPermission: () -> Unit,
) {
  OnboardingBalancedPage(verticalSpacing = 16.dp) {
    OnboardingPageHeader(
      icon = Icons.Rounded.NotificationsActive,
      title = "Build Alerts",
      subtitle = "Receive instant notifications when remote NDK builds complete or fail.",
    )

    OnboardingSectionSurface {
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
            text = "Background checks run automatically for dispatched workflows.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(
          checked = cloudSettings.notificationsEnabled,
          onCheckedChange = onToggleNotifications,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("onboarding_notifications_switch"),
        )
      }

      if (!hasNotificationPermission) {
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
          onClick = onRequestNotificationPermission,
          modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("onboarding_allow_notifications_btn"),
        ) {
          Icon(Icons.Rounded.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(8.dp))
          Text("Allow Notification Permission")
        }
      }
    }

    Surface(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(20.dp),
      color = MaterialTheme.colorScheme.surfaceContainer,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Ready: ${appSettings.themeMode.label}",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = if (cloudSettings.isAuthenticated) "@${cloudSettings.githubUsername}" else "Local Mode",
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Medium,
          fontFamily = JetBrainsMonoFontFamily,
          color = MaterialTheme.colorScheme.primary,
        )
      }
    }
  }
}

@Composable
private fun OnboardingPageHeader(
  icon: ImageVector,
  title: String,
  subtitle: String,
) {
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(26.dp),
      )
      Text(
        text = title,
        style = MaterialTheme.typography.headlineMedium,
      )
    }
    Text(
      text = subtitle,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun OnboardingHighlightCard(
  icon: ImageVector,
  title: String,
  description: String,
) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(22.dp),
    color = MaterialTheme.colorScheme.surfaceContainer,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
  ) {
    Row(
      modifier = Modifier.padding(16.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.Top,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(22.dp),
      )
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(
          text = description,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun OnboardingSectionSurface(content: @Composable () -> Unit) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(24.dp),
    color = MaterialTheme.colorScheme.surfaceContainer,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      content()
    }
  }
}
