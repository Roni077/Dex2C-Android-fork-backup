package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Schema
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
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
import com.example.ui.components.MetricTile
import com.example.ui.components.TagPill
import com.example.ui.components.expressivePressScale
import com.example.ui.components.formatApkSize
import com.example.ui.theme.JetBrainsMonoFontFamily

enum class HomeTab {
  OVERVIEW,
  HEADER_AND_MAP,
}

@Composable
fun HomeScreen(
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
  showOutputScreen: Boolean = true,
) {
  var activeTab by rememberSaveable { mutableStateOf(HomeTab.OVERVIEW) }

  BoxWithConstraints(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    val isWide = maxWidth >= 680.dp
    val needsCompactScroll = maxHeight < 480.dp

    when (loadState) {
      is DexLoadState.Ready -> {
        val dex = loadState.parsedDex
        val loadedDexList = loadState.loadedDexes.values.toList()
        val totalClasses = loadedDexList.sumOf { it.header.classDefsSize }
        val totalMethods = loadedDexList.sumOf { it.header.methodIdsSize }
        val totalStrings = loadedDexList.sumOf { it.header.stringIdsSize }
        val totalSizeBytes = loadedDexList.sumOf { it.header.fileSize }

        Column(
          modifier =
            Modifier
              .fillMaxSize()
              .widthIn(max = 880.dp)
              .padding(horizontal = 16.dp, vertical = 10.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            SingleChoiceSegmentedButtonRow(
              modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp),
            ) {
              SegmentedButton(
                selected = activeTab == HomeTab.OVERVIEW,
                onClick = { activeTab = HomeTab.OVERVIEW },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                modifier = Modifier.minimumInteractiveComponentSize().testTag("home_tab_OVERVIEW"),
                icon = {
                  SegmentedButtonDefaults.Icon(active = activeTab == HomeTab.OVERVIEW) {
                    Icon(Icons.Rounded.Dashboard, contentDescription = null, modifier = Modifier.size(16.dp))
                  }
                },
              ) {
                Text(
                  text = stringResource(R.string.home_tab_overview),
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
              SegmentedButton(
                selected = activeTab == HomeTab.HEADER_AND_MAP,
                onClick = { activeTab = HomeTab.HEADER_AND_MAP },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                modifier = Modifier.minimumInteractiveComponentSize().testTag("home_tab_HEADER_AND_MAP"),
                icon = {
                  SegmentedButtonDefaults.Icon(active = activeTab == HomeTab.HEADER_AND_MAP) {
                    Icon(Icons.Rounded.Schema, contentDescription = null, modifier = Modifier.size(16.dp))
                  }
                },
              ) {
                Text(
                  text = stringResource(R.string.home_tab_header_map),
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
          }

          val paneScrollState = rememberScrollState()
          Box(
            modifier =
              Modifier
                .weight(1f)
                .fillMaxWidth()
                .then(if (needsCompactScroll) Modifier.verticalScroll(paneScrollState) else Modifier),
          ) {
            AnimatedContent(
              targetState = activeTab,
              transitionSpec = {
                ExpressiveMotion.sharedAxisHorizontal(forward = targetState.ordinal >= initialState.ordinal)
              },
              label = "home_ready_tab",
            ) { tab ->
              when (tab) {
                HomeTab.OVERVIEW -> {
                  val bannerSummary =
                    if (loadedDexList.size > 1) "${loadedDexList.size} DEXes merged"
                    else loadState.activeEntryName

                  if (isWide) {
                    Row(
                      modifier = Modifier.fillMaxSize(),
                      horizontalArrangement = Arrangement.spacedBy(12.dp),
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
                          onBuildOutput = onNavigateToExport,
                          showOutputScreen = showOutputScreen,
                          compact = true,
                        )
                      }
                      Column(
                        modifier = Modifier.weight(0.95f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                      ) {
                        if (loadState.dexEntries.size > 1) {
                          DexContainersStrip(
                            dexEntries = loadState.dexEntries,
                            loadedDexKeys = loadState.loadedDexes.keys,
                            onSelectDexEntry = onSelectDexEntry,
                          )
                        }
                        HomeMetricsGrid(
                          totalClasses = totalClasses,
                          totalMethods = totalMethods,
                          totalStrings = totalStrings,
                          totalSizeBytes = totalSizeBytes,
                          compact = true,
                        )
                      }
                    }
                  } else {
                    Column(
                      modifier = Modifier.fillMaxWidth(),
                      verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                      HeroHomeBanner(
                        onOpenPicker = { onOpenPicker(PickerTab.STORAGE) },
                        onOpenInstalledApps = { onOpenPicker(PickerTab.APPS) },
                        compact = true,
                      )
                      if (loadState.dexEntries.size > 1) {
                        DexContainersStrip(
                          dexEntries = loadState.dexEntries,
                          loadedDexKeys = loadState.loadedDexes.keys,
                          onSelectDexEntry = onSelectDexEntry,
                        )
                      }
                      TranspileActionBanner(
                        sourceLabel = loadState.sourceLabel,
                        entryName = bannerSummary,
                        selectedMethodCount = selectedMethodCount,
                        onAutoSelect = onAutoSelectAppMethods,
                        onClearSelection = onClearSelection,
                        onBrowseClasses = onNavigateToClasses,
                        onBuildOutput = onNavigateToExport,
                        showOutputScreen = showOutputScreen,
                        compact = true,
                      )
                      HomeMetricsGrid(
                        totalClasses = totalClasses,
                        totalMethods = totalMethods,
                        totalStrings = totalStrings,
                        totalSizeBytes = totalSizeBytes,
                        compact = true,
                      )
                    }
                  }
                }

                HomeTab.HEADER_AND_MAP -> {
                  if (isWide) {
                    Row(
                      modifier = Modifier.fillMaxSize(),
                      horizontalArrangement = Arrangement.spacedBy(12.dp),
                      verticalAlignment = Alignment.Top,
                    ) {
                      DexBinaryHeaderCard(
                        dex = dex,
                        sourceLabel = loadState.sourceLabel,
                        loadedDexList = loadedDexList,
                        onFocusHeaderDex = onFocusHeaderDex,
                        modifier = Modifier.weight(1f),
                      )
                      DexMapListCard(
                        dex = dex,
                        onOpenRustBridge = onOpenRustBridge,
                        modifier = Modifier.weight(1f),
                      )
                    }
                  } else {
                    Column(
                      modifier = Modifier.fillMaxWidth(),
                      verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                      DexBinaryHeaderCard(
                        dex = dex,
                        sourceLabel = loadState.sourceLabel,
                        loadedDexList = loadedDexList,
                        onFocusHeaderDex = onFocusHeaderDex,
                      )
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
        }
      }

      else -> {
        val scrollState = rememberScrollState()
        Column(
          modifier =
            Modifier
              .fillMaxSize()
              .widthIn(max = 880.dp)
              .then(if (needsCompactScroll) Modifier.verticalScroll(scrollState) else Modifier)
              .padding(horizontal = 16.dp, vertical = 12.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          val stateContent: @Composable () -> Unit = {
            when (loadState) {
              is DexLoadState.Empty -> {
                EmptyWorkspaceCard(
                  onOpenPicker = { onOpenPicker(PickerTab.STORAGE) },
                  onInspectSelfApk = onInspectSelfApk,
                )
              }

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

              is DexLoadState.Ready -> Unit
            }
          }

          if (isWide) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(14.dp),
              verticalAlignment = Alignment.Top,
            ) {
              Column(modifier = Modifier.weight(1.1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HeroHomeBanner(
                  onOpenPicker = { onOpenPicker(PickerTab.STORAGE) },
                  onOpenInstalledApps = { onOpenPicker(PickerTab.APPS) },
                )
              }
              Column(modifier = Modifier.weight(0.9f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                stateContent()
              }
            }
            if (recentFiles.isNotEmpty() && loadState is DexLoadState.Empty) {
              RecentQuickStrip(
                recentFiles = recentFiles.take(5),
                onSelectRecent = onSelectRecent,
                onViewAll = { onOpenPicker(PickerTab.RECENT) },
              )
            }
          } else {
            HeroHomeBanner(
              onOpenPicker = { onOpenPicker(PickerTab.STORAGE) },
              onOpenInstalledApps = { onOpenPicker(PickerTab.APPS) },
            )
            if (recentFiles.isNotEmpty() && loadState is DexLoadState.Empty) {
              RecentQuickStrip(
                recentFiles = recentFiles.take(5),
                onSelectRecent = onSelectRecent,
                onViewAll = { onOpenPicker(PickerTab.RECENT) },
              )
            }
            stateContent()
          }
        }
      }
    }
  }
}

@Composable
private fun DexContainersStrip(
  dexEntries: List<com.example.dex.ApkDexEntry>,
  loadedDexKeys: Set<String>,
  onSelectDexEntry: (String) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(
      text = "${stringResource(R.string.label_dex_entries)} (${loadedDexKeys.size}/${dexEntries.size} loaded)",
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      items(dexEntries) { entry ->
        val isLoaded = entry.entryName in loadedDexKeys
        FilterChip(
          selected = isLoaded,
          onClick = { onSelectDexEntry(entry.entryName) },
          leadingIcon =
            if (isLoaded) {
              {
                Icon(
                  imageVector = Icons.Rounded.Check,
                  contentDescription = null,
                  modifier = Modifier.size(15.dp),
                )
              }
            } else {
              null
            },
          label = { Text("${entry.entryName} (${formatBytes(entry.byteSize)})", maxLines = 1) },
          modifier = Modifier.testTag("dex_entry_${entry.entryName}"),
        )
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
private fun DexBinaryHeaderCard(
  dex: com.example.dex.DexParsedFile,
  sourceLabel: String,
  loadedDexList: List<com.example.dex.DexParsedFile>,
  onFocusHeaderDex: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(24.dp),
    color = MaterialTheme.colorScheme.surfaceContainer,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
  ) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
      Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          CompactHeaderCell(label = "Source", value = sourceLabel, modifier = Modifier.weight(1f))
          CompactHeaderCell(label = "Container", value = dex.entryName, modifier = Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          CompactHeaderCell(label = "Adler32", value = "0x%08X".format(dex.header.checksum), modifier = Modifier.weight(1f))
          CompactHeaderCell(label = "SHA-1", value = dex.header.sha1Hex.take(10) + "…", modifier = Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          CompactHeaderCell(label = "Types/Protos", value = "${dex.header.typeIdsSize}/${dex.header.protoIdsSize}", modifier = Modifier.weight(1f))
          CompactHeaderCell(label = "Fields", value = "${dex.header.fieldIdsSize}", modifier = Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          CompactHeaderCell(
            label = "map_off",
            value = if (dex.header.mapOff > 0) "0x%08X".format(dex.header.mapOff) else "0x00000000",
            modifier = Modifier.weight(1f),
          )
          CompactHeaderCell(
            label = "App Classes",
            value = "${dex.classes.count { !it.isFrameworkClass }}/${dex.classes.size}",
            modifier = Modifier.weight(1f),
          )
        }
      }
    }
  }
}

@Composable
private fun CompactHeaderCell(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(10.dp),
    color = MaterialTheme.colorScheme.surfaceContainerLow,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
      )
      Spacer(Modifier.width(4.dp))
      Text(
        text = value,
        style = MaterialTheme.typography.labelSmall,
        fontFamily = JetBrainsMonoFontFamily,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
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
  showOutputScreen: Boolean = true,
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
        if (showOutputScreen) {
          Button(
            onClick = onBuildOutput,
            enabled = selectedMethodCount > 0,
            shape = RoundedCornerShape(20.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            modifier = Modifier.weight(1f).testTag("build_c_output_button"),
          ) {
            Text(stringResource(R.string.action_view_output), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(6.dp))
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
          }
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
