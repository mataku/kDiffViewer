package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mataku.compose.highlight.core.LocalSyntaxTheme
import io.github.mataku.kdiffviewer.app.DiffRow
import io.github.mataku.kdiffviewer.app.highlight.HighlightSlot
import io.github.mataku.kdiffviewer.core.model.ChangeType
import io.github.mataku.kdiffviewer.core.model.FileDiff
import io.github.mataku.kdiffviewer.core.model.LineKind

private val FileHeaderTopMargin = 12.dp

@Composable
fun DiffView(
  rows: List<DiffRow>,
  highlights: List<HighlightSlot>,
  listState: LazyListState,
  modifier: Modifier = Modifier,
) {
  LazyColumn(
    state = listState,
    modifier = modifier.testTag("diff-list"),
    contentPadding = PaddingValues(start = ContentHorizontalMargin, end = ContentHorizontalMargin, bottom = FileHeaderTopMargin),
  ) {
    items(rows, key = { it.key }) { row ->
      when (row) {
        is DiffRow.FileHeader -> FileHeaderRow(row)
        is DiffRow.HunkHeader -> HunkHeaderRow(row)
        is DiffRow.Line -> LineRow(row, highlights.getOrNull(row.fileIndex))
        is DiffRow.Placeholder -> PlaceholderRow(row.message)
      }
    }
  }
}

private fun FileDiff.headerTitle(): String = when {
  change == ChangeType.Renamed && oldPath != null && newPath != null -> "$oldPath → $newPath"
  else -> newPath ?: oldPath.orEmpty()
}

private fun FileDiff.badge(): String = if (isUntracked) "Untracked" else change.name

@Composable
private fun FileHeaderRow(row: DiffRow.FileHeader) {
  val colors = LocalDiffColors.current
  val typography = LocalDiffTypography.current
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(top = FileHeaderTopMargin)
      .background(colors.fileHeaderBackground)
      .border(1.dp, colors.border)
      .padding(horizontal = 12.dp, vertical = 8.dp)
      .testTag("file-header-${row.fileIndex}"),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Row(
      modifier = Modifier.weight(1f),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      BasicText(
        text = row.file.headerTitle(),
        modifier = Modifier.weight(1f, fill = false),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = typography.headerPath,
      )
      BasicText(
        text = row.file.badge(),
        modifier = Modifier
          .testTag("file-badge-${row.fileIndex}")
          .border(1.dp, colors.border, RoundedCornerShape(10.dp))
          .padding(horizontal = 6.dp, vertical = 1.dp),
        style = typography.headerBadge,
      )
    }
    val statsText = remember(row.stats, typography) { statsText(row.stats, typography.headerAdditions, typography.headerDeletions) }
    if (statsText != null) {
      BasicText(text = statsText, modifier = Modifier.testTag("file-stats-${row.fileIndex}"))
    }
  }
}

@Composable
private fun HunkHeaderRow(row: DiffRow.HunkHeader) {
  val colors = LocalDiffColors.current
  val typography = LocalDiffTypography.current
  BasicText(
    text = remember(row.hunk, typography.tabWidth) { expandTabs(hunkHeaderLabel(row.hunk), typography.tabWidth) },
    modifier = Modifier
      .fillMaxWidth()
      .background(colors.hunkBackground)
      .padding(horizontal = 12.dp, vertical = 2.dp),
    style = typography.hunkHeader,
  )
}

@Composable
private fun PlaceholderRow(message: String) {
  val colors = LocalDiffColors.current
  BasicText(
    text = message,
    modifier = Modifier.fillMaxWidth().padding(12.dp),
    style = TextStyle(color = colors.mutedText, fontSize = 13.sp),
  )
}

@Composable
private fun LineRow(row: DiffRow.Line, slot: HighlightSlot?) {
  val line = row.line
  val colors = LocalDiffColors.current
  val typography = LocalDiffTypography.current
  if (line.kind == LineKind.NoNewlineAtEof) {
    BasicText(
      text = "No newline at end of file",
      modifier = Modifier.fillMaxWidth().padding(start = typography.lineNumberWidth * 2 + 16.dp),
      style = typography.noNewline,
    )
    return
  }
  val (contentBackground, numberBackground, marker) = when (line.kind) {
    LineKind.Added -> Triple(colors.addedBackground, colors.addedLineNumberBackground, "+")
    LineKind.Removed -> Triple(colors.removedBackground, colors.removedLineNumberBackground, "-")
    else -> Triple(colors.background, colors.background, " ")
  }
  Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
    LineNumberCell(line.oldLineNo, numberBackground, typography)
    LineNumberCell(line.newLineNo, numberBackground, typography)
    Box(modifier = Modifier.width(16.dp).fillMaxHeight().background(contentBackground)) {
      BasicText(
        text = marker,
        modifier = Modifier.align(Alignment.TopCenter),
        style = typography.marker,
      )
    }
    val contentModifier = Modifier.weight(1f).fillMaxHeight().background(contentBackground)
    val spans = slot?.value?.spans?.getOrNull(row.hunkIndex)?.getOrNull(row.lineIndex)
    if (spans != null) {
      val theme = LocalSyntaxTheme.current
      val text = remember(line, spans, theme, typography.tabWidth) { displayText(line.text, spans, theme, typography.tabWidth) }
      BasicText(text = text, modifier = contentModifier, style = typography.code)
    } else {
      val text = remember(line.text, typography.tabWidth) { expandTabs(line.text, typography.tabWidth) }
      BasicText(text = text, modifier = contentModifier, style = typography.code)
    }
  }
}

@Composable
private fun LineNumberCell(number: Int?, background: Color, typography: DiffTypography) {
  Box(modifier = Modifier.width(typography.lineNumberWidth).fillMaxHeight().background(background).padding(end = 6.dp)) {
    BasicText(
      text = number?.toString().orEmpty(),
      modifier = Modifier.fillMaxWidth(),
      style = typography.lineNumber,
    )
  }
}
