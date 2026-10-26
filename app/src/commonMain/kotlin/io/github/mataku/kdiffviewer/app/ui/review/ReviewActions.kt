package io.github.mataku.kdiffviewer.app.ui.review

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.mataku.kdiffviewer.app.review.DraftTarget

interface ReviewActions {
  fun startDraft(target: DraftTarget): Boolean

  fun updateDraft(text: String)

  fun cancelDraft()

  fun submitDraft()

  fun setResolved(threadId: String, resolved: Boolean)

  fun delete(commentId: String)
}

val LocalReviewActions = staticCompositionLocalOf<ReviewActions?> { null }

private val NoDraftText: State<String> = mutableStateOf("")

val LocalDraftText = staticCompositionLocalOf { NoDraftText }

private val NoFocusRequests: State<Int> = mutableStateOf(0)

val LocalDraftFocusRequests = staticCompositionLocalOf { NoFocusRequests }
