package io.github.mataku.kdiffviewer.app.settings

import io.github.mataku.kdiffviewer.app.highlight.HighlightPipeline
import io.github.mataku.kdiffviewer.core.git.DiffSource
import io.github.mataku.kdiffviewer.core.git.GitInfo
import io.github.mataku.kdiffviewer.core.git.GitLocation
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
)

sealed interface GitStatus {
  data object Checking : GitStatus

  data class Ready(val info: GitInfo, val configuredRejected: Boolean) : GitStatus

  data class Rejected(val path: String, val active: GitInfo?) : GitStatus

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
      is GitLocation.Found -> GitStatus.Ready(initial.info, initial.configuredRejected)
      GitLocation.NotFound -> GitStatus.NotFound
    },
  )
  val status: StateFlow<GitStatus> = state

  private var active: GitInfo? = (initial as? GitLocation.Found)?.info
  private var job: Job? = null

  fun commit(text: String) {
    val path = text.trim()
    val current = state.value
    if (path == (settings.settings.value.gitPath ?: "") &&
      (current is GitStatus.Ready || current is GitStatus.NotFound)
    ) {
      return
    }
    job?.cancel()
    state.value = GitStatus.Checking
    job = scope.launch {
      if (path.isEmpty()) {
        settings.update { it.copy(gitPath = null) }
        val location = runInterruptible(ioDispatcher) { locate(null) }
        install(location)
      } else {
        val info = runInterruptible(ioDispatcher) { probe(path) }
        if (info == null) {
          state.value = GitStatus.Rejected(path, active)
        } else {
          settings.update { it.copy(gitPath = path) }
          install(GitLocation.Found(info, configuredRejected = false))
        }
      }
    }
  }

  private fun install(location: GitLocation) {
    when (location) {
      is GitLocation.Found -> {
        val backend = createBackend(location.info)
        active = location.info
        onBackend(backend)
        state.value = GitStatus.Ready(location.info, location.configuredRejected)
      }

      GitLocation.NotFound -> {
        active = null
        onBackend(null)
        state.value = GitStatus.NotFound
      }
    }
  }
}
