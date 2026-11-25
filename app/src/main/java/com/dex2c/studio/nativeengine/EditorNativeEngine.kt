package com.dex2c.studio.nativeengine

import android.util.Log
import com.dex2c.studio.dex.SmaliEditorEngine

/**
 * Token span representing a classified region within a Smali line.
 * [lineOffset] is 0-indexed relative to the requested line range.
 */
data class NativeTokenSpan(
  val lineOffset: Int,
  val startCol: Int,
  val endCol: Int,
  val kind: Int,
) {
  companion object {
    const val KIND_NONE = 0
    const val KIND_DIRECTIVE = 1      // .class, .method, .registers, etc.
    const val KIND_OPCODE = 2         // invoke-*, return, goto, const, etc.
    const val KIND_REGISTER = 3       // v0, p1, etc.
    const val KIND_LABEL_DEF = 4      // :cond_0 at line start
    const val KIND_LABEL_REF = 5      // :cond_0 in branch instruction
    const val KIND_MEMBER_REF = 6     // ->methodName or ->fieldName
    const val KIND_TYPE_DESCRIPTOR = 7// Lcom/example/Class;
    const val KIND_STRING_LITERAL = 8 // "Hello World"
    const val KIND_COMMENT = 9        // # ...
    const val KIND_NUMBER = 10        // 0x1, 100
    const val KIND_FILTER_EXCLUSION = 11 // !exclusion rule
    const val KIND_FILTER_EXACT = 12     // =exact rule
    const val KIND_FILTER_PATTERN = 13   // pattern or wildcard match rule
  }
}

/**
 * Unified interface for an interactive code editor session.
 * Backed by high-performance Rust native engine (`librust_dex2c.so`) with
 * seamless fallback to Kotlin in-memory implementation for testing and non-NDK environments.
 */
interface EditorSessionInterface : AutoCloseable {
  val lineCount: Int
  fun getLines(startLine: Int, count: Int): List<String>
  fun getLineTokens(startLine: Int, count: Int): List<List<NativeTokenSpan>>
  fun applyEdit(startChar: Int, endChar: Int, text: String)
  fun undo(): Int?
  fun redo(): Int?
  fun canUndo(): Boolean
  fun canRedo(): Boolean
  fun resolveLabelJump(lineIndex: Int): Int?
  fun getFullText(): String
  fun setGrammar(grammar: Int)
}

/**
 * High-performance editor session backed by the Rust B-Tree rope engine.
 */
class NativeEditorSession(private var handle: Long) : EditorSessionInterface {

  override val lineCount: Int
    get() {
      if (handle == 0L) return 0
      return EditorNativeEngine.nativeGetLineCount(handle)
    }

  override fun getLines(startLine: Int, count: Int): List<String> {
    if (handle == 0L || count <= 0) return emptyList()
    val arr = EditorNativeEngine.nativeGetLines(handle, startLine, count) ?: return emptyList()
    return arr.toList()
  }

  override fun getLineTokens(startLine: Int, count: Int): List<List<NativeTokenSpan>> {
    if (handle == 0L || count <= 0) return emptyList()
    val flat = EditorNativeEngine.nativeGetLineTokens(handle, startLine, count) ?: return emptyList()
    val result = ArrayList<MutableList<NativeTokenSpan>>(count)
    for (i in 0 until count) {
      result.add(mutableListOf())
    }

    var i = 0
    while (i + 3 < flat.size) {
      val lineOffset = flat[i]
      val startCol = flat[i + 1]
      val endCol = flat[i + 2]
      val kind = flat[i + 3]
      if (lineOffset in 0 until count) {
        result[lineOffset].add(NativeTokenSpan(lineOffset, startCol, endCol, kind))
      }
      i += 4
    }

    return result
  }

  override fun applyEdit(startChar: Int, endChar: Int, text: String) {
    if (handle != 0L) {
      EditorNativeEngine.nativeApplyEdit(handle, startChar, endChar, text)
    }
  }

  override fun undo(): Int? {
    if (handle == 0L) return null
    val res = EditorNativeEngine.nativeUndo(handle)
    return if (res >= 0) res else null
  }

  override fun redo(): Int? {
    if (handle == 0L) return null
    val res = EditorNativeEngine.nativeRedo(handle)
    return if (res >= 0) res else null
  }

  override fun canUndo(): Boolean {
    return handle != 0L && EditorNativeEngine.nativeCanUndo(handle)
  }

  override fun canRedo(): Boolean {
    return handle != 0L && EditorNativeEngine.nativeCanRedo(handle)
  }

  override fun resolveLabelJump(lineIndex: Int): Int? {
    if (handle == 0L) return null
    val target = EditorNativeEngine.nativeResolveLabelJump(handle, lineIndex)
    return if (target >= 0) target else null
  }

  override fun getFullText(): String {
    if (handle == 0L) return ""
    return EditorNativeEngine.nativeGetFullText(handle) ?: ""
  }

  override fun setGrammar(grammar: Int) {
    if (handle != 0L) {
      EditorNativeEngine.nativeSetGrammar(handle, grammar)
    }
  }

  override fun close() {
    if (handle != 0L) {
      EditorNativeEngine.nativeDestroySession(handle)
      handle = 0L
    }
  }
}

/**
 * Pure Kotlin in-memory fallback session for Robolectric and testing environments.
 */
class KotlinFallbackEditorSession(
  initialText: String,
  private var grammar: Int = EditorNativeEngine.GRAMMAR_SMALI,
) : EditorSessionInterface {
  private val lines = initialText.lines().toMutableList()
  private val undoStack = mutableListOf<String>()
  private val redoStack = mutableListOf<String>()

  override val lineCount: Int
    get() = lines.size

  override fun getLines(startLine: Int, count: Int): List<String> {
    if (startLine >= lines.size || count <= 0) return emptyList()
    val end = (startLine + count).coerceAtMost(lines.size)
    return lines.subList(startLine, end)
  }

  override fun getLineTokens(startLine: Int, count: Int): List<List<NativeTokenSpan>> {
    val chunk = getLines(startLine, count)
    return chunk.mapIndexed { offset, line ->
      tokenizeLine(offset, line)
    }
  }

  override fun applyEdit(startChar: Int, endChar: Int, text: String) {
    val full = getFullText()
    undoStack.add(full)
    redoStack.clear()
    val sb = StringBuilder(full)
    val safeStart = startChar.coerceIn(0, sb.length)
    val safeEnd = endChar.coerceIn(safeStart, sb.length)
    sb.replace(safeStart, safeEnd, text)
    lines.clear()
    lines.addAll(sb.toString().lines())
  }

  override fun undo(): Int? {
    if (undoStack.isEmpty()) return null
    val prev = undoStack.removeAt(undoStack.lastIndex)
    redoStack.add(getFullText())
    lines.clear()
    lines.addAll(prev.lines())
    return 0
  }

  override fun redo(): Int? {
    if (redoStack.isEmpty()) return null
    val next = redoStack.removeAt(redoStack.lastIndex)
    undoStack.add(getFullText())
    lines.clear()
    lines.addAll(next.lines())
    return 0
  }

  override fun canUndo(): Boolean = undoStack.isNotEmpty()
  override fun canRedo(): Boolean = redoStack.isNotEmpty()

  override fun resolveLabelJump(lineIndex: Int): Int? {
    return SmaliEditorEngine.resolveLabelJumpForLine(lines, lineIndex)?.lineIndex
  }

  override fun getFullText(): String = lines.joinToString("\n")

  override fun setGrammar(grammar: Int) {
    this.grammar = grammar
  }

  override fun close() {
    lines.clear()
    undoStack.clear()
    redoStack.clear()
  }

  private fun tokenizeLine(offset: Int, line: String): List<NativeTokenSpan> {
    val spans = mutableListOf<NativeTokenSpan>()
    val trimmed = line.trimStart()
    val leadingSpaces = line.length - trimmed.length
    if (trimmed.isEmpty()) return spans

    if (grammar == EditorNativeEngine.GRAMMAR_FILTER_TXT) {
      val commentIdx = line.indexOf('#')
      val ruleEnd = if (commentIdx >= 0) commentIdx else line.length
      if (ruleEnd > leadingSpaces) {
        val ruleContent = line.substring(leadingSpaces, ruleEnd).trimEnd()
        if (ruleContent.isNotEmpty() && !trimmed.startsWith("#")) {
          val kind = when {
            trimmed.startsWith("!") -> NativeTokenSpan.KIND_FILTER_EXCLUSION
            trimmed.startsWith("=") -> NativeTokenSpan.KIND_FILTER_EXACT
            else -> NativeTokenSpan.KIND_FILTER_PATTERN
          }
          spans.add(NativeTokenSpan(offset, leadingSpaces, leadingSpaces + ruleContent.length, kind))
        }
      }
      if (commentIdx >= 0) {
        spans.add(NativeTokenSpan(offset, commentIdx, line.length, NativeTokenSpan.KIND_COMMENT))
      }
      return spans
    }

    if (trimmed.startsWith("#")) {
      spans.add(NativeTokenSpan(offset, leadingSpaces, line.length, NativeTokenSpan.KIND_COMMENT))
      return spans
    }

    if (trimmed.startsWith(".")) {
      val word = trimmed.split(" ", "\t").firstOrNull() ?: trimmed
      spans.add(NativeTokenSpan(offset, leadingSpaces, leadingSpaces + word.length, NativeTokenSpan.KIND_DIRECTIVE))
    } else if (trimmed.startsWith(":")) {
      val word = trimmed.split(" ", "\t").firstOrNull() ?: trimmed
      spans.add(NativeTokenSpan(offset, leadingSpaces, leadingSpaces + word.length, NativeTokenSpan.KIND_LABEL_DEF))
    }

    return spans
  }
}

/**
 * JNI bridge facade to the high-performance Rust Editor Native Engine.
 */
object EditorNativeEngine {

  private const val TAG = "EditorNativeEngine"

  const val GRAMMAR_SMALI = 0
  const val GRAMMAR_FILTER_TXT = 1

  fun createSession(initialText: String, grammar: Int = GRAMMAR_SMALI): EditorSessionInterface {
    if (DexNativeEngine.isAvailable) {
      try {
        val handle = nativeCreateSession(initialText)
        if (handle != 0L) {
          val session = NativeEditorSession(handle)
          if (grammar != GRAMMAR_SMALI) {
            session.setGrammar(grammar)
          }
          return session
        }
      } catch (e: Throwable) {
        Log.w(TAG, "Failed to create native editor session, falling back to Kotlin: ${e.message}")
      }
    }
    return KotlinFallbackEditorSession(initialText, grammar)
  }

  // --- External JNI Declarations ---

  @JvmStatic
  external fun nativeCreateSession(initialText: String): Long

  @JvmStatic
  external fun nativeDestroySession(handle: Long)

  @JvmStatic
  external fun nativeSetGrammar(handle: Long, grammar: Int)

  @JvmStatic
  external fun nativeGetLineCount(handle: Long): Int

  @JvmStatic
  external fun nativeGetLines(handle: Long, startLine: Int, count: Int): Array<String>?

  @JvmStatic
  external fun nativeGetLineTokens(handle: Long, startLine: Int, count: Int): IntArray?

  @JvmStatic
  external fun nativeApplyEdit(handle: Long, startChar: Int, endChar: Int, replacement: String)

  @JvmStatic
  external fun nativeUndo(handle: Long): Int

  @JvmStatic
  external fun nativeRedo(handle: Long): Int

  @JvmStatic
  external fun nativeCanUndo(handle: Long): Boolean

  @JvmStatic
  external fun nativeCanRedo(handle: Long): Boolean

  @JvmStatic
  external fun nativeResolveLabelJump(handle: Long, lineIndex: Int): Int

  @JvmStatic
  external fun nativeGetFullText(handle: Long): String?

  @JvmStatic
  external fun nativeSearch(
    handle: Long,
    query: String,
    isRegex: Boolean,
    caseSensitive: Boolean,
    maxResults: Int,
  ): String?
}
