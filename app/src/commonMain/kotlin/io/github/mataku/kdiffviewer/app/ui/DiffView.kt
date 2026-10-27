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
import io.github.mataku.kdiffviewer.app.DiffRow
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

private fun DiffRow.contentType(): Int = when (this) {
  is DiffRow.FileHeader -> HEADER_CONTENT_TYPE
  is DiffRow.HunkHeader -> HUNK_HEADER_CONTENT_TYPE
  is DiffRow.Line -> LINE_CONTENT_TYPE
  is DiffRow.Placeholder -> PLACEHOLDER_CONTENT_TYPE
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DiffView(
  rows: List<DiffRow>,
  layout: DiffLayout,
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
  val codeStartX = with(LocalDensity.current) { (ContentHorizontalMargin + typography.lineNumberWidth * 2 + 16.dp).toPx() }
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
  val selectionModifier = Modifier.diffSelectionGesture(listState, selection, selectionSource, layouts, rows, layout, codeStartX, typography.tabWidth)
  LazyColumn(
    state = listState,
    modifier = modifier.testTag("diff-list").then(hoverModifier).then(selectionModifier),
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
      val header = rows[headerRow] as DiffRow.FileHeader
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
      .border(1.dp, colors.border)
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
          .border(1.dp, colors.border, RoundedCornerShape(10.dp))
          .padding(horizontal = 6.dp, vertical = 1.dp),
        style = typography.headerBadge,
      )
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
    .padding(horizontal = 12.dp, vertical = 2.dp)
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
  val key = row.key
  val hovered by remember(key) { derivedStateOf { hoveredKey.value == key } }
  val range = remember(rowIndex, line) { derivedStateOf { selection.selection?.rangeFor(rowIndex, line.text.length) } }
  val layoutState = remember(rowIndex) { mutableStateOf<TextLayoutResult?>(null) }
  val onTextLayout: (TextLayoutResult) -> Unit = remember(rowIndex, layouts) {
    { result ->
      layoutState.value = result
      layouts.register(rowIndex, result)
    }
  }
  DisposableEffect(rowIndex, layouts) {
    onDispose { layoutState.value?.let { layouts.unregister(rowIndex, it) } }
  }
  val tabWidth = typography.tabWidth
  val controlStyle = typography.controlMarker
  val selectionColor = colors.selectionBackground
  Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
    LineNumberCell(line.oldLineNo, numberBackground, typography, row, if (hovered) onAddComment else null)
    LineNumberCell(line.newLineNo, numberBackground, typography, null, null)
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
