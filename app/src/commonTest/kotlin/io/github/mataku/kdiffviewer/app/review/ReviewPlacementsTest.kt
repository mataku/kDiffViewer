package io.github.mataku.kdiffviewer.app.review

import io.github.mataku.kdiffviewer.core.review.Anchor
import io.github.mataku.kdiffviewer.core.review.LineLocation
import io.github.mataku.kdiffviewer.core.review.Placement
import io.github.mataku.kdiffviewer.core.review.ReviewThread
import io.github.mataku.kdiffviewer.core.review.Side
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

  @Test
  fun notesFilesCombinesFileThreadsAndFileDraft() {
    val thread = ReviewThread("t", Anchor.File("a"), emptyList(), false, false)
    val placements = ReviewPlacements(1L, emptyMap(), mapOf(2 to listOf(thread)), emptyList())
    val fileDraft = DraftTarget.NewThread(Anchor.File("b"), Placement.OnFile(5))
    val orphanDraft = DraftTarget.NewThread(Anchor.File("b"), Placement.Orphan)
    assertEquals(setOf(2), placements.notesFiles(null))
    assertEquals(setOf(2, 5), placements.notesFiles(fileDraft))
    assertEquals(setOf(2), placements.notesFiles(orphanDraft))
    assertEquals(setOf(2), placements.notesFiles(DraftTarget.Reply("t")))
  }

  @Test
  fun threadFilesCombinesLineAndFileThreads() {
    val thread = ReviewThread("t", Anchor.File("a"), emptyList(), false, false)
    val placements = ReviewPlacements(
      1L,
      mapOf(linePosition(3, 1, 4) to listOf(thread), linePosition(700, 0, 0) to listOf(thread)),
      mapOf(2 to listOf(thread)),
      listOf(thread),
    )
    assertEquals(setOf(2, 3, 700), placements.threadFiles())
  }

  @Test
  fun lineThreadsAreKeyedByAnchorSideAndEndLine() {
    val old = ReviewThread("o", Anchor.Lines("a", Side.Old, 4, 5, listOf("x", "y")), emptyList(), false, false)
    val new = ReviewThread("n", Anchor.Lines("a", Side.New, 7, 7, listOf("z")), emptyList(), false, false)
    val placements = buildPlacements(1L, listOf(old, new), mapOf("o" to Placement.OnLine(LineLocation(2)), "n" to Placement.OnLine(LineLocation(2))))
    assertEquals(listOf(old), placements.byLine[sidePosition(2, Side.Old, 5)])
    assertEquals(listOf(new), placements.byLine[sidePosition(2, Side.New, 7)])
    assertEquals(setOf(2), placements.threadFiles())
  }
}
