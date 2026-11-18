package com.dex2c.studio.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Class
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dex2c.studio.dex.DexClassInfo
import com.dex2c.studio.transpiler.FilterRuleEngine
import com.dex2c.studio.ui.theme.JetBrainsMonoFontFamily

/**
 * Modal dialog that presents the user's selected classes and their auto-generated
 * filter.txt rules, with 1-tap synchronization into the transpiler configuration.
 */
@Composable
fun SelectedClassesFilterDialog(
  visible: Boolean,
  selectedClasses: List<DexClassInfo>,
  selectedMethodIds: Set<String>,
  allowInit: Boolean,
  onDismiss: () -> Unit,
  onConfirmSync: (isExclude: Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  if (!visible) return

  var isExclude by remember { mutableStateOf(false) }

  val generatedRules =
    remember(selectedClasses, selectedMethodIds, allowInit, isExclude) {
      FilterRuleEngine.generateWildcardRulesFromSelection(
        classes = selectedClasses,
        selectedMethodIds = selectedMethodIds,
        allowInit = allowInit,
        isBlacklist = isExclude,
      )
    }

  val generatedRuleLines = remember(generatedRules) { generatedRules.map { it.toFilterLine() } }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.FilterAlt,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
          )
          Column {
            Text(
              text = "Selected Classes Filter",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = "${selectedClasses.size} class(es) • ${selectedMethodIds.size} method(s)",
              style = MaterialTheme.typography.labelSmall,
              fontFamily = JetBrainsMonoFontFamily,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        IconButton(
          onClick = onDismiss,
          modifier = Modifier.size(28.dp).testTag("selected_classes_dialog_close_btn"),
        ) {
          Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = "Close dialog",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
          )
        }
      }
    },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        // Toggle: Include (Protect) vs Exclude (!)
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          FilterChip(
            selected = !isExclude,
            onClick = { isExclude = false },
            leadingIcon = {
              Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
              )
            },
            label = { Text("Include / Protect") },
            modifier = Modifier.testTag("dialog_chip_include"),
          )
          FilterChip(
            selected = isExclude,
            onClick = { isExclude = true },
            leadingIcon = {
              Icon(
                imageVector = Icons.Rounded.Block,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
              )
            },
            label = { Text("Exclude (!)") },
            modifier = Modifier.testTag("dialog_chip_exclude"),
          )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        // Generated filter.txt rule preview
        Text(
          text = "Generated Rules (${generatedRuleLines.size}):",
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.primary,
        )

        Surface(
          shape = RoundedCornerShape(10.dp),
          color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier =
              Modifier.fillMaxWidth()
                .padding(8.dp)
                .horizontalScroll(rememberScrollState()),
          ) {
            if (generatedRuleLines.isEmpty()) {
              Text(
                text = "No rules generated from current selection.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            } else {
              generatedRuleLines.forEach { line ->
                Text(
                  text = line,
                  style = MaterialTheme.typography.labelSmall,
                  fontFamily = JetBrainsMonoFontFamily,
                  color =
                    if (line.startsWith("!")) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface,
                  modifier = Modifier.testTag("generated_rule_line"),
                )
              }
            }
          }
        }

        // Selected Classes List
        Text(
          text = "Classes Included in Sync:",
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurface,
        )

        Surface(
          shape = RoundedCornerShape(12.dp),
          color = MaterialTheme.colorScheme.surfaceContainerLow,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
          modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 220.dp),
        ) {
          LazyColumn(
            modifier = Modifier.fillMaxWidth().testTag("selected_classes_dialog_list"),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            items(selectedClasses, key = { it.uniqueKey }) { dexClass ->
              Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  Icon(
                    imageVector = Icons.Rounded.Class,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                  )
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = dexClass.simpleName,
                      style = MaterialTheme.typography.labelMedium,
                      fontFamily = JetBrainsMonoFontFamily,
                      fontWeight = FontWeight.Bold,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                      text = dexClass.packageName.ifBlank { "(default package)" },
                      style = MaterialTheme.typography.labelSmall,
                      fontFamily = JetBrainsMonoFontFamily,
                      fontSize = 10.sp,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                  }
                  Text(
                    text = "${dexClass.bytecodeMethodCount} mtds",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.secondary,
                  )
                }
              }
            }
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = { onConfirmSync(isExclude) },
        enabled = generatedRuleLines.isNotEmpty(),
        modifier = Modifier.testTag("dialog_sync_filter_btn"),
      ) {
        Text("Sync to filter.txt")
      }
    },
    dismissButton = {
      OutlinedButton(
        onClick = onDismiss,
        modifier = Modifier.testTag("dialog_sync_filter_cancel_btn"),
      ) {
        Text("Cancel")
      }
    },
    modifier = modifier.testTag("selected_classes_filter_dialog"),
  )
}
