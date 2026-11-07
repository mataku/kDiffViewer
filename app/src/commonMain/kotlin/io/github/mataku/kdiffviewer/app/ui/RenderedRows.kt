package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.mataku.kdiffviewer.app.DiffRow
import io.github.mataku.kdiffviewer.app.ui.markdown.LocalMarkdownStyles
import io.github.mataku.kdiffviewer.app.ui.markdown.MarkdownBlock

enum class FileViewMode { Raw, Rendered }

val FileViewModes = FileViewMode.entries

private val ChangeBarWidth = 3.dp
private val BlockVerticalPadding = 4.dp
private val IndentStep = 16.dp
private val QuoteStep = 12.dp
private val QuoteBarWidth = 3.dp
private val ListMarkerWidth = 20.dp
private val FoldVerticalPadding = 4.dp

private fun Modifier.renderedBars(barX: Dp, barColor: Color, quoteX: Dp, quoteDepth: Int, quoteColor: Color): Modifier = drawBehind {
  drawRect(barColor, Offset(barX.toPx(), 0f), Size(ChangeBarWidth.toPx(), size.height))
  for (depth in 0 until quoteDepth) {
    drawRect(quoteColor, Offset((quoteX + QuoteStep * depth).toPx(), 0f), Size(QuoteBarWidth.toPx(), size.height))
  }
}

@Composable
internal fun RenderedBlockRow(row: DiffRow.RenderedBlock, gutterWidth: Dp, markerColumnWidth: Dp) {
  val colors = LocalDiffColors.current
  val styles = LocalMarkdownStyles.current
  val source = row.source
  val contentStart = gutterWidth + markerColumnWidth
  val textStyle = if (source.quoteDepth > 0) styles.quote else styles.body
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .renderedBars(gutterWidth, colors.addedText, contentStart, source.quoteDepth, styles.quoteBar)
      .padding(start = contentStart + QuoteStep * source.quoteDepth + IndentStep * source.indent, end = 12.dp, top = BlockVerticalPadding, bottom = BlockVerticalPadding),
    horizontalArrangement = Arrangement.Start,
  ) {
    if (source.marker != null) {
      BasicText(source.marker, style = textStyle, modifier = Modifier.width(ListMarkerWidth))
    }
    Box(Modifier.weight(1f)) {
      MarkdownBlock(source.block, textStyle, styles)
    }
  }
}

internal fun removedLabel(count: Int): String = if (count == 1) "─ 1 line removed ─" else "─ $count lines removed ─"

internal fun unchangedLabel(count: Int): String = if (count == 1) "⋯ 1 block unchanged ⋯" else "⋯ $count blocks unchanged ⋯"

@Composable
internal fun RemovedMarkerRow(row: DiffRow.RemovedMarker) {
  CenteredLabelRow(removedLabel(row.count), LocalDiffColors.current.removedBackground)
}

@Composable
internal fun UnchangedFoldRow(row: DiffRow.UnchangedFold) {
  CenteredLabelRow(unchangedLabel(row.count), LocalDiffColors.current.hunkBackground)
}

@Composable
private fun CenteredLabelRow(text: String, background: Color) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .background(background)
      .padding(vertical = FoldVerticalPadding),
    contentAlignment = Alignment.Center,
  ) {
    BasicText(text, style = LocalDiffTypography.current.hunkHeader)
  }
}
