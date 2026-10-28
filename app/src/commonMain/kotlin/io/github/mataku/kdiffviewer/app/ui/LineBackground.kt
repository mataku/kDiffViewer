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
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.unit.Dp

fun Modifier.lineBackground(
  gutterWidth: Dp,
  leftGutter: Color,
  leftContent: Color,
  rightGutter: Color = Color.Unspecified,
  rightContent: Color = Color.Unspecified,
  split: Boolean = false,
): Modifier = this then LineBackgroundElement(gutterWidth, leftGutter, leftContent, rightGutter, rightContent, split)

private data class LineBackgroundElement(
  val gutterWidth: Dp,
  val leftGutter: Color,
  val leftContent: Color,
  val rightGutter: Color,
  val rightContent: Color,
  val split: Boolean,
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

  fun apply(element: LineBackgroundElement) {
    gutterWidth = element.gutterWidth
    leftGutter = element.leftGutter
    leftContent = element.leftContent
    rightGutter = element.rightGutter
    rightContent = element.rightContent
    split = element.split
  }

  override fun ContentDrawScope.draw() {
    val width = size.width.toInt()
    val gutter = with(requireDensity()) { gutterWidth.toPx() }
    if (split) {
      val leftWidth = width / 2
      paintHalf(0, leftWidth, gutter, leftGutter, leftContent)
      paintHalf(leftWidth, width - leftWidth, gutter, rightGutter, rightContent)
    } else {
      paintHalf(0, width, gutter, leftGutter, leftContent)
    }
    drawContent()
  }

  private fun DrawScope.paintHalf(start: Int, width: Int, gutter: Float, gutterColor: Color, contentColor: Color) {
    val gutterEnd = gutter.coerceAtMost(width.toFloat())
    if (gutterColor.isSpecified) {
      drawRect(gutterColor, Offset(start.toFloat(), 0f), Size(gutterEnd, size.height))
    }
    if (contentColor.isSpecified) {
      drawRect(contentColor, Offset(start + gutterEnd, 0f), Size(width - gutterEnd, size.height))
    }
  }
}
