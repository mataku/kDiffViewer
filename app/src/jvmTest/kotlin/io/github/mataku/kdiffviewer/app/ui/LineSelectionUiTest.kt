package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.asAwtTransferable
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.unit.dp
import io.github.mataku.kdiffviewer.app.DiffRow
import io.github.mataku.kdiffviewer.app.LineSelection
import io.github.mataku.kdiffviewer.app.LoadStatus
import io.github.mataku.kdiffviewer.app.ViewerUiState
import io.github.mataku.kdiffviewer.app.afterLineNumberClick
import io.github.mataku.kdiffviewer.app.buildFileTree
import io.github.mataku.kdiffviewer.app.fingerprint
import io.github.mataku.kdiffviewer.app.flattenRows
import io.github.mataku.kdiffviewer.app.review.ReviewSnapshot
import io.github.mataku.kdiffviewer.app.review.ReviewState
import io.github.mataku.kdiffviewer.app.ui.review.LocalReviewActions
import io.github.mataku.kdiffviewer.core.model.ChangeType
import io.github.mataku.kdiffviewer.core.model.DiffContent
import io.github.mataku.kdiffviewer.core.model.DiffLine
import io.github.mataku.kdiffviewer.core.model.FileDiff
import io.github.mataku.kdiffviewer.core.model.Hunk
import io.github.mataku.kdiffviewer.core.model.LineKind
import io.github.mataku.kdiffviewer.core.model.RepoPath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.awt.datatransfer.DataFlavor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

@OptIn(ExperimentalTestApi::class)
class LineSelectionUiTest {
  private val file = FileDiff(
    "a.kt",
    "a.kt",
    ChangeType.Modified,
    false,
    DiffContent.Text(
      listOf(
        Hunk(
          1,
          2,
          1,
          2,
          "",
          listOf(
            DiffLine(LineKind.Removed, "ONE", 1, null),
            DiffLine(LineKind.Added, "TWO", null, 1),
            DiffLine(LineKind.Context, "THREE", 2, 2),
          ),
        ),
      ),
    ),
  )

  private val ui: ViewerUiState = run {
    val files = listOf(file)
    val rows = flattenRows(files)
    val headers = rows.mapIndexedNotNull { i, r -> if (r is DiffRow.FileHeader) i else null }
    ViewerUiState(RepoPath("/repo"), LoadStatus.Done, files, rows, headers, buildFileTree(files), fingerprints = files.map { it.fingerprint() }, loadId = 1)
  }

  private class FakeClipboard : Clipboard {
    var entry: ClipEntry? = null

    override suspend fun getClipEntry(): ClipEntry? = entry

    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
      entry = clipEntry
    }
  }

  private val selection = mutableStateOf<LineSelection?>(null)
  private val clipboard = FakeClipboard()

  private fun ComposeUiTest.showViewer(review: ReviewState? = null) {
    setContent {
      CompositionLocalProvider(LocalClipboard provides clipboard, LocalReviewActions provides review) {
        val snapshot = review?.snapshot?.collectAsState()?.value
        ViewerScreen(
          ui,
          {},
          review = snapshot ?: ReviewSnapshot.Empty,
          selection = selection,
          onLineNumberClick = { rowIndex, extend -> selection.value = selection.value.afterLineNumberClick(ui.rows, rowIndex, extend) },
          onClearSelection = { selection.value = null },
        )
      }
    }
  }

  private fun ComposeUiTest.clickLineNumberOf(text: String) {
    val list = onNodeWithTag("diff-list").fetchSemanticsNode().boundsInRoot
    val line = onNodeWithText(text).fetchSemanticsNode().boundsInRoot
    val x = with(density) { (ContentHorizontalMargin + 8.dp).toPx() }
    onNodeWithTag("diff-list").performMouseInput { click(Offset(x, line.center.y - list.top)) }
    waitForIdle()
  }

  @OptIn(ExperimentalComposeUiApi::class)
  private fun copiedText(): String? = clipboard.entry?.asAwtTransferable?.getTransferData(DataFlavor.stringFlavor) as? String

  @Test
  fun clickingLineNumberSelectsLine() = runComposeUiTest {
    showViewer()
    clickLineNumberOf("TWO")
    assertEquals(LineSelection(3, 3, 3), selection.value)
  }

  @Test
  fun clickingCodeDoesNotSelect() = runComposeUiTest {
    showViewer()
    onNodeWithText("TWO").performMouseInput { click(center) }
    waitForIdle()
    assertNull(selection.value)
  }

  @Test
  fun shiftClickExtendsSelection() = runComposeUiTest {
    showViewer()
    clickLineNumberOf("ONE")
    onNodeWithTag("diff-list").performKeyInput { keyDown(Key.ShiftLeft) }
    clickLineNumberOf("THREE")
    onNodeWithTag("diff-list").performKeyInput { keyUp(Key.ShiftLeft) }
    assertEquals(LineSelection(2, 2, 4), selection.value)
  }

  @Test
  fun commandCCopiesSelectedCode() = runComposeUiTest {
    showViewer()
    clickLineNumberOf("ONE")
    selection.value = LineSelection(2, 2, 4)
    waitForIdle()
    onNodeWithTag("diff-list").performKeyInput { withKeyDown(Key.MetaLeft) { pressKey(Key.C) } }
    waitForIdle()
    assertEquals("ONE\nTWO\nTHREE", copiedText())
  }

  @Test
  fun escapeClearsSelection() = runComposeUiTest {
    showViewer()
    clickLineNumberOf("TWO")
    onNodeWithTag("diff-list").performKeyInput { pressKey(Key.Escape) }
    waitForIdle()
    assertNull(selection.value)
  }

  @Test
  fun addCommentButtonDoesNotChangeSelection() = runComposeUiTest {
    val review = ReviewState(CoroutineScope(Dispatchers.Unconfined), Dispatchers.Unconfined) { Instant.fromEpochSeconds(0) }.also { it.onViewerState(ui) }
    showViewer(review)
    onNodeWithText("TWO").performMouseInput { moveTo(center) }
    waitForIdle()
    onNodeWithTag("add-comment").performClick()
    waitForIdle()
    assertNull(selection.value)
    onNodeWithTag("comment-composer").assertExists()
  }
}
