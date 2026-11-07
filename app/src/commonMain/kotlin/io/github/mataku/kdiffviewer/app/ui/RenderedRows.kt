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
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.mataku.kdiffviewer.app.DiffRow
import io.github.mataku.kdiffviewer.app.HunkLineRange
import io.github.mataku.kdiffviewer.app.ui.markdown.LocalMarkdownStyles
import io.github.mataku.kdiffviewer.app.ui.markdown.MarkdownBlock
import io.github.mataku.kdiffviewer.core.model.Side

enum class FileViewMode { Raw, Rendered }

val FileViewModes = FileViewMode.entries

internal val RenderedRowTopPadding = 4.dp
private val ChangeBarWidth = 3.dp
private val IndentStep = 16.dp
private val QuoteStep = 12.dp
private val QuoteBarWidth = 3.dp
private val ListMarkerWidth = 20.dp

private fun Modifier.renderedBars(barX: Dp, barColor: Color, quoteX: Dp, quoteDepth: Int, quoteColor: Color): Modifier = drawBehind {
  drawRect(barColor, Offset(barX.toPx(), 0f), Size(ChangeBarWidth.toPx(), size.height))
  for (depth in 0 until quoteDepth) {
    drawRect(quoteColor, Offset((quoteX + QuoteStep * depth).toPx(), 0f), Size(QuoteBarWidth.toPx(), size.height))
  }
}

private fun Modifier.rangeHighlight(rangeState: CommentRangeState, fileIndex: Int, range: HunkLineRange?, side: Side, color: Color): Modifier {
  if (range == null) return this
  return drawBehind {
    if (rangeState.highlightsRange(fileIndex, range, side)) drawRect(color)
  }
}

@Composable
private fun rememberRowHover(hoveredKey: State<String?>, key: String): State<Boolean> = remember(hoveredKey, key) {
  val left = "$key:L"
  val right = "$key:R"
  derivedStateOf {
    val hovered = hoveredKey.value
    hovered == key || hovered == left || hovered == right
  }
}

@Composable
private fun RowAddComment(gutterWidth: Dp, show: Boolean) {
  if (show) {
    Box(Modifier.padding(start = gutterWidth, top = RenderedRowTopPadding)) {
      AddCommentButton(LocalDiffTypography.current)
    }
  }
}

@Composable
internal fun RenderedBlockRow(
  row: DiffRow.RenderedBlock,
  gutterWidth: Dp,
  markerColumnWidth: Dp,
  hoveredKey: State<String?>,
  commentable: Boolean,
  rangeState: CommentRangeState,
) {
  val colors = LocalDiffColors.current
  val styles = LocalMarkdownStyles.current
  val source = row.source
  val contentStart = gutterWidth + markerColumnWidth
  val textStyle = if (source.quoteDepth > 0) styles.quote else styles.body
  val hovered by rememberRowHover(hoveredKey, row.key)
  Box(Modifier.fillMaxWidth().rangeHighlight(rangeState, row.fileIndex, row.commentRange, Side.New, colors.rangeBackground)) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .renderedBars(gutterWidth, colors.addedText, contentStart, source.quoteDepth, styles.quoteBar)
        .padding(start = contentStart + QuoteStep * source.quoteDepth + IndentStep * source.indent, end = 12.dp, top = RenderedRowTopPadding, bottom = RenderedRowTopPadding),
      horizontalArrangement = Arrangement.Start,
    ) {
      if (source.marker != null) {
        BasicText(source.marker, style = textStyle, modifier = Modifier.width(ListMarkerWidth))
      }
      Box(Modifier.weight(1f)) {
        MarkdownBlock(source.block, textStyle, styles)
      }
    }
    RowAddComment(gutterWidth, hovered && commentable && row.commentRange != null)
  }
}

internal fun removedLabel(count: Int): String = if (count == 1) "─ 1 line removed ─" else "─ $count lines removed ─"

internal fun unchangedLabel(count: Int): String = if (count == 1) "⋯ 1 block unchanged ⋯" else "⋯ $count blocks unchanged ⋯"

@Composable
internal fun RemovedMarkerRow(
  row: DiffRow.RemovedMarker,
  gutterWidth: Dp,
  hoveredKey: State<String?>,
  commentable: Boolean,
  rangeState: CommentRangeState,
) {
  val colors = LocalDiffColors.current
  val hovered by rememberRowHover(hoveredKey, row.key)
  Box(Modifier.fillMaxWidth().background(colors.removedBackground).rangeHighlight(rangeState, row.fileIndex, row.range, Side.Old, colors.rangeBackground)) {
    CenteredLabel(removedLabel(row.count))
    RowAddComment(gutterWidth, hovered && commentable)
  }
}

@Composable
internal fun UnchangedFoldRow(row: DiffRow.UnchangedFold) {
  Box(Modifier.fillMaxWidth().background(LocalDiffColors.current.hunkBackground)) {
    CenteredLabel(unchangedLabel(row.count))
  }
}

@Composable
private fun CenteredLabel(text: String) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = RenderedRowTopPadding),
    contentAlignment = Alignment.Center,
  ) {
    BasicText(text, style = LocalDiffTypography.current.hunkHeader)
  }
}
