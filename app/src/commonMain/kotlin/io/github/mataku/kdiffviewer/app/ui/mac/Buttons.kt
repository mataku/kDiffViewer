package io.github.mataku.kdiffviewer.app.ui.mac

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mataku.kdiffviewer.app.ui.LocalDiffColors

@Composable
fun MacPushButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  default: Boolean = false,
) {
  val colors = LocalDiffColors.current
  val interactionSource = remember { MutableInteractionSource() }
  val pressed by interactionSource.collectIsPressedAsState()
  val fill = if (default) colors.accent.copy(alpha = if (pressed) 0.8f else 1f) else colors.buttonFill(pressed)
  Box(
    modifier = modifier
      .alpha(if (enabled) 1f else 0.4f)
      .height(24.dp)
      .buttonSurface(colors, fill)
      .clickable(interactionSource = interactionSource, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
      .padding(horizontal = 10.dp),
    contentAlignment = Alignment.Center,
  ) {
    BasicText(text = text, maxLines = 1, style = TextStyle(color = if (default) Color.White else colors.text, fontSize = 13.sp))
  }
}

@Composable
fun MacPopupButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = LocalDiffColors.current
  val interactionSource = remember { MutableInteractionSource() }
  val pressed by interactionSource.collectIsPressedAsState()
  Row(
    modifier = modifier
      .width(220.dp)
      .height(24.dp)
      .buttonSurface(colors, colors.buttonFill(pressed))
      .clickable(interactionSource = interactionSource, indication = null, role = Role.Button, onClick = onClick)
      .padding(start = 10.dp, end = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    BasicText(
      text = text,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      style = TextStyle(color = colors.text, fontSize = 13.sp),
      modifier = Modifier.weight(1f),
    )
    Chevron(colors.mutedText)
  }
}

@Composable
fun MacPullDownTag(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  icon: ImageVector? = null,
  textStyle: TextStyle,
  textModifier: Modifier = Modifier,
) {
  val colors = LocalDiffColors.current
  Row(
    modifier = modifier
      .clip(TagShape)
      .background(colors.text.copy(alpha = TAG_FILL_ALPHA))
      .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick)
      .padding(TagPadding),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (icon != null) {
      Image(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.padding(end = 4.dp).size(12.dp),
        colorFilter = ColorFilter.tint(colors.mutedText),
      )
    }
    BasicText(
      text = text,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      style = textStyle,
      modifier = Modifier.weight(1f, fill = false).then(textModifier),
    )
    Chevron(colors.mutedText)
  }
}

@Composable
private fun Chevron(color: Color) {
  Canvas(modifier = Modifier.padding(start = 6.dp).size(width = 9.dp, height = 6.dp)) {
    val stroke = 1.3.dp.toPx()
    drawLine(color, Offset(0.5f * stroke, 0.5f * stroke), Offset(size.width / 2, size.height - 0.5f * stroke), stroke, StrokeCap.Round)
    drawLine(color, Offset(size.width / 2, size.height - 0.5f * stroke), Offset(size.width - 0.5f * stroke, 0.5f * stroke), stroke, StrokeCap.Round)
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MacToolbarButton(
  icon: ImageVector,
  contentDescription: String,
  tooltip: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = LocalDiffColors.current
  val interactionSource = remember { MutableInteractionSource() }
  val pressed by interactionSource.collectIsPressedAsState()
  TooltipBox(
    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
    tooltip = { PlainTooltip { Text(tooltip) } },
    state = rememberTooltipState(),
  ) {
    Box(
      modifier = modifier
        .height(24.dp)
        .background(colors.trackFill(pressed), ControlShape)
        .clickable(interactionSource = interactionSource, indication = null, role = Role.Button, onClick = onClick)
        .padding(1.dp),
    ) {
      Box(
        modifier = Modifier
          .height(22.dp)
          .width(32.dp),
        contentAlignment = Alignment.Center,
      ) {
        Image(
          imageVector = icon,
          contentDescription = contentDescription,
          modifier = Modifier.size(14.dp),
          colorFilter = ColorFilter.tint(colors.text),
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> MacSegmented(
  options: List<T>,
  selected: T,
  label: (T) -> String,
  tag: (T) -> String,
  onSelect: (T) -> Unit,
  modifier: Modifier = Modifier,
  segmentWidth: Dp = 44.dp,
  icon: ((T) -> ImageVector)? = null,
  tooltip: ((T) -> String)? = null,
) {
  val colors = LocalDiffColors.current
  Row(
    modifier = modifier
      .height(24.dp)
      .background(colors.trackFill(), ControlShape)
      .padding(1.dp),
  ) {
    options.forEach { option ->
      val isSelected = option == selected
      val segmentModifier = if (isSelected) {
        Modifier.raisedSegment(colors)
      } else {
        Modifier
      }
      val segment: @Composable () -> Unit = {
        Box(
          modifier = Modifier
            .testTag(tag(option))
            .height(22.dp)
            .width(segmentWidth)
            .then(segmentModifier)
            .semantics { this.selected = isSelected }
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, role = Role.RadioButton) { onSelect(option) },
          contentAlignment = Alignment.Center,
        ) {
          if (icon != null) {
            Image(
              imageVector = icon(option),
              contentDescription = label(option),
              modifier = Modifier.size(14.dp),
              colorFilter = ColorFilter.tint(colors.text),
            )
          } else {
            BasicText(
              text = label(option),
              style = TextStyle(color = colors.text, fontSize = 13.sp, textAlign = TextAlign.Center),
            )
          }
        }
      }
      if (tooltip != null) {
        TooltipBox(
          positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
          tooltip = { PlainTooltip { Text(tooltip(option)) } },
          state = rememberTooltipState(),
          content = segment,
        )
      } else {
        segment()
      }
    }
  }
}
