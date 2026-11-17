package com.dex2c.studio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dex2c.studio.ui.editor.ClassViewerTab
import com.dex2c.studio.ui.theme.JetBrainsMonoFontFamily

/**
 * Multi-Class Tabs component for Class Viewer & Selection Studio.
 * Allows switching between multiple loaded DEX classes, showing selection state badges,
 * dirty modification state, tab close, and an add tab button.
 */
@Composable
fun ClassViewerTabBar(
  tabs: List<ClassViewerTab>,
  activeTabId: String?,
  selectedMethodIds: Set<String>,
  onSelectTab: (String) -> Unit,
  onCloseTab: (String) -> Unit,
  onCloseOtherTabs: (String) -> Unit,
  onCloseAllTabs: () -> Unit,
  onOpenNewTabClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var showMenuForTabId by remember { mutableStateOf<String?>(null) }

  Surface(
    modifier = modifier.fillMaxWidth(),
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    tonalElevation = 1.dp,
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState())
        .padding(horizontal = 8.dp, vertical = 4.dp),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      tabs.forEach { tab ->
        val isActive = tab.id == activeTabId
        val classMethods = (tab.dexClass.directMethods + tab.dexClass.virtualMethods).ifEmpty { tab.dexClass.allMethods }
        val methodIds = classMethods.map { it.id }.toSet()
        val selectedCount = methodIds.count { it in selectedMethodIds }
        val totalCount = methodIds.size
        val isAllSelected = totalCount > 0 && selectedCount == totalCount
        val isPartiallySelected = selectedCount in 1 until totalCount

        val backgroundColor = if (isActive) {
          MaterialTheme.colorScheme.primaryContainer
        } else {
          MaterialTheme.colorScheme.surfaceContainer
        }

        val contentColor = if (isActive) {
          MaterialTheme.colorScheme.onPrimaryContainer
        } else {
          MaterialTheme.colorScheme.onSurface
        }

        val borderColor = if (isActive) {
          MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        } else {
          MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        }

        Box {
          Row(
            modifier = Modifier
              .testTag("class_tab_${tab.id}")
              .clip(RoundedCornerShape(10.dp))
              .background(backgroundColor)
              .border(1.dp, borderColor, RoundedCornerShape(10.dp))
              .clickable { onSelectTab(tab.id) }
              .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            // Selection Badge
            when {
              isAllSelected -> {
                Box(
                  modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                  contentAlignment = Alignment.Center,
                ) {
                  Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = "All methods selected for Dex2C",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(11.dp),
                  )
                }
              }
              isPartiallySelected -> {
                Text(
                  text = "$selectedCount/$totalCount",
                  style = MaterialTheme.typography.labelSmall,
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.primary,
                  fontFamily = JetBrainsMonoFontFamily,
                  modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                )
              }
              else -> {
                Text(
                  text = "0/$totalCount",
                  style = MaterialTheme.typography.labelSmall,
                  fontSize = 10.sp,
                  color = contentColor.copy(alpha = 0.6f),
                  fontFamily = JetBrainsMonoFontFamily,
                )
              }
            }

            // Class Title
            Text(
              text = buildString {
                append(tab.title)
                if (tab.isDirty) append(" *")
              },
              style = MaterialTheme.typography.labelMedium,
              fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
              color = contentColor,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier
                .testTag("class_tab_title_${tab.id}")
                .clickable { onSelectTab(tab.id) },
            )

            // Context Menu Button
            IconButton(
              onClick = { showMenuForTabId = tab.id },
              modifier = Modifier
                .size(20.dp)
                .testTag("class_tab_menu_${tab.id}"),
            ) {
              Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = "Tab options",
                tint = contentColor.copy(alpha = 0.7f),
                modifier = Modifier.size(14.dp),
              )
            }

            // Close Tab Button
            IconButton(
              onClick = { onCloseTab(tab.id) },
              modifier = Modifier
                .size(20.dp)
                .testTag("class_tab_close_${tab.id}"),
            ) {
              Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Close ${tab.title}",
                tint = contentColor.copy(alpha = 0.7f),
                modifier = Modifier.size(14.dp),
              )
            }
          }

          // Dropdown Context Menu
          DropdownMenu(
            expanded = showMenuForTabId == tab.id,
            onDismissRequest = { showMenuForTabId = null },
          ) {
            DropdownMenuItem(
              text = { Text("Close Tab") },
              onClick = {
                showMenuForTabId = null
                onCloseTab(tab.id)
              },
            )
            DropdownMenuItem(
              text = { Text("Close Others") },
              onClick = {
                showMenuForTabId = null
                onCloseOtherTabs(tab.id)
              },
            )
            DropdownMenuItem(
              text = { Text("Close All Tabs") },
              onClick = {
                showMenuForTabId = null
                onCloseAllTabs()
              },
            )
          }
        }
      }

      // Add New Class Tab Button
      IconButton(
        onClick = onOpenNewTabClick,
        modifier = Modifier
          .height(32.dp)
          .width(32.dp)
          .clip(RoundedCornerShape(8.dp))
          .background(MaterialTheme.colorScheme.surfaceContainerHigh)
          .testTag("class_tab_add_btn"),
      ) {
        Icon(
          imageVector = Icons.Rounded.Add,
          contentDescription = "Open class in new tab",
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(18.dp),
        )
      }
    }
  }
}
