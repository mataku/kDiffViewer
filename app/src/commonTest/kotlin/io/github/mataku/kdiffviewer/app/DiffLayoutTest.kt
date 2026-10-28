package io.github.mataku.kdiffviewer.app

import io.github.mataku.kdiffviewer.core.model.ChangeType
import io.github.mataku.kdiffviewer.core.model.DiffContent
import io.github.mataku.kdiffviewer.core.model.DiffLine
import io.github.mataku.kdiffviewer.core.model.FileDiff
import io.github.mataku.kdiffviewer.core.model.Hunk
import io.github.mataku.kdiffviewer.core.model.LineKind
import io.github.mataku.kdiffviewer.core.model.RepoPath
import io.github.mataku.kdiffviewer.core.model.Side
import io.github.mataku.kdiffviewer.core.model.displayPath
import kotlin.test.Test
import kotlin.test.assertEquals

class DiffLayoutTest {
  private fun file(path: String, lines: Int) = FileDiff(
    path,
    path,
    ChangeType.Modified,
    false,
    DiffContent.Text(listOf(Hunk(1, lines, 1, lines, "", (1..lines).map { DiffLine(LineKind.Context, "$it", it, it) }))),
  )

  private fun state(files: List<FileDiff>, collapsedIndices: Set<Int> = emptySet()): ViewerUiState {
    val rows = flattenRows(files)
    val headers = rows.mapIndexedNotNull { index, row -> if (row is DiffRow.FileHeader) index else null }
    val fingerprints = files.map { it.fingerprint() }
    val collapsed = collapsedIndices.map { CollapsedFile(files[it].displayPath, fingerprints[it]) }.toSet()
    return ViewerUiState(RepoPath("/repo"), LoadStatus.Done, files, rows, headers, fingerprints = fingerprints, collapsed = collapsed, split = splitRowsOf(rows))
  }

  private fun changeFile(path: String, vararg lines: DiffLine) = FileDiff(path, path, ChangeType.Modified, false, DiffContent.Text(listOf(Hunk(1, 1, 1, 1, "", lines.toList()))))

  private val a = file("a.kt", 3)
  private val b = file("b.kt", 10)
  private val c = file("c.kt", 2)

  @Test
  fun nothingCollapsedMatchesRowIndices() {
    val layout = diffLayoutOf(state(listOf(a, b)))
    assertEquals(5, layout.headerVisibleIndex(1))
    assertEquals(17, layout.visibleCount)
    for (index in 0 until 17) {
      assertEquals(index, layout.rowIndexOf(index))
      assertEquals(index, layout.visibleIndexOf(index))
    }
  }

  @Test
  fun collapsingShiftsOnlyLaterFiles() {
    val layout = diffLayoutOf(state(listOf(a, b, c), setOf(1)))
    assertEquals(listOf(0, 5, 6), (0 until 3).map(layout::headerVisibleIndex))
    assertEquals(10, layout.visibleCount)
    assertEquals(17, layout.rowIndexOf(6))
    assertEquals(18, layout.rowIndexOf(7))
    assertEquals(5, layout.visibleIndexOf(10))
    assertEquals(7, layout.visibleIndexOf(18))
    assertEquals(2, layout.fileIndexOfVisible(6))
    assertEquals(1, layout.fileIndexOfVisible(5))
    assertEquals(true, layout.isCollapsed(1))
    assertEquals(false, layout.isCollapsed(0))
  }

  @Test
  fun headerOnlyFileHasNoBody() {
    val empty = FileDiff("e", "e", ChangeType.Modified, false, DiffContent.Text(emptyList()))
    val expanded = diffLayoutOf(state(listOf(empty, a)))
    val collapsed = diffLayoutOf(state(listOf(empty, a), setOf(0)))
    assertEquals(0, expanded.bodyRowCount(0))
    assertEquals(expanded.visibleCount, collapsed.visibleCount)
    assertEquals(1, collapsed.headerVisibleIndex(1))
  }

  @Test
  fun placeholderFileHasOneBodyRow() {
    val binary = FileDiff(null, "x.bin", ChangeType.Added, false, DiffContent.Binary)
    assertEquals(1, diffLayoutOf(state(listOf(binary))).bodyRowCount(0))
  }

  @Test
  fun layoutOfAppendedFilesExtendsTheEarlierOne() {
    val before = diffLayoutOf(state(listOf(a, b), setOf(0)))
    val after = diffLayoutOf(state(listOf(a, b, c), setOf(0)))
    for (fileIndex in 0 until 2) {
      assertEquals(before.headerVisibleIndex(fileIndex), after.headerVisibleIndex(fileIndex))
      assertEquals(before.bodyRowCount(fileIndex), after.bodyRowCount(fileIndex))
    }
  }

  @Test
  fun emptyState() {
    val layout = diffLayoutOf(ViewerUiState())
    assertEquals(0, layout.visibleCount)
    assertEquals(0, layout.fileCount)
  }

  @Test
  fun notesItemFollowsItsHeaderAndShiftsLaterFiles() {
    val layout = diffLayoutOf(state(listOf(a, b, c)), notesFiles = setOf(1))
    assertEquals(listOf(0, 5, 18), (0 until 3).map(layout::headerVisibleIndex))
    assertEquals(22, layout.visibleCount)
    assertEquals(true, layout.hasNotes(1))
    assertEquals(false, layout.hasNotes(0))
    assertEquals(1, layout.fileIndexOfVisible(6))
    assertEquals(layout.headerRowIndex(1), layout.rowIndexOf(6))
    assertEquals(layout.headerRowIndex(1) + 1, layout.rowIndexOf(7))
    assertEquals(7, layout.visibleIndexOf(layout.headerRowIndex(1) + 1))
  }

  @Test
  fun collapsingRemovesTheNotesItem() {
    val layout = diffLayoutOf(state(listOf(a, b, c), setOf(1)), notesFiles = setOf(1))
    assertEquals(false, layout.hasNotes(1))
    assertEquals(listOf(0, 5, 6), (0 until 3).map(layout::headerVisibleIndex))
    assertEquals(10, layout.visibleCount)
  }

  @Test
  fun orphanItemShiftsEveryIndexByOne() {
    val plain = diffLayoutOf(state(listOf(a, b)))
    val layout = diffLayoutOf(state(listOf(a, b)), hasOrphans = true)
    assertEquals(true, layout.hasOrphanItem)
    assertEquals(false, plain.hasOrphanItem)
    assertEquals(plain.visibleCount + 1, layout.visibleCount)
    for (fileIndex in 0 until 2) {
      assertEquals(plain.headerVisibleIndex(fileIndex) + 1, layout.headerVisibleIndex(fileIndex))
    }
    assertEquals(0, layout.fileIndexOfVisible(0))
    assertEquals(0, layout.rowIndexOf(0))
    assertEquals(0, layout.rowIndexOf(1))
  }

  @Test
  fun lineRowsRoundTripWithNotesAndOrphans() {
    val ui = state(listOf(a, b, c), setOf(2))
    val layout = diffLayoutOf(ui, notesFiles = setOf(0, 1, 2), hasOrphans = true)
    for (row in ui.rows.indices) {
      val file = layout.fileIndexOfVisible(layout.visibleIndexOf(row))
      if (layout.isCollapsed(file) && row != layout.headerRowIndex(file)) continue
      assertEquals(row, layout.rowIndexOf(layout.visibleIndexOf(row)))
    }
  }

  @Test
  fun scrollAnchorSurvivesNotesAndOrphans() {
    val ui = state(listOf(a, b))
    val layout = diffLayoutOf(ui, notesFiles = setOf(0, 1), hasOrphans = true)
    val visible = layout.visibleIndexOf(layout.headerRowIndex(1) + 3)
    assertEquals(ScrollAnchor("b.kt", 3), scrollAnchorOf(ui, layout, ui.displayRows(DiffMode.Unified), visible))
    assertEquals(visible, restoreTargetOf(ScrollAnchor("b.kt", 3), ui, layout, ui.displayRows(DiffMode.Unified)))
    assertEquals(ScrollAnchor("a.kt", 0), scrollAnchorOf(ui, layout, ui.displayRows(DiffMode.Unified), 0))
  }

  @Test
  fun zeroFilesWithOrphansNeverThrows() {
    val ui = ViewerUiState()
    val layout = diffLayoutOf(ui, hasOrphans = true)
    assertEquals(1, layout.visibleCount)
    assertEquals(-1, layout.fileIndexOfVisible(0))
    assertEquals(-1, layout.rowIndexOf(0))
    assertEquals(-1, layout.visibleIndexOf(0))
    assertEquals(null, scrollAnchorOf(ui, layout, ui.displayRows(DiffMode.Unified), 0))
    assertEquals(null, restoreTargetOf(ScrollAnchor("a.kt", 0), ui, layout, ui.displayRows(DiffMode.Unified)))
  }

  @Test
  fun orphansWithFirstFileCollapsed() {
    val layout = diffLayoutOf(state(listOf(a, b), setOf(0)), notesFiles = setOf(0), hasOrphans = true)
    assertEquals(1, layout.headerVisibleIndex(0))
    assertEquals(2, layout.headerVisibleIndex(1))
    assertEquals(false, layout.hasNotes(0))
    assertEquals(0, layout.fileIndexOfVisible(0))
    assertEquals(0, layout.rowIndexOf(0))
    assertEquals(1, layout.visibleIndexOf(3))
  }

  @Test
  fun lastFileWithNotes() {
    val ui = state(listOf(a, b))
    val layout = diffLayoutOf(ui, notesFiles = setOf(1))
    assertEquals(18, layout.visibleCount)
    val last = layout.visibleCount - 1
    assertEquals(1, layout.fileIndexOfVisible(last))
    assertEquals(ui.rows.size - 1, layout.rowIndexOf(last))
    assertEquals(last, layout.visibleIndexOf(ui.rows.size - 1))
  }

  @Test
  fun headerOnlyFileWithNotes() {
    val empty = FileDiff("e", "e", ChangeType.Modified, false, DiffContent.Text(emptyList()))
    val ui = state(listOf(empty, a))
    val layout = diffLayoutOf(ui, notesFiles = setOf(0))
    assertEquals(2, layout.headerVisibleIndex(1))
    assertEquals(0, layout.fileIndexOfVisible(1))
    assertEquals(0, layout.rowIndexOf(1))
    assertEquals(0, layout.visibleIndexOf(0))
    assertEquals(2, layout.visibleIndexOf(1))
  }

  @Test
  fun fileIndexOfNotesItemIsItsFile() {
    val layout = diffLayoutOf(state(listOf(a, b)), notesFiles = setOf(0, 1))
    assertEquals(0, layout.fileIndexOfVisible(1))
    assertEquals(1, layout.fileIndexOfVisible(layout.headerVisibleIndex(1) + 1))
  }

  @Test
  fun endItemFollowsEveryFile() {
    val plain = diffLayoutOf(state(listOf(a, b)))
    val layout = diffLayoutOf(state(listOf(a, b)), hasEnd = true)
    assertEquals(true, layout.hasEndItem)
    assertEquals(false, plain.hasEndItem)
    assertEquals(plain.visibleCount + 1, layout.visibleCount)
    for (fileIndex in 0 until 2) assertEquals(plain.headerVisibleIndex(fileIndex), layout.headerVisibleIndex(fileIndex))
    for (index in 0 until plain.visibleCount) assertEquals(plain.rowIndexOf(index), layout.rowIndexOf(index))
    assertEquals(state(listOf(a, b)).rows.size, layout.rowIndexOf(layout.visibleCount - 1))
    assertEquals(1, layout.fileIndexOfVisible(layout.visibleCount - 1))
  }

  @Test
  fun endItemAfterCollapsedLastFile() {
    val layout = diffLayoutOf(state(listOf(a, b), setOf(1)), hasEnd = true)
    assertEquals(layout.headerVisibleIndex(1) + 2, layout.visibleCount)
    assertEquals(state(listOf(a, b)).rows.size, layout.rowIndexOf(layout.visibleCount - 1))
  }

  @Test
  fun endItemWithOrphansAndNotes() {
    val layout = diffLayoutOf(state(listOf(a, b)), notesFiles = setOf(1), hasOrphans = true, hasEnd = true)
    assertEquals(state(listOf(a, b)).rows.size, layout.rowIndexOf(layout.visibleCount - 1))
    assertEquals(layout.visibleCount - 2, layout.visibleIndexOf(state(listOf(a, b)).rows.size - 1))
  }

  @Test
  fun endItemAloneOnEmptyState() {
    val layout = diffLayoutOf(state(emptyList()), hasEnd = true)
    assertEquals(1, layout.visibleCount)
    assertEquals(-1, layout.rowIndexOf(0))
  }

  @Test
  fun lastVisibleIndexEndsEachFile() {
    val layout = diffLayoutOf(state(listOf(a, b, c)))
    assertEquals(layout.headerVisibleIndex(1) - 1, layout.lastVisibleIndex(0))
    assertEquals(layout.headerVisibleIndex(2) - 1, layout.lastVisibleIndex(1))
    assertEquals(layout.visibleCount - 1, layout.lastVisibleIndex(2))
  }

  @Test
  fun lastVisibleIndexSkipsEndItem() {
    val layout = diffLayoutOf(state(listOf(a, b)), hasEnd = true)
    assertEquals(layout.visibleCount - 2, layout.lastVisibleIndex(1))
  }

  @Test
  fun lastVisibleIndexOfCollapsedFileIsItsHeader() {
    val layout = diffLayoutOf(state(listOf(a, b), setOf(0)))
    assertEquals(layout.headerVisibleIndex(0), layout.lastVisibleIndex(0))
  }

  @Test
  fun lastVisibleIndexIncludesNotesItem() {
    val plain = diffLayoutOf(state(listOf(a, b)))
    val layout = diffLayoutOf(state(listOf(a, b)), notesFiles = setOf(0))
    assertEquals(plain.lastVisibleIndex(0) + 1, layout.lastVisibleIndex(0))
  }

  private val replaced = changeFile("r.kt", DiffLine(LineKind.Removed, "a", 1, null), DiffLine(LineKind.Added, "b", null, 1))
  private val plain = changeFile("p.kt", DiffLine(LineKind.Context, "x", 1, 1))

  @Test
  fun splitLayoutCountsSplitRows() {
    val layout = diffLayoutOf(state(listOf(replaced, plain)), DiffMode.Split)
    assertEquals(2 + 2 + 2, layout.visibleCount)
    assertEquals(3, layout.headerVisibleIndex(1))
  }

  @Test
  fun splitLayoutWithCollapsedFile() {
    val ui = state(listOf(replaced, plain), setOf(0))
    val layout = diffLayoutOf(ui, DiffMode.Split)
    assertEquals(1, layout.headerVisibleIndex(1))
    assertEquals(ui.split.splitIndexOf(ui.fileHeaderRowIndex[1]), layout.rowIndexOf(1))
  }

  @Test
  fun splitLayoutWithNotesAndOrphans() {
    val ui = state(listOf(replaced, plain))
    val plainLayout = diffLayoutOf(ui, DiffMode.Split)
    val layout = diffLayoutOf(ui, DiffMode.Split, notesFiles = setOf(0, 1), hasOrphans = true, hasEnd = true)
    assertEquals(plainLayout.visibleCount + 4, layout.visibleCount)
    assertEquals(plainLayout.headerVisibleIndex(1) + 2, layout.headerVisibleIndex(1))
    assertEquals(ui.split.size, layout.rowIndexOf(layout.visibleCount - 1))
    assertEquals(layout.headerRowIndex(0), layout.rowIndexOf(0))
  }

  @Test
  fun displayRowsMapBetweenUnifiedAndSplit() {
    val ui = state(listOf(replaced, plain))
    val unified = ui.displayRows(DiffMode.Unified)
    val split = ui.displayRows(DiffMode.Split)
    assertEquals(3, unified.unifiedOf(3))
    assertEquals(3, unified.displayOf(3))
    assertEquals(2, split.unifiedOf(2, Side.Old))
    assertEquals(3, split.unifiedOf(2, Side.New))
    assertEquals(3, split.unifiedOf(2))
    assertEquals(2, split.displayOf(3))
    assertEquals(2, split.displayOf(2))
    val removedOnly = state(listOf(changeFile("x.kt", DiffLine(LineKind.Removed, "a", 1, null)))).displayRows(DiffMode.Split)
    assertEquals(2, removedOnly.unifiedOf(2))
    assertEquals(ui.rows.size, split.unifiedOf(ui.split.size))
  }
}
