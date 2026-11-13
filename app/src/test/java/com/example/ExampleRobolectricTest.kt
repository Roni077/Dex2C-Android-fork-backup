package com.example

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.cloud.CloudBuildNotifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Dex2C Studio", appName)
  }

  @Test
  fun `notification channel and deep link intent resolve properly`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    CloudBuildNotifier.ensureNotificationChannel(context)
    val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channel = mgr.getNotificationChannel(CloudBuildNotifier.CHANNEL_ID)
    assertNotNull(channel)
    assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)

    val intent =
      Intent(context, MainActivity::class.java).apply {
        putExtra(CloudBuildNotifier.EXTRA_NAVIGATE_ROUTE, ROUTE_CLOUD)
        putExtra(CloudBuildNotifier.EXTRA_CLOUD_RUN_ID, 987654321L)
      }
    val parsed = CloudBuildNotifier.extractDeepLinkTarget(intent)
    assertNotNull(parsed)
    assertEquals(ROUTE_CLOUD, parsed!!.route)
    assertEquals(987654321L, parsed.runId)

    assertNull(CloudBuildNotifier.extractDeepLinkTarget(Intent(context, MainActivity::class.java)))
  }
}
