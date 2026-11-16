package com.dex2c.studio.dex

import com.dex2c.studio.ui.components.SmaliSearchOptions
import com.dex2c.studio.ui.components.SmaliSearchResult
import com.dex2c.studio.ui.components.findSmaliSearchMatches

data class SmaliCompletionSuggestion(
  val token: String,
  val category: String, // "Opcode", "Directive", "Register", "Label"
)

data class SmaliReplaceResult(
  val updatedLines: List<String>,
  val replacedCount: Int,
  val modifiedLineCount: Int,
)

object SmaliEditorEngine {

  private val DALVIK_COMMON_OPCODES = listOf(
    "nop", "move", "move-wide", "move-object", "move-result", "move-result-wide",
    "move-result-object", "move-exception", "return-void", "return", "return-wide",
    "return-object", "const/4", "const/16", "const", "const-wide", "const-string",
    "const-class", "monitor-enter", "monitor-exit", "check-cast", "instance-of",
    "array-length", "new-instance", "new-array", "filled-new-array", "fill-array-data",
    "throw", "goto", "goto/16", "goto/32", "packed-switch", "sparse-switch",
    "cmpl-float", "cmpg-float", "cmpl-double", "cmpg-double", "cmp-long",
    "if-eq", "if-ne", "if-lt", "if-ge", "if-gt", "if-le", "if-eqz", "if-nez",
    "if-ltz", "if-gez", "if-gtz", "if-lez", "aget", "aget-wide", "aget-object",
    "aget-boolean", "aget-byte", "aget-char", "aget-short", "aput", "aput-wide",
    "aput-object", "aput-boolean", "aput-byte", "aput-char", "aput-short",
    "iget", "iget-wide", "iget-object", "iget-boolean", "iget-byte", "iget-char",
    "iget-short", "iput", "iput-wide", "iput-object", "iput-boolean", "iput-byte",
    "iput-char", "iput-short", "sget", "sget-wide", "sget-object", "sget-boolean",
    "sget-byte", "sget-char", "sget-short", "sput", "sput-wide", "sput-object",
    "sput-boolean", "sput-byte", "sput-char", "sput-short", "invoke-virtual",
    "invoke-super", "invoke-direct", "invoke-static", "invoke-interface",
    "invoke-virtual/range", "invoke-super/range", "invoke-direct/range",
    "invoke-static/range", "invoke-interface/range", "neg-int", "not-int",
    "neg-long", "not-long", "neg-float", "neg-double", "int-to-long", "int-to-float",
    "int-to-double", "long-to-int", "long-to-float", "long-to-double", "float-to-int",
    "float-to-long", "float-to-double", "double-to-int", "double-to-long",
    "double-to-float", "int-to-byte", "int-to-char", "int-to-short", "add-int",
    "sub-int", "mul-int", "div-int", "rem-int", "and-int", "or-int", "xor-int",
    "shl-int", "shr-int", "ushr-int", "add-long", "sub-long", "mul-long",
    "div-long", "rem-long", "and-long", "or-long", "xor-long", "shl-long",
    "shr-long", "ushr-long", "add-float", "sub-float", "mul-float", "div-float",
    "rem-float", "add-double", "sub-double", "mul-double", "div-double", "rem-double"
  )

  private val SMALI_DIRECTIVES = listOf(
    ".class", ".super", ".source", ".implements", ".field", ".end field",
    ".method", ".end method", ".registers", ".locals", ".parameter", ".prologue",
    ".line", ".local", ".end local", ".restart local", ".catch", ".catchall",
    ".annotation", ".end annotation", ".array-data", ".end array-data",
    ".packed-switch", ".end packed-switch", ".sparse-switch", ".end sparse-switch"
  )

  fun suggestCompletions(
    currentWordPrefix: String,
    currentLines: List<String>,
    maxSuggestions: Int = 10,
  ): List<SmaliCompletionSuggestion> {
    val prefix = currentWordPrefix.trim().lowercase()
    val results = mutableListOf<SmaliCompletionSuggestion>()

    val labelRegex = Regex("^\\s*(:[a-zA-Z0-9_$]+)")
    val localLabels = mutableSetOf<String>()
    for (line in currentLines) {
      val match = labelRegex.find(line)
      if (match != null) {
        localLabels.add(match.groupValues[1])
      }
    }

    if (prefix.startsWith(":")) {
      for (lbl in localLabels) {
        if (lbl.lowercase().contains(prefix)) {
          results.add(SmaliCompletionSuggestion(token = lbl, category = "Label"))
        }
      }
    }

    for (dir in SMALI_DIRECTIVES) {
      if (prefix.isEmpty() || dir.lowercase().startsWith(prefix)) {
        results.add(SmaliCompletionSuggestion(token = dir, category = "Directive"))
      }
    }

    for (op in DALVIK_COMMON_OPCODES) {
      if (prefix.isEmpty() || op.lowercase().startsWith(prefix)) {
        results.add(SmaliCompletionSuggestion(token = op, category = "Opcode"))
      }
    }

    return results.distinctBy { it.token }.take(maxSuggestions)
  }

  data class LabelJumpTarget(
    val label: String,
    val lineIndex: Int,
    val lineNumber: Int,
  )

  fun resolveLabelJumpForLine(lines: List<String>, lineIndex: Int): LabelJumpTarget? {
    if (lineIndex !in lines.indices) return null
    val line = lines[lineIndex].trim()
    val targetLabel = when {
      line.startsWith("goto ") || line.startsWith("goto/16 ") || line.startsWith("goto/32 ") ->
        line.substringAfter(' ').trim()
      line.contains(" :cond_") || line.contains(" :goto_") || line.contains(" :try_start_") || line.contains(" :try_end_") -> {
        val parts = line.split(Regex("\\s+"))
        parts.firstOrNull { it.startsWith(":") }
      }
      else -> null
    } ?: return null

    for (idx in lines.indices) {
      val candidate = lines[idx].trim()
      if (candidate.startsWith(targetLabel) || candidate.contains("$targetLabel:")) {
        return LabelJumpTarget(
          label = targetLabel,
          lineIndex = idx,
          lineNumber = idx + 1,
        )
      }
    }
    return null
  }

  fun replaceMatches(
    lines: List<String>,
    methodSpans: List<SmaliMethodSpan>,
    options: SmaliSearchOptions,
    replacement: String,
    replaceAll: Boolean,
    activeLineIndex: Int? = null,
  ): SmaliReplaceResult {
    if (options.query.isBlank() || lines.isEmpty()) {
      return SmaliReplaceResult(lines, 0, 0)
    }

    val updatedLines = lines.toMutableList()
    var replacedTotal = 0
    val modifiedLines = mutableSetOf<Int>()

    val searchMatchResult = findSmaliSearchMatches(lines, methodSpans, options)
    val matchesToProcess = if (replaceAll) {
      searchMatchResult.matches
    } else {
      val targetLine = activeLineIndex ?: searchMatchResult.matches.firstOrNull()?.lineIndex ?: return SmaliReplaceResult(lines, 0, 0)
      searchMatchResult.matches.filter { it.lineIndex == targetLine }
    }

    val sortedMatches = matchesToProcess.sortedByDescending { it.lineIndex }
    for (match in sortedMatches) {
      val idx = match.lineIndex
      if (idx !in updatedLines.indices) continue
      val originalLine = updatedLines[idx]
      val q = options.trimmedQuery

      val newLine = if (options.useRegex) {
        runCatching {
          val regex = if (options.caseSensitive) Regex(q) else Regex(q, RegexOption.IGNORE_CASE)
          regex.replace(originalLine, replacement)
        }.getOrDefault(originalLine)
      } else {
        if (options.caseSensitive) {
          originalLine.replace(q, replacement)
        } else {
          val lowerOrig = originalLine.lowercase()
          val lowerQ = q.lowercase()
          var index = lowerOrig.indexOf(lowerQ)
          var sb = StringBuilder(originalLine)
          var shift = 0
          while (index >= 0) {
            val start = index + shift
            val end = start + q.length
            sb.replace(start, end, replacement)
            shift += replacement.length - q.length
            index = lowerOrig.indexOf(lowerQ, index + q.length)
          }
          sb.toString()
        }
      }

      if (newLine != originalLine) {
        updatedLines[idx] = newLine
        replacedTotal++
        modifiedLines.add(idx)
      }
    }

    return SmaliReplaceResult(
      updatedLines = updatedLines,
      replacedCount = replacedTotal,
      modifiedLineCount = modifiedLines.size,
    )
  }
}
