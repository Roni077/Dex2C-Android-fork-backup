package com.example.ui.screens

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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import com.example.R
import com.example.transpiler.GeneratedBundle
import com.example.ui.components.SyntaxCodeViewer
import com.example.ui.components.TagPill

@Composable
fun OutputExportScreen(
  bundle: GeneratedBundle?,
  isTranspiling: Boolean,
  onCopyFile: (String) -> Unit,
  onExportZip: () -> Unit,
  onGoToClasses: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var selectedFileIndex by rememberSaveable { mutableIntStateOf(0) }

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(
      modifier = Modifier.fillMaxSize().widthIn(max = 920.dp).padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      if (isTranspiling) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text("Lowering Dalvik bytecode to C/JNI…", style = MaterialTheme.typography.titleMedium)
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
          }
          if (bundle.warnings.isNotEmpty()) {
            TagPill(
              text = "${bundle.warnings.size} warnings",
              containerColor = MaterialTheme.colorScheme.errorContainer,
              contentColor = MaterialTheme.colorScheme.error,
            )
          }
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          itemsIndexed(files) { idx, file ->
            FilterChip(
              selected = idx == safeIdx,
              onClick = { selectedFileIndex = idx },
              label = { Text(file.fileName) },
              modifier = Modifier.testTag("output_file_tab_${file.fileName}"),
            )
          }
        }

        Text(
          text = activeFile.description,
          style = MaterialTheme.typography.bodyMedium,
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
