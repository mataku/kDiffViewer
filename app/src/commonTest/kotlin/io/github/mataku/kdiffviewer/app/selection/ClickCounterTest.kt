package io.github.mataku.kdiffviewer.app.selection

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals

class ClickCounterTest {
  private val origin = Offset(10f, 10f)

  @Test
  fun singleDoubleTriple() {
    val counter = ClickCounter(500, 4f)
    assertEquals(1, counter.onPress(0, origin))
    assertEquals(2, counter.onPress(100, origin))
    assertEquals(3, counter.onPress(200, origin))
  }

  @Test
  fun fourthClickWrapsToOne() {
    val counter = ClickCounter(500, 4f)
    counter.onPress(0, origin)
    counter.onPress(100, origin)
    counter.onPress(200, origin)
    assertEquals(1, counter.onPress(300, origin))
  }

  @Test
  fun slowClickResets() {
    val counter = ClickCounter(500, 4f)
    counter.onPress(0, origin)
    assertEquals(1, counter.onPress(501, origin))
  }

  @Test
  fun distantClickResets() {
    val counter = ClickCounter(500, 4f)
    counter.onPress(0, origin)
    assertEquals(1, counter.onPress(100, Offset(20f, 10f)))
  }
}
