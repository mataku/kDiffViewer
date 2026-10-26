package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import io.github.mataku.kdiffviewer.core.model.DiffStats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StatsTextTest {
  private val green = TextStyle(color = Color.Green)
  private val red = TextStyle(color = Color.Red)

  @Test
  fun bothCountsAreSeparatedBySingleSpaceAndColoredIndependently() {
    val text = statsText(DiffStats(12, 3), green, red)!!
    assertEquals("+12 −3", text.text)
    assertEquals(2, text.spanStyles.size)
    assertEquals(Color.Green, text.spanStyles[0].item.color)
    assertEquals(0, text.spanStyles[0].start)
    assertEquals(3, text.spanStyles[0].end)
    assertEquals(Color.Red, text.spanStyles[1].item.color)
    assertEquals(4, text.spanStyles[1].start)
    assertEquals(6, text.spanStyles[1].end)
  }

  @Test
  fun zeroCountsAreHidden() {
    assertEquals("+40", statsText(DiffStats(40, 0), green, red)!!.text)
    assertEquals("−25", statsText(DiffStats(0, 25), green, red)!!.text)
    assertNull(statsText(DiffStats(0, 0), green, red))
  }
}
