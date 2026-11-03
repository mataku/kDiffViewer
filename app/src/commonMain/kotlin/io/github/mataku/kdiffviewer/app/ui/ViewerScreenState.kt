package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import io.github.mataku.kdiffviewer.app.DiffMode
import io.github.mataku.kdiffviewer.app.find.FindRequests
import io.github.mataku.kdiffviewer.app.find.FindState
import io.github.mataku.kdiffviewer.core.model.RepoPath

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

  // The window keeps this state across repository swaps, so the conversation panel is tied to the repository it was opened for.
  private var conversationRepo by mutableStateOf<RepoPath?>(null)
  private var conversationShown by mutableStateOf(false)
  private var seenAgentReplies by mutableIntStateOf(0)

  fun isConversationOpen(repo: RepoPath?): Boolean = conversationRepo == repo && conversationShown

  fun isConversationUnread(repo: RepoPath?, agentReplies: Int): Boolean {
    val seen = if (conversationRepo == repo) seenAgentReplies else 0
    return !isConversationOpen(repo) && agentReplies > seen
  }

  fun openConversation(repo: RepoPath?) {
    bindConversation(repo)
    conversationShown = true
  }

  fun toggleConversation(repo: RepoPath?) {
    bindConversation(repo)
    conversationShown = !conversationShown
  }

  fun markConversationSeen(repo: RepoPath?, agentReplies: Int) {
    if (isConversationOpen(repo) && seenAgentReplies != agentReplies) seenAgentReplies = agentReplies
  }

  private fun bindConversation(repo: RepoPath?) {
    if (conversationRepo == repo) return
    conversationRepo = repo
    conversationShown = false
    seenAgentReplies = 0
  }
}

@Composable
fun rememberViewerScreenState(
  mode: DiffMode = DiffMode.Unified,
  sidebarVisible: Boolean = true,
  treeWidth: Dp = DefaultTreeWidth,
): ViewerScreenState = remember { ViewerScreenState(mode, sidebarVisible, treeWidth) }
