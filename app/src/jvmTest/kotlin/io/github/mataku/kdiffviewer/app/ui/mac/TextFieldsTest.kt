package io.github.mataku.kdiffviewer.app.ui.mac

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class TextFieldsTest {
  @Test
  fun textFieldShowsPlaceholderOnlyWhileEmpty() = runComposeUiTest {
    var value by mutableStateOf("")
    setContent { MacTextField(value, { value = it }, Modifier.testTag("field"), placeholder = "Branch or revision") }
    onNodeWithText("Branch or revision").assertIsDisplayed()
    onNodeWithTag("field").performTextInput("ma")
    onNodeWithText("Branch or revision").assertDoesNotExist()
  }

  @Test
  fun searchFieldShowsPlaceholderOnlyWhileEmpty() = runComposeUiTest {
    var value by mutableStateOf("")
    setContent { MacSearchField(value, { value = it }, Modifier.testTag("search"), placeholder = "Filter") }
    onNodeWithText("Filter").assertIsDisplayed()
    onNodeWithTag("search").performTextInput("a")
    onNodeWithText("Filter").assertDoesNotExist()
  }

  @Test
  fun searchFieldClearButtonAppearsWithTextAndClears() = runComposeUiTest {
    var value by mutableStateOf("")
    setContent { MacSearchField(value, { value = it }, Modifier.testTag("search")) }
    onNodeWithTag("search-clear").assertDoesNotExist()
    onNodeWithTag("search").performTextInput("abc")
    onNodeWithTag("search-clear").assertIsDisplayed().performClick()
    onNodeWithTag("search").assertIsFocused()
    assertEquals("", value)
    onNodeWithTag("search-clear").assertDoesNotExist()
  }

  @Test
  fun escapeClearsTextAndIsConsumedOnlyWhileNonEmpty() = runComposeUiTest {
    var value by mutableStateOf("")
    var escapesReachedParent = 0
    setContent {
      Box(
        Modifier.onKeyEvent { event ->
          if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
            escapesReachedParent++
            true
          } else {
            false
          }
        },
      ) {
        MacSearchField(value, { value = it }, Modifier.testTag("search"))
      }
    }
    onNodeWithTag("search").performTextInput("abc")
    onNodeWithTag("search").performKeyInput { pressKey(Key.Escape) }
    assertEquals("", value)
    assertEquals(0, escapesReachedParent)
    onNodeWithTag("search").performKeyInput { pressKey(Key.Escape) }
    assertEquals(1, escapesReachedParent)
  }

  @Test
  fun inlineErrorShowsIconAndText() = runComposeUiTest {
    setContent { MacInlineError("Unknown revision 'x'", Modifier.testTag("err")) }
    onNodeWithTag("err").assertTextEquals("Unknown revision 'x'")
  }
}
