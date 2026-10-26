package io.github.mataku.kdiffviewer.app.review

import io.github.mataku.kdiffviewer.core.review.Anchor
import io.github.mataku.kdiffviewer.core.review.Placement
import io.github.mataku.kdiffviewer.core.review.ReviewThread
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
}
