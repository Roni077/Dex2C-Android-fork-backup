package com.example.ui.components

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
import com.example.R
import com.example.transpiler.COMMON_ANDROID_METHODS
import com.example.transpiler.FRAMEWORK_BLACKLIST_PRESETS
import com.example.transpiler.FilterMatchSummary
import com.example.transpiler.FilterRuleEngine
import com.example.transpiler.FilterRuleType
import com.example.transpiler.LIFECYCLE_SECURITY_PRESETS
import com.example.transpiler.StructuredFilterRule
import com.example.transpiler.TranspilerConfig
import com.example.ui.theme.JetBrainsMonoFontFamily

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
  onSyncSelectionToWildcardRules: (isBlacklist: Boolean) -> Unit,
  onApplyFilterRules: () -> Unit,
  modifier: Modifier = Modifier,
  fillAvailableHeight: Boolean = false,
  showModuleNameField: Boolean = false,
  onImportFilterTxt: (() -> Unit)? = null,
  onlyMatchingExplorerFilter: Boolean? = null,
  onToggleMatchingExplorerFilter: (() -> Unit)? = null,
) {
  var subMode by rememberSaveable { mutableStateOf(FilterEditorSubMode.BUILDER) }
  var showRuleDialog by remember { mutableStateOf(false) }
  var editingRule by remember { mutableStateOf<StructuredFilterRule?>(null) }

  val structuredRules =
    remember(config.filterRulesText) { FilterRuleEngine.parseStructuredRules(config.filterRulesText) }
  val activeRuleLines =
    remember(config.filterRulesText) {
      config.filterRulesText.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }
  val whitelistCount = structuredRules.count { !it.isBlacklist }
  val blacklistCount = structuredRules.count { it.isBlacklist }

  fun updateRulesList(newRules: List<StructuredFilterRule>) {
    onUpdateConfig { current ->
      current.copy(filterRulesText = FilterRuleEngine.serializeStructuredRules(newRules))
    }
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
      if (showModuleNameField) {
        Text(
          text = stringResource(R.string.config_filter_rules),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.weight(1f),
        )
      } else {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Filter Rule Builder",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
          )
          Text(
            text = "$whitelistCount Whitelist • $blacklistCount Blacklist",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = JetBrainsMonoFontFamily,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      SingleChoiceSegmentedButtonRow {
        SegmentedButton(
          selected = subMode == FilterEditorSubMode.BUILDER,
          onClick = { subMode = FilterEditorSubMode.BUILDER },
          shape = SegmentedButtonDefaults.itemShape(0, 2),
          modifier = Modifier.testTag("filter_mode_builder_btn"),
        ) {
          Text(stringResource(R.string.filter_mode_builder), style = MaterialTheme.typography.labelSmall)
        }
        SegmentedButton(
          selected = subMode == FilterEditorSubMode.RAW,
          onClick = { subMode = FilterEditorSubMode.RAW },
          shape = SegmentedButtonDefaults.itemShape(1, 2),
          modifier = Modifier.testTag("filter_mode_raw_btn"),
        ) {
          Text(stringResource(R.string.filter_mode_raw), style = MaterialTheme.typography.labelSmall)
        }
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
    } else {
      // Whitelist vs Blacklist Summary Cards
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
          modifier = Modifier.weight(1f).testTag("summary_card_whitelist"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.CheckCircle,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(16.dp),
            )
            Column {
              Text(
                text = "WHITELIST ($whitelistCount)",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
              )
              Text(
                text = "Include in focus",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
              )
            }
          }
        }

        Surface(
          shape = RoundedCornerShape(14.dp),
          color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
          modifier = Modifier.weight(1f).testTag("summary_card_blacklist"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.Block,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.error,
              modifier = Modifier.size(16.dp),
            )
            Column {
              Text(
                text = "BLACKLIST ($blacklistCount)",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer,
              )
              Text(
                text = "Skip / exclude",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
              )
            }
          }
        }
      }
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

    // One-Tap Presets: Lifecycle/Security Whitelists + Framework Blacklists
    if (!showModuleNameField) {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          text = "Presets (Tap to toggle Whitelist / Blacklist rules):",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.fillMaxWidth().testTag("filter_presets_row"),
        ) {
          items(LIFECYCLE_SECURITY_PRESETS, key = { it.id }) { preset ->
            val isActive = preset.ruleLine in activeRuleLines
            FilterChip(
              selected = isActive,
              onClick = {
                onUpdateConfig { current ->
                  current.copy(
                    filterRulesText = FilterRuleEngine.toggleRuleLine(current.filterRulesText, preset.ruleLine)
                  )
                }
              },
              leadingIcon = {
                Icon(
                  imageVector = if (isActive) Icons.Rounded.Check else Icons.Rounded.Add,
                  contentDescription = null,
                  modifier = Modifier.size(14.dp),
                )
              },
              label = {
                Text(
                  text = preset.label,
                  style = MaterialTheme.typography.labelSmall,
                  fontFamily = JetBrainsMonoFontFamily,
                )
              },
              modifier = Modifier.testTag("preset_lifecycle_${preset.id}"),
            )
          }
          items(FRAMEWORK_BLACKLIST_PRESETS, key = { it.id }) { preset ->
            val isActive = preset.ruleLine in activeRuleLines
            FilterChip(
              selected = isActive,
              onClick = {
                onUpdateConfig { current ->
                  current.copy(
                    filterRulesText = FilterRuleEngine.toggleRuleLine(current.filterRulesText, preset.ruleLine)
                  )
                }
              },
              leadingIcon = {
                Icon(
                  imageVector = if (isActive) Icons.Rounded.Check else Icons.Rounded.Block,
                  contentDescription = null,
                  modifier = Modifier.size(14.dp),
                )
              },
              label = {
                Text(
                  text = preset.label,
                  style = MaterialTheme.typography.labelSmall,
                  fontFamily = JetBrainsMonoFontFamily,
                )
              },
              modifier = Modifier.testTag("preset_framework_${preset.id}"),
            )
          }
        }
      }
    }

    if (subMode == FilterEditorSubMode.BUILDER) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        FilledTonalButton(
          onClick = {
            editingRule = null
            showRuleDialog = true
          },
          shape = RoundedCornerShape(14.dp),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
          modifier = Modifier.minimumInteractiveComponentSize().testTag("filter_add_rule_btn"),
        ) {
          Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(4.dp))
          Text(stringResource(R.string.filter_add_rule), style = MaterialTheme.typography.labelMedium)
        }
        OutlinedButton(
          onClick = { onSyncSelectionToWildcardRules(false) },
          enabled = selectedMethodCount > 0,
          shape = RoundedCornerShape(14.dp),
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
          modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("filter_sync_selection_btn"),
        ) {
          Icon(Icons.Rounded.AutoFixHigh, contentDescription = null, modifier = Modifier.size(15.dp))
          Spacer(Modifier.width(4.dp))
          Text(
            text =
              if (selectedMethodCount > 0) {
                "${stringResource(R.string.filter_sync_selection)} ($selectedMethodCount)"
              } else {
                stringResource(R.string.filter_sync_selection)
              },
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }

      Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier =
          Modifier
            .fillMaxWidth()
            .then(if (fillAvailableHeight) Modifier.weight(1f) else Modifier.heightIn(min = 120.dp, max = 230.dp))
            .testTag("filter_rules_builder_list"),
      ) {
        if (structuredRules.isEmpty()) {
          Box(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text =
                "No rules configured. Tap \"Add Rule\", tap a preset chip above, or use +W / !B in the Explorer tree.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        } else {
          LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            itemsIndexed(structuredRules, key = { idx, r -> "${r.id}_$idx" }) { idx, rule ->
              StructuredRuleItemCard(
                rule = rule,
                onToggleBlacklist = {
                  val updated =
                    structuredRules.toMutableList().also {
                      it[idx] = rule.copy(isBlacklist = !rule.isBlacklist)
                    }
                  updateRulesList(updated)
                },
                onEdit = {
                  editingRule = rule
                  showRuleDialog = true
                },
                onDelete = {
                  val updated = structuredRules.toMutableList().also { it.removeAt(idx) }
                  updateRulesList(updated)
                },
              )
            }
          }
        }
      }
    } else {
      OutlinedTextField(
        value = config.filterRulesText,
        onValueChange = { v -> onUpdateConfig { it.copy(filterRulesText = v) } },
        label = { Text(stringResource(R.string.config_filter_txt_label)) },
        shape = RoundedCornerShape(14.dp),
        textStyle = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
        modifier =
          Modifier
            .fillMaxWidth()
            .then(if (fillAvailableHeight) Modifier.weight(1f) else Modifier.heightIn(min = 140.dp, max = 230.dp))
            .testTag("input_filter_txt"),
      )
    }

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

  if (showRuleDialog) {
    FilterRuleDialogSheet(
      initialRule = editingRule,
      onDismiss = {
        showRuleDialog = false
        editingRule = null
      },
      onSave = { savedRule ->
        val updated =
          if (editingRule == null) {
            structuredRules + savedRule
          } else {
            structuredRules.map { if (it.id == editingRule?.id) savedRule else it }
          }
        updateRulesList(updated)
        showRuleDialog = false
        editingRule = null
      },
    )
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

/**
 * Interactive Filter Focus & Presets card displayed on the Home screen so users can manage
 * Whitelist/Blacklist presets, apply rules to the decompilation/transpilation queue, or jump
 * directly to the full Filter Rule Builder.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeFilterFocusCard(
  config: TranspilerConfig,
  filterMatchSummary: FilterMatchSummary?,
  hasLoadedDex: Boolean,
  onTogglePresetRule: (String) -> Unit,
  onApplyFilterRules: () -> Unit,
  onOpenRuleBuilder: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val structuredRules =
    remember(config.filterRulesText) { FilterRuleEngine.parseStructuredRules(config.filterRulesText) }
  val activeLines =
    remember(config.filterRulesText) {
      config.filterRulesText.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }
  val whitelistCount = structuredRules.count { !it.isBlacklist }
  val blacklistCount = structuredRules.count { it.isBlacklist }

  Surface(
    modifier = modifier.fillMaxWidth().testTag("home_filter_focus_card"),
    shape = RoundedCornerShape(22.dp),
    color = MaterialTheme.colorScheme.surfaceContainer,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.weight(1f),
        ) {
          Icon(
            imageVector = Icons.Rounded.FilterAlt,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
          )
          Column {
            Text(
              text = "Decompilation & Filter Focus",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.SemiBold,
            )
            Text(
              text = "$whitelistCount Whitelist • $blacklistCount Blacklist rules",
              style = MaterialTheme.typography.labelSmall,
              fontFamily = JetBrainsMonoFontFamily,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        if (filterMatchSummary != null) {
          TagPill(
            text = "${filterMatchSummary.matchedMethodIds.size}m / ${filterMatchSummary.matchedClassCount}c",
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
          )
        }
      }

      LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        items(LIFECYCLE_SECURITY_PRESETS.take(4), key = { "home_w_${it.id}" }) { preset ->
          val active = preset.ruleLine in activeLines
          FilterChip(
            selected = active,
            onClick = { onTogglePresetRule(preset.ruleLine) },
            label = {
              Text(
                text = preset.label,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = JetBrainsMonoFontFamily,
              )
            },
            modifier = Modifier.testTag("home_preset_${preset.id}"),
          )
        }
        items(FRAMEWORK_BLACKLIST_PRESETS.take(3), key = { "home_b_${it.id}" }) { preset ->
          val active = preset.ruleLine in activeLines
          FilterChip(
            selected = active,
            onClick = { onTogglePresetRule(preset.ruleLine) },
            label = {
              Text(
                text = preset.label,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = JetBrainsMonoFontFamily,
              )
            },
            modifier = Modifier.testTag("home_preset_${preset.id}"),
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        if (hasLoadedDex) {
          OutlinedButton(
            onClick = onApplyFilterRules,
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("home_apply_rules_btn"),
          ) {
            Text(
              text = "Apply to Queue",
              style = MaterialTheme.typography.labelMedium,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
        FilledTonalButton(
          onClick = onOpenRuleBuilder,
          shape = RoundedCornerShape(16.dp),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
          modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("home_manage_rules_btn"),
        ) {
          Text(
            text = "Rule Builder (${structuredRules.size})",
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Spacer(Modifier.width(4.dp))
          Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
            contentDescription = null,
            modifier = Modifier.size(15.dp),
          )
        }
      }
    }
  }
}
