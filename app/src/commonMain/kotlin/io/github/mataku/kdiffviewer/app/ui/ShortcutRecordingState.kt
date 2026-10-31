package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

/** Which shortcut row is recording, and why the last key pressed there was rejected. */
@Stable
class ShortcutRecordingState(private val keymap: Keymap) {
  var recording: ShortcutId? by mutableStateOf(null)
    private set

  var rejection: ShortcutProblem? by mutableStateOf(null)
    private set

  fun startRecording(id: ShortcutId) {
    recording = id
    rejection = null
  }

  fun cancel() {
    recording = null
    rejection = null
  }

  /**
   * Handles a key while a row is recording: a chord replaces the shortcut, plain Delete or Backspace removes it, and Esc
   * cancels. Rejected chords keep recording so another one can be tried.
   */
  fun onKey(event: KeyEvent, shortcuts: KeyboardShortcuts, onCommit: (ShortcutId, Chord?) -> Unit): Boolean {
    val id = recording ?: return false
    if (event.type != KeyEventType.KeyDown) return true
    when {
      event.key == Key.Escape && !event.hasModifiers() -> cancel()

      event.key in ClearKeys && !event.hasModifiers() -> {
        cancel()
        onCommit(id, null)
      }

      event.key in ModifierKeys -> Unit

      else -> {
        val chord = keymap.chordOf(event)
        val problem = if (chord == null) ShortcutProblem.UnsupportedKey else shortcuts.problemFor(id, chord)
        if (problem != null || chord == null) {
          rejection = problem
        } else {
          cancel()
          onCommit(id, chord)
        }
      }
    }
    return true
  }

  private fun KeyEvent.hasModifiers(): Boolean = isCtrlPressed || isMetaPressed || isShiftPressed || isAltPressed

  private companion object {
    val ClearKeys = setOf(Key.Backspace, Key.Delete)
    val ModifierKeys = setOf(
      Key.MetaLeft, Key.MetaRight, Key.CtrlLeft, Key.CtrlRight, Key.ShiftLeft, Key.ShiftRight, Key.AltLeft, Key.AltRight,
      Key.CapsLock, Key.Function,
    )
  }
}

@Composable
fun rememberShortcutRecordingState(keymap: Keymap = HostKeymap): ShortcutRecordingState = remember(keymap) { ShortcutRecordingState(keymap) }
