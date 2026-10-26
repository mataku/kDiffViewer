package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mataku.compose.highlight.core.LocalSyntaxTheme
import io.github.mataku.kdiffviewer.app.DiffLayout
import io.github.mataku.kdiffviewer.app.DiffRow
import io.github.mataku.kdiffviewer.app.highlight.HighlightSlot
import io.github.mataku.kdiffviewer.core.model.ChangeType
import io.github.mataku.kdiffviewer.core.model.FileDiff
import io.github.mataku.kdiffviewer.core.model.LineKind

private val FileHeaderTopMargin = 12.dp
private val FileChevronSize = 12.dp

private const val HEADER_CONTENT_TYPE = 0
private const val HUNK_HEADER_CONTENT_TYPE = 1
private const val LINE_CONTENT_TYPE = 2
private const val PLACEHOLDER_CONTENT_TYPE = 3

private fun DiffRow.contentType(): Int = when (this) {
  is DiffRow.FileHeader -> HEADER_CONTENT_TYPE
  is DiffRow.HunkHeader -> HUNK_HEADER_CONTENT_TYPE
  is DiffRow.Line -> LINE_CONTENT_TYPE
  is DiffRow.Placeholder -> PLACEHOLDER_CONTENT_TYPE
}

@Composable
fun DiffView(
  rows: List<DiffRow>,
  layout: DiffLayout,
  highlights: List<HighlightSlot>,
  listState: LazyListState,
  onHeaderClick: (fileIndex: Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  val currentOnHeaderClick by rememberUpdatedState(onHeaderClick)
  LazyColumn(
    state = listState,
    modifier = modifier.testTag("diff-list"),
    contentPadding = PaddingValues(start = ContentHorizontalMargin, end = ContentHorizontalMargin, bottom = FileHeaderTopMargin),
  ) {
    for (fileIndex in 0 until layout.fileCount) {
      val headerRow = layout.headerRowIndex(fileIndex)
      val collapsed = layout.isCollapsed(fileIndex)
      val header = rows[headerRow] as DiffRow.FileHeader
      stickyHeader(key = header.key, contentType = HEADER_CONTENT_TYPE) {
        FileHeaderRow(header, collapsed) { currentOnHeaderClick(header.fileIndex) }
      }
      if (!collapsed) {
        val bodyStart = headerRow + 1
        items(
          count = layout.bodyRowCount(fileIndex),
          key = { rows[bodyStart + it].key },
          contentType = { rows[bodyStart + it].contentType() },
        ) { offset ->
          when (val row = rows[bodyStart + offset]) {
            is DiffRow.FileHeader -> Unit
            is DiffRow.HunkHeader -> HunkHeaderRow(row)
            is DiffRow.Line -> LineRow(row, highlights.getOrNull(row.fileIndex))
            is DiffRow.Placeholder -> PlaceholderRow(row.message)
          }
        }
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
private fun FileHeaderRow(row: DiffRow.FileHeader, collapsed: Boolean, onClick: () -> Unit) {
  val colors = LocalDiffColors.current
  val typography = LocalDiffTypography.current
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(colors.background)
      .padding(top = FileHeaderTopMargin)
      .background(colors.fileHeaderBackground)
      .border(1.dp, colors.border)
      .clickable(interactionSource = null, indication = null, onClick = onClick)
      .padding(horizontal = 12.dp, vertical = 8.dp)
      .testTag("file-header-${row.fileIndex}"),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Image(
      imageVector = KdvIcons.Chevron,
      contentDescription = null,
      modifier = Modifier
        .testTag("file-chevron-${row.fileIndex}")
        .size(FileChevronSize)
        .rotate(if (collapsed) 0f else 90f),
      colorFilter = ColorFilter.tint(colors.mutedText),
    )
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
