package io.github.mataku.kdiffviewer.app.ui.mac

import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class TabbedPanelTest {
  private fun androidx.compose.ui.test.ComposeUiTest.show(onSelect: (String) -> Unit = {}) {
    setContent {
      MacTabbedPanel(
        options = listOf("A", "B"),
        selected = "A",
        label = { it },
        tag = { "tab-$it" },
        onSelect = onSelect,
      ) {
        BasicText("body")
      }
    }
  }

  @Test
  fun clickingTabSelectsIt() = runComposeUiTest {
    val selected = mutableListOf<String>()
    show { selected += it }
    onNodeWithTag("tab-B").performClick()
    assertEquals(listOf("B"), selected)
  }

  @Test
  fun selectedTabReportsSelected() = runComposeUiTest {
    show()
    onNodeWithTag("tab-A").assertIsSelected()
    onNodeWithTag("tab-B").assertIsNotSelected()
  }

  @Test
  fun showsContent() = runComposeUiTest {
    show()
    onNodeWithText("body").assertIsDisplayed()
  }
}
