package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import io.github.mataku.kdiffviewer.app.DiffLayout

data class FileFrame(val top: Float, val bottom: Float, val roundTop: Boolean, val roundBottom: Boolean)

fun fileFrames(layout: DiffLayout, items: List<LazyListItemInfo>, headerInset: Float, viewportHeight: Float, overshoot: Float): List<FileFrame> {
  val frames = ArrayList<FileFrame>()
  var file = -1
  var firstIndex = 0
  var firstOffset = 0
  var lastIndex = 0
  var lastEnd = 0
  fun emit() {
    if (file < 0) return
    val roundTop = firstIndex == layout.headerVisibleIndex(file)
    val roundBottom = lastIndex == layout.lastVisibleIndex(file)
    frames.add(
      FileFrame(
        top = if (roundTop) firstOffset + headerInset else -overshoot,
        bottom = if (roundBottom) lastEnd.toFloat() else viewportHeight + overshoot,
        roundTop = roundTop,
        roundBottom = roundBottom,
      ),
    )
  }
  for (item in items) {
    val index = item.index
    if (index < 0 || index >= layout.visibleCount) continue
    if (layout.hasOrphanItem && index == 0) continue
    if (layout.hasEndItem && index == layout.visibleCount - 1) continue
    val itemFile = layout.fileIndexOfVisible(index)
    if (itemFile != file) {
      emit()
      file = itemFile
      firstIndex = index
      firstOffset = item.offset
    }
    lastIndex = index
    lastEnd = item.offset + item.size
  }
  emit()
  return frames
}

private val FrameRadius = 6.dp
private val FrameStroke = 1.dp

class FileFramePainter(val mask: Path, val stroke: Stroke, val radius: Float, val strokeWidth: Float)

@Composable
fun rememberFileFramePainter(): FileFramePainter {
  val density = LocalDensity.current
  return remember(density) {
    val radius = with(density) { FrameRadius.toPx() }
    val strokeWidth = with(density) { FrameStroke.toPx() }
    FileFramePainter(cornerMask(radius + strokeWidth / 2), Stroke(strokeWidth), radius, strokeWidth)
  }
}

private fun cornerMask(side: Float): Path = Path().apply {
  moveTo(0f, 0f)
  lineTo(side, 0f)
  arcTo(Rect(0f, 0f, side * 2, side * 2), 270f, -90f, forceMoveTo = false)
  close()
}

fun Modifier.fileFrames(
  listState: LazyListState,
  layout: DiffLayout,
  painter: FileFramePainter,
  border: Color,
  background: Color,
  headerInset: Float,
  margin: Float,
): Modifier = drawWithContent {
  drawContent()
  val strokeWidth = painter.strokeWidth
  val half = strokeWidth / 2
  val left = margin
  val right = size.width - margin
  val frames = fileFrames(layout, listState.layoutInfo.visibleItemsInfo, headerInset, size.height, painter.radius + strokeWidth)
  for (frame in frames) {
    if (frame.roundTop) {
      drawCornerMask(painter.mask, background, left, frame.top, 0f)
      drawCornerMask(painter.mask, background, right, frame.top, 90f)
    }
    if (frame.roundBottom) {
      drawCornerMask(painter.mask, background, right, frame.bottom, 180f)
      drawCornerMask(painter.mask, background, left, frame.bottom, 270f)
    }
    drawRoundRect(
      color = border,
      topLeft = Offset(left + half, frame.top + half),
      size = Size(right - left - strokeWidth, frame.bottom - frame.top - strokeWidth),
      cornerRadius = CornerRadius(painter.radius),
      style = painter.stroke,
    )
  }
}

private fun DrawScope.drawCornerMask(mask: Path, color: Color, x: Float, y: Float, degrees: Float) {
  withTransform({
    translate(x, y)
    rotate(degrees, Offset.Zero)
  }) {
    drawPath(mask, color)
  }
}
