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
class ReviewBarTest {
  private val sent = mutableListOf<Pair<String, Boolean>>()

  private fun androidx.compose.ui.test.ComposeUiTest.showBar(pendingCount: Int, waiting: Boolean = false) {
    setContent {
      var summary by remember { mutableStateOf("") }
      ReviewBar(
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

  @Test
  fun hiddenWithNothingPendingAndNoWaiter() = runComposeUiTest {
    showBar(pendingCount = 0)
    onNodeWithTag("review-bar").assertDoesNotExist()
  }

  @Test
  fun shownWhileWaiting() = runComposeUiTest {
    showBar(pendingCount = 0, waiting = true)
    onNodeWithTag("review-bar").assertIsDisplayed()
    onNodeWithText("Agent waiting").assertIsDisplayed()
  }

  @Test
  fun waitingIndicatorOnlyWhileWaiting() = runComposeUiTest {
    showBar(pendingCount = 1)
    onNodeWithText("Agent waiting").assertDoesNotExist()
  }

  @Test
  fun placeholderNamesTheAgent() = runComposeUiTest {
    showBar(pendingCount = 1)
    onNodeWithText("Message to the agent (optional)").assertExists()
  }

  @Test
  fun sendToAgentSendsPending() = runComposeUiTest {
    showBar(pendingCount = 1)
    onNodeWithText("1 pending comment").assertIsDisplayed()
    onNodeWithText("Send to Agent").performClick()
    assertEquals(listOf("" to false), sent)
  }

  @Test
  fun sendToAgentDisabledWithNothingToSend() = runComposeUiTest {
    showBar(pendingCount = 0, waiting = true)
    onNodeWithText("Send to Agent").assertIsNotEnabled()
    onNodeWithText("Approve").assertIsEnabled()
  }

  @Test
  fun messageAloneEnablesSend() = runComposeUiTest {
    showBar(pendingCount = 0, waiting = true)
    onNodeWithTag("review-summary").performTextInput("overall")
    onNodeWithText("Send to Agent").performClick()
    assertEquals(listOf("overall" to false), sent)
  }

  @Test
  fun approveSendsApproval() = runComposeUiTest {
    showBar(pendingCount = 2)
    onNodeWithText("2 pending comments").assertIsDisplayed()
    onNodeWithText("Approve").performClick()
    assertEquals(listOf("" to true), sent)
  }
}
