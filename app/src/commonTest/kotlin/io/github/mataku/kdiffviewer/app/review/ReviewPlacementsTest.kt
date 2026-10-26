package io.github.mataku.kdiffviewer.app.review

import kotlin.test.Test
import kotlin.test.assertEquals

class ReviewPlacementsTest {
  @Test
  fun linePositionKeepsDistinctPositionsDistinct() {
    val positions = listOf(
      linePosition(1, 0, 0),
      linePosition(0, 1, 0),
      linePosition(0, 0, 1),
      linePosition(0, 1_048_575, 1_048_575),
    )
    assertEquals(positions.size, positions.toSet().size)
  }
}
