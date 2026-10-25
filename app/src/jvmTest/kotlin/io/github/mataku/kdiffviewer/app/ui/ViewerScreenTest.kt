package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import io.github.mataku.compose.highlight.core.HighlightSpan
import io.github.mataku.kdiffviewer.app.DiffRow
import io.github.mataku.kdiffviewer.app.LoadStatus
import io.github.mataku.kdiffviewer.app.ViewerUiState
import io.github.mataku.kdiffviewer.app.buildFileTree
import io.github.mataku.kdiffviewer.app.flattenRows
import io.github.mataku.kdiffviewer.app.highlight.FileHighlight
import io.github.mataku.kdiffviewer.app.highlight.HighlightSlot
import io.github.mataku.kdiffviewer.core.model.ChangeType
import io.github.mataku.kdiffviewer.core.model.DiffContent
import io.github.mataku.kdiffviewer.core.model.DiffLine
import io.github.mataku.kdiffviewer.core.model.FileDiff
import io.github.mataku.kdiffviewer.core.model.Hunk
import io.github.mataku.kdiffviewer.core.model.LineKind
import io.github.mataku.kdiffviewer.core.model.RepoPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
    return FileDiff(path, path, ChangeType.Modified, false, DiffContent.Text(listOf(Hunk(1, 1, 1, 1, " fun main()", body))))
  }

  @Test
  fun showsFileHeaderAndLines() = runComposeUiTest {
    setContent { ViewerScreen(stateOf(listOf(modified("src/a.kt"))), {}) }
    onNodeWithText("src/a.kt", useUnmergedTree = true).assertIsDisplayed()
    onNodeWithText("+1").assertIsDisplayed()
    onNodeWithText("−1").assertIsDisplayed()
    onNodeWithText("TWO").assertIsDisplayed()
    onNodeWithText("@@ -1,1 +1,1 @@ fun main()").assertIsDisplayed()
  }

  @Test
  fun expandsTabsInLineText() = runComposeUiTest {
    val line = DiffLine(LineKind.Added, "\tval x = 1", null, 1)
    val file = FileDiff(null, "t.kt", ChangeType.Added, false, DiffContent.Text(listOf(Hunk(0, 0, 1, 1, "", listOf(line)))))
    setContent { ViewerScreen(stateOf(listOf(file)), {}) }
    onNodeWithText("        val x = 1").assertIsDisplayed()
  }

  @Test
  fun longPathKeepsBadgeAndStatsVisible() = runComposeUiTest {
    val path = "very/long/" + "nested-directory/".repeat(5) + "a-file-with-a-rather-long-name.kt"
    setContent {
      Box(modifier = Modifier.width(800.dp)) {
        ViewerScreen(stateOf(listOf(modified(path))), {})
      }
    }
    listOf("file-badge-0", "file-additions-0", "file-deletions-0").forEach { tag ->
      val width = onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().size.width
      assertTrue(width > 0, "$tag width=$width")
    }
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
  fun reloadRestoresScrollToSameFile() = runComposeUiTest {
    val files = (0 until 30).map { modified("f${it.toString().padStart(2, '0')}.kt", 20) }
    val initial = stateOf(files).copy(loadId = 1)
    var ui by mutableStateOf(initial)
    setContent { ViewerScreen(ui, {}) }
    onNodeWithTag("diff-list").performScrollToIndex(initial.fileHeaderRowIndex[20])
    waitForIdle()
    onNodeWithTag("file-header-20").assertIsDisplayed()
    ui = ViewerUiState(RepoPath("/repo"), LoadStatus.Loading, loadId = 2)
    waitForIdle()
    ui = stateOf(listOf(modified("new.kt", 20)) + files).copy(loadId = 2)
    waitForIdle()
    onNodeWithTag("file-header-21").assertIsDisplayed()
    onNodeWithTag("file-header-0").assertDoesNotExist()
  }

  @Test
  fun reloadButtonInvokesCallback() = runComposeUiTest {
    var count = 0
    setContent { ViewerScreen(stateOf(emptyList()), { count++ }) }
    onNodeWithTag("reload-button").performClick()
    assertEquals(1, count)
  }

  private fun keywordFile(): FileDiff {
    val line = DiffLine(LineKind.Added, "fun x", null, 1)
    return FileDiff(null, "k.kt", ChangeType.Added, false, DiffContent.Text(listOf(Hunk(0, 0, 1, 1, "", listOf(line)))))
  }

  private fun keywordHighlight() = FileHighlight(arrayOf(arrayOf(listOf(HighlightSpan(0, 3, "keyword")))))

  @Test
  fun highlightedLineUsesSyntaxColors() = runComposeUiTest {
    val file = keywordFile()
    val slot = HighlightSlot().apply { value = keywordHighlight() }
    setContent { KdvTheme(darkTheme = false) { ViewerScreen(stateOf(listOf(file)).copy(highlights = listOf(slot)), {}) } }
    val ranges = lineText("fun x").spanStyles
    assertEquals(1, ranges.size)
    assertEquals(0, ranges[0].start)
    assertEquals(3, ranges[0].end)
    assertEquals(LightSyntaxTheme.resolve("keyword")!!.color, ranges[0].item.color)
  }

  @Test
  fun slotWriteRecolorsVisibleLine() = runComposeUiTest {
    val file = keywordFile()
    val slot = HighlightSlot()
    setContent { KdvTheme(darkTheme = false) { ViewerScreen(stateOf(listOf(file)).copy(highlights = listOf(slot)), {}) } }
    assertEquals(emptyList(), lineText("fun x").spanStyles)
    slot.value = keywordHighlight()
    waitForIdle()
    val ranges = lineText("fun x").spanStyles
    assertEquals(1, ranges.size)
    assertEquals(LightSyntaxTheme.resolve("keyword")!!.color, ranges[0].item.color)
  }

  @Test
  fun lineWithoutSpansStaysPlain() = runComposeUiTest {
    setContent { KdvTheme(darkTheme = false) { ViewerScreen(stateOf(listOf(keywordFile())), {}) } }
    assertEquals(emptyList(), lineText("fun x").spanStyles)
  }
}

fun SemanticsNodeInteractionsProvider.lineText(text: String): AnnotatedString = onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsProperties.Text].single()
