package com.example

import com.example.cloud.CloudSettings
import com.example.cloud.CloudSigningMode
import com.example.cloud.Dex2cWorkflowGenerator
import com.example.cloud.GitHubApiClient
import com.example.cloud.JsonMini
import com.example.cloud.NetworkResilience
import com.example.cloud.ResilientGitHubDns
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import com.example.transpiler.JniBindingStyle
import com.example.transpiler.TranspilerConfig
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
  fun generateDex2cYaml_includesKirlifD2cAndSigningAndFallback() {
    val defaultCfg = TranspilerConfig()
    assertFalse(defaultCfg.forceCustomLoader)
    val defaultYaml = Dex2cWorkflowGenerator.generateDex2cYaml(CloudSettings(), defaultCfg)
    assertTrue(defaultYaml.contains("force_custom_loader:"))
    assertTrue(defaultYaml.contains("default: 'false'"))

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
    assertTrue(yaml.contains("https://github.com/Kirlif/d2c.git"))
    assertTrue(yaml.contains("/tmp/d2c/requirements.txt"))
    assertTrue(yaml.contains("base_cfg = json.load(f)"))
    assertTrue(yaml.contains("working-directory: /tmp/d2c"))
    assertTrue(yaml.contains("set -o pipefail"))
    assertTrue(yaml.contains("-i /tmp/work/input.apk"))
    assertTrue(yaml.contains("-o /tmp/work/unsigned_dex2c.apk"))
    assertTrue(yaml.contains("-f /tmp/d2c/filter.txt"))
    assertFalse(yaml.contains("-a /tmp/work/input.apk"))
    assertTrue(yaml.contains("python3 /tmp/d2c/dcc.py"))
    assertTrue(yaml.contains("ARGS+=(-a)"))
    assertTrue(yaml.contains("ARGS+=(-p)"))
    assertTrue(yaml.contains("ARGS+=(-d)"))
    assertTrue(yaml.contains("ARGS+=(-c"))
    assertTrue(yaml.contains("ARGS+=(-r)"))
    assertTrue(yaml.contains("if [ ! -f /tmp/work/unsigned_dex2c.apk ]"))
    assertTrue(yaml.contains("com.custom.LoaderApp"))
    assertTrue(yaml.contains("apksigner"))
    assertTrue(yaml.contains("REPO_KEYSTORE_SECRETS"))
    assertTrue(yaml.contains("actions/upload-artifact@v4"))
    assertTrue(yaml.contains("dex2c-signed-apk"))
    assertTrue(yaml.contains("dex2c-signed-output.apk"))
    assertTrue(yaml.contains("dex2c-build-log"))
    assertTrue(yaml.contains("if: failure()"))
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
    val start = com.example.cloud.CloudTimeUtil.parseIsoUtcMillis("2026-11-12T19:45:00Z")!!
    val end = com.example.cloud.CloudTimeUtil.parseIsoUtcMillis("2026-11-12T19:48:42Z")!!
    assertEquals(222_000L, end - start)
    assertEquals("03:42", com.example.cloud.CloudTimeUtil.formatDurationMmSs(end - start))
    assertEquals("1:01:05", com.example.cloud.CloudTimeUtil.formatDurationMmSs(3665_000L))

    val steps =
      listOf(
        com.example.cloud.GitHubWorkflowStep("Checkout", "completed", "success"),
        com.example.cloud.GitHubWorkflowStep("Setup NDK", "completed", "success"),
        com.example.cloud.GitHubWorkflowStep("Run dcc.py", "in_progress", null),
        com.example.cloud.GitHubWorkflowStep("Sign APK", "queued", null),
      )
    assertEquals(0.5f, com.example.cloud.CloudTimeUtil.computeStepProgress(steps)!!, 0.001f)
    assertEquals(null, com.example.cloud.CloudTimeUtil.computeStepProgress(emptyList()))
  }

  @Test
  fun cloudBuildNotifier_formatsSuccessAndFailureMessages() {
    val ok =
      com.example.cloud.CloudBuildNotifier.buildNotificationContent(
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
      com.example.cloud.CloudBuildNotifier.buildNotificationContent(
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
    val defaults = com.example.data.AppSettings()
    assertEquals(com.example.data.AppThemeMode.DARK, defaults.themeMode)
    assertFalse(defaults.dynamicColor)
    assertEquals("#2EE59D", defaults.accentSeedHex)
    assertEquals(12, defaults.codeFontSizeSp)
    assertTrue(defaults.showLineNumbers)
    assertEquals(2500, defaults.maxCodePreviewLines)
    assertTrue(defaults.defaultHideFramework)
    assertTrue(defaults.defaultOnlyBytecode)
    assertEquals(32, defaults.autoSelectMethodLimit)
    assertTrue(defaults.preferRustJniBridge)
    assertFalse(defaults.hasCompletedOnboarding)
    assertEquals(4, com.example.data.AppThemeMode.entries.size)
    assertTrue(com.example.data.AppThemeMode.entries.contains(com.example.data.AppThemeMode.AMOLED))
  }

  @Test
  fun m3TonalColorEngine_generatesAccessibleLightDarkAndTrueBlackAmoledPalettes() {
    val seedHex = "#4C8DF6"
    val parsed = com.example.ui.theme.M3TonalColorEngine.parseHexOrDefault(seedHex)
    assertEquals(seedHex, com.example.ui.theme.M3TonalColorEngine.toHexString(parsed))

    val light = com.example.ui.theme.M3TonalColorEngine.buildSchemeFromSeed(parsed, isDark = false, isAmoled = false)
    val dark = com.example.ui.theme.M3TonalColorEngine.buildSchemeFromSeed(parsed, isDark = true, isAmoled = false)
    val amoled = com.example.ui.theme.M3TonalColorEngine.buildSchemeFromSeed(parsed, isDark = true, isAmoled = true)

    assertTrue(light.background.red > 0.9f)
    assertTrue(dark.background.blue > dark.background.red)
    assertEquals(com.example.ui.theme.AmoledBlack, amoled.background)
    assertEquals(com.example.ui.theme.AmoledBlack, amoled.surface)
    assertEquals(com.example.ui.theme.AmoledBlack, amoled.surfaceContainerLowest)
    assertTrue(com.example.data.isValidHexColor("#2EE59D"))
    assertFalse(com.example.data.isValidHexColor("2EE59"))
  }

  @Test
  fun cloudSettings_usesBuiltInGhCliClientIdByDefaultAndSupportsCustomOverride() {
    val defaultSettings = CloudSettings()
    assertFalse(defaultSettings.usingCustomClientId)
    assertEquals(com.example.cloud.GITHUB_CLI_CLIENT_ID, defaultSettings.effectiveClientId)
    assertEquals("178c6fc778ccc68e1d6a", defaultSettings.effectiveClientId)

    val customSettings = CloudSettings(githubClientId = "  Ov23liCustomAppId99  ")
    assertTrue(customSettings.usingCustomClientId)
    assertEquals("Ov23liCustomAppId99", customSettings.effectiveClientId)
  }

  @Test
  fun deviceAuthState_formats15MinuteExpiryCountdownAccurately() {
    val startMs = 1_700_000_000_000L
    val state =
      com.example.cloud.DeviceAuthState.AwaitingUser(
        userCode = "ABCD-1234",
        verificationUri = com.example.cloud.GITHUB_DEVICE_VERIFICATION_URL,
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
}
