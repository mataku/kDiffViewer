package io.github.mataku.kdiffviewer.app

import io.github.mataku.kdiffviewer.core.git.DiffSource
import io.github.mataku.kdiffviewer.core.model.BaseSpec
import io.github.mataku.kdiffviewer.core.model.ChangeType
import io.github.mataku.kdiffviewer.core.model.DiffContent
import io.github.mataku.kdiffviewer.core.model.FileDiff
import io.github.mataku.kdiffviewer.core.model.HeadRef
import io.github.mataku.kdiffviewer.core.model.RepoPath
import io.github.mataku.kdiffviewer.core.model.ResolvedBase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals

class DiffViewerStateThreadingTest {
  private val fileCount = 3000

  private val source = object : DiffSource {
    override suspend fun resolveBase(repo: RepoPath, spec: BaseSpec): ResolvedBase = ResolvedBase.EmptyTree
    override fun load(repo: RepoPath): Flow<FileDiff> = flow {
      repeat(fileCount) { index ->
        emit(FileDiff("f$index", "f$index", ChangeType.Modified, false, DiffContent.Text(emptyList())))
        if (index % 7 == 0) yield()
      }
    }

    override suspend fun head(repo: RepoPath): HeadRef? = null
  }

  @Test
  fun everyFileIsPublishedOnARealSingleThreadedDispatcher() {
    val executor = Executors.newSingleThreadExecutor()
    val dispatcher = executor.asCoroutineDispatcher()
    val scope = CoroutineScope(SupervisorJob() + dispatcher)
    try {
      repeat(20) { attempt ->
        val state = DiffViewerState(source, scope, 1)
        runBlocking(dispatcher) { state.open(RepoPath("/repo")) }
        val ui = runBlocking {
          withTimeout(30_000) { state.uiState.first { it.status != LoadStatus.Loading } }
        }
        assertEquals(LoadStatus.Done, ui.status, "attempt $attempt")
        assertEquals(fileCount, ui.files.size, "attempt $attempt")
      }
    } finally {
      scope.cancel()
      dispatcher.close()
    }
  }

  @Test
  fun parsingOnDefaultDispatcherPublishesEveryFingerprint() {
    val executor = Executors.newSingleThreadExecutor()
    val dispatcher = executor.asCoroutineDispatcher()
    val scope = CoroutineScope(SupervisorJob() + dispatcher)
    try {
      repeat(20) { attempt ->
        val state = DiffViewerState(source, scope, 1, loadContext = Dispatchers.Default)
        runBlocking(dispatcher) { state.open(RepoPath("/repo")) }
        val ui = runBlocking {
          withTimeout(30_000) { state.uiState.first { it.status != LoadStatus.Loading } }
        }
        assertEquals(LoadStatus.Done, ui.status, "attempt $attempt")
        assertEquals(fileCount, ui.files.size, "attempt $attempt")
        assertEquals(ui.files.map { it.fingerprint() }, ui.fingerprints, "attempt $attempt")
      }
    } finally {
      scope.cancel()
      dispatcher.close()
    }
  }
}
