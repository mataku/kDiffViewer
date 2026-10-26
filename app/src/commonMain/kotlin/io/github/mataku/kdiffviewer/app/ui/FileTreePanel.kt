package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mataku.kdiffviewer.app.TreeEntry
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
  width: Dp = DefaultTreeWidth,
) {
  val colors = LocalDiffColors.current
  val showStats = width >= StatsVisibleWidth
  LazyColumn(
    modifier = modifier
      .width(width)
      .fillMaxHeight()
      .background(colors.fileHeaderBackground)
      .testTag("file-tree"),
    contentPadding = PaddingValues(start = ContentHorizontalMargin, top = topInset, end = ContentHorizontalMargin),
  ) {
    itemsIndexed(entries, key = { index, _ -> index }) { index, entry ->
      val row = entry.row
      val fileIndex = row.fileIndex
      val directoryPath = entry.directoryPath
      val rowModifier = when {
        fileIndex != null -> Modifier.testTag("tree-file-$fileIndex").clickable { onFileClick(fileIndex) }
        directoryPath != null -> Modifier.testTag("tree-dir-$index").clickable { onDirectoryToggle(directoryPath) }
        else -> Modifier
      }
      Row(
        rowModifier
          .fillMaxWidth()
          .padding(start = (12 * row.depth).dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        val iconTint = ColorFilter.tint(colors.mutedText)
        if (fileIndex == null) {
          Image(
            imageVector = KdvIcons.Chevron,
            contentDescription = null,
            modifier = Modifier
              .testTag("tree-chevron-$index")
              .padding(end = 2.dp)
              .size(12.dp)
              .rotate(if (entry.collapsed) 0f else 90f),
            colorFilter = iconTint,
          )
        } else {
          Spacer(Modifier.width(14.dp))
        }
        Image(
          imageVector = if (fileIndex != null) KdvIcons.File else KdvIcons.Folder,
          contentDescription = null,
          modifier = Modifier
            .testTag(if (fileIndex != null) "tree-file-icon-$index" else "tree-folder-icon-$index")
            .padding(end = 6.dp)
            .size(14.dp),
          colorFilter = iconTint,
        )
        BasicText(
          text = row.name,
          modifier = Modifier.weight(1f),
          style = TextStyle(
            color = if (fileIndex != null) colors.text else colors.mutedText,
            fontSize = 13.sp,
          ),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        val stats = if (showStats && fileIndex != null) statsOf(fileIndex) else null
        if (stats != null && (stats.additions > 0 || stats.deletions > 0)) {
          BasicText(
            text = "+${stats.additions}",
            modifier = Modifier.testTag("tree-additions-$fileIndex").padding(start = 8.dp),
            style = TextStyle(color = colors.addedText, fontSize = 11.sp),
          )
          BasicText(
            text = "−${stats.deletions}",
            modifier = Modifier.testTag("tree-deletions-$fileIndex").padding(start = 4.dp),
            style = TextStyle(color = colors.removedText, fontSize = 11.sp),
          )
        }
      }
    }
  }
}
