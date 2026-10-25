package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mataku.kdiffviewer.app.DiffRow
import io.github.mataku.kdiffviewer.core.model.ChangeType
import io.github.mataku.kdiffviewer.core.model.DiffLine
import io.github.mataku.kdiffviewer.core.model.FileDiff
import io.github.mataku.kdiffviewer.core.model.LineKind

private const val MINUS = "−"

@Composable
fun DiffView(
    rows: List<DiffRow>,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    LazyColumn(state = listState, modifier = modifier.testTag("diff-list")) {
        items(rows, key = { it.key }) { row ->
            when (row) {
                is DiffRow.FileHeader -> FileHeaderRow(row)
                is DiffRow.HunkHeader -> HunkHeaderRow(row)
                is DiffRow.Line -> LineRow(row.line)
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .background(colors.fileHeaderBackground)
            .border(1.dp, colors.border)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("file-header-${row.fileIndex}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicText(
            text = row.file.headerTitle(),
            style = TextStyle(color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
        )
        BasicText(
            text = row.file.badge(),
            modifier = Modifier
                .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                .padding(horizontal = 6.dp, vertical = 1.dp),
            style = TextStyle(color = colors.mutedText, fontSize = 11.sp),
        )
        BasicText(
            text = "+${row.stats.additions}",
            style = TextStyle(color = colors.addedText, fontSize = 12.sp),
        )
        BasicText(
            text = "$MINUS${row.stats.deletions}",
            style = TextStyle(color = colors.removedText, fontSize = 12.sp),
        )
    }
}

@Composable
private fun HunkHeaderRow(row: DiffRow.HunkHeader) {
    val colors = LocalDiffColors.current
    BasicText(
        text = row.hunk.header,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.hunkBackground)
            .padding(horizontal = 12.dp, vertical = 2.dp),
        style = TextStyle(color = colors.mutedText, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
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
private fun LineRow(line: DiffLine) {
    val colors = LocalDiffColors.current
    val mono = TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    if (line.kind == LineKind.NoNewlineAtEof) {
        BasicText(
            text = "No newline at end of file",
            modifier = Modifier.fillMaxWidth().padding(start = 112.dp),
            style = mono.copy(color = colors.mutedText),
        )
        return
    }
    val (contentBackground, numberBackground, marker) = when (line.kind) {
        LineKind.Added -> Triple(colors.addedBackground, colors.addedLineNumberBackground, "+")
        LineKind.Removed -> Triple(colors.removedBackground, colors.removedLineNumberBackground, "-")
        else -> Triple(Color.Transparent, Color.Transparent, " ")
    }
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        LineNumberCell(line.oldLineNo, numberBackground, mono.copy(color = colors.mutedText))
        LineNumberCell(line.newLineNo, numberBackground, mono.copy(color = colors.mutedText))
        Box(modifier = Modifier.width(16.dp).fillMaxHeight().background(contentBackground)) {
            BasicText(
                text = marker,
                modifier = Modifier.align(Alignment.TopCenter),
                style = mono.copy(color = colors.text),
            )
        }
        BasicText(
            text = line.text,
            modifier = Modifier.weight(1f).fillMaxHeight().background(contentBackground),
            style = mono.copy(color = colors.text),
        )
    }
}

@Composable
private fun LineNumberCell(number: Int?, background: Color, style: TextStyle) {
    Box(modifier = Modifier.width(48.dp).fillMaxHeight().background(background).padding(end = 6.dp)) {
        BasicText(
            text = number?.toString().orEmpty(),
            modifier = Modifier.fillMaxWidth(),
            style = style.copy(textAlign = TextAlign.End),
        )
    }
}
