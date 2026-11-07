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
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import io.github.mataku.kdiffviewer.app.DiffMode
import io.github.mataku.kdiffviewer.app.DiffRow
import io.github.mataku.kdiffviewer.app.HunkLineRange
import io.github.mataku.kdiffviewer.app.LoadStatus
import io.github.mataku.kdiffviewer.app.ViewerUiState
import io.github.mataku.kdiffviewer.app.bodySwap
import io.github.mataku.kdiffviewer.app.buildFileTree
import io.github.mataku.kdiffviewer.app.fingerprint
import io.github.mataku.kdiffviewer.app.flattenRows
import io.github.mataku.kdiffviewer.app.markdown.MdBlock
import io.github.mataku.kdiffviewer.app.markdown.MdInline
import io.github.mataku.kdiffviewer.app.markdown.SourceBlock
import io.github.mataku.kdiffviewer.app.markdown.SourceParse
import io.github.mataku.kdiffviewer.app.markdown.parseMarkdownSource
import io.github.mataku.kdiffviewer.app.renderedBodyRows
import io.github.mataku.kdiffviewer.app.review.DraftMode
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
import io.github.mataku.kdiffviewer.app.withBodySwap
import io.github.mataku.kdiffviewer.core.model.ChangeType
import io.github.mataku.kdiffviewer.core.model.DiffContent
import io.github.mataku.kdiffviewer.core.model.DiffLine
import io.github.mataku.kdiffviewer.core.model.FileDiff
import io.github.mataku.kdiffviewer.core.model.Hunk
import io.github.mataku.kdiffviewer.core.model.LineKind
import io.github.mataku.kdiffviewer.core.model.RepoPath
import io.github.mataku.kdiffviewer.core.review.Anchor
import io.github.mataku.kdiffviewer.core.review.Placement
import io.github.mataku.kdiffviewer.core.review.anchorOfRange
import io.github.mataku.kdiffviewer.core.review.reanchor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import io.github.mataku.kdiffviewer.core.review.Side as ReviewSide

@OptIn(ExperimentalTestApi::class)
class RenderedCommentUiTest {
  private val coordinator = TextSelectionCoordinator(ClipboardWriter {}) { false }
  private val menu = object : NativeContextMenu {
    override fun show(positionInWindow: DpOffset, items: List<ContextMenuItem>) = Unit
  }
  private val review = ReviewState(CoroutineScope(Dispatchers.Unconfined), Dispatchers.Unconfined) { Instant.fromEpochSeconds(0) }

  private val firstHunk = Hunk(
    1,
    7,
    1,
    7,
    "",
    listOf(
      DiffLine(LineKind.Context, "para one", 1, 1),
      DiffLine(LineKind.Context, "", 2, 2),
      DiffLine(LineKind.Added, "para two", null, 3),
      DiffLine(LineKind.Context, "", 3, 4),
      DiffLine(LineKind.Context, "para three", 4, 5),
      DiffLine(LineKind.Removed, "gone a", 5, null),
      DiffLine(LineKind.Removed, "gone b", 6, null),
      DiffLine(LineKind.Context, "", 7, 6),
      DiffLine(LineKind.Added, "para four", null, 7),
    ),
  )
  private val secondHunk = Hunk(20, 0, 20, 1, "", listOf(DiffLine(LineKind.Added, "para six", null, 20)))
  private val file = FileDiff("a.md", "a.md", ChangeType.Modified, false, DiffContent.Text(listOf(firstHunk, secondHunk)))
  private val source = (listOf("para one", "", "para two", "", "para three", "", "para four") + List(12) { "" } + "para six").joinToString("\n")

  private fun rawState(): ViewerUiState {
    val rows = flattenRows(listOf(file))
    return ViewerUiState(RepoPath("/repo"), LoadStatus.Done, listOf(file), rows, listOf(0), buildFileTree(listOf(file)), loadId = 1, fingerprints = listOf(file.fingerprint()), split = splitRowsOf(rows))
  }

  private fun renderedState(body: List<DiffRow> = renderedBodyRows(0, file, (parseMarkdownSource(source) as SourceParse.Blocks).blocks)): ViewerUiState {
    val raw = rawState()
    val swap = raw.bodySwap(mapOf(0 to body))
    return raw.withBodySwap(swap).copy(rendered = setOf("a.md"), rawBodies = swap.replaced)
  }

  private var ui by mutableStateOf(rawState())

  private fun ComposeUiTest.show(state: ViewerUiState = renderedState(), mode: DiffMode = DiffMode.Unified) {
    ui = state
    review.onViewerState(state)
    setContent {
      val snapshot by review.snapshot.collectAsState()
      CompositionLocalProvider(
        LocalTextSelectionCoordinator provides coordinator,
        LocalNativeContextMenu provides menu,
        LocalReviewActions provides review,
      ) {
        Box(Modifier.size(1000.dp, 500.dp)) { ViewerScreen(ui, {}, review = snapshot, screenState = rememberViewerScreenState(mode = mode)) }
      }
    }
  }

  private fun ComposeUiTest.listBounds() = onNodeWithTag("diff-list").fetchSemanticsNode().boundsInRoot

  private fun ComposeUiTest.markerX(): Float = onNodeWithText("para two").fetchSemanticsNode().boundsInRoot.left - 8f * density.density - listBounds().left

  private fun ComposeUiTest.buttonPoint(text: String): Offset {
    val bounds = onNodeWithText(text).fetchSemanticsNode().boundsInRoot
    return Offset(markerX(), bounds.top + 4f * density.density - listBounds().top)
  }

  private fun ComposeUiTest.press(at: Offset) {
    onNodeWithTag("diff-list").performMouseInput {
      moveTo(at)
      press()
    }
    waitForIdle()
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

  private fun draftAnchor(): Anchor.Lines? = ((review.snapshot.value.draft?.target as? DraftTarget.NewThread)?.anchor) as? Anchor.Lines

  private val rawLine = anchorOfRange(file, 0, 0, 2, 2, ReviewSide.New)!!.anchor
  private val rawRange = anchorOfRange(file, 0, 0, 2, 8, ReviewSide.New)!!.anchor

  @Test
  fun clickingBlockButtonDraftsItsLines() = runComposeUiTest {
    show()
    drag(buttonPoint("para two"))
    assertEquals(Anchor.Lines("a.md", ReviewSide.New, 3, 3, listOf("para two")), draftAnchor())
    assertEquals(rawLine, draftAnchor())
  }

  @Test
  fun clickingMarkerButtonDraftsOldLines() = runComposeUiTest {
    show()
    val marker = onNodeWithText("─ 2 lines removed ─").fetchSemanticsNode().boundsInRoot
    drag(Offset(markerX(), marker.center.y - listBounds().top))
    assertEquals(Anchor.Lines("a.md", ReviewSide.Old, 5, 6, listOf("gone a", "gone b")), draftAnchor())
  }

  @Test
  fun dragDownCoversBothBlocks() = runComposeUiTest {
    show()
    drag(buttonPoint("para two"), buttonPoint("para four"))
    assertEquals(rawRange, draftAnchor())
  }

  @Test
  fun dragUpCoversBothBlocks() = runComposeUiTest {
    show()
    drag(buttonPoint("para four"), buttonPoint("para two"))
    assertEquals(rawRange, draftAnchor())
  }

  @Test
  fun dragAcrossHunksClamps() = runComposeUiTest {
    show()
    drag(buttonPoint("para four"), buttonPoint("para six"))
    assertEquals(Anchor.Lines("a.md", ReviewSide.New, 7, 7, listOf("para four")), draftAnchor())
  }

  @Test
  fun dragOverFoldKeepsTheLastBlock() = runComposeUiTest {
    show()
    val fold = onAllNodesWithText("⋯ 1 block unchanged ⋯")[1].fetchSemanticsNode().boundsInRoot
    press(buttonPoint("para four"))
    moveTo(buttonPoint("para two"))
    moveTo(Offset(markerX(), fold.center.y - listBounds().top))
    release()
    assertEquals(rawRange, draftAnchor())
  }

  @Test
  fun nonCommentableBlockHasNoButton() = runComposeUiTest {
    val leaf = SourceBlock(MdBlock.Paragraph(MdInline("para two", emptyList())), 3, 3, 0, 0, null)
    show(renderedState(listOf(DiffRow.RenderedBlock(0, 0, leaf, null))))
    moveTo(buttonPoint("para two"))
    onAllNodesWithTag("add-comment").assertCountEquals(0)
    drag(buttonPoint("para two"))
    assertNull(review.snapshot.value.draft)
  }

  @Test
  fun splitModeDraftsTheSameAnchors() = runComposeUiTest {
    show(mode = DiffMode.Split)
    drag(buttonPoint("para two"), buttonPoint("para four"))
    assertEquals(rawRange, draftAnchor())
  }

  @Test
  fun hoverShowsButtonOnlyOnHoveredRow() = runComposeUiTest {
    show()
    onAllNodesWithTag("add-comment").assertCountEquals(0)
    moveTo(buttonPoint("para two"))
    onAllNodesWithTag("add-comment").assertCountEquals(1)
  }

  @Test
  fun hoverShowsButtonInSplit() = runComposeUiTest {
    show(mode = DiffMode.Split)
    moveTo(buttonPoint("para two"))
    onAllNodesWithTag("add-comment").assertCountEquals(1)
  }

  @Test
  fun pressBesideNotesDoesNotDraft() = runComposeUiTest {
    show()
    drag(buttonPoint("para two"))
    review.updateDraft("my note")
    review.submitDraft(DraftMode.Single)
    waitForIdle()
    assertNull(review.snapshot.value.draft)
    val note = onNodeWithText("my note").fetchSemanticsNode().boundsInRoot
    drag(Offset(markerX(), note.center.y - listBounds().top))
    assertNull(review.snapshot.value.draft)
  }

  @Test
  fun rawAndRenderedShareThread() = runComposeUiTest {
    show()
    drag(buttonPoint("para two"))
    review.updateDraft("shared note")
    review.submitDraft(DraftMode.Single)
    waitForIdle()
    assertEquals(rawLine, review.snapshot.value.threads.single().anchor)
    val rendered = onNodeWithText("shared note").fetchSemanticsNode().boundsInRoot
    assertTrue(rendered.top > onNodeWithText("para two").fetchSemanticsNode().boundsInRoot.top)
    val raw = rawState()
    ui = raw
    review.onViewerState(raw)
    waitForIdle()
    val note = onNodeWithText("shared note").fetchSemanticsNode().boundsInRoot
    assertTrue(note.top > onNodeWithText("para two").fetchSemanticsNode().boundsInRoot.top)
    assertTrue(note.top < onNodeWithText("para three").fetchSemanticsNode().boundsInRoot.top)
  }

  @Test
  fun renderedAnchorReanchorsAfterReload() = runComposeUiTest {
    show()
    drag(buttonPoint("para two"), buttonPoint("para four"))
    val anchor = assertNotNull(draftAnchor())
    val (resolved, placement) = reanchor(anchor, listOf(file))
    assertEquals(anchor, resolved)
    assertIs<Placement.OnLine>(placement)
  }

  @Test
  fun commentRangeLinesAreClipped() = runComposeUiTest {
    val leaf = SourceBlock(MdBlock.Paragraph(MdInline("para two", emptyList())), 1, 30, 0, 0, null)
    show(renderedState(listOf(DiffRow.RenderedBlock(0, 0, leaf, HunkLineRange(0, 2, 2)))))
    drag(buttonPoint("para two"))
    assertEquals(rawLine, draftAnchor())
  }
}
