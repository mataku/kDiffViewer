package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import io.github.mataku.compose.highlight.core.LocalSyntaxTheme
import io.github.mataku.kdiffviewer.app.DiffLayout
import io.github.mataku.kdiffviewer.app.DiffMode
import io.github.mataku.kdiffviewer.app.DiffRow
import io.github.mataku.kdiffviewer.app.DisplayRows
import io.github.mataku.kdiffviewer.app.highlight.HighlightSlot
import io.github.mataku.kdiffviewer.app.review.DraftTarget
import io.github.mataku.kdiffviewer.app.review.ReviewPlacements
import io.github.mataku.kdiffviewer.app.review.isOrphanThread
import io.github.mataku.kdiffviewer.app.review.linePosition
import io.github.mataku.kdiffviewer.app.review.sidePosition
import io.github.mataku.kdiffviewer.app.selection.DiffSelectionState
import io.github.mataku.kdiffviewer.app.selection.SelectionSource
import io.github.mataku.kdiffviewer.app.ui.mac.MacInlineError
import io.github.mataku.kdiffviewer.app.ui.mac.MacOverlayScrollbar
import io.github.mataku.kdiffviewer.app.ui.review.LocalReviewActions
import io.github.mataku.kdiffviewer.app.ui.review.NoteStack
import io.github.mataku.kdiffviewer.app.ui.review.OrphanSection
import io.github.mataku.kdiffviewer.core.model.ChangeType
import io.github.mataku.kdiffviewer.core.model.ExpandDirection
import io.github.mataku.kdiffviewer.core.model.FileDiff
import io.github.mataku.kdiffviewer.core.model.LineKind
import io.github.mataku.kdiffviewer.core.model.Side
import io.github.mataku.kdiffviewer.core.review.Anchor
import io.github.mataku.kdiffviewer.core.review.Placement
import io.github.mataku.kdiffviewer.core.review.RangeAnchor
import io.github.mataku.kdiffviewer.core.review.ReviewThread
import io.github.mataku.kdiffviewer.core.review.anchorPath
import io.github.mataku.kdiffviewer.protocol.markControls
import io.github.mataku.kdiffviewer.core.review.Side as ReviewSide

private val FileHeaderTopMargin = 12.dp
private val FileChevronSize = 12.dp
private val FileCommentIconSize = 14.dp
private val MarkerColumnWidth = 16.dp
private val AddCommentSize = 16.dp
private val AddCommentTint = ColorFilter.tint(Color.White)
private val TextCursor = Modifier.pointerHoverIcon(PointerIcon.Text)

private const val HEADER_CONTENT_TYPE = 0
private const val HUNK_HEADER_CONTENT_TYPE = 1
private const val LINE_CONTENT_TYPE = 2
private const val PLACEHOLDER_CONTENT_TYPE = 3
private const val NOTES_CONTENT_TYPE = 4
private const val ORPHANS_CONTENT_TYPE = 5
private const val ORPHANS_KEY = "orphans"
private const val END_CONTENT_TYPE = 6
private const val END_KEY = "end"
private const val SPLIT_LINE_CONTENT_TYPE = 7
private const val TRAILING_GAP_CONTENT_TYPE = 8

private fun DiffRow.contentType(): Int = when (this) {
  is DiffRow.FileHeader -> HEADER_CONTENT_TYPE
  is DiffRow.HunkHeader -> HUNK_HEADER_CONTENT_TYPE
  is DiffRow.Line -> LINE_CONTENT_TYPE
  is DiffRow.Placeholder -> PLACEHOLDER_CONTENT_TYPE
  is DiffRow.TrailingGap -> TRAILING_GAP_CONTENT_TYPE
}

private fun splitItemKey(rows: List<DiffRow>, display: DisplayRows, displayRow: Int): String {
  val left = display.unifiedOf(displayRow, Side.Old)
  val right = display.unifiedOf(displayRow, Side.New)
  val leftRow = rows.getOrNull(left)
  val rightRow = rows.getOrNull(right)
  if (leftRow !is DiffRow.Line && rightRow !is DiffRow.Line) return (leftRow ?: rightRow)!!.key
  return "s" + (leftRow?.key ?: "-") + "|" + (rightRow?.key ?: "-")
}

private fun splitContentType(rows: List<DiffRow>, display: DisplayRows, displayRow: Int): Int {
  val row = rows[display.unifiedOf(displayRow, null)]
  return if (row is DiffRow.Line) SPLIT_LINE_CONTENT_TYPE else row.contentType()
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DiffView(
  rows: List<DiffRow>,
  layout: DiffLayout,
  display: DisplayRows,
  highlights: List<HighlightSlot>,
  listState: LazyListState,
  onHeaderClick: (fileIndex: Int) -> Unit,
  files: List<FileDiff>,
  placements: ReviewPlacements,
  draftTarget: DraftTarget?,
  loadId: Long,
  selection: DiffSelectionState,
  selectionSource: SelectionSource,
  modifier: Modifier = Modifier,
  rangeState: CommentRangeState = remember { CommentRangeState() },
  onExpand: (fileIndex: Int, gapIndex: Int, direction: ExpandDirection) -> Unit = { _, _, _ -> },
  expansionErrors: Map<Int, String> = emptyMap(),
) {
  val currentOnHeaderClick by rememberUpdatedState(onHeaderClick)
  val currentOnExpand by rememberUpdatedState(onExpand)
  val expand = remember { { fileIndex: Int, gapIndex: Int, direction: ExpandDirection -> currentOnExpand(fileIndex, gapIndex, direction) } }
  val actions = LocalReviewActions.current
  val typography = LocalDiffTypography.current
  val layouts = remember { VisibleLineLayouts() }
  val split = display.mode == DiffMode.Split
  val contentStartX = with(LocalDensity.current) { ContentHorizontalMargin.toPx() }
  val markerWidth = with(LocalDensity.current) { MarkerColumnWidth.toPx() }
  val codeStartX = with(LocalDensity.current) { (ContentHorizontalMargin + typography.lineNumberWidth * (if (split) 1 else 2) + MarkerColumnWidth).toPx() }
  val listWidth = remember { mutableIntStateOf(0) }
  val splitHalfWidth = remember(contentStartX) { { (listWidth.intValue - 2 * contentStartX) / 2f } }
  val colors = LocalDiffColors.current
  val iconTint = remember(colors.mutedText) { ColorFilter.tint(colors.mutedText) }
  val framePainter = rememberFileFramePainter()
  val frameInset = with(LocalDensity.current) { FileHeaderTopMargin.toPx() }
  val frameMargin = with(LocalDensity.current) { ContentHorizontalMargin.toPx() }
  val shown = placements.loadId == loadId
  val draft = if (shown) draftTarget else null
  val draftPlacement = (draft as? DraftTarget.NewThread)?.placement
  val draftLinePos = remember(draft) { draftLinePosition(draft) }
  val draftFileIndex = remember(draftPlacement) { (draftPlacement as? Placement.OnFile)?.fileIndex ?: -1 }
  val hasLineThreads = shown && placements.byLine.isNotEmpty()
  val hoveredKey = remember { mutableStateOf<String?>(null) }
  val draftSide = remember(draftTarget) { ((draftTarget as? DraftTarget.NewThread)?.anchor as? Anchor.Lines)?.side }
  val commentable = actions != null
  val onRange: ((RangeAnchor) -> Unit)? = remember(actions) {
    actions?.let { reviewActions ->
      { range -> reviewActions.startDraft(DraftTarget.NewThread(range.anchor, Placement.OnLine(range.location))) }
    }
  }
  val onFileComment: ((DiffRow.FileHeader) -> Unit)? = remember(actions) {
    actions?.let { reviewActions ->
      { header ->
        reviewActions.startDraft(DraftTarget.NewThread(Anchor.File(header.file.anchorPath), Placement.OnFile(header.fileIndex)))
      }
    }
  }
  val noteIndent = typography.lineNumberWidth * 2 + MarkerColumnWidth
  val splitNoteIndent = typography.lineNumberWidth + MarkerColumnWidth
  val hoverModifier = if (!commentable) {
    Modifier
  } else {
    Modifier
      .onPointerEvent(PointerEventType.Enter) { event -> hoveredKey.value = hoverKeyAt(listState, event.changes.first().position, split, contentStartX, splitHalfWidth) }
      .onPointerEvent(PointerEventType.Move) { event -> hoveredKey.value = hoverKeyAt(listState, event.changes.first().position, split, contentStartX, splitHalfWidth) }
      .onPointerEvent(PointerEventType.Exit) { hoveredKey.value = null }
  }
  val draftedRange = remember(draft, files, split) { draftRange(draft, files, split) }
  SideEffect { rangeState.drafted = draftedRange }
  val currentDraft by rememberUpdatedState(draft)
  val draftTargetOf = remember { { currentDraft } }
  val selectionModifier = Modifier.diffSelectionGesture(listState, selection, selectionSource, layouts, rows, layout, display, contentStartX, codeStartX, splitHalfWidth, typography.tabWidth, files, markerWidth, onRange, rangeState, draftTargetOf, loadId)
  Box(modifier) {
    LazyColumn(
      state = listState,
      modifier = Modifier
        .fillMaxSize()
        .testTag("diff-list")
        .onSizeChanged { listWidth.intValue = it.width }
        .then(hoverModifier)
        .then(selectionModifier)
        .fileFrames(listState, layout, framePainter, colors.border, colors.background, frameInset, frameMargin),
      contentPadding = PaddingValues(start = ContentHorizontalMargin, end = ContentHorizontalMargin, bottom = FileHeaderTopMargin),
    ) {
      if (layout.hasOrphanItem) {
        item(key = ORPHANS_KEY, contentType = ORPHANS_CONTENT_TYPE) {
          OrphanSection(placements.orphans, draft, composeNew = draft.isOrphanThread())
        }
      }
      for (fileIndex in 0 until layout.fileCount) {
        val headerRow = layout.headerRowIndex(fileIndex)
        val collapsed = layout.isCollapsed(fileIndex)
        val header = rows[display.unifiedOf(headerRow)] as DiffRow.FileHeader
        stickyHeader(key = header.key, contentType = HEADER_CONTENT_TYPE) {
          FileHeaderRow(header, collapsed, iconTint, onFileComment, expansionErrors[header.fileIndex]) { currentOnHeaderClick(header.fileIndex) }
        }
        if (!collapsed) {
          if (layout.hasNotes(fileIndex)) {
            item(key = "n$fileIndex", contentType = NOTES_CONTENT_TYPE) {
              NoteStack(
                threads = (if (shown) placements.byFile[fileIndex] else null).orEmpty(),
                draftTarget = draft,
                composeNew = draftFileIndex == fileIndex,
              )
            }
          }
          val bodyStart = headerRow + 1
          if (display.mode == DiffMode.Split) {
            items(
              count = layout.bodyRowCount(fileIndex),
              key = { splitItemKey(rows, display, bodyStart + it) },
              contentType = { splitContentType(rows, display, bodyStart + it) },
            ) { offset ->
              val displayRow = bodyStart + offset
              val leftRow = rows.getOrNull(display.unifiedOf(displayRow, Side.Old)) as? DiffRow.Line
              val rightRow = rows.getOrNull(display.unifiedOf(displayRow, Side.New)) as? DiffRow.Line
              when (val row = rows[display.unifiedOf(displayRow, null)]) {
                is DiffRow.FileHeader -> Unit

                is DiffRow.HunkHeader -> HunkHeaderRow(row, typography.lineNumberWidth, expand)

                is DiffRow.TrailingGap -> TrailingGapRow(row, typography.lineNumberWidth, expand)

                is DiffRow.Placeholder -> PlaceholderRow(row.message)

                is DiffRow.Line -> {
                  val slot = highlights.getOrNull(row.fileIndex)
                  val needsPositions = hasLineThreads || draftLinePos >= 0
                  val leftPos = if (needsPositions) sidePositionOf(leftRow, ReviewSide.Old) else -1L
                  val rightPos = if (needsPositions) sidePositionOf(rightRow, ReviewSide.New) else -1L
                  val leftAll = if (hasLineThreads && leftPos >= 0) placements.byLine[leftPos] else null
                  val rightAll = if (hasLineThreads && rightPos >= 0) placements.byLine[rightPos] else null
                  val leftThreads = leftAll?.filter { (it.anchor as? Anchor.Lines)?.side == ReviewSide.Old }.orEmpty()
                  val rightThreads = rightAll?.filter { (it.anchor as? Anchor.Lines)?.side == ReviewSide.New }.orEmpty()
                  val composingLeft = draftLinePos >= 0 && leftPos == draftLinePos && draftSide == ReviewSide.Old
                  val composingRight = draftLinePos >= 0 && rightPos == draftLinePos && draftSide == ReviewSide.New
                  if (leftThreads.isEmpty() && rightThreads.isEmpty() && !composingLeft && !composingRight) {
                    SplitLineRow(leftRow, display.unifiedOf(displayRow, Side.Old), rightRow, display.unifiedOf(displayRow, Side.New), slot, hoveredKey, commentable, selection, layouts, rangeState)
                  } else {
                    Column {
                      SplitLineRow(leftRow, display.unifiedOf(displayRow, Side.Old), rightRow, display.unifiedOf(displayRow, Side.New), slot, hoveredKey, commentable, selection, layouts, rangeState)
                      Row(modifier = Modifier.fillMaxWidth()) {
                        SplitNoteColumn(leftThreads, draft, composingLeft, splitNoteIndent, Modifier.weight(1f))
                        SplitNoteColumn(rightThreads, draft, composingRight, splitNoteIndent, Modifier.weight(1f))
                      }
                    }
                  }
                }
              }
            }
          } else {
            items(
              count = layout.bodyRowCount(fileIndex),
              key = { rows[bodyStart + it].key },
              contentType = { rows[bodyStart + it].contentType() },
            ) { offset ->
              when (val row = rows[bodyStart + offset]) {
                is DiffRow.FileHeader -> Unit

                is DiffRow.HunkHeader -> HunkHeaderRow(row, typography.lineNumberWidth * 2, expand)

                is DiffRow.TrailingGap -> TrailingGapRow(row, typography.lineNumberWidth * 2, expand)

                is DiffRow.Line -> {
                  val slot = highlights.getOrNull(row.fileIndex)
                  val needsPositions = hasLineThreads || draftLinePos >= 0
                  val oldPos = if (needsPositions) sidePositionOf(row, ReviewSide.Old) else -1L
                  val newPos = if (needsPositions) sidePositionOf(row, ReviewSide.New) else -1L
                  val threads = if (hasLineThreads) concatOrNull(placements.byLine[oldPos], placements.byLine[newPos]) else null
                  val composing = draftLinePos >= 0 && (oldPos == draftLinePos || newPos == draftLinePos)
                  if (threads == null && !composing) {
                    LineRow(row, bodyStart + offset, slot, hoveredKey, commentable, selection, layouts, rangeState)
                  } else {
                    Column {
                      LineRow(row, bodyStart + offset, slot, hoveredKey, commentable, selection, layouts, rangeState)
                      NoteStack(threads.orEmpty(), draft, composing, Modifier.padding(start = noteIndent, end = 8.dp))
                    }
                  }
                }

                is DiffRow.Placeholder -> PlaceholderRow(row.message)
              }
            }
          }
        }
      }
      if (layout.hasEndItem) {
        item(key = END_KEY, contentType = END_CONTENT_TYPE) { EndOfDiffFooter() }
      }
    }
    MacOverlayScrollbar(listState, Modifier.align(Alignment.CenterEnd).padding(end = 4.dp))
  }
}

private fun draftLinePosition(draft: DraftTarget?): Long {
  val target = draft as? DraftTarget.NewThread ?: return -1L
  val anchor = target.anchor as? Anchor.Lines ?: return -1L
  val placement = target.placement as? Placement.OnLine ?: return -1L
  return sidePosition(placement.location.fileIndex, anchor.side, anchor.endLine)
}

private fun sidePositionOf(row: DiffRow.Line?, side: ReviewSide): Long {
  val line = row?.line ?: return -1L
  if (line.kind == LineKind.NoNewlineAtEof) return -1L
  val number = (if (side == ReviewSide.Old) line.oldLineNo else line.newLineNo) ?: return -1L
  return sidePosition(row.fileIndex, side, number)
}

private fun concatOrNull(first: List<ReviewThread>?, second: List<ReviewThread>?): List<ReviewThread>? = when {
  first == null -> second
  second == null -> first
  else -> first + second
}

private fun keyAt(listState: LazyListState, y: Float): String? = listState.layoutInfo.visibleItemsInfo.firstOrNull { y >= it.offset && y < it.offset + it.size }?.key as? String

private fun hoverKeyAt(listState: LazyListState, position: Offset, split: Boolean, contentStartX: Float, splitHalfWidth: () -> Float): String? {
  val key = keyAt(listState, position.y) ?: return null
  if (!split) return key
  return key + if (position.x - contentStartX < splitHalfWidth()) ":L" else ":R"
}

@Composable
private fun SplitNoteColumn(threads: List<ReviewThread>, draft: DraftTarget?, composing: Boolean, indent: Dp, modifier: Modifier) {
  if (threads.isEmpty() && !composing) {
    Spacer(modifier)
  } else {
    NoteStack(threads, draft, composing, modifier.padding(start = indent, end = 8.dp))
  }
}

private fun FileDiff.headerTitle(): String {
  val old = oldPath
  val new = newPath
  return when {
    change == ChangeType.Renamed && old != null && new != null -> "${markControls(old)} → ${markControls(new)}"
    else -> markControls(new ?: old.orEmpty())
  }
}

private fun FileDiff.badge(): String = if (isUntracked) "Untracked" else change.name

private fun Modifier.headerBadge(borderColor: Color): Modifier = border(1.dp, borderColor, RoundedCornerShape(10.dp)).padding(horizontal = 6.dp, vertical = 1.dp)

@Composable
private fun FileHeaderRow(
  row: DiffRow.FileHeader,
  collapsed: Boolean,
  iconTint: ColorFilter,
  onComment: ((DiffRow.FileHeader) -> Unit)?,
  expansionError: String?,
  onClick: () -> Unit,
) {
  val colors = LocalDiffColors.current
  val typography = LocalDiffTypography.current
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(colors.background)
      .padding(top = FileHeaderTopMargin)
      .background(colors.fileHeaderBackground)
      .focusProperties { canFocus = false }
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
      colorFilter = iconTint,
    )
    Row(
      modifier = Modifier.weight(1f),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      BasicText(
        text = remember(row.file) { row.file.headerTitle() },
        modifier = Modifier.weight(1f, fill = false),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = typography.headerPath,
      )
      BasicText(
        text = row.file.badge(),
        modifier = Modifier
          .testTag("file-badge-${row.fileIndex}")
          .headerBadge(colors.border),
        style = typography.headerBadge,
      )
      if (row.largeDiff) {
        BasicText(
          text = "Large diff",
          modifier = Modifier
            .testTag("file-large-${row.fileIndex}")
            .headerBadge(colors.border),
          style = typography.headerBadge,
        )
      }
    }
    val statsText = remember(row.stats, typography) { statsText(row.stats, typography.headerAdditions, typography.headerDeletions) }
    if (statsText != null) {
      BasicText(text = statsText, modifier = Modifier.testTag("file-stats-${row.fileIndex}"))
    }
    if (expansionError != null) MacInlineError(expansionError)
    if (onComment != null) {
      Image(
        imageVector = KdvIcons.Comment,
        contentDescription = "Comment on file",
        modifier = Modifier
          .testTag("file-comment-${row.fileIndex}")
          .clickable(interactionSource = null, indication = null) { onComment(row) }
          .size(FileCommentIconSize),
        colorFilter = iconTint,
      )
    }
  }
}

@Composable
private fun HunkHeaderRow(row: DiffRow.HunkHeader, gutterWidth: Dp, onExpand: (Int, Int, ExpandDirection) -> Unit) {
  val colors = LocalDiffColors.current
  val typography = LocalDiffTypography.current
  val text = remember(row.hunk, typography.tabWidth, typography.controlMarker) {
    plainDisplayText(hunkHeaderLabel(row.hunk), typography.tabWidth, typography.controlMarker)
  }
  val lineHeight = with(LocalDensity.current) { typography.hunkHeader.lineHeight.toDp() }
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(colors.hunkBackground),
    verticalAlignment = Alignment.Bottom,
  ) {
    val gap = row.gap
    if (gap != null) ExpandGutter(gap, row.fileIndex, gutterWidth, lineHeight, onExpand) else Spacer(Modifier.width(gutterWidth))
    val modifier = Modifier.padding(horizontal = 12.dp)
    when (text) {
      is AnnotatedString -> BasicText(text = text, modifier = modifier, style = typography.hunkHeader)
      else -> BasicText(text = text.toString(), modifier = modifier, style = typography.hunkHeader)
    }
  }
}

@Composable
private fun TrailingGapRow(row: DiffRow.TrailingGap, gutterWidth: Dp, onExpand: (Int, Int, ExpandDirection) -> Unit) {
  val colors = LocalDiffColors.current
  val lineHeight = with(LocalDensity.current) { LocalDiffTypography.current.hunkHeader.lineHeight.toDp() }
  Row(modifier = Modifier.fillMaxWidth().background(colors.hunkBackground)) {
    ExpandGutter(row.gap, row.fileIndex, gutterWidth, lineHeight, onExpand)
  }
}

@Composable
private fun PlaceholderRow(message: String) {
  BasicText(
    text = message,
    modifier = Modifier.fillMaxWidth().padding(12.dp),
    style = LocalDiffTypography.current.placeholder,
  )
}

@Composable
private fun LineRow(
  row: DiffRow.Line,
  rowIndex: Int,
  slot: HighlightSlot?,
  hoveredKey: State<String?>,
  commentable: Boolean,
  selection: DiffSelectionState,
  layouts: VisibleLineLayouts,
  rangeState: CommentRangeState,
) {
  val key = row.key
  val hovered by remember(key) { derivedStateOf { hoveredKey.value == key } }
  val colors = LocalDiffColors.current
  val typography = LocalDiffTypography.current
  val modifier = Modifier.fillMaxWidth().lineBackground(
    typography.lineNumberWidth * 2,
    gutterColor(row.line.kind, colors),
    contentColor(row.line.kind, colors),
    range = rangeState,
    rangeColor = colors.rangeBackground,
    leftLine = LineId(linePosition(row.fileIndex, row.hunkIndex, row.lineIndex)),
  )
  LineHalf(row, rowIndex, null, slot, hovered && commentable, selection, layouts, modifier)
}

@Composable
private fun SplitLineRow(
  left: DiffRow.Line?,
  leftIndex: Int,
  right: DiffRow.Line?,
  rightIndex: Int,
  slot: HighlightSlot?,
  hoveredKey: State<String?>,
  commentable: Boolean,
  selection: DiffSelectionState,
  layouts: VisibleLineLayouts,
  rangeState: CommentRangeState,
) {
  val key = remember(left?.key, right?.key) { "s" + (left?.key ?: "-") + "|" + (right?.key ?: "-") }
  val leftKey = remember(key) { "$key:L" }
  val rightKey = remember(key) { "$key:R" }
  val leftHovered by remember(leftKey) { derivedStateOf { hoveredKey.value == leftKey } }
  val rightHovered by remember(rightKey) { derivedStateOf { hoveredKey.value == rightKey } }
  val colors = LocalDiffColors.current
  val typography = LocalDiffTypography.current
  val modifier = Modifier.fillMaxWidth().lineBackground(
    typography.lineNumberWidth,
    left?.let { gutterColor(it.line.kind, colors) } ?: colors.emptySideBackground,
    left?.let { contentColor(it.line.kind, colors) } ?: colors.emptySideBackground,
    right?.let { gutterColor(it.line.kind, colors) } ?: colors.emptySideBackground,
    right?.let { contentColor(it.line.kind, colors) } ?: colors.emptySideBackground,
    split = true,
    range = rangeState,
    rangeColor = colors.rangeBackground,
    leftLine = left?.let { LineId(linePosition(it.fileIndex, it.hunkIndex, it.lineIndex)) } ?: LineId.None,
    rightLine = right?.let { LineId(linePosition(it.fileIndex, it.hunkIndex, it.lineIndex)) } ?: LineId.None,
  )
  SplitLineLayout(
    left = { LineHalf(left, leftIndex, Side.Old, slot, leftHovered && commentable, selection, layouts, Modifier) },
    right = { LineHalf(right, rightIndex, Side.New, slot, rightHovered && commentable, selection, layouts, Modifier) },
    modifier = modifier,
  )
}

@Composable
private fun SplitLineLayout(left: @Composable () -> Unit, right: @Composable () -> Unit, modifier: Modifier) {
  Layout(content = {
    left()
    right()
  }, modifier = modifier) { measurables, constraints ->
    val width = constraints.maxWidth
    val leftWidth = width / 2
    val rightWidth = width - leftWidth
    val leftPlaceable = measurables[0].measure(Constraints(minWidth = leftWidth, maxWidth = leftWidth))
    val rightPlaceable = measurables[1].measure(Constraints(minWidth = rightWidth, maxWidth = rightWidth))
    val height = maxOf(leftPlaceable.height, rightPlaceable.height)
    layout(width, height) {
      leftPlaceable.place(0, 0)
      rightPlaceable.place(leftWidth, 0)
    }
  }
}

private fun gutterColor(kind: LineKind, colors: DiffColors): Color = when (kind) {
  LineKind.Added -> colors.addedLineNumberBackground
  LineKind.Removed -> colors.removedLineNumberBackground
  LineKind.NoNewlineAtEof -> Color.Unspecified
  else -> colors.background
}

private fun contentColor(kind: LineKind, colors: DiffColors): Color = when (kind) {
  LineKind.Added -> colors.addedBackground
  LineKind.Removed -> colors.removedBackground
  LineKind.NoNewlineAtEof -> Color.Unspecified
  else -> colors.background
}

private fun markerOf(kind: LineKind): String = when (kind) {
  LineKind.Added -> "+"
  LineKind.Removed -> "-"
  else -> " "
}

@Composable
private fun LineHalf(
  row: DiffRow.Line?,
  rowIndex: Int,
  side: Side?,
  slot: HighlightSlot?,
  showAdd: Boolean,
  selection: DiffSelectionState,
  layouts: VisibleLineLayouts,
  modifier: Modifier,
) {
  val colors = LocalDiffColors.current
  val typography = LocalDiffTypography.current
  if (row == null) {
    Box(modifier)
    return
  }
  val line = row.line
  if (line.kind == LineKind.NoNewlineAtEof) {
    val indent = if (side == null) typography.lineNumberWidth * 2 + MarkerColumnWidth else typography.lineNumberWidth + MarkerColumnWidth
    BasicText(
      text = "No newline at end of file",
      modifier = modifier.padding(start = indent),
      style = typography.noNewline,
    )
    return
  }
  val range = remember(rowIndex, line, side) {
    derivedStateOf {
      val current = selection.selection
      if (current != null && (current.side == null || current.side == side)) current.rangeFor(rowIndex, line.text.length) else null
    }
  }
  val layoutState = remember(rowIndex, side) { mutableStateOf<TextLayoutResult?>(null) }
  val onTextLayout: (TextLayoutResult) -> Unit = remember(rowIndex, side, layouts) {
    { result ->
      layoutState.value = result
      layouts.register(rowIndex, side, result)
    }
  }
  DisposableEffect(rowIndex, side, layouts) {
    onDispose { layoutState.value?.let { layouts.unregister(rowIndex, side, it) } }
  }
  val tabWidth = typography.tabWidth
  val controlStyle = typography.controlMarker
  val selectionColor = colors.selectionBackground
  Row(modifier = modifier) {
    if (side != Side.New) LineNumberCell(line.oldLineNo, typography)
    if (side != Side.Old) LineNumberCell(line.newLineNo, typography)
    if (showAdd) {
      AddCommentButton(typography)
    } else {
      BasicText(markerOf(line.kind), Modifier.width(MarkerColumnWidth), style = typography.marker)
    }
    val contentModifier = remember(range, layoutState, line.text, tabWidth, selectionColor) {
      Modifier
        .weight(1f)
        .drawBehind {
          val selected = range.value ?: return@drawBehind
          val layout = layoutState.value ?: return@drawBehind
          drawPath(layout.getPathForRange(sourceToDisplay(line.text, selected.first, tabWidth), sourceToDisplay(line.text, selected.last + 1, tabWidth)), selectionColor)
        }
        .then(TextCursor)
    }
    val spans = slot?.value?.spans?.getOrNull(row.hunkIndex)?.getOrNull(row.lineIndex)
    if (spans != null) {
      val theme = LocalSyntaxTheme.current
      val text = remember(line, spans, theme, tabWidth, controlStyle) { displayText(line.text, spans, theme, tabWidth, controlStyle) }
      BasicText(text = text, modifier = contentModifier, style = typography.code, onTextLayout = onTextLayout)
    } else {
      val text = remember(line.text, tabWidth, controlStyle) { plainDisplayText(line.text, tabWidth, controlStyle) }
      when (text) {
        is AnnotatedString -> BasicText(text = text, modifier = contentModifier, style = typography.code, onTextLayout = onTextLayout)
        else -> BasicText(text = text.toString(), modifier = contentModifier, style = typography.code, onTextLayout = onTextLayout)
      }
    }
  }
}

@Composable
private fun LineNumberCell(number: Int?, typography: DiffTypography) {
  Box(modifier = Modifier.width(typography.lineNumberWidth).padding(end = 6.dp)) {
    BasicText(
      text = number?.toString().orEmpty(),
      modifier = Modifier.fillMaxWidth(),
      style = typography.lineNumber,
    )
  }
}

@Composable
private fun AddCommentButton(typography: DiffTypography) {
  val colors = LocalDiffColors.current
  val lineHeight = with(LocalDensity.current) { typography.lineNumber.lineHeight.toDp() }
  val size = min(AddCommentSize, lineHeight)
  Box(modifier = Modifier.width(MarkerColumnWidth).height(lineHeight).testTag("add-comment"), contentAlignment = Alignment.Center) {
    Box(
      modifier = Modifier.size(size).clip(RoundedCornerShape(3.dp)).background(colors.accent),
      contentAlignment = Alignment.Center,
    ) {
      Image(imageVector = KdvIcons.Plus, contentDescription = "Add comment", modifier = Modifier.size(10.dp), colorFilter = AddCommentTint)
    }
  }
}
