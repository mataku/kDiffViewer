package io.github.mataku.kdiffviewer.app.review

import io.github.mataku.kdiffviewer.app.DiffRow
import io.github.mataku.kdiffviewer.app.HunkLineRange
import io.github.mataku.kdiffviewer.app.markdown.MdBlock
import io.github.mataku.kdiffviewer.app.markdown.SourceBlock
import io.github.mataku.kdiffviewer.core.review.Anchor
import io.github.mataku.kdiffviewer.core.review.Author
import io.github.mataku.kdiffviewer.core.review.Comment
import io.github.mataku.kdiffviewer.core.review.LineLocation
import io.github.mataku.kdiffviewer.core.review.Placement
import io.github.mataku.kdiffviewer.core.review.ReviewThread
import io.github.mataku.kdiffviewer.core.review.Side
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class RenderedNotesTest {
  private fun thread(id: String, side: Side, end: Int) = ReviewThread(
    id,
    Anchor.Lines("a.md", side, end, end, listOf("x")),
    listOf(Comment("c$id", Author.User, "body", Instant.fromEpochSeconds(0))),
    resolved = false,
    outdated = false,
  )

  private fun placements(fileIndex: Int, vararg threads: ReviewThread): ReviewPlacements {
    val placementById = threads.associate { it.id to Placement.OnLine(LineLocation(fileIndex)) }
    return buildPlacements(1L, threads.toList(), placementById)
  }

  private fun block(start: Int, end: Int, fileIndex: Int = 0) = DiffRow.RenderedBlock(fileIndex, start, SourceBlock(MdBlock.ThematicBreak, start, end, 0, 0, null), null)

  private fun marker(oldStart: Int, oldEnd: Int, fileIndex: Int = 0) = DiffRow.RemovedMarker(fileIndex, oldStart, HunkLineRange(0, 0, 0), oldStart, oldEnd, oldEnd - oldStart + 1)

  private fun header(): DiffRow = DiffRow.Placeholder(0, "")

  private fun draftOn(side: Side, end: Int, fileIndex: Int = 0) = DraftTarget.NewThread(Anchor.Lines("a.md", side, end, end, listOf("x")), Placement.OnLine(LineLocation(fileIndex)))

  @Test
  fun blockShowsThreadEndingInRange() {
    val placements = placements(0, thread("t", Side.New, 12))
    assertEquals(listOf("t"), placements.threadsOn(block(10, 14)).map { it.id })
    assertEquals(emptyList(), placements.threadsOn(block(15, 20)).map { it.id })
  }

  @Test
  fun oldSideThreadOnBlockIsNotShown() {
    assertEquals(emptyList(), placements(0, thread("t", Side.Old, 12)).threadsOn(block(10, 14)))
  }

  @Test
  fun markerShowsOldSideThreads() {
    val placements = placements(0, thread("old", Side.Old, 3), thread("new", Side.New, 3))
    assertEquals(listOf("old"), placements.threadsOn(marker(2, 4)).map { it.id })
  }

  @Test
  fun otherFilesThreadsAreNotShown() {
    assertEquals(emptyList(), placements(1, thread("t", Side.New, 12)).threadsOn(block(10, 14)))
  }

  @Test
  fun threadOutsideEveryRowGoesToNotes() {
    val context = thread("context", Side.New, 30)
    val old = thread("old", Side.Old, 8)
    val notes = renderedNotes(listOf(header(), block(10, 14), marker(2, 4)), listOf(0), setOf(0), placements(0, context, old), null)
    assertEquals(setOf("context", "old"), notes.threads.getValue(0).map { it.id }.toSet())
  }

  @Test
  fun threadOnRowIsNotInNotes() {
    val notes = renderedNotes(listOf(header(), block(10, 14), marker(2, 4)), listOf(0), setOf(0), placements(0, thread("a", Side.New, 12), thread("b", Side.Old, 3)), null)
    assertEquals(emptyMap(), notes.threads)
  }

  @Test
  fun draftWithNoRowGoesToNotes() {
    val rows = listOf(header(), block(10, 14))
    assertEquals(0, renderedNotes(rows, listOf(0), setOf(0), ReviewPlacements.Empty, draftOn(Side.New, 30)).draftFile)
    assertEquals(0, renderedNotes(rows, listOf(0), setOf(0), ReviewPlacements.Empty, draftOn(Side.Old, 12)).draftFile)
  }

  @Test
  fun draftOnRowIsNotInNotes() {
    assertEquals(-1, renderedNotes(listOf(header(), block(10, 14)), listOf(0), setOf(0), ReviewPlacements.Empty, draftOn(Side.New, 12)).draftFile)
  }

  @Test
  fun rawFilesAreUntouched() {
    val notes = renderedNotes(listOf(header()), listOf(0), emptySet(), placements(0, thread("t", Side.New, 30)), draftOn(Side.New, 30))
    assertEquals(emptyMap(), notes.threads)
    assertEquals(-1, notes.draftFile)
  }

  @Test
  fun secondFileSliceIsUsed() {
    val rows = listOf(header(), block(1, 1), header(), block(10, 14, fileIndex = 1))
    val notes = renderedNotes(rows, listOf(0, 2), setOf(1), placements(1, thread("in", Side.New, 12), thread("out", Side.New, 1)), null)
    assertEquals(listOf("out"), notes.threads.getValue(1).map { it.id })
  }

  @Test
  fun composesOnMatchesSideAndRange() {
    assertTrue(draftOn(Side.New, 12).composesOn(block(10, 14)))
    assertFalse(draftOn(Side.New, 15).composesOn(block(10, 14)))
    assertFalse(draftOn(Side.Old, 12).composesOn(block(10, 14)))
    assertFalse(draftOn(Side.New, 12, fileIndex = 1).composesOn(block(10, 14)))
    assertTrue(draftOn(Side.Old, 3).composesOn(marker(2, 4)))
    assertFalse(draftOn(Side.New, 3).composesOn(marker(2, 4)))
  }
}
