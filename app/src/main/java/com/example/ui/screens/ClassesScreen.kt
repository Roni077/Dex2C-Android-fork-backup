package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.dex.DexClassInfo
import com.example.dex.DexEncodedMethod
import com.example.dex.formatAccessFlags
import com.example.ui.ClassFilterState
import com.example.ui.DexLoadState
import com.example.ui.components.TagPill
import com.example.ui.theme.JetBrainsMonoFontFamily

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
) {
  val expandedMap = remember { mutableStateMapOf<Int, Boolean>() }

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(modifier = Modifier.fillMaxSize().widthIn(max = 840.dp)) {
      Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        OutlinedTextField(
          value = filterState.searchQuery,
          onValueChange = onSearchChange,
          modifier = Modifier.fillMaxWidth().testTag("class_search_input"),
          placeholder = { Text(stringResource(R.string.label_search_classes)) },
          leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
          trailingIcon = {
            if (filterState.searchQuery.isNotEmpty()) {
              IconButton(onClick = { onSearchChange("") }) {
                Icon(Icons.Default.Clear, contentDescription = "Clear search")
              }
            }
          },
          singleLine = true,
          shape = RoundedCornerShape(14.dp),
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            androidx.compose.material3.Button(
              onClick = onInspectSelfApk,
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
            text = "No classes match the active search or filter chips. Try turning off \"App Only\".",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 20.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          items(classes.take(250), key = { it.classIndex }) { dexClass ->
            val expanded = expandedMap[dexClass.classIndex] == true
            DexClassCard(
              dexClass = dexClass,
              expanded = expanded,
              selectedMethodIds = selectedMethodIds,
              allowInit = allowInit,
              onToggleExpand = { expandedMap[dexClass.classIndex] = !expanded },
              onToggleClassSelect = { onToggleClassSelection(dexClass) },
              onToggleMethodSelect = onToggleMethodSelection,
              onInspectMethod = { method -> onInspectMethod(dexClass, method) },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun DexClassCard(
  dexClass: DexClassInfo,
  expanded: Boolean,
  selectedMethodIds: Set<String>,
  allowInit: Boolean,
  onToggleExpand: () -> Unit,
  onToggleClassSelect: () -> Unit,
  onToggleMethodSelect: (String) -> Unit,
  onInspectMethod: (DexEncodedMethod) -> Unit,
) {
  val methods = dexClass.allMethods
  val selectableCount = methods.count { it.hasBytecode && (allowInit || !it.isInitConstructor) }.coerceAtLeast(1)
  val selectedCount = methods.count { it.id in selectedMethodIds }
  val allSelected = methods.isNotEmpty() && selectedCount >= selectableCount

  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surfaceContainer,
    border =
      BorderStroke(
        width = 1.dp,
        color =
          if (selectedCount > 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
          else MaterialTheme.colorScheme.outlineVariant,
      ),
  ) {
    Column {
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .clickable(onClick = onToggleExpand)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Checkbox(
          checked = allSelected,
          onCheckedChange = { onToggleClassSelect() },
          modifier = Modifier.minimumInteractiveComponentSize().testTag("class_checkbox_${dexClass.classIndex}"),
        )
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = dexClass.simpleName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = dexClass.packageName,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = JetBrainsMonoFontFamily,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Spacer(Modifier.height(4.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TagPill(text = "${dexClass.bytecodeMethodCount} bytecode")
            if (selectedCount > 0) {
              TagPill(
                text = "$selectedCount queued",
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
              )
            }
          }
        }
        IconButton(
          onClick = onToggleExpand,
          modifier = Modifier.minimumInteractiveComponentSize(),
        ) {
          Icon(
            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = if (expanded) "Collapse class methods" else "Expand class methods",
          )
        }
      }

      AnimatedVisibility(visible = expanded) {
        Column {
          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
          methods.forEach { method ->
            MethodRow(
              method = method,
              selected = method.id in selectedMethodIds,
              allowInit = allowInit,
              onToggleSelect = { onToggleMethodSelect(method.id) },
              onInspect = { onInspectMethod(method) },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun MethodRow(
  method: DexEncodedMethod,
  selected: Boolean,
  allowInit: Boolean,
  onToggleSelect: () -> Unit,
  onInspect: () -> Unit,
) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .clickable(onClick = onInspect)
        .padding(start = 12.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Checkbox(
      checked = selected,
      onCheckedChange = { onToggleSelect() },
      modifier = Modifier.minimumInteractiveComponentSize(),
    )
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = method.methodRef.prettySignature(),
        style = MaterialTheme.typography.labelMedium,
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      Spacer(Modifier.height(3.dp))
      val meta = method.codeMeta
      val metaStr =
        if (meta != null) {
          "regs=${meta.registersSize} • ${meta.insnsCount}w • ${formatAccessFlags(method.accessFlags, true)}"
        } else {
          "no code_item • ${formatAccessFlags(method.accessFlags, true)}"
        }
      Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = metaStr,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.weight(1f, fill = false),
        )
        if (method.isInitConstructor) {
          TagPill(
            text = if (allowInit) "<init> allowed" else "<init> skipped",
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
    Spacer(Modifier.width(6.dp))
    Icon(
      imageVector = Icons.Default.ChevronRight,
      contentDescription = "Inspect method",
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.size(18.dp),
    )
  }
}
