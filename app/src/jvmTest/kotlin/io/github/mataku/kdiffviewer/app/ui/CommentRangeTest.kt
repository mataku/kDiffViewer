package io.github.mataku.kdiffviewer.app.ui

import io.github.mataku.kdiffviewer.app.review.DraftTarget
import io.github.mataku.kdiffviewer.core.model.ChangeType
import io.github.mataku.kdiffviewer.core.model.DiffContent
import io.github.mataku.kdiffviewer.core.model.DiffLine
import io.github.mataku.kdiffviewer.core.model.FileDiff
import io.github.mataku.kdiffviewer.core.model.Hunk
import io.github.mataku.kdiffviewer.core.model.LineKind
import io.github.mataku.kdiffviewer.core.model.Side
import io.github.mataku.kdiffviewer.core.review.Anchor
import io.github.mataku.kdiffviewer.core.review.LineLocation
import io.github.mataku.kdiffviewer.core.review.Placement
import io.github.mataku.kdiffviewer.core.review.reanchor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import io.github.mataku.kdiffviewer.core.review.Side as ReviewSide

class CommentRangeTest {
  private fun file(lines: List<DiffLine>): FileDiff = FileDiff("a.kt", "a.kt", ChangeType.Modified, false, DiffContent.Text(listOf(Hunk(1, 1, 1, 1, "", lines))))

  private val files = listOf(
    file(
      listOf(
        DiffLine(LineKind.Context, "a", 1, 1),
        DiffLine(LineKind.Removed, "r", 2, null),
        DiffLine(LineKind.Added, "n1", null, 2),
        DiffLine(LineKind.Added, "n2", null, 3),
      ),
    ),
  )

  private val multiLine = DraftTarget.NewThread(
    Anchor.Lines("a.kt", ReviewSide.New, 1, 3, listOf("a", "n1", "n2")),
    Placement.OnLine(LineLocation(0, 0, 3)),
  )

  @Test
  fun singleLineDraftHasNoRange() {
    val target = DraftTarget.NewThread(
      Anchor.Lines("a.kt", ReviewSide.New, 2, 2, listOf("n1")),
      Placement.OnLine(LineLocation(0, 0, 2)),
    )
    assertNull(draftRange(target, files, split = false))
  }

  @Test
  fun fileDraftHasNoRange() {
    val target = DraftTarget.NewThread(Anchor.File("a.kt"), Placement.OnFile(0))
    assertNull(draftRange(target, files, split = false))
    assertNull(draftRange(null, files, split = false))
  }

  @Test
  fun multiLineDraftMapsToLineIndices() {
    assertEquals(CommentRange(0, 0, null, 0, 3), draftRange(multiLine, files, split = false))
  }

  @Test
  fun splitDraftCarriesViewSide() {
    assertEquals(Side.New, draftRange(multiLine, files, split = true)?.side)
  }

  @Test
  fun draftRangeFollowsReanchoredTarget() {
    val shifted = listOf(
      file(
        listOf(
          DiffLine(LineKind.Added, "i1", null, 1),
          DiffLine(LineKind.Added, "i2", null, 2),
          DiffLine(LineKind.Context, "a", 1, 3),
          DiffLine(LineKind.Removed, "r", 2, null),
          DiffLine(LineKind.Added, "n1", null, 4),
          DiffLine(LineKind.Added, "n2", null, 5),
        ),
      ),
    )
    val (anchor, placement) = reanchor(multiLine.anchor, shifted)
    val range = draftRange(DraftTarget.NewThread(anchor, placement), shifted, split = false)
    assertEquals(CommentRange(0, 0, null, 2, 5), range)
  }

  @Test
  fun draggingWinsOverDrafted() {
    val state = CommentRangeState()
    state.drafted = CommentRange(0, 0, null, 0, 1)
    assertTrue(state.highlights(0, 0, 1, null))
    state.dragging = CommentRange(0, 0, null, 2, 3)
    assertTrue(state.highlights(0, 0, 3, null))
    assertFalse(state.highlights(0, 0, 1, null))
  }

  @Test
  fun containsRespectsSide() {
    val unified = CommentRange(0, 0, null, 0, 2)
    assertFalse(unified.contains(0, 0, 1, Side.Old))
    assertTrue(unified.contains(0, 0, 1, null))
    val split = CommentRange(0, 0, Side.Old, 2, 0)
    assertFalse(split.contains(0, 0, 1, Side.New))
    assertTrue(split.contains(0, 0, 1, Side.Old))
  }
}
