package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.mataku.kdiffviewer.core.model.EXPAND_STEP
import io.github.mataku.kdiffviewer.core.model.ExpandDirection
import io.github.mataku.kdiffviewer.core.model.GapView

private val GlyphWidth = 9.dp
private val GlyphHeight = 5.dp
private val GlyphStroke = 1.2.dp
private const val HOVER_FILL_ALPHA = 0.12f

internal val GapView.showsExpandAll: Boolean get() = hidden.let { it != null && it <= EXPAND_STEP }

internal val GapView.lineCount: Int get() = if (!showsExpandAll && canUp && canDown) 2 else 1

@Composable
internal fun ExpandGutter(gap: GapView, fileIndex: Int, width: Dp, lineHeight: Dp, onExpand: (Int, Int, ExpandDirection) -> Unit) {
  Column(modifier = Modifier.width(width)) {
    if (gap.showsExpandAll) {
      ExpandTarget(ExpandDirection.All, "expand-all-$fileIndex-${gap.gapIndex}", width, lineHeight) { onExpand(fileIndex, gap.gapIndex, ExpandDirection.All) }
    } else {
      if (gap.canDown) ExpandTarget(ExpandDirection.Down, "expand-down-$fileIndex-${gap.gapIndex}", width, lineHeight) { onExpand(fileIndex, gap.gapIndex, ExpandDirection.Down) }
      if (gap.canUp) ExpandTarget(ExpandDirection.Up, "expand-up-$fileIndex-${gap.gapIndex}", width, lineHeight) { onExpand(fileIndex, gap.gapIndex, ExpandDirection.Up) }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpandTarget(direction: ExpandDirection, tag: String, width: Dp, height: Dp, onClick: () -> Unit) {
  val colors = LocalDiffColors.current
  val interactionSource = remember { MutableInteractionSource() }
  val hovered by interactionSource.collectIsHoveredAsState()
  TooltipBox(
    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(if (direction == ExpandDirection.Up) TooltipAnchorPosition.Below else TooltipAnchorPosition.Above),
    tooltip = { PlainTooltip { Text(direction.tooltip) } },
    state = rememberTooltipState(),
  ) {
    Box(
      modifier = Modifier
        .testTag(tag)
        .size(width, height)
        .background(if (hovered) colors.accent.copy(alpha = HOVER_FILL_ALPHA) else Color.Transparent)
        .focusProperties { canFocus = false }
        .clickable(interactionSource = interactionSource, indication = null, role = Role.Button, onClick = onClick),
      contentAlignment = Alignment.Center,
    ) {
      val tint = if (hovered) colors.accent else colors.mutedText
      Canvas(modifier = Modifier.size(GlyphWidth, if (direction == ExpandDirection.All) GlyphHeight * 2 + 2.dp else GlyphHeight)) {
        val glyph = GlyphHeight.toPx()
        when (direction) {
          ExpandDirection.Up -> chevron(tint, 0f, size.height, pointsDown = false)

          ExpandDirection.Down -> chevron(tint, 0f, size.height, pointsDown = true)

          ExpandDirection.All -> {
            chevron(tint, 0f, glyph, pointsDown = false)
            chevron(tint, size.height - glyph, size.height, pointsDown = true)
          }
        }
      }
    }
  }
}

private val ExpandDirection.tooltip: String
  get() = when (this) {
    ExpandDirection.Up -> "Expand Up"
    ExpandDirection.Down -> "Expand Down"
    ExpandDirection.All -> "Expand All"
  }

private fun DrawScope.chevron(color: Color, top: Float, bottom: Float, pointsDown: Boolean) {
  val stroke = GlyphStroke.toPx()
  val inset = 0.5f * stroke
  val edgeY = if (pointsDown) top + inset else bottom - inset
  val tipY = if (pointsDown) bottom - inset else top + inset
  drawLine(color, Offset(inset, edgeY), Offset(size.width / 2, tipY), stroke, StrokeCap.Round)
  drawLine(color, Offset(size.width / 2, tipY), Offset(size.width - inset, edgeY), stroke, StrokeCap.Round)
}
