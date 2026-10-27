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
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import io.github.mataku.kdiffviewer.app.DiffLayout
import io.github.mataku.kdiffviewer.app.DiffRow
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private const val AUTO_SCROLL_SPEED = 0.5f
private const val MAX_AUTO_SCROLL_STEP = 40f

class VisibleLineLayouts {
  private val layouts = HashMap<Int, TextLayoutResult>()

  fun register(row: Int, layout: TextLayoutResult) {
    layouts[row] = layout
  }

  fun unregister(row: Int, layout: TextLayoutResult) {
    if (layouts[row] === layout) layouts.remove(row)
  }

  operator fun get(row: Int): TextLayoutResult? = layouts[row]
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
  codeStartX: Float,
  tabWidth: Int,
): Modifier {
  val coordinator = LocalTextSelectionCoordinator.current
  val contextMenu = LocalNativeContextMenu.current
  val density = LocalDensity.current
  val focusManager = LocalFocusManager.current
  val gesture = remember(listState, selection, selectionSource, layouts) { DiffSelectionGesture(listState, selection, selectionSource, layouts) }
  SideEffect {
    gesture.rows = rows
    gesture.layout = layout
    gesture.coordinator = coordinator
    gesture.contextMenu = contextMenu
    gesture.codeStartX = codeStartX
    gesture.tabWidth = tabWidth
    gesture.density = density
    gesture.focusManager = focusManager
  }
  return onGloballyPositioned { gesture.coordinates = it }.pointerInput(gesture) { gesture.run { handleGestures() } }
}

internal class DiffSelectionGesture(
  private val listState: LazyListState,
  private val selection: DiffSelectionState,
  private val source: SelectionSource,
  private val layouts: VisibleLineLayouts,
) {
  var rows: List<DiffRow> = emptyList()
  var layout: DiffLayout? = null
  var coordinator: TextSelectionCoordinator? = null
  var contextMenu: NativeContextMenu? = null
  var codeStartX = 0f
  var tabWidth = 1
  var density = Density(1f)
  var coordinates: LayoutCoordinates? = null
  var focusManager: FocusManager? = null

  private var pointer = Offset.Zero
  private var viewportHeight = 0
  private var moved = false
  private var autoScroll: Job? = null

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
      val hit = hitTest(down.position, press = true) ?: return@awaitEachGesture
      coordinator?.activate(source)
      focusManager?.clearFocus()
      val shift = event.keyboardModifiers.isShiftPressed
      val clicks = counter.onPress(down.uptimeMillis, down.position)
      when {
        clicks == 2 -> selectWord(hit, down.position)
        clicks == 3 -> selectRow(hit.row)
        shift -> selection.extendTo(hit)
        else -> selection.start(hit)
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
      extendTo(hitTest(clampedPointer(), press = false))
    }
  }

  private fun cancelAutoScroll() {
    autoScroll?.cancel()
    autoScroll = null
  }

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
      return if (position.y < first.offset) rows.positionAtOrAfter(0) else rows.positionAtOrAfter(rows.size)
    }
    val row = layout.rowIndexOf(item.index)
    val localY = position.y - item.offset
    val line = lineAt(row, localY)
    if (line == null) {
      if (press) return null
      return rows.positionAtOrAfter(if (rows.selectableRow(row)) row + 1 else row)
    }
    val text = layouts[row]
    if (text == null || position.x < codeStartX) return TextPosition(row, 0)
    val display = text.getOffsetForPosition(Offset(position.x - codeStartX, localY))
    return TextPosition(row, displayToSource(line.line.text, display, tabWidth))
  }

  private fun lineAt(row: Int, localY: Float): DiffRow.Line? {
    if (!rows.selectableRow(row)) return null
    val text = layouts[row]
    if (text != null && localY >= text.size.height) return null
    return rows[row] as DiffRow.Line
  }

  private fun selectWord(hit: TextPosition, position: Offset) {
    val item = itemAt(position.y, preferLast = false)
    val text = layouts[hit.row]
    if (item == null || text == null || position.x < codeStartX) {
      selection.start(hit)
      return
    }
    val source = (rows[hit.row] as DiffRow.Line).line.text
    val word = text.getWordBoundary(text.getOffsetForPosition(Offset(position.x - codeStartX, position.y - item.offset)))
    selection.select(
      TextPosition(hit.row, displayToSource(source, word.start, tabWidth)),
      TextPosition(hit.row, displayToSource(source, word.end, tabWidth)),
    )
  }

  private fun selectRow(row: Int) {
    val next = rows.nextSelectableRow(row)
    val focus = if (next != null) TextPosition(next, 0) else TextPosition(row, (rows[row] as DiffRow.Line).line.text.length)
    selection.select(TextPosition(row, 0), focus)
  }

  private fun showContextMenu(position: Offset) {
    val menu = contextMenu ?: return
    val root = coordinates?.takeIf { it.isAttached }?.localToRoot(position) ?: position
    val at = with(density) { DpOffset(root.x.toDp(), root.y.toDp()) }
    menu.show(at, listOf(ContextMenuItem("Copy", coordinator?.hasSelection == true, shortcut = 'C') { coordinator?.copy() }))
  }
}
