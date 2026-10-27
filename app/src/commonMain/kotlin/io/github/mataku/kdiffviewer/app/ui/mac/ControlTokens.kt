package io.github.mataku.kdiffviewer.app.ui.mac

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import io.github.mataku.kdiffviewer.app.ui.DiffColors

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
