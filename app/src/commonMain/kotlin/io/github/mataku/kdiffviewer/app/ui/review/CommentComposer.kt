package io.github.mataku.kdiffviewer.app.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.mataku.kdiffviewer.app.ui.MacPushButton
import io.github.mataku.kdiffviewer.app.ui.MacTextArea

@Composable
fun CommentComposer(
  submitLabel: String,
  onTextChange: (String) -> Unit,
  onSubmit: () -> Unit,
  onCancel: () -> Unit,
) {
  val focusRequester = remember { FocusRequester() }
  val draftText = LocalDraftText.current
  val latestSubmit by rememberUpdatedState(onSubmit)
  val latestCancel by rememberUpdatedState(onCancel)
  val focusRequests = LocalDraftFocusRequests.current.value
  LaunchedEffect(focusRequests) { focusRequester.requestFocus() }
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when {
          event.key == Key.Escape -> {
            latestCancel()
            true
          }

          event.isMetaPressed && (event.key == Key.Enter || event.key == Key.NumPadEnter) -> {
            if (draftText.value.isNotBlank()) latestSubmit()
            true
          }

          else -> false
        }
      },
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    val text = draftText.value
    MacTextArea(
      value = text,
      onValueChange = onTextChange,
      modifier = Modifier.testTag("comment-composer").focusRequester(focusRequester),
    )
    Row(
      modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
    ) {
      MacPushButton(text = "Cancel", onClick = onCancel)
      MacPushButton(text = submitLabel, onClick = onSubmit, enabled = text.isNotBlank())
    }
  }
}
