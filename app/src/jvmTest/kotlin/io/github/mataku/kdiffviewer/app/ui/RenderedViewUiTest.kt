package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.DpOffset
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
import io.github.mataku.kdiffviewer.app.review.ReviewSnapshot
import io.github.mataku.kdiffviewer.app.review.buildPlacements
import io.github.mataku.kdiffviewer.app.selection.ClipboardWriter
import io.github.mataku.kdiffviewer.app.selection.ContextMenuItem
import io.github.mataku.kdiffviewer.app.selection.LocalNativeContextMenu
import io.github.mataku.kdiffviewer.app.selection.LocalTextSelectionCoordinator
import io.github.mataku.kdiffviewer.app.selection.NativeContextMenu
import io.github.mataku.kdiffviewer.app.selection.TextSelectionCoordinator
import io.github.mataku.kdiffviewer.app.splitRowsOf
import io.github.mataku.kdiffviewer.app.withBodySwap
import io.github.mataku.kdiffviewer.core.model.ChangeType
import io.github.mataku.kdiffviewer.core.model.DiffContent
import io.github.mataku.kdiffviewer.core.model.DiffLine
import io.github.mataku.kdiffviewer.core.model.FileDiff
import io.github.mataku.kdiffviewer.core.model.Hunk
import io.github.mataku.kdiffviewer.core.model.LineKind
import io.github.mataku.kdiffviewer.core.model.RepoPath
import io.github.mataku.kdiffviewer.core.review.Anchor
import io.github.mataku.kdiffviewer.core.review.Author
import io.github.mataku.kdiffviewer.core.review.Comment
import io.github.mataku.kdiffviewer.core.review.LineLocation
import io.github.mataku.kdiffviewer.core.review.Placement
import io.github.mataku.kdiffviewer.core.review.ReviewThread
import io.github.mataku.kdiffviewer.core.review.Side
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalTestApi::class)
class RenderedViewUiTest {
  private val coordinator = TextSelectionCoordinator(ClipboardWriter {}) { false }
  private val menu = object : NativeContextMenu {
    override fun show(positionInWindow: DpOffset, items: List<ContextMenuItem>) = Unit
  }

  private val hunk = Hunk(1, 1, 1, 2, "", listOf(DiffLine(LineKind.Context, "c", 1, 1), DiffLine(LineKind.Added, "a", null, 2)))

  private fun file(path: String) = FileDiff(path, path, ChangeType.Modified, false, DiffContent.Text(listOf(hunk)))

  private fun stateOf(vararg files: FileDiff): ViewerUiState {
    val list = files.toList()
    val rows = flattenRows(list)
    val headers = rows.mapIndexedNotNull { i, r -> if (r is DiffRow.FileHeader) i else null }
    return ViewerUiState(RepoPath("/repo"), LoadStatus.Done, list, rows, headers, buildFileTree(list), loadId = 1, fingerprints = list.map { it.fingerprint() }, split = splitRowsOf(rows))
  }

  private fun paragraph(text: String, marker: String? = null) = SourceBlock(MdBlock.Paragraph(MdInline(text, emptyList())), 1, 1, if (marker == null) 0 else 1, 0, marker)

  private fun rendered(vararg body: DiffRow): ViewerUiState {
    val raw = stateOf(file("a.md"))
    val swap = raw.bodySwap(mapOf(0 to body.toList()))
    return raw.withBodySwap(swap).copy(rendered = setOf("a.md"), rawBodies = swap.replaced)
  }

  private fun ComposeUiTest.show(state: ViewerUiState, mode: DiffMode = DiffMode.Unified, onToggleFile: (Int) -> Unit = {}, onToggleRendered: (Int) -> Unit = {}) {
    setContent {
      CompositionLocalProvider(LocalTextSelectionCoordinator provides coordinator, LocalNativeContextMenu provides menu) {
        ViewerScreen(state, {}, screenState = rememberViewerScreenState(mode = mode), onToggleFile = onToggleFile, onToggleRendered = onToggleRendered)
      }
    }
  }

  @Test
  fun toggleShownOnlyForRenderable() = runComposeUiTest {
    show(stateOf(file("a.md"), file("b.kt")))
    onNodeWithTag("file-render-toggle-0-raw").assertIsDisplayed()
    onNodeWithTag("file-render-toggle-1-raw").assertDoesNotExist()
  }

  @Test
  fun toggleUsesIconsInsteadOfText() = runComposeUiTest {
    show(stateOf(file("a.md")))
    onNodeWithContentDescription("Raw").assertIsDisplayed()
    onNodeWithContentDescription("Rendered").assertIsDisplayed()
    onNodeWithText("Raw").assertDoesNotExist()
    onNodeWithText("Rendered").assertDoesNotExist()
  }

  @Test
  fun pressingToggleDoesNotCollapse() = runComposeUiTest {
    val toggled = mutableListOf<Int>()
    val collapsed = mutableListOf<Int>()
    show(stateOf(file("a.md")), onToggleFile = { collapsed += it }, onToggleRendered = { toggled += it })
    onNodeWithTag("file-render-toggle-0-rendered").performClick()
    assertEquals(listOf(0), toggled)
    assertEquals(emptyList(), collapsed)
  }

  @Test
  fun pressingSelectedSegmentIsNoOp() = runComposeUiTest {
    val toggled = mutableListOf<Int>()
    show(stateOf(file("a.md")), onToggleRendered = { toggled += it })
    onNodeWithTag("file-render-toggle-0-raw").performClick()
    assertEquals(emptyList(), toggled)
  }

  @Test
  fun selectedSegmentFollowsState() = runComposeUiTest {
    show(rendered(DiffRow.RenderedBlock(0, 0, paragraph("hello"), null)))
    onNodeWithTag("file-render-toggle-0-rendered").assertIsSelected()
    onNodeWithTag("file-render-toggle-0-raw").assertIsNotSelected()
  }

  @Test
  fun rendersBlockFoldAndMarker() = runComposeUiTest {
    show(
      rendered(
        DiffRow.UnchangedFold(0, 0, 2),
        DiffRow.RenderedBlock(0, 1, paragraph("hello"), null),
        DiffRow.RemovedMarker(0, 2, HunkLineRange(0, 0, 2), 3, 5, 3),
      ),
    )
    onNodeWithText("hello").assertIsDisplayed()
    onNodeWithText("⋯ 2 blocks unchanged ⋯").assertIsDisplayed()
    onNodeWithText("─ 3 lines removed ─").assertIsDisplayed()
  }

  @Test
  fun singularLabels() = runComposeUiTest {
    show(rendered(DiffRow.UnchangedFold(0, 0, 1), DiffRow.RemovedMarker(0, 1, HunkLineRange(0, 0, 0), 3, 3, 1)))
    onNodeWithText("⋯ 1 block unchanged ⋯").assertIsDisplayed()
    onNodeWithText("─ 1 line removed ─").assertIsDisplayed()
  }

  @Test
  fun rendersInSplitModeOnce() = runComposeUiTest {
    show(rendered(DiffRow.RenderedBlock(0, 0, paragraph("hello"), null), DiffRow.UnchangedFold(0, 1, 2)), mode = DiffMode.Split)
    onAllNodesWithText("hello").assertCountEquals(1)
    onAllNodesWithText("⋯ 2 blocks unchanged ⋯").assertCountEquals(1)
  }

  @Test
  fun listMarkerIsShown() = runComposeUiTest {
    show(rendered(DiffRow.RenderedBlock(0, 0, paragraph("item", marker = "•"), null)))
    onNodeWithText("•").assertIsDisplayed()
    onNodeWithText("item").assertIsDisplayed()
  }

  private fun thread(id: String, side: Side, end: Int, body: String) = ReviewThread(
    id,
    Anchor.Lines("a.md", side, end, end, listOf("x")),
    listOf(Comment("c$id", Author.User, body, Instant.fromEpochSeconds(0))),
    resolved = false,
    outdated = false,
  )

  private fun reviewOf(vararg threads: ReviewThread) = ReviewSnapshot.Empty.copy(
    threads = threads.toList(),
    placements = buildPlacements(1L, threads.toList(), threads.associate { it.id to Placement.OnLine(LineLocation(0)) }),
  )

  private fun ComposeUiTest.showReview(state: ViewerUiState, review: ReviewSnapshot, mode: DiffMode = DiffMode.Unified) {
    setContent {
      CompositionLocalProvider(LocalTextSelectionCoordinator provides coordinator, LocalNativeContextMenu provides menu) {
        ViewerScreen(state, {}, screenState = rememberViewerScreenState(mode = mode), review = review)
      }
    }
  }

  private fun ComposeUiTest.assertBelow(lower: String, upper: String) {
    val lowerTop = onNodeWithText(lower).getUnclippedBoundsInRoot().top
    val upperTop = onNodeWithText(upper).getUnclippedBoundsInRoot().top
    assertTrue(lowerTop > upperTop, "$lower at $lowerTop should be below $upper at $upperTop")
  }

  @Test
  fun threadShowsBelowItsBlock() = runComposeUiTest {
    val state = rendered(DiffRow.RenderedBlock(0, 0, paragraph("hello").copy(startLine = 10, endLine = 14), null), DiffRow.UnchangedFold(0, 1, 2))
    showReview(state, reviewOf(thread("t", Side.New, 12, "on the block")))
    onNodeWithText("on the block").assertIsDisplayed()
    assertBelow("on the block", "hello")
    assertBelow("⋯ 2 blocks unchanged ⋯", "on the block")
  }

  @Test
  fun threadShowsBelowItsBlockInSplit() = runComposeUiTest {
    val state = rendered(DiffRow.RenderedBlock(0, 0, paragraph("hello").copy(startLine = 10, endLine = 14), null))
    showReview(state, reviewOf(thread("t", Side.New, 12, "on the block")), DiffMode.Split)
    onAllNodesWithText("on the block").assertCountEquals(1)
    assertBelow("on the block", "hello")
  }

  @Test
  fun oldSideThreadShowsBelowItsMarker() = runComposeUiTest {
    val state = rendered(DiffRow.RemovedMarker(0, 0, HunkLineRange(0, 0, 2), 3, 5, 3))
    showReview(state, reviewOf(thread("t", Side.Old, 4, "on the deletion")))
    assertBelow("on the deletion", "─ 3 lines removed ─")
  }

  @Test
  fun threadWithNoRowShowsInFileNotes() = runComposeUiTest {
    val state = rendered(DiffRow.RenderedBlock(0, 0, paragraph("hello").copy(startLine = 10, endLine = 14), null))
    showReview(state, reviewOf(thread("t", Side.New, 30, "off the rows")))
    onNodeWithText("off the rows").assertIsDisplayed()
    assertBelow("hello", "off the rows")
  }

  @Test
  fun codeBlockIsShown() = runComposeUiTest {
    val code = SourceBlock(MdBlock.CodeBlock(null, "val a = 1"), 1, 1, 0, 0, null)
    show(rendered(DiffRow.RenderedBlock(0, 0, code, null)))
    onNodeWithText("val a = 1").assertIsDisplayed()
  }
}
