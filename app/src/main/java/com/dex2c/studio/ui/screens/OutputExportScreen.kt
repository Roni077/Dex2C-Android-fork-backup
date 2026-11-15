package com.dex2c.studio.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dex2c.studio.R
import com.dex2c.studio.transpiler.GeneratedBundle
import com.dex2c.studio.transpiler.TranspileProgress
import com.dex2c.studio.ui.components.SyntaxCodeViewer
import com.dex2c.studio.ui.components.TagPill
import java.util.Locale

@Composable
fun OutputExportScreen(
  bundle: GeneratedBundle?,
  isTranspiling: Boolean,
  onCopyFile: (String) -> Unit,
  onExportZip: () -> Unit,
  onGoToClasses: () -> Unit,
  modifier: Modifier = Modifier,
  transpileProgress: TranspileProgress? = null,
) {
  var selectedFileIndex by rememberSaveable { mutableIntStateOf(0) }

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(
      modifier = Modifier.fillMaxSize().widthIn(max = 920.dp).padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      if (isTranspiling) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Column(
            modifier = Modifier.widthIn(max = 360.dp).padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text("Lowering Dalvik bytecode to C/JNI…", style = MaterialTheme.typography.titleMedium)
            if (transpileProgress != null && transpileProgress.totalMethods > 0) {
              Spacer(Modifier.height(10.dp))
              LinearProgressIndicator(
                progress = { transpileProgress.fraction },
                modifier = Modifier.fillMaxWidth().testTag("transpile_progress_bar"),
              )
              Spacer(Modifier.height(6.dp))
              val chunkNote =
                if (transpileProgress.totalChunks > 1) {
                  " • C/C++ Unit ${transpileProgress.activeChunkIndex}/${transpileProgress.totalChunks}"
                } else {
                  ""
                }
              Text(
                text = "${transpileProgress.completedMethods} / ${transpileProgress.totalMethods} methods$chunkNote",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      } else if (bundle == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Surface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 460.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
          ) {
            Column(
              modifier = Modifier.padding(24.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              Icon(
                imageVector = Icons.Rounded.Code,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp),
              )
              Spacer(Modifier.height(12.dp))
              Text(
                text = stringResource(R.string.export_empty_title),
                style = MaterialTheme.typography.titleLarge,
              )
              Spacer(Modifier.height(8.dp))
              Text(
                text = stringResource(R.string.export_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Spacer(Modifier.height(16.dp))
              Button(
                onClick = onGoToClasses,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.testTag("export_go_classes_btn"),
              ) {
                Text("Select Methods")
              }
            }
          }
        }
      } else {
        val files = bundle.allFiles
        val safeIdx = selectedFileIndex.coerceIn(0, files.lastIndex)
        val activeFile = files[safeIdx]

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TagPill(
              text = "${bundle.transpiledMethodCount} methods",
              containerColor = MaterialTheme.colorScheme.primaryContainer,
              contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            TagPill(
              text = "${bundle.instructionCount} Dalvik ops",
              containerColor = MaterialTheme.colorScheme.secondaryContainer,
              contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            if (bundle.sourceFiles.size > 1) {
              TagPill(
                text = "${bundle.sourceFiles.size} C/C++ units",
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
              )
            }
          }
          if (bundle.warnings.isNotEmpty()) {
            TagPill(
              text = "${bundle.warnings.size} warnings",
              containerColor = MaterialTheme.colorScheme.errorContainer,
              contentColor = MaterialTheme.colorScheme.error,
            )
          }
        }

        LazyRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.testTag("output_files_row"),
        ) {
          itemsIndexed(files) { idx, file ->
            FilterChip(
              selected = idx == safeIdx,
              onClick = { selectedFileIndex = idx },
              label = { Text(file.fileName) },
              modifier = Modifier.testTag("output_file_tab_${file.fileName}"),
            )
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = activeFile.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
          )
          Spacer(Modifier.width(8.dp))
          TagPill(
            text = formatOutputBytes(activeFile.totalByteSize),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        if (activeFile.isPreviewTruncated) {
          Surface(
            modifier = Modifier.fillMaxWidth().testTag("output_preview_truncated_banner"),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = Icons.Rounded.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(16.dp),
              )
              Spacer(Modifier.width(8.dp))
              Text(
                text = "Streamed preview window (${activeFile.totalLineCount} lines • ${formatOutputBytes(activeFile.totalByteSize)} on disk). Full file exports in ZIP.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
              )
            }
          }
        }

        SyntaxCodeViewer(
          code = activeFile.content,
          modifier = Modifier.weight(1f).fillMaxWidth(),
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          OutlinedButton(
            onClick = { onCopyFile(activeFile.content) },
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.weight(1f).testTag("copy_output_file_btn"),
            contentPadding = PaddingValues(vertical = 12.dp),
          ) {
            Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.action_copy_code))
          }
          Button(
            onClick = onExportZip,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.weight(1f).testTag("export_ndk_zip_btn"),
            contentPadding = PaddingValues(vertical = 12.dp),
          ) {
            Icon(Icons.Rounded.Archive, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.action_export_zip))
          }
        }
      }
    }
  }
}

private fun formatOutputBytes(bytes: Long): String {
  if (bytes < 1024L) return "$bytes B"
  val kb = bytes / 1024.0
  if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
  val mb = kb / 1024.0
  return String.format(Locale.US, "%.2f MB", mb)
}
