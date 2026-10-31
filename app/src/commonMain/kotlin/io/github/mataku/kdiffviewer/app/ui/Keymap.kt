package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

enum class Keymap { Mac, Standard }

expect val HostKeymap: Keymap

@Immutable
class Chord(
  val key: Key,
  val label: String,
  val ctrl: Boolean = false,
  val meta: Boolean = false,
  val shift: Boolean = false,
) {
  fun matches(event: KeyEvent): Boolean = event.type == KeyEventType.KeyDown &&
    event.key.withoutNumPad() == key &&
    event.isCtrlPressed == ctrl &&
    event.isMetaPressed == meta &&
    event.isShiftPressed == shift &&
    !event.isAltPressed

  private fun Key.withoutNumPad(): Key = if (this == Key.NumPadEnter) Key.Enter else this
}

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

  // ⌘↑/⌘↓ is the macOS idiom; Ctrl+Home/End also works there for keyboards that have those keys.
  val scrollToTop: List<Chord> = listOfNotNull(
    chord(Key.DirectionUp, "↑", meta = true).takeIf { mac },
    chord(Key.MoveHome, "Home", ctrl = true),
  )
  val scrollToBottom: List<Chord> = listOfNotNull(
    chord(Key.DirectionDown, "↓", meta = true).takeIf { mac },
    chord(Key.MoveEnd, "End", ctrl = true),
  )

  private val enterLabel = if (mac) "↩" else "Enter"
  val submitComment = primary(Key.Enter, enterLabel)
  val addSingleComment = primary(Key.Enter, enterLabel, shift = true)
  val togglePreview = primary(Key.P, "P", shift = true)

  /** App-specific shortcuts for the Settings list; OS-standard ones (⌘C, ⌘F, Esc…) are left out. */
  val reference: List<ShortcutGroup> by lazy {
    listOf(
      ShortcutGroup(
        "Viewer",
        listOf(
          ShortcutEntry("Reload", listOf(reload)),
          ShortcutEntry("Show or Hide Sidebar", listOf(toggleSidebar)),
          ShortcutEntry("Scroll to Top", scrollToTop),
          ShortcutEntry("Scroll to Bottom", scrollToBottom),
        ),
      ),
      ShortcutGroup(
        "Comments",
        listOf(
          ShortcutEntry("Add Review Comment or Save", listOf(submitComment)),
          ShortcutEntry("Add Single Comment", listOf(addSingleComment)),
          ShortcutEntry("Switch Write and Preview", listOf(togglePreview)),
        ),
      ),
    )
  }

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

@Immutable
class ShortcutEntry(val action: String, val chords: List<Chord>) {
  val keysLabel: String = chords.joinToString(" or ") { it.label }
}

@Immutable
class ShortcutGroup(val title: String, val entries: List<ShortcutEntry>)
