package com.example

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.cloud.CloudBuildNotifier
import com.example.cloud.CloudSettings
import com.example.cloud.DeviceAuthState
import com.example.cloud.GITHUB_DEVICE_VERIFICATION_URL
import com.example.data.AppSettings
import com.example.ui.screens.OnboardingScreen
import com.example.ui.theme.Dex2cTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @get:Rule val composeTestRule = createComposeRule()

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

  @Test
  fun `onboarding hero shows continue with github account, 8-digit code, 15min timer, and device link`() {
    var authState by mutableStateOf<DeviceAuthState>(DeviceAuthState.Idle)
    val openedUrls = mutableListOf<String>()

    composeTestRule.setContent {
      Dex2cTheme {
        OnboardingScreen(
          isReplayMode = false,
          appSettings = AppSettings(),
          cloudSettings = CloudSettings(),
          deviceAuthState = authState,
          hasNotificationPermission = true,
          onUpdateAppSettings = {},
          onUpdateCloudSettings = {},
          onStartDeviceLogin = {
            authState =
              DeviceAuthState.AwaitingUser(
                userCode = "WDJB-MJHT",
                verificationUri = GITHUB_DEVICE_VERIFICATION_URL,
                expiresInSec = 900,
                intervalSec = 5,
                expiresAtMillis = System.currentTimeMillis() + 900_000L,
              )
          },
          onCancelDeviceLogin = { authState = DeviceAuthState.Idle },
          onCopyCode = {},
          onOpenBrowserUrl = { url -> openedUrls.add(url) },
          onToggleNotifications = {},
          onRequestNotificationPermission = {},
          onFinish = {},
          onDismissReplay = {},
        )
      }
    }

    composeTestRule.onNodeWithTag("hero_continue_github_btn")
      .performScrollTo()
      .assertIsDisplayed()
      .assertIsEnabled()
      .assertTextContains("Continue with GitHub account")
      .performClick()

    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("hero_device_code_box")
      .performScrollTo()
      .assertIsDisplayed()

    composeTestRule.onNodeWithTag("hero_device_user_code", useUnmergedTree = true)
      .assertIsDisplayed()
      .assertTextContains("WDJB-MJHT")

    composeTestRule.onNodeWithTag("hero_device_expiry_badge")
      .performScrollTo()
      .assertIsDisplayed()
      .assert(
        hasText("Expires in 15:00", substring = true) or
          hasText("Expires in 14:5", substring = true)
      )

    composeTestRule.onNodeWithTag("hero_device_url_link")
      .performScrollTo()
      .assertIsDisplayed()
      .assertTextContains("https://github.com/login/device", substring = true)
      .performClick()

    assertTrue(openedUrls.contains("https://github.com/login/device"))
  }
}
