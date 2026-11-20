package com.dex2c.studio.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.dex2c.studio.R
import com.dex2c.studio.apk.BinarySourceType
import com.dex2c.studio.apk.PickerTab
import com.dex2c.studio.apk.RecentBinaryItem
import com.dex2c.studio.ui.DexLoadState
import com.dex2c.studio.ui.WindowWidthSizeClass
import com.dex2c.studio.ui.rememberWindowAdaptiveInfo
import com.dex2c.studio.ui.components.ExpressiveMotion
import com.dex2c.studio.ui.components.MetricTile
import com.dex2c.studio.ui.components.RecentItemIcon
import com.dex2c.studio.ui.components.TagPill
import com.dex2c.studio.ui.components.expressivePressScale
import com.dex2c.studio.ui.components.formatApkSize

@Composable
fun HomeScreen(
  loadState: DexLoadState,
  selectedMethodCount: Int,
  recentFiles: List<RecentBinaryItem>,
  onOpenPicker: (PickerTab) -> Unit,
  onSelectRecent: (RecentBinaryItem) -> Unit,
  onAutoSelectAppMethods: () -> Unit,
  onClearSelection: () -> Unit,
  onNavigateToClasses: () -> Unit,
  modifier: Modifier = Modifier,
) {
  BoxWithConstraints(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    val adaptiveInfo = rememberWindowAdaptiveInfo(maxWidth, maxHeight)
    val isWide = adaptiveInfo.widthSizeClass != WindowWidthSizeClass.COMPACT
    val isExpanded = adaptiveInfo.widthSizeClass == WindowWidthSizeClass.EXPANDED
    val contentMax = if (isExpanded) 1100.dp else 880.dp

    when (loadState) {
      is DexLoadState.Ready -> {
        val loadedDexList = loadState.loadedDexes.values.toList()
        val totalClasses = loadedDexList.sumOf { it.header.classDefsSize }
        val totalMethods = loadedDexList.sumOf { it.header.methodIdsSize }
        val totalStrings = loadedDexList.sumOf { it.header.stringIdsSize }
        val totalSizeBytes = loadedDexList.sumOf { it.header.fileSize }
        val scrollState = rememberScrollState()
        val bannerSummary =
          if (loadedDexList.size > 1) "${loadedDexList.size} DEXes merged"
          else loadState.activeEntryName

        if (isWide) {
          Row(
            modifier =
              Modifier
                .fillMaxSize()
                .widthIn(max = contentMax)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top,
          ) {
            Column(
              modifier = Modifier.weight(1.05f),
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              HeroHomeBanner(
                onOpenPicker = { onOpenPicker(PickerTab.STORAGE) },
                onOpenInstalledApps = { onOpenPicker(PickerTab.APPS) },
                compact = true,
              )
              TranspileActionBanner(
                sourceLabel = loadState.sourceLabel,
                entryName = bannerSummary,
                selectedMethodCount = selectedMethodCount,
                onAutoSelect = onAutoSelectAppMethods,
                onClearSelection = onClearSelection,
                onBrowseClasses = onNavigateToClasses,
                compact = true,
              )
              if (recentFiles.isNotEmpty() && isExpanded) {
                RecentQuickStrip(
                  recentFiles = recentFiles,
                  onSelectRecent = onSelectRecent,
                  onViewAll = { onOpenPicker(PickerTab.RECENT) },
                )
              }
            }
            Column(
              modifier = Modifier.weight(0.95f),
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              HomeMetricsGrid(
                totalClasses = totalClasses,
                totalMethods = totalMethods,
                totalStrings = totalStrings,
                totalSizeBytes = totalSizeBytes,
                compact = !isExpanded,
              )
              if (recentFiles.isNotEmpty() && !isExpanded) {
                RecentQuickStrip(
                  recentFiles = recentFiles,
                  onSelectRecent = onSelectRecent,
                  onViewAll = { onOpenPicker(PickerTab.RECENT) },
                )
              }
            }
          }
        } else {
          Column(
            modifier =
              Modifier
                .fillMaxSize()
                .widthIn(max = 880.dp)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            HeroHomeBanner(
              onOpenPicker = { onOpenPicker(PickerTab.STORAGE) },
              onOpenInstalledApps = { onOpenPicker(PickerTab.APPS) },
              compact = true,
            )
            TranspileActionBanner(
              sourceLabel = loadState.sourceLabel,
              entryName = bannerSummary,
              selectedMethodCount = selectedMethodCount,
              onAutoSelect = onAutoSelectAppMethods,
              onClearSelection = onClearSelection,
              onBrowseClasses = onNavigateToClasses,
              compact = true,
            )
            HomeMetricsGrid(
              totalClasses = totalClasses,
              totalMethods = totalMethods,
              totalStrings = totalStrings,
              totalSizeBytes = totalSizeBytes,
              compact = true,
            )
            if (recentFiles.isNotEmpty()) {
              RecentQuickStrip(
                recentFiles = recentFiles,
                onSelectRecent = onSelectRecent,
                onViewAll = { onOpenPicker(PickerTab.RECENT) },
              )
            }
          }
        }
      }

      else -> {
        val scrollState = rememberScrollState()
        if (isWide && recentFiles.isNotEmpty()) {
          Row(
            modifier =
              Modifier
                .fillMaxSize()
                .widthIn(max = contentMax)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top,
          ) {
            Column(
              modifier = Modifier.weight(1.05f),
              verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              HeroHomeBanner(
                onOpenPicker = { onOpenPicker(PickerTab.STORAGE) },
                onOpenInstalledApps = { onOpenPicker(PickerTab.APPS) },
              )
              when (loadState) {
                is DexLoadState.Loading -> {
                  Surface(
                    modifier = Modifier.fillMaxWidth().animateContentSize(ExpressiveMotion.spatialSpring()),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                  ) {
                    Row(
                      modifier = Modifier.padding(20.dp),
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

                is DexLoadState.Error -> {
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

                is DexLoadState.Empty, is DexLoadState.Ready -> Unit
              }
            }
            Column(
              modifier = Modifier.weight(0.95f),
              verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              RecentQuickStrip(
                recentFiles = recentFiles,
                onSelectRecent = onSelectRecent,
                onViewAll = { onOpenPicker(PickerTab.RECENT) },
              )
            }
          }
        } else {
          Column(
            modifier =
              Modifier
                .fillMaxSize()
                .widthIn(max = contentMax)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            HeroHomeBanner(
              onOpenPicker = { onOpenPicker(PickerTab.STORAGE) },
              onOpenInstalledApps = { onOpenPicker(PickerTab.APPS) },
            )

            when (loadState) {
              is DexLoadState.Loading -> {
                Surface(
                  modifier = Modifier.fillMaxWidth().animateContentSize(ExpressiveMotion.spatialSpring()),
                  shape = RoundedCornerShape(24.dp),
                  color = MaterialTheme.colorScheme.surfaceContainer,
                  border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                ) {
                  Row(
                    modifier = Modifier.padding(20.dp),
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

              is DexLoadState.Error -> {
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

              is DexLoadState.Empty, is DexLoadState.Ready -> Unit
            }

            if (recentFiles.isNotEmpty()) {
              RecentQuickStrip(
                recentFiles = recentFiles,
                onSelectRecent = onSelectRecent,
                onViewAll = { onOpenPicker(PickerTab.RECENT) },
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun HomeMetricsGrid(
  totalClasses: Int,
  totalMethods: Int,
  totalStrings: Int,
  totalSizeBytes: Int,
  compact: Boolean = false,
) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      MetricTile(
        label = "Classes",
        value = "%,d".format(totalClasses),
        icon = Icons.Rounded.DataObject,
        accent = MaterialTheme.colorScheme.primary,
        compact = compact,
        modifier = Modifier.weight(1f),
      )
      MetricTile(
        label = "Methods",
        value = "%,d".format(totalMethods),
        icon = Icons.Rounded.Code,
        accent = MaterialTheme.colorScheme.tertiary,
        compact = compact,
        modifier = Modifier.weight(1f),
      )
    }
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      MetricTile(
        label = "Strings",
        value = "%,d".format(totalStrings),
        icon = Icons.Rounded.Memory,
        accent = MaterialTheme.colorScheme.secondary,
        compact = compact,
        modifier = Modifier.weight(1f),
      )
      MetricTile(
        label = "Binary Size",
        value = formatBytes(totalSizeBytes),
        icon = Icons.Rounded.Storage,
        accent = MaterialTheme.colorScheme.primary,
        compact = compact,
        modifier = Modifier.weight(1f),
      )
    }
  }
}

@Composable
private fun HeroHomeBanner(
  onOpenPicker: () -> Unit,
  onOpenInstalledApps: () -> Unit,
  compact: Boolean = false,
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
      Column(modifier = Modifier.padding(if (compact) 14.dp else 20.dp)) {
        TagPill(
          text = "DALVIK → NATIVE JNI",
          containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
          contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Spacer(Modifier.height(if (compact) 6.dp else 12.dp))
        Text(
          text = stringResource(R.string.hero_title),
          style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
          fontWeight = FontWeight.SemiBold,
          color = Color.White,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
          text = stringResource(R.string.hero_subtitle),
          style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
          color = Color(0xFFBFD0E6),
          maxLines = if (compact) 2 else 3,
          overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(if (compact) 10.dp else 18.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Button(
            onClick = onOpenPicker,
            interactionSource = openInteraction,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.weight(1f).expressivePressScale(openInteraction).testTag("open_dex_apk_button"),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = if (compact) 8.dp else 12.dp),
          ) {
            Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.action_open_file), maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
          OutlinedButton(
            onClick = onOpenInstalledApps,
            interactionSource = appsInteraction,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.weight(1f).expressivePressScale(appsInteraction).testTag("open_installed_apps_button"),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = if (compact) 8.dp else 12.dp),
          ) {
            Icon(Icons.Rounded.Apps, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.picker_tab_apps), maxLines = 1, overflow = TextOverflow.Ellipsis)
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
  var showAllRecent by rememberSaveable { mutableStateOf(false) }
  val displayedItems = if (showAllRecent) recentFiles else recentFiles.take(2)

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
      if (recentFiles.size > 2) {
        TextButton(
          onClick = { showAllRecent = !showAllRecent },
          modifier = Modifier.testTag("recent_view_all_button"),
        ) {
          Text(if (showAllRecent) "Show Less" else "View All")
        }
      }
    }
    LazyRow(
      modifier = Modifier.testTag("recent_lazy_row"),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      items(displayedItems, key = { it.id }) { item ->
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
            RecentItemIcon(
              item = item,
              size = 28.dp,
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
private fun TranspileActionBanner(
  sourceLabel: String,
  entryName: String,
  selectedMethodCount: Int,
  onAutoSelect: () -> Unit,
  onClearSelection: () -> Unit,
  onBrowseClasses: () -> Unit,
  compact: Boolean = false,
) {
  Surface(
    modifier = Modifier.fillMaxWidth().animateContentSize(ExpressiveMotion.spatialSpring()),
    shape = RoundedCornerShape(24.dp),
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
  ) {
    Column(
      modifier = Modifier.padding(if (compact) 14.dp else 18.dp),
      verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
    ) {
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
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = "$entryName • $selectedMethodCount methods queued for C",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        Spacer(Modifier.width(8.dp))
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
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
          modifier = Modifier.weight(1f).testTag("auto_select_methods_button"),
        ) {
          Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text(stringResource(R.string.action_select_app_classes), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        AnimatedVisibility(visible = selectedMethodCount > 0) {
          TextButton(onClick = onClearSelection, modifier = Modifier.testTag("clear_selection_button")) {
            Text(stringResource(R.string.action_clear_selection))
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedButton(
          onClick = onBrowseClasses,
          shape = RoundedCornerShape(20.dp),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
          modifier = Modifier.weight(1f).testTag("browse_classes_button"),
        ) {
          Text("Browse Classes", maxLines = 1, overflow = TextOverflow.Ellipsis)
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
