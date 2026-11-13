package com.example

import android.app.Application
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
import com.example.cloud.CloudBuildRunState
import com.example.cloud.CloudErrorAction
import com.example.cloud.CloudSettings
import com.example.cloud.DeviceAuthState
import com.example.cloud.GITHUB_DEVICE_VERIFICATION_URL
import com.example.cloud.GitHubApiClient
import com.example.cloud.GitHubWorkflowStep
import com.example.cloud.RepoSetupState
import com.example.data.AppSettings
import com.example.ui.Dex2cViewModel
import com.example.ui.components.BuildMonitorCard
import com.example.ui.screens.OnboardingScreen
import com.example.ui.theme.Dex2cTheme
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.robolectric.shadows.ShadowLooper
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

  @Test
  fun `onboarding github card shows inline repo initialization status and BuildMonitor shows reconnecting and retry`() {
    var setupState by mutableStateOf<RepoSetupState>(RepoSetupState.Working("Auto-initializing private repo…"))
    var runState by mutableStateOf<CloudBuildRunState>(
      CloudBuildRunState.Polling(
        runId = 77L,
        runNumber = 7,
        jobName = "dex2c-apk",
        status = "in_progress",
        conclusion = null,
        htmlUrl = "https://github.com/userio12/dex2c-cloud-workspace/actions/runs/77",
        steps = listOf(GitHubWorkflowStep("Checkout", "completed", "success")),
        startedAtMillis = System.currentTimeMillis() - 12_000L,
        lastPolledAtMillis = System.currentTimeMillis(),
        reconnectingNote = "Temporary DNS/network issue — retrying automatically (attempt 1)…",
      )
    )
    var retryClicked = false

    composeTestRule.setContent {
      Dex2cTheme {
        androidx.compose.foundation.layout.Column {
          OnboardingScreen(
            isReplayMode = false,
            appSettings = AppSettings(),
            cloudSettings = CloudSettings(accessToken = "gho_abc", githubUsername = "userio12"),
            deviceAuthState = DeviceAuthState.Authenticated("userio12"),
            repoSetupState = setupState,
            hasNotificationPermission = true,
            onUpdateAppSettings = {},
            onUpdateCloudSettings = {},
            onStartDeviceLogin = {},
            onCancelDeviceLogin = {},
            onCopyCode = {},
            onOpenBrowserUrl = {},
            onRetryRepoSetup = {},
            onToggleNotifications = {},
            onRequestNotificationPermission = {},
            onFinish = {},
            onDismissReplay = {},
            modifier = androidx.compose.ui.Modifier.weight(1f),
          )
          BuildMonitorCard(
            cloudSettings = CloudSettings(accessToken = "gho_abc", githubUsername = "userio12", workflowInitialized = true),
            cloudRunState = runState,
            isAutoPollEnabled = true,
            onToggleAutoPoll = {},
            onRefreshMonitor = {},
            onRetryError = { retryClicked = true },
            onOpenBrowserUrl = {},
            onDownloadArtifact = {},
          )
        }
      }
    }

    composeTestRule.onNodeWithTag("onboarding_page_dot_2").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("onboarding_repo_init_working").performScrollTo().assertIsDisplayed()

    setupState = RepoSetupState.Ready("userio12", "dex2c-cloud-workspace", "https://github.com/userio12/dex2c-cloud-workspace", true, true)
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("onboarding_repo_init_ready").performScrollTo().assertIsDisplayed()

    composeTestRule.onNodeWithText("RECONNECTING").assertIsDisplayed()
    composeTestRule.onNodeWithTag("build_monitor_open_logs_btn").assertIsDisplayed()

    runState = CloudBuildRunState.Error(
      message = "Unable to resolve GitHub host (DNS lookup failed).",
      retryAction = CloudErrorAction.RETRY_POLL,
      failedRunId = 77L,
    )
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("build_monitor_retry_btn").assertIsDisplayed().performClick()
    assertTrue(retryClicked)
  }

  @Test
  fun `github sign in completion automatically initializes private repo and commits workflow despite transient dns error`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    kotlinx.coroutines.runBlocking { com.example.data.CloudPreferences(app).clearAuthSession() }
    val tokenPollCount = AtomicInteger(0)
    val repoCreated = AtomicInteger(0)
    val committedPaths = CopyOnWriteArrayList<String>()
    val jsonMedia = "application/json; charset=utf-8".toMediaType()

    val loopbackDns = object : Dns { override fun lookup(hostname: String): List<InetAddress> = listOf(InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))) }
    val client =
      OkHttpClient.Builder()
        .dns(loopbackDns)
        .addInterceptor { chain ->
          val req = chain.request()
          val path = req.url.encodedPath
          val method = req.method
          val (code, body) =
            when {
              path.endsWith("/login/device/code") ->
                200 to """{"device_code":"dev_1","user_code":"AUTO-9999","verification_uri":"https://github.com/login/device","expires_in":900,"interval":5}"""
              path.endsWith("/login/oauth/access_token") -> {
                if (tokenPollCount.incrementAndGet() == 1) {
                  throw UnknownHostException("Unable to resolve host \"github.com\": No address associated with hostname")
                }
                200 to """{"access_token":"gho_auto_token","token_type":"bearer","scope":"repo workflow"}"""
              }
              path.endsWith("/user") ->
                200 to """{"login":"userio12"}"""
              method == "GET" && path == "/repos/userio12/dex2c-cloud-workspace" ->
                if (repoCreated.get() == 0) 404 to """{"message":"Not Found"}"""
                else 200 to """{"name":"dex2c-cloud-workspace","full_name":"userio12/dex2c-cloud-workspace","html_url":"https://github.com/userio12/dex2c-cloud-workspace","private":true,"default_branch":"main","owner":{"login":"userio12"}}"""
              method == "POST" && path == "/user/repos" -> {
                repoCreated.incrementAndGet()
                201 to """{"name":"dex2c-cloud-workspace","full_name":"userio12/dex2c-cloud-workspace","html_url":"https://github.com/userio12/dex2c-cloud-workspace","private":true,"default_branch":"main","owner":{"login":"userio12"}}"""
              }
              method == "GET" && path.contains("/contents/") ->
                404 to """{"message":"Not Found"}"""
              method == "PUT" && path.contains("/contents/") -> {
                committedPaths.add(path.substringAfter("/contents/"))
                201 to """{"content":{"sha":"abc123"}}"""
              }
              path.contains("/actions/workflows/") ->
                200 to """{"workflow_runs":[]}"""
              else -> 404 to """{"message":"Unhandled test route $method $path"}"""
            }

          Response.Builder()
            .request(req)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code in 200..299) "OK" else "ERR")
            .body(body.toResponseBody(jsonMedia))
            .build()
        }
        .build()

    val vm = Dex2cViewModel(app, GitHubApiClient(httpClient = client))
    ShadowLooper.idleMainLooper()
    vm.startDeviceFlowLogin()

    // Pump Looper virtual clock incrementally while IO threads complete OkHttp + DataStore hops
    val doneDeadline = System.currentTimeMillis() + 15_000L
    while (vm.repoSetupState.value !is RepoSetupState.Ready && System.currentTimeMillis() < doneDeadline) {
      ShadowLooper.idleMainLooper(1_000L, TimeUnit.MILLISECONDS)
      Thread.sleep(50L)
    }

    assertTrue("Expected Authenticated state, got ${vm.deviceAuthState.value}", vm.deviceAuthState.value is DeviceAuthState.Authenticated)
    val ready = vm.repoSetupState.value as? RepoSetupState.Ready
    assertNotNull("Expected RepoSetupState.Ready after sign-in, got ${vm.repoSetupState.value}", ready)
    assertEquals("userio12", ready!!.owner)
    assertEquals("dex2c-cloud-workspace", ready.repo)
    assertTrue(ready.hasWorkflow)
    assertTrue(vm.cloudSettings.value.workflowInitialized)
    assertEquals(1, repoCreated.get())
    assertTrue(committedPaths.contains(".github/workflows/dex2c.yml"))
    assertTrue(committedPaths.contains("dcc.cfg"))
    assertTrue(committedPaths.contains("filter.txt"))
  }

  @Test
  fun `build monitor survives transient UnknownHostException during active poll and recovers to completed`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    kotlinx.coroutines.runBlocking { com.example.data.CloudPreferences(app).clearAuthSession() }
    val runsCallCount = AtomicInteger(0)
    val jsonMedia = "application/json; charset=utf-8".toMediaType()
    val loopbackDns = object : Dns { override fun lookup(hostname: String): List<InetAddress> = listOf(InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))) }

    val client =
      OkHttpClient.Builder()
        .dns(loopbackDns)
        .addInterceptor { chain ->
          val req = chain.request()
          val path = req.url.encodedPath
          val (code, body) =
            when {
              path.contains("/actions/workflows/") && path.endsWith("/runs") -> {
                val n = runsCallCount.incrementAndGet()
                when (n) {
                  1 -> 200 to """{"workflow_runs":[{"id":501,"run_number":9,"status":"in_progress","conclusion":null,"html_url":"https://github.com/userio12/dex2c-cloud-workspace/actions/runs/501","created_at":"2026-11-13T04:00:00Z"}]}"""
                  2 -> throw UnknownHostException("Unable to resolve host \"api.github.com\": No address associated with hostname")
                  else -> 200 to """{"workflow_runs":[{"id":501,"run_number":9,"status":"completed","conclusion":"success","html_url":"https://github.com/userio12/dex2c-cloud-workspace/actions/runs/501","created_at":"2026-11-13T04:00:00Z"}]}"""
                }
              }
              path.endsWith("/jobs") ->
                200 to """{"jobs":[{"name":"dex2c-apk","status":"in_progress","steps":[{"name":"Build APK","status":"completed","conclusion":"success"}]}]}"""
              path.endsWith("/artifacts") ->
                200 to """{"artifacts":[]}"""
              else -> 200 to "{}"
            }

          Response.Builder()
            .request(req)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("OK")
            .body(body.toResponseBody(jsonMedia))
            .build()
        }
        .build()

    val vm = Dex2cViewModel(app, GitHubApiClient(httpClient = client))
    vm.updateCloudSettings {
      it.copy(
        accessToken = "gho_test",
        githubUsername = "userio12",
        repoOwner = "userio12",
        repoName = "dex2c-cloud-workspace",
        workflowInitialized = true,
        notificationsEnabled = false,
      )
    }

    vm.attachToCloudRun(501L)

    // 1st poll: active Polling state
    val pollDeadline = System.currentTimeMillis() + 5_000L
    while (vm.cloudRunState.value !is CloudBuildRunState.Polling && System.currentTimeMillis() < pollDeadline) {
      ShadowLooper.idleMainLooper()
      Thread.sleep(25L)
    }
    assertTrue(vm.cloudRunState.value is CloudBuildRunState.Polling)

    // Step until 2nd poll throws UnknownHostException and sets reconnectingNote (while staying Polling!)
    val reconnectDeadline = System.currentTimeMillis() + 10_000L
    while (
      (vm.cloudRunState.value as? CloudBuildRunState.Polling)?.reconnectingNote == null &&
        System.currentTimeMillis() < reconnectDeadline
    ) {
      ShadowLooper.idleMainLooper(1_000L, TimeUnit.MILLISECONDS)
      Thread.sleep(50L)
    }
    val reconnecting = vm.cloudRunState.value as? CloudBuildRunState.Polling
    assertNotNull("Monitor should stay in Polling on transient DNS error, got ${vm.cloudRunState.value}", reconnecting)
    assertNotNull("Expected reconnectingNote after DNS error", reconnecting!!.reconnectingNote)

    // Step until 3rd poll recovers and transitions to Completed(success)
    val doneDeadline = System.currentTimeMillis() + 10_000L
    while (vm.cloudRunState.value !is CloudBuildRunState.Completed && System.currentTimeMillis() < doneDeadline) {
      ShadowLooper.idleMainLooper(1_000L, TimeUnit.MILLISECONDS)
      Thread.sleep(50L)
    }
    val completed = vm.cloudRunState.value as? CloudBuildRunState.Completed
    assertNotNull("Expected Completed after recovery, got ${vm.cloudRunState.value}", completed)
    assertEquals("success", completed!!.conclusion)
  }

  @Test
  fun `dispatch auto-syncs workflow files with force_custom_loader and BuildMonitorCard labels failure log artifact`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    kotlinx.coroutines.runBlocking { com.example.data.CloudPreferences(app).clearAuthSession() }
    val committedPaths = CopyOnWriteArrayList<String>()
    var dispatchBody = ""
    val jsonMedia = "application/json; charset=utf-8".toMediaType()
    val loopbackDns = object : Dns { override fun lookup(hostname: String): List<InetAddress> = listOf(InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))) }

    val client =
      OkHttpClient.Builder()
        .dns(loopbackDns)
        .addInterceptor { chain ->
          val req = chain.request()
          val path = req.url.encodedPath
          val method = req.method
          if (method == "POST" && path.endsWith("/dispatches")) {
            val buf = okio.Buffer()
            req.body?.writeTo(buf)
            dispatchBody = buf.readUtf8()
          }
          val (code, body) =
            when {
              method == "GET" && path.contains("/contents/") -> 200 to """{"sha":"oldsha"}"""
              method == "PUT" && path.contains("/contents/") -> {
                committedPaths.add(path.substringAfter("/contents/"))
                200 to """{"content":{"sha":"newsha"}}"""
              }
              method == "POST" && path.endsWith("/dispatches") -> 204 to ""
              path.contains("/actions/workflows/") && path.endsWith("/runs") ->
                200 to """{"workflow_runs":[{"id":808,"run_number":12,"status":"completed","conclusion":"failure","html_url":"https://github.com/userio12/dex2c-cloud-workspace/actions/runs/808","created_at":"2026-11-13T04:10:00Z"}]}"""
              path.endsWith("/jobs") ->
                200 to """{"jobs":[{"name":"dex2c-apk","status":"completed","steps":[{"name":"Run Kirlif/d2c Compilation","status":"completed","conclusion":"failure"}]}]}"""
              path.endsWith("/artifacts") ->
                200 to """{"artifacts":[{"id":91,"name":"dex2c-build-log","size_in_bytes":2048,"archive_download_url":"https://api.github.com/repos/userio12/dex2c-cloud-workspace/actions/artifacts/91/zip","created_at":"2026-11-13T04:11:00Z","expired":false}]}"""
              else -> 200 to "{}"
            }

          Response.Builder()
            .request(req)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code == 204) "No Content" else "OK")
            .body(body.toResponseBody(jsonMedia))
            .build()
        }
        .build()

    val vm = Dex2cViewModel(app, GitHubApiClient(httpClient = client))
    vm.updateCloudSettings {
      it.copy(
        accessToken = "gho_test",
        githubUsername = "userio12",
        repoOwner = "userio12",
        repoName = "dex2c-cloud-workspace",
        workflowInitialized = true,
        notificationsEnabled = false,
      )
    }
    vm.updateConfig { it.copy(forceCustomLoader = true) }
    vm.updateCloudApkUrl("https://example.com/test.apk")
    vm.dispatchCloudDex2cBuild()

    val deadline = System.currentTimeMillis() + 10_000L
    while (vm.cloudRunState.value !is CloudBuildRunState.Completed && System.currentTimeMillis() < deadline) {
      ShadowLooper.idleMainLooper(1_000L, TimeUnit.MILLISECONDS)
      Thread.sleep(50L)
    }

    assertTrue(committedPaths.contains(".github/workflows/dex2c.yml"))
    assertTrue(committedPaths.contains("dcc.cfg"))
    assertTrue(committedPaths.contains("filter.txt"))
    assertTrue("Expected force_custom_loader in dispatch body: $dispatchBody", dispatchBody.contains("\"force_custom_loader\":\"true\""))

    val completed = vm.cloudRunState.value as? CloudBuildRunState.Completed
    assertNotNull(completed)
    assertEquals("failure", completed!!.conclusion)

    composeTestRule.setContent {
      Dex2cTheme {
        BuildMonitorCard(
          cloudSettings = vm.cloudSettings.value,
          cloudRunState = completed,
          isAutoPollEnabled = false,
          onToggleAutoPoll = {},
          onRefreshMonitor = {},
          onOpenBrowserUrl = {},
          onDownloadArtifact = {},
        )
      }
    }
    composeTestRule.onNodeWithText("Save Build Log").assertIsDisplayed()
  }
}
