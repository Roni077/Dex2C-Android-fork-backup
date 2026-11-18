package com.dex2c.studio.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dex2c.studio.R
import com.dex2c.studio.transpiler.ALL_NDK_ABIS
import com.dex2c.studio.transpiler.COMMON_ANDROID_METHODS
import com.dex2c.studio.transpiler.FilterMatchSummary
import com.dex2c.studio.transpiler.FilterRuleEngine
import com.dex2c.studio.transpiler.FilterRuleType
import com.dex2c.studio.transpiler.JniBindingStyle
import com.dex2c.studio.transpiler.LoaderMode
import com.dex2c.studio.transpiler.StructuredFilterRule
import com.dex2c.studio.transpiler.TargetCLanguage
import com.dex2c.studio.transpiler.TranspilerConfig
import com.dex2c.studio.ui.components.ExpressiveMotion
import com.dex2c.studio.ui.components.InteractiveFilterRuleBuilderPanel
import com.dex2c.studio.ui.components.TagPill
import com.dex2c.studio.ui.theme.JetBrainsMonoFontFamily

enum class PipelineCategory {
  LOADER,
  NDK_PASSES,
  FILTERS,
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PipelineConfigScreen(
  config: TranspilerConfig,
  hasLoadedDex: Boolean,
  filterMatchSummary: FilterMatchSummary?,
  onUpdateConfig: ((TranspilerConfig) -> TranspilerConfig) -> Unit,
  onImportFilterTxt: () -> Unit,
  onImportDccCfg: () -> Unit,
  onApplyFilterRules: () -> Unit,
  modifier: Modifier = Modifier,
  selectedMethodCount: Int = 0,
  onSyncSelectionToWildcardRules: (isBlacklist: Boolean) -> Unit = {},
) {
  var activeCategory by rememberSaveable { mutableStateOf(PipelineCategory.LOADER) }

  BoxWithConstraints(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    val isWide = maxWidth >= 680.dp
    val needsCompactScroll = maxHeight < 360.dp
    val contentMax = if (maxWidth >= 960.dp) 1100.dp else 880.dp

    Column(
      modifier =
        Modifier
          .fillMaxSize()
          .widthIn(max = contentMax)
          .padding(horizontal = 16.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = stringResource(R.string.config_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = stringResource(R.string.config_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(
          onClick = onImportDccCfg,
          shape = RoundedCornerShape(20.dp),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
          modifier = Modifier.testTag("import_dcc_cfg_button"),
        ) {
          Icon(Icons.Rounded.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text(stringResource(R.string.config_import_dcc_cfg), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        PipelineCategoryPill(
          label = stringResource(R.string.pipeline_tab_loader),
          icon = Icons.Rounded.Tune,
          selected = activeCategory == PipelineCategory.LOADER,
          onClick = { activeCategory = PipelineCategory.LOADER },
          modifier = Modifier.weight(1f).testTag("pipeline_tab_LOADER"),
        )
        PipelineCategoryPill(
          label = stringResource(R.string.pipeline_tab_ndk_passes),
          icon = Icons.Rounded.Memory,
          selected = activeCategory == PipelineCategory.NDK_PASSES,
          onClick = { activeCategory = PipelineCategory.NDK_PASSES },
          modifier = Modifier.weight(1f).testTag("pipeline_tab_NDK_PASSES"),
        )
        PipelineCategoryPill(
          label = stringResource(R.string.pipeline_tab_filters),
          icon = Icons.Rounded.FilterAlt,
          selected = activeCategory == PipelineCategory.FILTERS,
          onClick = { activeCategory = PipelineCategory.FILTERS },
          modifier = Modifier.weight(1f).testTag("pipeline_tab_FILTERS"),
        )
      }

      val paneScrollState = rememberScrollState()
      Box(
        modifier =
          Modifier
            .weight(1f)
            .fillMaxWidth()
            .then(if (needsCompactScroll) Modifier.verticalScroll(paneScrollState) else Modifier),
      ) {
        AnimatedContent(
          targetState = activeCategory,
          transitionSpec = {
            ExpressiveMotion.sharedAxisHorizontal(forward = targetState.ordinal >= initialState.ordinal)
          },
          label = "pipeline_category_pane",
        ) { category ->
          when (category) {
            PipelineCategory.LOADER -> {
              if (isWide) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(12.dp),
                  verticalAlignment = Alignment.Top,
                ) {
                  LoaderFlagsCard(
                    config = config,
                    onUpdateConfig = onUpdateConfig,
                    modifier = Modifier.weight(1.25f),
                  )
                  JniBindingCard(
                    config = config,
                    onUpdateConfig = onUpdateConfig,
                    modifier = Modifier.weight(0.75f),
                  )
                }
              } else {
                Column(
                  modifier = Modifier.fillMaxWidth(),
                  verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                  LoaderFlagsCard(
                    config = config,
                    onUpdateConfig = onUpdateConfig,
                  )
                  JniBindingCard(
                    config = config,
                    onUpdateConfig = onUpdateConfig,
                  )
                }
              }
            }

            PipelineCategory.NDK_PASSES -> {
              if (isWide) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(12.dp),
                  verticalAlignment = Alignment.Top,
                ) {
                  TargetStandardAndAbisCard(
                    config = config,
                    onUpdateConfig = onUpdateConfig,
                    modifier = Modifier.weight(1f),
                  )
                  ProtectionPassesCard(
                    config = config,
                    onUpdateConfig = onUpdateConfig,
                    modifier = Modifier.weight(1f),
                  )
                }
              } else {
                Column(
                  modifier = Modifier.fillMaxWidth(),
                  verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                  TargetStandardAndAbisCard(
                    config = config,
                    onUpdateConfig = onUpdateConfig,
                  )
                  ProtectionPassesCard(
                    config = config,
                    onUpdateConfig = onUpdateConfig,
                  )
                }
              }
            }

            PipelineCategory.FILTERS -> {
              FilterRulesCard(
                config = config,
                hasLoadedDex = hasLoadedDex,
                filterMatchSummary = filterMatchSummary,
                onUpdateConfig = onUpdateConfig,
                onImportFilterTxt = onImportFilterTxt,
                onApplyFilterRules = onApplyFilterRules,
                fillAvailableHeight = !needsCompactScroll,
                selectedMethodCount = selectedMethodCount,
                onSyncSelectionToWildcardRules = onSyncSelectionToWildcardRules,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun PipelineCategoryPill(
  label: String,
  icon: ImageVector,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  FilterChip(
    selected = selected,
    onClick = onClick,
    leadingIcon = {
      Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(16.dp),
      )
    },
    label = {
      Text(
        text = label,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
      )
    },
    colors =
      FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
      ),
    modifier = modifier.minimumInteractiveComponentSize(),
  )
}

@Composable
private fun LoaderFlagsCard(
  config: TranspilerConfig,
  onUpdateConfig: ((TranspilerConfig) -> TranspilerConfig) -> Unit,
  modifier: Modifier = Modifier,
) {
  ConfigSectionCard(title = stringResource(R.string.config_d2c_flags), modifier = modifier) {
    ToggleSettingRow(
      title = stringResource(R.string.config_allow_init),
      subtitle = stringResource(R.string.config_allow_init_desc),
      checked = config.allowInit,
      onCheckedChange = { v -> onUpdateConfig { it.copy(allowInit = v) } },
      testTag = "switch_allow_init",
    )
    ToggleSettingRow(
      title = stringResource(R.string.config_skip_synthetic),
      subtitle = stringResource(R.string.config_skip_synthetic_desc),
      checked = config.skipSynthetic,
      onCheckedChange = { v -> onUpdateConfig { it.copy(skipSynthetic = v) } },
      testTag = "switch_skip_synthetic",
    )
    Spacer(Modifier.height(6.dp))
    Text(
      text = stringResource(R.string.config_loader_mode),
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(4.dp))
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
      LoaderMode.entries.forEachIndexed { idx, mode ->
        val shortLabel =
          when (mode) {
            LoaderMode.AUTO_APP_OR_CUSTOM -> "Auto"
            LoaderMode.CUSTOM_LOADER -> "Custom"
            LoaderMode.MANUAL_LOAD_LIBRARY -> "Manual"
          }
        SegmentedButton(
          selected = config.loaderMode == mode,
          onClick = { onUpdateConfig { it.copy(loaderMode = mode) } },
          shape = SegmentedButtonDefaults.itemShape(idx, LoaderMode.entries.size),
          modifier = Modifier.testTag("loader_mode_${mode.name}"),
        ) {
          Text(shortLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
      }
    }
    Spacer(Modifier.height(4.dp))
    Text(
      text = "${config.loaderMode.label}: ${config.loaderMode.description}",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
      value = config.customLoaderClass,
      onValueChange = { v -> onUpdateConfig { it.copy(customLoaderClass = v) } },
      label = { Text(stringResource(R.string.config_custom_loader)) },
      singleLine = true,
      shape = RoundedCornerShape(14.dp),
      textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
      modifier = Modifier.fillMaxWidth().testTag("input_custom_loader"),
    )
    Spacer(Modifier.height(6.dp))
    ToggleSettingRow(
      title = stringResource(R.string.config_force_custom_loader),
      subtitle = stringResource(R.string.config_force_custom_loader_desc),
      checked = config.forceCustomLoader,
      onCheckedChange = { v -> onUpdateConfig { it.copy(forceCustomLoader = v) } },
      testTag = "switch_force_custom_loader",
    )
  }
}

@Composable
private fun JniBindingCard(
  config: TranspilerConfig,
  onUpdateConfig: ((TranspilerConfig) -> TranspilerConfig) -> Unit,
  modifier: Modifier = Modifier,
) {
  ConfigSectionCard(title = stringResource(R.string.config_jni_binding), modifier = modifier) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
      JniBindingStyle.entries.forEachIndexed { idx, style ->
        val shortLabel =
          if (style == JniBindingStyle.REGISTER_NATIVES) "RegisterNatives" else "Static Java_*"
        SegmentedButton(
          selected = config.jniStyle == style,
          onClick = { onUpdateConfig { it.copy(jniStyle = style) } },
          shape = SegmentedButtonDefaults.itemShape(idx, JniBindingStyle.entries.size),
          modifier = Modifier.testTag("jni_style_${style.name}"),
        ) {
          Text(shortLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
      }
    }
    Spacer(Modifier.height(6.dp))
    Text(
      text = config.jniStyle.summary,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TargetStandardAndAbisCard(
  config: TranspilerConfig,
  onUpdateConfig: ((TranspilerConfig) -> TranspilerConfig) -> Unit,
  modifier: Modifier = Modifier,
) {
  ConfigSectionCard(title = stringResource(R.string.config_target_lang), modifier = modifier) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
      TargetCLanguage.entries.forEachIndexed { idx, lang ->
        SegmentedButton(
          selected = config.targetLanguage == lang,
          onClick = { onUpdateConfig { it.copy(targetLanguage = lang) } },
          shape = SegmentedButtonDefaults.itemShape(idx, TargetCLanguage.entries.size),
          modifier = Modifier.testTag("target_lang_${lang.name}"),
        ) {
          Text(lang.label, maxLines = 1)
        }
      }
    }
    Spacer(Modifier.height(12.dp))
    Text(
      text = stringResource(R.string.config_target_abis),
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(6.dp))
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      ALL_NDK_ABIS.forEach { abi ->
        val selected = abi in config.targetAbis
        FilterChip(
          selected = selected,
          onClick = {
            onUpdateConfig { current ->
              val next =
                if (selected && current.targetAbis.size > 1) current.targetAbis - abi
                else current.targetAbis + abi
              current.copy(targetAbis = next)
            }
          },
          label = { Text(abi, fontFamily = JetBrainsMonoFontFamily) },
          modifier = Modifier.testTag("abi_chip_$abi"),
        )
      }
    }
  }
}

@Composable
private fun ProtectionPassesCard(
  config: TranspilerConfig,
  onUpdateConfig: ((TranspilerConfig) -> TranspilerConfig) -> Unit,
  modifier: Modifier = Modifier,
) {
  ConfigSectionCard(title = stringResource(R.string.config_protection_passes), modifier = modifier) {
    ToggleSettingRow(
      title = stringResource(R.string.config_string_xor),
      subtitle = stringResource(R.string.config_string_xor_desc),
      checked = config.stringEncryption,
      onCheckedChange = { v -> onUpdateConfig { it.copy(stringEncryption = v) } },
      testTag = "switch_string_xor",
    )
    ToggleSettingRow(
      title = stringResource(R.string.config_cf_flattening),
      subtitle = stringResource(R.string.config_cf_flattening_desc),
      checked = config.controlFlowFlattening,
      onCheckedChange = { v -> onUpdateConfig { it.copy(controlFlowFlattening = v) } },
      testTag = "switch_cf_flattening",
    )
    ToggleSettingRow(
      title = stringResource(R.string.config_null_guards),
      subtitle = stringResource(R.string.config_null_guards_desc),
      checked = config.nullCheckGuards,
      onCheckedChange = { v -> onUpdateConfig { it.copy(nullCheckGuards = v) } },
      testTag = "switch_null_guards",
    )
  }
}

@Composable
private fun FilterRulesCard(
  config: TranspilerConfig,
  hasLoadedDex: Boolean,
  filterMatchSummary: FilterMatchSummary?,
  onUpdateConfig: ((TranspilerConfig) -> TranspilerConfig) -> Unit,
  onImportFilterTxt: () -> Unit,
  onApplyFilterRules: () -> Unit,
  fillAvailableHeight: Boolean,
  selectedMethodCount: Int,
  onSyncSelectionToWildcardRules: (isBlacklist: Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier =
      modifier
        .fillMaxWidth()
        .then(if (fillAvailableHeight) Modifier.fillMaxSize() else Modifier)
        .animateContentSize(ExpressiveMotion.spatialSpring()),
    shape = RoundedCornerShape(24.dp),
    color = MaterialTheme.colorScheme.surfaceContainer,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
  ) {
    InteractiveFilterRuleBuilderPanel(
      config = config,
      hasLoadedDex = hasLoadedDex,
      filterMatchSummary = filterMatchSummary,
      selectedMethodCount = selectedMethodCount,
      onUpdateConfig = onUpdateConfig,
      onSyncSelectionToWildcardRules = onSyncSelectionToWildcardRules,
      onApplyFilterRules = onApplyFilterRules,
      fillAvailableHeight = fillAvailableHeight,
      showModuleNameField = true,
      onImportFilterTxt = onImportFilterTxt,
      modifier =
        Modifier
          .padding(14.dp)
          .then(if (fillAvailableHeight) Modifier.fillMaxSize() else Modifier),
    )
  }
}

@Composable
private fun ConfigSectionCard(
  title: String,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  Surface(
    modifier = modifier.fillMaxWidth().animateContentSize(ExpressiveMotion.spatialSpring()),
    shape = RoundedCornerShape(24.dp),
    color = MaterialTheme.colorScheme.surfaceContainer,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
      )
      Spacer(Modifier.height(6.dp))
      content()
    }
  }
}

@Composable
private fun ToggleSettingRow(
  title: String,
  subtitle: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  testTag: String,
) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .clickable { onCheckedChange(!checked) }
        .padding(vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
      Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
    }
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      modifier = Modifier.testTag(testTag),
    )
  }
}
