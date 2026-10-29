package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mataku.kdiffviewer.app.CollapsedFile
import io.github.mataku.kdiffviewer.app.DiffLayout
import io.github.mataku.kdiffviewer.app.DiffMode
import io.github.mataku.kdiffviewer.app.DiffRow
import io.github.mataku.kdiffviewer.app.DisplayRows
import io.github.mataku.kdiffviewer.app.ExpansionError
import io.github.mataku.kdiffviewer.app.GitFileStatus
import io.github.mataku.kdiffviewer.app.LoadStatus
import io.github.mataku.kdiffviewer.app.ScrollRestorer
import io.github.mataku.kdiffviewer.app.ViewerError
import io.github.mataku.kdiffviewer.app.ViewerUiState
import io.github.mataku.kdiffviewer.app.compactTree
import io.github.mataku.kdiffviewer.app.diffLayoutOf
import io.github.mataku.kdiffviewer.app.displayRows
import io.github.mataku.kdiffviewer.app.filterTree
import io.github.mataku.kdiffviewer.app.find.FindCommand
import io.github.mataku.kdiffviewer.app.find.FindKey
import io.github.mataku.kdiffviewer.app.find.FindRequests
import io.github.mataku.kdiffviewer.app.find.FindState
import io.github.mataku.kdiffviewer.app.find.findMatchUpdates
import io.github.mataku.kdiffviewer.app.gitStatus
import io.github.mataku.kdiffviewer.app.isCollapsed
import io.github.mataku.kdiffviewer.app.isLargeDiff
import io.github.mataku.kdiffviewer.app.review.DraftTarget
import io.github.mataku.kdiffviewer.app.review.ReviewSnapshot
import io.github.mataku.kdiffviewer.app.review.isOrphanThread
import io.github.mataku.kdiffviewer.app.review.notesFiles
import io.github.mataku.kdiffviewer.app.review.threadFiles
import io.github.mataku.kdiffviewer.app.review.toSide
import io.github.mataku.kdiffviewer.app.selection.DiffSelectionState
import io.github.mataku.kdiffviewer.app.selection.LocalTextSelectionCoordinator
import io.github.mataku.kdiffviewer.app.ui.mac.MacSegmented
import io.github.mataku.kdiffviewer.app.ui.mac.MacToolbarButton
import io.github.mataku.kdiffviewer.app.ui.mac.TAG_FILL_ALPHA
import io.github.mataku.kdiffviewer.app.ui.mac.TagPadding
import io.github.mataku.kdiffviewer.app.ui.mac.TagShape
import io.github.mataku.kdiffviewer.app.ui.review.LocalDraftFocusRequests
import io.github.mataku.kdiffviewer.app.ui.review.LocalDraftText
import io.github.mataku.kdiffviewer.app.ui.review.LocalReviewActions
import io.github.mataku.kdiffviewer.app.ui.review.LocalReviewInProgress
import io.github.mataku.kdiffviewer.app.ui.review.RevealingReviewActions
import io.github.mataku.kdiffviewer.app.ui.review.ReviewBar
import io.github.mataku.kdiffviewer.app.ui.review.draftAnchor
import io.github.mataku.kdiffviewer.app.ui.review.draftPlacement
import io.github.mataku.kdiffviewer.app.ui.review.fileIndexOrNull
import io.github.mataku.kdiffviewer.app.ui.review.revealIndex
import io.github.mataku.kdiffviewer.app.visibleTree
import io.github.mataku.kdiffviewer.core.model.BaseSpec
import io.github.mataku.kdiffviewer.core.model.BranchRef
import io.github.mataku.kdiffviewer.core.model.DiffStats
import io.github.mataku.kdiffviewer.core.model.ExpandDirection
import io.github.mataku.kdiffviewer.core.model.HeadRef
import io.github.mataku.kdiffviewer.core.model.ResolvedBase
import io.github.mataku.kdiffviewer.core.model.displayPath
import io.github.mataku.kdiffviewer.core.review.Anchor
import io.github.mataku.kdiffviewer.core.review.Placement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.CoroutineContext

private val FindPanelTopInset = 40.dp
private val FindPanelWidth = 340.dp
private val HeaderIconSize = 16.dp
private val HeaderLeadingGap = 40.dp

internal fun sidebarButtonIcon(visible: Boolean): ImageVector = if (visible) KdvIcons.SidebarCollapse else KdvIcons.SidebarExpand

@Composable
fun ViewerScreen(
  state: ViewerUiState,
  onReload: () -> Unit,
  modifier: Modifier = Modifier,
  screenState: ViewerScreenState = rememberViewerScreenState(),
  titleBarLeadingInset: Dp = 0.dp,
  onToggleFile: (Int) -> Unit = {},
  onExpandFile: (Int) -> Unit = {},
  review: ReviewSnapshot = ReviewSnapshot.Empty,
  loadBranches: suspend () -> List<BranchRef> = { emptyList() },
  onSwitchBase: suspend (BaseSpec) -> ViewerError? = { null },
  rangeState: CommentRangeState = remember { CommentRangeState() },
  onExpandContext: (fileIndex: Int, gapIndex: Int, direction: ExpandDirection) -> Unit = { _, _, _ -> },
  findContext: CoroutineContext = Dispatchers.Default,
) {
  val colors = LocalDiffColors.current
  val mode = screenState.mode
  val findState = screenState.findState
  SideEffect { findState.setLive(state.loadId, state.rowsGeneration) }
  val expansionErrors = remember(state.expansionErrors, state.files, state.fingerprints) { state.expansionErrorsByFile() }
  val listState = rememberLazyListState()
  val currentState = rememberUpdatedState(state)
  val shown = review.placements.loadId == state.loadId
  val draftTarget = review.draft?.target
  val draftText = rememberUpdatedState(review.draft?.text.orEmpty())
  val reviewInProgress = rememberUpdatedState(review.pendingCount > 0)
  val notesFiles = remember(shown, review.placements, draftTarget) { if (shown) review.placements.notesFiles(draftTarget) else emptySet() }
  val hasOrphans = shown && (review.placements.orphans.isNotEmpty() || draftTarget.isOrphanThread())
  val hasEnd = state.status == LoadStatus.Done && state.files.isNotEmpty()
  val layout = remember(state.rows, state.fileHeaderRowIndex, state.files, state.fingerprints, state.collapsed, state.expanded, notesFiles, hasOrphans, hasEnd, mode, state.split) {
    diffLayoutOf(state, mode, notesFiles, hasOrphans, hasEnd)
  }
  val currentLayout = rememberUpdatedState(layout)
  val display = remember(state.rows, state.split, mode) { state.displayRows(mode) }
  val currentDisplay = rememberUpdatedState(display)
  ScrollJumpEffect(screenState.scrollJumps, listState, currentLayout)
  val coordinator = LocalTextSelectionCoordinator.current
  val diffSelection = remember { DiffSelectionState() }
  diffSelection.resetFor(listOf(state.loadId, state.contentId, state.collapsed, state.expanded, mode))
  val selectionSource = remember(diffSelection) {
    DiffSelectionSource(
      diffSelection,
      { currentState.value.rows },
      { row -> (currentState.value.rows[row] as? DiffRow.Line)?.let { !currentLayout.value.isCollapsed(it.fileIndex) } ?: false },
    )
  }
  DisposableEffect(coordinator, selectionSource) {
    onDispose { coordinator?.release(selectionSource) }
  }
  val focusRequests = remember { mutableIntStateOf(0) }
  var revealRequests by remember { mutableIntStateOf(0) }
  val baseActions = LocalReviewActions.current
  var reviewSummary by remember(state.repo) { mutableStateOf("") }
  val sendReview: (Boolean) -> Unit = { approve ->
    baseActions?.submitReview(reviewSummary, approve)
    reviewSummary = ""
  }
  val actions = remember(baseActions, draftText) { baseActions?.let { RevealingReviewActions(it, draftText) { revealRequests++ } } }
  val currentReview = rememberUpdatedState(review)
  val currentOnExpandFile = rememberUpdatedState(onExpandFile)
  val threadFiles = remember(shown, review.placements) { if (shown) review.placements.threadFiles() else emptySet() }
  LaunchedEffect(state.loadId, threadFiles, layout.fileCount) {
    for (fileIndex in threadFiles) {
      if (fileIndex < currentLayout.value.fileCount && currentLayout.value.isCollapsed(fileIndex) && currentState.value.isLargeDiff(fileIndex)) currentOnExpandFile.value(fileIndex)
    }
  }
  DraftRevealEffect(revealRequests, listState, currentReview, currentState, currentLayout, currentDisplay, currentOnExpandFile) { focusRequests.intValue++ }
  ScrollRestoreEffect(listState, currentState, currentLayout, currentDisplay)
  val stepFind = rememberFindNavigation(findState, screenState.findRequests, listState, currentState, currentLayout, currentDisplay, findContext)
  val tree = rememberFileTreeState(state.repo)
  val rows = state.rows
  val headerRowIndex = state.fileHeaderRowIndex
  val statsOf: (Int) -> DiffStats? = remember(rows, headerRowIndex) {
    { fileIndex -> (headerRowIndex.getOrNull(fileIndex)?.let(rows::getOrNull) as? DiffRow.FileHeader)?.stats }
  }
  val statusOf: (Int) -> GitFileStatus? = remember(rows, headerRowIndex) {
    { fileIndex -> (headerRowIndex.getOrNull(fileIndex)?.let(rows::getOrNull) as? DiffRow.FileHeader)?.file?.gitStatus() }
  }
  val needle = tree.query.trim()
  val filtering = needle.isNotEmpty()
  val sidebarVisible = screenState.sidebarVisible
  val sidebar = remember { SidebarTransition(sidebarVisible) }
  LaunchedEffect(sidebarVisible) {
    if (sidebar.target != sidebarVisible) sidebar.animateTo(sidebarVisible)
  }
  val treeComposed = sidebar.treeComposed
  val collapsedDirectories = tree.collapsedDirectories
  val compactedTree = remember(state.tree, treeComposed) { if (treeComposed) compactTree(state.tree) else emptyList() }
  val treeEntries = remember(state.tree, compactedTree, state.files, collapsedDirectories, needle, treeComposed) {
    when {
      !treeComposed -> emptyList()
      filtering -> visibleTree(compactTree(filterTree(state.tree, state.files, needle)), emptySet())
      else -> visibleTree(compactedTree, collapsedDirectories)
    }
  }
  val density = LocalDensity.current
  Column(modifier = modifier.fillMaxSize().background(colors.background)) {
    RepoHeader(
      state = state,
      titleBarLeadingInset = titleBarLeadingInset,
      sidebarVisible = sidebarVisible,
      onToggleSidebar = screenState::toggleSidebar,
      loadBranches = loadBranches,
      onSwitchBase = onSwitchBase,
      mode = mode,
      onModeChange = screenState::changeMode,
      onReload = onReload,
    )
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.border))
    SidebarLayout(
      transition = sidebar,
      modifier = Modifier.weight(1f).fillMaxWidth(),
      tree = {
        FileTreePanel(
          entries = treeEntries,
          listState = tree.listState,
          topInset = 12.dp,
          onFileClick = { fileIndex ->
            onExpandFile(fileIndex)
            listState.requestScrollToItem(layout.headerVisibleIndex(fileIndex))
          },
          onDirectoryToggle = tree::toggleDirectory,
          statsOf = statsOf,
          statusOf = statusOf,
          width = { screenState.treeWidth },
          query = tree.query,
          onQueryChange = tree::changeQuery,
          filtering = filtering,
        )
      },
      divider = {
        PaneDivider(onDrag = { delta -> screenState.resizeTree(with(density) { delta.toDp() }) })
      },
      diff = {
        Column(modifier = Modifier.testTag("diff-column").fillMaxSize()) {
          if (state.truncated) {
            TruncatedNotice()
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.border))
          }
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
                LocalReviewInProgress provides reviewInProgress,
                LocalDraftFocusRequests provides focusRequests,
                LocalReviewActions provides actions,
                LocalFindState provides findState,
              ) {
                DiffView(
                  rows = state.rows,
                  layout = layout,
                  display = display,
                  highlights = state.highlights,
                  listState = listState,
                  files = state.files,
                  placements = review.placements,
                  draftTarget = draftTarget,
                  loadId = state.loadId,
                  selection = diffSelection,
                  selectionSource = selectionSource,
                  rangeState = rangeState,
                  onExpand = onExpandContext,
                  expansionErrors = expansionErrors,
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
            if (findState.open) {
              FindPanel(findState, stepFind, Modifier.align(Alignment.TopEnd).padding(top = FindPanelTopInset, end = 12.dp).width(FindPanelWidth))
            }
          }
        }
      },
    )
    if (review.reviewStarted) {
      Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.border))
      ReviewBar(
        pendingCount = review.pendingCount,
        waiting = review.waiting,
        summary = reviewSummary,
        onSummaryChange = { reviewSummary = it },
        onSend = sendReview,
      )
    }
  }
}

@Composable
private fun ScrollJumpEffect(scrollJumps: ScrollJumps, listState: LazyListState, layout: State<DiffLayout>) {
  LaunchedEffect(scrollJumps, listState) {
    scrollJumps.requests.collect { jump ->
      val count = layout.value.visibleCount
      if (count > 0) listState.requestScrollToItem(if (jump == ScrollJump.Top) 0 else count - 1)
    }
  }
}

@Composable
private fun DraftRevealEffect(
  revealRequests: Int,
  listState: LazyListState,
  review: State<ReviewSnapshot>,
  state: State<ViewerUiState>,
  layout: State<DiffLayout>,
  display: State<DisplayRows>,
  onExpandFile: State<(Int) -> Unit>,
  onRevealed: () -> Unit,
) {
  LaunchedEffect(revealRequests) {
    if (revealRequests == 0) return@LaunchedEffect
    val snapshot = review.value
    val target = snapshot.draft?.target ?: return@LaunchedEffect
    val placement = draftPlacement(target, snapshot.threads, snapshot.core.placementById) ?: return@LaunchedEffect
    val fileIndex = placement.fileIndexOrNull()?.takeIf { it < layout.value.fileCount }
    if (fileIndex != null && layout.value.isCollapsed(fileIndex)) {
      onExpandFile.value(fileIndex)
      withTimeoutOrNull(1_000) { snapshotFlow { layout.value }.first { !it.isCollapsed(fileIndex) } }
    }
    val currentLayout = layout.value
    val currentDisplay = display.value
    val index = revealIndex(placement, draftAnchor(target, snapshot.threads), currentLayout, state.value.rows, currentDisplay)
    listState.scrollToItem(index)
    if (fileIndex != null && placement is Placement.OnLine) {
      val headerKey = state.value.rows[currentDisplay.unifiedOf(currentLayout.headerRowIndex(fileIndex))].key
      val pinned = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == headerKey && it.index != index }
      if (pinned != null) listState.scrollBy(-pinned.size.toFloat())
    }
    onRevealed()
  }
}

@Composable
private fun ScrollRestoreEffect(listState: LazyListState, state: State<ViewerUiState>, layout: State<DiffLayout>, display: State<DisplayRows>) {
  val restorer = remember { ScrollRestorer() }
  LaunchedEffect(listState) {
    snapshotFlow { Pair(Triple(state.value, layout.value, display.value), listState.firstVisibleItemIndex) }.collect { (inputs, firstVisible) ->
      val (ui, uiLayout, uiDisplay) = inputs
      restorer.next(ui, uiLayout, uiDisplay, firstVisible)?.let { listState.scrollToItem(it) }
    }
  }
}

@Composable
private fun rememberFindNavigation(
  findState: FindState,
  findRequests: FindRequests,
  listState: LazyListState,
  state: State<ViewerUiState>,
  layout: State<DiffLayout>,
  display: State<DisplayRows>,
  findContext: CoroutineContext,
): (Boolean) -> Unit {
  val findTopInset = with(LocalDensity.current) { FindPanelTopInset.toPx() }
  val revealMatch: suspend (Int) -> Unit = remember(listState, findTopInset) {
    { row ->
      if (findState.isLive && row < state.value.rows.size) {
        val index = layout.value.visibleIndexOf(display.value.displayOf(row))
        val info = listState.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == index }
        val fullyVisible = item != null && item.offset >= info.viewportStartOffset + findTopInset && item.offset + item.size <= info.viewportEndOffset
        if (index >= 0 && !fullyVisible) {
          listState.scrollToItem(index)
          listState.scrollBy(-(info.viewportEndOffset - info.viewportStartOffset) / 3f)
        }
      }
    }
  }
  val currentFindContext by rememberUpdatedState(findContext)
  LaunchedEffect(findState) {
    findMatchUpdates(snapshotFlow { findState.query }, snapshotFlow { findState.open }, snapshotFlow { state.value }) { previous, query, ui ->
      withContext(currentFindContext) {
        previous.updatedFor(ui.rows, FindKey(query, ui.loadId, ui.rowsGeneration, ui.collapsed, ui.expanded), skipFile = ui::isCollapsed)
      }
    }.collect { matches ->
      val count = layout.value.visibleCount
      val anchor = if (count == 0) 0 else display.value.unifiedOf(layout.value.rowIndexOf(listState.firstVisibleItemIndex.coerceIn(0, count - 1)))
      findState.onMatches(matches, anchor)?.let { revealMatch(it) }
    }
  }
  val findScope = rememberCoroutineScope()
  val stepFind: (Boolean) -> Unit = remember(findState) { { forward -> findScope.launch { findState.step(forward)?.let { revealMatch(it) } } } }
  LaunchedEffect(findRequests, findState) {
    findRequests.requests.collect { command ->
      when {
        command == FindCommand.Show || !findState.open -> findState.show()
        else -> stepFind(command == FindCommand.Next)
      }
    }
  }
  return stepFind
}

@Composable
private fun RepoHeader(
  state: ViewerUiState,
  titleBarLeadingInset: Dp,
  sidebarVisible: Boolean,
  onToggleSidebar: () -> Unit,
  loadBranches: suspend () -> List<BranchRef>,
  onSwitchBase: suspend (BaseSpec) -> ViewerError?,
  mode: DiffMode,
  onModeChange: (DiffMode) -> Unit,
  onReload: () -> Unit,
) {
  val colors = LocalDiffColors.current
  val startPadding = if (titleBarLeadingInset > 0.dp) titleBarLeadingInset else 20.dp
  RepoHeaderLayout(
    modifier = Modifier
      .testTag("repo-header")
      .fillMaxWidth()
      .height(ViewerTitleBarHeight)
      .background(colors.fileHeaderBackground)
      .padding(start = startPadding, end = 20.dp),
    leading = {
      MacToolbarButton(
        icon = sidebarButtonIcon(sidebarVisible),
        contentDescription = if (sidebarVisible) "Hide Sidebar" else "Show Sidebar",
        tooltip = if (sidebarVisible) "Hide Sidebar (⌃⌘S)" else "Show Sidebar (⌃⌘S)",
        onClick = onToggleSidebar,
        modifier = Modifier
          .testTag("sidebar-control")
          .titleBarClientRegion("sidebar-control"),
        iconSize = HeaderIconSize,
      )
    },
    center = {
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
          Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
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
    },
    trailing = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        MacSegmented(
          options = DiffMode.entries,
          selected = mode,
          label = { it.name },
          tag = { "diff-mode-${it.name.lowercase()}" },
          onSelect = { if (it != mode) onModeChange(it) },
          modifier = Modifier
            .testTag("diff-mode-control")
            .titleBarClientRegion("diff-mode"),
          segmentWidth = 32.dp,
          icon = { if (it == DiffMode.Unified) KdvIcons.Unified else KdvIcons.Split },
          tooltip = { it.name },
          iconSize = HeaderIconSize,
        )
        Spacer(Modifier.width(8.dp))
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
    },
  )
}

@Composable
private fun RepoHeaderLayout(
  leading: @Composable () -> Unit,
  center: @Composable () -> Unit,
  trailing: @Composable () -> Unit,
  modifier: Modifier,
) {
  Layout(
    contents = listOf(leading, center, trailing),
    modifier = modifier,
  ) { (leadings, centers, trailings), constraints ->
    val maxWidth = constraints.maxWidth
    val maxHeight = constraints.maxHeight
    val leadingGap = HeaderLeadingGap.roundToPx()
    val trailingGap = 12.dp.roundToPx()
    val leadingPlaceable = leadings.first().measure(Constraints(maxWidth = maxWidth, maxHeight = maxHeight))
    val trailingPlaceable = trailings.first().measure(
      Constraints(maxWidth = (maxWidth - leadingPlaceable.width - leadingGap - trailingGap).coerceAtLeast(0), maxHeight = maxHeight),
    )
    val available = (maxWidth - leadingPlaceable.width - trailingPlaceable.width - leadingGap - trailingGap).coerceAtLeast(0)
    val centerPlaceable = centers.first().measure(Constraints(maxWidth = available, maxHeight = maxHeight))
    val centerSlotX = leadingPlaceable.width + leadingGap
    fun centred(height: Int) = Alignment.CenterVertically.align(height, maxHeight)
    layout(maxWidth, maxHeight) {
      leadingPlaceable.placeRelative(0, centred(leadingPlaceable.height))
      centerPlaceable.placeRelative(centerSlotX, centred(centerPlaceable.height))
      trailingPlaceable.placeRelative(centerSlotX + available + trailingGap, centred(trailingPlaceable.height))
    }
  }
}

@Composable
private fun TruncatedNotice() {
  val colors = LocalDiffColors.current
  val style = remember(colors) { TextStyle(color = colors.mutedText, fontSize = 12.sp) }
  Row(
    modifier = Modifier
      .testTag("diff-truncated-notice")
      .fillMaxWidth()
      .background(colors.background)
      .padding(horizontal = 20.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    BasicText(text = "ⓘ", modifier = Modifier.padding(end = 6.dp), style = style)
    BasicText(text = "Some files are not shown because the diff is too large.", style = style)
  }
}

private fun ViewerUiState.expansionErrorsByFile(): Map<Int, String> {
  if (expansionErrors.isEmpty()) return emptyMap()
  val result = HashMap<Int, String>()
  files.forEachIndexed { index, file ->
    val fingerprint = fingerprints.getOrNull(index) ?: return@forEachIndexed
    expansionErrors[CollapsedFile(file.displayPath, fingerprint)]?.let { result[index] = it.message() }
  }
  return result
}

internal fun ExpansionError.message(): String = when (this) {
  ExpansionError.Unreadable -> "Could not read this file"
  ExpansionError.TooLarge -> "Too large to expand"
  ExpansionError.Stale -> "The file no longer matches this diff — reload to expand"
}
