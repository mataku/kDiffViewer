package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
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
import io.github.mataku.kdiffviewer.core.model.HeadRef
import io.github.mataku.kdiffviewer.core.model.Hunk
import io.github.mataku.kdiffviewer.core.model.LineKind
import io.github.mataku.kdiffviewer.core.model.RepoPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    onNodeWithTag("file-stats-0", useUnmergedTree = true).assertTextEquals("+1 −1")
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
  fun expandsTabsWithConfiguredTabWidth() = runComposeUiTest {
    val line = DiffLine(LineKind.Added, "\tx", null, 1)
    val file = FileDiff(null, "t.kt", ChangeType.Added, false, DiffContent.Text(listOf(Hunk(0, 0, 1, 1, "", listOf(line)))))
    setContent { KdvTheme(darkTheme = false, tabWidth = 4) { ViewerScreen(stateOf(listOf(file)), {}) } }
    onNodeWithText("    x").assertIsDisplayed()
  }

  @Test
  fun longPathKeepsBadgeAndStatsVisible() = runComposeUiTest {
    val path = "very/long/" + "nested-directory/".repeat(5) + "a-file-with-a-rather-long-name.kt"
    setContent {
      Box(modifier = Modifier.width(800.dp)) {
        ViewerScreen(stateOf(listOf(modified(path))), {})
      }
    }
    listOf("file-badge-0", "file-stats-0").forEach { tag ->
      val width = onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().size.width
      assertTrue(width > 0, "$tag width=$width")
    }
  }

  @Test
  fun headerStatsRightAligned() = runComposeUiTest {
    setContent { Box(modifier = Modifier.size(1000.dp, 500.dp)) { ViewerScreen(stateOf(listOf(modified("a.kt"))), {}) } }
    val header = onNodeWithTag("file-header-0").fetchSemanticsNode().boundsInRoot
    val badge = onNodeWithTag("file-badge-0", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    val stats = onNodeWithTag("file-stats-0", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    assertTrue(stats.left >= badge.right, "stats=$stats badge=$badge")
    assertTrue(kotlin.math.abs(header.right - stats.right) <= 1f, "stats=$stats header=$header")
  }

  @Test
  fun headerHidesZeroCounts() = runComposeUiTest {
    val added = FileDiff(null, "n.kt", ChangeType.Added, false, DiffContent.Text(listOf(Hunk(0, 0, 40, 1, "", List(40) { DiffLine(LineKind.Added, "x$it", null, it + 1) }))))
    val renamed = FileDiff("a.txt", "b.txt", ChangeType.Renamed, false, DiffContent.Text(emptyList()))
    setContent { ViewerScreen(stateOf(listOf(added, renamed)), {}) }
    onNodeWithTag("file-stats-0", useUnmergedTree = true).assertTextEquals("+40")
    onNodeWithTag("file-stats-1", useUnmergedTree = true).assertDoesNotExist()
  }

  @Test
  fun headerStatsSingleSpace() = runComposeUiTest {
    val body = List(12) { DiffLine(LineKind.Added, "a$it", null, it + 1) } + List(3) { DiffLine(LineKind.Removed, "r$it", it + 1, null) }
    val file = FileDiff("m.kt", "m.kt", ChangeType.Modified, false, DiffContent.Text(listOf(Hunk(1, 3, 1, 12, "", body))))
    setContent { ViewerScreen(stateOf(listOf(file)), {}) }
    onNodeWithTag("file-stats-0", useUnmergedTree = true).assertTextEquals("+12 −3")
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

  @Test
  fun headerShowsRepoName() = runComposeUiTest {
    setContent { ViewerScreen(stateOf(emptyList()).copy(repo = RepoPath("/work/my-repo")), {}) }
    onNodeWithTag("repo-name").assertTextEquals("my-repo")
  }

  @Test
  fun branchSitsBelowRepoNameWithBranchIcon() = runComposeUiTest {
    setContent { ViewerScreen(stateOf(emptyList()).copy(head = HeadRef.Branch("feature/x")), {}) }
    onNodeWithTag("head-label", useUnmergedTree = true).assertTextEquals("feature/x")
    val name = onNodeWithTag("repo-name", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    val label = onNodeWithTag("head-label", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    val icon = onNodeWithTag("head-branch-icon", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    assertTrue(label.top >= name.bottom, "label=$label name=$name")
    assertTrue(icon.right <= label.left, "icon=$icon label=$label")
    onNodeWithTag("head-commit-icon", useUnmergedTree = true).assertDoesNotExist()
  }

  @Test
  fun detachedHeadShowsHashWithCommitIcon() = runComposeUiTest {
    setContent { ViewerScreen(stateOf(emptyList()).copy(head = HeadRef.Detached("abc1234")), {}) }
    onNodeWithTag("head-label", useUnmergedTree = true).assertTextEquals("abc1234")
    onNodeWithTag("head-commit-icon", useUnmergedTree = true).assertIsDisplayed()
    onNodeWithTag("head-branch-icon", useUnmergedTree = true).assertDoesNotExist()
  }

  @Test
  fun titleBarInsetPushesTreeRowsDown() = runComposeUiTest {
    setContent { ViewerScreen(stateOf(listOf(modified("a.kt"))), {}, titleBarInset = 28.dp) }
    val row = onNodeWithTag("tree-file-0").fetchSemanticsNode().boundsInRoot
    assertTrue(row.top >= with(density) { 28.dp.toPx() }, "row=$row")
  }

  @Test
  fun headerLeavesRoomAroundItsContent() = runComposeUiTest {
    setContent { ViewerScreen(stateOf(emptyList()).copy(head = HeadRef.Branch("main")), {}) }
    val header = onNodeWithTag("repo-header").fetchSemanticsNode().boundsInRoot
    val reload = onNodeWithTag("reload-button").fetchSemanticsNode().boundsInRoot
    val minHeight = with(density) { 52.dp.toPx() }
    val margin = with(density) { 16.dp.toPx() }
    assertTrue(header.height >= minHeight, "header=$header")
    assertTrue(header.right - reload.right >= margin, "reload=$reload header=$header")
  }

  @Test
  fun clickingDirectoryCollapsesAndExpandsIt() = runComposeUiTest {
    setContent { ViewerScreen(stateOf(listOf(modified("src/a.kt"))), {}) }
    onNodeWithTag("tree-dir-0").performClick()
    onNodeWithTag("tree-file-0").assertDoesNotExist()
    onNodeWithTag("tree-dir-0").performClick()
    onNodeWithTag("tree-file-0").assertIsDisplayed()
  }

  @Test
  fun collapsedDirectoryStaysCollapsedAfterReload() = runComposeUiTest {
    var ui by mutableStateOf(stateOf(listOf(modified("src/a.kt"))).copy(loadId = 1))
    setContent { ViewerScreen(ui, {}) }
    onNodeWithTag("tree-dir-0").performClick()
    ui = stateOf(listOf(modified("src/a.kt"), modified("src/b.kt"))).copy(loadId = 2)
    waitForIdle()
    onNodeWithTag("tree-file-0").assertDoesNotExist()
    onNodeWithTag("tree-file-1").assertDoesNotExist()
  }

  @Test
  fun openingAnotherRepoExpandsEverything() = runComposeUiTest {
    var ui by mutableStateOf(stateOf(listOf(modified("src/a.kt"))))
    setContent { ViewerScreen(ui, {}) }
    onNodeWithTag("tree-dir-0").performClick()
    ui = stateOf(listOf(modified("src/a.kt"))).copy(repo = RepoPath("/other"))
    waitForIdle()
    onNodeWithTag("tree-file-0").assertIsDisplayed()
  }

  @Test
  fun reloadButtonRegistersTitleBarClientRegion() = runComposeUiTest {
    val regions = TitleBarRegions()
    setContent {
      CompositionLocalProvider(LocalTitleBarRegions provides regions) {
        ViewerScreen(stateOf(emptyList()), {})
      }
    }
    val reload = onNodeWithTag("reload-button").fetchSemanticsNode().boundsInRoot
    assertTrue(regions.isClientArea(reload.center))
    assertFalse(regions.isClientArea(Offset(reload.left - 1f, reload.center.y)))
  }

  @Test
  fun reloadButtonRegionIsRemovedWhenScreenLeaves() = runComposeUiTest {
    val regions = TitleBarRegions()
    var show by mutableStateOf(true)
    setContent {
      CompositionLocalProvider(LocalTitleBarRegions provides regions) {
        if (show) ViewerScreen(stateOf(emptyList()), {})
      }
    }
    val reload = onNodeWithTag("reload-button").fetchSemanticsNode().boundsInRoot
    show = false
    waitForIdle()
    assertFalse(regions.isClientArea(reload.center))
  }

  @Test
  fun treeRowsKeepHorizontalMargin() = runComposeUiTest {
    setContent { ViewerScreen(stateOf(listOf(modified("a.kt"))), {}) }
    val tree = onNodeWithTag("file-tree").fetchSemanticsNode().boundsInRoot
    val row = onNodeWithTag("tree-file-0").fetchSemanticsNode().boundsInRoot
    val margin = with(density) { ContentHorizontalMargin.toPx() }
    assertTrue(row.left - tree.left >= margin, "row=$row tree=$tree")
    assertTrue(tree.right - row.right >= margin, "row=$row tree=$tree")
  }

  @Test
  fun diffRowsKeepHorizontalMargin() = runComposeUiTest {
    setContent { ViewerScreen(stateOf(listOf(modified("a.kt"))), {}) }
    val list = onNodeWithTag("diff-list").fetchSemanticsNode().boundsInRoot
    val header = onNodeWithTag("file-header-0").fetchSemanticsNode().boundsInRoot
    val margin = with(density) { ContentHorizontalMargin.toPx() }
    assertTrue(header.left - list.left >= margin, "header=$header list=$list")
    assertTrue(list.right - header.right >= margin, "header=$header list=$list")
  }

  @Test
  fun lastDiffRowKeepsBottomMargin() = runComposeUiTest {
    val state = stateOf(List(5) { modified("f$it.kt", 20) })
    setContent {
      Box(modifier = Modifier.size(800.dp, 600.dp)) {
        ViewerScreen(state, {})
      }
    }
    onNodeWithTag("diff-list").performScrollToIndex(state.rows.lastIndex)
    waitForIdle()
    val list = onNodeWithTag("diff-list").fetchSemanticsNode().boundsInRoot
    val lastBottom = onAllNodesWithText("ctx17").fetchSemanticsNodes().maxOf { it.boundsInRoot.bottom }
    val margin = with(density) { 12.dp.toPx() }
    assertTrue(list.bottom - lastBottom >= margin, "lastBottom=$lastBottom list=$list")
  }

  @Test
  fun reloadButtonIsAnIconWithAccessibleLabel() = runComposeUiTest {
    setContent { ViewerScreen(stateOf(emptyList()), {}) }
    onNodeWithContentDescription("Reload").assertIsDisplayed()
    onNodeWithText("Reload").assertDoesNotExist()
  }

  @Test
  fun hoveringReloadShowsShortcutTooltip() = runComposeUiTest {
    setContent { ViewerScreen(stateOf(emptyList()), {}) }
    onNodeWithTag("reload-button").performMouseInput { moveTo(center) }
    waitUntil(timeoutMillis = 3_000) { onAllNodesWithText("Reload (⌘R)").fetchSemanticsNodes().isNotEmpty() }
  }

  @Test
  fun loadingIndicatorDoesNotShiftTheDiffList() = runComposeUiTest {
    var ui by mutableStateOf(stateOf(listOf(modified("a.kt"))))
    setContent { ViewerScreen(ui, {}) }
    val doneTop = onNodeWithTag("diff-list").fetchSemanticsNode().boundsInRoot.top
    ui = ui.copy(status = LoadStatus.Loading)
    waitForIdle()
    val loadingTop = onNodeWithTag("diff-list").fetchSemanticsNode().boundsInRoot.top
    assertEquals(doneTop, loadingTop)
  }

  @Test
  fun treeShowsFileStatsFromTheDiff() = runComposeUiTest {
    setContent { ViewerScreen(stateOf(listOf(modified("a.kt"))), {}) }
    onNodeWithTag("tree-stats-0", useUnmergedTree = true).assertTextEquals("+1 −1")
  }

  @Test
  fun draggingDividerResizesTree() = runComposeUiTest {
    setContent {
      Box(modifier = Modifier.size(1000.dp, 600.dp)) {
        ViewerScreen(stateOf(listOf(modified("a.kt"))), {})
      }
    }
    val before = onNodeWithTag("file-tree").fetchSemanticsNode().boundsInRoot.width
    onNodeWithTag("tree-divider").performMouseInput {
      moveTo(center)
      press()
      moveBy(Offset(100f, 0f))
      release()
    }
    waitForIdle()
    val after = onNodeWithTag("file-tree").fetchSemanticsNode().boundsInRoot.width
    assertTrue(after - before in 90f..110f, "before=$before after=$after")
  }

  @Test
  fun treeWidthIsClampedAndNarrowTreeHidesStats() = runComposeUiTest {
    setContent {
      Box(modifier = Modifier.size(1000.dp, 600.dp)) {
        ViewerScreen(stateOf(listOf(modified("a.kt"))), {})
      }
    }
    onNodeWithTag("tree-divider").performMouseInput {
      moveTo(center)
      press()
      moveBy(Offset(-1000f, 0f))
      release()
    }
    waitForIdle()
    val width = onNodeWithTag("file-tree").fetchSemanticsNode().boundsInRoot.width
    assertEquals(with(density) { MinTreeWidth.toPx() }, width)
    onNodeWithTag("tree-stats-0", useUnmergedTree = true).assertDoesNotExist()
  }

  @Test
  fun treeSpansFullHeightAndHeaderSitsRightOfIt() = runComposeUiTest {
    setContent {
      Box(modifier = Modifier.size(800.dp, 600.dp)) {
        ViewerScreen(stateOf(listOf(modified("a.kt"))), {})
      }
    }
    val tree = onNodeWithTag("file-tree").fetchSemanticsNode().boundsInRoot
    val header = onNodeWithTag("repo-header").fetchSemanticsNode().boundsInRoot
    val reload = onNodeWithTag("reload-button").fetchSemanticsNode().boundsInRoot
    val root = onRoot().fetchSemanticsNode().boundsInRoot
    assertEquals(root.top, tree.top)
    assertEquals(root.bottom, tree.bottom)
    assertTrue(header.left >= tree.right, "header=$header tree=$tree")
    assertTrue(reload.left >= tree.right, "reload=$reload tree=$tree")
    assertEquals(root.top, header.top)
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

  @Test
  fun headerPathGrowsWithFontSize() = runComposeUiTest {
    var fontSize by mutableStateOf(12)
    setContent { KdvTheme(darkTheme = false, fontSize = fontSize) { ViewerScreen(stateOf(listOf(modified("a.kt"))), {}) } }
    val small = onNodeWithTag("file-header-0").fetchSemanticsNode().boundsInRoot.height
    fontSize = 24
    waitForIdle()
    val large = onNodeWithTag("file-header-0").fetchSemanticsNode().boundsInRoot.height
    assertTrue(large > small, "small=$small large=$large")
  }

  @Test
  fun lineNumberColumnWidensWithFontSize() = runComposeUiTest {
    var fontSize by mutableStateOf(24)
    setContent { KdvTheme(darkTheme = false, fontSize = fontSize) { ViewerScreen(stateOf(listOf(modified("a.kt"))), {}) } }
    onAllNodesWithText("1", useUnmergedTree = true)[0].assertWidthIsEqualTo(90.dp)
    fontSize = 12
    waitForIdle()
    onAllNodesWithText("1", useUnmergedTree = true)[0].assertWidthIsEqualTo(42.dp)
  }
}

fun SemanticsNodeInteractionsProvider.lineText(text: String): AnnotatedString = onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsProperties.Text].single()
