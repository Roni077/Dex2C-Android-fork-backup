package com.dex2c.studio.ui.components

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.dex2c.studio.data.CURATED_ACCENT_PRESETS
import com.dex2c.studio.data.isValidHexColor
import com.dex2c.studio.data.normalizeHexColor
import com.dex2c.studio.ui.theme.JetBrainsMonoFontFamily
import com.dex2c.studio.ui.theme.M3TonalColorEngine

object ExpressiveMotion {
  fun <T> spatialSpring() =
    spring<T>(
      dampingRatio = 0.86f,
      stiffness = Spring.StiffnessMedium,
    )

  fun <T> effectsSpring() =
    spring<T>(
      dampingRatio = Spring.DampingRatioNoBouncy,
      stiffness = Spring.StiffnessMedium,
    )

  fun <T> microSpring() =
    spring<T>(
      dampingRatio = 0.78f,
      stiffness = Spring.StiffnessMedium,
    )

  fun sharedAxisHorizontal(forward: Boolean): ContentTransform {
    val direction = if (forward) 1 else -1
    val spatial = spring<IntOffset>(dampingRatio = 0.88f, stiffness = Spring.StiffnessMedium)
    return (slideInHorizontally(animationSpec = spatial) { fullWidth -> (fullWidth * 0.14f * direction).toInt() } +
      fadeIn(animationSpec = tween(160)))
      .togetherWith(
        slideOutHorizontally(animationSpec = spatial) { fullWidth -> (-fullWidth * 0.10f * direction).toInt() } +
          fadeOut(animationSpec = tween(120))
      )
  }

  fun navEnterTransition(forward: Boolean) =
    slideInHorizontally(
      animationSpec = spring(dampingRatio = 0.88f, stiffness = Spring.StiffnessMedium),
    ) { w -> (w * (if (forward) 0.12f else -0.12f)).toInt() } +
      fadeIn(animationSpec = tween(160))

  fun navExitTransition(forward: Boolean) =
    slideOutHorizontally(
      animationSpec = spring(dampingRatio = 0.88f, stiffness = Spring.StiffnessMedium),
    ) { w -> (w * (if (forward) -0.10f else 0.10f)).toInt() } +
      fadeOut(animationSpec = tween(120))
}

fun Modifier.expressivePressScale(
  interactionSource: MutableInteractionSource,
  pressedScale: Float = 0.97f,
): Modifier = composed {
  val isPressed by interactionSource.collectIsPressedAsState()
  val scale by
    animateFloatAsState(
      targetValue = if (isPressed) pressedScale else 1f,
      animationSpec = ExpressiveMotion.microSpring(),
      label = "expressivePressScale",
    )
  this.graphicsLayer {
    scaleX = scale
    scaleY = scale
  }
}

@Composable
fun ExpressiveTonalCard(
  modifier: Modifier = Modifier,
  containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
  borderColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
  content: @Composable () -> Unit,
) {
  Surface(
    modifier = modifier.fillMaxWidth().animateContentSize(ExpressiveMotion.spatialSpring()),
    shape = RoundedCornerShape(24.dp),
    color = containerColor,
    border = BorderStroke(1.dp, borderColor),
  ) {
    Column(modifier = Modifier.padding(18.dp)) {
      content()
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccentColorPickerSection(
  selectedHex: String,
  enabled: Boolean,
  onSelectHex: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  var showCustomDialog by remember { mutableStateOf(false) }
  val isPreset = CURATED_ACCENT_PRESETS.any { it.hex.equals(selectedHex, ignoreCase = true) }

  Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      CURATED_ACCENT_PRESETS.forEach { preset ->
        val swatchColor = M3TonalColorEngine.parseHexOrDefault(preset.hex)
        val isSelected = selectedHex.equals(preset.hex, ignoreCase = true)
        AccentSwatchDot(
          color = swatchColor,
          label = preset.name,
          selected = isSelected,
          enabled = enabled,
          onClick = { onSelectHex(preset.hex) },
          testTag = "accent_swatch_${preset.name.lowercase().replace(' ', '_')}",
        )
      }

      // Custom Hex Trigger Swatch
      val customColor = M3TonalColorEngine.parseHexOrDefault(selectedHex)
      Surface(
        shape = CircleShape,
        color = if (!isPreset) customColor else MaterialTheme.colorScheme.surfaceContainerHigh,
        border =
          BorderStroke(
            width = if (!isPreset) 2.5.dp else 1.dp,
            color = if (!isPreset) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
          ),
        modifier =
          Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled) { showCustomDialog = true }
            .testTag("accent_custom_picker_btn"),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Rounded.ColorLens,
            contentDescription = "Custom hex color picker",
            tint =
              if (!isPreset) {
                if (customColor.red * 0.299f + customColor.green * 0.587f + customColor.blue * 0.114f > 0.55f)
                  Color.Black
                else Color.White
              } else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(22.dp),
          )
        }
      }
    }

    val activePresetName =
      CURATED_ACCENT_PRESETS.firstOrNull { it.hex.equals(selectedHex, ignoreCase = true) }?.name
    Text(
      text =
        if (enabled) {
          val normalized = normalizeHexColor(selectedHex)
          if (activePresetName != null) "Active seed: $activePresetName ($normalized)"
          else "Active seed: $normalized"
        } else {
          "Disable Dynamic Wallpaper Colors above to use a custom accent seed"
        },
      style = MaterialTheme.typography.bodySmall,
      fontFamily = if (enabled) JetBrainsMonoFontFamily else MaterialTheme.typography.bodySmall.fontFamily,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }

  if (showCustomDialog) {
    CustomAccentHexDialog(
      initialHex = selectedHex,
      onApply = { hex ->
        showCustomDialog = false
        onSelectHex(hex)
      },
      onDismiss = { showCustomDialog = false },
    )
  }
}

@Composable
private fun AccentSwatchDot(
  color: Color,
  label: String,
  selected: Boolean,
  enabled: Boolean,
  onClick: () -> Unit,
  testTag: String,
) {
  val interactionSource = remember { MutableInteractionSource() }
  Surface(
    shape = CircleShape,
    color = if (enabled) color else color.copy(alpha = 0.35f),
    border =
      if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface)
      else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    modifier =
      Modifier
        .size(48.dp)
        .expressivePressScale(interactionSource, 0.92f)
        .clip(CircleShape)
        .clickable(enabled = enabled, interactionSource = interactionSource, indication = null, onClick = onClick)
        .testTag(testTag),
  ) {
    if (selected) {
      val checkTint =
        if (color.red * 0.299f + color.green * 0.587f + color.blue * 0.114f > 0.58f)
          Color.Black
        else Color.White
      Box(contentAlignment = Alignment.Center) {
        Icon(
          imageVector = Icons.Rounded.Check,
          contentDescription = "$label selected",
          tint = checkTint,
          modifier = Modifier.size(22.dp),
        )
      }
    }
  }
}

@Composable
fun CustomAccentHexDialog(
  initialHex: String,
  onApply: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  val initialColor = remember(initialHex) { M3TonalColorEngine.parseHexOrDefault(initialHex) }
  val initialHsl = remember(initialColor) { M3TonalColorEngine.extractHsl(initialColor) }
  var hue by remember { mutableFloatStateOf(initialHsl[0]) }
  var sat by remember { mutableFloatStateOf(initialHsl[1].coerceIn(0.35f, 1f)) }
  var light by remember { mutableFloatStateOf(initialHsl[2].coerceIn(0.35f, 0.72f)) }
  var hexText by remember { mutableStateOf(normalizeHexColor(initialHex)) }

  val liveColor = M3TonalColorEngine.fromHsl(hue, sat, light)
  val hueGradient = remember {
    Brush.horizontalGradient(
      listOf(
        Color(0xFFFF4D4D),
        Color(0xFFFFFF4D),
        Color(0xFF4DFF4D),
        Color(0xFF4DFFFF),
        Color(0xFF4D4DFF),
        Color(0xFFFF4DFF),
        Color(0xFFFF4D4D),
      )
    )
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    shape = RoundedCornerShape(28.dp),
    title = { Text("Custom Accent Seed", style = MaterialTheme.typography.headlineSmall) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
          modifier = Modifier.fillMaxWidth().height(56.dp),
          shape = RoundedCornerShape(18.dp),
          color = liveColor,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
          Box(contentAlignment = Alignment.Center) {
            val onPreview =
              if (liveColor.red * 0.299f + liveColor.green * 0.587f + liveColor.blue * 0.114f > 0.55f)
                Color.Black
              else Color.White
            Text(
              text = M3TonalColorEngine.toHexString(liveColor),
              style = MaterialTheme.typography.titleMedium,
              fontFamily = JetBrainsMonoFontFamily,
              color = onPreview,
            )
          }
        }

        Column {
          Text("Hue", style = MaterialTheme.typography.labelLarge)
          Spacer(Modifier.height(4.dp))
          Box(
            modifier =
              Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
                .background(hueGradient),
          )
          Slider(
            value = hue,
            onValueChange = { newH ->
              hue = newH
              hexText = M3TonalColorEngine.toHexString(M3TonalColorEngine.fromHsl(hue, sat, light))
            },
            valueRange = 0f..360f,
            colors = SliderDefaults.colors(thumbColor = liveColor, activeTrackColor = liveColor),
            modifier = Modifier.fillMaxWidth().testTag("custom_accent_hue_slider"),
          )
        }

        Column {
          Text("Vividness", style = MaterialTheme.typography.labelLarge)
          Slider(
            value = sat,
            onValueChange = { newS ->
              sat = newS
              hexText = M3TonalColorEngine.toHexString(M3TonalColorEngine.fromHsl(hue, sat, light))
            },
            valueRange = 0.25f..1f,
            modifier = Modifier.fillMaxWidth().testTag("custom_accent_sat_slider"),
          )
        }

        OutlinedTextField(
          value = hexText,
          onValueChange = { raw ->
            hexText = raw.uppercase()
            if (isValidHexColor(raw)) {
              val parsed = M3TonalColorEngine.parseHexOrDefault(raw)
              val hsl = M3TonalColorEngine.extractHsl(parsed)
              hue = hsl[0]
              sat = hsl[1]
              light = hsl[2]
            }
          },
          label = { Text("Hex Color (#RRGGBB)") },
          singleLine = true,
          isError = !isValidHexColor(hexText),
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier.fillMaxWidth().testTag("custom_accent_hex_input"),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = { onApply(M3TonalColorEngine.toHexString(liveColor)) },
        enabled = isValidHexColor(hexText),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.minimumInteractiveComponentSize().testTag("custom_accent_apply_btn"),
      ) {
        Text("Apply Accent")
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDismiss,
        modifier = Modifier.minimumInteractiveComponentSize(),
      ) {
        Text("Cancel")
      }
    },
  )
}
