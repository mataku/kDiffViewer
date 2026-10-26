package io.github.mataku.kdiffviewer.app.ui.review

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mataku.kdiffviewer.app.ui.LocalDiffColors
import io.github.mataku.kdiffviewer.app.ui.MacPushButton
import io.github.mataku.kdiffviewer.app.ui.MacSegmented
import io.github.mataku.kdiffviewer.app.ui.MacTextArea
import io.github.mataku.kdiffviewer.app.ui.markdown.MarkdownBody

private enum class ComposerMode(val label: String, val tag: String) {
  Write("Write", "composer-mode-write"),
  Preview("Preview", "composer-mode-preview"),
}

private val PreviewShape = RoundedCornerShape(6.dp)
private val PreviewPadding = 6.dp

@Composable
fun CommentComposer(
  submitLabel: String,
  onTextChange: (String) -> Unit,
  onSubmit: () -> Unit,
  onCancel: () -> Unit,
) {
  val focusRequester = remember { FocusRequester() }
  val previewFocusRequester = remember { FocusRequester() }
  val draftText = LocalDraftText.current
  val latestSubmit by rememberUpdatedState(onSubmit)
  val latestCancel by rememberUpdatedState(onCancel)
  val focusRequests = LocalDraftFocusRequests.current.value
  var mode by remember { mutableStateOf(ComposerMode.Write) }
  var writeHeightPx by remember { mutableIntStateOf(0) }
  LaunchedEffect(focusRequests, mode) {
    if (mode == ComposerMode.Preview) previewFocusRequester.requestFocus() else focusRequester.requestFocus()
  }
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

          event.isMetaPressed && event.isShiftPressed && event.key == Key.P -> {
            mode = if (mode == ComposerMode.Write) ComposerMode.Preview else ComposerMode.Write
            true
          }

          else -> false
        }
      },
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    val text = draftText.value
    MacSegmented(
      options = ComposerMode.entries,
      selected = mode,
      label = { it.label },
      tag = { it.tag },
      onSelect = { mode = it },
      segmentWidth = 56.dp,
    )
    when (mode) {
      ComposerMode.Write -> MacTextArea(
        value = text,
        onValueChange = onTextChange,
        modifier = Modifier
          .testTag("comment-composer")
          .focusRequester(focusRequester)
          .onSizeChanged { writeHeightPx = it.height },
      )

      ComposerMode.Preview -> PreviewPane(
        text = text,
        minHeightPx = writeHeightPx,
        modifier = Modifier.focusRequester(previewFocusRequester),
      )
    }
    Row(
      modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
    ) {
      MacPushButton(text = "Cancel", onClick = onCancel)
      MacPushButton(text = submitLabel, onClick = onSubmit, enabled = text.isNotBlank())
    }
  }
}

@Composable
private fun PreviewPane(
  text: String,
  minHeightPx: Int,
  modifier: Modifier = Modifier,
) {
  val colors = LocalDiffColors.current
  val minHeight = with(LocalDensity.current) { minHeightPx.toDp() } + PreviewPadding * 2
  val placeholderStyle = remember(colors) { TextStyle(color = colors.mutedText, fontSize = 13.sp) }
  Box(
    modifier = modifier
      .testTag("composer-preview")
      .fillMaxWidth()
      .heightIn(min = minHeight)
      .background(colors.background, PreviewShape)
      .border(0.5.dp, colors.border, PreviewShape)
      .padding(horizontal = 8.dp, vertical = PreviewPadding)
      .focusable(),
  ) {
    if (text.isBlank()) {
      BasicText("Nothing to preview", style = placeholderStyle)
    } else {
      MarkdownBody(text)
    }
  }
}
