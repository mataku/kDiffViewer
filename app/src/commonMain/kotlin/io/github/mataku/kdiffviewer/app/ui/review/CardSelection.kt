package io.github.mataku.kdiffviewer.app.ui.review

import androidx.compose.foundation.ContextMenuState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.contextMenuOpenDetector
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.TextContextMenu
import androidx.compose.foundation.text.selection.SelectionState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import io.github.mataku.kdiffviewer.app.selection.ContextMenuItem
import io.github.mataku.kdiffviewer.app.selection.NativeContextMenu
import io.github.mataku.kdiffviewer.app.selection.SelectionSource

class CardSelectionSource(val state: SelectionState) : SelectionSource {
  override val hasSelection: Boolean get() = state.selectedTexts.any { it.isNotEmpty() }

  override fun selectedText(): String? = state.selectedTexts.joinToString("\n") { it.text }.ifEmpty { null }

  override fun clear() = state.clear()
}

@OptIn(ExperimentalFoundationApi::class)
class NativeTextContextMenu(
  private val menu: NativeContextMenu,
  private val density: Density,
) : TextContextMenu {
  @Composable
  override fun Area(textManager: TextContextMenu.TextManager, state: ContextMenuState, content: @Composable () -> Unit) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(
      Modifier
        .fillMaxWidth()
        .onGloballyPositioned { origin = it.positionInWindow() }
        .contextMenuOpenDetector(state) { state.status = ContextMenuState.Status.Open(Rect(it, 0f)) },
    ) {
      content()
    }
    val status = state.status
    if (status is ContextMenuState.Status.Open) {
      LaunchedEffect(status) {
        val copy = textManager.copy
        val position = origin + status.rect.topLeft
        val windowPosition = with(density) { DpOffset(position.x.toDp(), position.y.toDp()) }
        state.status = ContextMenuState.Status.Closed
        menu.show(windowPosition, listOf(ContextMenuItem("Copy", enabled = copy?.enabled == true) { copy?.execute?.invoke() }))
      }
    }
  }
}
