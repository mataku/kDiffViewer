package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mataku.compose.highlight.core.HighlightSpan
import io.github.mataku.kdiffviewer.app.CollapsedFile
import io.github.mataku.kdiffviewer.app.DiffMode
import io.github.mataku.kdiffviewer.app.DiffRow
import io.github.mataku.kdiffviewer.app.LARGE_DIFF_LINES
import io.github.mataku.kdiffviewer.app.LoadStatus
import io.github.mataku.kdiffviewer.app.ViewerUiState
import io.github.mataku.kdiffviewer.app.buildFileTree
import io.github.mataku.kdiffviewer.app.fingerprint
import io.github.mataku.kdiffviewer.app.flattenRows
import io.github.mataku.kdiffviewer.app.highlight.FileHighlight
import io.github.mataku.kdiffviewer.app.highlight.HighlightSlot
import io.github.mataku.kdiffviewer.app.isCollapsed
import io.github.mataku.kdiffviewer.app.review.DraftTarget
import io.github.mataku.kdiffviewer.app.review.ReviewSnapshot
import io.github.mataku.kdiffviewer.app.review.ReviewState
import io.github.mataku.kdiffviewer.app.review.buildPlacements
import io.github.mataku.kdiffviewer.app.splitRowsOf
import io.github.mataku.kdiffviewer.app.ui.review.LocalReviewActions
import io.github.mataku.kdiffviewer.core.model.BaseSpec
import io.github.mataku.kdiffviewer.core.model.BranchRef
import io.github.mataku.kdiffviewer.core.model.ChangeType
import io.github.mataku.kdiffviewer.core.model.DiffContent
import io.github.mataku.kdiffviewer.core.model.DiffLine
import io.github.mataku.kdiffviewer.core.model.FileDiff
import io.github.mataku.kdiffviewer.core.model.HeadRef
import io.github.mataku.kdiffviewer.core.model.Hunk
import io.github.mataku.kdiffviewer.core.model.LineKind
import io.github.mataku.kdiffviewer.core.model.RepoPath
import io.github.mataku.kdiffviewer.core.model.ResolvedBase
import io.github.mataku.kdiffviewer.core.model.displayPath
import io.github.mataku.kdiffviewer.core.review.Anchor
import io.github.mataku.kdiffviewer.core.review.Author
import io.github.mataku.kdiffviewer.core.review.Comment
import io.github.mataku.kdiffviewer.core.review.LineLocation
import io.github.mataku.kdiffviewer.core.review.Placement
import io.github.mataku.kdiffviewer.core.review.ReviewThread
import io.github.mataku.kdiffviewer.core.review.anchorOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalTestApi::class)
class ViewerScreenTest {
  private fun stateOf(files: List<FileDiff>, status: LoadStatus = LoadStatus.Done, largeDiffLines: Int = LARGE_DIFF_LINES): ViewerUiState {
    val rows = flattenRows(files, largeDiffLines = largeDiffLines)
    val headers = rows.mapIndexedNotNull { i, r -> if (r is DiffRow.FileHeader) i else null }
    return ViewerUiState(RepoPath("/repo"), status, files, rows, headers, buildFileTree(files), fingerprints = files.map { it.fingerprint() })
  }

  private fun ViewerUiState.toggled(fileIndex: Int): ViewerUiState {
    val entry = CollapsedFile(files[fileIndex].displayPath, fingerprints[fileIndex])
    return copy(collapsed = if (entry in collapsed) collapsed - entry else collapsed + entry)
  }

  private fun ViewerUiState.expanded(fileIndex: Int): ViewerUiState = if (isCollapsed(fileIndex)) toggled(fileIndex) else this

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
  fun truncatedNoticeShownWhenTruncated() = runComposeUiTest {
    setContent { ViewerScreen(stateOf(listOf(modified("src/a.kt"))).copy(truncated = true), {}) }
    onNodeWithTag("diff-truncated-notice").assertIsDisplayed()
    onNodeWithText("Some files are not shown because the diff is too large.", useUnmergedTree = true).assertIsDisplayed()
  }

  @Test
  fun truncatedNoticeHiddenOtherwise() = runComposeUiTest {
    setContent { ViewerScreen(stateOf(listOf(modified("src/a.kt"))), {}) }
    onNodeWithTag("diff-truncated-notice").assertDoesNotExist()
  }

  @Test
  fun noticeDoesNotShiftListItems() = runComposeUiTest {
    setContent { ViewerScreen(stateOf(listOf(modified("src/a.kt"))).copy(truncated = true), {}) }
    val notice = onNodeWithTag("diff-truncated-notice").fetchSemanticsNode().boundsInRoot
    val header = onNodeWithTag("file-header-0").fetchSemanticsNode().boundsInRoot
    assertTrue(header.top >= notice.bottom)
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
    setContent { KdvTheme(colorTheme = ColorTheme.Light, tabWidth = 4) { ViewerScreen(stateOf(listOf(file)), {}) } }
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
    val padding = with(density) { 12.dp.toPx() }
    assertTrue(kotlin.math.abs(header.right - padding - stats.right) <= 1f, "stats=$stats header=$header")
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
  fun modeSwitchKeepsRemovedLineAtTheTop() = runComposeUiTest {
    val body = buildList {
      repeat(60) { add(DiffLine(LineKind.Context, "before$it", it + 1, it + 1)) }
      add(DiffLine(LineKind.Removed, "REMOVED_LINE", 61, null))
      add(DiffLine(LineKind.Added, "ADDED_LINE", null, 61))
      repeat(60) { add(DiffLine(LineKind.Context, "after$it", it + 62, it + 62)) }
    }
    val file = FileDiff("long.kt", "long.kt", ChangeType.Modified, false, DiffContent.Text(listOf(Hunk(1, 121, 1, 121, "", body))))
    val ui = stateOf(listOf(file)).let { it.copy(split = splitRowsOf(it.rows)) }
    var mode by mutableStateOf(DiffMode.Unified)
    setContent { ViewerScreen(ui, {}, mode = mode) }
    onNodeWithTag("diff-list").performScrollToIndex(ui.rows.indexOfFirst { it is DiffRow.Line && it.line.text == "REMOVED_LINE" })
    waitForIdle()
    val listTop = onNodeWithTag("diff-list").fetchSemanticsNode().boundsInRoot.top
    val unifiedTop = onNodeWithText("REMOVED_LINE").fetchSemanticsNode().boundsInRoot.top - listTop
    mode = DiffMode.Split
    waitForIdle()
    val splitTop = onNodeWithText("REMOVED_LINE").fetchSemanticsNode().boundsInRoot.top - listTop
    assertEquals(unifiedTop, splitTop)
    onAllNodesWithText("before59").assertCountEquals(0)
  }

  @Test
  fun reloadButtonInvokesCallback() = runComposeUiTest {
    var count = 0
    setContent { ViewerScreen(stateOf(emptyList()), { count++ }) }
    onNodeWithTag("reload-button").performClick()
    assertEquals(1, count)
  }

  @Test
  fun modeControlSwitchesToSplit() = runComposeUiTest {
    val changes = mutableListOf<DiffMode>()
    setContent { ViewerScreen(stateOf(emptyList()), {}, mode = DiffMode.Unified, onModeChange = { changes += it }) }
    onNodeWithTag("diff-mode-split").performClick()
    assertEquals(listOf(DiffMode.Split), changes)
  }

  @Test
  fun clickingCurrentModeIsNoOp() = runComposeUiTest {
    val changes = mutableListOf<DiffMode>()
    setContent { ViewerScreen(stateOf(emptyList()), {}, mode = DiffMode.Unified, onModeChange = { changes += it }) }
    onNodeWithTag("diff-mode-unified").performClick()
    assertEquals(emptyList(), changes)
  }

  @Test
  fun modeControlMarksSelected() = runComposeUiTest {
    setContent { ViewerScreen(stateOf(emptyList()), {}, mode = DiffMode.Split) }
    assertEquals(true, onNodeWithTag("diff-mode-split").fetchSemanticsNode().config[SemanticsProperties.Selected])
    assertEquals(false, onNodeWithTag("diff-mode-unified").fetchSemanticsNode().config[SemanticsProperties.Selected])
  }

  @Test
  fun modeControlSitsLeftOfReloadInsideTitleBarClientRegion() = runComposeUiTest {
    val regions = TitleBarRegions()
    setContent {
      CompositionLocalProvider(LocalTitleBarRegions provides regions) {
        ViewerScreen(stateOf(emptyList()), {})
      }
    }
    val control = onNodeWithTag("diff-mode-control").fetchSemanticsNode().boundsInRoot
    val reload = onNodeWithTag("reload-button").fetchSemanticsNode().boundsInRoot
    assertTrue(regions.isClientArea(control.center))
    val header = onNodeWithTag("repo-header").fetchSemanticsNode().boundsInRoot
    val gap = reload.left - control.right
    assertTrue(kotlin.math.abs(gap - with(density) { 8.dp.toPx() }) <= with(density) { 0.5.dp.toPx() }, "gap=$gap control=$control reload=$reload")
    assertTrue(control.right > header.center.x, "control=$control header=$header")
    onNodeWithTag("diff-mode-split").assertWidthIsEqualTo(32.dp)
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
  fun clickingTheBaseTagOpensThePicker() = runComposeUiTest {
    var switched: BaseSpec? = null
    setContent {
      ViewerScreen(
        stateOf(emptyList()).copy(base = ResolvedBase.Commit("origin/develop", "m1", "a1b2c3d"), head = HeadRef.Branch("feature/x")),
        {},
        loadBranches = { listOf(BranchRef("main", false)) },
        onSwitchBase = {
          switched = it
          null
        },
      )
    }
    onNodeWithTag("base-picker").performClick()
    waitForIdle()
    onNodeWithTag("base-item-local-main").performClick()
    waitForIdle()
    assertEquals(BaseSpec.Ref("main"), switched)
  }

  @Test
  fun baseTagAndPopupAreTitleBarClientRegions() = runComposeUiTest {
    val regions = TitleBarRegions()
    setContent {
      CompositionLocalProvider(LocalTitleBarRegions provides regions) {
        ViewerScreen(stateOf(emptyList()).copy(base = ResolvedBase.Commit("origin/develop", "m1", "a1b2c3d"), head = HeadRef.Branch("feature/x")), {})
      }
    }
    assertTrue(regions.isClientArea(onNodeWithTag("base-picker").fetchSemanticsNode().boundsInWindow.center))
    onNodeWithTag("base-picker").performClick()
    waitForIdle()
    val popup = onNodeWithTag("base-popup").fetchSemanticsNode().boundsInWindow
    assertTrue(regions.isClientArea(popup.center))
    assertFalse(regions.isClientArea(Offset(popup.right + 20f, popup.center.y)))
  }

  @Test
  fun baseLabelPrecedesHead() = runComposeUiTest {
    setContent {
      ViewerScreen(stateOf(emptyList()).copy(base = ResolvedBase.Commit("origin/develop", "m1", "a1b2c3d"), head = HeadRef.Branch("feature/x")), {})
    }
    onNodeWithTag("base-label", useUnmergedTree = true).assertTextEquals("origin/develop")
    onNodeWithText("...", useUnmergedTree = true).assertExists()
    onNodeWithText("→", useUnmergedTree = true).assertDoesNotExist()
    onNodeWithText("a1b2c3d", substring = true, useUnmergedTree = true).assertDoesNotExist()
    val base = onNodeWithTag("base-label", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    val head = onNodeWithTag("head-label", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    assertTrue(base.right <= head.left, "base=$base head=$head")
  }

  @Test
  fun headBaseShowsUncommittedChangesWithoutArrow() = runComposeUiTest {
    setContent { ViewerScreen(stateOf(emptyList()).copy(base = ResolvedBase.Commit("HEAD", "h1", "h1abc"), head = HeadRef.Branch("develop")), {}) }
    onNodeWithTag("base-label", useUnmergedTree = true).assertTextEquals("Uncommitted changes")
    onNodeWithTag("head-label", useUnmergedTree = true).assertTextEquals("develop")
    onNodeWithText("→", useUnmergedTree = true).assertDoesNotExist()
  }

  @Test
  fun hoveringBaseShowsNoTooltip() = runComposeUiTest {
    setContent {
      ViewerScreen(stateOf(emptyList()).copy(base = ResolvedBase.Commit("origin/develop", "m1", "a1b2c3d"), head = HeadRef.Branch("feature/x")), {})
    }
    onNodeWithTag("base-label", useUnmergedTree = true).performMouseInput { moveTo(center) }
    mainClock.advanceTimeBy(3_000)
    waitForIdle()
    onAllNodesWithText("merge-base", substring = true).assertCountEquals(0)
  }

  @Test
  fun emptyTreeBaseShowsOnlyHead() = runComposeUiTest {
    setContent { ViewerScreen(stateOf(emptyList()).copy(base = ResolvedBase.EmptyTree, head = HeadRef.Branch("main")), {}) }
    onNodeWithTag("base-label", useUnmergedTree = true).assertDoesNotExist()
    onNodeWithTag("head-label", useUnmergedTree = true).assertTextEquals("main")
  }

  @Test
  fun unresolvedBaseShowsOnlyHead() = runComposeUiTest {
    setContent { ViewerScreen(stateOf(emptyList()).copy(base = null, head = HeadRef.Branch("main")), {}) }
    onNodeWithTag("base-label", useUnmergedTree = true).assertDoesNotExist()
    onNodeWithTag("head-label", useUnmergedTree = true).assertTextEquals("main")
  }

  @Test
  fun hiddenFromStartHasNoTreeAndFullWidthDiff() = runComposeUiTest {
    setContent {
      Box(modifier = Modifier.size(800.dp, 600.dp)) {
        ViewerScreen(stateOf(listOf(modified("a.kt"))), {}, sidebarVisible = false)
      }
    }
    onNodeWithTag("tree-panel").assertDoesNotExist()
    val diff = onNodeWithTag("diff-column").getUnclippedBoundsInRoot()
    assertEquals(0.dp, diff.left)
    assertEquals(800.dp, diff.right - diff.left)
  }

  @Test
  fun hidingRemovesTreeAfterAnimation() = runComposeUiTest {
    var visible by mutableStateOf(true)
    setContent {
      Box(modifier = Modifier.size(800.dp, 600.dp)) {
        ViewerScreen(stateOf(listOf(modified("a.kt"))), {}, sidebarVisible = visible)
      }
    }
    mainClock.autoAdvance = false
    visible = false
    mainClock.advanceTimeBy(100)
    onNodeWithTag("tree-panel").assertExists()
    val diff = onNodeWithTag("diff-column").getUnclippedBoundsInRoot()
    assertEquals(800.dp, diff.right - diff.left)
    mainClock.advanceTimeBy(200)
    onNodeWithTag("tree-panel").assertDoesNotExist()
  }

  @Test
  fun showingNarrowsDiffOnlyAtEnd() = runComposeUiTest {
    var visible by mutableStateOf(false)
    setContent {
      Box(modifier = Modifier.size(800.dp, 600.dp)) {
        ViewerScreen(stateOf(listOf(modified("a.kt"))), {}, sidebarVisible = visible)
      }
    }
    mainClock.autoAdvance = false
    visible = true
    mainClock.advanceTimeBy(100)
    onNodeWithTag("tree-panel").assertExists()
    val midway = onNodeWithTag("diff-column").getUnclippedBoundsInRoot()
    assertEquals(800.dp, midway.right - midway.left)
    mainClock.advanceTimeBy(200)
    val tree = onNodeWithTag("tree-panel").getUnclippedBoundsInRoot()
    val diff = onNodeWithTag("diff-column").getUnclippedBoundsInRoot()
    assertEquals(DefaultTreeWidth + 1.dp, diff.left)
    assertEquals(800.dp - DefaultTreeWidth - 1.dp, diff.right - diff.left)
    assertEquals(0.dp, tree.left)
  }

  @Test
  fun hideShowKeepsTreeWidthAndScroll() = runComposeUiTest {
    var visible by mutableStateOf(true)
    setContent {
      Box(modifier = Modifier.size(800.dp, 600.dp)) {
        ViewerScreen(stateOf((0 until 40).map { modified("f${it.toString().padStart(2, '0')}.kt") }), {}, sidebarVisible = visible)
      }
    }
    onNodeWithTag("tree-divider").performMouseInput {
      moveTo(center)
      press()
      moveBy(Offset(100f, 0f))
      release()
    }
    onNodeWithTag("file-tree").performScrollToIndex(30)
    waitForIdle()
    val width = onNodeWithTag("tree-panel").getUnclippedBoundsInRoot().let { it.right - it.left }
    val row = onNodeWithTag("tree-file-30").getUnclippedBoundsInRoot()
    visible = false
    waitForIdle()
    onNodeWithTag("tree-panel").assertDoesNotExist()
    visible = true
    waitForIdle()
    assertEquals(width, onNodeWithTag("tree-panel").getUnclippedBoundsInRoot().let { it.right - it.left })
    assertEquals(row, onNodeWithTag("tree-file-30").getUnclippedBoundsInRoot())
  }

  @Test
  fun hideShowKeepsFilterText() = runComposeUiTest {
    var visible by mutableStateOf(true)
    setContent { ViewerScreen(stateOf(listOf(modified("a.kt"), modified("b.kt"))), {}, sidebarVisible = visible) }
    onNodeWithTag("tree-filter").performTextInput("b.kt")
    visible = false
    waitForIdle()
    visible = true
    waitForIdle()
    onNodeWithTag("tree-file-0").assertDoesNotExist()
    onNodeWithTag("tree-file-1").assertExists()
  }

  @Test
  fun leadingInsetPushesToggleRight() = runComposeUiTest {
    setContent { ViewerScreen(stateOf(listOf(modified("a.kt"))), {}, titleBarLeadingInset = 78.dp) }
    val toggle = onNodeWithTag("sidebar-control").fetchSemanticsNode().boundsInRoot
    assertTrue(toggle.left >= with(density) { 78.dp.toPx() }, "toggle=$toggle")
  }

  @Test
  fun toggleSitsLeftOfRepoName() = runComposeUiTest {
    setContent { ViewerScreen(stateOf(listOf(modified("a.kt"))), {}) }
    val toggle = onNodeWithTag("sidebar-control").fetchSemanticsNode().boundsInRoot
    val name = onNodeWithTag("repo-name").fetchSemanticsNode().boundsInRoot
    assertTrue(toggle.right <= name.left, "toggle=$toggle name=$name")
  }

  @Test
  fun sidebarSegmentsMarkVisibility() = runComposeUiTest {
    var visible by mutableStateOf(true)
    setContent { ViewerScreen(stateOf(listOf(modified("a.kt"))), {}, sidebarVisible = visible) }
    assertEquals(true, onNodeWithTag("sidebar-shown").fetchSemanticsNode().config[SemanticsProperties.Selected])
    assertEquals(false, onNodeWithTag("sidebar-hidden").fetchSemanticsNode().config[SemanticsProperties.Selected])
    visible = false
    waitForIdle()
    assertEquals(false, onNodeWithTag("sidebar-shown").fetchSemanticsNode().config[SemanticsProperties.Selected])
    assertEquals(true, onNodeWithTag("sidebar-hidden").fetchSemanticsNode().config[SemanticsProperties.Selected])
  }

  @Test
  fun selectingTheOtherSidebarSegmentTogglesAndControlIsClientRegion() = runComposeUiTest {
    val regions = TitleBarRegions()
    var toggles = 0
    setContent {
      CompositionLocalProvider(LocalTitleBarRegions provides regions) {
        ViewerScreen(stateOf(listOf(modified("a.kt"))), {}, onToggleSidebar = { toggles++ })
      }
    }
    val control = onNodeWithTag("sidebar-control").fetchSemanticsNode().boundsInWindow
    assertTrue(regions.isClientArea(control.center))
    onNodeWithTag("sidebar-shown").performClick()
    assertEquals(0, toggles)
    onNodeWithTag("sidebar-hidden").performClick()
    assertEquals(1, toggles)
    onNodeWithTag("sidebar-hidden").assertWidthIsEqualTo(32.dp)
  }

  @Test
  fun toggleShowsInEmptyState() = runComposeUiTest {
    setContent { ViewerScreen(stateOf(emptyList()), {}) }
    onNodeWithTag("sidebar-control").assertIsDisplayed()
  }

  @Test
  fun repoNameAlignsWithDiffContentWhenTreeShown() = runComposeUiTest {
    setContent {
      Box(modifier = Modifier.size(800.dp, 600.dp)) {
        ViewerScreen(stateOf(listOf(modified("a.kt"))), {})
      }
    }
    val diff = onNodeWithTag("diff-column").getUnclippedBoundsInRoot()
    val name = onNodeWithTag("repo-name").getUnclippedBoundsInRoot()
    assertEquals(diff.left + 20.dp, name.left)
  }

  @Test
  fun repoNameFollowsControlWhenTreeHidden() = runComposeUiTest {
    setContent {
      Box(modifier = Modifier.size(800.dp, 600.dp)) {
        ViewerScreen(stateOf(listOf(modified("a.kt"))), {}, sidebarVisible = false)
      }
    }
    val control = onNodeWithTag("sidebar-control").getUnclippedBoundsInRoot()
    val name = onNodeWithTag("repo-name").getUnclippedBoundsInRoot()
    assertEquals(control.right + 12.dp, name.left)
  }

  @Test
  fun repoNameStaysPutWhenDividerDragged() = runComposeUiTest {
    setContent {
      Box(modifier = Modifier.size(800.dp, 600.dp)) {
        ViewerScreen(stateOf(listOf(modified("a.kt"))), {})
      }
    }
    val before = onNodeWithTag("repo-name").getUnclippedBoundsInRoot()
    onNodeWithTag("tree-divider").performMouseInput {
      moveTo(center)
      press()
      moveBy(Offset(100f, 0f))
      release()
    }
    waitForIdle()
    val diff = onNodeWithTag("diff-column").getUnclippedBoundsInRoot()
    assertTrue(diff.left > before.left - 20.dp, "divider drag did not move the diff column: diff=$diff")
    assertEquals(before, onNodeWithTag("repo-name").getUnclippedBoundsInRoot())
  }

  @Test
  fun headerLeavesRoomAroundItsContent() = runComposeUiTest {
    setContent { ViewerScreen(stateOf(emptyList()).copy(head = HeadRef.Branch("main")), {}) }
    val header = onNodeWithTag("repo-header").fetchSemanticsNode().boundsInRoot
    val reload = onNodeWithTag("reload-button").fetchSemanticsNode().boundsInRoot
    val minHeight = with(density) { 64.dp.toPx() }
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
  fun headerSpansFullWidthAndTreeSitsBelowIt() = runComposeUiTest {
    setContent {
      Box(modifier = Modifier.size(800.dp, 600.dp)) {
        ViewerScreen(stateOf(listOf(modified("a.kt"))), {})
      }
    }
    val tree = onNodeWithTag("tree-panel").fetchSemanticsNode().boundsInRoot
    val header = onNodeWithTag("repo-header").fetchSemanticsNode().boundsInRoot
    val root = onRoot().fetchSemanticsNode().boundsInRoot
    assertEquals(root.left, header.left)
    assertEquals(root.right, header.right)
    assertEquals(root.top, header.top)
    assertEquals(with(density) { 64.dp.toPx() }, header.height)
    assertTrue(tree.top >= header.bottom, "tree=$tree header=$header")
    assertEquals(root.bottom, tree.bottom)
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
    setContent { KdvTheme(colorTheme = ColorTheme.Light) { ViewerScreen(stateOf(listOf(file)).copy(highlights = listOf(slot)), {}) } }
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
    setContent { KdvTheme(colorTheme = ColorTheme.Light) { ViewerScreen(stateOf(listOf(file)).copy(highlights = listOf(slot)), {}) } }
    assertEquals(emptyList(), lineText("fun x").spanStyles)
    slot.value = keywordHighlight()
    waitForIdle()
    val ranges = lineText("fun x").spanStyles
    assertEquals(1, ranges.size)
    assertEquals(LightSyntaxTheme.resolve("keyword")!!.color, ranges[0].item.color)
  }

  @Test
  fun lineWithoutSpansStaysPlain() = runComposeUiTest {
    setContent { KdvTheme(colorTheme = ColorTheme.Light) { ViewerScreen(stateOf(listOf(keywordFile())), {}) } }
    assertEquals(emptyList(), lineText("fun x").spanStyles)
  }

  @Test
  fun headerPathGrowsWithFontSize() = runComposeUiTest {
    var fontSize by mutableStateOf(12)
    setContent { KdvTheme(colorTheme = ColorTheme.Light, fontSize = fontSize) { ViewerScreen(stateOf(listOf(modified("a.kt"))), {}) } }
    val small = onNodeWithTag("file-header-0").fetchSemanticsNode().boundsInRoot.height
    fontSize = 24
    waitForIdle()
    val large = onNodeWithTag("file-header-0").fetchSemanticsNode().boundsInRoot.height
    assertTrue(large > small, "small=$small large=$large")
  }

  @Test
  fun lineNumberColumnWidensWithFontSize() = runComposeUiTest {
    var fontSize by mutableStateOf(24)
    setContent { KdvTheme(colorTheme = ColorTheme.Light, fontSize = fontSize) { ViewerScreen(stateOf(listOf(modified("a.kt"))), {}) } }
    onAllNodesWithText("1", useUnmergedTree = true)[0].assertWidthIsEqualTo(90.dp)
    fontSize = 12
    waitForIdle()
    onAllNodesWithText("1", useUnmergedTree = true)[0].assertWidthIsEqualTo(42.dp)
  }

  @Test
  fun clickingHeaderCollapsesAndExpandsFile() = runComposeUiTest {
    var ui by mutableStateOf(stateOf(listOf(modified("a.kt"), modified("b.kt"))))
    setContent { ViewerScreen(ui, {}, onToggleFile = { ui = ui.toggled(it) }) }
    assertEquals(2, onAllNodesWithText("ONE").fetchSemanticsNodes().size)
    onNodeWithTag("file-header-0").performClick()
    waitForIdle()
    assertEquals(1, onAllNodesWithText("ONE").fetchSemanticsNodes().size)
    onNodeWithTag("file-header-1").assertIsDisplayed()
    onNodeWithTag("file-header-0").performClick()
    waitForIdle()
    assertEquals(2, onAllNodesWithText("ONE").fetchSemanticsNodes().size)
  }

  @Test
  fun largeDiffLabelShowsOnlyOnLargeFiles() = runComposeUiTest {
    val small = FileDiff(null, "small.kt", ChangeType.Added, false, DiffContent.Text(listOf(Hunk(0, 0, 1, 1, "", listOf(DiffLine(LineKind.Added, "x", null, 1))))))
    setContent { ViewerScreen(stateOf(listOf(modified("big.json"), small), largeDiffLines = 2), {}) }
    onNodeWithTag("file-large-0", useUnmergedTree = true).assertTextEquals("Large diff")
    onNodeWithTag("file-large-1", useUnmergedTree = true).assertDoesNotExist()
  }

  @Test
  fun largeDiffLabelMatchesChangeBadgeHeight() = runComposeUiTest {
    setContent { ViewerScreen(stateOf(listOf(modified("big.json")), largeDiffLines = 2), {}) }
    val badge = onNodeWithTag("file-badge-0", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    val label = onNodeWithTag("file-large-0", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    assertEquals(badge.height, label.height)
  }

  @Test
  fun largeDiffExpandsOnHeaderClick() = runComposeUiTest {
    var ui by mutableStateOf(stateOf(listOf(modified("big.json")), largeDiffLines = 2))
    setContent { ViewerScreen(ui, {}, onToggleFile = { index -> ui = ui.copy(expanded = ui.expanded + CollapsedFile(ui.files[index].displayPath, ui.fingerprints[index])) }) }
    onNodeWithText("ONE").assertDoesNotExist()
    onNodeWithTag("file-header-0").performClick()
    waitForIdle()
    onNodeWithText("ONE").assertIsDisplayed()
  }

  @Test
  fun largeDiffWithThreadExpandsOncePerLoad() = runComposeUiTest {
    var ui by mutableStateOf(stateOf(listOf(modified("a.kt")), largeDiffLines = 2).copy(loadId = 1))
    var review by mutableStateOf(snapshotOf(1, 1, listOf(staticThread()), Placement.OnFile(0)))
    val expanded = mutableListOf<Int>()
    val entry = CollapsedFile(ui.files[0].displayPath, ui.fingerprints[0])
    setContent {
      ViewerScreen(
        ui,
        {},
        onToggleFile = { ui = ui.copy(expanded = if (entry in ui.expanded) ui.expanded - entry else ui.expanded + entry) },
        onExpandFile = {
          expanded += it
          ui = ui.copy(expanded = ui.expanded + entry)
        },
        review = review,
      )
    }
    waitForIdle()
    assertEquals(listOf(0), expanded)
    onNodeWithTag("file-header-0").performClick()
    waitForIdle()
    assertTrue(ui.isCollapsed(0))
    assertEquals(listOf(0), expanded)
    ui = ui.copy(loadId = 2)
    review = snapshotOf(2, 2, listOf(staticThread()), Placement.OnFile(0))
    waitForIdle()
    assertEquals(listOf(0, 0), expanded)
  }

  @Test
  fun largeDiffWithoutThreadStaysCollapsed() = runComposeUiTest {
    val expanded = mutableListOf<Int>()
    setContent { ViewerScreen(stateOf(listOf(modified("a.kt")), largeDiffLines = 2).copy(loadId = 1), {}, onExpandFile = { expanded += it }, review = snapshotOf(1, 1, emptyList(), null)) }
    waitForIdle()
    assertEquals(emptyList(), expanded)
  }

  @Test
  fun chevronClickTogglesFile() = runComposeUiTest {
    val toggled = mutableListOf<Int>()
    setContent { ViewerScreen(stateOf(listOf(modified("a.kt"))), {}, onToggleFile = { toggled += it }) }
    onNodeWithTag("file-chevron-0", useUnmergedTree = true).assertIsDisplayed()
    onNodeWithTag("file-chevron-0", useUnmergedTree = true).performClick()
    assertEquals(listOf(0), toggled)
  }

  @Test
  fun clickingCollapsedFileInTreeExpandsAndShowsIt() = runComposeUiTest {
    val files = (0 until 30).map { modified("f${it.toString().padStart(2, '0')}.kt", 20) }
    var ui by mutableStateOf(stateOf(files).let { it.toggled(25) })
    setContent { ViewerScreen(ui, {}, onExpandFile = { ui = ui.expanded(it) }) }
    onNodeWithTag("file-tree").performScrollToNode(hasTestTag("tree-file-25"))
    onNodeWithTag("tree-file-25").performClick()
    waitForIdle()
    assertFalse(ui.isCollapsed(25))
    onNodeWithTag("file-header-25").assertIsDisplayed()
    onNodeWithTag("file-header-0").assertDoesNotExist()
  }

  @Test
  fun headerStaysPinnedWhileScrollingThroughFile() = runComposeUiTest {
    setContent { ViewerScreen(stateOf(listOf(modified("long.kt", 200), modified("next.kt"))), {}) }
    onNodeWithTag("diff-list").performScrollToIndex(100)
    waitForIdle()
    onNodeWithTag("file-header-0").assertIsDisplayed()
  }

  @Test
  fun nextHeaderStaysPinnedAfterCollapsingFirstFile() = runComposeUiTest {
    var ui by mutableStateOf(stateOf(listOf(modified("a.kt", 5), modified("long.kt", 200), modified("next.kt"))))
    setContent { ViewerScreen(ui, {}, onToggleFile = { ui = ui.toggled(it) }) }
    onNodeWithTag("file-header-0").performClick()
    waitForIdle()
    repeat(30) {
      onNodeWithTag("diff-list").performMouseInput { scroll(1f) }
      waitForIdle()
    }
    onNodeWithTag("file-header-1").assertIsDisplayed()
    assertPinnedAtTop("file-header-1")
  }

  @Test
  fun collapsingPinnedHeaderKeepsItAtTop() = runComposeUiTest {
    var ui by mutableStateOf(stateOf(listOf(modified("long.kt", 200), modified("next.kt"))))
    setContent { ViewerScreen(ui, {}, onToggleFile = { ui = ui.toggled(it) }) }
    onNodeWithTag("diff-list").performScrollToIndex(100)
    waitForIdle()
    onNodeWithTag("file-header-0").performClick()
    waitForIdle()
    assertPinnedAtTop("file-header-0")
    onNodeWithTag("file-header-1").assertIsDisplayed()
  }

  @Test
  fun collapsingLastFileAtBottomShowsContent() = runComposeUiTest {
    val files = (0 until 30).map { modified("f${it.toString().padStart(2, '0')}.kt", 20) }
    var ui by mutableStateOf(stateOf(files))
    setContent { ViewerScreen(ui, {}, onToggleFile = { ui = ui.toggled(it) }) }
    onNodeWithTag("diff-list").performScrollToIndex(ui.rows.lastIndex)
    waitForIdle()
    onNodeWithTag("file-header-29").performClick()
    waitForIdle()
    assertTrue(ui.isCollapsed(29))
    onNodeWithTag("file-header-29").assertIsDisplayed()
    onNodeWithTag("file-header-28").assertIsDisplayed()
  }

  private val at = Instant.fromEpochSeconds(0)

  private fun reviewState(ui: ViewerUiState): ReviewState = ReviewState(CoroutineScope(Dispatchers.Unconfined), Dispatchers.Unconfined) { at }.also { it.onViewerState(ui) }

  private fun staticThread(id: String = "t1", resolved: Boolean = false) = ReviewThread(id, Anchor.File("a.kt"), listOf(Comment("c$id", Author.User, "note $id", at)), resolved, false)

  private fun snapshotOf(loadId: Long, placementsLoadId: Long, threads: List<ReviewThread>, placement: Placement?): ReviewSnapshot {
    val byId = if (placement == null) emptyMap() else threads.associate { it.id to placement }
    return ReviewSnapshot(threads, buildPlacements(placementsLoadId, threads, byId), null, loadId, false)
  }

  private val reviewUi get() = stateOf(listOf(modified("a.kt"))).copy(loadId = 1)

  private val threeFiles get() = listOf(modified("src/a.kt"), modified("src/b.kt"), modified("README.md"))

  @Test
  fun typingInTreeFilterHidesNonMatchingRows() = runComposeUiTest {
    setContent { ViewerScreen(stateOf(threeFiles), {}) }
    onNodeWithTag("tree-filter").performTextInput("b.kt")
    onNodeWithTag("tree-dir-0").assertIsDisplayed()
    onNodeWithTag("tree-file-1").assertIsDisplayed()
    onNodeWithTag("tree-file-0").assertDoesNotExist()
    onNodeWithTag("tree-file-2").assertDoesNotExist()
    onNodeWithTag("file-header-0").assertExists()
  }

  @Test
  fun whitespaceQueryDoesNotFilter() = runComposeUiTest {
    setContent { ViewerScreen(stateOf(threeFiles), {}) }
    onNodeWithTag("tree-filter").performTextInput("  ")
    onNodeWithTag("tree-file-0").assertIsDisplayed()
    onNodeWithTag("tree-file-2").assertIsDisplayed()
    onNodeWithTag("tree-chevron-0", useUnmergedTree = true).assertIsDisplayed()
  }

  @Test
  fun clearingTreeFilterRestoresCollapseState() = runComposeUiTest {
    setContent { ViewerScreen(stateOf(threeFiles), {}) }
    onNodeWithTag("tree-dir-0").performClick()
    onNodeWithTag("tree-file-0").assertDoesNotExist()
    onNodeWithTag("tree-filter").performTextInput("a.kt")
    onNodeWithTag("tree-file-0").assertIsDisplayed()
    onNodeWithTag("tree-dir-0").performClick()
    onNodeWithTag("tree-file-0").assertIsDisplayed()
    onNodeWithTag("search-clear").performClick()
    onNodeWithTag("tree-file-0").assertDoesNotExist()
    onNodeWithTag("tree-file-2").assertIsDisplayed()
  }

  @Test
  fun noMatchShowsEmptyLine() = runComposeUiTest {
    setContent { ViewerScreen(stateOf(threeFiles), {}) }
    onNodeWithTag("tree-filter").performTextInput("zzz")
    onNodeWithTag("tree-empty").assertTextEquals("No matching files")
  }

  @Test
  fun clickingFilteredRowExpandsThatFile() = runComposeUiTest {
    val expanded = mutableListOf<Int>()
    setContent { ViewerScreen(stateOf(threeFiles), {}, onExpandFile = { expanded += it }) }
    onNodeWithTag("tree-filter").performTextInput("README")
    onNodeWithTag("tree-file-2").performClick()
    assertEquals(listOf(2), expanded)
  }

  @Test
  fun filterAppliesToRowsThatStreamInLater() = runComposeUiTest {
    var ui by mutableStateOf(stateOf(listOf(modified("src/a.kt")), LoadStatus.Loading))
    setContent { ViewerScreen(ui, {}) }
    onNodeWithTag("tree-filter").performTextInput("b")
    onNodeWithTag("tree-file-0").assertDoesNotExist()
    ui = stateOf(listOf(modified("src/a.kt"), modified("src/b.kt"), modified("c.kt")))
    waitForIdle()
    onNodeWithTag("tree-file-1").assertIsDisplayed()
    onNodeWithTag("tree-file-0").assertDoesNotExist()
    onNodeWithTag("tree-file-2").assertDoesNotExist()
  }

  @Test
  fun treeFilterSurvivesReloadAndResetsOnRepoChange() = runComposeUiTest {
    var ui by mutableStateOf(stateOf(threeFiles).copy(loadId = 1))
    setContent { ViewerScreen(ui, {}) }
    onNodeWithTag("tree-filter").performTextInput("README")
    ui = stateOf(threeFiles).copy(loadId = 2)
    waitForIdle()
    onNodeWithTag("tree-file-0").assertDoesNotExist()
    onNodeWithTag("tree-file-2").assertIsDisplayed()
    ui = stateOf(threeFiles).copy(repo = RepoPath("/other"), loadId = 3)
    waitForIdle()
    onNodeWithTag("tree-file-0").assertIsDisplayed()
    onNodeWithTag("tree-chevron-0", useUnmergedTree = true).assertIsDisplayed()
  }

  @Test
  fun hoveringSingleLineRowDoesNotShiftRows() = runComposeUiTest {
    val ui = stateOf(listOf(modified("a.kt", lines = 3))).copy(loadId = 1)
    val review = reviewState(ui)
    setContent {
      val snapshot by review.snapshot.collectAsState()
      CompositionLocalProvider(LocalReviewActions provides review) { ViewerScreen(ui, {}, review = snapshot) }
    }
    val before = onNodeWithText("ctx0").fetchSemanticsNode().boundsInRoot
    onNodeWithText("TWO").performMouseInput { moveTo(center) }
    waitForIdle()
    onNodeWithTag("add-comment").assertIsDisplayed()
    val after = onNodeWithText("ctx0").fetchSemanticsNode().boundsInRoot
    assertEquals(before, after)
  }

  @Test
  fun addCommentButtonSitsOnFirstLineOfWrappedRow() = runComposeUiTest {
    val long = "TWO " + "word ".repeat(80)
    val file = FileDiff(
      "a.kt",
      "a.kt",
      ChangeType.Modified,
      false,
      DiffContent.Text(listOf(Hunk(1, 1, 1, 1, "", listOf(DiffLine(LineKind.Added, long, null, 1))))),
    )
    val ui = stateOf(listOf(file)).copy(loadId = 1)
    val review = reviewState(ui)
    setContent {
      val snapshot by review.snapshot.collectAsState()
      CompositionLocalProvider(LocalReviewActions provides review) {
        Box(modifier = Modifier.width(800.dp)) { ViewerScreen(ui, {}, review = snapshot) }
      }
    }
    val row = onNodeWithText(long).fetchSemanticsNode().boundsInRoot
    val lineHeight = with(density) { 18.sp.toPx() }
    assertTrue(row.height > lineHeight * 2, "row=$row")
    onNodeWithText(long).performMouseInput { moveTo(center) }
    waitForIdle()
    val button = onNodeWithTag("add-comment").fetchSemanticsNode().boundsInRoot
    assertTrue(abs(button.center.y - (row.top + lineHeight / 2)) <= 1f, "button=$button row=$row lineHeight=$lineHeight")
  }

  @Test
  fun addCommentButtonFitsInsideSingleLineRow() = runComposeUiTest {
    val ui = reviewUi
    val review = reviewState(ui)
    setContent {
      val snapshot by review.snapshot.collectAsState()
      CompositionLocalProvider(LocalReviewActions provides review, LocalDiffTypography provides diffTypography(FontFamily.Monospace, 9, 8, LightDiffColors)) {
        ViewerScreen(ui, {}, review = snapshot)
      }
    }
    val line = onNodeWithText("TWO").fetchSemanticsNode().boundsInRoot
    onNodeWithText("TWO").performMouseInput { moveTo(center) }
    waitForIdle()
    val button = onNodeWithTag("add-comment").fetchSemanticsNode().boundsInRoot
    assertTrue(button.height <= line.height, "button=$button line=$line")
  }

  @Test
  fun hoveringLineShowsAddCommentAndClickOpensComposerUnderIt() = runComposeUiTest {
    val ui = reviewUi
    val review = reviewState(ui)
    setContent {
      val snapshot by review.snapshot.collectAsState()
      CompositionLocalProvider(LocalReviewActions provides review) { ViewerScreen(ui, {}, review = snapshot) }
    }
    onNodeWithTag("add-comment").assertDoesNotExist()
    onNodeWithText("TWO").performMouseInput { moveTo(center) }
    waitForIdle()
    onNodeWithTag("add-comment").assertIsDisplayed()
    onNodeWithTag("add-comment").performClick()
    waitForIdle()
    val line = onNodeWithText("TWO").fetchSemanticsNode().boundsInRoot
    val composer = onNodeWithTag("comment-composer").fetchSemanticsNode().boundsInRoot
    assertTrue(composer.top >= line.bottom, "composer=$composer line=$line")
  }

  @Test
  fun typingAndCommandEnterCreatesThreadCard() = runComposeUiTest {
    val ui = reviewUi
    val review = reviewState(ui)
    setContent {
      val snapshot by review.snapshot.collectAsState()
      CompositionLocalProvider(LocalReviewActions provides review) { ViewerScreen(ui, {}, review = snapshot) }
    }
    onNodeWithText("TWO").performMouseInput { moveTo(center) }
    waitForIdle()
    onNodeWithTag("add-comment").performClick()
    waitForIdle()
    onNodeWithTag("comment-composer").performTextInput("looks wrong")
    onNodeWithTag("comment-composer").performKeyInput { withKeyDown(Key.MetaLeft) { pressKey(Key.Enter) } }
    waitForIdle()
    onNodeWithTag("thread-t1").assertIsDisplayed()
    onNodeWithText("looks wrong").assertIsDisplayed()
    onNodeWithTag("comment-composer").assertDoesNotExist()
  }

  @Test
  fun agentReplyAppearsInCard() = runComposeUiTest {
    val ui = reviewUi
    val review = reviewState(ui)
    review.startDraft(DraftTarget.NewThread(anchorOf(ui.files[0], 0, 1)!!, Placement.OnLine(LineLocation(0, 0, 1))))
    review.updateDraft("mine")
    review.submitDraft()
    setContent {
      val snapshot by review.snapshot.collectAsState()
      CompositionLocalProvider(LocalReviewActions provides review) { ViewerScreen(ui, {}, review = snapshot) }
    }
    onNodeWithText("mine").assertIsDisplayed()
    review.reply("t1", Author.Agent("claude"), "agent says")
    waitForIdle()
    onNodeWithText("agent says").assertIsDisplayed()
  }

  @Test
  fun fileCommentButtonOpensComposerWithoutCollapsing() = runComposeUiTest {
    val ui = reviewUi
    val review = reviewState(ui)
    var toggled = 0
    setContent {
      val snapshot by review.snapshot.collectAsState()
      CompositionLocalProvider(LocalReviewActions provides review) { ViewerScreen(ui, {}, review = snapshot, onToggleFile = { toggled++ }) }
    }
    onNodeWithTag("file-comment-0", useUnmergedTree = true).performClick()
    waitForIdle()
    assertEquals(0, toggled)
    onNodeWithText("TWO").assertIsDisplayed()
    val header = onNodeWithTag("file-header-0").fetchSemanticsNode().boundsInRoot
    val composer = onNodeWithTag("comment-composer").fetchSemanticsNode().boundsInRoot
    assertTrue(composer.top >= header.bottom, "composer=$composer header=$header")
  }

  @Test
  fun orphanThreadRendersInTopSection() = runComposeUiTest {
    val ui = reviewUi
    setContent { ViewerScreen(ui, {}, review = snapshotOf(1, 1, listOf(staticThread()), null)) }
    onNodeWithText("Comments on files no longer in the diff").assertIsDisplayed()
    onNodeWithTag("thread-t1").assertIsDisplayed()
    val orphan = onNodeWithTag("thread-t1").fetchSemanticsNode().boundsInRoot
    val header = onNodeWithTag("file-header-0").fetchSemanticsNode().boundsInRoot
    assertTrue(orphan.bottom <= header.top + 1f, "orphan=$orphan header=$header")
  }

  @Test
  fun orphanThreadsShowEvenWhenDiffIsEmpty() = runComposeUiTest {
    val ui = stateOf(emptyList()).copy(loadId = 1)
    setContent { ViewerScreen(ui, {}, review = snapshotOf(1, 1, listOf(staticThread()), null)) }
    onNodeWithText("No changes").assertDoesNotExist()
    onNodeWithTag("thread-t1").assertIsDisplayed()
  }

  @Test
  fun threadsAreHiddenWhilePlacementsAreStale() = runComposeUiTest {
    val ui = reviewUi
    setContent { ViewerScreen(ui, {}, review = snapshotOf(1, 0, listOf(staticThread()), null)) }
    onNodeWithTag("thread-t1").assertDoesNotExist()
    onNodeWithText("Comments on files no longer in the diff").assertDoesNotExist()
  }

  @Test
  fun fileLevelThreadShowsUnderHeaderAndHidesWhenCollapsed() = runComposeUiTest {
    var ui by mutableStateOf(reviewUi)
    val snapshot = snapshotOf(1, 1, listOf(staticThread()), Placement.OnFile(0))
    setContent { ViewerScreen(ui, {}, review = snapshot) }
    onNodeWithTag("thread-t1").assertIsDisplayed()
    ui = ui.toggled(0)
    waitForIdle()
    onNodeWithTag("thread-t1").assertDoesNotExist()
  }

  @Test
  fun typingInComposerDoesNotRecomposeDiffRows() = runComposeUiTest {
    val base = stateOf(listOf(modified("a.kt", 20), modified("b.kt", 20))).copy(loadId = 1)
    var rowReads = 0
    val countedRows = object : AbstractList<DiffRow>() {
      override val size: Int get() = base.rows.size

      override fun get(index: Int): DiffRow {
        rowReads++
        return base.rows[index]
      }
    }
    val ui = base.copy(rows = countedRows)
    val review = reviewState(ui)
    setContent {
      val snapshot by review.snapshot.collectAsState()
      CompositionLocalProvider(LocalReviewActions provides review) { ViewerScreen(ui, {}, review = snapshot) }
    }
    onNodeWithTag("file-comment-0", useUnmergedTree = true).performClick()
    waitForIdle()
    onNodeWithTag("comment-composer").performTextInput("a")
    waitForIdle()
    val before = rowReads
    onNodeWithTag("comment-composer").performTextInput("b")
    waitForIdle()
    onNodeWithTag("comment-composer").performTextInput("c")
    waitForIdle()
    assertEquals(before, rowReads)
    onNodeWithTag("comment-composer").assertTextEquals("abc")
  }

  @Test
  fun draftOnFileThatLeftTheDiffStaysVisibleAndCancellable() = runComposeUiTest {
    val first = stateOf(listOf(modified("a.kt"), modified("b.kt"))).copy(loadId = 1)
    var ui by mutableStateOf(first)
    val review = reviewState(first)
    review.startDraft(DraftTarget.NewThread(anchorOf(first.files[1], 0, 1)!!, Placement.OnLine(LineLocation(1, 0, 1))))
    review.updateDraft("still here")
    setContent {
      val snapshot by review.snapshot.collectAsState()
      CompositionLocalProvider(LocalReviewActions provides review) { ViewerScreen(ui, {}, review = snapshot) }
    }
    val second = stateOf(listOf(modified("a.kt"))).copy(loadId = 2)
    ui = second
    review.onViewerState(second)
    waitForIdle()
    onNodeWithText("Comments on files no longer in the diff").assertIsDisplayed()
    onNodeWithTag("comment-composer").assertIsDisplayed()
    onNodeWithTag("comment-composer").assertTextEquals("still here")
    onNodeWithTag("comment-composer").performKeyInput { pressKey(Key.Escape) }
    waitForIdle()
    onNodeWithTag("comment-composer").assertDoesNotExist()
    onNodeWithText("Comments on files no longer in the diff").assertDoesNotExist()
    onNodeWithText("TWO").performMouseInput { moveTo(center) }
    waitForIdle()
    onNodeWithTag("add-comment").performClick()
    waitForIdle()
    onNodeWithTag("comment-composer").assertIsDisplayed()
  }

  @Test
  fun orphanDraftShowsEvenWhenDiffIsEmpty() = runComposeUiTest {
    val first = stateOf(listOf(modified("a.kt"))).copy(loadId = 1)
    var ui by mutableStateOf(first)
    val review = reviewState(first)
    review.startDraft(DraftTarget.NewThread(Anchor.File("a.kt"), Placement.OnFile(0)))
    review.updateDraft("note")
    setContent {
      val snapshot by review.snapshot.collectAsState()
      CompositionLocalProvider(LocalReviewActions provides review) { ViewerScreen(ui, {}, review = snapshot) }
    }
    val second = stateOf(emptyList()).copy(loadId = 2)
    ui = second
    review.onViewerState(second)
    waitForIdle()
    onNodeWithText("No changes").assertDoesNotExist()
    onNodeWithTag("comment-composer").assertTextEquals("note")
  }

  private fun manyFilesUi() = stateOf((0 until 30).map { modified("f${it.toString().padStart(2, '0')}.kt", 20) }).copy(loadId = 1)

  @Test
  fun addCommentWithPendingDraftRevealsAndFocusesItInCollapsedFile() = runComposeUiTest {
    val initial = manyFilesUi()
    var ui by mutableStateOf(initial)
    val review = reviewState(initial)
    review.startDraft(DraftTarget.NewThread(anchorOf(initial.files[0], 0, 1)!!, Placement.OnLine(LineLocation(0, 0, 1))))
    review.updateDraft("keep")
    ui = ui.toggled(0)
    setContent {
      val snapshot by review.snapshot.collectAsState()
      CompositionLocalProvider(LocalReviewActions provides review) {
        ViewerScreen(ui, {}, review = snapshot, onExpandFile = { ui = ui.expanded(it) })
      }
    }
    onNodeWithTag("diff-list").performScrollToIndex(600)
    waitForIdle()
    onNodeWithTag("comment-composer").assertDoesNotExist()
    onAllNodesWithText("TWO").onLast().performMouseInput { moveTo(center) }
    waitForIdle()
    onNodeWithTag("add-comment").performClick()
    waitForIdle()
    assertFalse(ui.isCollapsed(0))
    onNodeWithTag("comment-composer").assertIsDisplayed()
    onNodeWithTag("comment-composer").assertTextEquals("keep")
    onNodeWithTag("comment-composer").assertIsFocused()
    assertEquals("keep", review.snapshot.value.draft?.text)
  }

  @Test
  fun fileCommentWithPendingDraftRevealsAndFocusesIt() = runComposeUiTest {
    val ui = manyFilesUi()
    val review = reviewState(ui)
    review.startDraft(DraftTarget.NewThread(Anchor.File("f00.kt"), Placement.OnFile(0)))
    review.updateDraft("file draft")
    setContent {
      val snapshot by review.snapshot.collectAsState()
      CompositionLocalProvider(LocalReviewActions provides review) { ViewerScreen(ui, {}, review = snapshot) }
    }
    onNodeWithTag("diff-list").performScrollToIndex(ui.rows.lastIndex)
    waitForIdle()
    onNodeWithTag("comment-composer").assertIsNotDisplayed()
    onNodeWithTag("file-comment-29", useUnmergedTree = true).performClick()
    waitForIdle()
    onNodeWithTag("comment-composer").assertIsDisplayed()
    onNodeWithTag("comment-composer").assertTextEquals("file draft")
    onNodeWithTag("comment-composer").assertIsFocused()
  }

  @Test
  fun noAddCommentButtonOnNoNewlineRow() = runComposeUiTest {
    val body = listOf(DiffLine(LineKind.Added, "x", null, 1), DiffLine(LineKind.NoNewlineAtEof, "", null, null))
    val file = FileDiff(null, "n.kt", ChangeType.Added, false, DiffContent.Text(listOf(Hunk(0, 0, 1, 1, "", body))))
    val ui = stateOf(listOf(file)).copy(loadId = 1)
    val review = reviewState(ui)
    setContent { CompositionLocalProvider(LocalReviewActions provides review) { ViewerScreen(ui, {}, review = ReviewSnapshot.Empty) } }
    onNodeWithText("No newline at end of file").performMouseInput { moveTo(center) }
    waitForIdle()
    onNodeWithTag("add-comment").assertDoesNotExist()
  }

  @Test
  fun footerShowsAfterLastFileWhenDone() = runComposeUiTest {
    val files = (0 until 30).map { modified("f$it.kt", 20) }
    setContent { ViewerScreen(stateOf(files), {}) }
    onNodeWithTag("diff-list").performScrollToNode(hasTestTag("end-of-diff"))
    onNodeWithTag("end-of-diff").assertIsDisplayed()
    onNodeWithText("It fits. You're done.").assertIsDisplayed()
  }

  @Test
  fun footerHiddenWhileLoading() = runComposeUiTest {
    setContent { ViewerScreen(stateOf(listOf(modified("a.kt")), status = LoadStatus.Loading), {}) }
    onNodeWithTag("end-of-diff").assertDoesNotExist()
  }

  @Test
  fun footerHiddenOnNoChanges() = runComposeUiTest {
    setContent { ViewerScreen(stateOf(emptyList()), {}) }
    onNodeWithText("No changes").assertIsDisplayed()
    onNodeWithTag("end-of-diff").assertDoesNotExist()
  }

  @Test
  fun footerStaysAfterCollapsingLastFile() = runComposeUiTest {
    val state = stateOf(listOf(modified("a.kt"), modified("b.kt"))).toggled(1)
    setContent { ViewerScreen(state, {}) }
    onNodeWithTag("end-of-diff").assertIsDisplayed()
  }

  @Test
  fun footerHidesOnReloadAndReturnsOnDone() = runComposeUiTest {
    val files = (0 until 30).map { modified("f$it.kt", 20) }
    var ui by mutableStateOf(stateOf(files).copy(loadId = 1))
    setContent { ViewerScreen(ui, {}) }
    onNodeWithTag("diff-list").performScrollToNode(hasTestTag("end-of-diff"))
    onNodeWithTag("end-of-diff").assertIsDisplayed()
    ui = ui.copy(status = LoadStatus.Loading, loadId = 2)
    waitForIdle()
    onNodeWithTag("end-of-diff").assertDoesNotExist()
    ui = stateOf(files).copy(loadId = 2)
    waitForIdle()
    onNodeWithTag("diff-list").performScrollToNode(hasTestTag("end-of-diff"))
    onNodeWithTag("end-of-diff").assertIsDisplayed()
  }

  private fun ComposeUiTest.listPixels() = onNodeWithTag("diff-list").captureToImage().toPixelMap()

  private fun ComposeUiTest.cornerOf(tag: String): IntOffset {
    val list = onNodeWithTag("diff-list").fetchSemanticsNode().boundsInRoot
    val node = onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
    return IntOffset((node.left - list.left).toInt(), (node.top - list.top).toInt())
  }

  @Test
  fun fileFrameRoundsHeaderCorners() = runComposeUiTest {
    setContent { Box(Modifier.size(800.dp, 600.dp)) { ViewerScreen(stateOf(listOf(modified("a.kt"), modified("b.kt"))), {}) } }
    val pixels = listPixels()
    val corner = cornerOf("file-header-0")
    assertEquals(LightDiffColors.background, pixels[corner.x, corner.y])
    assertEquals(LightDiffColors.border, pixels[corner.x + 10, corner.y])
    assertEquals(LightDiffColors.border, pixels[corner.x, corner.y + 20])
  }

  @Test
  fun fileFrameRoundsLastRowCorners() = runComposeUiTest {
    setContent { Box(Modifier.size(800.dp, 600.dp)) { ViewerScreen(stateOf(listOf(modified("a.kt"), modified("b.kt"))), {}) } }
    val pixels = listPixels()
    val next = cornerOf("file-header-1")
    val bottom = next.y - 12 - 1
    assertEquals(LightDiffColors.background, pixels[next.x, bottom])
    assertEquals(LightDiffColors.border, pixels[next.x + 10, bottom])
  }

  @Test
  fun collapsedFileFrameIsClosed() = runComposeUiTest {
    setContent { Box(Modifier.size(800.dp, 600.dp)) { ViewerScreen(stateOf(listOf(modified("a.kt"), modified("b.kt"))).toggled(0), {}) } }
    val pixels = listPixels()
    val header = onNodeWithTag("file-header-0").fetchSemanticsNode().boundsInRoot
    val list = onNodeWithTag("diff-list").fetchSemanticsNode().boundsInRoot
    val x = (header.left - list.left).toInt()
    val bottom = (header.bottom - list.top).toInt() - 1
    assertEquals(LightDiffColors.background, pixels[x, bottom])
    assertEquals(LightDiffColors.border, pixels[x + 10, bottom])
  }

  @Test
  fun framesSurviveReloadWhileScrolled() = runComposeUiTest {
    val files = (0 until 30).map { modified("f$it.kt", 20) }
    var ui by mutableStateOf(stateOf(files).copy(loadId = 1))
    setContent { ViewerScreen(ui, {}) }
    onNodeWithTag("diff-list").performScrollToIndex(files.size * 10)
    waitForIdle()
    ui = ViewerUiState(RepoPath("/repo"), LoadStatus.Loading, loadId = 2)
    waitForIdle()
    ui = stateOf(listOf(modified("only.kt"))).copy(loadId = 2)
    waitForIdle()
    onNodeWithTag("file-header-0").assertIsDisplayed()
  }

  @Test
  fun fileFramesStayInsideListDuringHeaderHandoff() = runComposeUiTest {
    setContent { Box(Modifier.size(800.dp, 600.dp)) { ViewerScreen(stateOf(listOf(modified("a.kt", 40), modified("b.kt", 40))), {}) } }
    onNodeWithTag("diff-list").performScrollToIndex(41)
    waitForIdle()
    val pixels = onRoot().captureToImage().toPixelMap()
    val list = onNodeWithTag("diff-list").fetchSemanticsNode().boundsInRoot
    val top = list.top.toInt()
    val left = list.left.toInt()
    for (y in (top - 30) until (top - 1)) {
      for (x in (left + 15)..(left + 40)) {
        val pixel = pixels[x, y]
        assertTrue(pixel != LightDiffColors.border && pixel != LightDiffColors.background, "frame painted at ($x, $y) above the list")
      }
    }
  }

  @Test
  fun hunkHeaderMatchesCodeLineHeight() = runComposeUiTest {
    setContent { ViewerScreen(stateOf(listOf(modified("a.kt"))), {}) }
    val code = onNodeWithText("TWO", useUnmergedTree = true).fetchSemanticsNode().size.height
    val hunk = onNodeWithText("@@ -1,1 +1,1 @@ fun main()", useUnmergedTree = true).fetchSemanticsNode().size.height
    assertEquals(code, hunk)
  }

  @Test
  fun noNewlineRowMatchesCodeLineHeight() = runComposeUiTest {
    val body = listOf(DiffLine(LineKind.Added, "x", null, 1), DiffLine(LineKind.NoNewlineAtEof, "", null, null))
    val file = FileDiff(null, "n.kt", ChangeType.Added, false, DiffContent.Text(listOf(Hunk(0, 0, 1, 1, "", body))))
    setContent { ViewerScreen(stateOf(listOf(file)), {}) }
    val code = onNodeWithText("x", useUnmergedTree = true).fetchSemanticsNode().size.height
    val noNewline = onNodeWithText("No newline at end of file", useUnmergedTree = true).fetchSemanticsNode().size.height
    assertEquals(code, noNewline)
  }

  @Test
  fun scrollJumpBottomShowsFooter() = runComposeUiTest {
    val jumps = ScrollJumps()
    setContent { ViewerScreen(stateOf((0 until 30).map { modified("f$it.kt", 20) }), {}, scrollJumps = jumps) }
    waitForIdle()
    jumps.request(ScrollJump.Bottom)
    waitForIdle()
    onNodeWithTag("end-of-diff").assertIsDisplayed()
  }

  @Test
  fun scrollJumpTopReturnsToFirstItem() = runComposeUiTest {
    val jumps = ScrollJumps()
    setContent { ViewerScreen(stateOf((0 until 30).map { modified("f$it.kt", 20) }), {}, scrollJumps = jumps) }
    waitForIdle()
    jumps.request(ScrollJump.Bottom)
    waitForIdle()
    onNodeWithTag("file-header-0").assertDoesNotExist()
    jumps.request(ScrollJump.Top)
    waitForIdle()
    onNodeWithTag("file-header-0").assertIsDisplayed()
  }

  @Test
  fun scrollJumpOnNoChangesIsNoOp() = runComposeUiTest {
    val jumps = ScrollJumps()
    setContent { ViewerScreen(stateOf(emptyList()), {}, scrollJumps = jumps) }
    waitForIdle()
    jumps.request(ScrollJump.Bottom)
    waitForIdle()
    jumps.request(ScrollJump.Top)
    waitForIdle()
    onNodeWithText("No changes").assertIsDisplayed()
  }

  private fun splitState(vararg body: DiffLine): ViewerUiState {
    val file = FileDiff("a.kt", "a.kt", ChangeType.Modified, false, DiffContent.Text(listOf(Hunk(1, 1, 1, 1, "", body.toList()))))
    val state = stateOf(listOf(file))
    return state.copy(split = splitRowsOf(state.rows))
  }

  @Test
  fun splitShowsPairInOneItem() = runComposeUiTest {
    val state = splitState(DiffLine(LineKind.Removed, "oldtext", 1, null), DiffLine(LineKind.Added, "newtext", null, 1))
    setContent { Box(modifier = Modifier.size(1000.dp, 500.dp)) { ViewerScreen(state, {}, mode = DiffMode.Split) } }
    val old = onNodeWithText("oldtext", useUnmergedTree = true).getUnclippedBoundsInRoot()
    val new = onNodeWithText("newtext", useUnmergedTree = true).getUnclippedBoundsInRoot()
    assertEquals(old.top, new.top)
    assertTrue(new.left > old.right, "old=$old new=$new")
  }

  @Test
  fun splitPadsUnpairedLine() = runComposeUiTest {
    val state = splitState(
      DiffLine(LineKind.Removed, "aaa", 1, null),
      DiffLine(LineKind.Removed, "bbb", 2, null),
      DiffLine(LineKind.Added, "ccc", null, 1),
    )
    setContent { Box(modifier = Modifier.size(1000.dp, 500.dp)) { ViewerScreen(state, {}, mode = DiffMode.Split) } }
    val a = onNodeWithText("aaa", useUnmergedTree = true).getUnclippedBoundsInRoot()
    val b = onNodeWithText("bbb", useUnmergedTree = true).getUnclippedBoundsInRoot()
    val c = onNodeWithText("ccc", useUnmergedTree = true).getUnclippedBoundsInRoot()
    assertEquals(a.top, c.top)
    assertTrue(abs((a.bottom - b.top).value) < 0.5f, "a=$a b=$b")
    onAllNodesWithText("+", useUnmergedTree = true).assertCountEquals(1)
    onAllNodesWithText("-", useUnmergedTree = true).assertCountEquals(2)
    val pixels = onNodeWithTag("diff-list").captureToImage().toPixelMap()
    val list = onNodeWithTag("diff-list").fetchSemanticsNode().boundsInRoot
    val x = (list.left + (list.width * 0.9f)).toInt() - list.left.toInt()
    val y = with(density) { ((b.top + b.bottom) / 2).toPx() }.toInt() - list.top.toInt()
    assertEquals(LightDiffColors.emptySideBackground, pixels[x, y])
  }

  @Test
  fun splitOrphanPressWithNoFilesDoesNotCrash() = runComposeUiTest {
    val ui = stateOf(emptyList()).copy(loadId = 1)
    setContent { Box(modifier = Modifier.size(1000.dp, 500.dp)) { ViewerScreen(ui, {}, review = snapshotOf(1, 1, listOf(staticThread()), null), mode = DiffMode.Split) } }
    onNodeWithTag("diff-list").performMouseInput {
      moveTo(Offset(20f, 10f))
      press()
      release()
    }
    waitForIdle()
    onNodeWithTag("thread-t1").assertIsDisplayed()
  }

  @Test
  fun splitContextOnBothSides() = runComposeUiTest {
    val state = splitState(DiffLine(LineKind.Context, "same", 1, 1))
    setContent { Box(modifier = Modifier.size(1000.dp, 500.dp)) { ViewerScreen(state, {}, mode = DiffMode.Split) } }
    onAllNodesWithText("same", useUnmergedTree = true).assertCountEquals(2)
  }

  private fun anchoredThread(id: String, side: io.github.mataku.kdiffviewer.core.review.Side, line: Int) = ReviewThread(id, Anchor.Lines("a.kt", side, line, line, listOf("same")), listOf(Comment("c$id", Author.User, "note $id", at)), false, false)

  private fun splitReviewUi(vararg body: DiffLine): ViewerUiState = splitState(*body).copy(loadId = 1)

  @Test
  fun splitHoverShowsPlusOnHoveredSide() = runComposeUiTest {
    val ui = splitReviewUi(DiffLine(LineKind.Context, "same", 4, 6))
    val review = reviewState(ui)
    setContent {
      val snapshot by review.snapshot.collectAsState()
      CompositionLocalProvider(LocalReviewActions provides review) { Box(modifier = Modifier.size(1000.dp, 500.dp)) { ViewerScreen(ui, {}, review = snapshot, mode = DiffMode.Split) } }
    }
    val list = onNodeWithTag("diff-list").fetchSemanticsNode().boundsInRoot
    onAllNodesWithText("same", useUnmergedTree = true)[0].performMouseInput { moveTo(center) }
    waitForIdle()
    onAllNodesWithTag("add-comment").assertCountEquals(1)
    assertTrue(onNodeWithTag("add-comment").fetchSemanticsNode().boundsInRoot.right < list.center.x)
    onAllNodesWithText("same", useUnmergedTree = true)[1].performMouseInput { moveTo(center) }
    waitForIdle()
    onAllNodesWithTag("add-comment").assertCountEquals(1)
    assertTrue(onNodeWithTag("add-comment").fetchSemanticsNode().boundsInRoot.left > list.center.x)
  }

  @Test
  fun splitPlusOnLeftContextAnchorsOld() = runComposeUiTest {
    val ui = splitReviewUi(DiffLine(LineKind.Context, "same", 4, 6))
    val review = reviewState(ui)
    setContent {
      val snapshot by review.snapshot.collectAsState()
      CompositionLocalProvider(LocalReviewActions provides review) { Box(modifier = Modifier.size(1000.dp, 500.dp)) { ViewerScreen(ui, {}, review = snapshot, mode = DiffMode.Split) } }
    }
    onAllNodesWithText("same", useUnmergedTree = true)[0].performMouseInput { moveTo(center) }
    waitForIdle()
    onNodeWithTag("add-comment").performClick()
    waitForIdle()
    val anchor = ((review.snapshot.value.draft!!.target) as DraftTarget.NewThread).anchor as Anchor.Lines
    assertEquals(io.github.mataku.kdiffviewer.core.review.Side.Old, anchor.side)
    assertEquals(4, anchor.startLine)
  }

  @Test
  fun splitPlusOnRightAddedLineBesideNoNewlineLeft() = runComposeUiTest {
    val ui = splitReviewUi(
      DiffLine(LineKind.Removed, "gone", 1, null),
      DiffLine(LineKind.NoNewlineAtEof, "", null, null),
      DiffLine(LineKind.Added, "first", null, 1),
      DiffLine(LineKind.Added, "fresh", null, 2),
    )
    val review = reviewState(ui)
    setContent {
      val snapshot by review.snapshot.collectAsState()
      CompositionLocalProvider(LocalReviewActions provides review) { Box(modifier = Modifier.size(1000.dp, 500.dp)) { ViewerScreen(ui, {}, review = snapshot, mode = DiffMode.Split) } }
    }
    onNodeWithText("fresh", useUnmergedTree = true).performMouseInput { moveTo(center) }
    waitForIdle()
    onAllNodesWithTag("add-comment").assertCountEquals(1)
  }

  @Test
  fun splitThreadRendersInItsColumn() = runComposeUiTest {
    val ui = splitReviewUi(DiffLine(LineKind.Context, "same", 1, 1))
    val threads = listOf(anchoredThread("t1", io.github.mataku.kdiffviewer.core.review.Side.Old, 1), anchoredThread("t2", io.github.mataku.kdiffviewer.core.review.Side.New, 1))
    setContent { Box(modifier = Modifier.size(1000.dp, 500.dp)) { ViewerScreen(ui, {}, review = snapshotOf(1, 1, threads, Placement.OnLine(LineLocation(0, 0, 0))), mode = DiffMode.Split) } }
    val list = onNodeWithTag("diff-list").fetchSemanticsNode().boundsInRoot
    val old = onNodeWithTag("thread-t1").fetchSemanticsNode().boundsInRoot
    val new = onNodeWithTag("thread-t2").fetchSemanticsNode().boundsInRoot
    assertTrue(old.left < list.center.x && old.right <= list.center.x, "old=$old list=$list")
    assertTrue(new.left > list.center.x, "new=$new list=$list")
  }

  @Test
  fun splitDraftRendersInItsColumn() = runComposeUiTest {
    val ui = splitReviewUi(DiffLine(LineKind.Context, "same", 1, 1))
    val review = reviewState(ui)
    review.startDraft(DraftTarget.NewThread(anchorOf(ui.files[0], 0, 0, io.github.mataku.kdiffviewer.core.review.Side.Old)!!, Placement.OnLine(LineLocation(0, 0, 0))))
    setContent {
      val snapshot by review.snapshot.collectAsState()
      CompositionLocalProvider(LocalReviewActions provides review) { Box(modifier = Modifier.size(1000.dp, 500.dp)) { ViewerScreen(ui, {}, review = snapshot, mode = DiffMode.Split) } }
    }
    val list = onNodeWithTag("diff-list").fetchSemanticsNode().boundsInRoot
    val composer = onNodeWithTag("comment-composer").fetchSemanticsNode().boundsInRoot
    assertTrue(composer.right <= list.center.x, "composer=$composer list=$list")
  }

  @Test
  fun draftSurvivesModeSwitch() = runComposeUiTest {
    val ui = splitReviewUi(DiffLine(LineKind.Context, "same", 1, 1))
    val review = reviewState(ui)
    review.startDraft(DraftTarget.NewThread(anchorOf(ui.files[0], 0, 0, io.github.mataku.kdiffviewer.core.review.Side.New)!!, Placement.OnLine(LineLocation(0, 0, 0))))
    var mode by mutableStateOf(DiffMode.Unified)
    setContent {
      val snapshot by review.snapshot.collectAsState()
      CompositionLocalProvider(LocalReviewActions provides review) { Box(modifier = Modifier.size(1000.dp, 500.dp)) { ViewerScreen(ui, {}, review = snapshot, mode = mode) } }
    }
    onNodeWithTag("comment-composer").performTextInput("typed")
    mode = DiffMode.Split
    waitForIdle()
    onNodeWithTag("comment-composer").assertTextEquals("typed")
    mode = DiffMode.Unified
    waitForIdle()
    onNodeWithTag("comment-composer").assertTextEquals("typed")
  }

  @Test
  fun splitKeepsFileHeaderFullWidth() = runComposeUiTest {
    val state = splitState(DiffLine(LineKind.Removed, "oldtext", 1, null), DiffLine(LineKind.Added, "newtext", null, 1))
    var mode by mutableStateOf(DiffMode.Unified)
    setContent { Box(modifier = Modifier.size(1000.dp, 500.dp)) { ViewerScreen(state, {}, mode = mode) } }
    val list = onNodeWithTag("diff-list").fetchSemanticsNode().boundsInRoot
    val margin = with(density) { ContentHorizontalMargin.toPx() }
    val unified = onNodeWithTag("file-header-0").fetchSemanticsNode().boundsInRoot.width
    mode = DiffMode.Split
    waitForIdle()
    val split = onNodeWithTag("file-header-0").fetchSemanticsNode().boundsInRoot.width
    assertEquals(unified, split)
    assertTrue(abs(list.width - 2 * margin - split) <= 1f, "list=$list header=$split")
  }
}

private fun SemanticsNodeInteractionsProvider.assertPinnedAtTop(tag: String) {
  val list = onNodeWithTag("diff-list").fetchSemanticsNode().boundsInRoot
  val header = onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
  assertTrue(header.top - list.top in 0f..30f, "header=$header list=$list")
}

fun SemanticsNodeInteractionsProvider.lineText(text: String): AnnotatedString = onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsProperties.Text].single()
