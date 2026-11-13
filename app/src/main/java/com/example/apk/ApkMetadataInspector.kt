package com.example.apk

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.example.dex.ApkDexEntry
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

object ApkMetadataInspector {

  fun inspectFromUri(context: Context, uri: Uri, displayName: String): ApkStagingPreview {
    val bytes =
      context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        ?: throw IllegalArgumentException("Unable to read \"$displayName\". Permission may have expired.")
    return inspectBytes(
      context = context,
      bytes = bytes,
      sourceLabel = displayName,
      sourceRef = uri.toString(),
      sourceType = BinarySourceType.SAF_URI,
      knownApp = null,
    )
  }

  fun inspectMultipleUris(context: Context, items: List<Pair<Uri, String>>): ApkStagingPreview {
    if (items.isEmpty()) {
      throw IllegalArgumentException("No files selected.")
    }
    if (items.size == 1) {
      val (uri, name) = items.first()
      return inspectFromUri(context, uri, name)
    }

    val entries = mutableListOf<ApkDexEntry>()
    val dexMap = linkedMapOf<String, ByteArray>()
    val abis = linkedSetOf<String>()
    var totalBytes = 0L

    fun registerEntry(desiredName: String, dexBytes: ByteArray) {
      var candidate = desiredName
      var suffix = 2
      while (candidate in dexMap) {
        val dot = desiredName.lastIndexOf('.')
        candidate =
          if (dot > 0) "${desiredName.substring(0, dot)} ($suffix)${desiredName.substring(dot)}"
          else "$desiredName ($suffix)"
        suffix++
      }
      entries += ApkDexEntry(candidate, dexBytes.size)
      dexMap[candidate] = dexBytes
    }

    items.forEach { (uri, displayName) ->
      val bytes =
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
          ?: throw IllegalArgumentException("Unable to read \"$displayName\".")
      totalBytes += bytes.size.toLong()
      if (
        bytes.size >= 8 &&
          bytes[0] == 'd'.code.toByte() &&
          bytes[1] == 'e'.code.toByte() &&
          bytes[2] == 'x'.code.toByte() &&
          bytes[3] == '\n'.code.toByte()
      ) {
        val entryName = if (displayName.endsWith(".dex", ignoreCase = true)) displayName else "$displayName.dex"
        registerEntry(entryName, bytes)
      } else {
        var foundInsideZip = false
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
          while (true) {
            val entry = zis.nextEntry ?: break
            val name = entry.name
            if (!entry.isDirectory) {
              if (name.endsWith(".dex", ignoreCase = true)) {
                foundInsideZip = true
                val dexBytes = zis.readBytes()
                registerEntry("${displayName.substringBeforeLast('.')}/$name", dexBytes)
              } else if (name.startsWith("lib/") && name.endsWith(".so")) {
                val parts = name.split('/')
                if (parts.size >= 3) abis += parts[1]
              }
            }
            zis.closeEntry()
          }
        }
        if (!foundInsideZip) {
          throw IllegalArgumentException("\"$displayName\" is not a valid DEX or APK archive.")
        }
      }
    }

    if (entries.isEmpty()) {
      throw IllegalArgumentException("No valid DEX entries found in the selected files.")
    }

    return ApkStagingPreview(
      sourceLabel = "${items.size} DEX files",
      sourceRef = items.joinToString("|") { it.first.toString() },
      sourceType = BinarySourceType.SAF_URI,
      packageName = null,
      appLabel = "${entries.size} Selected DEX Binaries",
      versionName = "${entries.size} DEX files",
      versionCode = null,
      minSdk = null,
      targetSdk = null,
      totalByteSize = totalBytes,
      nativeAbis = abis.sorted(),
      dexEntries = entries,
      dexByteMap = dexMap,
      isSingleDex = false,
      iconBitmap = null,
    )
  }

  fun inspectFromInstalledApp(context: Context, app: InstalledAppItem): ApkStagingPreview {
    val file = File(app.apkPath)
    if (!file.exists() || !file.canRead()) {
      throw IllegalArgumentException("APK for ${app.packageName} is no longer accessible.")
    }
    val bytes = file.readBytes()
    return inspectBytes(
      context = context,
      bytes = bytes,
      sourceLabel = "${app.appLabel}.apk",
      sourceRef = app.apkPath,
      sourceType = BinarySourceType.INSTALLED_APK,
      knownApp = app,
    )
  }

  fun inspectFromFilePath(context: Context, path: String, fallbackLabel: String): ApkStagingPreview {
    val file = File(path)
    if (!file.exists() || !file.canRead()) {
      throw IllegalArgumentException("File \"$fallbackLabel\" is not accessible.")
    }
    val bytes = file.readBytes()
    return inspectBytes(
      context = context,
      bytes = bytes,
      sourceLabel = fallbackLabel,
      sourceRef = path,
      sourceType = BinarySourceType.INSTALLED_APK,
      knownApp = null,
    )
  }

  private fun inspectBytes(
    context: Context,
    bytes: ByteArray,
    sourceLabel: String,
    sourceRef: String,
    sourceType: BinarySourceType,
    knownApp: InstalledAppItem?,
  ): ApkStagingPreview {
    // Check standalone .dex magic ("dex\n")
    if (
      bytes.size >= 8 &&
        bytes[0] == 'd'.code.toByte() &&
        bytes[1] == 'e'.code.toByte() &&
        bytes[2] == 'x'.code.toByte() &&
        bytes[3] == '\n'.code.toByte()
    ) {
      val entryName = if (sourceLabel.endsWith(".dex", ignoreCase = true)) sourceLabel else "classes.dex"
      return ApkStagingPreview(
        sourceLabel = sourceLabel,
        sourceRef = sourceRef,
        sourceType = sourceType,
        packageName = null,
        appLabel = sourceLabel,
        versionName = "DEX v" + String(bytes, 4, 3, Charsets.US_ASCII),
        versionCode = null,
        minSdk = null,
        targetSdk = null,
        totalByteSize = bytes.size.toLong(),
        nativeAbis = emptyList(),
        dexEntries = listOf(ApkDexEntry(entryName, bytes.size)),
        dexByteMap = mapOf(entryName to bytes),
        isSingleDex = true,
        iconBitmap = null,
      )
    }

    val entries = mutableListOf<ApkDexEntry>()
    val dexMap = linkedMapOf<String, ByteArray>()
    val abis = linkedSetOf<String>()

    ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
      while (true) {
        val entry = zis.nextEntry ?: break
        val name = entry.name
        if (!entry.isDirectory) {
          if (name.endsWith(".dex", ignoreCase = true)) {
            val dexBytes = zis.readBytes()
            entries += ApkDexEntry(name, dexBytes.size)
            dexMap[name] = dexBytes
          } else if (name.startsWith("lib/") && name.endsWith(".so")) {
            val parts = name.split('/')
            if (parts.size >= 3) abis += parts[1]
          }
        }
        zis.closeEntry()
      }
    }

    if (entries.isEmpty()) {
      throw IllegalArgumentException("No DEX binary or classes*.dex entries found inside \"$sourceLabel\".")
    }

    val sortedEntries = entries.sortedWith(compareBy({ it.entryName.length }, { it.entryName }))

    // Extract APK archive metadata if not already provided by InstalledAppItem
    var pkgName = knownApp?.packageName
    var appLabel = knownApp?.appLabel
    var verName = knownApp?.versionName
    var verCode = knownApp?.versionCode
    var minSdk = knownApp?.minSdk
    var targetSdk = knownApp?.targetSdk
    var iconBitmap = knownApp?.iconBitmap

    if (pkgName == null) {
      val archiveMeta = extractArchiveInfo(context, bytes, sourceRef, sourceType)
      if (archiveMeta != null) {
        pkgName = archiveMeta.packageName
        appLabel = archiveMeta.appLabel
        verName = archiveMeta.versionName
        verCode = archiveMeta.versionCode
        minSdk = archiveMeta.minSdk
        targetSdk = archiveMeta.targetSdk
        iconBitmap = archiveMeta.iconBitmap
      }
    }

    return ApkStagingPreview(
      sourceLabel = sourceLabel,
      sourceRef = sourceRef,
      sourceType = sourceType,
      packageName = pkgName,
      appLabel = appLabel ?: sourceLabel,
      versionName = verName,
      versionCode = verCode,
      minSdk = minSdk,
      targetSdk = targetSdk,
      totalByteSize = bytes.size.toLong(),
      nativeAbis = abis.sorted(),
      dexEntries = sortedEntries,
      dexByteMap = dexMap,
      isSingleDex = false,
      iconBitmap = iconBitmap,
    )
  }

  private data class ArchiveMeta(
    val packageName: String,
    val appLabel: String?,
    val versionName: String?,
    val versionCode: Long?,
    val minSdk: Int?,
    val targetSdk: Int?,
    val iconBitmap: android.graphics.Bitmap?,
  )

  private fun extractArchiveInfo(
    context: Context,
    bytes: ByteArray,
    sourceRef: String,
    sourceType: BinarySourceType,
  ): ArchiveMeta? {
    val pm = context.packageManager
    var tempFile: File? = null
    return try {
      val apkPath =
        if (sourceType == BinarySourceType.INSTALLED_APK && File(sourceRef).exists()) {
          sourceRef
        } else {
          File.createTempFile("dex2c_stage_", ".apk", context.cacheDir).also { tmp ->
            tempFile = tmp
            FileOutputStream(tmp).use { it.write(bytes) }
          }.absolutePath
        }

      val pkgInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          pm.getPackageArchiveInfo(apkPath, PackageManager.PackageInfoFlags.of(0L))
        } else {
          @Suppress("DEPRECATION")
          pm.getPackageArchiveInfo(apkPath, 0)
        } ?: return null

      val appInfo = pkgInfo.applicationInfo
      if (appInfo != null) {
        appInfo.sourceDir = apkPath
        appInfo.publicSourceDir = apkPath
      }

      val label = appInfo?.loadLabel(pm)?.toString()
      val icon = runCatching { appInfo?.loadIcon(pm)?.let { InstalledAppRepository.drawableToBitmap(it) } }.getOrNull()
      val verCode =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
          pkgInfo.longVersionCode
        } else {
          @Suppress("DEPRECATION")
          pkgInfo.versionCode.toLong()
        }
      val minSdk =
        if (appInfo != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) appInfo.minSdkVersion else null

      ArchiveMeta(
        packageName = pkgInfo.packageName,
        appLabel = label,
        versionName = pkgInfo.versionName,
        versionCode = verCode,
        minSdk = minSdk,
        targetSdk = appInfo?.targetSdkVersion,
        iconBitmap = icon,
      )
    } catch (_: Throwable) {
      null
    } finally {
      tempFile?.delete()
    }
  }
}
