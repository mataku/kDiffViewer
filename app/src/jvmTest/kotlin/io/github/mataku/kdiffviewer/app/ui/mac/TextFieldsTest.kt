package io.github.mataku.kdiffviewer.app.ui.mac

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class TextFieldsTest {
  fun textFieldShowsPlaceholderOnlyWhileEmpty() = runComposeUiTest {
    var value by mutableStateOf("")
    setContent { MacTextField(value, { value = it }, Modifier.testTag("field"), placeholder = "Branch or revision") }
    onNodeWithText("Branch or revision").assertIsDisplayed()
    onNodeWithTag("field").performTextInput("ma")
    onNodeWithText("Branch or revision").assertDoesNotExist()
  }

  @Test
  fun inlineErrorShowsIconAndText() = runComposeUiTest {
    setContent { MacInlineError("Unknown revision 'x'", Modifier.testTag("err")) }
    onNodeWithTag("err").assertTextEquals("Unknown revision 'x'")
  }
}
