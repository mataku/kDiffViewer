package io.github.mataku.kdiffviewer.app

import io.github.mataku.kdiffviewer.app.review.DraftMode
import io.github.mataku.kdiffviewer.app.review.DraftTarget
import io.github.mataku.kdiffviewer.app.review.QuitWarning
import io.github.mataku.kdiffviewer.app.review.ReviewState
import io.github.mataku.kdiffviewer.app.settings.FakeSettingsStorage
import io.github.mataku.kdiffviewer.app.settings.GitBackendController
import io.github.mataku.kdiffviewer.app.settings.SettingsRepository
import io.github.mataku.kdiffviewer.app.ui.ViewerScreenState
import io.github.mataku.kdiffviewer.core.git.GitLocation
import io.github.mataku.kdiffviewer.core.model.ChangeType
import io.github.mataku.kdiffviewer.core.model.DiffContent
import io.github.mataku.kdiffviewer.core.model.FileDiff
import io.github.mataku.kdiffviewer.core.model.RepoPath
import io.github.mataku.kdiffviewer.core.review.Anchor
import io.github.mataku.kdiffviewer.core.review.Placement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class KdvAppStateTest {
  private val repo = RepoPath("/repo")
  private val file = FileDiff("a.kt", "a.kt", ChangeType.Modified, false, DiffContent.Text(emptyList()))

  private class Harness(scope: TestScope, picked: String? = null) {
    val dispatcher = StandardTestDispatcher(scope.testScheduler)
    val viewer = DiffViewerState(null, scope, 16)
    val review = ReviewState(scope, dispatcher, { Instant.fromEpochSeconds(0) })
    var shutdowns = 0
    val appState = KdvAppState(
      viewer = viewer,
      review = review,
      gitBackend = GitBackendController(
        initial = GitLocation.NotFound,
        settings = SettingsRepository(FakeSettingsStorage(), CoroutineScope(Job()), dispatcher),
        scope = scope,
        ioDispatcher = dispatcher,
        probe = { null },
        locate = { GitLocation.NotFound },
        createBackend = { error("unused") },
        onBackend = {},
      ),
      screen = ViewerScreenState(),
      pickFolder = { picked },
      onShutdown = { shutdowns++ },
    )
  }

  private fun TestScope.withPendingComment(harness: Harness) {
    harness.review.onViewerState(ViewerUiState(repo = repo, status = LoadStatus.Done, files = listOf(file), loadId = 1))
    advanceUntilIdle()
    harness.review.startDraft(DraftTarget.NewThread(Anchor.File("a.kt"), Placement.OnFile(0)))
    harness.review.updateDraft("fix")
    harness.review.submitDraft(DraftMode.Review)
    advanceUntilIdle()
  }

  @Test
  fun quitWithNothingAtRiskShutsDown() = runTest {
    val harness = Harness(this)
    harness.appState.requestQuit()
    assertEquals(1, harness.shutdowns)
    assertNull(harness.appState.quitWarning)
  }

  @Test
  fun quitWithPendingCommentAsksFirst() = runTest {
    val harness = Harness(this)
    withPendingComment(harness)
    harness.appState.requestQuit()
    assertEquals(0, harness.shutdowns)
    assertEquals(QuitWarning(pendingComments = 1, agentWaiting = false, undeliveredReviews = 0), harness.appState.quitWarning)
    assertEquals(1, harness.appState.quitPrompts)
  }

  @Test
  fun repeatedQuitKeepsWarningAndPromptsAgain() = runTest {
    val harness = Harness(this)
    withPendingComment(harness)
    harness.appState.requestQuit()
    val warning = harness.appState.quitWarning
    harness.appState.requestQuit()
    assertEquals(warning, harness.appState.quitWarning)
    assertEquals(2, harness.appState.quitPrompts)
    assertEquals(0, harness.shutdowns)
  }

  @Test
  fun cancelQuitClearsWarning() = runTest {
    val harness = Harness(this)
    withPendingComment(harness)
    harness.appState.requestQuit()
    harness.appState.cancelQuit()
    assertNull(harness.appState.quitWarning)
    assertEquals(0, harness.shutdowns)
  }

  @Test
  fun openSettingsRequestsFocusEachTime() = runTest {
    val harness = Harness(this)
    harness.appState.openSettings()
    harness.appState.openSettings()
    assertEquals(true, harness.appState.settingsOpen)
    assertEquals(2, harness.appState.settingsFocusRequests)
    harness.appState.closeSettings()
    assertEquals(false, harness.appState.settingsOpen)
  }

  @Test
  fun openFolderOpensPickedRepository() = runTest {
    val harness = Harness(this, picked = "/picked")
    harness.appState.openFolder()
    assertEquals(RepoPath("/picked"), harness.viewer.uiState.value.repo)
  }

  @Test
  fun cancelledPickerLeavesViewerAlone() = runTest {
    val harness = Harness(this, picked = null)
    harness.appState.openFolder()
    assertNull(harness.viewer.uiState.value.repo)
  }
}
