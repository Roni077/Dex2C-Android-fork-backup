package com.dex2c.studio.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dex2c.studio.R
import com.dex2c.studio.apk.ApkStagingPreview
import com.dex2c.studio.ui.ApkStagingState
import com.dex2c.studio.ui.theme.JetBrainsMonoFontFamily

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ApkPreviewSheet(
  stagingState: ApkStagingState,
  onSelectDexEntry: (String) -> Unit,
  onConfirmLoad: () -> Unit,
  onDismiss: () -> Unit,
  onSelectAllDexEntries: () -> Unit = {},
  onSelectPrimaryDexEntry: () -> Unit = {},
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Text(
        text = stringResource(R.string.preview_title),
        style = MaterialTheme.typography.titleLarge,
      )

      when (stagingState) {
        is ApkStagingState.Loading -> {
          Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
          ) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp))
            Column {
              Text(
                text = stringResource(R.string.preview_staging_loading),
                style = MaterialTheme.typography.titleMedium,
              )
              Text(
                text = stagingState.label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }

        is ApkStagingState.Error -> {
          Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.errorContainer,
          ) {
            Row(
              modifier = Modifier.padding(18.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
              Text(stagingState.message, style = MaterialTheme.typography.bodyMedium)
            }
          }
          OutlinedButton(
            onClick = onDismiss,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text("Close")
          }
        }

        is ApkStagingState.Ready -> {
          PreviewReadyContent(
            preview = stagingState.preview,
            selectedEntries = stagingState.selectedEntryNames,
            onToggleDexEntry = onSelectDexEntry,
            onSelectAllDexEntries = onSelectAllDexEntries,
            onSelectPrimaryDexEntry = onSelectPrimaryDexEntry,
            onConfirmLoad = onConfirmLoad,
            onDismiss = onDismiss,
          )
        }
      }
      Spacer(Modifier.height(16.dp))
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.PreviewReadyContent(
  preview: ApkStagingPreview,
  selectedEntries: Set<String>,
  onToggleDexEntry: (String) -> Unit,
  onSelectAllDexEntries: () -> Unit,
  onSelectPrimaryDexEntry: () -> Unit,
  onConfirmLoad: () -> Unit,
  onDismiss: () -> Unit,
) {
  val iconBitmap = remember(preview.iconBitmap) { preview.iconBitmap?.asImageBitmap() }
  val isMultiDexContainer = preview.dexEntries.size > 1
  val allSelected = isMultiDexContainer && selectedEntries.size >= preview.dexEntries.size

  LazyColumn(
    modifier = Modifier.fillMaxWidth().weight(1f, fill = false).testTag("preview_dex_list"),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    item(key = "preview_meta_card") {
      Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            if (iconBitmap != null) {
              Image(
                bitmap = iconBitmap,
                contentDescription = "${preview.appLabel} icon",
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)),
              )
            } else {
              Box(
                modifier =
                  Modifier.size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector = if (preview.isSingleDex) Icons.Rounded.Memory else Icons.Rounded.Android,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
              }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = preview.appLabel ?: preview.sourceLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              if (!preview.packageName.isNullOrBlank()) {
                Text(
                  text = preview.packageName,
                  style = MaterialTheme.typography.labelSmall,
                  fontFamily = JetBrainsMonoFontFamily,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              } else {
                Text(
                  text = preview.sourceLabel,
                  style = MaterialTheme.typography.labelSmall,
                  fontFamily = JetBrainsMonoFontFamily,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }

          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            TagPill(preview.sourceType.label)
            TagPill(formatApkSize(preview.totalByteSize))
            if (!preview.versionName.isNullOrBlank()) {
              val verText =
                if (preview.versionCode != null) "v${preview.versionName} (${preview.versionCode})"
                else preview.versionName
              TagPill(verText)
            }
            if (preview.minSdk != null) TagPill("minSdk ${preview.minSdk}")
            if (preview.targetSdk != null && preview.targetSdk > 0) TagPill("targetSdk ${preview.targetSdk}")
          }

          if (!preview.isSingleDex) {
            Text(
              text = stringResource(R.string.preview_abis_section),
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              if (preview.nativeAbis.isEmpty()) {
                TagPill("Pure Dalvik (No .so libs)")
              } else {
                preview.nativeAbis.forEach { abi ->
                  TagPill(
                    text = abi,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                  )
                }
              }
            }
          }
        }
      }
    }

    item(key = "preview_dex_header") {
      Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text =
            if (isMultiDexContainer) "${stringResource(R.string.preview_dex_section)} (${selectedEntries.size}/${preview.dexEntries.size})"
            else "${stringResource(R.string.preview_dex_section)} (${preview.dexEntries.size})",
          style = MaterialTheme.typography.titleMedium,
          modifier = Modifier.weight(1f),
        )
        if (isMultiDexContainer) {
          TextButton(
            onClick = { if (allSelected) onSelectPrimaryDexEntry() else onSelectAllDexEntries() },
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            modifier = Modifier.testTag("preview_select_all_dex_btn"),
          ) {
            Text(if (allSelected) "Primary Only" else "Select All")
          }
        }
      }
    }

    items(preview.dexEntries, key = { it.entryName }) { entry ->
      val isSelected = entry.entryName in selectedEntries
      Surface(
        modifier =
          Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable { onToggleDexEntry(entry.entryName) }
            .testTag("preview_dex_${entry.entryName}"),
        shape = RoundedCornerShape(18.dp),
        color =
          if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
          else MaterialTheme.colorScheme.surfaceContainer,
        border =
          BorderStroke(
            width = 1.dp,
            color =
              if (isSelected) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
          ),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggleDexEntry(entry.entryName) },
            modifier = Modifier.minimumInteractiveComponentSize().testTag("preview_dex_checkbox_${entry.entryName}"),
          )
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = entry.entryName,
              style = MaterialTheme.typography.labelLarge,
              fontFamily = JetBrainsMonoFontFamily,
              fontWeight = FontWeight.SemiBold,
            )
            Text(
              text = formatApkSize(entry.byteSize.toLong()),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
  }

  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    OutlinedButton(
      onClick = onDismiss,
      shape = RoundedCornerShape(20.dp),
      modifier = Modifier.weight(1f),
      contentPadding = PaddingValues(vertical = 12.dp),
    ) {
      Text("Cancel")
    }
    Button(
      onClick = onConfirmLoad,
      enabled = selectedEntries.isNotEmpty(),
      shape = RoundedCornerShape(20.dp),
      modifier = Modifier.weight(1.4f).testTag("preview_load_dex_button"),
      contentPadding = PaddingValues(vertical = 12.dp),
    ) {
      val buttonText =
        when (selectedEntries.size) {
          0 -> "Select a DEX"
          1 -> stringResource(R.string.preview_load_dex)
          else -> "Load ${selectedEntries.size} DEXes"
        }
      Text(buttonText)
    }
  }
}
