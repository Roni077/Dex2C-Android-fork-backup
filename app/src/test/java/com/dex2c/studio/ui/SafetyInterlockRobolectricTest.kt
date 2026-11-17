package com.dex2c.studio.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.dex2c.studio.dex.DexClassInfo
import com.dex2c.studio.dex.DexHeaderInfo
import com.dex2c.studio.dex.DexParsedFile
import com.dex2c.studio.ui.components.DexStringsExplorerTab
import com.dex2c.studio.ui.screens.ClassesScreen
import com.dex2c.studio.ui.screens.SmaliClassScreen
import com.dex2c.studio.ui.theme.Dex2cTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SafetyInterlockRobolectricTest {

  @get:Rule val composeTestRule = createComposeRule()

  private fun createDummyClass(descriptor: String = "Lcom/example/MyClass;"): DexClassInfo {
    return DexClassInfo(
      classIndex = 0,
      descriptor = descriptor,
      fullName = descriptor.removePrefix("L").removeSuffix(";").replace('/', '.'),
      simpleName = descriptor.substringAfterLast('/').removeSuffix(";"),
      packageName = descriptor.removePrefix("L").substringBeforeLast('/', "").replace('/', '.'),
      superclass = "Ljava/lang/Object;",
      interfaces = emptyList(),
      sourceFile = null,
      accessFlags = 1,
      directMethods = emptyList(),
      virtualMethods = emptyList(),
      instanceFields = emptyList(),
      staticFields = emptyList(),
    )
  }

  private fun createDummyDex(classes: List<DexClassInfo>): DexParsedFile {
    val hdr = DexHeaderInfo(
      magic = "dex\n035",
      version = "035",
      checksum = 1234L,
      sha1Hex = "0123456789abcdef",
      fileSize = 100,
      endianTag = 0x12345678L,
      stringIdsSize = 1,
      typeIdsSize = 1,
      protoIdsSize = 1,
      fieldIdsSize = 1,
      methodIdsSize = 1,
      classDefsSize = classes.size,
    )
    return DexParsedFile(
      sourceLabel = "test.apk",
      entryName = "classes.dex",
      rawBytes = ByteArray(100),
      header = hdr,
      classes = classes,
      strings = listOf("hello", "world"),
    )
  }

  @Test
  fun testSmaliClassScreenAssemblyDisabledWhenFlagOff() {
    val cls = createDummyClass()
    val state = SmaliClassUiState.Ready(
      dexClass = cls,
      lines = listOf(".class public Lcom/example/MyClass;", ".super Ljava/lang/Object;"),
      lineCount = 2,
      methodCount = 0,
      fieldCount = 0,
      durationMs = 5L,
    )

    composeTestRule.setContent {
      Dex2cTheme {
        SmaliClassScreen(
          state = state,
          onCopySmali = {},
          onExportSmali = { _, _ -> },
          onBack = {},
          dexEditingEnabled = false,
        )
      }
    }

    // Switch to edit mode
    composeTestRule.onNodeWithTag("smali_mode_edit").performClick()
    composeTestRule.waitForIdle()

    // Banner must be displayed
    composeTestRule.onNodeWithTag("smali_assembly_disabled_banner").assertIsDisplayed()

    // Assemble button must be disabled
    composeTestRule.onNodeWithTag("smali_compile_dex_btn").assertIsNotEnabled()
  }

  @Test
  fun testStringsExplorerTabDisabledWhenFlagOff() {
    val dex = createDummyDex(listOf(createDummyClass()))

    composeTestRule.setContent {
      Dex2cTheme {
        DexStringsExplorerTab(
          parsedDex = dex,
          onReplaceStringInDex = { _, _, _ -> },
          onCopyString = {},
          dexEditingEnabled = false,
        )
      }
    }

    composeTestRule.onNodeWithTag("strings_editing_disabled_note").assertIsDisplayed()
    composeTestRule.onNodeWithTag("strings_replace_btn").assertIsNotEnabled()
  }

  @Test
  fun testSmaliClassScreenAssemblyEnabledWhenFlagOn() {
    val cls = createDummyClass()
    val state = SmaliClassUiState.Ready(
      dexClass = cls,
      lines = listOf(".class public Lcom/example/MyClass;", ".super Ljava/lang/Object;"),
      lineCount = 2,
      methodCount = 0,
      fieldCount = 0,
      durationMs = 5L,
    )

    composeTestRule.setContent {
      Dex2cTheme {
        SmaliClassScreen(
          state = state,
          onCopySmali = {},
          onExportSmali = { _, _ -> },
          onBack = {},
          dexEditingEnabled = true,
        )
      }
    }

    // Switch to edit mode
    composeTestRule.onNodeWithTag("smali_mode_edit").performClick()
    composeTestRule.waitForIdle()

    // Banner must NOT be displayed
    composeTestRule.onNodeWithTag("smali_assembly_disabled_banner").assertDoesNotExist()

    // Assemble button must be enabled
    composeTestRule.onNodeWithTag("smali_compile_dex_btn").assertIsEnabled()
  }

  @Test
  fun testStringsExplorerTabEnabledWhenFlagOn() {
    val dex = createDummyDex(listOf(createDummyClass()))

    composeTestRule.setContent {
      Dex2cTheme {
        DexStringsExplorerTab(
          parsedDex = dex,
          onReplaceStringInDex = { _, _, _ -> },
          onCopyString = {},
          dexEditingEnabled = true,
        )
      }
    }

    composeTestRule.onNodeWithTag("strings_editing_disabled_note").assertDoesNotExist()
    // When search query is entered, replace button is enabled
    composeTestRule.onNodeWithTag("strings_explorer_search").performTextInput("hello")
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("strings_replace_btn").assertIsEnabled()
  }

  @Test
  fun testClassesScreenDeleteConfirmationDialogCancelAndConfirm() {
    val cls = createDummyClass("Lcom/example/DeleteTarget;")
    val dex = createDummyDex(listOf(cls))
    val readyState = DexLoadState.Ready("test.apk", emptyList(), "classes.dex", dex)
    var deletedClass: DexClassInfo? = null

    composeTestRule.setContent {
      Dex2cTheme {
        ClassesScreen(
          loadState = readyState,
          classes = listOf(cls),
          filterState = ClassFilterState(searchQuery = "DeleteTarget"),
          selectedMethodIds = emptySet(),
          allowInit = false,
          onSearchChange = {},
          onToggleHideFramework = {},
          onToggleOnlyBytecode = {},
          onToggleOnlySelected = {},
          onToggleClassSelection = {},
          onToggleMethodSelection = {},
          onInspectMethod = { _, _ -> },
          onDeleteDexClass = { deletedClass = it },
        )
      }
    }

    // Enable selection mode so class action buttons (+W, !B, delete) are displayed
    composeTestRule.onNodeWithTag("chip_select_mode").performClick()
    composeTestRule.waitForIdle()

    // Click delete icon
    composeTestRule.onNodeWithTag("class_delete_btn_0").performClick()
    composeTestRule.waitForIdle()

    // Dialog should be displayed
    composeTestRule.onNodeWithTag("delete_class_dialog").assertIsDisplayed()
    assertNull(deletedClass)

    // Click cancel
    composeTestRule.onNodeWithTag("delete_class_cancel_btn").performClick()
    composeTestRule.waitForIdle()
    assertNull(deletedClass)

    // Click delete icon again and confirm
    composeTestRule.onNodeWithTag("class_delete_btn_0").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("delete_class_dialog").assertIsDisplayed()

    composeTestRule.onNodeWithTag("delete_class_confirm_btn").performClick()
    composeTestRule.waitForIdle()
    assertEquals("Lcom/example/DeleteTarget;", deletedClass?.descriptor)
  }
}
