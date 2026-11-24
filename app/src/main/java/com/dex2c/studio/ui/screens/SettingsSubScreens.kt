package com.dex2c.studio.ui.screens

import android.content.Context
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dex2c.studio.BuildConfig
import com.dex2c.studio.R
import com.dex2c.studio.cloud.CloudSettings
import com.dex2c.studio.cloud.CloudSigningMode
import com.dex2c.studio.cloud.Dex2cWorkflowGenerator
import com.dex2c.studio.cloud.SUPPORTED_JDK_VERSIONS
import com.dex2c.studio.cloud.SUPPORTED_NDK_VERSIONS
import com.dex2c.studio.data.AppIconManager
import com.dex2c.studio.data.AppIconStyle
import com.dex2c.studio.data.AppLanguage
import com.dex2c.studio.data.AppSettings
import com.dex2c.studio.data.AppThemeMode
import com.dex2c.studio.data.CURATED_ACCENT_PRESETS
import com.dex2c.studio.data.EditorEngineType
import com.dex2c.studio.data.SmaliSyntaxTheme
import com.dex2c.studio.data.normalizeHexColor
import com.dex2c.studio.transpiler.TranspilerConfig
import com.dex2c.studio.ui.components.AccentColorPickerSection
import com.dex2c.studio.ui.components.ExpressiveMotion
import com.dex2c.studio.ui.components.SyntaxCodeViewer
import com.dex2c.studio.ui.components.TagPill
import com.dex2c.studio.ui.components.expressivePressScale
import com.dex2c.studio.ui.theme.JetBrainsMonoFontFamily
import kotlin.math.roundToInt

@Composable
fun SettingsNavRow(
  icon: ImageVector,
  title: String,
  subtitle: String,
  badgeText: String? = null,
  onClick: () -> Unit,
  testTag: String? = null,
) {
  val interactionSource = remember { MutableInteractionSource() }
  Surface(
    shape = RoundedCornerShape(20.dp),
    color = MaterialTheme.colorScheme.surfaceContainer,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    modifier =
      Modifier.fillMaxWidth()
        .expressivePressScale(interactionSource)
        .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
        .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
  ) {
    Row(
      modifier = Modifier.padding(16.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.size(40.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp))
        }
      }
      Column(modifier = Modifier.weight(1f)) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
          if (badgeText != null) {
            TagPill(text = badgeText)
          }
        }
        Spacer(Modifier.height(2.dp))
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Icon(
        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
        contentDescription = "Open $title",
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(20.dp),
      )
    }
  }
}

@Composable
fun SettingsNestedHeader(
  title: String,
  subtitle: String? = null,
  onBack: () -> Unit,
  testTag: String = "nested_back_btn",
) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    IconButton(
      onClick = onBack,
      modifier = Modifier.minimumInteractiveComponentSize().testTag(testTag),
    ) {
      Icon(
        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
        contentDescription = "Back",
      )
    }
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
      )
      if (subtitle != null) {
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

// ------------------------------------------------------------------------------------------------
// 1. APPEARANCE SUB-SCREEN
// ------------------------------------------------------------------------------------------------

@Composable
fun SettingsAppearanceSubScreen(
  appSettings: AppSettings,
  onUpdateAppSettings: ((AppSettings) -> AppSettings) -> Unit,
) {
  var activeNested by rememberSaveable { mutableStateOf<String?>(null) }
  BackHandler(enabled = activeNested != null) { activeNested = null }

  val colorSourceLabel =
    if (appSettings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      "Dynamic Wallpaper"
    } else {
      CURATED_ACCENT_PRESETS.firstOrNull {
        it.hex.equals(appSettings.accentSeedHex, ignoreCase = true)
      }?.name ?: normalizeHexColor(appSettings.accentSeedHex)
    }

  AnimatedContent(
    targetState = activeNested,
    transitionSpec = { ExpressiveMotion.sharedAxisHorizontal(targetState != null) },
    label = "appearanceNestedTransition",
  ) { nested ->
    when (nested) {
      null -> {
        LazyColumn(
          modifier = Modifier.fillMaxSize().testTag("settings_appearance_list"),
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          item {
            SettingsNavRow(
              icon = Icons.Rounded.Palette,
              title = stringResource(R.string.settings_nav_theme),
              subtitle = "${stringResource(appSettings.themeMode.labelRes)} • $colorSourceLabel",
              onClick = { activeNested = "THEME" },
              testTag = "settings_nav_theme",
            )
          }
          item {
            SettingsNavRow(
              icon = Icons.Rounded.AutoAwesome,
              title = stringResource(R.string.settings_nav_app_icon),
              subtitle = stringResource(appSettings.appIconStyle.labelRes),
              onClick = { activeNested = "APP_ICON" },
              testTag = "settings_nav_app_icon",
            )
          }
          item {
            SettingsNavRow(
              icon = Icons.Rounded.Translate,
              title = stringResource(R.string.settings_nav_language),
              subtitle = stringResource(appSettings.appLanguage.displayNameRes),
              onClick = { activeNested = "LANGUAGE" },
              testTag = "settings_nav_language",
            )
          }
        }
      }

      "THEME" -> {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          item {
            SettingsNestedHeader(
              title = stringResource(R.string.settings_nav_theme),
              subtitle = stringResource(R.string.settings_nav_theme_desc),
              onBack = { activeNested = null },
            )
          }

          item {
            SubScreenCard(title = stringResource(R.string.settings_card_theme_posture), icon = Icons.Rounded.Palette) {
              AppThemeMode.entries.forEachIndexed { index, mode ->
                val modeIcon =
                  when (mode) {
                    AppThemeMode.SYSTEM -> Icons.Rounded.BrightnessAuto
                    AppThemeMode.LIGHT -> Icons.Rounded.LightMode
                    AppThemeMode.DARK -> Icons.Rounded.DarkMode
                    AppThemeMode.AMOLED -> Icons.Rounded.Contrast
                  }
                val isSelected = appSettings.themeMode == mode
                Row(
                  modifier =
                    Modifier.fillMaxWidth()
                      .clickable { onUpdateAppSettings { it.copy(themeMode = mode) } }
                      .padding(vertical = 8.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                  Surface(
                    shape = RoundedCornerShape(10.dp),
                    color =
                      if (isSelected) MaterialTheme.colorScheme.primaryContainer
                      else MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor =
                      if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                      else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(34.dp),
                  ) {
                    Box(contentAlignment = Alignment.Center) {
                      Icon(imageVector = modeIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                  }
                  Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(mode.labelRes), style = MaterialTheme.typography.titleSmall)
                    Text(
                      text = stringResource(mode.summaryRes),
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                  RadioButton(
                    selected = isSelected,
                    onClick = { onUpdateAppSettings { it.copy(themeMode = mode) } },
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("settings_theme_${mode.name}"),
                  )
                }
                if (index < AppThemeMode.entries.lastIndex) {
                  HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                }
              }
            }
          }

          item {
            SubScreenCard(title = stringResource(R.string.settings_card_dynamic_colors), icon = Icons.Rounded.AutoAwesome) {
              val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
              SettingsToggleRow(
                title = stringResource(R.string.settings_dynamic_palette),
                subtitle =
                  if (supportsDynamic) stringResource(R.string.settings_dynamic_palette_desc)
                  else "Available on Android 12+ (custom accent seed used below)",
                checked = appSettings.dynamicColor,
                enabled = supportsDynamic,
                onCheckedChange = { enabled -> onUpdateAppSettings { it.copy(dynamicColor = enabled) } },
                testTag = "settings_dynamic_color_switch",
              )
            }
          }

          item {
            SubScreenCard(title = stringResource(R.string.settings_card_custom_accent), icon = Icons.Rounded.ColorLens) {
              AccentColorPickerSection(
                selectedHex = appSettings.accentSeedHex,
                enabled = !appSettings.dynamicColor || Build.VERSION.SDK_INT < Build.VERSION_CODES.S,
                onSelectHex = { hex -> onUpdateAppSettings { it.copy(accentSeedHex = hex, dynamicColor = false) } },
              )
            }
          }
        }
      }

      "APP_ICON" -> {
        val context = LocalContext.current
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          item {
            SettingsNestedHeader(
              title = stringResource(R.string.settings_nav_app_icon),
              subtitle = stringResource(R.string.settings_nav_app_icon_desc),
              onBack = { activeNested = null },
            )
          }

          item {
            SubScreenCard(title = stringResource(R.string.settings_card_launcher_icon), icon = Icons.Rounded.AutoAwesome) {
              Text(
                text = stringResource(R.string.settings_launcher_icon_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Spacer(Modifier.height(12.dp))
              AppIconStyle.entries.forEachIndexed { index, style ->
                val isSelected = appSettings.appIconStyle == style
                Row(
                  modifier =
                    Modifier.fillMaxWidth()
                      .clip(RoundedCornerShape(12.dp))
                      .clickable {
                        onUpdateAppSettings { it.copy(appIconStyle = style) }
                        AppIconManager.setAppIcon(context, style)
                      }
                      .padding(vertical = 8.dp, horizontal = 4.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                  Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(
                      width = if (isSelected) 2.dp else 1.dp,
                      color =
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    ),
                    modifier = Modifier.size(46.dp),
                  ) {
                    Image(
                      painter = painterResource(id = style.previewRes),
                      contentDescription = stringResource(style.labelRes),
                      contentScale = ContentScale.Crop,
                      modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                    )
                  }
                  Column(modifier = Modifier.weight(1f)) {
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                      Text(stringResource(style.labelRes), style = MaterialTheme.typography.titleSmall)
                      if (style == AppIconStyle.DEFAULT) {
                        TagPill(
                          text = "Default",
                          containerColor = MaterialTheme.colorScheme.primaryContainer,
                          contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                      }
                    }
                    Text(
                      text = stringResource(style.descriptionRes),
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                  RadioButton(
                    selected = isSelected,
                    onClick = {
                      onUpdateAppSettings { it.copy(appIconStyle = style) }
                      AppIconManager.setAppIcon(context, style)
                    },
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("settings_app_icon_${style.name}"),
                  )
                }
                if (index < AppIconStyle.entries.lastIndex) {
                  HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                }
              }
            }
          }
        }
      }

      "LANGUAGE" -> {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          item {
            SettingsNestedHeader(
              title = stringResource(R.string.settings_nav_language),
              subtitle = stringResource(R.string.settings_nav_language_desc),
              onBack = { activeNested = null },
            )
          }

          item {
            SubScreenCard(title = stringResource(R.string.language_title), icon = Icons.Rounded.Translate) {
              Text(
                text = stringResource(R.string.language_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Spacer(Modifier.height(12.dp))
              AppLanguage.entries.forEachIndexed { index, language ->
                val isSelected = appSettings.appLanguage == language
                Row(
                  modifier =
                    Modifier.fillMaxWidth()
                      .clip(RoundedCornerShape(12.dp))
                      .clickable { onUpdateAppSettings { it.copy(appLanguage = language) } }
                      .padding(vertical = 10.dp, horizontal = 4.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                  Surface(
                    shape = RoundedCornerShape(10.dp),
                    color =
                      if (isSelected) MaterialTheme.colorScheme.primaryContainer
                      else MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor =
                      if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                      else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(36.dp),
                  ) {
                    Box(contentAlignment = Alignment.Center) {
                      Text(
                        text = when (language) {
                          AppLanguage.SYSTEM -> "⚙"
                          AppLanguage.ENGLISH -> "EN"
                          AppLanguage.CHINESE_SIMPLIFIED -> "中"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                      )
                    }
                  }
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = stringResource(language.displayNameRes),
                      style = MaterialTheme.typography.titleSmall,
                      fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                    Text(
                      text = when (language) {
                        AppLanguage.SYSTEM -> "Match device system language"
                        AppLanguage.ENGLISH -> "English (US)"
                        AppLanguage.CHINESE_SIMPLIFIED -> "简体中文 (Simplified Chinese)"
                      },
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                  RadioButton(
                    selected = isSelected,
                    onClick = { onUpdateAppSettings { it.copy(appLanguage = language) } },
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("settings_language_${language.name.lowercase()}"),
                  )
                }
                if (index < AppLanguage.entries.lastIndex) {
                  HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                }
              }
            }
          }
        }
      }
    }
  }
}

// ------------------------------------------------------------------------------------------------
// 2. CODE EDITOR SUB-SCREEN
// ------------------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsCodeEditorSubScreen(
  appSettings: AppSettings,
  onUpdateAppSettings: ((AppSettings) -> AppSettings) -> Unit,
) {
  var activeNested by rememberSaveable { mutableStateOf<String?>(null) }
  BackHandler(enabled = activeNested != null) { activeNested = null }

  AnimatedContent(
    targetState = activeNested,
    transitionSpec = { ExpressiveMotion.sharedAxisHorizontal(targetState != null) },
    label = "codeEditorNestedTransition",
  ) { nested ->
    when (nested) {
      null -> {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          item {
            SettingsNavRow(
              icon = Icons.Rounded.Memory,
              title = stringResource(R.string.settings_nav_editor_engine),
              subtitle = stringResource(appSettings.editorEngine.titleRes),
              onClick = { activeNested = "ENGINE" },
              testTag = "settings_nav_editor_engine",
            )
          }
          item {
            val wrapSummary = if (appSettings.codeWordWrap) "Wrap ON" else "Wrap OFF"
            SettingsNavRow(
              icon = Icons.Rounded.FormatSize,
              title = stringResource(R.string.settings_nav_typography),
              subtitle = "${appSettings.codeFontSizeSp} sp • $wrapSummary • Cap ${appSettings.maxCodePreviewLines}",
              onClick = { activeNested = "TYPOGRAPHY" },
              testTag = "settings_nav_typography",
            )
          }
          item {
            SettingsNavRow(
              icon = Icons.Rounded.Palette,
              title = stringResource(R.string.settings_nav_syntax_themes),
              subtitle = stringResource(appSettings.smaliSyntaxTheme.labelRes),
              onClick = { activeNested = "SYNTAX_THEMES" },
              testTag = "settings_nav_syntax_themes",
            )
          }
        }
      }

      "ENGINE" -> {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          item {
            SettingsNestedHeader(
              title = stringResource(R.string.settings_nav_editor_engine),
              subtitle = stringResource(R.string.settings_nav_editor_engine_desc),
              onBack = { activeNested = null },
            )
          }
          item {
            SubScreenCard(title = stringResource(R.string.settings_card_editor_engine), icon = Icons.Rounded.Code) {
              Text(
                text = stringResource(R.string.settings_editor_engine_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Spacer(Modifier.height(10.dp))
              FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                EditorEngineType.entries.forEach { engine ->
                  FilterChip(
                    selected = appSettings.editorEngine == engine,
                    onClick = { onUpdateAppSettings { it.copy(editorEngine = engine) } },
                    label = { Text(stringResource(engine.titleRes)) },
                    modifier = Modifier.testTag("settings_editor_engine_${engine.name.lowercase()}"),
                  )
                }
              }
            }
          }
        }
      }

      "TYPOGRAPHY" -> {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          item {
            SettingsNestedHeader(
              title = stringResource(R.string.settings_nav_typography),
              subtitle = stringResource(R.string.settings_nav_typography_desc),
              onBack = { activeNested = null },
            )
          }
          item {
            SubScreenCard(title = stringResource(R.string.settings_card_typography), icon = Icons.Rounded.FormatSize) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(stringResource(R.string.settings_font_size), style = MaterialTheme.typography.titleSmall)
                TagPill(text = "${appSettings.codeFontSizeSp} sp")
              }
              Slider(
                value = appSettings.codeFontSizeSp.toFloat(),
                onValueChange = { v -> onUpdateAppSettings { it.copy(codeFontSizeSp = v.roundToInt()) } },
                valueRange = 10f..18f,
                steps = 7,
                modifier = Modifier.fillMaxWidth().testTag("settings_code_font_slider"),
              )
              Spacer(Modifier.height(6.dp))
              SettingsToggleRow(
                title = stringResource(R.string.settings_show_line_numbers),
                subtitle = stringResource(R.string.settings_show_line_numbers_desc),
                checked = appSettings.showLineNumbers,
                onCheckedChange = { show -> onUpdateAppSettings { it.copy(showLineNumbers = show) } },
                testTag = "settings_show_line_numbers_switch",
              )
              Spacer(Modifier.height(6.dp))
              SettingsToggleRow(
                title = stringResource(R.string.settings_code_word_wrap),
                subtitle = stringResource(R.string.settings_code_word_wrap_desc),
                checked = appSettings.codeWordWrap,
                onCheckedChange = { wrap -> onUpdateAppSettings { it.copy(codeWordWrap = wrap) } },
                testTag = "settings_code_word_wrap_switch",
              )
              Spacer(Modifier.height(10.dp))
              Text(
                text = "Code Viewer Line Cap",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Spacer(Modifier.height(6.dp))
              FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1000, 2500, 5000).forEach { cap ->
                  FilterChip(
                    selected = appSettings.maxCodePreviewLines == cap,
                    onClick = { onUpdateAppSettings { it.copy(maxCodePreviewLines = cap) } },
                    label = { Text("$cap lines") },
                    modifier = Modifier.testTag("settings_code_line_cap_$cap"),
                  )
                }
              }
            }
          }
        }
      }

      "SYNTAX_THEMES" -> {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          item {
            SettingsNestedHeader(
              title = stringResource(R.string.settings_nav_syntax_themes),
              subtitle = stringResource(R.string.settings_nav_syntax_themes_desc),
              onBack = { activeNested = null },
            )
          }
          item {
            SubScreenCard(title = stringResource(R.string.settings_card_smali_theme), icon = Icons.Rounded.Palette) {
              Text(
                text = stringResource(R.string.settings_smali_theme_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Spacer(Modifier.height(10.dp))
              FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                SmaliSyntaxTheme.entries.forEach { theme ->
                  FilterChip(
                    selected = appSettings.smaliSyntaxTheme == theme,
                    onClick = { onUpdateAppSettings { it.copy(smaliSyntaxTheme = theme) } },
                    label = { Text(stringResource(theme.labelRes)) },
                    modifier = Modifier.testTag("settings_smali_theme_${theme.tagSuffix}"),
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}

// ------------------------------------------------------------------------------------------------
// 3. DEX PARSER SUB-SCREEN
// ------------------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsDexParserSubScreen(
  appSettings: AppSettings,
  transpilerConfig: TranspilerConfig,
  onUpdateAppSettings: ((AppSettings) -> AppSettings) -> Unit,
  onGoToPipelineConfig: () -> Unit,
) {
  var activeNested by rememberSaveable { mutableStateOf<String?>(null) }
  BackHandler(enabled = activeNested != null) { activeNested = null }

  AnimatedContent(
    targetState = activeNested,
    transitionSpec = { ExpressiveMotion.sharedAxisHorizontal(targetState != null) },
    label = "dexParserNestedTransition",
  ) { nested ->
    when (nested) {
      null -> {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          item {
            val hideSummary = if (appSettings.defaultHideFramework) "Hide Framework: ON" else "Hide Framework: OFF"
            SettingsNavRow(
              icon = Icons.Rounded.Tune,
              title = stringResource(R.string.settings_nav_class_filters),
              subtitle = "$hideSummary • Limit ${appSettings.autoSelectMethodLimit} methods",
              onClick = { activeNested = "FILTERS" },
              testTag = "settings_nav_class_filters",
            )
          }
          item {
            SettingsNavRow(
              icon = Icons.Rounded.Build,
              title = stringResource(R.string.settings_nav_pipeline_config),
              subtitle = "Module: ${transpilerConfig.moduleName} • Binding: ${transpilerConfig.jniStyle.label}",
              onClick = { activeNested = "PIPELINE" },
              testTag = "settings_nav_pipeline_config",
            )
          }
        }
      }

      "FILTERS" -> {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          item {
            SettingsNestedHeader(
              title = stringResource(R.string.settings_nav_class_filters),
              subtitle = stringResource(R.string.settings_nav_class_filters_desc),
              onBack = { activeNested = null },
            )
          }
          item {
            SubScreenCard(title = stringResource(R.string.settings_nav_class_filters), icon = Icons.Rounded.Tune) {
              SettingsToggleRow(
                title = stringResource(R.string.settings_hide_frameworks),
                subtitle = stringResource(R.string.settings_hide_frameworks_desc),
                checked = appSettings.defaultHideFramework,
                onCheckedChange = { hide -> onUpdateAppSettings { it.copy(defaultHideFramework = hide) } },
                testTag = "settings_default_hide_framework_switch",
              )
              Spacer(Modifier.height(6.dp))
              SettingsToggleRow(
                title = stringResource(R.string.settings_only_bytecode),
                subtitle = stringResource(R.string.settings_only_bytecode_desc),
                checked = appSettings.defaultOnlyBytecode,
                onCheckedChange = { only -> onUpdateAppSettings { it.copy(defaultOnlyBytecode = only) } },
                testTag = "settings_default_only_bytecode_switch",
              )
              Spacer(Modifier.height(10.dp))
              Text(
                text = "Auto-Select Method Limit",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Spacer(Modifier.height(6.dp))
              FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(16, 32, 64, 128).forEach { limit ->
                  FilterChip(
                    selected = appSettings.autoSelectMethodLimit == limit,
                    onClick = { onUpdateAppSettings { it.copy(autoSelectMethodLimit = limit) } },
                    label = { Text("$limit methods") },
                    modifier = Modifier.testTag("settings_autoselect_cap_$limit"),
                  )
                }
              }
            }
          }
        }
      }

      "PIPELINE" -> {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          item {
            SettingsNestedHeader(
              title = stringResource(R.string.settings_nav_pipeline_config),
              subtitle = stringResource(R.string.settings_nav_pipeline_config_desc),
              onBack = { activeNested = null },
            )
          }
          item {
            SubScreenCard(title = "DEX Disassembly & codehasan/dex2c Integration", icon = Icons.Rounded.Build) {
              AboutRow("Default Native Module", transpilerConfig.moduleName)
              AboutRow("JNI Binding Style", transpilerConfig.jniStyle.label)
              AboutRow("16KB Page Alignment", if (transpilerConfig.ollvm.align16KbPages) "Enabled (-Wl,-z,max-page-size=16384)" else "Disabled")
              AboutRow("OLLVM Obfuscation", if (transpilerConfig.ollvm.enabled) "Active (${transpilerConfig.ollvm.activePassCount()} passes)" else "Disabled")
              Spacer(Modifier.height(12.dp))
              OutlinedButton(
                onClick = onGoToPipelineConfig,
                modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("settings_open_pipeline_btn"),
              ) {
                Icon(Icons.Rounded.Build, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.settings_card_pipeline_shortcut))
              }
            }
          }
        }
      }
    }
  }
}

// ------------------------------------------------------------------------------------------------
// 4. GITHUB CLOUD SUB-SCREEN
// ------------------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsGitHubCloudSubScreen(
  cloudSettings: CloudSettings,
  transpilerConfig: TranspilerConfig,
  onUpdateCloudSettings: ((CloudSettings) -> CloudSettings) -> Unit,
  onSyncWorkflowToRepo: () -> Unit,
  onStartDeviceLogin: () -> Unit,
  onSignOutGitHub: () -> Unit,
  onOpenGitHubAppSettings: () -> Unit,
  onCopyText: (String) -> Unit,
) {
  var activeNested by rememberSaveable { mutableStateOf<String?>(null) }
  BackHandler(enabled = activeNested != null) { activeNested = null }

  var showYamlPreview by remember { mutableStateOf(false) }
  val generatedYaml =
    remember(cloudSettings, transpilerConfig) {
      Dex2cWorkflowGenerator.generateDex2cYaml(cloudSettings, transpilerConfig)
    }

  AnimatedContent(
    targetState = activeNested,
    transitionSpec = { ExpressiveMotion.sharedAxisHorizontal(targetState != null) },
    label = "githubCloudNestedTransition",
  ) { nested ->
    when (nested) {
      null -> {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          item {
            val accountSummary = if (cloudSettings.isAuthenticated) "@${cloudSettings.githubUsername}" else "Signed Out"
            SettingsNavRow(
              icon = Icons.Rounded.Key,
              title = stringResource(R.string.settings_nav_github_account),
              subtitle = accountSummary,
              onClick = { activeNested = "ACCOUNT" },
              testTag = "settings_nav_github_account",
            )
          }
          item {
            SettingsNavRow(
              icon = Icons.Rounded.CloudQueue,
              title = stringResource(R.string.settings_nav_workspace_repo),
              subtitle = "${cloudSettings.repoOwner}/${cloudSettings.repoName} • ${cloudSettings.defaultBranch}",
              onClick = { activeNested = "WORKSPACE" },
              testTag = "settings_nav_workspace_repo",
            )
          }
          item {
            val dohSummary = if (cloudSettings.dohFallbackEnabled) "DoH Fallback Enabled" else "Default System DNS"
            SettingsNavRow(
              icon = Icons.Rounded.Dns,
              title = stringResource(R.string.settings_nav_network_resilience),
              subtitle = dohSummary,
              onClick = { activeNested = "NETWORK" },
              testTag = "settings_nav_network_resilience",
            )
          }
        }
      }

      "ACCOUNT" -> {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          item {
            SettingsNestedHeader(
              title = stringResource(R.string.settings_nav_github_account),
              subtitle = stringResource(R.string.settings_nav_github_account_desc),
              onBack = { activeNested = null },
            )
          }
          item {
            SubScreenCard(title = stringResource(R.string.settings_card_github_oauth), icon = Icons.Rounded.Key) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(text = "Account Session", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                  TagPill(
                    text = if (cloudSettings.usingCustomClientId) "Custom OAuth ID" else "gh CLI Default",
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                  )
                  if (cloudSettings.isAuthenticated) {
                    TagPill(
                      text = "@${cloudSettings.githubUsername}",
                      containerColor = MaterialTheme.colorScheme.primaryContainer,
                      contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                  } else {
                    TagPill(text = "Signed Out")
                  }
                }
              }
              Spacer(Modifier.height(10.dp))
              OutlinedTextField(
                value = cloudSettings.githubClientId,
                onValueChange = { id -> onUpdateCloudSettings { it.copy(githubClientId = id.trim()) } },
                label = { Text("Custom GitHub OAuth Client ID (optional)") },
                placeholder = { Text(com.dex2c.studio.cloud.GITHUB_CLI_CLIENT_ID) },
                leadingIcon = { Icon(Icons.Rounded.Key, contentDescription = null) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = JetBrainsMonoFontFamily),
                modifier = Modifier.fillMaxWidth().testTag("settings_client_id_input"),
              )
              Spacer(Modifier.height(6.dp))
              Text(
                text = "Leave blank to use GitHub CLI's built-in Client ID (${com.dex2c.studio.cloud.GITHUB_CLI_CLIENT_ID}), or enter your own GitHub OAuth App Client ID.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Spacer(Modifier.height(12.dp))
              FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                if (!cloudSettings.isAuthenticated) {
                  Button(
                    onClick = onStartDeviceLogin,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("settings_start_device_login_btn"),
                  ) {
                    Icon(Icons.Rounded.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_continue_with_github))
                  }
                } else {
                  OutlinedButton(
                    onClick = onSignOutGitHub,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("settings_sign_out_btn"),
                  ) {
                    Text("Sign Out (@${cloudSettings.githubUsername})")
                  }
                }
                OutlinedButton(
                  onClick = onOpenGitHubAppSettings,
                  shape = RoundedCornerShape(20.dp),
                  modifier = Modifier.minimumInteractiveComponentSize().testTag("settings_open_github_dev_btn"),
                ) {
                  Icon(Icons.Rounded.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(Modifier.width(6.dp))
                  Text("GitHub OAuth Settings")
                }
              }
            }
          }
        }
      }

      "WORKSPACE" -> {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          item {
            SettingsNestedHeader(
              title = stringResource(R.string.settings_nav_workspace_repo),
              subtitle = stringResource(R.string.settings_nav_workspace_repo_desc),
              onBack = { activeNested = null },
            )
          }
          item {
            SubScreenCard(title = stringResource(R.string.settings_card_workspace_repo), icon = Icons.Rounded.CloudQueue) {
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                  value = cloudSettings.repoOwner,
                  onValueChange = { v -> onUpdateCloudSettings { it.copy(repoOwner = v.trim()) } },
                  label = { Text(stringResource(R.string.settings_repo_owner)) },
                  placeholder = { Text(cloudSettings.githubUsername.ifBlank { "username" }) },
                  singleLine = true,
                  textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                  modifier = Modifier.weight(1f).testTag("settings_repo_owner_input"),
                )
                OutlinedTextField(
                  value = cloudSettings.repoName,
                  onValueChange = { v -> onUpdateCloudSettings { it.copy(repoName = v.trim()) } },
                  label = { Text(stringResource(R.string.settings_repo_name)) },
                  singleLine = true,
                  textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                  modifier = Modifier.weight(1.4f).testTag("settings_repo_name_input"),
                )
              }
              Spacer(Modifier.height(10.dp))
              OutlinedTextField(
                value = cloudSettings.defaultBranch,
                onValueChange = { v -> onUpdateCloudSettings { it.copy(defaultBranch = v.trim()) } },
                label = { Text(stringResource(R.string.settings_target_branch)) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                modifier = Modifier.fillMaxWidth().testTag("settings_repo_branch_input"),
              )
              Spacer(Modifier.height(10.dp))
              SettingsToggleRow(
                title = stringResource(R.string.settings_private_repo_toggle),
                subtitle = stringResource(R.string.settings_private_repo_desc),
                checked = cloudSettings.privateRepo,
                onCheckedChange = { priv -> onUpdateCloudSettings { it.copy(privateRepo = priv) } },
                testTag = "settings_private_repo_switch",
              )
              Spacer(Modifier.height(12.dp))
              FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Button(
                  onClick = onSyncWorkflowToRepo,
                  enabled = cloudSettings.isAuthenticated,
                  modifier = Modifier.minimumInteractiveComponentSize().testTag("settings_sync_workflow_btn"),
                ) {
                  Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(Modifier.width(6.dp))
                  Text(stringResource(R.string.settings_init_sync_yaml))
                }
                OutlinedButton(
                  onClick = { showYamlPreview = !showYamlPreview },
                  modifier = Modifier.minimumInteractiveComponentSize().testTag("settings_toggle_yaml_preview_btn"),
                ) {
                  Icon(Icons.Rounded.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(Modifier.width(6.dp))
                  Text(if (showYamlPreview) "Hide dex2c.yml" else "Preview dex2c.yml")
                }
                if (showYamlPreview) {
                  TextButton(
                    onClick = { onCopyText(generatedYaml) },
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("settings_copy_yaml_btn"),
                  ) {
                    Text(stringResource(R.string.settings_copy_yaml))
                  }
                }
              }
              if (showYamlPreview) {
                Spacer(Modifier.height(10.dp))
                SyntaxCodeViewer(
                  code = generatedYaml,
                  modifier = Modifier.fillMaxWidth().height(260.dp),
                )
              }
            }
          }
        }
      }

      "NETWORK" -> {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          item {
            SettingsNestedHeader(
              title = stringResource(R.string.settings_nav_network_resilience),
              subtitle = stringResource(R.string.settings_nav_network_resilience_desc),
              onBack = { activeNested = null },
            )
          }
          item {
            SubScreenCard(title = stringResource(R.string.settings_card_network_resilience), icon = Icons.Rounded.Dns) {
              SettingsToggleRow(
                title = stringResource(R.string.settings_doh_fallback),
                subtitle = stringResource(R.string.settings_doh_desc),
                checked = cloudSettings.dohFallbackEnabled,
                onCheckedChange = { enabled -> onUpdateCloudSettings { it.copy(dohFallbackEnabled = enabled) } },
                testTag = "settings_doh_fallback_switch",
              )
            }
          }
        }
      }
    }
  }
}

// ------------------------------------------------------------------------------------------------
// 5. NDK TOOLCHAIN & SIGNING SUB-SCREEN
// ------------------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsNdkSigningSubScreen(
  cloudSettings: CloudSettings,
  onUpdateCloudSettings: ((CloudSettings) -> CloudSettings) -> Unit,
) {
  var activeNested by rememberSaveable { mutableStateOf<String?>(null) }
  BackHandler(enabled = activeNested != null) { activeNested = null }

  AnimatedContent(
    targetState = activeNested,
    transitionSpec = { ExpressiveMotion.sharedAxisHorizontal(targetState != null) },
    label = "ndkSigningNestedTransition",
  ) { nested ->
    when (nested) {
      null -> {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          item {
            SettingsNavRow(
              icon = Icons.Rounded.Build,
              title = stringResource(R.string.settings_nav_toolchains),
              subtitle = "NDK ${cloudSettings.ndkVersion} • JDK ${cloudSettings.jdkVersion}",
              onClick = { activeNested = "TOOLCHAINS" },
              testTag = "settings_nav_toolchains",
            )
          }
          item {
            SettingsNavRow(
              icon = Icons.Rounded.Key,
              title = stringResource(R.string.settings_nav_signing),
              subtitle = stringResource(cloudSettings.signingMode.labelRes),
              onClick = { activeNested = "SIGNING" },
              testTag = "settings_nav_signing",
            )
          }
          item {
            SettingsNavRow(
              icon = Icons.Rounded.Speed,
              title = stringResource(R.string.settings_nav_limits),
              subtitle = "${cloudSettings.maxDirectUploadMb} MB upload limit • ${cloudSettings.pollIntervalSeconds}s polling",
              onClick = { activeNested = "LIMITS" },
              testTag = "settings_nav_limits",
            )
          }
        }
      }

      "TOOLCHAINS" -> {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          item {
            SettingsNestedHeader(
              title = stringResource(R.string.settings_nav_toolchains),
              subtitle = stringResource(R.string.settings_nav_toolchains_desc),
              onBack = { activeNested = null },
            )
          }
          item {
            SubScreenCard(title = stringResource(R.string.settings_card_ndk_jdk), icon = Icons.Rounded.Build) {
              Text(
                text = "Android NDK Revision (ubuntu-latest)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Spacer(Modifier.height(6.dp))
              FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SUPPORTED_NDK_VERSIONS.forEach { ndk ->
                  FilterChip(
                    selected = cloudSettings.ndkVersion == ndk,
                    onClick = { onUpdateCloudSettings { it.copy(ndkVersion = ndk) } },
                    label = { Text(ndk) },
                    modifier = Modifier.testTag("settings_ndk_$ndk"),
                  )
                }
              }
              Spacer(Modifier.height(12.dp))
              Text(
                text = "Java JDK Version",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Spacer(Modifier.height(6.dp))
              FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SUPPORTED_JDK_VERSIONS.forEach { jdk ->
                  FilterChip(
                    selected = cloudSettings.jdkVersion == jdk,
                    onClick = { onUpdateCloudSettings { it.copy(jdkVersion = jdk) } },
                    label = { Text("JDK $jdk") },
                    modifier = Modifier.testTag("settings_jdk_$jdk"),
                  )
                }
              }
            }
          }
        }
      }

      "SIGNING" -> {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          item {
            SettingsNestedHeader(
              title = stringResource(R.string.settings_nav_signing),
              subtitle = stringResource(R.string.settings_nav_signing_desc),
              onBack = { activeNested = null },
            )
          }
          item {
            SubScreenCard(title = stringResource(R.string.settings_card_apk_signing), icon = Icons.Rounded.Key) {
              CloudSigningMode.entries.forEachIndexed { index, mode ->
                val isSelected = cloudSettings.signingMode == mode
                Row(
                  modifier =
                    Modifier.fillMaxWidth()
                      .clickable { onUpdateCloudSettings { it.copy(signingMode = mode) } }
                      .padding(vertical = 8.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(mode.labelRes), style = MaterialTheme.typography.titleSmall)
                    Text(
                      text = stringResource(mode.summaryRes),
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                  RadioButton(
                    selected = isSelected,
                    onClick = { onUpdateCloudSettings { it.copy(signingMode = mode) } },
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("settings_signing_${mode.name}"),
                  )
                }
                if (index < CloudSigningMode.entries.lastIndex) {
                  HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                }
              }
            }
          }
        }
      }

      "LIMITS" -> {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          item {
            SettingsNestedHeader(
              title = stringResource(R.string.settings_nav_limits),
              subtitle = stringResource(R.string.settings_nav_limits_desc),
              onBack = { activeNested = null },
            )
          }
          item {
            SubScreenCard(title = stringResource(R.string.settings_max_upload_size), icon = Icons.Rounded.Speed) {
              Text(
                text = "Workflow Output APK Limit",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Spacer(Modifier.height(6.dp))
              FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(50, 100, 200, 500).forEach { mb ->
                  FilterChip(
                    selected = cloudSettings.maxDirectUploadMb == mb,
                    onClick = { onUpdateCloudSettings { it.copy(maxDirectUploadMb = mb) } },
                    label = { Text("$mb MB") },
                    modifier = Modifier.testTag("settings_upload_limit_$mb"),
                  )
                }
              }
              Spacer(Modifier.height(12.dp))
              Text(
                text = "Cloud Status Poll Interval",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Spacer(Modifier.height(6.dp))
              FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(10, 15, 30, 60).forEach { sec ->
                  FilterChip(
                    selected = cloudSettings.pollIntervalSeconds == sec,
                    onClick = { onUpdateCloudSettings { it.copy(pollIntervalSeconds = sec) } },
                    label = { Text("${sec}s") },
                    modifier = Modifier.testTag("settings_poll_interval_$sec"),
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}

// ------------------------------------------------------------------------------------------------
// 6. NOTIFICATIONS SUB-SCREEN
// ------------------------------------------------------------------------------------------------

@Composable
fun SettingsNotificationsSubScreen(
  cloudSettings: CloudSettings,
  hasNotificationPermission: Boolean,
  onToggleNotifications: (Boolean) -> Unit,
  onRequestPermission: () -> Unit,
  appSettings: AppSettings = AppSettings(),
) {
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    item {
      SubScreenCard(title = stringResource(R.string.settings_card_push_alerts), icon = Icons.Rounded.Notifications) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Text(stringResource(R.string.settings_completion_alerts), style = MaterialTheme.typography.titleSmall)
              TagPill(
                text = if (hasNotificationPermission) "Allowed" else "Permission Needed",
                containerColor =
                  if (hasNotificationPermission) MaterialTheme.colorScheme.primaryContainer
                  else MaterialTheme.colorScheme.secondaryContainer,
                contentColor =
                  if (hasNotificationPermission) MaterialTheme.colorScheme.onPrimaryContainer
                  else MaterialTheme.colorScheme.onSecondaryContainer,
              )
            }
            Spacer(Modifier.height(4.dp))
            Text(
              text = "Uses WorkManager with network constraints to alert you when cloud APK builds finish or fail, even while backgrounded.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Switch(
            checked = cloudSettings.notificationsEnabled,
            onCheckedChange = onToggleNotifications,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("settings_notifications_switch"),
          )
        }

        if (!hasNotificationPermission) {
          Spacer(Modifier.height(12.dp))
          Button(
            onClick = onRequestPermission,
            modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("settings_request_notif_btn"),
          ) {
            Icon(Icons.Rounded.Notifications, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.settings_allow_permission))
          }
        }
      }
    }
  }
}

// ------------------------------------------------------------------------------------------------
// 7. STORAGE, CACHE & RESET SUB-SCREEN
// ------------------------------------------------------------------------------------------------

private fun computeCacheSizeKb(context: Context): Long {
  return runCatching {
    var totalBytes = 0L
    context.cacheDir?.walkTopDown()?.forEach { if (it.isFile) totalBytes += it.length() }
    context.codeCacheDir?.walkTopDown()?.forEach { if (it.isFile) totalBytes += it.length() }
    totalBytes / 1024
  }.getOrDefault(0L)
}

private fun formatCacheSize(kb: Long): String {
  return when {
    kb >= 1024 -> String.format(java.util.Locale.US, "%.1f MB", kb / 1024.0)
    else -> "$kb KB"
  }
}

@Composable
fun SettingsStorageResetSubScreen(
  recentHistoryCount: Int,
  isGitHubSignedIn: Boolean,
  onClearRecentHistory: () -> Unit,
  onResetAllSettings: (clearGitHubSession: Boolean) -> Unit,
  onTrimMemory: () -> Unit = {},
  onClearDiskCache: () -> Unit = {},
) {
  var activeNested by rememberSaveable { mutableStateOf<String?>(null) }
  BackHandler(enabled = activeNested != null) { activeNested = null }

  var showClearHistoryDialog by remember { mutableStateOf(false) }
  var showResetDialog by remember { mutableStateOf(false) }
  var alsoSignOutOnReset by remember { mutableStateOf(false) }

  val context = LocalContext.current
  var diskCacheKb by remember { mutableLongStateOf(computeCacheSizeKb(context)) }
  var statusToast by remember { mutableStateOf<String?>(null) }

  AnimatedContent(
    targetState = activeNested,
    transitionSpec = { ExpressiveMotion.sharedAxisHorizontal(targetState != null) },
    label = "storageResetNestedTransition",
  ) { nested ->
    when (nested) {
      null -> {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          item {
            val runtime = Runtime.getRuntime()
            val usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
            SettingsNavRow(
              icon = Icons.Rounded.Memory,
              title = stringResource(R.string.settings_nav_memory_management),
              subtitle = "${usedMb} MB JVM heap • ${formatCacheSize(diskCacheKb)} disk cache",
              onClick = { activeNested = "MEMORY" },
              testTag = "settings_nav_memory_management",
            )
          }
          item {
            SettingsNavRow(
              icon = Icons.Rounded.History,
              title = stringResource(R.string.settings_nav_history),
              subtitle = "$recentHistoryCount saved quick-open entries",
              onClick = { activeNested = "HISTORY" },
              testTag = "settings_nav_history",
            )
          }
          item {
            SettingsNavRow(
              icon = Icons.Rounded.RestartAlt,
              title = stringResource(R.string.settings_nav_reset),
              subtitle = stringResource(R.string.settings_nav_reset_desc),
              onClick = { activeNested = "RESET" },
              testTag = "settings_nav_reset",
            )
          }
        }
      }

      "MEMORY" -> {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          item {
            SettingsNestedHeader(
              title = stringResource(R.string.settings_nav_memory_management),
              subtitle = stringResource(R.string.settings_nav_memory_management_desc),
              onBack = { activeNested = null },
            )
          }
          item {
            val runtime = Runtime.getRuntime()
            val usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
            val totalMb = runtime.totalMemory() / (1024 * 1024)
            val maxMb = runtime.maxMemory() / (1024 * 1024)

            SubScreenCard(title = stringResource(R.string.settings_nav_memory_management), icon = Icons.Rounded.Memory) {
              AboutRow(stringResource(R.string.settings_memory_heap_label), "$usedMb MB / $totalMb MB (Max: $maxMb MB)")
              AboutRow(stringResource(R.string.settings_disk_cache_label), formatCacheSize(diskCacheKb))
              if (statusToast != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                  text = statusToast!!,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.primary,
                )
              }
              Spacer(Modifier.height(14.dp))
              OutlinedButton(
                onClick = {
                  onTrimMemory()
                  statusToast = context.getString(R.string.settings_memory_trimmed_toast)
                },
                modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("settings_trim_memory_btn"),
              ) {
                Icon(Icons.Rounded.Memory, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.settings_trim_memory))
              }
              Spacer(Modifier.height(8.dp))
              Button(
                onClick = {
                  onClearDiskCache()
                  diskCacheKb = computeCacheSizeKb(context)
                  statusToast = context.getString(R.string.settings_disk_cache_cleared_toast)
                },
                modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("settings_clear_disk_cache_btn"),
              ) {
                Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.settings_clear_disk_cache))
              }
            }
          }
        }
      }

      "HISTORY" -> {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          item {
            SettingsNestedHeader(
              title = stringResource(R.string.settings_nav_history),
              subtitle = stringResource(R.string.settings_nav_history_desc),
              onBack = { activeNested = null },
            )
          }
          item {
            SubScreenCard(title = stringResource(R.string.settings_card_workspace_history), icon = Icons.Rounded.Storage) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(stringResource(R.string.settings_recent_entries_count), style = MaterialTheme.typography.titleSmall)
                  Text(
                    text = "$recentHistoryCount saved quick-open entries in local DataStore",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                TagPill(text = "$recentHistoryCount items")
              }
              Spacer(Modifier.height(14.dp))
              OutlinedButton(
                onClick = { showClearHistoryDialog = true },
                enabled = recentHistoryCount > 0,
                modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("settings_clear_history_btn"),
              ) {
                Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.settings_clear_recent_history))
              }
            }
          }
        }
      }

      "RESET" -> {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          item {
            SettingsNestedHeader(
              title = stringResource(R.string.settings_nav_reset),
              subtitle = stringResource(R.string.settings_nav_reset_desc),
              onBack = { activeNested = null },
            )
          }
          item {
            SubScreenCard(title = stringResource(R.string.settings_restore_defaults), icon = Icons.Rounded.RestartAlt) {
              Text(
                text = "Reset Appearance, Code Editor formatting, DEX Parser defaults, Transpiler Pipeline options, and NDK/Signing settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Spacer(Modifier.height(14.dp))
              Button(
                onClick = {
                  alsoSignOutOnReset = false
                  showResetDialog = true
                },
                colors =
                  ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                  ),
                modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("settings_reset_all_btn"),
              ) {
                Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.settings_reset_all))
              }
            }
          }
        }
      }
    }
  }

  if (showClearHistoryDialog) {
    AlertDialog(
      onDismissRequest = { showClearHistoryDialog = false },
      shape = RoundedCornerShape(28.dp),
      title = { Text(stringResource(R.string.settings_clear_history_dialog_title)) },
      text = {
        Text(stringResource(R.string.settings_clear_history_dialog_desc, recentHistoryCount))
      },
      confirmButton = {
        Button(
          onClick = {
            showClearHistoryDialog = false
            onClearRecentHistory()
          },
          modifier = Modifier.testTag("confirm_clear_history_btn"),
        ) {
          Text("Clear History")
        }
      },
      dismissButton = {
        TextButton(onClick = { showClearHistoryDialog = false }) { Text("Cancel") }
      },
    )
  }

  if (showResetDialog) {
    AlertDialog(
      onDismissRequest = { showResetDialog = false },
      shape = RoundedCornerShape(28.dp),
      title = { Text(stringResource(R.string.settings_reset_all_dialog_title)) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(stringResource(R.string.settings_reset_all_dialog_desc))
          if (isGitHubSignedIn) {
            SettingsToggleRow(
              title = stringResource(R.string.settings_also_sign_out_github),
              subtitle = stringResource(R.string.settings_sign_out_desc),
              checked = alsoSignOutOnReset,
              onCheckedChange = { alsoSignOutOnReset = it },
              testTag = "reset_also_sign_out_switch",
            )
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            showResetDialog = false
            onResetAllSettings(alsoSignOutOnReset)
          },
          colors =
            ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.error,
              contentColor = MaterialTheme.colorScheme.onError,
            ),
          modifier = Modifier.testTag("confirm_reset_all_btn"),
        ) {
          Text("Reset Defaults")
        }
      },
      dismissButton = {
        TextButton(onClick = { showResetDialog = false }) { Text("Cancel") }
      },
    )
  }
}

// ------------------------------------------------------------------------------------------------
// 8. ABOUT SUB-SCREEN
// ------------------------------------------------------------------------------------------------

@Composable
fun SettingsAboutSubScreen(
  transpilerConfig: TranspilerConfig,
  onCopyText: (String) -> Unit = {},
) {
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    item {
      Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier.padding(16.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.size(56.dp),
          ) {
            val isDark = androidx.compose.foundation.isSystemInDarkTheme()
            Image(
              painter = painterResource(id = if (isDark) R.drawable.ic_dex2c_dark_src else R.drawable.ic_dex2c_light_src),
              contentDescription = stringResource(R.string.app_name),
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)),
            )
          }
          Column {
            Text(
              text = stringResource(R.string.app_name),
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = "Native Dalvik-to-C Transpiler & Cloud Build Studio",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }

    item {
      SubScreenCard(title = "Dex2C Studio", icon = Icons.Rounded.Info) {
        AboutRow("App Name", stringResource(R.string.app_name))
        AboutRow("Version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        AboutRow("Application ID", BuildConfig.APPLICATION_ID)
        AboutRow("Active DEX Engine", "Kotlin DexFile + smali-dexlib2")
        AboutRow("Supported DEX Formats", "035, 037, 038, 039, 040, 041")
        AboutRow("Default Module Name", transpilerConfig.moduleName)
      }
    }

    item {
      SubScreenCard(title = stringResource(R.string.settings_bundled_toolchains), icon = Icons.Rounded.Build) {
        Text(
          text = "• codehasan/dex2c native transpiler with OLLVM & 16KB alignment (dcc.cfg, filter.txt)\n" +
            "• Google smali-dexlib2 & smali-baksmali class disassembly + Kotlin CFG/payload analysis\n" +
            "• GitHub OAuth Device Flow + Private Workspace dex2c.yml Actions + WorkManager push alerts",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

// ------------------------------------------------------------------------------------------------
// COMMON HELPERS
// ------------------------------------------------------------------------------------------------

@Composable
private fun AboutRow(label: String, value: String) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(
      text = value,
      style = MaterialTheme.typography.bodySmall,
      fontFamily = JetBrainsMonoFontFamily,
      color = MaterialTheme.colorScheme.onSurface,
    )
  }
}

@Composable
fun SubScreenCard(
  title: String,
  icon: ImageVector = Icons.Rounded.Tune,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  Surface(
    modifier = modifier.fillMaxWidth().animateContentSize(ExpressiveMotion.spatialSpring()),
    shape = RoundedCornerShape(24.dp),
    color = MaterialTheme.colorScheme.surfaceContainer,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
  ) {
    Column(modifier = Modifier.padding(18.dp)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
          contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
          modifier = Modifier.size(28.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = icon,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
            )
          }
        }
        Text(
          text = title,
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface,
        )
      }
      Spacer(Modifier.height(12.dp))
      content()
    }
  }
}

@Composable
private fun SettingsToggleRow(
  title: String,
  subtitle: String,
  checked: Boolean,
  enabled: Boolean = true,
  onCheckedChange: (Boolean) -> Unit,
  testTag: String,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(enabled = enabled) { onCheckedChange(!checked) }
      .padding(vertical = 4.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
      Text(title, style = MaterialTheme.typography.titleSmall)
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      enabled = enabled,
      modifier = Modifier.minimumInteractiveComponentSize().testTag(testTag),
    )
  }
}
