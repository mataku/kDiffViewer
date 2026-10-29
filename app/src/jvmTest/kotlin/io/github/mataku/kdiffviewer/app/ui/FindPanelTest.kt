package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.unit.dp
import io.github.mataku.kdiffviewer.app.CollapsedFile
import io.github.mataku.kdiffviewer.app.DiffRow
import io.github.mataku.kdiffviewer.app.LoadStatus
import io.github.mataku.kdiffviewer.app.ViewerUiState
import io.github.mataku.kdiffviewer.app.find.FindCommand
import io.github.mataku.kdiffviewer.app.find.FindRequests
import io.github.mataku.kdiffviewer.app.fingerprint
import io.github.mataku.kdiffviewer.app.flattenRows
import io.github.mataku.kdiffviewer.core.model.ChangeType
import io.github.mataku.kdiffviewer.core.model.DiffContent
import io.github.mataku.kdiffviewer.core.model.DiffLine
import io.github.mataku.kdiffviewer.core.model.FileDiff
import io.github.mataku.kdiffviewer.core.model.Hunk
import io.github.mataku.kdiffviewer.core.model.LineKind
import io.github.mataku.kdiffviewer.core.model.RepoPath
import io.github.mataku.kdiffviewer.core.model.displayPath
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class FindPanelTest {
  private fun file(path: String, vararg texts: String): FileDiff {
    val lines = texts.mapIndexed { index, text -> DiffLine(LineKind.Context, text, index + 1, index + 1) }
    return FileDiff(path, path, ChangeType.Modified, false, DiffContent.Text(listOf(Hunk(1, texts.size, 1, texts.size, "", lines))))
  }

  private fun stateOf(files: List<FileDiff>, loadId: Long = 1): ViewerUiState {
    val rows = flattenRows(files)
    val headers = rows.mapIndexedNotNull { index, row -> if (row is DiffRow.FileHeader) index else null }
    return ViewerUiState(RepoPath("/repo"), LoadStatus.Done, files, rows, headers, loadId = loadId, fingerprints = files.map { it.fingerprint() })
  }

  private fun ViewerUiState.collapsing(fileIndex: Int) = copy(collapsed = collapsed + CollapsedFile(files[fileIndex].displayPath, fingerprints[fileIndex]))

  private fun ComposeUiTest.show(ui: () -> ViewerUiState): FindRequests {
    val requests = FindRequests()
    setContent {
      Box(Modifier.size(800.dp, 400.dp)) { ViewerScreen(ui(), {}, findRequests = requests, findContext = EmptyCoroutineContext) }
    }
    waitForIdle()
    requests.request(FindCommand.Show)
    waitForIdle()
    return requests
  }

  private fun ComposeUiTest.type(text: String) {
    onNodeWithTag("find-field").performTextInput(text)
    mainClock.advanceTimeBy(200)
    waitForIdle()
  }

  private val three = listOf(file("a.kt", "foo", "x foo"), file("b.kt", "foo"))

  @Test
  fun showFocusesField() = runComposeUiTest {
    show { stateOf(three) }
    onNodeWithTag("find-panel").assertIsDisplayed()
    onNodeWithTag("find-field").assertIsFocused()
  }

  @Test
  fun typingShowsCount() = runComposeUiTest {
    show { stateOf(three) }
    type("FOO")
    onNodeWithTag("find-count").assertTextEquals("1 of 3")
  }

  @Test
  fun fieldWidthStaysWhileTyping() = runComposeUiTest {
    show { stateOf(three) }
    val empty = onNodeWithTag("find-box").fetchSemanticsNode().boundsInRoot.width
    type("foo")
    onNodeWithTag("find-count").assertTextEquals("1 of 3")
    assertEquals(empty, onNodeWithTag("find-box").fetchSemanticsNode().boundsInRoot.width)
    type("zzz")
    onNodeWithTag("find-count").assertTextEquals("No results")
    assertEquals(empty, onNodeWithTag("find-box").fetchSemanticsNode().boundsInRoot.width)
  }

  @Test
  fun countSitsInsideField() = runComposeUiTest {
    show { stateOf(three) }
    type("foo")
    val box = onNodeWithTag("find-box").fetchSemanticsNode().boundsInRoot
    val count = onNodeWithTag("find-count").fetchSemanticsNode().boundsInRoot
    assertTrue(count.left >= box.left && count.right <= box.right, "count $count inside field $box")
  }

  @Test
  fun countStaysWhileNextQueryIsPending() = runComposeUiTest {
    show { stateOf(three) }
    type("fo")
    onNodeWithTag("find-count").assertTextEquals("1 of 3")
    onNodeWithTag("find-field").performTextInput("o")
    onNodeWithTag("find-count").assertTextEquals("1 of 3")
  }

  @Test
  fun enterAndShiftEnterStep() = runComposeUiTest {
    show { stateOf(three) }
    type("foo")
    onNodeWithTag("find-field").performKeyInput { pressKey(Key.Enter) }
    onNodeWithTag("find-count").assertTextEquals("2 of 3")
    onNodeWithTag("find-field").performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.Enter) } }
    onNodeWithTag("find-count").assertTextEquals("1 of 3")
  }

  @Test
  fun buttonsStep() = runComposeUiTest {
    show { stateOf(three) }
    type("foo")
    onNodeWithTag("find-previous").performClick()
    onNodeWithTag("find-count").assertTextEquals("3 of 3")
    onNodeWithTag("find-next").performClick()
    onNodeWithTag("find-count").assertTextEquals("1 of 3")
  }

  @Test
  fun noResults() = runComposeUiTest {
    show { stateOf(three) }
    type("zzz")
    onNodeWithTag("find-count").assertTextEquals("No results")
  }

  @Test
  fun escClearsThenCloses() = runComposeUiTest {
    show { stateOf(three) }
    type("foo")
    onNodeWithTag("find-field").performKeyInput { pressKey(Key.Escape) }
    onNodeWithTag("find-panel").assertIsDisplayed()
    onNodeWithTag("find-count").assertDoesNotExist()
    onNodeWithTag("find-field").performKeyInput { pressKey(Key.Escape) }
    onNodeWithTag("find-panel").assertDoesNotExist()
  }

  @Test
  fun closeButtonCloses() = runComposeUiTest {
    show { stateOf(three) }
    onNodeWithTag("find-close").performClick()
    onNodeWithTag("find-panel").assertDoesNotExist()
  }

  @Test
  fun nextRequestScrollsToOffscreenMatch() = runComposeUiTest {
    val lines = Array(300) { if (it == 279) "the needle here" else "line $it" }
    val requests = show { stateOf(listOf(file("a.kt", *lines))) }
    type("needle")
    onNodeWithTag("find-close").performClick()
    onNodeWithTag("diff-list").performKeyInput { pressKey(Key.MoveHome) }
    requests.request(FindCommand.Next)
    waitForIdle()
    onNodeWithTag("find-panel").assertIsDisplayed()
    onNodeWithText("the needle here").assertIsDisplayed()
  }

  @Test
  fun typingRevealsFirstMatch() = runComposeUiTest {
    val lines = Array(300) { if (it == 279) "the needle here" else "line $it" }
    show { stateOf(listOf(file("a.kt", *lines))) }
    type("needle")
    onNodeWithText("the needle here").assertIsDisplayed()
  }

  @Test
  fun collapsedFileExcluded() = runComposeUiTest {
    show { stateOf(listOf(file("a.kt", "foo"), file("b.kt", "foo"))).collapsing(1) }
    type("foo")
    onNodeWithTag("find-count").assertTextEquals("1 of 1")
  }

  @Test
  fun collapsingUpdatesCount() = runComposeUiTest {
    var ui by mutableStateOf(stateOf(listOf(file("a.kt", "foo"), file("b.kt", "foo"))))
    show { ui }
    type("foo")
    onNodeWithTag("find-count").assertTextEquals("1 of 2")
    ui = ui.collapsing(1)
    waitForIdle()
    onNodeWithTag("find-count").assertTextEquals("1 of 1")
  }

  @Test
  fun reloadToFewerFilesDoesNotCrash() = runComposeUiTest {
    var ui by mutableStateOf(stateOf(listOf(file("a.kt", "foo"), file("b.kt", "foo"), file("c.kt", "foo"))))
    show { ui }
    type("foo")
    onNodeWithTag("find-count").assertTextEquals("1 of 3")
    ui = stateOf(listOf(file("a.kt", "x foo")), loadId = 2)
    waitForIdle()
    onNodeWithTag("find-count").assertTextEquals("1 of 1")
  }
}
