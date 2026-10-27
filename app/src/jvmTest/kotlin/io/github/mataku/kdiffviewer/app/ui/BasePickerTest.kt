package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import io.github.mataku.kdiffviewer.app.ViewerError
import io.github.mataku.kdiffviewer.core.git.DiffLoadException
import io.github.mataku.kdiffviewer.core.model.BaseSpec
import io.github.mataku.kdiffviewer.core.model.BranchRef
import io.github.mataku.kdiffviewer.core.model.HeadRef
import io.github.mataku.kdiffviewer.core.model.ResolvedBase
import kotlinx.coroutines.CompletableDeferred
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class BasePickerTest {
  private val base = ResolvedBase.Commit("origin/develop", "m1", "a1b2c3d")
  private val head = HeadRef.Branch("feature-x")
  private val branches = listOf(
    BranchRef("feature-x", false),
    BranchRef("main", false),
    BranchRef("origin/x", false),
    BranchRef("origin/develop", true),
    BranchRef("origin/x", true),
  )

  private class Recorder(var result: ViewerError? = null) {
    val specs = mutableListOf<BaseSpec>()
    suspend fun switch(spec: BaseSpec): ViewerError? {
      specs += spec
      return result
    }
  }

  private fun ComposeUiTest.show(
    recorder: Recorder,
    baseSpec: BaseSpec = BaseSpec.Default,
    loadBranches: suspend () -> List<BranchRef> = { branches },
  ) {
    setContent {
      KdvTheme(darkTheme = false) {
        BasePicker(base, baseSpec, head, loadBranches, recorder::switch, TextStyle(fontSize = 12.sp))
      }
    }
  }

  private fun ComposeUiTest.open() {
    onNodeWithTag("base-picker").performClick()
    waitForIdle()
  }

  private fun ComposeUiTest.type(text: String) {
    onNodeWithTag("base-filter").performTextInput(text)
    waitForIdle()
  }

  private fun ComposeUiTest.enter() {
    onNodeWithTag("base-filter").performKeyInput { pressKey(Key.Enter) }
    waitForIdle()
  }

  private fun ComposeUiTest.assertGone(tag: String) = onNodeWithTag(tag).assertDoesNotExist()

  private fun ComposeUiTest.checkIn(tag: String) = onNode(hasTestTag("base-check") and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true)

  @Test
  fun clickOpensPopupWithDefaultAndSections() = runComposeUiTest {
    show(Recorder())
    open()
    onNodeWithTag("base-popup").assertIsDisplayed()
    onNodeWithTag("base-default").assertTextEquals("Default (origin/develop)")
    onNodeWithTag("base-section-local").assertTextEquals("Local")
    onNodeWithTag("base-section-remote").assertTextEquals("Remote")
    onNodeWithTag("base-item-local-main").assertIsDisplayed()
    onNodeWithTag("base-item-remote-origin/develop").assertIsDisplayed()
    assertGone("base-item-local-feature-x")
    onNodeWithTag("base-item-local-origin/x").assertExists()
    onNodeWithTag("base-item-remote-origin/x").assertExists()
  }

  @Test
  fun currentBaseRowIsChecked() = runComposeUiTest {
    show(Recorder(), BaseSpec.Ref("origin/develop"))
    open()
    checkIn("base-item-remote-origin/develop").assertExists()
    checkIn("base-default").assertDoesNotExist()
  }

  @Test
  fun defaultRowIsCheckedWhileDefaultIsActive() = runComposeUiTest {
    show(Recorder(), BaseSpec.Default)
    open()
    checkIn("base-default").assertExists()
  }

  @Test
  fun filterHidesRowsAndEmptySections() = runComposeUiTest {
    show(Recorder())
    open()
    type("MAI")
    onNodeWithTag("base-item-local-main").assertIsDisplayed()
    assertGone("base-section-remote")
    assertGone("base-item-remote-origin/develop")
    onNodeWithTag("base-default").assertIsDisplayed()
  }

  @Test
  fun noMatchShowsReturnHint() = runComposeUiTest {
    show(Recorder())
    open()
    type("HEAD~3")
    onNodeWithTag("base-list-status").assertTextEquals("Press Return to use \"HEAD~3\" as the base")
  }

  @Test
  fun returnSwitchesToTrimmedTextAndCloses() = runComposeUiTest {
    val recorder = Recorder()
    show(recorder)
    open()
    type(" HEAD~3 ")
    enter()
    assertEquals(listOf<BaseSpec>(BaseSpec.Ref("HEAD~3")), recorder.specs)
    assertGone("base-popup")
  }

  @Test
  fun optionLikeTextShowsInlineErrorWithoutSwitching() = runComposeUiTest {
    val recorder = Recorder()
    show(recorder)
    open()
    type("--x")
    enter()
    onNodeWithTag("base-error").assertTextEquals("Invalid revision '--x'")
    assertTrue(recorder.specs.isEmpty())
    onNodeWithTag("base-popup").assertExists()
    type("y")
    assertGone("base-error")
  }

  @Test
  fun switchErrorStaysOpenWithText() = runComposeUiTest {
    show(Recorder(ViewerError.UnknownBase("nope")))
    open()
    type("nope")
    enter()
    onNodeWithTag("base-error").assertTextEquals("Unknown revision 'nope'")
    onNodeWithTag("base-popup").assertExists()
  }

  @Test
  fun clickingARowSwitchesAndCloses() = runComposeUiTest {
    val recorder = Recorder()
    show(recorder)
    open()
    onNodeWithTag("base-item-local-main").performClick()
    waitForIdle()
    assertEquals(listOf<BaseSpec>(BaseSpec.Ref("main")), recorder.specs)
    assertGone("base-popup")
    open()
    onNodeWithTag("base-default").performClick()
    waitForIdle()
    assertEquals(BaseSpec.Default, recorder.specs.last())
  }

  @Test
  fun escapeClosesWithoutSwitching() = runComposeUiTest {
    val recorder = Recorder()
    show(recorder)
    open()
    onNodeWithTag("base-filter").performKeyInput { pressKey(Key.Escape) }
    waitForIdle()
    assertGone("base-popup")
    assertTrue(recorder.specs.isEmpty())
  }

  @Test
  fun loadingStatusLine() = runComposeUiTest {
    val gate = CompletableDeferred<List<BranchRef>>()
    show(Recorder()) { gate.await() }
    open()
    onNodeWithTag("base-list-status").assertTextEquals("Loading branches…")
  }

  @Test
  fun failureStatusLineKeepsDefaultAndFilterUsable() = runComposeUiTest {
    show(Recorder()) { throw DiffLoadException.GitFailed(emptyList(), 1, "x") }
    open()
    onNodeWithTag("base-list-status").assertTextEquals("Could not list branches")
    onNodeWithTag("base-default").assertIsDisplayed()
    onNodeWithTag("base-filter").assertIsDisplayed()
  }
}
