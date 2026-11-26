package com.dex2c.studio.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dex2c.studio.dex.DexAnnotationEntry
import com.dex2c.studio.dex.DexMethodStructuralMeta
import com.dex2c.studio.dex.DexTryBlock
import com.dex2c.studio.dex.descriptorToJavaName
import com.dex2c.studio.ui.theme.JetBrainsMonoFontFamily

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
  ann: DexAnnotationEntry,
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
