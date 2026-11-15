package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.data.AppThemeMode
import com.example.data.SmaliSyntaxTheme

/**
 * Material 3 Expressive Shape Scale:
 * Generous 24dp cards/surfaces, 28-32dp sheets/dialogs, and tactile rounded controls.
 */
val ExpressiveShapes =
  Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
  )

@Immutable
data class EditorDisplaySettings(
  val fontSizeSp: Int = 12,
  val showLineNumbers: Boolean = true,
  val maxLinesLimit: Int = 2500,
  val smaliSyntaxTheme: SmaliSyntaxTheme = SmaliSyntaxTheme.APP_DEFAULT,
)

fun resolveSmaliSyntaxPalette(
  theme: SmaliSyntaxTheme,
  appDefaultPalette: SyntaxPalette,
): SyntaxPalette =
  when (theme) {
    SmaliSyntaxTheme.APP_DEFAULT -> appDefaultPalette
    SmaliSyntaxTheme.LIGHT -> LightSyntaxPalette
    SmaliSyntaxTheme.DARK -> DarkSyntaxPalette
    SmaliSyntaxTheme.BYTECODE_HIGH_CONTRAST -> BytecodeHighContrastSyntaxPalette
  }

val LocalSyntaxPalette = staticCompositionLocalOf { DarkSyntaxPalette }
val LocalEditorSettings = staticCompositionLocalOf { EditorDisplaySettings() }

@Composable
fun Dex2cTheme(
  themeMode: AppThemeMode = AppThemeMode.SYSTEM,
  dynamicColor: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
  accentSeedHex: String = "#2EE59D",
  editorSettings: EditorDisplaySettings = EditorDisplaySettings(),
  content: @Composable () -> Unit,
) {
  val context = LocalContext.current
  val systemDark = isSystemInDarkTheme()
  val isAmoled = themeMode == AppThemeMode.AMOLED
  val isDark =
    when (themeMode) {
      AppThemeMode.SYSTEM -> systemDark
      AppThemeMode.LIGHT -> false
      AppThemeMode.DARK, AppThemeMode.AMOLED -> true
    }

  val seedColor = remember(accentSeedHex) { M3TonalColorEngine.parseHexOrDefault(accentSeedHex) }

  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val dynamic =
          if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        if (isAmoled) M3TonalColorEngine.applyAmoledOverride(dynamic) else dynamic
      }
      else ->
        remember(seedColor, isDark, isAmoled) {
          M3TonalColorEngine.buildSchemeFromSeed(
            seedColor = seedColor,
            isDark = isDark,
            isAmoled = isAmoled,
          )
        }
    }

  val syntaxPalette =
    when {
      !isDark -> LightSyntaxPalette
      isAmoled -> AmoledSyntaxPalette
      else -> DarkSyntaxPalette
    }

  CompositionLocalProvider(
    LocalSyntaxPalette provides syntaxPalette,
    LocalEditorSettings provides editorSettings,
  ) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = AppTypography,
      shapes = ExpressiveShapes,
      content = content,
    )
  }
}
