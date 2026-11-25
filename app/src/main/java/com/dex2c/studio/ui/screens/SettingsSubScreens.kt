package com.dex2c.studio.ui.screens

import android.content.Context
import android.os.Build
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.Icon
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
import com.dex2c.studio.nativeengine.DexNativeEngine
import com.dex2c.studio.transpiler.TranspilerConfig
import com.dex2c.studio.ui.components.AccentColorPickerSection
import com.dex2c.studio.ui.components.ExpressiveMotion
import com.dex2c.studio.ui.components.SyntaxCodeViewer
import com.dex2c.studio.ui.components.TagPill
import com.dex2c.studio.ui.components.expressivePressScale
import com.dex2c.studio.ui.theme.JetBrainsMonoFontFamily
import kotlin.math.roundToInt

// ------------------------------------------------------------------------------------------------
// REUSABLE COMPONENTS & DIALOGS
// ------------------------------------------------------------------------------------------------

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
fun <T> SettingsSingleChoiceDialog(
  title: String,
  items: List<T>,
  selectedItem: T,
  itemLabel: @Composable (T) -> String,
  itemSummary: (@Composable (T) -> String)? = null,
  itemIcon: (@Composable (T) -> ImageVector)? = null,
  testTagPrefix: String,
  testTagSuffix: (T) -> String,
  onSelect: (T) -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    shape = RoundedCornerShape(28.dp),
    title = {
      Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
      )
    },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        items.forEach { item ->
          val isSelected = item == selectedItem
          Row(
            modifier =
              Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                  onSelect(item)
                  onDismiss()
                }
                .padding(vertical = 8.dp, horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            if (itemIcon != null) {
              val icon = itemIcon(item)
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
                  Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
                }
              }
            }
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = itemLabel(item),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
              )
              if (itemSummary != null) {
                Text(
                  text = itemSummary(item),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
            RadioButton(
              selected = isSelected,
              onClick = {
                onSelect(item)
                onDismiss()
              },
              modifier =
                Modifier.minimumInteractiveComponentSize().testTag("${testTagPrefix}_${testTagSuffix(item)}"),
            )
          }
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = onDismiss,
        modifier = Modifier.testTag("nested_back_btn"),
      ) {
        Text(stringResource(R.string.action_cancel))
      }
    },
  )
}

@Composable
fun AppLauncherIconDialog(
  currentStyle: AppIconStyle,
  onSelect: (AppIconStyle) -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    shape = RoundedCornerShape(28.dp),
    title = {
      Text(
        text = stringResource(R.string.settings_select_icon),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
      )
    },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        AppIconStyle.entries.forEach { style ->
          val isSelected = style == currentStyle
          Row(
            modifier =
              Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                  onSelect(style)
                  onDismiss()
                }
                .padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = MaterialTheme.colorScheme.surfaceContainerHigh,
              border =
                BorderStroke(
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
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                  stringResource(style.labelRes),
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                )
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
                onSelect(style)
                onDismiss()
              },
              modifier = Modifier.minimumInteractiveComponentSize().testTag("settings_app_icon_${style.name}"),
            )
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss, modifier = Modifier.testTag("nested_back_btn")) {
        Text(stringResource(R.string.action_cancel))
      }
    },
  )
}

// ------------------------------------------------------------------------------------------------
// 1. APPEARANCE SUB-SCREEN
// ------------------------------------------------------------------------------------------------

@Composable
fun SettingsAppearanceSubScreen(
  appSettings: AppSettings,
  onUpdateAppSettings: ((AppSettings) -> AppSettings) -> Unit,
) {
  val context = LocalContext.current
  var showThemeDialog by rememberSaveable { mutableStateOf(false) }
  var showIconDialog by rememberSaveable { mutableStateOf(false) }
  var showLanguageDialog by rememberSaveable { mutableStateOf(false) }

  val colorSourceLabel =
    if (appSettings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      "Dynamic Wallpaper"
    } else {
      CURATED_ACCENT_PRESETS.firstOrNull {
        it.hex.equals(appSettings.accentSeedHex, ignoreCase = true)
      }?.name ?: normalizeHexColor(appSettings.accentSeedHex)
    }

  LazyColumn(
    modifier = Modifier.fillMaxSize().testTag("settings_appearance_list"),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      SettingsNavRow(
        icon = Icons.Rounded.Palette,
        title = stringResource(R.string.settings_nav_theme),
        subtitle = "${stringResource(appSettings.themeMode.labelRes)} • $colorSourceLabel",
        onClick = { showThemeDialog = true },
        testTag = "settings_nav_theme",
      )
    }
    item {
      SettingsNavRow(
        icon = Icons.Rounded.AutoAwesome,
        title = stringResource(R.string.settings_nav_app_icon),
        subtitle = stringResource(appSettings.appIconStyle.labelRes),
        onClick = { showIconDialog = true },
        testTag = "settings_nav_app_icon",
      )
    }
    item {
      SettingsNavRow(
        icon = Icons.Rounded.Translate,
        title = stringResource(R.string.settings_nav_language),
        subtitle = stringResource(appSettings.appLanguage.displayNameRes),
        onClick = { showLanguageDialog = true },
        testTag = "settings_nav_language",
      )
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

  if (showThemeDialog) {
    SettingsSingleChoiceDialog(
      title = stringResource(R.string.settings_select_theme),
      items = AppThemeMode.entries,
      selectedItem = appSettings.themeMode,
      itemLabel = { stringResource(it.labelRes) },
      itemSummary = { stringResource(it.summaryRes) },
      itemIcon = { mode ->
        when (mode) {
          AppThemeMode.SYSTEM -> Icons.Rounded.BrightnessAuto
          AppThemeMode.LIGHT -> Icons.Rounded.LightMode
          AppThemeMode.DARK -> Icons.Rounded.DarkMode
          AppThemeMode.AMOLED -> Icons.Rounded.Contrast
        }
      },
      testTagPrefix = "settings_theme",
      testTagSuffix = { it.name },
      onSelect = { mode -> onUpdateAppSettings { it.copy(themeMode = mode) } },
      onDismiss = { showThemeDialog = false },
    )
  }

  if (showIconDialog) {
    AppLauncherIconDialog(
      currentStyle = appSettings.appIconStyle,
      onSelect = { style ->
        onUpdateAppSettings { it.copy(appIconStyle = style) }
        AppIconManager.setAppIcon(context, style)
      },
      onDismiss = { showIconDialog = false },
    )
  }

  if (showLanguageDialog) {
    SettingsSingleChoiceDialog(
      title = stringResource(R.string.settings_select_language),
      items = AppLanguage.entries,
      selectedItem = appSettings.appLanguage,
      itemLabel = { stringResource(it.displayNameRes) },
      itemSummary = {
        when (it) {
          AppLanguage.SYSTEM -> stringResource(R.string.theme_mode_system_desc)
          AppLanguage.ENGLISH -> "English (United States)"
        }
      },
      itemIcon = { Icons.Rounded.Translate },
      testTagPrefix = "settings_language",
      testTagSuffix = { it.name.lowercase() },
      onSelect = { lang -> onUpdateAppSettings { it.copy(appLanguage = lang) } },
      onDismiss = { showLanguageDialog = false },
    )
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
  var showEngineDialog by rememberSaveable { mutableStateOf(false) }
  var showSyntaxThemeDialog by rememberSaveable { mutableStateOf(false) }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      SettingsNavRow(
        icon = Icons.Rounded.Memory,
        title = stringResource(R.string.settings_nav_editor_engine),
        subtitle = stringResource(appSettings.editorEngine.titleRes),
        onClick = { showEngineDialog = true },
        testTag = "settings_nav_editor_engine",
      )
    }
    item {
      SettingsNavRow(
        icon = Icons.Rounded.Palette,
        title = stringResource(R.string.settings_nav_syntax_themes),
        subtitle = stringResource(appSettings.smaliSyntaxTheme.labelRes),
        onClick = { showSyntaxThemeDialog = true },
        testTag = "settings_nav_syntax_themes",
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

  if (showEngineDialog) {
    SettingsSingleChoiceDialog(
      title = stringResource(R.string.settings_select_engine),
      items = EditorEngineType.entries,
      selectedItem = appSettings.editorEngine,
      itemLabel = { stringResource(it.titleRes) },
      itemSummary = {
        when (it) {
          EditorEngineType.RUST_HYBRID -> "High-performance Rust Native Hybrid engine backed by B-tree ropes"
          EditorEngineType.SORA_LEGACY -> "Pure Android legacy text engine with standard code scrolling"
        }
      },
      itemIcon = { Icons.Rounded.Code },
      testTagPrefix = "settings_editor_engine",
      testTagSuffix = { it.name.lowercase() },
      onSelect = { engine -> onUpdateAppSettings { it.copy(editorEngine = engine) } },
      onDismiss = { showEngineDialog = false },
    )
  }

  if (showSyntaxThemeDialog) {
    SettingsSingleChoiceDialog(
      title = stringResource(R.string.settings_select_syntax_theme),
      items = SmaliSyntaxTheme.entries,
      selectedItem = appSettings.smaliSyntaxTheme,
      itemLabel = { stringResource(it.labelRes) },
      itemSummary = { "Smali & C++ bytecode syntax coloring" },
      itemIcon = { Icons.Rounded.Palette },
      testTagPrefix = "settings_smali_theme",
      testTagSuffix = { it.tagSuffix },
      onSelect = { theme -> onUpdateAppSettings { it.copy(smaliSyntaxTheme = theme) } },
      onDismiss = { showSyntaxThemeDialog = false },
    )
  }
}

// ------------------------------------------------------------------------------------------------
// 3. DEX PARSER SUB-SCREEN
// ------------------------------------------------------------------------------------------------

@Composable
fun SettingsDexParserSubScreen(
  appSettings: AppSettings,
  transpilerConfig: TranspilerConfig,
  onUpdateAppSettings: ((AppSettings) -> AppSettings) -> Unit,
  onGoToPipelineConfig: () -> Unit,
) {
  var showLimitDialog by rememberSaveable { mutableStateOf(false) }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
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
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .clickable { showLimitDialog = true }
              .padding(vertical = 8.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_auto_select_limit), style = MaterialTheme.typography.titleSmall)
            Text(
              text = "Auto-select threshold: ${appSettings.autoSelectMethodLimit} methods",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          TagPill(text = "${appSettings.autoSelectMethodLimit} methods")
        }
      }
    }
    item {
      SubScreenCard(title = "DEX Disassembly & codehasan/dex2c Integration", icon = Icons.Rounded.Build) {
        AboutRow("Default Native Module", transpilerConfig.moduleName)
        AboutRow("JNI Binding Style", transpilerConfig.jniStyle.label)
        AboutRow(
          "16KB Page Alignment",
          if (transpilerConfig.ollvm.align16KbPages) "Enabled (-Wl,-z,max-page-size=16384)" else "Disabled",
        )
        AboutRow(
          "OLLVM Obfuscation",
          if (transpilerConfig.ollvm.enabled) "Active (${transpilerConfig.ollvm.activePassCount()} passes)" else "Disabled",
        )
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

  if (showLimitDialog) {
    SettingsSingleChoiceDialog(
      title = stringResource(R.string.settings_select_auto_select_limit),
      items = listOf(16, 32, 64, 128, 256),
      selectedItem = appSettings.autoSelectMethodLimit,
      itemLabel = { "$it methods" },
      itemSummary = { "Automatically select up to $it bytecode methods on class open" },
      testTagPrefix = "settings_autoselect_cap",
      testTagSuffix = { it.toString() },
      onSelect = { limit -> onUpdateAppSettings { it.copy(autoSelectMethodLimit = limit) } },
      onDismiss = { showLimitDialog = false },
    )
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
  var showYamlPreview by remember { mutableStateOf(false) }
  val generatedYaml =
    remember(cloudSettings, transpilerConfig) {
      Dex2cWorkflowGenerator.generateDex2cYaml(cloudSettings, transpilerConfig)
    }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      SubScreenCard(title = stringResource(R.string.settings_card_github_oauth), icon = Icons.Rounded.Key) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "Account Session",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
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
          text =
            "Leave blank to use GitHub CLI's built-in Client ID (${com.dex2c.studio.cloud.GITHUB_CLI_CLIENT_ID}), or enter your own GitHub OAuth App Client ID.",
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

// ------------------------------------------------------------------------------------------------
// 5. NDK TOOLCHAIN & SIGNING SUB-SCREEN
// ------------------------------------------------------------------------------------------------

@Composable
fun SettingsNdkSigningSubScreen(
  cloudSettings: CloudSettings,
  onUpdateCloudSettings: ((CloudSettings) -> CloudSettings) -> Unit,
) {
  var showNdkDialog by rememberSaveable { mutableStateOf(false) }
  var showJdkDialog by rememberSaveable { mutableStateOf(false) }
  var showSigningDialog by rememberSaveable { mutableStateOf(false) }
  var showUploadDialog by rememberSaveable { mutableStateOf(false) }
  var showPollDialog by rememberSaveable { mutableStateOf(false) }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      SubScreenCard(title = stringResource(R.string.settings_card_ndk_jdk), icon = Icons.Rounded.Build) {
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .clickable { showNdkDialog = true }
              .padding(vertical = 8.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Android NDK Revision", style = MaterialTheme.typography.titleSmall)
            Text(
              text = "ubuntu-latest runner NDK toolchain: ${cloudSettings.ndkVersion}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          TagPill(text = cloudSettings.ndkVersion)
        }
        Spacer(Modifier.height(6.dp))
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .clickable { showJdkDialog = true }
              .padding(vertical = 8.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Java JDK Runtime", style = MaterialTheme.typography.titleSmall)
            Text(
              text = "Runner Gradle compile JDK: JDK ${cloudSettings.jdkVersion}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          TagPill(text = "JDK ${cloudSettings.jdkVersion}")
        }
      }
    }

    item {
      SubScreenCard(title = stringResource(R.string.settings_card_apk_signing), icon = Icons.Rounded.Key) {
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .clickable { showSigningDialog = true }
              .padding(vertical = 8.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(cloudSettings.signingMode.labelRes), style = MaterialTheme.typography.titleSmall)
            Text(
              text = stringResource(cloudSettings.signingMode.summaryRes),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          TagPill(text = stringResource(cloudSettings.signingMode.labelRes))
        }
      }
    }

    item {
      SubScreenCard(title = stringResource(R.string.settings_nav_limits), icon = Icons.Rounded.Speed) {
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .clickable { showUploadDialog = true }
              .padding(vertical = 8.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Workflow Output APK Cap", style = MaterialTheme.typography.titleSmall)
            Text(
              text = "Max direct release upload: ${cloudSettings.maxDirectUploadMb} MB",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          TagPill(text = "${cloudSettings.maxDirectUploadMb} MB")
        }
        Spacer(Modifier.height(6.dp))
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .clickable { showPollDialog = true }
              .padding(vertical = 8.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Cloud Status Poll Interval", style = MaterialTheme.typography.titleSmall)
            Text(
              text = "Query GitHub Actions run every ${cloudSettings.pollIntervalSeconds}s",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          TagPill(text = "${cloudSettings.pollIntervalSeconds}s")
        }
      }
    }
  }

  if (showNdkDialog) {
    SettingsSingleChoiceDialog(
      title = stringResource(R.string.settings_select_ndk_version),
      items = SUPPORTED_NDK_VERSIONS,
      selectedItem = cloudSettings.ndkVersion,
      itemLabel = { it },
      itemSummary = { "Official Android NDK $it toolchain" },
      itemIcon = { Icons.Rounded.Build },
      testTagPrefix = "settings_ndk",
      testTagSuffix = { it },
      onSelect = { ndk -> onUpdateCloudSettings { it.copy(ndkVersion = ndk) } },
      onDismiss = { showNdkDialog = false },
    )
  }

  if (showJdkDialog) {
    SettingsSingleChoiceDialog(
      title = stringResource(R.string.settings_select_jdk_version),
      items = SUPPORTED_JDK_VERSIONS,
      selectedItem = cloudSettings.jdkVersion,
      itemLabel = { "JDK $it" },
      itemSummary = { "Eclipse Temurin OpenJDK $it runtime" },
      itemIcon = { Icons.Rounded.Memory },
      testTagPrefix = "settings_jdk",
      testTagSuffix = { it.toString() },
      onSelect = { jdk -> onUpdateCloudSettings { it.copy(jdkVersion = jdk) } },
      onDismiss = { showJdkDialog = false },
    )
  }

  if (showSigningDialog) {
    SettingsSingleChoiceDialog(
      title = stringResource(R.string.settings_select_signing_mode),
      items = CloudSigningMode.entries,
      selectedItem = cloudSettings.signingMode,
      itemLabel = { stringResource(it.labelRes) },
      itemSummary = { stringResource(it.summaryRes) },
      itemIcon = { Icons.Rounded.Key },
      testTagPrefix = "settings_signing",
      testTagSuffix = { it.name },
      onSelect = { mode -> onUpdateCloudSettings { it.copy(signingMode = mode) } },
      onDismiss = { showSigningDialog = false },
    )
  }

  if (showUploadDialog) {
    SettingsSingleChoiceDialog(
      title = stringResource(R.string.settings_select_upload_cap),
      items = listOf(50, 100, 200, 500),
      selectedItem = cloudSettings.maxDirectUploadMb,
      itemLabel = { "$it MB" },
      itemSummary = { "Cap workflow direct release artifact uploads at $it MB" },
      testTagPrefix = "settings_upload_limit",
      testTagSuffix = { it.toString() },
      onSelect = { mb -> onUpdateCloudSettings { it.copy(maxDirectUploadMb = mb) } },
      onDismiss = { showUploadDialog = false },
    )
  }

  if (showPollDialog) {
    SettingsSingleChoiceDialog(
      title = stringResource(R.string.settings_select_poll_interval),
      items = listOf(5, 10, 15, 30),
      selectedItem = cloudSettings.pollIntervalSeconds,
      itemLabel = { "${it}s" },
      itemSummary = { "Poll GitHub Actions workflow status every $it seconds" },
      testTagPrefix = "settings_poll_interval",
      testTagSuffix = { it.toString() },
      onSelect = { sec -> onUpdateCloudSettings { it.copy(pollIntervalSeconds = sec) } },
      onDismiss = { showPollDialog = false },
    )
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
  appSettings: AppSettings,
) {
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      SubScreenCard(title = stringResource(R.string.settings_card_push_alerts), icon = Icons.Rounded.Notifications) {
        SettingsToggleRow(
          title = stringResource(R.string.settings_completion_alerts),
          subtitle = stringResource(R.string.settings_completion_alerts_desc),
          checked = cloudSettings.notificationsEnabled,
          onCheckedChange = onToggleNotifications,
          testTag = "settings_notifications_switch",
        )
      }
    }

    if (cloudSettings.notificationsEnabled && !hasNotificationPermission) {
      item {
        SubScreenCard(title = stringResource(R.string.onboarding_allow_permission), icon = Icons.Rounded.Notifications) {
          Text(
            text = stringResource(R.string.settings_completion_alerts_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(Modifier.height(10.dp))
          Button(
            onClick = onRequestPermission,
            modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("settings_request_permission_btn"),
          ) {
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

@Composable
fun SettingsStorageResetSubScreen(
  recentHistoryCount: Int,
  isGitHubSignedIn: Boolean,
  onClearRecentHistory: () -> Unit,
  onResetAllSettings: (clearGitHubSession: Boolean) -> Unit,
  onTrimMemory: () -> Unit = {},
  onClearDiskCache: () -> Unit = {},
) {
  val context = LocalContext.current
  var diskCacheKb by remember { mutableLongStateOf(computeCacheSizeKb(context)) }
  var statusToast by remember { mutableStateOf<String?>(null) }
  var showClearHistoryDialog by rememberSaveable { mutableStateOf(false) }
  var showClearCacheDialog by rememberSaveable { mutableStateOf(false) }
  var showResetDialog by rememberSaveable { mutableStateOf(false) }
  var alsoSignOutOnReset by rememberSaveable { mutableStateOf(false) }

  val runtime = Runtime.getRuntime()
  val usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
  val totalMb = runtime.totalMemory() / (1024 * 1024)
  val maxMb = runtime.maxMemory() / (1024 * 1024)

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
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
          onClick = { showClearCacheDialog = true },
          modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("settings_clear_disk_cache_btn"),
        ) {
          Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text(stringResource(R.string.settings_clear_disk_cache))
        }
      }
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

  if (showClearCacheDialog) {
    AlertDialog(
      onDismissRequest = { showClearCacheDialog = false },
      shape = RoundedCornerShape(28.dp),
      title = { Text(stringResource(R.string.settings_confirm_clear_cache_title)) },
      text = { Text(stringResource(R.string.settings_confirm_clear_cache_desc)) },
      confirmButton = {
        Button(
          onClick = {
            showClearCacheDialog = false
            onClearDiskCache()
            diskCacheKb = computeCacheSizeKb(context)
            statusToast = context.getString(R.string.settings_disk_cache_cleared_toast)
          },
          modifier = Modifier.testTag("confirm_clear_cache_btn"),
        ) {
          Text("Clear Cache")
        }
      },
      dismissButton = {
        TextButton(onClick = { showClearCacheDialog = false }, modifier = Modifier.testTag("nested_back_btn")) {
          Text("Cancel")
        }
      },
    )
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
        TextButton(onClick = { showClearHistoryDialog = false }, modifier = Modifier.testTag("nested_back_btn")) {
          Text("Cancel")
        }
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
          Text("Reset Settings")
        }
      },
      dismissButton = {
        TextButton(onClick = { showResetDialog = false }, modifier = Modifier.testTag("nested_back_btn")) {
          Text("Cancel")
        }
      },
    )
  }
}

// ------------------------------------------------------------------------------------------------
// 8. ABOUT SUB-SCREEN
// ------------------------------------------------------------------------------------------------

@Composable
fun SettingsAboutSubScreen(
  appSettings: AppSettings,
  transpilerConfig: TranspilerConfig,
  onCopyText: (String) -> Unit,
) {
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      SubScreenCard(title = "Dex2C Studio", icon = Icons.Rounded.Info) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(14.dp),
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
        AboutRow(
          "Active DEX Engine",
          if (DexNativeEngine.isAvailable) "⚡ Rust Native (v${DexNativeEngine.nativeEngineVersion ?: "0.2.0"})"
          else "Kotlin DexFile + smali-dexlib2",
        )
        AboutRow("Supported DEX Formats", "035, 037, 038, 039, 040, 041")
        AboutRow("Default Module Name", transpilerConfig.moduleName)
      }
    }

    item {
      SubScreenCard(title = "Rust Native Engine Subsystems", icon = Icons.Rounded.Speed) {
        AboutRow(
          "Engine Shared Library",
          if (DexNativeEngine.isAvailable) "Active ⚡ (librust_dex2c.so)" else "JVM Fallback Mode",
        )
        AboutRow(
          "DEX Class Parser",
          if (DexNativeEngine.isAvailable) "Rust Zero-Copy Buffer (D2CN)" else "smali-dexlib2 / Kotlin",
        )
        AboutRow(
          "Smali Disassembler",
          if (DexNativeEngine.isAvailable) "Rust Native (DalvikDisassembler)" else "Google baksmali",
        )
        AboutRow(
          "Filter Evaluation Engine",
          if (DexNativeEngine.isAvailable) "Rust SIMD RegexSet" else "Kotlin Regex Engine",
        )
        AboutRow(
          "Smali Text Editor",
          if (appSettings.editorEngine == EditorEngineType.RUST_HYBRID) "Rust B-Tree Rope (dex2c-editor)" else "Sora Editor (Legacy)",
        )
        AboutRow(
          "filter.txt Editor",
          if (DexNativeEngine.isAvailable) "Rust FilterTokenizer (Active ⚡)" else "Kotlin Fallback Tokenizer",
        )
      }
    }

    item {
      SubScreenCard(title = stringResource(R.string.settings_bundled_toolchains), icon = Icons.Rounded.Build) {
        Text(
          text =
            "• High-performance Rust Native Engine (librust_dex2c.so) for zero-copy parsing, SIMD filtering, Dalvik disassembly & B-Tree editing\n" +
              "• codehasan/dex2c native transpiler with OLLVM & 16KB alignment (dcc.cfg, filter.txt)\n" +
              "• Google smali-dexlib2 & smali-baksmali class disassembly fallback + Kotlin CFG analysis\n" +
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
    modifier =
      Modifier
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
