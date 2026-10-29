package io.github.mataku.kdiffviewer.app.ui.mac

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mataku.kdiffviewer.app.ui.LocalDiffColors

private val TabHeight = 28.dp
private val TabEdge = 0.5.dp

@Composable
fun <T> MacTabbedPanel(
  options: List<T>,
  selected: T,
  label: (T) -> String,
  tag: (T) -> String,
  onSelect: (T) -> Unit,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  val colors = LocalDiffColors.current
  val selectedStyle = remember(colors) { TextStyle(color = colors.text, fontSize = 13.sp) }
  val unselectedStyle = remember(colors) { TextStyle(color = colors.mutedText, fontSize = 13.sp) }
  Column(
    modifier = modifier
      .fillMaxWidth()
      .clip(ControlShape)
      .border(TabEdge, colors.border, ControlShape),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(TabHeight)
        .background(colors.fileHeaderBackground),
    ) {
      options.forEachIndexed { index, option ->
        val isSelected = option == selected
        val edges = if (isSelected) {
          Modifier.background(colors.background).sideEdges(colors.border, left = index > 0)
        } else {
          Modifier.bottomEdge(colors.border)
        }
        Box(
          modifier = Modifier
            .testTag(tag(option))
            .fillMaxHeight()
            .then(edges)
            .semantics { this.selected = isSelected }
            .focusProperties { canFocus = false }
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, role = Role.Tab) { onSelect(option) }
            .padding(horizontal = 12.dp),
          contentAlignment = Alignment.Center,
        ) {
          BasicText(label(option), style = if (isSelected) selectedStyle else unselectedStyle)
        }
      }
      Spacer(Modifier.weight(1f).fillMaxHeight().bottomEdge(colors.border))
    }
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .background(colors.background)
        .padding(8.dp),
    ) {
      content()
    }
  }
}

private fun Modifier.bottomEdge(color: Color): Modifier = drawBehind {
  val stroke = TabEdge.toPx()
  val y = size.height - stroke / 2
  drawLine(color, Offset(0f, y), Offset(size.width, y), stroke)
}

private fun Modifier.sideEdges(color: Color, left: Boolean): Modifier = drawBehind {
  val stroke = TabEdge.toPx()
  val right = size.width - stroke / 2
  drawLine(color, Offset(right, 0f), Offset(right, size.height), stroke)
  if (left) drawLine(color, Offset(stroke / 2, 0f), Offset(stroke / 2, size.height), stroke)
}
