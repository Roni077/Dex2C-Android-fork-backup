package com.dex2c.studio.ui.editor

import android.content.Context
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import com.dex2c.studio.data.SmaliSyntaxTheme
import com.dex2c.studio.dex.SmaliCompletionSuggestion
import com.dex2c.studio.dex.SmaliEditorEngine
import com.dex2c.studio.ui.theme.SyntaxPalette
import io.github.rosemoe.sora.event.ClickEvent
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import org.eclipse.tm4e.core.registry.IThemeSource
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean

object SmaliEditorManager {

  private val initialized = AtomicBoolean(false)
  private const val THEME_DARCULA = "darcula"
  private const val THEME_QUIETLIGHT = "quietlight"
  const val SCOPE_SMALI = "source.smali"

  fun ensureInitialized(context: Context) {
    if (initialized.get()) return
    synchronized(this) {
      if (initialized.get()) return
      try {
        val appContext = context.applicationContext
        val fileProviderRegistry = FileProviderRegistry.getInstance()
        fileProviderRegistry.addFileProvider(AssetsFileResolver(appContext.assets))

        val themeRegistry = ThemeRegistry.getInstance()
        loadThemeSafe(themeRegistry, THEME_DARCULA, "textmate/themes/darcula.json", isDark = true)
        loadThemeSafe(themeRegistry, THEME_QUIETLIGHT, "textmate/themes/quietlight.json", isDark = false)
        themeRegistry.setTheme(THEME_DARCULA)

        val grammarRegistry = GrammarRegistry.getInstance()
        grammarRegistry.loadGrammars("textmate/smali/syntaxes.json")

        initialized.set(true)
      } catch (e: Throwable) {
        android.util.Log.e("SmaliEditorManager", "Failed to initialize TextMate registries", e)
      }
    }
  }

  private fun loadThemeSafe(
    themeRegistry: ThemeRegistry,
    name: String,
    assetPath: String,
    isDark: Boolean
  ) {
    try {
      val fileProvider = FileProviderRegistry.getInstance()
      val stream: InputStream? = fileProvider.tryGetInputStream(assetPath)
      if (stream != null) {
        val themeSource = IThemeSource.fromInputStream(stream, assetPath, null)
        val model = ThemeModel(themeSource, name).apply {
          this.isDark = isDark
        }
        themeRegistry.loadTheme(model)
      }
    } catch (e: Throwable) {
      android.util.Log.w("SmaliEditorManager", "Could not load theme $name from $assetPath: ${e.message}")
    }
  }

  fun createSmaliLanguage(): TextMateLanguage? {
    return try {
      TextMateLanguage.create(SCOPE_SMALI, true)
    } catch (e: Throwable) {
      android.util.Log.e("SmaliEditorManager", "Failed to create Smali TextMateLanguage", e)
      null
    }
  }

  fun applyColorScheme(
    editor: CodeEditor,
    syntaxTheme: SmaliSyntaxTheme,
    palette: SyntaxPalette,
    isAppDark: Boolean
  ) {
    try {
      val themeRegistry = ThemeRegistry.getInstance()
      val isDark = when (syntaxTheme) {
        SmaliSyntaxTheme.LIGHT -> false
        SmaliSyntaxTheme.DARK, SmaliSyntaxTheme.BYTECODE_HIGH_CONTRAST -> true
        SmaliSyntaxTheme.APP_DEFAULT -> isAppDark
      }

      val targetThemeName = if (isDark) THEME_DARCULA else THEME_QUIETLIGHT
      themeRegistry.setTheme(targetThemeName)

      val textMateScheme = TextMateColorScheme.create(themeRegistry)
      // Apply Dex2c syntax palette overrides for core editor surfaces
      textMateScheme.setColor(EditorColorScheme.WHOLE_BACKGROUND, palette.codeBackground.toArgb())
      textMateScheme.setColor(EditorColorScheme.LINE_NUMBER, palette.lineNumber.toArgb())
      textMateScheme.setColor(EditorColorScheme.LINE_NUMBER_CURRENT, palette.label.toArgb())
      textMateScheme.setColor(EditorColorScheme.CURRENT_LINE, palette.codeBackground.copy(alpha = 0.5f).toArgb())
      textMateScheme.setColor(EditorColorScheme.TEXT_NORMAL, palette.defaultText.toArgb())
      editor.colorScheme = textMateScheme
    } catch (e: Throwable) {
      // Fallback to basic color scheme if TextMate fails
      val basic = editor.colorScheme
      basic.setColor(EditorColorScheme.WHOLE_BACKGROUND, palette.codeBackground.toArgb())
      basic.setColor(EditorColorScheme.LINE_NUMBER, palette.lineNumber.toArgb())
      basic.setColor(EditorColorScheme.LINE_NUMBER_CURRENT, palette.label.toArgb())
      basic.setColor(EditorColorScheme.CURRENT_LINE, palette.codeBackground.copy(alpha = 0.5f).toArgb())
      basic.setColor(EditorColorScheme.TEXT_NORMAL, palette.defaultText.toArgb())
      editor.colorScheme = basic
    }
  }
}
