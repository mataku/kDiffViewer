package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed

enum class Keymap { Mac, Standard }

expect val HostKeymap: Keymap

fun KeyEvent.isPrimaryModifierPressed(keymap: Keymap = HostKeymap): Boolean = when (keymap) {
  Keymap.Mac -> isMetaPressed
  Keymap.Standard -> isCtrlPressed
}

class Chord(
  val key: Key,
  val label: String,
  val ctrl: Boolean = false,
  val meta: Boolean = false,
  val shift: Boolean = false,
)

class AppShortcuts(keymap: Keymap) {
  private val mac = keymap == Keymap.Mac

  val open = primary(Key.O, "O")
  val settings = primary(Key.Comma, ",")
  val copy = primary(Key.C, "C")
  val find = primary(Key.F, "F")
  val findNext = primary(Key.G, "G")
  val findPrevious = primary(Key.G, "G", shift = true)

  // ⌃⌘S has no Ctrl-based equivalent once Ctrl is the primary modifier; F9 is the GNOME Files sidebar key.
  val toggleSidebar = if (mac) chord(Key.S, "S", ctrl = true, meta = true) else chord(Key.F9, "F9")
  val reload = primary(Key.R, "R")

  private fun primary(key: Key, keyLabel: String, shift: Boolean = false): Chord = chord(key, keyLabel, ctrl = !mac, meta = mac, shift = shift)

  private fun chord(key: Key, keyLabel: String, ctrl: Boolean = false, meta: Boolean = false, shift: Boolean = false): Chord {
    val label = if (mac) {
      buildString {
        if (ctrl) append('⌃')
        if (shift) append('⇧')
        if (meta) append('⌘')
        append(keyLabel)
      }
    } else {
      listOfNotNull("Ctrl".takeIf { ctrl }, "Shift".takeIf { shift }, keyLabel).joinToString("+")
    }
    return Chord(key, label, ctrl = ctrl, meta = meta, shift = shift)
  }

  companion object {
    val Host = AppShortcuts(HostKeymap)
  }
}
