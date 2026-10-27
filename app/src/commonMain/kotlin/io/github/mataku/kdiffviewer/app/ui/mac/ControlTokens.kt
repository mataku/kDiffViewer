package io.github.mataku.kdiffviewer.app.ui.mac

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mataku.kdiffviewer.app.ui.DiffColors
import io.github.mataku.kdiffviewer.app.ui.KdvIcons
import io.github.mataku.kdiffviewer.app.ui.LocalDiffColors
import kotlin.math.roundToInt

internal val ControlShape = RoundedCornerShape(6.dp)
internal val SegmentShape = RoundedCornerShape(5.dp)
internal val LightBorder = Color(0x26000000)
internal val TagShape = RoundedCornerShape(4.dp)
internal const val TAG_FILL_ALPHA = 0.08f
internal val TagPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)

internal val DiffColors.isDark: Boolean get() = background.luminance() < 0.5f

internal fun DiffColors.buttonFill(pressed: Boolean): Color = when {
  isDark -> if (pressed) Color(0x1FFFFFFF) else Color(0x2EFFFFFF)
  else -> if (pressed) Color(0xFFE4E4E4) else Color(0xFFFFFFFF)
}

internal fun DiffColors.trackFill(): Color = if (isDark) Color(0x1FFFFFFF) else Color(0x14000000)

internal fun DiffColors.raisedFill(): Color = if (isDark) Color(0x4DFFFFFF) else Color(0xFFFFFFFF)

internal fun Modifier.buttonSurface(colors: DiffColors, fill: Color): Modifier = if (colors.isDark) {
  background(fill, ControlShape)
} else {
  shadow(1.dp, ControlShape, clip = false).background(fill, ControlShape).border(0.5.dp, LightBorder, ControlShape)
}
