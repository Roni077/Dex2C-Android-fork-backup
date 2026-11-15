package com.dex2c.studio.cloud

import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

enum class CloudSigningMode(val label: String, val summary: String) {
  DEBUG_AUTO("Auto Debug Keystore", "Generates and signs output APK with androiddebugkey in workflow"),
  REPO_KEYSTORE_SECRETS("Repo Secrets Keystore", "Uses KEYSTORE_BASE64 / KEYSTORE_PASS secrets with debug fallback"),
}

val SUPPORTED_NDK_VERSIONS = listOf("r25b", "r26d", "r27c")
val SUPPORTED_JDK_VERSIONS = listOf("17", "21")

const val GITHUB_CLI_CLIENT_ID = "178c6fc778ccc68e1d6a"
const val GITHUB_DEVICE_VERIFICATION_URL = "https://github.com/login/device"

data class CloudSettings(
  val githubClientId: String = "",
  val accessToken: String = "",
  val githubUsername: String = "",
  val repoOwner: String = "",
  val repoName: String = "dex2c-cloud-workspace",
  val defaultBranch: String = "main",
  val privateRepo: Boolean = true,
  val workflowInitialized: Boolean = false,
  val ndkVersion: String = "r25b",
  val jdkVersion: String = "17",
  val signingMode: CloudSigningMode = CloudSigningMode.DEBUG_AUTO,
  val pollIntervalSeconds: Int = 6,
  val maxDirectUploadMb: Int = 45,
  val notificationsEnabled: Boolean = true,
  val lastNotifiedRunId: Long = 0L,
) {
  val isAuthenticated: Boolean get() = accessToken.isNotBlank() && githubUsername.isNotBlank()
  val effectiveOwner: String get() = repoOwner.ifBlank { githubUsername }
  val isRepoConfigured: Boolean get() = isAuthenticated && effectiveOwner.isNotBlank() && repoName.isNotBlank()
  val effectiveClientId: String get() = githubClientId.trim().ifBlank { GITHUB_CLI_CLIENT_ID }
  val usingCustomClientId: Boolean get() = githubClientId.trim().isNotEmpty()
}

data class DeviceCodeResponse(
  val deviceCode: String,
  val userCode: String,
  val verificationUri: String,
  val expiresInSec: Int,
  val intervalSec: Int,
)

sealed interface DeviceAuthState {
  data object Idle : DeviceAuthState
  data object RequestingCode : DeviceAuthState
  data class AwaitingUser(
    val userCode: String,
    val verificationUri: String,
    val expiresInSec: Int,
    val intervalSec: Int,
    val expiresAtMillis: Long = System.currentTimeMillis() + expiresInSec.coerceAtLeast(1) * 1000L,
    val statusNote: String = "Waiting for authorization at github.com/login/device…",
  ) : DeviceAuthState {
    fun remainingMillisAt(nowMillis: Long): Long = (expiresAtMillis - nowMillis).coerceAtLeast(0L)

    fun remainingFormattedAt(nowMillis: Long): String {
      val remMs = remainingMillisAt(nowMillis)
      val displaySec =
        if (remMs == 0L) 0L
        else ((remMs + 999L) / 1000L).coerceAtMost(expiresInSec.toLong().coerceAtLeast(1L))
      return CloudTimeUtil.formatDurationMmSs(displaySec * 1000L)
    }
  }
  data class Authenticated(val username: String) : DeviceAuthState
  data class Error(val message: String) : DeviceAuthState
}

sealed interface RepoSetupState {
  data object NotConfigured : RepoSetupState
  data class Working(val stepLabel: String) : RepoSetupState
  data class Ready(
    val owner: String,
    val repo: String,
    val htmlUrl: String,
    val isPrivate: Boolean,
    val hasWorkflow: Boolean,
  ) : RepoSetupState
  data class Error(val message: String) : RepoSetupState
}

data class GitHubWorkflowStep(
  val name: String,
  val status: String,
  val conclusion: String?,
)

data class GitHubJobTelemetry(
  val jobName: String = "dex2c-apk",
  val jobStatus: String = "queued",
  val startedAtMillis: Long? = null,
  val completedAtMillis: Long? = null,
  val steps: List<GitHubWorkflowStep> = emptyList(),
)

data class GitHubWorkflowRunSummary(
  val id: Long,
  val runNumber: Int,
  val status: String,
  val conclusion: String?,
  val htmlUrl: String,
  val createdAt: String,
  val runStartedAtMillis: Long? = null,
  val updatedAtMillis: Long? = null,
)

data class GitHubArtifactItem(
  val id: Long,
  val name: String,
  val sizeInBytes: Long,
  val archiveDownloadUrl: String,
  val createdAt: String,
  val expired: Boolean,
)

enum class CloudErrorAction {
  RETRY_DISPATCH,
  RETRY_POLL,
}

sealed interface CloudBuildRunState {
  data object Idle : CloudBuildRunState
  data class UploadingInputs(
    val message: String,
    val progressFraction: Float? = null,
    val bytesUploaded: Long = 0L,
    val totalBytes: Long = 0L,
  ) : CloudBuildRunState
  data object Dispatching : CloudBuildRunState
  data class Polling(
    val runId: Long,
    val runNumber: Int,
    val jobName: String,
    val status: String,
    val conclusion: String?,
    val htmlUrl: String,
    val steps: List<GitHubWorkflowStep>,
    val startedAtMillis: Long?,
    val lastPolledAtMillis: Long,
    val reconnectingNote: String? = null,
  ) : CloudBuildRunState
  data class Completed(
    val runId: Long,
    val runNumber: Int,
    val conclusion: String,
    val htmlUrl: String,
    val artifacts: List<GitHubArtifactItem>,
    val steps: List<GitHubWorkflowStep> = emptyList(),
    val startedAtMillis: Long? = null,
    val completedAtMillis: Long? = null,
  ) : CloudBuildRunState
  data class SavingArtifact(
    val label: String,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val progressFraction: Float? = null,
    val completedSnapshot: Completed? = null,
  ) : CloudBuildRunState
  data class Error(
    val message: String,
    val retryAction: CloudErrorAction? = null,
    val failedRunId: Long? = null,
  ) : CloudBuildRunState
}

data class CloudDownloadedApk(
  val fileName: String,
  val bytes: ByteArray,
  val isZipFallback: Boolean,
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is CloudDownloadedApk) return false
    return fileName == other.fileName && isZipFallback == other.isZipFallback && bytes.contentEquals(other.bytes)
  }

  override fun hashCode(): Int {
    var result = fileName.hashCode()
    result = 31 * result + bytes.contentHashCode()
    result = 31 * result + isZipFallback.hashCode()
    return result
  }
}

object CloudTimeUtil {
  private val ISO_REGEX =
    Regex("""^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})(?:\.\d+)?(Z|[+-]\d{2}:\d{2})$""")

  fun parseIsoUtcMillis(iso: String?): Long? {
    val clean = iso?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val match = ISO_REGEX.matchEntire(clean) ?: return null
    val year = match.groupValues[1].toIntOrNull() ?: return null
    val month = match.groupValues[2].toIntOrNull() ?: return null
    val day = match.groupValues[3].toIntOrNull() ?: return null
    val hour = match.groupValues[4].toIntOrNull() ?: return null
    val minute = match.groupValues[5].toIntOrNull() ?: return null
    val second = match.groupValues[6].toIntOrNull() ?: return null
    val tzPart = match.groupValues[7]

    val cal =
      GregorianCalendar(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(year, month - 1, day, hour, minute, second)
        set(Calendar.MILLISECOND, 0)
      }
    var millis = cal.timeInMillis
    if (tzPart != "Z") {
      val sign = if (tzPart.startsWith("-")) -1 else 1
      val offHour = tzPart.substring(1, 3).toIntOrNull() ?: 0
      val offMin = tzPart.substring(4, 6).toIntOrNull() ?: 0
      val offsetMillis = sign * (offHour * 3600_000L + offMin * 60_000L)
      millis -= offsetMillis
    }
    return millis
  }

  fun formatDurationMmSs(durationMs: Long): String {
    val safeSec = (durationMs.coerceAtLeast(0L)) / 1000L
    val hours = safeSec / 3600L
    val minutes = (safeSec % 3600L) / 60L
    val seconds = safeSec % 60L
    return if (hours > 0L) {
      String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
      String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
  }

  fun computeStepProgress(steps: List<GitHubWorkflowStep>): Float? {
    if (steps.isEmpty()) return null
    val completed = steps.count { it.status == "completed" }
    return (completed.toFloat() / steps.size.toFloat()).coerceIn(0f, 1f)
  }

  fun formatTransferBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return when {
      mb >= 1.0 -> String.format(Locale.US, "%.2f MB", mb)
      kb >= 1.0 -> String.format(Locale.US, "%.1f KB", kb)
      else -> "$bytes B"
    }
  }
}
