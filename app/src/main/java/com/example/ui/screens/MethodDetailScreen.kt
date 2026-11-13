package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.MethodInspectionState
import com.example.ui.components.DalvikInstructionViewer
import com.example.ui.components.DexMethodMetaViewer
import com.example.ui.components.ExpressiveMotion
import com.example.ui.components.SyntaxCodeViewer
import com.example.ui.components.TagPill

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MethodDetailScreen(
  inspection: MethodInspectionState,
  isSelected: Boolean,
  onToggleSelect: () -> Unit,
  onCopyCCode: (String) -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  BackHandler(onBack = onBack)
  var selectedTab by rememberSaveable { mutableIntStateOf(1) }

  Column(modifier = modifier.fillMaxSize()) {
    TopAppBar(
      title = {
        Column {
          Text(
            text = inspection.method.methodRef.name,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = inspection.dexClass.fullName,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      },
      navigationIcon = {
        IconButton(onClick = onBack, modifier = Modifier.testTag("method_detail_back_btn")) {
          Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
        }
      },
      actions = {
        IconButton(
          onClick = { onCopyCCode(inspection.transpiledC) },
          modifier = Modifier.testTag("method_copy_c_btn"),
        ) {
          Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(R.string.action_copy_code))
        }
      },
    )

    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      val meta = inspection.method.codeMeta
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (meta != null) {
          TagPill("regs=${meta.registersSize}")
          TagPill("ins=${meta.insSize}")
          TagPill("${inspection.instructions.size} ops")
          if (inspection.tryBlocks.isNotEmpty()) {
            TagPill(
              text = "${inspection.tryBlocks.size} try/catch",
              containerColor = MaterialTheme.colorScheme.secondaryContainer,
              contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
          }
        } else {
          TagPill("No bytecode")
        }
      }

      FilledTonalButton(
        onClick = onToggleSelect,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.testTag("method_toggle_queue_btn"),
      ) {
        Icon(
          imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.AddCircleOutline,
          contentDescription = null,
          modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(if (isSelected) "Queued for C" else "Queue for C")
      }
    }

    Spacer(Modifier.height(10.dp))

    BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 4.dp)) {
      if (maxWidth >= 740.dp) {
        Column(modifier = Modifier.fillMaxSize().padding(bottom = 12.dp)) {
          SingleChoiceSegmentedButtonRow(modifier = Modifier.padding(bottom = 8.dp)) {
            SegmentedButton(
              selected = selectedTab != 2,
              onClick = { selectedTab = 1 },
              shape = SegmentedButtonDefaults.itemShape(0, 2),
            ) {
              Text("${stringResource(R.string.tab_dalvik)} + ${stringResource(R.string.tab_transpiled_c)}")
            }
            SegmentedButton(
              selected = selectedTab == 2,
              onClick = { selectedTab = 2 },
              shape = SegmentedButtonDefaults.itemShape(1, 2),
              modifier = Modifier.testTag("tab_dex_meta_wide"),
            ) {
              Text(stringResource(R.string.tab_dex_meta))
            }
          }
          if (selectedTab == 2) {
            DexMethodMetaViewer(
              meta = inspection.structuralMeta,
              tryBlocks = inspection.tryBlocks,
              modifier = Modifier.fillMaxSize(),
            )
          } else {
            Row(
              modifier = Modifier.fillMaxSize(),
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = stringResource(R.string.tab_dalvik),
                  style = MaterialTheme.typography.labelLarge,
                  color = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.padding(bottom = 6.dp),
                )
                DalvikInstructionViewer(
                  instructions = inspection.instructions,
                  tryBlocks = inspection.tryBlocks,
                  modifier = Modifier.fillMaxSize(),
                )
              }
              Column(modifier = Modifier.weight(1.15f)) {
                Text(
                  text = stringResource(R.string.tab_transpiled_c),
                  style = MaterialTheme.typography.labelLarge,
                  color = MaterialTheme.colorScheme.secondary,
                  modifier = Modifier.padding(bottom = 6.dp),
                )
                SyntaxCodeViewer(
                  code = inspection.transpiledC,
                  modifier = Modifier.fillMaxSize(),
                )
              }
            }
          }
        }
      } else {
        Column(modifier = Modifier.fillMaxSize().padding(bottom = 12.dp)) {
          PrimaryTabRow(selectedTabIndex = selectedTab) {
            Tab(
              selected = selectedTab == 0,
              onClick = { selectedTab = 0 },
              text = { Text(stringResource(R.string.tab_dalvik)) },
              modifier = Modifier.testTag("tab_dalvik_ir"),
            )
            Tab(
              selected = selectedTab == 1,
              onClick = { selectedTab = 1 },
              text = { Text(stringResource(R.string.tab_transpiled_c)) },
              modifier = Modifier.testTag("tab_transpiled_c"),
            )
            Tab(
              selected = selectedTab == 2,
              onClick = { selectedTab = 2 },
              text = { Text(stringResource(R.string.tab_dex_meta)) },
              modifier = Modifier.testTag("tab_dex_meta"),
            )
          }
          Spacer(Modifier.height(10.dp))
          AnimatedContent(
            targetState = selectedTab,
            transitionSpec = { ExpressiveMotion.sharedAxisHorizontal(targetState >= initialState) },
            label = "method_detail_tab",
            modifier = Modifier.weight(1f),
          ) { tab ->
            when (tab) {
              0 ->
                DalvikInstructionViewer(
                  instructions = inspection.instructions,
                  tryBlocks = inspection.tryBlocks,
                  modifier = Modifier.fillMaxSize(),
                )
              1 ->
                SyntaxCodeViewer(
                  code = inspection.transpiledC,
                  modifier = Modifier.fillMaxSize(),
                )
              else ->
                DexMethodMetaViewer(
                  meta = inspection.structuralMeta,
                  tryBlocks = inspection.tryBlocks,
                  modifier = Modifier.fillMaxSize(),
                )
            }
          }
        }
      }
    }
  }
}
