package com.example

import com.example.cloud.CloudSettings
import com.example.cloud.CloudSigningMode
import com.example.cloud.Dex2cWorkflowGenerator
import com.example.cloud.GitHubApiClient
import com.example.cloud.JsonMini
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
        stringEncryption = true,
        jniStyle = JniBindingStyle.REGISTER_NATIVES,
      )

    val yaml = Dex2cWorkflowGenerator.generateDex2cYaml(settings, cfg)

    assertTrue(yaml.contains("workflow_dispatch:"))
    assertTrue(yaml.contains("apk_url:"))
    assertTrue(yaml.contains("android-ndk-r26d-linux.zip"))
    assertTrue(yaml.contains("java-version: '21'"))
    assertTrue(yaml.contains("https://github.com/Kirlif/d2c.git"))
    assertTrue(yaml.contains("python3 /tmp/d2c/dcc.py"))
    assertTrue(yaml.contains("--allow-init"))
    assertTrue(yaml.contains("--obfuscate"))
    assertTrue(yaml.contains("--dynamic-register"))
    assertTrue(yaml.contains("com.custom.LoaderApp"))
    assertTrue(yaml.contains("apksigner"))
    assertTrue(yaml.contains("REPO_KEYSTORE_SECRETS"))
    assertTrue(yaml.contains("actions/upload-artifact@v4"))
    assertTrue(yaml.contains("dex2c-signed-output.apk"))
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
}
