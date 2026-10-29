package io.github.mataku.kdiffviewer.app

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.mataku.kdiffviewer.app.review.QuitWarning
import io.github.mataku.kdiffviewer.app.review.ReviewState
import io.github.mataku.kdiffviewer.app.review.quitWarningOf
import io.github.mataku.kdiffviewer.app.settings.GitBackendController
import io.github.mataku.kdiffviewer.app.ui.ViewerScreenState
import io.github.mataku.kdiffviewer.core.model.RepoPath

@Stable
class KdvAppState(
  val viewer: DiffViewerState,
  val review: ReviewState,
  val gitBackend: GitBackendController,
  val screen: ViewerScreenState,
  private val pickFolder: () -> String?,
  private val onShutdown: () -> Unit,
) {
  var quitWarning by mutableStateOf<QuitWarning?>(null)
    private set

  var quitPrompts by mutableIntStateOf(0)
    private set

  var settingsOpen by mutableStateOf(false)
    private set

  var settingsFocusRequests by mutableIntStateOf(0)
    private set

  fun requestQuit() {
    val warning = quitWarning ?: quitWarningOf(review.snapshot.value)
    if (warning == null) {
      shutdown()
    } else {
      quitWarning = warning
      quitPrompts++
    }
  }

  fun cancelQuit() {
    quitWarning = null
  }

  fun shutdown() {
    onShutdown()
  }

  fun openSettings() {
    settingsOpen = true
    settingsFocusRequests++
  }

  fun closeSettings() {
    settingsOpen = false
  }

  fun openFolder() {
    pickFolder()?.let { viewer.open(RepoPath(it)) }
  }
}
