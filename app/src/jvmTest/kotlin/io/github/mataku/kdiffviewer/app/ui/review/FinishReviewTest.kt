package io.github.mataku.kdiffviewer.app.ui.review

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class FinishReviewTest {
  private val sent = mutableListOf<Pair<String, Boolean>>()

  private fun androidx.compose.ui.test.ComposeUiTest.showButton(pendingCount: Int, waiting: Boolean = false, initialSummary: String = "") {
    setContent {
      var summary by remember { mutableStateOf(initialSummary) }
      FinishReviewButton(
        pendingCount = pendingCount,
        waiting = waiting,
        summary = summary,
        onSummaryChange = { summary = it },
        onSend = { approve ->
          sent += summary to approve
          summary = ""
        },
      )
    }
  }

  private fun androidx.compose.ui.test.ComposeUiTest.showBar(pendingCount: Int, waiting: Boolean = false) {
    setContent {
      var summary by remember { mutableStateOf("") }
      ReviewBar(
        pendingCount = pendingCount,
        waiting = waiting,
        summary = summary,
        onSummaryChange = { summary = it },
        onSend = { approve -> sent += summary to approve },
      )
    }
  }

  @Test
  fun buttonShowsPendingCount() = runComposeUiTest {
    showButton(pendingCount = 3)
    onNodeWithText("Finish Review").assertIsDisplayed()
    onNodeWithTag("finish-review-count").assertIsDisplayed()
    onNodeWithText("3").assertIsDisplayed()
  }

  @Test
  fun buttonHidesCountWhenNothingPending() = runComposeUiTest {
    showButton(pendingCount = 0)
    onNodeWithTag("finish-review-count").assertDoesNotExist()
  }

  @Test
  fun popoverSubmitDisabledWithNothingToSend() = runComposeUiTest {
    showButton(pendingCount = 0)
    onNodeWithText("Finish Review").performClick()
    onNodeWithText("Submit").assertIsNotEnabled()
    onNodeWithText("Approve").assertIsEnabled()
  }

  @Test
  fun popoverSubmitEnabledWithSummary() = runComposeUiTest {
    showButton(pendingCount = 0)
    onNodeWithText("Finish Review").performClick()
    onNodeWithTag("review-summary").performTextInput("overall")
    onNodeWithText("Submit").assertIsEnabled()
    onNodeWithText("Submit").performClick()
    assertEquals(listOf("overall" to false), sent)
    onNodeWithTag("finish-review-popover").assertDoesNotExist()
  }

  @Test
  fun popoverShowsPendingCountAndApproves() = runComposeUiTest {
    showButton(pendingCount = 2)
    onNodeWithText("Finish Review").performClick()
    onNodeWithText("2 pending comments").assertIsDisplayed()
    onNodeWithText("Approve").performClick()
    assertEquals(listOf("" to true), sent)
  }

  @Test
  fun waitingIndicatorOnlyWhileWaiting() = runComposeUiTest {
    var waiting by mutableStateOf(false)
    setContent { FinishReviewButton(0, waiting, "", {}, {}) }
    onNodeWithText("Agent waiting").assertDoesNotExist()
    waiting = true
    onNodeWithText("Agent waiting").assertIsDisplayed()
  }

  @Test
  fun barHiddenWithNothingPendingAndNoWaiter() = runComposeUiTest {
    showBar(pendingCount = 0)
    onNodeWithTag("review-bar").assertDoesNotExist()
  }

  @Test
  fun barShownWhileWaiting() = runComposeUiTest {
    showBar(pendingCount = 0, waiting = true)
    onNodeWithTag("review-bar").assertIsDisplayed()
    onNodeWithText("Agent waiting").assertIsDisplayed()
  }

  @Test
  fun barSubmitsPending() = runComposeUiTest {
    showBar(pendingCount = 1)
    onNodeWithText("1 pending comment").assertIsDisplayed()
    onNodeWithText("Submit").performClick()
    assertEquals(listOf("" to false), sent)
  }
}
