package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.apk.BinarySourceType
import com.example.apk.RecentBinaryItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.recentFilesDataStore by preferencesDataStore(name = "dex2c_recent_files")

class RecentFilesStore(private val context: Context) {

  private val recentKey = stringPreferencesKey("recent_binaries_json")
  private val maxItems = 15

  val recentItemsFlow: Flow<List<RecentBinaryItem>> =
    context.recentFilesDataStore.data.map { prefs ->
      parseJson(prefs[recentKey].orEmpty())
    }

  suspend fun recordOpened(item: RecentBinaryItem) {
    context.recentFilesDataStore.edit { prefs ->
      val current = parseJson(prefs[recentKey].orEmpty()).toMutableList()
      current.removeAll { it.id == item.id || it.uriOrPath == item.uriOrPath }
      current.add(0, item)
      val trimmed = current.take(maxItems)
      prefs[recentKey] = serializeJson(trimmed)
    }
  }

  suspend fun removeItem(id: String) {
    context.recentFilesDataStore.edit { prefs ->
      val updated = parseJson(prefs[recentKey].orEmpty()).filterNot { it.id == id }
      prefs[recentKey] = serializeJson(updated)
    }
  }

  suspend fun clearAll() {
    context.recentFilesDataStore.edit { prefs ->
      prefs[recentKey] = "[]"
    }
  }

  private fun parseJson(raw: String): List<RecentBinaryItem> {
    if (raw.isBlank()) return emptyList()
    return runCatching {
      val arr = JSONArray(raw)
      buildList {
        for (i in 0 until arr.length()) {
          val obj = arr.getJSONObject(i)
          val sourceType =
            runCatching { BinarySourceType.valueOf(obj.getString("sourceType")) }
              .getOrDefault(BinarySourceType.SAF_URI)
          add(
            RecentBinaryItem(
              id = obj.getString("id"),
              displayName = obj.getString("displayName"),
              sourceType = sourceType,
              uriOrPath = obj.getString("uriOrPath"),
              packageName = obj.optString("packageName").takeIf { it.isNotBlank() },
              byteSize = obj.optLong("byteSize", 0L),
              lastOpenedMillis = obj.optLong("lastOpenedMillis", 0L),
            )
          )
        }
      }
    }.getOrDefault(emptyList())
  }

  private fun serializeJson(items: List<RecentBinaryItem>): String {
    val arr = JSONArray()
    items.forEach { item ->
      arr.put(
        JSONObject().apply {
          put("id", item.id)
          put("displayName", item.displayName)
          put("sourceType", item.sourceType.name)
          put("uriOrPath", item.uriOrPath)
          put("packageName", item.packageName ?: "")
          put("byteSize", item.byteSize)
          put("lastOpenedMillis", item.lastOpenedMillis)
        }
      )
    }
    return arr.toString()
  }
}
