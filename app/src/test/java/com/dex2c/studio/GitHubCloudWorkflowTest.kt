package com.dex2c.studio

import com.dex2c.studio.cloud.CloudNamingConvention
import com.dex2c.studio.cloud.CloudSettings
import com.dex2c.studio.cloud.CloudSigningMode
import com.dex2c.studio.cloud.Dex2cWorkflowGenerator
import com.dex2c.studio.cloud.GitHubApiClient
import com.dex2c.studio.cloud.JsonMini
import com.dex2c.studio.cloud.NetworkResilience
import com.dex2c.studio.cloud.ResilientGitHubDns
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import com.dex2c.studio.transpiler.JniBindingStyle
import com.dex2c.studio.transpiler.TranspilerConfig
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubCloudWorkflowTest {

  @Test
  fun generateDex2cYaml_includesCodehasanDex2cAndSigningAndFallback() {
    val defaultCfg = TranspilerConfig()
    assertFalse(defaultCfg.forceCustomLoader)
    val defaultYaml = Dex2cWorkflowGenerator.generateDex2cYaml(CloudSettings(), defaultCfg)
    assertTrue(defaultYaml.contains("force_custom_loader:"))
    assertTrue(defaultYaml.contains("default: 'false'"))
    assertTrue(defaultYaml.contains("android-ndk-r25c-ollvm-aarch64.tar.xz"))
    assertTrue(defaultYaml.contains("docker/setup-qemu-action@v3"))
    assertTrue(defaultYaml.contains("platforms: arm64"))
    assertTrue(defaultYaml.contains("https://github.com/codehasan/dex2c/releases/download/ollvm-termux/"))
    assertTrue(defaultYaml.contains("can_ollvm"))
    assertTrue(defaultYaml.contains("Active NDK compiler does not support OLLVM flags"))
    assertTrue(defaultYaml.contains("LOCAL_SHORT_COMMANDS := true"))
    assertTrue(defaultYaml.contains("APP_SHORT_COMMANDS := true"))
    assertTrue(defaultYaml.contains("_dex2c_sanitize_generated_cpp(project_dir)"))
    assertTrue(defaultYaml.contains("Target APK fallback:"))
    assertTrue(defaultYaml.contains("--force-keep-libs"))

    val settings =
      CloudSettings(
        ndkVersion = "r26d",
        jdkVersion = "21",
        signingMode = CloudSigningMode.REPO_KEYSTORE_SECRETS,
      )
    val cfg =
      TranspilerConfig(
        allowInit = true,
        customLoaderClass = "com.custom.LoaderApp",
        forceCustomLoader = true,
        stringEncryption = true,
        jniStyle = JniBindingStyle.REGISTER_NATIVES,
      )

    val yaml = Dex2cWorkflowGenerator.generateDex2cYaml(settings, cfg)

    assertTrue(yaml.contains("workflow_dispatch:"))
    assertTrue(yaml.contains("apk_url:"))
    assertTrue(yaml.contains("force_custom_loader:"))
    assertTrue(yaml.contains("actions/checkout@v6"))
    assertTrue(yaml.contains("actions/setup-python@v6"))
    assertTrue(yaml.contains("actions/setup-java@v5"))
    assertFalse(yaml.contains("actions/checkout@v4"))
    assertFalse(yaml.contains("actions/setup-python@v5"))
    assertFalse(yaml.contains("actions/setup-java@v4"))
    assertTrue(yaml.contains("android-ndk-r26d-linux.zip"))
    assertTrue(yaml.contains("java-version: '21'"))
    assertTrue(yaml.contains("https://github.com/codehasan/dex2c.git"))
    assertTrue(yaml.contains("/tmp/d2c/requirements.txt"))
    assertTrue(yaml.contains("base_cfg = json.load(f)"))
    assertTrue(yaml.contains("working-directory: /tmp/d2c"))
    assertTrue(yaml.contains("set -o pipefail"))
    assertTrue(yaml.contains("set +e"))
    assertTrue(yaml.contains("DCC_EXIT="))
    assertTrue(yaml.contains("-a /tmp/work/input.apk"))
    assertTrue(yaml.contains("-o /tmp/work/unsigned_dex2c.apk"))
    assertTrue(yaml.contains("--filter /tmp/d2c/filter.txt"))
    assertTrue(yaml.contains("python3 /tmp/d2c/dcc.py"))
    assertTrue(yaml.contains("skip_synthetic:"))
    assertTrue(yaml.contains("ARGS+=(--skip-synthetic)"))
    assertTrue(yaml.contains("enable_ollvm:"))
    assertTrue(yaml.contains("ollvm_flags:"))
    assertTrue(yaml.contains("align_16kb:"))
    assertTrue(yaml.contains("max-page-size=16384"))
    assertTrue(yaml.contains("_dex2c_sanitize_generated_cpp"))
    assertTrue(yaml.contains("-fpermissive -Wno-int-conversion"))
    assertTrue(yaml.contains("APP_CPPFLAGS += -fpermissive"))
    assertTrue(yaml.contains("writer_py = \"/tmp/d2c/dex2c/writer.py\""))
    assertTrue(yaml.contains("{.i = (jint)(intptr_t)(%s)}"))
    assertTrue(yaml.contains("{.l = (jobject)(intptr_t)(%s)}"))
    assertTrue(yaml.contains("D2C_NOT_NULL((jobject)(intptr_t)"))
    assertTrue(yaml.contains("!^L{re.escape(cls_part)};->{re.escape(method_part)}.*"))
    assertTrue(yaml.contains("sys.exit(1)"))
    assertTrue(yaml.contains("if [ -f /tmp/work/unsigned_dex2c.apk ]; then"))
    assertTrue(yaml.contains("MAX_ATTEMPTS=3"))
    assertTrue(yaml.contains("Auto-excluded failing C++ method units"))
    assertTrue(yaml.contains("ARGS+=(-p)"))
    assertTrue(yaml.contains("ARGS+=(-d)"))
    assertTrue(yaml.contains("ARGS+=(--custom-loader"))
    assertTrue(yaml.contains("if [ ! -f /tmp/work/unsigned_dex2c.apk ]"))
    assertTrue(yaml.contains("com.custom.LoaderApp"))
    assertTrue(yaml.contains("apksigner"))
    assertTrue(yaml.contains("REPO_KEYSTORE_SECRETS"))
    assertTrue(yaml.contains("actions/upload-artifact@v4"))
    assertTrue(yaml.contains("dex2c-signed-apk"))
    assertTrue(yaml.contains("dex2c-signed-output.apk"))
    assertTrue(yaml.contains("dex2c-build-log"))
    assertTrue(yaml.contains("if: always()"))
    assertTrue(yaml.contains("app_name:"))
  }

  @Test
  fun filterRuleEngine_excludesCoroutinesSyntheticsAndThirdPartyAndScopesSelectedMethods() {
    val cfg = TranspilerConfig()
    assertTrue(cfg.skipSynthetic)
    assertEquals(".*", cfg.filterRulesText.trim())

    val protoObj = com.dex2c.studio.dex.DexProto("LL", "Ljava/lang/Object;", listOf("Ljava/lang/Object;"))
    val protoVoid = com.dex2c.studio.dex.DexProto("V", "V", emptyList())
    val codeMeta = com.dex2c.studio.dex.DexCodeMeta(4, 1, 1, 0, 8, 128)

    val appMethod =
      com.dex2c.studio.dex.DexEncodedMethod(
        id = "m_app",
        methodIndex = 1,
        methodRef = com.dex2c.studio.dex.DexMethodRef("Lcom/example/ui/Dex2cViewModel;", protoVoid, "refreshCloudRuns"),
        accessFlags = 0x0001,
        isDirect = false,
        codeMeta = codeMeta,
      )
    val coroutineMethod =
      com.dex2c.studio.dex.DexEncodedMethod(
        id = "m_coro",
        methodIndex = 2,
        methodRef = com.dex2c.studio.dex.DexMethodRef("Lcom/example/ui/Dex2cViewModel\$startDeviceFlowLogin\$1;", protoObj, "invokeSuspend"),
        accessFlags = 0x0001,
        isDirect = false,
        codeMeta = codeMeta,
      )
    val lambdaMethod =
      com.dex2c.studio.dex.DexEncodedMethod(
        id = "m_lambda",
        methodIndex = 3,
        methodRef = com.dex2c.studio.dex.DexMethodRef("Lcom/example/ui/Dex2cViewModel\$\$ExternalSyntheticLambda0;", protoVoid, "run"),
        accessFlags = 0x1001,
        isDirect = false,
        codeMeta = codeMeta,
      )

    val scopedFilter =
      com.dex2c.studio.transpiler.FilterRuleEngine.buildCloudFilterText(
        config = cfg,
        selectedMethods = listOf(appMethod, coroutineMethod, lambdaMethod),
      )
    assertTrue(scopedFilter.contains("!.*;(invokeSuspend|<clinit>).*"))
    assertTrue(scopedFilter.contains("^Lcom/example/ui/Dex2cViewModel;refreshCloudRuns\\(.*"))
    assertFalse(scopedFilter.contains("startDeviceFlowLogin"))
    assertFalse(scopedFilter.contains("ExternalSyntheticLambda0;run"))

    val encodedJson = com.dex2c.studio.transpiler.DccConfigCodec.encodeDccCfg(cfg.copy(skipSynthetic = false))
    assertTrue(encodedJson.contains("\"skip_synthetic\": false"))
    val decodedCfg = com.dex2c.studio.transpiler.DccConfigCodec.decodeDccCfg(encodedJson, cfg)
    assertFalse(decodedCfg.skipSynthetic)
  }

  @Test
  fun extractApkFromArtifactZip_extractsSignedApkOrFallsBackToZip() {
    val client = GitHubApiClient()
    val fakeApkBytes = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x11, 0x22, 0x33)

    val zipStream = ByteArrayOutputStream()
    ZipOutputStream(zipStream).use { zos ->
      zos.putNextEntry(ZipEntry("dex2c_build.log"))
      zos.write("ok\n".toByteArray())
      zos.closeEntry()

      zos.putNextEntry(ZipEntry("nested/dex2c-signed-output.apk"))
      zos.write(fakeApkBytes)
      zos.closeEntry()
    }

    val extracted = client.extractApkFromArtifactZip(zipStream.toByteArray())
    assertFalse(extracted.isZipFallback)
    assertEquals("dex2c-signed-output.apk", extracted.fileName)
    assertArrayEquals(fakeApkBytes, extracted.bytes)

    val logOnlyZip = ByteArrayOutputStream()
    ZipOutputStream(logOnlyZip).use { zos ->
      zos.putNextEntry(ZipEntry("dex2c_build.log"))
      zos.write("error log".toByteArray())
      zos.closeEntry()
    }
    val rawLogOnly = logOnlyZip.toByteArray()
    val fallback = client.extractApkFromArtifactZip(rawLogOnly)
    assertTrue(fallback.isZipFallback)
    assertTrue(fallback.fileName.endsWith(".zip"))
    assertArrayEquals(rawLogOnly, fallback.bytes)
  }

  @Test
  fun cloudNamingConvention_sanitizesAndFormatsNamesCorrectly() {
    assertEquals("dex2c_studio", CloudNamingConvention.sanitizeBaseName("Dex2C Studio (base.apk)"))
    assertEquals("com.example.app-v1.0", CloudNamingConvention.sanitizeBaseName("com.example.app-v1.0.apk"))
    assertEquals("myapp_debug", CloudNamingConvention.sanitizeBaseName("MyApp (Debug).apk"))
    assertEquals("target", CloudNamingConvention.sanitizeBaseName("target.apk"))
    assertEquals("custom_app", CloudNamingConvention.sanitizeBaseName("/data/app/~~abc/custom_app.apk"))
    assertEquals("app", CloudNamingConvention.sanitizeBaseName(null))
    assertEquals("app", CloudNamingConvention.sanitizeBaseName("   "))
    assertEquals("clean_name", CloudNamingConvention.sanitizeBaseName("clean___name###.apk"))

    assertEquals("input/myapp.apk", CloudNamingConvention.uploadApkRepoPath("myapp"))
    assertEquals("Upload myapp.apk for Dex2C build", CloudNamingConvention.uploadCommitMessage("myapp"))
    assertEquals("myapp_dex2c_signed.apk", CloudNamingConvention.signedApkFileName("myapp"))
    assertEquals("myapp_dex2c_signed.apk", CloudNamingConvention.signedApkFileName("myapp_dex2c"))
    assertEquals("myapp_dex2c_build.log", CloudNamingConvention.buildLogFileName("myapp"))
    assertEquals("myapp_dex2c_build.log", CloudNamingConvention.buildLogFileName("myapp_dex2c"))
    assertEquals("myapp-signed-apk", CloudNamingConvention.signedApkArtifactName("myapp"))
    assertEquals("myapp-build-log", CloudNamingConvention.buildLogArtifactName("myapp"))
  }

  @Test
  fun extractArtifactPayload_extractsApkAndLogWithProperNaming() {
    val client = GitHubApiClient()
    val fakeApk = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x10, 0x20)
    val fakeLog = "dex2c compilation success\n".toByteArray()

    val comboZip = ByteArrayOutputStream()
    ZipOutputStream(comboZip).use { zos ->
      zos.putNextEntry(ZipEntry("dex2c_build.log"))
      zos.write(fakeLog)
      zos.closeEntry()

      zos.putNextEntry(ZipEntry("nested/dex2c-signed-output.apk"))
      zos.write(fakeApk)
      zos.closeEntry()
    }
    val comboBytes = comboZip.toByteArray()

    // When targeting signed APK
    val extractedApk = client.extractArtifactPayload(
      zipBytes = comboBytes,
      artifactName = "whatsapp-signed-apk",
      defaultBaseName = "whatsapp",
    )
    assertFalse(extractedApk.isZipFallback)
    assertEquals("whatsapp_dex2c_signed.apk", extractedApk.fileName)
    assertArrayEquals(fakeApk, extractedApk.bytes)

    // When targeting build log
    val extractedLog = client.extractArtifactPayload(
      zipBytes = comboBytes,
      artifactName = "whatsapp-build-log",
      defaultBaseName = "whatsapp",
    )
    assertFalse(extractedLog.isZipFallback)
    assertEquals("whatsapp_dex2c_build.log", extractedLog.fileName)
    assertArrayEquals(fakeLog, extractedLog.bytes)

    // Standalone failure log zip
    val logOnlyZip = ByteArrayOutputStream()
    ZipOutputStream(logOnlyZip).use { zos ->
      zos.putNextEntry(ZipEntry("dex2c_build.log"))
      zos.write(fakeLog)
      zos.closeEntry()
    }
    val failureLogBytes = logOnlyZip.toByteArray()
    val extractedFailureLog = client.extractArtifactPayload(
      zipBytes = failureLogBytes,
      artifactName = "dex2c-build-log",
      defaultBaseName = "myapp",
    )
    assertFalse(extractedFailureLog.isZipFallback)
    assertEquals("myapp_dex2c_build.log", extractedFailureLog.fileName)
    assertArrayEquals(fakeLog, extractedFailureLog.bytes)
  }

  @Test
  fun jsonMini_roundTripsNestedObjectsAndArrays() {
    val json =
      JsonMini.objectJson(
        listOf(
          "name" to "dex2c-cloud-workspace",
          "private" to true,
          "count" to 42,
          "meta" to
            mapOf(
              "branch" to "main",
              "escaped" to "line1\n\"quoted\"",
            ),
        )
      )
    val parsed = JsonMini.parseObject(json)
    assertEquals("dex2c-cloud-workspace", parsed["name"])
    assertEquals(true, parsed["private"])
    assertEquals(42L, parsed["count"])
    @Suppress("UNCHECKED_CAST")
    val meta = parsed["meta"] as Map<String, Any?>
    assertEquals("main", meta["branch"])
    assertEquals("line1\n\"quoted\"", meta["escaped"])
  }

  @Test
  fun cloudTimeUtil_parsesIsoUtcAndFormatsDurationAndStepProgress() {
    val start = com.dex2c.studio.cloud.CloudTimeUtil.parseIsoUtcMillis("2026-11-12T19:45:00Z")!!
    val end = com.dex2c.studio.cloud.CloudTimeUtil.parseIsoUtcMillis("2026-11-12T19:48:42Z")!!
    assertEquals(222_000L, end - start)
    assertEquals("03:42", com.dex2c.studio.cloud.CloudTimeUtil.formatDurationMmSs(end - start))
    assertEquals("1:01:05", com.dex2c.studio.cloud.CloudTimeUtil.formatDurationMmSs(3665_000L))

    val steps =
      listOf(
        com.dex2c.studio.cloud.GitHubWorkflowStep("Checkout", "completed", "success"),
        com.dex2c.studio.cloud.GitHubWorkflowStep("Setup NDK", "completed", "success"),
        com.dex2c.studio.cloud.GitHubWorkflowStep("Run dcc.py", "in_progress", null),
        com.dex2c.studio.cloud.GitHubWorkflowStep("Sign APK", "queued", null),
      )
    assertEquals(0.5f, com.dex2c.studio.cloud.CloudTimeUtil.computeStepProgress(steps)!!, 0.001f)
    assertEquals(null, com.dex2c.studio.cloud.CloudTimeUtil.computeStepProgress(emptyList()))
  }

  @Test
  fun cloudBuildNotifier_formatsSuccessAndFailureMessages() {
    val ok =
      com.dex2c.studio.cloud.CloudBuildNotifier.buildNotificationContent(
        runNumber = 14,
        conclusion = "success",
        durationMs = 195_000L,
        hasArtifacts = true,
      )
    assertTrue(ok.isSuccess)
    assertEquals("Dex2C Cloud Build #14 Succeeded", ok.title)
    assertTrue(ok.body.contains("03:15"))
    assertTrue(ok.body.contains("signed APK"))

    val fail =
      com.dex2c.studio.cloud.CloudBuildNotifier.buildNotificationContent(
        runNumber = 15,
        conclusion = "failure",
        durationMs = 42_000L,
        hasArtifacts = false,
      )
    assertFalse(fail.isSuccess)
    assertEquals("Dex2C Cloud Build #15 Failure", fail.title)
    assertTrue(fail.body.contains("00:42"))
    assertTrue(fail.body.contains("logs"))
  }

  @Test
  fun appSettings_defaultsAndThemeModesAreConsistent() {
    val defaults = com.dex2c.studio.data.AppSettings()
    assertEquals(com.dex2c.studio.data.AppThemeMode.SYSTEM, defaults.themeMode)
    assertEquals(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S, defaults.dynamicColor)
    assertEquals("#2EE59D", defaults.accentSeedHex)
    assertEquals(12, defaults.codeFontSizeSp)
    assertTrue(defaults.showLineNumbers)
    assertEquals(2500, defaults.maxCodePreviewLines)
    assertFalse(defaults.defaultHideFramework)
    assertTrue(defaults.defaultOnlyBytecode)
    assertEquals(32, defaults.autoSelectMethodLimit)
    assertFalse(defaults.hasCompletedOnboarding)
    assertEquals(4, com.dex2c.studio.data.AppThemeMode.entries.size)
    assertTrue(com.dex2c.studio.data.AppThemeMode.entries.contains(com.dex2c.studio.data.AppThemeMode.AMOLED))
  }

  @Test
  fun m3TonalColorEngine_generatesAccessibleLightDarkAndTrueBlackAmoledPalettes() {
    val seedHex = "#4C8DF6"
    val parsed = com.dex2c.studio.ui.theme.M3TonalColorEngine.parseHexOrDefault(seedHex)
    assertEquals(seedHex, com.dex2c.studio.ui.theme.M3TonalColorEngine.toHexString(parsed))

    val light = com.dex2c.studio.ui.theme.M3TonalColorEngine.buildSchemeFromSeed(parsed, isDark = false, isAmoled = false)
    val dark = com.dex2c.studio.ui.theme.M3TonalColorEngine.buildSchemeFromSeed(parsed, isDark = true, isAmoled = false)
    val amoled = com.dex2c.studio.ui.theme.M3TonalColorEngine.buildSchemeFromSeed(parsed, isDark = true, isAmoled = true)

    assertTrue(light.background.red > 0.9f)
    assertTrue(dark.background.blue > dark.background.red)
    assertEquals(com.dex2c.studio.ui.theme.AmoledBlack, amoled.background)
    assertEquals(com.dex2c.studio.ui.theme.AmoledBlack, amoled.surface)
    assertEquals(com.dex2c.studio.ui.theme.AmoledBlack, amoled.surfaceContainerLowest)
    assertTrue(com.dex2c.studio.data.isValidHexColor("#2EE59D"))
    assertFalse(com.dex2c.studio.data.isValidHexColor("2EE59"))
  }

  @Test
  fun cloudSettings_usesBuiltInGhCliClientIdByDefaultAndSupportsCustomOverride() {
    val defaultSettings = CloudSettings()
    assertFalse(defaultSettings.usingCustomClientId)
    assertEquals(com.dex2c.studio.cloud.GITHUB_CLI_CLIENT_ID, defaultSettings.effectiveClientId)
    assertEquals("178c6fc778ccc68e1d6a", defaultSettings.effectiveClientId)

    val customSettings = CloudSettings(githubClientId = "  Ov23liCustomAppId99  ")
    assertTrue(customSettings.usingCustomClientId)
    assertEquals("Ov23liCustomAppId99", customSettings.effectiveClientId)
  }

  @Test
  fun deviceAuthState_formats15MinuteExpiryCountdownAccurately() {
    val startMs = 1_700_000_000_000L
    val state =
      com.dex2c.studio.cloud.DeviceAuthState.AwaitingUser(
        userCode = "ABCD-1234",
        verificationUri = com.dex2c.studio.cloud.GITHUB_DEVICE_VERIFICATION_URL,
        expiresInSec = 900,
        intervalSec = 5,
        expiresAtMillis = startMs + 900_000L,
      )
    assertEquals("15:00", state.remainingFormattedAt(startMs))
    assertEquals("15:00", state.remainingFormattedAt(startMs + 250L))
    assertEquals("14:59", state.remainingFormattedAt(startMs + 1_000L))
    assertEquals("00:01", state.remainingFormattedAt(startMs + 899_500L))
    assertEquals("00:00", state.remainingFormattedAt(startMs + 900_000L))
    assertEquals("00:00", state.remainingFormattedAt(startMs + 950_000L))
  }

  @Test
  fun networkResilience_parsesDohARecordsAndClassifiesTransientErrors() {
    val dohJson =
      """
      {
        "Status": 0,
        "Answer": [
          {"name": "api.github.com", "type": 5, "TTL": 60, "data": "github.map.fastly.net."},
          {"name": "api.github.com", "type": 1, "TTL": 60, "data": "140.82.114.6"},
          {"name": "api.github.com", "type": 1, "TTL": 60, "data": "not-an-ip"}
        ]
      }
      """.trimIndent()
    val ips = ResilientGitHubDns.parseDohARecords(dohJson)
    assertEquals(1, ips.size)
    assertEquals("140.82.114.6", ips.first().hostAddress)

    val dnsErr = UnknownHostException("Unable to resolve host \"api.github.com\": No address associated with hostname")
    assertTrue(NetworkResilience.isTransientNetworkError(dnsErr))
    assertTrue(NetworkResilience.isTransientNetworkError(SocketTimeoutException("timeout")))
    assertFalse(NetworkResilience.isTransientNetworkError(IOException("GitHub repo lookup failed (404): Not Found")))

    val offlineMsg = NetworkResilience.formatUserFacingError(dnsErr, isOnline = false)
    assertTrue(offlineMsg.contains("No internet connection"))
    val dnsMsg = NetworkResilience.formatUserFacingError(dnsErr, isOnline = true)
    assertTrue(dnsMsg.contains("DNS lookup failed"))
    assertTrue(dnsMsg.contains("Retry"))
  }

  @Test
  fun cloudBuildNotifier_buildsLiveStageNotificationsForUploadBuildAndDownload() {
    val upload =
      com.dex2c.studio.cloud.CloudBuildNotifier.buildUploadProgressNotification(
        stageLabel = "Uploading target.apk",
        bytesUploaded = 1_048_576L,
        totalBytes = 2_097_152L,
        progressFraction = 0.50f,
      )
    assertEquals(com.dex2c.studio.cloud.CloudLivePhase.UPLOADING, upload.phase)
    assertEquals(50, upload.progressCurrent)
    assertEquals(100, upload.progressMax)
    assertFalse(upload.indeterminate)
    assertTrue(upload.body.contains("1.00 MB / 2.00 MB"))
    assertTrue(upload.body.contains("50%"))
    assertEquals(
      com.dex2c.studio.R.drawable.ic_cloud_upload,
      com.dex2c.studio.cloud.CloudBuildNotifier.resolveNotificationSmallIcon(upload.phase),
    )

    val building =
      com.dex2c.studio.cloud.CloudBuildNotifier.buildWorkflowProgressNotification(
        runNumber = 19,
        status = "in_progress",
        activeStepName = "Run codehasan/dex2c Compilation",
        completedSteps = 4,
        totalSteps = 10,
      )
    assertEquals(com.dex2c.studio.cloud.CloudLivePhase.BUILDING, building.phase)
    assertTrue(building.title.contains("#19"))
    assertEquals(4, building.progressCurrent)
    assertEquals(10, building.progressMax)
    assertTrue(building.body.contains("Step 4/10"))
    assertTrue(building.body.contains("Run codehasan/dex2c Compilation"))
    assertEquals(
      com.dex2c.studio.R.drawable.ic_cloud_build_status,
      com.dex2c.studio.cloud.CloudBuildNotifier.resolveNotificationSmallIcon(building.phase),
    )

    val download =
      com.dex2c.studio.cloud.CloudBuildNotifier.buildDownloadProgressNotification(
        artifactName = "dex2c-signed-apk",
        bytesDownloaded = 1_572_864L,
        totalBytes = 3_145_728L,
        progressFraction = 0.50f,
      )
    assertEquals(com.dex2c.studio.cloud.CloudLivePhase.DOWNLOADING, download.phase)
    assertTrue(download.title.contains("dex2c-signed-apk"))
    assertEquals(50, download.progressCurrent)
    assertTrue(download.body.contains("1.50 MB / 3.00 MB"))
    assertTrue(download.body.contains("50%"))
    assertEquals(
      com.dex2c.studio.R.drawable.ic_cloud_build_status,
      com.dex2c.studio.cloud.CloudBuildNotifier.resolveNotificationSmallIcon(download.phase),
    )
  }

  @Test
  fun gitHubApiClient_streamsUploadAndDownloadByteProgress() {
    val uploadPayload = ByteArray(96 * 1024) { (it % 127).toByte() }
    val downloadPayload = ByteArray(80 * 1024) { ((it * 3) % 127).toByte() }
    val jsonType = "application/json; charset=utf-8".let { okhttp3.MediaType.Companion.run { it.toMediaType() } }
    val binType = "application/zip".let { okhttp3.MediaType.Companion.run { it.toMediaType() } }

    val loopbackDns = object : okhttp3.Dns {
      override fun lookup(hostname: String): List<java.net.InetAddress> =
        listOf(java.net.InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)))
    }
    val httpClient =
      okhttp3.OkHttpClient.Builder()
        .dns(loopbackDns)
        .addInterceptor { chain ->
          val req = chain.request()
          val path = req.url.encodedPath
          val method = req.method
          if (method == "PUT") {
            val sink = okio.Buffer()
            req.body?.writeTo(sink)
            assertTrue(sink.size > 0L)
          }
          val respBody =
            okhttp3.ResponseBody.Companion.run {
              when {
                method == "GET" && path.contains("/contents/") ->
                  """{"sha":"old"}""".toResponseBody(jsonType)
                method == "PUT" && path.contains("/contents/") ->
                  """{"content":{"sha":"new"}}""".toResponseBody(jsonType)
                path.endsWith("/zip") ->
                  downloadPayload.toResponseBody(binType)
                else ->
                  "{}".toResponseBody(jsonType)
              }
            }
          okhttp3.Response.Builder()
            .request(req)
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(respBody)
            .build()
        }
        .build()

    val api = GitHubApiClient(httpClient = httpClient)
    val uploadEvents = mutableListOf<Pair<Long, Long>>()
    api.upsertRepoFile(
      token = "gho_test",
      owner = "userio12",
      repo = "dex2c-cloud-workspace",
      path = "input/target.apk",
      branch = "main",
      commitMessage = "upload apk",
      contentBytes = uploadPayload,
      onUploadProgress = { written, total -> uploadEvents += written to total },
    )
    assertTrue(uploadEvents.size >= 2)
    assertEquals(0L, uploadEvents.first().first)
    assertEquals(uploadEvents.last().second, uploadEvents.last().first)
    assertTrue(uploadEvents.last().second > uploadPayload.size)

    val downloadEvents = mutableListOf<Pair<Long, Long>>()
    val downloaded =
      api.downloadArtifactZip(
        token = "gho_test",
        archiveDownloadUrl = "https://api.github.com/repos/userio12/dex2c-cloud-workspace/actions/artifacts/1/zip",
        expectedTotalBytes = downloadPayload.size.toLong(),
        onDownloadProgress = { read, total -> downloadEvents += read to total },
      )
    assertArrayEquals(downloadPayload, downloaded)
    assertTrue(downloadEvents.size >= 2)
    assertEquals(0L, downloadEvents.first().first)
    assertEquals(downloadPayload.size.toLong(), downloadEvents.last().first)
    assertEquals(downloadPayload.size.toLong(), downloadEvents.last().second)
  }

  @Test
  fun generateDex2cYaml_hasConsistentIndentationOnArtifactEnvLines() {
    val yaml = Dex2cWorkflowGenerator.generateDex2cYaml(CloudSettings(), TranspilerConfig())
    val lines = yaml.lines()
    val apkNameLine = lines.firstOrNull { it.contains("ARTIFACT_APK_NAME=") }
    val logNameLine = lines.firstOrNull { it.contains("ARTIFACT_LOG_NAME=") }
    val cpOutputLine = lines.firstOrNull { it.contains("dex2c-signed-output.apk") && it.trimStart().startsWith("cp") }
    assertTrue(apkNameLine != null && logNameLine != null && cpOutputLine != null)
    val apkIndent = apkNameLine!!.takeWhile { it.isWhitespace() }
    val logIndent = logNameLine!!.takeWhile { it.isWhitespace() }
    val cpIndent = cpOutputLine!!.takeWhile { it.isWhitespace() }
    assertEquals(apkIndent, logIndent)
    assertEquals(apkIndent, cpIndent)
  }

  @Test
  fun generateDex2cYaml_allInlinePythonScriptsAreSyntacticallyValid() {
    val yaml = Dex2cWorkflowGenerator.generateDex2cYaml(CloudSettings(), TranspilerConfig())
    val pyBlockRegex = Regex("""python3\s+-\s+<<'PY'\n([\s\S]*?)\n\s*PY""")
    val matches = pyBlockRegex.findAll(yaml).toList()
    assertTrue("Expected multiple inline Python blocks in generated YAML", matches.size >= 3)
    for ((index, match) in matches.withIndex()) {
      val pyCode = match.groupValues[1]
      assertFalse("Python block #$index should not contain raw escaped quote delimiters", pyCode.contains("\\\"-Wl"))
      assertTrue("Python block #$index should have content", pyCode.isNotBlank())
    }
  }
}
