package com.dex2c.studio.ui.screens

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
import com.dex2c.studio.ui.components.ClassesFilterTray
import com.dex2c.studio.ui.components.CompactClassRow
import com.dex2c.studio.ui.components.DexFieldsExplorerTab
import com.dex2c.studio.ui.components.DexMethodsExplorerTab
import com.dex2c.studio.ui.components.DexStringsExplorerTab
import com.dex2c.studio.ui.components.InteractiveFilterRuleBuilderPanel
import com.dex2c.studio.ui.components.PackageFolderRow
import com.dex2c.studio.ui.theme.JetBrainsMonoFontFamily

enum class ClassesViewMode {
  TREE,
  FLAT,
}

enum class ClassesSubTab(val label: String, val tagSuffix: String) {
  EXPLORER("CLASSES", "explorer"),
  METHODS("METHODS", "methods"),
  FIELDS("FIELDS", "fields"),
  STRINGS("STRINGS", "strings"),
  FILTER_RULES("RULES", "rules"),
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
  onSearchChange: (String) -> Unit,
  onToggleHideFramework: () -> Unit,
  onToggleOnlyBytecode: () -> Unit,
  onToggleOnlySelected: () -> Unit,
  onToggleClassSelection: (DexClassInfo) -> Unit,
  onToggleMethodSelection: (String) -> Unit,
  onInspectMethod: (DexClassInfo, DexEncodedMethod) -> Unit,
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
) {
  var activeSubTab by rememberSaveable(initialSubTab) { mutableStateOf(initialSubTab) }
  var viewMode by rememberSaveable { mutableStateOf(ClassesViewMode.TREE) }
  var compactMiddlePackages by rememberSaveable { mutableStateOf(false) }
  var showSelectionCheckboxes by rememberSaveable { mutableStateOf(false) }
  var showSortMenu by remember { mutableStateOf(false) }
  var showDexMenu by remember { mutableStateOf(false) }
  var showExportMenu by remember { mutableStateOf(false) }
  var showExtraFilters by remember { mutableStateOf(false) }
  val manualExpandedPackages = remember { mutableStateMapOf<String, Boolean>() }
  val searchCollapsedPackages = remember { mutableStateMapOf<String, Boolean>() }

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

  val totalDistinctPackages =
    remember(classes, defaultPackageLabel) {
      classes.map { normalizePackageName(it.packageName, defaultPackageLabel) }.distinct().size
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

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(modifier = Modifier.fillMaxSize().widthIn(max = 840.dp)) {

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
              val tabText =
                when (tab) {
                  ClassesSubTab.FILTER_RULES -> "${tab.label} (${structuredRules.size})"
                  else -> tab.label
                }
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

            Box {
              IconButton(
                onClick = { showExportMenu = true },
                modifier = Modifier.size(32.dp).testTag("classes_export_patched_menu_btn"),
              ) {
                Icon(
                  imageVector = Icons.Rounded.SaveAlt,
                  contentDescription = "Export Patched DEX or APK",
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(18.dp),
                )
              }
              DropdownMenu(
                expanded = showExportMenu,
                onDismissRequest = { showExportMenu = false },
              ) {
                DropdownMenuItem(
                  text = { Text("Export Patched DEX (.dex)") },
                  onClick = {
                    showExportMenu = false
                    onExportPatchedDex()
                  },
                  modifier = Modifier.testTag("classes_export_patched_dex_item"),
                )
                DropdownMenuItem(
                  text = { Text("Export Patched APK (.apk)") },
                  onClick = {
                    showExportMenu = false
                    onExportPatchedApk()
                  },
                  modifier = Modifier.testTag("classes_export_patched_apk_item"),
                )
              }
            }
          }
        }

        // EXPLORER CONTROLS (Only visible on EXPLORER tab)
        if (activeSubTab == ClassesSubTab.EXPLORER) {
          // Compact APK Package Banner
          if (loadState is DexLoadState.Ready && !detectedApkPackage.isNullOrBlank()) {
            Surface(
              modifier =
                Modifier.fillMaxWidth()
                  .padding(bottom = 6.dp)
                  .testTag("classes_apk_package_banner"),
              shape = RoundedCornerShape(12.dp),
              color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Icon(
                  imageVector = Icons.Rounded.Android,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(16.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = "APK Package: $detectedApkPackage",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("classes_apk_package_text"),
                  )
                }
                TextButton(
                  onClick = {
                    viewMode = ClassesViewMode.TREE
                    val segments = detectedApkPackage.split('.').filter { it.isNotBlank() }
                    var acc = ""
                    for (seg in segments) {
                      acc = if (acc.isEmpty()) seg else "$acc.$seg"
                      manualExpandedPackages[acc] = true
                    }
                  },
                  contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                  modifier =
                    Modifier.defaultMinSize(minHeight = 28.dp).testTag("classes_focus_apk_pkg_btn"),
                ) {
                  Text(
                    text = "Expand APK Pkg",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                  )
                }
              }
            }
          }

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

          // Filter Chips Row
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

            if (viewMode == ClassesViewMode.TREE) {
              item {
                FilterChip(
                  selected = !compactMiddlePackages,
                  onClick = { compactMiddlePackages = !compactMiddlePackages },
                  leadingIcon = {
                    Icon(
                      imageVector = Icons.Rounded.FolderOpen,
                      contentDescription = null,
                      modifier = Modifier.size(16.dp),
                    )
                  },
                  label = {
                    Text(if (compactMiddlePackages) "Compact Pkgs" else "Clean Levels")
                  },
                  modifier = Modifier.testTag("chip_folder_levels_toggle"),
                )
              }
            }
          }

          // Modular Expandable Filter Tray
          ClassesFilterTray(
            visible = showExtraFilters,
            filterState = filterState,
            onClassTypeFilterChange = onClassTypeFilterChange,
            onToggleHideInnerClasses = onToggleHideInnerClasses,
            onToggleNonEmptyPackages = onToggleNonEmptyPackages,
            onMethodScopeFilterChange = onMethodScopeFilterChange,
            onResetExtraFilters = onResetExtraFilters,
          )

          // Active Filter Summary Strip (shown when tray is closed and extra filters are active)
          if (!showExtraFilters) {
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
      }

      // SUB-TAB SCREENS
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
                    onTogglePackageSelect = { onTogglePackageSelection(item.group.subtreeClasses) },
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
                    onToggleClassSelect = { onToggleClassSelection(item.dexClass) },
                    onWhitelistClass = { onQuickClassRule(item.dexClass, false) },
                    onBlacklistClass = { onQuickClassRule(item.dexClass, true) },
                    onDeleteClass = { pendingDeleteClass = item.dexClass },
                    onOpenSmali = { onInspectClassSmali(item.dexClass) },
                  )
                }
              }
            }
          }
        }
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
          onWhitelistSelected = { onSyncSelectionToWildcardRules(false) },
          onBlacklistSelected = { onSyncSelectionToWildcardRules(true) },
          onClearSelection = { onTogglePackageSelection(emptyList()) },
        )
      }
    }
  }
}
