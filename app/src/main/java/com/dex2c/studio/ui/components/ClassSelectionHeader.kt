package com.dex2c.studio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dex2c.studio.dex.DexClassInfo
import com.dex2c.studio.ui.theme.JetBrainsMonoFontFamily

/**
 * High-visibility banner and controller making Class & Method Selection
 * the primary, unmistakable feature in the Class Viewer.
 */
@Composable
fun ClassSelectionHeader(
  dexClass: DexClassInfo,
  selectedMethodIds: Set<String>,
  onToggleSelectClass: () -> Unit,
  onSelectAllMethods: () -> Unit,
  onDeselectAllMethods: () -> Unit,
  onInvertMethodSelection: () -> Unit,
  onOpenMethodsSheet: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val bytecodeMethods = remember(dexClass) {
    (dexClass.directMethods + dexClass.virtualMethods).filter { it.hasBytecode }
      .ifEmpty { dexClass.allMethods }
  }
  val methodIds = remember(bytecodeMethods) { bytecodeMethods.map { it.id }.toSet() }
  val totalCount = methodIds.size
  val selectedCount = remember(methodIds, selectedMethodIds) {
    methodIds.count { it in selectedMethodIds }
  }

  val toggleableState = when {
    totalCount == 0 -> ToggleableState.Off
    selectedCount == totalCount -> ToggleableState.On
    selectedCount > 0 -> ToggleableState.Indeterminate
    else -> ToggleableState.Off
  }

  Card(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 12.dp, vertical = 4.dp)
      .testTag("class_selection_header"),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ),
    shape = RoundedCornerShape(14.dp),
    border = androidx.compose.foundation.BorderStroke(
      width = 1.dp,
      color = if (selectedCount > 0) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
      } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
      },
    ),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(10.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      // Main Selection Status Row
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        TriStateCheckbox(
          state = toggleableState,
          onClick = onToggleSelectClass,
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("class_selection_checkbox"),
        )

        Column(modifier = Modifier.weight(1f)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Text(
              text = "Dex2C Transpilation",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface,
            )
            Surface(
              shape = RoundedCornerShape(6.dp),
              color = if (selectedCount > 0) {
                MaterialTheme.colorScheme.primaryContainer
              } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
              },
            ) {
              Text(
                text = "$selectedCount / $totalCount methods",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.SemiBold,
                color = if (selectedCount > 0) {
                  MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                  MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
              )
            }
          }

          Text(
            text = when {
              selectedCount == totalCount && totalCount > 0 -> "All methods queued for C transpilation"
              selectedCount > 0 -> "$selectedCount method(s) queued for C transpilation"
              else -> "No methods queued (Smali will remain Dalvik bytecode)"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }

        // Methods Sheet Button
        FilledTonalButton(
          onClick = onOpenMethodsSheet,
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier
            .height(32.dp)
            .testTag("class_open_methods_sheet_btn"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Rounded.List,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
          )
          Spacer(Modifier.width(4.dp))
          Text(
            text = "Methods ($totalCount)",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
          )
        }
      }

      // Fast Selection Action Buttons (All, None, Invert)
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        TextButton(
          onClick = onSelectAllMethods,
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
          modifier = Modifier
            .height(28.dp)
            .testTag("class_select_all_btn"),
        ) {
          Text("Select All", style = MaterialTheme.typography.labelSmall)
        }

        TextButton(
          onClick = onDeselectAllMethods,
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
          modifier = Modifier
            .height(28.dp)
            .testTag("class_deselect_all_btn"),
        ) {
          Text("Deselect All", style = MaterialTheme.typography.labelSmall)
        }

        TextButton(
          onClick = onInvertMethodSelection,
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
          modifier = Modifier
            .height(28.dp)
            .testTag("class_invert_selection_btn"),
        ) {
          Icon(
            imageVector = Icons.Rounded.SwapHoriz,
            contentDescription = null,
            modifier = Modifier.size(13.dp),
          )
          Spacer(Modifier.width(3.dp))
          Text("Invert", style = MaterialTheme.typography.labelSmall)
        }
      }
    }
  }
}
