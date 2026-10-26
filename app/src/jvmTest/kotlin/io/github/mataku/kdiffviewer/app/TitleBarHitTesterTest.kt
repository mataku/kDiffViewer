package io.github.mataku.kdiffviewer.app

import androidx.compose.ui.geometry.Rect
import io.github.mataku.kdiffviewer.app.ui.TitleBarRegions
import java.awt.event.MouseEvent
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals

class TitleBarHitTesterTest {
  private val source = JPanel()
  private val regions = TitleBarRegions().apply { update("reload", Rect(200f, 20f, 300f, 60f)) }
  private val results = mutableListOf<Boolean>()
  private val tester = TitleBarHitTester(regions, scale = { 2.0 }) { results += it }

  private fun move(x: Int, y: Int) = MouseEvent(source, MouseEvent.MOUSE_MOVED, 0L, 0, x, y, 0, false)

  @Test
  fun pointOverRegisteredRegionIsClientArea() {
    tester.mouseMoved(move(120, 20))
    assertEquals(listOf(true), results)
  }

  @Test
  fun pointOutsideRegionsIsTitleBar() {
    tester.mouseMoved(move(60, 20))
    assertEquals(listOf(false), results)
  }
}
