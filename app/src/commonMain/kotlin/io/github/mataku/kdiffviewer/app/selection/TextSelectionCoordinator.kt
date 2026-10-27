package io.github.mataku.kdiffviewer.app.selection

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

interface SelectionSource {
  val hasSelection: Boolean
  fun selectedText(): String?
  fun clear()
}

fun interface ClipboardWriter {
  fun write(text: String)
}

@Stable
class TextSelectionCoordinator(
  private val clipboard: ClipboardWriter,
  private val copyOnSelect: () -> Boolean,
) {
  private var active: SelectionSource? by mutableStateOf(null)

  val hasSelection: Boolean get() = active?.hasSelection == true

  fun activate(source: SelectionSource) {
    val previous = active
    if (previous === source) return
    active = source
    previous?.clear()
  }

  fun release(source: SelectionSource) {
    if (active === source) active = null
  }

  fun copy(): Boolean {
    val text = active?.selectedText()
    if (text.isNullOrEmpty()) return false
    clipboard.write(text)
    return true
  }

  fun onSelectionFinished(source: SelectionSource) {
    if (active === source && copyOnSelect()) copy()
  }
}

val LocalTextSelectionCoordinator = staticCompositionLocalOf<TextSelectionCoordinator?> { null }
