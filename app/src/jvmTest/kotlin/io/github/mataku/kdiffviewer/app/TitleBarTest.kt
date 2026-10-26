package io.github.mataku.kdiffviewer.app

import javax.swing.JRootPane
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TitleBarTest {
  @Test
  fun macOsMergesTitleBarIntoContent() {
    val pane = JRootPane()
    assertTrue(mergeTitleBarIntoContent(pane, "Mac OS X"))
    assertEquals(true, pane.getClientProperty("apple.awt.fullWindowContent"))
    assertEquals(true, pane.getClientProperty("apple.awt.transparentTitleBar"))
    assertEquals(false, pane.getClientProperty("apple.awt.windowTitleVisible"))
  }

  @Test
  fun otherSystemsKeepTheNativeTitleBar() {
    val pane = JRootPane()
    assertFalse(mergeTitleBarIntoContent(pane, "Linux"))
    assertNull(pane.getClientProperty("apple.awt.fullWindowContent"))
  }
}
