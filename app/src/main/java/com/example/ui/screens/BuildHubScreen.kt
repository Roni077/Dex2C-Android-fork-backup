package com.example.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.transpiler.FilterMatchSummary
import com.example.transpiler.GeneratedBundle
import com.example.transpiler.TranspileProgress
import com.example.transpiler.TranspilerConfig

enum class BuildHubTab {
  PIPELINE,
  OUTPUT,
}

@Composable
fun BuildHubScreen(
  activeTab: BuildHubTab,
  onSelectTab: (BuildHubTab) -> Unit,
  selectedMethodCount: Int,
  config: TranspilerConfig,
  hasLoadedDex: Boolean,
  filterMatchSummary: FilterMatchSummary?,
  onUpdateConfig: ((TranspilerConfig) -> TranspilerConfig) -> Unit,
  onImportFilterTxt: () -> Unit,
  onImportDccCfg: () -> Unit,
  onApplyFilterRules: () -> Unit,
  bundle: GeneratedBundle?,
  isTranspiling: Boolean,
  onCopyFile: (String) -> Unit,
  onExportZip: () -> Unit,
  onGoToClasses: () -> Unit,
  modifier: Modifier = Modifier,
  transpileProgress: TranspileProgress? = null,
  isBundleDirty: Boolean = false,
  onEnsureBundleBuilt: () -> Unit = {},
) {
  LaunchedEffect(activeTab, selectedMethodCount, isBundleDirty) {
    if (activeTab == BuildHubTab.OUTPUT && selectedMethodCount > 0) {
      onEnsureBundleBuilt()
    }
  }

  Column(modifier = modifier.fillMaxSize()) {
    Surface(
      color = MaterialTheme.colorScheme.surfaceContainerLow,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Box(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
      ) {
        SingleChoiceSegmentedButtonRow(
          modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp),
        ) {
          SegmentedButton(
            selected = activeTab == BuildHubTab.PIPELINE,
            onClick = { onSelectTab(BuildHubTab.PIPELINE) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            modifier = Modifier.minimumInteractiveComponentSize().testTag("build_tab_pipeline"),
            icon = {
              SegmentedButtonDefaults.Icon(active = activeTab == BuildHubTab.PIPELINE) {
                Icon(
                  imageVector = Icons.Rounded.Tune,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp),
                )
              }
            },
          ) {
            Text(
              text = stringResource(R.string.nav_config),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }

          SegmentedButton(
            selected = activeTab == BuildHubTab.OUTPUT,
            onClick = { onSelectTab(BuildHubTab.OUTPUT) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            modifier = Modifier.minimumInteractiveComponentSize().testTag("build_tab_output"),
            icon = {
              SegmentedButtonDefaults.Icon(active = activeTab == BuildHubTab.OUTPUT) {
                Icon(
                  imageVector = Icons.Rounded.Code,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp),
                )
              }
            },
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                text = stringResource(R.string.nav_export),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              if (selectedMethodCount > 0) {
                Spacer(Modifier.width(6.dp))
                Badge(
                  containerColor =
                    if (activeTab == BuildHubTab.OUTPUT) {
                      MaterialTheme.colorScheme.primary
                    } else {
                      MaterialTheme.colorScheme.secondaryContainer
                    },
                  contentColor =
                    if (activeTab == BuildHubTab.OUTPUT) {
                      MaterialTheme.colorScheme.onPrimary
                    } else {
                      MaterialTheme.colorScheme.onSecondaryContainer
                    },
                ) {
                  Text("$selectedMethodCount")
                }
              }
            }
          }
        }
      }
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
      when (activeTab) {
        BuildHubTab.PIPELINE -> {
          PipelineConfigScreen(
            config = config,
            hasLoadedDex = hasLoadedDex,
            filterMatchSummary = filterMatchSummary,
            onUpdateConfig = onUpdateConfig,
            onImportFilterTxt = onImportFilterTxt,
            onImportDccCfg = onImportDccCfg,
            onApplyFilterRules = {
              onApplyFilterRules()
              onSelectTab(BuildHubTab.OUTPUT)
            },
          )
        }
        BuildHubTab.OUTPUT -> {
          OutputExportScreen(
            bundle = bundle,
            isTranspiling = isTranspiling,
            onCopyFile = onCopyFile,
            onExportZip = onExportZip,
            onGoToClasses = onGoToClasses,
            transpileProgress = transpileProgress,
          )
        }
      }
    }
  }
}
