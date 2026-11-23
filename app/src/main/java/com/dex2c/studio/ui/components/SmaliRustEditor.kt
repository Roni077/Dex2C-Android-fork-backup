package com.dex2c.studio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import java.lang.ref.WeakReference

/**
 * Controller to drive Undo/Redo operations on [SmaliRustEditor] from external UI toolbars.
 */
class RustUndoController {
  internal var sessionRef: WeakReference<EditorSessionInterface>? = null
  var canUndoState by mutableStateOf(false)
    internal set
  var canRedoState by mutableStateOf(false)
    internal set

  fun undo() {
    sessionRef?.get()?.let { session ->
      if (session.canUndo()) {
        session.undo()
        updateState()
      }
    }
  }

  fun redo() {
    sessionRef?.get()?.let { session ->
      if (session.canRedo()) {
        session.redo()
        updateState()
      }
    }
  }

  fun updateState() {
    val session = sessionRef?.get()
    canUndoState = session?.canUndo() == true
    canRedoState = session?.canRedo() == true
  }
}

/**
 * High-performance Jetpack Compose virtualized Code Editor backed by the
 * Rust native engine (`librust_dex2c.so` / `dex2c-editor`).
 *
 * Provides sub-millisecond line queries, instant O(1) label jumping, zero GC pressure on
 * 100,000+ line Smali files, and seamless Material 3 syntax theming.
 */
@Composable
fun SmaliRustEditor(
  text: String,
  modifier: Modifier = Modifier,
  isReadOnly: Boolean = true,
  activeLineIndex: Int? = null,
  activeColumnIndex: Int? = null,
  onTextChanged: ((String) -> Unit)? = null,
  onLineClick: ((Int) -> Unit)? = null,
  onLabelJump: ((targetLine: Int) -> Unit)? = null,
  syntaxTheme: SmaliSyntaxTheme = SmaliSyntaxTheme.APP_DEFAULT,
  fontSizeSp: Int = 12,
  showLineNumbers: Boolean = true,
  wordWrap: Boolean = false,
  undoController: RustUndoController? = null,
) {
  val defaultPalette = LocalSyntaxPalette.current
  val resolvedPalette = remember(syntaxTheme, defaultPalette) {
    resolveSmaliSyntaxPalette(syntaxTheme, defaultPalette)
  }

  val currentOnTextChanged by rememberUpdatedState(onTextChanged)
  val currentOnLineClick by rememberUpdatedState(onLineClick)
  val currentOnLabelJump by rememberUpdatedState(onLabelJump)
  val scope = rememberCoroutineScope()

  // Initialize native editor session
  val session = remember(text) {
    EditorNativeEngine.createSession(text)
  }

  DisposableEffect(session) {
    undoController?.let {
      it.sessionRef = WeakReference(session)
      it.updateState()
    }
    onDispose {
      undoController?.sessionRef = null
      session.close()
    }
  }

  val lineCount = session.lineCount
  val listState = rememberLazyListState()
  val hScroll = rememberScrollState()

  // Jump to active line if specified
  LaunchedEffect(activeLineIndex) {
    if (activeLineIndex != null && activeLineIndex in 0 until lineCount) {
      listState.animateScrollToItem(activeLineIndex)
    }
  }

  val lineDigits = remember(lineCount) {
    lineCount.coerceAtLeast(1).toString().length.coerceAtLeast(3)
  }
  val gutterWidth = (lineDigits * 8 + 16).dp
  val fontSp = fontSizeSp.sp
  val lineNumberSp = (fontSizeSp - 1).coerceAtLeast(9).sp
  val lineHeightSp = (fontSizeSp + 6).sp

  Box(
    modifier = modifier
      .testTag("smali_rust_editor")
      .clip(RoundedCornerShape(20.dp))
      .background(resolvedPalette.codeBackground)
      .border(
        1.dp,
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
        RoundedCornerShape(20.dp),
      )
  ) {
    SelectionContainer {
      LazyColumn(
        state = listState,
        modifier = Modifier
          .fillMaxSize()
          .horizontalScroll(hScroll)
          .padding(horizontal = 8.dp, vertical = 8.dp)
          .testTag("smali_rust_code_list"),
      ) {
        items(
          count = lineCount,
          key = { it },
        ) { lineIdx ->
          val isCurrentActive = activeLineIndex == lineIdx

          // Fetch single line content and tokens directly from the engine
          val lineText = remember(session, lineIdx) {
            session.getLines(lineIdx, 1).firstOrNull() ?: ""
          }
          val tokenSpans = remember(session, lineIdx) {
            session.getLineTokens(lineIdx, 1).firstOrNull() ?: emptyList()
          }

          val annotatedLine = remember(lineText, tokenSpans, resolvedPalette) {
            buildAnnotatedLine(lineText, tokenSpans, resolvedPalette)
          }

          val lineBg = when {
            isCurrentActive -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f)
            else -> Color.Transparent
          }

          Row(
            modifier = Modifier
              .fillMaxWidth()
              .background(lineBg)
              .clickable {
                currentOnLineClick?.invoke(lineIdx)
                val targetJump = session.resolveLabelJump(lineIdx)
                if (targetJump != null) {
                  currentOnLabelJump?.invoke(targetJump)
                  scope.launch {
                    listState.animateScrollToItem(targetJump)
                  }
                }
              }
              .padding(vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            if (showLineNumbers) {
              Text(
                text = "${lineIdx + 1}",
                color = if (isCurrentActive) resolvedPalette.label else resolvedPalette.lineNumber,
                fontSize = lineNumberSp,
                lineHeight = lineHeightSp,
                fontFamily = JetBrainsMonoFontFamily,
                textAlign = TextAlign.End,
                modifier = Modifier
                  .width(gutterWidth)
                  .padding(end = 10.dp),
              )
            }

            Text(
              text = annotatedLine,
              fontSize = fontSp,
              lineHeight = lineHeightSp,
              fontFamily = JetBrainsMonoFontFamily,
            )
          }
        }
      }
    }
  }
}

/**
 * Builds an [AnnotatedString] from raw line text and [NativeTokenSpan] items.
 */
internal fun buildAnnotatedLine(
  text: String,
  spans: List<NativeTokenSpan>,
  palette: SyntaxPalette,
): AnnotatedString {
  if (spans.isEmpty() || text.isEmpty()) {
    return AnnotatedString(text, spanStyles = listOf(
      AnnotatedString.Range(
        SpanStyle(color = palette.defaultText),
        0,
        text.length,
      )
    ))
  }

  return buildAnnotatedString {
    append(text)
    // Default style
    addStyle(SpanStyle(color = palette.defaultText), 0, text.length)

    for (span in spans) {
      val start = span.startCol.coerceIn(0, text.length)
      val end = span.endCol.coerceIn(start, text.length)
      if (start < end) {
        val color = when (span.kind) {
          NativeTokenSpan.KIND_DIRECTIVE -> palette.keyword
          NativeTokenSpan.KIND_OPCODE -> palette.opcode
          NativeTokenSpan.KIND_REGISTER -> palette.register
          NativeTokenSpan.KIND_LABEL_DEF, NativeTokenSpan.KIND_LABEL_REF -> palette.label
          NativeTokenSpan.KIND_MEMBER_REF -> palette.memberTarget
          NativeTokenSpan.KIND_TYPE_DESCRIPTOR -> palette.typeName
          NativeTokenSpan.KIND_STRING_LITERAL -> palette.stringLiteral
          NativeTokenSpan.KIND_COMMENT -> palette.comment
          NativeTokenSpan.KIND_NUMBER -> palette.numberLiteral
          else -> palette.defaultText
        }
        val isBold = span.kind == NativeTokenSpan.KIND_DIRECTIVE || span.kind == NativeTokenSpan.KIND_OPCODE
        addStyle(
          SpanStyle(
            color = color,
            fontWeight = if (isBold) FontWeight.SemiBold else FontWeight.Normal,
          ),
          start,
          end,
        )
      }
    }
  }
}
