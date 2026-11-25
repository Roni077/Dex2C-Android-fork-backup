package com.dex2c.studio.apk

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.dex2c.studio.dex.ApkDexEntry
import com.dex2c.studio.dex.DexParser
import com.dex2c.studio.dex.inferDominantDexPackageName
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

object Apk元数据检查器 {

  fun 检查Uri(context: Context, uri: Uri, displayName: String): ApkStagingPreview =
    inspectFromUri(context, uri, displayName)

  fun 检查已安装应用(context: Context, app: 已安装应用项): ApkStagingPreview =
    inspectFromInstalledApp(context, app)

  fun inspectFromUri(context: Context, uri: Uri, displayName: String): ApkStagingPreview {
    val totalSize = queryUriByteSize(context, uri)
    val stream =
      context.contentResolver.openInputStream(uri)
        ?: throw IllegalArgumentException("Unable to read \"$displayName\". Permission may have expired.")
    return stream.use { rawStream ->
      inspectStream(
        context = context,
        inputStream = rawStream,
        knownTotalBytes = totalSize,
        sourceLabel = displayName,
        sourceRef = uri.toString(),
        sourceType = BinarySourceType.SAF_URI,
        knownApp = null,
      )
    }
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
      val uriSize = queryUriByteSize(context, uri)
      val stream =
        context.contentResolver.openInputStream(uri)
          ?: throw IllegalArgumentException("Unable to read \"$displayName\".")
      stream.use { rawInput ->
        val buffered = BufferedInputStream(rawInput, 32 * 1024)
        buffered.mark(16)
        val header = ByteArray(8)
        val headerRead = buffered.read(header)
        buffered.reset()

        if (
          headerRead >= 4 &&
            header[0] == 'd'.code.toByte() &&
            header[1] == 'e'.code.toByte() &&
            header[2] == 'x'.code.toByte() &&
            header[3] == '\n'.code.toByte()
        ) {
          val bytes = buffered.readBytes()
          totalBytes += if (uriSize > 0L) uriSize else bytes.size.toLong()
          val entryName = if (displayName.endsWith(".dex", ignoreCase = true)) displayName else "$displayName.dex"
          registerEntry(entryName, bytes)
        } else {
          var foundInsideZip = false
          var extractedBytesSum = 0L
          ZipInputStream(buffered).use { zis ->
            while (true) {
              val entry = zis.nextEntry ?: break
              val name = entry.name
              if (!entry.isDirectory) {
                if (name.endsWith(".dex", ignoreCase = true)) {
                  foundInsideZip = true
                  val dexBytes = zis.readBytes()
                  extractedBytesSum += dexBytes.size.toLong()
                  registerEntry("${displayName.substringBeforeLast('.')}/$name", dexBytes)
                } else if (name.equals("AndroidManifest.xml", ignoreCase = true) && detectedPkgName == null) {
                  val manifestBytes = zis.readBytes()
                  extractedBytesSum += manifestBytes.size.toLong()
                  detectedPkgName = extractPackageNameFromManifestBytes(manifestBytes)
                } else if (name.startsWith("lib/") && name.endsWith(".so")) {
                  val parts = name.split('/')
                  if (parts.size >= 3) abis += parts[1]
                }
              }
              zis.closeEntry()
            }
          }
          totalBytes += if (uriSize > 0L) uriSize else extractedBytesSum
          if (!foundInsideZip) {
            throw IllegalArgumentException("\"$displayName\" is not a valid DEX or APK archive.")
          }
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
    return inspectLocalFile(
      context = context,
      file = file,
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
    return inspectLocalFile(
      context = context,
      file = file,
      sourceLabel = fallbackLabel,
      sourceRef = path,
      sourceType = BinarySourceType.INSTALLED_APK,
      knownApp = null,
    )
  }

  private fun queryUriByteSize(context: Context, uri: Uri): Long {
    return runCatching {
      context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
        afd.length.takeIf { it > 0L } ?: 0L
      } ?: 0L
    }.getOrDefault(0L)
  }

  /**
   * Memory-optimized local file inspector: uses random-access [ZipFile] when the file is an APK
   * so only `classes*.dex` and `AndroidManifest.xml` entries are read into memory, avoiding
   * allocating a full-APK `ByteArray` on the heap.
   */
  private fun inspectLocalFile(
    context: Context,
    file: File,
    sourceLabel: String,
    sourceRef: String,
    sourceType: BinarySourceType,
    knownApp: InstalledAppItem?,
  ): ApkStagingPreview {
    val fileLen = file.length()
    if (fileLen < 8L) {
      throw IllegalArgumentException("File \"$sourceLabel\" is too small.")
    }
    val magicHeader = ByteArray(8)
    FileInputStream(file).use { fis -> fis.read(magicHeader) }
    if (
      magicHeader[0] == 'd'.code.toByte() &&
        magicHeader[1] == 'e'.code.toByte() &&
        magicHeader[2] == 'x'.code.toByte() &&
        magicHeader[3] == '\n'.code.toByte()
    ) {
      val bytes = file.readBytes()
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
        totalByteSize = fileLen,
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

    ZipFile(file).use { zip ->
      val enumeration = zip.entries()
      while (enumeration.hasMoreElements()) {
        val entry = enumeration.nextElement()
        if (entry.isDirectory) continue
        val name = entry.name
        if (name.endsWith(".dex", ignoreCase = true)) {
          val dexBytes = zip.getInputStream(entry).use { it.readBytes() }
          entries += ApkDexEntry(name, dexBytes.size)
          dexMap[name] = dexBytes
        } else if (name.equals("AndroidManifest.xml", ignoreCase = true)) {
          manifestBytes = zip.getInputStream(entry).use { it.readBytes() }
        } else if (name.startsWith("lib/") && name.endsWith(".so")) {
          val parts = name.split('/')
          if (parts.size >= 3) abis += parts[1]
        }
      }
    }

    return buildApkStagingPreview(
      context = context,
      sourceLabel = sourceLabel,
      sourceRef = sourceRef,
      sourceType = sourceType,
      totalByteSize = fileLen,
      entries = entries,
      dexMap = dexMap,
      abis = abis,
      manifestBytes = manifestBytes,
      knownApp = knownApp,
    )
  }

  private fun inspectStream(
    context: Context,
    inputStream: InputStream,
    knownTotalBytes: Long,
    sourceLabel: String,
    sourceRef: String,
    sourceType: BinarySourceType,
    knownApp: InstalledAppItem?,
  ): ApkStagingPreview {
    val buffered = BufferedInputStream(inputStream, 32 * 1024)
    buffered.mark(16)
    val magic = ByteArray(8)
    val readCount = buffered.read(magic)
    buffered.reset()

    if (
      readCount >= 8 &&
        magic[0] == 'd'.code.toByte() &&
        magic[1] == 'e'.code.toByte() &&
        magic[2] == 'x'.code.toByte() &&
        magic[3] == '\n'.code.toByte()
    ) {
      val bytes = buffered.readBytes()
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
        totalByteSize = if (knownTotalBytes > 0L) knownTotalBytes else bytes.size.toLong(),
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
    var extractedTotal = 0L

    ZipInputStream(buffered).use { zis ->
      while (true) {
        val entry = zis.nextEntry ?: break
        val name = entry.name
        if (!entry.isDirectory) {
          if (name.endsWith(".dex", ignoreCase = true)) {
            val dexBytes = zis.readBytes()
            extractedTotal += dexBytes.size.toLong()
            entries += ApkDexEntry(name, dexBytes.size)
            dexMap[name] = dexBytes
          } else if (name.equals("AndroidManifest.xml", ignoreCase = true)) {
            val mb = zis.readBytes()
            extractedTotal += mb.size.toLong()
            manifestBytes = mb
          } else if (name.startsWith("lib/") && name.endsWith(".so")) {
            val parts = name.split('/')
            if (parts.size >= 3) abis += parts[1]
          }
        }
        zis.closeEntry()
      }
    }

    return buildApkStagingPreview(
      context = context,
      sourceLabel = sourceLabel,
      sourceRef = sourceRef,
      sourceType = sourceType,
      totalByteSize = if (knownTotalBytes > 0L) knownTotalBytes else extractedTotal,
      entries = entries,
      dexMap = dexMap,
      abis = abis,
      manifestBytes = manifestBytes,
      knownApp = knownApp,
    )
  }

  private fun buildApkStagingPreview(
    context: Context,
    sourceLabel: String,
    sourceRef: String,
    sourceType: BinarySourceType,
    totalByteSize: Long,
    entries: List<ApkDexEntry>,
    dexMap: Map<String, ByteArray>,
    abis: Set<String>,
    manifestBytes: ByteArray?,
    knownApp: InstalledAppItem?,
  ): ApkStagingPreview {
    if (entries.isEmpty()) {
      throw IllegalArgumentException("No DEX binary or classes*.dex entries found inside \"$sourceLabel\".")
    }

    val sortedEntries = entries.sortedWith(compareBy({ it.entryName.length }, { it.entryName }))

    var pkgName = knownApp?.packageName
    var appLabel = knownApp?.appLabel
    var verName = knownApp?.versionName
    var verCode = knownApp?.versionCode
    var minSdk = knownApp?.minSdk
    var targetSdk = knownApp?.targetSdk
    var iconBitmap = knownApp?.iconBitmap

    // Fast path: parse binary or text AndroidManifest.xml directly from memory without writing a temp APK
    if (pkgName.isNullOrBlank() && manifestBytes != null) {
      pkgName = extractPackageNameFromManifestBytes(manifestBytes)
    }

    // If this is a local file path, query PackageManager directly without copying any bytes
    if (sourceType == BinarySourceType.INSTALLED_APK && File(sourceRef).exists()) {
      val archiveMeta = extractArchiveInfoFromFile(context, sourceRef)
      if (archiveMeta != null) {
        if (pkgName.isNullOrBlank()) pkgName = archiveMeta.packageName
        if (appLabel.isNullOrBlank()) appLabel = archiveMeta.appLabel
        if (verName.isNullOrBlank()) verName = archiveMeta.versionName
        if (verCode == null) verCode = archiveMeta.versionCode
        if (minSdk == null) minSdk = archiveMeta.minSdk
        if (targetSdk == null) targetSdk = archiveMeta.targetSdk
        if (iconBitmap == null) iconBitmap = archiveMeta.iconBitmap
      }
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
      totalByteSize = totalByteSize,
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

  private fun extractArchiveInfoFromFile(
    context: Context,
    apkPath: String,
  ): ArchiveMeta? {
    val pm = context.packageManager
    return try {
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
    }
  }
}

typealias ApkMetadataInspector = Apk元数据检查器
