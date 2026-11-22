package com.dex2c.studio.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dex2c.studio.R
import com.dex2c.studio.dex.DexClassInfo
import com.dex2c.studio.dex.DexEncodedMethod
import com.dex2c.studio.dex.SmaliMethodSpan
import com.dex2c.studio.dex.formatAccessFlags
import com.dex2c.studio.ui.theme.JetBrainsMonoFontFamily

/**
 * Modal bottom sheet dedicated to granular method selection and navigation
 * within the active class in the Class Viewer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassMethodsSelectorSheet(
  dexClass: DexClassInfo,
  methodSpans: List<SmaliMethodSpan>,
  selectedMethodIds: Set<String>,
  onToggleMethodSelection: (String) -> Unit,
  onSelectAllMethods: () -> Unit,
  onDeselectAllMethods: () -> Unit,
  onInvertMethodSelection: () -> Unit,
  onJumpToSpan: (SmaliMethodSpan) -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
  onInspectMethodC: (DexEncodedMethod) -> Unit = {},
) {
  var searchQuery by rememberSaveable { mutableStateOf("") }
  val allMethods = remember(dexClass) { dexClass.allMethods }

  // Pair methods with spans
  val methodItems = remember(allMethods, methodSpans) {
    if (methodSpans.isNotEmpty()) {
      methodSpans.mapIndexed { idx, span ->
        val matchedMethod = allMethods.getOrNull(idx)?.takeIf { it.methodRef.name == span.name }
          ?: allMethods.firstOrNull { "${it.methodRef.name}${it.methodRef.jniSignature()}" == span.signature }
          ?: allMethods.firstOrNull { it.methodRef.name == span.name }
        MethodSelectorItem(
          span = span,
          method = matchedMethod,
          displayName = span.name,
          signature = span.signature,
          methodId = matchedMethod?.id ?: "${dexClass.descriptor}->${span.name}()V",
          startLine = span.startLineIndex + 1,
          endLine = span.endLineIndex + 1,
        )
      }
    } else {
      allMethods.map { m ->
        MethodSelectorItem(
          span = null,
          method = m,
          displayName = m.methodRef.name,
          signature = "${m.methodRef.name}${m.methodRef.jniSignature()}",
          methodId = m.id,
          startLine = -1,
          endLine = -1,
        )
      }
    }
  }

  val filteredItems = remember(methodItems, searchQuery) {
    if (searchQuery.isBlank()) {
      methodItems
    } else {
      val q = searchQuery.trim().lowercase()
      methodItems.filter {
        it.displayName.lowercase().contains(q) || it.signature.lowercase().contains(q)
      }
    }
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    modifier = modifier.testTag("class_methods_selector_sheet"),
  ) {
    Box(
      modifier = Modifier.fillMaxWidth(),
      contentAlignment = Alignment.TopCenter,
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .widthIn(max = 600.dp)
          .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
      // Header Title & Count
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column {
          Text(
            text = "${dexClass.simpleName} Methods",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = "Select methods to transpile into native C via Dex2C",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        val total = methodItems.size
        val selected = methodItems.count { it.methodId in selectedMethodIds }
        Text(
          text = "$selected / $total selected",
          style = MaterialTheme.typography.labelMedium,
          fontFamily = JetBrainsMonoFontFamily,
          color = MaterialTheme.colorScheme.primary,
        )
      }

      // Search Field
      OutlinedTextField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        singleLine = true,
        placeholder = { Text(stringResource(R.string.selector_search_hint), style = MaterialTheme.typography.bodySmall) },
        leadingIcon = {
          Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp))
        },
        trailingIcon = {
          if (searchQuery.isNotEmpty()) {
            IconButton(
              onClick = { searchQuery = "" },
              modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
              Icon(Icons.Rounded.Clear, contentDescription = "Clear search", modifier = Modifier.size(16.dp))
            }
          }
        },
        modifier = Modifier
          .fillMaxWidth()
          .testTag("methods_search_input"),
      )

      // Fast Selection Buttons (All, None, Invert)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        TextButton(
          onClick = onSelectAllMethods,
          contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
          modifier = Modifier.height(28.dp),
        ) {
          Text(stringResource(R.string.selector_select_all), style = MaterialTheme.typography.labelSmall)
        }
        TextButton(
          onClick = onDeselectAllMethods,
          contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
          modifier = Modifier.height(28.dp),
        ) {
          Text(stringResource(R.string.selector_deselect_all), style = MaterialTheme.typography.labelSmall)
        }
        TextButton(
          onClick = onInvertMethodSelection,
          contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
          modifier = Modifier.height(28.dp),
        ) {
          Text(stringResource(R.string.selector_invert_selection), style = MaterialTheme.typography.labelSmall)
        }
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

      // Methods List
      LazyColumn(
        modifier = Modifier
          .fillMaxWidth()
          .height(380.dp)
          .testTag("class_methods_selector_list"),
        contentPadding = PaddingValues(vertical = 4.dp),
      ) {
        itemsIndexed(filteredItems, key = { _, item -> item.methodId }) { idx, item ->
          val isChecked = item.methodId in selectedMethodIds

          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable { onToggleMethodSelection(item.methodId) }
              .padding(vertical = 4.dp, horizontal = 2.dp)
              .testTag("method_item_$idx"),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Checkbox(
              checked = isChecked,
              onCheckedChange = { onToggleMethodSelection(item.methodId) },
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("method_checkbox_$idx"),
            )

            Column(
              modifier = Modifier
                .weight(1f)
                .clickable {
                  item.span?.let { onJumpToSpan(it) }
                },
            ) {
              Text(
                text = item.signature,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                if (item.startLine > 0) {
                  Text(
                    text = "[L${item.startLine} - L${item.endLine}]",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    fontFamily = JetBrainsMonoFontFamily,
                    color = MaterialTheme.colorScheme.primary,
                  )
                }
                item.method?.let { m ->
                  val flags = formatAccessFlags(m.accessFlags, isMethod = true)
                  if (flags.isNotBlank()) {
                    Text(
                      text = flags,
                      style = MaterialTheme.typography.labelSmall,
                      fontSize = 10.sp,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }
              }
            }

            if (item.span != null) {
              TextButton(
                onClick = { onJumpToSpan(item.span) },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                modifier = Modifier
                  .height(28.dp)
                  .testTag("method_jump_btn_$idx"),
              ) {
                Text(stringResource(R.string.selector_jump), style = MaterialTheme.typography.labelSmall)
              }
            }
          }

          if (idx < filteredItems.lastIndex) {
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

private data class MethodSelectorItem(
  val span: SmaliMethodSpan?,
  val method: DexEncodedMethod?,
  val displayName: String,
  val signature: String,
  val methodId: String,
  val startLine: Int,
  val endLine: Int,
)
