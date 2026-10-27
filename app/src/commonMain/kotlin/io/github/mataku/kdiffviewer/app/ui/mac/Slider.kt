package io.github.mataku.kdiffviewer.app.ui.mac

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import io.github.mataku.kdiffviewer.app.ui.DiffColors
import io.github.mataku.kdiffviewer.app.ui.LocalDiffColors
import kotlin.math.roundToInt

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
