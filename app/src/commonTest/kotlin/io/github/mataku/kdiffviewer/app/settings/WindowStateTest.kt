package io.github.mataku.kdiffviewer.app.settings

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WindowStateTest {
  private val min = DpSize(640.dp, 400.dp)
  private val max = DpSize(1800.dp, 1000.dp)

  @Test
  fun roundTrips() {
    val state = WindowState(mainWidth = 1200, mainHeight = 800)

    assertEquals(state, WindowStateCodec.decode(WindowStateCodec.encode(state)))
  }

  @Test
  fun missingTextMeansNoSize() {
    assertEquals(WindowState(), WindowStateCodec.decode(null))
  }

  @Test
  fun junkMeansNoSize() {
    assertEquals(WindowState(), WindowStateCodec.decode("{"))
    assertEquals(WindowState(), WindowStateCodec.decode("[1,2]"))
    assertEquals(WindowState(), WindowStateCodec.decode("""{"mainWidth":"wide","mainHeight":"tall"}"""))
    assertNull(WindowStateCodec.decode("garbage").mainSizeOrNull(min, max))
  }

  @Test
  fun sizeWithinBoundsIsKept() {
    assertEquals(DpSize(1200.dp, 800.dp), WindowState(1200, 800).mainSizeOrNull(min, max))
  }

  @Test
  fun sizeIsClampedToBounds() {
    assertEquals(DpSize(640.dp, 400.dp), WindowState(100, 50).mainSizeOrNull(min, max))
    assertEquals(DpSize(1800.dp, 1000.dp), WindowState(5000, 4000).mainSizeOrNull(min, max))
  }

  @Test
  fun missingValueMeansNoSize() {
    assertNull(WindowState(mainWidth = 1200).mainSizeOrNull(min, max))
    assertNull(WindowState(mainHeight = 800).mainSizeOrNull(min, max))
    assertNull(WindowState().mainSizeOrNull(min, max))
  }
}
