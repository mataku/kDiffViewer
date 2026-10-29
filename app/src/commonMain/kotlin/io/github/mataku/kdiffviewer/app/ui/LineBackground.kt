package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.unit.Dp
import io.github.mataku.kdiffviewer.core.model.Side
import kotlin.jvm.JvmInline

@JvmInline
value class LineId(val packed: Long) {
  companion object {
    val None = LineId(-1L)
  }
}

fun Modifier.lineBackground(
  gutterWidth: Dp,
  leftGutter: Color,
  leftContent: Color,
  rightGutter: Color = Color.Unspecified,
  rightContent: Color = Color.Unspecified,
  split: Boolean = false,
  range: CommentRangeState? = null,
  rangeColor: Color = Color.Unspecified,
  leftLine: LineId = LineId.None,
  rightLine: LineId = LineId.None,
): Modifier = this then LineBackgroundElement(gutterWidth, leftGutter, leftContent, rightGutter, rightContent, split, range, rangeColor, leftLine, rightLine)

private data class LineBackgroundElement(
  val gutterWidth: Dp,
  val leftGutter: Color,
  val leftContent: Color,
  val rightGutter: Color,
  val rightContent: Color,
  val split: Boolean,
  val range: CommentRangeState?,
  val rangeColor: Color,
  val leftLine: LineId,
  val rightLine: LineId,
) : ModifierNodeElement<LineBackgroundNode>() {
  override fun create() = LineBackgroundNode().also { it.apply(this) }

  override fun update(node: LineBackgroundNode) {
    node.apply(this)
    node.invalidateDraw()
  }
}

private class LineBackgroundNode :
  Modifier.Node(),
  DrawModifierNode {
  private var gutterWidth = Dp.Unspecified
  private var leftGutter = Color.Unspecified
  private var leftContent = Color.Unspecified
  private var rightGutter = Color.Unspecified
  private var rightContent = Color.Unspecified
  private var split = false
  private var range: CommentRangeState? = null
  private var rangeColor = Color.Unspecified
  private var leftLine = LineId.None
  private var rightLine = LineId.None

  fun apply(element: LineBackgroundElement) {
    gutterWidth = element.gutterWidth
    leftGutter = element.leftGutter
    leftContent = element.leftContent
    rightGutter = element.rightGutter
    rightContent = element.rightContent
    split = element.split
    range = element.range
    rangeColor = element.rangeColor
    leftLine = element.leftLine
    rightLine = element.rightLine
  }

  override fun ContentDrawScope.draw() {
    val width = size.width.toInt()
    val gutter = gutterWidth.toPx()
    if (split) {
      val leftWidth = width / 2
      paintHalf(0, leftWidth, gutter, leftGutter, leftContent, leftLine, Side.Old)
      paintHalf(leftWidth, width - leftWidth, gutter, rightGutter, rightContent, rightLine, Side.New)
    } else {
      paintHalf(0, width, gutter, leftGutter, leftContent, leftLine, null)
    }
    drawContent()
  }

  private fun DrawScope.paintHalf(start: Int, width: Int, gutter: Float, kindGutterColor: Color, kindContentColor: Color, line: LineId, side: Side?) {
    val highlighted = rangeColor.isSpecified && range?.highlights(line.packed, side) == true
    val gutterColor = if (highlighted) rangeColor else kindGutterColor
    val contentColor = if (highlighted) rangeColor else kindContentColor
    val gutterEnd = gutter.coerceAtMost(width.toFloat())
    if (gutterColor.isSpecified) {
      drawRect(gutterColor, Offset(start.toFloat(), 0f), Size(gutterEnd, size.height))
    }
    if (contentColor.isSpecified) {
      drawRect(contentColor, Offset(start + gutterEnd, 0f), Size(width - gutterEnd, size.height))
    }
  }
}
