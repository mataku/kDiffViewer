package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

/** [settingsKey] names the per-platform block in settings.json. */
enum class Keymap(val settingsKey: String) { Mac("macos"), Standard("linux") }

expect val HostKeymap: Keymap

/** A key plus exact modifiers. Equality ignores [label], which only depends on the keymap. */
@Immutable
class Chord(
  val key: Key,
  val label: String,
  val ctrl: Boolean = false,
  val meta: Boolean = false,
  val shift: Boolean = false,
  val alt: Boolean = false,
) {
  fun matches(event: KeyEvent): Boolean = event.type == KeyEventType.KeyDown &&
    event.key.withoutNumPad() == key &&
    event.isCtrlPressed == ctrl &&
    event.isMetaPressed == meta &&
    event.isShiftPressed == shift &&
    event.isAltPressed == alt

  override fun equals(other: Any?): Boolean = other is Chord &&
    key == other.key &&
    ctrl == other.ctrl &&
    meta == other.meta &&
    shift == other.shift &&
    alt == other.alt

  override fun hashCode(): Int = listOf(key, ctrl, meta, shift, alt).hashCode()

  override fun toString(): String = label
}

fun Chord?.matches(event: KeyEvent): Boolean = this != null && matches(event)

internal fun Key.withoutNumPad(): Key = if (this == Key.NumPadEnter) Key.Enter else this

fun Keymap.chord(key: Key, ctrl: Boolean = false, meta: Boolean = false, shift: Boolean = false, alt: Boolean = false): Chord {
  val keyLabel = KeyNames.label(key, this)
  val label = when (this) {
    Keymap.Mac -> buildString {
      if (ctrl) append('⌃')
      if (alt) append('⌥')
      if (shift) append('⇧')
      if (meta) append('⌘')
      append(keyLabel)
    }

    Keymap.Standard -> listOfNotNull("Ctrl".takeIf { ctrl }, "Alt".takeIf { alt }, "Shift".takeIf { shift }, "Meta".takeIf { meta }, keyLabel).joinToString("+")
  }
  return Chord(key, label, ctrl = ctrl, meta = meta, shift = shift, alt = alt)
}

/** Builds the chord an event represents, or null when its key has no name in [KeyNames]. */
fun Keymap.chordOf(event: KeyEvent): Chord? {
  val key = event.key.withoutNumPad()
  if (!KeyNames.supports(key)) return null
  return chord(key, ctrl = event.isCtrlPressed, meta = event.isMetaPressed, shift = event.isShiftPressed, alt = event.isAltPressed)
}

enum class ShortcutCategory(val title: String) { Viewer("Viewer"), Comments("Comments") }

/** Customizable shortcuts. [settingsKey] is the name used in settings.json and must stay stable. */
enum class ShortcutId(val settingsKey: String, val title: String, val category: ShortcutCategory) {
  Reload("reload", "Reload", ShortcutCategory.Viewer),
  ToggleSidebar("toggleSidebar", "Show or Hide Sidebar", ShortcutCategory.Viewer),
  ScrollToTop("scrollToTop", "Scroll to Top", ShortcutCategory.Viewer),
  ScrollToBottom("scrollToBottom", "Scroll to Bottom", ShortcutCategory.Viewer),
  SubmitComment("submitComment", "Add Review Comment or Save", ShortcutCategory.Comments),
  AddSingleComment("addSingleComment", "Add Single Comment", ShortcutCategory.Comments),
  TogglePreview("togglePreview", "Switch Write and Preview", ShortcutCategory.Comments),
  ;

  companion object {
    private val bySettingsKey = entries.associateBy { it.settingsKey }
    fun fromSettingsKey(key: String): ShortcutId? = bySettingsKey[key]
  }
}

/** Overrides map an id to its new chord, or to null when the user removed the shortcut. */
@Immutable
class KeyboardShortcuts(val keymap: Keymap, overrides: Map<ShortcutId, Chord?> = emptyMap()) {
  private val mac = keymap == Keymap.Mac

  val open = primary(Key.O)
  val settings = primary(Key.Comma)
  val copy = primary(Key.C)
  val find = primary(Key.F)
  val findNext = primary(Key.G)
  val findPrevious = primary(Key.G, shift = true)

  /** Menu shortcuts that follow OS conventions and cannot be changed. */
  val fixed: List<Chord> = listOf(open, settings, copy, find, findNext, findPrevious)

  val defaults: Map<ShortcutId, Chord> = ShortcutId.entries.associateWith(::defaultChord)
  val bindings: Map<ShortcutId, Chord?> = defaults + overrides

  operator fun get(id: ShortcutId): Chord? = bindings[id]

  val toggleSidebar: Chord? get() = this[ShortcutId.ToggleSidebar]
  val reload: Chord? get() = this[ShortcutId.Reload]
  val scrollToTop: Chord? get() = this[ShortcutId.ScrollToTop]
  val scrollToBottom: Chord? get() = this[ShortcutId.ScrollToBottom]
  val submitComment: Chord? get() = this[ShortcutId.SubmitComment]
  val addSingleComment: Chord? get() = this[ShortcutId.AddSingleComment]
  val togglePreview: Chord? get() = this[ShortcutId.TogglePreview]

  private fun defaultChord(id: ShortcutId): Chord = when (id) {
    ShortcutId.Reload -> primary(Key.R)

    // ⌃⌘S has no Ctrl-based equivalent once Ctrl is the primary modifier; F9 is the GNOME Files sidebar key.
    ShortcutId.ToggleSidebar -> if (mac) keymap.chord(Key.S, ctrl = true, meta = true) else keymap.chord(Key.F9)

    ShortcutId.ScrollToTop -> if (mac) keymap.chord(Key.DirectionUp, meta = true) else keymap.chord(Key.MoveHome, ctrl = true)

    ShortcutId.ScrollToBottom -> if (mac) keymap.chord(Key.DirectionDown, meta = true) else keymap.chord(Key.MoveEnd, ctrl = true)

    ShortcutId.SubmitComment -> primary(Key.Enter)

    ShortcutId.AddSingleComment -> primary(Key.Enter, shift = true)

    ShortcutId.TogglePreview -> primary(Key.P, shift = true)
  }

  private fun primary(key: Key, shift: Boolean = false): Chord = keymap.chord(key, ctrl = !mac, meta = mac, shift = shift)

  companion object {
    val Host = KeyboardShortcuts(HostKeymap)
  }
}

val LocalKeyboardShortcuts = staticCompositionLocalOf { KeyboardShortcuts.Host }
