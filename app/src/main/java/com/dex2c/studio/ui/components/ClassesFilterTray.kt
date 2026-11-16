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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        // Row 1: Class Type Filters & Inner/NonEmpty Toggles
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          ClassTypeFilter.entries.forEach { typeFilter ->
            item {
              FilterChip(
                selected = filterState.classTypeFilter == typeFilter,
                onClick = { onClassTypeFilterChange(typeFilter) },
                label = { Text(typeFilter.label) },
                modifier = Modifier.testTag("filter_class_type_${typeFilter.name}"),
              )
            }
          }
          item {
            FilterChip(
              selected = filterState.hideInnerClasses,
              onClick = onToggleHideInnerClasses,
              label = { Text("Hide \$Inner") },
              modifier = Modifier.testTag("filter_hide_inner"),
            )
          }
          item {
            FilterChip(
              selected = filterState.nonEmptyPackagesOnly,
              onClick = onToggleNonEmptyPackages,
              label = { Text("Non-Empty Pkgs") },
              modifier = Modifier.testTag("filter_non_empty_pkgs"),
            )
          }
        }

        // Row 2: Method Scope Filters & Reset Action
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            MethodScopeFilter.entries.forEach { scope ->
              item {
                FilterChip(
                  selected = filterState.methodScopeFilter == scope,
                  onClick = { onMethodScopeFilterChange(scope) },
                  label = { Text(scope.label) },
                  modifier = Modifier.testTag("filter_method_scope_${scope.name}"),
                )
              }
            }
          }

          if (filterState.extraFilterCount > 0) {
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
