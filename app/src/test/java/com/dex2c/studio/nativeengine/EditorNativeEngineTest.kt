package com.dex2c.studio.nativeengine

import com.dex2c.studio.ui.components.buildAnnotatedLine
import com.dex2c.studio.ui.theme.DarkSyntaxPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EditorNativeEngineTest {

  private val sampleSmali =
    """
    |.class public Lcom/example/TestClass;
    |.super Ljava/lang/Object;
    |
    |.method public static check(I)Z
    |    .registers 2
    |    const/4 v0, 0x1
    |    if-lez p0, :cond_pos
    |    const/4 v0, 0x0
    |    goto :return_val
    |
    |:cond_pos
    |    const/4 v0, 0x1
    |
    |:return_val
    |    return v0
    |.end method
    """.trimMargin()

  @Test
  fun editorSession_initializesAndReadsLines() {
    val session = EditorNativeEngine.createSession(sampleSmali)
    try {
      assertTrue("Line count should be at least 15", session.lineCount >= 15)
      val lines = session.getLines(0, 3)
      assertEquals(3, lines.size)
      assertEquals(".class public Lcom/example/TestClass;", lines[0])
      assertEquals(".super Ljava/lang/Object;", lines[1])
    } finally {
      session.close()
    }
  }

  @Test
  fun editorSession_tokenizesLines() {
    val session = EditorNativeEngine.createSession(sampleSmali)
    try {
      val tokens = session.getLineTokens(0, 1)
      assertEquals(1, tokens.size)
      val line0Tokens = tokens[0]
      assertTrue("Directives or identifiers should be found", line0Tokens.isNotEmpty())
    } finally {
      session.close()
    }
  }

  @Test
  fun editorSession_resolvesLabelJumps() {
    val session = EditorNativeEngine.createSession(sampleSmali)
    try {
      val lines = session.getLines(0, session.lineCount)
      val ifLineIndex = lines.indexOfFirst { it.contains("if-lez") }
      assertTrue("if-lez line must exist", ifLineIndex >= 0)

      val targetJump = session.resolveLabelJump(ifLineIndex)
      assertNotNull("Label jump must be resolved", targetJump)
      val targetLine = lines[targetJump!!].trim()
      assertEquals(":cond_pos", targetLine)
    } finally {
      session.close()
    }
  }

  @Test
  fun editorSession_editsAndUndoRedo() {
    val session = EditorNativeEngine.createSession("first line\nsecond line\nthird line")
    try {
      assertEquals(3, session.lineCount)
      assertFalse(session.canUndo())

      session.applyEdit(0, 10, "MODIFIED")
      val modifiedLines = session.getLines(0, session.lineCount)
      assertEquals("MODIFIED", modifiedLines[0])
      assertTrue(session.canUndo())

      val undoPos = session.undo()
      assertNotNull(undoPos)
      val restoredLines = session.getLines(0, session.lineCount)
      assertEquals("first line", restoredLines[0])
      assertTrue(session.canRedo())

      val redoPos = session.redo()
      assertNotNull(redoPos)
      val redoneLines = session.getLines(0, session.lineCount)
      assertEquals("MODIFIED", redoneLines[0])
    } finally {
      session.close()
    }
  }

  @Test
  fun buildAnnotatedLine_appliesTokenStyles() {
    val text = ".class public Lcom/example/MyClass;"
    val spans = listOf(
      NativeTokenSpan(0, 0, 6, NativeTokenSpan.KIND_DIRECTIVE),
      NativeTokenSpan(0, 14, text.length, NativeTokenSpan.KIND_TYPE_DESCRIPTOR),
    )
    val annotated = buildAnnotatedLine(text, spans, DarkSyntaxPalette)
    assertEquals(text, annotated.text)
    assertTrue("Styles must be applied", annotated.spanStyles.isNotEmpty())
  }

  @Test
  fun editorSession_tokenizesFilterTxtGrammar() {
    val filterText =
      """
      |# Dex2C Filter Rules
      |!com/example/UnwantedClass # skip this
      |=Lcom/example/MainActivity;onCreate(Landroid/os/Bundle;)V
      |^Lcom/dex2c/studio/.*
      """.trimMargin()

    val session = EditorNativeEngine.createSession(filterText, EditorNativeEngine.GRAMMAR_FILTER_TXT)
    try {
      assertEquals(4, session.lineCount)
      val tokens = session.getLineTokens(0, 4)
      assertEquals(4, tokens.size)

      // Line 0: Comment
      val line0 = tokens[0]
      assertEquals(1, line0.size)
      assertEquals(NativeTokenSpan.KIND_COMMENT, line0[0].kind)

      // Line 1: Exclusion + Inline comment
      val line1 = tokens[1]
      assertTrue("Line 1 should have exclusion rule and optional comment", line1.isNotEmpty())
      assertEquals(NativeTokenSpan.KIND_FILTER_EXCLUSION, line1[0].kind)
      if (line1.size > 1) {
        assertEquals(NativeTokenSpan.KIND_COMMENT, line1[1].kind)
      }

      // Line 2: Exact rule
      val line2 = tokens[2]
      assertEquals(1, line2.size)
      assertEquals(NativeTokenSpan.KIND_FILTER_EXACT, line2[0].kind)

      // Line 3: Pattern rule
      val line3 = tokens[3]
      assertEquals(1, line3.size)
      assertEquals(NativeTokenSpan.KIND_FILTER_PATTERN, line3[0].kind)
    } finally {
      session.close()
    }
  }

  @Test
  fun buildAnnotatedFilterLine_appliesFilterStyles() {
    val line = "!com/example/TargetClass"
    val spans = listOf(
      NativeTokenSpan(0, 0, line.length, NativeTokenSpan.KIND_FILTER_EXCLUSION)
    )
    val annotated = com.dex2c.studio.ui.components.buildAnnotatedFilterLine(line, spans, DarkSyntaxPalette)
    assertEquals(line, annotated.text)
    assertTrue("Styles must be applied", annotated.spanStyles.isNotEmpty())
  }
}
