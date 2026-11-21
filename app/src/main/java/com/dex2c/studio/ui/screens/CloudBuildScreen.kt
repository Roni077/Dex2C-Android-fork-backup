package com.dex2c.studio.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dex2c.studio.R
import com.dex2c.studio.cloud.CloudBuildRunState
import com.dex2c.studio.cloud.CloudSettings
import com.dex2c.studio.cloud.Dex2cWorkflowGenerator
import com.dex2c.studio.cloud.DeviceAuthState
import com.dex2c.studio.cloud.GITHUB_DEVICE_VERIFICATION_URL
import com.dex2c.studio.cloud.GitHubArtifactItem
import com.dex2c.studio.cloud.GitHubWorkflowRunSummary
import com.dex2c.studio.cloud.RepoSetupState
import com.dex2c.studio.transpiler.JniBindingStyle
import com.dex2c.studio.transpiler.TranspilerConfig
import com.dex2c.studio.ui.CloudApkChoice
import com.dex2c.studio.ui.components.BuildMonitorCard
import com.dex2c.studio.ui.components.ExpressiveMotion
import com.dex2c.studio.ui.components.GitHubDeviceAuthCard
import com.dex2c.studio.ui.components.TagPill
import com.dex2c.studio.ui.theme.JetBrainsMonoFontFamily
import java.util.Locale
import kotlinx.coroutines.delay

enum class CloudStage {
  SETUP,
  DISPATCH,
  MONITOR,
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CloudBuildScreen(
  cloudSettings: CloudSettings,
  transpilerConfig: TranspilerConfig,
  deviceAuthState: DeviceAuthState,
  repoSetupState: RepoSetupState,
  cloudRunState: CloudBuildRunState,
  isAutoPollEnabled: Boolean,
  recentRuns: List<GitHubWorkflowRunSummary>,
  apkChoice: CloudApkChoice?,
  apkUrlFallback: String,
  onStartDeviceLogin: () -> Unit,
  onCancelDeviceLogin: () -> Unit,
  onSignOut: () -> Unit,
  onCopyCode: (String) -> Unit,
  onOpenBrowserUrl: (String) -> Unit,
  onUpdateRepoName: (String) -> Unit,
  onInitializeRepoAndWorkflow: () -> Unit,
  onVerifyRepo: () -> Unit,
  onPickApkFile: () -> Unit,
  onUseLiveApk: () -> Unit,
  onApkUrlChange: (String) -> Unit,
  onDispatchBuild: () -> Unit,
  onToggleAutoPoll: () -> Unit,
  onRefreshMonitor: () -> Unit,
  onRefreshRuns: () -> Unit,
  onAttachToRun: (Long) -> Unit,
  onDownloadArtifact: (GitHubArtifactItem) -> Unit,
  modifier: Modifier = Modifier,
  onRetryMonitor: (() -> Unit)? = null,
) {
  var lastAutoOpenedUserCode by rememberSaveable { mutableStateOf<String?>(null) }
  var activeStage by rememberSaveable {
    mutableStateOf(
      when {
        cloudRunState !is CloudBuildRunState.Idle -> CloudStage.MONITOR
        cloudSettings.isAuthenticated && cloudSettings.workflowInitialized -> CloudStage.DISPATCH
        else -> CloudStage.SETUP
      }
    )
  }

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

  val isSetupComplete =
    cloudSettings.isAuthenticated &&
      (cloudSettings.workflowInitialized || (repoSetupState is RepoSetupState.Ready && repoSetupState.hasWorkflow))
  val canRun =
    cloudSettings.isAuthenticated &&
      cloudSettings.workflowInitialized &&
      (apkChoice != null || apkUrlFallback.isNotBlank()) &&
      cloudRunState !is CloudBuildRunState.UploadingInputs &&
      cloudRunState !is CloudBuildRunState.Dispatching

  val (setupIcon, setupBadgeColor) =
    when {
      isSetupComplete -> Icons.Rounded.CheckCircle to MaterialTheme.colorScheme.primary
      repoSetupState is RepoSetupState.Error || deviceAuthState is DeviceAuthState.Error ->
        Icons.Rounded.ErrorOutline to MaterialTheme.colorScheme.error
      cloudSettings.isAuthenticated || repoSetupState is RepoSetupState.Working ->
        Icons.Rounded.Sync to MaterialTheme.colorScheme.tertiary
      else -> Icons.Rounded.Lock to MaterialTheme.colorScheme.onSurfaceVariant
    }
  val dispatchBadgeColor =
    when {
      canRun -> MaterialTheme.colorScheme.primary
      apkChoice != null || apkUrlFallback.isNotBlank() -> MaterialTheme.colorScheme.tertiary
      else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
  val (monitorIcon, monitorBadgeColor) =
    when (cloudRunState) {
      is CloudBuildRunState.UploadingInputs,
      is CloudBuildRunState.Dispatching,
      is CloudBuildRunState.Polling,
      is CloudBuildRunState.SavingArtifact -> Icons.Rounded.Sync to MaterialTheme.colorScheme.tertiary
      is CloudBuildRunState.Completed ->
        if (cloudRunState.conclusion == "success") Icons.Rounded.CheckCircle to MaterialTheme.colorScheme.primary
        else Icons.Rounded.ErrorOutline to MaterialTheme.colorScheme.error
      is CloudBuildRunState.Error -> Icons.Rounded.ErrorOutline to MaterialTheme.colorScheme.error
      is CloudBuildRunState.Idle ->
        Icons.Rounded.History to
          if (recentRuns.isNotEmpty()) MaterialTheme.colorScheme.secondary
          else MaterialTheme.colorScheme.onSurfaceVariant
    }

  BoxWithConstraints(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    val isWide = maxWidth >= 680.dp
    val contentMax = if (maxWidth >= 960.dp) 1100.dp else 880.dp

    Column(
      modifier =
        Modifier
          .fillMaxSize()
          .widthIn(max = contentMax)
          .padding(horizontal = 16.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      if (cloudSettings.isAuthenticated) {
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.CheckCircle,
              contentDescription = null,
              modifier = Modifier.size(14.dp),
              tint = MaterialTheme.colorScheme.primary,
            )
            Text(
              text = "@${cloudSettings.githubUsername} • ${cloudSettings.effectiveOwner}/${cloudSettings.repoName}",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }

      Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp)) {
          SegmentedButton(
            selected = activeStage == CloudStage.SETUP,
            onClick = { activeStage = CloudStage.SETUP },
            shape = SegmentedButtonDefaults.itemShape(0, 3),
            modifier = Modifier.minimumInteractiveComponentSize().testTag("cloud_tab_SETUP"),
            icon = { StageStatusIcon(icon = setupIcon, tint = setupBadgeColor) },
          ) {
            Text(stringResource(R.string.cloud_stage_setup), maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
          SegmentedButton(
            selected = activeStage == CloudStage.DISPATCH,
            onClick = { activeStage = CloudStage.DISPATCH },
            shape = SegmentedButtonDefaults.itemShape(1, 3),
            modifier = Modifier.minimumInteractiveComponentSize().testTag("cloud_tab_DISPATCH"),
            icon = { StageStatusIcon(icon = Icons.Rounded.PlayArrow, tint = dispatchBadgeColor) },
          ) {
            Text(stringResource(R.string.cloud_stage_dispatch), maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
          SegmentedButton(
            selected = activeStage == CloudStage.MONITOR,
            onClick = { activeStage = CloudStage.MONITOR },
            shape = SegmentedButtonDefaults.itemShape(2, 3),
            modifier = Modifier.minimumInteractiveComponentSize().testTag("cloud_tab_MONITOR"),
            icon = { StageStatusIcon(icon = monitorIcon, tint = monitorBadgeColor) },
          ) {
            Text(stringResource(R.string.cloud_stage_monitor), maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
        }
      }

      val paneScrollState = rememberScrollState()
      Box(
        modifier =
          Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(paneScrollState)
            .padding(bottom = 24.dp),
      ) {
        AnimatedContent(
          targetState = activeStage,
          transitionSpec = {
            ExpressiveMotion.sharedAxisHorizontal(forward = targetState.ordinal >= initialState.ordinal)
          },
          label = "cloud_stage_pane",
        ) { stage ->
          when (stage) {
            CloudStage.SETUP -> {
              if (isWide) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(12.dp),
                  verticalAlignment = Alignment.Top,
                ) {
                  SetupAuthCard(
                    cloudSettings = cloudSettings,
                    deviceAuthState = deviceAuthState,
                    repoSetupState = repoSetupState,
                    onInitializeRepoAndWorkflow = onInitializeRepoAndWorkflow,
                    onStartDeviceLogin = onStartDeviceLogin,
                    onCancelDeviceLogin = onCancelDeviceLogin,
                    onCopyCode = onCopyCode,
                    onOpenBrowserUrl = onOpenBrowserUrl,
                    onSignOut = onSignOut,
                    modifier = Modifier.weight(1f),
                  )
                  SetupRepoCard(
                    cloudSettings = cloudSettings,
                    repoSetupState = repoSetupState,
                    isSetupComplete = isSetupComplete,
                    onUpdateRepoName = onUpdateRepoName,
                    onInitializeRepoAndWorkflow = onInitializeRepoAndWorkflow,
                    onVerifyRepo = onVerifyRepo,
                    onOpenBrowserUrl = onOpenBrowserUrl,
                    onProceedToDispatch = { activeStage = CloudStage.DISPATCH },
                    modifier = Modifier.weight(1f),
                  )
                }
              } else {
                Column(
                  modifier = Modifier.fillMaxWidth(),
                  verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                  SetupAuthCard(
                    cloudSettings = cloudSettings,
                    deviceAuthState = deviceAuthState,
                    repoSetupState = repoSetupState,
                    onInitializeRepoAndWorkflow = onInitializeRepoAndWorkflow,
                    onStartDeviceLogin = onStartDeviceLogin,
                    onCancelDeviceLogin = onCancelDeviceLogin,
                    onCopyCode = onCopyCode,
                    onOpenBrowserUrl = onOpenBrowserUrl,
                    onSignOut = onSignOut,
                  )
                  SetupRepoCard(
                    cloudSettings = cloudSettings,
                    repoSetupState = repoSetupState,
                    isSetupComplete = isSetupComplete,
                    onUpdateRepoName = onUpdateRepoName,
                    onInitializeRepoAndWorkflow = onInitializeRepoAndWorkflow,
                    onVerifyRepo = onVerifyRepo,
                    onOpenBrowserUrl = onOpenBrowserUrl,
                    onProceedToDispatch = { activeStage = CloudStage.DISPATCH },
                  )
                }
              }
            }

            CloudStage.DISPATCH -> {
              Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                if (!isSetupComplete) {
                  Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                  ) {
                    Row(
                      modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                      Text(
                        text =
                          if (!cloudSettings.isAuthenticated) "Sign in to GitHub in Setup before dispatching."
                          else "Initialize private workspace repo in Setup first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.weight(1f),
                      )
                      TextButton(
                        onClick = { activeStage = CloudStage.SETUP },
                        modifier = Modifier.testTag("cloud_go_to_setup_btn"),
                      ) {
                        Text("Open Setup")
                      }
                    }
                  }
                }

                CloudStepCard(
                  title = stringResource(R.string.cloud_step_build),
                  trailingBadge = {
                    TagPill(
                      text = "NDK ${cloudSettings.ndkVersion}",
                      containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                      contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                  },
                ) {
                  Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                  ) {
                    Row(
                      modifier = Modifier.padding(12.dp),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                      Icon(
                        imageVector = Icons.Rounded.Android,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                      )
                      Column(modifier = Modifier.weight(1f)) {
                        Text(
                          text = apkChoice?.label ?: "No APK Selected",
                          style = MaterialTheme.typography.titleSmall,
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                          text =
                            if (apkChoice != null) {
                              "${formatFileSize(apkChoice.byteSize.toLong())} • Direct commit limit ${cloudSettings.maxDirectUploadMb} MB"
                            } else {
                              "Pick an APK from Files or paste an APK URL fallback below"
                            },
                          style = MaterialTheme.typography.bodySmall,
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis,
                        )
                      }
                    }
                  }

                  Spacer(Modifier.height(8.dp))
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    OutlinedButton(
                      onClick = onPickApkFile,
                      shape = RoundedCornerShape(20.dp),
                      contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                      modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("cloud_pick_apk_btn"),
                    ) {
                      Icon(
                        imageVector = Icons.Rounded.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                      )
                      Spacer(Modifier.width(6.dp))
                      Text(stringResource(R.string.cloud_pick_apk), maxLines = 1)
                    }
                    OutlinedButton(
                      onClick = onUseLiveApk,
                      shape = RoundedCornerShape(20.dp),
                      contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                      modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("cloud_use_live_apk_btn"),
                    ) {
                      Icon(
                        imageVector = Icons.Rounded.Android,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                      )
                      Spacer(Modifier.width(6.dp))
                      Text(stringResource(R.string.cloud_use_live_apk), maxLines = 1)
                    }
                  }

                  Spacer(Modifier.height(8.dp))
                  OutlinedTextField(
                    value = apkUrlFallback,
                    onValueChange = onApkUrlChange,
                    label = { Text("APK URL Fallback (optional)") },
                    placeholder = { Text("https://example.com/target.apk") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                    modifier = Modifier.fillMaxWidth().testTag("cloud_apk_url_input"),
                  )

                  Spacer(Modifier.height(8.dp))
                  FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                  ) {
                    TagPill(
                      text = if (transpilerConfig.allowInit) "allow-init: ON" else "allow-init: OFF",
                      containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    )
                    TagPill(
                      text =
                        if (transpilerConfig.jniStyle == JniBindingStyle.REGISTER_NATIVES) "RegisterNatives"
                        else "JNI Export",
                      containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    )
                    TagPill(
                      text = "Loader: ${transpilerConfig.customLoaderClass.substringAfterLast('.')}",
                      containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    )
                    TagPill(
                      text = if (transpilerConfig.forceCustomLoader) "Force -r: ON" else "Force -r: OFF",
                      containerColor =
                        if (transpilerConfig.forceCustomLoader) MaterialTheme.colorScheme.tertiaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                      contentColor =
                        if (transpilerConfig.forceCustomLoader) MaterialTheme.colorScheme.onTertiaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TagPill(
                      text = cloudSettings.signingMode.label,
                      containerColor = MaterialTheme.colorScheme.primaryContainer,
                      contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                  }

                  Spacer(Modifier.height(12.dp))
                  Button(
                    onClick = {
                      onDispatchBuild()
                      activeStage = CloudStage.MONITOR
                    },
                    enabled = canRun,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("cloud_run_build_btn"),
                  ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.cloud_run_build_btn), maxLines = 1)
                  }
                }
              }
            }

            CloudStage.MONITOR -> {
              if (isWide) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(12.dp),
                  verticalAlignment = Alignment.Top,
                ) {
                  BuildMonitorCard(
                    cloudSettings = cloudSettings,
                    cloudRunState = cloudRunState,
                    isAutoPollEnabled = isAutoPollEnabled,
                    onToggleAutoPoll = onToggleAutoPoll,
                    onRefreshMonitor = onRefreshMonitor,
                    onRetryError = onRetryMonitor ?: onRefreshMonitor,
                    onOpenBrowserUrl = onOpenBrowserUrl,
                    onDownloadArtifact = onDownloadArtifact,
                    modifier = Modifier.weight(1.15f),
                  )
                  RecentCloudRunsCard(
                    cloudSettings = cloudSettings,
                    recentRuns = recentRuns,
                    onRefreshRuns = onRefreshRuns,
                    onAttachToRun = onAttachToRun,
                    modifier = Modifier.weight(0.85f),
                  )
                }
              } else {
                Column(
                  modifier = Modifier.fillMaxWidth(),
                  verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                  BuildMonitorCard(
                    cloudSettings = cloudSettings,
                    cloudRunState = cloudRunState,
                    isAutoPollEnabled = isAutoPollEnabled,
                    onToggleAutoPoll = onToggleAutoPoll,
                    onRefreshMonitor = onRefreshMonitor,
                    onRetryError = onRetryMonitor ?: onRefreshMonitor,
                    onOpenBrowserUrl = onOpenBrowserUrl,
                    onDownloadArtifact = onDownloadArtifact,
                  )
                  RecentCloudRunsCard(
                    cloudSettings = cloudSettings,
                    recentRuns = recentRuns,
                    onRefreshRuns = onRefreshRuns,
                    onAttachToRun = onAttachToRun,
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun StageStatusIcon(
  icon: ImageVector,
  tint: Color,
) {
  Icon(
    imageVector = icon,
    contentDescription = null,
    tint = tint,
    modifier = Modifier.size(16.dp),
  )
}

@Composable
private fun SetupAuthCard(
  cloudSettings: CloudSettings,
  deviceAuthState: DeviceAuthState,
  repoSetupState: RepoSetupState,
  onInitializeRepoAndWorkflow: () -> Unit,
  onStartDeviceLogin: () -> Unit,
  onCancelDeviceLogin: () -> Unit,
  onCopyCode: (String) -> Unit,
  onOpenBrowserUrl: (String) -> Unit,
  onSignOut: () -> Unit,
  modifier: Modifier = Modifier,
) {
  CloudStepCard(
    title = stringResource(R.string.cloud_step_auth),
    modifier = modifier,
    trailingBadge = {
      if (cloudSettings.isAuthenticated) {
        TagPill(
          text = "@${cloudSettings.githubUsername}",
          containerColor = MaterialTheme.colorScheme.primaryContainer,
          contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
      } else {
        TagPill(
          text = if (cloudSettings.usingCustomClientId) "Custom OAuth ID" else "gh CLI Ready",
          containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
          contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    },
  ) {
    GitHubDeviceAuthCard(
      cloudSettings = cloudSettings,
      deviceAuthState = deviceAuthState,
      repoSetupState = repoSetupState,
      onRetryRepoSetup = onInitializeRepoAndWorkflow,
      onStartDeviceLogin = onStartDeviceLogin,
      onCancelDeviceLogin = onCancelDeviceLogin,
      onCopyCode = onCopyCode,
      onOpenBrowserUrl = onOpenBrowserUrl,
      tagPrefix = "cloud",
      compact = true,
      onSignOut = onSignOut,
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SetupRepoCard(
  cloudSettings: CloudSettings,
  repoSetupState: RepoSetupState,
  isSetupComplete: Boolean,
  onUpdateRepoName: (String) -> Unit,
  onInitializeRepoAndWorkflow: () -> Unit,
  onVerifyRepo: () -> Unit,
  onOpenBrowserUrl: (String) -> Unit,
  onProceedToDispatch: () -> Unit,
  modifier: Modifier = Modifier,
) {
  CloudStepCard(
    title = stringResource(R.string.cloud_step_repo),
    modifier = modifier,
    trailingBadge = {
      when (repoSetupState) {
        is RepoSetupState.Ready -> {
          TagPill(
            text = if (repoSetupState.isPrivate) "Private Repo Ready" else "Repo Ready",
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
          )
        }
        is RepoSetupState.Working -> {
          TagPill(
            text = "Initializing…",
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
          )
        }
        else -> {
          TagPill(
            text = "Setup Required",
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
          )
        }
      }
    },
  ) {
    Text(
      text =
        "Hosts ${Dex2cWorkflowGenerator.WORKFLOW_PATH}, ${Dex2cWorkflowGenerator.DCC_CFG_PATH}, and ${Dex2cWorkflowGenerator.FILTER_TXT_PATH}.",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
      value = cloudSettings.repoName,
      onValueChange = onUpdateRepoName,
      label = { Text("Private Workspace Repository Name") },
      leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
      singleLine = true,
      shape = RoundedCornerShape(14.dp),
      textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
      modifier = Modifier.fillMaxWidth().testTag("cloud_repo_name_input"),
    )
    Spacer(Modifier.height(8.dp))

    when (repoSetupState) {
      is RepoSetupState.Working -> {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.padding(vertical = 6.dp),
        ) {
          CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
          Text(repoSetupState.stepLabel, style = MaterialTheme.typography.bodySmall)
        }
      }

      is RepoSetupState.Ready -> {
        Surface(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(16.dp),
          color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
          Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "${repoSetupState.owner}/${repoSetupState.repo}",
                style = MaterialTheme.typography.titleSmall,
                fontFamily = JetBrainsMonoFontFamily,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
              )
              Spacer(Modifier.width(6.dp))
              TagPill(
                text = if (repoSetupState.hasWorkflow) "dex2c.yml synced" else "missing dex2c.yml",
                containerColor =
                  if (repoSetupState.hasWorkflow) MaterialTheme.colorScheme.primaryContainer
                  else MaterialTheme.colorScheme.errorContainer,
                contentColor =
                  if (repoSetupState.hasWorkflow) MaterialTheme.colorScheme.onPrimaryContainer
                  else MaterialTheme.colorScheme.onErrorContainer,
              )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              OutlinedButton(
                onClick = onInitializeRepoAndWorkflow,
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier.minimumInteractiveComponentSize().testTag("cloud_resync_workflow_btn"),
              ) {
                Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text("Re-sync", maxLines = 1)
              }
              OutlinedButton(
                onClick = { onOpenBrowserUrl(repoSetupState.htmlUrl) },
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier.minimumInteractiveComponentSize().testTag("cloud_open_repo_btn"),
              ) {
                Icon(Icons.Rounded.OpenInBrowser, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text("Open Repo", maxLines = 1)
              }
              TextButton(
                onClick = onVerifyRepo,
                modifier = Modifier.minimumInteractiveComponentSize().testTag("cloud_verify_repo_btn"),
              ) {
                Text("Verify", maxLines = 1)
              }
            }
          }
        }
      }

      is RepoSetupState.Error -> {
        if (!cloudSettings.isAuthenticated || !repoSetupState.message.contains("Sign in", ignoreCase = true)) {
          Text(
            text = repoSetupState.message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
          Spacer(Modifier.height(6.dp))
        }
        Button(
          onClick = onInitializeRepoAndWorkflow,
          enabled = cloudSettings.isAuthenticated,
          shape = RoundedCornerShape(20.dp),
          modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("cloud_init_repo_btn"),
        ) {
          Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text(stringResource(R.string.cloud_init_repo_btn), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
      }

      is RepoSetupState.NotConfigured -> {
        Button(
          onClick = onInitializeRepoAndWorkflow,
          enabled = cloudSettings.isAuthenticated,
          shape = RoundedCornerShape(20.dp),
          modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("cloud_init_repo_btn"),
        ) {
          Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text(stringResource(R.string.cloud_init_repo_btn), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
      }
    }

    if (isSetupComplete) {
      Spacer(Modifier.height(8.dp))
      Button(
        onClick = onProceedToDispatch,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("cloud_next_dispatch_btn"),
      ) {
        Text("Continue to Dispatch", maxLines = 1)
        Spacer(Modifier.width(6.dp))
        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
      }
    }
  }
}

@Composable
private fun RecentCloudRunsCard(
  cloudSettings: CloudSettings,
  recentRuns: List<GitHubWorkflowRunSummary>,
  onRefreshRuns: () -> Unit,
  onAttachToRun: (Long) -> Unit,
  modifier: Modifier = Modifier,
) {
  CloudStepCard(
    title = "Recent Cloud Runs",
    modifier = modifier,
    trailingBadge = {
      if (cloudSettings.isRepoConfigured) {
        TextButton(
          onClick = onRefreshRuns,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("cloud_refresh_runs_btn"),
        ) {
          Icon(Icons.Rounded.Refresh, contentDescription = "Refresh Runs", modifier = Modifier.size(15.dp))
          Spacer(Modifier.width(4.dp))
          Text("Refresh", maxLines = 1)
        }
      }
    },
  ) {
    if (recentRuns.isEmpty()) {
      Text(
        text =
          if (cloudSettings.isRepoConfigured) "No recent GitHub Actions runs recorded yet."
          else "Configure your private repository in Setup to view run history.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    } else {
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        recentRuns.take(3).forEach { run ->
          Surface(
            modifier =
              Modifier.fillMaxWidth()
                .clickable { onAttachToRun(run.id) }
                .testTag("cloud_run_item_${run.id}"),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Run #${run.runNumber}",
                  style = MaterialTheme.typography.titleSmall,
                  fontFamily = JetBrainsMonoFontFamily,
                  maxLines = 1,
                )
                Text(
                  text = run.createdAt,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
              TagPill(
                text = run.conclusion ?: run.status,
                containerColor =
                  when (run.conclusion) {
                    "success" -> MaterialTheme.colorScheme.primaryContainer
                    "failure" -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.tertiaryContainer
                  },
                contentColor =
                  when (run.conclusion) {
                    "success" -> MaterialTheme.colorScheme.onPrimaryContainer
                    "failure" -> MaterialTheme.colorScheme.onErrorContainer
                    else -> MaterialTheme.colorScheme.onTertiaryContainer
                  },
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun CloudStepCard(
  title: String,
  modifier: Modifier = Modifier,
  trailingBadge: (@Composable () -> Unit)? = null,
  content: @Composable () -> Unit,
) {
  Surface(
    modifier = modifier.fillMaxWidth().animateContentSize(ExpressiveMotion.spatialSpring()),
    shape = RoundedCornerShape(24.dp),
    color = MaterialTheme.colorScheme.surfaceContainer,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.primary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
        if (trailingBadge != null) {
          Spacer(Modifier.width(6.dp))
          trailingBadge()
        }
      }
      Spacer(Modifier.height(8.dp))
      content()
    }
  }
}

private fun formatFileSize(bytes: Long): String {
  if (bytes <= 0L) return "0 B"
  val kb = bytes / 1024.0
  val mb = kb / 1024.0
  return when {
    mb >= 1.0 -> String.format(Locale.US, "%.2f MB", mb)
    kb >= 1.0 -> String.format(Locale.US, "%.1f KB", kb)
    else -> "$bytes B"
  }
}
