package com.dex2c.studio.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dex2c.studio.R
import com.dex2c.studio.dex.formatAccessFlags
import com.dex2c.studio.ui.MethodInspectionState
import com.dex2c.studio.ui.components.DalvikCfgViewer
import com.dex2c.studio.ui.components.DalvikInstructionViewer
import com.dex2c.studio.ui.components.DexMethodMetaViewer
import com.dex2c.studio.ui.components.ExpressiveMotion
import com.dex2c.studio.ui.components.SmaliSyntaxCodeViewer
import com.dex2c.studio.ui.components.SyntaxCodeViewer
import com.dex2c.studio.ui.components.TagPill

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MethodDetailScreen(
  inspection: MethodInspectionState,
  isSelected: Boolean,
  onToggleSelect: () -> Unit,
  onCopyCCode: (String) -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  onOpenClassSmali: () -> Unit = {},
) {
  BackHandler(onBack = onBack)
  var selectedTab by rememberSaveable { mutableIntStateOf(2) }

  val resolvedMethodSmali =
    remember(inspection.methodSmali, inspection.method, inspection.instructions) {
      inspection.methodSmali.ifBlank {
        val flags = formatAccessFlags(inspection.method.accessFlags, isMethod = true)
        val flagPart = if (flags.isBlank()) "" else "$flags "
        val sig = "${inspection.method.methodRef.name}${inspection.method.methodRef.jniSignature()}"
        buildString {
          appendLine(".method $flagPart$sig")
          inspection.method.codeMeta?.let { meta ->
            appendLine("    .registers ${meta.registersSize}")
          }
          if (inspection.instructions.isNotEmpty()) {
            appendLine()
            for (insn in inspection.instructions) {
              val ops = if (insn.operandsText.isBlank()) "" else " ${insn.operandsText}"
              appendLine("    ${insn.mnemonic}$ops")
            }
          }
          append(".end method")
        }
      }
    }
  val smaliLines = remember(resolvedMethodSmali) { resolvedMethodSmali.lines() }

  Column(modifier = modifier.fillMaxSize()) {
    TopAppBar(
      windowInsets = WindowInsets(0, 0, 0, 0),
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
        TextButton(
          onClick = onOpenClassSmali,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("method_view_smali_btn"),
        ) {
          Icon(
            imageVector = Icons.Rounded.Code,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
          Spacer(Modifier.width(4.dp))
          Text(stringResource(R.string.action_class_smali))
        }
        IconButton(
          onClick = {
            val activeCopyText =
              when (selectedTab) {
                4 -> resolvedMethodSmali
                5 -> inspection.decompiledJava
                else -> inspection.transpiledC
              }
            onCopyCCode(activeCopyText)
          },
          modifier = Modifier.minimumInteractiveComponentSize().testTag("method_copy_c_btn"),
        ) {
          Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(R.string.action_copy_code))
        }
      },
    )

    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      val meta = inspection.method.codeMeta
      Row(
        modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (meta != null) {
          TagPill("regs=${meta.registersSize}")
          TagPill("ins=${meta.insSize}")
          TagPill("${inspection.instructions.size} ops")
          if (inspection.cfg.blocks.isNotEmpty()) {
            TagPill(
              text = "${inspection.cfg.blocks.size} blocks",
              containerColor = MaterialTheme.colorScheme.primaryContainer,
              contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
          }
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
              selected = selectedTab == 0 || selectedTab == 2,
              onClick = { selectedTab = 2 },
              shape = SegmentedButtonDefaults.itemShape(0, 5),
            ) {
              Text("${stringResource(R.string.tab_dalvik)} + ${stringResource(R.string.tab_transpiled_c)}")
            }
            SegmentedButton(
              selected = selectedTab == 5,
              onClick = { selectedTab = 5 },
              shape = SegmentedButtonDefaults.itemShape(1, 5),
              modifier = Modifier.testTag("tab_method_java_wide"),
            ) {
              Text("Java")
            }
            SegmentedButton(
              selected = selectedTab == 4,
              onClick = { selectedTab = 4 },
              shape = SegmentedButtonDefaults.itemShape(2, 5),
              modifier = Modifier.testTag("tab_method_smali_wide"),
            ) {
              Text(stringResource(R.string.tab_smali))
            }
            SegmentedButton(
              selected = selectedTab == 1,
              onClick = { selectedTab = 1 },
              shape = SegmentedButtonDefaults.itemShape(3, 5),
              modifier = Modifier.testTag("tab_blocks_wide"),
            ) {
              Text(stringResource(R.string.tab_blocks))
            }
            SegmentedButton(
              selected = selectedTab == 3,
              onClick = { selectedTab = 3 },
              shape = SegmentedButtonDefaults.itemShape(4, 5),
              modifier = Modifier.testTag("tab_dex_meta_wide"),
            ) {
              Text(stringResource(R.string.tab_dex_meta))
            }
          }
          when (selectedTab) {
            1 ->
              DalvikCfgViewer(
                cfg = inspection.cfg,
                modifier = Modifier.fillMaxSize(),
              )
            3 ->
              DexMethodMetaViewer(
                meta = inspection.structuralMeta,
                tryBlocks = inspection.tryBlocks,
                modifier = Modifier.fillMaxSize(),
              )
            4 ->
              SmaliSyntaxCodeViewer(
                lines = smaliLines,
                modifier = Modifier.fillMaxSize(),
              )
            5 ->
              SyntaxCodeViewer(
                code = inspection.decompiledJava.ifBlank { "// No Java decompilation available." },
                modifier = Modifier.fillMaxSize(),
              )
            else ->
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
        val visualTabIndex =
          when (selectedTab) {
            0 -> 0
            4 -> 1
            5 -> 2
            1 -> 3
            2 -> 4
            else -> 5
          }
        Column(modifier = Modifier.fillMaxSize().padding(bottom = 12.dp)) {
          PrimaryTabRow(selectedTabIndex = visualTabIndex) {
            Tab(
              selected = selectedTab == 0,
              onClick = { selectedTab = 0 },
              text = { Text(stringResource(R.string.tab_dalvik), maxLines = 1, overflow = TextOverflow.Ellipsis) },
              modifier = Modifier.testTag("tab_dalvik_ir"),
            )
            Tab(
              selected = selectedTab == 4,
              onClick = { selectedTab = 4 },
              text = { Text(stringResource(R.string.tab_smali), maxLines = 1, overflow = TextOverflow.Ellipsis) },
              modifier = Modifier.testTag("tab_method_smali"),
            )
            Tab(
              selected = selectedTab == 5,
              onClick = { selectedTab = 5 },
              text = { Text("Java", maxLines = 1, overflow = TextOverflow.Ellipsis) },
              modifier = Modifier.testTag("tab_method_java"),
            )
            Tab(
              selected = selectedTab == 1,
              onClick = { selectedTab = 1 },
              text = { Text(stringResource(R.string.tab_blocks), maxLines = 1, overflow = TextOverflow.Ellipsis) },
              modifier = Modifier.testTag("tab_blocks"),
            )
            Tab(
              selected = selectedTab == 2,
              onClick = { selectedTab = 2 },
              text = { Text(stringResource(R.string.tab_transpiled_c), maxLines = 1, overflow = TextOverflow.Ellipsis) },
              modifier = Modifier.testTag("tab_transpiled_c"),
            )
            Tab(
              selected = selectedTab == 3,
              onClick = { selectedTab = 3 },
              text = { Text(stringResource(R.string.tab_dex_meta), maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
              4 ->
                SmaliSyntaxCodeViewer(
                  lines = smaliLines,
                  modifier = Modifier.fillMaxSize(),
                )
              5 ->
                SyntaxCodeViewer(
                  code = inspection.decompiledJava.ifBlank { "// No Java decompilation available." },
                  modifier = Modifier.fillMaxSize(),
                )
              1 ->
                DalvikCfgViewer(
                  cfg = inspection.cfg,
                  modifier = Modifier.fillMaxSize(),
                )
              2 ->
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
