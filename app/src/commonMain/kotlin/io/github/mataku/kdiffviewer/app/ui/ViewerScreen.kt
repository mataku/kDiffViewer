package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mataku.kdiffviewer.app.DiffRow
import io.github.mataku.kdiffviewer.app.LoadStatus
import io.github.mataku.kdiffviewer.app.ScrollRestorer
import io.github.mataku.kdiffviewer.app.ViewerUiState
import io.github.mataku.kdiffviewer.app.visibleTree
import io.github.mataku.kdiffviewer.core.model.DiffStats
import io.github.mataku.kdiffviewer.core.model.HeadRef
import kotlinx.coroutines.launch

@Composable
fun ViewerScreen(
  state: ViewerUiState,
  onReload: () -> Unit,
  modifier: Modifier = Modifier,
  titleBarInset: Dp = 0.dp,
) {
  val colors = LocalDiffColors.current
  val listState = rememberLazyListState()
  val scope = rememberCoroutineScope()
  val currentState by rememberUpdatedState(state)
  val restorer = remember { ScrollRestorer() }
  LaunchedEffect(listState) {
    snapshotFlow { currentState to listState.firstVisibleItemIndex }.collect { (ui, firstVisible) ->
      restorer.next(ui, firstVisible)?.let { listState.scrollToItem(it) }
    }
  }
  var collapsed by remember(state.repo) { mutableStateOf(emptySet<String>()) }
  var treeWidth by remember { mutableStateOf(DefaultTreeWidth) }
  val density = LocalDensity.current
  val rows = state.rows
  val headerRowIndex = state.fileHeaderRowIndex
  val statsOf: (Int) -> DiffStats? = remember(rows, headerRowIndex) {
    { fileIndex -> (headerRowIndex.getOrNull(fileIndex)?.let(rows::getOrNull) as? DiffRow.FileHeader)?.stats }
  }
  val treeEntries = remember(state.tree, collapsed) { visibleTree(state.tree, collapsed) }
  Row(modifier = modifier.fillMaxSize().background(colors.background)) {
    FileTreePanel(
      entries = treeEntries,
      topInset = titleBarInset + 12.dp,
      onFileClick = { fileIndex ->
        scope.launch { listState.scrollToItem(state.fileHeaderRowIndex[fileIndex]) }
      },
      onDirectoryToggle = { path -> collapsed = if (path in collapsed) collapsed - path else collapsed + path },
      statsOf = statsOf,
      width = treeWidth,
    )
    PaneDivider(
      onDrag = { delta ->
        treeWidth = (treeWidth + with(density) { delta.toDp() }).coerceIn(MinTreeWidth, MaxTreeWidth)
      },
    )
    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
      Row(
        modifier = Modifier
          .testTag("repo-header")
          .fillMaxWidth()
          .heightIn(min = TitleBarHeight)
          .background(colors.fileHeaderBackground)
          .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column {
          BasicText(
            text = state.repo?.let(::repoName).orEmpty(),
            modifier = Modifier.testTag("repo-name"),
            style = TextStyle(color = colors.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
          )
          state.head?.let { head ->
            val (icon, iconTag, label) = when (head) {
              is HeadRef.Branch -> Triple(KdvIcons.Branch, "head-branch-icon", head.name)
              is HeadRef.Detached -> Triple(KdvIcons.Commit, "head-commit-icon", head.shortHash)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
              Image(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.testTag(iconTag).padding(end = 4.dp).size(12.dp),
                colorFilter = ColorFilter.tint(colors.mutedText),
              )
              BasicText(
                text = label,
                modifier = Modifier.testTag("head-label"),
                style = TextStyle(color = colors.mutedText, fontSize = 12.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
        GlassIconButton(
          icon = KdvIcons.Reload,
          contentDescription = "Reload",
          tooltip = "Reload (⌘R)",
          onClick = onReload,
          modifier = Modifier
            .testTag("reload-button")
            .titleBarClientRegion("reload-button"),
        )
      }
      Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.border))
      Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
        if (state.status == LoadStatus.Done && state.files.isEmpty()) {
          BasicText(
            text = "No changes",
            modifier = Modifier.align(Alignment.Center),
            style = TextStyle(color = colors.mutedText, fontSize = 14.sp),
          )
        } else {
          DiffView(rows = state.rows, highlights = state.highlights, listState = listState)
        }
        if (state.status == LoadStatus.Loading) {
          LinearProgressIndicator(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
      }
    }
  }
}
