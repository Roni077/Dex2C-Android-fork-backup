package com.dex2c.studio.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.dex2c.studio.cloud.GitHubApiClient
import com.dex2c.studio.cloud.GitHubWorkflowStep
import com.dex2c.studio.transpiler.TranspilerConfig
import com.dex2c.studio.ui.components.BatchActionBar
import com.dex2c.studio.ui.components.FilterRustEditor
import com.dex2c.studio.ui.components.InteractiveFilterRuleBuilderPanel
import com.dex2c.studio.ui.components.formatDisplaySteps
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
class UiEnhancementsRobolectricTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun testFormatDisplayStepsHidesLogStepOnSuccessAndMovesToEndOnFailure() {
    val steps = listOf(
      GitHubWorkflowStep("Set up job", "completed", "success"),
      GitHubWorkflowStep("Checkout Private Workspace", "completed", "success"),
      GitHubWorkflowStep("Compile codehasan/dex2c", "completed", "success"),
      GitHubWorkflowStep("Align & Auto-Sign Output APK", "completed", "success"),
      GitHubWorkflowStep("Upload Signed Dex2C APK Artifact", "completed", "success"),
      GitHubWorkflowStep("Upload Dex2C Build Log", "completed", "skipped"),
      GitHubWorkflowStep("Post Setup", "completed", "success"),
      GitHubWorkflowStep("Complete job", "completed", "success"),
    )

    // Build succeeded: Upload Dex2C Build Log must be hidden
    val successDisplay = formatDisplaySteps(steps, isSuccess = true)
    assertEquals(7, successDisplay.size)
    assertFalse(successDisplay.any { it.name.contains("upload dex2c build log", ignoreCase = true) })

    // Build failed: Upload Dex2C Build Log must be at the very end
    val failureSteps = listOf(
      GitHubWorkflowStep("Set up job", "completed", "success"),
      GitHubWorkflowStep("Run codehasan/dex2c Compilation", "completed", "failure"),
      GitHubWorkflowStep("Upload Dex2C Build Log", "completed", "success"),
      GitHubWorkflowStep("Post Setup", "completed", "success"),
      GitHubWorkflowStep("Complete job", "completed", "success"),
    )
    val failureDisplay = formatDisplaySteps(failureSteps, isSuccess = false)
    assertEquals(5, failureDisplay.size)
    assertEquals("Upload Dex2C Build Log", failureDisplay.last().name)
  }

  @Test
  fun testAwsS3XmlErrorDetection() {
    val client = GitHubApiClient()
    val s3ErrorXml = """
      <?xml version="1.0" encoding="utf-8"?>
      <Error><Code>InvalidArgument</Code><Message>Only one auth mechanism allowed; only the X-Amz-Algorithm query parameter, Signature query string parameter or the Authorization header should be specified</Message></Error>
    """.trimIndent()
    assertTrue(client.isAwsS3XmlError(s3ErrorXml))

    val normalLog = "Starting Dex2C build...\nCompiled 45 classes cleanly.\nDone."
    assertFalse(client.isAwsS3XmlError(normalLog))
  }

  @Test
  fun testBatchActionBarOnlyShowsCountAndFilterButtons() {
    composeTestRule.setContent {
      Dex2cTheme {
        BatchActionBar(
          selectedCount = 3,
          onFilterSelected = {},
          onClearSelection = {},
        )
      }
    }

    composeTestRule.onNodeWithText("3 sel").assertIsDisplayed()
    composeTestRule.onNodeWithTag("batch_add_to_filter_btn").assertIsDisplayed()
    composeTestRule.onNodeWithTag("batch_clear_btn").assertIsDisplayed()

    // Select All, Deselect, Invert must not exist
    composeTestRule.onNodeWithTag("batch_select_all_btn").assertDoesNotExist()
    composeTestRule.onNodeWithTag("batch_deselect_all_btn").assertDoesNotExist()
    composeTestRule.onNodeWithTag("batch_invert_selection_btn").assertDoesNotExist()
  }

  @Test
  fun testFilterRustEditorOnlyShowsEditButton() {
    composeTestRule.setContent {
      Dex2cTheme {
        FilterRustEditor(
          filterText = "com/example/MyClass",
          onFilterTextChanged = {},
          wordWrap = true,
        )
      }
    }

    composeTestRule.onNodeWithTag("filter_bulk_edit_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("filter_search_toggle").assertDoesNotExist()
    composeTestRule.onNodeWithTag("filter_undo_button").assertDoesNotExist()
    composeTestRule.onNodeWithTag("filter_redo_button").assertDoesNotExist()
  }

  @Test
  fun testInteractiveFilterRuleBuilderPanelShowsDynamicTargetPackage() {
    composeTestRule.setContent {
      Dex2cTheme {
        InteractiveFilterRuleBuilderPanel(
          config = TranspilerConfig(),
          hasLoadedDex = true,
          filterMatchSummary = null,
          selectedMethodCount = 0,
          onUpdateConfig = {},
          onApplyFilterRules = {},
          targetPackageName = "com.sample.dynamictarget",
        )
      }
    }

    composeTestRule.onNodeWithText("Target: com.sample.dynamictarget.*").assertIsDisplayed()
    composeTestRule.onNodeWithTag("preset_target_package").assertIsDisplayed()
  }
}
