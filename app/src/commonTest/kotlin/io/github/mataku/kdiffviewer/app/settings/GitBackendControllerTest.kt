package io.github.mataku.kdiffviewer.app.settings

import io.github.mataku.kdiffviewer.core.git.DiffSource
import io.github.mataku.kdiffviewer.core.git.GitInfo
import io.github.mataku.kdiffviewer.core.git.GitLocation
import io.github.mataku.kdiffviewer.core.model.FileDiff
import io.github.mataku.kdiffviewer.core.model.HeadRef
import io.github.mataku.kdiffviewer.core.model.RepoPath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private object NoopDiffSource : DiffSource {
  override fun load(repo: RepoPath): Flow<FileDiff> = emptyFlow()

  override suspend fun head(repo: RepoPath): HeadRef? = null
}

private fun info(path: String) = GitInfo(path, "git version 2.0 ($path)")

@OptIn(ExperimentalCoroutinesApi::class)
class GitBackendControllerTest {
  private class Harness(
    scope: TestScope,
    initial: GitLocation = GitLocation.Found(info("/initial")),
    private val accepted: Set<String> = setOf("/g", "/a", "/b"),
    private val located: GitLocation = GitLocation.Found(info("/auto")),
    initialGitPath: String? = null,
  ) {
    val storage = FakeSettingsStorage(
      content = initialGitPath?.let { SettingsCodec.encode(Settings(gitPath = it)) },
    )
    val settings = SettingsRepository(
      storage = storage,
      scope = CoroutineScope(Job()),
      ioDispatcher = StandardTestDispatcher(scope.testScheduler),
    )
    var onProbe: (String) -> Unit = {}
    var onLocate: () -> Unit = {}
    var probeFailure: Throwable? = null
    val probed = mutableListOf<String>()
    val locateCalls = mutableListOf<String?>()
    val installed = mutableListOf<GitBackend?>()
    val controller = GitBackendController(
      initial = initial,
      settings = settings,
      scope = scope,
      ioDispatcher = StandardTestDispatcher(scope.testScheduler),
      probe = { path ->
        probed += path
        onProbe(path)
        probeFailure?.let { throw it }
        if (path in accepted) info(path) else null
      },
      locate = { configured ->
        locateCalls += configured
        onLocate()
        located
      },
      createBackend = { GitBackend(it, NoopDiffSource, null) },
      onBackend = { installed += it },
    )
  }

  @Test
  fun initialFoundIsReadyWithoutCallingOnBackend() = runTest(StandardTestDispatcher()) {
    val h = Harness(this)
    advanceUntilIdle()

    assertEquals(GitStatus.Ready(info("/initial")), h.controller.status.value)
    assertTrue(h.installed.isEmpty())
  }

  @Test
  fun initialConfiguredUnavailableIsRejected() = runTest(StandardTestDispatcher()) {
    val h = Harness(this, initial = GitLocation.ConfiguredUnavailable("/gone"))
    advanceUntilIdle()

    assertEquals(GitStatus.Rejected("/gone", null), h.controller.status.value)
    assertTrue(h.installed.isEmpty())
  }

  @Test
  fun recommittingAfterRejectionRetries() = runTest(StandardTestDispatcher()) {
    val h = Harness(
      this,
      initial = GitLocation.ConfiguredUnavailable("/gone"),
      accepted = setOf("/gone"),
      initialGitPath = "/gone",
    )
    h.controller.commit("/gone")
    advanceUntilIdle()

    assertEquals(listOf("/gone"), h.installed.map { it?.info?.path })
    assertEquals(GitStatus.Ready(info("/gone")), h.controller.status.value)
  }

  @Test
  fun validPathIsSavedAndInstalled() = runTest(StandardTestDispatcher()) {
    val h = Harness(this)
    h.controller.commit("/g")
    advanceUntilIdle()

    assertEquals("/g", h.settings.settings.value.gitPath)
    assertEquals(listOf("/g"), h.installed.map { it?.info?.path })
    assertEquals(GitStatus.Ready(info("/g")), h.controller.status.value)
  }

  @Test
  fun invalidPathIsRejectedAndNotSaved() = runTest(StandardTestDispatcher()) {
    val h = Harness(this)
    h.controller.commit("/bad")
    advanceUntilIdle()

    assertEquals(GitStatus.Rejected("/bad", info("/initial")), h.controller.status.value)
    assertNull(h.settings.settings.value.gitPath)
    assertTrue(h.installed.isEmpty())
  }

  @Test
  fun probeFailureIsRejectedAndNotSaved() = runTest(StandardTestDispatcher()) {
    val h = Harness(this)
    h.probeFailure = IllegalStateException("boom")
    h.controller.commit("/g")
    advanceUntilIdle()

    assertEquals(GitStatus.Rejected("/g", info("/initial")), h.controller.status.value)
    assertNull(h.settings.settings.value.gitPath)
    assertTrue(h.installed.isEmpty())
  }

  @Test
  fun rejectedActiveFollowsLastInstalledBackend() = runTest(StandardTestDispatcher()) {
    val h = Harness(this)
    h.controller.commit("/g")
    advanceUntilIdle()
    h.controller.commit("/bad")
    advanceUntilIdle()

    assertEquals(GitStatus.Rejected("/bad", info("/g")), h.controller.status.value)
  }

  @Test
  fun rejectedWithNothingInstalledHasNoActive() = runTest(StandardTestDispatcher()) {
    val h = Harness(this, initial = GitLocation.NotFound)
    h.controller.commit("/bad")
    advanceUntilIdle()

    assertEquals(GitStatus.Rejected("/bad", null), h.controller.status.value)
  }

  @Test
  fun pathIsTrimmed() = runTest(StandardTestDispatcher()) {
    val h = Harness(this)
    h.controller.commit("  /g  ")
    advanceUntilIdle()

    assertEquals(listOf("/g"), h.probed)
  }

  @Test
  fun emptyInputResetsToAutoDetection() = runTest(StandardTestDispatcher()) {
    val h = Harness(this, initialGitPath = "/g")
    h.controller.commit("")
    advanceUntilIdle()

    assertNull(h.settings.settings.value.gitPath)
    assertEquals(listOf<String?>(null), h.locateCalls)
    assertEquals(listOf("/auto"), h.installed.map { it?.info?.path })
    assertEquals(GitStatus.Ready(info("/auto")), h.controller.status.value)
  }

  @Test
  fun emptyInputWithNothingFoundClearsBackend() = runTest(StandardTestDispatcher()) {
    val h = Harness(this, located = GitLocation.NotFound, initialGitPath = "/g")
    h.controller.commit("")
    advanceUntilIdle()

    assertEquals(listOf<GitBackend?>(null), h.installed)
    assertEquals(GitStatus.NotFound, h.controller.status.value)
  }

  @Test
  fun unchangedCommitIsNoOp() = runTest(StandardTestDispatcher()) {
    val h = Harness(this)
    h.controller.commit("/g")
    advanceUntilIdle()
    h.controller.commit("/g")
    advanceUntilIdle()

    assertEquals(1, h.probed.size)
    assertEquals(1, h.installed.size)
  }

  @Test
  fun emptyCommitWithNoSavedPathIsNoOp() = runTest(StandardTestDispatcher()) {
    val h = Harness(this)
    h.controller.commit("  ")
    advanceUntilIdle()

    assertTrue(h.locateCalls.isEmpty())
    assertTrue(h.installed.isEmpty())
  }

  @Test
  fun laterCommitWins() = runTest(StandardTestDispatcher()) {
    val h = Harness(this)
    h.controller.commit("/a")
    h.controller.commit("/b")
    advanceUntilIdle()

    assertEquals("/b", h.settings.settings.value.gitPath)
    assertEquals(listOf("/b"), h.installed.map { it?.info?.path })
  }

  @Test
  fun statusIsCheckingWhileProbing() = runTest(StandardTestDispatcher()) {
    val h = Harness(this)
    h.controller.commit("/g")

    assertEquals(GitStatus.Checking, h.controller.status.value)
    advanceUntilIdle()
  }

  @Test
  fun inFlightProbeCancelledNeitherSavesNorInstalls() = runTest(StandardTestDispatcher()) {
    val h = Harness(this)
    var triggered = false
    h.onProbe = { path ->
      if (path == "/a" && !triggered) {
        triggered = true
        h.controller.commit("/b")
      }
    }
    h.controller.commit("/a")
    advanceUntilIdle()

    assertEquals("/b", h.settings.settings.value.gitPath)
    assertEquals(listOf("/b"), h.installed.map { it?.info?.path })
  }

  @Test
  fun inFlightEmptyCommitCancelledDoesNotSaveNull() = runTest(StandardTestDispatcher()) {
    val h = Harness(
      this,
      initial = GitLocation.Found(info("/g")),
      initialGitPath = "/g",
    )
    var triggered = false
    h.onLocate = {
      if (!triggered) {
        triggered = true
        h.controller.commit("/bad")
      }
    }
    h.controller.commit("")
    advanceUntilIdle()

    assertEquals("/g", h.settings.settings.value.gitPath)
    assertTrue(h.installed.isEmpty())
    assertEquals(GitStatus.Rejected("/bad", info("/g")), h.controller.status.value)
  }
}
