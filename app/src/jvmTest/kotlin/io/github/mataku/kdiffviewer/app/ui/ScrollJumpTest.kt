package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.KeyInjectionScope
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.test.withKeysDown
import androidx.compose.ui.unit.dp
import io.github.mataku.kdiffviewer.app.ui.mac.MacSearchField
import io.github.mataku.kdiffviewer.app.ui.mac.MacTextArea
import io.github.mataku.kdiffviewer.app.ui.mac.MacTextField
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ScrollJumpTest {
  private fun matches(keymap: Keymap = Keymap.Mac, input: KeyInjectionScope.() -> Unit): List<ScrollJump?> {
    val seen = mutableListOf<ScrollJump?>()
    val shortcuts = AppShortcuts(keymap)
    runComposeUiTest {
      setContent {
        val focus = remember { FocusRequester() }
        Box(
          Modifier
            .size(10.dp)
            .testTag("target")
            .focusRequester(focus)
            .onKeyEvent { event ->
              scrollJumpOf(event, shortcuts)?.let(seen::add)
              false
            }
            .focusable(),
        )
        LaunchedEffect(Unit) { focus.requestFocus() }
      }
      waitForIdle()
      onNodeWithTag("target").performKeyInput(input)
    }
    return seen
  }

  @Test
  fun commandUpJumpsToTop() {
    assertEquals(listOf<ScrollJump?>(ScrollJump.Top), matches { withKeyDown(Key.MetaLeft) { pressKey(Key.DirectionUp) } })
  }

  @Test
  fun commandDownJumpsToBottom() {
    assertEquals(listOf<ScrollJump?>(ScrollJump.Bottom), matches { withKeyDown(Key.MetaLeft) { pressKey(Key.DirectionDown) } })
  }

  @Test
  fun controlHomeJumpsToTop() {
    assertEquals(listOf<ScrollJump?>(ScrollJump.Top), matches { withKeyDown(Key.CtrlLeft) { pressKey(Key.MoveHome) } })
  }

  @Test
  fun controlEndJumpsToBottom() {
    assertEquals(listOf<ScrollJump?>(ScrollJump.Bottom), matches { withKeyDown(Key.CtrlLeft) { pressKey(Key.MoveEnd) } })
  }

  @Test
  fun otherCombinationsDoNothing() {
    assertEquals(emptyList(), matches { pressKey(Key.DirectionUp) })
    assertEquals(emptyList(), matches { pressKey(Key.MoveHome) })
    assertEquals(emptyList(), matches { pressKey(Key.MoveEnd) })
    assertEquals(emptyList(), matches { withKeysDown(listOf(Key.ShiftLeft, Key.MetaLeft)) { pressKey(Key.DirectionUp) } })
    assertEquals(emptyList(), matches { withKeysDown(listOf(Key.CtrlLeft, Key.MetaLeft)) { pressKey(Key.DirectionUp) } })
    assertEquals(emptyList(), matches { withKeyDown(Key.AltLeft) { pressKey(Key.DirectionDown) } })
  }

  @Test
  fun focusedTextFieldKeepsTopJump() = runComposeUiTest {
    val outer = mutableListOf<ScrollJump>()
    setContent {
      val focus = remember { FocusRequester() }
      var text by remember { mutableStateOf("one\ntwo\nthree") }
      Box(
        Modifier.onKeyEvent { event ->
          scrollJumpOf(event)?.let(outer::add)
          false
        },
      ) {
        BasicTextField(text, { text = it }, Modifier.size(200.dp).testTag("field").focusRequester(focus))
      }
      LaunchedEffect(Unit) { focus.requestFocus() }
    }
    waitForIdle()
    onNodeWithTag("field").performKeyInput { hostTopJump() }
    waitForIdle()
    assertFalse(ScrollJump.Top in outer)
  }

  @Test
  fun standardKeymapIgnoresCommandArrows() {
    assertEquals(emptyList(), matches(Keymap.Standard) { withKeyDown(Key.MetaLeft) { pressKey(Key.DirectionUp) } })
    assertEquals(emptyList(), matches(Keymap.Standard) { withKeyDown(Key.MetaLeft) { pressKey(Key.DirectionDown) } })
  }

  @Test
  fun standardKeymapUsesControlHomeAndEnd() {
    assertEquals(listOf<ScrollJump?>(ScrollJump.Top), matches(Keymap.Standard) { withKeyDown(Key.CtrlLeft) { pressKey(Key.MoveHome) } })
    assertEquals(listOf<ScrollJump?>(ScrollJump.Bottom), matches(Keymap.Standard) { withKeyDown(Key.CtrlLeft) { pressKey(Key.MoveEnd) } })
  }

  @Test
  fun requestNeverBlocks() {
    val jumps = ScrollJumps()
    assertTrue(jumps.request(ScrollJump.Top))
    assertTrue(jumps.request(ScrollJump.Bottom))
  }

  private fun KeyInjectionScope.hostTopJump() = if (HostKeymap == Keymap.Mac) {
    withKeyDown(Key.MetaLeft) { pressKey(Key.DirectionUp) }
  } else {
    withKeyDown(Key.CtrlLeft) { pressKey(Key.MoveHome) }
  }

  private val jumpKeys: List<KeyInjectionScope.() -> Unit> = buildList<KeyInjectionScope.() -> Unit> {
    if (HostKeymap == Keymap.Mac) {
      add { withKeyDown(Key.MetaLeft) { pressKey(Key.DirectionUp) } }
      add { withKeyDown(Key.MetaLeft) { pressKey(Key.DirectionDown) } }
    }
    add { withKeyDown(Key.CtrlLeft) { pressKey(Key.MoveHome) } }
    add { withKeyDown(Key.CtrlLeft) { pressKey(Key.MoveEnd) } }
  }

  private fun leaksFrom(control: @Composable () -> Unit): List<ScrollJump> {
    val outer = mutableListOf<ScrollJump>()
    runComposeUiTest {
      setContent {
        KdvTheme(colorTheme = ColorTheme.Light) {
          Box(
            Modifier.onKeyEvent { event ->
              scrollJumpOf(event)?.let(outer::add)
              false
            },
          ) { control() }
        }
      }
      val field = onNode(hasSetTextAction())
      field.performClick()
      waitForIdle()
      for (keys in jumpKeys) field.performKeyInput(keys)
      waitForIdle()
    }
    return outer
  }

  @Test
  fun macTextFieldKeepsAllJumpKeys() {
    assertEquals(emptyList(), leaksFrom { MacTextField("one", {}) })
  }

  @Test
  fun macSearchFieldKeepsAllJumpKeys() {
    assertEquals(emptyList(), leaksFrom { MacSearchField("one", {}) })
  }

  @Test
  fun macTextAreaKeepsAllJumpKeys() {
    assertEquals(emptyList(), leaksFrom { MacTextArea("one\ntwo", {}) })
  }

  @Test
  fun keepScrollJumpsStopsJumpKeysFromAFocusedElement() = runComposeUiTest {
    val outer = mutableListOf<ScrollJump>()
    setContent {
      val focus = remember { FocusRequester() }
      Box(
        Modifier.onKeyEvent { event ->
          scrollJumpOf(event)?.let(outer::add)
          false
        },
      ) {
        Box(Modifier.size(10.dp).testTag("pane").focusRequester(focus).keepScrollJumps().focusable())
      }
      LaunchedEffect(Unit) { focus.requestFocus() }
    }
    waitForIdle()
    for (keys in jumpKeys) onNodeWithTag("pane").performKeyInput(keys)
    waitForIdle()
    assertEquals(emptyList(), outer)
  }
}
