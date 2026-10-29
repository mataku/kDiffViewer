package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.DpOffset
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
import io.github.mataku.kdiffviewer.app.ui.mac.SCROLLBAR_TAG
import io.github.mataku.kdiffviewer.core.model.ChangeType
import io.github.mataku.kdiffviewer.core.model.DiffContent
import io.github.mataku.kdiffviewer.core.model.DiffLine
import io.github.mataku.kdiffviewer.core.model.FileDiff
import io.github.mataku.kdiffviewer.core.model.Hunk
import io.github.mataku.kdiffviewer.core.model.LineKind
import io.github.mataku.kdiffviewer.core.model.RepoPath
import kotlin.test.Test
import kotlin.test.assertFalse

@OptIn(ExperimentalTestApi::class)
class DiffScrollbarUiTest {
  private val coordinator = TextSelectionCoordinator(ClipboardWriter {}) { false }
  private val menu = object : NativeContextMenu {
    override fun show(positionInWindow: DpOffset, items: List<ContextMenuItem>) = Unit
  }

  private val longFile = FileDiff(
    "long.kt",
    "long.kt",
    ChangeType.Modified,
    false,
    DiffContent.Text(listOf(Hunk(1, 300, 1, 300, "", List(300) { DiffLine(LineKind.Context, "line $it", it + 1, it + 1) }))),
  )

  private fun ComposeUiTest.show() {
    val files = listOf(longFile)
    val rows = flattenRows(files)
    val headers = rows.mapIndexedNotNull { i, r -> if (r is DiffRow.FileHeader) i else null }
    val state = ViewerUiState(RepoPath("/repo"), LoadStatus.Done, files, rows, headers, buildFileTree(files), loadId = 1, fingerprints = files.map { it.fingerprint() })
    setContent {
      CompositionLocalProvider(LocalTextSelectionCoordinator provides coordinator, LocalNativeContextMenu provides menu) {
        ViewerScreen(state, {})
      }
    }
  }

  private fun ComposeUiTest.dragScrollbarDown() {
    onNodeWithTag(SCROLLBAR_TAG).performMouseInput {
      moveTo(Offset(centerX, top + 10f))
      press()
      moveTo(center)
      release()
    }
    waitForIdle()
  }

  @Test
  fun draggingTheThumbScrollsTheList() = runComposeUiTest {
    show()
    onNodeWithText("line 0").assertIsDisplayed()
    dragScrollbarDown()
    onNodeWithText("line 0").assertDoesNotExist()
  }

  @Test
  fun draggingTheThumbDoesNotSelectText() = runComposeUiTest {
    show()
    dragScrollbarDown()
    assertFalse(coordinator.hasSelection)
  }
}
