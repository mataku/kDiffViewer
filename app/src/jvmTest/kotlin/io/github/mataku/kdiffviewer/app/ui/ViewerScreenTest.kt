package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.mataku.kdiffviewer.app.LoadStatus
import io.github.mataku.kdiffviewer.app.ViewerUiState
import io.github.mataku.kdiffviewer.app.buildFileTree
import io.github.mataku.kdiffviewer.app.flattenRows
import io.github.mataku.kdiffviewer.app.DiffRow
import io.github.mataku.kdiffviewer.core.model.ChangeType
import io.github.mataku.kdiffviewer.core.model.DiffContent
import io.github.mataku.kdiffviewer.core.model.DiffLine
import io.github.mataku.kdiffviewer.core.model.FileDiff
import io.github.mataku.kdiffviewer.core.model.Hunk
import io.github.mataku.kdiffviewer.core.model.LineKind
import io.github.mataku.kdiffviewer.core.model.RepoPath
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ViewerScreenTest {
    private fun stateOf(files: List<FileDiff>, status: LoadStatus = LoadStatus.Done): ViewerUiState {
        val rows = flattenRows(files)
        val headers = rows.mapIndexedNotNull { i, r -> if (r is DiffRow.FileHeader) i else null }
        return ViewerUiState(RepoPath("/repo"), status, files, rows, headers, buildFileTree(files))
    }

    private fun modified(path: String, lines: Int = 2): FileDiff {
        val body = buildList {
            add(DiffLine(LineKind.Removed, "ONE", 1, null))
            add(DiffLine(LineKind.Added, "TWO", null, 1))
            repeat(lines - 2) { add(DiffLine(LineKind.Context, "ctx$it", it + 2, it + 2)) }
        }
        return FileDiff(path, path, ChangeType.Modified, false, DiffContent.Text(listOf(Hunk(1, 1, 1, 1, "@@ -1 +1 @@", body))))
    }

    @Test
    fun showsFileHeaderAndLines() = runComposeUiTest {
        setContent { ViewerScreen(stateOf(listOf(modified("src/a.kt"))), {}) }
        onNodeWithText("src/a.kt", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("+1").assertIsDisplayed()
        onNodeWithText("−1").assertIsDisplayed()
        onNodeWithText("TWO").assertIsDisplayed()
    }

    @Test
    fun expandsTabsInLineText() = runComposeUiTest {
        val line = DiffLine(LineKind.Added, "\tval x = 1", null, 1)
        val file = FileDiff(null, "t.kt", ChangeType.Added, false, DiffContent.Text(listOf(Hunk(0, 0, 1, 1, "", listOf(line)))))
        setContent { ViewerScreen(stateOf(listOf(file)), {}) }
        onNodeWithText("        val x = 1").assertIsDisplayed()
    }

    @Test
    fun showsRenameAndBadges() = runComposeUiTest {
        val renamed = FileDiff("a.txt", "b.txt", ChangeType.Renamed, false, DiffContent.Text(emptyList()))
        val untracked = FileDiff(null, "n.txt", ChangeType.Added, true, DiffContent.Text(emptyList()))
        setContent { ViewerScreen(stateOf(listOf(renamed, untracked)), {}) }
        onNodeWithText("a.txt → b.txt").assertIsDisplayed()
        onNodeWithText("Renamed").assertIsDisplayed()
        onNodeWithText("Untracked").assertIsDisplayed()
    }

    @Test
    fun showsPlaceholder() = runComposeUiTest {
        val binary = FileDiff(null, "x.bin", ChangeType.Added, false, DiffContent.Binary)
        setContent { ViewerScreen(stateOf(listOf(binary)), {}) }
        onNodeWithText("Binary file not shown").assertIsDisplayed()
    }

    @Test
    fun showsNoChanges() = runComposeUiTest {
        setContent { ViewerScreen(stateOf(emptyList()), {}) }
        onNodeWithText("No changes").assertIsDisplayed()
    }

    @Test
    fun clickingTreeEntryScrollsToFile() = runComposeUiTest {
        val files = (0 until 30).map { modified("f${it.toString().padStart(2, '0')}.kt", 20) }
        setContent { ViewerScreen(stateOf(files), {}) }
        onNodeWithTag("file-tree").performScrollToNode(hasTestTag("tree-file-25"))
        onNodeWithTag("tree-file-25").performClick()
        waitForIdle()
        onNodeWithTag("file-header-25").assertIsDisplayed()
    }

    @Test
    fun reloadButtonInvokesCallback() = runComposeUiTest {
        var count = 0
        setContent { ViewerScreen(stateOf(emptyList()), { count++ }) }
        onNodeWithTag("reload-button").performClick()
        assertEquals(1, count)
    }
}
