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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performMultiModalInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
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
import io.github.mataku.kdiffviewer.core.review.Anchor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import io.github.mataku.kdiffviewer.core.review.Side as ReviewSide

@OptIn(ExperimentalTestApi::class)
class CommentRangeUiTest {
  private val coordinator = TextSelectionCoordinator(object : ClipboardWriter {
    override fun write(text: String) = Unit
  }) { false }
  private val menu = object : NativeContextMenu {
    override fun show(positionInWindow: DpOffset, items: List<ContextMenuItem>) = Unit
  }
  private val rangeState = CommentRangeState()
  private val review = ReviewState(CoroutineScope(Dispatchers.Unconfined), Dispatchers.Unconfined) { Instant.fromEpochSeconds(0) }

  private val file = FileDiff(
    "f.kt",
    "f.kt",
    ChangeType.Modified,
    false,
    DiffContent.Text(
      listOf(
        Hunk(
          1,
          4,
          1,
          5,
          "",
          listOf(
            DiffLine(LineKind.Context, "a", 1, 1),
            DiffLine(LineKind.Removed, "r1", 2, null),
            DiffLine(LineKind.Removed, "r2", 3, null),
            DiffLine(LineKind.Added, "n1", null, 2),
            DiffLine(LineKind.Added, "n2", null, 3),
            DiffLine(LineKind.Context, "z", 4, 4),
          ),
        ),
        Hunk(20, 0, 20, 1, "", listOf(DiffLine(LineKind.Added, "q", null, 20))),
      ),
    ),
  )

  private fun stateOf(loadId: Long = 1, split: Boolean = false): ViewerUiState {
    val files = listOf(file)
    val rows = flattenRows(files)
    val headers = rows.mapIndexedNotNull { i, r -> if (r is DiffRow.FileHeader) i else null }
    val state = ViewerUiState(RepoPath("/repo"), LoadStatus.Done, files, rows, headers, buildFileTree(files), loadId = loadId, fingerprints = files.map { it.fingerprint() })
    return if (split) state.copy(split = splitRowsOf(rows)) else state
  }

  private var ui by mutableStateOf(stateOf())

  private fun ComposeUiTest.show(state: ViewerUiState = stateOf(), mode: DiffMode = DiffMode.Unified) {
    ui = state
    review.onViewerState(state)
    setContent {
      val snapshot by review.snapshot.collectAsState()
      CompositionLocalProvider(
        LocalTextSelectionCoordinator provides coordinator,
        LocalNativeContextMenu provides menu,
        LocalReviewActions provides review,
      ) {
        Box(Modifier.size(1000.dp, 500.dp)) { ViewerScreen(ui, {}, review = snapshot, screenState = rememberViewerScreenState(mode = mode), rangeState = rangeState) }
      }
    }
  }

  private fun ComposeUiTest.listBounds() = onNodeWithTag("diff-list").fetchSemanticsNode().boundsInRoot

  private fun ComposeUiTest.markerPoint(text: String, half: Int = 0): Offset {
    val node = onAllNodesWithText(text)[half].fetchSemanticsNode()
    val list = listBounds()
    return Offset(node.boundsInRoot.left - 8f * density.density - list.left, node.boundsInRoot.center.y - list.top)
  }

  private fun ComposeUiTest.press(at: Offset) = onNodeWithTag("diff-list").performMouseInput {
    moveTo(at)
    press()
  }

  private fun ComposeUiTest.moveTo(at: Offset) {
    onNodeWithTag("diff-list").performMouseInput { moveTo(at) }
    waitForIdle()
  }

  private fun ComposeUiTest.release() {
    onNodeWithTag("diff-list").performMouseInput {
      release()
      advanceEventTime(1_000)
    }
    waitForIdle()
  }

  private fun ComposeUiTest.drag(from: Offset, to: Offset? = null) {
    press(from)
    if (to != null) moveTo(to)
    release()
  }

  private fun lines(): Anchor.Lines? = ((review.snapshot.value.draft?.target as? DraftTarget.NewThread)?.anchor) as? Anchor.Lines

  private fun assertRange(side: ReviewSide, start: Int, end: Int) {
    val anchor = assertNotNull(lines())
    assertEquals(side, anchor.side)
    assertEquals(start, anchor.startLine)
    assertEquals(end, anchor.endLine)
  }

  private fun ComposeUiTest.bottomOf(text: String, half: Int = 0) = onAllNodesWithText(text)[half].fetchSemanticsNode().boundsInRoot.bottom

  private fun ComposeUiTest.composerTop() = onNodeWithTag("comment-composer").fetchSemanticsNode().boundsInRoot.top

  @Test
  fun dragOpensRangeDraftUnified() = runComposeUiTest {
    show()
    drag(markerPoint("n1"), markerPoint("z"))
    assertRange(ReviewSide.New, 2, 4)
    assertTrue(composerTop() >= bottomOf("z") - 1f)
  }

  @Test
  fun upwardDragOpensComposerUnderLowerLine() = runComposeUiTest {
    show()
    press(markerPoint("n2"))
    moveTo(markerPoint("z"))
    moveTo(markerPoint("n1"))
    release()
    assertRange(ReviewSide.New, 2, 3)
    assertTrue(composerTop() >= bottomOf("n2") - 1f)
  }

  @Test
  fun dragPastHunkEndClampsToHunk() = runComposeUiTest {
    show()
    drag(markerPoint("n1"), markerPoint("q"))
    assertRange(ReviewSide.New, 2, 4)
  }

  @Test
  fun dragAboveListClampsToHunkFirstLine() = runComposeUiTest {
    show()
    val z = markerPoint("z")
    press(z)
    mainClock.autoAdvance = false
    onNodeWithTag("diff-list").performMouseInput { moveTo(Offset(z.x, -20f)) }
    repeat(10) { mainClock.advanceTimeByFrame() }
    onNodeWithTag("diff-list").performMouseInput { release() }
    mainClock.autoAdvance = true
    waitForIdle()
    val anchor = assertNotNull(lines())
    assertEquals(ReviewSide.New, anchor.side)
    assertEquals(1, anchor.startLine)
    assertEquals(4, anchor.endLine)
  }

  @Test
  fun splitDragAboveListClampsToHunkFirstLine() = runComposeUiTest {
    show(stateOf(split = true), DiffMode.Split)
    val z = markerPoint("z")
    press(z)
    mainClock.autoAdvance = false
    onNodeWithTag("diff-list").performMouseInput { moveTo(Offset(z.x, -20f)) }
    repeat(10) { mainClock.advanceTimeByFrame() }
    onNodeWithTag("diff-list").performMouseInput { release() }
    mainClock.autoAdvance = true
    waitForIdle()
    assertRange(ReviewSide.Old, 1, 4)
  }

  @Test
  fun splitDragUsesPressedPane() = runComposeUiTest {
    show(stateOf(split = true), DiffMode.Split)
    drag(markerPoint("r1"), markerPoint("r2"))
    assertRange(ReviewSide.Old, 2, 3)
    review.cancelDraft()
    waitForIdle()
    drag(markerPoint("n1"), markerPoint("n2"))
    assertRange(ReviewSide.New, 2, 3)
  }

  @Test
  fun draggingHighlightsRange() = runComposeUiTest {
    show()
    press(markerPoint("n1"))
    moveTo(markerPoint("z"))
    assertEquals(CommentRange(0, 0, null, 3, 5), rangeState.dragging)
    release()
    assertNull(rangeState.dragging)
  }

  private fun ComposeUiTest.shiftClick(at: Offset) {
    onNodeWithTag("diff-list").performMultiModalInput {
      key { keyDown(Key.ShiftLeft) }
      mouse { moveTo(at) }
      mouse { press() }
      mouse { release() }
      key { keyUp(Key.ShiftLeft) }
    }
    waitForIdle()
  }

  @Test
  fun shiftClickExtendsBlankDraft() = runComposeUiTest {
    show()
    drag(markerPoint("n1"))
    assertRange(ReviewSide.New, 2, 2)
    shiftClick(markerPoint("z"))
    assertRange(ReviewSide.New, 2, 4)
  }

  @Test
  fun shiftClickInOtherHunkIsPlainPress() = runComposeUiTest {
    show()
    drag(markerPoint("n1"))
    shiftClick(markerPoint("q"))
    assertRange(ReviewSide.New, 20, 20)
  }

  @Test
  fun shiftClickFromContextDraftKeepsNewSide() = runComposeUiTest {
    show()
    drag(markerPoint("a"))
    assertRange(ReviewSide.New, 1, 1)
    shiftClick(markerPoint("z"))
    assertEquals(Anchor.Lines("f.kt", ReviewSide.New, 1, 4, listOf("a", "n1", "n2", "z")), lines())
  }

  @Test
  fun shiftClickOnContextRowExtendsOldDraft() = runComposeUiTest {
    show()
    drag(markerPoint("r1"))
    assertRange(ReviewSide.Old, 2, 2)
    shiftClick(markerPoint("z"))
    assertEquals(Anchor.Lines("f.kt", ReviewSide.Old, 2, 4, listOf("r1", "r2", "z")), lines())
  }

  @Test
  fun splitShiftClickOnOtherPaneIsPlainPress() = runComposeUiTest {
    show(stateOf(split = true), DiffMode.Split)
    drag(markerPoint("r1"))
    assertRange(ReviewSide.Old, 2, 2)
    shiftClick(markerPoint("n2"))
    assertRange(ReviewSide.New, 3, 3)
  }

  @Test
  fun shiftClickKeepsDraftWithText() = runComposeUiTest {
    show()
    drag(markerPoint("n1"))
    review.updateDraft("hi")
    waitForIdle()
    shiftClick(markerPoint("z"))
    assertRange(ReviewSide.New, 2, 2)
    assertEquals("hi", review.snapshot.value.draft?.text)
  }

  @Test
  fun codeDragAcrossMarkerStaysTextSelection() = runComposeUiTest {
    show()
    val code = onNodeWithText("n1").fetchSemanticsNode().boundsInRoot
    val list = listBounds()
    val start = Offset(code.left + 4f - list.left, code.center.y - list.top)
    drag(start, markerPoint("z"))
    assertTrue(coordinator.hasSelection)
    assertNull(review.snapshot.value.draft)
  }

  @Test
  fun reloadDuringDropsDrag() = runComposeUiTest {
    show()
    press(markerPoint("n1"))
    moveTo(markerPoint("z"))
    assertFalse(rangeState.dragging == null)
    ui = stateOf(loadId = 2)
    waitForIdle()
    assertNull(rangeState.dragging)
    release()
    assertNull(review.snapshot.value.draft)
    assertNull(rangeState.dragging)
  }
}
