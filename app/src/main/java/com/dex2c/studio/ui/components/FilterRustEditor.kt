package com.dex2c.studio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dex2c.studio.data.SmaliSyntaxTheme
import com.dex2c.studio.nativeengine.EditorNativeEngine
import com.dex2c.studio.nativeengine.EditorSessionInterface
import com.dex2c.studio.nativeengine.NativeTokenSpan
import com.dex2c.studio.ui.theme.JetBrainsMonoFontFamily
import com.dex2c.studio.ui.theme.LocalSyntaxPalette
import com.dex2c.studio.ui.theme.SyntaxPalette
import com.dex2c.studio.ui.theme.resolveSmaliSyntaxPalette
import kotlinx.coroutines.launch

/**
 * High-performance Jetpack Compose virtualized editor for `filter.txt` rules
 * backed by the Rust native engine (`librust_dex2c.so` / `dex2c-editor`).
 *
 * Supports syntax tokenization for comments (`#`), exclusions (`!`), exact matches (`=`),
 * and regex/wildcard patterns with line numbers, instant undo/redo, and inline search.
 */
@Composable
fun FilterRustEditor(
  filterText: String,
  onFilterTextChanged: (String) -> Unit,
  modifier: Modifier = Modifier,
  isReadOnly: Boolean = false,
  syntaxTheme: SmaliSyntaxTheme = SmaliSyntaxTheme.APP_DEFAULT,
  fontSizeSp: Int = 12,
  showLineNumbers: Boolean = true,
  wordWrap: Boolean = true,
) {
  val defaultPalette = LocalSyntaxPalette.current
  val resolvedPalette = remember(syntaxTheme, defaultPalette) {
    resolveSmaliSyntaxPalette(syntaxTheme, defaultPalette)
  }

  val currentOnTextChanged by rememberUpdatedState(onFilterTextChanged)
  val scope = rememberCoroutineScope()

  // Native editor session backed by Rust B-Tree rope with FilterTxt grammar
  val session = remember(filterText) {
    EditorNativeEngine.createSession(filterText, EditorNativeEngine.GRAMMAR_FILTER_TXT)
  }

  DisposableEffect(session) {
    onDispose {
      session.close()
    }
  }

  val lineCount = session.lineCount
  val listState = rememberLazyListState()
  val hScroll = rememberScrollState()

  // Editor states
  val searchQuery = ""
  var editingLineIndex by remember { mutableStateOf<Int?>(null) }
  var isBulkEditOpen by remember { mutableStateOf(false) }

  // Count rules and exclusions
  val ruleStats = remember(filterText) {
    var rules = 0
    var exclusions = 0
    filterText.lineSequence().forEach { rawLine ->
      val trimmed = rawLine.trim()
      if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
        rules++
        if (trimmed.startsWith("!")) {
          exclusions++
        }
      }
    }
    rules to exclusions
  }

  val lineDigits = remember(lineCount) {
    lineCount.coerceAtLeast(1).toString().length.coerceAtLeast(2)
  }
  val gutterWidth = (lineDigits * 8 + 14).dp
  val fontSp = fontSizeSp.sp
  val lineNumberSp = (fontSizeSp - 1).coerceAtLeast(9).sp
  val lineHeightSp = (fontSizeSp + 6).sp

  Column(
    modifier = modifier
      .testTag("input_filter_txt")
      .clip(RoundedCornerShape(16.dp))
      .background(resolvedPalette.codeBackground)
      .border(
        1.dp,
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
        RoundedCornerShape(16.dp),
      )
  ) {
    // --- Header Toolbar ---
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
        .padding(horizontal = 10.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      // Rust Engine Status Badge
      Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
      ) {
        Text(
          text = "⚡ Rust Engine",
          style = MaterialTheme.typography.labelSmall.copy(
            fontFamily = JetBrainsMonoFontFamily,
            fontWeight = FontWeight.Bold,
          ),
          color = MaterialTheme.colorScheme.onPrimaryContainer,
          modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
      }

      // Rule stats pill
      Text(
        text = "${ruleStats.first} rules (${ruleStats.second} excl)",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Spacer(Modifier.weight(1f))

      // Direct / Bulk Edit button ONLY
      if (!isReadOnly) {
        IconButton(
          onClick = { isBulkEditOpen = true },
          modifier = Modifier.size(28.dp).testTag("filter_bulk_edit_button"),
        ) {
          Icon(
            imageVector = Icons.Rounded.Edit,
            contentDescription = "Edit Raw Text",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
          )
        }
      }
    }

    // --- Virtualized Editor Code List ---
    SelectionContainer(modifier = Modifier.weight(1f).fillMaxWidth()) {
      val columnModifier = Modifier
        .fillMaxSize()
        .then(if (!wordWrap) Modifier.horizontalScroll(hScroll) else Modifier)
        .padding(horizontal = 6.dp, vertical = 6.dp)

      LazyColumn(
        state = listState,
        modifier = columnModifier.testTag("filter_code_list"),
      ) {
        items(
          count = lineCount,
          key = { it },
        ) { lineIdx ->
          val lineText = remember(session, lineIdx) {
            session.getLines(lineIdx, 1).firstOrNull() ?: ""
          }
          val tokenSpans = remember(session, lineIdx) {
            session.getLineTokens(lineIdx, 1).firstOrNull() ?: emptyList()
          }

          val isMatchingSearch = remember(lineText, searchQuery) {
            searchQuery.isNotBlank() && lineText.contains(searchQuery, ignoreCase = true)
          }

          val annotatedLine = remember(lineText, tokenSpans, resolvedPalette, searchQuery) {
            buildAnnotatedFilterLine(lineText, tokenSpans, resolvedPalette, searchQuery)
          }

          val lineBg = when {
            isMatchingSearch -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
            else -> Color.Transparent
          }

          Row(
            modifier = Modifier
              .fillMaxWidth()
              .background(lineBg)
              .clickable(enabled = !isReadOnly) {
                editingLineIndex = lineIdx
              }
              .padding(vertical = 1.dp),
            verticalAlignment = Alignment.Top,
          ) {
            if (showLineNumbers) {
              Text(
                text = "${lineIdx + 1}",
                color = resolvedPalette.lineNumber,
                fontSize = lineNumberSp,
                lineHeight = lineHeightSp,
                fontFamily = JetBrainsMonoFontFamily,
                textAlign = TextAlign.End,
                modifier = Modifier
                  .width(gutterWidth)
                  .padding(end = 8.dp),
              )
            }

            Text(
              text = annotatedLine,
              fontSize = fontSp,
              lineHeight = lineHeightSp,
              fontFamily = JetBrainsMonoFontFamily,
              softWrap = wordWrap,
              modifier = if (wordWrap) Modifier.weight(1f) else Modifier,
            )
          }
        }
      }
    }
  }

  // --- Line Edit Dialog ---
  if (editingLineIndex != null) {
    val lineIdx = editingLineIndex!!
    val currentLineText = remember(lineIdx) {
      session.getLines(lineIdx, 1).firstOrNull() ?: ""
    }
    var editedText by remember(lineIdx) { mutableStateOf(currentLineText) }

    AlertDialog(
      onDismissRequest = { editingLineIndex = null },
      title = { Text("Edit Rule (Line ${lineIdx + 1})") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = editedText,
            onValueChange = { editedText = it },
            label = { Text("Rule Syntax") },
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().testTag("filter_line_edit_input"),
          )
          Text(
            text = "Prefix '!': exclude  •  '=': exact match  •  '#': comment",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            val allLines = session.getLines(0, session.lineCount).toMutableList()
            if (lineIdx in 0 until allLines.size) {
              allLines[lineIdx] = editedText
              val newFullText = allLines.joinToString("\n")
              currentOnTextChanged(newFullText)
            }
            editingLineIndex = null
          },
        ) {
          Text("Save")
        }
      },
      dismissButton = {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          TextButton(
            onClick = {
              val allLines = session.getLines(0, session.lineCount).toMutableList()
              if (lineIdx in 0 until allLines.size) {
                allLines.removeAt(lineIdx)
                val newFullText = allLines.joinToString("\n")
                currentOnTextChanged(newFullText)
              }
              editingLineIndex = null
            },
          ) {
            Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Delete Line", color = MaterialTheme.colorScheme.error)
          }

          TextButton(onClick = { editingLineIndex = null }) {
            Text("Cancel")
          }
        }
      },
    )
  }

  // --- Bulk / Raw Edit Dialog ---
  if (isBulkEditOpen) {
    var rawText by remember { mutableStateOf(filterText) }

    AlertDialog(
      onDismissRequest = { isBulkEditOpen = false },
      title = { Text("Edit Raw filter.txt") },
      text = {
        OutlinedTextField(
          value = rawText,
          onValueChange = { rawText = it },
          shape = RoundedCornerShape(12.dp),
          textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
          modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .testTag("filter_bulk_edit_input"),
        )
      },
      confirmButton = {
        Button(
          onClick = {
            currentOnTextChanged(rawText)
            isBulkEditOpen = false
          },
        ) {
          Text("Apply Changes")
        }
      },
      dismissButton = {
        OutlinedButton(onClick = { isBulkEditOpen = false }) {
          Text("Cancel")
        }
      },
    )
  }
}

/**
 * Builds syntax-highlighted [AnnotatedString] specifically for `filter.txt` rules.
 */
internal fun buildAnnotatedFilterLine(
  text: String,
  spans: List<NativeTokenSpan>,
  palette: SyntaxPalette,
  searchQuery: String = "",
): AnnotatedString {
  if (text.isEmpty()) {
    return AnnotatedString("")
  }

  return buildAnnotatedString {
    append(text)
    // Base default style
    addStyle(SpanStyle(color = palette.defaultText), 0, text.length)

    for (span in spans) {
      val start = span.startCol.coerceIn(0, text.length)
      val end = span.endCol.coerceIn(start, text.length)
      if (start < end) {
        val (color, fontWeight) = when (span.kind) {
          NativeTokenSpan.KIND_COMMENT -> palette.comment to FontWeight.Normal
          NativeTokenSpan.KIND_FILTER_EXCLUSION -> Color(0xFFEF5350) to FontWeight.SemiBold
          NativeTokenSpan.KIND_FILTER_EXACT -> Color(0xFF26C6DA) to FontWeight.Medium
          NativeTokenSpan.KIND_FILTER_PATTERN -> palette.memberTarget to FontWeight.Normal
          else -> palette.defaultText to FontWeight.Normal
        }

        addStyle(
          SpanStyle(
            color = color,
            fontWeight = fontWeight,
          ),
          start,
          end,
        )
      }
    }

    // Highlight search query matches
    if (searchQuery.isNotBlank()) {
      var searchStart = 0
      while (searchStart < text.length) {
        val foundIndex = text.indexOf(searchQuery, searchStart, ignoreCase = true)
        if (foundIndex == -1) break
        val foundEnd = (foundIndex + searchQuery.length).coerceAtMost(text.length)
        addStyle(
          SpanStyle(
            background = Color(0xFFFFD54F).copy(alpha = 0.5f),
            fontWeight = FontWeight.Bold,
          ),
          foundIndex,
          foundEnd,
        )
        searchStart = foundEnd
      }
    }
  }
}
