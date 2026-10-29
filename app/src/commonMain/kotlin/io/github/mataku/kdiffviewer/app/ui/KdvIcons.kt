package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object KdvIcons {
  val Chevron: ImageVector = outlineIcon("Chevron") {
    moveTo(6f, 3.5f)
    lineTo(10.5f, 8f)
    lineTo(6f, 12.5f)
  }

  val Folder: ImageVector = outlineIcon("Folder") {
    moveTo(1.5f, 3.5f)
    lineTo(6f, 3.5f)
    lineTo(7.5f, 5f)
    lineTo(14.5f, 5f)
    lineTo(14.5f, 13f)
    lineTo(1.5f, 13f)
    close()
  }

  val Branch: ImageVector = outlineIcon("Branch") {
    circle(4.5f, 3f, 1.5f)
    circle(4.5f, 13f, 1.5f)
    circle(11.5f, 3f, 1.5f)
    moveTo(4.5f, 4.5f)
    lineTo(4.5f, 11.5f)
    moveTo(11.5f, 4.5f)
    curveTo(11.5f, 8f, 4.5f, 7.5f, 4.5f, 11.5f)
  }

  val Commit: ImageVector = outlineIcon("Commit") {
    circle(8f, 8f, 2.5f)
    moveTo(1f, 8f)
    lineTo(5.5f, 8f)
    moveTo(10.5f, 8f)
    lineTo(15f, 8f)
  }

  val Reload: ImageVector = outlineIcon("Reload") {
    moveTo(12.76f, 5.25f)
    arcTo(5.5f, 5.5f, 0f, true, true, 8f, 2.5f)
    moveTo(6.2f, 0.6f)
    lineTo(8.6f, 2.5f)
    lineTo(6.2f, 4.4f)
  }

  val CheckCircle: ImageVector = outlineIcon("CheckCircle") {
    circle(8f, 8f, 6.5f)
    moveTo(5f, 8.2f)
    lineTo(7.1f, 10.3f)
    lineTo(11f, 5.8f)
  }

  val XCircle: ImageVector = outlineIcon("XCircle") {
    circle(8f, 8f, 6.5f)
    moveTo(5.5f, 5.5f)
    lineTo(10.5f, 10.5f)
    moveTo(10.5f, 5.5f)
    lineTo(5.5f, 10.5f)
  }

  val Comment: ImageVector = outlineIcon("Comment") {
    moveTo(2.5f, 2.5f)
    lineTo(13.5f, 2.5f)
    lineTo(13.5f, 11f)
    lineTo(7f, 11f)
    lineTo(4f, 13.5f)
    lineTo(4f, 11f)
    lineTo(2.5f, 11f)
    close()
  }

  val Plus: ImageVector = outlineIcon("Plus") {
    moveTo(8f, 3f)
    lineTo(8f, 13f)
    moveTo(3f, 8f)
    lineTo(13f, 8f)
  }

  val File: ImageVector = outlineIcon("File") {
    moveTo(3.5f, 1.5f)
    lineTo(9.5f, 1.5f)
    lineTo(12.5f, 4.5f)
    lineTo(12.5f, 14.5f)
    lineTo(3.5f, 14.5f)
    close()
    moveTo(9.5f, 1.5f)
    lineTo(9.5f, 4.5f)
    lineTo(12.5f, 4.5f)
  }

  val Unified: ImageVector = outlineIcon("Unified") {
    roundedRect(1.5f, 2.5f, 14.5f, 13.5f, 1.5f)
    moveTo(4.5f, 6f)
    lineTo(11.5f, 6f)
    moveTo(4.5f, 8f)
    lineTo(11.5f, 8f)
    moveTo(4.5f, 10f)
    lineTo(11.5f, 10f)
  }

  val Split: ImageVector = outlineIcon("Split") {
    roundedRect(1.5f, 2.5f, 14.5f, 13.5f, 1.5f)
    moveTo(8f, 2.5f)
    lineTo(8f, 13.5f)
    moveTo(3.5f, 6f)
    lineTo(6f, 6f)
    moveTo(3.5f, 8f)
    lineTo(6f, 8f)
    moveTo(10f, 6f)
    lineTo(12.5f, 6f)
    moveTo(10f, 8f)
    lineTo(12.5f, 8f)
  }

  val SidebarCollapse: ImageVector = outlineIcon("SidebarCollapse") {
    roundedRect(1.5f, 2.5f, 14.5f, 13.5f, 1.5f)
    moveTo(6f, 2.5f)
    lineTo(6f, 13.5f)
    moveTo(11.25f, 5.5f)
    lineTo(9f, 8f)
    lineTo(11.25f, 10.5f)
  }

  val SidebarExpand: ImageVector = outlineIcon("SidebarExpand") {
    roundedRect(1.5f, 2.5f, 14.5f, 13.5f, 1.5f)
    moveTo(6f, 2.5f)
    lineTo(6f, 13.5f)
    moveTo(9.25f, 5.5f)
    lineTo(11.5f, 8f)
    lineTo(9.25f, 10.5f)
  }

  val Search: ImageVector = outlineIcon("Search") {
    circle(7f, 7f, 4f)
    moveTo(10f, 10f)
    lineTo(13.5f, 13.5f)
  }

  val Close: ImageVector = outlineIcon("Close") {
    moveTo(4f, 4f)
    lineTo(12f, 12f)
    moveTo(12f, 4f)
    lineTo(4f, 12f)
  }
}

private fun PathBuilder.roundedRect(left: Float, top: Float, right: Float, bottom: Float, radius: Float) {
  moveTo(left + radius, top)
  lineTo(right - radius, top)
  arcTo(radius, radius, 0f, false, true, right, top + radius)
  lineTo(right, bottom - radius)
  arcTo(radius, radius, 0f, false, true, right - radius, bottom)
  lineTo(left + radius, bottom)
  arcTo(radius, radius, 0f, false, true, left, bottom - radius)
  lineTo(left, top + radius)
  arcTo(radius, radius, 0f, false, true, left + radius, top)
  close()
}

private fun PathBuilder.circle(centerX: Float, centerY: Float, radius: Float) {
  moveTo(centerX - radius, centerY)
  arcToRelative(radius, radius, 0f, true, true, radius * 2, 0f)
  arcToRelative(radius, radius, 0f, true, true, -radius * 2, 0f)
  close()
}

private fun outlineIcon(name: String, pathBuilder: PathBuilder.() -> Unit): ImageVector = ImageVector.Builder(
  name = name,
  defaultWidth = 16.dp,
  defaultHeight = 16.dp,
  viewportWidth = 16f,
  viewportHeight = 16f,
).path(
  stroke = SolidColor(Color.Black),
  strokeLineWidth = 1.2f,
  strokeLineJoin = StrokeJoin.Round,
  pathBuilder = pathBuilder,
).build()
