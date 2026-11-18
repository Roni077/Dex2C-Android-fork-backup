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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dex2c.studio.dex.DexClassInfo
import com.dex2c.studio.ui.theme.JetBrainsMonoFontFamily

/**
 * Modal bottom sheet that lists loaded DEX classes to open any class
 * in a new tab within the Class Viewer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassPickerSheet(
  classes: List<DexClassInfo>,
  selectedMethodIds: Set<String>,
  onSelectClass: (DexClassInfo) -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var searchQuery by rememberSaveable { mutableStateOf("") }
  var showOnlySelected by rememberSaveable { mutableStateOf(false) }

  val filteredClasses = remember(classes, searchQuery, showOnlySelected, selectedMethodIds) {
    var result = classes
    if (showOnlySelected) {
      result = result.filter { cls ->
        val methods = (cls.directMethods + cls.virtualMethods).ifEmpty { cls.allMethods }
        methods.any { it.id in selectedMethodIds }
      }
    }
    if (searchQuery.isNotBlank()) {
      val q = searchQuery.trim().lowercase()
      result = result.filter {
        it.simpleName.lowercase().contains(q) ||
          it.packageName.lowercase().contains(q) ||
          it.descriptor.lowercase().contains(q)
      }
    }
    result
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    modifier = modifier.testTag("class_picker_sheet"),
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
      // Header
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column {
          Text(
            text = "Open Class in Viewer",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = "Pick a class from the loaded DEX bundle",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Text(
          text = "${filteredClasses.size} of ${classes.size}",
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
        placeholder = { Text("Search classes...", style = MaterialTheme.typography.bodySmall) },
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
          .testTag("class_picker_search"),
      )

      // Filter Chips
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        FilterChip(
          selected = !showOnlySelected,
          onClick = { showOnlySelected = false },
          label = { Text("All Classes (${classes.size})") },
        )
        FilterChip(
          selected = showOnlySelected,
          onClick = { showOnlySelected = true },
          label = { Text("Selected for Dex2C") },
        )
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

      // Class List
      LazyColumn(
        modifier = Modifier
          .fillMaxWidth()
          .height(380.dp)
          .testTag("class_picker_list"),
        contentPadding = PaddingValues(vertical = 4.dp),
      ) {
        itemsIndexed(filteredClasses, key = { _, cls -> cls.fullName }) { idx, cls ->
          val methods = (cls.directMethods + cls.virtualMethods).ifEmpty { cls.allMethods }
          val selectedInClass = methods.count { it.id in selectedMethodIds }
          val totalMethods = methods.size
          val isAllSelected = totalMethods > 0 && selectedInClass == totalMethods
          val isPartial = selectedInClass in 1 until totalMethods

          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable { onSelectClass(cls) }
              .padding(vertical = 6.dp, horizontal = 4.dp)
              .testTag("class_picker_item_$idx"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = cls.simpleName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text = cls.packageName.ifBlank { "default package" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }

            // Selection Badge
            when {
              isAllSelected -> {
                Surface(
                  shape = RoundedCornerShape(6.dp),
                  color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                  ) {
                    Icon(
                      imageVector = Icons.Rounded.Check,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.onPrimaryContainer,
                      modifier = Modifier.size(12.dp),
                    )
                    Text(
                      text = "$totalMethods m",
                      style = MaterialTheme.typography.labelSmall,
                      fontSize = 11.sp,
                      fontWeight = FontWeight.Bold,
                      fontFamily = JetBrainsMonoFontFamily,
                      color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                  }
                }
              }
              isPartial -> {
                Surface(
                  shape = RoundedCornerShape(6.dp),
                  color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                  Text(
                    text = "$selectedInClass/$totalMethods m",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                  )
                }
              }
              else -> {
                Text(
                  text = "$totalMethods m",
                  style = MaterialTheme.typography.labelSmall,
                  fontSize = 11.sp,
                  fontFamily = JetBrainsMonoFontFamily,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }

          if (idx < filteredClasses.lastIndex) {
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
