package com.dex2c.studio

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
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.core.app.ApplicationProvider
import com.dex2c.studio.cloud.CloudBuildNotifier
import com.dex2c.studio.cloud.CloudBuildRunState
import com.dex2c.studio.cloud.CloudErrorAction
import com.dex2c.studio.cloud.CloudSettings
import com.dex2c.studio.cloud.DeviceAuthState
import com.dex2c.studio.cloud.GITHUB_DEVICE_VERIFICATION_URL
import com.dex2c.studio.cloud.GitHubApiClient
import com.dex2c.studio.cloud.GitHubWorkflowStep
import com.dex2c.studio.cloud.RepoSetupState
import com.dex2c.studio.data.AppSettings
import com.dex2c.studio.ui.Dex2cViewModel
import com.dex2c.studio.ui.components.BuildMonitorCard
import com.dex2c.studio.ui.screens.OnboardingScreen
import com.dex2c.studio.ui.theme.Dex2cTheme
import kotlinx.coroutines.flow.first
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
import org.junit.Assert.assertFalse
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
  fun `onboarding shows step header progress and swipes across Intro, About, GitHub, Theme, Alerts in order`() {
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

    composeTestRule.onNodeWithTag("onboarding_step_header").assertIsDisplayed().assertTextContains("Step 1 of 5", substring = true)
    composeTestRule.onNodeWithTag("onboarding_step_progress").assertIsDisplayed()
    composeTestRule.onNodeWithTag("onboarding_page_dots").assertIsDisplayed()
    repeat(5) { idx ->
      composeTestRule.onNodeWithTag("onboarding_page_dot_$idx").assertIsDisplayed()
    }
    composeTestRule.onNodeWithText("Dex2C Studio").assertIsDisplayed()

    // Tap dot 1 -> About screen
    composeTestRule.onNodeWithTag("onboarding_page_dot_1").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("onboarding_step_header").assertTextContains("Step 2 of 5", substring = true)
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
    composeTestRule.onNodeWithTag("onboarding_step_header").assertTextContains("Step 4 of 5", substring = true)
    composeTestRule.onNodeWithText("Theme & Palette").performScrollTo().assertIsDisplayed()

    composeTestRule.onNodeWithTag("onboarding_page_dot_2").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("onboarding_step_header").assertTextContains("Step 3 of 5", substring = true)
    composeTestRule.onNodeWithText("GitHub Cloud CI").performScrollTo().assertIsDisplayed()

    composeTestRule.onNodeWithTag("onboarding_pager").performTouchInput { swipeLeft() }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Theme & Palette").performScrollTo().assertIsDisplayed()

    composeTestRule.onNodeWithTag("onboarding_pager").performTouchInput { swipeLeft() }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("onboarding_step_header").assertTextContains("Step 5 of 5", substring = true)
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
    kotlinx.coroutines.runBlocking { com.dex2c.studio.data.CloudPreferences(app).clearAuthSession() }
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
    kotlinx.coroutines.runBlocking { com.dex2c.studio.data.CloudPreferences(app).clearAuthSession() }
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
    kotlinx.coroutines.runBlocking { com.dex2c.studio.data.CloudPreferences(app).clearAuthSession() }
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
    fun makeClass(idx: Int, pkg: String, simple: String, methodName: String): com.dex2c.studio.dex.DexClassInfo {
      val clsDesc = "L${pkg.replace('.', '/')}/$simple;"
      val m =
        com.dex2c.studio.dex.DexEncodedMethod(
          id = "m_$idx",
          methodIndex = idx,
          methodRef =
            com.dex2c.studio.dex.DexMethodRef(
              definingClass = clsDesc,
              proto = com.dex2c.studio.dex.DexProto("V", "V", emptyList()),
              name = methodName,
            ),
          accessFlags = 0x0001,
          isDirect = false,
          codeMeta =
            com.dex2c.studio.dex.DexCodeMeta(
              registersSize = 2,
              insSize = 1,
              outsSize = 0,
              triesSize = 0,
              insnsCount = 4,
              insnsOffsetBytes = 0,
            ),
        )
      return com.dex2c.studio.dex.DexClassInfo(
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
      com.dex2c.studio.dex.DexParsedFile(
        sourceLabel = "test.apk",
        entryName = "classes.dex",
        rawBytes = ByteArray(0),
        header =
          com.dex2c.studio.dex.DexHeaderInfo(
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
    val readyState = com.dex2c.studio.ui.DexLoadState.Ready("test.apk", emptyList(), "classes.dex", dummyDex)
    var selectedIds by mutableStateOf(setOf("m_1"))
    var packageToggleInvoked = false

    composeTestRule.setContent {
      Dex2cTheme {
        com.dex2c.studio.ui.screens.ClassesScreen(
          loadState = readyState,
          classes = classes,
          filterState = com.dex2c.studio.ui.ClassFilterState(),
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

    // Initially in Tree mode: package rows shown cleanly without checkboxes until Select Mode is toggled
    composeTestRule.onNodeWithTag("pkg_row_okhttp3").assertIsDisplayed()
    composeTestRule.onNodeWithTag("pkg_row_okio").assertIsDisplayed()
    composeTestRule.onNodeWithTag("pkg_checkbox_okhttp3").assertDoesNotExist()
    composeTestRule.onNodeWithTag("chip_select_mode").performClick()
    composeTestRule.waitForIdle()
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
    var filterState by mutableStateOf(com.dex2c.studio.ui.ClassFilterState())
    val cls =
      com.dex2c.studio.dex.DexClassInfo(
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
      com.dex2c.studio.dex.DexParsedFile(
        sourceLabel = "app.apk",
        entryName = "classes.dex",
        rawBytes = ByteArray(0),
        header =
          com.dex2c.studio.dex.DexHeaderInfo(
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
        com.dex2c.studio.ui.screens.ClassesScreen(
          loadState = com.dex2c.studio.ui.DexLoadState.Ready("app.apk", emptyList(), "classes.dex", dummyDex),
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
                classTypeFilter = com.dex2c.studio.ui.ClassTypeFilter.ALL,
                hideInnerClasses = false,
                methodScopeFilter = com.dex2c.studio.ui.MethodScopeFilter.ALL,
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
    assertEquals(com.dex2c.studio.ui.ClassSortOrder.SELECTED_FIRST, filterState.sortOrder)

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
    var activeTab by mutableStateOf(com.dex2c.studio.apk.PickerTab.APPS)
    val userApp =
      com.dex2c.studio.apk.InstalledAppItem(
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
      com.dex2c.studio.apk.InstalledAppItem(
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
      com.dex2c.studio.apk.RecentBinaryItem(
        id = "rec_storage",
        displayName = "target.apk",
        sourceType = com.dex2c.studio.apk.BinarySourceType.SAF_URI,
        uriOrPath = "content://doc/1",
        packageName = null,
        byteSize = 5_000_000L,
        lastOpenedMillis = 1000L,
      )
    val recentApp =
      com.dex2c.studio.apk.RecentBinaryItem(
        id = "rec_app",
        displayName = "Alpha App",
        sourceType = com.dex2c.studio.apk.BinarySourceType.INSTALLED_APK,
        uriOrPath = "/data/app/alpha.apk",
        packageName = "com.user.alpha",
        byteSize = 10_000_000L,
        lastOpenedMillis = 2000L,
      )

    composeTestRule.setContent {
      Dex2cTheme {
        com.dex2c.studio.ui.components.ApkPickerSheet(
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
    var activeTab by mutableStateOf(com.dex2c.studio.ui.screens.BuildHubTab.PIPELINE)
    var appliedFilters by mutableStateOf(false)

    composeTestRule.setContent {
      Dex2cTheme {
        com.dex2c.studio.ui.screens.BuildHubScreen(
          activeTab = activeTab,
          onSelectTab = { activeTab = it },
          selectedMethodCount = 4,
          config = com.dex2c.studio.transpiler.TranspilerConfig(),
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
    assertEquals(com.dex2c.studio.ui.screens.BuildHubTab.OUTPUT, activeTab)
    composeTestRule.onNodeWithTag("export_go_classes_btn").assertIsDisplayed()

    // Switch back to Pipeline and verify category pills fit without scrolling, then trigger Apply Filters -> switches in-place to Output
    composeTestRule.onNodeWithTag("build_tab_pipeline").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("pipeline_tab_LOADER").assertIsDisplayed()
    composeTestRule.onNodeWithTag("pipeline_tab_NDK_PASSES").assertIsDisplayed()
    composeTestRule.onNodeWithTag("pipeline_tab_FILTERS").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("apply_filter_rules_button").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()
    assertTrue(appliedFilters)
    assertEquals(com.dex2c.studio.ui.screens.BuildHubTab.OUTPUT, activeTab)
    composeTestRule.onNodeWithTag("export_go_classes_btn").assertIsDisplayed()
  }

  @Test
  fun `dex2cApp displays 4 bottom navigation tabs and opens Build hub`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val vm = Dex2cViewModel(app)
    vm.completeOnboarding()
    ShadowLooper.idleMainLooper()

    composeTestRule.setContent {
      Dex2cTheme {
        Dex2cApp(viewModel = vm)
      }
    }

    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      ShadowLooper.idleMainLooper()
      vm.appSettingsHydrated.value && vm.appSettings.value.hasCompletedOnboarding
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("nav_home").assertIsDisplayed()
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
        com.dex2c.studio.dex.ApkDexEntry("classes.dex", 1_200_000),
        com.dex2c.studio.dex.ApkDexEntry("classes2.dex", 850_000),
        com.dex2c.studio.dex.ApkDexEntry("classes3.dex", 320_000),
      )
    val preview =
      com.dex2c.studio.apk.ApkStagingPreview(
        sourceLabel = "sample.apk",
        sourceRef = "content://sample.apk",
        sourceType = com.dex2c.studio.apk.BinarySourceType.SAF_URI,
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
      com.dex2c.studio.ui.ApkStagingState.Ready(preview, setOf("classes.dex"))
    )
    var confirmed by mutableStateOf(false)

    composeTestRule.setContent {
      Dex2cTheme {
        com.dex2c.studio.ui.components.ApkPreviewSheet(
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
    val proto = com.dex2c.studio.dex.DexProto("V", "V", emptyList())
    val m1 =
      com.dex2c.studio.dex.DexEncodedMethod(
        id = "classes.dex::Lcom/app/Main;#0",
        methodIndex = 0,
        methodRef = com.dex2c.studio.dex.DexMethodRef("Lcom/app/Main;", proto, "runPrimary"),
        accessFlags = 0x0001,
        isDirect = false,
        codeMeta = com.dex2c.studio.dex.DexCodeMeta(1, 1, 0, 0, 2, 0),
        sourceEntryName = "classes.dex",
      )
    val c1 =
      com.dex2c.studio.dex.DexClassInfo(
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
      com.dex2c.studio.dex.DexEncodedMethod(
        id = "classes2.dex::Lcom/app/Helper;#0",
        methodIndex = 0,
        methodRef = com.dex2c.studio.dex.DexMethodRef("Lcom/app/Helper;", proto, "runSecondary"),
        accessFlags = 0x0001,
        isDirect = false,
        codeMeta = com.dex2c.studio.dex.DexCodeMeta(1, 1, 0, 0, 2, 0),
        sourceEntryName = "classes2.dex",
      )
    val c2 =
      com.dex2c.studio.dex.DexClassInfo(
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
      com.dex2c.studio.dex.DexHeaderInfo("dex\n035", "035", 1L, "abc", 100, 0x12345678L, 1, 1, 1, 1, 1, 1)
    val dex1 =
      com.dex2c.studio.dex.DexParsedFile(
        "app.apk", "classes.dex", ByteArray(0), hdr, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), listOf(c1)
      )
    val dex2 =
      com.dex2c.studio.dex.DexParsedFile(
        "app.apk", "classes2.dex", ByteArray(0), hdr, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), listOf(c2)
      )
    val readyState =
      com.dex2c.studio.ui.DexLoadState.Ready(
        sourceLabel = "app.apk",
        dexEntries = listOf(com.dex2c.studio.dex.ApkDexEntry("classes.dex", 100), com.dex2c.studio.dex.ApkDexEntry("classes2.dex", 100)),
        activeEntryName = "classes.dex",
        parsedDex = dex1,
        loadedDexes = linkedMapOf("classes.dex" to dex1, "classes2.dex" to dex2),
      )

    var filterState by mutableStateOf(com.dex2c.studio.ui.ClassFilterState())

    composeTestRule.setContent {
      val visibleClasses =
        readyState.mergedClasses.filter { cls ->
          filterState.dexSourceFilter == null || cls.sourceEntryName == filterState.dexSourceFilter
        }
      Dex2cTheme {
        com.dex2c.studio.ui.screens.ClassesScreen(
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

  @Test
  fun `buildHubScreen triggers onEnsureBundleBuilt on Output tab and renders modular chunks and truncated preview banner`() {
    var activeTab by mutableStateOf(com.dex2c.studio.ui.screens.BuildHubTab.PIPELINE)
    var ensureBuildCalls by mutableStateOf(0)

    val coordinator =
      com.dex2c.studio.transpiler.GeneratedFile(
        fileName = "dex2c_bridge.cpp",
        zipPath = "jni/dex2c_bridge.cpp",
        description = "JNI coordinator & RegisterNatives dispatch across 2 C/C++ units",
        content = "// Coordinator preview\nJNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) { return 0; }\n",
        totalByteSize = 1_500_000L,
        totalLineCount = 42_000,
        isPreviewTruncated = true,
      )
    val part1 =
      com.dex2c.studio.transpiler.GeneratedFile(
        fileName = "dex2c_bridge_part_01.cpp",
        zipPath = "jni/dex2c_bridge_part_01.cpp",
        description = "Modular C/C++ translation unit #1",
        content = "// Part 1 body\n",
        totalByteSize = 820_000L,
        totalLineCount = 21_000,
        isPreviewTruncated = true,
      )
    val dummySmall =
      com.dex2c.studio.transpiler.GeneratedFile("dex2c_runtime.h", "jni/dex2c_runtime.h", "Header", "#pragma once\n")
    val bundle =
      com.dex2c.studio.transpiler.GeneratedBundle(
        sourceFile = coordinator,
        headerFile = dummySmall,
        androidMkFile = dummySmall.copy(fileName = "Android.mk"),
        applicationMkFile = dummySmall.copy(fileName = "Application.mk"),
        cmakeFile = dummySmall.copy(fileName = "CMakeLists.txt"),
        dccCfgFile = dummySmall.copy(fileName = "dcc.cfg"),
        filterTxtFile = dummySmall.copy(fileName = "filter.txt"),
        loaderStubFile = dummySmall.copy(fileName = "DccApplicationLoader.java"),
        transpiledMethodCount = 4236,
        instructionCount = 68400,
        warnings = emptyList(),
        sourceFiles = listOf(coordinator, part1),
      )

    composeTestRule.setContent {
      Dex2cTheme {
        com.dex2c.studio.ui.screens.BuildHubScreen(
          activeTab = activeTab,
          onSelectTab = { activeTab = it },
          selectedMethodCount = 4236,
          config = com.dex2c.studio.transpiler.TranspilerConfig(),
          hasLoadedDex = true,
          filterMatchSummary = null,
          onUpdateConfig = {},
          onImportFilterTxt = {},
          onImportDccCfg = {},
          onApplyFilterRules = {},
          bundle = bundle,
          isTranspiling = false,
          onCopyFile = {},
          onExportZip = {},
          onGoToClasses = {},
          isBundleDirty = true,
          onEnsureBundleBuilt = { ensureBuildCalls++ },
        )
      }
    }

    composeTestRule.waitForIdle()
    // While on PIPELINE tab, onEnsureBundleBuilt has not been triggered
    assertEquals(0, ensureBuildCalls)

    // Switch to OUTPUT tab -> triggers onEnsureBundleBuilt and displays modular C/C++ unit count + truncated banner
    composeTestRule.onNodeWithTag("build_tab_output").performClick()
    composeTestRule.waitForIdle()
    assertEquals(1, ensureBuildCalls)
    composeTestRule.onNodeWithText("4236 methods").assertIsDisplayed()
    composeTestRule.onNodeWithText("2 C/C++ units").assertIsDisplayed()
    composeTestRule.onNodeWithTag("output_preview_truncated_banner").assertIsDisplayed()
    composeTestRule.onNodeWithTag("output_file_tab_dex2c_bridge_part_01.cpp").assertIsDisplayed()
  }

  @Test
  @Config(sdk = [36], qualifiers = "w412dp-h892dp")
  fun `settingsScreen renders grouped list cards and navigates to subscreen`() {
    var pipelineShortcutClicked by mutableStateOf(false)

    composeTestRule.setContent {
      Dex2cTheme {
        com.dex2c.studio.ui.screens.SettingsScreen(
          appSettings = AppSettings(),
          cloudSettings = CloudSettings(accessToken = "gho_test", githubUsername = "userio12"),
          transpilerConfig = com.dex2c.studio.transpiler.TranspilerConfig(),
          recentHistoryCount = 2,
          hasNotificationPermission = true,
          onUpdateAppSettings = {},
          onUpdateCloudSettings = {},
          onToggleNotifications = {},
          onRequestNotificationPermission = {},
          onSendTestNotification = {},
          onSyncWorkflowToRepo = {},
          onStartDeviceLogin = {},
          onSignOutGitHub = {},
          onOpenGitHubAppSettings = {},
          onGoToPipelineConfig = { pipelineShortcutClicked = true },
          onClearRecentHistory = {},
          onResetAllSettings = {},
          onReplayOnboarding = {},
          onCopyText = {},
          onBack = {},
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
    composeTestRule.onNodeWithText("Detailed Settings").assertDoesNotExist()
    composeTestRule.onNodeWithText("Theme, editor, DEX parser, and Cloud NDK").assertIsDisplayed()
    composeTestRule.onNodeWithText("Follow System • Dynamic").assertIsDisplayed()
    composeTestRule.onNodeWithText("dex2c_native • RegisterNatives • C++17 (env->)").assertIsDisplayed()
    composeTestRule.onNodeWithTag("settings_hub_row_appearance").assertIsDisplayed()
    composeTestRule.onNodeWithTag("settings_hub_row_code_editor").assertIsDisplayed()
    composeTestRule.onNodeWithTag("settings_hub_row_dex_parser").assertIsDisplayed()
    composeTestRule.onNodeWithTag("settings_hub_row_pipeline_shortcut").assertIsDisplayed()
    composeTestRule.onNodeWithTag("settings_hub_row_github_cloud").assertIsDisplayed()
    composeTestRule.onNodeWithTag("settings_hub_row_ndk_signing").assertIsDisplayed()
    composeTestRule.onNodeWithTag("settings_hub_row_notifications").assertIsDisplayed()

    composeTestRule.onNodeWithTag("settings_hub_row_pipeline_shortcut").performClick()
    composeTestRule.waitForIdle()
    assertTrue(pipelineShortcutClicked)

    // Open Appearance subscreen, check Live Theme Preview + Theme Posture, and return via back button
    composeTestRule.onNodeWithTag("settings_hub_row_appearance").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("theme_live_preview_card").assertIsDisplayed()
    composeTestRule.onNodeWithTag("settings_theme_DARK").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("settings_back_btn").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("settings_hub_row_appearance").assertIsDisplayed()
  }

  @Test
  @Config(sdk = [36], qualifiers = "w412dp-h892dp")
  fun `homeScreen Ready state switches between Overview and Header & Map tabs without scrolling`() {
    val mapItems =
      (0 until 10).map { idx ->
        com.dex2c.studio.dex.DexMapItem(
          typeCode = idx,
          typeName = "SECTION_$idx",
          count = idx + 1,
          offset = idx * 64,
        )
      }
    val parsed =
      com.dex2c.studio.dex.DexParsedFile(
        sourceLabel = "TargetApp.apk",
        entryName = "classes.dex",
        rawBytes = ByteArray(128),
        header =
          com.dex2c.studio.dex.DexHeaderInfo(
            magic = "dex\n038\u0000",
            version = "038",
            checksum = 0x0CED6858L,
            sha1Hex = "1edb6edd6871357b9a24abcdef",
            fileSize = 1_500_000,
            endianTag = 0x12345678L,
            stringIdsSize = 12_345,
            typeIdsSize = 900,
            protoIdsSize = 1_200,
            fieldIdsSize = 2_400,
            methodIdsSize = 8_500,
            classDefsSize = 1_100,
            mapOff = 0x1000,
          ),
        strings = emptyList(),
        types = emptyList(),
        protos = emptyList(),
        fields = emptyList(),
        methods = emptyList(),
        classes = emptyList(),
        mapItems = mapItems,
      )
    val readyState =
      com.dex2c.studio.ui.DexLoadState.Ready(
        sourceLabel = "TargetApp.apk",
        activeEntryName = "classes.dex",
        parsedDex = parsed,
        dexEntries = listOf(com.dex2c.studio.dex.ApkDexEntry("classes.dex", 1_500_000)),
        loadedDexes = mapOf("classes.dex" to parsed),
      )

    composeTestRule.setContent {
      Dex2cTheme {
        com.dex2c.studio.ui.screens.HomeScreen(
          loadState = readyState,
          selectedMethodCount = 12,
          recentFiles = emptyList(),
          onOpenPicker = {},
          onInspectSelfApk = {},
          onSelectRecent = {},
          onSelectDexEntry = {},
          onAutoSelectAppMethods = {},
          onClearSelection = {},
          onNavigateToClasses = {},
          onNavigateToExport = {},
        )
      }
    }

    composeTestRule.waitForIdle()
    // Overview tab shows hero, action buttons, and metric tiles on one screen
    composeTestRule.onNodeWithTag("home_tab_OVERVIEW").assertIsDisplayed()
    composeTestRule.onNodeWithTag("home_tab_HEADER_AND_MAP").assertIsDisplayed()
    composeTestRule.onNodeWithTag("open_dex_apk_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("build_c_output_button").assertIsDisplayed()
    composeTestRule.onNodeWithText("1,100").assertIsDisplayed()

    // Switch to Header & Map tab and verify header + map_list render without scrolling
    composeTestRule.onNodeWithTag("home_tab_HEADER_AND_MAP").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("DEX v038").assertIsDisplayed()
    composeTestRule.onNodeWithTag("toggle_all_map_list_btn").assertIsDisplayed()
  }

  @Test
  @Config(sdk = [36], qualifiers = "w412dp-h892dp")
  fun `cloudBuildScreen separates Setup, Dispatch, and Monitor into stage tabs and opens Monitor on dispatch`() {
    var dispatchCount = 0
    val readyCloud =
      CloudSettings(
        accessToken = "gho_test_token",
        githubUsername = "userio12",
        repoOwner = "userio12",
        repoName = "dex2c-cloud-workspace",
        workflowInitialized = true,
      )

    composeTestRule.setContent {
      Dex2cTheme {
        com.dex2c.studio.ui.screens.CloudBuildScreen(
          cloudSettings = readyCloud,
          transpilerConfig = com.dex2c.studio.transpiler.TranspilerConfig(),
          deviceAuthState = DeviceAuthState.Authenticated("userio12"),
          repoSetupState = RepoSetupState.Ready("userio12", "dex2c-cloud-workspace", "https://github.com/userio12/dex2c-cloud-workspace", true, true),
          cloudRunState = CloudBuildRunState.Idle,
          isAutoPollEnabled = true,
          recentRuns = emptyList(),
          apkChoice = null,
          apkUrlFallback = "https://example.com/target.apk",
          onStartDeviceLogin = {},
          onCancelDeviceLogin = {},
          onSignOut = {},
          onCopyCode = {},
          onOpenBrowserUrl = {},
          onUpdateRepoName = {},
          onInitializeRepoAndWorkflow = {},
          onVerifyRepo = {},
          onPickApkFile = {},
          onUseLiveApk = {},
          onApkUrlChange = {},
          onDispatchBuild = { dispatchCount++ },
          onToggleAutoPoll = {},
          onRefreshMonitor = {},
          onRefreshRuns = {},
          onAttachToRun = {},
          onDownloadArtifact = {},
        )
      }
    }

    composeTestRule.waitForIdle()
    // Redundant Settings pill button is removed (TopAppBar holds the single Settings gear)
    composeTestRule.onNodeWithTag("cloud_open_settings_btn").assertDoesNotExist()
    // When already authenticated & repo initialized, Dispatch stage opens directly with all controls visible
    composeTestRule.onNodeWithTag("cloud_tab_SETUP").assertIsDisplayed()
    composeTestRule.onNodeWithTag("cloud_tab_DISPATCH").assertIsDisplayed()
    composeTestRule.onNodeWithTag("cloud_tab_MONITOR").assertIsDisplayed()
    composeTestRule.onNodeWithTag("cloud_run_build_btn").assertIsDisplayed().assertIsEnabled().performClick()
    composeTestRule.waitForIdle()

    assertEquals(1, dispatchCount)
    // Auto-switches to Monitor stage after dispatch
    composeTestRule.onNodeWithTag("build_monitor_card").assertIsDisplayed()

    // Can also jump to Setup stage directly
    composeTestRule.onNodeWithTag("cloud_tab_SETUP").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("cloud_repo_name_input").assertIsDisplayed()
  }

  @Test
  fun `appSettings defaults to SYSTEM theme and enables Dynamic Color only on Android 12+`() {
    val defaults = AppSettings()
    assertEquals(com.dex2c.studio.data.AppThemeMode.SYSTEM, defaults.themeMode)
    assertTrue(defaults.dynamicColor)
    assertTrue(defaults.showOutputScreen)
    assertTrue(com.dex2c.studio.data.defaultDynamicColorForDevice(android.os.Build.VERSION_CODES.S))
    assertFalse(com.dex2c.studio.data.defaultDynamicColorForDevice(android.os.Build.VERSION_CODES.R))
  }

  @Test
  @Config(sdk = [36], qualifiers = "w412dp-h892dp")
  fun `output screen toggle under DEX Parser Settings hides Build Output tab and Home Build C Output button`() {
    var settings by mutableStateOf(AppSettings())
    var ensureBuildCalls by mutableStateOf(0)

    val parsed =
      com.dex2c.studio.dex.DexParsedFile(
        sourceLabel = "TargetApp.apk",
        entryName = "classes.dex",
        rawBytes = ByteArray(64),
        header =
          com.dex2c.studio.dex.DexHeaderInfo(
            magic = "dex\n038\u0000",
            version = "038",
            checksum = 0x1L,
            sha1Hex = "abcdef",
            fileSize = 64,
            endianTag = 0x12345678L,
            stringIdsSize = 10,
            typeIdsSize = 5,
            protoIdsSize = 4,
            fieldIdsSize = 3,
            methodIdsSize = 8,
            classDefsSize = 2,
            mapOff = 0x20,
          ),
        strings = emptyList(),
        types = emptyList(),
        protos = emptyList(),
        fields = emptyList(),
        methods = emptyList(),
        classes = emptyList(),
        mapItems = emptyList(),
      )
    val readyState =
      com.dex2c.studio.ui.DexLoadState.Ready(
        sourceLabel = "TargetApp.apk",
        activeEntryName = "classes.dex",
        parsedDex = parsed,
        dexEntries = listOf(com.dex2c.studio.dex.ApkDexEntry("classes.dex", 64)),
        loadedDexes = mapOf("classes.dex" to parsed),
      )

    var screenStep by mutableStateOf(0)

    composeTestRule.setContent {
      Dex2cTheme {
        when (screenStep) {
          0 ->
            com.dex2c.studio.ui.screens.SettingsDexParserSubScreen(
              appSettings = settings,
              transpilerConfig = com.dex2c.studio.transpiler.TranspilerConfig(),
              onUpdateAppSettings = { transform -> settings = transform(settings) },
              onGoToPipelineConfig = {},
            )
          1 ->
            com.dex2c.studio.ui.screens.BuildHubScreen(
              activeTab = com.dex2c.studio.ui.screens.BuildHubTab.OUTPUT,
              onSelectTab = {},
              selectedMethodCount = 5,
              config = com.dex2c.studio.transpiler.TranspilerConfig(),
              hasLoadedDex = true,
              filterMatchSummary = null,
              onUpdateConfig = {},
              onImportFilterTxt = {},
              onImportDccCfg = {},
              onApplyFilterRules = {},
              bundle = null,
              isTranspiling = false,
              onCopyFile = {},
              onExportZip = {},
              onGoToClasses = {},
              isBundleDirty = true,
              onEnsureBundleBuilt = { ensureBuildCalls++ },
              showOutputScreen = settings.showOutputScreen,
            )
          else ->
            com.dex2c.studio.ui.screens.HomeScreen(
              loadState = readyState,
              selectedMethodCount = 5,
              recentFiles = emptyList(),
              onOpenPicker = {},
              onInspectSelfApk = {},
              onSelectRecent = {},
              onSelectDexEntry = {},
              onAutoSelectAppMethods = {},
              onClearSelection = {},
              onNavigateToClasses = {},
              onNavigateToExport = {},
              showOutputScreen = settings.showOutputScreen,
            )
        }
      }
    }

    composeTestRule.waitForIdle()
    assertTrue(settings.showOutputScreen)
    composeTestRule.onNodeWithTag("settings_show_output_screen_switch").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()
    assertFalse(settings.showOutputScreen)

    screenStep = 1
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("build_tab_output").assertDoesNotExist()
    composeTestRule.onNodeWithTag("build_tab_pipeline").assertDoesNotExist()
    assertEquals(0, ensureBuildCalls)

    screenStep = 2
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("browse_classes_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("build_c_output_button").assertDoesNotExist()
  }

  @Test
  @Config(sdk = [36], qualifiers = "w412dp-h892dp")
  fun `recentBinariesTab places Clear History in dedicated header above full-width filter chips without overlap`() {
    var clearClicks = 0
    val sampleRecent =
      listOf(
        com.dex2c.studio.apk.RecentBinaryItem(
          id = "app_1",
          displayName = "Dex2C Studio",
          sourceType = com.dex2c.studio.apk.BinarySourceType.INSTALLED_APK,
          uriOrPath = "/data/app/base.apk",
          packageName = "com.aistudio.dex2cstudio.vxrkmp",
          byteSize = 23_170_000L,
          lastOpenedMillis = 1_700_000_000_000L,
        )
      )

    composeTestRule.setContent {
      Dex2cTheme {
        com.dex2c.studio.ui.components.RecentBinariesTab(
          items = sampleRecent,
          onSelectRecent = {},
          onRemoveRecent = {},
          onClearRecent = { clearClicks++ },
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("picker_recent_count_label").assertIsDisplayed().assertTextContains("1 saved binary")
    composeTestRule.onNodeWithTag("clear_recent_history_btn").assertIsDisplayed()
    composeTestRule.onNodeWithTag("picker_recent_sort_chip").assertIsDisplayed()
    composeTestRule.onNodeWithTag("picker_recent_filter_ALL").assertIsDisplayed()
    composeTestRule.onNodeWithTag("picker_recent_filter_STORAGE").assertIsDisplayed()

    val clearBounds = composeTestRule.onNodeWithTag("clear_recent_history_btn").fetchSemanticsNode().boundsInRoot
    val filterRowBounds = composeTestRule.onNodeWithTag("picker_recent_filter_row").fetchSemanticsNode().boundsInRoot
    assertTrue(
      "Expected Clear History (${clearBounds.bottom}) above filter chip row (${filterRowBounds.top})",
      clearBounds.bottom <= filterRowBounds.top + 1f,
    )

    composeTestRule.onNodeWithTag("clear_recent_history_btn").performClick()
    composeTestRule.waitForIdle()
    assertEquals(1, clearClicks)
  }

  @Test
  @Config(sdk = [36], qualifiers = "w412dp-h892dp")
  fun `onboarding page 4 renders all 6 accent swatches in one row and page 5 places unclipped permission pill below title`() {
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
          onFinish = {},
          onDismissReplay = {},
        )
      }
    }

    // Navigate to Page 4 (Theme & Palette)
    composeTestRule.onNodeWithTag("onboarding_page_dot_3").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("onboarding_accent_row").performScrollTo().assertIsDisplayed()

    val swatchTags =
      com.dex2c.studio.data.CURATED_ACCENT_PRESETS.map {
        "onboarding_accent_${it.name.lowercase().replace(' ', '_')}"
      }
    val swatchBounds =
      swatchTags.map { tag ->
        composeTestRule.onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
      }
    val firstTop = swatchBounds.first().top
    val firstBottom = swatchBounds.first().bottom
    swatchBounds.forEachIndexed { idx, bounds ->
      assertEquals("Expected swatch $idx on row 1 (top)", firstTop, bounds.top, 2f)
      assertEquals("Expected swatch $idx on row 1 (bottom)", firstBottom, bounds.bottom, 2f)
    }

    // Navigate to Page 5 (Build Alerts)
    composeTestRule.onNodeWithTag("onboarding_page_dot_4").performClick()
    composeTestRule.waitForIdle()
    val titleBounds =
      composeTestRule.onNodeWithText("Build Completion Alerts").performScrollTo().assertIsDisplayed().fetchSemanticsNode().boundsInRoot
    val pillNode =
      composeTestRule.onNodeWithTag("onboarding_notification_status_pill").assertIsDisplayed().assertTextContains("Permission Needed")
    val pillBounds = pillNode.fetchSemanticsNode().boundsInRoot
    val switchBounds =
      composeTestRule.onNodeWithTag("onboarding_notifications_switch").assertIsDisplayed().fetchSemanticsNode().boundsInRoot

    assertTrue(
      "Expected status pill (${pillBounds.top}) below title (${titleBounds.bottom})",
      pillBounds.top >= titleBounds.bottom - 1f,
    )
    assertTrue(
      "Expected status pill (${pillBounds.right}) left of switch (${switchBounds.left})",
      pillBounds.right <= switchBounds.left + 1f,
    )
  }

  @Test
  @Config(sdk = [36], qualifiers = "w412dp-h892dp")
  fun `onboarding showcase cards fill all 5 pages with interactive code peek, live theme preview, and checklist quick jump`() {
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
          onFinish = {},
          onDismissReplay = {},
        )
      }
    }

    // Page 1: Pipeline flow card
    composeTestRule.onNodeWithTag("onboarding_pipeline_flow_card").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("Native Pipeline Flow").assertIsDisplayed()

    // Page 2: Interactive Dalvik <-> JNI C++ peek
    composeTestRule.onNodeWithTag("onboarding_page_dot_1").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("onboarding_code_peek_card").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("onboarding_peek_tab_dalvik").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("const-string", substring = true).assertIsDisplayed()
    composeTestRule.onNodeWithTag("onboarding_peek_tab_cpp").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("JNIEXPORT", substring = true).assertIsDisplayed()

    // Page 3: Cloud workspace steps
    composeTestRule.onNodeWithTag("onboarding_page_dot_2").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("onboarding_cloud_steps_card").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("How Private Cloud Sync Works").assertIsDisplayed()

    // Page 4: Live Studio Preview
    composeTestRule.onNodeWithTag("onboarding_page_dot_3").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("onboarding_live_theme_preview_card").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("Live Studio Preview").assertIsDisplayed()

    // Page 5: Setup Checklist + quick jump back to Theme & Cloud
    composeTestRule.onNodeWithTag("onboarding_page_dot_4").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("onboarding_setup_checklist_card").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("onboarding_review_theme_btn").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Theme & Palette").assertIsDisplayed()

    composeTestRule.onNodeWithTag("onboarding_page_dot_4").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("onboarding_review_cloud_btn").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("GitHub Cloud CI").assertIsDisplayed()
  }

  @Test
  fun `recentFilesStore persists and clears lastBytecodeSummary`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val store = com.dex2c.studio.data.RecentFilesStore(context)
    kotlinx.coroutines.runBlocking {
      store.clearAll()
      assertNull(store.lastBytecodeSummaryFlow.first())

      val summary =
        com.dex2c.studio.dex.BytecodeOverviewSummary(
          sourceLabel = "SecureVault.apk",
          entryName = "classes.dex",
          dexCount = 2,
          dexVersion = "039",
          totalMethods = 4210,
          bytecodeMethods = 3100,
          totalClassDefinitions = 640,
          appClassDefinitions = 180,
          totalStrings = 9500,
          analyzedAtMillis = 1_700_000_000_000L,
          topClasses =
            listOf(
              com.dex2c.studio.dex.ClassDefinitionSummary("VaultEngine", "com.demo.vault", 14, 12, false),
              com.dex2c.studio.dex.ClassDefinitionSummary("KeyStore", "com.demo.crypto", 9, 8, false),
            ),
        )
      store.saveLastBytecodeSummary(summary)
      val loaded = store.lastBytecodeSummaryFlow.first()
      assertNotNull(loaded)
      assertEquals("SecureVault.apk", loaded!!.sourceLabel)
      assertEquals(4210, loaded.totalMethods)
      assertEquals(640, loaded.totalClassDefinitions)
      assertEquals(2, loaded.topClasses.size)
      assertEquals("VaultEngine", loaded.topClasses.first().simpleName)

      store.clearAll()
      assertNull(store.lastBytecodeSummaryFlow.first())
    }
  }

  @Test
  @Config(sdk = [36], qualifiers = "w412dp-h892dp")
  fun `homeScreen renders BytecodeOverviewCard in live ready state and empty saved state`() {
    fun makeMethod(id: String, clsDesc: String, name: String, hasCode: Boolean): com.dex2c.studio.dex.DexEncodedMethod =
      com.dex2c.studio.dex.DexEncodedMethod(
        id = id,
        methodIndex = id.hashCode(),
        methodRef =
          com.dex2c.studio.dex.DexMethodRef(
            definingClass = clsDesc,
            proto = com.dex2c.studio.dex.DexProto("V", "V", emptyList()),
            name = name,
          ),
        accessFlags = 0x0001,
        isDirect = false,
        codeMeta =
          if (hasCode)
            com.dex2c.studio.dex.DexCodeMeta(
              registersSize = 2,
              insSize = 1,
              outsSize = 0,
              triesSize = 0,
              insnsCount = 6,
              insnsOffsetBytes = 0,
            )
          else null,
      )

    val appClassTop =
      com.dex2c.studio.dex.DexClassInfo(
        classIndex = 1,
        descriptor = "Lcom/demo/app/MainRouter;",
        fullName = "com.demo.app.MainRouter",
        simpleName = "MainRouter",
        packageName = "com.demo.app",
        superclass = "Ljava/lang/Object;",
        interfaces = emptyList(),
        sourceFile = "MainRouter.kt",
        accessFlags = 0x0001,
        staticFields = emptyList(),
        instanceFields = emptyList(),
        directMethods = emptyList(),
        virtualMethods =
          listOf(
            makeMethod("m1", "Lcom/demo/app/MainRouter;", "route", true),
            makeMethod("m2", "Lcom/demo/app/MainRouter;", "auth", true),
            makeMethod("m3", "Lcom/demo/app/MainRouter;", "sync", true),
          ),
      )
    val appClassSecond =
      com.dex2c.studio.dex.DexClassInfo(
        classIndex = 2,
        descriptor = "Lcom/demo/core/TokenGuard;",
        fullName = "com.demo.core.TokenGuard",
        simpleName = "TokenGuard",
        packageName = "com.demo.core",
        superclass = "Ljava/lang/Object;",
        interfaces = emptyList(),
        sourceFile = "TokenGuard.kt",
        accessFlags = 0x0001,
        staticFields = emptyList(),
        instanceFields = emptyList(),
        directMethods = emptyList(),
        virtualMethods = listOf(makeMethod("m4", "Lcom/demo/core/TokenGuard;", "verify", true)),
      )

    val liveDex =
      com.dex2c.studio.dex.DexParsedFile(
        sourceLabel = "LiveTarget.apk",
        entryName = "classes.dex",
        rawBytes = ByteArray(1024),
        header =
          com.dex2c.studio.dex.DexHeaderInfo(
            magic = "dex\n039\u0000",
            version = "039",
            checksum = 0x1234L,
            sha1Hex = "deadbeef",
            fileSize = 256_000,
            endianTag = 0x12345678L,
            stringIdsSize = 420,
            typeIdsSize = 88,
            protoIdsSize = 64,
            fieldIdsSize = 40,
            methodIdsSize = 512,
            classDefsSize = 38,
            mapOff = 0x100,
          ),
        strings = emptyList(),
        types = emptyList(),
        protos = emptyList(),
        fields = emptyList(),
        methods = emptyList(),
        classes = listOf(appClassSecond, appClassTop),
      )

    val recentSaved =
      com.dex2c.studio.apk.RecentBinaryItem(
        id = "SAF_URI:content://recent/Saved.apk",
        displayName = "SavedApp.apk",
        sourceType = com.dex2c.studio.apk.BinarySourceType.SAF_URI,
        uriOrPath = "content://recent/Saved.apk",
        packageName = "com.saved.app",
        byteSize = 4_200_000L,
        lastOpenedMillis = 1_700_000_000_000L,
      )
    val savedSummary =
      com.dex2c.studio.dex.BytecodeOverviewSummary(
        sourceLabel = "SavedApp.apk",
        entryName = "classes.dex",
        dexCount = 1,
        dexVersion = "038",
        totalMethods = 1920,
        bytecodeMethods = 1400,
        totalClassDefinitions = 215,
        appClassDefinitions = 64,
        totalStrings = 3100,
        analyzedAtMillis = 1_700_000_000_000L,
        topClasses =
          listOf(
            com.dex2c.studio.dex.ClassDefinitionSummary("SavedEngine", "com.saved.app", 11, 10, false),
          ),
      )

    var loadState by mutableStateOf<com.dex2c.studio.ui.DexLoadState>(
      com.dex2c.studio.ui.DexLoadState.Ready(
        sourceLabel = "LiveTarget.apk",
        dexEntries = listOf(com.dex2c.studio.dex.ApkDexEntry("classes.dex", 256_000)),
        activeEntryName = "classes.dex",
        parsedDex = liveDex,
        loadedDexes = mapOf("classes.dex" to liveDex),
      )
    )
    var persistedSummary by mutableStateOf<com.dex2c.studio.dex.BytecodeOverviewSummary?>(savedSummary)
    var browseClassesCount = 0
    var reopenedItem: com.dex2c.studio.apk.RecentBinaryItem? = null
    var inspectSelfCount = 0

    composeTestRule.setContent {
      Dex2cTheme {
        com.dex2c.studio.ui.screens.HomeScreen(
          loadState = loadState,
          selectedMethodCount = 2,
          recentFiles = listOf(recentSaved),
          lastBytecodeSummary = persistedSummary,
          onOpenPicker = {},
          onInspectSelfApk = { inspectSelfCount++ },
          onSelectRecent = { reopenedItem = it },
          onSelectDexEntry = {},
          onAutoSelectAppMethods = {},
          onClearSelection = {},
          onNavigateToClasses = { browseClassesCount++ },
          onNavigateToExport = {},
        )
      }
    }

    // Live Ready mode: card renders live metrics, top class definitions, and Browse Classes action
    composeTestRule.onNodeWithTag("bytecode_overview_card").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("bytecode_overview_status_pill").assertTextContains("Live · v039")
    composeTestRule.onNodeWithTag("bytecode_overview_methods").assertTextContains("512 total", substring = true)
    composeTestRule.onNodeWithTag("bytecode_overview_classes").assertTextContains("38 defs", substring = true)
    composeTestRule.onNodeWithText("MainRouter").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("TokenGuard").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("bytecode_overview_action_btn").performScrollTo().assertTextContains("Browse Classes").performClick()
    assertEquals(1, browseClassesCount)

    // Switch to Empty state with saved summary -> renders Last Analyzed snapshot & Reopen Last DEX
    loadState = com.dex2c.studio.ui.DexLoadState.Empty
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("bytecode_overview_card").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("bytecode_overview_status_pill").assertTextContains("Last Analyzed · v038")
    composeTestRule.onNodeWithText("SavedEngine").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("bytecode_overview_action_btn").performScrollTo().assertTextContains("Reopen Last DEX").performClick()
    assertEquals(recentSaved, reopenedItem)

    // Switch to clean Empty state without saved summary -> Awaiting DEX + Inspect Live APK
    persistedSummary = null
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("bytecode_overview_status_pill").assertTextContains("Awaiting DEX")
    composeTestRule.onNodeWithTag("bytecode_overview_action_btn").performScrollTo().assertTextContains("Inspect Live APK").performClick()
    assertEquals(1, inspectSelfCount)
  }

  @Test
  @Config(sdk = [36], qualifiers = "w412dp-h892dp")
  fun `methodDetailScreen renders Blocks CFG tab with basic blocks, outgoing edges, and decoded payloads`() {
    val payload =
      com.dex2c.studio.dex.DalvikPayload.PackedSwitch(
        offsetWords = 8,
        wordCount = 8,
        firstKey = 0,
        targetOffsets = listOf(5, 7),
      )
    val insns =
      listOf(
        com.dex2c.studio.dex.DalvikInstruction(
          offsetWords = 0,
          widthWords = 2,
          opcode = 0x38,
          mnemonic = "if-eqz",
          format = "21t",
          operandsText = "v0, L_0005",
          rawWords = listOf(0x0038, 0x0005),
          category = com.dex2c.studio.dex.OpCategory.BRANCH,
          regA = 0,
          targetOffset = 5,
        ),
        com.dex2c.studio.dex.DalvikInstruction(
          offsetWords = 2,
          widthWords = 3,
          opcode = 0x2B,
          mnemonic = "packed-switch",
          format = "31t",
          operandsText = "v1, L_0008",
          rawWords = listOf(0x012B, 0x0006, 0x0000),
          category = com.dex2c.studio.dex.OpCategory.BRANCH,
          regA = 1,
          targetOffset = 8,
          payload = payload,
        ),
        com.dex2c.studio.dex.DalvikInstruction(
          offsetWords = 5,
          widthWords = 1,
          opcode = 0x12,
          mnemonic = "const/4",
          format = "11n",
          operandsText = "v0, #1",
          rawWords = listOf(0x1012),
          category = com.dex2c.studio.dex.OpCategory.CONST,
        ),
        com.dex2c.studio.dex.DalvikInstruction(
          offsetWords = 6,
          widthWords = 1,
          opcode = 0x0F,
          mnemonic = "return",
          format = "11x",
          operandsText = "v0",
          rawWords = listOf(0x000F),
          category = com.dex2c.studio.dex.OpCategory.RETURN,
        ),
        com.dex2c.studio.dex.DalvikInstruction(
          offsetWords = 7,
          widthWords = 1,
          opcode = 0x0F,
          mnemonic = "return",
          format = "11x",
          operandsText = "v0",
          rawWords = listOf(0x000F),
          category = com.dex2c.studio.dex.OpCategory.RETURN,
        ),
        com.dex2c.studio.dex.DalvikInstruction(
          offsetWords = 8,
          widthWords = 8,
          opcode = 0x0100,
          mnemonic = "packed-switch-payload",
          format = "payload",
          operandsText = "first=0 [0->L_0005, 1->L_0007]",
          rawWords = listOf(0x0100, 0x0002, 0x0000, 0x0000),
          category = com.dex2c.studio.dex.OpCategory.PAYLOAD,
          payload = payload,
        ),
      )

    val method =
      com.dex2c.studio.dex.DexEncodedMethod(
        id = "m_cfg_ui",
        methodIndex = 1,
        methodRef = com.dex2c.studio.dex.DexMethodRef("Lcom/demo/Router;", com.dex2c.studio.dex.DexProto("II", "I", listOf("I")), "dispatch"),
        accessFlags = 0x0001,
        isDirect = false,
        codeMeta = com.dex2c.studio.dex.DexCodeMeta(2, 1, 0, 0, 16, 0),
      )
    val dexClass =
      com.dex2c.studio.dex.DexClassInfo(
        classIndex = 1,
        descriptor = "Lcom/demo/Router;",
        fullName = "com.demo.Router",
        simpleName = "Router",
        packageName = "com.demo",
        superclass = "Ljava/lang/Object;",
        interfaces = emptyList(),
        sourceFile = "Router.kt",
        accessFlags = 0x0001,
        staticFields = emptyList(),
        instanceFields = emptyList(),
        directMethods = emptyList(),
        virtualMethods = listOf(method),
      )
    val meta =
      com.dex2c.studio.dex.DexMethodStructuralMeta(
        methodIndex = 1,
        codeItemOffset = 0x100,
        debugInfoOffset = 0,
        classAnnotationsOffset = 0,
        staticValuesOffset = 0,
        parameterNames = listOf("code"),
        debugLines = emptyList(),
        classAnnotations = emptyList(),
        methodAnnotations = emptyList(),
        staticValuesPreview = emptyList(),
        sourceFile = "Router.kt",
      )
    val inspection =
      com.dex2c.studio.ui.MethodInspectionState(
        method = method,
        dexClass = dexClass,
        instructions = insns,
        tryBlocks = emptyList(),
        transpiledC = "jint Java_Router_dispatch(JNIEnv* env) { return 1; }",
        structuralMeta = meta,
      )

    composeTestRule.setContent {
      Dex2cTheme {
        com.dex2c.studio.ui.screens.MethodDetailScreen(
          inspection = inspection,
          isSelected = false,
          onToggleSelect = {},
          onCopyCCode = {},
          onBack = {},
        )
      }
    }

    // Switch to Blocks tab
    composeTestRule.onNodeWithTag("tab_blocks").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("cfg_viewer_container").assertIsDisplayed()
    composeTestRule.onNodeWithTag("cfg_stat_blocks").assertTextContains("4 blocks")
    composeTestRule.onNodeWithTag("cfg_stat_payloads").assertTextContains("1 payloads")
    composeTestRule.onNodeWithTag("cfg_block_BB_0").assertIsDisplayed()

    // Scroll to decoded payload section in CFG list
    composeTestRule.onNodeWithTag("cfg_blocks_list").performScrollToNode(hasTestTag("cfg_payloads_section"))
    composeTestRule.onNodeWithTag("cfg_payloads_section").assertIsDisplayed()
    composeTestRule.onNodeWithTag("cfg_payload_8").assertIsDisplayed()

    // Switch to Dalvik IR tab and check inline payload comment line
    composeTestRule.onNodeWithTag("tab_dalvik_ir").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("; 0->L_0005, 1->L_0007", substring = true).assertIsDisplayed()

    // View Smali button in TopAppBar is visible and clickable
    composeTestRule.onNodeWithTag("method_view_smali_btn").assertIsDisplayed()
  }

  @Test
  @Config(sdk = [36], qualifiers = "w412dp-h892dp")
  fun `classesScreen opens Smali class view from class row action button`() {
    val dexClass =
      com.dex2c.studio.dex.DexClassInfo(
        classIndex = 4,
        descriptor = "Lcom/example/Vault;",
        fullName = "com.example.Vault",
        simpleName = "Vault",
        packageName = "com.example",
        superclass = "Ljava/lang/Object;",
        interfaces = emptyList(),
        sourceFile = "Vault.kt",
        accessFlags = 0x0001,
        staticFields = emptyList(),
        instanceFields = emptyList(),
        directMethods = emptyList(),
        virtualMethods = emptyList(),
      )
    val parsed =
      com.dex2c.studio.dex.DexParsedFile(
        sourceLabel = "App.apk",
        entryName = "classes.dex",
        rawBytes = ByteArray(64),
        header =
          com.dex2c.studio.dex.DexHeaderInfo(
            magic = "dex\n039\u0000",
            version = "039",
            checksum = 1L,
            sha1Hex = "abc",
            fileSize = 64,
            endianTag = 0x12345678L,
            stringIdsSize = 1,
            typeIdsSize = 1,
            protoIdsSize = 1,
            fieldIdsSize = 0,
            methodIdsSize = 1,
            classDefsSize = 1,
          ),
        strings = emptyList(),
        types = emptyList(),
        protos = emptyList(),
        fields = emptyList(),
        methods = emptyList(),
        classes = listOf(dexClass),
      )
    val ready =
      com.dex2c.studio.ui.DexLoadState.Ready(
        sourceLabel = "App.apk",
        dexEntries = listOf(com.dex2c.studio.dex.ApkDexEntry("classes.dex", 64)),
        activeEntryName = "classes.dex",
        parsedDex = parsed,
        loadedDexes = mapOf("classes.dex" to parsed),
      )

    var openedSmaliClass: com.dex2c.studio.dex.DexClassInfo? = null
    composeTestRule.setContent {
      Dex2cTheme {
        com.dex2c.studio.ui.screens.ClassesScreen(
          loadState = ready,
          classes = listOf(dexClass),
          filterState = com.dex2c.studio.ui.ClassFilterState(onlyWithBytecode = false),
          selectedMethodIds = emptySet(),
          allowInit = false,
          onSearchChange = {},
          onToggleHideFramework = {},
          onToggleOnlyBytecode = {},
          onToggleOnlySelected = {},
          onToggleClassSelection = {},
          onToggleMethodSelection = {},
          onInspectMethod = { _, _ -> },
          onInspectSelfApk = {},
          onInspectClassSmali = { openedSmaliClass = it },
        )
      }
    }

    // Top-level "com" is auto-expanded to reveal nested "example" package; expanding it reveals class leaf row
    composeTestRule.onNodeWithTag("pkg_row_com").assertIsDisplayed()
    composeTestRule.onNodeWithTag("pkg_row_com.example").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("class_row_4").assertIsDisplayed().performClick()
    assertEquals(dexClass, openedSmaliClass)

    // Verify Dex Editor Plus hierarchy (com -> crackme -> ui.theme)
    val mainActivityCls = dexClass.copy(packageName = "com.crackme", simpleName = "MainActivity")
    val themeCls = dexClass.copy(classIndex = 5, packageName = "com.crackme.ui.theme", simpleName = "ThemeKt")
    val tree =
      com.dex2c.studio.ui.screens.buildNestedPackageTree(
        classes = listOf(mainActivityCls, themeCls),
        selectedMethodIds = emptySet(),
        sortOrder = com.dex2c.studio.ui.ClassSortOrder.NAME_ASC,
        defaultPackageLabel = "(default)",
      )
    assertEquals(1, tree.size)
    assertEquals("com", tree[0].displayLabel)
    assertEquals(1, tree[0].children.size)
    val crackmeGroup = tree[0].children[0]
    assertEquals("crackme", crackmeGroup.displayLabel)
    assertEquals("com.crackme", crackmeGroup.packageName)
    assertEquals(listOf("MainActivity"), crackmeGroup.directClasses.map { it.simpleName })
    assertEquals(1, crackmeGroup.children.size)
    assertEquals("ui.theme", crackmeGroup.children[0].displayLabel)
    assertEquals("com.crackme.ui.theme", crackmeGroup.children[0].packageName)
  }

  @Test
  @Config(sdk = [36], qualifiers = "w412dp-h892dp")
  fun `smaliClassScreen renders Smali source, searches matches, and fires copy and export`() {
    val dexClass =
      com.dex2c.studio.dex.DexClassInfo(
        classIndex = 0,
        descriptor = "Lcom/example/TokenGuard;",
        fullName = "com.example.TokenGuard",
        simpleName = "TokenGuard",
        packageName = "com.example",
        superclass = "Ljava/lang/Object;",
        interfaces = emptyList(),
        sourceFile = "TokenGuard.kt",
        accessFlags = 0x0011,
        staticFields = emptyList(),
        instanceFields = emptyList(),
        directMethods = emptyList(),
        virtualMethods = emptyList(),
      )
    val sampleSmali =
      """
      .class public final Lcom/example/TokenGuard;
      .super Ljava/lang/Object;
      .source "TokenGuard.kt"

      # direct methods
      .method public constructor <init>()V
          .registers 1
          invoke-direct {p0}, Ljava/lang/Object;-><init>()V
          return-void
      .end method

      .method public final verify(Ljava/lang/String;)Z
          .registers 3
          const-string v0, "ok"
          invoke-virtual {p1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
          move-result v0
          return v0
      .end method
      """.trimIndent()
    val lines = sampleSmali.lines()
    val state =
      com.dex2c.studio.ui.SmaliClassUiState.Ready(
        dexClass = dexClass,
        smaliText = sampleSmali,
        lines = lines,
        lineCount = lines.size,
        methodCount = 2,
        fieldCount = 0,
        durationMs = 4L,
        methodSpans = com.dex2c.studio.dex.SmaliClassDisassembler.extractMethodSpans(lines),
      )

    var copiedText = ""
    var exportedFile = ""
    var backPressed = false

    composeTestRule.setContent {
      Dex2cTheme {
        com.dex2c.studio.ui.screens.SmaliClassScreen(
          state = state,
          onCopySmali = { copiedText = it },
          onExportSmali = { fileName, _ -> exportedFile = fileName },
          onBack = { backPressed = true },
        )
      }
    }

    composeTestRule.onNodeWithText("TokenGuard.smali").assertIsDisplayed()
    composeTestRule.onNodeWithTag("smali_method_range_bar").assertIsDisplayed()
    composeTestRule.onNodeWithTag("smali_active_method_range_text", useUnmergedTree = true).assertIsDisplayed()
    composeTestRule.onNodeWithTag("smali_code_list").assertIsDisplayed()
    composeTestRule.onNodeWithText(".class public final Lcom/example/TokenGuard;", substring = true).assertIsDisplayed()

    // Toggle search bar, search for "return", and step to match 2/2
    composeTestRule.onNodeWithTag("smali_search_toggle_btn").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("smali_search_input").performTextReplacement("return")
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("smali_search_count").assertTextContains("1/2")
    composeTestRule.onNodeWithTag("smali_search_next_btn").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("smali_search_count").assertTextContains("2/2")

    // Copy, export, and back actions
    composeTestRule.onNodeWithTag("smali_copy_btn").performClick()
    assertEquals(sampleSmali, copiedText)
    composeTestRule.onNodeWithTag("smali_export_btn").performClick()
    assertEquals("TokenGuard.smali", exportedFile)
    composeTestRule.onNodeWithTag("smali_back_btn").performClick()
    assertTrue(backPressed)
  }

  @Test
  @Config(sdk = [36], qualifiers = "w412dp-h892dp")
  fun `methodDetailScreen displays Smali tab and PipelineConfigScreen renders Filter Rule Builder`() {
    val method =
      com.dex2c.studio.dex.DexEncodedMethod(
        id = "classes.dex::Lcom/example/Auth;#0",
        methodIndex = 0,
        methodRef =
          com.dex2c.studio.dex.DexMethodRef(
            definingClass = "Lcom/example/Auth;",
            proto = com.dex2c.studio.dex.DexProto("Z", "Z", emptyList()),
            name = "check",
          ),
        accessFlags = 0x0001,
        isDirect = false,
        codeMeta =
          com.dex2c.studio.dex.DexCodeMeta(
            registersSize = 2,
            insSize = 1,
            outsSize = 0,
            triesSize = 0,
            insnsCount = 2,
            insnsOffsetBytes = 0,
          ),
      )
    val dexClass =
      com.dex2c.studio.dex.DexClassInfo(
        classIndex = 0,
        descriptor = "Lcom/example/Auth;",
        fullName = "com.example.Auth",
        simpleName = "Auth",
        packageName = "com.example",
        superclass = "Ljava/lang/Object;",
        interfaces = emptyList(),
        sourceFile = "Auth.kt",
        accessFlags = 0x0001,
        staticFields = emptyList(),
        instanceFields = emptyList(),
        directMethods = emptyList(),
        virtualMethods = listOf(method),
      )
    val sampleMethodSmali =
      """
      .method public check()Z
          .registers 2
          const/4 v0, 0x1
          return v0
      .end method
      """.trimIndent()
    val inspection =
      com.dex2c.studio.ui.MethodInspectionState(
        method = method,
        dexClass = dexClass,
        instructions = emptyList(),
        tryBlocks = emptyList(),
        transpiledC = "jboolean Java_com_example_Auth_check(JNIEnv* env, jobject thiz) { return 1; }",
        structuralMeta =
          com.dex2c.studio.dex.DexMethodStructuralMeta(
            methodIndex = 0,
            codeItemOffset = 0,
            debugInfoOffset = 0,
            classAnnotationsOffset = 0,
            staticValuesOffset = 0,
            parameterNames = emptyList(),
            debugLines = emptyList(),
            classAnnotations = emptyList(),
            methodAnnotations = emptyList(),
            staticValuesPreview = emptyList(),
            sourceFile = "Auth.kt",
          ),
        methodSmali = sampleMethodSmali,
      )

    var copiedCode = ""
    composeTestRule.setContent {
      Dex2cTheme {
        com.dex2c.studio.ui.screens.MethodDetailScreen(
          inspection = inspection,
          isSelected = false,
          onToggleSelect = {},
          onCopyCCode = { copiedCode = it },
          onBack = {},
        )
      }
    }

    composeTestRule.onNodeWithTag("tab_method_smali").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("smali_code_list").assertIsDisplayed()
    composeTestRule.onNodeWithText(".method public check()Z", substring = true).assertIsDisplayed()
    composeTestRule.onNodeWithTag("method_copy_c_btn").performClick()
    assertEquals(sampleMethodSmali, copiedCode)
  }

  @Test
  @Config(sdk = [36], qualifiers = "w412dp-h892dp")
  fun `classesScreen Filter Rules sub-tab toggles Whitelist and Blacklist presets and quick row rules`() {
    val dexClass =
      com.dex2c.studio.dex.DexClassInfo(
        classIndex = 0,
        descriptor = "Lcom/crackme/MainActivity;",
        fullName = "com.crackme.MainActivity",
        simpleName = "MainActivity",
        packageName = "com.crackme",
        superclass = "Ljava/lang/Object;",
        interfaces = emptyList(),
        sourceFile = "MainActivity.kt",
        accessFlags = 0x0001,
        staticFields = emptyList(),
        instanceFields = emptyList(),
        directMethods = emptyList(),
        virtualMethods = emptyList(),
      )
    val parsed =
      com.dex2c.studio.dex.DexParsedFile(
        sourceLabel = "CrackMe.apk",
        entryName = "classes.dex",
        rawBytes = ByteArray(64),
        header =
          com.dex2c.studio.dex.DexHeaderInfo(
            magic = "dex\n039\u0000",
            version = "039",
            checksum = 1L,
            sha1Hex = "abc",
            fileSize = 64,
            endianTag = 0x12345678L,
            stringIdsSize = 1,
            typeIdsSize = 1,
            protoIdsSize = 1,
            fieldIdsSize = 0,
            methodIdsSize = 1,
            classDefsSize = 1,
          ),
        strings = emptyList(),
        types = emptyList(),
        protos = emptyList(),
        fields = emptyList(),
        methods = emptyList(),
        classes = listOf(dexClass),
      )
    val ready =
      com.dex2c.studio.ui.DexLoadState.Ready(
        sourceLabel = "CrackMe.apk",
        dexEntries = listOf(com.dex2c.studio.dex.ApkDexEntry("classes.dex", 64)),
        activeEntryName = "classes.dex",
        parsedDex = parsed,
        loadedDexes = mapOf("classes.dex" to parsed),
      )
    var cfg by mutableStateOf(com.dex2c.studio.transpiler.TranspilerConfig(filterRulesText = ""))

    composeTestRule.setContent {
      Dex2cTheme {
        com.dex2c.studio.ui.screens.ClassesScreen(
          loadState = ready,
          classes = listOf(dexClass),
          filterState = com.dex2c.studio.ui.ClassFilterState(onlyWithBytecode = false),
          selectedMethodIds = emptySet(),
          allowInit = false,
          onSearchChange = {},
          onToggleHideFramework = {},
          onToggleOnlyBytecode = {},
          onToggleOnlySelected = {},
          onToggleClassSelection = {},
          onToggleMethodSelection = {},
          onInspectMethod = { _, _ -> },
          onInspectSelfApk = {},
          config = cfg,
          onUpdateConfig = { transform -> cfg = transform(cfg) },
          onQuickPackageRule = { pkg, isBlacklist ->
            val rule =
              com.dex2c.studio.transpiler.StructuredFilterRule(
                packageName = pkg.replace('.', '/'),
                className = "",
                methodName = "",
                isBlacklist = isBlacklist,
                ruleType = com.dex2c.studio.transpiler.FilterRuleType.PACKAGE_ALL,
              )
            cfg =
              cfg.copy(
                filterRulesText =
                  com.dex2c.studio.transpiler.FilterRuleEngine.upsertStructuredRule(cfg.filterRulesText, rule)
              )
          },
        )
      }
    }

    // Enable Select Mode in Explorer and click +W on com.crackme package
    composeTestRule.onNodeWithTag("chip_select_mode").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("pkg_whitelist_btn_com.crackme").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()
    assertTrue(cfg.filterRulesText.contains("com/crackme/.*;.*"))

    // Switch to FILTER RULES sub-tab and verify Whitelist/Blacklist cards and presets
    composeTestRule.onNodeWithTag("classes_subtab_rules").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("summary_card_whitelist").assertIsDisplayed()
    composeTestRule.onNodeWithTag("summary_card_blacklist").assertIsDisplayed()
    composeTestRule.onNodeWithTag("preset_lifecycle_onCreate").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    assertTrue(cfg.filterRulesText.contains(".*;onCreate\\(.*"))
  }

  @Test
  fun `findSmaliSearchMatches and extractLineInteractiveTokens handle wholeWord, regex, category filters, and symbol tokens`() {
    val lines =
      listOf(
        ".class public Lcom/crackme/Auth;",
        ".method public verify(Ljava/lang/String;)Z",
        "    .registers 12",
        "    const-string v0, \"secret_key\"",
        "    const/4 v1, 0x1",
        "    const/4 v10, 0x0",
        "    invoke-virtual {p1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z",
        "    move-result v1",
        "    return v1",
        ".end method",
      )
    val spans = com.dex2c.studio.dex.SmaliClassDisassembler.extractMethodSpans(lines)

    // 1. Whole-word search for "v1" should match lines 4, 7, 8 ("v1"), NOT line 5 ("v10")
    val wholeWordRes =
      com.dex2c.studio.ui.components.findSmaliSearchMatches(
        lines = lines,
        methodSpans = spans,
        options =
          com.dex2c.studio.ui.components.SmaliSearchOptions(
            query = "v1",
            wholeWord = true,
          ),
      )
    assertEquals(listOf(4, 7, 8), wholeWordRes.matches.map { it.lineIndex })
    assertEquals("verify", wholeWordRes.matches.first().enclosingMethodName)

    // 2. Category filter for METHODS without query should match method declaration + invoke-virtual call
    val methodsRes =
      com.dex2c.studio.ui.components.findSmaliSearchMatches(
        lines = lines,
        methodSpans = spans,
        options =
          com.dex2c.studio.ui.components.SmaliSearchOptions(
            query = "",
            category = com.dex2c.studio.ui.components.SmaliSearchCategory.METHODS,
          ),
      )
    assertEquals(listOf(1, 6), methodsRes.matches.map { it.lineIndex })

    // 3. Regex search for "const(-string|/4)"
    val regexRes =
      com.dex2c.studio.ui.components.findSmaliSearchMatches(
        lines = lines,
        methodSpans = spans,
        options =
          com.dex2c.studio.ui.components.SmaliSearchOptions(
            query = "const(-string|/4)",
            useRegex = true,
          ),
      )
    assertEquals(listOf(3, 4, 5), regexRes.matches.map { it.lineIndex })

    // 4. Interactive token extraction on invoke-virtual line
    val tokens = com.dex2c.studio.ui.components.extractLineInteractiveTokens(lines[6])
    assertTrue(tokens.any { it.text == "invoke-virtual" && it.kind == com.dex2c.studio.ui.components.SmaliTokenKind.OPCODE })
    assertTrue(tokens.any { it.text == "p1" && it.kind == com.dex2c.studio.ui.components.SmaliTokenKind.REGISTER })
    assertTrue(tokens.any { it.text == "v0" && it.kind == com.dex2c.studio.ui.components.SmaliTokenKind.REGISTER })
    assertTrue(tokens.any { it.text == "->equals(" && it.kind == com.dex2c.studio.ui.components.SmaliTokenKind.METHOD })
  }

  @Test
  @Config(sdk = [36], qualifiers = "w412dp-h892dp")
  fun `smaliClassScreen supports category filter chips, interactive register highlighting, and matching lines drawer`() {
    val dexClass =
      com.dex2c.studio.dex.DexClassInfo(
        classIndex = 0,
        descriptor = "Lcom/crackme/Auth;",
        fullName = "com.crackme.Auth",
        simpleName = "Auth",
        packageName = "com.crackme",
        superclass = "Ljava/lang/Object;",
        interfaces = emptyList(),
        sourceFile = "Auth.kt",
        accessFlags = 0x0001,
        staticFields = emptyList(),
        instanceFields = emptyList(),
        directMethods = emptyList(),
        virtualMethods = emptyList(),
      )
    val lines =
      listOf(
        ".class public Lcom/crackme/Auth;",
        ".super Ljava/lang/Object;",
        ".method public verify(Ljava/lang/String;)Z",
        "    .registers 4",
        "    const-string v0, \"secret\"",
        "    invoke-virtual {p1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z",
        "    move-result v0",
        "    return v0",
        ".end method",
      )
    val readyState =
      com.dex2c.studio.ui.SmaliClassUiState.Ready(
        dexClass = dexClass,
        smaliText = lines.joinToString("\n"),
        lines = lines,
        lineCount = lines.size,
        methodCount = 1,
        fieldCount = 0,
        durationMs = 5L,
        methodSpans = com.dex2c.studio.dex.SmaliClassDisassembler.extractMethodSpans(lines),
      )

    composeTestRule.setContent {
      Dex2cTheme {
        com.dex2c.studio.ui.screens.SmaliClassScreen(
          state = readyState,
          onCopySmali = {},
          onExportSmali = { _, _ -> },
          onBack = {},
        )
      }
    }

    // Tap line 5 (invoke-virtual {p1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z) to inspect symbols
    composeTestRule.onNodeWithTag("smali_code_line_5").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("smali_inspected_line_bar").assertIsDisplayed()

    // Tap register v0 token chip from the inspected line bar -> opens search bar & highlights all 4 lines with v0
    composeTestRule.onNodeWithTag("smali_line_token_v0").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("smali_search_count").assertTextContains("1/4")

    // Open Matching Lines drawer and verify rows are displayed
    composeTestRule.onNodeWithTag("smali_search_matches_drawer_toggle").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("smali_matching_lines_drawer").assertIsDisplayed()
    composeTestRule.onNodeWithTag("smali_match_result_row_7").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("smali_search_count").assertTextContains("4/4")

    // Clear search and tap Strings category chip to highlight string literals
    composeTestRule.onNodeWithTag("smali_search_clear_btn").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("smali_search_category_strings").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("smali_search_count").assertTextContains("1/1")
  }

  @Test
  fun `resolveSmaliSyntaxPalette and highlightSmaliLine apply BytecodeHighContrast semantic colors`() {
    val hcPalette =
      com.dex2c.studio.ui.theme.resolveSmaliSyntaxPalette(
        com.dex2c.studio.data.SmaliSyntaxTheme.BYTECODE_HIGH_CONTRAST,
        com.dex2c.studio.ui.theme.DarkSyntaxPalette,
      )
    assertTrue(hcPalette.isHighContrastBytecode)
    assertEquals(com.dex2c.studio.ui.theme.BytecodeHighContrastSyntaxPalette, hcPalette)

    val invokeLine = "    invoke-virtual {p1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z"
    val annotatedInvoke =
      com.dex2c.studio.ui.components.highlightSmaliLine(
        line = invokeLine,
        palette = hcPalette,
      )
    assertTrue(annotatedInvoke.spanStyles.any { it.item.color == hcPalette.opcodeInvoke })
    assertTrue(annotatedInvoke.spanStyles.any { it.item.color == hcPalette.parameterRegister })
    assertTrue(annotatedInvoke.spanStyles.any { it.item.color == hcPalette.register })
    assertTrue(annotatedInvoke.spanStyles.any { it.item.color == hcPalette.memberTarget })

    val branchLine = "    if-eqz v0, :cond_0"
    val annotatedBranch =
      com.dex2c.studio.ui.components.highlightSmaliLine(
        line = branchLine,
        palette = hcPalette,
      )
    assertTrue(annotatedBranch.spanStyles.any { it.item.color == hcPalette.opcodeBranch })
  }

  @Test
  @Config(sdk = [36], qualifiers = "w412dp-h892dp")
  fun `smaliClassScreen theme switcher toggles Light, Dark, and Bytecode High-Contrast themes`() {
    val dexClass =
      com.dex2c.studio.dex.DexClassInfo(
        classIndex = 0,
        descriptor = "Lcom/crackme/Auth;",
        fullName = "com.crackme.Auth",
        simpleName = "Auth",
        packageName = "com.crackme",
        superclass = "Ljava/lang/Object;",
        interfaces = emptyList(),
        sourceFile = "Auth.kt",
        accessFlags = 0x0001,
        staticFields = emptyList(),
        instanceFields = emptyList(),
        directMethods = emptyList(),
        virtualMethods = emptyList(),
      )
    val lines =
      listOf(
        ".class public Lcom/crackme/Auth;",
        ".method public verify(Ljava/lang/String;)Z",
        "    .registers 3",
        "    const-string v0, \"secret\"",
        "    invoke-virtual {p1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z",
        "    move-result v0",
        "    if-eqz v0, :cond_0",
        "    :cond_0",
        "    return v0",
        ".end method",
      )
    val readyState =
      com.dex2c.studio.ui.SmaliClassUiState.Ready(
        dexClass = dexClass,
        smaliText = lines.joinToString("\n"),
        lines = lines,
        lineCount = lines.size,
        methodCount = 1,
        fieldCount = 0,
        durationMs = 4L,
        methodSpans = com.dex2c.studio.dex.SmaliClassDisassembler.extractMethodSpans(lines),
      )

    var selectedTheme by mutableStateOf(com.dex2c.studio.data.SmaliSyntaxTheme.APP_DEFAULT)

    composeTestRule.setContent {
      Dex2cTheme {
        com.dex2c.studio.ui.screens.SmaliClassScreen(
          state = readyState,
          onCopySmali = {},
          onExportSmali = { _, _ -> },
          onBack = {},
          smaliSyntaxTheme = selectedTheme,
          onSelectSmaliSyntaxTheme = { selectedTheme = it },
        )
      }
    }

    // Open the Smali syntax theme switcher bar from the top toolbar
    composeTestRule.onNodeWithTag("smali_theme_switcher_btn").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("smali_theme_selector_bar").assertIsDisplayed()

    // Select Light theme
    composeTestRule.onNodeWithTag("smali_theme_chip_light").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    assertEquals(com.dex2c.studio.data.SmaliSyntaxTheme.LIGHT, selectedTheme)

    // Select Bytecode High-Contrast theme and verify the semantic color legend appears
    composeTestRule.onNodeWithTag("smali_theme_chip_high_contrast").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    assertEquals(com.dex2c.studio.data.SmaliSyntaxTheme.BYTECODE_HIGH_CONTRAST, selectedTheme)
    composeTestRule.onNodeWithTag("smali_high_contrast_legend").assertIsDisplayed()
  }

  @Test
  @Config(sdk = [36], qualifiers = "w412dp-h892dp")
  fun `cloudBuildNotifier and SettingsNotificationsSubScreen sync notification colors with theme and accent seed`() {
    val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
    org.robolectric.Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)

    val amberLightSettings =
      com.dex2c.studio.data.AppSettings(
        themeMode = com.dex2c.studio.data.AppThemeMode.LIGHT,
        dynamicColor = false,
        accentSeedHex = "#F59E0B",
      )
    val blueDarkSettings =
      com.dex2c.studio.data.AppSettings(
        themeMode = com.dex2c.studio.data.AppThemeMode.DARK,
        dynamicColor = false,
        accentSeedHex = "#4C8DF6",
      )

    val amberColors =
      CloudBuildNotifier.resolveNotificationThemeColors(
        context = app,
        appSettings = amberLightSettings,
        isError = false,
      )
    val blueColors =
      CloudBuildNotifier.resolveNotificationThemeColors(
        context = app,
        appSettings = blueDarkSettings,
        isError = false,
      )
    val errorColors =
      CloudBuildNotifier.resolveNotificationThemeColors(
        context = app,
        appSettings = blueDarkSettings,
        isError = true,
      )

    assertTrue(!amberColors.isDark)
    assertTrue(blueColors.isDark)
    assertTrue(amberColors.accentArgb != blueColors.accentArgb)
    assertTrue(errorColors.accentArgb != blueColors.accentArgb)

    // Verify posted notification color matches resolved theme accentArgb
    assertTrue(CloudBuildNotifier.sendTestNotification(app, amberLightSettings))
    val nm = app.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
    val shadowNm = org.robolectric.Shadows.shadowOf(nm)
    val posted = shadowNm.getNotification(7777)
    assertNotNull(posted)
    assertEquals(amberColors.accentArgb, posted.color)

    // Verify SettingsNotificationsSubScreen displays the live Notification & Banner Theme Preview card
    composeTestRule.setContent {
      Dex2cTheme(
        themeMode = blueDarkSettings.themeMode,
        dynamicColor = blueDarkSettings.dynamicColor,
        accentSeedHex = blueDarkSettings.accentSeedHex,
      ) {
        com.dex2c.studio.ui.screens.SettingsNotificationsSubScreen(
          cloudSettings = com.dex2c.studio.cloud.CloudSettings(notificationsEnabled = true),
          hasNotificationPermission = true,
          onToggleNotifications = {},
          onRequestPermission = {},
          onSendTestNotification = {},
          appSettings = blueDarkSettings,
        )
      }
    }

    composeTestRule.onNodeWithTag("settings_notification_theme_preview_card").assertIsDisplayed()
    composeTestRule.onNodeWithTag("settings_system_notif_preview").assertIsDisplayed()
    composeTestRule.onNodeWithTag("settings_inapp_banner_preview").assertIsDisplayed()
  }

  @Test
  @Config(sdk = [36], qualifiers = "w412dp-h892dp")
  fun `apkPackageDetection and ClassesScreen clean folder levels and APK package focus work end to end`() {
    // 1. Verify parseClassDescriptor and normalizePackageName handle standard, root, and slash/malformed descriptors
    val stdParsed = com.dex2c.studio.dex.parseClassDescriptor("Lcom/crackme/app/ui/MainActivity;")
    assertEquals("com.crackme.app.ui", stdParsed.packageName)
    assertEquals("MainActivity", stdParsed.simpleName)
    assertEquals("com.crackme.app.ui.MainActivity", stdParsed.fullName)

    val rootParsed = com.dex2c.studio.dex.parseClassDescriptor("LRootBootstrap;")
    assertEquals("(default)", rootParsed.packageName)
    assertEquals("RootBootstrap", rootParsed.simpleName)

    val slashPkg = com.dex2c.studio.dex.normalizePackageName("Lcom//crackme/app/;", "(default)")
    assertEquals("com.crackme.app", slashPkg)

    // 2. Verify XML manifest package extraction & dominant DEX package inference
    val xmlManifest =
      """<?xml version="1.0" encoding="utf-8"?>
      <manifest xmlns:android="http://schemas.android.com/apk/res/android"
          package="com.crackme.app"
          android:versionCode="1">
      </manifest>""".trimIndent().toByteArray(Charsets.UTF_8)
    assertEquals(
      "com.crackme.app",
      com.dex2c.studio.apk.ApkMetadataInspector.extractPackageNameFromManifestBytes(xmlManifest),
    )

    val baseClass =
      com.dex2c.studio.dex.DexClassInfo(
        classIndex = 0,
        descriptor = "Lcom/crackme/app/MainActivity;",
        fullName = "com.crackme.app.MainActivity",
        simpleName = "MainActivity",
        packageName = "com.crackme.app",
        superclass = "Ljava/lang/Object;",
        interfaces = emptyList(),
        sourceFile = "MainActivity.kt",
        accessFlags = 0x0001,
        staticFields = emptyList(),
        instanceFields = emptyList(),
        directMethods = emptyList(),
        virtualMethods = emptyList(),
      )
    val themeClass =
      baseClass.copy(
        classIndex = 1,
        descriptor = "Lcom/crackme/app/ui/theme/ColorKt;",
        fullName = "com.crackme.app.ui.theme.ColorKt",
        simpleName = "ColorKt",
        packageName = "com.crackme.app.ui.theme",
      )
    assertEquals(
      "com.crackme.app",
      com.dex2c.studio.dex.inferDominantDexPackageName(listOf(baseClass, themeClass)),
    )

    // 3. Verify clean folder levels (compactMiddlePackages = false) keeps each folder level distinct
    val cleanTree =
      com.dex2c.studio.ui.screens.buildNestedPackageTree(
        classes = listOf(baseClass, themeClass),
        selectedMethodIds = emptySet(),
        sortOrder = com.dex2c.studio.ui.ClassSortOrder.NAME_ASC,
        defaultPackageLabel = "(default)",
        compactMiddlePackages = false,
      )
    val comNode = cleanTree.single()
    assertEquals("com", comNode.displayLabel)
    val crackmeNode = comNode.children.single()
    assertEquals("crackme", crackmeNode.displayLabel)
    val appNode = crackmeNode.children.single()
    assertEquals("app", appNode.displayLabel)
    assertEquals("com.crackme.app", appNode.packageName)
    val uiNode = appNode.children.single()
    assertEquals("ui", uiNode.displayLabel)
    assertEquals("com.crackme.app.ui", uiNode.packageName)
    val themeNode = uiNode.children.single()
    assertEquals("theme", themeNode.displayLabel)
    assertEquals("com.crackme.app.ui.theme", themeNode.packageName)

    // 4. Verify ClassesScreen renders APK Package banner, expands APK package levels, and shows APK badge
    val fakeDex =
      com.dex2c.studio.dex.DexParsedFile(
        sourceLabel = "CrackMe.apk",
        entryName = "classes.dex",
        rawBytes = ByteArray(112),
        header =
          com.dex2c.studio.dex.DexHeaderInfo(
            magic = "dex\n039\u0000",
            version = "039",
            checksum = 1L,
            sha1Hex = "00",
            fileSize = 112,
            endianTag = 0x12345678L,
            stringIdsSize = 10,
            typeIdsSize = 4,
            protoIdsSize = 2,
            fieldIdsSize = 0,
            methodIdsSize = 2,
            classDefsSize = 2,
          ),
        strings = emptyList(),
        types = emptyList(),
        protos = emptyList(),
        fields = emptyList(),
        methods = emptyList(),
        classes = listOf(baseClass, themeClass),
      )
    val readyState =
      com.dex2c.studio.ui.DexLoadState.Ready(
        sourceLabel = "CrackMe.apk",
        dexEntries = listOf(com.dex2c.studio.dex.ApkDexEntry("classes.dex", 112)),
        activeEntryName = "classes.dex",
        parsedDex = fakeDex,
        apkPackageName = "com.crackme.app",
      )

    composeTestRule.setContent {
      Dex2cTheme {
        com.dex2c.studio.ui.screens.ClassesScreen(
          loadState = readyState,
          classes = listOf(baseClass, themeClass),
          filterState = com.dex2c.studio.ui.ClassFilterState(),
          selectedMethodIds = emptySet(),
          allowInit = false,
          onSearchChange = {},
          onToggleHideFramework = {},
          onToggleOnlyBytecode = {},
          onToggleOnlySelected = {},
          onToggleClassSelection = {},
          onToggleMethodSelection = {},
          onInspectMethod = { _, _ -> },
          onInspectSelfApk = {},
        )
      }
    }

    composeTestRule.onNodeWithTag("classes_apk_package_banner").assertIsDisplayed()
    composeTestRule.onNodeWithTag("classes_focus_apk_pkg_btn").performClick()
    composeTestRule.waitForIdle()

    // Verify each package folder level along com -> crackme -> app is expanded cleanly and APK badge is shown
    composeTestRule.onNodeWithTag("pkg_row_com").assertIsDisplayed()
    composeTestRule.onNodeWithTag("pkg_row_com.crackme").assertIsDisplayed()
    composeTestRule.onNodeWithTag("pkg_row_com.crackme.app").assertIsDisplayed()
    composeTestRule.onNodeWithTag("pkg_apk_badge_com.crackme.app", useUnmergedTree = true).assertIsDisplayed()
    composeTestRule.onNodeWithTag("pkg_row_com.crackme.app.ui").assertIsDisplayed()
  }

  @Test
  fun performanceOptimization_memoizedDexModelsAndNestedPackageTreeAggregationWorkCorrectly() {
    val initMethod =
      com.dex2c.studio.dex.DexEncodedMethod(
        id = "Lcom/perf/app/Engine;-><init>()V",
        methodIndex = 0,
        methodRef =
          com.dex2c.studio.dex.DexMethodRef(
            definingClass = "Lcom/perf/app/Engine;",
            proto = com.dex2c.studio.dex.DexProto("V", "V", emptyList()),
            name = "<init>",
          ),
        accessFlags = 0x10001,
        isDirect = true,
        codeMeta =
          com.dex2c.studio.dex.DexCodeMeta(
            registersSize = 1,
            insSize = 1,
            outsSize = 1,
            triesSize = 0,
            insnsCount = 1,
            insnsOffsetBytes = 100,
          ),
      )
    val runMethod =
      com.dex2c.studio.dex.DexEncodedMethod(
        id = "Lcom/perf/app/Engine;->executeFast(Ljava/lang/String;)I",
        methodIndex = 1,
        methodRef =
          com.dex2c.studio.dex.DexMethodRef(
            definingClass = "Lcom/perf/app/Engine;",
            proto = com.dex2c.studio.dex.DexProto("IL", "I", listOf("Ljava/lang/String;")),
            name = "executeFast",
          ),
        accessFlags = 0x0001,
        isDirect = false,
        codeMeta =
          com.dex2c.studio.dex.DexCodeMeta(
            registersSize = 2,
            insSize = 2,
            outsSize = 0,
            triesSize = 0,
            insnsCount = 1,
            insnsOffsetBytes = 120,
          ),
      )
    val engineClass =
      com.dex2c.studio.dex.DexClassInfo(
        classIndex = 0,
        descriptor = "Lcom/perf/app/Engine;",
        fullName = "com.perf.app.Engine",
        simpleName = "Engine",
        packageName = "com.perf.app",
        superclass = "Ljava/lang/Object;",
        interfaces = emptyList(),
        sourceFile = "Engine.kt",
        accessFlags = 0x0001,
        staticFields = emptyList(),
        instanceFields = emptyList(),
        directMethods = listOf(initMethod),
        virtualMethods = listOf(runMethod),
      )

    // Verify memoized reference identity and flags without extra duplicate lowercase String fields
    assertTrue(engineClass.allMethods === engineClass.allMethods)
    assertEquals(2, engineClass.bytecodeMethodCount)
    assertTrue(initMethod.isInitConstructor)
    assertTrue(runMethod.hasBytecode)

    // Verify O(N) bottom-up buildNestedPackageTree aggregation
    val tree =
      com.dex2c.studio.ui.screens.buildNestedPackageTree(
        classes = listOf(engineClass),
        selectedMethodIds = setOf(runMethod.id),
        sortOrder = com.dex2c.studio.ui.ClassSortOrder.NAME_ASC,
        defaultPackageLabel = "(default)",
      )
    assertEquals(1, tree.size)
    val comNode = tree.first()
    assertEquals("com", comNode.packageName)
    assertEquals(2, comNode.totalBytecodeMethods)
    assertEquals(1, comNode.selectedMethodCount)
    assertEquals(1, comNode.subtreeClasses.size)

    // Verify two-stage Smali base syntax highlight + binary search method span resolution
    val smaliLines =
      listOf(
        ".class public Lcom/perf/app/Engine;",
        ".method public executeFast(Ljava/lang/String;)I",
        "    const-string v0, \"fast_token\"",
        "    return v0",
        ".end method",
      )
    val spans =
      listOf(
        com.dex2c.studio.dex.SmaliMethodSpan(
          name = "executeFast",
          signature = "executeFast(Ljava/lang/String;)I",
          startLineIndex = 1,
          endLineIndex = 4,
        )
      )
    val searchRes =
      com.dex2c.studio.ui.components.findSmaliSearchMatches(
        lines = smaliLines,
        methodSpans = spans,
        options = com.dex2c.studio.ui.components.SmaliSearchOptions(query = "fast_token"),
      )
    assertEquals(1, searchRes.totalOccurrences)
    assertEquals("executeFast", searchRes.matches.first().enclosingMethodName)

    // Verify memory optimization: releaseTransientPools frees constant-pool lists while preserving lazy Smali text
    val fakeDexWithPools =
      com.dex2c.studio.dex.DexParsedFile(
        sourceLabel = "Perf.apk",
        entryName = "classes.dex",
        rawBytes = ByteArray(112),
        header =
          com.dex2c.studio.dex.DexHeaderInfo(
            magic = "dex\\n039\\0",
            version = "039",
            checksum = 1L,
            sha1Hex = "00",
            fileSize = 112,
            endianTag = 0x12345678L,
            stringIdsSize = 1,
            typeIdsSize = 1,
            protoIdsSize = 1,
            fieldIdsSize = 0,
            methodIdsSize = 1,
            classDefsSize = 1,
          ),
        strings = listOf("Lcom/perf/app/Engine;"),
        types = listOf("Lcom/perf/app/Engine;"),
        protos = listOf(com.dex2c.studio.dex.DexProto("V", "V", emptyList())),
        fields = emptyList(),
        methods = listOf(initMethod.methodRef),
        classes = listOf(engineClass),
      )
    assertFalse(fakeDexWithPools.areTransientPoolsReleased)
    assertEquals("Lcom/perf/app/Engine;", fakeDexWithPools.stringAt(0))
    val trimmedDex = fakeDexWithPools.releaseTransientPools()
    assertTrue(trimmedDex.areTransientPoolsReleased)
    assertTrue(trimmedDex.strings.isEmpty())
    assertTrue(trimmedDex.methods.isEmpty())

    val smaliResult =
      com.dex2c.studio.dex.SmaliDisassemblyResult(
        lines = smaliLines,
        lineCount = smaliLines.size,
        methodCount = 1,
        fieldCount = 0,
        durationMs = 2L,
        methodSpans = spans,
      )
    assertEquals(smaliLines.joinToString("\n"), smaliResult.smaliText)
  }

  @Test
  fun `workflowGuideDialog renders interactive phases, search filter, and raw WORKFLOW markdown`() {
    var copiedText = ""
    composeTestRule.setContent {
      Dex2cTheme {
        com.dex2c.studio.ui.components.WorkflowGuideDialog(
          onDismiss = {},
          onCopyText = { copiedText = it },
        )
      }
    }

    composeTestRule.onNodeWithTag("workflow_guide_dialog").assertIsDisplayed()
    composeTestRule.onNodeWithTag("workflow_card_architecture").assertIsDisplayed()
    composeTestRule.onNodeWithTag("workflow_copy_md_btn").performClick()
    composeTestRule.waitForIdle()
    assertTrue(copiedText.contains("Dex2C Studio"))

    // Switch to Raw WORKFLOW.md tab
    composeTestRule.onNodeWithTag("workflow_mode_raw_btn").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("workflow_raw_markdown_box").assertIsDisplayed()
  }
}



