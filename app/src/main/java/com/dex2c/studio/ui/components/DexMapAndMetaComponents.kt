package com.dex2c.studio.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Schema
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
import com.dex2c.studio.R
import com.dex2c.studio.dex.DexMethodStructuralMeta
import com.dex2c.studio.dex.DexParsedFile
import com.dex2c.studio.dex.DexTryBlock
import com.dex2c.studio.dex.descriptorToJavaName
import com.dex2c.studio.ui.theme.JetBrainsMonoFontFamily

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DexMapListCard(
  dex: DexParsedFile,
  modifier: Modifier = Modifier,
) {
  var showAllSheet by remember { mutableStateOf(false) }

  Surface(
    modifier = modifier.fillMaxWidth().animateContentSize(ExpressiveMotion.spatialSpring()),
    shape = RoundedCornerShape(24.dp),
    color = MaterialTheme.colorScheme.surfaceContainer,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
            imageVector = Icons.Rounded.Schema,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
          )
          Column {
            Text(
              text = stringResource(R.string.label_map_list),
              style = MaterialTheme.typography.titleMedium,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = "${dex.parserEngine} • ${dex.parseDurationMs} ms",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }

      // DEX 035-041 Section Summary Badges
      val debugCount = dex.mapItems.firstOrNull { it.typeCode == 0x2003 }?.count ?: 0
      val annotDirCount = dex.mapItems.firstOrNull { it.typeCode == 0x2006 }?.count ?: 0
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        TagPill(
          text = "${dex.mapItems.size} sections",
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
            text = "HiddenAPI: ${hidden.classEntryCount}",
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
        val previewItems = dex.mapItems.take(4)
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
          previewItems.chunked(2).forEach { pair ->
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              pair.forEach { item ->
                Surface(
                  modifier = Modifier.weight(1f),
                  shape = RoundedCornerShape(10.dp),
                  color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                  Column(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                  ) {
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically,
                    ) {
                      Text(
                        text = item.typeHex,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMonoFontFamily,
                        color = MaterialTheme.colorScheme.primary,
                      )
                      Text(
                        text = "×${item.count}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMonoFontFamily,
                        color = MaterialTheme.colorScheme.secondary,
                      )
                    }
                    Text(
                      text = item.typeName,
                      style = MaterialTheme.typography.labelSmall,
                      fontFamily = JetBrainsMonoFontFamily,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                  }
                }
              }
              if (pair.size == 1) {
                Spacer(Modifier.weight(1f))
              }
            }
          }
        }

        if (dex.mapItems.size > 4 || dex.methodHandles.isNotEmpty() || dex.callSites.isNotEmpty()) {
          Row(
            modifier =
              Modifier.fillMaxWidth()
                .clickable { showAllSheet = true }
                .padding(vertical = 4.dp)
                .testTag("toggle_all_map_list_btn"),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = Icons.Rounded.ExpandMore,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
              text = "Inspect all ${dex.mapItems.size} map_list sections",
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.primary,
            )
          }
        }
      }
    }
  }

  if (showAllSheet) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
      onDismissRequest = { showAllSheet = false },
      sheetState = sheetState,
      shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
      containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
      Column(
        modifier =
          Modifier.fillMaxWidth()
            .height(520.dp)
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
              text = "${dex.entryName} • map_list (${dex.mapItems.size})",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.SemiBold,
            )
            Text(
              text = "${dex.parserEngine} • parsed in ${dex.parseDurationMs} ms",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          TextButton(onClick = { showAllSheet = false }) { Text("Close") }
        }
        LazyColumn(
          modifier = Modifier.weight(1f).fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(6.dp),
          contentPadding = PaddingValues(bottom = 16.dp),
        ) {
          itemsIndexed(dex.mapItems) { _, item ->
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
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
          if (dex.methodHandles.isNotEmpty() || dex.callSites.isNotEmpty()) {
            item {
              Spacer(Modifier.height(6.dp))
              Text(
                text = "DEX 038–041 CallSites (${dex.callSites.size}) & MethodHandles (${dex.methodHandles.size})",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.tertiary,
              )
            }
            itemsIndexed(dex.methodHandles.take(24)) { _, mh ->
              Text(
                text = "handle[${mh.index}] ${mh.kindLabel} -> ${mh.memberSummary}",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = JetBrainsMonoFontFamily,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            itemsIndexed(dex.callSites.take(24)) { _, cs ->
              val offHex = "0x%06X".format(cs.encodedArrayOffset)
              Text(
                text = "call_site[${cs.index}] ${cs.methodName ?: "bootstrap"} ${cs.methodDescriptor ?: ""} @ $offHex",
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
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
          shape = RoundedCornerShape(24.dp),
          color = MaterialTheme.colorScheme.surfaceContainer,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        ) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
              text = "Try/Catch Spans & Static Field Values",
              style = MaterialTheme.typography.titleMedium,
            )
            tryBlocks.forEach { tb ->
              val catches = tb.handlers.joinToString(", ") { h ->
                val hOff = "0x%04X".format(h.handlerOffsetWord)
                "${h.typeDescriptor ?: "<all>"}@$hOff"
              }
              val spanHex = "[0x%04X..0x%04X)".format(tb.startOffsetWord, tb.endOffsetWord)
              Text(
                text = "try $spanHex -> $catches",
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
  ann: com.dex2c.studio.dex.DexAnnotationEntry,
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
