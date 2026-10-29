package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextLayoutResult
import io.github.mataku.kdiffviewer.app.find.FindState

val LocalFindState: ProvidableCompositionLocal<FindState?> = staticCompositionLocalOf { null }

internal fun DrawScope.drawFindMatches(
  find: FindState,
  rowIndex: Int,
  text: String,
  layout: TextLayoutResult,
  tabWidth: Int,
  matchColor: Color,
  currentColor: Color,
) {
  if (!find.open || !find.isPaintable) return
  val matches = find.matches
  var index = matches.firstIndexOfRow(rowIndex)
  if (index < 0) return
  val length = matches.queryLength
  val current = find.current
  while (index < matches.count && matches.rowAt(index) == rowIndex) {
    val start = matches.startAt(index)
    val color = if (index == current) currentColor else matchColor
    drawPath(layout.getPathForRange(sourceToDisplay(text, start, tabWidth), sourceToDisplay(text, start + length, tabWidth)), color)
    index++
  }
}
