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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.UnfoldLess
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import com.example.dex.formatAccessFlags
import com.example.ui.ClassFilterState
import com.example.ui.ClassSortOrder
import com.example.ui.ClassTypeFilter
import com.example.ui.DexLoadState
import com.example.ui.MethodScopeFilter
import com.example.ui.matchesMethodScope
import com.example.ui.theme.JetBrainsMonoFontFamily

enum class ClassesViewMode {
  TREE,
  FLAT,
}

private data class PackageGroup(
  val packageName: String,
  val classes: List<DexClassInfo>,
  val totalBytecodeMethods: Int,
  val selectedMethodCount: Int,
)

private sealed interface ExplorerRowItem {
  val key: String

  data class PackageRow(val group: PackageGroup, val expanded: Boolean) : ExplorerRowItem {
    override val key: String = "pkg:${group.packageName}"
  }

  data class ClassRow(
    val dexClass: DexClassInfo,
    val expanded: Boolean,
    val inTreeMode: Boolean,
  ) : ExplorerRowItem {
    override val key: String = "cls:${dexClass.uniqueKey}"
  }

  data class MethodRow(
    val dexClass: DexClassInfo,
    val method: DexEncodedMethod,
    val inTreeMode: Boolean,
  ) : ExplorerRowItem {
    override val key: String = "mtd:${method.id}"
  }
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
  onToggleMethodSelection: (String) -> Unit,
  onInspectMethod: (DexClassInfo, DexEncodedMethod) -> Unit,
  onInspectSelfApk: () -> Unit,
  modifier: Modifier = Modifier,
  onSortOrderChange: (ClassSortOrder) -> Unit = {},
  onClassTypeFilterChange: (ClassTypeFilter) -> Unit = {},
  onToggleHideInnerClasses: () -> Unit = {},
  onMethodScopeFilterChange: (MethodScopeFilter) -> Unit = {},
  onToggleNonEmptyPackages: () -> Unit = {},
  onResetExtraFilters: () -> Unit = {},
  onTogglePackageSelection: (List<DexClassInfo>) -> Unit = {},
  onDexSourceFilterChange: (String?) -> Unit = {},
) {
  var viewMode by remember { mutableStateOf(ClassesViewMode.TREE) }
  var showSortMenu by remember { mutableStateOf(false) }
  var showDexMenu by remember { mutableStateOf(false) }
  var showExtraFilters by remember { mutableStateOf(false) }
  val manualExpandedPackages = remember { mutableStateMapOf<String, Boolean>() }
  val searchCollapsedPackages = remember { mutableStateMapOf<String, Boolean>() }
  val expandedClasses = remember { mutableStateMapOf<String, Boolean>() }

  val autoExpandPackages = filterState.searchQuery.isNotBlank() || filterState.onlySelected
  LaunchedEffect(autoExpandPackages) {
    if (!autoExpandPackages) {
      searchCollapsedPackages.clear()
    }
  }

  val defaultPackageLabel = stringResource(R.string.label_default_package)
  val packageGroups =
    remember(classes, selectedMethodIds, filterState.sortOrder, defaultPackageLabel) {
      val grouped =
        classes
          .groupBy { it.packageName.ifBlank { defaultPackageLabel } }
          .entries
          .map { (pkg, pkgClasses) ->
            val sortedClasses = sortClassesForDisplay(pkgClasses, filterState.sortOrder, selectedMethodIds)
            val selCount =
              sortedClasses.sumOf { cls -> cls.allMethods.count { it.id in selectedMethodIds } }
            PackageGroup(
              packageName = pkg,
              classes = sortedClasses,
              totalBytecodeMethods = sortedClasses.sumOf { it.bytecodeMethodCount },
              selectedMethodCount = selCount,
            )
          }
      when (filterState.sortOrder) {
        ClassSortOrder.NAME_ASC -> grouped.sortedBy { it.packageName.lowercase() }
        ClassSortOrder.NAME_DESC -> grouped.sortedByDescending { it.packageName.lowercase() }
        ClassSortOrder.BYTECODE_DESC ->
          grouped.sortedWith(
            compareByDescending<PackageGroup> { it.totalBytecodeMethods }.thenBy { it.packageName.lowercase() }
          )
        ClassSortOrder.SELECTED_FIRST ->
          grouped.sortedWith(
            compareByDescending<PackageGroup> { it.selectedMethodCount > 0 }
              .thenByDescending { it.selectedMethodCount }
              .thenByDescending { it.totalBytecodeMethods }
              .thenBy { it.packageName.lowercase() }
          )
        ClassSortOrder.DEX_ORDER -> grouped
      }
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
      filterState.methodScopeFilter,
      autoExpandPackages,
      manualExpandedPackages.toMap(),
      searchCollapsedPackages.toMap(),
      expandedClasses.toMap(),
    ) {
      buildList {
        if (viewMode == ClassesViewMode.TREE) {
          packageGroups.forEach { group ->
            val pkgExpanded =
              if (autoExpandPackages) {
                searchCollapsedPackages[group.packageName] != true
              } else {
                manualExpandedPackages[group.packageName] == true
              }
            add(ExplorerRowItem.PackageRow(group = group, expanded = pkgExpanded))
            if (pkgExpanded) {
              group.classes.forEach { dexClass ->
                val clsExpanded = expandedClasses[dexClass.uniqueKey] == true
                add(ExplorerRowItem.ClassRow(dexClass = dexClass, expanded = clsExpanded, inTreeMode = true))
                if (clsExpanded) {
                  dexClass.allMethods
                    .filter { matchesMethodScope(it, filterState.methodScopeFilter) }
                    .forEach { method ->
                      add(ExplorerRowItem.MethodRow(dexClass = dexClass, method = method, inTreeMode = true))
                    }
                }
              }
            }
          }
        } else {
          flatClasses.take(flatCap).forEach { dexClass ->
            val clsExpanded = expandedClasses[dexClass.uniqueKey] == true
            add(ExplorerRowItem.ClassRow(dexClass = dexClass, expanded = clsExpanded, inTreeMode = false))
            if (clsExpanded) {
              dexClass.allMethods
                .filter { matchesMethodScope(it, filterState.methodScopeFilter) }
                .forEach { method ->
                  add(ExplorerRowItem.MethodRow(dexClass = dexClass, method = method, inTreeMode = false))
                }
            }
          }
        }
      }
    }

  val hasAnyExpandedPackage =
    viewMode == ClassesViewMode.TREE &&
      (manualExpandedPackages.values.any { it } || expandedClasses.values.any { it })

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(modifier = Modifier.fillMaxSize().widthIn(max = 840.dp)) {
      Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
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
          leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
          trailingIcon = {
            if (filterState.searchQuery.isNotEmpty()) {
              IconButton(onClick = { onSearchChange("") }) {
                Icon(Icons.Rounded.Clear, contentDescription = "Clear search", modifier = Modifier.size(18.dp))
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
                  imageVector = if (isTree) Icons.Rounded.AccountTree else Icons.AutoMirrored.Rounded.ViewList,
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
              label = { Text("${stringResource(R.string.chip_selected_only)} (${selectedMethodIds.size})") },
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
                    modifier = Modifier.defaultMinSize(minHeight = 32.dp).testTag("filter_reset_extra_btn"),
                  ) {
                    Text("Reset", style = MaterialTheme.typography.labelSmall)
                  }
                }
              }
            }
          }
        }

        if (loadState is DexLoadState.Ready && classes.isNotEmpty()) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            val summaryText =
              if (viewMode == ClassesViewMode.TREE) {
                "${packageGroups.size} pkgs • ${classes.size} cls • ${filterState.sortOrder.label}"
              } else if (classes.size > flatCap) {
                "Top $flatCap of ${classes.size} cls • ${filterState.sortOrder.label}"
              } else {
                "${classes.size} cls • ${filterState.sortOrder.label}"
              }
            Text(
              text = summaryText,
              style = MaterialTheme.typography.labelSmall,
              fontFamily = JetBrainsMonoFontFamily,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f),
            )
            if (hasAnyExpandedPackage) {
              TextButton(
                onClick = {
                  manualExpandedPackages.clear()
                  expandedClasses.clear()
                },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier.defaultMinSize(minHeight = 32.dp).testTag("classes_collapse_all_btn"),
              ) {
                Icon(
                  imageVector = Icons.Rounded.UnfoldLess,
                  contentDescription = null,
                  modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text("Collapse", style = MaterialTheme.typography.labelSmall)
              }
            }
          }
        }
      }

      if (loadState !is DexLoadState.Ready) {
        Box(
          modifier = Modifier.fillMaxSize().padding(24.dp),
          contentAlignment = Alignment.Center,
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
              text = "Load a DEX or APK on the Workbench first",
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
            text = "No classes match the active search or filters. Adjust filters or turn off \"App Only\".",
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
            contentPadding = PaddingValues(vertical = 2.dp),
          ) {
            itemsIndexed(visibleRows, key = { _, item -> item.key }) { idx, item ->
              when (item) {
                is ExplorerRowItem.PackageRow -> {
                  PackageFolderRow(
                    group = item.group,
                    expanded = item.expanded,
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
                    onTogglePackageSelect = { onTogglePackageSelection(item.group.classes) },
                  )
                }

                is ExplorerRowItem.ClassRow -> {
                  val isMultiDex = (loadState as? DexLoadState.Ready)?.loadedDexes?.size ?: 0 > 1
                  CompactClassRow(
                    dexClass = item.dexClass,
                    expanded = item.expanded,
                    inTreeMode = item.inTreeMode,
                    showDexOrigin = isMultiDex,
                    selectedMethodIds = selectedMethodIds,
                    methodScopeFilter = filterState.methodScopeFilter,
                    allowInit = allowInit,
                    onToggleExpand = { expandedClasses[item.dexClass.uniqueKey] = !item.expanded },
                    onToggleClassSelect = { onToggleClassSelection(item.dexClass) },
                  )
                }

                is ExplorerRowItem.MethodRow -> {
                  CompactMethodRow(
                    method = item.method,
                    inTreeMode = item.inTreeMode,
                    selected = item.method.id in selectedMethodIds,
                    allowInit = allowInit,
                    onToggleSelect = { onToggleMethodSelection(item.method.id) },
                    onInspect = { onInspectMethod(item.dexClass, item.method) },
                  )
                }
              }
              if (idx < visibleRows.lastIndex) {
                HorizontalDivider(
                  thickness = 0.5.dp,
                  color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                )
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
      classes.sortedWith(compareBy({ it.simpleName.lowercase() }, { it.packageName.lowercase() }, { it.classIndex }))
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
        compareByDescending<DexClassInfo> { cls -> cls.allMethods.count { it.id in selectedMethodIds } }
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
  selectedMethodIds: Set<String>,
  methodScopeFilter: MethodScopeFilter,
  allowInit: Boolean,
  onToggleExpand: () -> Unit,
  onTogglePackageSelect: () -> Unit,
) {
  val targetIds =
    remember(group, allowInit, methodScopeFilter) {
      val scoped = group.classes.flatMap { cls -> cls.allMethods.filter { matchesMethodScope(it, methodScopeFilter) } }
      val bytecodeIds = scoped.filter { it.hasBytecode && (allowInit || !it.isInitConstructor) }.map { it.id }
      bytecodeIds.ifEmpty { scoped.map { it.id } }
    }
  val selectedCount = targetIds.count { it in selectedMethodIds }
  val toggleState =
    when {
      targetIds.isEmpty() || selectedCount == 0 -> ToggleableState.Off
      selectedCount >= targetIds.size -> ToggleableState.On
      else -> ToggleableState.Indeterminate
    }

  Row(
    modifier =
      Modifier.fillMaxWidth()
        .defaultMinSize(minHeight = 48.dp)
        .background(
          if (expanded) MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.7f)
          else Color.Transparent
        )
        .clickable(onClick = onToggleExpand)
        .padding(start = 2.dp, end = 10.dp)
        .testTag("pkg_row_${group.packageName}"),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    TriStateCheckbox(
      state = toggleState,
      onClick = onTogglePackageSelect,
      modifier = Modifier.minimumInteractiveComponentSize().testTag("pkg_checkbox_${group.packageName}"),
    )
    Icon(
      imageVector = if (expanded) Icons.Rounded.FolderOpen else Icons.Rounded.Folder,
      contentDescription = null,
      tint =
        if (expanded || selectedCount > 0) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.size(18.dp),
    )
    Spacer(Modifier.width(8.dp))
    Text(
      text = group.packageName,
      style = MaterialTheme.typography.labelLarge,
      fontFamily = JetBrainsMonoFontFamily,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.onSurface,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f),
    )
    Spacer(Modifier.width(6.dp))
    Text(
      text = "${group.classes.size}c • ${group.totalBytecodeMethods}m",
      style = MaterialTheme.typography.labelSmall,
      fontFamily = JetBrainsMonoFontFamily,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (selectedCount > 0) {
      Spacer(Modifier.width(4.dp))
      InlineBadge(
        text = "${selectedCount}q",
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
      )
    }
    Spacer(Modifier.width(4.dp))
    Icon(
      imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ChevronRight,
      contentDescription = if (expanded) "Collapse package" else "Expand package",
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.size(18.dp),
    )
  }
}

@Composable
private fun CompactClassRow(
  dexClass: DexClassInfo,
  expanded: Boolean,
  inTreeMode: Boolean,
  showDexOrigin: Boolean,
  selectedMethodIds: Set<String>,
  methodScopeFilter: MethodScopeFilter,
  allowInit: Boolean,
  onToggleExpand: () -> Unit,
  onToggleClassSelect: () -> Unit,
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

  Row(
    modifier =
      Modifier.fillMaxWidth()
        .defaultMinSize(minHeight = 48.dp)
        .background(
          if (selectedCount > 0) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.14f)
          else Color.Transparent
        )
        .clickable(onClick = onToggleExpand)
        .padding(start = if (inTreeMode) 14.dp else 2.dp, end = 10.dp)
        .testTag(classRowTag),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (inTreeMode) {
      Box(
        modifier =
          Modifier.width(1.dp)
            .height(28.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
      )
      Spacer(Modifier.width(2.dp))
    }
    TriStateCheckbox(
      state = toggleState,
      onClick = onToggleClassSelect,
      modifier = Modifier.minimumInteractiveComponentSize().testTag(classCheckboxTag),
    )
    Column(modifier = Modifier.weight(1f).padding(vertical = 4.dp)) {
      Text(
        text = dexClass.simpleName,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
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
    Spacer(Modifier.width(6.dp))
    val displayedBytecodeCount =
      if (methodScopeFilter == MethodScopeFilter.ALL) dexClass.bytecodeMethodCount
      else scopedMethods.count { it.hasBytecode }
    InlineBadge(
      text = "${displayedBytecodeCount}m",
      containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
      contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (selectedCount > 0) {
      Spacer(Modifier.width(4.dp))
      InlineBadge(
        text = "${selectedCount}q",
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
      )
    }
    Spacer(Modifier.width(4.dp))
    Icon(
      imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
      contentDescription = if (expanded) "Collapse class methods" else "Expand class methods",
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.size(18.dp),
    )
  }
}

@Composable
private fun CompactMethodRow(
  method: DexEncodedMethod,
  inTreeMode: Boolean,
  selected: Boolean,
  allowInit: Boolean,
  onToggleSelect: () -> Unit,
  onInspect: () -> Unit,
) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .defaultMinSize(minHeight = 48.dp)
        .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.45f))
        .clickable(onClick = onInspect)
        .padding(start = if (inTreeMode) 30.dp else 16.dp, end = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      modifier =
        Modifier.width(1.dp)
          .height(26.dp)
          .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    )
    Spacer(Modifier.width(2.dp))
    Checkbox(
      checked = selected,
      onCheckedChange = { onToggleSelect() },
      modifier = Modifier.minimumInteractiveComponentSize().testTag("method_checkbox_${method.id}"),
    )
    Column(modifier = Modifier.weight(1f).padding(vertical = 4.dp)) {
      Text(
        text = method.methodRef.prettySignature(),
        style = MaterialTheme.typography.labelMedium,
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      val meta = method.codeMeta
      val metaStr =
        if (meta != null) {
          "r=${meta.registersSize} • ${meta.insnsCount}w • ${formatAccessFlags(method.accessFlags, true)}"
        } else {
          "no code • ${formatAccessFlags(method.accessFlags, true)}"
        }
      Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = metaStr,
          style = MaterialTheme.typography.labelSmall,
          fontSize = 11.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f, fill = false),
        )
        if (method.isInitConstructor) {
          InlineBadge(
            text = if (allowInit) "<init>" else "skip",
            containerColor =
              if (allowInit) MaterialTheme.colorScheme.primaryContainer
              else MaterialTheme.colorScheme.secondaryContainer,
            contentColor =
              if (allowInit) MaterialTheme.colorScheme.onPrimaryContainer
              else MaterialTheme.colorScheme.onSecondaryContainer,
          )
        }
      }
    }
    Spacer(Modifier.width(4.dp))
    Icon(
      imageVector = Icons.Rounded.ChevronRight,
      contentDescription = "Inspect method",
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.size(16.dp),
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
