package com.dex2c.studio.apk

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.dex2c.studio.dex.ApkDexEntry
import com.dex2c.studio.dex.DexParser
import com.dex2c.studio.dex.inferDominantDexPackageName
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
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
    var detectedPkgName: String? = null
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
              } else if (name.equals("AndroidManifest.xml", ignoreCase = true) && detectedPkgName == null) {
                val manifestBytes = zis.readBytes()
                detectedPkgName = extractPackageNameFromManifestBytes(manifestBytes)
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

    if (detectedPkgName == null) {
      detectedPkgName =
        dexMap.entries.firstNotNullOfOrNull { (entryName, dexBytes) ->
          inferPackageNameFromDexBytes(dexBytes, entryName)
        }
    }

    return ApkStagingPreview(
      sourceLabel = "${items.size} DEX files",
      sourceRef = items.joinToString("|") { it.first.toString() },
      sourceType = BinarySourceType.SAF_URI,
      packageName = detectedPkgName,
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
      val inferredDexPkg = inferPackageNameFromDexBytes(bytes, sourceLabel)
      return ApkStagingPreview(
        sourceLabel = sourceLabel,
        sourceRef = sourceRef,
        sourceType = sourceType,
        packageName = inferredDexPkg,
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
    var manifestBytes: ByteArray? = null

    ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
      while (true) {
        val entry = zis.nextEntry ?: break
        val name = entry.name
        if (!entry.isDirectory) {
          if (name.endsWith(".dex", ignoreCase = true)) {
            val dexBytes = zis.readBytes()
            entries += ApkDexEntry(name, dexBytes.size)
            dexMap[name] = dexBytes
          } else if (name.equals("AndroidManifest.xml", ignoreCase = true)) {
            manifestBytes = zis.readBytes()
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

    // Tier 2 Fallback: Parse binary or text AndroidManifest.xml inside the APK ZIP
    if (pkgName.isNullOrBlank() && manifestBytes != null) {
      pkgName = extractPackageNameFromManifestBytes(manifestBytes!!)
    }

    // Tier 3 Fallback: Infer dominant non-framework package from primary DEX entries
    if (pkgName.isNullOrBlank()) {
      val primaryDexEntry = sortedEntries.first()
      val primaryBytes = dexMap[primaryDexEntry.entryName]
      if (primaryBytes != null) {
        pkgName = inferPackageNameFromDexBytes(primaryBytes, sourceLabel)
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

  /**
   * Extracts the Android `package="..."` attribute from either a compiled binary XML (`AXML`)
   * `AndroidManifest.xml` resource or a plain-text XML manifest.
   */
  internal fun extractPackageNameFromManifestBytes(manifestBytes: ByteArray): String? {
    if (manifestBytes.size < 8) return null
    return runCatching {
      val buf = ByteBuffer.wrap(manifestBytes).order(ByteOrder.LITTLE_ENDIAN)
      val fileType = buf.getShort(0).toInt() and 0xFFFF
      // Standard compiled Android Binary XML (RES_XML_TYPE = 0x0003)
      if (fileType == 0x0003) {
        parseBinaryAxmlPackageName(buf, manifestBytes)
      } else {
        parsePlainTextXmlPackageName(manifestBytes)
      }
    }.getOrNull()
  }

  private fun parsePlainTextXmlPackageName(bytes: ByteArray): String? {
    val text = String(bytes, Charsets.UTF_8)
    val regex = Regex("""<manifest\b[^>]*?\bpackage\s*=\s*["']([A-Za-z0-9_.$]+)["']""")
    val match = regex.find(text)?.groupValues?.getOrNull(1)?.trim()
    return match?.takeIf { isValidAndroidPackageName(it) }
  }

  private fun parseBinaryAxmlPackageName(buf: ByteBuffer, rawBytes: ByteArray): String? {
    val fileLen = rawBytes.size
    val xmlHeaderSize = (buf.getShort(2).toInt() and 0xFFFF).coerceAtLeast(8)
    var pos = xmlHeaderSize
    var strings: List<String> = emptyList()

    while (pos + 8 <= fileLen) {
      val chunkType = buf.getShort(pos).toInt() and 0xFFFF
      val chunkHeaderSize = buf.getShort(pos + 2).toInt() and 0xFFFF
      val chunkSize = buf.getInt(pos + 4)
      if (chunkSize <= 0 || pos + chunkSize > fileLen) break

      when (chunkType) {
        // RES_STRING_POOL_TYPE = 0x0001
        0x0001 -> {
          if (chunkHeaderSize >= 28 && pos + 28 <= fileLen) {
            val stringCount = buf.getInt(pos + 8).coerceIn(0, 65536)
            val flags = buf.getInt(pos + 16)
            val stringsStart = buf.getInt(pos + 20)
            val isUtf8 = (flags and 0x0100) != 0
            val offsetsBase = pos + chunkHeaderSize
            val poolDataBase = pos + stringsStart
            val pool = ArrayList<String>(stringCount)
            for (i in 0 until stringCount) {
              val offPos = offsetsBase + i * 4
              if (offPos + 4 > fileLen) break
              val strOff = poolDataBase + buf.getInt(offPos)
              if (strOff < 0 || strOff >= fileLen) {
                pool += ""
              } else {
                pool +=
                  if (isUtf8) readAxmlUtf8String(rawBytes, strOff)
                  else readAxmlUtf16String(buf, rawBytes, strOff)
              }
            }
            strings = pool
          }
        }

        // RES_XML_START_ELEMENT_TYPE = 0x0102
        0x0102 -> {
          val nodeExtBase = pos + chunkHeaderSize
          if (nodeExtBase + 20 <= fileLen) {
            val nameIdx = buf.getInt(nodeExtBase + 4)
            val attrStart = buf.getShort(nodeExtBase + 8).toInt() and 0xFFFF
            val attrSize = (buf.getShort(nodeExtBase + 10).toInt() and 0xFFFF).coerceAtLeast(20)
            val attrCount = (buf.getShort(nodeExtBase + 12).toInt() and 0xFFFF).coerceIn(0, 256)
            val elemName = strings.getOrNull(nameIdx)
            if (elemName == "manifest") {
              var attrPos = nodeExtBase + attrStart
              for (a in 0 until attrCount) {
                if (attrPos + 20 > fileLen) break
                val attrNameIdx = buf.getInt(attrPos + 4)
                val attrRawValIdx = buf.getInt(attrPos + 8)
                val dataType = rawBytes[attrPos + 15].toInt() and 0xFF
                val dataVal = buf.getInt(attrPos + 16)
                val attrName = strings.getOrNull(attrNameIdx)
                if (attrName == "package") {
                  val rawStr = strings.getOrNull(attrRawValIdx)?.takeIf { it.isNotBlank() }
                  val typedStr =
                    if (dataType == 0x03) strings.getOrNull(dataVal)?.takeIf { it.isNotBlank() }
                    else null
                  val resolved = (rawStr ?: typedStr)?.trim()
                  if (resolved != null && isValidAndroidPackageName(resolved)) {
                    return resolved
                  }
                }
                attrPos += attrSize
              }
            }
          }
        }
      }
      pos += chunkSize
    }
    return null
  }

  private fun readAxmlUtf8String(bytes: ByteArray, offset: Int): String {
    var idx = offset
    if (idx >= bytes.size) return ""
    val u16LenFirst = bytes[idx++].toInt() and 0xFF
    if ((u16LenFirst and 0x80) != 0) {
      if (idx >= bytes.size) return ""
      idx++
    }
    if (idx >= bytes.size) return ""
    var utf8ByteLen = bytes[idx++].toInt() and 0xFF
    if ((utf8ByteLen and 0x80) != 0) {
      if (idx >= bytes.size) return ""
      utf8ByteLen = ((utf8ByteLen and 0x7F) shl 8) or (bytes[idx++].toInt() and 0xFF)
    }
    val safeLen = utf8ByteLen.coerceIn(0, (bytes.size - idx).coerceAtLeast(0))
    return String(bytes, idx, safeLen, Charsets.UTF_8)
  }

  private fun readAxmlUtf16String(buf: ByteBuffer, bytes: ByteArray, offset: Int): String {
    var idx = offset
    if (idx + 2 > bytes.size) return ""
    var charLen = buf.getShort(idx).toInt() and 0xFFFF
    idx += 2
    if ((charLen and 0x8000) != 0) {
      if (idx + 2 > bytes.size) return ""
      charLen = ((charLen and 0x7FFF) shl 16) or (buf.getShort(idx).toInt() and 0xFFFF)
      idx += 2
    }
    val byteLen = (charLen * 2).coerceIn(0, (bytes.size - idx).coerceAtLeast(0))
    return String(bytes, idx, byteLen, Charsets.UTF_16LE)
  }

  private fun isValidAndroidPackageName(candidate: String): Boolean {
    val trimmed = candidate.trim()
    if (trimmed.isEmpty() || !trimmed.contains('.')) return false
    val parts = trimmed.split('.')
    return parts.size >= 2 && parts.all { part -> part.isNotEmpty() && part.all { ch -> ch.isLetterOrDigit() || ch == '_' || ch == '$' } }
  }

  private fun inferPackageNameFromDexBytes(dexBytes: ByteArray, sourceLabel: String): String? {
    return runCatching {
      val parsed = DexParser.parseDex(rawBytes = dexBytes, sourceLabel = sourceLabel, entryName = "classes.dex")
      inferDominantDexPackageName(parsed.classes)
    }.getOrNull()
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
