package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import io.github.mataku.compose.highlight.core.LocalSyntaxTheme
import io.github.mataku.kdiffviewer.app.DiffLayout
import io.github.mataku.kdiffviewer.app.DiffRow
import io.github.mataku.kdiffviewer.app.LineSelection
import io.github.mataku.kdiffviewer.app.highlight.HighlightSlot
import io.github.mataku.kdiffviewer.app.review.DraftTarget
import io.github.mataku.kdiffviewer.app.review.ReviewPlacements
import io.github.mataku.kdiffviewer.app.review.isOrphanThread
import io.github.mataku.kdiffviewer.app.review.linePosition
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
import kotlinx.coroutines.launch

private val FileHeaderTopMargin = 12.dp
private val FileChevronSize = 12.dp
private val FileCommentIconSize = 14.dp
private val AddCommentSize = 16.dp

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
  selection: State<LineSelection?>,
  onLineNumberClick: (rowIndex: Int, extend: Boolean) -> Unit,
  onClearSelection: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val currentOnHeaderClick by rememberUpdatedState(onHeaderClick)
  val actions = LocalReviewActions.current
  val typography = LocalDiffTypography.current
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
  val density = LocalDensity.current
  val numberStart = with(density) { ContentHorizontalMargin.toPx() }
  val numberEnd = numberStart + with(density) { (typography.lineNumberWidth * 2).toPx() }
  val rowAtPointer: (Offset) -> Int = { position -> lineNumberRowAt(listState, layout, rows, position, numberStart, numberEnd) }
  val overLineNumber = remember { mutableStateOf(false) }
  val listFocused = remember { mutableStateOf(false) }
  val focusRequester = remember { FocusRequester() }
  val clipboard = LocalClipboard.current
  val coroutineScope = rememberCoroutineScope()
  val onHover: (Offset) -> Unit = { position ->
    if (onAddComment != null) hoveredKey.value = keyAt(listState, position.y)
    overLineNumber.value = rowAtPointer(position) >= 0
  }
  LineNumberCursor(overLineNumber, modifier) {
    LazyColumn(
      state = listState,
      modifier = Modifier
        .testTag("diff-list")
        .onPointerEvent(PointerEventType.Enter) { event -> onHover(event.changes.first().position) }
        .onPointerEvent(PointerEventType.Move) { event -> onHover(event.changes.first().position) }
        .onPointerEvent(PointerEventType.Exit) {
          hoveredKey.value = null
          overLineNumber.value = false
        }
        .onPointerEvent(PointerEventType.Press) { event ->
          val change = event.changes.first()
          if (change.isConsumed || !event.buttons.isPrimaryPressed) return@onPointerEvent
          val rowIndex = rowAtPointer(change.position)
          if (rowIndex < 0) return@onPointerEvent
          focusRequester.requestFocus()
          onLineNumberClick(rowIndex, event.keyboardModifiers.isShiftPressed)
        }
        .onFocusChanged { listFocused.value = it.isFocused }
        .onPreviewKeyEvent { event ->
          if (!listFocused.value || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
          when {
            event.key == Key.Escape && selection.value != null -> {
              onClearSelection()
              true
            }

            event.key == Key.C && event.isMetaPressed -> {
              val text = selection.value?.copiedText(rows) ?: return@onPreviewKeyEvent false
              coroutineScope.launch { clipboard.setClipEntry(textClipEntry(text)) }
              true
            }

            else -> false
          }
        }
        .focusRequester(focusRequester)
        .focusable(),
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
                  LineRow(row, bodyStart + offset, slot, hoveredKey, selection, onAddComment)
                } else {
                  Column {
                    LineRow(row, bodyStart + offset, slot, hoveredKey, selection, onAddComment)
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
}

private fun keyAt(listState: LazyListState, y: Float): String? = listState.layoutInfo.visibleItemsInfo.firstOrNull { y >= it.offset && y < it.offset + it.size }?.key as? String

private fun lineNumberRowAt(listState: LazyListState, layout: DiffLayout, rows: List<DiffRow>, position: Offset, numberStart: Float, numberEnd: Float): Int {
  if (position.x < numberStart || position.x >= numberEnd) return -1
  var visibleIndex = -1
  for (item in listState.layoutInfo.visibleItemsInfo) {
    if (position.y < item.offset || position.y >= item.offset + item.size) continue
    if (item.contentType != LINE_CONTENT_TYPE) return -1
    visibleIndex = item.index
  }
  if (visibleIndex < 0) return -1
  val rowIndex = layout.rowIndexOf(visibleIndex)
  val row = rows.getOrNull(rowIndex) as? DiffRow.Line ?: return -1
  return if (row.line.kind == LineKind.NoNewlineAtEof) -1 else rowIndex
}

@Composable
private fun LineNumberCursor(overLineNumber: State<Boolean>, modifier: Modifier, content: @Composable () -> Unit) {
  Box(modifier = modifier.pointerHoverIcon(if (overLineNumber.value) PointerIcon.Hand else PointerIcon.Default)) {
    content()
  }
}

private fun FileDiff.headerTitle(): String = when {
  change == ChangeType.Renamed && oldPath != null && newPath != null -> "$oldPath → $newPath"
  else -> newPath ?: oldPath.orEmpty()
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
        text = row.file.headerTitle(),
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
  BasicText(
    text = remember(row.hunk, typography.tabWidth) { expandTabs(hunkHeaderLabel(row.hunk), typography.tabWidth) },
    modifier = Modifier
      .fillMaxWidth()
      .background(colors.hunkBackground)
      .padding(horizontal = 12.dp, vertical = 2.dp),
    style = typography.hunkHeader,
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
private fun LineRow(
  row: DiffRow.Line,
  rowIndex: Int,
  slot: HighlightSlot?,
  hoveredKey: State<String?>,
  selection: State<LineSelection?>,
  onAddComment: ((DiffRow.Line) -> Unit)?,
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
  val key = row.key
  val flags by remember(key, rowIndex, hoveredKey, selection) {
    derivedStateOf {
      (if (hoveredKey.value == key) HOVERED_FLAG else 0) or (if (selection.value?.contains(rowIndex) == true) SELECTED_FLAG else 0)
    }
  }
  val hovered = flags and HOVERED_FLAG != 0
  val (contentBackground, numberBackground) = when {
    flags and SELECTED_FLAG != 0 -> colors.selectedBackground to colors.selectedLineNumberBackground
    line.kind == LineKind.Added -> colors.addedBackground to colors.addedLineNumberBackground
    line.kind == LineKind.Removed -> colors.removedBackground to colors.removedLineNumberBackground
    else -> colors.background to colors.background
  }
  val marker = when (line.kind) {
    LineKind.Added -> "+"
    LineKind.Removed -> "-"
    else -> " "
  }
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
    val contentModifier = Modifier.weight(1f).fillMaxHeight().background(contentBackground)
    val spans = slot?.value?.spans?.getOrNull(row.hunkIndex)?.getOrNull(row.lineIndex)
    if (spans != null) {
      val theme = LocalSyntaxTheme.current
      val text = remember(line, spans, theme, typography.tabWidth) { displayText(line.text, spans, theme, typography.tabWidth) }
      BasicText(text = text, modifier = contentModifier, style = typography.code)
    } else {
      val text = remember(line.text, typography.tabWidth) { expandTabs(line.text, typography.tabWidth) }
      BasicText(text = text, modifier = contentModifier, style = typography.code)
    }
  }
}

private const val HOVERED_FLAG = 1
private const val SELECTED_FLAG = 2

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
