package io.github.mataku.kdiffviewer.app.ui.review

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.withKeyDown
import io.github.mataku.kdiffviewer.app.review.DraftTarget
import io.github.mataku.kdiffviewer.core.review.Anchor
import io.github.mataku.kdiffviewer.core.review.Author
import io.github.mataku.kdiffviewer.core.review.Comment
import io.github.mataku.kdiffviewer.core.review.ReviewThread
import io.github.mataku.kdiffviewer.core.review.Side
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

@OptIn(ExperimentalTestApi::class)
class ThreadCardTest {
  private class FakeActions : ReviewActions {
    val calls = mutableListOf<String>()
    override fun startDraft(target: DraftTarget): Boolean {
      calls += "start:$target"
      return true
    }
    override fun updateDraft(text: String) {
      calls += "update:$text"
    }
    override fun cancelDraft() {
      calls += "cancel"
    }
    override fun submitDraft() {
      calls += "submit"
    }
    override fun setResolved(threadId: String, resolved: Boolean) {
      calls += "resolved:$threadId:$resolved"
    }
    override fun delete(commentId: String) {
      calls += "delete:$commentId"
    }
  }

  private val at = Instant.fromEpochSeconds(0)
  private val anchor = Anchor.Lines("a.kt", Side.New, 1, 1, listOf("val snapshotLine = 1"))

  private fun thread(
    resolved: Boolean = false,
    outdated: Boolean = false,
    comments: List<Comment> = listOf(
      Comment("c1", Author.User, "user body", at),
      Comment("c2", Author.Agent("claude"), "agent body", at),
    ),
  ) = ReviewThread("t1", anchor, comments, resolved, outdated)

  @Test
  fun resolvedThreadWithReplyDraftIsShownExpanded() = runComposeUiTest {
    setContent {
      CompositionLocalProvider(LocalReviewActions provides FakeActions(), LocalDraftText provides mutableStateOf("typing")) {
        ThreadCard(thread(resolved = true), DraftTarget.Reply("t1"))
      }
    }
    onNodeWithTag("thread-t1-collapsed").assertDoesNotExist()
    onNodeWithTag("comment-composer").assertIsDisplayed()
  }

  @Test
  fun resolvedThreadWithEditDraftIsShownExpanded() = runComposeUiTest {
    setContent {
      CompositionLocalProvider(LocalReviewActions provides FakeActions(), LocalDraftText provides mutableStateOf("typing")) {
        ThreadCard(thread(resolved = true), DraftTarget.Edit("c1"))
      }
    }
    onNodeWithTag("thread-t1-collapsed").assertDoesNotExist()
    onNodeWithTag("comment-composer").assertIsDisplayed()
  }

  @Test
  fun showsBodiesAndAgentBadge() = runComposeUiTest {
    setContent { CompositionLocalProvider(LocalReviewActions provides FakeActions()) { ThreadCard(thread(), null) } }
    onNodeWithText("user body").assertIsDisplayed()
    onNodeWithText("agent body").assertIsDisplayed()
    onNodeWithText("You").assertIsDisplayed()
    onNodeWithText("claude").assertIsDisplayed()
    onNodeWithText("Agent").assertIsDisplayed()
  }

  @Test
  fun resolveCallsActions() = runComposeUiTest {
    val actions = FakeActions()
    setContent { CompositionLocalProvider(LocalReviewActions provides actions) { ThreadCard(thread(), null) } }
    onNodeWithText("Resolve").performClick()
    assertEquals(listOf("resolved:t1:true"), actions.calls)
  }

  @Test
  fun replyStartsDraft() = runComposeUiTest {
    val actions = FakeActions()
    setContent { CompositionLocalProvider(LocalReviewActions provides actions) { ThreadCard(thread(), null) } }
    onNodeWithText("Reply").performClick()
    assertEquals(listOf("start:Reply(threadId=t1)"), actions.calls)
  }

  @Test
  fun replyDraftShowsComposerInsideCard() = runComposeUiTest {
    setContent {
      CompositionLocalProvider(LocalReviewActions provides FakeActions(), LocalDraftText provides mutableStateOf("hi")) {
        ThreadCard(thread(), DraftTarget.Reply("t1"))
      }
    }
    onNodeWithTag("comment-composer").assertIsDisplayed()
  }

  @Test
  fun resolvedCardIsCollapsedAndExpandsOnClick() = runComposeUiTest {
    setContent { CompositionLocalProvider(LocalReviewActions provides FakeActions()) { ThreadCard(thread(resolved = true), null) } }
    onNodeWithTag("thread-t1-collapsed").assertIsDisplayed()
    onNodeWithText("Resolved · user body").assertIsDisplayed()
    onAllNodesWithText("agent body").assertCountEquals(0)
    onNodeWithTag("thread-t1-collapsed").performClick()
    onNodeWithText("agent body").assertIsDisplayed()
    onNodeWithText("Unresolve").assertIsDisplayed()
  }

  @Test
  fun editAndDeleteOnlyOnUserComments() = runComposeUiTest {
    val actions = FakeActions()
    setContent { CompositionLocalProvider(LocalReviewActions provides actions) { ThreadCard(thread(), null) } }
    onAllNodesWithText("Edit").assertCountEquals(1)
    onAllNodesWithText("Delete").assertCountEquals(1)
    onNodeWithText("Delete").performClick()
    onNodeWithText("Edit").performClick()
    assertEquals(listOf("delete:c1", "start:Edit(commentId=c1)"), actions.calls)
  }

  @Test
  fun outdatedCardShowsBadgeAndSnapshot() = runComposeUiTest {
    setContent { CompositionLocalProvider(LocalReviewActions provides FakeActions()) { ThreadCard(thread(outdated = true), null) } }
    onNodeWithText("Outdated").assertIsDisplayed()
    onNodeWithText("val snapshotLine = 1").assertIsDisplayed()
  }

  @Test
  fun composerCommandEnterSubmitsAndEscapeCancels() = runComposeUiTest {
    var submits = 0
    var cancels = 0
    setContent {
      Composer("hello", onTextChange = {}, onSubmit = { submits++ }, onCancel = { cancels++ })
    }
    onNodeWithTag("comment-composer").performKeyInput {
      withKeyDown(Key.MetaLeft) { pressKey(Key.Enter) }
    }
    assertEquals(1, submits)
    onNodeWithTag("comment-composer").performKeyInput { pressKey(Key.Escape) }
    assertEquals(1, cancels)
  }

  @Test
  fun submitDisabledWhenBlank() = runComposeUiTest {
    var text = "  "
    setContent {
      Composer(text, onTextChange = { text = it }, onSubmit = {}, onCancel = {})
    }
    onNode(hasText("Comment")).assertIsNotEnabled()
  }

  @Test
  fun submitEnabledWhenNotBlank() = runComposeUiTest {
    setContent { Composer("x", onTextChange = {}, onSubmit = {}, onCancel = {}) }
    onNode(hasText("Comment")).assertIsEnabled()
  }

  @Composable
  private fun Composer(text: String, onTextChange: (String) -> Unit, onSubmit: () -> Unit, onCancel: () -> Unit) {
    CompositionLocalProvider(LocalDraftText provides mutableStateOf(text)) {
      CommentComposer("Comment", onTextChange = onTextChange, onSubmit = onSubmit, onCancel = onCancel)
    }
  }

  @Test
  fun unresolveCallsActions() = runComposeUiTest {
    val actions = FakeActions()
    setContent { CompositionLocalProvider(LocalReviewActions provides actions) { ThreadCard(thread(resolved = true), null) } }
    onNodeWithTag("thread-t1-collapsed").performClick()
    onNodeWithText("Unresolve").performClick()
    assertEquals(listOf("resolved:t1:false"), actions.calls)
  }

  @Test
  fun editDraftShowsComposerInPlaceOfBody() = runComposeUiTest {
    val actions = FakeActions()
    setContent {
      CompositionLocalProvider(LocalReviewActions provides actions, LocalDraftText provides mutableStateOf("edited text")) {
        ThreadCard(thread(), DraftTarget.Edit("c1"))
      }
    }
    onNodeWithTag("comment-composer").assertIsDisplayed()
    onNodeWithText("edited text").assertIsDisplayed()
    onAllNodesWithText("user body").assertCountEquals(0)
    onNodeWithText("agent body").assertIsDisplayed()
    onNodeWithText("Save").assertIsEnabled()
    onNodeWithTag("comment-composer").performKeyInput {
      withKeyDown(Key.MetaLeft) { pressKey(Key.Enter) }
    }
    assertEquals(listOf("submit"), actions.calls)
  }
}
