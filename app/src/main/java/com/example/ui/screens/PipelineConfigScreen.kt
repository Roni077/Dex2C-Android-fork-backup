package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.transpiler.ALL_NDK_ABIS
import com.example.transpiler.FilterMatchSummary
import com.example.transpiler.JniBindingStyle
import com.example.transpiler.LoaderMode
import com.example.transpiler.TargetCLanguage
import com.example.transpiler.TranspilerConfig
import com.example.ui.components.TagPill
import com.example.ui.theme.JetBrainsMonoFontFamily

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
) {
  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier.fillMaxSize().widthIn(max = 840.dp),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = stringResource(R.string.config_title),
              style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
              text = stringResource(R.string.config_subtitle),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          OutlinedButton(
            onClick = onImportDccCfg,
            modifier = Modifier.testTag("import_dcc_cfg_button"),
          ) {
            Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.config_import_dcc_cfg))
          }
        }
      }

      item {
        ConfigSectionCard(title = stringResource(R.string.config_d2c_flags)) {
          ToggleSettingRow(
            title = stringResource(R.string.config_allow_init),
            subtitle = stringResource(R.string.config_allow_init_desc),
            checked = config.allowInit,
            onCheckedChange = { v -> onUpdateConfig { it.copy(allowInit = v) } },
            testTag = "switch_allow_init",
          )
          Spacer(Modifier.height(8.dp))
          Text(
            text = stringResource(R.string.config_loader_mode),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(Modifier.height(4.dp))
          LoaderMode.entries.forEach { mode ->
            Row(
              modifier =
                Modifier.fillMaxWidth()
                  .clickable { onUpdateConfig { it.copy(loaderMode = mode) } }
                  .padding(vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              RadioButton(
                selected = config.loaderMode == mode,
                onClick = { onUpdateConfig { it.copy(loaderMode = mode) } },
                modifier = Modifier.minimumInteractiveComponentSize().testTag("loader_mode_${mode.name}"),
              )
              Column(modifier = Modifier.weight(1f)) {
                Text(mode.label, style = MaterialTheme.typography.titleMedium)
                Text(
                  text = mode.description,
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
          Spacer(Modifier.height(8.dp))
          OutlinedTextField(
            value = config.customLoaderClass,
            onValueChange = { v -> onUpdateConfig { it.copy(customLoaderClass = v) } },
            label = { Text(stringResource(R.string.config_custom_loader)) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = JetBrainsMonoFontFamily),
            modifier = Modifier.fillMaxWidth().testTag("input_custom_loader"),
          )
        }
      }

      item {
        ConfigSectionCard(title = stringResource(R.string.config_jni_binding)) {
          JniBindingStyle.entries.forEach { style ->
            Row(
              modifier =
                Modifier.fillMaxWidth()
                  .clickable { onUpdateConfig { it.copy(jniStyle = style) } }
                  .padding(vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              RadioButton(
                selected = config.jniStyle == style,
                onClick = { onUpdateConfig { it.copy(jniStyle = style) } },
                modifier = Modifier.minimumInteractiveComponentSize().testTag("jni_style_${style.name}"),
              )
              Column(modifier = Modifier.weight(1f)) {
                Text(style.label, style = MaterialTheme.typography.titleMedium)
                Text(
                  text = style.summary,
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }
      }

      item {
        ConfigSectionCard(title = stringResource(R.string.config_target_lang)) {
          SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            TargetCLanguage.entries.forEachIndexed { idx, lang ->
              SegmentedButton(
                selected = config.targetLanguage == lang,
                onClick = { onUpdateConfig { it.copy(targetLanguage = lang) } },
                shape = SegmentedButtonDefaults.itemShape(idx, TargetCLanguage.entries.size),
                modifier = Modifier.testTag("target_lang_${lang.name}"),
              ) {
                Text(lang.label)
              }
            }
          }
          Spacer(Modifier.height(14.dp))
          Text(
            text = stringResource(R.string.config_target_abis),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(Modifier.height(8.dp))
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

      item {
        ConfigSectionCard(title = stringResource(R.string.config_protection_passes)) {
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

      item {
        ConfigSectionCard(title = stringResource(R.string.config_filter_rules)) {
          OutlinedTextField(
            value = config.moduleName,
            onValueChange = { v -> onUpdateConfig { it.copy(moduleName = v) } },
            label = { Text(stringResource(R.string.config_module_name)) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = JetBrainsMonoFontFamily),
            modifier = Modifier.fillMaxWidth().testTag("input_module_name"),
          )
          Spacer(Modifier.height(12.dp))

          if (filterMatchSummary != null) {
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              TagPill(
                text = "Matches ${filterMatchSummary.matchedMethodIds.size} methods (${filterMatchSummary.matchedClassCount} classes)",
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
              )
              if (filterMatchSummary.skippedInitCount > 0) {
                TagPill(
                  text = "${filterMatchSummary.skippedInitCount} <init> skipped",
                  containerColor = MaterialTheme.colorScheme.secondaryContainer,
                  contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
              }
              if (filterMatchSummary.invalidRules.isNotEmpty()) {
                TagPill(
                  text = "${filterMatchSummary.invalidRules.size} invalid regex",
                  containerColor = MaterialTheme.colorScheme.errorContainer,
                  contentColor = MaterialTheme.colorScheme.error,
                )
              }
            }
            Spacer(Modifier.height(10.dp))
          }

          OutlinedTextField(
            value = config.filterRulesText,
            onValueChange = { v -> onUpdateConfig { it.copy(filterRulesText = v) } },
            label = { Text(stringResource(R.string.config_filter_txt_label)) },
            minLines = 5,
            maxLines = 9,
            textStyle = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
            modifier = Modifier.fillMaxWidth().testTag("input_filter_txt"),
          )
          Spacer(Modifier.height(12.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            OutlinedButton(
              onClick = onImportFilterTxt,
              modifier = Modifier.weight(1f).testTag("import_filter_txt_button"),
            ) {
              Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(6.dp))
              Text(stringResource(R.string.config_import_filter), maxLines = 1)
            }
            Button(
              onClick = onApplyFilterRules,
              enabled = hasLoadedDex,
              modifier = Modifier.weight(1.2f).testTag("apply_filter_rules_button"),
            ) {
              Text(stringResource(R.string.config_apply_filters), maxLines = 1)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ConfigSectionCard(
  title: String,
  content: @Composable () -> Unit,
) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(18.dp),
    color = MaterialTheme.colorScheme.surfaceContainer,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
      )
      Spacer(Modifier.height(10.dp))
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
        .padding(vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
      Text(title, style = MaterialTheme.typography.titleMedium)
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      modifier = Modifier.testTag(testTag),
    )
  }
}
