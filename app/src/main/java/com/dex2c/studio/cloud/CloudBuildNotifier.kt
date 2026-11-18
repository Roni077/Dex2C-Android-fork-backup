package com.dex2c.studio.cloud

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.dex2c.studio.MainActivity
import com.dex2c.studio.R
import com.dex2c.studio.ROUTE_CLOUD
import com.dex2c.studio.data.AppPreferences
import com.dex2c.studio.data.AppSettings
import com.dex2c.studio.data.AppThemeMode
import com.dex2c.studio.data.CloudPreferences
import com.dex2c.studio.ui.theme.M3TonalColorEngine
import kotlinx.coroutines.flow.first

data class CloudNotificationContent(
  val title: String,
  val body: String,
  val isSuccess: Boolean,
)

data class CloudDeepLinkTarget(
  val route: String,
  val runId: Long,
)

enum class CloudLivePhase(val badge: String) {
  UPLOADING("Uploading"),
  BUILDING("Building"),
  DOWNLOADING("Downloading"),
}

data class CloudLiveNotificationContent(
  val title: String,
  val body: String,
  val progressMax: Int,
  val progressCurrent: Int,
  val indeterminate: Boolean,
  val phase: CloudLivePhase,
)

data class NotificationThemeColors(
  val accentArgb: Int,
  val containerArgb: Int,
  val onContainerArgb: Int,
  val surfaceArgb: Int,
  val isDark: Boolean,
)

object CloudBuildNotifier {

  const val CHANNEL_ID = "dex2c_cloud_builds"
  const val PROGRESS_CHANNEL_ID = "dex2c_cloud_progress"
  const val LIVE_PROGRESS_NOTIFICATION_ID = 4201
  const val DOWNLOAD_DONE_NOTIFICATION_ID = 4202
  const val EXTRA_NAVIGATE_ROUTE = "navigate_route"
  const val EXTRA_CLOUD_RUN_ID = "cloud_run_id"
  private val NOTIFY_LOCK = Any()
  @Volatile private var lastDispatchedRunId: Long = 0L
  @Volatile private var cachedAppSettings: AppSettings = AppSettings()
  private val themeColorCache = java.util.concurrent.ConcurrentHashMap<String, NotificationThemeColors>()

  fun updateCachedAppSettings(settings: AppSettings) {
    cachedAppSettings = settings
    themeColorCache.clear()
  }

  fun resolveNotificationSmallIcon(phase: CloudLivePhase): Int {
    return when (phase) {
      CloudLivePhase.UPLOADING -> R.drawable.ic_cloud_upload
      CloudLivePhase.DOWNLOADING -> R.drawable.ic_cloud_build_status
      CloudLivePhase.BUILDING -> R.drawable.ic_cloud_build_status
    }
  }

  fun resolveNotificationThemeColors(
    context: Context,
    appSettings: AppSettings? = null,
    isError: Boolean = false,
  ): NotificationThemeColors {
    val effective = appSettings ?: cachedAppSettings
    if (appSettings != null) {
      cachedAppSettings = appSettings
    }
    val systemDark =
      (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES
    val isAmoled = effective.themeMode == AppThemeMode.AMOLED
    val isDark =
      when (effective.themeMode) {
        AppThemeMode.SYSTEM -> systemDark
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK, AppThemeMode.AMOLED -> true
      }

    val cacheKey = "${effective.themeMode}_${effective.dynamicColor}_${effective.accentSeedHex}_${isDark}_${isAmoled}_$isError"
    themeColorCache[cacheKey]?.let { return it }

    val scheme =
      if (effective.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val dynamic =
          if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        if (isAmoled) M3TonalColorEngine.applyAmoledOverride(dynamic) else dynamic
      } else {
        val seed = M3TonalColorEngine.parseHexOrDefault(effective.accentSeedHex)
        M3TonalColorEngine.buildSchemeFromSeed(
          seedColor = seed,
          isDark = isDark,
          isAmoled = isAmoled,
        )
      }

    val accent = if (isError) scheme.error else scheme.primary
    val container = if (isError) scheme.errorContainer else scheme.primaryContainer
    val onContainer = if (isError) scheme.onErrorContainer else scheme.onPrimaryContainer

    val resolved = NotificationThemeColors(
      accentArgb = accent.toArgb(),
      containerArgb = container.toArgb(),
      onContainerArgb = onContainer.toArgb(),
      surfaceArgb = scheme.surfaceContainerHigh.toArgb(),
      isDark = isDark,
    )
    themeColorCache[cacheKey] = resolved
    return resolved
  }

  fun hasNotificationPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(
      context,
      Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
  }

  fun ensureNotificationChannel(
    context: Context,
    appSettings: AppSettings? = null,
  ) {
    if (appSettings != null) {
      cachedAppSettings = appSettings
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
      val themeColors = resolveNotificationThemeColors(context, appSettings, isError = false)
      if (manager.getNotificationChannel(CHANNEL_ID) == null) {
        val channel =
          NotificationChannel(
            CHANNEL_ID,
            "Cloud Build Status",
            NotificationManager.IMPORTANCE_HIGH,
          ).apply {
            description = "Alerts when GitHub Actions cloud Dex2C APK builds or downloads finish"
            enableVibration(true)
            enableLights(true)
            lightColor = themeColors.accentArgb
          }
        manager.createNotificationChannel(channel)
      } else {
        manager.getNotificationChannel(CHANNEL_ID)?.let { existing ->
          existing.lightColor = themeColors.accentArgb
          manager.createNotificationChannel(existing)
        }
      }
      if (manager.getNotificationChannel(PROGRESS_CHANNEL_ID) == null) {
        val progressChannel =
          NotificationChannel(
            PROGRESS_CHANNEL_ID,
            "Live Upload & Build Progress",
            NotificationManager.IMPORTANCE_LOW,
          ).apply {
            description = "Silent ongoing progress bar during APK upload, cloud compilation, and download"
            setShowBadge(false)
          }
        manager.createNotificationChannel(progressChannel)
      }
    }
  }

  fun buildUploadProgressNotification(
    stageLabel: String,
    bytesUploaded: Long,
    totalBytes: Long,
    progressFraction: Float?,
  ): CloudLiveNotificationContent {
    val pct = ((progressFraction ?: 0f) * 100f).toInt().coerceIn(0, 100)
    val body =
      if (totalBytes > 0L) {
        val transferred = CloudTimeUtil.formatTransferBytes(bytesUploaded.coerceAtMost(totalBytes))
        val total = CloudTimeUtil.formatTransferBytes(totalBytes)
        "$stageLabel • $transferred / $total ($pct%)"
      } else if (progressFraction != null) {
        "$stageLabel ($pct%)"
      } else {
        stageLabel
      }
    return CloudLiveNotificationContent(
      title = "Dex2C Studio • Uploading to GitHub",
      body = body,
      progressMax = 100,
      progressCurrent = pct,
      indeterminate = progressFraction == null,
      phase = CloudLivePhase.UPLOADING,
    )
  }

  fun buildWorkflowProgressNotification(
    runNumber: Int,
    status: String,
    activeStepName: String?,
    completedSteps: Int,
    totalSteps: Int,
  ): CloudLiveNotificationContent {
    val statusLabel = status.replace('_', ' ').replaceFirstChar { it.uppercase() }
    val title =
      if (runNumber > 0) "Dex2C Cloud Build #$runNumber • $statusLabel"
      else "Dex2C Cloud Build • $statusLabel"
    val body =
      if (totalSteps > 0) {
        "Step $completedSteps/$totalSteps • ${activeStepName ?: "Running workflow…"}"
      } else {
        activeStepName ?: "Waiting for GitHub Actions runner…"
      }
    return CloudLiveNotificationContent(
      title = title,
      body = body,
      progressMax = if (totalSteps > 0) totalSteps else 100,
      progressCurrent = if (totalSteps > 0) completedSteps.coerceIn(0, totalSteps) else 0,
      indeterminate = totalSteps <= 0,
      phase = CloudLivePhase.BUILDING,
    )
  }

  fun buildDownloadProgressNotification(
    artifactName: String,
    bytesDownloaded: Long,
    totalBytes: Long,
    progressFraction: Float?,
  ): CloudLiveNotificationContent {
    val pct = ((progressFraction ?: 0f) * 100f).toInt().coerceIn(0, 100)
    val body =
      when {
        totalBytes > 0L -> {
          val recv = CloudTimeUtil.formatTransferBytes(bytesDownloaded.coerceAtMost(totalBytes))
          val total = CloudTimeUtil.formatTransferBytes(totalBytes)
          "$recv / $total ($pct%)"
        }
        bytesDownloaded > 0L -> "Downloaded ${CloudTimeUtil.formatTransferBytes(bytesDownloaded)}…"
        else -> "Connecting to GitHub Actions artifact…"
      }
    return CloudLiveNotificationContent(
      title = "Downloading $artifactName",
      body = body,
      progressMax = 100,
      progressCurrent = pct,
      indeterminate = progressFraction == null,
      phase = CloudLivePhase.DOWNLOADING,
    )
  }

  fun buildNotificationContent(
    runNumber: Int,
    conclusion: String,
    durationMs: Long?,
    hasArtifacts: Boolean,
  ): CloudNotificationContent {
    val isSuccess = conclusion.equals("success", ignoreCase = true)
    val durationLabel =
      durationMs?.takeIf { it > 0L }?.let { " in ${CloudTimeUtil.formatDurationMmSs(it)}" }.orEmpty()
    val title =
      if (isSuccess) "Dex2C Cloud Build #$runNumber Succeeded"
      else "Dex2C Cloud Build #$runNumber ${conclusion.replaceFirstChar { it.uppercase() }}"
    val body =
      if (isSuccess) {
        if (hasArtifacts) {
          "Compiled & signed APK ready to download${durationLabel}."
        } else {
          "Workflow completed cleanly${durationLabel}. Tap to inspect."
        }
      } else {
        "GitHub Actions workflow finished with status '$conclusion'${durationLabel}. Tap to view logs."
      }
    return CloudNotificationContent(title = title, body = body, isSuccess = isSuccess)
  }

  fun extractDeepLinkTarget(intent: Intent?): CloudDeepLinkTarget? {
    if (intent == null) return null
    val route = intent.getStringExtra(EXTRA_NAVIGATE_ROUTE) ?: return null
    val runId = intent.getLongExtra(EXTRA_CLOUD_RUN_ID, -1L)
    if (runId <= 0L) return null
    return CloudDeepLinkTarget(route = route, runId = runId)
  }

  fun updateLiveProgressNotification(
    context: Context,
    content: CloudLiveNotificationContent,
    runId: Long? = null,
    notificationsEnabled: Boolean = true,
    appSettings: AppSettings? = null,
  ): Boolean {
    if (!notificationsEnabled || !hasNotificationPermission(context)) return false
    ensureNotificationChannel(context, appSettings)
    val themeColors = resolveNotificationThemeColors(context, appSettings, isError = false)
    val tapIntent =
      Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(EXTRA_NAVIGATE_ROUTE, ROUTE_CLOUD)
        if (runId != null && runId > 0L) {
          putExtra(EXTRA_CLOUD_RUN_ID, runId)
        }
      }
    val pendingIntent =
      PendingIntent.getActivity(
        context,
        LIVE_PROGRESS_NOTIFICATION_ID,
        tapIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val notification =
      NotificationCompat.Builder(context, PROGRESS_CHANNEL_ID)
        .setSmallIcon(resolveNotificationSmallIcon(content.phase))
        .setContentTitle(content.title)
        .setContentText(content.body)
        .setSubText(content.phase.badge)
        .setProgress(content.progressMax, content.progressCurrent, content.indeterminate)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setColor(themeColors.accentArgb)
        .setContentIntent(pendingIntent)
        .build()

    return try {
      NotificationManagerCompat.from(context).notify(LIVE_PROGRESS_NOTIFICATION_ID, notification)
      true
    } catch (_: SecurityException) {
      false
    }
  }

  fun cancelLiveProgressNotification(context: Context) {
    runCatching {
      NotificationManagerCompat.from(context).cancel(LIVE_PROGRESS_NOTIFICATION_ID)
    }
  }

  fun notifyDownloadCompleted(
    context: Context,
    fileName: String,
    isError: Boolean = false,
    notificationsEnabled: Boolean = true,
    bodyOverride: String? = null,
    appSettings: AppSettings? = null,
  ): Boolean {
    cancelLiveProgressNotification(context)
    if (!notificationsEnabled || !hasNotificationPermission(context)) return false
    ensureNotificationChannel(context, appSettings)
    val themeColors = resolveNotificationThemeColors(context, appSettings, isError = isError)

    val tapIntent =
      Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(EXTRA_NAVIGATE_ROUTE, ROUTE_CLOUD)
      }
    val pendingIntent =
      PendingIntent.getActivity(
        context,
        DOWNLOAD_DONE_NOTIFICATION_ID,
        tapIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val title = if (isError) "Download Failed" else "Download Complete"
    val body =
      bodyOverride ?: if (isError) {
        "Unable to download $fileName from GitHub Actions."
      } else {
        "$fileName is ready."
      }

    val notification =
      NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_cloud_build_status)
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setColor(themeColors.accentArgb)
        .setAutoCancel(true)
        .setContentIntent(pendingIntent)
        .build()

    return try {
      NotificationManagerCompat.from(context).notify(DOWNLOAD_DONE_NOTIFICATION_ID, notification)
      true
    } catch (_: SecurityException) {
      false
    }
  }

  suspend fun notifyBuildOutcomeIfNeeded(
    context: Context,
    runId: Long,
    runNumber: Int,
    conclusion: String,
    durationMs: Long?,
    hasArtifacts: Boolean,
    appSettings: AppSettings? = null,
  ): Boolean {
    cancelLiveProgressNotification(context)
    val prefs = CloudPreferences(context)
    val settings = prefs.settingsFlow.first()
    if (!settings.notificationsEnabled) return false

    synchronized(NOTIFY_LOCK) {
      if (settings.lastNotifiedRunId == runId || lastDispatchedRunId == runId) return false
      lastDispatchedRunId = runId
    }

    if (!hasNotificationPermission(context)) return false
    val resolvedAppSettings =
      appSettings ?: runCatching { AppPreferences(context).settingsFlow.first() }.getOrNull()
    ensureNotificationChannel(context, resolvedAppSettings)

    val content = buildNotificationContent(runNumber, conclusion, durationMs, hasArtifacts)
    val themeColors =
      resolveNotificationThemeColors(
        context = context,
        appSettings = resolvedAppSettings,
        isError = !content.isSuccess,
      )
    val tapIntent =
      Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(EXTRA_NAVIGATE_ROUTE, ROUTE_CLOUD)
        putExtra(EXTRA_CLOUD_RUN_ID, runId)
      }
    val pendingIntent =
      PendingIntent.getActivity(
        context,
        runId.toInt(),
        tapIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val notification =
      NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_cloud_build_status)
        .setContentTitle(content.title)
        .setContentText(content.body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(content.body))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setColor(themeColors.accentArgb)
        .setAutoCancel(true)
        .setContentIntent(pendingIntent)
        .build()

    return try {
      NotificationManagerCompat.from(context).notify(runId.toInt(), notification)
      prefs.markRunNotified(runId)
      true
    } catch (_: SecurityException) {
      false
    }
  }

  fun sendTestNotification(
    context: Context,
    appSettings: AppSettings? = null,
  ): Boolean {
    if (!hasNotificationPermission(context)) return false
    ensureNotificationChannel(context, appSettings)
    val themeColors = resolveNotificationThemeColors(context, appSettings, isError = false)
    val tapIntent =
      Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(EXTRA_NAVIGATE_ROUTE, ROUTE_CLOUD)
      }
    val pendingIntent =
      PendingIntent.getActivity(
        context,
        7777,
        tapIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
    val notification =
      NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_cloud_build_status)
        .setContentTitle("Dex2C Studio • Test Alert")
        .setContentText("Background cloud build notifications are active and matched to your theme.")
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setColor(themeColors.accentArgb)
        .setAutoCancel(true)
        .setContentIntent(pendingIntent)
        .build()
    return try {
      NotificationManagerCompat.from(context).notify(7777, notification)
      true
    } catch (_: SecurityException) {
      false
    }
  }
}
