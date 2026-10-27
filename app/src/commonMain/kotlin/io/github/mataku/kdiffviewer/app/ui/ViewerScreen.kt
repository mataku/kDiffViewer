package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import io.github.mataku.kdiffviewer.app.GitFileStatus
import io.github.mataku.kdiffviewer.app.LoadStatus
import io.github.mataku.kdiffviewer.app.ScrollRestorer
import io.github.mataku.kdiffviewer.app.ViewerError
import io.github.mataku.kdiffviewer.app.ViewerUiState
import io.github.mataku.kdiffviewer.app.diffLayoutOf
import io.github.mataku.kdiffviewer.app.filterTree
import io.github.mataku.kdiffviewer.app.gitStatus
import io.github.mataku.kdiffviewer.app.review.ReviewSnapshot
import io.github.mataku.kdiffviewer.app.review.isOrphanThread
import io.github.mataku.kdiffviewer.app.review.notesFiles
import io.github.mataku.kdiffviewer.app.selection.DiffSelectionState
import io.github.mataku.kdiffviewer.app.selection.LocalTextSelectionCoordinator
import io.github.mataku.kdiffviewer.app.ui.mac.TAG_FILL_ALPHA
import io.github.mataku.kdiffviewer.app.ui.mac.TagPadding
import io.github.mataku.kdiffviewer.app.ui.mac.TagShape
import io.github.mataku.kdiffviewer.app.ui.review.LocalDraftFocusRequests
import io.github.mataku.kdiffviewer.app.ui.review.LocalDraftText
import io.github.mataku.kdiffviewer.app.ui.review.LocalReviewActions
import io.github.mataku.kdiffviewer.app.ui.review.RevealingReviewActions
import io.github.mataku.kdiffviewer.app.ui.review.draftPlacement
import io.github.mataku.kdiffviewer.app.ui.review.fileIndexOrNull
import io.github.mataku.kdiffviewer.app.ui.review.revealIndex
import io.github.mataku.kdiffviewer.app.visibleTree
import io.github.mataku.kdiffviewer.core.model.BaseSpec
import io.github.mataku.kdiffviewer.core.model.BranchRef
import io.github.mataku.kdiffviewer.core.model.DiffStats
import io.github.mataku.kdiffviewer.core.model.HeadRef
import io.github.mataku.kdiffviewer.core.model.ResolvedBase
import io.github.mataku.kdiffviewer.core.review.Placement
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun ViewerScreen(
  state: ViewerUiState,
  onReload: () -> Unit,
  modifier: Modifier = Modifier,
  titleBarInset: Dp = 0.dp,
  onToggleFile: (Int) -> Unit = {},
  onExpandFile: (Int) -> Unit = {},
  review: ReviewSnapshot = ReviewSnapshot.Empty,
  loadBranches: suspend () -> List<BranchRef> = { emptyList() },
  onSwitchBase: suspend (BaseSpec) -> ViewerError? = { null },
  scrollJumps: ScrollJumps? = null,
) {
  val colors = LocalDiffColors.current
  val listState = rememberLazyListState()
  val currentState by rememberUpdatedState(state)
  val restorer = remember { ScrollRestorer() }
  val shown = review.placements.loadId == state.loadId
  val draftTarget = review.draft?.target
  val draftText = rememberUpdatedState(review.draft?.text.orEmpty())
  val notesFiles = remember(shown, review.placements, draftTarget) { if (shown) review.placements.notesFiles(draftTarget) else emptySet() }
  val hasOrphans = shown && (review.placements.orphans.isNotEmpty() || draftTarget.isOrphanThread())
  val hasEnd = state.status == LoadStatus.Done && state.files.isNotEmpty()
  val layout = remember(state.rows, state.fileHeaderRowIndex, state.files, state.fingerprints, state.collapsed, notesFiles, hasOrphans, hasEnd) {
    diffLayoutOf(state, notesFiles, hasOrphans, hasEnd)
  }
  val currentLayout by rememberUpdatedState(layout)
  LaunchedEffect(scrollJumps, listState) {
    scrollJumps?.requests?.collect { jump ->
      val count = currentLayout.visibleCount
      if (count > 0) listState.requestScrollToItem(if (jump == ScrollJump.Top) 0 else count - 1)
    }
  }
  val coordinator = LocalTextSelectionCoordinator.current
  val diffSelection = remember { DiffSelectionState() }
  diffSelection.resetFor(state.loadId to state.collapsed)
  val selectionSource = remember(diffSelection) {
    DiffSelectionSource(
      diffSelection,
      { currentState.rows },
      { row -> !currentLayout.isCollapsed(currentLayout.fileIndexOfVisible(currentLayout.visibleIndexOf(row))) },
    )
  }
  DisposableEffect(coordinator, selectionSource) {
    onDispose { coordinator?.release(selectionSource) }
  }
  val focusRequests = remember { mutableIntStateOf(0) }
  var revealRequests by remember { mutableIntStateOf(0) }
  val baseActions = LocalReviewActions.current
  val actions = remember(baseActions, draftText) { baseActions?.let { RevealingReviewActions(it, draftText) { revealRequests++ } } }
  val currentReview by rememberUpdatedState(review)
  val currentOnExpandFile by rememberUpdatedState(onExpandFile)
  LaunchedEffect(revealRequests) {
    if (revealRequests == 0) return@LaunchedEffect
    val snapshot = currentReview
    val target = snapshot.draft?.target ?: return@LaunchedEffect
    val placement = draftPlacement(target, snapshot.threads, snapshot.core.placementById) ?: return@LaunchedEffect
    val fileIndex = placement.fileIndexOrNull()?.takeIf { it < currentLayout.fileCount }
    if (fileIndex != null && currentLayout.isCollapsed(fileIndex)) {
      currentOnExpandFile(fileIndex)
      withTimeoutOrNull(1_000) { snapshotFlow { currentLayout }.first { !it.isCollapsed(fileIndex) } }
    }
    val index = revealIndex(placement, currentLayout, currentState.rows)
    listState.scrollToItem(index)
    if (fileIndex != null && placement is Placement.OnLine) {
      val headerKey = (currentState.rows[currentLayout.headerRowIndex(fileIndex)]).key
      val pinned = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == headerKey && it.index != index }
      if (pinned != null) listState.scrollBy(-pinned.size.toFloat())
    }
    focusRequests.intValue++
  }
  LaunchedEffect(listState) {
    snapshotFlow { Triple(currentState, currentLayout, listState.firstVisibleItemIndex) }.collect { (ui, uiLayout, firstVisible) ->
      restorer.next(ui, uiLayout, firstVisible)?.let { listState.scrollToItem(it) }
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
  val statusOf: (Int) -> GitFileStatus? = remember(rows, headerRowIndex) {
    { fileIndex -> (headerRowIndex.getOrNull(fileIndex)?.let(rows::getOrNull) as? DiffRow.FileHeader)?.file?.gitStatus() }
  }
  var treeQuery by remember(state.repo) { mutableStateOf("") }
  val needle = treeQuery.trim()
  val filtering = needle.isNotEmpty()
  val treeEntries = remember(state.tree, state.files, collapsed, needle) {
    if (filtering) visibleTree(filterTree(state.tree, state.files, needle), emptySet()) else visibleTree(state.tree, collapsed)
  }
  Row(modifier = modifier.fillMaxSize().background(colors.background)) {
    FileTreePanel(
      entries = treeEntries,
      topInset = titleBarInset + 12.dp,
      onFileClick = { fileIndex ->
        onExpandFile(fileIndex)
        listState.requestScrollToItem(layout.headerVisibleIndex(fileIndex))
      },
      onDirectoryToggle = { path -> collapsed = if (path in collapsed) collapsed - path else collapsed + path },
      statsOf = statsOf,
      statusOf = statusOf,
      width = treeWidth,
      query = treeQuery,
      onQueryChange = { treeQuery = it },
      filtering = filtering,
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
          val base = state.base as? ResolvedBase.Commit
          val head = state.head
          if (base != null || head != null) {
            val labelStyle = TextStyle(color = colors.mutedText, fontSize = 12.sp)
            Row(modifier = Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
              if (base != null) {
                val uncommittedOnly = base.label == "HEAD"
                BasePicker(base, state.baseSpec, head, loadBranches, onSwitchBase, labelStyle, Modifier.weight(1f, fill = false).titleBarClientRegion("base-picker"))
                if (head != null) {
                  BasicText(text = if (uncommittedOnly) "·" else "...", modifier = Modifier.padding(horizontal = 6.dp), style = labelStyle)
                }
              }
              if (head != null) {
                val (icon, iconTag, label) = when (head) {
                  is HeadRef.Branch -> Triple(KdvIcons.Branch, "head-branch-icon", head.name)
                  is HeadRef.Detached -> Triple(KdvIcons.Commit, "head-commit-icon", head.shortHash)
                }
                Row(
                  modifier = Modifier.clip(TagShape).background(colors.text.copy(alpha = TAG_FILL_ALPHA)).padding(TagPadding),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Image(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.testTag(iconTag).padding(end = 4.dp).size(12.dp),
                    colorFilter = ColorFilter.tint(colors.mutedText),
                  )
                  BasicText(
                    text = label,
                    modifier = Modifier.testTag("head-label"),
                    style = labelStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
              }
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
        if (state.status == LoadStatus.Done && state.files.isEmpty() && !hasOrphans) {
          BasicText(
            text = "No changes",
            modifier = Modifier.align(Alignment.Center),
            style = TextStyle(color = colors.mutedText, fontSize = 14.sp),
          )
        } else {
          CompositionLocalProvider(
            LocalDraftText provides draftText,
            LocalDraftFocusRequests provides focusRequests,
            LocalReviewActions provides actions,
          ) {
            DiffView(
              rows = state.rows,
              layout = layout,
              highlights = state.highlights,
              listState = listState,
              files = state.files,
              placements = review.placements,
              draftTarget = draftTarget,
              loadId = state.loadId,
              selection = diffSelection,
              selectionSource = selectionSource,
              onHeaderClick = { fileIndex ->
                val header = layout.headerVisibleIndex(fileIndex)
                val pinned = listState.firstVisibleItemIndex >= header
                onToggleFile(fileIndex)
                if (pinned) listState.requestScrollToItem(header)
              },
            )
          }
        }
        if (state.status == LoadStatus.Loading) {
          LinearProgressIndicator(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
      }
    }
  }
}
