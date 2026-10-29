package io.github.mataku.kdiffviewer.app.ui.review

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.unit.height
import io.github.mataku.kdiffviewer.app.review.DraftMode
import io.github.mataku.kdiffviewer.app.review.DraftTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class CommentComposerTest {
  private class FakeActions : ReviewActions {
    val calls = mutableListOf<String>()
    override fun startDraft(target: DraftTarget): Boolean = true
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
    override fun setResolved(threadId: String, resolved: Boolean) = Unit
    override fun delete(commentId: String) = Unit
  }

  private fun androidx.compose.ui.test.ComposeUiTest.show(draft: String, actions: FakeActions = FakeActions()) {
    setContent {
      CompositionLocalProvider(LocalDraftText provides mutableStateOf(draft)) {
        CommentComposer(ComposerActions.Save { actions.submitDraft(DraftMode.Single) }, actions::updateDraft, actions::cancelDraft)
      }
    }
  }

  @Test
  fun startsInWrite() = runComposeUiTest {
    show("x")
    onNodeWithTag("comment-composer").assertIsDisplayed()
    onNodeWithTag("composer-preview").assertDoesNotExist()
    onNodeWithTag("composer-mode-write").assertIsSelected()
  }

  @Test
  fun previewRendersDraft() = runComposeUiTest {
    show("**hi**")
    onNodeWithTag("composer-mode-preview").performClick()
    onNode(hasText("hi") and hasAnyAncestor(hasTestTag("composer-preview"))).assertIsDisplayed()
    onNodeWithTag("comment-composer").assertDoesNotExist()
  }

  @Test
  fun blankDraftShowsPlaceholder() = runComposeUiTest {
    show("  ")
    onNodeWithTag("composer-mode-preview").performClick()
    onNodeWithText("Nothing to preview").assertIsDisplayed()
  }

  @Test
  fun cmdShiftPToggles() = runComposeUiTest {
    show("x")
    onNodeWithTag("comment-composer").performKeyInput { withKeyDown(Key.MetaLeft) { withKeyDown(Key.ShiftLeft) { pressKey(Key.P) } } }
    onNodeWithTag("composer-preview").assertIsDisplayed()
    onNodeWithTag("composer-preview").performKeyInput { withKeyDown(Key.MetaLeft) { withKeyDown(Key.ShiftLeft) { pressKey(Key.P) } } }
    onNodeWithTag("comment-composer").assertIsFocused()
  }

  @Test
  fun clickingWriteTabFocusesTextArea() = runComposeUiTest {
    show("x")
    onNodeWithTag("composer-mode-preview").performClick()
    onNodeWithTag("composer-mode-write").performClick()
    onNodeWithTag("comment-composer").assertIsFocused()
  }

  @Test
  fun clickingSelectedTabKeepsWrite() = runComposeUiTest {
    show("x")
    onNodeWithTag("composer-mode-write").performClick()
    onNodeWithTag("comment-composer").assertIsDisplayed().assertIsFocused()
    onNodeWithTag("composer-preview").assertDoesNotExist()
  }

  @Test
  fun escapeCancelsInPreview() = runComposeUiTest {
    val actions = FakeActions()
    show("x", actions)
    onNodeWithTag("composer-mode-preview").performClick()
    onNodeWithTag("composer-preview").performKeyInput { pressKey(Key.Escape) }
    assertEquals(listOf("cancel"), actions.calls)
  }

  @Test
  fun cmdEnterSubmitsInPreview() = runComposeUiTest {
    val actions = FakeActions()
    show("x", actions)
    onNodeWithTag("composer-mode-preview").performClick()
    onNodeWithTag("composer-preview").performKeyInput { withKeyDown(Key.MetaLeft) { pressKey(Key.Enter) } }
    assertEquals(listOf("submit"), actions.calls)
  }

  @Test
  fun cmdEnterWithBlankDraftDoesNotSubmitInPreview() = runComposeUiTest {
    val actions = FakeActions()
    show(" ", actions)
    onNodeWithTag("composer-mode-preview").performClick()
    onNodeWithTag("composer-preview").performKeyInput { withKeyDown(Key.MetaLeft) { pressKey(Key.Enter) } }
    assertEquals(emptyList(), actions.calls)
  }

  @Test
  fun previewKeepsWriteHeight() = runComposeUiTest {
    val draft = mutableStateOf("1\n2\n3\n4\n5\n6")
    setContent {
      CompositionLocalProvider(LocalDraftText provides draft) {
        CommentComposer(ComposerActions.Save {}, { draft.value = it }, {})
      }
    }
    val writeHeight = onNodeWithTag("comment-composer").getUnclippedBoundsInRoot().height
    onNodeWithTag("composer-mode-preview").performClick()
    draft.value = "x"
    waitForIdle()
    val previewHeight = onNodeWithTag("composer-preview").getUnclippedBoundsInRoot().height
    assertTrue(previewHeight >= writeHeight, "preview $previewHeight < write $writeHeight")
  }

  private fun androidx.compose.ui.test.ComposeUiTest.showPost(
    draft: String,
    actions: FakeActions,
    inProgress: Boolean = false,
    single: Boolean = true,
  ) {
    setContent {
      CompositionLocalProvider(LocalDraftText provides mutableStateOf(draft), LocalReviewInProgress provides mutableStateOf(inProgress)) {
        CommentComposer(
          ComposerActions.Post(
            onReview = { actions.submitDraft(DraftMode.Review) },
            onSingle = if (single) ({ actions.submitDraft(DraftMode.Single) }) else null,
          ),
          actions::updateDraft,
          actions::cancelDraft,
        )
      }
    }
  }

  @Test
  fun postWithoutReviewOffersStartAReview() = runComposeUiTest {
    showPost("x", FakeActions())
    onNodeWithText("Start a review").assertIsDisplayed()
    onNodeWithText("Add single comment").assertIsDisplayed()
    onNodeWithText("Add review comment").assertDoesNotExist()
  }

  @Test
  fun postDuringReviewOffersAddReviewComment() = runComposeUiTest {
    showPost("x", FakeActions(), inProgress = true)
    onNodeWithText("Add review comment").assertIsDisplayed()
    onNodeWithText("Start a review").assertDoesNotExist()
  }

  @Test
  fun buttonsCallTheirModes() = runComposeUiTest {
    val actions = FakeActions()
    showPost("x", actions)
    onNodeWithText("Add single comment").performClick()
    onNodeWithText("Start a review").performClick()
    assertEquals(listOf("submit", "review"), actions.calls)
  }

  @Test
  fun cmdEnterStartsReviewAndCmdShiftEnterSendsSingle() = runComposeUiTest {
    val actions = FakeActions()
    showPost("x", actions)
    onNodeWithTag("comment-composer").performKeyInput { withKeyDown(Key.MetaLeft) { pressKey(Key.Enter) } }
    onNodeWithTag("comment-composer").performKeyInput { withKeyDown(Key.MetaLeft) { withKeyDown(Key.ShiftLeft) { pressKey(Key.Enter) } } }
    assertEquals(listOf("review", "submit"), actions.calls)
  }

  @Test
  fun singleHiddenWhenNotOffered() = runComposeUiTest {
    val actions = FakeActions()
    showPost("x", actions, inProgress = true, single = false)
    onNodeWithText("Add single comment").assertDoesNotExist()
    onNodeWithTag("comment-composer").performKeyInput { withKeyDown(Key.MetaLeft) { withKeyDown(Key.ShiftLeft) { pressKey(Key.Enter) } } }
    assertEquals(emptyList(), actions.calls)
  }

  @Test
  fun saveShowsOnlySave() = runComposeUiTest {
    show("x")
    onNodeWithText("Save").assertIsDisplayed()
    onNodeWithText("Start a review").assertDoesNotExist()
    onNodeWithText("Add single comment").assertDoesNotExist()
  }
}
