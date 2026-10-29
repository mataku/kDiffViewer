package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import io.github.mataku.kdiffviewer.app.DiffLayout
import io.github.mataku.kdiffviewer.app.DiffMode
import io.github.mataku.kdiffviewer.app.DiffRow
import io.github.mataku.kdiffviewer.app.DisplayRows
import io.github.mataku.kdiffviewer.app.review.DraftTarget
import io.github.mataku.kdiffviewer.app.review.toReviewSide
import io.github.mataku.kdiffviewer.app.selection.ClickCounter
import io.github.mataku.kdiffviewer.app.selection.ContextMenuItem
import io.github.mataku.kdiffviewer.app.selection.DiffSelectionState
import io.github.mataku.kdiffviewer.app.selection.LocalNativeContextMenu
import io.github.mataku.kdiffviewer.app.selection.LocalTextSelectionCoordinator
import io.github.mataku.kdiffviewer.app.selection.NativeContextMenu
import io.github.mataku.kdiffviewer.app.selection.SelectionSource
import io.github.mataku.kdiffviewer.app.selection.TextPosition
import io.github.mataku.kdiffviewer.app.selection.TextSelectionCoordinator
import io.github.mataku.kdiffviewer.app.selection.nextSelectableRow
import io.github.mataku.kdiffviewer.app.selection.positionAtOrAfter
import io.github.mataku.kdiffviewer.app.selection.selectableRow
import io.github.mataku.kdiffviewer.app.selection.selectedText
import io.github.mataku.kdiffviewer.core.model.DiffContent
import io.github.mataku.kdiffviewer.core.model.FileDiff
import io.github.mataku.kdiffviewer.core.model.Side
import io.github.mataku.kdiffviewer.core.review.Anchor
import io.github.mataku.kdiffviewer.core.review.Placement
import io.github.mataku.kdiffviewer.core.review.RangeAnchor
import io.github.mataku.kdiffviewer.core.review.anchorOfRange
import io.github.mataku.kdiffviewer.core.review.locate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import io.github.mataku.kdiffviewer.core.review.Side as ReviewSide

private const val AUTO_SCROLL_SPEED = 0.5f
private const val MAX_AUTO_SCROLL_STEP = 40f

class VisibleLineLayouts {
  private val layouts = HashMap<Int, TextLayoutResult>()

  private fun keyOf(row: Int, side: Side?): Int = row * 2 + (if (side == Side.New) 1 else 0)

  fun register(row: Int, side: Side?, layout: TextLayoutResult) {
    layouts[keyOf(row, side)] = layout
  }

  fun unregister(row: Int, side: Side?, layout: TextLayoutResult) {
    val key = keyOf(row, side)
    if (layouts[key] === layout) layouts.remove(key)
  }

  operator fun get(row: Int, side: Side?): TextLayoutResult? = layouts[keyOf(row, side)]
}

class DiffSelectionSource(
  private val state: DiffSelectionState,
  private val rows: () -> List<DiffRow>,
  private val isVisible: (Int) -> Boolean,
) : SelectionSource {
  override val hasSelection: Boolean get() = state.selection?.isEmpty == false

  override fun selectedText(): String? = state.selection?.let { selectedText(rows(), it, isVisible) }

  override fun clear() = state.clear()
}

@Composable
internal fun Modifier.diffSelectionGesture(
  listState: LazyListState,
  selection: DiffSelectionState,
  selectionSource: SelectionSource,
  layouts: VisibleLineLayouts,
  rows: List<DiffRow>,
  layout: DiffLayout,
  display: DisplayRows,
  contentStartX: Float,
  codeStartX: Float,
  splitHalfWidth: () -> Float,
  tabWidth: Int,
  files: List<FileDiff>,
  markerWidth: Float,
  onRange: ((RangeAnchor) -> Unit)?,
  range: CommentRangeState,
  draftTarget: () -> DraftTarget?,
  loadId: Long,
): Modifier {
  val coordinator = LocalTextSelectionCoordinator.current
  val contextMenu = LocalNativeContextMenu.current
  val density = LocalDensity.current
  val focusManager = LocalFocusManager.current
  val gesture = remember(listState, selection, selectionSource, layouts) { DiffSelectionGesture(listState, selection, selectionSource, layouts) }
  SideEffect {
    gesture.rows = rows
    gesture.layout = layout
    gesture.display = display
    gesture.coordinator = coordinator
    gesture.contextMenu = contextMenu
    gesture.contentStartX = contentStartX
    gesture.codeStartX = codeStartX
    gesture.splitHalfWidth = splitHalfWidth
    gesture.tabWidth = tabWidth
    gesture.files = files
    gesture.markerWidth = markerWidth
    gesture.onRange = onRange
    gesture.range = range
    gesture.draftTarget = draftTarget
    gesture.loadId = loadId
    gesture.dropStaleRange()
    gesture.density = density
    gesture.focusManager = focusManager
  }
  return onPlaced { gesture.coordinates = it }.pointerInput(gesture) { gesture.run { handleGestures() } }
}

internal class DiffSelectionGesture(
  private val listState: LazyListState,
  private val selection: DiffSelectionState,
  private val source: SelectionSource,
  private val layouts: VisibleLineLayouts,
) {
  var rows: List<DiffRow> = emptyList()
  var layout: DiffLayout? = null
  var display: DisplayRows? = null
  var coordinator: TextSelectionCoordinator? = null
  var contextMenu: NativeContextMenu? = null
  var contentStartX = 0f
  var codeStartX = 0f
  var splitHalfWidth: () -> Float = { 0f }
  var tabWidth = 1
  var files: List<FileDiff> = emptyList()
  var markerWidth = 0f
  var onRange: ((RangeAnchor) -> Unit)? = null
  var range = CommentRangeState()
  var draftTarget: () -> DraftTarget? = { null }
  var loadId = 0L
  var density = Density(1f)
  var coordinates: LayoutCoordinates? = null
  var focusManager: FocusManager? = null

  private var pointer = Offset.Zero
  private var viewportHeight = 0
  private var moved = false
  private var side: Side? = null
  private var autoScroll: Job? = null
  private var rangeDrag: RangeDrag? = null

  private class RangeDrag(
    val fileIndex: Int,
    val hunkIndex: Int,
    val side: Side?,
    val pressRow: Int,
    val pressLine: Int,
    val lastLine: Int,
    val loadId: Long,
    val display: DisplayRows?,
  ) {
    var from = pressLine
    var to = pressLine
  }

  suspend fun PointerInputScope.handleGestures() = coroutineScope {
    val counter = ClickCounter(viewConfiguration.doubleTapTimeoutMillis, viewConfiguration.touchSlop)
    awaitEachGesture {
      val event = awaitDown()
      val down = event.changes[0]
      val buttons = event.buttons
      if (buttons.isSecondaryPressed || (buttons.isPrimaryPressed && event.keyboardModifiers.isCtrlPressed)) {
        showContextMenu(down.position)
        return@awaitEachGesture
      }
      if (!buttons.isPrimaryPressed) return@awaitEachGesture
      val shift = event.keyboardModifiers.isShiftPressed
      val markerSide = pressSide(down.position, false)
      val drag = rangeDragAt(down.position, markerSide)
      if (drag != null) {
        side = markerSide
        selection.clear()
        coordinator?.activate(source)
        focusManager?.clearFocus()
        val extendedSide = if (shift) extendDraft(drag) else null
        if (extendedSide != null) {
          finishRange(drag, extendedSide)
        } else {
          dragRange(down, drag, this@coroutineScope)
        }
        return@awaitEachGesture
      }
      side = pressSide(down.position, shift)
      val hit = hitTest(down.position, press = true) ?: return@awaitEachGesture
      coordinator?.activate(source)
      focusManager?.clearFocus()
      val clicks = counter.onPress(down.uptimeMillis, down.position)
      when {
        clicks == 2 -> selectWord(hit, down.position)
        clicks == 3 -> selectRow(hit.row)
        shift && selection.selection != null -> selection.extendTo(hit)
        else -> selection.start(hit, side)
      }
      moved = false
      viewportHeight = size.height
      try {
        while (true) {
          val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
          if (!change.pressed) break
          if (!change.positionChanged()) continue
          pointer = change.position
          if (pointer.y < 0f || pointer.y > viewportHeight) {
            if (autoScroll == null) autoScroll = this@coroutineScope.launch { autoScrollLoop() }
          } else {
            cancelAutoScroll()
          }
          extendTo(hitTest(clampedPointer(), press = false))
        }
      } finally {
        cancelAutoScroll()
      }
      val current = selection.selection
      if (clicks == 1 && !shift && !moved) {
        selection.clear()
      } else if (current != null && !current.isEmpty) {
        coordinator?.onSelectionFinished(source)
      }
    }
  }

  // Not awaitFirstDown: it drops mouse presses without the primary button, so secondary presses would never open the menu.
  private suspend fun AwaitPointerEventScope.awaitDown(): PointerEvent {
    while (true) {
      val event = awaitPointerEvent()
      if (event.type == PointerEventType.Press && event.changes.all { it.pressed && !it.previousPressed && !it.isConsumed }) return event
    }
  }

  private suspend fun autoScrollLoop() {
    while (true) {
      withFrameNanos { }
      val y = pointer.y
      val past = when {
        y < 0f -> y
        y > viewportHeight -> y - viewportHeight
        else -> continue
      }
      listState.scrollBy((past * AUTO_SCROLL_SPEED).coerceIn(-MAX_AUTO_SCROLL_STEP, MAX_AUTO_SCROLL_STEP))
      val drag = rangeDrag
      if (drag != null) updateRange(drag) else extendTo(hitTest(clampedPointer(), press = false))
    }
  }

  private fun cancelAutoScroll() {
    autoScroll?.cancel()
    autoScroll = null
  }

  private fun pressSide(position: Offset, shift: Boolean): Side? {
    if (display?.mode != DiffMode.Split) return null
    if (shift) selection.selection?.side?.let { return it }
    return if (position.x - contentStartX < splitHalfWidth()) Side.Old else Side.New
  }

  private suspend fun AwaitPointerEventScope.dragRange(down: PointerInputChange, drag: RangeDrag, scope: CoroutineScope) {
    viewportHeight = size.height
    pointer = down.position
    rangeDrag = drag
    publish(drag)
    try {
      while (true) {
        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: return
        if (!change.pressed) break
        if (!change.positionChanged()) continue
        pointer = change.position
        if (pointer.y < 0f || pointer.y > viewportHeight) {
          if (autoScroll == null) autoScroll = scope.launch { autoScrollLoop() }
        } else {
          cancelAutoScroll()
        }
        updateRange(drag)
      }
      if (!isStale(drag)) finishRange(drag)
    } finally {
      cancelAutoScroll()
      rangeDrag = null
      range.dragging = null
    }
  }

  fun dropStaleRange() {
    val drag = rangeDrag ?: return
    if (isStale(drag)) {
      range.dragging = null
      cancelAutoScroll()
    }
  }

  private fun isStale(drag: RangeDrag): Boolean = loadId != drag.loadId || display !== drag.display

  private fun publish(drag: RangeDrag) {
    range.dragging = CommentRange(drag.fileIndex, drag.hunkIndex, drag.side, drag.from, drag.to)
  }

  private fun updateRange(drag: RangeDrag) {
    if (isStale(drag)) {
      range.dragging = null
      return
    }
    val to = resolveTo(drag)
    if (to == drag.to) return
    drag.to = to
    publish(drag)
  }

  private fun resolveTo(drag: RangeDrag): Int {
    val position = clampedPointer()
    val item = itemAt(position.y, preferLast = false)
    if (item == null) {
      val first = listState.layoutInfo.visibleItemsInfo.firstOrNull() ?: return drag.to
      return if (position.y < first.offset) 0 else drag.lastLine
    }
    val layout = layout ?: return drag.to
    val displayRow = layout.rowIndexOf(item.index)
    val row = display?.unifiedOf(displayRow, drag.side) ?: displayRow
    if (row < 0) return drag.to
    val hit = rows.getOrNull(row)
    if (hit is DiffRow.Line && hit.fileIndex == drag.fileIndex && hit.hunkIndex == drag.hunkIndex) return hit.lineIndex
    return if (row < drag.pressRow) 0 else drag.lastLine
  }

  private fun finishRange(drag: RangeDrag, side: ReviewSide? = drag.side?.toReviewSide()) {
    val file = files.getOrNull(drag.fileIndex) ?: return
    val anchor = anchorOfRange(file, drag.fileIndex, drag.hunkIndex, drag.from, drag.to, side) ?: return
    onRange?.invoke(anchor)
  }

  private fun extendDraft(drag: RangeDrag): ReviewSide? {
    val target = draftTarget() as? DraftTarget.NewThread ?: return null
    val anchor = target.anchor as? Anchor.Lines ?: return null
    val location = (target.placement as? Placement.OnLine)?.location ?: return null
    if (location.fileIndex != drag.fileIndex) return null
    val file = files.getOrNull(drag.fileIndex) ?: return null
    val last = file.locate(anchor.side, anchor.endLine) ?: return null
    if (last.hunkIndex != drag.hunkIndex) return null
    if (drag.side != null) {
      if (drag.side.toReviewSide() != anchor.side) return null
    } else if (anchorOfRange(file, drag.fileIndex, drag.hunkIndex, drag.pressLine, drag.pressLine, anchor.side) == null) {
      return null
    }
    drag.from = draftRange(target, files, display?.mode == DiffMode.Split)?.fromLineIndex ?: last.lineIndex
    return anchor.side
  }

  private fun rangeDragAt(position: Offset, side: Side?): RangeDrag? {
    if (onRange == null) return null
    val codeStart = codeStartFor(side)
    if (position.x < codeStart - markerWidth || position.x >= codeStart) return null
    val layout = layout ?: return null
    val item = itemAt(position.y, preferLast = false) ?: return null
    val displayRow = layout.rowIndexOf(item.index)
    val row = display?.unifiedOf(displayRow, side) ?: displayRow
    if (row < 0) return null
    val line = lineAt(row, position.y - item.offset, side) ?: return null
    val file = files.getOrNull(line.fileIndex) ?: return null
    if (anchorOfRange(file, line.fileIndex, line.hunkIndex, line.lineIndex, line.lineIndex, side?.toReviewSide()) == null) return null
    val lines = (file.content as? DiffContent.Text)?.hunks?.getOrNull(line.hunkIndex)?.lines ?: return null
    return RangeDrag(line.fileIndex, line.hunkIndex, side, row, line.lineIndex, lines.lastIndex, loadId, display)
  }

  private fun codeStartFor(side: Side?): Float = if (side == Side.New) codeStartX + splitHalfWidth() else codeStartX

  private fun clampedPointer(): Offset = Offset(pointer.x, pointer.y.coerceIn(0f, (viewportHeight - 1).coerceAtLeast(0).toFloat()))

  private fun extendTo(position: TextPosition?) {
    if (position == null) return
    val before = selection.selection?.focus
    selection.extendTo(position)
    if (selection.selection?.focus != before) moved = true
  }

  private fun itemAt(y: Float, preferLast: Boolean): LazyListItemInfo? {
    var found: LazyListItemInfo? = null
    for (item in listState.layoutInfo.visibleItemsInfo) {
      if (y >= item.offset && y < item.offset + item.size) {
        found = item
        if (!preferLast) break
      }
    }
    return found
  }

  private fun hitTest(position: Offset, press: Boolean): TextPosition? {
    val rows = rows
    val layout = layout ?: return null
    val item = itemAt(position.y, preferLast = !press)
    if (item == null) {
      if (press) return null
      val first = listState.layoutInfo.visibleItemsInfo.firstOrNull() ?: return null
      return if (position.y < first.offset) rows.positionAtOrAfter(0, side) else rows.positionAtOrAfter(rows.size, side)
    }
    val side = side
    val displayRow = layout.rowIndexOf(item.index)
    val row = display?.unifiedOf(displayRow, side) ?: displayRow
    if (row < 0) {
      if (press) return null
      return rows.positionAtOrAfter(nextUnifiedRow(displayRow, side), side)
    }
    val localY = position.y - item.offset
    val line = lineAt(row, localY, side)
    if (line == null) {
      if (press) return null
      return rows.positionAtOrAfter(if (rows.selectableRow(row, side)) row + 1 else row, side)
    }
    val codeStart = codeStartFor(side)
    val text = layouts[row, side]
    if (text == null || position.x < codeStart) return TextPosition(row, 0)
    val display = text.getOffsetForPosition(Offset(position.x - codeStart, localY))
    return TextPosition(row, displayToSource(line.line.text, display, tabWidth))
  }

  private fun nextUnifiedRow(displayRow: Int, side: Side?): Int {
    val display = display ?: return displayRow + 1
    val end = display.displayOf(rows.size)
    for (next in displayRow + 1..end) {
      val row = display.unifiedOf(next, side)
      if (row >= 0) return row
    }
    return rows.size
  }

  private fun lineAt(row: Int, localY: Float, side: Side?): DiffRow.Line? {
    if (!rows.selectableRow(row, side)) return null
    val text = layouts[row, side]
    if (text != null && localY >= text.size.height) return null
    return rows[row] as DiffRow.Line
  }

  private fun selectWord(hit: TextPosition, position: Offset) {
    val item = itemAt(position.y, preferLast = false)
    val side = side
    val codeStart = codeStartFor(side)
    val text = layouts[hit.row, side]
    if (item == null || text == null || position.x < codeStart) {
      selection.start(hit, side)
      return
    }
    val source = (rows[hit.row] as DiffRow.Line).line.text
    val word = text.getWordBoundary(text.getOffsetForPosition(Offset(position.x - codeStart, position.y - item.offset)))
    selection.select(
      TextPosition(hit.row, displayToSource(source, word.start, tabWidth)),
      TextPosition(hit.row, displayToSource(source, word.end, tabWidth)),
      side,
    )
  }

  private fun selectRow(row: Int) {
    val next = rows.nextSelectableRow(row, side)
    val focus = if (next != null) TextPosition(next, 0) else TextPosition(row, (rows[row] as DiffRow.Line).line.text.length)
    selection.select(TextPosition(row, 0), focus, side)
  }

  private fun showContextMenu(position: Offset) {
    val menu = contextMenu ?: return
    val root = coordinates?.takeIf { it.isAttached }?.localToRoot(position) ?: position
    val at = with(density) { DpOffset(root.x.toDp(), root.y.toDp()) }
    menu.show(at, listOf(ContextMenuItem("Copy", coordinator?.hasSelection == true, shortcut = 'C') { coordinator?.copy() }))
  }
}
