package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mataku.kdiffviewer.app.TreeRow

@Composable
fun FileTreePanel(
  tree: List<TreeRow>,
  onFileClick: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = LocalDiffColors.current
  LazyColumn(
    modifier = modifier
      .width(280.dp)
      .fillMaxHeight()
      .background(colors.fileHeaderBackground)
      .testTag("file-tree"),
  ) {
    itemsIndexed(tree, key = { index, _ -> index }) { _, row ->
      val fileIndex = row.fileIndex
      val rowModifier = if (fileIndex != null) {
        Modifier.testTag("tree-file-$fileIndex").clickable { onFileClick(fileIndex) }
      } else {
        Modifier
      }
      Box(
        rowModifier
          .fillMaxWidth()
          .padding(start = (8 + 12 * row.depth).dp, end = 8.dp, top = 3.dp, bottom = 3.dp),
      ) {
        BasicText(
          text = row.name,
          style = TextStyle(
            color = if (fileIndex != null) colors.text else colors.mutedText,
            fontSize = 13.sp,
          ),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}
