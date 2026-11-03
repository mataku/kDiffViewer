package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.ui.unit.dp
import io.github.mataku.kdiffviewer.app.DiffMode
import io.github.mataku.kdiffviewer.core.model.RepoPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ViewerScreenStateTest {
  @Test
  fun initialTreeWidthIsClamped() {
    assertEquals(MinTreeWidth, ViewerScreenState(treeWidth = 10.dp).treeWidth)
    assertEquals(MaxTreeWidth, ViewerScreenState(treeWidth = 2000.dp).treeWidth)
  }

  @Test
  fun resizeTreeStaysWithinBounds() {
    val state = ViewerScreenState(treeWidth = 300.dp)
    state.resizeTree(50.dp)
    assertEquals(350.dp, state.treeWidth)
    state.resizeTree(-1000.dp)
    assertEquals(MinTreeWidth, state.treeWidth)
    state.resizeTree(1000.dp)
    assertEquals(MaxTreeWidth, state.treeWidth)
  }

  @Test
  fun changeModeAndToggleSidebar() {
    val state = ViewerScreenState()
    state.changeMode(DiffMode.Split)
    assertEquals(DiffMode.Split, state.mode)
    state.toggleSidebar()
    assertFalse(state.sidebarVisible)
    state.toggleSidebar()
    assertTrue(state.sidebarVisible)
  }

  @Test
  fun conversationUnreadUntilOpened() {
    val state = ViewerScreenState()
    val repo = RepoPath("/a")
    assertFalse(state.isConversationUnread(repo, agentReplies = 0))
    assertTrue(state.isConversationUnread(repo, agentReplies = 1))
    state.toggleConversation(repo)
    assertTrue(state.isConversationOpen(repo))
    state.markConversationSeen(repo, agentReplies = 1)
    state.toggleConversation(repo)
    assertFalse(state.isConversationUnread(repo, agentReplies = 1))
    assertTrue(state.isConversationUnread(repo, agentReplies = 2))
  }

  @Test
  fun conversationResetsForAnotherRepository() {
    val state = ViewerScreenState()
    val a = RepoPath("/a")
    val b = RepoPath("/b")
    state.openConversation(a)
    state.markConversationSeen(a, agentReplies = 3)
    assertFalse(state.isConversationOpen(b))
    assertTrue(state.isConversationUnread(b, agentReplies = 1))
    state.toggleConversation(b)
    assertTrue(state.isConversationOpen(b))
    assertFalse(state.isConversationOpen(a))
  }
}
