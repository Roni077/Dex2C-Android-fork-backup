package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.DataArray
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.dex.BasicBlockInfo
import com.example.dex.BlockTerminatorKind
import com.example.dex.CfgEdge
import com.example.dex.CfgEdgeKind
import com.example.dex.DalvikPayload
import com.example.dex.MethodControlFlowGraph
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalEditorSettings
import com.example.ui.theme.LocalSyntaxPalette
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DalvikCfgViewer(
  cfg: MethodControlFlowGraph,
  modifier: Modifier = Modifier,
) {
  if (cfg.blocks.isEmpty()) {
    Surface(
      modifier = modifier.fillMaxWidth().testTag("cfg_empty_state"),
      shape = RoundedCornerShape(20.dp),
      color = MaterialTheme.colorScheme.surfaceContainerLow,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
    ) {
      Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(
          imageVector = Icons.Rounded.AccountTree,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(36.dp),
        )
        Text(
          text = stringResource(R.string.cfg_empty_title),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
        )
        Text(
          text = stringResource(R.string.cfg_empty_body),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    return
  }

  val listState = rememberLazyListState()
  val scope = rememberCoroutineScope()
  var focusedBlockId by remember(cfg) { mutableStateOf(cfg.blocks.firstOrNull()?.id) }
  val blockIndexMap = remember(cfg) { cfg.blocks.mapIndexed { idx, b -> b.id to idx }.toMap() }

  fun jumpToBlock(blockId: String) {
    focusedBlockId = blockId
    val idx = blockIndexMap[blockId] ?: return
    scope.launch {
      listState.animateScrollToItem(idx)
    }
  }

  Column(
    modifier = modifier.fillMaxSize().testTag("cfg_viewer_container"),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      TagPill(
        text = "${cfg.blocks.size} blocks",
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.testTag("cfg_stat_blocks"),
      )
      TagPill(
        text = "${cfg.edgeCount} edges",
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.testTag("cfg_stat_edges"),
      )
      TagPill(
        text = "${cfg.branchCount} branches",
        modifier = Modifier.testTag("cfg_stat_branches"),
      )
      if (cfg.exceptionEdgeCount > 0) {
        TagPill(
          text = "${cfg.exceptionEdgeCount} catch edges",
          containerColor = MaterialTheme.colorScheme.errorContainer,
          contentColor = MaterialTheme.colorScheme.onErrorContainer,
          modifier = Modifier.testTag("cfg_stat_catches"),
        )
      }
      if (cfg.payloads.isNotEmpty()) {
        TagPill(
          text = "${cfg.payloads.size} payloads",
          containerColor = MaterialTheme.colorScheme.tertiaryContainer,
          contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
          modifier = Modifier.testTag("cfg_stat_payloads"),
        )
      }
      TagPill(text = cfg.engineLabel)
    }

    LazyColumn(
      state = listState,
      modifier = Modifier.weight(1f).fillMaxWidth().testTag("cfg_blocks_list"),
      contentPadding = PaddingValues(bottom = 12.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      itemsIndexed(cfg.blocks, key = { _, b -> b.id }) { _, block ->
        BasicBlockCard(
          block = block,
          isFocused = block.id == focusedBlockId,
          onSelectBlock = { targetId -> jumpToBlock(targetId) },
        )
      }

      if (cfg.payloads.isNotEmpty()) {
        item(key = "cfg_payloads_header") {
          Spacer(Modifier.height(4.dp))
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 2.dp).testTag("cfg_payloads_section"),
          ) {
            Icon(
              imageVector = Icons.Rounded.DataArray,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.tertiary,
              modifier = Modifier.size(18.dp),
            )
            Text(
              text = stringResource(R.string.cfg_payloads_title),
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.SemiBold,
            )
          }
        }

        itemsIndexed(cfg.payloads, key = { idx, p -> "payload_${p.offsetWords}_$idx" }) { _, payload ->
          PayloadDetailCard(payload = payload)
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BasicBlockCard(
  block: BasicBlockInfo,
  isFocused: Boolean,
  onSelectBlock: (String) -> Unit,
) {
  val palette = LocalSyntaxPalette.current
  val editorSettings = LocalEditorSettings.current
  val fontSp = (editorSettings.fontSizeSp - 1).coerceAtLeast(10).sp
  val lineHeightSp = (editorSettings.fontSizeSp + 4).sp

  val borderColor =
    if (isFocused) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)

  Surface(
    modifier = Modifier.fillMaxWidth().testTag("cfg_block_${block.id}"),
    shape = RoundedCornerShape(20.dp),
    color = if (isFocused) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer,
    border = BorderStroke(if (isFocused) 1.5.dp else 1.dp, borderColor),
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          TagPill(
            text = block.id,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
          )
          Text(
            text = "L_%04x..L_%04x".format(block.startOffsetWords, block.endOffsetWords),
            style = MaterialTheme.typography.labelMedium,
            fontFamily = JetBrainsMonoFontFamily,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          if (block.isEntry) {
            TagPill(
              text = "ENTRY",
              containerColor = MaterialTheme.colorScheme.secondaryContainer,
              contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
          }
          if (block.isExceptionHandler) {
            TagPill(
              text = "CATCH",
              containerColor = MaterialTheme.colorScheme.errorContainer,
              contentColor = MaterialTheme.colorScheme.onErrorContainer,
            )
          }
          TagPill(text = block.terminatorKind.badgeLabel)
        }
      }

      if (block.predecessors.isNotEmpty()) {
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text(
            text = "${stringResource(R.string.cfg_predecessors_label)}:",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterVertically),
          )
          block.predecessors.forEach { predId ->
            Surface(
              onClick = { onSelectBlock(predId) },
              shape = RoundedCornerShape(10.dp),
              color = MaterialTheme.colorScheme.surfaceContainerHighest,
              modifier = Modifier.minimumInteractiveComponentSize().semantics(mergeDescendants = true) {},
            ) {
              Text(
                text = "← $predId",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = JetBrainsMonoFontFamily,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
              )
            }
          }
        }
      }

      Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = palette.codeBackground,
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(10.dp),
          verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
          block.instructions.forEach { insn ->
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                text = "%04X:".format(insn.offsetWords),
                color = palette.label,
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = fontSp,
                lineHeight = lineHeightSp,
              )
              Spacer(Modifier.width(8.dp))
              Text(
                text = insn.mnemonic.padEnd(20, ' '),
                color = palette.opcode,
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = fontSp,
                lineHeight = lineHeightSp,
              )
              if (insn.operandsText.isNotEmpty()) {
                Text(
                  text = insn.operandsText,
                  color = palette.defaultText,
                  fontFamily = JetBrainsMonoFontFamily,
                  fontSize = fontSp,
                  lineHeight = lineHeightSp,
                )
              }
            }
          }
        }
      }

      if (block.outgoingEdges.isNotEmpty()) {
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text(
            text = "${stringResource(R.string.cfg_outgoing_label)}:",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterVertically),
          )
          block.outgoingEdges.forEachIndexed { idx, edge ->
            CfgEdgePill(
              edge = edge,
              onClick = { onSelectBlock(edge.toBlockId) },
              modifier = Modifier.testTag("cfg_edge_${block.id}_${edge.toBlockId}_$idx"),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun CfgEdgePill(
  edge: CfgEdge,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val (container, content) =
    when (edge.kind) {
      CfgEdgeKind.CONDITIONAL_BRANCH,
      CfgEdgeKind.SWITCH_CASE ->
        MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
      CfgEdgeKind.UNCONDITIONAL_JUMP ->
        MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
      CfgEdgeKind.EXCEPTION_HANDLER ->
        MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
      CfgEdgeKind.FALLTHROUGH ->
        MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurface
    }

  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(12.dp),
    color = container,
    modifier = modifier.minimumInteractiveComponentSize().semantics(mergeDescendants = true) {},
  ) {
    Text(
      text = "${edge.label} → ${edge.toBlockId} (L_%04x)".format(edge.toOffsetWords),
      style = MaterialTheme.typography.labelSmall,
      fontFamily = JetBrainsMonoFontFamily,
      fontWeight = FontWeight.Medium,
      color = content,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
    )
  }
}

@Composable
private fun PayloadDetailCard(
  payload: DalvikPayload,
) {
  Surface(
    modifier = Modifier.fillMaxWidth().testTag("cfg_payload_${payload.offsetWords}"),
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = payload.summaryLabel,
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.SemiBold,
        )
        TagPill(
          text = "L_%04x · ${payload.wordCount}w".format(payload.offsetWords),
          containerColor = MaterialTheme.colorScheme.tertiaryContainer,
          contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        )
      }

      when (payload) {
        is DalvikPayload.PackedSwitch -> {
          val entries =
            payload.targetOffsets.mapIndexed { idx, tgt ->
              "case ${payload.firstKey + idx} → L_%04x".format(tgt)
            }
          Text(
            text = entries.joinToString("  •  "),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = JetBrainsMonoFontFamily,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        is DalvikPayload.SparseSwitch -> {
          val entries =
            payload.cases.map { (k, tgt) ->
              "case $k → L_%04x".format(tgt)
            }
          Text(
            text = entries.joinToString("  •  "),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = JetBrainsMonoFontFamily,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        is DalvikPayload.FillArrayData -> {
          if (payload.previewElements.isNotEmpty()) {
            Text(
              text = "Values: [${payload.previewElements.joinToString(", ")}]",
              style = MaterialTheme.typography.bodySmall,
              fontFamily = JetBrainsMonoFontFamily,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
          if (payload.hexPreview.isNotBlank()) {
            Text(
              text = "Raw: ${payload.hexPreview}",
              style = MaterialTheme.typography.labelSmall,
              fontFamily = JetBrainsMonoFontFamily,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
  }
}
