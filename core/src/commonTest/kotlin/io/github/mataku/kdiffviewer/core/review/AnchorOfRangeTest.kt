package io.github.mataku.kdiffviewer.core.review

import io.github.mataku.kdiffviewer.core.model.DiffLine
import io.github.mataku.kdiffviewer.core.model.LineKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AnchorOfRangeTest {
  private fun mixed() = modified(
    "a.kt",
    hunk(ctx("a", 1, 1), removed("r1", 2), removed("r2", 3), added("n1", 2), added("n2", 3), ctx("z", 4, 4)),
  )

  private fun noNewline() = modified("a.kt", hunk(added("y", 1), DiffLine(LineKind.NoNewlineAtEof, "\\ No newline at end of file", null, null)))

  @Test
  fun addedStartCollectsNewSideOnly() {
    val result = anchorOfRange(mixed(), 0, 0, 3, 5)!!
    assertEquals(linesAnchor("a.kt", Side.New, 2, 4, "n1", "n2", "z"), result.anchor)
    assertEquals(LineLocation(0, 0, 5), result.location)
  }

  @Test
  fun removedStartSkipsAddedLines() {
    val result = anchorOfRange(mixed(), 0, 0, 1, 5)!!
    assertEquals(linesAnchor("a.kt", Side.Old, 2, 4, "r1", "r2", "z"), result.anchor)
    assertEquals(LineLocation(0, 0, 5), result.location)
  }

  @Test
  fun contextStartTakesFirstNonContextSideTowardTo() {
    val result = anchorOfRange(mixed(), 0, 0, 0, 4)!!
    assertEquals(linesAnchor("a.kt", Side.Old, 1, 3, "a", "r1", "r2"), result.anchor)
    assertEquals(LineLocation(0, 0, 2), result.location)
  }

  @Test
  fun contextStartTowardAddedUpwardTakesNewSide() {
    val file = modified("a.kt", hunk(ctx("a", 1, 1), ctx("b", 2, 2), added("c", 3)))
    val result = anchorOfRange(file, 0, 0, 1, 2)!!
    assertEquals(Side.New, result.anchor.side)
    assertEquals(linesAnchor("a.kt", Side.New, 2, 3, "b", "c"), result.anchor)
  }

  @Test
  fun allContextUsesNewSide() {
    val file = modified("a.kt", hunk(ctx("a", 5, 6), ctx("b", 6, 7)))
    val result = anchorOfRange(file, 0, 0, 0, 1)!!
    assertEquals(linesAnchor("a.kt", Side.New, 6, 7, "a", "b"), result.anchor)
  }

  @Test
  fun explicitSideOverridesStart() {
    val result = anchorOfRange(mixed(), 0, 0, 0, 5, Side.Old)!!
    assertEquals(linesAnchor("a.kt", Side.Old, 1, 4, "a", "r1", "r2", "z"), result.anchor)
  }

  @Test
  fun reversedIndicesGiveSameAnchor() {
    assertEquals(anchorOfRange(mixed(), 0, 0, 5, 3), anchorOfRange(mixed(), 0, 0, 3, 5))
  }

  @Test
  fun indicesClampToHunk() {
    assertEquals(anchorOfRange(mixed(), 0, 0, 3, 5), anchorOfRange(mixed(), 0, 0, 3, 99))
  }

  @Test
  fun noNewlineStartIsNull() {
    assertNull(anchorOfRange(noNewline(), 0, 0, 1, 0))
  }

  @Test
  fun noNewlineInsideRangeIsSkipped() {
    val result = anchorOfRange(noNewline(), 0, 0, 0, 1)!!
    assertEquals(linesAnchor("a.kt", Side.New, 1, 1, "y"), result.anchor)
  }

  @Test
  fun singleLineMatchesAnchorOf() {
    val file = mixed()
    assertEquals(anchorOf(file, 0, 3), anchorOfRange(file, 0, 0, 3, 3)!!.anchor)
  }

  @Test
  fun missingHunkIsNull() {
    assertNull(anchorOfRange(mixed(), 0, 4, 0, 1))
  }
}
