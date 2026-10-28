package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
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
import io.github.mataku.kdiffviewer.app.selection.DiffSelectionState
import io.github.mataku.kdiffviewer.app.selection.SelectionSource
import io.github.mataku.kdiffviewer.app.ui.review.LocalReviewActions
import io.github.mataku.kdiffviewer.app.ui.review.NoteStack
import io.github.mataku.kdiffviewer.app.ui.review.OrphanSection
import io.github.mataku.kdiffviewer.core.model.ChangeType
import io.github.mataku.kdiffviewer.core.model.FileDiff
import io.github.mataku.kdiffviewer.core.model.LineKind
import io.github.mataku.kdiffviewer.core.model.Side
import io.github.mataku.kdiffviewer.core.review.Anchor
import io.github.mataku.kdiffviewer.core.review.LineLocation
import io.github.mataku.kdiffviewer.core.review.Placement
import io.github.mataku.kdiffviewer.core.review.anchorOf
import io.github.mataku.kdiffviewer.core.review.anchorPath
import io.github.mataku.kdiffviewer.protocol.markControls

private val FileHeaderTopMargin = 12.dp
private val FileChevronSize = 12.dp
private val FileCommentIconSize = 14.dp
private val AddCommentSize = 16.dp
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

private fun DiffRow.contentType(): Int = when (this) {
  is DiffRow.FileHeader -> HEADER_CONTENT_TYPE
  is DiffRow.HunkHeader -> HUNK_HEADER_CONTENT_TYPE
  is DiffRow.Line -> LINE_CONTENT_TYPE
  is DiffRow.Placeholder -> PLACEHOLDER_CONTENT_TYPE
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
) {
  val currentOnHeaderClick by rememberUpdatedState(onHeaderClick)
  val actions = LocalReviewActions.current
  val typography = LocalDiffTypography.current
  val layouts = remember { VisibleLineLayouts() }
  val split = display.mode == DiffMode.Split
  val contentStartX = with(LocalDensity.current) { ContentHorizontalMargin.toPx() }
  val codeStartX = with(LocalDensity.current) { (ContentHorizontalMargin + typography.lineNumberWidth * (if (split) 1 else 2) + 16.dp).toPx() }
  val listWidth = remember { mutableIntStateOf(0) }
  val splitHalfWidth = remember(contentStartX) { { (listWidth.intValue - 2 * contentStartX) / 2f } }
  val colors = LocalDiffColors.current
  val framePainter = rememberFileFramePainter()
  val frameInset = with(LocalDensity.current) { FileHeaderTopMargin.toPx() }
  val frameMargin = with(LocalDensity.current) { ContentHorizontalMargin.toPx() }
  val shown = placements.loadId == loadId
  val draft = if (shown) draftTarget else null
  val draftPlacement = (draft as? DraftTarget.NewThread)?.placement
  val draftLinePos = remember(draftPlacement) { (draftPlacement as? Placement.OnLine)?.location?.let { linePosition(it.fileIndex, it.hunkIndex, it.lineIndex) } ?: -1L }
  val draftFileIndex = remember(draftPlacement) { (draftPlacement as? Placement.OnFile)?.fileIndex ?: -1 }
  val hasLineThreads = shown && placements.byLine.isNotEmpty()
  val hoveredKey = remember { mutableStateOf<String?>(null) }
  val onAddComment: ((DiffRow.Line) -> Unit)? = remember(files, actions) {
    actions?.let { reviewActions ->
      { row ->
        anchorOf(files[row.fileIndex], row.hunkIndex, row.lineIndex)?.let { anchor ->
          reviewActions.startDraft(DraftTarget.NewThread(anchor, Placement.OnLine(LineLocation(row.fileIndex, row.hunkIndex, row.lineIndex))))
        }
      }
    }
  }
  val onFileComment: ((DiffRow.FileHeader) -> Unit)? = remember(actions) {
    actions?.let { reviewActions ->
      { header ->
        reviewActions.startDraft(DraftTarget.NewThread(Anchor.File(header.file.anchorPath), Placement.OnFile(header.fileIndex)))
      }
    }
  }
  val noteIndent = typography.lineNumberWidth * 2 + 16.dp
  val hoverModifier = if (onAddComment == null) {
    Modifier
  } else {
    Modifier
      .onPointerEvent(PointerEventType.Enter) { event -> hoveredKey.value = keyAt(listState, event.changes.first().position.y) }
      .onPointerEvent(PointerEventType.Move) { event -> hoveredKey.value = keyAt(listState, event.changes.first().position.y) }
      .onPointerEvent(PointerEventType.Exit) { hoveredKey.value = null }
  }
  val selectionModifier = Modifier.diffSelectionGesture(listState, selection, selectionSource, layouts, rows, layout, display, contentStartX, codeStartX, splitHalfWidth, typography.tabWidth)
  LazyColumn(
    state = listState,
    modifier = modifier
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
        FileHeaderRow(header, collapsed, onFileComment) { currentOnHeaderClick(header.fileIndex) }
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

              is DiffRow.HunkHeader -> HunkHeaderRow(row)

              is DiffRow.Placeholder -> PlaceholderRow(row.message)

              is DiffRow.Line -> {
                val slot = highlights.getOrNull(row.fileIndex)
                val leftPos = if (leftRow != null && (hasLineThreads || draftLinePos >= 0)) linePosition(leftRow.fileIndex, leftRow.hunkIndex, leftRow.lineIndex) else -1L
                val rightPos = if (rightRow != null && (hasLineThreads || draftLinePos >= 0)) linePosition(rightRow.fileIndex, rightRow.hunkIndex, rightRow.lineIndex) else -1L
                val leftThreads = if (hasLineThreads && leftPos >= 0) placements.byLine[leftPos] else null
                val rightThreads = if (hasLineThreads && rightPos >= 0 && rightPos != leftPos) placements.byLine[rightPos] else null
                val composing = draftLinePos >= 0 && (leftPos == draftLinePos || rightPos == draftLinePos)
                if (leftThreads == null && rightThreads == null && !composing) {
                  SplitLineRow(leftRow, display.unifiedOf(displayRow, Side.Old), rightRow, display.unifiedOf(displayRow, Side.New), slot, hoveredKey, onAddComment, selection, layouts)
                } else {
                  val threads = when {
                    leftThreads == null -> rightThreads.orEmpty()
                    rightThreads == null -> leftThreads
                    else -> leftThreads + rightThreads
                  }
                  Column {
                    SplitLineRow(leftRow, display.unifiedOf(displayRow, Side.Old), rightRow, display.unifiedOf(displayRow, Side.New), slot, hoveredKey, onAddComment, selection, layouts)
                    NoteStack(threads, draft, composing, Modifier.padding(start = noteIndent, end = 8.dp))
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

              is DiffRow.HunkHeader -> HunkHeaderRow(row)

              is DiffRow.Line -> {
                val slot = highlights.getOrNull(row.fileIndex)
                val position = if (hasLineThreads || draftLinePos >= 0) linePosition(row.fileIndex, row.hunkIndex, row.lineIndex) else -1L
                val threads = if (hasLineThreads) placements.byLine[position] else null
                val composing = position >= 0 && position == draftLinePos
                if (threads == null && !composing) {
                  LineRow(row, bodyStart + offset, slot, hoveredKey, onAddComment, selection, layouts)
                } else {
                  Column {
                    LineRow(row, bodyStart + offset, slot, hoveredKey, onAddComment, selection, layouts)
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
}

private fun keyAt(listState: LazyListState, y: Float): String? = listState.layoutInfo.visibleItemsInfo.firstOrNull { y >= it.offset && y < it.offset + it.size }?.key as? String

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
private fun FileHeaderRow(row: DiffRow.FileHeader, collapsed: Boolean, onComment: ((DiffRow.FileHeader) -> Unit)?, onClick: () -> Unit) {
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
      colorFilter = ColorFilter.tint(colors.mutedText),
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
    if (onComment != null) {
      Image(
        imageVector = KdvIcons.Comment,
        contentDescription = "Comment on file",
        modifier = Modifier
          .testTag("file-comment-${row.fileIndex}")
          .clickable(interactionSource = null, indication = null) { onComment(row) }
          .size(FileCommentIconSize),
        colorFilter = ColorFilter.tint(colors.mutedText),
      )
    }
  }
}

@Composable
private fun HunkHeaderRow(row: DiffRow.HunkHeader) {
  val colors = LocalDiffColors.current
  val typography = LocalDiffTypography.current
  val text = remember(row.hunk, typography.tabWidth, typography.controlMarker) {
    plainDisplayText(hunkHeaderLabel(row.hunk), typography.tabWidth, typography.controlMarker)
  }
  val modifier = Modifier
    .fillMaxWidth()
    .background(colors.hunkBackground)
    .padding(horizontal = 12.dp)
  when (text) {
    is AnnotatedString -> BasicText(text = text, modifier = modifier, style = typography.hunkHeader)
    else -> BasicText(text = text.toString(), modifier = modifier, style = typography.hunkHeader)
  }
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
private fun LineRow(
  row: DiffRow.Line,
  rowIndex: Int,
  slot: HighlightSlot?,
  hoveredKey: State<String?>,
  onAddComment: ((DiffRow.Line) -> Unit)?,
  selection: DiffSelectionState,
  layouts: VisibleLineLayouts,
) {
  val key = row.key
  val hovered by remember(key) { derivedStateOf { hoveredKey.value == key } }
  LineHalf(row, rowIndex, null, slot, if (hovered) onAddComment else null, selection, layouts, Modifier.fillMaxWidth().height(IntrinsicSize.Min))
}

@Composable
private fun SplitLineRow(
  left: DiffRow.Line?,
  leftIndex: Int,
  right: DiffRow.Line?,
  rightIndex: Int,
  slot: HighlightSlot?,
  hoveredKey: State<String?>,
  onAddComment: ((DiffRow.Line) -> Unit)?,
  selection: DiffSelectionState,
  layouts: VisibleLineLayouts,
) {
  val key = remember(left?.key, right?.key) { "s" + (left?.key ?: "-") + "|" + (right?.key ?: "-") }
  val hovered by remember(key) { derivedStateOf { hoveredKey.value == key } }
  val onAdd = if (hovered) onAddComment else null
  Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
    LineHalf(left, leftIndex, Side.Old, slot, onAdd, selection, layouts, Modifier.weight(1f).fillMaxHeight())
    LineHalf(right, rightIndex, Side.New, slot, if (left == null) onAdd else null, selection, layouts, Modifier.weight(1f).fillMaxHeight())
  }
}

@Composable
private fun LineHalf(
  row: DiffRow.Line?,
  rowIndex: Int,
  side: Side?,
  slot: HighlightSlot?,
  onAddComment: ((DiffRow.Line) -> Unit)?,
  selection: DiffSelectionState,
  layouts: VisibleLineLayouts,
  modifier: Modifier,
) {
  val colors = LocalDiffColors.current
  val typography = LocalDiffTypography.current
  if (row == null) {
    Box(modifier = modifier.background(colors.emptySideBackground))
    return
  }
  val line = row.line
  if (line.kind == LineKind.NoNewlineAtEof) {
    val indent = if (side == null) typography.lineNumberWidth * 2 + 16.dp else typography.lineNumberWidth + 16.dp
    BasicText(
      text = "No newline at end of file",
      modifier = modifier.padding(start = indent),
      style = typography.noNewline,
    )
    return
  }
  val (contentBackground, numberBackground, marker) = when (line.kind) {
    LineKind.Added -> Triple(colors.addedBackground, colors.addedLineNumberBackground, "+")
    LineKind.Removed -> Triple(colors.removedBackground, colors.removedLineNumberBackground, "-")
    else -> Triple(colors.background, colors.background, " ")
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
    if (side != Side.New) LineNumberCell(line.oldLineNo, numberBackground, typography, row, onAddComment)
    if (side != Side.Old) LineNumberCell(line.newLineNo, numberBackground, typography, if (side == Side.New) row else null, if (side == Side.New) onAddComment else null)
    Box(modifier = Modifier.width(16.dp).fillMaxHeight().background(contentBackground)) {
      BasicText(
        text = marker,
        modifier = Modifier.align(Alignment.TopCenter),
        style = typography.marker,
      )
    }
    val contentModifier = remember(contentBackground, range, layoutState, line.text, tabWidth, selectionColor) {
      Modifier
        .weight(1f)
        .fillMaxHeight()
        .background(contentBackground)
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
private fun LineNumberCell(number: Int?, background: Color, typography: DiffTypography, row: DiffRow.Line?, onAdd: ((DiffRow.Line) -> Unit)?) {
  Box(modifier = Modifier.width(typography.lineNumberWidth).fillMaxHeight().background(background).padding(end = 6.dp)) {
    BasicText(
      text = number?.toString().orEmpty(),
      modifier = Modifier.fillMaxWidth(),
      style = typography.lineNumber,
    )
    if (onAdd != null && row != null) {
      val colors = LocalDiffColors.current
      val lineHeight = with(LocalDensity.current) { typography.lineNumber.lineHeight.toDp() }
      val size = min(AddCommentSize, lineHeight)
      Box(
        modifier = Modifier
          .align(Alignment.TopStart)
          .padding(start = 2.dp, top = (lineHeight - size) / 2)
          .testTag("add-comment")
          .size(size)
          .clip(RoundedCornerShape(3.dp))
          .background(colors.accent)
          .clickable(interactionSource = null, indication = null) { onAdd(row) },
        contentAlignment = Alignment.Center,
      ) {
        Image(imageVector = KdvIcons.Plus, contentDescription = "Add comment", modifier = Modifier.size(10.dp), colorFilter = ColorFilter.tint(Color.White))
      }
    }
  }
}
