package com.dex2c.studio.dex

/**
 * Utility functions for analyzing cursor position, method spans, and label navigation in Smali bytecode.
 */
object SmaliCursorUtils {

  /**
   * Resolves the [SmaliMethodSpan] containing the given [lineIndex].
   */
  fun resolveMethodSpanForLine(
    methodSpans: List<SmaliMethodSpan>,
    lineIndex: Int,
  ): SmaliMethodSpan? {
    if (lineIndex < 0 || methodSpans.isEmpty()) return null
    return methodSpans.firstOrNull { lineIndex in it.startLineIndex..it.endLineIndex }
  }

  /**
   * Collects all jump target labels (e.g. `:cond_0`, `:goto_1`, `:try_start_0`) within a given line range.
   */
  fun collectLabelsInRange(
    lines: List<String>,
    startLine: Int,
    endLine: Int,
  ): List<SmaliEditorEngine.LabelJumpTarget> {
    val result = mutableListOf<SmaliEditorEngine.LabelJumpTarget>()
    val safeStart = startLine.coerceAtLeast(0)
    val safeEnd = endLine.coerceAtMost(lines.lastIndex)
    if (safeStart > safeEnd) return emptyList()

    val labelDeclRegex = Regex("^\\s*(:[a-zA-Z0-9_$]+)")
    for (idx in safeStart..safeEnd) {
      val line = lines[idx]
      val match = labelDeclRegex.find(line)
      if (match != null) {
        val label = match.groupValues[1]
        result.add(
          SmaliEditorEngine.LabelJumpTarget(
            label = label,
            lineIndex = idx,
            lineNumber = idx + 1,
          )
        )
      }
    }
    return result
  }

  /**
   * Collects all labels defined in the entire file.
   */
  fun collectAllLabels(lines: List<String>): List<SmaliEditorEngine.LabelJumpTarget> {
    return collectLabelsInRange(lines, 0, lines.lastIndex)
  }
}
