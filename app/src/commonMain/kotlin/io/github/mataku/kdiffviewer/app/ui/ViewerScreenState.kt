package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import io.github.mataku.kdiffviewer.app.DiffMode
import io.github.mataku.kdiffviewer.app.find.FindRequests
import io.github.mataku.kdiffviewer.app.find.FindState

@Stable
class ViewerScreenState(
  mode: DiffMode = DiffMode.Unified,
  sidebarVisible: Boolean = true,
  treeWidth: Dp = DefaultTreeWidth,
) {
  val findState = FindState()
  val findRequests = FindRequests()
  val scrollJumps = ScrollJumps()

  var mode by mutableStateOf(mode)
    private set

  var sidebarVisible by mutableStateOf(sidebarVisible)
    private set

  var treeWidth by mutableStateOf(treeWidth.coerceIn(MinTreeWidth, MaxTreeWidth))
    private set

  fun changeMode(mode: DiffMode) {
    if (mode != this.mode) this.mode = mode
  }

  fun toggleSidebar() {
    sidebarVisible = !sidebarVisible
  }

  fun resizeTree(delta: Dp) {
    treeWidth = (treeWidth + delta).coerceIn(MinTreeWidth, MaxTreeWidth)
  }
}

@Composable
fun rememberViewerScreenState(
  mode: DiffMode = DiffMode.Unified,
  sidebarVisible: Boolean = true,
  treeWidth: Dp = DefaultTreeWidth,
): ViewerScreenState = remember { ViewerScreenState(mode, sidebarVisible, treeWidth) }
