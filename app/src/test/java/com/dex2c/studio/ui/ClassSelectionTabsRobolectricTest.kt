package com.dex2c.studio.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.dex2c.studio.dex.DexClassInfo
import com.dex2c.studio.dex.DexEncodedMethod
import com.dex2c.studio.dex.DexMethodRef
import com.dex2c.studio.dex.DexProto
import com.dex2c.studio.dex.SmaliMethodSpan
import com.dex2c.studio.ui.components.ClassMethodsSelectorSheet
import com.dex2c.studio.ui.components.ClassPickerSheet
import com.dex2c.studio.ui.components.ClassSelectionHeader
import com.dex2c.studio.ui.components.ClassViewerTabBar
import com.dex2c.studio.ui.components.SymbolQuickBar
import com.dex2c.studio.ui.editor.ClassViewerTab
import com.dex2c.studio.ui.theme.Dex2cTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h892dp")
class ClassSelectionTabsRobolectricTest {

  @get:Rule val composeTestRule = createComposeRule()

  private fun createDummyMethod(
    classDescriptor: String,
    methodName: String,
    methodIndex: Int = 0,
    hasBytecode: Boolean = true,
  ): DexEncodedMethod {
    val methodRef = DexMethodRef(
      definingClass = classDescriptor,
      proto = DexProto(shorty = "V", returnType = "V", parameterTypes = emptyList()),
      name = methodName,
    )
    return DexEncodedMethod(
      id = "$classDescriptor->$methodName()V",
      methodIndex = methodIndex,
      methodRef = methodRef,
      accessFlags = 1,
      isDirect = true,
      codeMeta = if (hasBytecode) com.dex2c.studio.dex.DexCodeMeta(1, 1, 0, 0, 2, 0) else null,
      sourceEntryName = "classes.dex",
    )
  }

  private fun createDummyClass(
    descriptor: String = "Lcom/example/MainActivity;",
    methodNames: List<String> = listOf("onCreate", "onResume"),
  ): DexClassInfo {
    val directMethods = methodNames.mapIndexed { idx, name ->
      createDummyMethod(descriptor, name, idx)
    }
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
      directMethods = directMethods,
      virtualMethods = emptyList(),
      instanceFields = emptyList(),
      staticFields = emptyList(),
    )
  }

  @Test
  fun testClassViewerTabBarRendersAndSelectsTabs() {
    val classA = createDummyClass("Lcom/example/MainActivity;")
    val classB = createDummyClass("Lcom/example/HttpHelper;")

    val tabA = ClassViewerTab(
      id = "tab_a",
      dexClass = classA,
      initialLines = listOf(".class public Lcom/example/MainActivity;"),
    )
    val tabB = ClassViewerTab(
      id = "tab_b",
      dexClass = classB,
      initialLines = listOf(".class public Lcom/example/HttpHelper;"),
    )

    var selectedTabId: String? = "tab_a"
    var closedTabId: String? = null
    var addClicked = false

    composeTestRule.setContent {
      Dex2cTheme {
        ClassViewerTabBar(
          tabs = listOf(tabA, tabB),
          activeTabId = selectedTabId,
          selectedMethodIds = setOf("Lcom/example/MainActivity;->onCreate()V"),
          onSelectTab = { selectedTabId = it },
          onCloseTab = { closedTabId = it },
          onCloseOtherTabs = {},
          onCloseAllTabs = {},
          onOpenNewTabClick = { addClicked = true },
        )
      }
    }

    composeTestRule.onNodeWithTag("class_tab_tab_a").assertIsDisplayed()
    composeTestRule.onNodeWithTag("class_tab_tab_b").assertIsDisplayed()
    composeTestRule.onNodeWithTag("class_tab_add_btn").assertIsDisplayed()

    // Click tab B title to select it
    composeTestRule.onNodeWithTag("class_tab_title_tab_b", useUnmergedTree = true).performClick()
    assertEquals("tab_b", selectedTabId)

    // Click tab A close button
    composeTestRule.onNodeWithTag("class_tab_close_tab_a", useUnmergedTree = true).performClick()
    assertEquals("tab_a", closedTabId)

    // Click add button
    composeTestRule.onNodeWithTag("class_tab_add_btn").performClick()
    assertTrue(addClicked)
  }

  @Test
  fun testClassSelectionHeaderTriStateAndButtons() {
    val cls = createDummyClass(
      "Lcom/example/TestService;",
      listOf("startService", "stopService"),
    )
    var toggled = false
    var selectAllCalled = false
    var deselectAllCalled = false
    var invertCalled = false
    var methodsSheetOpened = false

    composeTestRule.setContent {
      Dex2cTheme {
        ClassSelectionHeader(
          dexClass = cls,
          selectedMethodIds = setOf("Lcom/example/TestService;->startService()V"),
          onToggleSelectClass = { toggled = true },
          onSelectAllMethods = { selectAllCalled = true },
          onDeselectAllMethods = { deselectAllCalled = true },
          onInvertMethodSelection = { invertCalled = true },
          onOpenMethodsSheet = { methodsSheetOpened = true },
        )
      }
    }

    composeTestRule.onNodeWithTag("class_selection_header").assertIsDisplayed()
    composeTestRule.onNodeWithTag("class_selection_checkbox").assertIsDisplayed()
    composeTestRule.onNodeWithTag("class_select_all_btn").assertIsDisplayed()
    composeTestRule.onNodeWithTag("class_deselect_all_btn").assertIsDisplayed()
    composeTestRule.onNodeWithTag("class_invert_selection_btn").assertIsDisplayed()
    composeTestRule.onNodeWithTag("class_open_methods_sheet_btn").assertIsDisplayed()

    // Test clicking all buttons
    composeTestRule.onNodeWithTag("class_selection_checkbox").performClick()
    assertTrue(toggled)

    composeTestRule.onNodeWithTag("class_select_all_btn").performClick()
    assertTrue(selectAllCalled)

    composeTestRule.onNodeWithTag("class_deselect_all_btn").performClick()
    assertTrue(deselectAllCalled)

    composeTestRule.onNodeWithTag("class_invert_selection_btn").performClick()
    assertTrue(invertCalled)

    composeTestRule.onNodeWithTag("class_open_methods_sheet_btn").performClick()
    assertTrue(methodsSheetOpened)
  }

  @Test
  fun testClassMethodsSelectorSheetRendersAndToggles() {
    val cls = createDummyClass(
      "Lcom/example/Calc;",
      listOf("add", "subtract"),
    )
    val spans = listOf(
      SmaliMethodSpan(name = "add", signature = "add(II)I", startLineIndex = 5, endLineIndex = 12),
      SmaliMethodSpan(name = "subtract", signature = "subtract(II)I", startLineIndex = 14, endLineIndex = 20),
    )

    var toggledMethodId: String? = null
    var jumpedSpan: SmaliMethodSpan? = null

    composeTestRule.setContent {
      Dex2cTheme {
        ClassMethodsSelectorSheet(
          dexClass = cls,
          methodSpans = spans,
          selectedMethodIds = setOf("Lcom/example/Calc;->add()V"),
          onToggleMethodSelection = { toggledMethodId = it },
          onSelectAllMethods = {},
          onDeselectAllMethods = {},
          onInvertMethodSelection = {},
          onJumpToSpan = { jumpedSpan = it },
          onInspectMethodC = {},
          onDismiss = {},
        )
      }
    }

    composeTestRule.onNodeWithTag("class_methods_selector_sheet").assertIsDisplayed()
    composeTestRule.onNodeWithTag("methods_search_input").assertIsDisplayed()
    composeTestRule.onNodeWithTag("method_checkbox_0").assertExists()
    composeTestRule.onNodeWithTag("method_jump_btn_0").assertExists()

    // Click checkbox for second method
    composeTestRule.onNodeWithTag("method_checkbox_1").assertExists().performClick()
    assertEquals("Lcom/example/Calc;->subtract()V", toggledMethodId)

    // Click jump to method 0
    composeTestRule.onNodeWithTag("method_jump_btn_0").assertExists().performClick()
    assertEquals("add", jumpedSpan?.name)

    // Filter methods using search
    composeTestRule.onNodeWithTag("methods_search_input").performTextInput("sub")
    composeTestRule.onNodeWithTag("method_checkbox_0").assertExists()
  }

  @Test
  fun testClassPickerSheetFiltersAndSelects() {
    val classA = createDummyClass("Lcom/example/AppClass;")
    val classB = createDummyClass("Lcom/example/NetworkService;")
    var pickedClass: DexClassInfo? = null

    composeTestRule.setContent {
      Dex2cTheme {
        ClassPickerSheet(
          classes = listOf(classA, classB),
          selectedMethodIds = emptySet(),
          onSelectClass = { pickedClass = it },
          onDismiss = {},
        )
      }
    }

    composeTestRule.onNodeWithTag("class_picker_sheet").assertIsDisplayed()
    composeTestRule.onNodeWithTag("class_picker_search").assertIsDisplayed()
    composeTestRule.onNodeWithTag("class_picker_item_0").assertExists()
    composeTestRule.onNodeWithTag("class_picker_item_1").assertExists().performClick()
    assertEquals("NetworkService", pickedClass?.simpleName)
  }

  @Test
  fun testSymbolQuickBarInsertsSymbols() {
    var inserted: String? = null

    composeTestRule.setContent {
      Dex2cTheme {
        SymbolQuickBar(
          onInsertSymbol = { inserted = it },
        )
      }
    }

    composeTestRule.onNodeWithTag("symbol_quick_bar").assertIsDisplayed()
    composeTestRule.onNodeWithTag("symbol_btn_->").assertIsDisplayed().performClick()
    assertEquals("->", inserted)

    composeTestRule.onNodeWithTag("symbol_btn_{}").assertIsDisplayed().performClick()
    assertEquals("{}", inserted)
  }
}
