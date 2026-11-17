package com.dex2c.studio.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dex2c.studio.dex.SmaliEditorEngine
import com.dex2c.studio.ui.theme.JetBrainsMonoFontFamily

/**
 * Bottom sheet displaying all branch and exception labels in the current method/class
 * for quick navigation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmaliLabelJumpSheet(
  labels: List<SmaliEditorEngine.LabelJumpTarget>,
  onJumpToLabel: (Int) -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    modifier = modifier.testTag("smali_label_jump_sheet"),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 6.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
        text = "Branch Labels (${labels.size})",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
      )
      Text(
        text = "Tap any label to jump to its instruction in Smali",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

      if (labels.isEmpty()) {
        Text(
          text = "No labels found in this scope.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(vertical = 16.dp),
        )
      } else {
        LazyColumn(
          modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .testTag("smali_labels_list"),
          contentPadding = PaddingValues(vertical = 4.dp),
        ) {
          itemsIndexed(labels, key = { _, item -> "${item.label}_${item.lineIndex}" }) { idx, item ->
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clickable {
                  onJumpToLabel(item.lineIndex)
                  onDismiss()
                }
                .padding(vertical = 8.dp, horizontal = 4.dp)
                .testTag("smali_label_item_$idx"),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = item.label,
                style = MaterialTheme.typography.labelLarge,
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
              )
              Text(
                text = "Line ${item.lineNumber}",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = JetBrainsMonoFontFamily,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            if (idx < labels.lastIndex) {
              HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
              )
            }
          }
        }
      }
    }
  }
}
