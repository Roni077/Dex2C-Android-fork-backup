package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Schema
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.dex.DexMethodStructuralMeta
import com.example.dex.DexParsedFile
import com.example.dex.DexTryBlock
import com.example.dex.NativeDexParserBridge
import com.example.dex.RustJniScaffolding
import com.example.dex.descriptorToJavaName
import com.example.ui.theme.JetBrainsMonoFontFamily

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DexMapListCard(
  dex: DexParsedFile,
  onOpenRustBridge: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var showAllMapItems by remember { mutableStateOf(false) }
  var showDex038Details by remember { mutableStateOf(false) }

  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(18.dp),
    color = MaterialTheme.colorScheme.surfaceContainer,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          modifier = Modifier.weight(1f),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(
            imageVector = Icons.Default.Schema,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
          )
          Column {
            Text(
              text = stringResource(R.string.label_map_list),
              style = MaterialTheme.typography.titleMedium,
            )
            Text(
              text = "${dex.parserEngine} • parsed in ${dex.parseDurationMs} ms",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        OutlinedButton(
          onClick = onOpenRustBridge,
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
          modifier = Modifier.testTag("open_rust_jni_bridge_btn"),
        ) {
          Icon(Icons.Default.Memory, contentDescription = null, modifier = Modifier.size(15.dp))
          Spacer(Modifier.width(5.dp))
          Text(stringResource(R.string.label_rust_jni_bridge), style = MaterialTheme.typography.labelMedium)
        }
      }

      // DEX 035-041 Section Summary Badges
      val debugCount = dex.mapItems.firstOrNull { it.typeCode == 0x2003 }?.count ?: 0
      val annotDirCount = dex.mapItems.firstOrNull { it.typeCode == 0x2006 }?.count ?: 0
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        TagPill(
          text = "${dex.mapItems.size} map sections",
          containerColor = MaterialTheme.colorScheme.primaryContainer,
          contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        TagPill(text = "DebugInfo: $debugCount")
        TagPill(text = "AnnDirs: $annotDirCount")
        if (dex.callSites.isNotEmpty() || dex.header.version >= "038") {
          TagPill(
            text = "CallSites: ${dex.callSites.size}",
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
          )
        }
        if (dex.methodHandles.isNotEmpty() || dex.header.version >= "038") {
          TagPill(
            text = "Handles: ${dex.methodHandles.size}",
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
          )
        }
        dex.hiddenApiSummary?.let { hidden ->
          TagPill(
            text = "HiddenAPI: ${hidden.classEntryCount} cls",
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
          )
        }
      }

      if (dex.mapItems.isEmpty()) {
        Text(
          text = "map_list offset is 0x00000000 in this minimal DEX.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      } else {
        val visibleItems = if (showAllMapItems) dex.mapItems else dex.mapItems.take(6)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          visibleItems.forEach { item ->
            Surface(
              shape = RoundedCornerShape(10.dp),
              color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                TagPill(
                  text = item.typeHex,
                  containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                  contentColor = MaterialTheme.colorScheme.primary,
                )
                Text(
                  text = item.typeName,
                  style = MaterialTheme.typography.labelMedium,
                  fontFamily = JetBrainsMonoFontFamily,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.weight(1f),
                )
                Text(
                  text = "×${item.count}",
                  style = MaterialTheme.typography.labelMedium,
                  fontFamily = JetBrainsMonoFontFamily,
                  color = MaterialTheme.colorScheme.secondary,
                )
                Text(
                  text = item.offsetHex,
                  style = MaterialTheme.typography.labelSmall,
                  fontFamily = JetBrainsMonoFontFamily,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }

        if (dex.mapItems.size > 6) {
          Row(
            modifier =
              Modifier.fillMaxWidth()
                .clickable { showAllMapItems = !showAllMapItems }
                .padding(vertical = 4.dp)
                .testTag("toggle_all_map_list_btn"),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = if (showAllMapItems) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
              text =
                if (showAllMapItems) "Collapse map_list"
                else "Show all ${dex.mapItems.size} map_list sections",
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.primary,
            )
          }
        }
      }

      if (dex.methodHandles.isNotEmpty() || dex.callSites.isNotEmpty()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .clickable { showDex038Details = !showDex038Details }
              .padding(vertical = 2.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "DEX 038–041 CallSites (${dex.callSites.size}) & MethodHandles (${dex.methodHandles.size})",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.tertiary,
          )
          Icon(
            imageVector = if (showDex038Details) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
          )
        }
        AnimatedVisibility(visible = showDex038Details) {
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            dex.methodHandles.take(12).forEach { mh ->
              Text(
                text = "handle[${mh.index}] ${mh.kindLabel} -> ${mh.memberSummary}",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = JetBrainsMonoFontFamily,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            dex.callSites.take(12).forEach { cs ->
              Text(
                text = "call_site[${cs.index}] ${cs.methodName ?: "bootstrap"} ${cs.methodDescriptor ?: ""} @ 0x%06X".format(cs.encodedArrayOffset),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = JetBrainsMonoFontFamily,
                color = MaterialTheme.colorScheme.onSurface,
              )
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DexMethodMetaViewer(
  meta: DexMethodStructuralMeta,
  tryBlocks: List<DexTryBlock>,
  modifier: Modifier = Modifier,
) {
  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(bottom = 20.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
      ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(
            text = "DexHelper Offsets & Structure",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
          )
          Spacer(Modifier.height(4.dp))
          HeaderKeyValueRow("Method Index", "#${meta.methodIndex}")
          HeaderKeyValueRow("Source File", meta.sourceFile ?: "Unknown")
          HeaderKeyValueRow("code_item Offset", if (meta.codeItemOffset > 0) "0x%08X".format(meta.codeItemOffset) else "None")
          HeaderKeyValueRow("debug_info_off", if (meta.debugInfoOffset > 0) "0x%08X".format(meta.debugInfoOffset) else "None")
          HeaderKeyValueRow("annotations_dir_off", if (meta.classAnnotationsOffset > 0) "0x%08X".format(meta.classAnnotationsOffset) else "None")
          HeaderKeyValueRow("static_values_off", if (meta.staticValuesOffset > 0) "0x%08X".format(meta.staticValuesOffset) else "None")
          if (meta.parameterNames.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text("Parameter Names", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
              meta.parameterNames.forEach { p -> TagPill(text = p) }
            }
          }
        }
      }
    }

    item {
      Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
      ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = "Annotations (${meta.methodAnnotations.size} method • ${meta.classAnnotations.size} class)",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.secondary,
          )
          if (meta.methodAnnotations.isEmpty() && meta.classAnnotations.isEmpty()) {
            Text(
              text = "No annotation_item entries attached to this method or class.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          } else {
            meta.methodAnnotations.forEach { ann ->
              AnnotationBadgeRow(scopeLabel = "METHOD", ann = ann)
            }
            meta.classAnnotations.forEach { ann ->
              AnnotationBadgeRow(scopeLabel = "CLASS", ann = ann)
            }
          }
        }
      }
    }

    item {
      Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
      ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = "debug_info_item Line Mapping (${meta.debugLines.size} entries)",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.tertiary,
          )
          if (meta.debugLines.isEmpty()) {
            Text(
              text = "No debug line-number program present in code_item.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          } else {
            meta.debugLines.take(40).forEach { entry ->
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text(
                  text = "PC 0x%04X".format(entry.addressWord),
                  style = MaterialTheme.typography.labelMedium,
                  fontFamily = JetBrainsMonoFontFamily,
                  color = MaterialTheme.colorScheme.primary,
                )
                Text(
                  text = "Line ${entry.lineNumber}",
                  style = MaterialTheme.typography.labelMedium,
                  fontFamily = JetBrainsMonoFontFamily,
                  color = MaterialTheme.colorScheme.onSurface,
                )
              }
            }
          }
        }
      }
    }

    if (tryBlocks.isNotEmpty() || meta.staticValuesPreview.isNotEmpty()) {
      item {
        Surface(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(14.dp),
          color = MaterialTheme.colorScheme.surfaceContainer,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
              text = "Try/Catch Spans & Static Field Values",
              style = MaterialTheme.typography.titleMedium,
            )
            tryBlocks.forEach { tb ->
              val catches = tb.handlers.joinToString(", ") { "${it.typeDescriptor ?: "<all>"}@0x%04X".format(it.handlerOffsetWord) }
              Text(
                text = "try [0x%04X..0x%04X) -> $catches".format(tb.startOffsetWord, tb.endOffsetWord),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = JetBrainsMonoFontFamily,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            if (meta.staticValuesPreview.isNotEmpty()) {
              Text(
                text = "Class static encoded_array: ${meta.staticValuesPreview.joinToString(", ")}",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = JetBrainsMonoFontFamily,
                color = MaterialTheme.colorScheme.secondary,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun AnnotationBadgeRow(
  scopeLabel: String,
  ann: com.example.dex.DexAnnotationEntry,
) {
  Surface(
    shape = RoundedCornerShape(10.dp),
    color = MaterialTheme.colorScheme.surfaceContainerLow,
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        TagPill(
          text = "$scopeLabel • ${ann.visibilityLabel}",
          containerColor = MaterialTheme.colorScheme.primaryContainer,
          contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text(
          text = "@${descriptorToJavaName(ann.typeDescriptor)}",
          style = MaterialTheme.typography.labelMedium,
          fontFamily = JetBrainsMonoFontFamily,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      if (ann.elementsPreview.isNotEmpty()) {
        Text(
          text = ann.elementsPreview.joinToString(", "),
          style = MaterialTheme.typography.labelSmall,
          fontFamily = JetBrainsMonoFontFamily,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RustJniBridgeSheet(
  onCopyCode: (String) -> Unit,
  onExportZip: () -> Unit,
  onDismiss: () -> Unit,
) {
  val files = remember { RustJniScaffolding.generateBridgeFiles() }
  var selectedIdx by remember { mutableIntStateOf(0) }
  val activeFile = files[selectedIdx.coerceIn(files.indices)]
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .height(540.dp)
          .windowInsetsPadding(WindowInsets.navigationBars)
          .padding(horizontal = 16.dp, vertical = 6.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = stringResource(R.string.rust_bridge_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
          )
          Text(
            text = stringResource(R.string.rust_bridge_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        TagPill(
          text = if (NativeDexParserBridge.isNativeLoaded) "SO LOADED" else "KOTLIN FALLBACK",
          containerColor =
            if (NativeDexParserBridge.isNativeLoaded) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.secondaryContainer,
          contentColor =
            if (NativeDexParserBridge.isNativeLoaded) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSecondaryContainer,
        )
      }

      LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(files) { idx, file ->
          FilterChip(
            selected = idx == selectedIdx,
            onClick = { selectedIdx = idx },
            leadingIcon = { Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(15.dp)) },
            label = { Text(file.fileName, fontFamily = JetBrainsMonoFontFamily) },
          )
        }
      }

      Text(
        text = "${activeFile.relativePath} — ${activeFile.description}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      SyntaxCodeViewer(
        code = activeFile.content,
        modifier = Modifier.weight(1f).fillMaxWidth(),
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        OutlinedButton(
          onClick = { onCopyCode(activeFile.content) },
          modifier = Modifier.weight(1f).testTag("copy_rust_bridge_btn"),
        ) {
          Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text(stringResource(R.string.action_copy_code))
        }
        Button(
          onClick = onExportZip,
          modifier = Modifier.weight(1.2f).testTag("export_rust_bridge_zip_btn"),
        ) {
          Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text("Export Crate .ZIP")
        }
      }
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onDismiss) { Text("Close") }
      }
    }
  }
}
