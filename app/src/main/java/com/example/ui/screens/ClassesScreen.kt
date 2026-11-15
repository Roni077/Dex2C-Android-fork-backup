package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.UnfoldLess
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.dex.DexClassInfo
import com.example.dex.DexEncodedMethod
import com.example.transpiler.FilterMatchSummary
import com.example.transpiler.FilterRuleEngine
import com.example.transpiler.TranspilerConfig
import com.example.ui.ClassFilterState
import com.example.ui.ClassSortOrder
import com.example.ui.ClassTypeFilter
import com.example.ui.DexLoadState
import com.example.ui.MethodScopeFilter
import com.example.ui.components.InteractiveFilterRuleBuilderPanel
import com.example.ui.matchesMethodScope
import com.example.ui.theme.JetBrainsMonoFontFamily

enum class ClassesViewMode {
  TREE,
  FLAT,
}

enum class ClassesSubTab {
  EXPLORER,
  FILTER_RULES,
}

internal data class PackageGroup(
  val packageName: String,
  val displayLabel: String,
  val depth: Int,
  val directClasses: List<DexClassInfo>,
  val subtreeClasses: List<DexClassInfo>,
  val subpackageCount: Int,
  val totalBytecodeMethods: Int,
  val selectedMethodCount: Int,
  val children: List<PackageGroup>,
)

private sealed interface ExplorerRowItem {
  val key: String

  data class PackageRow(val group: PackageGroup, val expanded: Boolean) : ExplorerRowItem {
    override val key: String = "pkg:${group.packageName}"
  }

  data class ClassRow(
    val dexClass: DexClassInfo,
    val inTreeMode: Boolean,
    val depth: Int = 0,
  ) : ExplorerRowItem {
    override val key: String = "cls:${dexClass.uniqueKey}"
  }
}

/**
 * Builds a Dex Editor Plus style hierarchical package tree (`com` -> `crackme` -> `ui.theme`):
 * - Depth 0 (`com`) and Depth 1 (`crackme`) remain distinct expandable folder levels so top-level
 *   domains and app packages are nested hierarchically.
 * - Deeper subpackage chains (`depth >= 2`, such as `ui` -> `theme`) compact single-child empty
 *   intermediate folders into `ui.theme`.
 */
internal fun buildNestedPackageTree(
  classes: List<DexClassInfo>,
  selectedMethodIds: Set<String>,
  sortOrder: ClassSortOrder,
  defaultPackageLabel: String,
): List<PackageGroup> {
  class MutablePkgNode(
    val segment: String,
    val fullPath: String,
    val children: LinkedHashMap<String, MutablePkgNode> = linkedMapOf(),
    val directClasses: MutableList<DexClassInfo> = mutableListOf(),
  )

  val root = MutablePkgNode("", "")
  for (cls in classes) {
    val pkg = cls.packageName.ifBlank { defaultPackageLabel }
    if (pkg == defaultPackageLabel || pkg == "(default)") {
      val defNode =
        root.children.getOrPut(defaultPackageLabel) {
          MutablePkgNode(defaultPackageLabel, defaultPackageLabel)
        }
      defNode.directClasses += cls
    } else {
      val parts = pkg.split('.').filter { it.isNotBlank() }
      var curr = root
      var pathAcc = ""
      for (part in parts) {
        pathAcc = if (pathAcc.isEmpty()) part else "$pathAcc.$part"
        curr = curr.children.getOrPut(part) { MutablePkgNode(part, pathAcc) }
      }
      curr.directClasses += cls
    }
  }

  fun compactAndConvert(node: MutablePkgNode, depth: Int): PackageGroup {
    var current = node
    var label = node.segment
    if (depth >= 2) {
      while (current.directClasses.isEmpty() && current.children.size == 1) {
        val onlyChild = current.children.values.first()
        label = if (label.isEmpty()) onlyChild.segment else "$label.${onlyChild.segment}"
        current = onlyChild
      }
    }

    val convertedChildren =
      current.children.values.map { child -> compactAndConvert(child, depth + 1) }
    val sortedChildren = sortPackageGroups(convertedChildren, sortOrder)
    val sortedDirectClasses =
      sortClassesForDisplay(current.directClasses, sortOrder, selectedMethodIds)
    val allSubtreeClasses = sortedDirectClasses + sortedChildren.flatMap { it.subtreeClasses }
    val totalBytecode = allSubtreeClasses.sumOf { it.bytecodeMethodCount }
    val selectedCount =
      allSubtreeClasses.sumOf { cls -> cls.allMethods.count { it.id in selectedMethodIds } }

    return PackageGroup(
      packageName = current.fullPath,
      displayLabel = label,
      depth = depth,
      directClasses = sortedDirectClasses,
      subtreeClasses = allSubtreeClasses,
      subpackageCount = sortedChildren.size,
      totalBytecodeMethods = totalBytecode,
      selectedMethodCount = selectedCount,
      children = sortedChildren,
    )
  }

  val topGroups = root.children.values.map { compactAndConvert(it, 0) }
  return sortPackageGroups(topGroups, sortOrder)
}

private fun sortPackageGroups(
  groups: List<PackageGroup>,
  sortOrder: ClassSortOrder,
): List<PackageGroup> =
  when (sortOrder) {
    ClassSortOrder.NAME_ASC -> groups.sortedBy { it.packageName.lowercase() }
    ClassSortOrder.NAME_DESC -> groups.sortedByDescending { it.packageName.lowercase() }
    ClassSortOrder.BYTECODE_DESC ->
      groups.sortedWith(
        compareByDescending<PackageGroup> { it.totalBytecodeMethods }
          .thenBy { it.packageName.lowercase() }
      )
    ClassSortOrder.SELECTED_FIRST ->
      groups.sortedWith(
        compareByDescending<PackageGroup> { it.selectedMethodCount > 0 }
          .thenByDescending { it.selectedMethodCount }
          .thenByDescending { it.totalBytecodeMethods }
          .thenBy { it.packageName.lowercase() }
      )
    ClassSortOrder.DEX_ORDER -> groups
  }

@Composable
fun ClassesScreen(
  loadState: DexLoadState,
  classes: List<DexClassInfo>,
  filterState: ClassFilterState,
  selectedMethodIds: Set<String>,
  allowInit: Boolean,
  onSearchChange: (String) -> Unit,
  onToggleHideFramework: () -> Unit,
  onToggleOnlyBytecode: () -> Unit,
  onToggleOnlySelected: () -> Unit,
  onToggleClassSelection: (DexClassInfo) -> Unit,
  @Suppress("UNUSED_PARAMETER") onToggleMethodSelection: (String) -> Unit,
  @Suppress("UNUSED_PARAMETER") onInspectMethod: (DexClassInfo, DexEncodedMethod) -> Unit,
  onInspectSelfApk: () -> Unit,
  modifier: Modifier = Modifier,
  onInspectClassSmali: (DexClassInfo) -> Unit = {},
  onSortOrderChange: (ClassSortOrder) -> Unit = {},
  onClassTypeFilterChange: (ClassTypeFilter) -> Unit = {},
  onToggleHideInnerClasses: () -> Unit = {},
  onMethodScopeFilterChange: (MethodScopeFilter) -> Unit = {},
  onToggleNonEmptyPackages: () -> Unit = {},
  onResetExtraFilters: () -> Unit = {},
  onTogglePackageSelection: (List<DexClassInfo>) -> Unit = {},
  onDexSourceFilterChange: (String?) -> Unit = {},
  config: TranspilerConfig = TranspilerConfig(),
  filterMatchSummary: FilterMatchSummary? = null,
  onUpdateConfig: ((TranspilerConfig) -> TranspilerConfig) -> Unit = {},
  onSyncSelectionToWildcardRules: (isBlacklist: Boolean) -> Unit = {},
  onApplyFilterRules: () -> Unit = {},
  onToggleOnlyMatchingFilterRules: () -> Unit = {},
  onQuickPackageRule: (packageName: String, isBlacklist: Boolean) -> Unit = { _, _ -> },
  onQuickClassRule: (dexClass: DexClassInfo, isBlacklist: Boolean) -> Unit = { _, _ -> },
  initialSubTab: ClassesSubTab = ClassesSubTab.EXPLORER,
) {
  var activeSubTab by rememberSaveable(initialSubTab) { mutableStateOf(initialSubTab) }
  var viewMode by rememberSaveable { mutableStateOf(ClassesViewMode.TREE) }
  var showSelectionCheckboxes by rememberSaveable { mutableStateOf(false) }
  var showSortMenu by remember { mutableStateOf(false) }
  var showDexMenu by remember { mutableStateOf(false) }
  var showExtraFilters by remember { mutableStateOf(false) }
  val manualExpandedPackages = remember { mutableStateMapOf<String, Boolean>() }
  val searchCollapsedPackages = remember { mutableStateMapOf<String, Boolean>() }

  val structuredRules =
    remember(config.filterRulesText) { FilterRuleEngine.parseStructuredRules(config.filterRulesText) }

  val autoExpandPackages =
    filterState.searchQuery.isNotBlank() ||
      filterState.onlySelected ||
      filterState.onlyMatchingFilterRules
  LaunchedEffect(autoExpandPackages) {
    if (!autoExpandPackages) {
      searchCollapsedPackages.clear()
    }
  }

  val defaultPackageLabel = stringResource(R.string.label_default_package)
  val packageGroups =
    remember(classes, selectedMethodIds, filterState.sortOrder, defaultPackageLabel) {
      buildNestedPackageTree(
        classes = classes,
        selectedMethodIds = selectedMethodIds,
        sortOrder = filterState.sortOrder,
        defaultPackageLabel = defaultPackageLabel,
      )
    }

  val totalDistinctPackages =
    remember(classes, defaultPackageLabel) {
      classes.map { it.packageName.ifBlank { defaultPackageLabel } }.distinct().size
    }

  val flatClasses =
    remember(classes, selectedMethodIds, filterState.sortOrder) {
      sortClassesForDisplay(classes, filterState.sortOrder, selectedMethodIds)
    }

  val flatCap = 500
  val visibleRows =
    remember(
      viewMode,
      packageGroups,
      flatClasses,
      autoExpandPackages,
      manualExpandedPackages.toMap(),
      searchCollapsedPackages.toMap(),
    ) {
      buildList {
        fun isGroupExpanded(group: PackageGroup): Boolean {
          if (autoExpandPackages) {
            return searchCollapsedPackages[group.packageName] != true
          }
          val explicit = manualExpandedPackages[group.packageName]
          if (explicit != null) return explicit
          return group.depth == 0 && group.directClasses.isEmpty() && group.children.size == 1
        }

        fun appendPackageGroup(group: PackageGroup) {
          val pkgExpanded = isGroupExpanded(group)
          add(ExplorerRowItem.PackageRow(group = group, expanded = pkgExpanded))
          if (pkgExpanded) {
            group.children.forEach { childGroup -> appendPackageGroup(childGroup) }
            group.directClasses.forEach { dexClass ->
              add(
                ExplorerRowItem.ClassRow(
                  dexClass = dexClass,
                  inTreeMode = true,
                  depth = group.depth + 1,
                )
              )
            }
          }
        }

        if (viewMode == ClassesViewMode.TREE) {
          packageGroups.forEach { group -> appendPackageGroup(group) }
        } else {
          flatClasses.take(flatCap).forEach { dexClass ->
            add(ExplorerRowItem.ClassRow(dexClass = dexClass, inTreeMode = false))
          }
        }
      }
    }

  val hasAnyExpandedPackage =
    viewMode == ClassesViewMode.TREE &&
      visibleRows.any { it is ExplorerRowItem.PackageRow && it.expanded }

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(modifier = Modifier.fillMaxSize().widthIn(max = 840.dp)) {
      Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
        // Top Sub-Tab Strip: EXPLORER | FILTER RULES
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          Column(
            modifier =
              Modifier
                .clickable { activeSubTab = ClassesSubTab.EXPLORER }
                .padding(vertical = 4.dp)
                .testTag("classes_subtab_explorer"),
          ) {
            Text(
              text = "EXPLORER",
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold,
              color =
                if (activeSubTab == ClassesSubTab.EXPLORER) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Box(
              modifier =
                Modifier.width(68.dp)
                  .height(2.dp)
                  .background(
                    if (activeSubTab == ClassesSubTab.EXPLORER) MaterialTheme.colorScheme.primary
                    else Color.Transparent,
                    RoundedCornerShape(1.dp),
                  )
            )
          }

          Column(
            modifier =
              Modifier
                .clickable { activeSubTab = ClassesSubTab.FILTER_RULES }
                .padding(vertical = 4.dp)
                .testTag("classes_subtab_rules"),
          ) {
            Text(
              text = "FILTER RULES (${structuredRules.size})",
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold,
              color =
                if (activeSubTab == ClassesSubTab.FILTER_RULES) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Box(
              modifier =
                Modifier.width(108.dp)
                  .height(2.dp)
                  .background(
                    if (activeSubTab == ClassesSubTab.FILTER_RULES) MaterialTheme.colorScheme.primary
                    else Color.Transparent,
                    RoundedCornerShape(1.dp),
                  )
            )
          }

          Spacer(Modifier.weight(1f))

          if (activeSubTab == ClassesSubTab.EXPLORER && loadState is DexLoadState.Ready && classes.isNotEmpty()) {
            val summaryText =
              if (viewMode == ClassesViewMode.TREE) {
                "$totalDistinctPackages pkgs • ${classes.size} cls"
              } else {
                "${classes.size} cls"
              }
            Text(
              text = summaryText,
              style = MaterialTheme.typography.labelSmall,
              fontFamily = JetBrainsMonoFontFamily,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            if (hasAnyExpandedPackage) {
              TextButton(
                onClick = {
                  packageGroups.forEach { top -> manualExpandedPackages[top.packageName] = false }
                  manualExpandedPackages.keys.toList().forEach { key ->
                    manualExpandedPackages[key] = false
                  }
                },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                modifier =
                  Modifier.defaultMinSize(minHeight = 30.dp).testTag("classes_collapse_all_btn"),
              ) {
                Icon(
                  imageVector = Icons.Rounded.UnfoldLess,
                  contentDescription = null,
                  modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(2.dp))
                Text("Collapse", style = MaterialTheme.typography.labelSmall)
              }
            }
          }
        }

        if (activeSubTab == ClassesSubTab.EXPLORER) {
          OutlinedTextField(
            value = filterState.searchQuery,
            onValueChange = onSearchChange,
            modifier = Modifier.fillMaxWidth().testTag("class_search_input"),
            placeholder = {
              Text(
                text = stringResource(R.string.label_search_classes),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            },
            leadingIcon = {
              Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp))
            },
            trailingIcon = {
              if (filterState.searchQuery.isNotEmpty()) {
                IconButton(onClick = { onSearchChange("") }) {
                  Icon(
                    Icons.Rounded.Clear,
                    contentDescription = "Clear search",
                    modifier = Modifier.size(18.dp),
                  )
                }
              }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
          )
          Spacer(Modifier.height(6.dp))
          LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
              val isTree = viewMode == ClassesViewMode.TREE
              FilterChip(
                selected = true,
                onClick = { viewMode = if (isTree) ClassesViewMode.FLAT else ClassesViewMode.TREE },
                leadingIcon = {
                  Icon(
                    imageVector =
                      if (isTree) Icons.Rounded.AccountTree else Icons.AutoMirrored.Rounded.ViewList,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                  )
                },
                label = {
                  Text(stringResource(if (isTree) R.string.chip_view_tree else R.string.chip_view_flat))
                },
                modifier = Modifier.testTag("chip_view_mode_toggle"),
              )
            }
            item {
              FilterChip(
                selected = showSelectionCheckboxes,
                onClick = { showSelectionCheckboxes = !showSelectionCheckboxes },
                leadingIcon = {
                  Icon(
                    imageVector = Icons.Rounded.CheckBox,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                  )
                },
                label = {
                  Text(
                    if (selectedMethodIds.isNotEmpty()) "Select (${selectedMethodIds.size})"
                    else "Select"
                  )
                },
                modifier = Modifier.testTag("chip_select_mode"),
              )
            }
            item {
              FilterChip(
                selected = filterState.onlyMatchingFilterRules,
                onClick = onToggleOnlyMatchingFilterRules,
                leadingIcon = {
                  Icon(
                    imageVector = Icons.Rounded.FilterAlt,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                  )
                },
                label = {
                  Text(
                    if (structuredRules.isNotEmpty()) "Match Rules (${structuredRules.size})"
                    else "Match Rules"
                  )
                },
                modifier = Modifier.testTag("chip_match_filter_rules"),
              )
            }
            item {
              Box {
                FilterChip(
                  selected = filterState.sortOrder != ClassSortOrder.NAME_ASC,
                  onClick = { showSortMenu = true },
                  leadingIcon = {
                    Icon(
                      imageVector = Icons.AutoMirrored.Rounded.Sort,
                      contentDescription = null,
                      modifier = Modifier.size(16.dp),
                    )
                  },
                  label = { Text("Sort: ${filterState.sortOrder.label}") },
                  modifier = Modifier.testTag("chip_class_sort"),
                )
                DropdownMenu(
                  expanded = showSortMenu,
                  onDismissRequest = { showSortMenu = false },
                ) {
                  ClassSortOrder.entries.forEach { order ->
                    DropdownMenuItem(
                      text = { Text(order.label) },
                      trailingIcon = {
                        if (filterState.sortOrder == order) {
                          Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                          )
                        }
                      },
                      onClick = {
                        onSortOrderChange(order)
                        showSortMenu = false
                      },
                      modifier = Modifier.testTag("sort_option_${order.name}"),
                    )
                  }
                }
              }
            }
            val loadedDexNames =
              (loadState as? DexLoadState.Ready)?.loadedEntryNames ?: emptyList()
            if (loadedDexNames.size > 1) {
              item {
                val activeDex = filterState.dexSourceFilter?.takeIf { it in loadedDexNames }
                Box {
                  FilterChip(
                    selected = activeDex != null,
                    onClick = { showDexMenu = true },
                    label = {
                      Text(
                        if (activeDex == null) "DEX: All (${loadedDexNames.size})"
                        else "DEX: $activeDex"
                      )
                    },
                    modifier = Modifier.testTag("chip_dex_source"),
                  )
                  DropdownMenu(
                    expanded = showDexMenu,
                    onDismissRequest = { showDexMenu = false },
                  ) {
                    DropdownMenuItem(
                      text = { Text("All Loaded DEXes (${loadedDexNames.size})") },
                      trailingIcon = {
                        if (activeDex == null) {
                          Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                          )
                        }
                      },
                      onClick = {
                        onDexSourceFilterChange(null)
                        showDexMenu = false
                      },
                      modifier = Modifier.testTag("dex_source_all"),
                    )
                    loadedDexNames.forEach { entryName ->
                      DropdownMenuItem(
                        text = { Text(entryName, fontFamily = JetBrainsMonoFontFamily) },
                        trailingIcon = {
                          if (activeDex == entryName) {
                            Icon(
                              imageVector = Icons.Rounded.Check,
                              contentDescription = null,
                              modifier = Modifier.size(16.dp),
                            )
                          }
                        },
                        onClick = {
                          onDexSourceFilterChange(entryName)
                          showDexMenu = false
                        },
                        modifier = Modifier.testTag("dex_source_$entryName"),
                      )
                    }
                  }
                }
              }
            }
            item {
              val activeCount = filterState.extraFilterCount
              FilterChip(
                selected = showExtraFilters || activeCount > 0,
                onClick = { showExtraFilters = !showExtraFilters },
                leadingIcon = {
                  Icon(
                    imageVector = Icons.Rounded.Tune,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                  )
                },
                label = {
                  Text(if (activeCount > 0) "Filters ($activeCount)" else "Filters")
                },
                modifier = Modifier.testTag("chip_extra_filters"),
              )
            }
            item {
              FilterChip(
                selected = filterState.hideFramework,
                onClick = onToggleHideFramework,
                label = { Text(stringResource(R.string.chip_hide_framework)) },
                modifier = Modifier.testTag("chip_hide_framework"),
              )
            }
            item {
              FilterChip(
                selected = filterState.onlyWithBytecode,
                onClick = onToggleOnlyBytecode,
                label = { Text(stringResource(R.string.chip_has_bytecode)) },
                modifier = Modifier.testTag("chip_has_bytecode"),
              )
            }
            item {
              FilterChip(
                selected = filterState.onlySelected,
                onClick = onToggleOnlySelected,
                label = {
                  Text("${stringResource(R.string.chip_selected_only)} (${selectedMethodIds.size})")
                },
                modifier = Modifier.testTag("chip_selected_only"),
              )
            }
          }

          AnimatedVisibility(visible = showExtraFilters) {
            Surface(
              modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
              shape = RoundedCornerShape(12.dp),
              color = MaterialTheme.colorScheme.surfaceContainer,
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
            ) {
              Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
              ) {
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
      }

      if (activeSubTab == ClassesSubTab.FILTER_RULES) {
        Surface(
          modifier = Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
          shape = RoundedCornerShape(16.dp),
          color = MaterialTheme.colorScheme.surfaceContainer,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        ) {
          InteractiveFilterRuleBuilderPanel(
            config = config,
            hasLoadedDex = loadState is DexLoadState.Ready,
            filterMatchSummary = filterMatchSummary,
            selectedMethodCount = selectedMethodIds.size,
            onUpdateConfig = onUpdateConfig,
            onSyncSelectionToWildcardRules = onSyncSelectionToWildcardRules,
            onApplyFilterRules = onApplyFilterRules,
            fillAvailableHeight = true,
            showModuleNameField = false,
            onlyMatchingExplorerFilter = filterState.onlyMatchingFilterRules,
            onToggleMatchingExplorerFilter = onToggleOnlyMatchingFilterRules,
            modifier = Modifier.fillMaxSize().padding(14.dp),
          )
        }
      } else if (loadState !is DexLoadState.Ready) {
        Box(
          modifier = Modifier.fillMaxSize().padding(24.dp),
          contentAlignment = Alignment.Center,
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
              text = "Load a DEX or APK on Home first",
              style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(10.dp))
            Button(
              onClick = onInspectSelfApk,
              shape = RoundedCornerShape(20.dp),
              modifier = Modifier.testTag("classes_inspect_apk_btn"),
            ) {
              Text(stringResource(R.string.action_inspect_self_apk))
            }
          }
        }
      } else if (classes.isEmpty()) {
        Box(
          modifier = Modifier.fillMaxSize().padding(24.dp),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text =
              "No classes match the active search or filters. Adjust filters or turn off \"App Only\".",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      } else {
        Surface(
          modifier = Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
          shape = RoundedCornerShape(16.dp),
          color = MaterialTheme.colorScheme.surfaceContainerLow,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        ) {
          LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("classes_explorer_list"),
            contentPadding = PaddingValues(vertical = 4.dp),
          ) {
            itemsIndexed(visibleRows, key = { _, item -> item.key }) { _, item ->
              when (item) {
                is ExplorerRowItem.PackageRow -> {
                  PackageFolderRow(
                    group = item.group,
                    expanded = item.expanded,
                    showSelectionCheckboxes = showSelectionCheckboxes,
                    selectedMethodIds = selectedMethodIds,
                    methodScopeFilter = filterState.methodScopeFilter,
                    allowInit = allowInit,
                    onToggleExpand = {
                      if (autoExpandPackages) {
                        searchCollapsedPackages[item.group.packageName] = item.expanded
                      } else {
                        manualExpandedPackages[item.group.packageName] = !item.expanded
                      }
                    },
                    onTogglePackageSelect = { onTogglePackageSelection(item.group.subtreeClasses) },
                    onWhitelistPackage = { onQuickPackageRule(item.group.packageName, false) },
                    onBlacklistPackage = { onQuickPackageRule(item.group.packageName, true) },
                  )
                }

                is ExplorerRowItem.ClassRow -> {
                  val isMultiDex = (loadState as? DexLoadState.Ready)?.loadedDexes?.size ?: 0 > 1
                  CompactClassRow(
                    dexClass = item.dexClass,
                    inTreeMode = item.inTreeMode,
                    depth = item.depth,
                    showDexOrigin = isMultiDex,
                    showSelectionCheckboxes = showSelectionCheckboxes,
                    selectedMethodIds = selectedMethodIds,
                    methodScopeFilter = filterState.methodScopeFilter,
                    allowInit = allowInit,
                    onToggleClassSelect = { onToggleClassSelection(item.dexClass) },
                    onWhitelistClass = { onQuickClassRule(item.dexClass, false) },
                    onBlacklistClass = { onQuickClassRule(item.dexClass, true) },
                    onOpenSmali = { onInspectClassSmali(item.dexClass) },
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

private fun sortClassesForDisplay(
  classes: List<DexClassInfo>,
  sortOrder: ClassSortOrder,
  selectedMethodIds: Set<String>,
): List<DexClassInfo> =
  when (sortOrder) {
    ClassSortOrder.NAME_ASC ->
      classes.sortedWith(
        compareBy({ it.simpleName.lowercase() }, { it.packageName.lowercase() }, { it.classIndex })
      )
    ClassSortOrder.NAME_DESC ->
      classes.sortedWith(
        compareByDescending<DexClassInfo> { it.simpleName.lowercase() }
          .thenBy { it.packageName.lowercase() }
          .thenBy { it.classIndex }
      )
    ClassSortOrder.BYTECODE_DESC ->
      classes.sortedWith(
        compareByDescending<DexClassInfo> { it.bytecodeMethodCount }
          .thenBy { it.simpleName.lowercase() }
          .thenBy { it.classIndex }
      )
    ClassSortOrder.SELECTED_FIRST ->
      classes.sortedWith(
        compareByDescending<DexClassInfo> { cls ->
            cls.allMethods.count { it.id in selectedMethodIds }
          }
          .thenByDescending { it.bytecodeMethodCount }
          .thenBy { it.simpleName.lowercase() }
          .thenBy { it.classIndex }
      )
    ClassSortOrder.DEX_ORDER -> classes
  }

@Composable
private fun PackageFolderRow(
  group: PackageGroup,
  expanded: Boolean,
  showSelectionCheckboxes: Boolean,
  selectedMethodIds: Set<String>,
  methodScopeFilter: MethodScopeFilter,
  allowInit: Boolean,
  onToggleExpand: () -> Unit,
  onTogglePackageSelect: () -> Unit,
  onWhitelistPackage: () -> Unit = {},
  onBlacklistPackage: () -> Unit = {},
) {
  val targetIds =
    remember(group, allowInit, methodScopeFilter) {
      val scoped =
        group.subtreeClasses.flatMap { cls ->
          cls.allMethods.filter { matchesMethodScope(it, methodScopeFilter) }
        }
      val bytecodeIds =
        scoped.filter { it.hasBytecode && (allowInit || !it.isInitConstructor) }.map { it.id }
      bytecodeIds.ifEmpty { scoped.map { it.id } }
    }
  val selectedCount = targetIds.count { it in selectedMethodIds }
  val toggleState =
    when {
      targetIds.isEmpty() || selectedCount == 0 -> ToggleableState.Off
      selectedCount >= targetIds.size -> ToggleableState.On
      else -> ToggleableState.Indeterminate
    }

  val startPad = (10 + group.depth * 20).dp

  Row(
    modifier =
      Modifier.fillMaxWidth()
        .defaultMinSize(minHeight = 48.dp)
        .clickable(onClick = onToggleExpand)
        .padding(start = startPad, end = 12.dp)
        .testTag("pkg_row_${group.packageName}"),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (expanded) {
      Text(
        text = "┘",
        style = MaterialTheme.typography.labelMedium,
        fontFamily = JetBrainsMonoFontFamily,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(end = 6.dp),
      )
    } else {
      Icon(
        imageVector = Icons.Rounded.ChevronRight,
        contentDescription = "Expand package",
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(16.dp).padding(end = 2.dp),
      )
    }
    if (showSelectionCheckboxes) {
      TriStateCheckbox(
        state = toggleState,
        onClick = onTogglePackageSelect,
        modifier =
          Modifier.minimumInteractiveComponentSize().testTag("pkg_checkbox_${group.packageName}"),
      )
    }
    Icon(
      imageVector = if (expanded) Icons.Rounded.FolderOpen else Icons.Rounded.Folder,
      contentDescription = null,
      tint =
        if (expanded || selectedCount > 0) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.size(20.dp),
    )
    Spacer(Modifier.width(8.dp))
    Text(
      text = group.displayLabel.ifBlank { group.packageName },
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = FontWeight.Medium,
      color = MaterialTheme.colorScheme.onSurface,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f),
    )
    if (selectedCount > 0) {
      Spacer(Modifier.width(6.dp))
      InlineBadge(
        text = "${selectedCount}q",
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
      )
    }
    if (showSelectionCheckboxes) {
      Spacer(Modifier.width(4.dp))
      QuickRuleBadgeButton(
        text = "+W",
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        onClick = onWhitelistPackage,
        testTag = "pkg_whitelist_btn_${group.packageName}",
      )
      Spacer(Modifier.width(4.dp))
      QuickRuleBadgeButton(
        text = "!B",
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        onClick = onBlacklistPackage,
        testTag = "pkg_blacklist_btn_${group.packageName}",
      )
      Spacer(Modifier.width(6.dp))
      Text(
        text = "${group.subtreeClasses.size}c",
        style = MaterialTheme.typography.labelSmall,
        fontFamily = JetBrainsMonoFontFamily,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Icon(
        imageVector = if (expanded) Icons.Rounded.ExpandMore else Icons.Rounded.ChevronRight,
        contentDescription = if (expanded) "Collapse package" else "Expand package",
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(18.dp),
      )
    }
  }
}

/**
 * Renders a class leaf row modeled after Dex Editor Plus (panel 18):
 * - Circular blue `(C)` badge (`(I)` for interface, `(E)` for enum) + class `simpleName`.
 * - Tapping anywhere on the row directly opens the full Class Smali screen (`onOpenSmali`),
 *   bypassing method child rows.
 */
@Composable
private fun CompactClassRow(
  dexClass: DexClassInfo,
  inTreeMode: Boolean,
  depth: Int,
  showDexOrigin: Boolean,
  showSelectionCheckboxes: Boolean,
  selectedMethodIds: Set<String>,
  methodScopeFilter: MethodScopeFilter,
  allowInit: Boolean,
  onToggleClassSelect: () -> Unit,
  onWhitelistClass: () -> Unit = {},
  onBlacklistClass: () -> Unit = {},
  onOpenSmali: () -> Unit,
) {
  val scopedMethods =
    remember(dexClass, methodScopeFilter) {
      dexClass.allMethods.filter { matchesMethodScope(it, methodScopeFilter) }
    }
  val targetIds =
    remember(scopedMethods, allowInit) {
      val bytecodeIds =
        scopedMethods.filter { it.hasBytecode && (allowInit || !it.isInitConstructor) }.map { it.id }
      bytecodeIds.ifEmpty { scopedMethods.map { it.id } }
    }
  val selectedCount = targetIds.count { it in selectedMethodIds }
  val toggleState =
    when {
      targetIds.isEmpty() || selectedCount == 0 -> ToggleableState.Off
      selectedCount >= targetIds.size -> ToggleableState.On
      else -> ToggleableState.Indeterminate
    }

  val classRowTag =
    if (dexClass.sourceEntryName == "classes.dex") "class_row_${dexClass.classIndex}"
    else "class_row_${dexClass.sourceEntryName}_${dexClass.classIndex}"
  val classCheckboxTag =
    if (dexClass.sourceEntryName == "classes.dex") "class_checkbox_${dexClass.classIndex}"
    else "class_checkbox_${dexClass.sourceEntryName}_${dexClass.classIndex}"
  val classSmaliTag =
    if (dexClass.sourceEntryName == "classes.dex") "class_smali_btn_${dexClass.classIndex}"
    else "class_smali_btn_${dexClass.sourceEntryName}_${dexClass.classIndex}"
  val classWhitelistTag =
    if (dexClass.sourceEntryName == "classes.dex") "class_whitelist_btn_${dexClass.classIndex}"
    else "class_whitelist_btn_${dexClass.sourceEntryName}_${dexClass.classIndex}"
  val classBlacklistTag =
    if (dexClass.sourceEntryName == "classes.dex") "class_blacklist_btn_${dexClass.classIndex}"
    else "class_blacklist_btn_${dexClass.sourceEntryName}_${dexClass.classIndex}"

  val startPad = if (inTreeMode) (16 + depth * 20).dp else 12.dp
  val isInterface = (dexClass.accessFlags and 0x0200) != 0
  val isEnum = (dexClass.accessFlags and 0x4000) != 0
  val badgeLetter =
    when {
      isInterface -> "I"
      isEnum -> "E"
      else -> "C"
    }

  Row(
    modifier =
      Modifier.fillMaxWidth()
        .defaultMinSize(minHeight = 48.dp)
        .background(
          if (selectedCount > 0) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.14f)
          else Color.Transparent
        )
        .clickable(onClick = onOpenSmali)
        .padding(start = startPad, end = 12.dp, top = 4.dp, bottom = 4.dp)
        .testTag(classRowTag),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (showSelectionCheckboxes) {
      TriStateCheckbox(
        state = toggleState,
        onClick = onToggleClassSelect,
        modifier = Modifier.minimumInteractiveComponentSize().testTag(classCheckboxTag),
      )
    }
    Surface(
      shape = CircleShape,
      color =
        when {
          isInterface -> MaterialTheme.colorScheme.tertiary
          isEnum -> MaterialTheme.colorScheme.secondary
          else -> Color(0xFF4A76C5)
        },
      contentColor = Color.White,
      modifier = Modifier.size(24.dp),
    ) {
      Box(contentAlignment = Alignment.Center) {
        Text(
          text = badgeLetter,
          style = MaterialTheme.typography.labelSmall,
          fontFamily = JetBrainsMonoFontFamily,
          fontWeight = FontWeight.Bold,
          fontSize = 12.sp,
        )
      }
    }
    Spacer(Modifier.width(12.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = dexClass.simpleName,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Normal,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      if (!inTreeMode && dexClass.packageName.isNotBlank()) {
        Text(
          text = dexClass.packageName,
          style = MaterialTheme.typography.labelSmall,
          fontFamily = JetBrainsMonoFontFamily,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
    if (showDexOrigin) {
      Spacer(Modifier.width(4.dp))
      InlineBadge(
        text = dexClass.sourceEntryName.substringAfterLast('/').removeSuffix(".dex"),
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
      )
    }
    if (selectedCount > 0) {
      Spacer(Modifier.width(4.dp))
      InlineBadge(
        text = "${selectedCount}q",
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
      )
    }
    if (showSelectionCheckboxes) {
      Spacer(Modifier.width(4.dp))
      QuickRuleBadgeButton(
        text = "+W",
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        onClick = onWhitelistClass,
        testTag = classWhitelistTag,
      )
      Spacer(Modifier.width(4.dp))
      QuickRuleBadgeButton(
        text = "!B",
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        onClick = onBlacklistClass,
        testTag = classBlacklistTag,
      )
    }
    IconButton(
      onClick = onOpenSmali,
      modifier = Modifier.minimumInteractiveComponentSize().testTag(classSmaliTag),
    ) {
      Icon(
        imageVector = Icons.Rounded.Code,
        contentDescription = "Open Smali for ${dexClass.simpleName}",
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier.size(16.dp),
      )
    }
  }
}

@Composable
private fun QuickRuleBadgeButton(
  text: String,
  containerColor: Color,
  contentColor: Color,
  onClick: () -> Unit,
  testTag: String,
) {
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(6.dp),
    color = containerColor,
    contentColor = contentColor,
    modifier = Modifier.testTag(testTag),
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelSmall,
      fontFamily = JetBrainsMonoFontFamily,
      fontSize = 10.sp,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
    )
  }
}

@Composable
private fun InlineBadge(
  text: String,
  containerColor: Color,
  contentColor: Color,
) {
  Surface(
    shape = RoundedCornerShape(6.dp),
    color = containerColor,
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelSmall,
      fontFamily = JetBrainsMonoFontFamily,
      fontSize = 10.sp,
      fontWeight = FontWeight.Medium,
      color = contentColor,
      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
    )
  }
}
