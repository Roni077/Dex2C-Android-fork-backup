package com.dex2c.studio.data

import android.content.Context
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dex2c.studio.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class AppThemeMode(
  val labelRes: Int,
  val summaryRes: Int,
  val label: String,
  val summary: String,
) {
  SYSTEM(
    labelRes = R.string.theme_mode_system,
    summaryRes = R.string.theme_mode_system_desc,
    label = "Follow System",
    summary = "Automatically match Android system appearance",
  ),
  LIGHT(
    labelRes = R.string.theme_mode_light,
    summaryRes = R.string.theme_mode_light_desc,
    label = "Light",
    summary = "Crisp daylight Material 3 tonal surfaces",
  ),
  DARK(
    labelRes = R.string.theme_mode_dark,
    summaryRes = R.string.theme_mode_dark_desc,
    label = "Dark",
    summary = "Deep obsidian tonal surfaces for low-light focus",
  ),
  AMOLED(
    labelRes = R.string.theme_mode_amoled,
    summaryRes = R.string.theme_mode_amoled_desc,
    label = "AMOLED Black",
    summary = "Pure #000000 canvas for OLED contrast and power savings",
  ),
}

enum class SmaliSyntaxTheme(
  val labelRes: Int,
  val shortLabelRes: Int,
  val summaryRes: Int,
  val tagSuffix: String,
  val label: String,
  val shortLabel: String,
  val summary: String,
) {
  APP_DEFAULT(
    labelRes = R.string.syntax_theme_default,
    shortLabelRes = R.string.syntax_theme_default_short,
    summaryRes = R.string.syntax_theme_default_desc,
    tagSuffix = "default",
    label = "App Default",
    shortLabel = "Default",
    summary = "Automatically match the active app appearance",
  ),
  LIGHT(
    labelRes = R.string.syntax_theme_light,
    shortLabelRes = R.string.syntax_theme_light_short,
    summaryRes = R.string.syntax_theme_light_desc,
    tagSuffix = "light",
    label = "Light",
    shortLabel = "Light",
    summary = "High-legibility daylight canvas for bright environments",
  ),
  DARK(
    labelRes = R.string.syntax_theme_dark,
    shortLabelRes = R.string.syntax_theme_dark_short,
    summaryRes = R.string.syntax_theme_dark_desc,
    tagSuffix = "dark",
    label = "Dark",
    shortLabel = "Dark",
    summary = "Deep obsidian canvas with balanced syntax contrast",
  ),
  BYTECODE_HIGH_CONTRAST(
    labelRes = R.string.syntax_theme_high_contrast,
    shortLabelRes = R.string.syntax_theme_high_contrast_short,
    summaryRes = R.string.syntax_theme_high_contrast_desc,
    tagSuffix = "high_contrast",
    label = "Bytecode High-Contrast",
    shortLabel = "High-Contrast",
    summary = "Color-coded opcode families, distinct v/p registers, and call targets",
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

enum class AppIconStyle(
  val id: String,
  val labelRes: Int,
  val descriptionRes: Int,
  val aliasName: String,
  @param:DrawableRes val previewRes: Int,
  val label: String,
  val description: String,
) {
  DARK(
    id = "dark",
    labelRes = R.string.icon_style_dark,
    descriptionRes = R.string.icon_style_dark_desc,
    aliasName = "MainActivityDark",
    previewRes = R.drawable.ic_dex2c_dark_src,
    label = "DC Ribbon Dark",
    description = "Obsidian glass tile with radiant cyan neon rim and luminous ribbon",
  ),
  LIGHT(
    id = "light",
    labelRes = R.string.icon_style_light,
    descriptionRes = R.string.icon_style_light_desc,
    aliasName = "MainActivityLight",
    previewRes = R.drawable.ic_dex2c_light_src,
    label = "DC Ribbon Light",
    description = "Sleek dark emerald squircle with crisp mint & ice-white folding ribbon",
  );

  companion object {
    val DEFAULT = DARK
  }
}

enum class EditorEngineType(val titleRes: Int) {
  RUST_HYBRID(R.string.editor_engine_rust_hybrid),
  SORA_LEGACY(R.string.editor_engine_sora_legacy),
}

enum class AppLanguage(val code: String, val displayNameRes: Int) {
  SYSTEM("system", R.string.language_system),
  ENGLISH("en", R.string.language_english),
  CHINESE_SIMPLIFIED("zh", R.string.language_chinese),
}

fun defaultDynamicColorForDevice(sdkInt: Int = Build.VERSION.SDK_INT): Boolean = sdkInt >= Build.VERSION_CODES.S

data class AppSettings(
  val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
  val appLanguage: AppLanguage = AppLanguage.SYSTEM,
  val appIconStyle: AppIconStyle = AppIconStyle.DEFAULT,
  val dynamicColor: Boolean = defaultDynamicColorForDevice(),
  val accentSeedHex: String = "#2EE59D",
  val codeFontSizeSp: Int = 12,
  val showLineNumbers: Boolean = true,
  val maxCodePreviewLines: Int = 2500,
  val smaliSyntaxTheme: SmaliSyntaxTheme = SmaliSyntaxTheme.APP_DEFAULT,
  val editorEngine: EditorEngineType = EditorEngineType.RUST_HYBRID,
  val defaultHideFramework: Boolean = false,
  val defaultOnlyBytecode: Boolean = true,
  val autoSelectMethodLimit: Int = 32,
  val hasCompletedOnboarding: Boolean = false,
)

private val Context.appDataStore by preferencesDataStore(name = "dex2c_app_prefs")

class AppPreferences(private val context: Context) {

  private object Keys {
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val APP_LANGUAGE = stringPreferencesKey("app_language")
    val APP_ICON_STYLE = stringPreferencesKey("app_icon_style")
    val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    val ACCENT_SEED_HEX = stringPreferencesKey("accent_seed_hex")
    val CODE_FONT_SIZE_SP = intPreferencesKey("code_font_size_sp")
    val SHOW_LINE_NUMBERS = booleanPreferencesKey("show_line_numbers")
    val MAX_CODE_PREVIEW_LINES = intPreferencesKey("max_code_preview_lines")
    val SMALI_SYNTAX_THEME = stringPreferencesKey("smali_syntax_theme")
    val EDITOR_ENGINE = stringPreferencesKey("editor_engine")
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
      appLanguage =
        prefs[Keys.APP_LANGUAGE]?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() }
          ?: defaults.appLanguage,
      appIconStyle =
        prefs[Keys.APP_ICON_STYLE]?.let { runCatching { AppIconStyle.valueOf(it) }.getOrNull() }
          ?: defaults.appIconStyle,
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
      editorEngine =
        prefs[Keys.EDITOR_ENGINE]?.let {
          runCatching { EditorEngineType.valueOf(it) }.getOrNull()
        } ?: defaults.editorEngine,
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
    prefs[Keys.APP_LANGUAGE] = settings.appLanguage.name
    prefs[Keys.APP_ICON_STYLE] = settings.appIconStyle.name
    prefs[Keys.DYNAMIC_COLOR] = settings.dynamicColor
    prefs[Keys.ACCENT_SEED_HEX] =
      if (isValidHexColor(settings.accentSeedHex)) normalizeHexColor(settings.accentSeedHex)
      else "#2EE59D"
    prefs[Keys.CODE_FONT_SIZE_SP] = settings.codeFontSizeSp
    prefs[Keys.SHOW_LINE_NUMBERS] = settings.showLineNumbers
    prefs[Keys.MAX_CODE_PREVIEW_LINES] = settings.maxCodePreviewLines
    prefs[Keys.SMALI_SYNTAX_THEME] = settings.smaliSyntaxTheme.name
    prefs[Keys.EDITOR_ENGINE] = settings.editorEngine.name
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
