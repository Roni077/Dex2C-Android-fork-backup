package com.dex2c.studio.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

object AppIconManager {
  private const val TAG = "AppIconManager"

  private val DEPRECATED_ALIASES = listOf(
    "MainActivityShodo",
    "MainActivityCursive",
    "MainActivityMonogram",
    "MainActivityClassic",
  )

  fun setAppIcon(context: Context, style: AppIconStyle) {
    val pm = context.packageManager
    val packageName = context.packageName

    // Clean up any deprecated aliases from previous versions
    for (deprecated in DEPRECATED_ALIASES) {
      try {
        val comp = ComponentName(packageName, "$packageName.$deprecated")
        if (pm.getComponentEnabledSetting(comp) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
          pm.setComponentEnabledSetting(
            comp,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
          )
        }
      } catch (_: Exception) {}
    }

    for (candidate in AppIconStyle.entries) {
      val componentName = ComponentName(packageName, "$packageName.${candidate.aliasName}")
      val targetState =
        if (candidate == style) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        else PackageManager.COMPONENT_ENABLED_STATE_DISABLED

      try {
        val currentState = pm.getComponentEnabledSetting(componentName)
        if (currentState != targetState) {
          pm.setComponentEnabledSetting(
            componentName,
            targetState,
            PackageManager.DONT_KILL_APP,
          )
        }
      } catch (e: Exception) {
        Log.w(TAG, "Failed setting component state for ${candidate.aliasName}", e)
      }
    }
  }
}
