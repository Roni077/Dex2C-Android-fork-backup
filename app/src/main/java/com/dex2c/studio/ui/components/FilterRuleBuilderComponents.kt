package com.dex2c.studio.ui.components

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dex2c.studio.R
import com.dex2c.studio.transpiler.COMMON_ANDROID_METHODS
import com.dex2c.studio.transpiler.FRAMEWORK_BLACKLIST_PRESETS
import com.dex2c.studio.transpiler.FilterMatchSummary
import com.dex2c.studio.transpiler.FilterRuleEngine
import com.dex2c.studio.transpiler.FilterRuleType
import com.dex2c.studio.transpiler.LIFECYCLE_SECURITY_PRESETS
import com.dex2c.studio.transpiler.StructuredFilterRule
import com.dex2c.studio.transpiler.TranspilerConfig
import com.dex2c.studio.ui.theme.JetBrainsMonoFontFamily

enum class FilterEditorSubMode {
  BUILDER,
  RAW,
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InteractiveFilterRuleBuilderPanel(
  config: TranspilerConfig,
  hasLoadedDex: Boolean,
  filterMatchSummary: FilterMatchSummary?,
  selectedMethodCount: Int,
  onUpdateConfig: ((TranspilerConfig) -> TranspilerConfig) -> Unit,
  onSyncSelectionToWildcardRules: (isBlacklist: Boolean) -> Unit = {},
  onApplyFilterRules: () -> Unit,
  modifier: Modifier = Modifier,
  fillAvailableHeight: Boolean = false,
  showModuleNameField: Boolean = false,
  onImportFilterTxt: (() -> Unit)? = null,
  onlyMatchingExplorerFilter: Boolean? = null,
  onToggleMatchingExplorerFilter: (() -> Unit)? = null,
) {
  val lineCount = remember(config.filterRulesText) {
    config.filterRulesText.lineSequence().count { it.trim().isNotEmpty() && !it.trim().startsWith("#") }
  }

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = if (showModuleNameField) stringResource(R.string.config_filter_rules) else "Filter Rules (filter.txt)",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.primary,
        )
        Text(
          text = "$lineCount rule line(s) • # comment • ! exclude",
          style = MaterialTheme.typography.labelSmall,
          fontFamily = JetBrainsMonoFontFamily,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    if (showModuleNameField) {
      OutlinedTextField(
        value = config.moduleName,
        onValueChange = { v -> onUpdateConfig { it.copy(moduleName = v) } },
        label = { Text(stringResource(R.string.config_module_name)) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
        modifier = Modifier.fillMaxWidth().testTag("input_module_name"),
      )
    }

    if (filterMatchSummary != null || onToggleMatchingExplorerFilter != null) {
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        if (filterMatchSummary != null) {
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
        if (onToggleMatchingExplorerFilter != null && onlyMatchingExplorerFilter != null) {
          FilterChip(
            selected = onlyMatchingExplorerFilter,
            onClick = onToggleMatchingExplorerFilter,
            leadingIcon = {
              Icon(
                imageVector = Icons.Rounded.FilterAlt,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
              )
            },
            label = {
              Text(
                text = if (onlyMatchingExplorerFilter) "Explorer: Rule-Filtered" else "Filter Explorer Tree",
                style = MaterialTheme.typography.labelSmall,
              )
            },
            modifier = Modifier.testTag("builder_match_explorer_chip"),
          )
        }
      }
    }



    OutlinedTextField(
      value = config.filterRulesText,
      onValueChange = { v -> onUpdateConfig { it.copy(filterRulesText = v) } },
      label = { Text(stringResource(R.string.config_filter_txt_label)) },
      shape = RoundedCornerShape(14.dp),
      textStyle = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
      modifier =
        Modifier
          .fillMaxWidth()
          .then(if (fillAvailableHeight) Modifier.weight(1f) else Modifier.heightIn(min = 140.dp, max = 260.dp))
          .testTag("input_filter_txt"),
    )



    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      if (onImportFilterTxt != null) {
        OutlinedButton(
          onClick = onImportFilterTxt,
          shape = RoundedCornerShape(20.dp),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
          modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("import_filter_txt_button"),
        ) {
          Icon(Icons.Rounded.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text(
            text = stringResource(R.string.config_import_filter),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
      Button(
        onClick = onApplyFilterRules,
        enabled = hasLoadedDex,
        shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("apply_filter_rules_button"),
      ) {
        Text(
          text = stringResource(R.string.config_apply_filters),
          maxLines = 1,
          softWrap = false,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

@Composable
fun StructuredRuleItemCard(
  rule: StructuredFilterRule,
  onToggleBlacklist: () -> Unit,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(12.dp),
    color =
      if (rule.isBlacklist) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
      else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
    border =
      BorderStroke(
        1.dp,
        if (rule.isBlacklist) MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
        else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
      ),
    modifier = Modifier.fillMaxWidth().testTag("structured_rule_card"),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Surface(
        onClick = onToggleBlacklist,
        shape = RoundedCornerShape(8.dp),
        color =
          if (rule.isBlacklist) MaterialTheme.colorScheme.errorContainer
          else MaterialTheme.colorScheme.primaryContainer,
        contentColor =
          if (rule.isBlacklist) MaterialTheme.colorScheme.onErrorContainer
          else MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.testTag("rule_flip_mode_btn"),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.SwapHoriz,
            contentDescription = "Flip Whitelist/Blacklist",
            modifier = Modifier.size(12.dp),
          )
          Text(
            text = if (rule.isBlacklist) "BLACKLIST" else "WHITELIST",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = JetBrainsMonoFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
          )
        }
      }
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = rule.toFilterLine(),
          style = MaterialTheme.typography.labelMedium,
          fontFamily = JetBrainsMonoFontFamily,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = rule.ruleType.displayName,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      IconButton(
        onClick = onEdit,
        modifier = Modifier.size(36.dp).testTag("edit_rule_btn"),
      ) {
        Icon(
          imageVector = Icons.Rounded.Edit,
          contentDescription = "Edit rule",
          modifier = Modifier.size(16.dp),
        )
      }
      IconButton(
        onClick = onDelete,
        modifier = Modifier.size(36.dp).testTag("delete_rule_btn"),
      ) {
        Icon(
          imageVector = Icons.Rounded.DeleteOutline,
          contentDescription = "Delete rule",
          tint = MaterialTheme.colorScheme.error,
          modifier = Modifier.size(16.dp),
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterRuleDialogSheet(
  initialRule: StructuredFilterRule?,
  onDismiss: () -> Unit,
  onSave: (StructuredFilterRule) -> Unit,
) {
  val editableTypes =
    remember {
      listOf(
        FilterRuleType.CLASS_METHOD,
        FilterRuleType.ALL_CLASS_METHODS,
        FilterRuleType.PACKAGE_ALL,
        FilterRuleType.METHOD_ALL_CLASSES,
      )
    }
  var ruleType by remember {
    mutableStateOf(
      initialRule?.ruleType?.takeIf { it != FilterRuleType.CUSTOM_REGEX }
        ?: FilterRuleType.CLASS_METHOD
    )
  }
  var packageName by remember { mutableStateOf(initialRule?.packageName ?: "com/example") }
  var className by remember { mutableStateOf(initialRule?.className ?: "MainActivity") }
  var methodName by remember { mutableStateOf(initialRule?.methodName ?: "onCreate(") }
  var isBlacklist by remember { mutableStateOf(initialRule?.isBlacklist ?: false) }

  val previewRule =
    StructuredFilterRule(
      id = initialRule?.id ?: "rule_${System.nanoTime()}",
      packageName = packageName,
      className = className,
      methodName = methodName,
      isBlacklist = isBlacklist,
      ruleType = ruleType,
    )

  val canSave =
    when (ruleType) {
      FilterRuleType.CLASS_METHOD -> className.isNotBlank() && methodName.isNotBlank()
      FilterRuleType.ALL_CLASS_METHODS -> className.isNotBlank()
      FilterRuleType.PACKAGE_ALL -> packageName.isNotBlank()
      FilterRuleType.METHOD_ALL_CLASSES -> methodName.isNotBlank()
      FilterRuleType.CUSTOM_REGEX -> true
    }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text = if (initialRule == null) "Add Filter Rule" else "Edit Filter Rule",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
      )

      Text("Rule Type", style = MaterialTheme.typography.titleSmall)
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        editableTypes.forEach { type ->
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
              Modifier
                .fillMaxWidth()
                .clickable { ruleType = type }
                .padding(vertical = 2.dp)
                .testTag("rule_type_option_${type.name}"),
          ) {
            RadioButton(
              selected = ruleType == type,
              onClick = { ruleType = type },
            )
            Column(modifier = Modifier.padding(start = 6.dp)) {
              Text(
                text = type.displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
              )
              Text(
                text = type.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }

      if (ruleType != FilterRuleType.METHOD_ALL_CLASSES) {
        OutlinedTextField(
          value = packageName,
          onValueChange = { packageName = it },
          label = { Text("Package Name (slash or dot format)") },
          placeholder = { Text("com/example/app") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("rule_input_package"),
        )
      }

      if (ruleType == FilterRuleType.CLASS_METHOD || ruleType == FilterRuleType.ALL_CLASS_METHODS) {
        OutlinedTextField(
          value = className,
          onValueChange = { className = it },
          label = { Text("Class Name") },
          placeholder = { Text("MainActivity") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("rule_input_class"),
        )
      }

      if (ruleType == FilterRuleType.CLASS_METHOD || ruleType == FilterRuleType.METHOD_ALL_CLASSES) {
        OutlinedTextField(
          value = methodName,
          onValueChange = { methodName = it },
          label = { Text("Method Name") },
          placeholder = { Text("onCreate(") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("rule_input_method"),
        )
        Text(
          text = "Common Android & Security Methods:",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          COMMON_ANDROID_METHODS.forEach { method ->
            AssistChip(
              onClick = { methodName = "$method(" },
              label = { Text(method, style = MaterialTheme.typography.labelSmall) },
              modifier = Modifier.testTag("preset_method_$method"),
            )
          }
        }
      }

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
          text = "Mode:",
          style = MaterialTheme.typography.bodyMedium,
          modifier = Modifier.weight(1f),
        )
        FilterChip(
          selected = !isBlacklist,
          onClick = { isBlacklist = false },
          label = { Text(stringResource(R.string.filter_whitelist)) },
          leadingIcon =
            if (!isBlacklist) {
              { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
            } else null,
          modifier = Modifier.testTag("rule_chip_whitelist"),
        )
        FilterChip(
          selected = isBlacklist,
          onClick = { isBlacklist = true },
          label = { Text(stringResource(R.string.filter_blacklist)) },
          leadingIcon =
            if (isBlacklist) {
              { Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(16.dp)) }
            } else null,
          modifier = Modifier.testTag("rule_chip_blacklist"),
        )
      }

      Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
          text = previewRule.toFilterLine(),
          style = MaterialTheme.typography.labelMedium,
          fontFamily = JetBrainsMonoFontFamily,
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.padding(12.dp).testTag("rule_preview_text"),
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        OutlinedButton(
          onClick = onDismiss,
          modifier = Modifier.weight(1f),
        ) {
          Text("Cancel")
        }
        Button(
          onClick = { onSave(previewRule) },
          enabled = canSave,
          modifier = Modifier.weight(1f).testTag("rule_save_btn"),
        ) {
          Text(if (initialRule == null) "Add Rule" else "Save Changes")
        }
      }
    }
  }
}

