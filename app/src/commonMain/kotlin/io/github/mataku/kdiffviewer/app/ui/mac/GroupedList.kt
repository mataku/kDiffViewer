package io.github.mataku.kdiffviewer.app.ui.mac

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.mataku.kdiffviewer.app.ui.DiffColors
import io.github.mataku.kdiffviewer.app.ui.LocalDiffColors

private val GroupShape = RoundedCornerShape(8.dp)
private val RowHorizontalPadding = 12.dp

/** The rounded, divided group of rows System Settings uses for its forms. */
@Composable
fun <T> MacGroupedList(items: List<T>, modifier: Modifier = Modifier, row: @Composable (T) -> Unit) {
  val colors = LocalDiffColors.current
  Column(
    modifier = modifier
      .fillMaxWidth()
      .clip(GroupShape)
      .background(colors.groupFill())
      .border(0.5.dp, colors.groupEdge(), GroupShape),
  ) {
    items.forEachIndexed { index, item ->
      if (index > 0) {
        Box(Modifier.padding(horizontal = RowHorizontalPadding).fillMaxWidth().height(0.5.dp).background(colors.groupEdge()))
      }
      Box(Modifier.fillMaxWidth().padding(horizontal = RowHorizontalPadding, vertical = 8.dp)) {
        row(item)
      }
    }
  }
}

private fun DiffColors.groupFill(): Color = if (isDark) Color(0x0DFFFFFF) else Color(0x08000000)

private fun DiffColors.groupEdge(): Color = if (isDark) Color(0x1AFFFFFF) else Color(0x14000000)
