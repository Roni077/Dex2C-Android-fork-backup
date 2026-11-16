package com.dex2c.studio.security

import com.dex2c.studio.cloud.CloudSettings
import com.dex2c.studio.cloud.ResilientGitHubDns
import com.dex2c.studio.data.KeystoreTokenEncryptor
import com.dex2c.studio.data.TokenEncryptor
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class Phase3SecurityAndNetworkTest {

  @Test
  fun testBackupRulesExcludeCloudPreferences() {
    val backupRulesFile = File("src/main/res/xml/backup_rules.xml").let {
      if (it.exists()) it else File("app/src/main/res/xml/backup_rules.xml")
    }
    assertTrue("backup_rules.xml must exist", backupRulesFile.exists())
    val content = backupRulesFile.readText()

    assertTrue("Must exclude cloud preferences file domain", content.contains("""<exclude domain="file" path="datastore/dex2c_cloud_prefs.preferences_pb""""))
    assertTrue("Must exclude cloud preferences sharedpref domain", content.contains("""<exclude domain="sharedpref" path="dex2c_cloud_prefs.preferences_pb""""))
    assertFalse("Must not have unconfigured empty backup content", content.contains("Sample backup rules file"))
  }

  @Test
  fun testDataExtractionRulesExcludeCloudPreferencesFromCloudAndDeviceTransfer() {
    val dataRulesFile = File("src/main/res/xml/data_extraction_rules.xml").let {
      if (it.exists()) it else File("app/src/main/res/xml/data_extraction_rules.xml")
    }
    assertTrue("data_extraction_rules.xml must exist", dataRulesFile.exists())
    val content = dataRulesFile.readText()

    // Both cloud-backup and device-transfer must contain exclusions
    val cloudBackupBlock = content.substringAfter("<cloud-backup>").substringBefore("</cloud-backup>")
    val deviceTransferBlock = content.substringAfter("<device-transfer>").substringBefore("</device-transfer>")

    assertTrue(cloudBackupBlock.contains("""<exclude domain="file" path="datastore/dex2c_cloud_prefs.preferences_pb""""))
    assertTrue(cloudBackupBlock.contains("""<exclude domain="sharedpref" path="dex2c_cloud_prefs.preferences_pb""""))

    assertTrue(deviceTransferBlock.contains("""<exclude domain="file" path="datastore/dex2c_cloud_prefs.preferences_pb""""))
    assertTrue(deviceTransferBlock.contains("""<exclude domain="sharedpref" path="dex2c_cloud_prefs.preferences_pb""""))
  }

  @Test
  fun testAndroidManifestLinksBackupAndExtractionRules() {
    val manifestFile = File("src/main/AndroidManifest.xml").let {
      if (it.exists()) it else File("app/src/main/AndroidManifest.xml")
    }
    assertTrue("AndroidManifest.xml must exist", manifestFile.exists())
    val content = manifestFile.readText()

    assertTrue(content.contains("""android:dataExtractionRules="@xml/data_extraction_rules""""))
    assertTrue(content.contains("""android:fullBackupContent="@xml/backup_rules""""))
  }

  @Test
  fun testTokenEncryptorEncryptsWithPrefixAndDecryptsSuccessfully() {
    val encryptor = KeystoreTokenEncryptor()
    val rawToken = "gho_SecureSecretTokenForGitHubActions1234567890"

    val encrypted = encryptor.encrypt(rawToken)
    assertTrue("Encrypted token must start with enc:v1: prefix", encrypted.startsWith(KeystoreTokenEncryptor.PREFIX))
    assertNotEquals("Encrypted token must not match plaintext", rawToken, encrypted)
    assertFalse("Encrypted token must not expose plaintext in payload", encrypted.contains(rawToken))

    val decrypted = encryptor.decrypt(encrypted)
    assertEquals("Decrypted token must match original plaintext", rawToken, decrypted)
  }

  @Test
  fun testTokenEncryptorHandlesLegacyPlaintextAndEdgeCases() {
    val encryptor = KeystoreTokenEncryptor()

    // 1. Legacy unencrypted token (no prefix)
    val legacyToken = "ghp_LegacyRawPlaintextToken987654321"
    val decryptedLegacy = encryptor.decrypt(legacyToken)
    assertEquals("Legacy token without prefix must be returned transparently", legacyToken, decryptedLegacy)

    // 2. Empty string
    assertEquals("", encryptor.encrypt(""))
    assertEquals("", encryptor.encrypt("   "))
    assertEquals("", encryptor.decrypt(""))
    assertEquals("", encryptor.decrypt("   "))

    // 3. Corrupted ciphertext with valid prefix
    val corrupted = "enc:v1:NotAValidBase64String!!@#"
    assertEquals("Corrupted base64 payload must safely return empty string", "", encryptor.decrypt(corrupted))

    // 4. Truncated payload (too short to hold 12-byte IV + GCM tag)
    val truncated = "enc:v1:AQID"
    assertEquals("Truncated payload must safely return empty string", "", encryptor.decrypt(truncated))
  }

  @Test
  fun testResilientGitHubDnsRespectsDohToggle() {
    val mockCallsCount = AtomicInteger(0)

    val mockDohClient = OkHttpClient.Builder()
      .addInterceptor { chain ->
        mockCallsCount.incrementAndGet()
        val request = chain.request()
        val dummyJson = """
          {
            "Status": 0,
            "Answer": [
              {"name": "mock.target.internal", "type": 1, "TTL": 60, "data": "192.0.2.1"}
            ]
          }
        """.trimIndent()
        Response.Builder()
          .request(request)
          .protocol(Protocol.HTTP_1_1)
          .code(200)
          .message("OK")
          .body(dummyJson.toResponseBody(null))
          .build()
      }
      .build()

    val invalidHost = "non-existent-host-test-dex2c-xyz.internal"

    // 1. With DoH disabled (default)
    var isDohEnabledState = false
    val dnsWithDohDisabled = ResilientGitHubDns(
      isDohEnabled = { isDohEnabledState },
      dohClient = mockDohClient,
    )

    try {
      dnsWithDohDisabled.lookup(invalidHost)
      fail("Expected UnknownHostException when DoH is disabled and system DNS fails")
    } catch (_: UnknownHostException) {
      // Expected
    }
    assertEquals("DoH client must NOT be called when isDohEnabled is false", 0, mockCallsCount.get())

    // 2. With DoH enabled
    isDohEnabledState = true
    val resolved = dnsWithDohDisabled.lookup(invalidHost)
    assertTrue("Should resolve IP via DoH when enabled", resolved.isNotEmpty())
    assertEquals("192.0.2.1", resolved.first().hostAddress)
    assertTrue("DoH client must have been called", mockCallsCount.get() > 0)
  }

  @Test
  fun testResilientGitHubDnsLookupDoesNotThreadSleep() {
    // Verify that looking up an invalid host with DoH disabled finishes quickly without 150ms sleep
    val dns = ResilientGitHubDns(
      isDohEnabled = { false },
    )

    val start = System.currentTimeMillis()
    try {
      dns.lookup("invalid-quick-test-host.internal")
    } catch (_: UnknownHostException) {
      // Expected
    }
    val elapsed = System.currentTimeMillis() - start
    // If Thread.sleep(150L) were present, elapsed would be strictly >= 150ms.
    // Without sleep, local system DNS failure is almost immediate (< 100ms on most systems).
    // Allow generous headroom for slow CI, but verify it didn't sleep multiple intervals.
    assertTrue("Lookup without DoH should execute quickly (took ${elapsed}ms)", elapsed < 500)
  }

  @Test
  fun testCloudSettingsDohFallbackDefaultAndToggle() {
    val defaultSettings = CloudSettings()
    assertFalse("DoH fallback must default to false", defaultSettings.dohFallbackEnabled)

    val enabledSettings = defaultSettings.copy(dohFallbackEnabled = true)
    assertTrue(enabledSettings.dohFallbackEnabled)
  }
}
