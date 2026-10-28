package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performMultiModalInput
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.tripleClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import io.github.mataku.kdiffviewer.app.CollapsedFile
import io.github.mataku.kdiffviewer.app.DiffMode
import io.github.mataku.kdiffviewer.app.DiffRow
import io.github.mataku.kdiffviewer.app.LoadStatus
import io.github.mataku.kdiffviewer.app.ViewerUiState
import io.github.mataku.kdiffviewer.app.buildFileTree
import io.github.mataku.kdiffviewer.app.fingerprint
import io.github.mataku.kdiffviewer.app.flattenRows
import io.github.mataku.kdiffviewer.app.review.DraftTarget
import io.github.mataku.kdiffviewer.app.review.ReviewState
import io.github.mataku.kdiffviewer.app.selection.ClipboardWriter
import io.github.mataku.kdiffviewer.app.selection.ContextMenuItem
import io.github.mataku.kdiffviewer.app.selection.LocalNativeContextMenu
import io.github.mataku.kdiffviewer.app.selection.LocalTextSelectionCoordinator
import io.github.mataku.kdiffviewer.app.selection.NativeContextMenu
import io.github.mataku.kdiffviewer.app.selection.TextSelectionCoordinator
import io.github.mataku.kdiffviewer.app.splitRowsOf
import io.github.mataku.kdiffviewer.app.ui.review.LocalReviewActions
import io.github.mataku.kdiffviewer.core.model.ChangeType
import io.github.mataku.kdiffviewer.core.model.DiffContent
import io.github.mataku.kdiffviewer.core.model.DiffLine
import io.github.mataku.kdiffviewer.core.model.FileDiff
import io.github.mataku.kdiffviewer.core.model.Hunk
import io.github.mataku.kdiffviewer.core.model.LineKind
import io.github.mataku.kdiffviewer.core.model.RepoPath
import io.github.mataku.kdiffviewer.core.model.displayPath
import io.github.mataku.kdiffviewer.core.review.Anchor
import io.github.mataku.kdiffviewer.core.review.LineLocation
import io.github.mataku.kdiffviewer.core.review.Placement
import io.github.mataku.kdiffviewer.core.review.Side
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalTestApi::class)
class DiffSelectionUiTest {
  private class FakeClipboard : ClipboardWriter {
    val writes = mutableListOf<String>()

    override fun write(text: String) {
      writes += text
    }
  }

  private class RecordingMenu : NativeContextMenu {
    val shown = mutableListOf<List<ContextMenuItem>>()

    override fun show(positionInWindow: DpOffset, items: List<ContextMenuItem>) {
      shown += items
    }
  }

  private val clipboard = FakeClipboard()
  private var copyOnSelect = false
  private val coordinator = TextSelectionCoordinator(clipboard) { copyOnSelect }
  private val menu = RecordingMenu()

  private val fileA = FileDiff(
    "a.kt",
    "a.kt",
    ChangeType.Modified,
    false,
    DiffContent.Text(
      listOf(
        Hunk(
          1,
          3,
          1,
          3,
          "",
          listOf(
            DiffLine(LineKind.Context, "alpha beta", 1, 1),
            DiffLine(LineKind.Context, "\tgamma", 2, 2),
            DiffLine(LineKind.Context, "delta", 3, 3),
          ),
        ),
      ),
    ),
  )
  private val fileB = FileDiff(
    "b.kt",
    "b.kt",
    ChangeType.Modified,
    false,
    DiffContent.Text(listOf(Hunk(1, 0, 1, 1, "", listOf(DiffLine(LineKind.Added, "epsilon", null, 1))))),
  )

  private fun stateOf(files: List<FileDiff> = listOf(fileA, fileB), loadId: Long = 1): ViewerUiState {
    val rows = flattenRows(files)
    val headers = rows.mapIndexedNotNull { i, r -> if (r is DiffRow.FileHeader) i else null }
    return ViewerUiState(RepoPath("/repo"), LoadStatus.Done, files, rows, headers, buildFileTree(files), loadId = loadId, fingerprints = files.map { it.fingerprint() })
  }

  private fun ViewerUiState.toggled(fileIndex: Int): ViewerUiState {
    val entry = CollapsedFile(files[fileIndex].displayPath, fingerprints[fileIndex])
    return copy(collapsed = if (entry in collapsed) collapsed - entry else collapsed + entry)
  }

  private fun ComposeUiTest.listBounds() = onNodeWithTag("diff-list").fetchSemanticsNode().boundsInRoot

  private fun ComposeUiTest.codePosition(text: String, displayColumn: Int): Offset {
    val node = onNodeWithText(text).fetchSemanticsNode()
    val layouts = mutableListOf<TextLayoutResult>()
    node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
    val layout = layouts.single()
    val bounds = node.boundsInRoot
    val list = listBounds()
    return Offset(
      bounds.left + layout.getHorizontalPosition(displayColumn, usePrimaryDirection = true) + 1f - list.left,
      bounds.top + layout.size.height / 2f - list.top,
    )
  }

  private fun ComposeUiTest.rowPosition(text: String, x: Float): Offset = Offset(x, codePosition(text, 0).y)

  private fun ComposeUiTest.drag(from: Offset, to: Offset) {
    onNodeWithTag("diff-list").performMouseInput {
      moveTo(from)
      press()
      moveTo(to)
      release()
      advanceEventTime(1_000)
    }
    waitForIdle()
  }

  private fun ComposeUiTest.show(state: ViewerUiState) {
    setContent {
      CompositionLocalProvider(LocalTextSelectionCoordinator provides coordinator, LocalNativeContextMenu provides menu) {
        ViewerScreen(state, {})
      }
    }
  }

  @Test
  fun dragAcrossRowsCopiesCode() = runComposeUiTest {
    show(stateOf())
    drag(codePosition("alpha beta", 6), codePosition("delta", 2))
    assertTrue(coordinator.hasSelection)
    assertTrue(coordinator.copy())
    assertEquals(listOf("beta\n\tgamma\nde"), clipboard.writes)
  }

  @Test
  fun doubleClickSelectsWord() = runComposeUiTest {
    show(stateOf())
    onNodeWithTag("diff-list").performMouseInput { doubleClick(codePosition("alpha beta", 7)) }
    waitForIdle()
    assertTrue(coordinator.copy())
    assertEquals(listOf("beta"), clipboard.writes)
  }

  @Test
  fun tripleClickSelectsRowWithNewline() = runComposeUiTest {
    show(stateOf())
    onNodeWithTag("diff-list").performMouseInput { tripleClick(codePosition("alpha beta", 3)) }
    waitForIdle()
    assertTrue(coordinator.copy())
    assertEquals(listOf("alpha beta\n"), clipboard.writes)
  }

  @Test
  fun clickClearsSelection() = runComposeUiTest {
    show(stateOf())
    drag(codePosition("alpha beta", 6), codePosition("delta", 2))
    assertTrue(coordinator.hasSelection)
    onNodeWithTag("diff-list").performMouseInput { click(codePosition("        gamma", 10)) }
    waitForIdle()
    assertFalse(coordinator.hasSelection)
  }

  @Test
  fun shiftClickExtends() = runComposeUiTest {
    show(stateOf())
    drag(codePosition("alpha beta", 6), codePosition("alpha beta", 8))
    onNodeWithTag("diff-list").performMultiModalInput {
      key { keyDown(Key.ShiftLeft) }
      mouse { click(codePosition("delta", 2)) }
      key { keyUp(Key.ShiftLeft) }
    }
    waitForIdle()
    assertTrue(coordinator.copy())
    assertEquals(listOf("beta\n\tgamma\nde"), clipboard.writes)
  }

  @Test
  fun pressOnLineNumberStartsAtColumnZero() = runComposeUiTest {
    show(stateOf())
    drag(rowPosition("alpha beta", 30f), codePosition("alpha beta", 5))
    assertTrue(coordinator.copy())
    assertEquals(listOf("alpha"), clipboard.writes)
  }

  @Test
  fun addCommentButtonDoesNotSelect() = runComposeUiTest {
    val ui = stateOf()
    val review = ReviewState(CoroutineScope(Dispatchers.Unconfined), Dispatchers.Unconfined) { Instant.fromEpochSeconds(0) }.also { it.onViewerState(ui) }
    setContent {
      val snapshot by review.snapshot.collectAsState()
      CompositionLocalProvider(
        LocalTextSelectionCoordinator provides coordinator,
        LocalNativeContextMenu provides menu,
        LocalReviewActions provides review,
      ) {
        ViewerScreen(ui, {}, review = snapshot)
      }
    }
    onNodeWithText("alpha beta").performMouseInput { moveTo(center) }
    waitForIdle()
    val button = onNodeWithTag("add-comment").fetchSemanticsNode().boundsInRoot.center - listBounds().topLeft
    val target = codePosition("delta", 2)
    onNodeWithTag("diff-list").performMouseInput {
      moveTo(button)
      press()
      moveTo(target)
      release()
      advanceEventTime(1_000)
    }
    waitForIdle()
    assertFalse(coordinator.hasSelection)
    onNodeWithTag("add-comment").performClick()
    waitForIdle()
    onNodeWithTag("comment-composer").assertIsDisplayed()
    assertFalse(coordinator.hasSelection)
  }

  @Test
  fun dragInDiffTakesFocusFromComposer() = runComposeUiTest {
    val ui = stateOf()
    val review = ReviewState(CoroutineScope(Dispatchers.Unconfined), Dispatchers.Unconfined) { Instant.fromEpochSeconds(0) }.also { it.onViewerState(ui) }
    setContent {
      val snapshot by review.snapshot.collectAsState()
      CompositionLocalProvider(
        LocalTextSelectionCoordinator provides coordinator,
        LocalNativeContextMenu provides menu,
        LocalReviewActions provides review,
      ) {
        ViewerScreen(ui, {}, review = snapshot)
      }
    }
    onNodeWithText("alpha beta").performMouseInput { moveTo(center) }
    waitForIdle()
    onNodeWithTag("add-comment").performClick()
    waitForIdle()
    onNodeWithTag("comment-composer").assertIsFocused()
    drag(codePosition("alpha beta", 6), codePosition("delta", 2))
    assertTrue(coordinator.hasSelection)
    onNodeWithTag("comment-composer").assertIsNotFocused()
  }

  @Test
  fun copyAfterCollapsedFile() = runComposeUiTest {
    val fileC = FileDiff("c.kt", "c.kt", ChangeType.Modified, false, DiffContent.Text(listOf(Hunk(1, 1, 1, 1, "", listOf(DiffLine(LineKind.Context, "zeta", 1, 1))))))
    val files = listOf(fileA, fileB, fileC)
    show(stateOf(files).toggled(1))
    drag(codePosition("alpha beta", 6), codePosition("zeta", 2))
    assertTrue(coordinator.copy())
    assertEquals(listOf("beta\n\tgamma\ndelta\nze"), clipboard.writes)
  }

  private fun splitStateOf(vararg files: FileDiff): ViewerUiState {
    val state = stateOf(files.toList())
    return state.copy(split = splitRowsOf(state.rows))
  }

  private fun ComposeUiTest.showSplit(state: ViewerUiState) {
    setContent {
      CompositionLocalProvider(LocalTextSelectionCoordinator provides coordinator, LocalNativeContextMenu provides menu) {
        Box(modifier = Modifier.size(1000.dp, 500.dp)) { ViewerScreen(state, {}, mode = DiffMode.Split) }
      }
    }
  }

  private fun ComposeUiTest.splitCodePosition(text: String, half: Int, displayColumn: Int): Offset {
    val node = onAllNodesWithText(text)[half].fetchSemanticsNode()
    val layouts = mutableListOf<TextLayoutResult>()
    node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
    val layout = layouts.single()
    val bounds = node.boundsInRoot
    val list = listBounds()
    return Offset(
      bounds.left + layout.getHorizontalPosition(displayColumn, usePrimaryDirection = true) + 1f - list.left,
      bounds.top + layout.size.height / 2f - list.top,
    )
  }

  private val mixedFile = FileDiff(
    "m.kt",
    "m.kt",
    ChangeType.Modified,
    false,
    DiffContent.Text(
      listOf(
        Hunk(
          1,
          3,
          1,
          3,
          "",
          listOf(
            DiffLine(LineKind.Context, "alpha", 1, 1),
            DiffLine(LineKind.Removed, "bravo", 2, null),
            DiffLine(LineKind.Added, "charlie", null, 2),
            DiffLine(LineKind.Context, "delta", 3, 3),
          ),
        ),
      ),
    ),
  )

  @Test
  fun splitDragOnLeftCopiesOldSide() = runComposeUiTest {
    showSplit(splitStateOf(mixedFile))
    drag(splitCodePosition("alpha", 0, 0), splitCodePosition("delta", 0, 5))
    assertTrue(coordinator.copy())
    assertEquals(listOf("alpha\nbravo\ndelta"), clipboard.writes)
  }

  @Test
  fun splitDragOnRightCopiesNewSide() = runComposeUiTest {
    showSplit(splitStateOf(mixedFile))
    drag(splitCodePosition("alpha", 1, 0), splitCodePosition("delta", 1, 5))
    assertTrue(coordinator.copy())
    assertEquals(listOf("alpha\ncharlie\ndelta"), clipboard.writes)
  }

  @Test
  fun oldSideDragLeavesRightContextUnhighlighted() = runComposeUiTest {
    showSplit(splitStateOf(mixedFile))
    drag(splitCodePosition("alpha", 0, 0), splitCodePosition("alpha", 0, 5))
    val selected = LightDiffColors.selectionBackground
    fun ComposeUiTest.hasSelectionPixels(half: Int): Boolean {
      val pixels = onAllNodesWithText("alpha")[half].captureToImage().toPixelMap()
      return (0 until pixels.width).any { x -> (0 until pixels.height).any { y -> pixels[x, y] == selected } }
    }
    assertTrue(hasSelectionPixels(0))
    assertFalse(hasSelectionPixels(1))
  }

  @Test
  fun copyAfterCollapsedFileInSplit() = runComposeUiTest {
    val fileC = FileDiff("c.kt", "c.kt", ChangeType.Modified, false, DiffContent.Text(listOf(Hunk(1, 1, 1, 1, "", listOf(DiffLine(LineKind.Context, "zeta", 1, 1))))))
    val state = splitStateOf(mixedFile, fileB, fileC).toggled(0)
    showSplit(state)
    drag(splitCodePosition("epsilon", 0, 0), splitCodePosition("zeta", 1, 2))
    assertTrue(coordinator.copy())
    assertEquals(listOf("epsilon\nze"), clipboard.writes)
  }

  @Test
  fun doubleClickSelectsWordOnPressedSide() = runComposeUiTest {
    showSplit(splitStateOf(mixedFile))
    drag(splitCodePosition("bravo", 0, 0), splitCodePosition("bravo", 0, 3))
    onNodeWithTag("diff-list").performMouseInput { doubleClick(splitCodePosition("charlie", 0, 3)) }
    waitForIdle()
    assertTrue(coordinator.copy())
    assertEquals(listOf("charlie"), clipboard.writes)
  }

  @Test
  fun tripleClickSelectsRowOnPressedSide() = runComposeUiTest {
    showSplit(splitStateOf(mixedFile))
    drag(splitCodePosition("bravo", 0, 0), splitCodePosition("bravo", 0, 3))
    onNodeWithTag("diff-list").performMouseInput { tripleClick(splitCodePosition("charlie", 0, 2)) }
    waitForIdle()
    assertTrue(coordinator.copy())
    assertEquals(listOf("charlie\n"), clipboard.writes)
  }

  @Test
  fun leftDragOverUnpairedAddedLineCopiesOnlyOldRows() = runComposeUiTest {
    val file = FileDiff(
      "u.kt",
      "u.kt",
      ChangeType.Modified,
      false,
      DiffContent.Text(
        listOf(
          Hunk(
            1,
            2,
            1,
            3,
            "",
            listOf(
              DiffLine(LineKind.Context, "above", 1, 1),
              DiffLine(LineKind.Added, "solo", null, 2),
              DiffLine(LineKind.Context, "below", 2, 3),
            ),
          ),
        ),
      ),
    )
    showSplit(splitStateOf(file))
    drag(splitCodePosition("above", 0, 0), splitCodePosition("below", 0, 5))
    assertTrue(coordinator.copy())
    assertEquals(listOf("above\nbelow"), clipboard.writes)
  }

  @Test
  fun leftPressDraggedOverRightHalfCopiesOnlyOldText() = runComposeUiTest {
    showSplit(splitStateOf(mixedFile))
    drag(splitCodePosition("alpha", 0, 0), splitCodePosition("charlie", 0, 4))
    assertTrue(coordinator.copy())
    assertEquals(listOf("alpha\nbravo"), clipboard.writes)
  }

  @Test
  fun collapsingFileClearsSelection() = runComposeUiTest {
    var ui by mutableStateOf(stateOf())
    setContent {
      CompositionLocalProvider(LocalTextSelectionCoordinator provides coordinator, LocalNativeContextMenu provides menu) {
        ViewerScreen(ui, {}, onToggleFile = { ui = ui.toggled(it) })
      }
    }
    drag(codePosition("alpha beta", 6), codePosition("delta", 2))
    assertTrue(coordinator.hasSelection)
    onNodeWithTag("file-header-1").performClick()
    waitForIdle()
    assertFalse(coordinator.hasSelection)
  }

  @Test
  fun reloadClearsSelection() = runComposeUiTest {
    var ui by mutableStateOf(stateOf())
    setContent {
      CompositionLocalProvider(LocalTextSelectionCoordinator provides coordinator, LocalNativeContextMenu provides menu) {
        ViewerScreen(ui, {})
      }
    }
    drag(codePosition("alpha beta", 6), codePosition("delta", 2))
    assertTrue(coordinator.hasSelection)
    ui = stateOf(loadId = 2)
    waitForIdle()
    assertFalse(coordinator.hasSelection)
  }

  @Test
  fun rowKeptAcrossReloadSelectsAtItsNewIndex() = runComposeUiTest {
    var ui by mutableStateOf(stateOf())
    setContent {
      CompositionLocalProvider(LocalTextSelectionCoordinator provides coordinator, LocalNativeContextMenu provides menu) {
        ViewerScreen(ui, {})
      }
    }
    val grown = fileA.copy(
      content = DiffContent.Text(
        listOf(
          Hunk(
            1,
            4,
            1,
            4,
            "",
            listOf(
              DiffLine(LineKind.Context, "alpha beta", 1, 1),
              DiffLine(LineKind.Context, "\tgamma", 2, 2),
              DiffLine(LineKind.Context, "delta", 3, 3),
              DiffLine(LineKind.Context, "zeta", 4, 4),
            ),
          ),
        ),
      ),
    )
    val before = ui.rows.indexOfFirst { it.key == "l1-0-0" }
    ui = stateOf(listOf(grown, fileB), loadId = 2)
    waitForIdle()
    assertEquals(before + 1, ui.rows.indexOfFirst { it.key == "l1-0-0" })
    drag(codePosition("epsilon", 2), codePosition("epsilon", 5))
    assertTrue(coordinator.copy())
    assertEquals(listOf("sil"), clipboard.writes)
  }

  @Test
  fun dragPastBottomClampsToLastLine() = runComposeUiTest {
    show(stateOf())
    val start = codePosition("alpha beta", 0)
    val list = listBounds()
    onNodeWithTag("diff-list").performMouseInput {
      moveTo(start)
      press()
      moveTo(Offset(start.x, list.height + 200f))
      moveTo(Offset(start.x, list.height + 250f))
      release()
    }
    waitForIdle()
    assertTrue(coordinator.copy())
    assertEquals(listOf("alpha beta\n\tgamma\ndelta\nepsilon"), clipboard.writes)
  }

  @Test
  fun dragPastBottomAutoScrollsWhileFramesAdvance() = runComposeUiTest {
    val lines = (1..300).map { DiffLine(LineKind.Context, "line $it", it, it) }
    val file = FileDiff("long.kt", "long.kt", ChangeType.Modified, false, DiffContent.Text(listOf(Hunk(1, 300, 1, 300, "", lines))))
    show(stateOf(listOf(file)))
    val start = codePosition("line 1", 0)
    val list = listBounds()
    mainClock.autoAdvance = false
    onNodeWithTag("diff-list").performMouseInput {
      moveTo(start)
      press()
      moveTo(Offset(start.x, list.height + 100f))
    }
    repeat(60) { mainClock.advanceTimeByFrame() }
    onNodeWithTag("diff-list").performMouseInput { release() }
    mainClock.autoAdvance = true
    waitForIdle()
    assertTrue(coordinator.copy())
    val text = clipboard.writes.single()
    assertTrue(text.startsWith("line 1\nline 2\n"), text.take(40))
    assertTrue(text.lines().size > 60, "lines=${text.lines().size}")
  }

  @Test
  fun copyOnSelectWritesOnRelease() = runComposeUiTest {
    copyOnSelect = true
    show(stateOf())
    val from = codePosition("alpha beta", 6)
    val to = codePosition("delta", 2)
    onNodeWithTag("diff-list").performMouseInput {
      moveTo(from)
      press()
      moveTo(to)
      moveTo(to + Offset(1f, 0f))
    }
    waitForIdle()
    assertEquals(emptyList(), clipboard.writes)
    onNodeWithTag("diff-list").performMouseInput { release() }
    waitForIdle()
    assertEquals(listOf("beta\n\tgamma\nde"), clipboard.writes)
    copyOnSelect = false
    onNodeWithTag("diff-list").performMouseInput { advanceEventTime(1_000) }
    drag(codePosition("alpha beta", 0), codePosition("alpha beta", 5))
    assertTrue(coordinator.hasSelection)
    assertEquals(listOf("beta\n\tgamma\nde"), clipboard.writes)
  }

  @Test
  fun doubleClickThenDragWritesOnce() = runComposeUiTest {
    copyOnSelect = true
    show(stateOf())
    val word = codePosition("alpha beta", 7)
    val to = codePosition("delta", 2)
    onNodeWithTag("diff-list").performMouseInput {
      click(word)
      advanceEventTime(50)
      press()
      moveTo(to)
      moveTo(to + Offset(1f, 0f))
      release()
    }
    waitForIdle()
    assertEquals(1, clipboard.writes.size, clipboard.writes.toString())
  }

  @Test
  fun secondaryPressDoesNotSelect() = runComposeUiTest {
    show(stateOf())
    onNodeWithTag("diff-list").performMouseInput { rightClick(codePosition("alpha beta", 2)) }
    waitForIdle()
    val items = menu.shown.single()
    assertEquals(listOf("Copy"), items.map { it.label })
    assertFalse(items.single().enabled)
    assertFalse(coordinator.hasSelection)
  }

  @Test
  fun rightClickInsideCardOpensOnlyTheCardMenu() = runComposeUiTest {
    val ui = stateOf()
    val review = ReviewState(CoroutineScope(Dispatchers.Unconfined), Dispatchers.Unconfined) { Instant.fromEpochSeconds(0) }.also { it.onViewerState(ui) }
    review.startDraft(DraftTarget.NewThread(Anchor.Lines("a.kt", Side.New, 1, 1, listOf("alpha beta")), Placement.OnLine(LineLocation(0, 0, 0))))
    review.updateDraft("hello world")
    review.submitDraft()
    setContent {
      val snapshot by review.snapshot.collectAsState()
      CompositionLocalProvider(
        LocalTextSelectionCoordinator provides coordinator,
        LocalNativeContextMenu provides menu,
        LocalReviewActions provides review,
      ) {
        ViewerScreen(ui, {}, review = snapshot)
      }
    }
    waitForIdle()
    onNodeWithText("hello world").performMouseInput { rightClick(Offset(10f, height / 2f)) }
    waitForIdle()
    assertEquals(1, menu.shown.size)
  }

  @Test
  fun rightClickInsideCardKeepsDiffSelection() = runComposeUiTest {
    val ui = stateOf()
    val review = ReviewState(CoroutineScope(Dispatchers.Unconfined), Dispatchers.Unconfined) { Instant.fromEpochSeconds(0) }.also { it.onViewerState(ui) }
    review.startDraft(DraftTarget.NewThread(Anchor.Lines("a.kt", Side.New, 1, 1, listOf("alpha beta")), Placement.OnLine(LineLocation(0, 0, 0))))
    review.updateDraft("hello world")
    review.submitDraft()
    setContent {
      val snapshot by review.snapshot.collectAsState()
      CompositionLocalProvider(
        LocalTextSelectionCoordinator provides coordinator,
        LocalNativeContextMenu provides menu,
        LocalReviewActions provides review,
      ) {
        ViewerScreen(ui, {}, review = snapshot)
      }
    }
    waitForIdle()
    drag(codePosition("delta", 0), codePosition("delta", 3))
    assertTrue(coordinator.hasSelection)
    onNodeWithText("hello world").performMouseInput { rightClick(Offset(10f, height / 2f)) }
    waitForIdle()
    assertTrue(coordinator.hasSelection)
    assertTrue(coordinator.copy())
    assertEquals(listOf("del"), clipboard.writes)
  }

  private fun ComposeUiTest.footerPosition(): Offset {
    val bounds = onNodeWithTag("end-of-diff").fetchSemanticsNode().boundsInRoot
    val list = listBounds()
    return Offset(bounds.center.x - list.left, bounds.center.y - list.top)
  }

  @Test
  fun pressOnFooterStartsNoSelection() = runComposeUiTest {
    show(stateOf())
    onNodeWithTag("diff-list").performMouseInput { click(footerPosition()) }
    waitForIdle()
    assertFalse(coordinator.hasSelection)
  }

  @Test
  fun doubleClickOnFooterStartsNoSelection() = runComposeUiTest {
    show(stateOf())
    onNodeWithTag("diff-list").performMouseInput { doubleClick(footerPosition()) }
    waitForIdle()
    assertFalse(coordinator.hasSelection)
  }

  @Test
  fun dragOntoFooterSelectsThroughLastLine() = runComposeUiTest {
    show(stateOf())
    drag(codePosition("alpha beta", 6), footerPosition())
    assertTrue(coordinator.hasSelection)
    assertTrue(coordinator.copy())
    val copied = clipboard.writes.single()
    assertTrue(copied.startsWith("beta\n\tgamma\ndelta"), copied)
    assertTrue(copied.endsWith("epsilon"), copied)
  }
}
