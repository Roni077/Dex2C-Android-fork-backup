package com.example.ui.screens

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.cloud.CloudSettings
import com.example.cloud.DeviceAuthState
import com.example.data.AppSettings
import com.example.data.AppThemeMode
import com.example.ui.components.AccentColorPickerSection
import com.example.ui.components.SyntaxCodeViewer
import com.example.ui.components.TagPill
import com.example.ui.components.expressivePressScale
import com.example.ui.theme.JetBrainsMonoFontFamily
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private const val ONBOARDING_PAGE_COUNT = 4

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

  BackHandler {
    when {
      pagerState.currentPage > 0 ->
        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
      isReplayMode -> onDismissReplay()
      else -> onFinish()
    }
  }

  Column(
    modifier =
      modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    // Top step bar with Skip / Replay Close
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .widthIn(max = 680.dp)
          .padding(horizontal = 16.dp, vertical = 10.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        if (isReplayMode) {
          IconButton(
            onClick = onDismissReplay,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("onboarding_close_btn"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
              contentDescription = stringResource(R.string.action_back),
            )
          }
        }
        TagPill(
          text = "Step ${pagerState.currentPage + 1} of $ONBOARDING_PAGE_COUNT",
          containerColor = MaterialTheme.colorScheme.primaryContainer,
          contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
      }

      if (pagerState.currentPage < ONBOARDING_PAGE_COUNT - 1) {
        TextButton(
          onClick = { if (isReplayMode) onDismissReplay() else onFinish() },
          modifier = Modifier.minimumInteractiveComponentSize().testTag("onboarding_skip_btn"),
        ) {
          Text(
            text =
              if (isReplayMode) stringResource(R.string.onboarding_done)
              else stringResource(R.string.onboarding_skip)
          )
        }
      } else {
        Spacer(Modifier.height(48.dp))
      }
    }

    // Swipeable multi-step pager
    HorizontalPager(
      state = pagerState,
      modifier = Modifier.weight(1f).fillMaxWidth(),
    ) { page ->
      Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
      ) {
        Box(
          modifier =
            Modifier
              .fillMaxSize()
              .widthIn(max = 680.dp)
              .padding(horizontal = 20.dp),
        ) {
          when (page) {
            0 -> OnboardingTourPage()
            1 ->
              OnboardingThemeEditorPage(
                appSettings = appSettings,
                onUpdateAppSettings = onUpdateAppSettings,
              )
            2 ->
              OnboardingCloudSetupPage(
                cloudSettings = cloudSettings,
                deviceAuthState = deviceAuthState,
                onUpdateCloudSettings = onUpdateCloudSettings,
                onStartDeviceLogin = onStartDeviceLogin,
                onCancelDeviceLogin = onCancelDeviceLogin,
                onCopyCode = onCopyCode,
                onOpenBrowserUrl = onOpenBrowserUrl,
              )
            else ->
              OnboardingNotificationsFinishPage(
                appSettings = appSettings,
                cloudSettings = cloudSettings,
                hasNotificationPermission = hasNotificationPermission,
                onToggleNotifications = onToggleNotifications,
                onRequestNotificationPermission = onRequestNotificationPermission,
              )
          }
        }
      }
    }

    // Bottom navigation & animated indicators
    Surface(
      modifier = Modifier.fillMaxWidth(),
      color = MaterialTheme.colorScheme.surfaceContainerLow,
      tonalElevation = 2.dp,
    ) {
      Row(
        modifier =
          Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().widthIn(max = 680.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (pagerState.currentPage > 0) {
            OutlinedButton(
              onClick = {
                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
              },
              modifier = Modifier.minimumInteractiveComponentSize().testTag("onboarding_prev_btn"),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
              )
              Spacer(Modifier.width(6.dp))
              Text(stringResource(R.string.action_back))
            }
          } else {
            Spacer(Modifier.width(96.dp))
          }

          Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            repeat(ONBOARDING_PAGE_COUNT) { index ->
              val isSelected = pagerState.currentPage == index
              val dotWidth by
                animateDpAsState(
                  targetValue = if (isSelected) 24.dp else 8.dp,
                  animationSpec = spring(),
                  label = "dotWidth",
                )
              val dotColor by
                animateColorAsState(
                  targetValue =
                    if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant,
                  label = "dotColor",
                )
              Box(
                modifier =
                  Modifier
                    .height(8.dp)
                    .width(dotWidth)
                    .clip(CircleShape)
                    .background(dotColor),
              )
            }
          }

          val isLast = pagerState.currentPage == ONBOARDING_PAGE_COUNT - 1
          val nextInteraction = remember { MutableInteractionSource() }
          Button(
            onClick = {
              if (!isLast) {
                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
              } else {
                if (isReplayMode) onDismissReplay() else onFinish()
              }
            },
            interactionSource = nextInteraction,
            shape = RoundedCornerShape(20.dp),
            modifier =
              Modifier
                .expressivePressScale(nextInteraction, 0.96f)
                .minimumInteractiveComponentSize()
                .testTag(if (isLast) "onboarding_finish_btn" else "onboarding_next_btn"),
          ) {
            Text(
              text =
                if (!isLast) stringResource(R.string.onboarding_next)
                else if (isReplayMode) stringResource(R.string.onboarding_done)
                else stringResource(R.string.onboarding_get_started)
            )
            if (!isLast) {
              Spacer(Modifier.width(6.dp))
              Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun OnboardingTourPage() {
  Column(
    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Surface(
      modifier = Modifier.fillMaxWidth().height(180.dp),
      shape = RoundedCornerShape(24.dp),
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

    Text(
      text = "Welcome to Dex2C Studio",
      style = MaterialTheme.typography.headlineLarge,
    )
    Text(
      text =
        "Inspect Android DEX bytecode, transpile sensitive classes into native JNI C/C++, and optionally dispatch signed cloud builds via GitHub Actions.",
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    OnboardingFeatureCard(
      icon = Icons.Rounded.DataObject,
      title = "1. Deep Dalvik DEX Inspector",
      description = "Open APKs, multi-dex containers, or the live app APK to browse classes, opcodes, debug info, and structural map tables.",
    )
    OnboardingFeatureCard(
      icon = Icons.Rounded.Code,
      title = "2. Kirlif/d2c & Rust JNI Parity",
      description = "Apply dcc.cfg & filter.txt rules, XOR string obfuscation, control-flow flattening, and export CMake + Android.mk bundles.",
    )
    OnboardingFeatureCard(
      icon = Icons.Rounded.CloudQueue,
      title = "3. Cloud CI Builds & Live Alerts",
      description = "Connect your private GitHub workspace once to compile NDK shared libraries remotely and receive background completion alerts.",
    )
    Spacer(Modifier.height(12.dp))
  }
}

@Composable
private fun OnboardingThemeEditorPage(
  appSettings: AppSettings,
  onUpdateAppSettings: ((AppSettings) -> AppSettings) -> Unit,
) {
  val previewSnippet =
    """
    |JNIEXPORT void JNICALL
    |Java_com_app_Vault_unlock(JNIEnv* env, jobject obj) {
    |  /* Protected native stub */
    |}
    """.trimMargin()

  Column(
    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Icon(Icons.Rounded.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
      Text(
        text = "Appearance & Code Viewer",
        style = MaterialTheme.typography.headlineMedium,
      )
    }
    Text(
      text = "Choose your preferred silicon lab theme and code typography. Changes apply immediately.",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    OnboardingSectionSurface {
      Text("Color Theme", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
      Spacer(Modifier.height(8.dp))
      AppThemeMode.entries.forEach { mode ->
        Row(
          modifier =
            Modifier
              .fillMaxWidth()
              .clickable { onUpdateAppSettings { it.copy(themeMode = mode) } }
              .padding(vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          RadioButton(
            selected = appSettings.themeMode == mode,
            onClick = { onUpdateAppSettings { it.copy(themeMode = mode) } },
            modifier = Modifier.minimumInteractiveComponentSize().testTag("onboarding_theme_${mode.name}"),
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

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Spacer(Modifier.height(4.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text("Material You Dynamic Color", style = MaterialTheme.typography.titleSmall)
            Text(
              text = "Use Android wallpaper accent palette",
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
      }
    }

    OnboardingSectionSurface {
      Text("Custom Accent Seed", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
      Spacer(Modifier.height(10.dp))
      AccentColorPickerSection(
        selectedHex = appSettings.accentSeedHex,
        enabled = !appSettings.dynamicColor || Build.VERSION.SDK_INT < Build.VERSION_CODES.S,
        onSelectHex = { hex -> onUpdateAppSettings { it.copy(accentSeedHex = hex, dynamicColor = false) } },
      )
    }

    OnboardingSectionSurface {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text("Code Font Size", style = MaterialTheme.typography.titleSmall)
        TagPill(text = "${appSettings.codeFontSizeSp} sp")
      }
      Slider(
        value = appSettings.codeFontSizeSp.toFloat(),
        onValueChange = { v -> onUpdateAppSettings { it.copy(codeFontSizeSp = v.roundToInt()) } },
        valueRange = 10f..18f,
        steps = 7,
        modifier = Modifier.fillMaxWidth().testTag("onboarding_font_slider"),
      )
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text("Show Line Numbers", style = MaterialTheme.typography.titleSmall)
        Switch(
          checked = appSettings.showLineNumbers,
          onCheckedChange = { show -> onUpdateAppSettings { it.copy(showLineNumbers = show) } },
          modifier = Modifier.minimumInteractiveComponentSize().testTag("onboarding_line_numbers_switch"),
        )
      }
      Spacer(Modifier.height(8.dp))
      SyntaxCodeViewer(
        code = previewSnippet,
        modifier = Modifier.fillMaxWidth().height(112.dp),
      )
    }
    Spacer(Modifier.height(12.dp))
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OnboardingCloudSetupPage(
  cloudSettings: CloudSettings,
  deviceAuthState: DeviceAuthState,
  onUpdateCloudSettings: ((CloudSettings) -> CloudSettings) -> Unit,
  onStartDeviceLogin: () -> Unit,
  onCancelDeviceLogin: () -> Unit,
  onCopyCode: (String) -> Unit,
  onOpenBrowserUrl: (String) -> Unit,
) {
  Column(
    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Icon(Icons.Rounded.CloudQueue, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
      Text(
        text = "GitHub Cloud CI (Optional)",
        style = MaterialTheme.typography.headlineMedium,
      )
    }
    Text(
      text = "Link GitHub Device Flow OAuth to dispatch remote NDK + Kirlif/d2c APK builds in a private repository. You can also skip this and work 100% locally.",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    OnboardingSectionSurface {
      if (cloudSettings.isAuthenticated) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Connected as @${cloudSettings.githubUsername}",
              style = MaterialTheme.typography.titleSmall,
            )
            Text(
              text = "Workspace repository: ${cloudSettings.effectiveOwner}/${cloudSettings.repoName}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          TagPill(text = "Ready")
        }
      } else {
        Text("GitHub OAuth App Client ID", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
          value = cloudSettings.githubClientId,
          onValueChange = { cid -> onUpdateCloudSettings { it.copy(githubClientId = cid.trim()) } },
          label = { Text("OAuth Client ID (Ov23li…)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("onboarding_github_client_id"),
        )
        Spacer(Modifier.height(10.dp))

        when (deviceAuthState) {
          is DeviceAuthState.RequestingCode -> {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
              Text("Requesting GitHub Device Flow code…", style = MaterialTheme.typography.bodyMedium)
            }
          }

          is DeviceAuthState.AwaitingUser -> {
            Surface(
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(12.dp),
              color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
            ) {
              Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Enter this code on GitHub:", style = MaterialTheme.typography.labelLarge)
                Text(
                  text = deviceAuthState.userCode,
                  style = MaterialTheme.typography.headlineMedium,
                  fontFamily = JetBrainsMonoFontFamily,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.primary,
                )
                Text(
                  text = deviceAuthState.statusNote,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  OutlinedButton(
                    onClick = { onCopyCode(deviceAuthState.userCode) },
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("onboarding_copy_device_code"),
                  ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Copy Code")
                  }
                  Button(
                    onClick = { onOpenBrowserUrl(deviceAuthState.verificationUri) },
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("onboarding_open_device_url"),
                  ) {
                    Icon(Icons.Rounded.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Open GitHub")
                  }
                  TextButton(
                    onClick = onCancelDeviceLogin,
                    modifier = Modifier.minimumInteractiveComponentSize(),
                  ) {
                    Text("Cancel")
                  }
                }
              }
            }
          }

          else -> {
            if (deviceAuthState is DeviceAuthState.Error) {
              Text(
                text = deviceAuthState.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
              )
              Spacer(Modifier.height(8.dp))
            }
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Button(
                onClick = onStartDeviceLogin,
                enabled = cloudSettings.githubClientId.isNotBlank(),
                modifier = Modifier.minimumInteractiveComponentSize().testTag("onboarding_start_oauth_btn"),
              ) {
                Icon(Icons.Rounded.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Sign in via Device Flow")
              }
              OutlinedButton(
                onClick = { onOpenBrowserUrl("https://github.com/settings/developers") },
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
    Spacer(Modifier.height(12.dp))
  }
}

@Composable
private fun OnboardingNotificationsFinishPage(
  appSettings: AppSettings,
  cloudSettings: CloudSettings,
  hasNotificationPermission: Boolean,
  onToggleNotifications: (Boolean) -> Unit,
  onRequestNotificationPermission: () -> Unit,
) {
  Column(
    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Icon(Icons.Rounded.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
      Text(
        text = "Alerts & Ready to Build",
        style = MaterialTheme.typography.headlineMedium,
      )
    }
    Text(
      text = "Enable background notifications to get alerted immediately when a GitHub Actions cloud build finishes or fails.",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            text = "Background WorkManager checks active GitHub workflows even if you leave the app.",
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
        Spacer(Modifier.height(10.dp))
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

    OnboardingSectionSurface {
      Text("Setup Summary", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
      Spacer(Modifier.height(8.dp))
      OnboardingSummaryRow("Theme", appSettings.themeMode.label)
      OnboardingSummaryRow("Code Font", "${appSettings.codeFontSizeSp}sp • Lines: ${if (appSettings.showLineNumbers) "On" else "Off"}")
      OnboardingSummaryRow(
        "GitHub Cloud",
        if (cloudSettings.isAuthenticated) "@${cloudSettings.githubUsername}" else "Local Mode (configure later in Settings)",
      )
      OnboardingSummaryRow(
        "Build Alerts",
        if (cloudSettings.notificationsEnabled && hasNotificationPermission) "Active"
        else if (cloudSettings.notificationsEnabled) "Enabled (grant OS permission)"
        else "Muted",
      )
    }
    Spacer(Modifier.height(12.dp))
  }
}

@Composable
private fun OnboardingFeatureCard(
  icon: ImageVector,
  title: String,
  description: String,
) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(24.dp),
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
      Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
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
    Column(modifier = Modifier.padding(18.dp)) {
      content()
    }
  }
}

@Composable
private fun OnboardingSummaryRow(label: String, value: String) {
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
