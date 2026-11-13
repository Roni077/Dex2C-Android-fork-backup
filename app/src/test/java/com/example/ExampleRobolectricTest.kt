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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
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
  fun `onboarding github screen shows continue with github account, 8-digit code, 15min timer, and device link`() {
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

    // Intro screen stays clean without Continue with GitHub button
    composeTestRule.onNodeWithTag("hero_continue_github_btn").assertDoesNotExist()
    composeTestRule.onNodeWithTag("onboarding_continue_github_btn").assertDoesNotExist()

    // Swipe left twice: Intro -> About -> GitHub
    composeTestRule.onNodeWithTag("onboarding_pager").performTouchInput { swipeLeft() }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("onboarding_pager").performTouchInput { swipeLeft() }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("onboarding_continue_github_btn")
      .performScrollTo()
      .assertIsDisplayed()
      .assertIsEnabled()
      .assertTextContains("Continue with GitHub account")
      .performClick()

    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("onboarding_device_code_box")
      .performScrollTo()
      .assertIsDisplayed()

    composeTestRule.onNodeWithTag("onboarding_device_user_code", useUnmergedTree = true)
      .assertIsDisplayed()
      .assertTextContains("WDJB-MJHT")

    composeTestRule.onNodeWithTag("onboarding_device_expiry_badge")
      .performScrollTo()
      .assertIsDisplayed()
      .assert(
        hasText("Expires in 15:00", substring = true) or
          hasText("Expires in 14:5", substring = true)
      )

    composeTestRule.onNodeWithTag("onboarding_device_url_link")
      .performScrollTo()
      .assertIsDisplayed()
      .assertTextContains("https://github.com/login/device", substring = true)
      .performClick()

    assertTrue(openedUrls.contains("https://github.com/login/device"))
  }

  @Test
  fun `onboarding removes step tracking and swipes across Intro, About, GitHub, Theme, Alerts in order`() {
    var finished = false

    composeTestRule.setContent {
      Dex2cTheme {
        OnboardingScreen(
          isReplayMode = false,
          appSettings = AppSettings(),
          cloudSettings = CloudSettings(),
          deviceAuthState = DeviceAuthState.Idle,
          hasNotificationPermission = false,
          onUpdateAppSettings = {},
          onUpdateCloudSettings = {},
          onStartDeviceLogin = {},
          onCancelDeviceLogin = {},
          onCopyCode = {},
          onOpenBrowserUrl = {},
          onToggleNotifications = {},
          onRequestNotificationPermission = {},
          onFinish = { finished = true },
          onDismissReplay = {},
        )
      }
    }

    composeTestRule.onNodeWithText("Step 1", substring = true).assertDoesNotExist()
    composeTestRule.onNodeWithTag("onboarding_page_dots").assertIsDisplayed()
    repeat(5) { idx ->
      composeTestRule.onNodeWithTag("onboarding_page_dot_$idx").assertIsDisplayed()
    }
    composeTestRule.onNodeWithText("Dex2C Studio").assertIsDisplayed()

    // Tap dot 1 -> About screen
    composeTestRule.onNodeWithTag("onboarding_page_dot_1").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("About Dex2C Studio").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("DEX Inspector").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("JNI Transpiler").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("Cloud NDK").performScrollTo().assertIsDisplayed()

    // Swipe right back to Intro screen
    composeTestRule.onNodeWithTag("onboarding_pager").performTouchInput { swipeRight() }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Dex2C Studio").performScrollTo().assertIsDisplayed()

    // Tap dot 3 -> Theme screen, then tap dot 2 -> GitHub screen, then swipe left through Theme -> Alerts
    composeTestRule.onNodeWithTag("onboarding_page_dot_3").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Theme & Palette").performScrollTo().assertIsDisplayed()

    composeTestRule.onNodeWithTag("onboarding_page_dot_2").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("GitHub Cloud CI").performScrollTo().assertIsDisplayed()

    composeTestRule.onNodeWithTag("onboarding_pager").performTouchInput { swipeLeft() }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Theme & Palette").performScrollTo().assertIsDisplayed()

    composeTestRule.onNodeWithTag("onboarding_pager").performTouchInput { swipeLeft() }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Build Alerts").performScrollTo().assertIsDisplayed()

    composeTestRule.onNodeWithTag("onboarding_finish_btn").performClick()
    assertTrue(finished)
  }
}
