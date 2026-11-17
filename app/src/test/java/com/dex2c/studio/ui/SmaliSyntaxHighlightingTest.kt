package com.dex2c.studio.ui

import androidx.test.core.app.ApplicationProvider
import com.dex2c.studio.data.SmaliSyntaxTheme
import com.dex2c.studio.ui.components.highlightSmaliSyntaxBase
import com.dex2c.studio.ui.editor.SmaliEditorManager
import com.dex2c.studio.ui.theme.DarkSyntaxPalette
import com.dex2c.studio.ui.theme.resolveSmaliSyntaxPalette
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SmaliSyntaxHighlightingTest {

  @Test
  fun smaliEditorManager_initializesAndCreatesTextMateLanguageSuccessfully() {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    SmaliEditorManager.ensureInitialized(context)

    val grammar = GrammarRegistry.getInstance().findGrammar(SmaliEditorManager.SCOPE_SMALI)
    assertNotNull("Smali grammar must be registered in GrammarRegistry", grammar)

    val language = SmaliEditorManager.createSmaliLanguage()
    assertNotNull("Smali TextMateLanguage must be successfully instantiated", language)
  }

  @Test
  fun highlightSmaliSyntaxBase_highlightsDirectivesRegistersAndLabels() {
    val palette = resolveSmaliSyntaxPalette(SmaliSyntaxTheme.DARK, DarkSyntaxPalette)

    val classLine = ".class public final Lcom/example/MyClass;"
    val classAnnotated = highlightSmaliSyntaxBase(classLine, palette)
    assertTrue(classAnnotated.spanStyles.isNotEmpty())

    val opcodeLine = "    invoke-virtual {p0, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z"
    val opcodeAnnotated = highlightSmaliSyntaxBase(opcodeLine, palette)
    assertTrue(opcodeAnnotated.spanStyles.isNotEmpty())

    val labelLine = ":cond_0"
    val labelAnnotated = highlightSmaliSyntaxBase(labelLine, palette)
    assertTrue(labelAnnotated.spanStyles.isNotEmpty())

    val fieldLine = "    iget-object v0, p0, Lcom/example/MyClass;->targetField:Ljava/lang/String;"
    val fieldAnnotated = highlightSmaliSyntaxBase(fieldLine, palette)
    assertTrue(fieldAnnotated.spanStyles.isNotEmpty())
  }
}
