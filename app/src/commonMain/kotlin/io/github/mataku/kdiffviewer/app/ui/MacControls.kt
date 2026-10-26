package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private val ControlShape = RoundedCornerShape(6.dp)
private val SegmentShape = RoundedCornerShape(5.dp)
private val LightBorder = Color(0x26000000)

internal val DiffColors.isDark: Boolean get() = background.luminance() < 0.5f

private fun DiffColors.buttonFill(pressed: Boolean): Color = when {
  isDark -> if (pressed) Color(0x1FFFFFFF) else Color(0x2EFFFFFF)
  else -> if (pressed) Color(0xFFE4E4E4) else Color(0xFFFFFFFF)
}

private fun DiffColors.trackFill(): Color = if (isDark) Color(0x1FFFFFFF) else Color(0x14000000)

private fun DiffColors.raisedFill(): Color = if (isDark) Color(0x4DFFFFFF) else Color(0xFFFFFFFF)

private fun Modifier.buttonSurface(colors: DiffColors, fill: Color): Modifier = if (colors.isDark) {
  background(fill, ControlShape)
} else {
  shadow(1.dp, ControlShape, clip = false).background(fill, ControlShape).border(0.5.dp, LightBorder, ControlShape)
}

@Composable
fun MacPushButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
) {
  val colors = LocalDiffColors.current
  val interactionSource = remember { MutableInteractionSource() }
  val pressed by interactionSource.collectIsPressedAsState()
  Box(
    modifier = modifier
      .alpha(if (enabled) 1f else 0.4f)
      .height(24.dp)
      .buttonSurface(colors, colors.buttonFill(pressed))
      .clickable(interactionSource = interactionSource, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
      .padding(horizontal = 10.dp),
    contentAlignment = Alignment.Center,
  ) {
    BasicText(text = text, maxLines = 1, style = TextStyle(color = colors.text, fontSize = 13.sp))
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
private fun Chevron(color: Color) {
  Canvas(modifier = Modifier.padding(start = 6.dp).size(width = 9.dp, height = 6.dp)) {
    val stroke = 1.3.dp.toPx()
    drawLine(color, Offset(0.5f * stroke, 0.5f * stroke), Offset(size.width / 2, size.height - 0.5f * stroke), stroke, StrokeCap.Round)
    drawLine(color, Offset(size.width / 2, size.height - 0.5f * stroke), Offset(size.width - 0.5f * stroke, 0.5f * stroke), stroke, StrokeCap.Round)
  }
}

@Composable
fun <T> MacSegmented(
  options: List<T>,
  selected: T,
  label: (T) -> String,
  tag: (T) -> String,
  onSelect: (T) -> Unit,
  modifier: Modifier = Modifier,
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
        Modifier.shadow(if (colors.isDark) 0.dp else 1.dp, SegmentShape, clip = false).background(colors.raisedFill(), SegmentShape)
      } else {
        Modifier
      }
      Box(
        modifier = Modifier
          .testTag(tag(option))
          .height(22.dp)
          .width(44.dp)
          .then(segmentModifier)
          .semantics { this.selected = isSelected }
          .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, role = Role.RadioButton) { onSelect(option) },
        contentAlignment = Alignment.Center,
      ) {
        BasicText(
          text = label(option),
          style = TextStyle(color = colors.text, fontSize = 13.sp, textAlign = TextAlign.Center),
        )
      }
    }
  }
}

@Composable
fun MacTextField(
  value: String,
  onValueChange: (String) -> Unit,
  modifier: Modifier = Modifier,
  containerModifier: Modifier = Modifier,
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
      textStyle = TextStyle(color = colors.text, fontSize = 13.sp),
      cursorBrush = SolidColor(colors.text),
      modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
    )
  }
}

private val SliderTrackHeight = 4.dp
private val SliderKnobSize = 20.dp
private val SliderWidth = 200.dp

private fun DiffColors.knobFill(pressed: Boolean): Color = when {
  isDark -> if (pressed) Color(0xFFC8C8C8) else Color(0xFFE6E6E6)
  else -> if (pressed) Color(0xFFEBEBEB) else Color(0xFFFFFFFF)
}

@Composable
fun MacSlider(
  value: Int,
  range: IntRange,
  onValueChange: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = LocalDiffColors.current
  val latestValue by rememberUpdatedState(value)
  val latestOnValueChange by rememberUpdatedState(onValueChange)
  val focusRequester = remember { FocusRequester() }
  var pressed by remember { mutableStateOf(false) }
  var focused by remember { mutableStateOf(false) }
  val current = value.coerceIn(range)
  val knobFill = colors.knobFill(pressed)
  val trackColor = colors.trackFill()
  val ringColor = colors.accent.copy(alpha = 0.5f)
  val dark = colors.isDark
  Canvas(
    modifier = modifier
      .width(SliderWidth)
      .height(SliderKnobSize)
      .semantics {
        progressBarRangeInfo = ProgressBarRangeInfo(current.toFloat(), range.first.toFloat()..range.last.toFloat(), (range.last - range.first - 1).coerceAtLeast(0))
        setProgress { target ->
          val next = target.roundToInt().coerceIn(range)
          if (next != latestValue) latestOnValueChange(next)
          true
        }
      }
      .onFocusChanged { focused = it.isFocused }
      .onKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
        val delta = when (event.key) {
          Key.DirectionLeft, Key.DirectionDown -> -1
          Key.DirectionRight, Key.DirectionUp -> 1
          else -> return@onKeyEvent false
        }
        val next = (latestValue + delta).coerceIn(range)
        if (next != latestValue) latestOnValueChange(next)
        true
      }
      .focusRequester(focusRequester)
      .focusable()
      .pointerInput(range) {
        val inset = SliderKnobSize.toPx() / 2
        val span = range.last - range.first
        fun valueAt(x: Float): Int {
          val travel = (size.width - 2 * inset).coerceAtLeast(1f)
          return range.first + (((x - inset) / travel).coerceIn(0f, 1f) * span).roundToInt()
        }
        awaitEachGesture {
          val down = awaitFirstDown()
          focusRequester.requestFocus()
          pressed = true
          var emitted = latestValue
          fun emit(x: Float) {
            val next = valueAt(x)
            if (next != emitted) {
              emitted = next
              latestOnValueChange(next)
            }
          }
          emit(down.position.x)
          down.consume()
          while (true) {
            val change = awaitPointerEvent().changes.first()
            if (!change.pressed) break
            emit(change.position.x)
            change.consume()
          }
          pressed = false
        }
      },
  ) {
    val radius = SliderKnobSize.toPx() / 2
    val travel = size.width - 2 * radius
    val fraction = if (range.last > range.first) (current - range.first).toFloat() / (range.last - range.first) else 0f
    val knobX = radius + travel * fraction
    val centerY = size.height / 2
    val trackThickness = SliderTrackHeight.toPx()
    val trackTop = centerY - trackThickness / 2
    val corner = CornerRadius(trackThickness / 2)
    drawRoundRect(trackColor, Offset(0f, trackTop), Size(size.width, trackThickness), corner)
    drawRoundRect(colors.accent, Offset(0f, trackTop), Size(knobX, trackThickness), corner)
    if (focused) {
      drawCircle(ringColor, radius + 1.5.dp.toPx(), Offset(knobX, centerY), style = Stroke(3.dp.toPx()))
    }
    if (!dark) {
      drawCircle(Color(0x1F000000), radius + 0.5.dp.toPx(), Offset(knobX, centerY + 0.5.dp.toPx()))
      drawCircle(Color(0x26000000), radius, Offset(knobX, centerY))
    }
    drawCircle(knobFill, radius - if (dark) 0f else 0.5.dp.toPx(), Offset(knobX, centerY))
  }
}
