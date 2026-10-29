package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.DpOffset
import io.github.mataku.kdiffviewer.app.CollapsedFile
import io.github.mataku.kdiffviewer.app.DiffMode
import io.github.mataku.kdiffviewer.app.DiffRow
import io.github.mataku.kdiffviewer.app.LoadStatus
import io.github.mataku.kdiffviewer.app.ViewerUiState
import io.github.mataku.kdiffviewer.app.buildFileTree
import io.github.mataku.kdiffviewer.app.fingerprint
import io.github.mataku.kdiffviewer.app.flattenExpanded
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
import io.github.mataku.kdiffviewer.core.model.ContextExpansion
import io.github.mataku.kdiffviewer.core.model.DiffContent
import io.github.mataku.kdiffviewer.core.model.DiffLine
import io.github.mataku.kdiffviewer.core.model.ExpandDirection
import io.github.mataku.kdiffviewer.core.model.FileDiff
import io.github.mataku.kdiffviewer.core.model.Hunk
import io.github.mataku.kdiffviewer.core.model.LineKind
import io.github.mataku.kdiffviewer.core.model.RepoPath
import io.github.mataku.kdiffviewer.core.model.expandContext
import io.github.mataku.kdiffviewer.core.model.reveal
import io.github.mataku.kdiffviewer.core.review.LineLocation
import io.github.mataku.kdiffviewer.core.review.Placement
import io.github.mataku.kdiffviewer.core.review.anchorOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

@OptIn(ExperimentalTestApi::class)
class ContextExpanderUiTest {
  private val coordinator = TextSelectionCoordinator(ClipboardWriter {}) { false }
  private val menu = object : NativeContextMenu {
    override fun show(positionInWindow: DpOffset, items: List<ContextMenuItem>) = Unit
  }

  private fun context(line: Int) = DiffLine(LineKind.Context, "L$line", line, line)

  private fun replaceHunk(line: Int) = Hunk(
    line - 3,
    7,
    line - 3,
    7,
    "",
    listOf(context(line - 3), context(line - 2), context(line - 1), DiffLine(LineKind.Removed, "old$line", line, null), DiffLine(LineKind.Added, "L$line", null, line), context(line + 1), context(line + 2), context(line + 3)),
  )

  private val file = FileDiff("f.kt", "f.kt", ChangeType.Modified, false, DiffContent.Text(listOf(replaceHunk(11), replaceHunk(40))))

  private fun stateOf(files: List<FileDiff> = listOf(file)): ViewerUiState {
    val rows = flattenRows(files)
    val headers = rows.mapIndexedNotNull { i, r -> if (r is DiffRow.FileHeader) i else null }
    return ViewerUiState(RepoPath("/repo"), LoadStatus.Done, files, rows, headers, buildFileTree(files), loadId = 1, fingerprints = files.map { it.fingerprint() }, split = splitRowsOf(rows))
  }

  private fun ComposeUiTest.show(state: ViewerUiState, mode: DiffMode = DiffMode.Unified, onExpand: (Int, Int, ExpandDirection) -> Unit = { _, _, _ -> }) {
    setContent {
      CompositionLocalProvider(LocalTextSelectionCoordinator provides coordinator, LocalNativeContextMenu provides menu) {
        ViewerScreen(state, {}, mode = mode, onExpandContext = onExpand)
      }
    }
  }

  @Test
  fun controlsShowForEachGap() = runComposeUiTest {
    show(stateOf())
    onNodeWithTag("expand-all-0-0").assertIsDisplayed()
    onNodeWithTag("expand-up-0-0").assertDoesNotExist()
    onNodeWithTag("expand-down-0-1").assertIsDisplayed()
    onNodeWithTag("expand-up-0-1").assertIsDisplayed()
    onNodeWithTag("expand-down-0-2").assertIsDisplayed()
  }

  @Test
  fun clickingAControlReportsFileGapAndDirection() = runComposeUiTest {
    val calls = mutableListOf<Triple<Int, Int, ExpandDirection>>()
    show(stateOf()) { fileIndex, gap, direction -> calls += Triple(fileIndex, gap, direction) }
    onNodeWithTag("expand-down-0-1").performClick()
    onNodeWithTag("expand-up-0-1").performClick()
    onNodeWithTag("expand-all-0-0").performClick()
    onNodeWithTag("expand-down-0-2").performClick()
    assertEquals(
      listOf(Triple(0, 1, ExpandDirection.Down), Triple(0, 1, ExpandDirection.Up), Triple(0, 0, ExpandDirection.All), Triple(0, 2, ExpandDirection.Down)),
      calls,
    )
  }

  @Test
  fun clickingAControlDoesNotSelectText() = runComposeUiTest {
    show(stateOf())
    onNodeWithTag("expand-down-0-1").performClick()
    assertEquals(false, coordinator.hasSelection)
  }

  @Test
  fun controlsShowInSplitMode() = runComposeUiTest {
    show(stateOf(), mode = DiffMode.Split)
    onNodeWithTag("expand-down-0-1").assertIsDisplayed()
    onNodeWithTag("expand-down-0-2").assertIsDisplayed()
  }

  @Test
  fun expansionErrorShowsInFileHeader() = runComposeUiTest {
    val state = stateOf()
    show(state.copy(expansionErrors = mapOf(CollapsedFile("f.kt", state.fingerprints[0]) to "Too large to expand")))
    onNodeWithText("Too large to expand").assertIsDisplayed()
  }

  private val text = List(60) { "L${it + 1}" }

  private fun ViewerUiState.expanded(gapIndex: Int, direction: ExpandDirection): ViewerUiState {
    val expansion = (null as ContextExpansion?).reveal(file, gapIndex, direction, text.size)
    val rows = flattenExpanded(listOf(expandContext(file, expansion, text)))
    val headers = rows.mapIndexedNotNull { i, r -> if (r is DiffRow.FileHeader) i else null }
    return copy(files = listOf(expandContext(file, expansion, text).file), rows = rows, fileHeaderRowIndex = headers, split = splitRowsOf(rows), contentId = contentId + 1)
  }

  @Test
  fun composerStaysFocusedWhileTheSameFileExpandsAbove() = runComposeUiTest {
    var ui by mutableStateOf(stateOf().copy(contentId = 1))
    val review = ReviewState(CoroutineScope(Dispatchers.Unconfined), Dispatchers.Unconfined) { Instant.fromEpochSeconds(0) }.also { it.onViewerState(ui) }
    review.startDraft(DraftTarget.NewThread(anchorOf(file, 0, 5)!!, Placement.OnLine(LineLocation(0))))
    setContent {
      val snapshot by review.snapshot.collectAsState()
      CompositionLocalProvider(LocalTextSelectionCoordinator provides coordinator, LocalNativeContextMenu provides menu, LocalReviewActions provides review) {
        ViewerScreen(ui, {}, review = snapshot)
      }
    }
    onNodeWithTag("comment-composer").performTextInput("keep")
    onNodeWithTag("comment-composer").assertIsFocused()
    ui = ui.expanded(0, ExpandDirection.All)
    waitForIdle()
    onNodeWithText("L1").assertIsDisplayed()
    onNodeWithTag("comment-composer").assertIsFocused()
    onNodeWithTag("comment-composer").assertTextEquals("keep")
  }

  @Test
  fun splitExpansionPairsRevealedContext() = runComposeUiTest {
    show(stateOf().expanded(1, ExpandDirection.Down), mode = DiffMode.Split)
    assertEquals(2, onAllNodesWithText("L20").fetchSemanticsNodes().size)
  }
}
