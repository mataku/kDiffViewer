package io.github.mataku.kdiffviewer.app

import io.github.mataku.kdiffviewer.app.markdown.MdBlock
import io.github.mataku.kdiffviewer.app.markdown.MdInline
import io.github.mataku.kdiffviewer.app.markdown.SourceBlock
import io.github.mataku.kdiffviewer.core.model.ChangeType
import io.github.mataku.kdiffviewer.core.model.DiffContent
import io.github.mataku.kdiffviewer.core.model.DiffLine
import io.github.mataku.kdiffviewer.core.model.FileDiff
import io.github.mataku.kdiffviewer.core.model.Hunk
import io.github.mataku.kdiffviewer.core.model.LineKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RenderedRowsTest {
  private fun ctx(old: Int, new: Int) = DiffLine(LineKind.Context, "c", old, new)

  private fun added(new: Int) = DiffLine(LineKind.Added, "a", null, new)

  private fun removed(old: Int) = DiffLine(LineKind.Removed, "r", old, null)

  private fun hunk(vararg lines: DiffLine, newStart: Int = 1, newCount: Int = 1) = Hunk(1, 1, newStart, newCount, "@@", lines.toList())

  private fun file(vararg hunks: Hunk, change: ChangeType = ChangeType.Modified) = FileDiff("a.md", "a.md", change, false, DiffContent.Text(hunks.toList()))

  private fun block(start: Int, end: Int = start) = SourceBlock(MdBlock.Paragraph(MdInline("p", emptyList())), start, end, 0, 0, null)

  private fun shapes(rows: List<DiffRow>): List<String> = rows.map {
    when (it) {
      is DiffRow.RenderedBlock -> "B${it.source.startLine}"
      is DiffRow.RemovedMarker -> "R${it.count}"
      is DiffRow.UnchangedFold -> "F${it.count}"
      else -> error("unexpected $it")
    }
  }

  private fun render(file: FileDiff, vararg blocks: SourceBlock) = renderedBodyRows(0, file, blocks.toList())

  @Test
  fun changedBlockIsShownAndOthersFold() {
    val rows = render(file(hunk(ctx(2, 2), added(3), ctx(3, 4))), block(1), block(3), block(5))
    assertEquals(listOf("F1", "B3", "F1"), shapes(rows))
  }

  @Test
  fun consecutiveFoldsMerge() {
    val rows = render(file(hunk(added(7))), block(1), block(3), block(5), block(7), block(9), block(11))
    assertEquals(listOf("F3", "B7", "F2"), shapes(rows))
  }

  @Test
  fun contextOnlyHunkDoesNotChangeBlock() {
    assertEquals(listOf("F1"), shapes(render(file(hunk(ctx(1, 1))), block(1))))
  }

  @Test
  fun replacementHasNoMarker() {
    val rows = render(file(hunk(ctx(1, 1), removed(2), added(2), ctx(3, 3))), block(1, 3))
    assertEquals(listOf("B1"), shapes(rows))
  }

  @Test
  fun pureDeletionBetweenBlocks() {
    val rows = render(file(hunk(ctx(1, 1), removed(2), removed(3), removed(4), ctx(5, 3))), block(1), block(3))
    assertEquals(listOf("F1", "R3", "F1"), shapes(rows))
    val marker = assertIs<DiffRow.RemovedMarker>(rows[1])
    assertEquals(2, marker.oldStart)
    assertEquals(4, marker.oldEnd)
    assertEquals(HunkLineRange(0, 1, 3), marker.range)
  }

  @Test
  fun deletionInsideBlockMarksBlock() {
    val rows = render(file(hunk(ctx(1, 1), removed(2), ctx(3, 2))), block(1, 2), block(4))
    assertEquals(listOf("B1", "R1", "F1"), shapes(rows))
  }

  @Test
  fun trailingDeletionComesLast() {
    val rows = render(file(hunk(ctx(1, 1), removed(2))), block(1))
    assertEquals(listOf("F1", "R1"), shapes(rows))
  }

  @Test
  fun commentRangeClipsToHunk() {
    val lines = listOf(ctx(8, 8), ctx(9, 9), added(10), ctx(10, 11), ctx(11, 12), ctx(12, 13), ctx(13, 14))
    val rows = render(file(hunk(*lines.toTypedArray())), block(2, 20))
    assertEquals(HunkLineRange(0, 0, 6), assertIs<DiffRow.RenderedBlock>(rows.single()).commentRange)
  }

  @Test
  fun multiHunkBlockUsesFirstChangedHunk() {
    val rows = render(file(hunk(ctx(1, 1)), hunk(ctx(5, 5), added(6))), block(1, 10))
    assertEquals(HunkLineRange(1, 0, 1), assertIs<DiffRow.RenderedBlock>(rows.single()).commentRange)
  }

  @Test
  fun deletionOnlyBlockWithoutHunkLinesIsNotCommentable() {
    val rows = render(file(hunk(removed(5), newStart = 4, newCount = 0)), block(3, 6))
    assertEquals(listOf("B3", "R1"), shapes(rows))
    assertNull(assertIs<DiffRow.RenderedBlock>(rows[0]).commentRange)
  }

  @Test
  fun deletionOnlyBlockUsesHunkLinesInRange() {
    val rows = render(file(hunk(ctx(1, 1), removed(2), ctx(3, 2))), block(1, 2))
    assertEquals(HunkLineRange(0, 0, 2), assertIs<DiffRow.RenderedBlock>(rows[0]).commentRange)
  }

  @Test
  fun blankAddedLinesOutsideBlocksOnlyFold() {
    val rows = render(file(hunk(ctx(1, 1), added(2), ctx(2, 3))), block(1), block(3))
    assertEquals(listOf("F2"), shapes(rows))
  }

  @Test
  fun addedFileShowsEveryBlock() {
    val rows = render(file(hunk(added(1), added(2), added(3)), change = ChangeType.Added), block(1), block(3))
    assertEquals(listOf("B1", "B3"), shapes(rows))
  }

  @Test
  fun noNewlineRowsAreIgnored() {
    val noNewline = DiffLine(LineKind.NoNewlineAtEof, "\\ No newline at end of file", null, null)
    val rows = render(file(hunk(ctx(1, 1), noNewline)), block(1))
    assertEquals(listOf("F1"), shapes(rows))
  }

  @Test
  fun removedRunSpansNoNewlineRow() {
    val noNewline = DiffLine(LineKind.NoNewlineAtEof, "\\ No newline at end of file", null, null)
    val rows = render(file(hunk(ctx(1, 1), removed(2), noNewline)), block(1))
    assertEquals(listOf("F1", "R1"), shapes(rows))
  }

  @Test
  fun keysAreUniqueWithinAFile() {
    val rows = render(file(hunk(ctx(1, 1), removed(2), ctx(3, 2), added(3))), block(1), block(2), block(3), block(5))
    assertEquals(rows.size, rows.map { it.key }.toSet().size)
  }

  @Test
  fun isRenderable() {
    val text = DiffContent.Text(listOf(hunk(added(1))))
    assertTrue(FileDiff("a.md", "a.md", ChangeType.Modified, false, text).isRenderable())
    assertTrue(FileDiff(null, "a.md", ChangeType.Added, false, text).isRenderable())
    assertTrue(FileDiff(null, "a.md", ChangeType.Added, true, text).isRenderable())
    assertFalse(FileDiff("a.md", null, ChangeType.Deleted, false, text).isRenderable())
    assertFalse(FileDiff("a.md", "a.md", ChangeType.Modified, false, DiffContent.Binary).isRenderable())
    assertFalse(FileDiff("a.md", "b.md", ChangeType.Renamed, false, DiffContent.Text(emptyList())).isRenderable())
    assertFalse(FileDiff("a.kt", "a.kt", ChangeType.Modified, false, text).isRenderable())
  }
}
