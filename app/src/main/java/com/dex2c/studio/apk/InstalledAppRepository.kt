package com.dex2c.studio.apk

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import java.io.File

object InstalledAppRepository {

  fun queryLaunchableApps(context: Context): List<InstalledAppItem> {
    val pm = context.packageManager
    val launcherIntent =
      Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }

    val resolveInfos =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.queryIntentActivities(launcherIntent, PackageManager.ResolveInfoFlags.of(0L))
      } else {
        @Suppress("DEPRECATION")
        pm.queryIntentActivities(launcherIntent, 0)
      }

    val seenPackages = HashSet<String>()
    val items = mutableListOf<InstalledAppItem>()

    for (ri in resolveInfos) {
      val activityInfo = ri.activityInfo ?: continue
      val pkgName = activityInfo.packageName ?: continue
      if (!seenPackages.add(pkgName)) continue

      val appInfo = activityInfo.applicationInfo ?: continue
      val sourceDir = appInfo.sourceDir ?: continue
      val apkFile = File(sourceDir)
      if (!apkFile.exists() || !apkFile.canRead()) continue

      val pkgInfo = getPackageInfoSafe(pm, pkgName)
      val label = ri.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() } ?: pkgName
      val verName = pkgInfo?.versionName ?: "1.0"
      val verCode =
        if (pkgInfo != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
          pkgInfo.longVersionCode
        } else {
          @Suppress("DEPRECATION")
          (pkgInfo?.versionCode?.toLong() ?: 1L)
        }
      val minSdk =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) appInfo.minSdkVersion else null
      val targetSdk = appInfo.targetSdkVersion
      val isSystemApp =
        (appInfo.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
      val lastUpdateTimeMillis = pkgInfo?.lastUpdateTime ?: apkFile.lastModified()
      val iconBitmap = runCatching { drawableToBitmap(ri.loadIcon(pm)) }.getOrNull()

      items +=
        InstalledAppItem(
          packageName = pkgName,
          appLabel = label,
          versionName = verName,
          versionCode = verCode,
          minSdk = minSdk,
          targetSdk = targetSdk,
          apkPath = sourceDir,
          apkSizeBytes = apkFile.length(),
          iconBitmap = iconBitmap,
          isSystemApp = isSystemApp,
          lastUpdateTimeMillis = lastUpdateTimeMillis,
        )
    }

    return items.sortedWith(
      compareBy<InstalledAppItem> { it.packageName != context.packageName }
        .thenBy { it.appLabel.lowercase() }
    )
  }

  private fun getPackageInfoSafe(pm: PackageManager, pkgName: String): PackageInfo? {
    return runCatching {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.getPackageInfo(pkgName, PackageManager.PackageInfoFlags.of(0L))
      } else {
        @Suppress("DEPRECATION")
        pm.getPackageInfo(pkgName, 0)
      }
    }.getOrNull()
  }

  fun drawableToBitmap(drawable: Drawable, sizePx: Int = 64): Bitmap {
    if (drawable is BitmapDrawable && drawable.bitmap != null) {
      val src = drawable.bitmap
      if (src.width <= sizePx && src.height <= sizePx) return src
      return Bitmap.createScaledBitmap(src, sizePx, sizePx, true)
    }
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
  }
}
