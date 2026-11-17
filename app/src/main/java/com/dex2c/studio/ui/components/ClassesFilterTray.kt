package com.dex2c.studio.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dex2c.studio.ui.ClassFilterState
import com.dex2c.studio.ui.ClassTypeFilter
import com.dex2c.studio.ui.MethodScopeFilter
import com.dex2c.studio.ui.theme.JetBrainsMonoFontFamily

/**
 * Modern, organized expandable filter tray for DEX Classes Explorer.
 * Neatly groups Class Types and Method Scopes in a sleek, compact container.
 */
@Composable
fun ClassesFilterTray(
  visible: Boolean,
  filterState: ClassFilterState,
  onClassTypeFilterChange: (ClassTypeFilter) -> Unit,
  onToggleHideInnerClasses: () -> Unit,
  onToggleNonEmptyPackages: () -> Unit,
  onMethodScopeFilterChange: (MethodScopeFilter) -> Unit,
  onResetExtraFilters: () -> Unit,
  modifier: Modifier = Modifier,
  onToggleHideFramework: (() -> Unit)? = null,
  onToggleOnlyBytecode: (() -> Unit)? = null,
  onToggleOnlySelected: (() -> Unit)? = null,
  onToggleOnlyMatchingFilterRules: (() -> Unit)? = null,
  compactMiddlePackages: Boolean = false,
  onToggleCompactPackages: (() -> Unit)? = null,
) {
  AnimatedVisibility(
    visible = visible,
    modifier = modifier,
  ) {
    Surface(
      shape = RoundedCornerShape(14.dp),
      color = MaterialTheme.colorScheme.surfaceContainer,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
      modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
    ) {
      Column(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        // Section 1: Scope & Rules
        if (onToggleHideFramework != null || onToggleOnlyBytecode != null || onToggleOnlyMatchingFilterRules != null || onToggleOnlySelected != null) {
          Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            if (onToggleHideFramework != null) {
              FilterChip(
                selected = filterState.hideFramework,
                onClick = onToggleHideFramework,
                label = { Text("App Only") },
                modifier = Modifier.testTag("chip_hide_framework"),
              )
            }
            if (onToggleOnlyBytecode != null) {
              FilterChip(
                selected = filterState.onlyWithBytecode,
                onClick = onToggleOnlyBytecode,
                label = { Text("Has Bytecode") },
                modifier = Modifier.testTag("chip_has_bytecode"),
              )
            }
            if (onToggleOnlyMatchingFilterRules != null) {
              FilterChip(
                selected = filterState.onlyMatchingFilterRules,
                onClick = onToggleOnlyMatchingFilterRules,
                label = { Text("Match Rules") },
                modifier = Modifier.testTag("chip_match_filter_rules"),
              )
            }
            if (onToggleOnlySelected != null) {
              FilterChip(
                selected = filterState.onlySelected,
                onClick = onToggleOnlySelected,
                label = { Text("Selected Only") },
                modifier = Modifier.testTag("chip_selected_only"),
              )
            }
            if (onToggleCompactPackages != null) {
              FilterChip(
                selected = compactMiddlePackages,
                onClick = onToggleCompactPackages,
                label = { Text("Compact Levels") },
                modifier = Modifier.testTag("chip_folder_levels_toggle"),
              )
            }
          }
        }

        // Section 2: Class Type Filters & Inner/NonEmpty Toggles
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          ClassTypeFilter.entries.forEach { typeFilter ->
            FilterChip(
              selected = filterState.classTypeFilter == typeFilter,
              onClick = { onClassTypeFilterChange(typeFilter) },
              label = { Text(typeFilter.label) },
              modifier = Modifier.testTag("filter_class_type_${typeFilter.name}"),
            )
          }
          FilterChip(
            selected = filterState.hideInnerClasses,
            onClick = onToggleHideInnerClasses,
            label = { Text("Hide \$Inner") },
            modifier = Modifier.testTag("filter_hide_inner"),
          )
          FilterChip(
            selected = filterState.nonEmptyPackagesOnly,
            onClick = onToggleNonEmptyPackages,
            label = { Text("Non-Empty Pkgs") },
            modifier = Modifier.testTag("filter_non_empty_pkgs"),
          )
        }

        // Section 3: Method Scope Filters & Reset Action
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            MethodScopeFilter.entries.forEach { scope ->
              FilterChip(
                selected = filterState.methodScopeFilter == scope,
                onClick = { onMethodScopeFilterChange(scope) },
                label = { Text(scope.label) },
                modifier = Modifier.testTag("filter_method_scope_${scope.name}"),
              )
            }
          }

          if (filterState.extraFilterCount > 0) {
            Spacer(Modifier.width(6.dp))
            TextButton(
              onClick = onResetExtraFilters,
              contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
              modifier =
                Modifier.defaultMinSize(minHeight = 32.dp).testTag("filter_reset_extra_btn"),
            ) {
              Text("Reset", style = MaterialTheme.typography.labelSmall)
            }
          }
        }
      }
    }
  }
}

/**
 * Compact strip summarizing active non-default filters.
 * Gives instant feedback without requiring the filter tray to stay open.
 */
@Composable
fun ActiveFilterSummaryStrip(
  filterState: ClassFilterState,
  onResetFilters: () -> Unit,
  onClearClassType: () -> Unit,
  onClearMethodScope: () -> Unit,
  onClearInnerClasses: () -> Unit,
  onClearNonEmpty: () -> Unit,
  onClearMatchRules: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val hasActiveFilters = filterState.extraFilterCount > 0

  if (hasActiveFilters) {
    Row(
      modifier =
        modifier
          .fillMaxWidth()
          .padding(vertical = 3.dp)
          .horizontalScroll(rememberScrollState()),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
            imageVector = Icons.Rounded.FilterAlt,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(12.dp),
          )
          Spacer(Modifier.width(4.dp))
          Text(
            text = "Active:",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = JetBrainsMonoFontFamily,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
          )
        }
      }

      if (filterState.classTypeFilter != ClassTypeFilter.ALL) {
        ActiveFilterPill(
          label = filterState.classTypeFilter.label,
          onDismiss = onClearClassType,
        )
      }

      if (filterState.methodScopeFilter != MethodScopeFilter.ALL) {
        ActiveFilterPill(
          label = filterState.methodScopeFilter.label,
          onDismiss = onClearMethodScope,
        )
      }

      if (filterState.hideInnerClasses) {
        ActiveFilterPill(
          label = "No \$Inner",
          onDismiss = onClearInnerClasses,
        )
      }

      if (filterState.nonEmptyPackagesOnly) {
        ActiveFilterPill(
          label = "Non-Empty",
          onDismiss = onClearNonEmpty,
        )
      }

      if (filterState.onlyMatchingFilterRules) {
        ActiveFilterPill(
          label = "Match Rules",
          onDismiss = onClearMatchRules,
        )
      }

      TextButton(
        onClick = onResetFilters,
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
        modifier = Modifier.defaultMinSize(minHeight = 24.dp),
      ) {
        Text(
          text = "Clear all",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.primary,
        )
      }
    }
  }
}

@Composable
private fun ActiveFilterPill(
  label: String,
  onDismiss: () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(8.dp),
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
  ) {
    Row(
      modifier = Modifier.padding(start = 6.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Spacer(Modifier.width(2.dp))
      IconButton(
        onClick = onDismiss,
        modifier = Modifier.size(18.dp),
      ) {
        Icon(
          imageVector = Icons.Rounded.Close,
          contentDescription = "Remove filter $label",
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(12.dp),
        )
      }
    }
  }
}

/**
 * Modern modal bottom sheet organizing DEX Classes Explorer filters into clean sections.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassesFilterSheet(
  visible: Boolean,
  filterState: ClassFilterState,
  onDismiss: () -> Unit,
  onToggleHideFramework: () -> Unit,
  onToggleOnlyBytecode: () -> Unit,
  onToggleOnlySelected: () -> Unit,
  onToggleOnlyMatchingFilterRules: () -> Unit,
  onClassTypeFilterChange: (ClassTypeFilter) -> Unit,
  onToggleHideInnerClasses: () -> Unit,
  onToggleNonEmptyPackages: () -> Unit,
  onMethodScopeFilterChange: (MethodScopeFilter) -> Unit,
  onResetExtraFilters: () -> Unit,
  compactMiddlePackages: Boolean = false,
  onToggleCompactPackages: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  if (visible) {
    ModalBottomSheet(
      onDismissRequest = onDismiss,
      shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
      containerColor = MaterialTheme.colorScheme.surfaceContainer,
      modifier = modifier.testTag("classes_filter_sheet"),
    ) {
      Column(
        modifier =
          Modifier.fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.FilterAlt,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(20.dp),
            )
            Text(
              text = "Filters",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
          }

          Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
              onClick = onResetExtraFilters,
              modifier = Modifier.testTag("filter_reset_extra_btn"),
            ) {
              Text("Reset All", style = MaterialTheme.typography.labelMedium)
            }
            IconButton(onClick = onDismiss) {
              Icon(Icons.Rounded.Close, contentDescription = "Close filters")
            }
          }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        // Section 1: Scope & Rules
        Text(
          text = "Scope & Rules",
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.primary,
        )
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          FilterChip(
            selected = filterState.hideFramework,
            onClick = onToggleHideFramework,
            label = { Text("App Only") },
            modifier = Modifier.testTag("chip_hide_framework"),
          )
          FilterChip(
            selected = filterState.onlyWithBytecode,
            onClick = onToggleOnlyBytecode,
            label = { Text("Has Bytecode") },
            modifier = Modifier.testTag("chip_has_bytecode"),
          )
          FilterChip(
            selected = filterState.onlyMatchingFilterRules,
            onClick = onToggleOnlyMatchingFilterRules,
            label = { Text("Match Rules") },
            modifier = Modifier.testTag("chip_match_filter_rules"),
          )
          FilterChip(
            selected = filterState.onlySelected,
            onClick = onToggleOnlySelected,
            label = { Text("Selected Only") },
            modifier = Modifier.testTag("chip_selected_only"),
          )
        }

        // Section 2: Hierarchy & Structure
        Text(
          text = "Hierarchy & Structure",
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.primary,
        )
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          if (onToggleCompactPackages != null) {
            FilterChip(
              selected = compactMiddlePackages,
              onClick = onToggleCompactPackages,
              label = { Text("Compact Levels") },
              modifier = Modifier.testTag("chip_folder_levels_toggle"),
            )
          }
          FilterChip(
            selected = filterState.hideInnerClasses,
            onClick = onToggleHideInnerClasses,
            label = { Text("Hide \$Inner") },
            modifier = Modifier.testTag("filter_hide_inner"),
          )
          FilterChip(
            selected = filterState.nonEmptyPackagesOnly,
            onClick = onToggleNonEmptyPackages,
            label = { Text("Non-Empty Pkgs") },
            modifier = Modifier.testTag("filter_non_empty_pkgs"),
          )
        }

        // Section 3: Class Type
        Text(
          text = "Class Type",
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.primary,
        )
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          ClassTypeFilter.entries.forEach { typeFilter ->
            FilterChip(
              selected = filterState.classTypeFilter == typeFilter,
              onClick = { onClassTypeFilterChange(typeFilter) },
              label = { Text(typeFilter.label) },
              modifier = Modifier.testTag("filter_class_type_${typeFilter.name}"),
            )
          }
        }

        // Section 4: Method Scope
        Text(
          text = "Method Scope",
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.primary,
        )
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          MethodScopeFilter.entries.forEach { scope ->
            FilterChip(
              selected = filterState.methodScopeFilter == scope,
              onClick = { onMethodScopeFilterChange(scope) },
              label = { Text(scope.label) },
              modifier = Modifier.testTag("filter_method_scope_${scope.name}"),
            )
          }
        }

        Spacer(Modifier.height(16.dp))
      }
    }
  }
}
