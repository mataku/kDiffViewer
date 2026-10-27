package io.github.mataku.kdiffviewer.app.selection

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DiffSelectionStateTest {
  @Test
  fun startCreatesEmptySelection() {
    val state = DiffSelectionState()
    state.start(TextPosition(1, 2))
    assertEquals(DiffSelection(TextPosition(1, 2), TextPosition(1, 2)), state.selection)
  }

  @Test
  fun extendToMovesFocusOnly() {
    val state = DiffSelectionState()
    state.start(TextPosition(1, 2))
    state.extendTo(TextPosition(3, 4))
    assertEquals(DiffSelection(TextPosition(1, 2), TextPosition(3, 4)), state.selection)
  }

  @Test
  fun extendToWithoutSelectionStarts() {
    val state = DiffSelectionState()
    state.extendTo(TextPosition(3, 4))
    assertEquals(DiffSelection(TextPosition(3, 4), TextPosition(3, 4)), state.selection)
  }

  @Test
  fun selectSetsAnchorAndFocus() {
    val state = DiffSelectionState()
    state.select(TextPosition(0, 1), TextPosition(2, 3))
    assertEquals(DiffSelection(TextPosition(0, 1), TextPosition(2, 3)), state.selection)
  }

  @Test
  fun clearRemovesSelection() {
    val state = DiffSelectionState()
    state.start(TextPosition(1, 2))
    state.clear()
    assertNull(state.selection)
  }
}
