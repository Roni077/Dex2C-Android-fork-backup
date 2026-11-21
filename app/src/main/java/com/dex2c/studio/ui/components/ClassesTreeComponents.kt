package com.dex2c.studio.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dex2c.studio.dex.DexClassInfo
import com.dex2c.studio.ui.MethodScopeFilter
import com.dex2c.studio.ui.matchesMethodScope
import com.dex2c.studio.ui.screens.PackageGroup
import com.dex2c.studio.ui.theme.JetBrainsMonoFontFamily

/**
 * Package folder row representing a package level in the DEX tree.
 * Styled with clean hierarchy, distinct folder states, legible badges, and well-spaced quick-actions.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PackageFolderRow(
  group: PackageGroup,
  expanded: Boolean,
  apkPackageName: String? = null,
  showSelectionCheckboxes: Boolean,
  selectedMethodIds: Set<String>,
  methodScopeFilter: MethodScopeFilter,
  allowInit: Boolean,
  onToggleExpand: () -> Unit,
  onTogglePackageSelect: () -> Unit,
  onWhitelistPackage: () -> Unit = {},
  onBlacklistPackage: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val totalMethodsCount: Int
  val selectedCount: Int

  if (methodScopeFilter == MethodScopeFilter.ALL) {
    totalMethodsCount =
      if (allowInit) group.totalBytecodeMethods
      else group.subtreeClasses.sumOf { cls -> cls.allMethods.count { it.hasBytecode && !it.isInitConstructor } }
    selectedCount =
      if (selectedMethodIds.isEmpty()) 0
      else group.selectedMethodCount
  } else {
    var total = 0
    var selected = 0
    for (cls in group.subtreeClasses) {
      for (m in cls.allMethods) {
        if (matchesMethodScope(m, methodScopeFilter) && m.hasBytecode && (allowInit || !m.isInitConstructor)) {
          total++
          if (m.id in selectedMethodIds) selected++
        }
      }
    }
    totalMethodsCount = total
    selectedCount = selected
  }

  val toggleState =
    when {
      !showSelectionCheckboxes || totalMethodsCount == 0 || selectedCount == 0 -> ToggleableState.Off
      selectedCount >= totalMethodsCount -> ToggleableState.On
      else -> ToggleableState.Indeterminate
    }

  val isApkRootPackage = !apkPackageName.isNullOrBlank() && group.packageName == apkPackageName
  val isApkBranch =
    !apkPackageName.isNullOrBlank() &&
      (group.packageName == apkPackageName ||
        apkPackageName.startsWith("${group.packageName}.") ||
        group.packageName.startsWith("$apkPackageName."))

  val startPad = (10 + group.depth * 18).dp
  val cleanSegmentLabel = group.displayLabel.ifBlank { group.packageName }
  val chevronRotation by animateFloatAsState(
    targetValue = if (expanded) 90f else 0f,
    label = "chevronRotation",
  )

  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .defaultMinSize(minHeight = 44.dp)
        .background(
          color =
            if (isApkRootPackage) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.14f)
            else if (expanded) MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.35f)
            else Color.Transparent,
          shape = RoundedCornerShape(8.dp),
        )
        .combinedClickable(
          onClick = onToggleExpand,
          onLongClick = onTogglePackageSelect,
        )
        .padding(start = startPad, end = 10.dp, top = 2.dp, bottom = 2.dp)
        .testTag("pkg_row_${group.packageName}"),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    // Rotating Expand Chevron Indicator
    Icon(
      imageVector = Icons.Rounded.ChevronRight,
      contentDescription = if (expanded) "Collapse package" else "Expand package",
      tint =
        if (expanded) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
      modifier =
        Modifier.size(18.dp)
          .rotate(chevronRotation)
          .padding(end = 2.dp),
    )

    if (showSelectionCheckboxes) {
      TriStateCheckbox(
        state = toggleState,
        onClick = onTogglePackageSelect,
        modifier =
          Modifier.minimumInteractiveComponentSize().testTag("pkg_checkbox_${group.packageName}"),
      )
    } else {
      Spacer(Modifier.width(2.dp))
    }

    // Folder Icon with tonal states
    Icon(
      imageVector = if (expanded) Icons.Rounded.FolderOpen else Icons.Rounded.Folder,
      contentDescription = null,
      tint =
        when {
          isApkRootPackage -> MaterialTheme.colorScheme.primary
          expanded -> MaterialTheme.colorScheme.primary
          isApkBranch || selectedCount > 0 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
          else -> MaterialTheme.colorScheme.secondary
        },
      modifier = Modifier.size(20.dp),
    )

    Spacer(Modifier.width(8.dp))

    Column(modifier = Modifier.weight(1f)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Text(
          text = cleanSegmentLabel,
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = if (isApkRootPackage) FontWeight.Bold else FontWeight.Medium,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )

        if (isApkRootPackage) {
          Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.testTag("pkg_apk_badge_${group.packageName}"),
          ) {
            Text(
              text = "APK",
              style = MaterialTheme.typography.labelSmall,
              fontFamily = JetBrainsMonoFontFamily,
              fontWeight = FontWeight.Bold,
              fontSize = 10.sp,
              modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
            )
          }
        }
      }

      if (group.depth > 0 && group.packageName != cleanSegmentLabel) {
        Text(
          text = group.packageName,
          style = MaterialTheme.typography.labelSmall,
          fontFamily = JetBrainsMonoFontFamily,
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.testTag("pkg_full_path_${group.packageName}"),
        )
      }
    }

    // Selected count pill
    if (selectedCount > 0) {
      Spacer(Modifier.width(6.dp))
      InlineBadge(
        text = "$selectedCount sel",
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
      )
    }

    Spacer(Modifier.width(6.dp))

    // Class count badge
    Surface(
      shape = RoundedCornerShape(6.dp),
      color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
    ) {
      Text(
        text = "${group.subtreeClasses.size} cls",
        style = MaterialTheme.typography.labelSmall,
        fontFamily = JetBrainsMonoFontFamily,
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
      )
    }
  }
}

/**
 * Compact Class Leaf Row representing a single DEX class.
 * Provides clear type categorization (C / I / E), crisp class name, and smooth Smali navigation.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CompactClassRow(
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
  onDeleteClass: () -> Unit = {},
  onOpenSmali: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val hasAnySelected = selectedMethodIds.isNotEmpty()
  val targetIds =
    remember(dexClass, methodScopeFilter, allowInit, showSelectionCheckboxes, hasAnySelected) {
      if (!showSelectionCheckboxes && !hasAnySelected) {
        emptyList()
      } else {
        val scoped =
          if (methodScopeFilter == MethodScopeFilter.ALL) dexClass.allMethods
          else dexClass.allMethods.filter { matchesMethodScope(it, methodScopeFilter) }
        val bytecodeIds =
          scoped.filter { it.hasBytecode && (allowInit || !it.isInitConstructor) }.map { it.id }
        bytecodeIds.ifEmpty { scoped.map { it.id } }
      }
    }

  val selectedCount =
    if (!hasAnySelected || targetIds.isEmpty()) 0
    else targetIds.count { it in selectedMethodIds }

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

  val startPad = if (inTreeMode) (16 + depth * 18).dp else 12.dp
  val isInterface = (dexClass.accessFlags and 0x0200) != 0
  val isEnum = (dexClass.accessFlags and 0x4000) != 0

  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .defaultMinSize(minHeight = 44.dp)
        .background(
          color =
            if (selectedCount > 0) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.16f)
            else Color.Transparent,
          shape = RoundedCornerShape(8.dp),
        )
        .combinedClickable(
          onClick = {
            if (showSelectionCheckboxes) {
              onToggleClassSelect()
            } else {
              onOpenSmali()
            }
          },
          onLongClick = onToggleClassSelect,
        )
        .padding(start = startPad, end = 10.dp, top = 3.dp, bottom = 3.dp)
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

    ClassTypeBadge(
      isInterface = isInterface,
      isEnum = isEnum,
    )

    Spacer(Modifier.width(10.dp))

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
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }

    if (showDexOrigin) {
      Spacer(Modifier.width(4.dp))
      InlineBadge(
        text = dexClass.sourceEntryName.substringAfterLast('/').removeSuffix(".dex"),
        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f),
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
      )
    }

    if (selectedCount > 0) {
      Spacer(Modifier.width(4.dp))
      InlineBadge(
        text = "$selectedCount sel",
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
      )
    }

    if (showSelectionCheckboxes) {
      Spacer(Modifier.width(2.dp))
      IconButton(
        onClick = onDeleteClass,
        modifier =
          Modifier.minimumInteractiveComponentSize()
            .size(36.dp)
            .testTag("class_delete_btn_${dexClass.classIndex}"),
      ) {
        Icon(
          imageVector = Icons.Rounded.DeleteOutline,
          contentDescription = "Delete class ${dexClass.simpleName} from DEX",
          tint = MaterialTheme.colorScheme.error,
          modifier = Modifier.size(16.dp),
        )
      }
    }

    IconButton(
      onClick = onOpenSmali,
      modifier = Modifier.minimumInteractiveComponentSize().size(36.dp).testTag(classSmaliTag),
    ) {
      Icon(
        imageVector = Icons.Rounded.Code,
        contentDescription = "Open Smali for ${dexClass.simpleName}",
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier.size(17.dp),
      )
    }
  }
}

/**
 * Visual badge for Class types:
 * - C (Concrete Class)
 * - I (Interface)
 * - E (Enum)
 */
@Composable
fun ClassTypeBadge(
  isInterface: Boolean,
  isEnum: Boolean,
  modifier: Modifier = Modifier,
) {
  val badgeLetter =
    when {
      isInterface -> "I"
      isEnum -> "E"
      else -> "C"
    }

  val badgeColor =
    when {
      isInterface -> MaterialTheme.colorScheme.tertiary
      isEnum -> MaterialTheme.colorScheme.secondary
      else -> MaterialTheme.colorScheme.primary
    }

  val badgeContentColor =
    when {
      isInterface -> MaterialTheme.colorScheme.onTertiary
      isEnum -> MaterialTheme.colorScheme.onSecondary
      else -> MaterialTheme.colorScheme.onPrimary
    }

  Surface(
    shape = CircleShape,
    color = badgeColor,
    contentColor = badgeContentColor,
    modifier = modifier.size(22.dp),
  ) {
    Box(contentAlignment = Alignment.Center) {
      Text(
        text = badgeLetter,
        style = MaterialTheme.typography.labelSmall,
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
      )
    }
  }
}

/**
 * Clickable badge button for rapid rule actions (+W and !B).
 */
@Composable
fun QuickRuleBadgeButton(
  text: String,
  containerColor: Color,
  contentColor: Color,
  onClick: () -> Unit,
  testTag: String,
  modifier: Modifier = Modifier,
) {
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(6.dp),
    color = containerColor,
    contentColor = contentColor,
    modifier = modifier.testTag(testTag),
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

/**
 * Informational badge pill.
 */
@Composable
fun InlineBadge(
  text: String,
  containerColor: Color,
  contentColor: Color,
  modifier: Modifier = Modifier,
) {
  Surface(
    shape = RoundedCornerShape(6.dp),
    color = containerColor,
    modifier = modifier,
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelSmall,
      fontFamily = JetBrainsMonoFontFamily,
      fontSize = 10.sp,
      fontWeight = FontWeight.Medium,
      color = contentColor,
      modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
    )
  }
}

/**
 * Floating batch actions bar that appears when in Select Mode or items are selected.
 * Enables 1-tap bulk actions: Select All, Deselect All, Invert, Whitelist, Blacklist, Done.
 */
@Composable
fun BatchActionBar(
  selectedCount: Int,
  onFilterSelected: () -> Unit,
  onClearSelection: () -> Unit,
  modifier: Modifier = Modifier,
  onSelectAll: (() -> Unit)? = null,
  onDeselectAll: (() -> Unit)? = null,
  onInvertSelection: (() -> Unit)? = null,
  onDone: (() -> Unit)? = null,
  onWhitelistSelected: (() -> Unit)? = null,
  onBlacklistSelected: (() -> Unit)? = null,
) {
  AnimatedVisibility(
    visible = selectedCount > 0,
    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
    modifier = modifier,
  ) {
    Surface(
      shape = RoundedCornerShape(20.dp),
      color = MaterialTheme.colorScheme.surfaceContainerHigh,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
      shadowElevation = 6.dp,
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
      Row(
        modifier =
          Modifier.horizontalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Surface(
          shape = CircleShape,
          color = MaterialTheme.colorScheme.primaryContainer,
          modifier = Modifier.size(24.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = Icons.Rounded.Check,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.size(14.dp),
            )
          }
        }

        Text(
          text = if (selectedCount > 0) "$selectedCount sel" else "Select Mode",
          style = MaterialTheme.typography.labelMedium,
          fontFamily = JetBrainsMonoFontFamily,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface,
        )

        if (onSelectAll != null) {
          QuickRuleBadgeButton(
            text = "Select All",
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface,
            onClick = onSelectAll,
            testTag = "batch_select_all_btn",
          )
        }

        if (onDeselectAll != null) {
          QuickRuleBadgeButton(
            text = "Deselect",
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface,
            onClick = onDeselectAll,
            testTag = "batch_deselect_all_btn",
          )
        }

        if (onInvertSelection != null) {
          QuickRuleBadgeButton(
            text = "Invert",
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface,
            onClick = onInvertSelection,
            testTag = "batch_invert_selection_btn",
          )
        }

        QuickRuleBadgeButton(
          text = "Add to Filter",
          containerColor = MaterialTheme.colorScheme.primary,
          contentColor = MaterialTheme.colorScheme.onPrimary,
          onClick = onFilterSelected,
          testTag = "batch_add_to_filter_btn",
        )

        IconButton(
          onClick = {
            onDone?.invoke() ?: onClearSelection()
          },
          modifier = Modifier.size(28.dp).testTag("batch_clear_btn"),
        ) {
          Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = "Close selection bar",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
          )
        }
      }
    }
  }
}
