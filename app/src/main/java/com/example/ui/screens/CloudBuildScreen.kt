package com.example.ui.screens

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
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.cloud.CloudBuildRunState
import com.example.cloud.CloudSettings
import com.example.cloud.Dex2cWorkflowGenerator
import com.example.cloud.DeviceAuthState
import com.example.cloud.GitHubArtifactItem
import com.example.cloud.GitHubWorkflowRunSummary
import com.example.cloud.RepoSetupState
import com.example.transpiler.JniBindingStyle
import com.example.transpiler.TranspilerConfig
import com.example.ui.CloudApkChoice
import com.example.ui.components.BuildMonitorCard
import com.example.ui.components.TagPill
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.util.Locale

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
  onOpenSettings: () -> Unit,
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
) {
  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier.fillMaxSize().widthIn(max = 860.dp),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = stringResource(R.string.cloud_title),
              style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
              text = stringResource(R.string.cloud_subtitle),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Spacer(Modifier.width(8.dp))
          OutlinedButton(
            onClick = onOpenSettings,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("cloud_open_settings_btn"),
          ) {
            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.nav_settings))
          }
        }
      }

      // STEP 1: GitHub Device Flow Authentication
      item {
        CloudStepCard(
          title = stringResource(R.string.cloud_step_auth),
          trailingBadge = {
            if (cloudSettings.isAuthenticated) {
              TagPill(
                text = "@${cloudSettings.githubUsername}",
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
              )
            } else {
              TagPill(
                text = "Not Signed In",
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          },
        ) {
          if (cloudSettings.githubClientId.isBlank()) {
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)),
            ) {
              Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Text(
                  text = "GitHub OAuth Client ID Required",
                  style = MaterialTheme.typography.titleSmall,
                  color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                  text =
                    "Enter your GitHub OAuth App Client ID (with Device Flow enabled) in Detailed Settings to sign in at github.com/login/device.",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                  onClick = onOpenSettings,
                  modifier = Modifier.minimumInteractiveComponentSize().testTag("cloud_configure_client_id_btn"),
                ) {
                  Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(Modifier.width(8.dp))
                  Text("Configure OAuth Client ID")
                }
              }
            }
          } else {
            when (deviceAuthState) {
              is DeviceAuthState.Idle -> {
                Text(
                  text =
                    "Authenticate with GitHub using OAuth Device Flow (scopes: repo, workflow). No manual personal access token copy-paste required.",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Button(
                  onClick = onStartDeviceLogin,
                  modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("cloud_device_signin_btn"),
                ) {
                  Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(Modifier.width(8.dp))
                  Text(stringResource(R.string.cloud_sign_in_device))
                }
              }

              is DeviceAuthState.RequestingCode -> {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                  CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                  Text(
                    text = "Requesting device verification code from GitHub…",
                    style = MaterialTheme.typography.bodyMedium,
                  )
                }
              }

              is DeviceAuthState.AwaitingUser -> {
                Surface(
                  modifier = Modifier.fillMaxWidth(),
                  shape = RoundedCornerShape(14.dp),
                  color = MaterialTheme.colorScheme.surfaceContainerHigh,
                  border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                ) {
                  Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                  ) {
                    Text(
                      text = "Enter this code at ${deviceAuthState.verificationUri}",
                      style = MaterialTheme.typography.labelLarge,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                      text = deviceAuthState.userCode,
                      fontFamily = JetBrainsMonoFontFamily,
                      fontSize = 28.sp,
                      fontWeight = FontWeight.Bold,
                      letterSpacing = 3.sp,
                      color = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.testTag("cloud_device_user_code"),
                    )
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                      OutlinedButton(
                        onClick = { onCopyCode(deviceAuthState.userCode) },
                        modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("cloud_copy_device_code_btn"),
                      ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Copy Code")
                      }
                      Button(
                        onClick = { onOpenBrowserUrl(deviceAuthState.verificationUri) },
                        modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("cloud_open_device_url_btn"),
                      ) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Authorize")
                      }
                    }
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                      CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                      Text(
                        text = "${deviceAuthState.statusNote} (${deviceAuthState.intervalSec}s poll)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                    }
                    TextButton(
                      onClick = onCancelDeviceLogin,
                      modifier = Modifier.minimumInteractiveComponentSize().testTag("cloud_cancel_device_btn"),
                    ) {
                      Text("Cancel Login")
                    }
                  }
                }
              }

              is DeviceAuthState.Authenticated -> {
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
                      imageVector = Icons.Default.CheckCircle,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.size(20.dp),
                    )
                    Text(
                      text = "Signed in via Device Flow as @${deviceAuthState.username}",
                      style = MaterialTheme.typography.bodyMedium,
                    )
                  }
                  TextButton(
                    onClick = onSignOut,
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("cloud_sign_out_btn"),
                  ) {
                    Text("Sign Out")
                  }
                }
              }

              is DeviceAuthState.Error -> {
                Text(
                  text = deviceAuthState.message,
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(10.dp))
                Button(
                  onClick = onStartDeviceLogin,
                  modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("cloud_device_retry_btn"),
                ) {
                  Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(Modifier.width(8.dp))
                  Text("Retry Device Flow Login")
                }
              }
            }
          }
        }
      }

      // STEP 2: Private Repository & dex2c.yml Initialization
      item {
        CloudStepCard(
          title = stringResource(R.string.cloud_step_repo),
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
              "First-time setup creates your private GitHub workspace repository and commits ${Dex2cWorkflowGenerator.WORKFLOW_PATH}, ${Dex2cWorkflowGenerator.DCC_CFG_PATH}, and ${Dex2cWorkflowGenerator.FILTER_TXT_PATH}.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(Modifier.height(10.dp))
          OutlinedTextField(
            value = cloudSettings.repoName,
            onValueChange = onUpdateRepoName,
            label = { Text("Private Workspace Repository Name") },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = JetBrainsMonoFontFamily),
            modifier = Modifier.fillMaxWidth().testTag("cloud_repo_name_input"),
          )
          Spacer(Modifier.height(10.dp))

          when (repoSetupState) {
            is RepoSetupState.Working -> {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(vertical = 8.dp),
              ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(repoSetupState.stepLabel, style = MaterialTheme.typography.bodyMedium)
              }
            }

            is RepoSetupState.Ready -> {
              Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
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
                    )
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
                  FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                      onClick = onInitializeRepoAndWorkflow,
                      modifier = Modifier.minimumInteractiveComponentSize().testTag("cloud_resync_workflow_btn"),
                    ) {
                      Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                      Spacer(Modifier.width(6.dp))
                      Text("Re-sync dex2c.yml")
                    }
                    OutlinedButton(
                      onClick = { onOpenBrowserUrl(repoSetupState.htmlUrl) },
                      modifier = Modifier.minimumInteractiveComponentSize().testTag("cloud_open_repo_btn"),
                    ) {
                      Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                      Spacer(Modifier.width(6.dp))
                      Text("Open Repo")
                    }
                    TextButton(
                      onClick = onVerifyRepo,
                      modifier = Modifier.minimumInteractiveComponentSize().testTag("cloud_verify_repo_btn"),
                    ) {
                      Text("Verify")
                    }
                  }
                }
              }
            }

            is RepoSetupState.Error -> {
              Text(
                text = repoSetupState.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
              )
              Spacer(Modifier.height(8.dp))
              Button(
                onClick = onInitializeRepoAndWorkflow,
                enabled = cloudSettings.isAuthenticated,
                modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("cloud_init_repo_btn"),
              ) {
                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.cloud_init_repo_btn))
              }
            }

            is RepoSetupState.NotConfigured -> {
              Button(
                onClick = onInitializeRepoAndWorkflow,
                enabled = cloudSettings.isAuthenticated,
                modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("cloud_init_repo_btn"),
              ) {
                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.cloud_init_repo_btn))
              }
            }
          }
        }
      }

      // STEP 3: Target APK Input & Workflow Dispatch
      item {
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
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
          ) {
            Row(
              modifier = Modifier.padding(12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Icon(
                imageVector = Icons.Default.Android,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
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
                )
              }
            }
          }

          Spacer(Modifier.height(10.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            OutlinedButton(
              onClick = onPickApkFile,
              modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("cloud_pick_apk_btn"),
            ) {
              Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(6.dp))
              Text("Pick APK")
            }
            OutlinedButton(
              onClick = onUseLiveApk,
              modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("cloud_use_live_apk_btn"),
            ) {
              Icon(Icons.Default.Android, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(6.dp))
              Text("Use base.apk")
            }
          }

          Spacer(Modifier.height(10.dp))
          OutlinedTextField(
            value = apkUrlFallback,
            onValueChange = onApkUrlChange,
            label = { Text("APK URL Fallback (optional, skips repo APK upload)") },
            placeholder = { Text("https://example.com/target.apk") },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
            modifier = Modifier.fillMaxWidth().testTag("cloud_apk_url_input"),
          )

          Spacer(Modifier.height(10.dp))
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
              text = cloudSettings.signingMode.label,
              containerColor = MaterialTheme.colorScheme.primaryContainer,
              contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
          }

          Spacer(Modifier.height(14.dp))
          val canRun =
            cloudSettings.isAuthenticated &&
              cloudSettings.workflowInitialized &&
              (apkChoice != null || apkUrlFallback.isNotBlank()) &&
              cloudRunState !is CloudBuildRunState.UploadingInputs &&
              cloudRunState !is CloudBuildRunState.Dispatching
          Button(
            onClick = onDispatchBuild,
            enabled = canRun,
            modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("cloud_run_build_btn"),
          ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.cloud_run_build_btn))
          }
        }
      }

      // STEP 4: Dedicated Build Monitor Card above Recent Cloud Runs
      item {
        BuildMonitorCard(
          cloudSettings = cloudSettings,
          cloudRunState = cloudRunState,
          isAutoPollEnabled = isAutoPollEnabled,
          onToggleAutoPoll = onToggleAutoPoll,
          onRefreshMonitor = onRefreshMonitor,
          onOpenBrowserUrl = onOpenBrowserUrl,
          onDownloadArtifact = onDownloadArtifact,
        )
      }

      if (recentRuns.isNotEmpty()) {
        item {
          CloudStepCard(
            title = "Recent Cloud Runs",
            trailingBadge = {
              if (cloudSettings.isRepoConfigured) {
                TextButton(
                  onClick = onRefreshRuns,
                  modifier = Modifier.minimumInteractiveComponentSize().testTag("cloud_refresh_runs_btn"),
                ) {
                  Icon(Icons.Default.Refresh, contentDescription = "Refresh Runs", modifier = Modifier.size(16.dp))
                  Spacer(Modifier.width(4.dp))
                  Text("Refresh")
                }
              }
            },
          ) {
            recentRuns.take(5).forEach { run ->
              Surface(
                modifier =
                  Modifier.fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clickable { onAttachToRun(run.id) }
                    .testTag("cloud_run_item_${run.id}"),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = "Run #${run.runNumber}",
                      style = MaterialTheme.typography.titleSmall,
                      fontFamily = JetBrainsMonoFontFamily,
                    )
                    Text(
                      text = run.createdAt,
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
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

      item { Spacer(Modifier.height(24.dp)) }
    }
  }
}

@Composable
private fun CloudStepCard(
  title: String,
  trailingBadge: (@Composable () -> Unit)? = null,
  content: @Composable () -> Unit,
) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surfaceContainer,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.weight(1f),
        )
        if (trailingBadge != null) {
          trailingBadge()
        }
      }
      Spacer(Modifier.height(10.dp))
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
