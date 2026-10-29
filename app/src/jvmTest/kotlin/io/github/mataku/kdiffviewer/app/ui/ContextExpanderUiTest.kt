package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.DpOffset
import io.github.mataku.kdiffviewer.app.CollapsedFile
import io.github.mataku.kdiffviewer.app.DiffMode
import io.github.mataku.kdiffviewer.app.DiffRow
import io.github.mataku.kdiffviewer.app.LoadStatus
import io.github.mataku.kdiffviewer.app.ViewerUiState
import io.github.mataku.kdiffviewer.app.buildFileTree
import io.github.mataku.kdiffviewer.app.fingerprint
import io.github.mataku.kdiffviewer.app.flattenRows
import io.github.mataku.kdiffviewer.app.selection.ClipboardWriter
import io.github.mataku.kdiffviewer.app.selection.ContextMenuItem
import io.github.mataku.kdiffviewer.app.selection.LocalNativeContextMenu
import io.github.mataku.kdiffviewer.app.selection.LocalTextSelectionCoordinator
import io.github.mataku.kdiffviewer.app.selection.NativeContextMenu
import io.github.mataku.kdiffviewer.app.selection.TextSelectionCoordinator
import io.github.mataku.kdiffviewer.app.splitRowsOf
import io.github.mataku.kdiffviewer.core.model.ChangeType
import io.github.mataku.kdiffviewer.core.model.DiffContent
import io.github.mataku.kdiffviewer.core.model.DiffLine
import io.github.mataku.kdiffviewer.core.model.ExpandDirection
import io.github.mataku.kdiffviewer.core.model.FileDiff
import io.github.mataku.kdiffviewer.core.model.Hunk
import io.github.mataku.kdiffviewer.core.model.LineKind
import io.github.mataku.kdiffviewer.core.model.RepoPath
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
