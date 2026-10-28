package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.mataku.kdiffviewer.app.GitFileStatus
import io.github.mataku.kdiffviewer.app.TreeEntry
import io.github.mataku.kdiffviewer.app.ui.mac.MacSearchField
import io.github.mataku.kdiffviewer.core.model.DiffStats

val DefaultTreeWidth = 280.dp
val MinTreeWidth = 160.dp
val MaxTreeWidth = 600.dp
private val StatsVisibleWidth = 240.dp

@Composable
fun FileTreePanel(
  entries: List<TreeEntry>,
  onFileClick: (Int) -> Unit,
  onDirectoryToggle: (String) -> Unit,
  modifier: Modifier = Modifier,
  topInset: Dp = 0.dp,
  statsOf: (Int) -> DiffStats? = { null },
  statusOf: (Int) -> GitFileStatus? = { null },
  width: Dp = DefaultTreeWidth,
  query: String = "",
  onQueryChange: (String) -> Unit = {},
  filtering: Boolean = false,
) {
  val colors = LocalDiffColors.current
  val typography = LocalTreeTypography.current
  val showStats = width >= StatsVisibleWidth
  val measurer = rememberTextMeasurer()
  val density = LocalDensity.current
  val emptyStyle = remember(typography, colors) { typography.name.copy(color = colors.mutedText) }
  val statusWidth = remember(typography, measurer, density) {
    with(density) { measurer.measure("M", typography.statusModified).size.width.toDp() }
  }
  Column(
    modifier
      .width(width)
      .fillMaxHeight()
      .testTag("tree-panel")
      .background(colors.sidebarBackground)
      .padding(top = topInset),
  ) {
    MacSearchField(
      value = query,
      onValueChange = onQueryChange,
      modifier = Modifier.testTag("tree-filter"),
      containerModifier = Modifier
        .fillMaxWidth()
        .padding(start = ContentHorizontalMargin, end = ContentHorizontalMargin, bottom = 8.dp),
      placeholder = "Filter",
    )
    if (filtering && entries.isEmpty()) {
      BasicText(
        text = "No matching files",
        modifier = Modifier.testTag("tree-empty").padding(horizontal = ContentHorizontalMargin, vertical = 4.dp),
        style = emptyStyle,
      )
    } else {
      LazyColumn(
        modifier = Modifier.fillMaxWidth().testTag("file-tree"),
        contentPadding = PaddingValues(start = ContentHorizontalMargin, end = ContentHorizontalMargin),
      ) {
        itemsIndexed(entries, key = { index, _ -> index }) { index, entry ->
          val row = entry.row
          val fileIndex = row.fileIndex
          val directoryPath = entry.directoryPath
          val rowModifier = when {
            fileIndex != null -> Modifier.testTag("tree-file-$fileIndex").clickable { onFileClick(fileIndex) }
            directoryPath != null && filtering -> Modifier.testTag("tree-dir-$index")
            directoryPath != null -> Modifier.testTag("tree-dir-$index").clickable { onDirectoryToggle(directoryPath) }
            else -> Modifier
          }
          Row(
            rowModifier
              .fillMaxWidth()
              .padding(start = typography.indentPerDepth * row.depth, top = 3.dp, bottom = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            val iconTint = ColorFilter.tint(colors.mutedText)
            if (fileIndex == null && !filtering) {
              Image(
                imageVector = KdvIcons.Chevron,
                contentDescription = null,
                modifier = Modifier
                  .testTag("tree-chevron-$index")
                  .padding(end = 2.dp)
                  .size(typography.chevronSize)
                  .rotate(if (entry.collapsed) 0f else 90f),
                colorFilter = iconTint,
              )
            } else {
              Spacer(Modifier.width(typography.chevronSize + 2.dp))
            }
            Image(
              imageVector = if (fileIndex != null) KdvIcons.File else KdvIcons.Folder,
              contentDescription = null,
              modifier = Modifier
                .testTag(if (fileIndex != null) "tree-file-icon-$index" else "tree-folder-icon-$index")
                .padding(end = 6.dp)
                .size(typography.iconSize),
              colorFilter = iconTint,
            )
            BasicText(
              text = row.name,
              modifier = Modifier.weight(1f),
              style = if (fileIndex != null) typography.name else typography.directoryName,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            if (showStats && fileIndex != null) {
              val stats = statsOf(fileIndex)
              val statsText = remember(stats, typography) { stats?.let { statsText(it, typography.additions, typography.deletions) } }
              if (statsText != null) {
                BasicText(
                  text = statsText,
                  modifier = Modifier.padding(start = 8.dp).testTag("tree-stats-$fileIndex"),
                )
              }
              Box(Modifier.padding(start = 6.dp).widthIn(min = statusWidth), contentAlignment = Alignment.Center) {
                val status = statusOf(fileIndex)
                if (status != null) {
                  BasicText(
                    text = status.letter(),
                    modifier = Modifier.testTag("tree-status-$fileIndex"),
                    style = typography.statusStyle(status),
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}

private fun GitFileStatus.letter(): String = when (this) {
  GitFileStatus.Untracked -> "U"
  GitFileStatus.Added -> "A"
  GitFileStatus.Modified -> "M"
  GitFileStatus.Deleted -> "D"
  GitFileStatus.Renamed -> "R"
}
