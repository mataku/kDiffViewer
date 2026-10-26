package io.github.mataku.kdiffviewer.app

import io.github.mataku.kdiffviewer.core.model.ChangeType
import io.github.mataku.kdiffviewer.core.model.DiffContent
import io.github.mataku.kdiffviewer.core.model.DiffLine
import io.github.mataku.kdiffviewer.core.model.FileDiff
import io.github.mataku.kdiffviewer.core.model.Hunk
import io.github.mataku.kdiffviewer.core.model.LineKind
import io.github.mataku.kdiffviewer.core.model.RepoPath
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
    val collapsed = collapsedIndices.associate { files[it].displayPath to fingerprints[it] }
    return ViewerUiState(RepoPath("/repo"), LoadStatus.Done, files, rows, headers, fingerprints = fingerprints, collapsed = collapsed)
  }

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
}
