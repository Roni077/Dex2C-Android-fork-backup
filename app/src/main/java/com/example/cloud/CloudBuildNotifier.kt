package com.example.cloud

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.ROUTE_CLOUD
import com.example.data.CloudPreferences
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

object CloudBuildNotifier {

  const val CHANNEL_ID = "dex2c_cloud_builds"
  const val EXTRA_NAVIGATE_ROUTE = "navigate_route"
  const val EXTRA_CLOUD_RUN_ID = "cloud_run_id"
  private val NOTIFY_LOCK = Any()
  @Volatile private var lastDispatchedRunId: Long = 0L

  fun hasNotificationPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(
      context,
      Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
  }

  fun ensureNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
      val existing = manager.getNotificationChannel(CHANNEL_ID)
      if (existing == null) {
        val channel =
          NotificationChannel(
            CHANNEL_ID,
            "Cloud Build Status",
            NotificationManager.IMPORTANCE_HIGH,
          ).apply {
            description = "Alerts when GitHub Actions cloud Dex2C APK builds finish or fail"
            enableVibration(true)
          }
        manager.createNotificationChannel(channel)
      }
    }
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

  suspend fun notifyBuildOutcomeIfNeeded(
    context: Context,
    runId: Long,
    runNumber: Int,
    conclusion: String,
    durationMs: Long?,
    hasArtifacts: Boolean,
  ): Boolean {
    val prefs = CloudPreferences(context)
    val settings = prefs.settingsFlow.first()
    if (!settings.notificationsEnabled) return false

    synchronized(NOTIFY_LOCK) {
      if (settings.lastNotifiedRunId == runId || lastDispatchedRunId == runId) return false
      lastDispatchedRunId = runId
    }

    if (!hasNotificationPermission(context)) return false
    ensureNotificationChannel(context)

    val content = buildNotificationContent(runNumber, conclusion, durationMs, hasArtifacts)
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

    val accentColor = if (content.isSuccess) 0xFF2EE59D.toInt() else 0xFFFF6B7A.toInt()
    val notification =
      NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_cloud_build_status)
        .setContentTitle(content.title)
        .setContentText(content.body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(content.body))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setColor(accentColor)
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

  fun sendTestNotification(context: Context): Boolean {
    if (!hasNotificationPermission(context)) return false
    ensureNotificationChannel(context)
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
        .setContentText("Background cloud build notifications are active and ready.")
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setColor(0xFF2EE59D.toInt())
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
