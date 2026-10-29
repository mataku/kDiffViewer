package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mataku.kdiffviewer.app.find.FindState
import io.github.mataku.kdiffviewer.app.ui.mac.MacIconButton
import io.github.mataku.kdiffviewer.app.ui.mac.MacSearchField

private val PanelShape = RoundedCornerShape(6.dp)

@Composable
fun FindPanel(state: FindState, onStep: (forward: Boolean) -> Unit, modifier: Modifier = Modifier) {
  val colors = LocalDiffColors.current
  val countStyle = remember(colors) { TextStyle(color = colors.mutedText, fontSize = 12.sp) }
  val focusRequester = remember { FocusRequester() }
  var field by remember { mutableStateOf(TextFieldValue(state.query)) }
  if (field.text != state.query) field = TextFieldValue(state.query, TextRange(state.query.length))
  LaunchedEffect(state.focusRequests) {
    field = field.copy(selection = TextRange(0, field.text.length))
    focusRequester.requestFocus()
  }
  Row(
    modifier = modifier
      .testTag("find-panel")
      .shadow(4.dp, PanelShape)
      .background(colors.fileHeaderBackground, PanelShape)
      .border(1.dp, colors.border, PanelShape)
      .padding(horizontal = 6.dp, vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    MacSearchField(
      value = field,
      onValueChange = {
        field = it
        state.query = it.text
      },
      modifier = Modifier
        .testTag("find-field")
        .focusRequester(focusRequester)
        .onPreviewKeyEvent { event ->
          if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
          when (event.key) {
            Key.Enter, Key.NumPadEnter -> {
              onStep(!event.isShiftPressed)
              true
            }

            Key.Escape -> {
              state.dismiss()
              true
            }

            else -> false
          }
        },
      containerModifier = Modifier.weight(1f),
      placeholder = "Find",
    )
    state.countText()?.let { BasicText(it, Modifier.testTag("find-count"), style = countStyle, maxLines = 1) }
    MacIconButton(KdvIcons.Chevron, "Previous match", { onStep(false) }, Modifier.testTag("find-previous"), iconRotation = -90f)
    MacIconButton(KdvIcons.Chevron, "Next match", { onStep(true) }, Modifier.testTag("find-next"), iconRotation = 90f)
    MacIconButton(KdvIcons.Close, "Close", state::close, Modifier.testTag("find-close"))
  }
}
