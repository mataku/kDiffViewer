package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.ui.unit.dp
import io.github.mataku.kdiffviewer.app.DiffMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ViewerScreenStateTest {
  @Test
  fun initialTreeWidthIsClamped() {
    assertEquals(MinTreeWidth, ViewerScreenState(treeWidth = 10.dp).treeWidth)
    assertEquals(MaxTreeWidth, ViewerScreenState(treeWidth = 2000.dp).treeWidth)
  }

  @Test
  fun resizeTreeStaysWithinBounds() {
    val state = ViewerScreenState(treeWidth = 300.dp)
    state.resizeTree(50.dp)
    assertEquals(350.dp, state.treeWidth)
    state.resizeTree(-1000.dp)
    assertEquals(MinTreeWidth, state.treeWidth)
    state.resizeTree(1000.dp)
    assertEquals(MaxTreeWidth, state.treeWidth)
  }

  @Test
  fun changeModeAndToggleSidebar() {
    val state = ViewerScreenState()
    state.changeMode(DiffMode.Split)
    assertEquals(DiffMode.Split, state.mode)
    state.toggleSidebar()
    assertFalse(state.sidebarVisible)
    state.toggleSidebar()
    assertTrue(state.sidebarVisible)
  }
}
