package com.dex2c.studio.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dex2c.studio.R
import com.dex2c.studio.cloud.CloudBuildRunState
import com.dex2c.studio.cloud.CloudSettings
import com.dex2c.studio.cloud.CloudTimeUtil
import com.dex2c.studio.cloud.GitHubArtifactItem
import com.dex2c.studio.cloud.GitHubWorkflowStep
import com.dex2c.studio.ui.theme.JetBrainsMonoFontFamily
import java.util.Locale
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BuildMonitorCard(
  cloudSettings: CloudSettings,
  cloudRunState: CloudBuildRunState,
  isAutoPollEnabled: Boolean,
  onToggleAutoPoll: () -> Unit,
  onRefreshMonitor: () -> Unit,
  onOpenBrowserUrl: (String) -> Unit,
  onDownloadArtifact: (GitHubArtifactItem) -> Unit,
  modifier: Modifier = Modifier,
  onRetryError: (() -> Unit)? = null,
  onInspectLogs: ((runId: Long) -> Unit)? = null,
) {
  var tickerNowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

  val isLiveRunning =
    cloudRunState is CloudBuildRunState.Polling ||
      cloudRunState is CloudBuildRunState.UploadingInputs ||
      cloudRunState is CloudBuildRunState.Dispatching ||
      cloudRunState is CloudBuildRunState.SavingArtifact

  LaunchedEffect(cloudRunState, isLiveRunning) {
    if (isLiveRunning) {
      while (true) {
        tickerNowMillis = System.currentTimeMillis()
        delay(1000L)
      }
    }
  }

  Surface(
    modifier = modifier.fillMaxWidth().animateContentSize(ExpressiveMotion.spatialSpring()).testTag("build_monitor_card"),
    shape = RoundedCornerShape(24.dp),
    color = MaterialTheme.colorScheme.surfaceContainer,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
  ) {
    Column(modifier = Modifier.padding(18.dp)) {
      // Card Header
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Build Monitor",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
          )
          Text(
            text =
              if (cloudSettings.isRepoConfigured) {
                "${cloudSettings.effectiveOwner}/${cloudSettings.repoName} • ${cloudSettings.pollIntervalSeconds}s poll"
              } else {
                "GitHub Actions live job telemetry"
              },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          FilterChip(
            selected = isAutoPollEnabled,
            onClick = onToggleAutoPoll,
            label = { Text(if (isAutoPollEnabled) "Auto" else "Paused") },
            leadingIcon = {
              Icon(
                imageVector = if (isAutoPollEnabled) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
              )
            },
            modifier = Modifier.testTag("build_monitor_autopoll_btn"),
          )
          if (cloudSettings.isRepoConfigured) {
            IconButton(
              onClick = onRefreshMonitor,
              modifier = Modifier.minimumInteractiveComponentSize().testTag("build_monitor_refresh_btn"),
            ) {
              Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = "Refresh Build Monitor",
              )
            }
          }
        }
      }

      Spacer(Modifier.height(12.dp))

      when (cloudRunState) {
        is CloudBuildRunState.Idle -> {
          MonitorGaugeBlock(
            progress = 0f,
            isIndeterminate = false,
            centerText = "0%",
            centerSubtext = "IDLE",
            isError = false,
            runHeading = "No active workflow",
            durationText = "00:00",
            statusSummary = "Dispatch a Cloud Dex2C build above or tap a run below to monitor GitHub Actions.",
            statusPillText = "READY",
            htmlUrl = null,
            onOpenBrowserUrl = onOpenBrowserUrl,
          )
        }

        is CloudBuildRunState.UploadingInputs -> {
          val fraction = cloudRunState.progressFraction
          val pct = fraction?.let { "${(it * 100).toInt().coerceIn(0, 100)}%" } ?: "…"
          MonitorGaugeBlock(
            progress = fraction,
            isIndeterminate = fraction == null,
            centerText = pct,
            centerSubtext = "UPLOAD",
            isError = false,
            runHeading = "Preparing Workspace Inputs",
            durationText = "00:00",
            statusSummary = cloudRunState.message,
            statusPillText = if (fraction != null) pct else "SYNCING",
            htmlUrl = null,
            onOpenBrowserUrl = onOpenBrowserUrl,
          )
          Spacer(Modifier.height(10.dp))
          TransferProgressPanel(
            title = cloudRunState.message,
            transferredBytes = cloudRunState.bytesUploaded,
            totalBytes = cloudRunState.totalBytes,
            progressFraction = fraction,
            containerTag = "build_monitor_upload_progress",
            barTag = "build_monitor_upload_bar",
            bytesTag = "build_monitor_upload_bytes",
            pctTag = "build_monitor_upload_pct",
          )
        }

        is CloudBuildRunState.Dispatching -> {
          MonitorGaugeBlock(
            progress = null,
            isIndeterminate = true,
            centerText = "…",
            centerSubtext = "START",
            isError = false,
            runHeading = "Triggering dex2c.yml",
            durationText = "00:00",
            statusSummary = "Sending workflow_dispatch request to GitHub Actions…",
            statusPillText = "DISPATCH",
            htmlUrl = null,
            onOpenBrowserUrl = onOpenBrowserUrl,
          )
        }

        is CloudBuildRunState.SavingArtifact -> {
          val fraction = cloudRunState.progressFraction
          val pct = fraction?.let { "${(it * 100).toInt().coerceIn(0, 100)}%" } ?: "APK"
          val snap = cloudRunState.completedSnapshot
          val runDurationMs =
            if (snap?.startedAtMillis != null && snap.completedAtMillis != null) {
              (snap.completedAtMillis - snap.startedAtMillis).coerceAtLeast(0L)
            } else {
              null
            }
          MonitorGaugeBlock(
            progress = fraction,
            isIndeterminate = fraction == null,
            centerText = pct,
            centerSubtext = "FETCH",
            isError = false,
            runHeading =
              if (snap != null) "Run #${snap.runNumber} • Downloading"
              else "Downloading Output Artifact",
            durationText = runDurationMs?.let { CloudTimeUtil.formatDurationMmSs(it) } ?: "--:--",
            statusSummary = cloudRunState.label,
            statusPillText = if (fraction != null) pct else "FETCHING",
            htmlUrl = snap?.htmlUrl,
            onOpenBrowserUrl = onOpenBrowserUrl,
            onInspectLogs = snap?.runId?.let { rId -> { onInspectLogs?.invoke(rId) } },
          )
          Spacer(Modifier.height(10.dp))
          TransferProgressPanel(
            title = cloudRunState.label,
            transferredBytes = cloudRunState.bytesDownloaded,
            totalBytes = cloudRunState.totalBytes,
            progressFraction = fraction,
            containerTag = "build_monitor_download_progress",
            barTag = "build_monitor_download_bar",
            bytesTag = "build_monitor_download_bytes",
            pctTag = "build_monitor_download_pct",
          )
          if (snap != null && snap.steps.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            StepProgressList(steps = snap.steps)
          }
        }

        is CloudBuildRunState.Polling -> {
          val stepProgress = CloudTimeUtil.computeStepProgress(cloudRunState.steps)
          val doneSteps = cloudRunState.steps.count { it.status == "completed" }
          val totalSteps = cloudRunState.steps.size
          val pct = stepProgress?.let { "${(it * 100).toInt()}%" } ?: "…"
          val sub =
            if (totalSteps > 0) "$doneSteps/$totalSteps" else cloudRunState.status.uppercase(Locale.US)
          val durationMs =
            cloudRunState.startedAtMillis?.let { (tickerNowMillis - it).coerceAtLeast(0L) } ?: 0L
          val activeStep =
            cloudRunState.steps.firstOrNull { it.status == "in_progress" }?.name
              ?: cloudRunState.steps.lastOrNull { it.status == "completed" }?.name
              ?: "Waiting for Ubuntu runner…"
          val isReconnecting = !cloudRunState.reconnectingNote.isNullOrBlank()

          MonitorGaugeBlock(
            progress = stepProgress,
            isIndeterminate = stepProgress == null,
            centerText = pct,
            centerSubtext = sub,
            isError = false,
            runHeading = "Run #${cloudRunState.runNumber} • ${cloudRunState.jobName}",
            durationText = CloudTimeUtil.formatDurationMmSs(durationMs),
            statusSummary = cloudRunState.reconnectingNote ?: "Step: $activeStep",
            statusPillText = if (isReconnecting) "RECONNECTING" else cloudRunState.status.uppercase(Locale.US),
            htmlUrl = cloudRunState.htmlUrl,
            onOpenBrowserUrl = onOpenBrowserUrl,
            onInspectLogs = { onInspectLogs?.invoke(cloudRunState.runId) },
          )

          if (cloudRunState.steps.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            StepProgressList(steps = cloudRunState.steps)
          }
        }

        is CloudBuildRunState.Completed -> {
          val isSuccess = cloudRunState.conclusion == "success"
          val durationMs =
            if (cloudRunState.startedAtMillis != null && cloudRunState.completedAtMillis != null) {
              (cloudRunState.completedAtMillis - cloudRunState.startedAtMillis).coerceAtLeast(0L)
            } else {
              0L
            }
          val doneSteps = cloudRunState.steps.count { it.status == "completed" }
          val totalSteps = cloudRunState.steps.size
          val ratio = if (isSuccess) 1f else (CloudTimeUtil.computeStepProgress(cloudRunState.steps) ?: 1f)

          MonitorGaugeBlock(
            progress = ratio,
            isIndeterminate = false,
            centerText = if (isSuccess) "100%" else "${(ratio * 100).toInt()}%",
            centerSubtext = if (totalSteps > 0) "$doneSteps/$totalSteps" else cloudRunState.conclusion.uppercase(Locale.US),
            isError = !isSuccess,
            runHeading = "Run #${cloudRunState.runNumber} • Finished",
            durationText = CloudTimeUtil.formatDurationMmSs(durationMs),
            statusSummary =
              if (isSuccess) "Dex2C compilation and APK signing completed cleanly."
              else "Workflow finished with conclusion: ${cloudRunState.conclusion}",
            statusPillText = cloudRunState.conclusion.uppercase(Locale.US),
            htmlUrl = cloudRunState.htmlUrl,
            onOpenBrowserUrl = onOpenBrowserUrl,
            onInspectLogs = { onInspectLogs?.invoke(cloudRunState.runId) },
          )

          if (cloudRunState.steps.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            StepProgressList(steps = cloudRunState.steps)
          }

          Spacer(Modifier.height(12.dp))
          if (cloudRunState.artifacts.isEmpty()) {
            Text(
              text = "No artifacts uploaded for this workflow run. Open Logs to inspect build output.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              cloudRunState.artifacts.forEach { artifact ->
                val isLogOnlyArtifact =
                  artifact.name.contains("log", ignoreCase = true) &&
                    !artifact.name.contains("apk", ignoreCase = true)
                Surface(
                  modifier = Modifier.fillMaxWidth(),
                  shape = RoundedCornerShape(18.dp),
                  color = MaterialTheme.colorScheme.surfaceContainerHigh,
                  border =
                    BorderStroke(
                      1.dp,
                      (if (isLogOnlyArtifact) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                        .copy(alpha = 0.45f),
                    ),
                ) {
                  Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                  ) {
                    Icon(
                      Icons.Rounded.CloudUpload,
                      contentDescription = null,
                      tint = if (isLogOnlyArtifact) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        text = artifact.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = JetBrainsMonoFontFamily,
                      )
                      Text(
                        text =
                          if (isLogOnlyArtifact) {
                            "${formatArtifactBytes(artifact.sizeInBytes)} • Build log (.log)"
                          } else {
                            "${formatArtifactBytes(artifact.sizeInBytes)} • Signed APK (.apk)"
                          },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                    }
                    Button(
                      onClick = { onDownloadArtifact(artifact) },
                      enabled = !artifact.expired,
                      shape = RoundedCornerShape(20.dp),
                      modifier = Modifier.minimumInteractiveComponentSize().testTag("cloud_save_apk_btn"),
                    ) {
                      Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                      Spacer(Modifier.width(6.dp))
                      Text(
                        stringResource(
                          if (isLogOnlyArtifact) R.string.cloud_save_build_log
                          else R.string.cloud_save_signed_apk
                        )
                      )
                    }
                  }
                }
              }
            }
          }
        }

        is CloudBuildRunState.Error -> {
          MonitorGaugeBlock(
            progress = 1f,
            isIndeterminate = false,
            centerText = "ERR",
            centerSubtext = "FAILED",
            isError = true,
            runHeading = "Cloud Workflow Error",
            durationText = "00:00",
            statusSummary = cloudRunState.message,
            statusPillText = "ERROR",
            htmlUrl = null,
            onOpenBrowserUrl = onOpenBrowserUrl,
            onInspectLogs = cloudRunState.failedRunId?.let { rId -> { onInspectLogs?.invoke(rId) } },
          )
          Spacer(Modifier.height(10.dp))
          Button(
            onClick = { (onRetryError ?: onRefreshMonitor).invoke() },
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("build_monitor_retry_btn"),
          ) {
            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Retry")
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MonitorGaugeBlock(
  progress: Float?,
  isIndeterminate: Boolean,
  centerText: String,
  centerSubtext: String,
  isError: Boolean,
  runHeading: String,
  durationText: String,
  statusSummary: String,
  statusPillText: String,
  htmlUrl: String?,
  onOpenBrowserUrl: (String) -> Unit,
  onInspectLogs: (() -> Unit)? = null,
) {
  val animatedProgress by
    animateFloatAsState(
      targetValue = progress ?: 0f,
      animationSpec = spring(stiffness = Spring.StiffnessLow),
      label = "monitor_ring_progress",
    )

  val ringColor =
    if (isError) MaterialTheme.colorScheme.error
    else MaterialTheme.colorScheme.primary

  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(18.dp),
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
  ) {
    Row(
      modifier = Modifier.padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Box(
        modifier = Modifier.size(80.dp).testTag("build_monitor_circular_progress"),
        contentAlignment = Alignment.Center,
      ) {
        if (isIndeterminate) {
          CircularProgressIndicator(
            modifier = Modifier.size(76.dp),
            color = ringColor,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            strokeWidth = 6.dp,
            strokeCap = StrokeCap.Round,
          )
        } else {
          CircularProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier.size(76.dp),
            color = ringColor,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            strokeWidth = 6.dp,
            strokeCap = StrokeCap.Round,
          )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text(
            text = centerText,
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text = centerSubtext,
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = runHeading,
            style = MaterialTheme.typography.titleSmall,
            fontFamily = JetBrainsMonoFontFamily,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
          )
          Spacer(Modifier.width(6.dp))
          TagPill(
            text = statusPillText,
            containerColor =
              when {
                isError -> MaterialTheme.colorScheme.errorContainer
                statusPillText == "SUCCESS" -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.tertiaryContainer
              },
            contentColor =
              when {
                isError -> MaterialTheme.colorScheme.onErrorContainer
                statusPillText == "SUCCESS" -> MaterialTheme.colorScheme.onPrimaryContainer
                else -> MaterialTheme.colorScheme.onTertiaryContainer
              },
          )
        }

        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalArrangement = Arrangement.Center,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Rounded.Schedule,
              contentDescription = "Job Duration",
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
              text = durationText,
              fontFamily = JetBrainsMonoFontFamily,
              fontSize = 16.sp,
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.testTag("build_monitor_duration"),
            )
          }
          if (onInspectLogs != null) {
            TextButton(
              onClick = onInspectLogs,
              modifier = Modifier.testTag("build_monitor_inspect_logs_btn"),
            ) {
              Icon(Icons.Rounded.Terminal, contentDescription = null, modifier = Modifier.size(14.dp))
              Spacer(Modifier.width(4.dp))
              Text("Inspect", style = MaterialTheme.typography.labelMedium)
            }
          }
          if (!htmlUrl.isNullOrBlank()) {
            TextButton(
              onClick = { onOpenBrowserUrl(htmlUrl) },
              modifier = Modifier.testTag("build_monitor_open_logs_btn"),
            ) {
              Icon(Icons.Rounded.OpenInBrowser, contentDescription = null, modifier = Modifier.size(14.dp))
              Spacer(Modifier.width(4.dp))
              Text("Logs", style = MaterialTheme.typography.labelMedium)
            }
          }
        }

        Text(
          text = statusSummary,
          style = MaterialTheme.typography.bodySmall,
          color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

@Composable
private fun StepProgressList(steps: List<GitHubWorkflowStep>) {
  Column(
    modifier = Modifier.animateContentSize(),
    verticalArrangement = Arrangement.spacedBy(3.dp),
  ) {
    steps.forEach { step ->
      Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        when {
          step.conclusion == "success" ->
            Icon(
              Icons.Rounded.CheckCircle,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(14.dp),
            )
          step.conclusion == "failure" ->
            Icon(
              Icons.Rounded.ErrorOutline,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.error,
              modifier = Modifier.size(14.dp),
            )
          step.status == "in_progress" ->
            CircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 2.dp)
          else ->
            Icon(
              Icons.Rounded.Schedule,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(14.dp),
            )
        }
        Text(
          text = step.name,
          style = MaterialTheme.typography.bodySmall,
          fontFamily = JetBrainsMonoFontFamily,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
        Text(
          text = step.conclusion ?: step.status,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun TransferProgressPanel(
  title: String,
  transferredBytes: Long,
  totalBytes: Long,
  progressFraction: Float?,
  containerTag: String,
  barTag: String,
  bytesTag: String,
  pctTag: String,
) {
  val pctInt = progressFraction?.let { (it * 100).toInt().coerceIn(0, 100) }
  val animatedBar by
    animateFloatAsState(
      targetValue = progressFraction ?: 0f,
      animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
      label = "transfer_bar_progress",
    )

  Surface(
    modifier = Modifier.fillMaxWidth().testTag(containerTag),
    shape = RoundedCornerShape(18.dp),
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleSmall,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
          text = pctInt?.let { "$it%" } ?: "…",
          fontFamily = JetBrainsMonoFontFamily,
          fontSize = 13.sp,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.testTag(pctTag),
        )
      }

      if (progressFraction != null) {
        LinearProgressIndicator(
          progress = { animatedBar },
          modifier = Modifier.fillMaxWidth().height(7.dp).testTag(barTag),
          color = MaterialTheme.colorScheme.primary,
          trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
          strokeCap = StrokeCap.Round,
        )
      } else {
        LinearProgressIndicator(
          modifier = Modifier.fillMaxWidth().height(7.dp).testTag(barTag),
          color = MaterialTheme.colorScheme.primary,
          trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
          strokeCap = StrokeCap.Round,
        )
      }

      val byteSummary =
        when {
          totalBytes > 0L ->
            "${CloudTimeUtil.formatTransferBytes(transferredBytes.coerceAtMost(totalBytes))} / ${CloudTimeUtil.formatTransferBytes(totalBytes)}"
          transferredBytes > 0L ->
            "${CloudTimeUtil.formatTransferBytes(transferredBytes)} transferred"
          else -> "Preparing transfer…"
        }
      Text(
        text = byteSummary,
        fontFamily = JetBrainsMonoFontFamily,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag(bytesTag),
      )
    }
  }
}

private fun formatArtifactBytes(bytes: Long): String = CloudTimeUtil.formatTransferBytes(bytes)
