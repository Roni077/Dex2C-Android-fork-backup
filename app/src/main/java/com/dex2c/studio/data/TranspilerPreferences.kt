package com.dex2c.studio.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dex2c.studio.transpiler.DEFAULT_FILTER_RULES
import com.dex2c.studio.transpiler.JniBindingStyle
import com.dex2c.studio.transpiler.LEGACY_DEFAULT_FILTER_RULES
import com.dex2c.studio.transpiler.LoaderMode
import com.dex2c.studio.transpiler.TargetCLanguage
import com.dex2c.studio.transpiler.TranspilerConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "dex2c_prefs")

class TranspilerPreferences(private val context: Context) {

  private object Keys {
    val JNI_STYLE = stringPreferencesKey("jni_style")
    val TARGET_LANG = stringPreferencesKey("target_lang")
    val STRING_XOR = booleanPreferencesKey("string_xor")
    val CF_FLATTENING = booleanPreferencesKey("cf_flattening")
    val NULL_GUARDS = booleanPreferencesKey("null_guards")
    val ALLOW_INIT = booleanPreferencesKey("allow_init")
    val SKIP_SYNTHETIC = booleanPreferencesKey("skip_synthetic")
    val LOADER_MODE = stringPreferencesKey("loader_mode")
    val CUSTOM_LOADER = stringPreferencesKey("custom_loader")
    val FORCE_CUSTOM_LOADER = booleanPreferencesKey("force_custom_loader")
    val TARGET_ABIS = stringSetPreferencesKey("target_abis")
    val FILTER_RULES_TEXT = stringPreferencesKey("filter_rules_text")
    val MODULE_NAME = stringPreferencesKey("module_name")
  }

  val configFlow: Flow<TranspilerConfig> =
    context.dataStore.data.map { prefs ->
      val defaults = TranspilerConfig()
      val storedFilter = prefs[Keys.FILTER_RULES_TEXT]?.trim()
      val resolvedFilter =
        if (storedFilter.isNullOrEmpty() || storedFilter == LEGACY_DEFAULT_FILTER_RULES.trim()) {
          DEFAULT_FILTER_RULES
        } else {
          prefs[Keys.FILTER_RULES_TEXT] ?: defaults.filterRulesText
        }
      TranspilerConfig(
        jniStyle = prefs[Keys.JNI_STYLE]?.let { runCatching { JniBindingStyle.valueOf(it) }.getOrNull() } ?: defaults.jniStyle,
        targetLanguage = prefs[Keys.TARGET_LANG]?.let { runCatching { TargetCLanguage.valueOf(it) }.getOrNull() } ?: defaults.targetLanguage,
        stringEncryption = prefs[Keys.STRING_XOR] ?: defaults.stringEncryption,
        controlFlowFlattening = prefs[Keys.CF_FLATTENING] ?: defaults.controlFlowFlattening,
        nullCheckGuards = prefs[Keys.NULL_GUARDS] ?: defaults.nullCheckGuards,
        allowInit = prefs[Keys.ALLOW_INIT] ?: defaults.allowInit,
        skipSynthetic = prefs[Keys.SKIP_SYNTHETIC] ?: defaults.skipSynthetic,
        loaderMode = prefs[Keys.LOADER_MODE]?.let { runCatching { LoaderMode.valueOf(it) }.getOrNull() } ?: defaults.loaderMode,
        customLoaderClass = prefs[Keys.CUSTOM_LOADER] ?: defaults.customLoaderClass,
        forceCustomLoader = prefs[Keys.FORCE_CUSTOM_LOADER] ?: defaults.forceCustomLoader,
        targetAbis = prefs[Keys.TARGET_ABIS]?.takeIf { it.isNotEmpty() } ?: defaults.targetAbis,
        filterRulesText = resolvedFilter,
        moduleName = prefs[Keys.MODULE_NAME] ?: defaults.moduleName,
      )
    }

  suspend fun saveConfig(config: TranspilerConfig) {
    context.dataStore.edit { prefs ->
      prefs[Keys.JNI_STYLE] = config.jniStyle.name
      prefs[Keys.TARGET_LANG] = config.targetLanguage.name
      prefs[Keys.STRING_XOR] = config.stringEncryption
      prefs[Keys.CF_FLATTENING] = config.controlFlowFlattening
      prefs[Keys.NULL_GUARDS] = config.nullCheckGuards
      prefs[Keys.ALLOW_INIT] = config.allowInit
      prefs[Keys.SKIP_SYNTHETIC] = config.skipSynthetic
      prefs[Keys.LOADER_MODE] = config.loaderMode.name
      prefs[Keys.CUSTOM_LOADER] = config.customLoaderClass
      prefs[Keys.FORCE_CUSTOM_LOADER] = config.forceCustomLoader
      prefs[Keys.TARGET_ABIS] = config.targetAbis
      prefs[Keys.FILTER_RULES_TEXT] = config.filterRulesText
      prefs[Keys.MODULE_NAME] = config.moduleName
    }
  }
}
