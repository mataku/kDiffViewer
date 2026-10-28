package io.github.mataku.kdiffviewer.app

import io.github.mataku.kdiffviewer.core.model.BaseSpec
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScrollRestorerTest {
  private val repo = RepoPath("/repo")

  private fun file(path: String, lines: Int) = FileDiff(
    path,
    path,
    ChangeType.Modified,
    false,
    DiffContent.Text(listOf(Hunk(1, lines, 1, lines, "", (1..lines).map { DiffLine(LineKind.Context, "$it", it, it) }))),
  )

  private fun state(
    files: List<FileDiff>,
    loadId: Long,
    status: LoadStatus = LoadStatus.Done,
    repoPath: RepoPath = repo,
    collapsedIndices: Set<Int> = emptySet(),
    baseSpec: BaseSpec = BaseSpec.Default,
  ): ViewerUiState {
    val rows = flattenRows(files)
    val headers = rows.mapIndexedNotNull { index, row -> if (row is DiffRow.FileHeader) index else null }
    val fingerprints = files.map { it.fingerprint() }
    val collapsed = collapsedIndices.map { CollapsedFile(files[it].displayPath, fingerprints[it]) }.toSet()
    return ViewerUiState(repoPath, status, files, rows, headers, buildFileTree(files), loadId, fingerprints = fingerprints, collapsed = collapsed, baseSpec = baseSpec, split = splitRowsOf(rows))
  }

  private fun anchorOf(ui: ViewerUiState, firstVisibleIndex: Int) = scrollAnchorOf(ui, diffLayoutOf(ui), ui.displayRows(DiffMode.Unified), firstVisibleIndex)

  private fun targetOf(anchor: ScrollAnchor, ui: ViewerUiState) = restoreTargetOf(anchor, ui, diffLayoutOf(ui), ui.displayRows(DiffMode.Unified))

  private fun ScrollRestorer.nextFor(ui: ViewerUiState, firstVisibleIndex: Int, mode: DiffMode = DiffMode.Unified, hasEnd: Boolean = false) = next(ui, diffLayoutOf(ui, mode, hasEnd = hasEnd), ui.displayRows(mode), firstVisibleIndex)

  private val hunkFile = FileDiff(
    "h.kt",
    "h.kt",
    ChangeType.Modified,
    false,
    DiffContent.Text(
      listOf(
        Hunk(
          1,
          2,
          1,
          2,
          "",
          listOf(
            DiffLine(LineKind.Removed, "a", 1, null),
            DiffLine(LineKind.Removed, "b", 2, null),
            DiffLine(LineKind.Added, "c", null, 1),
            DiffLine(LineKind.Added, "d", null, 2),
          ),
        ),
      ),
    ),
  )

  private val a = file("a.kt", 3)
  private val b = file("b.kt", 10)
  private val c = file("c.kt", 2)

  @Test
  fun anchorIsFileHeaderAtOrAboveFirstVisibleRowPlusOffset() {
    val ui = state(listOf(a, b), 1)
    assertEquals(ScrollAnchor("a.kt", 0), anchorOf(ui, 0))
    assertEquals(ScrollAnchor("a.kt", 4), anchorOf(ui, 4))
    assertEquals(ScrollAnchor("b.kt", 3), anchorOf(ui, 8))
    assertNull(anchorOf(state(emptyList(), 1), 0))
  }

  @Test
  fun targetIsSameOffsetInsideTheFirstFileWithSamePath() {
    val ui = state(listOf(c, a, b, file("b.kt", 1)), 2)
    assertEquals(12, targetOf(ScrollAnchor("b.kt", 3), ui))
  }

  @Test
  fun targetIsClampedToTheLastRowOfThatFile() {
    val ui = state(listOf(file("b.kt", 2), a), 2)
    assertEquals(3, targetOf(ScrollAnchor("b.kt", 9), ui))
    val last = state(listOf(a, file("b.kt", 2)), 2)
    assertEquals(8, targetOf(ScrollAnchor("b.kt", 9), last))
  }

  @Test
  fun targetIsNullWhenPathIsMissing() {
    assertNull(targetOf(ScrollAnchor("zzz.kt", 0), state(listOf(a), 2)))
  }

  @Test
  fun reloadRestoresOnceWhenFileArrives() {
    val restorer = ScrollRestorer()
    assertNull(restorer.nextFor(state(listOf(a, b), 1), 8))
    assertNull(restorer.nextFor(state(emptyList(), 2, LoadStatus.Loading), 8))
    assertNull(restorer.nextFor(state(listOf(c), 2, LoadStatus.Loading), 0))
    assertEquals(7, restorer.nextFor(state(listOf(c, b), 2, LoadStatus.Loading), 0))
    assertNull(restorer.nextFor(state(listOf(c, b, a), 2), 7))
  }

  @Test
  fun reloadStopsRestoringWhenLoadEndsWithoutTheFile() {
    val restorer = ScrollRestorer()
    restorer.nextFor(state(listOf(a, b), 1), 8)
    assertNull(restorer.nextFor(state(listOf(c), 2), 0))
    assertNull(restorer.nextFor(state(listOf(c, b), 2, LoadStatus.Loading), 0))
  }

  @Test
  fun openingAnotherRepositoryDoesNotRestore() {
    val restorer = ScrollRestorer()
    restorer.nextFor(state(listOf(a, b), 1), 8)
    assertNull(restorer.nextFor(state(listOf(a, b), 2, repoPath = RepoPath("/other")), 0))
  }

  @Test
  fun anchorFollowsScrollingWithinTheSameLoad() {
    val restorer = ScrollRestorer()
    restorer.nextFor(state(listOf(a, b), 1), 8)
    restorer.nextFor(state(listOf(a, b), 1), 1)
    assertEquals(5, restorer.nextFor(state(listOf(c, a), 2), 0))
  }

  @Test
  fun anchorSkipsCollapsedFileBefore() {
    val ui = state(listOf(a, b, c), 1, collapsedIndices = setOf(1))
    assertEquals(ScrollAnchor("c.kt", 1), anchorOf(ui, 7))
  }

  @Test
  fun targetInsideFileCollapsedAfterReloadIsItsHeader() {
    val ui = state(listOf(a, b, c), 2, collapsedIndices = setOf(1))
    assertEquals(diffLayoutOf(ui).headerVisibleIndex(1), targetOf(ScrollAnchor("b.kt", 4), ui))
  }

  @Test
  fun targetAfterCollapsedFileIsShifted() {
    val ui = state(listOf(a, b, c), 2, collapsedIndices = setOf(1))
    assertEquals(7, targetOf(ScrollAnchor("c.kt", 1), ui))
  }

  @Test
  fun baseChangeScrollsToTopWhenTheNewLoadArrives() {
    val restorer = ScrollRestorer()
    assertNull(restorer.nextFor(state(listOf(a, b), 1), 8))
    assertNull(restorer.nextFor(state(listOf(a, b), 1, LoadStatus.Loading, baseSpec = BaseSpec.Ref("x")), 8))
    assertEquals(0, restorer.nextFor(state(listOf(c, b), 2, baseSpec = BaseSpec.Ref("x")), 8))
    assertNull(restorer.nextFor(state(listOf(c, b), 2, baseSpec = BaseSpec.Ref("x")), 0))
  }

  @Test
  fun baseChangeWaitsForRowsBeforeScrollingToTop() {
    val restorer = ScrollRestorer()
    restorer.nextFor(state(listOf(a, b), 1), 8)
    assertNull(restorer.nextFor(state(emptyList(), 2, LoadStatus.Loading, baseSpec = BaseSpec.Ref("x")), 0))
    assertEquals(0, restorer.nextFor(state(listOf(c), 2, LoadStatus.Loading, baseSpec = BaseSpec.Ref("x")), 0))
    assertNull(restorer.nextFor(state(listOf(c, b), 2, baseSpec = BaseSpec.Ref("x")), 0))
  }

  @Test
  fun sameBaseReloadStillRestoresByPath() {
    val restorer = ScrollRestorer()
    restorer.nextFor(state(listOf(a, b), 1, baseSpec = BaseSpec.Ref("x")), 8)
    assertEquals(7, restorer.nextFor(state(listOf(c, b), 2, baseSpec = BaseSpec.Ref("x")), 0))
  }

  @Test
  fun modeSwitchKeepsUnifiedRow() {
    val ui = state(listOf(hunkFile), 1)
    val restorer = ScrollRestorer()
    assertNull(restorer.nextFor(ui, 5))
    assertEquals(3, restorer.nextFor(ui, 5, DiffMode.Split))
    assertNull(restorer.nextFor(ui, 3, DiffMode.Split))
    assertEquals(5, restorer.nextFor(ui, 3, DiffMode.Unified))
  }

  @Test
  fun reloadInSplitRestores() {
    val restorer = ScrollRestorer()
    assertNull(restorer.nextFor(state(listOf(a, b), 1), 8, DiffMode.Split))
    assertNull(restorer.nextFor(state(emptyList(), 2, LoadStatus.Loading), 8, DiffMode.Split))
    assertNull(restorer.nextFor(state(listOf(c), 2, LoadStatus.Loading), 0, DiffMode.Split))
    assertEquals(7, restorer.nextFor(state(listOf(c, b), 2, LoadStatus.Loading), 0, DiffMode.Split))
    assertNull(restorer.nextFor(state(listOf(c, b, a), 2), 7, DiffMode.Split))
  }

  @Test
  fun modeSwitchWithEndItemFirstVisible() {
    val ui = state(listOf(hunkFile), 1)
    val restorer = ScrollRestorer()
    val unifiedLast = diffLayoutOf(ui, DiffMode.Unified, hasEnd = true).visibleCount - 1
    assertNull(restorer.nextFor(ui, unifiedLast, DiffMode.Unified, hasEnd = true))
    val target = restorer.nextFor(ui, unifiedLast, DiffMode.Split, hasEnd = true)!!
    assertTrue(target <= diffLayoutOf(ui, DiffMode.Split, hasEnd = true).visibleCount - 1)
    assertNull(restorer.nextFor(ui, target, DiffMode.Split, hasEnd = true))
    val back = restorer.nextFor(ui, target, DiffMode.Unified, hasEnd = true)!!
    assertTrue(back <= unifiedLast)
  }
}
