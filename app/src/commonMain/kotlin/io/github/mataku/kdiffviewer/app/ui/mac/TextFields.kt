package io.github.mataku.kdiffviewer.app.ui.mac

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mataku.kdiffviewer.app.ui.KdvIcons
import io.github.mataku.kdiffviewer.app.ui.LocalDiffColors

@Composable
fun MacTextField(
  value: String,
  onValueChange: (String) -> Unit,
  modifier: Modifier = Modifier,
  containerModifier: Modifier = Modifier,
  placeholder: String? = null,
) {
  val colors = LocalDiffColors.current
  var focused by remember { mutableStateOf(false) }
  val ringColor = colors.accent.copy(alpha = 0.5f)
  Box(
    modifier = containerModifier
      .drawBehind {
        if (focused) {
          val ring = 3.dp.toPx()
          drawRoundRect(
            color = ringColor,
            topLeft = Offset(-ring / 2, -ring / 2),
            size = Size(size.width + ring, size.height + ring),
            cornerRadius = CornerRadius(6.dp.toPx() + ring / 2),
            style = Stroke(ring),
          )
        }
      }
      .background(colors.background, ControlShape)
      .border(0.5.dp, if (focused) colors.accent else colors.border, ControlShape)
      .padding(horizontal = 8.dp, vertical = 4.dp),
    contentAlignment = Alignment.CenterStart,
  ) {
    BasicTextField(
      value = value,
      onValueChange = onValueChange,
      singleLine = true,
      textStyle = TextStyle(color = colors.text, fontSize = 13.sp),
      cursorBrush = SolidColor(colors.text),
      modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
      decorationBox = { innerTextField ->
        Box(contentAlignment = Alignment.CenterStart) {
          if (value.isEmpty() && placeholder != null) {
            BasicText(text = placeholder, maxLines = 1, style = TextStyle(color = colors.mutedText, fontSize = 13.sp))
          }
          innerTextField()
        }
      },
    )
  }
}

@Composable
fun MacTextArea(
  value: String,
  onValueChange: (String) -> Unit,
  modifier: Modifier = Modifier,
  minLines: Int = 3,
) {
  val colors = LocalDiffColors.current
  var focused by remember { mutableStateOf(false) }
  val ringColor = colors.accent.copy(alpha = 0.5f)
  val textStyle = remember(colors) { TextStyle(color = colors.text, fontSize = 13.sp) }
  Box(
    modifier = Modifier
      .drawBehind {
        if (focused) {
          val ring = 3.dp.toPx()
          drawRoundRect(
            color = ringColor,
            topLeft = Offset(-ring / 2, -ring / 2),
            size = Size(size.width + ring, size.height + ring),
            cornerRadius = CornerRadius(6.dp.toPx() + ring / 2),
            style = Stroke(ring),
          )
        }
      }
      .background(colors.background, ControlShape)
      .border(0.5.dp, if (focused) colors.accent else colors.border, ControlShape)
      .padding(horizontal = 8.dp, vertical = 6.dp),
  ) {
    BasicTextField(
      value = value,
      onValueChange = onValueChange,
      minLines = minLines,
      textStyle = textStyle,
      cursorBrush = SolidColor(colors.text),
      modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
    )
  }
}

@Composable
fun MacInlineError(
  text: String,
  modifier: Modifier = Modifier,
) {
  val colors = LocalDiffColors.current
  Row(
    modifier = modifier.semantics(mergeDescendants = true) {},
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Image(
      imageVector = KdvIcons.XCircle,
      contentDescription = null,
      modifier = Modifier.size(12.dp),
      colorFilter = ColorFilter.tint(colors.removedText),
    )
    BasicText(
      text = text,
      style = TextStyle(color = colors.removedText, fontSize = 12.sp),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}
