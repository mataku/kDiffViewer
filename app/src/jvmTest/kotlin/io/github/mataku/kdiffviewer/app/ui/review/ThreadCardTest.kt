package io.github.mataku.kdiffviewer.app.ui.review

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import io.github.mataku.kdiffviewer.app.review.DraftMode
import io.github.mataku.kdiffviewer.app.review.DraftTarget
import io.github.mataku.kdiffviewer.app.selection.ClipboardWriter
import io.github.mataku.kdiffviewer.app.selection.ContextMenuItem
import io.github.mataku.kdiffviewer.app.selection.LocalNativeContextMenu
import io.github.mataku.kdiffviewer.app.selection.LocalTextSelectionCoordinator
import io.github.mataku.kdiffviewer.app.selection.NativeContextMenu
import io.github.mataku.kdiffviewer.app.selection.SelectionSource
import io.github.mataku.kdiffviewer.app.selection.TextSelectionCoordinator
import io.github.mataku.kdiffviewer.app.ui.PrimaryModifierKey
import io.github.mataku.kdiffviewer.core.review.Anchor
import io.github.mataku.kdiffviewer.core.review.Author
import io.github.mataku.kdiffviewer.core.review.Comment
import io.github.mataku.kdiffviewer.core.review.ReviewThread
import io.github.mataku.kdiffviewer.core.review.Side
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
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
    override fun submitReview(summary: String?, approve: Boolean) {
    }

    override fun submitDraft(mode: DraftMode) {
      calls += if (mode == DraftMode.Review) "review" else "submit"
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

  private class FakeClipboard : ClipboardWriter {
    val writes = mutableListOf<String>()

    override fun write(text: String) {
      writes += text
    }
  }

  private class FakeSource : SelectionSource {
    var clears = 0
    override val hasSelection = true

    override fun selectedText() = "diff"

    override fun clear() {
      clears++
    }
  }

  private val clipboard = FakeClipboard()
  private var copyOnSelect = false
  private val coordinator = TextSelectionCoordinator(clipboard) { copyOnSelect }
  private val helloThread = thread(comments = listOf(Comment("c1", Author.User, "hello world", at)))

  private fun androidx.compose.ui.test.ComposeUiTest.setCard(content: ReviewThread = helloThread, actions: ReviewActions = FakeActions()) {
    setContent {
      CompositionLocalProvider(LocalReviewActions provides actions, LocalTextSelectionCoordinator provides coordinator) {
        ThreadCard(content, null)
      }
    }
  }

  private fun androidx.compose.ui.test.ComposeUiTest.dragAcrossBody() {
    onNodeWithText("hello world").performMouseInput {
      moveTo(Offset(0f, height / 2f))
      press()
      moveTo(Offset(width + 40f, height / 2f))
      release()
    }
  }

  @Test
  fun bodyTextIsSelectableAndCopies() = runComposeUiTest {
    setCard()
    dragAcrossBody()
    waitForIdle()
    assertTrue(coordinator.copy())
    assertEquals(listOf("hello world"), clipboard.writes)
  }

  @Test
  fun pressInCardClearsDiffSource() = runComposeUiTest {
    val diff = FakeSource()
    setCard()
    coordinator.activate(diff)
    onNodeWithText("hello world").performMouseInput {
      moveTo(Offset(2f, height / 2f))
      press()
      release()
    }
    assertEquals(1, diff.clears)
  }

  @Test
  fun linkStillClicks() = runComposeUiTest {
    val actions = FakeActions()
    setCard(thread(), actions)
    onNodeWithText("Edit").performClick()
    assertEquals(listOf("start:Edit(commentId=c1)"), actions.calls)
  }

  @Test
  fun copyOnSelectWritesOnReleaseInCard() = runComposeUiTest {
    copyOnSelect = true
    setCard()
    onNodeWithText("hello world").performMouseInput {
      moveTo(Offset(0f, height / 2f))
      press()
      moveTo(Offset(width / 2f, height / 2f))
      moveTo(Offset(width + 40f, height / 2f))
      assertEquals(emptyList(), clipboard.writes)
      release()
    }
    waitForIdle()
    assertEquals(listOf("hello world"), clipboard.writes)
  }

  @Test
  fun clickingButtonInCardDoesNotWriteAgain() = runComposeUiTest {
    copyOnSelect = true
    setCard()
    dragAcrossBody()
    waitForIdle()
    assertEquals(1, clipboard.writes.size)
    onNodeWithText("Resolve").performClick()
    waitForIdle()
    assertEquals(1, clipboard.writes.size)
  }

  private class RecordingMenu : NativeContextMenu {
    val shown = mutableListOf<Pair<DpOffset, List<ContextMenuItem>>>()

    override fun show(positionInWindow: DpOffset, items: List<ContextMenuItem>) {
      shown += positionInWindow to items
    }
  }

  private fun androidx.compose.ui.test.ComposeUiTest.setCardWithMenu(menu: NativeContextMenu) {
    setContent {
      CompositionLocalProvider(
        LocalReviewActions provides FakeActions(),
        LocalTextSelectionCoordinator provides coordinator,
        LocalNativeContextMenu provides menu,
      ) {
        ThreadCard(helloThread, null)
      }
    }
  }

  @Test
  fun rightClickShowsCopyMenuAtClickPositionWithoutChangingSelection() = runComposeUiTest {
    val menu = RecordingMenu()
    setCardWithMenu(menu)
    onNodeWithText("hello world").performMouseInput { rightClick(Offset(10f, height / 2f)) }
    waitForIdle()
    assertEquals(1, menu.shown.size)
    val (position, items) = menu.shown.single()
    assertEquals(listOf("Copy"), items.map { it.label })
    assertEquals(false, items.single().enabled)
    val bounds = onNodeWithText("hello world").getBoundsInRoot()
    val density = 1f
    assertTrue(kotlin.math.abs(position.x.value - (bounds.left.value + 10f / density)) <= 1f, "x=${position.x} bounds=$bounds")
    assertTrue(kotlin.math.abs(position.y.value - (bounds.top.value + (bounds.bottom.value - bounds.top.value) / 2f)) <= 1f, "y=${position.y} bounds=$bounds")
    assertEquals(false, coordinator.hasSelection)
  }

  @Test
  fun rightClickAfterScrollShowsMenuAtClickPosition() = runComposeUiTest {
    val menu = RecordingMenu()
    val scroll = ScrollState(0)
    setContent {
      CompositionLocalProvider(
        LocalReviewActions provides FakeActions(),
        LocalTextSelectionCoordinator provides coordinator,
        LocalNativeContextMenu provides menu,
      ) {
        Column(Modifier.testTag("scroller").height(200.dp).verticalScroll(scroll)) {
          Spacer(Modifier.height(150.dp))
          ThreadCard(helloThread, null)
        }
      }
    }
    val before = onNodeWithText("hello world").getBoundsInRoot()
    runOnIdle { scroll.dispatchRawDelta(100f) }
    waitForIdle()
    assertTrue(scroll.value > 0, "scroll=${scroll.value}")
    onNodeWithText("hello world").performMouseInput { rightClick(Offset(10f, height / 2f)) }
    waitForIdle()
    val bounds = onNodeWithText("hello world").getBoundsInRoot()
    assertTrue(bounds.top.value < before.top.value - 1f, "before=$before after=$bounds")
    val position = menu.shown.single().first
    assertTrue(kotlin.math.abs(position.x.value - (bounds.left.value + 10f)) <= 1f, "x=${position.x} bounds=$bounds")
    assertTrue(kotlin.math.abs(position.y.value - (bounds.top.value + (bounds.bottom.value - bounds.top.value) / 2f)) <= 1f, "y=${position.y} bounds=$bounds")
  }

  @Test
  fun rightClickInInCardComposerKeepsCutPasteAndSelectAll() = runComposeUiTest {
    val menu = RecordingMenu()
    setContent {
      CompositionLocalProvider(
        LocalReviewActions provides FakeActions(),
        LocalTextSelectionCoordinator provides coordinator,
        LocalNativeContextMenu provides menu,
        LocalDraftText provides mutableStateOf("draft text"),
      ) {
        ThreadCard(helloThread, DraftTarget.Reply("t1"))
      }
    }
    onNodeWithTag("comment-composer").performMouseInput { rightClick(Offset(10f, height / 2f)) }
    waitForIdle()
    val labels = menu.shown.single().second.map { it.label }
    assertTrue("Paste" in labels, "labels=$labels")
    assertTrue("Select All" in labels, "labels=$labels")
    assertEquals(listOf('C'), menu.shown.single().second.filter { it.label == "Copy" }.map { it.shortcut })
  }

  @Test
  fun rightClickInBodyOffersOnlyCopy() = runComposeUiTest {
    val menu = RecordingMenu()
    setCardWithMenu(menu)
    onNodeWithText("hello world").performMouseInput { rightClick(Offset(10f, height / 2f)) }
    waitForIdle()
    assertEquals(listOf("Copy"), menu.shown.single().second.map { it.label })
  }

  @Test
  fun rightClickKeepsSelectionAndEnablesCopy() = runComposeUiTest {
    val menu = RecordingMenu()
    setCardWithMenu(menu)
    dragAcrossBody()
    waitForIdle()
    onNodeWithText("hello world").performMouseInput { rightClick(Offset(10f, height / 2f)) }
    waitForIdle()
    assertEquals(true, menu.shown.single().second.single().enabled)
    assertTrue(coordinator.copy())
    assertEquals(listOf("hello world"), clipboard.writes)
  }

  @Test
  fun rightClickReleaseDoesNotCopyOnSelect() = runComposeUiTest {
    copyOnSelect = true
    setCard()
    dragAcrossBody()
    waitForIdle()
    val before = clipboard.writes.size
    onNodeWithText("hello world").performMouseInput { rightClick(Offset(10f, height / 2f)) }
    waitForIdle()
    assertEquals(before, clipboard.writes.size)
  }

  @Test
  fun markdownLinkInBodyStillOpensUri() = runComposeUiTest {
    val opened = mutableListOf<String>()
    val handler = object : UriHandler {
      override fun openUri(uri: String) {
        opened += uri
      }
    }
    setContent {
      CompositionLocalProvider(
        LocalReviewActions provides FakeActions(),
        LocalTextSelectionCoordinator provides coordinator,
        LocalUriHandler provides handler,
      ) {
        ThreadCard(thread(comments = listOf(Comment("c1", Author.User, "[docs](https://e.com)", at))), null)
      }
    }
    onNodeWithText("docs").performClick()
    assertEquals(listOf("https://e.com"), opened)
  }

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
  fun rendersMarkdownBody() = runComposeUiTest {
    setContent {
      CompositionLocalProvider(LocalReviewActions provides FakeActions()) {
        ThreadCard(thread(comments = listOf(Comment("c1", Author.User, "Use `foo`\n\n- a\n- b", at))), null)
      }
    }
    onNodeWithText("Use foo").assertIsDisplayed()
    onAllNodesWithText("•").assertCountEquals(2)
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
    onNodeWithText("Edit").performClick()
    assertEquals(listOf("start:Edit(commentId=c1)"), actions.calls)
  }

  @Test
  fun deleteAsksForConfirmationBeforeDeleting() = runComposeUiTest {
    val actions = FakeActions()
    setContent { CompositionLocalProvider(LocalReviewActions provides actions) { ThreadCard(thread(), null) } }
    onNodeWithText("Delete").performClick()
    onNodeWithText("Delete?").assertIsDisplayed()
    onNodeWithText("Cancel").assertIsDisplayed()
    onAllNodesWithText("Edit").assertCountEquals(0)
    assertEquals(emptyList(), actions.calls)
    onNodeWithText("Delete").performClick()
    assertEquals(listOf("delete:c1"), actions.calls)
  }

  @Test
  fun cancellingDeleteKeepsTheComment() = runComposeUiTest {
    val actions = FakeActions()
    setContent { CompositionLocalProvider(LocalReviewActions provides actions) { ThreadCard(thread(), null) } }
    onNodeWithText("Delete").performClick()
    onNodeWithText("Cancel").performClick()
    onAllNodesWithText("Delete?").assertCountEquals(0)
    onNodeWithText("Edit").assertIsDisplayed()
    onNodeWithText("Delete").assertIsDisplayed()
    assertEquals(emptyList(), actions.calls)
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
      withKeyDown(PrimaryModifierKey) { pressKey(Key.Enter) }
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
    onNode(hasText("Save")).assertIsNotEnabled()
  }

  @Test
  fun submitEnabledWhenNotBlank() = runComposeUiTest {
    setContent { Composer("x", onTextChange = {}, onSubmit = {}, onCancel = {}) }
    onNode(hasText("Save")).assertIsEnabled()
  }

  @Composable
  private fun Composer(text: String, onTextChange: (String) -> Unit, onSubmit: () -> Unit, onCancel: () -> Unit) {
    CompositionLocalProvider(LocalDraftText provides mutableStateOf(text)) {
      CommentComposer(ComposerActions.Save(onSubmit), onTextChange = onTextChange, onCancel = onCancel)
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
      withKeyDown(PrimaryModifierKey) { pressKey(Key.Enter) }
    }
    assertEquals(listOf("submit"), actions.calls)
  }

  @Test
  fun pendingCommentShowsBadge() = runComposeUiTest {
    setCard(thread(comments = listOf(Comment("c1", Author.User, "published", at), Comment("c2", Author.User, "later", at, pending = true))))
    onAllNodesWithText("Pending").assertCountEquals(1)
  }

  @Test
  fun publishedCommentHasNoPendingBadge() = runComposeUiTest {
    setCard()
    onNodeWithText("Pending").assertDoesNotExist()
  }

  @Test
  fun pendingThreadOffersOnlyReviewReply() = runComposeUiTest {
    val pending = thread(comments = listOf(Comment("c1", Author.User, "draft", at, pending = true)))
    setContent {
      CompositionLocalProvider(
        LocalReviewActions provides FakeActions(),
        LocalTextSelectionCoordinator provides coordinator,
        LocalReviewInProgress provides mutableStateOf(true),
      ) {
        ThreadCard(pending, DraftTarget.Reply("t1"))
      }
    }
    onNodeWithText("Add review comment").assertExists()
    onNodeWithText("Add single comment").assertDoesNotExist()
    onNodeWithText("Resolve").assertDoesNotExist()
  }

  @Test
  fun publishedThreadReplyOffersBoth() = runComposeUiTest {
    setContent {
      CompositionLocalProvider(LocalReviewActions provides FakeActions(), LocalTextSelectionCoordinator provides coordinator) {
        ThreadCard(helloThread, DraftTarget.Reply("t1"))
      }
    }
    onNodeWithText("Start a review").assertExists()
    onNodeWithText("Add single comment").assertExists()
  }
}
