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
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
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

  @Test
  fun `buildMonitor renders upload and download progress bars with bytes and updates live notification`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    org.robolectric.Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
    var runState by mutableStateOf<CloudBuildRunState>(
      CloudBuildRunState.UploadingInputs(
        message = "Uploading base.apk…",
        progressFraction = 0.62f,
        bytesUploaded = 1_572_864L,
        totalBytes = 2_621_440L,
      )
    )

    composeTestRule.setContent {
      Dex2cTheme {
        BuildMonitorCard(
          cloudSettings = CloudSettings(accessToken = "gho_abc", githubUsername = "userio12", workflowInitialized = true),
          cloudRunState = runState,
          isAutoPollEnabled = true,
          onToggleAutoPoll = {},
          onRefreshMonitor = {},
          onOpenBrowserUrl = {},
          onDownloadArtifact = {},
        )
      }
    }

    composeTestRule.onNodeWithTag("build_monitor_upload_progress").assertIsDisplayed()
    composeTestRule.onNodeWithTag("build_monitor_upload_bar").assertIsDisplayed()
    composeTestRule.onNodeWithTag("build_monitor_upload_pct").assertTextContains("62%")
    composeTestRule.onNodeWithTag("build_monitor_upload_bytes").assertTextContains("1.50 MB / 2.50 MB")

    val snapshot =
      CloudBuildRunState.Completed(
        runId = 909L,
        runNumber = 15,
        conclusion = "success",
        htmlUrl = "https://github.com/userio12/dex2c-cloud-workspace/actions/runs/909",
        artifacts = emptyList(),
        steps = listOf(GitHubWorkflowStep("Run Kirlif/d2c Compilation", "completed", "success")),
      )
    runState =
      CloudBuildRunState.SavingArtifact(
        label = "Downloading dex2c-signed-apk…",
        bytesDownloaded = 2_097_152L,
        totalBytes = 4_194_304L,
        progressFraction = 0.50f,
        completedSnapshot = snapshot,
      )
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("build_monitor_download_progress").assertIsDisplayed()
    composeTestRule.onNodeWithTag("build_monitor_download_bar").assertIsDisplayed()
    composeTestRule.onNodeWithTag("build_monitor_download_pct").assertTextContains("50%")
    composeTestRule.onNodeWithTag("build_monitor_download_bytes").assertTextContains("2.00 MB / 4.00 MB")

    val shadowNm = org.robolectric.Shadows.shadowOf(
      app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    )
    CloudBuildNotifier.ensureNotificationChannel(app)
    val progressChannel = (app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
      .getNotificationChannel(CloudBuildNotifier.PROGRESS_CHANNEL_ID)
    assertNotNull(progressChannel)
    assertEquals(NotificationManager.IMPORTANCE_LOW, progressChannel.importance)

    val liveDownload =
      CloudBuildNotifier.buildDownloadProgressNotification(
        artifactName = "dex2c-signed-apk",
        bytesDownloaded = 2_097_152L,
        totalBytes = 4_194_304L,
        progressFraction = 0.50f,
      )
    assertTrue(CloudBuildNotifier.updateLiveProgressNotification(app, liveDownload, runId = 909L))
    val postedLive = shadowNm.getNotification(CloudBuildNotifier.LIVE_PROGRESS_NOTIFICATION_ID)
    assertNotNull(postedLive)
    assertEquals(CloudBuildNotifier.PROGRESS_CHANNEL_ID, postedLive.channelId)

    assertTrue(CloudBuildNotifier.notifyDownloadCompleted(app, "dex2c-signed-output.apk"))
    assertNull(shadowNm.getNotification(CloudBuildNotifier.LIVE_PROGRESS_NOTIFICATION_ID))
    assertNotNull(shadowNm.getNotification(CloudBuildNotifier.DOWNLOAD_DONE_NOTIFICATION_ID))
  }

  @Test
  fun `classesScreen groups packages with tri-state checkbox and switches between tree and flat view`() {
    fun makeClass(idx: Int, pkg: String, simple: String, methodName: String): com.example.dex.DexClassInfo {
      val clsDesc = "L${pkg.replace('.', '/')}/$simple;"
      val m =
        com.example.dex.DexEncodedMethod(
          id = "m_$idx",
          methodIndex = idx,
          methodRef =
            com.example.dex.DexMethodRef(
              definingClass = clsDesc,
              proto = com.example.dex.DexProto("V", "V", emptyList()),
              name = methodName,
            ),
          accessFlags = 0x0001,
          isDirect = false,
          codeMeta =
            com.example.dex.DexCodeMeta(
              registersSize = 2,
              insSize = 1,
              outsSize = 0,
              triesSize = 0,
              insnsCount = 4,
              insnsOffsetBytes = 0,
            ),
        )
      return com.example.dex.DexClassInfo(
        classIndex = idx,
        descriptor = clsDesc,
        fullName = "$pkg.$simple",
        simpleName = simple,
        packageName = pkg,
        superclass = "Ljava/lang/Object;",
        interfaces = emptyList(),
        sourceFile = "$simple.kt",
        accessFlags = 0x0001,
        staticFields = emptyList(),
        instanceFields = emptyList(),
        directMethods = emptyList(),
        virtualMethods = listOf(m),
      )
    }

    val c1 = makeClass(1, "okhttp3", "Address", "url")
    val c2 = makeClass(2, "okhttp3", "Authenticator", "authenticate")
    val c3 = makeClass(3, "okio", "ForwardingSource", "read")
    val classes = listOf(c1, c2, c3)
    val dummyDex =
      com.example.dex.DexParsedFile(
        sourceLabel = "test.apk",
        entryName = "classes.dex",
        rawBytes = ByteArray(0),
        header =
          com.example.dex.DexHeaderInfo(
            magic = "dex\n035",
            version = "035",
            checksum = 1L,
            sha1Hex = "00",
            fileSize = 0,
            endianTag = 0x12345678L,
            stringIdsSize = 0,
            typeIdsSize = 0,
            protoIdsSize = 0,
            fieldIdsSize = 0,
            methodIdsSize = 0,
            classDefsSize = 3,
          ),
        strings = emptyList(),
        types = emptyList(),
        protos = emptyList(),
        fields = emptyList(),
        methods = emptyList(),
        classes = classes,
      )
    val readyState = com.example.ui.DexLoadState.Ready("test.apk", emptyList(), "classes.dex", dummyDex)
    var selectedIds by mutableStateOf(setOf("m_1"))
    var packageToggleInvoked = false

    composeTestRule.setContent {
      Dex2cTheme {
        com.example.ui.screens.ClassesScreen(
          loadState = readyState,
          classes = classes,
          filterState = com.example.ui.ClassFilterState(),
          selectedMethodIds = selectedIds,
          allowInit = false,
          onSearchChange = {},
          onToggleHideFramework = {},
          onToggleOnlyBytecode = {},
          onToggleOnlySelected = {},
          onTogglePackageSelection = { pkgClasses ->
            packageToggleInvoked = true
            val ids = pkgClasses.flatMap { it.allMethods }.map { it.id }.toSet()
            selectedIds = if (ids.all { it in selectedIds }) selectedIds - ids else selectedIds + ids
          },
          onToggleClassSelection = {},
          onToggleMethodSelection = {},
          onInspectMethod = { _, _ -> },
          onInspectSelfApk = {},
        )
      }
    }

    // Initially in Tree mode: package rows shown, class rows collapsed
    composeTestRule.onNodeWithTag("pkg_row_okhttp3").assertIsDisplayed()
    composeTestRule.onNodeWithTag("pkg_row_okio").assertIsDisplayed()
    composeTestRule.onNodeWithTag("pkg_checkbox_okhttp3").assert(
      androidx.compose.ui.test.SemanticsMatcher.expectValue(
        androidx.compose.ui.semantics.SemanticsProperties.ToggleableState,
        androidx.compose.ui.state.ToggleableState.Indeterminate,
      )
    )

    // Tapping package checkbox selects all methods in okhttp3
    composeTestRule.onNodeWithTag("pkg_checkbox_okhttp3").performClick()
    composeTestRule.waitForIdle()
    assertTrue(packageToggleInvoked)
    assertTrue("m_1" in selectedIds && "m_2" in selectedIds)
    composeTestRule.onNodeWithTag("pkg_checkbox_okhttp3").assert(
      androidx.compose.ui.test.SemanticsMatcher.expectValue(
        androidx.compose.ui.semantics.SemanticsProperties.ToggleableState,
        androidx.compose.ui.state.ToggleableState.On,
      )
    )

    // Expand okhttp3 folder -> reveals Address and Authenticator
    composeTestRule.onNodeWithTag("pkg_row_okhttp3").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("class_row_1").assertIsDisplayed()
    composeTestRule.onNodeWithTag("class_row_2").assertIsDisplayed()

    // Switch to Flat view -> shows ForwardingSource without folder nesting
    composeTestRule.onNodeWithTag("chip_view_mode_toggle").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("class_row_3").assertIsDisplayed()
  }

  @Test
  fun `classesScreen sort dropdown and extra filter tray update filter state`() {
    var filterState by mutableStateOf(com.example.ui.ClassFilterState())
    val cls =
      com.example.dex.DexClassInfo(
        classIndex = 10,
        descriptor = "Lcom/demo/MainApp;",
        fullName = "com.demo.MainApp",
        simpleName = "MainApp",
        packageName = "com.demo",
        superclass = "Ljava/lang/Object;",
        interfaces = emptyList(),
        sourceFile = "MainApp.kt",
        accessFlags = 0x0001,
        staticFields = emptyList(),
        instanceFields = emptyList(),
        directMethods = emptyList(),
        virtualMethods = emptyList(),
      )
    val dummyDex =
      com.example.dex.DexParsedFile(
        sourceLabel = "app.apk",
        entryName = "classes.dex",
        rawBytes = ByteArray(0),
        header =
          com.example.dex.DexHeaderInfo(
            magic = "dex\n035",
            version = "035",
            checksum = 1L,
            sha1Hex = "00",
            fileSize = 0,
            endianTag = 0x12345678L,
            stringIdsSize = 0,
            typeIdsSize = 0,
            protoIdsSize = 0,
            fieldIdsSize = 0,
            methodIdsSize = 0,
            classDefsSize = 1,
          ),
        strings = emptyList(),
        types = emptyList(),
        protos = emptyList(),
        fields = emptyList(),
        methods = emptyList(),
        classes = listOf(cls),
      )

    composeTestRule.setContent {
      Dex2cTheme {
        com.example.ui.screens.ClassesScreen(
          loadState = com.example.ui.DexLoadState.Ready("app.apk", emptyList(), "classes.dex", dummyDex),
          classes = listOf(cls),
          filterState = filterState,
          selectedMethodIds = emptySet(),
          allowInit = false,
          onSearchChange = {},
          onToggleHideFramework = {},
          onToggleOnlyBytecode = {},
          onToggleOnlySelected = {},
          onSortOrderChange = { filterState = filterState.copy(sortOrder = it) },
          onClassTypeFilterChange = { filterState = filterState.copy(classTypeFilter = it) },
          onToggleHideInnerClasses = { filterState = filterState.copy(hideInnerClasses = !filterState.hideInnerClasses) },
          onMethodScopeFilterChange = { filterState = filterState.copy(methodScopeFilter = it) },
          onToggleNonEmptyPackages = { filterState = filterState.copy(nonEmptyPackagesOnly = !filterState.nonEmptyPackagesOnly) },
          onResetExtraFilters = {
            filterState =
              filterState.copy(
                classTypeFilter = com.example.ui.ClassTypeFilter.ALL,
                hideInnerClasses = false,
                methodScopeFilter = com.example.ui.MethodScopeFilter.ALL,
                nonEmptyPackagesOnly = false,
              )
          },
          onToggleClassSelection = {},
          onToggleMethodSelection = {},
          onInspectMethod = { _, _ -> },
          onInspectSelfApk = {},
        )
      }
    }

    // Open sort menu and select SELECTED_FIRST
    composeTestRule.onNodeWithTag("chip_class_sort").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("sort_option_SELECTED_FIRST").performClick()
    composeTestRule.waitForIdle()
    assertEquals(com.example.ui.ClassSortOrder.SELECTED_FIRST, filterState.sortOrder)

    // Open extra filter tray and toggle Concrete + Static
    composeTestRule.onNodeWithTag("chip_extra_filters").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("filter_class_type_CONCRETE").performClick()
    composeTestRule.onNodeWithTag("filter_method_scope_STATIC_ONLY").performClick()
    composeTestRule.waitForIdle()
    assertEquals(2, filterState.extraFilterCount)
    composeTestRule.onNodeWithTag("chip_extra_filters").assertTextContains("Filters (2)")

    // Reset extra filters clears badge
    composeTestRule.onNodeWithTag("filter_reset_extra_btn").performClick()
    composeTestRule.waitForIdle()
    assertEquals(0, filterState.extraFilterCount)
  }

  @Test
  fun `apkPickerSheet filters installed apps by user or system scope and sorts recent items`() {
    var activeTab by mutableStateOf(com.example.apk.PickerTab.APPS)
    val userApp =
      com.example.apk.InstalledAppItem(
        packageName = "com.user.alpha",
        appLabel = "Alpha App",
        versionName = "1.0",
        versionCode = 1L,
        minSdk = 26,
        targetSdk = 34,
        apkPath = "/data/app/alpha.apk",
        apkSizeBytes = 10_000_000L,
        iconBitmap = null,
        isSystemApp = false,
        lastUpdateTimeMillis = 200L,
      )
    val sysApp =
      com.example.apk.InstalledAppItem(
        packageName = "com.android.sys",
        appLabel = "System Core",
        versionName = "14",
        versionCode = 34L,
        minSdk = 34,
        targetSdk = 34,
        apkPath = "/system/app/sys.apk",
        apkSizeBytes = 25_000_000L,
        iconBitmap = null,
        isSystemApp = true,
        lastUpdateTimeMillis = 100L,
      )
    val recentStorage =
      com.example.apk.RecentBinaryItem(
        id = "rec_storage",
        displayName = "target.apk",
        sourceType = com.example.apk.BinarySourceType.SAF_URI,
        uriOrPath = "content://doc/1",
        packageName = null,
        byteSize = 5_000_000L,
        lastOpenedMillis = 1000L,
      )
    val recentApp =
      com.example.apk.RecentBinaryItem(
        id = "rec_app",
        displayName = "Alpha App",
        sourceType = com.example.apk.BinarySourceType.INSTALLED_APK,
        uriOrPath = "/data/app/alpha.apk",
        packageName = "com.user.alpha",
        byteSize = 10_000_000L,
        lastOpenedMillis = 2000L,
      )

    composeTestRule.setContent {
      Dex2cTheme {
        com.example.ui.components.ApkPickerSheet(
          activeTab = activeTab,
          installedApps = listOf(userApp, sysApp),
          isLoadingApps = false,
          appSearchQuery = "",
          recentFiles = listOf(recentStorage, recentApp),
          onSelectTab = { activeTab = it },
          onBrowseStorage = {},
          onInspectSelfApk = {},
          onAppSearchChange = {},
          onSelectInstalledApp = {},
          onSelectRecent = {},
          onRemoveRecent = {},
          onClearRecent = {},
          onDismiss = {},
        )
      }
    }

    composeTestRule.onNodeWithTag("picker_apps_list")
      .performScrollToNode(androidx.compose.ui.test.hasTestTag("installed_app_com.user.alpha"))
    composeTestRule.onNodeWithTag("installed_app_com.user.alpha").assertIsDisplayed()
    composeTestRule.onNodeWithTag("picker_apps_list")
      .performScrollToNode(androidx.compose.ui.test.hasTestTag("installed_app_com.android.sys"))
    composeTestRule.onNodeWithTag("installed_app_com.android.sys").assertIsDisplayed()

    // Filter to User Only -> hides system app
    composeTestRule.onNodeWithTag("picker_apps_filter_row")
      .performScrollToNode(androidx.compose.ui.test.hasTestTag("picker_app_scope_USER_ONLY"))
    composeTestRule.onNodeWithTag("picker_app_scope_USER_ONLY").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("installed_app_com.user.alpha").assertIsDisplayed()
    composeTestRule.onNodeWithTag("installed_app_com.android.sys").assertDoesNotExist()

    // Filter to System Only -> shows system app only
    composeTestRule.onNodeWithTag("picker_apps_filter_row")
      .performScrollToNode(androidx.compose.ui.test.hasTestTag("picker_app_scope_SYSTEM_ONLY"))
    composeTestRule.onNodeWithTag("picker_app_scope_SYSTEM_ONLY").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("installed_app_com.android.sys").assertIsDisplayed()
    composeTestRule.onNodeWithTag("installed_app_com.user.alpha").assertDoesNotExist()

    // Switch back to All Apps and sort by Largest
    composeTestRule.onNodeWithTag("picker_apps_filter_row")
      .performScrollToNode(androidx.compose.ui.test.hasTestTag("picker_app_scope_ALL"))
    composeTestRule.onNodeWithTag("picker_app_scope_ALL").performClick()
    composeTestRule.onNodeWithTag("picker_apps_filter_row")
      .performScrollToNode(androidx.compose.ui.test.hasTestTag("picker_apps_sort_chip"))
    composeTestRule.onNodeWithTag("picker_apps_sort_chip").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("picker_sort_SIZE_DESC").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("picker_apps_sort_chip").assertTextContains("Sort: Largest")

    // Switch to Recent tab and filter to Storage only
    composeTestRule.onNodeWithTag("picker_tab_recent").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("picker_recent_list")
      .performScrollToNode(androidx.compose.ui.test.hasTestTag("recent_item_rec_storage"))
    composeTestRule.onNodeWithTag("recent_item_rec_storage").assertIsDisplayed()
    composeTestRule.onNodeWithTag("picker_recent_list")
      .performScrollToNode(androidx.compose.ui.test.hasTestTag("recent_item_rec_app"))
    composeTestRule.onNodeWithTag("recent_item_rec_app").assertIsDisplayed()

    composeTestRule.onNodeWithTag("picker_recent_filter_row")
      .performScrollToNode(androidx.compose.ui.test.hasTestTag("picker_recent_filter_STORAGE"))
    composeTestRule.onNodeWithTag("picker_recent_filter_STORAGE").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("recent_item_rec_storage").assertIsDisplayed()
    composeTestRule.onNodeWithTag("recent_item_rec_app").assertDoesNotExist()
  }

  @Test
  fun `buildHubScreen merges pipeline and output tabs with queued badge and filter auto-switch`() {
    var activeTab by mutableStateOf(com.example.ui.screens.BuildHubTab.PIPELINE)
    var appliedFilters by mutableStateOf(false)

    composeTestRule.setContent {
      Dex2cTheme {
        com.example.ui.screens.BuildHubScreen(
          activeTab = activeTab,
          onSelectTab = { activeTab = it },
          selectedMethodCount = 4,
          config = com.example.transpiler.TranspilerConfig(),
          hasLoadedDex = true,
          filterMatchSummary = null,
          onUpdateConfig = {},
          onImportFilterTxt = {},
          onImportDccCfg = {},
          onApplyFilterRules = { appliedFilters = true },
          bundle = null,
          isTranspiling = false,
          onCopyFile = {},
          onExportZip = {},
          onGoToClasses = {},
        )
      }
    }

    // Both segmented tabs render, with badge count on Output
    composeTestRule.onNodeWithTag("build_tab_pipeline").assertIsDisplayed()
    composeTestRule.onNodeWithTag("build_tab_output")
      .assertIsDisplayed()
      .assertTextContains("4", substring = true)

    // Pipeline content starts visible
    composeTestRule.onNodeWithTag("import_dcc_cfg_button").assertIsDisplayed()

    // Switch directly to Output tab via segmented control
    composeTestRule.onNodeWithTag("build_tab_output").performClick()
    composeTestRule.waitForIdle()
    assertEquals(com.example.ui.screens.BuildHubTab.OUTPUT, activeTab)
    composeTestRule.onNodeWithTag("export_go_classes_btn").assertIsDisplayed()

    // Switch back to Pipeline and trigger Apply Filter Rules -> switches in-place to Output
    composeTestRule.onNodeWithTag("build_tab_pipeline").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("pipeline_config_list")
      .performScrollToNode(androidx.compose.ui.test.hasTestTag("apply_filter_rules_button"))
    composeTestRule.onNodeWithTag("apply_filter_rules_button").performClick()
    composeTestRule.waitForIdle()
    assertTrue(appliedFilters)
    assertEquals(com.example.ui.screens.BuildHubTab.OUTPUT, activeTab)
    composeTestRule.onNodeWithTag("export_go_classes_btn").assertIsDisplayed()
  }

  @Test
  fun `dex2cApp displays 4 bottom navigation tabs and opens Build hub`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    kotlinx.coroutines.runBlocking {
      com.example.data.AppPreferences(app).saveSettings(
        AppSettings(hasCompletedOnboarding = false)
      )
    }
    val vm = Dex2cViewModel(app)
    vm.completeOnboarding()

    composeTestRule.setContent {
      Dex2cTheme {
        Dex2cApp(viewModel = vm)
      }
    }

    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      vm.appSettingsHydrated.value &&
        vm.appSettings.value.hasCompletedOnboarding &&
        runCatching {
          composeTestRule.onNodeWithTag("nav_workbench").assertExists()
          true
        }.getOrDefault(false)
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("nav_workbench").assertIsDisplayed()
    composeTestRule.onNodeWithTag("nav_classes").assertIsDisplayed()
    composeTestRule.onNodeWithTag("nav_build").assertIsDisplayed()
    composeTestRule.onNodeWithTag("nav_cloud").assertIsDisplayed()
    composeTestRule.onNodeWithTag("nav_config").assertDoesNotExist()
    composeTestRule.onNodeWithTag("nav_export").assertDoesNotExist()

    // Tapping Build tab shows merged Pipeline / Output segmented control
    composeTestRule.onNodeWithTag("nav_build").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("build_tab_pipeline").assertIsDisplayed()
    composeTestRule.onNodeWithTag("build_tab_output").assertIsDisplayed()
  }

  @Test
  fun `apkPreviewSheet supports checking multiple DEX entries and Select All`() {
    val entries =
      listOf(
        com.example.dex.ApkDexEntry("classes.dex", 1_200_000),
        com.example.dex.ApkDexEntry("classes2.dex", 850_000),
        com.example.dex.ApkDexEntry("classes3.dex", 320_000),
      )
    val preview =
      com.example.apk.ApkStagingPreview(
        sourceLabel = "sample.apk",
        sourceRef = "content://sample.apk",
        sourceType = com.example.apk.BinarySourceType.SAF_URI,
        packageName = "com.example.sample",
        appLabel = "Sample App",
        versionName = "1.0",
        versionCode = 1L,
        minSdk = 26,
        targetSdk = 34,
        totalByteSize = 3_000_000L,
        nativeAbis = listOf("arm64-v8a"),
        dexEntries = entries,
        dexByteMap = entries.associate { it.entryName to ByteArray(4) },
        isSingleDex = false,
        iconBitmap = null,
      )
    var staging by mutableStateOf(
      com.example.ui.ApkStagingState.Ready(preview, setOf("classes.dex"))
    )
    var confirmed by mutableStateOf(false)

    composeTestRule.setContent {
      Dex2cTheme {
        com.example.ui.components.ApkPreviewSheet(
          stagingState = staging,
          onSelectDexEntry = { entry ->
            val current = staging.selectedEntryNames
            val next = if (entry in current) current - entry else current + entry
            staging = staging.copy(selectedEntryNames = next)
          },
          onSelectAllDexEntries = {
            staging = staging.copy(selectedEntryNames = entries.map { it.entryName }.toSet())
          },
          onSelectPrimaryDexEntry = {
            staging = staging.copy(selectedEntryNames = setOf("classes.dex"))
          },
          onConfirmLoad = { confirmed = true },
          onDismiss = {},
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("preview_load_dex_button").assertTextContains("Load Selected DEX")

    // Check classes2.dex as well -> button updates to Load 2 DEXes
    composeTestRule.onNodeWithTag("preview_dex_list")
      .performScrollToNode(androidx.compose.ui.test.hasTestTag("preview_dex_classes2.dex"))
    composeTestRule.onNodeWithTag("preview_dex_checkbox_classes2.dex").performClick()
    composeTestRule.waitForIdle()
    assertEquals(setOf("classes.dex", "classes2.dex"), staging.selectedEntryNames)
    composeTestRule.onNodeWithTag("preview_load_dex_button").assertTextContains("Load 2 DEXes")

    // Tap Select All -> selects all 3 DEX entries
    composeTestRule.onNodeWithTag("preview_dex_list")
      .performScrollToNode(androidx.compose.ui.test.hasTestTag("preview_select_all_dex_btn"))
    composeTestRule.onNodeWithTag("preview_select_all_dex_btn").performClick()
    composeTestRule.waitForIdle()
    assertEquals(setOf("classes.dex", "classes2.dex", "classes3.dex"), staging.selectedEntryNames)
    composeTestRule.onNodeWithTag("preview_load_dex_button").assertTextContains("Load 3 DEXes")

    composeTestRule.onNodeWithTag("preview_load_dex_button").performClick()
    composeTestRule.waitForIdle()
    assertTrue(confirmed)
  }

  @Test
  fun `classesScreen merges multi-DEX classes and filters by DEX source chip`() {
    val proto = com.example.dex.DexProto("V", "V", emptyList())
    val m1 =
      com.example.dex.DexEncodedMethod(
        id = "classes.dex::Lcom/app/Main;#0",
        methodIndex = 0,
        methodRef = com.example.dex.DexMethodRef("Lcom/app/Main;", proto, "runPrimary"),
        accessFlags = 0x0001,
        isDirect = false,
        codeMeta = com.example.dex.DexCodeMeta(1, 1, 0, 0, 2, 0),
        sourceEntryName = "classes.dex",
      )
    val c1 =
      com.example.dex.DexClassInfo(
        classIndex = 0,
        descriptor = "Lcom/app/Main;",
        fullName = "com.app.Main",
        simpleName = "Main",
        packageName = "com.app",
        superclass = "Ljava/lang/Object;",
        interfaces = emptyList(),
        sourceFile = "Main.kt",
        accessFlags = 0x0001,
        staticFields = emptyList(),
        instanceFields = emptyList(),
        directMethods = emptyList(),
        virtualMethods = listOf(m1),
        sourceEntryName = "classes.dex",
      )
    val m2 =
      com.example.dex.DexEncodedMethod(
        id = "classes2.dex::Lcom/app/Helper;#0",
        methodIndex = 0,
        methodRef = com.example.dex.DexMethodRef("Lcom/app/Helper;", proto, "runSecondary"),
        accessFlags = 0x0001,
        isDirect = false,
        codeMeta = com.example.dex.DexCodeMeta(1, 1, 0, 0, 2, 0),
        sourceEntryName = "classes2.dex",
      )
    val c2 =
      com.example.dex.DexClassInfo(
        classIndex = 0,
        descriptor = "Lcom/app/Helper;",
        fullName = "com.app.Helper",
        simpleName = "Helper",
        packageName = "com.app",
        superclass = "Ljava/lang/Object;",
        interfaces = emptyList(),
        sourceFile = "Helper.kt",
        accessFlags = 0x0001,
        staticFields = emptyList(),
        instanceFields = emptyList(),
        directMethods = emptyList(),
        virtualMethods = listOf(m2),
        sourceEntryName = "classes2.dex",
      )
    val hdr =
      com.example.dex.DexHeaderInfo("dex\n035", "035", 1L, "abc", 100, 0x12345678L, 1, 1, 1, 1, 1, 1)
    val dex1 =
      com.example.dex.DexParsedFile(
        "app.apk", "classes.dex", ByteArray(0), hdr, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), listOf(c1)
      )
    val dex2 =
      com.example.dex.DexParsedFile(
        "app.apk", "classes2.dex", ByteArray(0), hdr, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), listOf(c2)
      )
    val readyState =
      com.example.ui.DexLoadState.Ready(
        sourceLabel = "app.apk",
        dexEntries = listOf(com.example.dex.ApkDexEntry("classes.dex", 100), com.example.dex.ApkDexEntry("classes2.dex", 100)),
        activeEntryName = "classes.dex",
        parsedDex = dex1,
        loadedDexes = linkedMapOf("classes.dex" to dex1, "classes2.dex" to dex2),
      )

    var filterState by mutableStateOf(com.example.ui.ClassFilterState())

    composeTestRule.setContent {
      val visibleClasses =
        readyState.mergedClasses.filter { cls ->
          filterState.dexSourceFilter == null || cls.sourceEntryName == filterState.dexSourceFilter
        }
      Dex2cTheme {
        com.example.ui.screens.ClassesScreen(
          loadState = readyState,
          classes = visibleClasses,
          filterState = filterState,
          selectedMethodIds = emptySet(),
          allowInit = false,
          onSearchChange = {},
          onToggleHideFramework = {},
          onToggleOnlyBytecode = {},
          onToggleOnlySelected = {},
          onDexSourceFilterChange = { filterState = filterState.copy(dexSourceFilter = it) },
          onToggleClassSelection = {},
          onToggleMethodSelection = {},
          onInspectMethod = { _, _ -> },
          onInspectSelfApk = {},
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("chip_dex_source").assertIsDisplayed().assertTextContains("DEX: All (2)")

    // Expand merged com.app package -> contains classes from BOTH DEXes (Main + Helper)
    composeTestRule.onNodeWithTag("pkg_row_com.app").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("class_row_0").assertIsDisplayed()
    composeTestRule.onNodeWithTag("class_row_classes2.dex_0").assertIsDisplayed()

    // Filter to classes2.dex only -> keeps Helper, hides Main
    composeTestRule.onNodeWithTag("chip_dex_source").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("dex_source_classes2.dex").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("chip_dex_source").assertTextContains("DEX: classes2.dex")
    composeTestRule.onNodeWithTag("class_row_classes2.dex_0").assertIsDisplayed()
    composeTestRule.onNodeWithTag("class_row_0").assertDoesNotExist()
  }
}
