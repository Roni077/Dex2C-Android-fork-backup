package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.apk.ApkMetadataInspector
import com.example.apk.BinarySourceType
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ApkInspectorTest {

  @Test
  fun inspectMultiDexApkDetectsDexContainersAndAbis() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val tempApk = File.createTempFile("sample_multi_", ".apk", context.cacheDir)
    try {
      ZipOutputStream(FileOutputStream(tempApk)).use { zos ->
        zos.putNextEntry(ZipEntry("classes.dex"))
        zos.write(ByteArray(32) { 0x01 })
        zos.closeEntry()

        zos.putNextEntry(ZipEntry("classes2.dex"))
        zos.write(ByteArray(64) { 0x02 })
        zos.closeEntry()

        zos.putNextEntry(ZipEntry("lib/arm64-v8a/libdemo.so"))
        zos.write(byteArrayOf(0x7F, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte()))
        zos.closeEntry()

        zos.putNextEntry(ZipEntry("lib/armeabi-v7a/libdemo.so"))
        zos.write(byteArrayOf(0x7F, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte()))
        zos.closeEntry()
      }

      val preview = ApkMetadataInspector.inspectFromFilePath(context, tempApk.absolutePath, "sample.apk")
      assertFalse(preview.isSingleDex)
      assertEquals(BinarySourceType.INSTALLED_APK, preview.sourceType)
      assertEquals(listOf("classes.dex", "classes2.dex"), preview.dexEntries.map { it.entryName })
      assertEquals(listOf("arm64-v8a", "armeabi-v7a"), preview.nativeAbis)
      assertTrue(preview.dexByteMap.containsKey("classes2.dex"))
    } finally {
      tempApk.delete()
    }
  }
}
