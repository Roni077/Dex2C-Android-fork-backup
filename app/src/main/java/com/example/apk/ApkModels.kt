package com.example.apk

import android.graphics.Bitmap
import com.example.dex.ApkDexEntry

enum class BinarySourceType(val label: String) {
  SAF_URI("Storage File"),
  INSTALLED_APK("Installed App"),
}

enum class PickerTab {
  STORAGE,
  APPS,
  RECENT,
}

enum class AppScopeFilter(val label: String) {
  ALL("All Apps"),
  USER_ONLY("User Only"),
  SYSTEM_ONLY("System"),
}

enum class PickerSortOrder(val label: String) {
  NAME_ASC("Name A–Z"),
  NAME_DESC("Name Z–A"),
  SIZE_DESC("Largest"),
  RECENT("Newest"),
}

enum class RecentSourceFilter(val label: String) {
  ALL("All Sources"),
  STORAGE("Storage"),
  APPS("Installed"),
}

data class InstalledAppItem(
  val packageName: String,
  val appLabel: String,
  val versionName: String,
  val versionCode: Long,
  val minSdk: Int?,
  val targetSdk: Int?,
  val apkPath: String,
  val apkSizeBytes: Long,
  val iconBitmap: Bitmap?,
  val isSystemApp: Boolean = false,
  val lastUpdateTimeMillis: Long = 0L,
)

data class RecentBinaryItem(
  val id: String,
  val displayName: String,
  val sourceType: BinarySourceType,
  val uriOrPath: String,
  val packageName: String?,
  val byteSize: Long,
  val lastOpenedMillis: Long,
)

data class ApkStagingPreview(
  val sourceLabel: String,
  val sourceRef: String,
  val sourceType: BinarySourceType,
  val packageName: String?,
  val appLabel: String?,
  val versionName: String?,
  val versionCode: Long?,
  val minSdk: Int?,
  val targetSdk: Int?,
  val totalByteSize: Long,
  val nativeAbis: List<String>,
  val dexEntries: List<ApkDexEntry>,
  val dexByteMap: Map<String, ByteArray>,
  val isSingleDex: Boolean,
  val iconBitmap: Bitmap?,
)
