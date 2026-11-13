package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.apk.BinarySourceType
import com.example.apk.PickerTab
import com.example.apk.RecentBinaryItem
import com.example.ui.DexLoadState
import com.example.ui.components.DexMapListCard
import com.example.ui.components.ExpressiveMotion
import com.example.ui.components.HeaderKeyValueRow
import com.example.ui.components.MetricTile
import com.example.ui.components.TagPill
import com.example.ui.components.expressivePressScale
import com.example.ui.components.formatApkSize

@Composable
fun WorkbenchScreen(
  loadState: DexLoadState,
  selectedMethodCount: Int,
  recentFiles: List<RecentBinaryItem>,
  onOpenPicker: (PickerTab) -> Unit,
  onInspectSelfApk: () -> Unit,
  onSelectRecent: (RecentBinaryItem) -> Unit,
  onSelectDexEntry: (String) -> Unit,
  onAutoSelectAppMethods: () -> Unit,
  onClearSelection: () -> Unit,
  onOpenRustBridge: () -> Unit,
  onNavigateToClasses: () -> Unit,
  onNavigateToExport: () -> Unit,
  modifier: Modifier = Modifier,
  onFocusHeaderDex: (String) -> Unit = {},
) {
  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier.fillMaxSize().widthIn(max = 840.dp),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      item {
        HeroWorkbenchBanner(
          onOpenPicker = { onOpenPicker(PickerTab.STORAGE) },
          onOpenInstalledApps = { onOpenPicker(PickerTab.APPS) },
        )
      }

      if (recentFiles.isNotEmpty() && loadState is DexLoadState.Empty) {
        item {
          RecentQuickStrip(
            recentFiles = recentFiles.take(5),
            onSelectRecent = onSelectRecent,
            onViewAll = { onOpenPicker(PickerTab.RECENT) },
          )
        }
      }

      when (loadState) {
        is DexLoadState.Empty -> {
          item {
            EmptyWorkspaceCard(
              onOpenPicker = { onOpenPicker(PickerTab.STORAGE) },
              onInspectSelfApk = onInspectSelfApk,
            )
          }
        }

        is DexLoadState.Loading -> {
          item {
            Surface(
              modifier = Modifier.fillMaxWidth().animateContentSize(ExpressiveMotion.spatialSpring()),
              shape = RoundedCornerShape(24.dp),
              color = MaterialTheme.colorScheme.surfaceContainer,
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
            ) {
              Row(
                modifier = Modifier.padding(24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
              ) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                Column {
                  Text(
                    text = stringResource(R.string.msg_loading_dex),
                    style = MaterialTheme.typography.titleMedium,
                  )
                  Text(
                    text = loadState.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }
          }
        }

        is DexLoadState.Error -> {
          item {
            Surface(
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(24.dp),
              color = MaterialTheme.colorScheme.errorContainer,
            ) {
              Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
              ) {
                Icon(
                  imageVector = Icons.Rounded.ErrorOutline,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.error,
                )
                Column(modifier = Modifier.weight(1f)) {
                  Text("DEX Parse Error", style = MaterialTheme.typography.titleMedium)
                  Text(loadState.message, style = MaterialTheme.typography.bodyMedium)
                }
              }
            }
          }
        }

        is DexLoadState.Ready -> {
          val dex = loadState.parsedDex
          val loadedDexList = loadState.loadedDexes.values.toList()
          val totalClasses = loadedDexList.sumOf { it.header.classDefsSize }
          val totalMethods = loadedDexList.sumOf { it.header.methodIdsSize }
          val totalStrings = loadedDexList.sumOf { it.header.stringIdsSize }
          val totalSizeBytes = loadedDexList.sumOf { it.header.fileSize }

          if (loadState.dexEntries.size > 1) {
            item {
              Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                  text = "${stringResource(R.string.label_dex_entries)} (${loadState.loadedDexes.size}/${loadState.dexEntries.size} loaded)",
                  style = MaterialTheme.typography.titleMedium,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  items(loadState.dexEntries) { entry ->
                    val isLoaded = entry.entryName in loadState.loadedDexes
                    FilterChip(
                      selected = isLoaded,
                      onClick = { onSelectDexEntry(entry.entryName) },
                      leadingIcon =
                        if (isLoaded) {
                          {
                            Icon(
                              imageVector = Icons.Rounded.Check,
                              contentDescription = null,
                              modifier = Modifier.size(16.dp),
                            )
                          }
                        } else {
                          null
                        },
                      label = { Text("${entry.entryName} (${formatBytes(entry.byteSize)})") },
                      modifier = Modifier.testTag("dex_entry_${entry.entryName}"),
                    )
                  }
                }
              }
            }
          }

          item {
            val bannerSummary =
              if (loadedDexList.size > 1) "${loadedDexList.size} DEXes merged"
              else loadState.activeEntryName
            TranspileActionBanner(
              sourceLabel = loadState.sourceLabel,
              entryName = bannerSummary,
              selectedMethodCount = selectedMethodCount,
              onAutoSelect = onAutoSelectAppMethods,
              onClearSelection = onClearSelection,
              onBrowseClasses = onNavigateToClasses,
              onBuildOutput = onNavigateToExport,
            )
          }

          item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                MetricTile(
                  label = "Classes",
                  value = "%,d".format(totalClasses),
                  icon = Icons.Rounded.DataObject,
                  accent = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.weight(1f),
                )
                MetricTile(
                  label = "Methods",
                  value = "%,d".format(totalMethods),
                  icon = Icons.Rounded.Code,
                  accent = MaterialTheme.colorScheme.tertiary,
                  modifier = Modifier.weight(1f),
                )
              }
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                MetricTile(
                  label = "Strings",
                  value = "%,d".format(totalStrings),
                  icon = Icons.Rounded.Memory,
                  accent = MaterialTheme.colorScheme.secondary,
                  modifier = Modifier.weight(1f),
                )
                MetricTile(
                  label = "Binary Size",
                  value = formatBytes(totalSizeBytes),
                  icon = Icons.Rounded.Storage,
                  accent = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.weight(1f),
                )
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
              Column(modifier = Modifier.padding(18.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Text(
                    text = stringResource(R.string.label_dex_header),
                    style = MaterialTheme.typography.titleMedium,
                  )
                  TagPill(
                    text = "DEX v${dex.header.version}",
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                  )
                }
                if (loadedDexList.size > 1) {
                  Spacer(Modifier.height(8.dp))
                  LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(loadedDexList, key = { it.entryName }) { loadedItem ->
                      FilterChip(
                        selected = loadedItem.entryName == dex.entryName,
                        onClick = { onFocusHeaderDex(loadedItem.entryName) },
                        label = { Text(loadedItem.entryName) },
                        modifier = Modifier.testTag("header_dex_${loadedItem.entryName}"),
                      )
                    }
                  }
                }
                Spacer(Modifier.height(10.dp))
                HeaderKeyValueRow("Source", loadState.sourceLabel)
                HeaderKeyValueRow("Container", dex.entryName)
                HeaderKeyValueRow("Adler32", "0x%08X".format(dex.header.checksum))
                HeaderKeyValueRow("SHA-1", dex.header.sha1Hex.take(20) + "…")
                HeaderKeyValueRow("Types / Protos", "${dex.header.typeIdsSize} / ${dex.header.protoIdsSize}")
                HeaderKeyValueRow("Fields", "${dex.header.fieldIdsSize}")
                HeaderKeyValueRow("map_off", if (dex.header.mapOff > 0) "0x%08X".format(dex.header.mapOff) else "0x00000000")
                HeaderKeyValueRow(
                  "App Classes",
                  "${dex.classes.count { !it.isFrameworkClass }} / ${dex.classes.size}",
                )
              }
            }
          }

          item {
            DexMapListCard(
              dex = dex,
              onOpenRustBridge = onOpenRustBridge,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun HeroWorkbenchBanner(
  onOpenPicker: () -> Unit,
  onOpenInstalledApps: () -> Unit,
) {
  val openInteraction = remember { MutableInteractionSource() }
  val appsInteraction = remember { MutableInteractionSource() }
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(24.dp),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
  ) {
    Box(modifier = Modifier.fillMaxWidth()) {
      Image(
        painter = painterResource(id = R.drawable.img_dex2c_hero),
        contentDescription = stringResource(R.string.hero_banner_desc),
        contentScale = ContentScale.Crop,
        modifier = Modifier.matchParentSize(),
      )
      Box(
        modifier =
          Modifier.matchParentSize()
            .background(
              Brush.verticalGradient(
                colors =
                  listOf(
                    Color(0xBB070C14),
                    Color(0xEE09111D),
                  )
              )
            )
      )
      Column(modifier = Modifier.padding(20.dp)) {
        TagPill(
          text = "DALVIK → NATIVE JNI",
          containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
          contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Spacer(Modifier.height(12.dp))
        Text(
          text = stringResource(R.string.hero_title),
          style = MaterialTheme.typography.headlineMedium,
          color = Color.White,
        )
        Spacer(Modifier.height(6.dp))
        Text(
          text = stringResource(R.string.hero_subtitle),
          style = MaterialTheme.typography.bodyMedium,
          color = Color(0xFFBFD0E6),
        )
        Spacer(Modifier.height(18.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Button(
            onClick = onOpenPicker,
            interactionSource = openInteraction,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.weight(1f).expressivePressScale(openInteraction).testTag("open_dex_apk_button"),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
          ) {
            Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.action_open_file), maxLines = 1)
          }
          OutlinedButton(
            onClick = onOpenInstalledApps,
            interactionSource = appsInteraction,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.weight(1f).expressivePressScale(appsInteraction).testTag("open_installed_apps_button"),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
          ) {
            Icon(Icons.Rounded.Apps, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.picker_tab_apps), maxLines = 1)
          }
        }
      }
    }
  }
}

@Composable
private fun RecentQuickStrip(
  recentFiles: List<RecentBinaryItem>,
  onSelectRecent: (RecentBinaryItem) -> Unit,
  onViewAll: () -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Text("Recent APKs & DEX", style = MaterialTheme.typography.titleMedium)
      }
      TextButton(onClick = onViewAll) {
        Text("View All")
      }
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      items(recentFiles, key = { it.id }) { item ->
        Surface(
          modifier =
            Modifier.widthIn(min = 170.dp, max = 230.dp)
              .clip(RoundedCornerShape(18.dp))
              .clickable { onSelectRecent(item) },
          shape = RoundedCornerShape(18.dp),
          color = MaterialTheme.colorScheme.surfaceContainer,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        ) {
          Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Icon(
              imageVector =
                if (item.sourceType == BinarySourceType.INSTALLED_APK) Icons.Rounded.Android
                else Icons.Rounded.FolderOpen,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(20.dp),
            )
            Column {
              Text(
                text = item.displayName,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text = formatApkSize(item.byteSize),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun EmptyWorkspaceCard(
  onOpenPicker: () -> Unit,
  onInspectSelfApk: () -> Unit,
) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(24.dp),
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
  ) {
    Column(
      modifier = Modifier.padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Icon(
        imageVector = Icons.Rounded.Memory,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(40.dp),
      )
      Spacer(Modifier.height(12.dp))
      Text(
        text = stringResource(R.string.empty_workspace_title),
        style = MaterialTheme.typography.titleLarge,
      )
      Spacer(Modifier.height(8.dp))
      Text(
        text = stringResource(R.string.empty_workspace_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(Modifier.height(18.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
          onClick = onOpenPicker,
          shape = RoundedCornerShape(20.dp),
          modifier = Modifier.testTag("empty_open_file_btn"),
        ) {
          Text(stringResource(R.string.action_open_file))
        }
        TextButton(onClick = onInspectSelfApk, modifier = Modifier.testTag("empty_inspect_apk_btn")) {
          Text(stringResource(R.string.action_inspect_self_apk))
        }
      }
    }
  }
}

@Composable
private fun TranspileActionBanner(
  sourceLabel: String,
  entryName: String,
  selectedMethodCount: Int,
  onAutoSelect: () -> Unit,
  onClearSelection: () -> Unit,
  onBrowseClasses: () -> Unit,
  onBuildOutput: () -> Unit,
) {
  Surface(
    modifier = Modifier.fillMaxWidth().animateContentSize(ExpressiveMotion.spatialSpring()),
    shape = RoundedCornerShape(24.dp),
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
  ) {
    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = sourceLabel,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
          )
          Text(
            text = "$entryName • $selectedMethodCount methods queued for C",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        TagPill(
          text = "$selectedMethodCount SELECTED",
          containerColor =
            if (selectedMethodCount > 0) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainer,
          contentColor =
            if (selectedMethodCount > 0) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedButton(
          onClick = onAutoSelect,
          shape = RoundedCornerShape(20.dp),
          modifier = Modifier.weight(1f).testTag("auto_select_methods_button"),
        ) {
          Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text(stringResource(R.string.action_select_app_classes), maxLines = 1)
        }
        AnimatedVisibility(visible = selectedMethodCount > 0) {
          TextButton(onClick = onClearSelection, modifier = Modifier.testTag("clear_selection_button")) {
            Text(stringResource(R.string.action_clear_selection))
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        OutlinedButton(
          onClick = onBrowseClasses,
          shape = RoundedCornerShape(20.dp),
          modifier = Modifier.weight(1f).testTag("browse_classes_button"),
        ) {
          Text("Browse Classes")
        }
        Button(
          onClick = onBuildOutput,
          enabled = selectedMethodCount > 0,
          shape = RoundedCornerShape(20.dp),
          modifier = Modifier.weight(1f).testTag("build_c_output_button"),
        ) {
          Text(stringResource(R.string.action_view_output))
          Spacer(Modifier.width(6.dp))
          Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
        }
      }
    }
  }
}

private fun formatBytes(bytes: Int): String {
  return when {
    bytes >= 1024 * 1024 -> "%.2f MB".format(bytes / (1024f * 1024f))
    bytes >= 1024 -> "%.1f KB".format(bytes / 1024f)
    else -> "$bytes B"
  }
}
