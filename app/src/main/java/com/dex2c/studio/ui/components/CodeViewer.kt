package com.dex2c.studio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dex2c.studio.dex.DalvikInstruction
import com.dex2c.studio.dex.DalvikPayload
import com.dex2c.studio.dex.DexTryBlock
import com.dex2c.studio.dex.OpCategory
import com.dex2c.studio.ui.theme.JetBrainsMonoFontFamily
import com.dex2c.studio.ui.theme.LocalEditorSettings
import com.dex2c.studio.ui.theme.LocalSyntaxPalette
import com.dex2c.studio.ui.theme.SyntaxPalette

@Composable
fun SyntaxCodeViewer(
  code: String,
  modifier: Modifier = Modifier,
  maxLinesLimit: Int? = null,
) {
  val palette = LocalSyntaxPalette.current
  val editorSettings = LocalEditorSettings.current
  val resolvedMaxLines = maxLinesLimit ?: editorSettings.maxLinesLimit
  val codeFontSp = editorSettings.fontSizeSp.sp
  val lineHeightSp = (editorSettings.fontSizeSp + 6).sp
  val lines = remember(code, resolvedMaxLines) { code.lines().take(resolvedMaxLines) }
  val hScroll = rememberScrollState()

  Box(
    modifier =
      modifier
        .clip(RoundedCornerShape(20.dp))
        .background(palette.codeBackground)
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
  ) {
    SelectionContainer {
      LazyColumn(
        modifier = Modifier.fillMaxSize().horizontalScroll(hScroll).padding(12.dp)
      ) {
        itemsIndexed(lines) { index, line ->
          val highlighted = remember(line, palette) { highlightCLine(line, palette) }
          Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
            if (editorSettings.showLineNumbers) {
              Text(
                text = "%4d".format(index + 1),
                color = palette.lineNumber,
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = (editorSettings.fontSizeSp - 1).coerceAtLeast(9).sp,
                lineHeight = lineHeightSp,
              )
              Spacer(Modifier.width(12.dp))
            }
            Text(
              text = highlighted,
              fontFamily = JetBrainsMonoFontFamily,
              fontSize = codeFontSp,
              lineHeight = lineHeightSp,
            )
          }
        }
      }
    }
  }
}

@Composable
fun DalvikInstructionViewer(
  instructions: List<DalvikInstruction>,
  tryBlocks: List<DexTryBlock> = emptyList(),
  modifier: Modifier = Modifier,
) {
  val palette = LocalSyntaxPalette.current
  val editorSettings = LocalEditorSettings.current
  val codeFontSp = editorSettings.fontSizeSp.sp
  val addrFontSp = (editorSettings.fontSizeSp - 1).coerceAtLeast(9).sp
  val lineHeightSp = (editorSettings.fontSizeSp + 6).sp
  val hScroll = rememberScrollState()
  val tryStarts = remember(tryBlocks) { tryBlocks.groupBy { it.startOffsetWord } }

  Box(
    modifier =
      modifier
        .clip(RoundedCornerShape(20.dp))
        .background(palette.codeBackground)
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
  ) {
    SelectionContainer {
      LazyColumn(
        modifier = Modifier.fillMaxSize().horizontalScroll(hScroll).padding(12.dp)
      ) {
        itemsIndexed(instructions) { _, insn ->
          tryStarts[insn.offsetWords]?.forEach { tb ->
            val catches =
              tb.handlers.joinToString(", ") { h ->
                val t = h.typeDescriptor ?: "<all>"
                val handlerLabel = "L_%04x".format(h.handlerOffsetWord)
                "$t -> $handlerLabel"
              }
            val trySpan = "[L_%04x..L_%04x)".format(tb.startOffsetWord, tb.endOffsetWord)
            Text(
              text = ".try $trySpan .catch ($catches)",
              color = palette.label,
              fontFamily = JetBrainsMonoFontFamily,
              fontSize = addrFontSp,
              lineHeight = lineHeightSp,
              modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
            )
          }

          Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            if (editorSettings.showLineNumbers) {
              Text(
                text = "%04X:".format(insn.offsetWords),
                color = palette.label,
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = addrFontSp,
                lineHeight = lineHeightSp,
              )
              Spacer(Modifier.width(8.dp))
            }
            Text(
              text = insn.formatHexWords().padEnd(14, ' '),
              color = palette.lineNumber,
              fontFamily = JetBrainsMonoFontFamily,
              fontSize = addrFontSp,
              lineHeight = lineHeightSp,
            )
            Spacer(Modifier.width(8.dp))
            Text(
              text = insn.mnemonic.padEnd(22, ' '),
              color = palette.opcode,
              fontFamily = JetBrainsMonoFontFamily,
              fontWeight = FontWeight.Medium,
              fontSize = codeFontSp,
              lineHeight = lineHeightSp,
            )
            Text(
              text = highlightDalvikOperands(insn.operandsText, palette),
              fontFamily = JetBrainsMonoFontFamily,
              fontSize = codeFontSp,
              lineHeight = lineHeightSp,
            )
          }
          val payload = insn.payload
          if (insn.category == OpCategory.PAYLOAD && payload != null) {
            val detailLine =
              when (payload) {
                is DalvikPayload.PackedSwitch ->
                  payload.targetOffsets.mapIndexed { idx, t -> "${payload.firstKey + idx}->L_%04x".format(t) }
                    .joinToString(", ")
                is DalvikPayload.SparseSwitch ->
                  payload.cases.joinToString(", ") { (k, t) -> "$k->L_%04x".format(t) }
                is DalvikPayload.FillArrayData ->
                  "data=[${payload.previewElements.joinToString(", ")}] hex=${payload.hexPreview}"
              }
            if (detailLine.isNotBlank()) {
              Text(
                text = "      ; $detailLine",
                color = palette.comment,
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = addrFontSp,
                lineHeight = lineHeightSp,
                modifier = Modifier.padding(bottom = 2.dp),
              )
            }
          }
        }
      }
    }
  }
}

private val cKeywords =
  setOf(
    "static", "const", "return", "if", "else", "while", "switch", "case", "default",
    "break", "goto", "do", "for", "void", "sizeof", "inline", "extern",
  )

private val cTypes =
  setOf(
    "JNIEnv", "JavaVM", "JNIEXPORT", "JNICALL", "JNINativeMethod", "jvalue", "jint", "jlong",
    "jfloat", "jdouble", "jboolean", "jbyte", "jshort", "jchar", "jobject", "jclass",
    "jstring", "jarray", "jobjectArray", "jintArray", "jlongArray", "jbooleanArray",
    "jbyteArray", "jcharArray", "jshortArray", "jfieldID", "jmethodID", "jthrowable",
    "uint8_t", "uint32_t", "uint64_t", "size_t", "char", "NULL", "JNI_OK", "JNI_ERR", "JNI_VERSION_1_6",
  )

private fun highlightCLine(line: String, palette: SyntaxPalette): AnnotatedString {
  return buildAnnotatedString {
    val trimmed = line.trimStart()
    if (trimmed.startsWith("//") || trimmed.startsWith("#") || trimmed.startsWith("/*")) {
      val color = if (trimmed.startsWith("#")) palette.label else palette.comment
      append(line)
      addStyle(SpanStyle(color = color), 0, line.length)
      return@buildAnnotatedString
    }

    append(line)
    addStyle(SpanStyle(color = palette.defaultText), 0, line.length)

    val strRegex = Regex("\"([^\"\\\\]|\\\\.)*\"")
    strRegex.findAll(line).forEach { m ->
      addStyle(SpanStyle(color = palette.stringLiteral), m.range.first, m.range.last + 1)
    }

    val commentIdx = line.indexOf("/*")
    val slashIdx = line.indexOf("//")
    val cStart = listOf(commentIdx, slashIdx).filter { it >= 0 }.minOrNull()
    if (cStart != null) {
      addStyle(SpanStyle(color = palette.comment), cStart, line.length)
    }

    val limit = cStart ?: line.length
    val wordRegex = Regex("\\b[A-Za-z_][A-Za-z0-9_]*\\b")
    wordRegex.findAll(line.substring(0, limit)).forEach { m ->
      when (m.value) {
        in cKeywords -> addStyle(SpanStyle(color = palette.keyword), m.range.first, m.range.last + 1)
        in cTypes -> addStyle(SpanStyle(color = palette.typeName), m.range.first, m.range.last + 1)
      }
    }

    val numRegex = Regex("\\b(0x[0-9A-Fa-f]+u?|\\d+LL?)\\b")
    numRegex.findAll(line.substring(0, limit)).forEach { m ->
      addStyle(SpanStyle(color = palette.numberLiteral), m.range.first, m.range.last + 1)
    }
  }
}

private fun highlightDalvikOperands(operands: String, palette: SyntaxPalette): AnnotatedString {
  return buildAnnotatedString {
    append(operands)
    addStyle(SpanStyle(color = palette.defaultText), 0, operands.length)

    Regex("\\bv\\d+\\b").findAll(operands).forEach { m ->
      addStyle(SpanStyle(color = palette.register), m.range.first, m.range.last + 1)
    }
    Regex("\\bL_[0-9a-fA-F]+\\b").findAll(operands).forEach { m ->
      addStyle(SpanStyle(color = palette.label), m.range.first, m.range.last + 1)
    }
    Regex("#-?(0x[0-9a-fA-F]+|\\d+)").findAll(operands).forEach { m ->
      addStyle(SpanStyle(color = palette.numberLiteral), m.range.first, m.range.last + 1)
    }
    Regex("\"([^\"\\\\]|\\\\.)*\"").findAll(operands).forEach { m ->
      addStyle(SpanStyle(color = palette.stringLiteral), m.range.first, m.range.last + 1)
    }
  }
}
