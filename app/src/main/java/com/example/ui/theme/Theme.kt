package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val DarkColorScheme =
  darkColorScheme(
    primary = PhosphorMint,
    onPrimary = PhosphorMintDark,
    primaryContainer = PhosphorMintContainer,
    onPrimaryContainer = PhosphorMintOnContainer,
    secondary = AmberQuartz,
    onSecondary = AmberQuartzDark,
    secondaryContainer = AmberQuartzContainer,
    onSecondaryContainer = AmberQuartzOnContainer,
    tertiary = ElectricCyan,
    onTertiary = ElectricCyanDark,
    tertiaryContainer = ElectricCyanContainer,
    onTertiaryContainer = ElectricCyanOnContainer,
    error = CrimsonSignal,
    errorContainer = CrimsonContainer,
    background = ObsidianVoid,
    onBackground = SlateTextPrimary,
    surface = ObsidianSurface,
    onSurface = SlateTextPrimary,
    surfaceVariant = ObsidianContainer,
    onSurfaceVariant = SlateTextSecondary,
    surfaceContainerLowest = ObsidianVoid,
    surfaceContainerLow = ObsidianContainerLow,
    surfaceContainer = ObsidianContainer,
    surfaceContainerHigh = ObsidianContainerHigh,
    outline = ObsidianOutline,
    outlineVariant = ObsidianOutline.copy(alpha = 0.45f),
  )

private val LightColorScheme =
  lightColorScheme(
    primary = LightMintPrimary,
    onPrimary = LightLabSurface,
    primaryContainer = LightMintContainer,
    onPrimaryContainer = LightMintOnContainer,
    secondary = LightAmberSecondary,
    onSecondary = LightLabSurface,
    secondaryContainer = LightAmberContainer,
    onSecondaryContainer = LightAmberOnContainer,
    tertiary = LightCyanTertiary,
    onTertiary = LightLabSurface,
    tertiaryContainer = LightCyanContainer,
    onTertiaryContainer = LightCyanOnContainer,
    background = LightLabBackground,
    onBackground = LightTextPrimary,
    surface = LightLabSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightLabContainer,
    onSurfaceVariant = LightTextSecondary,
    surfaceContainerLow = LightLabSurface,
    surfaceContainer = LightLabContainer,
    surfaceContainerHigh = LightLabContainerHigh,
    outline = LightLabOutline,
  )

private val AppShapes =
  Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
  )

@Immutable
data class EditorDisplaySettings(
  val fontSizeSp: Int = 12,
  val showLineNumbers: Boolean = true,
  val maxLinesLimit: Int = 2500,
)

val LocalSyntaxPalette = staticCompositionLocalOf { DarkSyntaxPalette }
val LocalEditorSettings = staticCompositionLocalOf { EditorDisplaySettings() }

@Composable
fun Dex2cTheme(
  darkTheme: Boolean = true,
  dynamicColor: Boolean = false,
  editorSettings: EditorDisplaySettings = EditorDisplaySettings(),
  content: @Composable () -> Unit,
) {
  val context = LocalContext.current
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }
      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }
  val syntaxPalette = if (darkTheme) DarkSyntaxPalette else LightSyntaxPalette

  CompositionLocalProvider(
    LocalSyntaxPalette provides syntaxPalette,
    LocalEditorSettings provides editorSettings,
  ) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = AppTypography,
      shapes = AppShapes,
      content = content,
    )
  }
}
