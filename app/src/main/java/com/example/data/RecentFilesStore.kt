package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.apk.BinarySourceType
import com.example.apk.RecentBinaryItem
import com.example.dex.BytecodeOverviewSummary
import com.example.dex.ClassDefinitionSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.recentFilesDataStore by preferencesDataStore(name = "dex2c_recent_files")

class RecentFilesStore(private val context: Context) {

  private val recentKey = stringPreferencesKey("recent_binaries_json")
  private val lastBytecodeSummaryKey = stringPreferencesKey("last_bytecode_summary_json")
  private val maxItems = 15

  val recentItemsFlow: Flow<List<RecentBinaryItem>> =
    context.recentFilesDataStore.data.map { prefs ->
      parseJson(prefs[recentKey].orEmpty())
    }

  val lastBytecodeSummaryFlow: Flow<BytecodeOverviewSummary?> =
    context.recentFilesDataStore.data.map { prefs ->
      parseSummaryJson(prefs[lastBytecodeSummaryKey].orEmpty())
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

  suspend fun saveLastBytecodeSummary(summary: BytecodeOverviewSummary) {
    context.recentFilesDataStore.edit { prefs ->
      prefs[lastBytecodeSummaryKey] = serializeSummaryJson(summary)
    }
  }

  suspend fun clearLastBytecodeSummary() {
    context.recentFilesDataStore.edit { prefs ->
      prefs.remove(lastBytecodeSummaryKey)
    }
  }

  suspend fun clearAll() {
    context.recentFilesDataStore.edit { prefs ->
      prefs[recentKey] = "[]"
      prefs.remove(lastBytecodeSummaryKey)
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

  private fun parseSummaryJson(raw: String): BytecodeOverviewSummary? {
    if (raw.isBlank()) return null
    return runCatching {
      val obj = JSONObject(raw)
      val classesArr = obj.optJSONArray("topClasses") ?: JSONArray()
      val topClasses =
        buildList {
          for (i in 0 until classesArr.length()) {
            val c = classesArr.getJSONObject(i)
            add(
              ClassDefinitionSummary(
                simpleName = c.getString("simpleName"),
                packageName = c.optString("packageName"),
                methodCount = c.optInt("methodCount", 0),
                bytecodeMethodCount = c.optInt("bytecodeMethodCount", 0),
                isFramework = c.optBoolean("isFramework", false),
              )
            )
          }
        }
      BytecodeOverviewSummary(
        sourceLabel = obj.getString("sourceLabel"),
        entryName = obj.optString("entryName", "classes.dex"),
        dexCount = obj.optInt("dexCount", 1),
        dexVersion = obj.optString("dexVersion", "035"),
        totalMethods = obj.optInt("totalMethods", 0),
        bytecodeMethods = obj.optInt("bytecodeMethods", 0),
        totalClassDefinitions = obj.optInt("totalClassDefinitions", 0),
        appClassDefinitions = obj.optInt("appClassDefinitions", 0),
        totalStrings = obj.optInt("totalStrings", 0),
        analyzedAtMillis = obj.optLong("analyzedAtMillis", 0L),
        topClasses = topClasses,
      )
    }.getOrNull()
  }

  private fun serializeSummaryJson(summary: BytecodeOverviewSummary): String {
    val classesArr = JSONArray()
    summary.topClasses.forEach { cls ->
      classesArr.put(
        JSONObject().apply {
          put("simpleName", cls.simpleName)
          put("packageName", cls.packageName)
          put("methodCount", cls.methodCount)
          put("bytecodeMethodCount", cls.bytecodeMethodCount)
          put("isFramework", cls.isFramework)
        }
      )
    }
    return JSONObject()
      .apply {
        put("sourceLabel", summary.sourceLabel)
        put("entryName", summary.entryName)
        put("dexCount", summary.dexCount)
        put("dexVersion", summary.dexVersion)
        put("totalMethods", summary.totalMethods)
        put("bytecodeMethods", summary.bytecodeMethods)
        put("totalClassDefinitions", summary.totalClassDefinitions)
        put("appClassDefinitions", summary.appClassDefinitions)
        put("totalStrings", summary.totalStrings)
        put("analyzedAtMillis", summary.analyzedAtMillis)
        put("topClasses", classesArr)
      }
      .toString()
  }
}

