package io.github.mataku.kdiffviewer.app

import io.github.mataku.kdiffviewer.core.model.ChangeType
import io.github.mataku.kdiffviewer.core.model.DiffContent
import io.github.mataku.kdiffviewer.core.model.DiffLine
import io.github.mataku.kdiffviewer.core.model.FileDiff
import io.github.mataku.kdiffviewer.core.model.Hunk
import io.github.mataku.kdiffviewer.core.model.LineKind
import io.github.mataku.kdiffviewer.core.model.RepoPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
    ): ViewerUiState {
        val rows = flattenRows(files)
        val headers = rows.mapIndexedNotNull { index, row -> if (row is DiffRow.FileHeader) index else null }
        return ViewerUiState(repoPath, status, files, rows, headers, buildFileTree(files), loadId)
    }

    private val a = file("a.kt", 3)
    private val b = file("b.kt", 10)
    private val c = file("c.kt", 2)

    @Test
    fun anchorIsFileHeaderAtOrAboveFirstVisibleRowPlusOffset() {
        val ui = state(listOf(a, b), 1)
        assertEquals(ScrollAnchor("a.kt", 0), scrollAnchorOf(ui, 0))
        assertEquals(ScrollAnchor("a.kt", 4), scrollAnchorOf(ui, 4))
        assertEquals(ScrollAnchor("b.kt", 3), scrollAnchorOf(ui, 8))
        assertNull(scrollAnchorOf(state(emptyList(), 1), 0))
    }

    @Test
    fun targetIsSameOffsetInsideTheFirstFileWithSamePath() {
        val ui = state(listOf(c, a, b, file("b.kt", 1)), 2)
        assertEquals(12, restoreTargetOf(ScrollAnchor("b.kt", 3), ui))
    }

    @Test
    fun targetIsClampedToTheLastRowOfThatFile() {
        val ui = state(listOf(file("b.kt", 2), a), 2)
        assertEquals(3, restoreTargetOf(ScrollAnchor("b.kt", 9), ui))
        val last = state(listOf(a, file("b.kt", 2)), 2)
        assertEquals(8, restoreTargetOf(ScrollAnchor("b.kt", 9), last))
    }

    @Test
    fun targetIsNullWhenPathIsMissing() {
        assertNull(restoreTargetOf(ScrollAnchor("zzz.kt", 0), state(listOf(a), 2)))
    }

    @Test
    fun reloadRestoresOnceWhenFileArrives() {
        val restorer = ScrollRestorer()
        assertNull(restorer.next(state(listOf(a, b), 1), 8))
        assertNull(restorer.next(state(emptyList(), 2, LoadStatus.Loading), 8))
        assertNull(restorer.next(state(listOf(c), 2, LoadStatus.Loading), 0))
        assertEquals(7, restorer.next(state(listOf(c, b), 2, LoadStatus.Loading), 0))
        assertNull(restorer.next(state(listOf(c, b, a), 2), 7))
    }

    @Test
    fun reloadStopsRestoringWhenLoadEndsWithoutTheFile() {
        val restorer = ScrollRestorer()
        restorer.next(state(listOf(a, b), 1), 8)
        assertNull(restorer.next(state(listOf(c), 2), 0))
        assertNull(restorer.next(state(listOf(c, b), 2, LoadStatus.Loading), 0))
    }

    @Test
    fun openingAnotherRepositoryDoesNotRestore() {
        val restorer = ScrollRestorer()
        restorer.next(state(listOf(a, b), 1), 8)
        assertNull(restorer.next(state(listOf(a, b), 2, repoPath = RepoPath("/other")), 0))
    }

    @Test
    fun anchorFollowsScrollingWithinTheSameLoad() {
        val restorer = ScrollRestorer()
        restorer.next(state(listOf(a, b), 1), 8)
        restorer.next(state(listOf(a, b), 1), 1)
        assertEquals(5, restorer.next(state(listOf(c, a), 2), 0))
    }
}
