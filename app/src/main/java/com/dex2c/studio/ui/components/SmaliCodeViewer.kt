package com.dex2c.studio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dex2c.studio.data.SmaliSyntaxTheme
import com.dex2c.studio.dex.SmaliMethodSpan
import com.dex2c.studio.ui.theme.JetBrainsMonoFontFamily
import com.dex2c.studio.ui.theme.LocalEditorSettings
import com.dex2c.studio.ui.theme.LocalSyntaxPalette
import com.dex2c.studio.ui.theme.SyntaxPalette
import com.dex2c.studio.ui.theme.resolveSmaliSyntaxPalette

enum class SmaliSearchCategory(val label: String, val tagSuffix: String) {
  ALL("All", "all"),
  REGISTERS("Registers", "registers"),
  OPCODES("Opcodes", "opcodes"),
  METHODS("Method Calls", "methods"),
  FIELDS("Fields", "fields"),
  STRINGS("Strings", "strings"),
}

data class SmaliSearchOptions(
  val query: String = "",
  val category: SmaliSearchCategory = SmaliSearchCategory.ALL,
  val caseSensitive: Boolean = false,
  val wholeWord: Boolean = false,
  val useRegex: Boolean = false,
) {
  val trimmedQuery: String
    get() = query.trim()

  val isActive: Boolean
    get() = trimmedQuery.isNotEmpty() || category != SmaliSearchCategory.ALL
}

data class SmaliSearchMatch(
  val lineIndex: Int,
  val lineNumber: Int,
  val ranges: List<IntRange>,
  val enclosingMethodName: String? = null,
  val linePreview: String = "",
) {
  val occurrenceCount: Int
    get() = ranges.size
}

data class SmaliSearchResult(
  val matches: List<SmaliSearchMatch> = emptyList(),
  val matchesByLine: Map<Int, List<IntRange>> = emptyMap(),
  val totalOccurrences: Int = 0,
  val regexError: String? = null,
)

enum class SmaliTokenKind(val badgeLabel: String) {
  REGISTER("REG"),
  OPCODE("OP"),
  METHOD("CALL"),
  FIELD("FLD"),
  STRING("STR"),
}

data class SmaliInteractiveToken(
  val text: String,
  val kind: SmaliTokenKind,
)

@Composable
fun SmaliSyntaxCodeViewer(
  lines: List<String>,
  modifier: Modifier = Modifier,
  searchQuery: String = "",
  activeMatchLineIndex: Int? = null,
  activeMethodRange: IntRange? = null,
  listState: LazyListState = rememberLazyListState(),
  searchOptions: SmaliSearchOptions = SmaliSearchOptions(query = searchQuery),
  lineMatchRanges: Map<Int, List<IntRange>>? = null,
  inspectedLineIndex: Int? = null,
  onLineClick: ((Int) -> Unit)? = null,
  syntaxThemeOverride: SmaliSyntaxTheme? = null,
) {
  val defaultPalette = LocalSyntaxPalette.current
  val editorSettings = LocalEditorSettings.current
  val effectiveSyntaxTheme = syntaxThemeOverride ?: editorSettings.smaliSyntaxTheme
  val palette =
    remember(effectiveSyntaxTheme, defaultPalette) {
      resolveSmaliSyntaxPalette(effectiveSyntaxTheme, defaultPalette)
    }
  val codeFontSp = editorSettings.fontSizeSp.sp
  val lineNumberSp = (editorSettings.fontSizeSp - 1).coerceAtLeast(9).sp
  val lineHeightSp = (editorSettings.fontSizeSp + 6).sp
  val hScroll = rememberScrollState()
  val matchBg = MaterialTheme.colorScheme.primaryContainer
  val matchFg = MaterialTheme.colorScheme.onPrimaryContainer
  val activeMatchBg = MaterialTheme.colorScheme.tertiary
  val activeMatchFg = MaterialTheme.colorScheme.onTertiary
  val activeLineTint = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f)
  val inspectedLineTint = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.26f)
  val methodHeaderTint = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.32f)
  val methodBodyTint = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.25f)
  val trimmedQuery = searchOptions.trimmedQuery.ifEmpty { searchQuery.trim() }
  val lineDigits = remember(lines.size) { lines.size.coerceAtLeast(1).toString().length.coerceAtLeast(3) }

  Box(
    modifier =
      modifier
        .clip(RoundedCornerShape(20.dp))
        .background(palette.codeBackground)
        .border(
          1.dp,
          MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
          RoundedCornerShape(20.dp),
        )
  ) {
    SelectionContainer {
      LazyColumn(
        state = listState,
        modifier =
          Modifier.fillMaxSize()
            .horizontalScroll(hScroll)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("smali_code_list"),
      ) {
        itemsIndexed(
          items = lines,
          key = { index, _ -> index },
          contentType = { _, _ -> 0 },
        ) { index, line ->
          val isActiveLine = activeMatchLineIndex == index
          val isInspectedLine = inspectedLineIndex == index
          val inActiveMethod = activeMethodRange != null && index in activeMethodRange
          val isMethodStart = activeMethodRange != null && index == activeMethodRange.first
          val explicitRanges = lineMatchRanges?.get(index)
          val rowBackground =
            when {
              isActiveLine -> activeLineTint
              isInspectedLine -> inspectedLineTint
              isMethodStart -> methodHeaderTint
              inActiveMethod -> methodBodyTint
              else -> Color.Transparent
            }
          val effectiveBg = if (isActiveLine) activeMatchBg else matchBg
          val effectiveFg = if (isActiveLine) activeMatchFg else matchFg
          val baseHighlighted =
            remember(line, palette) {
              highlightSmaliSyntaxBase(line = line, palette = palette)
            }
          val hasFallbackQueryMatch =
            lineMatchRanges == null &&
              trimmedQuery.isNotEmpty() &&
              line.contains(trimmedQuery, ignoreCase = true)
          val highlighted =
            if (!explicitRanges.isNullOrEmpty() || hasFallbackQueryMatch) {
              remember(baseHighlighted, line, trimmedQuery, explicitRanges, effectiveBg, effectiveFg) {
                overlaySmaliSearchHighlights(
                  base = baseHighlighted,
                  line = line,
                  searchQuery = trimmedQuery,
                  matchBackground = effectiveBg,
                  matchForeground = effectiveFg,
                  matchRanges = explicitRanges,
                )
              }
            } else {
              baseHighlighted
            }
          val rowModifier =
            Modifier.fillMaxWidth()
              .background(rowBackground)
              .let { base ->
                if (onLineClick != null) {
                  base.clickable { onLineClick(index) }
                } else {
                  base
                }
              }
              .padding(vertical = 1.dp, horizontal = 2.dp)
              .testTag("smali_code_line_$index")

          Row(modifier = rowModifier) {
            if (editorSettings.showLineNumbers) {
              Text(
                text = (index + 1).toString().padStart(lineDigits, ' '),
                color =
                  when {
                    isActiveLine -> MaterialTheme.colorScheme.tertiary
                    isInspectedLine || isMethodStart -> MaterialTheme.colorScheme.primary
                    else -> palette.lineNumber
                  },
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = lineNumberSp,
                lineHeight = lineHeightSp,
                fontWeight = if (isActiveLine || isInspectedLine) FontWeight.Bold else FontWeight.Normal,
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

private val smaliDirectiveRegex = Regex("^\\s*(\\.[a-z-]+)")
private val smaliModifierKeywords =
  setOf(
    "public",
    "private",
    "protected",
    "static",
    "final",
    "synchronized",
    "bridge",
    "varargs",
    "native",
    "abstract",
    "strictfp",
    "synthetic",
    "constructor",
    "declared-synchronized",
    "interface",
    "enum",
    "annotation",
    "transient",
    "volatile",
    "system",
    "runtime",
    "build",
    "whitelist",
    "greylist",
    "blacklist",
    "greylist-max-o",
    "greylist-max-p",
    "greylist-max-q",
    "greylist-max-r",
    "core-platform-api",
    "test-api",
  )
private val smaliTypeDescriptorRegex = Regex("\\[*(?:L[A-Za-z0-9_$/-]+;|[VZBSCIJFD](?![A-Za-z0-9_]))")
private val smaliLabelRegex = Regex(":[A-Za-z0-9_$-]+")
private val smaliRegisterRegex = Regex("\\b[vp]\\d+\\b")
private val smaliStringRegex = Regex("\"([^\"\\\\]|\\\\.)*\"")
private val smaliNumberRegex = Regex("(?<![A-Za-z0-9_])-?(?:0x[0-9A-Fa-f]+[tTsSlL]?|\\d+[lLsStT]?)\\b")
private val smaliOpcodeLineRegex = Regex("^\\s+([a-z][a-z0-9/-]*)(?:\\s|$)")
private val smaliWordRegex = Regex("\\b[a-z-]+\\b")
private val smaliMethodCallRefRegex =
  Regex("(?:L[A-Za-z0-9_$/-]+;)?->([A-Za-z0-9_$<>-]+)\\(")
private val smaliFieldRefRegex =
  Regex("(?:L[A-Za-z0-9_$/-]+;)?->([A-Za-z0-9_$]+):\\[*(?:L[A-Za-z0-9_$/-]+;|[VZBSCIJFD])")

fun findSmaliSearchMatches(
  lines: List<String>,
  methodSpans: List<SmaliMethodSpan> = emptyList(),
  options: SmaliSearchOptions,
): SmaliSearchResult {
  if (!options.isActive || lines.isEmpty()) {
    return SmaliSearchResult()
  }

  val compiledQueryRegex: Regex? =
    if (options.trimmedQuery.isEmpty()) {
      null
    } else {
      val basePattern =
        if (options.useRegex) {
          options.trimmedQuery
        } else {
          Regex.escape(options.trimmedQuery)
        }
      val wrappedPattern =
        if (options.wholeWord) {
          "(?<![A-Za-z0-9_$])$basePattern(?![A-Za-z0-9_$])"
        } else {
          basePattern
        }
      val regexOpts =
        if (options.caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
      try {
        Regex(wrappedPattern, regexOpts)
      } catch (e: Exception) {
        return SmaliSearchResult(
          regexError = e.message?.lineSequence()?.firstOrNull() ?: "Invalid regular expression"
        )
      }
    }

  val matches = ArrayList<SmaliSearchMatch>()
  val matchesByLine = LinkedHashMap<Int, List<IntRange>>()
  var totalOccurrences = 0

  for (lineIdx in lines.indices) {
    val line = lines[lineIdx]
    val ranges = findLineMatchRanges(line, options, compiledQueryRegex)
    if (ranges.isNotEmpty()) {
      val enclosingMethod = findEnclosingMethodName(methodSpans, lineIdx)
      val match =
        SmaliSearchMatch(
          lineIndex = lineIdx,
          lineNumber = lineIdx + 1,
          ranges = ranges,
          enclosingMethodName = enclosingMethod,
          linePreview = line.trim(),
        )
      matches.add(match)
      matchesByLine[lineIdx] = ranges
      totalOccurrences += ranges.size
    }
  }

  return SmaliSearchResult(
    matches = matches,
    matchesByLine = matchesByLine,
    totalOccurrences = totalOccurrences,
    regexError = null,
  )
}

private fun findEnclosingMethodName(
  methodSpans: List<SmaliMethodSpan>,
  lineIdx: Int,
): String? {
  if (methodSpans.isEmpty()) return null
  var low = 0
  var high = methodSpans.lastIndex
  while (low <= high) {
    val mid = (low + high) ushr 1
    val span = methodSpans[mid]
    when {
      lineIdx < span.startLineIndex -> high = mid - 1
      lineIdx > span.endLineIndex -> low = mid + 1
      else -> return span.name
    }
  }
  return null
}

private fun findLineMatchRanges(
  line: String,
  options: SmaliSearchOptions,
  compiledQueryRegex: Regex?,
): List<IntRange> {
  val categoryRanges = findCategoryTokenRanges(line, options.category)
  if (compiledQueryRegex == null) {
    return categoryRanges
  }
  if (options.category != SmaliSearchCategory.ALL && categoryRanges.isEmpty()) {
    return emptyList()
  }

  val queryRanges =
    compiledQueryRegex
      .findAll(line)
      .mapNotNull { m ->
        if (m.range.first <= m.range.last) m.range else null
      }
      .toList()

  if (queryRanges.isEmpty()) return emptyList()
  if (options.category == SmaliSearchCategory.ALL) return queryRanges

  return when (options.category) {
    SmaliSearchCategory.REGISTERS,
    SmaliSearchCategory.OPCODES,
    SmaliSearchCategory.STRINGS -> {
      queryRanges.filter { qRange ->
        categoryRanges.any { cRange ->
          qRange.first <= cRange.last && qRange.last >= cRange.first
        }
      }
    }
    SmaliSearchCategory.METHODS,
    SmaliSearchCategory.FIELDS -> {
      // Line already verified to belong to method or field category; return query matches on it
      queryRanges
    }
    SmaliSearchCategory.ALL -> queryRanges
  }
}

private fun findCategoryTokenRanges(
  line: String,
  category: SmaliSearchCategory,
): List<IntRange> {
  if (category == SmaliSearchCategory.ALL) return emptyList()
  val trimmed = line.trimStart()
  if (trimmed.startsWith("#")) return emptyList()
  val commentIdx = findSmaliCommentStart(line)
  val codePart = line.substring(0, commentIdx ?: line.length)

  return when (category) {
    SmaliSearchCategory.REGISTERS ->
      smaliRegisterRegex.findAll(codePart).map { it.range }.toList()
    SmaliSearchCategory.OPCODES -> {
      if (smaliDirectiveRegex.containsMatchIn(codePart)) {
        emptyList()
      } else {
        val opMatch = smaliOpcodeLineRegex.find(codePart)
        val group = opMatch?.groups?.get(1)
        if (group != null && group.value != "end") listOf(group.range) else emptyList()
      }
    }
    SmaliSearchCategory.METHODS -> {
      val callRanges =
        smaliMethodCallRefRegex.findAll(codePart).map { m ->
          // Exclude trailing '(' from the highlighted call range
          IntRange(m.range.first, (m.range.last - 1).coerceAtLeast(m.range.first))
        }.toList()
      if (callRanges.isNotEmpty()) {
        callRanges
      } else if (trimmed.startsWith(".method ")) {
        val startOffset = line.length - trimmed.length
        listOf(IntRange(startOffset, codePart.lastIndex.coerceAtLeast(startOffset)))
      } else {
        emptyList()
      }
    }
    SmaliSearchCategory.FIELDS -> {
      val fieldRanges = smaliFieldRefRegex.findAll(codePart).map { it.range }.toList()
      if (fieldRanges.isNotEmpty()) {
        fieldRanges
      } else if (trimmed.startsWith(".field ")) {
        val startOffset = line.length - trimmed.length
        listOf(IntRange(startOffset, codePart.lastIndex.coerceAtLeast(startOffset)))
      } else {
        emptyList()
      }
    }
    SmaliSearchCategory.STRINGS ->
      smaliStringRegex.findAll(codePart).map { it.range }.toList()
    SmaliSearchCategory.ALL -> emptyList()
  }
}

fun extractLineInteractiveTokens(line: String): List<SmaliInteractiveToken> {
  val trimmed = line.trimStart()
  if (trimmed.isEmpty() || trimmed.startsWith("#")) return emptyList()
  val commentIdx = findSmaliCommentStart(line)
  val codePart = line.substring(0, commentIdx ?: line.length)
  val tokens = LinkedHashSet<SmaliInteractiveToken>()

  // 1. Opcode mnemonic if this is an instruction line
  if (!smaliDirectiveRegex.containsMatchIn(codePart)) {
    val opMatch = smaliOpcodeLineRegex.find(codePart)
    val opGroup = opMatch?.groups?.get(1)
    if (opGroup != null && opGroup.value != "end") {
      tokens.add(SmaliInteractiveToken(opGroup.value, SmaliTokenKind.OPCODE))
    }
  }

  // 2. Registers (v0, p0, etc.)
  smaliRegisterRegex.findAll(codePart).forEach { m ->
    tokens.add(SmaliInteractiveToken(m.value, SmaliTokenKind.REGISTER))
  }

  // 3. Method calls (->methodName)
  smaliMethodCallRefRegex.findAll(codePart).forEach { m ->
    val methodName = m.groups[1]?.value
    if (!methodName.isNullOrBlank()) {
      tokens.add(SmaliInteractiveToken("->$methodName(", SmaliTokenKind.METHOD))
    }
  }

  // 4. Field references (->fieldName:)
  smaliFieldRefRegex.findAll(codePart).forEach { m ->
    val fieldName = m.groups[1]?.value
    if (!fieldName.isNullOrBlank()) {
      tokens.add(SmaliInteractiveToken("->$fieldName:", SmaliTokenKind.FIELD))
    }
  }

  // 5. Short string literals
  smaliStringRegex.findAll(codePart).forEach { m ->
    if (m.value.length in 3..32) {
      tokens.add(SmaliInteractiveToken(m.value, SmaliTokenKind.STRING))
    }
  }

  return tokens.toList()
}

fun extractQuickMethodTokens(
  lines: List<String>,
  activeMethodRange: IntRange? = null,
  maxRegisters: Int = 6,
  maxOpcodes: Int = 4,
): List<SmaliInteractiveToken> {
  if (lines.isEmpty()) return emptyList()
  val range =
    activeMethodRange
      ?: IntRange(0, (lines.lastIndex).coerceAtMost(120))
  val registerCounts = LinkedHashMap<String, Int>()
  val opcodeCounts = LinkedHashMap<String, Int>()

  for (idx in range.first.coerceAtLeast(0)..range.last.coerceAtMost(lines.lastIndex)) {
    val line = lines[idx]
    val trimmed = line.trimStart()
    if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
    val commentIdx = findSmaliCommentStart(line)
    val codePart = line.substring(0, commentIdx ?: line.length)

    smaliRegisterRegex.findAll(codePart).forEach { m ->
      registerCounts[m.value] = (registerCounts[m.value] ?: 0) + 1
    }
    if (!smaliDirectiveRegex.containsMatchIn(codePart)) {
      val op = smaliOpcodeLineRegex.find(codePart)?.groups?.get(1)?.value
      if (op != null && op != "end") {
        opcodeCounts[op] = (opcodeCounts[op] ?: 0) + 1
      }
    }
  }

  val result = ArrayList<SmaliInteractiveToken>()
  registerCounts.entries
    .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
    .take(maxRegisters)
    .forEach { (reg, _) ->
      result.add(SmaliInteractiveToken(reg, SmaliTokenKind.REGISTER))
    }

  opcodeCounts.entries
    .sortedByDescending { it.value }
    .take(maxOpcodes)
    .forEach { (op, _) ->
      result.add(SmaliInteractiveToken(op, SmaliTokenKind.OPCODE))
    }

  return result
}

fun highlightSmaliLine(
  line: String,
  palette: SyntaxPalette,
  searchQuery: String = "",
  matchBackground: Color = Color.Unspecified,
  matchForeground: Color = Color.Unspecified,
  matchRanges: List<IntRange>? = null,
): AnnotatedString {
  val base = highlightSmaliSyntaxBase(line, palette)
  if (matchBackground == Color.Unspecified || (matchRanges.isNullOrEmpty() && searchQuery.isEmpty())) {
    return base
  }
  return overlaySmaliSearchHighlights(
    base = base,
    line = line,
    searchQuery = searchQuery,
    matchBackground = matchBackground,
    matchForeground = matchForeground,
    matchRanges = matchRanges,
  )
}

fun highlightSmaliSyntaxBase(
  line: String,
  palette: SyntaxPalette,
): AnnotatedString {
  return buildAnnotatedString {
    append(line)
    if (line.isEmpty()) return@buildAnnotatedString
    addStyle(SpanStyle(color = palette.defaultText), 0, line.length)

    val trimmed = line.trimStart()
    if (trimmed.isEmpty()) return@buildAnnotatedString
    if (trimmed.startsWith("#")) {
      addStyle(SpanStyle(color = palette.comment), 0, line.length)
      return@buildAnnotatedString
    }

    val commentIdx = findSmaliCommentStart(line)
    val codeLimit = commentIdx ?: line.length
    val codePart = line.substring(0, codeLimit)

    // Numbers first (overridden later by labels/registers/strings if overlapping)
    smaliNumberRegex.findAll(codePart).forEach { m ->
      addStyle(SpanStyle(color = palette.numberLiteral), m.range.first, m.range.last + 1)
    }

    // Type descriptors (Lcom/example/Foo;, [I, etc.)
    smaliTypeDescriptorRegex.findAll(codePart).forEach { m ->
      addStyle(SpanStyle(color = palette.typeName), m.range.first, m.range.last + 1)
    }

    // Directives (.class, .super, .method, .end, .locals, .registers, etc.)
    val directiveMatch = smaliDirectiveRegex.find(codePart)
    if (directiveMatch != null) {
      val g = directiveMatch.groups[1]!!
      addStyle(
        SpanStyle(color = palette.keyword, fontWeight = FontWeight.SemiBold),
        g.range.first,
        g.range.last + 1,
      )
      smaliWordRegex.findAll(codePart).forEach { w ->
        if (w.value in smaliModifierKeywords) {
          addStyle(SpanStyle(color = palette.keyword), w.range.first, w.range.last + 1)
        }
      }
    } else {
      // Indented instruction opcode mnemonic classified by instruction family
      val opMatch = smaliOpcodeLineRegex.find(codePart)
      if (opMatch != null) {
        val g = opMatch.groups[1]!!
        val mnemonic = g.value
        if (mnemonic != "end") {
          val (opColor, opWeight) = resolveOpcodeStyle(mnemonic, palette)
          addStyle(
            SpanStyle(color = opColor, fontWeight = opWeight),
            g.range.first,
            g.range.last + 1,
          )
        }
      }
    }

    // Member call & field targets (->methodName or ->fieldName)
    if (palette.memberTarget != palette.defaultText && codePart.contains("->")) {
      smaliMemberTargetRegex.findAll(codePart).forEach { m ->
        addStyle(
          SpanStyle(
            color = palette.memberTarget,
            fontWeight = if (palette.isHighContrastBytecode) FontWeight.SemiBold else FontWeight.Normal,
          ),
          m.range.first,
          m.range.last + 1,
        )
      }
    }

    // Registers (local v0..vN vs parameter p0..pN)
    smaliRegisterRegex.findAll(codePart).forEach { m ->
      val isParam = m.value.startsWith("p")
      val regColor = if (isParam) palette.parameterRegister else palette.register
      val regWeight =
        if (palette.isHighContrastBytecode && isParam) FontWeight.SemiBold else FontWeight.Normal
      addStyle(
        SpanStyle(color = regColor, fontWeight = regWeight),
        m.range.first,
        m.range.last + 1,
      )
    }

    // Labels (:cond_0, :goto_1, :try_start_0, :pswitch_0)
    if (codePart.contains(':')) {
      smaliLabelRegex.findAll(codePart).forEach { m ->
        addStyle(
          SpanStyle(
            color = palette.label,
            fontWeight = if (palette.isHighContrastBytecode) FontWeight.SemiBold else FontWeight.Normal,
          ),
          m.range.first,
          m.range.last + 1,
        )
      }
    }

    // String literals ("...")
    if (codePart.contains('"')) {
      smaliStringRegex.findAll(codePart).forEach { m ->
        addStyle(SpanStyle(color = palette.stringLiteral), m.range.first, m.range.last + 1)
      }
    }

    if (commentIdx != null) {
      addStyle(SpanStyle(color = palette.comment), commentIdx, line.length)
    }
  }
}

private fun overlaySmaliSearchHighlights(
  base: AnnotatedString,
  line: String,
  searchQuery: String,
  matchBackground: Color,
  matchForeground: Color,
  matchRanges: List<IntRange>? = null,
): AnnotatedString =
  buildAnnotatedString {
    append(base)
    applySearchHighlights(line, searchQuery, matchBackground, matchForeground, matchRanges)
  }

private val smaliMemberTargetRegex = Regex("->([A-Za-z0-9_$<>-]+)")

private fun resolveOpcodeStyle(
  mnemonic: String,
  palette: SyntaxPalette,
): Pair<Color, FontWeight> {
  val isHighContrast = palette.isHighContrastBytecode
  return when {
    mnemonic.startsWith("if-") ||
      mnemonic.startsWith("goto") ||
      mnemonic.startsWith("return") ||
      mnemonic == "throw" ||
      mnemonic.endsWith("-switch") ->
      palette.opcodeBranch to (if (isHighContrast) FontWeight.Bold else FontWeight.Medium)

    mnemonic.startsWith("invoke-") || mnemonic.startsWith("execute-inline") ->
      palette.opcodeInvoke to (if (isHighContrast) FontWeight.Bold else FontWeight.Medium)

    mnemonic.startsWith("iget") ||
      mnemonic.startsWith("iput") ||
      mnemonic.startsWith("sget") ||
      mnemonic.startsWith("sput") ||
      mnemonic.startsWith("aget") ||
      mnemonic.startsWith("aput") ||
      mnemonic.startsWith("add-") ||
      mnemonic.startsWith("sub-") ||
      mnemonic.startsWith("mul-") ||
      mnemonic.startsWith("div-") ||
      mnemonic.startsWith("rem-") ||
      mnemonic.startsWith("and-") ||
      mnemonic.startsWith("or-") ||
      mnemonic.startsWith("xor-") ||
      mnemonic.startsWith("shl-") ||
      mnemonic.startsWith("shr-") ||
      mnemonic.startsWith("ushr-") ->
      palette.opcodeAccess to (if (isHighContrast) FontWeight.SemiBold else FontWeight.Medium)

    else ->
      palette.opcode to (if (isHighContrast) FontWeight.SemiBold else FontWeight.Medium)
  }
}

private fun AnnotatedString.Builder.applySearchHighlights(
  line: String,
  searchQuery: String,
  matchBackground: Color,
  matchForeground: Color,
  matchRanges: List<IntRange>? = null,
) {
  if (matchBackground == Color.Unspecified) return
  if (matchRanges != null) {
    for (range in matchRanges) {
      val start = range.first.coerceIn(0, line.length)
      val end = (range.last + 1).coerceIn(start, line.length)
      if (start < end) {
        addStyle(
          SpanStyle(
            background = matchBackground,
            color = if (matchForeground != Color.Unspecified) matchForeground else Color.Unspecified,
            fontWeight = FontWeight.Bold,
          ),
          start,
          end,
        )
      }
    }
    return
  }

  if (searchQuery.isEmpty()) return
  var startIdx = 0
  while (startIdx < line.length) {
    val found = line.indexOf(searchQuery, startIndex = startIdx, ignoreCase = true)
    if (found < 0) break
    val end = found + searchQuery.length
    addStyle(
      SpanStyle(
        background = matchBackground,
        color = if (matchForeground != Color.Unspecified) matchForeground else Color.Unspecified,
        fontWeight = FontWeight.Bold,
      ),
      found,
      end,
    )
    startIdx = end
  }
}

private fun findSmaliCommentStart(line: String): Int? {
  var inQuotes = false
  var escaped = false
  for (i in line.indices) {
    val c = line[i]
    if (escaped) {
      escaped = false
      continue
    }
    if (c == '\\' && inQuotes) {
      escaped = true
      continue
    }
    if (c == '"') {
      inQuotes = !inQuotes
      continue
    }
    if (c == '#' && !inQuotes) {
      return i
    }
  }
  return null
}
