package com.dex2c.studio.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.dex2c.studio.dex.ApkDexEntry
import com.dex2c.studio.dex.DexClassInfo
import com.dex2c.studio.dex.DexHeaderInfo
import com.dex2c.studio.dex.DexParsedFile
import com.dex2c.studio.transpiler.TranspilerConfig
import com.dex2c.studio.ui.screens.ClassesScreen
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
class AdaptiveLayoutRobolectricTest {

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

  private fun createDummyReadyLoadState(classes: List<DexClassInfo>): DexLoadState.Ready {
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
    val parsed = DexParsedFile(
      sourceLabel = "test.apk",
      entryName = "classes.dex",
      rawBytes = ByteArray(100),
      header = hdr,
      classes = classes,
      strings = listOf("sample"),
    )
    return DexLoadState.Ready(
      sourceLabel = "test.apk",
      dexEntries = listOf(ApkDexEntry("classes.dex", 100)),
      activeEntryName = "classes.dex",
      parsedDex = parsed,
      loadedDexes = mapOf("classes.dex" to parsed),
    )
  }

  @Test
  fun testWindowAdaptiveInfoBreakpoints() {
    val phonePortrait = WindowAdaptiveInfo(
      widthSizeClass = WindowWidthSizeClass.fromWidth(412.dp),
      heightSizeClass = WindowHeightSizeClass.fromHeight(892.dp),
      maxWidth = 412.dp,
      maxHeight = 892.dp,
    )
    assertEquals(WindowWidthSizeClass.COMPACT, phonePortrait.widthSizeClass)
    assertEquals(WindowHeightSizeClass.MEDIUM, phonePortrait.heightSizeClass)
    assertFalse(phonePortrait.isLandscape)
    assertFalse(phonePortrait.useNavRail)
    assertFalse(phonePortrait.isTwoPane)

    val phoneLandscape = WindowAdaptiveInfo(
      widthSizeClass = WindowWidthSizeClass.fromWidth(740.dp),
      heightSizeClass = WindowHeightSizeClass.fromHeight(360.dp),
      maxWidth = 740.dp,
      maxHeight = 360.dp,
    )
    assertEquals(WindowWidthSizeClass.MEDIUM, phoneLandscape.widthSizeClass)
    assertEquals(WindowHeightSizeClass.COMPACT, phoneLandscape.heightSizeClass)
    assertTrue(phoneLandscape.isLandscape)
    assertTrue(phoneLandscape.useNavRail)
    assertFalse(phoneLandscape.isTwoPane)

    val tabletLandscape = WindowAdaptiveInfo(
      widthSizeClass = WindowWidthSizeClass.fromWidth(1024.dp),
      heightSizeClass = WindowHeightSizeClass.fromHeight(768.dp),
      maxWidth = 1024.dp,
      maxHeight = 768.dp,
    )
    assertEquals(WindowWidthSizeClass.EXPANDED, tabletLandscape.widthSizeClass)
    assertEquals(WindowHeightSizeClass.MEDIUM, tabletLandscape.heightSizeClass)
    assertTrue(tabletLandscape.isLandscape)
    assertTrue(tabletLandscape.useNavRail)
    assertTrue(tabletLandscape.isTwoPane)
  }

  @Test
  @Config(sdk = [36], qualifiers = "w412dp-h892dp")
  fun testClassesScreenPhonePortraitSinglePaneInspection() {
    val dummyClass = createDummyClass("Lcom/example/PhoneActivity;")
    val loadState = createDummyReadyLoadState(listOf(dummyClass))
    var inspectedFullClass: DexClassInfo? = null
    var selectedInTwoPane: DexClassInfo? = null

    composeTestRule.setContent {
      Dex2cTheme {
        ClassesScreen(
          loadState = loadState,
          classes = listOf(dummyClass),
          filterState = ClassFilterState(onlyWithBytecode = false),
          selectedMethodIds = emptySet(),
          allowInit = true,
          onInspectClassSmali = { inspectedFullClass = it },
          onSelectClassInTwoPane = { selectedInTwoPane = it },
          config = TranspilerConfig(),
        )
      }
    }

    // In single-pane phone mode, expand package and click class to invoke full-screen navigation callback
    composeTestRule.onNodeWithTag("pkg_row_com.example").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("class_row_0").assertIsDisplayed().performClick()
    assertEquals(dummyClass, inspectedFullClass)
    assertEquals(null, selectedInTwoPane)
  }

  @Test
  @Config(sdk = [36], qualifiers = "w1024dp-h768dp")
  fun testClassesScreenTabletTwoPaneMasterDetail() {
    val dummyClass = createDummyClass("Lcom/example/TabletActivity;")
    val loadState = createDummyReadyLoadState(listOf(dummyClass))
    var inspectedFullClass: DexClassInfo? = null
    var selectedInTwoPane: DexClassInfo? = null

    composeTestRule.setContent {
      Dex2cTheme {
        ClassesScreen(
          loadState = loadState,
          classes = listOf(dummyClass),
          filterState = ClassFilterState(onlyWithBytecode = false),
          selectedMethodIds = emptySet(),
          allowInit = true,
          onInspectClassSmali = { inspectedFullClass = it },
          onSelectClassInTwoPane = { selectedInTwoPane = it },
          config = TranspilerConfig(),
        )
      }
    }

    // Master pane tree list must be displayed
    composeTestRule.onNodeWithTag("class_search_input").assertIsDisplayed()
    composeTestRule.onNodeWithTag("classes_explorer_list").assertIsDisplayed()

    // Expanding package and clicking a class in two-pane mode calls onSelectClassInTwoPane
    composeTestRule.onNodeWithTag("pkg_row_com.example").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("class_row_0").assertIsDisplayed().performClick()
    assertEquals(dummyClass, selectedInTwoPane)
    assertEquals(null, inspectedFullClass)
  }
}
