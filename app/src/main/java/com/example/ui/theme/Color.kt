package com.example.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.example.data.isValidHexColor
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// Brand Semantic Colors
val ObsidianVoid = Color(0xFF070C14)
val ObsidianSurface = Color(0xFF0C1420)
val ObsidianContainerLow = Color(0xFF111B2B)
val ObsidianContainer = Color(0xFF162235)
val ObsidianContainerHigh = Color(0xFF1D2C44)
val ObsidianOutline = Color(0xFF2D415E)

val AmoledBlack = Color(0xFF000000)
val AmoledContainerLow = Color(0xFF080B10)
val AmoledContainer = Color(0xFF0E131B)
val AmoledContainerHigh = Color(0xFF151D29)
val AmoledContainerHighest = Color(0xFF1D2736)
val AmoledOutline = Color(0xFF28374E)

val PhosphorMint = Color(0xFF2EE59D)
val AmberQuartz = Color(0xFFFFB454)
val ElectricCyan = Color(0xFF4FD1FF)
val CrimsonSignal = Color(0xFFFF6B7A)
val CrimsonContainer = Color(0xFF4D141D)

@Immutable
data class SyntaxPalette(
  val codeBackground: Color,
  val lineNumber: Color,
  val defaultText: Color,
  val keyword: Color,
  val typeName: Color,
  val stringLiteral: Color,
  val comment: Color,
  val numberLiteral: Color,
  val opcode: Color,
  val register: Color,
  val label: Color,
)

val DarkSyntaxPalette =
  SyntaxPalette(
    codeBackground = Color(0xFF080E18),
    lineNumber = Color(0xFF546883),
    defaultText = Color(0xFFDCE6F4),
    keyword = Color(0xFFFF7B93),
    typeName = Color(0xFF4FD1FF),
    stringLiteral = Color(0xFF2EE59D),
    comment = Color(0xFF6A809E),
    numberLiteral = Color(0xFFFFB454),
    opcode = Color(0xFF7BE8BC),
    register = Color(0xFFFFC978),
    label = Color(0xFFD1A4FF),
  )

val AmoledSyntaxPalette =
  DarkSyntaxPalette.copy(
    codeBackground = Color(0xFF000000),
    lineNumber = Color(0xFF627894),
  )

val LightSyntaxPalette =
  SyntaxPalette(
    codeBackground = Color(0xFFF6F8FC),
    lineNumber = Color(0xFF7F8FA6),
    defaultText = Color(0xFF1A2433),
    keyword = Color(0xFFB31D46),
    typeName = Color(0xFF006783),
    stringLiteral = Color(0xFF007A4E),
    comment = Color(0xFF64748B),
    numberLiteral = Color(0xFFA85600),
    opcode = Color(0xFF02724A),
    register = Color(0xFF9E5000),
    label = Color(0xFF6B35B5),
  )

/**
 * Pure Kotlin Material 3 tonal palette synthesizer deriving full accessible ColorSchemes
 * from any seed color across Light, Dark, and pure AMOLED Black (#000000) themes.
 */
object M3TonalColorEngine {

  fun parseHexOrDefault(hex: String, fallback: Color = PhosphorMint): Color {
    if (!isValidHexColor(hex)) return fallback
    val clean = hex.trim().removePrefix("#")
    val rgb = clean.toLongOrNull(16) ?: return fallback
    return Color((0xFF000000L or rgb).toInt())
  }

  fun toHexString(color: Color): String {
    val r = (color.red * 255f).toInt().coerceIn(0, 255)
    val g = (color.green * 255f).toInt().coerceIn(0, 255)
    val b = (color.blue * 255f).toInt().coerceIn(0, 255)
    return "#%02X%02X%02X".format(r, g, b)
  }

  fun extractHsl(color: Color): FloatArray {
    val r = color.red
    val g = color.green
    val b = color.blue
    val maxC = max(r, max(g, b))
    val minC = min(r, min(g, b))
    val l = (maxC + minC) / 2f
    if (maxC == minC) return floatArrayOf(0f, 0f, l)
    val d = maxC - minC
    val s = if (l > 0.5f) d / (2f - maxC - minC) else d / (maxC + minC)
    val h =
      when (maxC) {
        r -> ((g - b) / d + (if (g < b) 6f else 0f)) * 60f
        g -> ((b - r) / d + 2f) * 60f
        else -> ((r - g) / d + 4f) * 60f
      }
    return floatArrayOf(h.mod(360f), s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))
  }

  fun fromHsl(hue: Float, saturation: Float, lightness: Float): Color {
    val h = hue.mod(360f)
    val s = saturation.coerceIn(0f, 1f)
    val l = lightness.coerceIn(0f, 1f)
    val c = (1f - abs(2f * l - 1f)) * s
    val x = c * (1f - abs((h / 60f).mod(2f) - 1f))
    val m = l - c / 2f
    val (r1, g1, b1) =
      when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
      }
    return Color(
      red = (r1 + m).coerceIn(0f, 1f),
      green = (g1 + m).coerceIn(0f, 1f),
      blue = (b1 + m).coerceIn(0f, 1f),
    )
  }

  fun buildSchemeFromSeed(
    seedColor: Color,
    isDark: Boolean,
    isAmoled: Boolean,
  ): ColorScheme {
    val hsl = extractHsl(seedColor)
    val hue = hsl[0]
    val sat = hsl[1].coerceAtLeast(0.45f)
    val secondaryHue = (hue + 38f).mod(360f)
    val tertiaryHue = (hue + 148f).mod(360f)

    if (!isDark) {
      val primary = fromHsl(hue, sat.coerceIn(0.55f, 0.90f), 0.36f)
      val onPrimary = if (primary.luminance() > 0.35f) Color(0xFF0A141E) else Color.White
      val primaryContainer = fromHsl(hue, sat.coerceIn(0.45f, 0.85f), 0.89f)
      val onPrimaryContainer = fromHsl(hue, sat.coerceIn(0.55f, 0.95f), 0.14f)

      val secondary = fromHsl(secondaryHue, (sat * 0.65f).coerceIn(0.32f, 0.72f), 0.38f)
      val onSecondary = Color.White
      val secondaryContainer = fromHsl(secondaryHue, (sat * 0.55f).coerceIn(0.28f, 0.68f), 0.90f)
      val onSecondaryContainer = fromHsl(secondaryHue, (sat * 0.65f).coerceIn(0.35f, 0.75f), 0.14f)

      val tertiary = fromHsl(tertiaryHue, (sat * 0.70f).coerceIn(0.38f, 0.80f), 0.37f)
      val onTertiary = Color.White
      val tertiaryContainer = fromHsl(tertiaryHue, (sat * 0.60f).coerceIn(0.32f, 0.75f), 0.90f)
      val onTertiaryContainer = fromHsl(tertiaryHue, (sat * 0.70f).coerceIn(0.40f, 0.80f), 0.14f)

      val neutralTintBg = fromHsl(hue, 0.04f, 0.975f)
      val surface = Color(0xFFFFFFFF)
      val surfaceLow = fromHsl(hue, 0.04f, 0.955f)
      val surfaceContainer = fromHsl(hue, 0.04f, 0.925f)
      val surfaceHigh = fromHsl(hue, 0.05f, 0.89f)
      val surfaceHighest = fromHsl(hue, 0.05f, 0.85f)

      return lightColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        inversePrimary = fromHsl(hue, sat, 0.76f),
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary,
        onTertiary = onTertiary,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer,
        background = neutralTintBg,
        onBackground = Color(0xFF0E1621),
        surface = surface,
        onSurface = Color(0xFF0E1621),
        surfaceVariant = surfaceContainer,
        onSurfaceVariant = Color(0xFF43536B),
        surfaceTint = primary,
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = surfaceLow,
        surfaceContainer = surfaceContainer,
        surfaceContainerHigh = surfaceHigh,
        surfaceContainerHighest = surfaceHighest,
        outline = Color(0xFF74849B),
        outlineVariant = Color(0xFFC5D0DF),
        inverseSurface = Color(0xFF1A2433),
        inverseOnSurface = Color(0xFFF1F5FA),
        error = Color(0xFFBA1A2F),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD9),
        onErrorContainer = Color(0xFF410008),
      )
    }

    // Dark or AMOLED Black (neutral obsidian slate surfaces so vivid accent seeds don't muddy backgrounds)
    val primary = fromHsl(hue, sat.coerceIn(0.55f, 0.92f), 0.70f)
    val onPrimary = fromHsl(hue, sat.coerceIn(0.55f, 0.90f), 0.13f)
    val primaryContainer = fromHsl(hue, sat.coerceIn(0.45f, 0.82f), 0.22f)
    val onPrimaryContainer = fromHsl(hue, sat.coerceIn(0.55f, 0.92f), 0.90f)

    val secondary = fromHsl(secondaryHue, (sat * 0.70f).coerceIn(0.35f, 0.78f), 0.72f)
    val onSecondary = fromHsl(secondaryHue, 0.65f, 0.14f)
    val secondaryContainer = fromHsl(secondaryHue, (sat * 0.55f).coerceIn(0.30f, 0.68f), 0.22f)
    val onSecondaryContainer = fromHsl(secondaryHue, 0.72f, 0.90f)

    val tertiary = fromHsl(tertiaryHue, (sat * 0.75f).coerceIn(0.40f, 0.85f), 0.74f)
    val onTertiary = fromHsl(tertiaryHue, 0.65f, 0.14f)
    val tertiaryContainer = fromHsl(tertiaryHue, (sat * 0.60f).coerceIn(0.35f, 0.72f), 0.22f)
    val onTertiaryContainer = fromHsl(tertiaryHue, 0.75f, 0.91f)

    val darkScheme =
      darkColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        inversePrimary = fromHsl(hue, sat, 0.36f),
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary,
        onTertiary = onTertiary,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer,
        background = fromHsl(hue, 0.04f, 0.055f),
        onBackground = Color(0xFFEAF1FA),
        surface = fromHsl(hue, 0.04f, 0.075f),
        onSurface = Color(0xFFEAF1FA),
        surfaceVariant = fromHsl(hue, 0.05f, 0.135f),
        onSurfaceVariant = Color(0xFFA2AFBF),
        surfaceTint = Color.Transparent,
        surfaceContainerLowest = fromHsl(hue, 0.04f, 0.045f),
        surfaceContainerLow = fromHsl(hue, 0.05f, 0.09f),
        surfaceContainer = fromHsl(hue, 0.05f, 0.125f),
        surfaceContainerHigh = fromHsl(hue, 0.05f, 0.165f),
        surfaceContainerHighest = fromHsl(hue, 0.05f, 0.21f),
        outline = Color(0xFF6B7785),
        outlineVariant = Color(0xFF2B323B),
        inverseSurface = Color(0xFFEAF1FA),
        inverseOnSurface = Color(0xFF14181F),
        error = CrimsonSignal,
        onError = Color(0xFF4A0710),
        errorContainer = CrimsonContainer,
        onErrorContainer = Color(0xFFFFD9DD),
      )

    return if (isAmoled) applyAmoledOverride(darkScheme) else darkScheme
  }

  /**
   * Transforms any dark or dynamic dark ColorScheme into pure #000000 AMOLED black
   * while preserving its dynamic/custom accent roles and accessible tonal containers.
   */
  fun applyAmoledOverride(baseDark: ColorScheme): ColorScheme =
    baseDark.copy(
      background = AmoledBlack,
      surface = AmoledBlack,
      surfaceContainerLowest = AmoledBlack,
      surfaceContainerLow = AmoledContainerLow,
      surfaceContainer = AmoledContainer,
      surfaceContainerHigh = AmoledContainerHigh,
      surfaceContainerHighest = AmoledContainerHighest,
      surfaceVariant = AmoledContainer,
      outlineVariant = AmoledOutline,
    )
}
