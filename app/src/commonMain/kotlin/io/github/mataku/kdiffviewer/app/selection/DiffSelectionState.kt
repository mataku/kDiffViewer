package io.github.mataku.kdiffviewer.app.selection

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

@Stable
class DiffSelectionState {
  var selection: DiffSelection? by mutableStateOf(null)
    private set

  fun start(position: TextPosition) {
    selection = DiffSelection(position, position)
  }

  fun extendTo(position: TextPosition) {
    val current = selection
    if (current == null) {
      start(position)
    } else if (current.focus != position) {
      selection = current.copy(focus = position)
    }
  }

  fun select(anchor: TextPosition, focus: TextPosition) {
    selection = DiffSelection(anchor, focus)
  }

  private var key: Any? = null

  fun resetFor(key: Any) {
    if (this.key == key) return
    this.key = key
    clear()
  }

  fun clear() {
    if (selection != null) selection = null
  }
}
