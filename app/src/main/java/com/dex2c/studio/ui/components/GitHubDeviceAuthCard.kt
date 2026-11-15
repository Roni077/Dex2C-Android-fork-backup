package com.dex2c.studio.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dex2c.studio.R
import com.dex2c.studio.cloud.CloudSettings
import com.dex2c.studio.cloud.DeviceAuthState
import com.dex2c.studio.cloud.GITHUB_DEVICE_VERIFICATION_URL
import com.dex2c.studio.cloud.RepoSetupState
import com.dex2c.studio.ui.theme.JetBrainsMonoFontFamily
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GitHubDeviceAuthCard(
  cloudSettings: CloudSettings,
  deviceAuthState: DeviceAuthState,
  onStartDeviceLogin: () -> Unit,
  onCancelDeviceLogin: () -> Unit,
  onCopyCode: (String) -> Unit,
  onOpenBrowserUrl: (String) -> Unit,
  modifier: Modifier = Modifier,
  tagPrefix: String = "github",
  compact: Boolean = false,
  animateTransitions: Boolean = true,
  repoSetupState: RepoSetupState = RepoSetupState.NotConfigured,
  onRetryRepoSetup: (() -> Unit)? = null,
  onSignOut: (() -> Unit)? = null,
) {
  val effectiveAuthState: DeviceAuthState =
    if (cloudSettings.isAuthenticated && deviceAuthState !is DeviceAuthState.AwaitingUser && deviceAuthState !is DeviceAuthState.RequestingCode) {
      DeviceAuthState.Authenticated(cloudSettings.githubUsername)
    } else {
      deviceAuthState
    }

  val cardModifier =
    if (animateTransitions) {
      modifier
        .fillMaxWidth()
        .animateContentSize(animationSpec = ExpressiveMotion.spatialSpring())
    } else {
      modifier.fillMaxWidth()
    }

  Surface(
    modifier = cardModifier,
    shape = RoundedCornerShape(24.dp),
    color =
      when (effectiveAuthState) {
        is DeviceAuthState.AwaitingUser -> MaterialTheme.colorScheme.surfaceContainerHigh
        is DeviceAuthState.Authenticated -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
      },
    border =
      BorderStroke(
        width = 1.dp,
        color =
          when (effectiveAuthState) {
            is DeviceAuthState.AwaitingUser -> MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
            is DeviceAuthState.Authenticated -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            is DeviceAuthState.Error -> MaterialTheme.colorScheme.error.copy(alpha = 0.55f)
            else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
          },
      ),
  ) {
    Column(
      modifier = Modifier.padding(if (compact) 14.dp else 16.dp),
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
          modifier = Modifier.weight(1f),
        ) {
          Icon(
            imageVector =
              when (effectiveAuthState) {
                is DeviceAuthState.Authenticated -> Icons.Rounded.CheckCircle
                is DeviceAuthState.Error -> Icons.Rounded.ErrorOutline
                else -> Icons.Rounded.Security
              },
            contentDescription = null,
            tint =
              if (effectiveAuthState is DeviceAuthState.Error) MaterialTheme.colorScheme.error
              else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
          )
          Text(
            text =
              when (effectiveAuthState) {
                is DeviceAuthState.Authenticated -> "GitHub Connected"
                is DeviceAuthState.AwaitingUser -> "Authorize This Device"
                else -> "GitHub Account"
              },
            style = MaterialTheme.typography.titleMedium,
          )
        }
        Spacer(Modifier.width(8.dp))
        TagPill(
          text =
            when {
              effectiveAuthState is DeviceAuthState.Authenticated -> "@${effectiveAuthState.username}"
              cloudSettings.usingCustomClientId -> "Custom OAuth ID"
              else -> stringResource(R.string.github_device_cli_badge)
            },
          containerColor =
            if (effectiveAuthState is DeviceAuthState.Authenticated) {
              MaterialTheme.colorScheme.primaryContainer
            } else {
              MaterialTheme.colorScheme.secondaryContainer
            },
          contentColor =
            if (effectiveAuthState is DeviceAuthState.Authenticated) {
              MaterialTheme.colorScheme.onPrimaryContainer
            } else {
              MaterialTheme.colorScheme.onSecondaryContainer
            },
        )
      }

      if (animateTransitions) {
        AnimatedContent(
          targetState = effectiveAuthState,
          label = "github_device_auth_state",
        ) { state ->
          DeviceAuthStateBody(
            state = state,
            cloudSettings = cloudSettings,
            repoSetupState = repoSetupState,
            compact = compact,
            tagPrefix = tagPrefix,
            onStartDeviceLogin = onStartDeviceLogin,
            onCancelDeviceLogin = onCancelDeviceLogin,
            onCopyCode = onCopyCode,
            onOpenBrowserUrl = onOpenBrowserUrl,
            onRetryRepoSetup = onRetryRepoSetup,
            onSignOut = onSignOut,
          )
        }
      } else {
        DeviceAuthStateBody(
          state = effectiveAuthState,
          cloudSettings = cloudSettings,
          repoSetupState = repoSetupState,
          compact = compact,
          tagPrefix = tagPrefix,
          onStartDeviceLogin = onStartDeviceLogin,
          onCancelDeviceLogin = onCancelDeviceLogin,
          onCopyCode = onCopyCode,
          onOpenBrowserUrl = onOpenBrowserUrl,
          onRetryRepoSetup = onRetryRepoSetup,
          onSignOut = onSignOut,
        )
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeviceAuthStateBody(
  state: DeviceAuthState,
  cloudSettings: CloudSettings,
  repoSetupState: RepoSetupState,
  compact: Boolean,
  tagPrefix: String,
  onStartDeviceLogin: () -> Unit,
  onCancelDeviceLogin: () -> Unit,
  onCopyCode: (String) -> Unit,
  onOpenBrowserUrl: (String) -> Unit,
  onRetryRepoSetup: (() -> Unit)?,
  onSignOut: (() -> Unit)?,
) {
  when (state) {
    is DeviceAuthState.Idle -> {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text =
            if (compact) {
              "Sign in via gh CLI Device Flow (github.com/login/device) with an 8-digit code."
            } else {
              "Connect via OAuth Device Flow (repo, workflow) — identical to gh auth login."
            },
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
              val btnInteraction = remember { MutableInteractionSource() }
              Button(
                onClick = onStartDeviceLogin,
                interactionSource = btnInteraction,
                shape = RoundedCornerShape(20.dp),
                modifier =
                  Modifier
                    .fillMaxWidth()
                    .expressivePressScale(btnInteraction, 0.97f)
                    .minimumInteractiveComponentSize()
                    .testTag("${tagPrefix}_continue_github_btn"),
              ) {
                Icon(Icons.Rounded.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.action_continue_with_github))
              }
            }
          }

          is DeviceAuthState.RequestingCode -> {
            Row(
              modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
              Column {
                Text(
                  text = "Requesting 8-digit verification code…",
                  style = MaterialTheme.typography.titleSmall,
                )
                Text(
                  text = GITHUB_DEVICE_VERIFICATION_URL,
                  style = MaterialTheme.typography.bodySmall,
                  fontFamily = JetBrainsMonoFontFamily,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }

          is DeviceAuthState.AwaitingUser -> {
            var nowMillis by remember(state.userCode, state.expiresAtMillis) {
              mutableLongStateOf(System.currentTimeMillis())
            }
            LaunchedEffect(state.userCode, state.expiresAtMillis) {
              while (true) {
                nowMillis = System.currentTimeMillis()
                if (nowMillis >= state.expiresAtMillis) break
                delay(1000L)
              }
            }

            val remainingMs = state.remainingMillisAt(nowMillis)
            val remainingFormatted = state.remainingFormattedAt(nowMillis)
            val isExpiringSoon = remainingMs < 60_000L

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = stringResource(R.string.github_device_code_label),
                  style = MaterialTheme.typography.labelLarge,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.weight(1f),
                )
                Surface(
                  shape = RoundedCornerShape(999.dp),
                  color =
                    if (isExpiringSoon) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.tertiaryContainer,
                  modifier =
                    Modifier
                      .semantics(mergeDescendants = true) {}
                      .testTag("${tagPrefix}_device_expiry_badge"),
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                  ) {
                    Icon(
                      imageVector = Icons.Rounded.Timer,
                      contentDescription = null,
                      tint =
                        if (isExpiringSoon) MaterialTheme.colorScheme.onErrorContainer
                        else MaterialTheme.colorScheme.onTertiaryContainer,
                      modifier = Modifier.size(14.dp),
                    )
                    Text(
                      text = "Expires in $remainingFormatted",
                      style = MaterialTheme.typography.labelMedium,
                      fontFamily = JetBrainsMonoFontFamily,
                      color =
                        if (isExpiringSoon) MaterialTheme.colorScheme.onErrorContainer
                        else MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                  }
                }
              }

              // 8-digit one-time code box (tap anywhere to copy)
              Surface(
                modifier =
                  Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onCopyCode(state.userCode) }
                    .testTag("${tagPrefix}_device_code_box"),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Text(
                    text = state.userCode,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("${tagPrefix}_device_user_code"),
                  )
                  Icon(
                    imageVector = Icons.Rounded.ContentCopy,
                    contentDescription = "Copy verification code",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                  )
                }
              }

              // Clickable https://github.com/login/device link row
              val targetUri = state.verificationUri.ifBlank { GITHUB_DEVICE_VERIFICATION_URL }
              Surface(
                modifier =
                  Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onOpenBrowserUrl(targetUri) }
                    .minimumInteractiveComponentSize()
                    .testTag("${tagPrefix}_device_url_link"),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = "Verification URL (opens in browser)",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                      text = targetUri,
                      style = MaterialTheme.typography.bodyMedium,
                      fontFamily = JetBrainsMonoFontFamily,
                      fontWeight = FontWeight.SemiBold,
                      textDecoration = TextDecoration.Underline,
                      color = MaterialTheme.colorScheme.primary,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                  }
                  Spacer(Modifier.width(8.dp))
                  Icon(
                    imageVector = Icons.Rounded.OpenInBrowser,
                    contentDescription = stringResource(R.string.github_device_open_browser),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                  )
                }
              }

              FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
              ) {
                OutlinedButton(
                  onClick = { onCopyCode(state.userCode) },
                  shape = RoundedCornerShape(20.dp),
                  modifier = Modifier.minimumInteractiveComponentSize().testTag("${tagPrefix}_copy_device_code_btn"),
                ) {
                  Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(Modifier.width(6.dp))
                  Text("Copy Code")
                }
                Button(
                  onClick = { onOpenBrowserUrl(targetUri) },
                  shape = RoundedCornerShape(20.dp),
                  modifier = Modifier.minimumInteractiveComponentSize().testTag("${tagPrefix}_open_device_browser_btn"),
                ) {
                  Icon(Icons.Rounded.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(Modifier.width(6.dp))
                  Text(stringResource(R.string.github_device_open_browser))
                }
                TextButton(
                  onClick = onCancelDeviceLogin,
                  modifier = Modifier.minimumInteractiveComponentSize().testTag("${tagPrefix}_cancel_device_btn"),
                ) {
                  Text("Cancel")
                }
              }

              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                Text(
                  text = "${state.statusNote} (${state.intervalSec}s poll)",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }

          is DeviceAuthState.Authenticated -> {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = "Signed in via Device Flow as @${state.username}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                  )
                  Text(
                    text = "Workspace: ${cloudSettings.effectiveOwner}/${cloudSettings.repoName}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = JetBrainsMonoFontFamily,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                if (onSignOut != null) {
                  TextButton(
                    onClick = onSignOut,
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("${tagPrefix}_sign_out_btn"),
                  ) {
                    Text("Sign Out")
                  }
                }
              }

              InlineRepoSetupStatusRow(
                repoSetupState = repoSetupState,
                tagPrefix = tagPrefix,
                onRetryRepoSetup = onRetryRepoSetup,
              )
            }
          }

          is DeviceAuthState.Error -> {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
              Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
              ) {
                Text(
                  text = state.message,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onErrorContainer,
                  modifier = Modifier.fillMaxWidth().padding(12.dp),
                )
              }
              Button(
                onClick = onStartDeviceLogin,
                shape = RoundedCornerShape(20.dp),
                modifier =
                  Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("${tagPrefix}_continue_github_btn"),
              ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.action_continue_with_github))
              }
            }
          }
        }
      }

@Composable
private fun InlineRepoSetupStatusRow(
  repoSetupState: RepoSetupState,
  tagPrefix: String,
  onRetryRepoSetup: (() -> Unit)?,
) {
  when (repoSetupState) {
    is RepoSetupState.Working -> {
      Surface(
        modifier = Modifier.fillMaxWidth().testTag("${tagPrefix}_repo_init_working"),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
          Text(
            text = repoSetupState.stepLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
          )
        }
      }
    }

    is RepoSetupState.Ready -> {
      Surface(
        modifier = Modifier.fillMaxWidth().testTag("${tagPrefix}_repo_init_ready"),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(
            imageVector = if (repoSetupState.hasWorkflow) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
            contentDescription = null,
            tint = if (repoSetupState.hasWorkflow) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(16.dp),
          )
          Text(
            text =
              if (repoSetupState.hasWorkflow) {
                "${if (repoSetupState.isPrivate) "Private repo" else "Repo"} ready • dex2c.yml synced"
              } else {
                "Repo found • dex2c.yml not synced yet"
              },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
          )
          if (!repoSetupState.hasWorkflow && onRetryRepoSetup != null) {
            TextButton(
              onClick = onRetryRepoSetup,
              modifier = Modifier.minimumInteractiveComponentSize().testTag("${tagPrefix}_repo_sync_now_btn"),
            ) {
              Text("Sync Now")
            }
          }
        }
      }
    }

    is RepoSetupState.Error -> {
      Surface(
        modifier = Modifier.fillMaxWidth().testTag("${tagPrefix}_repo_init_error"),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(16.dp),
          )
          Text(
            text = repoSetupState.message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
          )
          if (onRetryRepoSetup != null) {
            TextButton(
              onClick = onRetryRepoSetup,
              modifier = Modifier.minimumInteractiveComponentSize().testTag("${tagPrefix}_repo_retry_btn"),
            ) {
              Text("Retry")
            }
          }
        }
      }
    }

    is RepoSetupState.NotConfigured -> {
      if (onRetryRepoSetup != null) {
        Surface(
          modifier = Modifier.fillMaxWidth().testTag("${tagPrefix}_repo_init_pending"),
          shape = RoundedCornerShape(14.dp),
          color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              text = "Private repo not initialized yet",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.weight(1f),
            )
            TextButton(
              onClick = onRetryRepoSetup,
              modifier = Modifier.minimumInteractiveComponentSize().testTag("${tagPrefix}_repo_init_now_btn"),
            ) {
              Text("Init Repo")
            }
          }
        }
      }
    }
  }
}

