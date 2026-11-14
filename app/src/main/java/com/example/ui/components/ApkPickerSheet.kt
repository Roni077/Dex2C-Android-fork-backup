package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.apk.AppScopeFilter
import com.example.apk.BinarySourceType
import com.example.apk.InstalledAppItem
import com.example.apk.PickerSortOrder
import com.example.apk.PickerTab
import com.example.apk.RecentBinaryItem
import com.example.apk.RecentSourceFilter
import com.example.ui.theme.JetBrainsMonoFontFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApkPickerSheet(
  activeTab: PickerTab,
  installedApps: List<InstalledAppItem>,
  isLoadingApps: Boolean,
  appSearchQuery: String,
  recentFiles: List<RecentBinaryItem>,
  onSelectTab: (PickerTab) -> Unit,
  onBrowseStorage: () -> Unit,
  onInspectSelfApk: () -> Unit,
  onAppSearchChange: (String) -> Unit,
  onSelectInstalledApp: (InstalledAppItem) -> Unit,
  onSelectRecent: (RecentBinaryItem) -> Unit,
  onRemoveRecent: (String) -> Unit,
  onClearRecent: () -> Unit,
  onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
    ) {
      Text(
        text = stringResource(R.string.picker_title),
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
      )
      Spacer(Modifier.height(8.dp))

      val tabIndex =
        when (activeTab) {
          PickerTab.STORAGE -> 0
          PickerTab.APPS -> 1
          PickerTab.RECENT -> 2
        }

      PrimaryTabRow(selectedTabIndex = tabIndex) {
        Tab(
          selected = activeTab == PickerTab.STORAGE,
          onClick = { onSelectTab(PickerTab.STORAGE) },
          text = { Text(stringResource(R.string.picker_tab_storage)) },
          icon = { Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp)) },
          modifier = Modifier.testTag("picker_tab_storage"),
        )
        Tab(
          selected = activeTab == PickerTab.APPS,
          onClick = { onSelectTab(PickerTab.APPS) },
          text = { Text(stringResource(R.string.picker_tab_apps)) },
          icon = { Icon(Icons.Rounded.Apps, contentDescription = null, modifier = Modifier.size(18.dp)) },
          modifier = Modifier.testTag("picker_tab_apps"),
        )
        Tab(
          selected = activeTab == PickerTab.RECENT,
          onClick = { onSelectTab(PickerTab.RECENT) },
          text = {
            val label =
              if (recentFiles.isNotEmpty()) "${stringResource(R.string.picker_tab_recent)} (${recentFiles.size})"
              else stringResource(R.string.picker_tab_recent)
            Text(label)
          },
          icon = { Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(18.dp)) },
          modifier = Modifier.testTag("picker_tab_recent"),
        )
      }

      Spacer(Modifier.height(14.dp))

      when (activeTab) {
        PickerTab.STORAGE -> {
          StoragePickerTab(
            onBrowseStorage = onBrowseStorage,
            onInspectSelfApk = onInspectSelfApk,
          )
        }

        PickerTab.APPS -> {
          InstalledAppsTab(
            apps = installedApps,
            isLoading = isLoadingApps,
            searchQuery = appSearchQuery,
            onSearchChange = onAppSearchChange,
            onSelectApp = onSelectInstalledApp,
          )
        }

        PickerTab.RECENT -> {
          RecentBinariesTab(
            items = recentFiles,
            onSelectRecent = onSelectRecent,
            onRemoveRecent = onRemoveRecent,
            onClearRecent = onClearRecent,
          )
        }
      }
    }
  }
}

@Composable
private fun StoragePickerTab(
  onBrowseStorage: () -> Unit,
  onInspectSelfApk: () -> Unit,
) {
  Column(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Surface(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(24.dp),
      color = MaterialTheme.colorScheme.surfaceContainer,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
    ) {
      Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(44.dp),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.Rounded.FolderOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
              )
            }
          }
          Column {
            Text(
              text = stringResource(R.string.picker_storage_title),
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              TagPill(".APK")
              TagPill(".DEX")
              TagPill("Multi-DEX")
            }
          }
        }

        Text(
          text = stringResource(R.string.picker_storage_desc),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Button(
          onClick = onBrowseStorage,
          shape = RoundedCornerShape(20.dp),
          modifier = Modifier.fillMaxWidth().testTag("picker_launch_saf_button"),
          contentPadding = PaddingValues(vertical = 13.dp),
        ) {
          Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text(stringResource(R.string.action_browse_storage))
        }
      }
    }

    OutlinedButton(
      onClick = onInspectSelfApk,
      shape = RoundedCornerShape(20.dp),
      modifier = Modifier.fillMaxWidth().testTag("picker_inspect_self_button"),
      contentPadding = PaddingValues(vertical = 12.dp),
    ) {
      Icon(Icons.Rounded.Android, contentDescription = null, modifier = Modifier.size(18.dp))
      Spacer(Modifier.width(8.dp))
      Text(stringResource(R.string.action_inspect_self_apk))
    }
  }
}

@Composable
private fun InstalledAppsTab(
  apps: List<InstalledAppItem>,
  isLoading: Boolean,
  searchQuery: String,
  onSearchChange: (String) -> Unit,
  onSelectApp: (InstalledAppItem) -> Unit,
) {
  var scopeFilter by remember { mutableStateOf(AppScopeFilter.ALL) }
  var sortOrder by remember { mutableStateOf(PickerSortOrder.NAME_ASC) }
  var showSortMenu by remember { mutableStateOf(false) }

  val displayedApps =
    remember(apps, scopeFilter, sortOrder) {
      val scoped =
        when (scopeFilter) {
          AppScopeFilter.ALL -> apps
          AppScopeFilter.USER_ONLY -> apps.filter { !it.isSystemApp }
          AppScopeFilter.SYSTEM_ONLY -> apps.filter { it.isSystemApp }
        }
      when (sortOrder) {
        PickerSortOrder.NAME_ASC -> scoped.sortedBy { it.appLabel.lowercase() }
        PickerSortOrder.NAME_DESC -> scoped.sortedByDescending { it.appLabel.lowercase() }
        PickerSortOrder.SIZE_DESC ->
          scoped.sortedWith(compareByDescending<InstalledAppItem> { it.apkSizeBytes }.thenBy { it.appLabel.lowercase() })
        PickerSortOrder.RECENT ->
          scoped.sortedWith(compareByDescending<InstalledAppItem> { it.lastUpdateTimeMillis }.thenBy { it.appLabel.lowercase() })
      }
    }

  Column(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    OutlinedTextField(
      value = searchQuery,
      onValueChange = onSearchChange,
      modifier = Modifier.fillMaxWidth().testTag("picker_apps_search"),
      placeholder = { Text(stringResource(R.string.picker_search_apps)) },
      leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
      trailingIcon = {
        if (searchQuery.isNotEmpty()) {
          IconButton(onClick = { onSearchChange("") }) {
            Icon(Icons.Rounded.Clear, contentDescription = "Clear filter")
          }
        }
      },
      singleLine = true,
      shape = RoundedCornerShape(18.dp),
    )

    LazyRow(
      modifier = Modifier.testTag("picker_apps_filter_row"),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      item {
        Box {
          FilterChip(
            selected = sortOrder != PickerSortOrder.NAME_ASC,
            onClick = { showSortMenu = true },
            leadingIcon = {
              Icon(
                imageVector = Icons.AutoMirrored.Rounded.Sort,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
              )
            },
            label = { Text("Sort: ${sortOrder.label}") },
            modifier = Modifier.testTag("picker_apps_sort_chip"),
          )
          DropdownMenu(
            expanded = showSortMenu,
            onDismissRequest = { showSortMenu = false },
          ) {
            PickerSortOrder.entries.forEach { order ->
              DropdownMenuItem(
                text = { Text(order.label) },
                trailingIcon = {
                  if (sortOrder == order) {
                    Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                  }
                },
                onClick = {
                  sortOrder = order
                  showSortMenu = false
                },
                modifier = Modifier.testTag("picker_sort_${order.name}"),
              )
            }
          }
        }
      }
      AppScopeFilter.entries.forEach { scope ->
        item {
          FilterChip(
            selected = scopeFilter == scope,
            onClick = { scopeFilter = scope },
            label = { Text(scope.label) },
            modifier = Modifier.testTag("picker_app_scope_${scope.name}"),
          )
        }
      }
    }

    if (isLoading) {
      Box(
        modifier = Modifier.fillMaxWidth().height(220.dp),
        contentAlignment = Alignment.Center,
      ) {
        CircularProgressIndicator()
      }
    } else if (displayedApps.isEmpty()) {
      Box(
        modifier = Modifier.fillMaxWidth().height(180.dp),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = stringResource(R.string.picker_apps_empty),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    } else {
      LazyColumn(
        modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 380.dp).testTag("picker_apps_list"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        items(displayedApps, key = { it.packageName }) { app ->
          InstalledAppRow(app = app, onClick = { onSelectApp(app) })
        }
      }
    }
  }
}

@Composable
private fun InstalledAppRow(
  app: InstalledAppItem,
  onClick: () -> Unit,
) {
  val bitmap = remember(app.iconBitmap) { app.iconBitmap?.asImageBitmap() }

  Surface(
    modifier =
      Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(18.dp))
        .clickable(onClick = onClick)
        .testTag("installed_app_${app.packageName}"),
    shape = RoundedCornerShape(18.dp),
    color = MaterialTheme.colorScheme.surfaceContainer,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (bitmap != null) {
        Image(
          bitmap = bitmap,
          contentDescription = "${app.appLabel} icon",
          modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)),
        )
      } else {
        Box(
          modifier =
            Modifier.size(40.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(MaterialTheme.colorScheme.primaryContainer),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            Icons.Rounded.Android,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
          )
        }
      }

      Spacer(Modifier.width(12.dp))

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = app.appLabel,
          style = MaterialTheme.typography.titleMedium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = app.packageName,
          style = MaterialTheme.typography.labelSmall,
          fontFamily = JetBrainsMonoFontFamily,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Row(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "v${app.versionName} • ${formatApkSize(app.apkSizeBytes)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
          )
          TagPill(text = if (app.isSystemApp) "Sys" else "User")
        }
      }

      Icon(
        imageVector = Icons.Rounded.ChevronRight,
        contentDescription = "Preview APK",
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
internal fun RecentBinariesTab(
  items: List<RecentBinaryItem>,
  onSelectRecent: (RecentBinaryItem) -> Unit,
  onRemoveRecent: (String) -> Unit,
  onClearRecent: () -> Unit,
) {
  var sourceFilter by remember { mutableStateOf(RecentSourceFilter.ALL) }
  var sortOrder by remember { mutableStateOf(PickerSortOrder.RECENT) }
  var showSortMenu by remember { mutableStateOf(false) }

  val displayedItems =
    remember(items, sourceFilter, sortOrder) {
      val filtered =
        when (sourceFilter) {
          RecentSourceFilter.ALL -> items
          RecentSourceFilter.STORAGE -> items.filter { it.sourceType == BinarySourceType.SAF_URI }
          RecentSourceFilter.APPS -> items.filter { it.sourceType == BinarySourceType.INSTALLED_APK }
        }
      when (sortOrder) {
        PickerSortOrder.RECENT -> filtered.sortedByDescending { it.lastOpenedMillis }
        PickerSortOrder.NAME_ASC -> filtered.sortedBy { it.displayName.lowercase() }
        PickerSortOrder.NAME_DESC -> filtered.sortedByDescending { it.displayName.lowercase() }
        PickerSortOrder.SIZE_DESC ->
          filtered.sortedWith(compareByDescending<RecentBinaryItem> { it.byteSize }.thenBy { it.displayName.lowercase() })
      }
    }

  Column(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    if (items.isEmpty()) {
      Box(
        modifier = Modifier.fillMaxWidth().height(180.dp),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = stringResource(R.string.picker_recent_empty),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    } else {
      val countSummary =
        if (displayedItems.size == items.size) {
          "${items.size} saved ${if (items.size == 1) "binary" else "binaries"}"
        } else {
          "${displayedItems.size} of ${items.size} shown"
        }
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = countSummary,
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.testTag("picker_recent_count_label"),
        )
        TextButton(
          onClick = onClearRecent,
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
          modifier = Modifier.minimumInteractiveComponentSize().testTag("clear_recent_history_btn"),
        ) {
          Icon(
            imageVector = Icons.Rounded.DeleteOutline,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
          Spacer(Modifier.width(4.dp))
          Text(
            text = stringResource(R.string.picker_clear_recent),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
      LazyRow(
        modifier = Modifier.fillMaxWidth().testTag("picker_recent_filter_row"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        item {
          Box {
            FilterChip(
              selected = sortOrder != PickerSortOrder.RECENT,
              onClick = { showSortMenu = true },
              leadingIcon = {
                Icon(
                  imageVector = Icons.AutoMirrored.Rounded.Sort,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                )
              },
              label = { Text("Sort: ${sortOrder.label}") },
              modifier = Modifier.testTag("picker_recent_sort_chip"),
            )
            DropdownMenu(
              expanded = showSortMenu,
              onDismissRequest = { showSortMenu = false },
            ) {
              PickerSortOrder.entries.forEach { order ->
                DropdownMenuItem(
                  text = { Text(order.label) },
                  trailingIcon = {
                    if (sortOrder == order) {
                      Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                  },
                  onClick = {
                    sortOrder = order
                    showSortMenu = false
                  },
                  modifier = Modifier.testTag("picker_recent_sort_${order.name}"),
                )
              }
            }
          }
        }
        RecentSourceFilter.entries.forEach { src ->
          item {
            FilterChip(
              selected = sourceFilter == src,
              onClick = { sourceFilter = src },
              label = { Text(src.label) },
              modifier = Modifier.testTag("picker_recent_filter_${src.name}"),
            )
          }
        }
      }
      if (displayedItems.isEmpty()) {
        Box(
          modifier = Modifier.fillMaxWidth().height(160.dp),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = "No recent binaries match this source filter.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 360.dp).testTag("picker_recent_list"),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          items(displayedItems, key = { it.id }) { item ->
            Surface(
              modifier =
                Modifier.fillMaxWidth()
                  .clip(RoundedCornerShape(18.dp))
                  .clickable { onSelectRecent(item) }
                  .testTag("recent_item_${item.id}"),
              shape = RoundedCornerShape(18.dp),
              color = MaterialTheme.colorScheme.surfaceContainer,
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
            ) {
              Row(
                modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(
                  imageVector =
                    if (item.sourceType == BinarySourceType.INSTALLED_APK) Icons.Rounded.Android
                    else Icons.Rounded.FolderOpen,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                  Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TagPill(item.sourceType.label)
                    if (item.byteSize > 0) {
                      TagPill(formatApkSize(item.byteSize))
                    }
                  }
                  if (!item.packageName.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                      text = item.packageName,
                      style = MaterialTheme.typography.labelSmall,
                      fontFamily = JetBrainsMonoFontFamily,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                  }
                }
                IconButton(
                  onClick = { onRemoveRecent(item.id) },
                  modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                  Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "Remove from history",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}

internal fun formatApkSize(bytes: Long): String {
  return when {
    bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024f * 1024f))
    bytes >= 1024 -> "%.1f KB".format(bytes / 1024f)
    else -> "$bytes B"
  }
}
