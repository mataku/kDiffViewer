package io.github.mataku.kdiffviewer.app.ui.mac

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.mataku.kdiffviewer.app.ui.LocalDiffColors

private val SwitchWidth = 26.dp
private val SwitchHeight = 15.dp

@Composable
fun MacSwitch(
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = LocalDiffColors.current
  var focused by remember { mutableStateOf(false) }
  val fill = if (checked) colors.accent else colors.border
  val ringColor = colors.accent.copy(alpha = 0.5f)
  Canvas(
    modifier = modifier
      .size(width = SwitchWidth, height = SwitchHeight)
      .onFocusChanged { focused = it.isFocused }
      .toggleable(
        value = checked,
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        role = Role.Switch,
        onValueChange = onCheckedChange,
      ),
  ) {
    val corner = CornerRadius(size.height / 2)
    if (focused) {
      val ring = 3.dp.toPx()
      drawRoundRect(
        color = ringColor,
        topLeft = Offset(-ring / 2, -ring / 2),
        size = Size(size.width + ring, size.height + ring),
        cornerRadius = CornerRadius(size.height / 2 + ring / 2),
        style = Stroke(ring),
      )
    }
    drawRoundRect(fill, Offset.Zero, size, corner)
    val inset = 1.dp.toPx()
    val knobRadius = size.height / 2 - inset
    val knobX = if (checked) size.width - inset - knobRadius else inset + knobRadius
    drawCircle(Color(0x33000000), knobRadius + 0.5.dp.toPx(), Offset(knobX, size.height / 2 + 0.5.dp.toPx()))
    drawCircle(Color.White, knobRadius, Offset(knobX, size.height / 2))
  }
}
