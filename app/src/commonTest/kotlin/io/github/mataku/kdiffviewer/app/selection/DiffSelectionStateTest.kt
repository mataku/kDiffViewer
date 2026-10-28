package io.github.mataku.kdiffviewer.app.selection

import io.github.mataku.kdiffviewer.core.model.Side
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
  fun extendKeepsSide() {
    val state = DiffSelectionState()
    state.start(TextPosition(1, 2), Side.Old)
    state.extendTo(TextPosition(3, 4))
    assertEquals(DiffSelection(TextPosition(1, 2), TextPosition(3, 4), Side.Old), state.selection)
    state.select(TextPosition(0, 0), TextPosition(2, 2))
    assertEquals(Side.Old, state.selection?.side)
    state.select(TextPosition(0, 0), TextPosition(2, 2), Side.New)
    assertEquals(Side.New, state.selection?.side)
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

  @Test
  fun resetForClearsOnlyWhenKeyChanges() {
    val state = DiffSelectionState()
    state.resetFor(1 to false)
    state.start(TextPosition(1, 2))
    state.resetFor(1 to false)
    assertEquals(DiffSelection(TextPosition(1, 2), TextPosition(1, 2)), state.selection)
    state.resetFor(2 to false)
    assertNull(state.selection)
    state.start(TextPosition(0, 0))
    state.resetFor(2 to false)
    assertEquals(DiffSelection(TextPosition(0, 0), TextPosition(0, 0)), state.selection)
  }
}
