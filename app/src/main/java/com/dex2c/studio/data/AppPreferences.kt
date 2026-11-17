package com.dex2c.studio.data

import android.content.Context
import android.os.Build
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class AppThemeMode(val label: String, val summary: String) {
  SYSTEM("Follow System", "Automatically match Android system appearance"),
  LIGHT("Light", "Crisp daylight Material 3 tonal surfaces"),
  DARK("Dark", "Deep obsidian tonal surfaces for low-light focus"),
  AMOLED("AMOLED Black", "Pure #000000 canvas for OLED contrast and power savings"),
}

enum class SmaliSyntaxTheme(
  val label: String,
  val shortLabel: String,
  val summary: String,
  val tagSuffix: String,
) {
  APP_DEFAULT(
    label = "App Default",
    shortLabel = "Default",
    summary = "Automatically match the active app appearance",
    tagSuffix = "default",
  ),
  LIGHT(
    label = "Light",
    shortLabel = "Light",
    summary = "High-legibility daylight canvas for bright environments",
    tagSuffix = "light",
  ),
  DARK(
    label = "Dark",
    shortLabel = "Dark",
    summary = "Deep obsidian canvas with balanced syntax contrast",
    tagSuffix = "dark",
  ),
  BYTECODE_HIGH_CONTRAST(
    label = "Bytecode High-Contrast",
    shortLabel = "High-Contrast",
    summary = "Color-coded opcode families, distinct v/p registers, and call targets",
    tagSuffix = "high_contrast",
  ),
}

data class AccentPreset(
  val name: String,
  val hex: String,
)

val CURATED_ACCENT_PRESETS =
  listOf(
    AccentPreset("Silicon Mint", "#2EE59D"),
    AccentPreset("Pixel Blue", "#4C8DF6"),
    AccentPreset("Electric Cyan", "#38BDF8"),
    AccentPreset("Amber Quartz", "#F59E0B"),
    AccentPreset("Iris Violet", "#8B5CF6"),
    AccentPreset("Coral Crimson", "#F43F5E"),
  )

fun defaultDynamicColorForDevice(sdkInt: Int = Build.VERSION.SDK_INT): Boolean = sdkInt >= Build.VERSION_CODES.S

data class AppSettings(
  val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
  val dynamicColor: Boolean = defaultDynamicColorForDevice(),
  val accentSeedHex: String = "#2EE59D",
  val codeFontSizeSp: Int = 12,
  val showLineNumbers: Boolean = true,
  val maxCodePreviewLines: Int = 2500,
  val smaliSyntaxTheme: SmaliSyntaxTheme = SmaliSyntaxTheme.APP_DEFAULT,
  val defaultHideFramework: Boolean = true,
  val defaultOnlyBytecode: Boolean = true,
  val autoSelectMethodLimit: Int = 32,
  val hasCompletedOnboarding: Boolean = false,
)

private val Context.appDataStore by preferencesDataStore(name = "dex2c_app_prefs")

class AppPreferences(private val context: Context) {

  private object Keys {
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    val ACCENT_SEED_HEX = stringPreferencesKey("accent_seed_hex")
    val CODE_FONT_SIZE_SP = intPreferencesKey("code_font_size_sp")
    val SHOW_LINE_NUMBERS = booleanPreferencesKey("show_line_numbers")
    val MAX_CODE_PREVIEW_LINES = intPreferencesKey("max_code_preview_lines")
    val SMALI_SYNTAX_THEME = stringPreferencesKey("smali_syntax_theme")
    val DEFAULT_HIDE_FRAMEWORK = booleanPreferencesKey("default_hide_framework")
    val DEFAULT_ONLY_BYTECODE = booleanPreferencesKey("default_only_bytecode")
    val AUTO_SELECT_METHOD_LIMIT = intPreferencesKey("auto_select_method_limit")
    val HAS_COMPLETED_ONBOARDING = booleanPreferencesKey("has_completed_onboarding")
  }

  private fun readSettings(prefs: androidx.datastore.preferences.core.Preferences): AppSettings {
    val defaults = AppSettings()
    return AppSettings(
      themeMode =
        prefs[Keys.THEME_MODE]?.let { runCatching { AppThemeMode.valueOf(it) }.getOrNull() }
          ?: defaults.themeMode,
      dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: defaults.dynamicColor,
      accentSeedHex =
        prefs[Keys.ACCENT_SEED_HEX]?.takeIf { isValidHexColor(it) } ?: defaults.accentSeedHex,
      codeFontSizeSp = (prefs[Keys.CODE_FONT_SIZE_SP] ?: defaults.codeFontSizeSp).coerceIn(10, 18),
      showLineNumbers = prefs[Keys.SHOW_LINE_NUMBERS] ?: defaults.showLineNumbers,
      maxCodePreviewLines =
        (prefs[Keys.MAX_CODE_PREVIEW_LINES] ?: defaults.maxCodePreviewLines).coerceIn(500, 10000),
      smaliSyntaxTheme =
        prefs[Keys.SMALI_SYNTAX_THEME]?.let {
          runCatching { SmaliSyntaxTheme.valueOf(it) }.getOrNull()
        } ?: defaults.smaliSyntaxTheme,
      defaultHideFramework = prefs[Keys.DEFAULT_HIDE_FRAMEWORK] ?: defaults.defaultHideFramework,
      defaultOnlyBytecode = prefs[Keys.DEFAULT_ONLY_BYTECODE] ?: defaults.defaultOnlyBytecode,
      autoSelectMethodLimit =
        (prefs[Keys.AUTO_SELECT_METHOD_LIMIT] ?: defaults.autoSelectMethodLimit).coerceIn(8, 256),
      hasCompletedOnboarding = prefs[Keys.HAS_COMPLETED_ONBOARDING] ?: defaults.hasCompletedOnboarding,
    )
  }

  private fun writeSettings(
    prefs: androidx.datastore.preferences.core.MutablePreferences,
    settings: AppSettings,
  ) {
    prefs[Keys.THEME_MODE] = settings.themeMode.name
    prefs[Keys.DYNAMIC_COLOR] = settings.dynamicColor
    prefs[Keys.ACCENT_SEED_HEX] =
      if (isValidHexColor(settings.accentSeedHex)) normalizeHexColor(settings.accentSeedHex)
      else "#2EE59D"
    prefs[Keys.CODE_FONT_SIZE_SP] = settings.codeFontSizeSp
    prefs[Keys.SHOW_LINE_NUMBERS] = settings.showLineNumbers
    prefs[Keys.MAX_CODE_PREVIEW_LINES] = settings.maxCodePreviewLines
    prefs[Keys.SMALI_SYNTAX_THEME] = settings.smaliSyntaxTheme.name
    prefs[Keys.DEFAULT_HIDE_FRAMEWORK] = settings.defaultHideFramework
    prefs[Keys.DEFAULT_ONLY_BYTECODE] = settings.defaultOnlyBytecode
    prefs[Keys.AUTO_SELECT_METHOD_LIMIT] = settings.autoSelectMethodLimit
    prefs[Keys.HAS_COMPLETED_ONBOARDING] = settings.hasCompletedOnboarding
  }

  val settingsFlow: Flow<AppSettings> =
    context.appDataStore.data.map { prefs -> readSettings(prefs) }

  suspend fun saveSettings(settings: AppSettings) {
    context.appDataStore.edit { prefs -> writeSettings(prefs, settings) }
  }

  suspend fun updateSettings(transform: (AppSettings) -> AppSettings): AppSettings {
    var updated = AppSettings()
    context.appDataStore.edit { prefs ->
      updated = transform(readSettings(prefs))
      writeSettings(prefs, updated)
    }
    return updated
  }

  suspend fun resetToDefaults(preserveOnboarding: Boolean = true) {
    saveSettings(AppSettings(hasCompletedOnboarding = preserveOnboarding))
  }
}

fun normalizeHexColor(input: String): String {
  val stripped = input.trim().removePrefix("#").uppercase()
  return "#$stripped"
}

fun isValidHexColor(input: String): Boolean {
  val clean = input.trim().removePrefix("#")
  return clean.length == 6 && clean.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
}
