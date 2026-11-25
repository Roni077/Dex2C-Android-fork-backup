package com.dex2c.studio.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dex2c.studio.R
import com.dex2c.studio.cloud.GitHubWorkflowRunSummary
import com.dex2c.studio.ui.theme.JetBrainsMonoFontFamily

private enum class RunStatusFilter(val label: String) {
  ALL("All"),
  FAILED("Failed"),
  SUCCESS("Success"),
  IN_PROGRESS("In Progress"),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkflowRunsHistoryDialog(
  runs: List<GitHubWorkflowRunSummary>,
  onDismiss: () -> Unit,
  onInspectLogs: (runId: Long, runNumber: Int?, conclusion: String?) -> Unit,
  onAttachToRun: (runId: Long) -> Unit,
  onOpenBrowserUrl: (String) -> Unit,
) {
  var selectedFilter by remember { mutableStateOf(RunStatusFilter.ALL) }
  var searchQuery by remember { mutableStateOf("") }

  val filteredRuns = remember(runs, selectedFilter, searchQuery) {
    runs.filter { run ->
      val matchesFilter = when (selectedFilter) {
        RunStatusFilter.ALL -> true
        RunStatusFilter.FAILED -> run.conclusion == "failure" || run.status == "failure"
        RunStatusFilter.SUCCESS -> run.conclusion == "success"
        RunStatusFilter.IN_PROGRESS -> run.status != "completed"
      }
      val matchesSearch = if (searchQuery.isBlank()) true else {
        val q = searchQuery.trim().lowercase()
        run.runNumber.toString().contains(q) ||
          run.id.toString().contains(q) ||
          (run.displayTitle?.lowercase()?.contains(q) == true) ||
          (run.conclusion?.lowercase()?.contains(q) == true) ||
          run.createdAt.lowercase().contains(q)
      }
      matchesFilter && matchesSearch
    }
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)
        .testTag("workflow_history_dialog"),
      shape = RoundedCornerShape(24.dp),
      color = MaterialTheme.colorScheme.surfaceContainerHigh,
      tonalElevation = 6.dp,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(20.dp)
      ) {
        // Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f),
          ) {
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = MaterialTheme.colorScheme.primaryContainer,
              modifier = Modifier.size(38.dp),
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  Icons.Rounded.History,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onPrimaryContainer,
                  modifier = Modifier.size(20.dp),
                )
              }
            }
            Column {
              Text(
                text = "Workflow Run History",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
              )
              Text(
                text = "${runs.size} recorded runs in workspace",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          IconButton(
            onClick = onDismiss,
            modifier = Modifier.minimumInteractiveComponentSize(),
          ) {
            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_cancel))
          }
        }

        Spacer(Modifier.height(10.dp))

        // Search & Filter Row
        OutlinedTextField(
          value = searchQuery,
          onValueChange = { searchQuery = it },
          modifier = Modifier.fillMaxWidth().testTag("workflow_history_search_input"),
          shape = RoundedCornerShape(14.dp),
          placeholder = { Text("Search by Run #, title, date...") },
          leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
          trailingIcon = {
            if (searchQuery.isNotEmpty()) {
              IconButton(onClick = { searchQuery = "" }) {
                Icon(Icons.Rounded.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
              }
            }
          },
          singleLine = true,
        )

        Spacer(Modifier.height(8.dp))

        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.Center,
        ) {
          RunStatusFilter.entries.forEach { filter ->
            FilterChip(
              selected = selectedFilter == filter,
              onClick = { selectedFilter = filter },
              label = { Text(filter.label) },
              modifier = Modifier.testTag("filter_chip_${filter.name.lowercase()}"),
            )
          }
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        Spacer(Modifier.height(8.dp))

        // Runs List
        if (filteredRuns.isEmpty()) {
          Box(
            modifier = Modifier
              .weight(1f)
              .fillMaxWidth(),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = if (searchQuery.isNotBlank() || selectedFilter != RunStatusFilter.ALL) {
                "No workflow runs match the active filters."
              } else {
                "No workflow runs found."
              },
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        } else {
          LazyColumn(
            modifier = Modifier
              .weight(1f)
              .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            items(filteredRuns, key = { it.id }) { run ->
              val isFailed = run.conclusion == "failure" || run.status == "failure"
              Surface(
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("workflow_history_item_${run.id}"),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(
                  1.dp,
                  if (isFailed) MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
                  else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                ),
              ) {
                Column(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                ) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        text = "Run #${run.runNumber}" + (if (!run.displayTitle.isNullOrBlank()) " • ${run.displayTitle}" else ""),
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = JetBrainsMonoFontFamily,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                      val metaText = buildString {
                        append(run.createdAt)
                        run.durationFormatted?.let { dur ->
                          append(" • ")
                          append(dur)
                        }
                        run.event?.let { evt ->
                          append(" • ")
                          append(evt)
                        }
                      }
                      Text(
                        text = metaText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                    }
                    Spacer(Modifier.width(8.dp))
                    TagPill(
                      text = (run.conclusion ?: run.status).uppercase(),
                      containerColor = when (run.conclusion) {
                        "success" -> MaterialTheme.colorScheme.primaryContainer
                        "failure" -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.tertiaryContainer
                      },
                      contentColor = when (run.conclusion) {
                        "success" -> MaterialTheme.colorScheme.onPrimaryContainer
                        "failure" -> MaterialTheme.colorScheme.onErrorContainer
                        else -> MaterialTheme.colorScheme.onTertiaryContainer
                      },
                    )
                  }

                  Spacer(Modifier.height(8.dp))

                  // Actions for this run
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    // Build logs are available when build APK failed
                    if (isFailed) {
                      OutlinedButton(
                        onClick = {
                          onInspectLogs(run.id, run.runNumber, run.conclusion)
                          onDismiss()
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.testTag("run_logs_btn_${run.id}"),
                      ) {
                        Icon(Icons.Rounded.Terminal, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("View Failure Logs")
                      }
                    }

                    OutlinedButton(
                      onClick = {
                        onAttachToRun(run.id)
                        onDismiss()
                      },
                      shape = RoundedCornerShape(14.dp),
                      modifier = Modifier.testTag("run_attach_btn_${run.id}"),
                    ) {
                      Icon(Icons.Rounded.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                      Spacer(Modifier.width(4.dp))
                      Text(if (run.status != "completed") "Monitor" else "Inspect Run")
                    }

                    if (run.htmlUrl.isNotBlank()) {
                      IconButton(
                        onClick = { onOpenBrowserUrl(run.htmlUrl) },
                        modifier = Modifier.minimumInteractiveComponentSize(),
                      ) {
                        Icon(Icons.Rounded.OpenInBrowser, contentDescription = "Open in GitHub", modifier = Modifier.size(18.dp))
                      }
                    }
                  }
                }
              }
            }
          }
        }

        Spacer(Modifier.height(12.dp))

        // Footer
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End,
        ) {
          Button(
            onClick = onDismiss,
            shape = RoundedCornerShape(16.dp),
          ) {
            Text("Done")
          }
        }
      }
    }
  }
}
