package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.dex.BytecodeOverviewSummary
import com.example.dex.ClassDefinitionSummary
import com.example.ui.theme.JetBrainsMonoFontFamily

@Composable
fun BytecodeOverviewCard(
  summary: BytecodeOverviewSummary?,
  isLive: Boolean,
  canReopenLast: Boolean,
  onBrowseClasses: () -> Unit,
  onReopenLast: () -> Unit,
  onInspectSelfApk: () -> Unit,
  modifier: Modifier = Modifier,
  compact: Boolean = false,
) {
  val statusText =
    when {
      summary != null && isLive -> "Live · v${summary.dexVersion}"
      summary != null -> "Last Analyzed · v${summary.dexVersion}"
      else -> stringResource(R.string.bytecode_overview_awaiting)
    }
  val statusContainerColor =
    when {
      summary != null && isLive -> MaterialTheme.colorScheme.primaryContainer
      summary != null -> MaterialTheme.colorScheme.secondaryContainer
      else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
  val statusContentColor =
    when {
      summary != null && isLive -> MaterialTheme.colorScheme.onPrimaryContainer
      summary != null -> MaterialTheme.colorScheme.onSecondaryContainer
      else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

  Surface(
    modifier = modifier.fillMaxWidth().testTag("bytecode_overview_card"),
    shape = RoundedCornerShape(24.dp),
    color = MaterialTheme.colorScheme.surfaceContainer,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
  ) {
    Column(
      modifier = Modifier.padding(if (compact) 14.dp else 18.dp),
      verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          modifier = Modifier.weight(1f),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Surface(
            modifier = Modifier.size(40.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.Rounded.Memory,
                contentDescription = stringResource(R.string.bytecode_overview_title),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp),
              )
            }
          }
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = stringResource(R.string.bytecode_overview_title),
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            val subtitle =
              when {
                summary == null -> stringResource(R.string.bytecode_overview_no_dex_subtitle)
                summary.dexCount > 1 -> "${summary.sourceLabel} · ${summary.entryName} (${summary.dexCount} DEXes)"
                else -> "${summary.sourceLabel} · ${summary.entryName}"
              }
            Text(
              text = subtitle,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
        Spacer(Modifier.width(8.dp))
        TagPill(
          text = statusText,
          containerColor = statusContainerColor,
          contentColor = statusContentColor,
          modifier = Modifier.testTag("bytecode_overview_status_pill"),
        )
      }

      if (summary != null) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OverviewStatBox(
            label = stringResource(R.string.bytecode_overview_methods_label),
            headline = "%,d total".format(summary.totalMethods),
            subline = "%,d with bytecode".format(summary.bytecodeMethods),
            icon = Icons.Rounded.Code,
            accent = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.weight(1f).testTag("bytecode_overview_methods"),
          )
          OverviewStatBox(
            label = stringResource(R.string.bytecode_overview_classes_label),
            headline = "%,d defs".format(summary.totalClassDefinitions),
            subline = "%,d app · %,d str".format(summary.appClassDefinitions, summary.totalStrings),
            icon = Icons.Rounded.DataObject,
            accent = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f).testTag("bytecode_overview_classes"),
          )
        }

        Column(
          modifier = Modifier.fillMaxWidth().testTag("bytecode_overview_top_classes"),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text(
            text = stringResource(R.string.bytecode_overview_top_classes),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          if (summary.topClasses.isNotEmpty()) {
            summary.topClasses.forEach { cls ->
              TopClassDefinitionRow(cls = cls)
            }
          } else {
            Surface(
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(14.dp),
              color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
              Text(
                text = stringResource(R.string.bytecode_overview_no_classes_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
              )
            }
          }
        }
      } else {
        Surface(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(16.dp),
          color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
          Text(
            text = stringResource(R.string.bytecode_overview_empty_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(12.dp),
          )
        }
      }

      when {
        isLive -> {
          FilledTonalButton(
            onClick = onBrowseClasses,
            shape = RoundedCornerShape(20.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("bytecode_overview_action_btn"),
          ) {
            Text(stringResource(R.string.bytecode_overview_browse_classes), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(6.dp))
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
          }
        }

        summary != null && canReopenLast -> {
          FilledTonalButton(
            onClick = onReopenLast,
            shape = RoundedCornerShape(20.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("bytecode_overview_action_btn"),
          ) {
            Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.bytecode_overview_reopen_last), maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
        }

        else -> {
          FilledTonalButton(
            onClick = onInspectSelfApk,
            shape = RoundedCornerShape(20.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("bytecode_overview_action_btn"),
          ) {
            Icon(Icons.Rounded.Android, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.action_inspect_self_apk), maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
        }
      }
    }
  }
}

@Composable
private fun OverviewStatBox(
  label: String,
  headline: String,
  subline: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  accent: androidx.compose.ui.graphics.Color,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier.semantics(mergeDescendants = true) {},
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(imageVector = icon, contentDescription = null, tint = accent, modifier = Modifier.size(15.dp))
        Text(
          text = label,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Text(
        text = headline,
        style = MaterialTheme.typography.titleMedium,
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = subline,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun TopClassDefinitionRow(
  cls: ClassDefinitionSummary,
) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(14.dp),
    color = MaterialTheme.colorScheme.surfaceContainerLow,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = cls.simpleName,
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = cls.packageName.ifBlank { stringResource(R.string.label_default_package) },
          style = MaterialTheme.typography.labelSmall,
          fontFamily = JetBrainsMonoFontFamily,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Spacer(Modifier.width(8.dp))
      TagPill(
        text = "${cls.methodCount} methods",
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}
