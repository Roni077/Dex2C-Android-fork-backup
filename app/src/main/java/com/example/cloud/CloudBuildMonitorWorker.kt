package com.example.cloud

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.CloudPreferences
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

class CloudBuildMonitorWorker(
  appContext: Context,
  params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

  override suspend fun doWork(): Result {
    val targetRunId = inputData.getLong(KEY_RUN_ID, -1L)
    val prefs = CloudPreferences(applicationContext)
    val settings = prefs.settingsFlow.first()

    if (!settings.isRepoConfigured || !settings.notificationsEnabled) {
      return Result.success()
    }

    val owner = settings.effectiveOwner
    val repo = settings.repoName
    val client = GitHubApiClient()
    val pollMs = settings.pollIntervalSeconds.coerceIn(5, 30) * 1000L

    // Poll up to ~8 minutes per WorkManager execution slice before rescheduling
    val maxAttempts = ((8 * 60_000L) / pollMs).toInt().coerceIn(8, 60)

    repeat(maxAttempts) { attempt ->
      if (isStopped) return Result.success()

      val runs =
        runCatching {
          client.listWorkflowRuns(settings.accessToken, owner, repo)
        }.getOrElse {
          if (attempt < 2) return Result.retry()
          return Result.failure()
        }

      val run =
        (if (targetRunId > 0L) runs.firstOrNull { it.id == targetRunId } else null)
          ?: runs.firstOrNull()
          ?: return Result.success()

      if (run.status == "completed") {
        val job =
          runCatching {
            client.getRunJobTelemetry(settings.accessToken, owner, repo, run.id)
          }.getOrNull()
        val artifacts =
          runCatching {
            client.listRunArtifacts(settings.accessToken, owner, repo, run.id)
          }.getOrDefault(emptyList())

        val startedMs = job?.startedAtMillis ?: run.runStartedAtMillis
        val completedMs = job?.completedAtMillis ?: run.updatedAtMillis
        val durationMs =
          if (startedMs != null && completedMs != null) (completedMs - startedMs).coerceAtLeast(0L)
          else null

        CloudBuildNotifier.notifyBuildOutcomeIfNeeded(
          context = applicationContext,
          runId = run.id,
          runNumber = run.runNumber,
          conclusion = run.conclusion ?: "unknown",
          durationMs = durationMs,
          hasArtifacts = artifacts.any { !it.expired },
        )
        return Result.success()
      }

      if (attempt < maxAttempts - 1) {
        delay(pollMs)
      }
    }

    // Build is still running beyond this worker slice; hand off to a fresh delayed check
    CloudBuildMonitorScheduler.scheduleMonitor(
      context = applicationContext,
      runId = targetRunId,
      initialDelaySec = settings.pollIntervalSeconds.coerceIn(5, 30).toLong(),
    )
    return Result.success()
  }

  companion object {
    const val KEY_RUN_ID = "run_id"
  }
}

object CloudBuildMonitorScheduler {
  private const val UNIQUE_WORK_NAME = "dex2c_cloud_build_monitor"

  fun scheduleMonitor(
    context: Context,
    runId: Long,
    initialDelaySec: Long = 5L,
  ) {
    val constraints =
      Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    val data = Data.Builder().putLong(CloudBuildMonitorWorker.KEY_RUN_ID, runId).build()

    val request =
      OneTimeWorkRequestBuilder<CloudBuildMonitorWorker>()
        .setConstraints(constraints)
        .setInputData(data)
        .setInitialDelay(initialDelaySec.coerceAtLeast(0L), TimeUnit.SECONDS)
        .build()

    WorkManager.getInstance(context)
      .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
  }

  fun cancelMonitor(context: Context) {
    WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
  }
}
