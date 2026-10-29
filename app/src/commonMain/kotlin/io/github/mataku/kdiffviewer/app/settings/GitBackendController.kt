package io.github.mataku.kdiffviewer.app.settings

import io.github.mataku.kdiffviewer.app.highlight.HighlightPipeline
import io.github.mataku.kdiffviewer.core.git.DiffSource
import io.github.mataku.kdiffviewer.core.git.GitInfo
import io.github.mataku.kdiffviewer.core.git.GitLocation
import io.github.mataku.kdiffviewer.core.git.WorkingTreeReader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible

data class GitBackend(
  val info: GitInfo,
  val source: DiffSource,
  val highlightPipeline: HighlightPipeline<*>?,
  val workingTree: WorkingTreeReader? = null,
)

sealed interface GitStatus {
  data object Checking : GitStatus

  data class Ready(val info: GitInfo) : GitStatus

  data class Rejected(val path: String, val active: GitInfo?) : GitStatus

  data class AutoDetectFailed(val active: GitInfo?) : GitStatus

  data object NotFound : GitStatus
}

class GitBackendController(
  initial: GitLocation,
  private val settings: SettingsRepository,
  private val scope: CoroutineScope,
  private val ioDispatcher: CoroutineDispatcher,
  private val probe: (String) -> GitInfo?,
  private val locate: (configured: String?) -> GitLocation,
  private val createBackend: (GitInfo) -> GitBackend,
  private val onBackend: (GitBackend?) -> Unit,
) {
  private val state = MutableStateFlow(
    when (initial) {
      is GitLocation.Found -> GitStatus.Ready(initial.info)
      GitLocation.NotFound -> GitStatus.NotFound
      is GitLocation.ConfiguredUnavailable -> GitStatus.Rejected(initial.path, null)
    },
  )
  val status: StateFlow<GitStatus> = state

  private var active: GitInfo? = (initial as? GitLocation.Found)?.info
  private var job: Job? = null

  fun commit(text: String) {
    val path = text.trim()
    val current = state.value
    if (path == (settings.settings.value.gitPath ?: "") && current is GitStatus.Ready) return
    job?.cancel()
    state.value = GitStatus.Checking
    job = scope.launch {
      try {
        if (path.isEmpty()) {
          val location = runInterruptible(ioDispatcher) { locate(null) }
          if (location is GitLocation.Found) {
            settings.update { it.copy(gitPath = null) }
            install(location.info)
          } else {
            state.value = GitStatus.AutoDetectFailed(active)
          }
        } else {
          val info = runInterruptible(ioDispatcher) { probe(path) }
          if (info == null) {
            state.value = GitStatus.Rejected(path, active)
          } else {
            settings.update { it.copy(gitPath = path) }
            install(info)
          }
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        state.value = GitStatus.Rejected(path, active)
      }
    }
  }

  private fun install(info: GitInfo) {
    val backend = createBackend(info)
    active = info
    onBackend(backend)
    state.value = GitStatus.Ready(info)
  }
}
