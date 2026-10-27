package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.foundation.lazy.LazyListItemInfo
import io.github.mataku.kdiffviewer.app.CollapsedFile
import io.github.mataku.kdiffviewer.app.DiffRow
import io.github.mataku.kdiffviewer.app.LoadStatus
import io.github.mataku.kdiffviewer.app.ViewerUiState
import io.github.mataku.kdiffviewer.app.diffLayoutOf
import io.github.mataku.kdiffviewer.app.fingerprint
import io.github.mataku.kdiffviewer.app.flattenRows
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

class FileFramesTest {
  private class Item(override val index: Int, override val offset: Int, override val size: Int) : LazyListItemInfo {
    override val key: Any get() = index
  }

  private fun items(first: Int, last: Int, firstOffset: Int = 0, size: Int = 10): List<LazyListItemInfo> = (first..last).mapIndexed { i, index -> Item(index, firstOffset + i * size, size) }

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
    return ViewerUiState(RepoPath("/repo"), LoadStatus.Done, files, rows, headers, fingerprints = fingerprints, collapsed = collapsed)
  }

  private val a = file("a.kt", 3)
  private val b = file("b.kt", 10)
  private val layout = diffLayoutOf(state(listOf(a, b)))

  @Test
  fun fullyVisibleFilesGetFullFrames() {
    val frames = fileFrames(layout, items(0, 16), 2f, 1000f, 7f)
    assertEquals(listOf(FileFrame(2f, 50f, true, true), FileFrame(52f, 170f, true, true)), frames)
  }

  @Test
  fun headerAboveViewportDropsTopCorners() {
    val frames = fileFrames(layout, items(7, 16, firstOffset = 0), 2f, 100f, 7f)
    assertEquals(listOf(FileFrame(-7f, 100f, false, true)), frames)
  }

  @Test
  fun lastRowBelowViewportDropsBottomCorners() {
    val frames = fileFrames(layout, items(0, 9), 2f, 100f, 7f)
    assertEquals(FileFrame(2f, 50f, true, true), frames[0])
    assertEquals(FileFrame(52f, 107f, true, false), frames[1])
  }

  @Test
  fun middleRowsOnlyGiveStraightSides() {
    val frames = fileFrames(layout, items(8, 10), 2f, 100f, 7f)
    assertEquals(listOf(FileFrame(-7f, 107f, false, false)), frames)
  }

  @Test
  fun collapsedFileIsOneRoundedRect() {
    val collapsed = diffLayoutOf(state(listOf(a, b), setOf(0)))
    val frames = fileFrames(collapsed, items(0, 1), 2f, 100f, 7f)
    assertEquals(FileFrame(2f, 10f, true, true), frames[0])
  }

  @Test
  fun orphanAndEndItemsAreNotFramed() {
    val withExtras = diffLayoutOf(state(listOf(a, b)), hasOrphans = true, hasEnd = true)
    val frames = fileFrames(withExtras, items(0, withExtras.visibleCount - 1), 2f, 1000f, 7f)
    assertEquals(2, frames.size)
    assertEquals(FileFrame(12f, 60f, true, true), frames[0])
    assertEquals(FileFrame(62f, 180f, true, true), frames[1])
  }

  @Test
  fun staleIndexBeyondLayoutIsIgnored() {
    val frames = fileFrames(layout, items(15, 20), 2f, 100f, 7f)
    assertEquals(listOf(FileFrame(-7f, 20f, false, true)), frames)
  }
}
