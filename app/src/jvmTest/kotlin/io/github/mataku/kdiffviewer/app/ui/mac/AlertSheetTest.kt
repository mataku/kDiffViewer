package io.github.mataku.kdiffviewer.app.ui.mac

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class AlertSheetTest {
  private class Calls {
    var confirms = 0
    var cancels = 0
    var behind = 0
  }

  private fun androidx.compose.ui.test.ComposeUiTest.showSheet(calls: Calls) {
    setContent {
      Box(Modifier.size(600.dp, 400.dp)) {
        Box(Modifier.fillMaxSize().testTag("behind").clickable { calls.behind++ })
        MacAlertSheet(
          title = "Quit kDiffViewer?",
          messageLines = listOf("2 pending comments will be lost."),
          confirmLabel = "Quit",
          onConfirm = { calls.confirms++ },
          onCancel = { calls.cancels++ },
        )
      }
    }
  }

  @Test
  fun showsTitleMessageAndButtons() = runComposeUiTest {
    showSheet(Calls())
    onNodeWithTag(ALERT_ICON_TAG).assertIsDisplayed()
    onNodeWithText("Quit kDiffViewer?").assertIsDisplayed()
    onNodeWithText("2 pending comments will be lost.").assertIsDisplayed()
    onNodeWithText("Cancel").assertIsDisplayed()
    onNodeWithText("Quit").assertIsDisplayed()
  }

  @Test
  fun buttonsReportTheirChoice() = runComposeUiTest {
    val calls = Calls()
    showSheet(calls)
    onNodeWithText("Cancel").performClick()
    onNodeWithText("Quit").performClick()
    assertEquals(1, calls.cancels)
    assertEquals(1, calls.confirms)
  }

  @Test
  fun escapeAndReturnCancel() = runComposeUiTest {
    val calls = Calls()
    showSheet(calls)
    onNodeWithTag(ALERT_SHEET_TAG).assertIsFocused()
    onNodeWithTag(ALERT_SHEET_TAG).performKeyInput { pressKey(Key.Escape) }
    onNodeWithTag(ALERT_SHEET_TAG).performKeyInput { pressKey(Key.Enter) }
    assertEquals(2, calls.cancels)
    assertEquals(0, calls.confirms)
  }

  @Test
  fun clicksDoNotReachTheContentBehind() = runComposeUiTest {
    val calls = Calls()
    showSheet(calls)
    onNodeWithTag("behind").performClick()
    assertEquals(0, calls.behind)
  }
}
