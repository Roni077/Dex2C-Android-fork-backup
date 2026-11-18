package com.dex2c.studio.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.UnfoldLess
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dex2c.studio.R
import com.dex2c.studio.dex.DexClassInfo
import com.dex2c.studio.dex.DexEncodedMethod
import com.dex2c.studio.dex.normalizePackageName
import com.dex2c.studio.transpiler.FilterMatchSummary
import com.dex2c.studio.transpiler.FilterRuleEngine
import com.dex2c.studio.transpiler.TranspilerConfig
import com.dex2c.studio.ui.ClassFilterState
import com.dex2c.studio.ui.ClassSortOrder
import com.dex2c.studio.ui.ClassTypeFilter
import com.dex2c.studio.ui.DexLoadState
import com.dex2c.studio.ui.MethodScopeFilter
import com.dex2c.studio.ui.components.ActiveFilterSummaryStrip
import com.dex2c.studio.ui.components.BatchActionBar
import com.dex2c.studio.ui.components.ClassesFilterSheet
import com.dex2c.studio.ui.components.CompactClassRow
import com.dex2c.studio.ui.components.DexFieldsExplorerTab
import com.dex2c.studio.ui.components.DexMethodsExplorerTab
import com.dex2c.studio.ui.components.DexStringsExplorerTab
import com.dex2c.studio.ui.components.PackageFolderRow
import com.dex2c.studio.ui.components.QuickRuleBadgeButton
import com.dex2c.studio.ui.components.SelectedClassesFilterDialog
import com.dex2c.studio.data.SmaliSyntaxTheme
import com.dex2c.studio.dex.formatAccessFlags
import com.dex2c.studio.ui.theme.LocalEditorSettings
import com.dex2c.studio.ui.SmaliClassUiState
import com.dex2c.studio.ui.components.ClassSelectionHeader
import com.dex2c.studio.ui.components.SmaliSoraEditor
import com.dex2c.studio.ui.components.TagPill
import com.dex2c.studio.ui.rememberWindowAdaptiveInfo
import com.dex2c.studio.ui.theme.JetBrainsMonoFontFamily
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.VerticalDivider
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.OpenInNew

enum class ClassesViewMode {
  TREE,
  FLAT,
}

enum class ClassesSubTab(val label: String, val tagSuffix: String) {
  EXPLORER("CLASSES", "explorer"),
  METHODS("METHODS", "methods"),
  FIELDS("FIELDS", "fields"),
  STRINGS("STRINGS", "strings"),
}

data class PackageGroup(
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
 * Builds a Dex Editor Plus style hierarchical package tree:
 * - Depth 0 and Depth 1 remain distinct expandable folder levels.
 * - Deeper subpackage chains compact single-child empty intermediate folders.
 */
internal fun buildNestedPackageTree(
  classes: List<DexClassInfo>,
  selectedMethodIds: Set<String>,
  sortOrder: ClassSortOrder,
  defaultPackageLabel: String,
  compactMiddlePackages: Boolean = true,
): List<PackageGroup> {
  if (classes.isEmpty()) return emptyList()

  class MutablePkgNode(
    val segment: String,
    val fullPath: String,
    val children: LinkedHashMap<String, MutablePkgNode> = linkedMapOf(),
    val directClasses: MutableList<DexClassInfo> = mutableListOf(),
  )

  val selectedCountByClassKey: Map<String, Int> =
    if (selectedMethodIds.isEmpty()) {
      emptyMap()
    } else {
      HashMap<String, Int>(classes.size).also { map ->
        for (cls in classes) {
          var cnt = 0
          for (m in cls.allMethods) {
            if (m.id in selectedMethodIds) cnt++
          }
          if (cnt > 0) map[cls.uniqueKey] = cnt
        }
      }
    }

  val root = MutablePkgNode("", "")
  for (cls in classes) {
    val pkg = normalizePackageName(cls.packageName, defaultPackageLabel)
    if (pkg == defaultPackageLabel || pkg == "(default)") {
      val defNode =
        root.children.getOrPut(defaultPackageLabel) {
          MutablePkgNode(defaultPackageLabel, defaultPackageLabel)
        }
      defNode.directClasses += cls
    } else {
      val parts = pkg.split('.').map { it.trim() }.filter { it.isNotEmpty() }
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
    if (compactMiddlePackages && depth >= 2) {
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
      sortClassesForDisplay(current.directClasses, sortOrder, selectedMethodIds, selectedCountByClassKey)

    var totalSubtreeSize = sortedDirectClasses.size
    var totalBytecode = 0
    var selectedCount = 0
    for (cls in sortedDirectClasses) {
      totalBytecode += cls.bytecodeMethodCount
      selectedCount += selectedCountByClassKey[cls.uniqueKey] ?: 0
    }
    for (child in sortedChildren) {
      totalSubtreeSize += child.subtreeClasses.size
      totalBytecode += child.totalBytecodeMethods
      selectedCount += child.selectedMethodCount
    }

    val allSubtreeClasses = ArrayList<DexClassInfo>(totalSubtreeSize)
    allSubtreeClasses.addAll(sortedDirectClasses)
    for (child in sortedChildren) {
      allSubtreeClasses.addAll(child.subtreeClasses)
    }

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

internal fun sortPackageGroups(
  groups: List<PackageGroup>,
  sortOrder: ClassSortOrder,
): List<PackageGroup> =
  when (sortOrder) {
    ClassSortOrder.NAME_ASC ->
      groups.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.packageName })
    ClassSortOrder.NAME_DESC ->
      groups.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.packageName })
    ClassSortOrder.BYTECODE_DESC ->
      groups.sortedWith(
        compareByDescending<PackageGroup> { it.totalBytecodeMethods }
          .thenBy(String.CASE_INSENSITIVE_ORDER) { it.packageName }
      )
    ClassSortOrder.SELECTED_FIRST ->
      groups.sortedWith(
        compareByDescending<PackageGroup> { it.selectedMethodCount > 0 }
          .thenByDescending { it.selectedMethodCount }
          .thenByDescending { it.totalBytecodeMethods }
          .thenBy(String.CASE_INSENSITIVE_ORDER) { it.packageName }
      )
    ClassSortOrder.DEX_ORDER -> groups
  }

internal fun sortClassesForDisplay(
  classes: List<DexClassInfo>,
  sortOrder: ClassSortOrder,
  selectedMethodIds: Set<String>,
  precomputedSelectedCounts: Map<String, Int>? = null,
): List<DexClassInfo> =
  when (sortOrder) {
    ClassSortOrder.NAME_ASC ->
      classes.sortedWith(
        compareBy<DexClassInfo, String>(String.CASE_INSENSITIVE_ORDER) { it.simpleName }
          .thenBy(String.CASE_INSENSITIVE_ORDER) { it.packageName }
          .thenBy { it.classIndex }
      )
    ClassSortOrder.NAME_DESC ->
      classes.sortedWith(
        compareByDescending<DexClassInfo, String>(String.CASE_INSENSITIVE_ORDER) { it.simpleName }
          .thenBy(String.CASE_INSENSITIVE_ORDER) { it.packageName }
          .thenBy { it.classIndex }
      )
    ClassSortOrder.BYTECODE_DESC ->
      classes.sortedWith(
        compareByDescending<DexClassInfo> { it.bytecodeMethodCount }
          .thenBy(String.CASE_INSENSITIVE_ORDER) { it.simpleName }
          .thenBy { it.classIndex }
      )
    ClassSortOrder.SELECTED_FIRST ->
      classes.sortedWith(
        compareByDescending<DexClassInfo> { cls ->
          precomputedSelectedCounts?.get(cls.uniqueKey)
            ?: if (selectedMethodIds.isEmpty()) 0 else cls.allMethods.count { it.id in selectedMethodIds }
        }
          .thenByDescending { it.bytecodeMethodCount }
          .thenBy(String.CASE_INSENSITIVE_ORDER) { it.simpleName }
          .thenBy { it.classIndex }
      )
    ClassSortOrder.DEX_ORDER -> classes
  }

/**
 * Redesigned Classes Screen for Dalvik DEX exploration and transpilation rule configuration.
 */
@Composable
fun ClassesScreen(
  loadState: DexLoadState,
  classes: List<DexClassInfo>,
  filterState: ClassFilterState,
  selectedMethodIds: Set<String>,
  allowInit: Boolean,
  onSearchChange: (String) -> Unit = {},
  onToggleHideFramework: () -> Unit = {},
  onToggleOnlyBytecode: () -> Unit = {},
  onToggleOnlySelected: () -> Unit = {},
  onToggleClassSelection: (DexClassInfo) -> Unit = {},
  onToggleMethodSelection: (String) -> Unit = {},
  onInspectMethod: (DexClassInfo, DexEncodedMethod) -> Unit = { _, _ -> },
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
  onReplaceStringInDex: (searchQuery: String, replacement: String, ignoreCase: Boolean) -> Unit = { _, _, _ -> },
  onCopyText: (String) -> Unit = {},
  onExportPatchedDex: () -> Unit = {},
  onExportPatchedApk: () -> Unit = {},
  onDeleteDexClass: (DexClassInfo) -> Unit = {},
  onSelectAllClasses: ((List<DexClassInfo>) -> Unit)? = null,
  onDeselectAllClasses: ((List<DexClassInfo>?) -> Unit)? = null,
  onInvertClassSelection: ((List<DexClassInfo>) -> Unit)? = null,
  smaliClassState: SmaliClassUiState? = null,
  smaliSyntaxTheme: SmaliSyntaxTheme? = null,
  onSelectClassInTwoPane: ((DexClassInfo) -> Unit)? = null,
) {
  var activeSubTab by rememberSaveable(initialSubTab) { mutableStateOf(initialSubTab) }
  var viewMode by rememberSaveable { mutableStateOf(ClassesViewMode.TREE) }
  var compactMiddlePackages by rememberSaveable { mutableStateOf(false) }
  var showSelectionCheckboxes by rememberSaveable { mutableStateOf(false) }
  var showSortMenu by remember { mutableStateOf(false) }
  var showDexMenu by remember { mutableStateOf(false) }
  var showExtraFilters by remember { mutableStateOf(false) }
  var showSelectedClassesDialog by remember { mutableStateOf(false) }
  val manualExpandedPackages = remember { mutableStateMapOf<String, Boolean>() }
  val searchCollapsedPackages = remember { mutableStateMapOf<String, Boolean>() }

  val selectedClassesList =
    remember(classes, selectedMethodIds) {
      classes.filter { cls -> cls.allMethods.any { it.id in selectedMethodIds } }
    }

  BackHandler(enabled = showSelectionCheckboxes) {
    showSelectionCheckboxes = false
  }

  val detectedApkPackage = (loadState as? DexLoadState.Ready)?.resolvedPackageName

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
    remember(classes, selectedMethodIds, filterState.sortOrder, defaultPackageLabel, compactMiddlePackages) {
      buildNestedPackageTree(
        classes = classes,
        selectedMethodIds = selectedMethodIds,
        sortOrder = filterState.sortOrder,
        defaultPackageLabel = defaultPackageLabel,
        compactMiddlePackages = compactMiddlePackages,
      )
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

  var pendingDeleteClass by remember { mutableStateOf<DexClassInfo?>(null) }

  // Delete Confirmation Dialog
  if (pendingDeleteClass != null) {
    val targetClass = pendingDeleteClass!!
    AlertDialog(
      onDismissRequest = { pendingDeleteClass = null },
      title = { Text("Delete class from DEX?") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = "Are you sure you want to permanently delete this class from ${targetClass.sourceEntryName.ifBlank { "active DEX" }}?",
            style = MaterialTheme.typography.bodyMedium,
          )
          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.padding(8.dp)) {
              Text(
                text = targetClass.fullName,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
              )
              Text(
                text = targetClass.descriptor,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = JetBrainsMonoFontFamily,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          Text(
            text = "Warning: References to this class from other classes will not be automatically rewritten.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            val toDelete = targetClass
            pendingDeleteClass = null
            onDeleteDexClass(toDelete)
          },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
          modifier = Modifier.testTag("delete_class_confirm_btn"),
        ) {
          Text("Delete", color = MaterialTheme.colorScheme.onError)
        }
      },
      dismissButton = {
        TextButton(
          onClick = { pendingDeleteClass = null },
          modifier = Modifier.testTag("delete_class_cancel_btn"),
        ) {
          Text("Cancel")
        }
      },
      modifier = Modifier.testTag("delete_class_dialog"),
    )
  }

  BoxWithConstraints(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    val screenWidth = maxWidth
    val screenHeight = maxHeight
    val adaptiveInfo = rememberWindowAdaptiveInfo(screenWidth, screenHeight)
    val isTwoPane = adaptiveInfo.isTwoPane

    val explorerMainContent: @Composable () -> Unit = {
      Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {

        // TOP SUB-TAB STRIP: CLASSES | METHODS | FIELDS | STRINGS | RULES + Summary Stats & Actions
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Row(
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
          ) {
            ClassesSubTab.entries.forEach { tab ->
              val isSelected = activeSubTab == tab
              val tabText = tab.label
              Column(
                modifier =
                  Modifier
                    .clickable { activeSubTab = tab }
                    .padding(vertical = 4.dp)
                    .testTag("classes_subtab_${tab.tagSuffix}"),
              ) {
                Text(
                  text = tabText,
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = FontWeight.Bold,
                  color =
                    if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                Box(
                  modifier =
                    Modifier.width(52.dp)
                      .height(2.dp)
                      .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else Color.Transparent,
                        RoundedCornerShape(1.dp),
                      )
                )
              }
            }
          }

          if (loadState is DexLoadState.Ready) {
            if (activeSubTab == ClassesSubTab.EXPLORER && classes.isNotEmpty()) {
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
        }

        // EXPLORER CONTROLS (Only visible on EXPLORER tab)
        if (activeSubTab == ClassesSubTab.EXPLORER) {

          // Search Field
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

          // Scrollable Control Toolbar
          Row(
            modifier =
              Modifier.fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
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

            val activeCount = filterState.extraFilterCount
            FilterChip(
              selected = showExtraFilters || activeCount > 0,
              onClick = { showExtraFilters = true },
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

            val loadedDexNames =
              (loadState as? DexLoadState.Ready)?.loadedEntryNames ?: emptyList()
            if (loadedDexNames.size > 1) {
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

          // Modal Bottom Sheet Filter UI
          ClassesFilterSheet(
            visible = showExtraFilters,
            filterState = filterState,
            onDismiss = { showExtraFilters = false },
            onClassTypeFilterChange = onClassTypeFilterChange,
            onToggleHideInnerClasses = onToggleHideInnerClasses,
            onToggleNonEmptyPackages = onToggleNonEmptyPackages,
            onMethodScopeFilterChange = onMethodScopeFilterChange,
            onResetExtraFilters = onResetExtraFilters,
            onToggleHideFramework = onToggleHideFramework,
            onToggleOnlyBytecode = onToggleOnlyBytecode,
            onToggleOnlySelected = onToggleOnlySelected,
            onToggleOnlyMatchingFilterRules = onToggleOnlyMatchingFilterRules,
            compactMiddlePackages = compactMiddlePackages,
            onToggleCompactPackages = { compactMiddlePackages = !compactMiddlePackages },
          )

          // Active Filter Summary Strip (shown when extra filters or quick filters are active)
          ActiveFilterSummaryStrip(
            filterState = filterState,
            onResetFilters = onResetExtraFilters,
            onClearClassType = { onClassTypeFilterChange(ClassTypeFilter.ALL) },
            onClearMethodScope = { onMethodScopeFilterChange(MethodScopeFilter.ALL) },
            onClearInnerClasses = onToggleHideInnerClasses,
            onClearNonEmpty = onToggleNonEmptyPackages,
            onClearMatchRules = onToggleOnlyMatchingFilterRules,
          )
        }
      }

      // SUB-TAB SCREENS
      if (loadState !is DexLoadState.Ready) {
        Box(
          modifier = Modifier.fillMaxSize().padding(24.dp),
          contentAlignment = Alignment.Center,
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.SearchOff,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
              modifier = Modifier.size(48.dp),
            )
            Text(
              text = "Load a DEX or APK on Home first",
              style = MaterialTheme.typography.titleMedium,
            )
          }
        }
      } else if (activeSubTab == ClassesSubTab.METHODS) {
        DexMethodsExplorerTab(
          classes = classes.ifEmpty { loadState.mergedClasses },
          selectedMethodIds = selectedMethodIds,
          onToggleMethodSelection = onToggleMethodSelection,
          onInspectMethod = onInspectMethod,
          onInspectClassSmali = { cls, _ -> onInspectClassSmali(cls) },
          modifier = Modifier.fillMaxSize(),
        )
      } else if (activeSubTab == ClassesSubTab.FIELDS) {
        DexFieldsExplorerTab(
          classes = classes.ifEmpty { loadState.mergedClasses },
          onInspectClassSmali = onInspectClassSmali,
          modifier = Modifier.fillMaxSize(),
        )
      } else if (activeSubTab == ClassesSubTab.STRINGS) {
        DexStringsExplorerTab(
          parsedDex = loadState.parsedDex,
          onReplaceStringInDex = onReplaceStringInDex,
          onCopyString = onCopyText,
          modifier = Modifier.fillMaxSize(),
        )
      } else if (classes.isEmpty()) {
        Box(
          modifier = Modifier.fillMaxSize().padding(24.dp),
          contentAlignment = Alignment.Center,
        ) {
          Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
            modifier = Modifier.padding(16.dp),
          ) {
            Column(
              modifier = Modifier.padding(24.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.FilterAlt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp),
              )
              Text(
                text = "No Classes Match Active Filters",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
              )
              Text(
                text = "Try adjusting your search query or turn off \"App Only\" to inspect framework packages.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              TextButton(onClick = onResetExtraFilters) {
                Text("Reset Filters")
              }
            }
          }
        }
      } else {
        // EXPLORER LAZY COLUMN
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
            itemsIndexed(
              items = visibleRows,
              key = { _, item -> item.key },
              contentType = { _, item -> if (item is ExplorerRowItem.PackageRow) 0 else 1 },
            ) { _, item ->
              when (item) {
                is ExplorerRowItem.PackageRow -> {
                  PackageFolderRow(
                    group = item.group,
                    expanded = item.expanded,
                    apkPackageName = detectedApkPackage,
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
                    onTogglePackageSelect = {
                      showSelectionCheckboxes = true
                      onTogglePackageSelection(item.group.subtreeClasses)
                    },
                    onWhitelistPackage = { onQuickPackageRule(item.group.packageName, false) },
                    onBlacklistPackage = { onQuickPackageRule(item.group.packageName, true) },
                  )
                }

                is ExplorerRowItem.ClassRow -> {
                  val isMultiDex = loadState.loadedDexes.size > 1
                  CompactClassRow(
                    dexClass = item.dexClass,
                    inTreeMode = item.inTreeMode,
                    depth = item.depth,
                    showDexOrigin = isMultiDex,
                    showSelectionCheckboxes = showSelectionCheckboxes,
                    selectedMethodIds = selectedMethodIds,
                    methodScopeFilter = filterState.methodScopeFilter,
                    allowInit = allowInit,
                    onToggleClassSelect = {
                      showSelectionCheckboxes = true
                      onToggleClassSelection(item.dexClass)
                    },
                    onWhitelistClass = { onQuickClassRule(item.dexClass, false) },
                    onBlacklistClass = { onQuickClassRule(item.dexClass, true) },
                    onDeleteClass = { pendingDeleteClass = item.dexClass },
                    onOpenSmali = {
                      if (isTwoPane) {
                        onSelectClassInTwoPane?.invoke(item.dexClass) ?: onInspectClassSmali(item.dexClass)
                      } else {
                        onInspectClassSmali(item.dexClass)
                      }
                    },
                  )
                }
              }
            }
          }
        }
      }
    }

    if (isTwoPane && activeSubTab == ClassesSubTab.EXPLORER) {
      Row(modifier = Modifier.fillMaxSize()) {
        Box(
          modifier =
            Modifier
              .width(if (screenWidth >= 1200.dp) 440.dp else 380.dp)
              .fillMaxHeight(),
        ) {
          Column(modifier = Modifier.fillMaxSize()) {
            explorerMainContent()
          }
        }
        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
          ClassesDetailPane(
            state = smaliClassState,
            syntaxTheme = smaliSyntaxTheme,
            selectedMethodIds = selectedMethodIds,
            onToggleClassSelection = onToggleClassSelection,
            onToggleMethodSelection = onToggleMethodSelection,
            onInspectClassSmali = onInspectClassSmali,
          )
        }
      }
    } else {
      Column(
        modifier =
          Modifier
            .fillMaxSize()
            .widthIn(max = if (isTwoPane) 1200.dp else 840.dp),
      ) {
        explorerMainContent()
      }
    }

    // FLOATING BATCH ACTION BAR (Bottom overlay during Select Mode)
    if (showSelectionCheckboxes && selectedMethodIds.isNotEmpty()) {
      Box(
        modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
        contentAlignment = Alignment.Center,
      ) {
        BatchActionBar(
          selectedCount = selectedMethodIds.size,
          onFilterSelected = { showSelectedClassesDialog = true },
          onClearSelection = {
            if (onDeselectAllClasses != null) {
              onDeselectAllClasses(classes)
            } else {
              onTogglePackageSelection(emptyList())
            }
            showSelectionCheckboxes = false
          },
          onSelectAll = {
            if (onSelectAllClasses != null) {
              onSelectAllClasses(classes)
            } else {
              onTogglePackageSelection(classes)
            }
          },
          onDeselectAll = {
            if (onDeselectAllClasses != null) {
              onDeselectAllClasses(classes)
            } else {
              onTogglePackageSelection(emptyList())
            }
            showSelectionCheckboxes = false
          },
          onInvertSelection = {
            if (onInvertClassSelection != null) {
              onInvertClassSelection(classes)
            }
          },
          onDone = { showSelectionCheckboxes = false },
        )
      }
    }

    if (showSelectedClassesDialog) {
      SelectedClassesFilterDialog(
        visible = true,
        selectedClasses = selectedClassesList,
        selectedMethodIds = selectedMethodIds,
        allowInit = allowInit,
        onDismiss = { showSelectedClassesDialog = false },
        onConfirmSync = { isExclude ->
          onSyncSelectionToWildcardRules(isExclude)
          showSelectedClassesDialog = false
          showSelectionCheckboxes = false
          if (onDeselectAllClasses != null) {
            onDeselectAllClasses(classes)
          } else {
            onTogglePackageSelection(emptyList())
          }
        },
      )
    }
  }
}

@Composable
private fun ClassesDetailPane(
  state: SmaliClassUiState?,
  syntaxTheme: SmaliSyntaxTheme?,
  selectedMethodIds: Set<String>,
  onToggleClassSelection: (DexClassInfo) -> Unit,
  onToggleMethodSelection: (String) -> Unit,
  onInspectClassSmali: (DexClassInfo) -> Unit,
  modifier: Modifier = Modifier,
) {
  val editorSettings = LocalEditorSettings.current
  val activeSyntaxTheme = syntaxTheme ?: editorSettings.smaliSyntaxTheme

  Surface(
    modifier = modifier.fillMaxSize().padding(12.dp),
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
  ) {
    when (state) {
      null -> {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
          Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
          ) {
            Column(
              modifier = Modifier.padding(28.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.DataObject,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp),
              )
              Text(
                text = "Select a Class to Inspect",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
              )
              Text(
                text = "Choose a class from the explorer on the left to inspect bytecode disassembly and select methods.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }

      is SmaliClassUiState.Loading -> {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            CircularProgressIndicator()
            Text(
              text = stringResource(R.string.smali_disassembling),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      is SmaliClassUiState.Error -> {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
          Text(text = state.message, color = MaterialTheme.colorScheme.error)
        }
      }

      is SmaliClassUiState.Ready -> {
        val dexClass = state.dexClass
        val accessLabel = formatAccessFlags(dexClass.accessFlags).ifBlank { "public" }

        Column(modifier = Modifier.fillMaxSize().padding(10.dp)) {
          // Top row: Class title + full screen action
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "${dexClass.simpleName}.smali",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text = dexClass.descriptor,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = JetBrainsMonoFontFamily,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
            IconButton(
              onClick = { onInspectClassSmali(dexClass) },
              modifier = Modifier.testTag("smali_open_fullscreen_btn"),
            ) {
              Icon(
                imageVector = Icons.Rounded.OpenInNew,
                contentDescription = "Open Full Screen",
                modifier = Modifier.size(20.dp),
              )
            }
          }

          // Metadata pills
          Row(
            modifier =
              Modifier.fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            TagPill(text = accessLabel)
            TagPill(text = dexClass.sourceEntryName)
            TagPill(
              text = "${state.methodCount} methods",
              containerColor = MaterialTheme.colorScheme.primaryContainer,
              contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            TagPill(text = "${state.fieldCount} fields")
            TagPill(text = "${state.lines.size} lines")
          }

          // Class Selection Header
          ClassSelectionHeader(
            dexClass = dexClass,
            selectedMethodIds = selectedMethodIds,
            onToggleSelectClass = { onToggleClassSelection(dexClass) },
            onSelectAllMethods = {
              val methods = (dexClass.directMethods + dexClass.virtualMethods).ifEmpty { dexClass.allMethods }
              methods.forEach { if (it.id !in selectedMethodIds) onToggleMethodSelection(it.id) }
            },
            onDeselectAllMethods = {
              val methods = (dexClass.directMethods + dexClass.virtualMethods).ifEmpty { dexClass.allMethods }
              methods.forEach { if (it.id in selectedMethodIds) onToggleMethodSelection(it.id) }
            },
            onInvertMethodSelection = {
              val methods = (dexClass.directMethods + dexClass.virtualMethods).ifEmpty { dexClass.allMethods }
              methods.forEach { onToggleMethodSelection(it.id) }
            },
            onOpenMethodsSheet = { onInspectClassSmali(dexClass) },
          )

          Spacer(Modifier.height(4.dp))

          Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            SmaliSoraEditor(
              text = state.smaliText,
              isReadOnly = true,
              activeLineIndex = null,
              onLineClick = {},
              onLabelJump = {},
              syntaxTheme = activeSyntaxTheme,
              fontSizeSp = editorSettings.fontSizeSp,
              showLineNumbers = editorSettings.showLineNumbers,
              modifier = Modifier.fillMaxSize().testTag("smali_syntax_code_viewer"),
            )
          }
        }
      }
    }
  }
}
