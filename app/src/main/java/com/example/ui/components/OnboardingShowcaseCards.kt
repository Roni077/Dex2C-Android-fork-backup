package com.example.ui.components

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.cloud.CloudSettings
import com.example.data.AppSettings
import com.example.data.CURATED_ACCENT_PRESETS
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalSyntaxPalette

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingPipelineFlowCard(modifier: Modifier = Modifier) {
  Surface(
    modifier = modifier.fillMaxWidth().testTag("onboarding_pipeline_flow_card"),
    shape = RoundedCornerShape(22.dp),
    color = MaterialTheme.colorScheme.surfaceContainer,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.DataObject,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
          )
          Text(
            text = "Native Pipeline Flow",
            style = MaterialTheme.typography.titleSmall,
          )
        }
        TagPill(
          text = "4 Stages",
          containerColor = MaterialTheme.colorScheme.primaryContainer,
          contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        PipelineStageChip(label = "APK/DEX", modifier = Modifier.weight(1f))
        PipelineConnectorArrow()
        PipelineStageChip(label = "Filter", modifier = Modifier.weight(1f))
        PipelineConnectorArrow()
        PipelineStageChip(label = "JNI C++", modifier = Modifier.weight(1f))
        PipelineConnectorArrow()
        PipelineStageChip(label = ".so/APK", highlighted = true, modifier = Modifier.weight(1f))
      }

      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        TagPill(text = "C11 · RegisterNatives")
        TagPill(text = "XOR Strings")
        TagPill(text = "arm64-v8a / x86_64")
      }
    }
  }
}

@Composable
private fun PipelineStageChip(
  label: String,
  modifier: Modifier = Modifier,
  highlighted: Boolean = false,
) {
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(12.dp),
    color =
      if (highlighted) MaterialTheme.colorScheme.primaryContainer
      else MaterialTheme.colorScheme.surfaceContainerHigh,
    border =
      BorderStroke(
        1.dp,
        if (highlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
      ),
  ) {
    Box(
      modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = FontWeight.SemiBold,
        color =
          if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer
          else MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun PipelineConnectorArrow() {
  Icon(
    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
    contentDescription = null,
    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
    modifier = Modifier.padding(horizontal = 2.dp).size(14.dp),
  )
}

@Composable
fun OnboardingDalvikCppPeekCard(modifier: Modifier = Modifier) {
  var showCpp by rememberSaveable { mutableStateOf(true) }
  val palette = LocalSyntaxPalette.current

  Surface(
    modifier = modifier.fillMaxWidth().testTag("onboarding_code_peek_card"),
    shape = RoundedCornerShape(22.dp),
    color = MaterialTheme.colorScheme.surfaceContainer,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Dalvik ➔ JNI C++ Peek",
          style = MaterialTheme.typography.titleSmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          CodePeekToggleChip(
            label = "Dalvik",
            selected = !showCpp,
            onClick = { showCpp = false },
            modifier = Modifier.testTag("onboarding_peek_tab_dalvik"),
          )
          CodePeekToggleChip(
            label = "JNI C++",
            selected = showCpp,
            onClick = { showCpp = true },
            modifier = Modifier.testTag("onboarding_peek_tab_cpp"),
          )
        }
      }

      Surface(
        shape = RoundedCornerShape(14.dp),
        color = palette.codeBackground,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth(),
      ) {
        AnimatedContent(targetState = showCpp, label = "onboarding_code_peek") { isCpp ->
          val codeText =
            if (!isCpp) {
              buildAnnotatedString {
                withStyle(SpanStyle(color = palette.keyword)) { append(".method ") }
                withStyle(SpanStyle(color = palette.defaultText)) { append("verify()Z\n  ") }
                withStyle(SpanStyle(color = palette.opcode)) { append("const-string ") }
                withStyle(SpanStyle(color = palette.register)) { append("v0") }
                withStyle(SpanStyle(color = palette.defaultText)) { append(", ") }
                withStyle(SpanStyle(color = palette.stringLiteral)) { append("\"d2c_seed\"\n  ") }
                withStyle(SpanStyle(color = palette.opcode)) { append("invoke-static ") }
                withStyle(SpanStyle(color = palette.register)) { append("{v0}") }
                withStyle(SpanStyle(color = palette.defaultText)) { append(", ") }
                withStyle(SpanStyle(color = palette.typeName)) { append("Guard->ok()\n  ") }
                withStyle(SpanStyle(color = palette.opcode)) { append("return ") }
                withStyle(SpanStyle(color = palette.register)) { append("v0") }
              }
            } else {
              buildAnnotatedString {
                withStyle(SpanStyle(color = palette.comment)) { append("// Exported via RegisterNatives\n") }
                withStyle(SpanStyle(color = palette.keyword)) { append("JNIEXPORT ") }
                withStyle(SpanStyle(color = palette.typeName)) { append("jboolean ") }
                withStyle(SpanStyle(color = palette.defaultText)) { append("d2c_verify(") }
                withStyle(SpanStyle(color = palette.typeName)) { append("JNIEnv") }
                withStyle(SpanStyle(color = palette.defaultText)) { append("* env) {\n  ") }
                withStyle(SpanStyle(color = palette.keyword)) { append("return ") }
                withStyle(SpanStyle(color = palette.defaultText)) { append("d2c_xor_str(env, ") }
                withStyle(SpanStyle(color = palette.numberLiteral)) { append("0x5E") }
                withStyle(SpanStyle(color = palette.defaultText)) { append(");\n}") }
              }
            }
          Text(
            text = codeText,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = JetBrainsMonoFontFamily,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
          )
        }
      }
    }
  }
}

@Composable
private fun CodePeekToggleChip(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier.minimumInteractiveComponentSize().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Surface(
      shape = RoundedCornerShape(12.dp),
      color =
        if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
      border =
        BorderStroke(
          1.dp,
          if (selected) MaterialTheme.colorScheme.primary
          else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
        ),
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color =
          if (selected) MaterialTheme.colorScheme.onPrimaryContainer
          else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
      )
    }
  }
}

@Composable
fun OnboardingCloudStepsCard(
  cloudSettings: CloudSettings,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier.fillMaxWidth().testTag("onboarding_cloud_steps_card"),
    shape = RoundedCornerShape(22.dp),
    color = MaterialTheme.colorScheme.surfaceContainer,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "How Private Cloud Sync Works",
          style = MaterialTheme.typography.titleSmall,
        )
        TagPill(
          text = if (cloudSettings.isAuthenticated) "Connected" else "Local Ready",
          containerColor =
            if (cloudSettings.isAuthenticated) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.secondaryContainer,
          contentColor =
            if (cloudSettings.isAuthenticated) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSecondaryContainer,
        )
      }

      CloudStepTimelineRow(
        step = "1",
        title = "Private Repo",
        detail = "Creates an isolated dex2c-cloud-workspace repository.",
        completed = cloudSettings.isAuthenticated,
      )
      CloudStepTimelineRow(
        step = "2",
        title = "Workflow Sync",
        detail = "Commits dex2c.yml, dcc.cfg, and filter.txt automatically.",
        completed = cloudSettings.workflowInitialized,
      )
      CloudStepTimelineRow(
        step = "3",
        title = "NDK Runners",
        detail = "Compiles .so & APK artifacts remotely, or work offline locally.",
        completed = false,
      )
    }
  }
}

@Composable
private fun CloudStepTimelineRow(
  step: String,
  title: String,
  detail: String,
  completed: Boolean,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(10.dp),
    verticalAlignment = Alignment.Top,
  ) {
    Surface(
      shape = CircleShape,
      color =
        if (completed) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.primaryContainer,
      modifier = Modifier.padding(top = 2.dp).size(24.dp),
    ) {
      Box(contentAlignment = Alignment.Center) {
        Text(
          text = step,
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.Bold,
          color =
            if (completed) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onPrimaryContainer,
        )
      }
    }
    Column(modifier = Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.labelLarge)
      Text(
        text = detail,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
fun OnboardingLiveThemePreviewCard(
  appSettings: AppSettings,
  modifier: Modifier = Modifier,
) {
  val palette = LocalSyntaxPalette.current
  val surfacePreviewColor by animateColorAsState(
    targetValue = MaterialTheme.colorScheme.surfaceContainerHigh,
    label = "onboardingPreviewSurface",
  )
  val primaryColor by animateColorAsState(
    targetValue = MaterialTheme.colorScheme.primary,
    label = "onboardingPreviewPrimary",
  )
  val paletteLabel = resolvePaletteLabel(appSettings)

  Surface(
    modifier = modifier.fillMaxWidth().testTag("onboarding_live_theme_preview_card"),
    shape = RoundedCornerShape(22.dp),
    color = MaterialTheme.colorScheme.surfaceContainer,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.Palette,
            contentDescription = null,
            tint = primaryColor,
            modifier = Modifier.size(18.dp),
          )
          Text("Live Studio Preview", style = MaterialTheme.typography.titleSmall)
        }
        TagPill(
          text = "${appSettings.themeMode.label} · $paletteLabel",
          containerColor = MaterialTheme.colorScheme.primaryContainer,
          contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
      }

      Surface(
        shape = RoundedCornerShape(14.dp),
        color = surfacePreviewColor,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Surface(
            shape = RoundedCornerShape(10.dp),
            color = primaryColor,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(30.dp),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(Icons.Rounded.Code, contentDescription = null, modifier = Modifier.size(16.dp))
            }
          }
          Column(modifier = Modifier.weight(1f)) {
            Text("Workbench & JNI Surface", style = MaterialTheme.typography.labelMedium)
            Text(
              text = "Tonal elevation & button accent contrast",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }

      Surface(
        shape = RoundedCornerShape(12.dp),
        color = palette.codeBackground,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
          text =
            buildAnnotatedString {
              withStyle(SpanStyle(color = palette.keyword)) { append("JNIEXPORT ") }
              withStyle(SpanStyle(color = palette.typeName)) { append("jint ") }
              withStyle(SpanStyle(color = palette.defaultText)) { append("verify(") }
              withStyle(SpanStyle(color = palette.stringLiteral)) { append("\"ok\"") }
              withStyle(SpanStyle(color = palette.defaultText)) { append(");") }
            },
          style = MaterialTheme.typography.bodySmall,
          fontFamily = JetBrainsMonoFontFamily,
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
      }
    }
  }
}

@Composable
fun OnboardingSetupChecklistCard(
  appSettings: AppSettings,
  cloudSettings: CloudSettings,
  hasNotificationPermission: Boolean,
  onJumpToCloudStep: () -> Unit,
  onJumpToThemeStep: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val paletteLabel = resolvePaletteLabel(appSettings)
  val alertsSummary =
    when {
      !cloudSettings.notificationsEnabled -> "Alerts paused (toggle above anytime)"
      hasNotificationPermission -> "Active with system permission granted"
      else -> "Enabled · tap Allow above for OS alerts"
    }

  Surface(
    modifier = modifier.fillMaxWidth().testTag("onboarding_setup_checklist_card"),
    shape = RoundedCornerShape(22.dp),
    color = MaterialTheme.colorScheme.surfaceContainer,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.RocketLaunch,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
          )
          Text("Ready to Launch Checklist", style = MaterialTheme.typography.titleSmall)
        }
        TagPill(
          text = if (cloudSettings.isAuthenticated) "Cloud + Local" else "Local Ready",
          containerColor = MaterialTheme.colorScheme.primaryContainer,
          contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
      }

      ChecklistReviewRow(
        icon = Icons.Rounded.Tune,
        label = "Theme & Palette",
        value = "${appSettings.themeMode.label} · $paletteLabel",
        actionLabel = "Edit",
        actionTag = "onboarding_review_theme_btn",
        onAction = onJumpToThemeStep,
      )
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
      ChecklistReviewRow(
        icon = if (cloudSettings.isAuthenticated) Icons.Rounded.CloudDone else Icons.Rounded.CloudQueue,
        label = "Cloud NDK",
        value =
          if (cloudSettings.isAuthenticated) "@${cloudSettings.githubUsername} connected"
          else "Local offline mode",
        actionLabel = if (cloudSettings.isAuthenticated) "View" else "Connect",
        actionTag = "onboarding_review_cloud_btn",
        onAction = onJumpToCloudStep,
      )
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Surface(
          shape = RoundedCornerShape(10.dp),
          color =
            if (cloudSettings.notificationsEnabled && hasNotificationPermission)
              MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh,
          modifier = Modifier.size(32.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector =
                when {
                  !cloudSettings.notificationsEnabled -> Icons.Rounded.NotificationsOff
                  hasNotificationPermission -> Icons.Rounded.CheckCircle
                  else -> Icons.Rounded.NotificationsActive
                },
              contentDescription = null,
              tint =
                if (cloudSettings.notificationsEnabled && hasNotificationPermission)
                  MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(17.dp),
            )
          }
        }
        Column(modifier = Modifier.weight(1f)) {
          Text("Notifications", style = MaterialTheme.typography.labelLarge)
          Text(
            text = alertsSummary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}

@Composable
private fun ChecklistReviewRow(
  icon: ImageVector,
  label: String,
  value: String,
  actionLabel: String,
  actionTag: String,
  onAction: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Surface(
      shape = RoundedCornerShape(10.dp),
      color = MaterialTheme.colorScheme.primaryContainer,
      modifier = Modifier.size(32.dp),
    ) {
      Box(contentAlignment = Alignment.Center) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onPrimaryContainer,
          modifier = Modifier.size(17.dp),
        )
      }
    }
    Column(modifier = Modifier.weight(1f)) {
      Text(label, style = MaterialTheme.typography.labelLarge)
      Text(
        text = value,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    TextButton(
      onClick = onAction,
      contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
      modifier = Modifier.minimumInteractiveComponentSize().testTag(actionTag),
    ) {
      Text(actionLabel, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
  }
}

private fun resolvePaletteLabel(appSettings: AppSettings): String {
  return if (appSettings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    "Wallpaper Dynamic"
  } else {
    CURATED_ACCENT_PRESETS.firstOrNull {
      it.hex.equals(appSettings.accentSeedHex, ignoreCase = true)
    }?.name ?: appSettings.accentSeedHex
  }
}
