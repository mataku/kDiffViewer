package io.github.mataku.kdiffviewer.app.ui.mac

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mataku.kdiffviewer.app.ui.LocalDiffColors

/**
 * The shortcut cell of System Settings' Keyboard Shortcuts list: plain text until clicked, then a focused field showing
 * the current shortcut that records the next key combination. Losing focus cancels. An empty [label] means no shortcut.
 */
@Composable
fun MacShortcutRecorder(
  label: String,
  recording: Boolean,
  onStartRecording: () -> Unit,
  onKey: (KeyEvent) -> Boolean,
  onCancel: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = LocalDiffColors.current
  val focusRequester = remember { FocusRequester() }
  if (recording) {
    // onFocusChanged reports "not focused" on attach, before the request lands; only a loss after gaining focus cancels.
    var gainedFocus by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Box(
      modifier = modifier
        .widthIn(min = 80.dp)
        .fieldChrome(colors, focused = true, capsule = false)
        .focusRequester(focusRequester)
        .onFocusChanged {
          if (it.isFocused) {
            gainedFocus = true
          } else if (gainedFocus) {
            onCancel()
          }
        }
        .onPreviewKeyEvent(onKey)
        .semantics(mergeDescendants = true) {}
        .focusable()
        .padding(horizontal = 8.dp, vertical = 3.dp),
      contentAlignment = Alignment.Center,
    ) {
      BasicText(label, style = TextStyle(color = colors.text, fontSize = 13.sp))
    }
  } else {
    Box(
      modifier = modifier
        .widthIn(min = 80.dp)
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onStartRecording)
        .padding(horizontal = 8.dp, vertical = 3.dp),
      contentAlignment = Alignment.CenterEnd,
    ) {
      BasicText(label, style = TextStyle(color = colors.mutedText, fontSize = 13.sp))
    }
  }
}
