package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.ui.input.key.Key

/** Keys a shortcut may use, with their settings.json token and display labels. Anything else is rejected. */
internal object KeyNames {
  private class Name(val key: Key, val token: String, val macLabel: String, val standardLabel: String = macLabel)

  private val letters = listOf(
    Key.A, Key.B, Key.C, Key.D, Key.E, Key.F, Key.G, Key.H, Key.I, Key.J, Key.K, Key.L, Key.M,
    Key.N, Key.O, Key.P, Key.Q, Key.R, Key.S, Key.T, Key.U, Key.V, Key.W, Key.X, Key.Y, Key.Z,
  ).mapIndexed { i, key -> ('a' + i).let { Name(key, it.toString(), it.uppercase()) } }

  private val digits = listOf(Key.Zero, Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine)
    .mapIndexed { i, key -> Name(key, i.toString(), i.toString()) }

  private val functionKeys = listOf(Key.F1, Key.F2, Key.F3, Key.F4, Key.F5, Key.F6, Key.F7, Key.F8, Key.F9, Key.F10, Key.F11, Key.F12)
    .mapIndexed { i, key -> Name(key, "f${i + 1}", "F${i + 1}") }

  private val others = listOf(
    Name(Key.DirectionUp, "up", "↑", "Up"),
    Name(Key.DirectionDown, "down", "↓", "Down"),
    Name(Key.DirectionLeft, "left", "←", "Left"),
    Name(Key.DirectionRight, "right", "→", "Right"),
    Name(Key.MoveHome, "home", "Home"),
    Name(Key.MoveEnd, "end", "End"),
    Name(Key.PageUp, "pageup", "Page Up"),
    Name(Key.PageDown, "pagedown", "Page Down"),
    Name(Key.Enter, "enter", "↩", "Enter"),
    Name(Key.Tab, "tab", "⇥", "Tab"),
    Name(Key.Spacebar, "space", "Space"),
    Name(Key.Backspace, "backspace", "⌫", "Backspace"),
    Name(Key.Delete, "delete", "⌦", "Delete"),
    Name(Key.Comma, "comma", ","),
    Name(Key.Period, "period", "."),
    Name(Key.Slash, "slash", "/"),
    Name(Key.Backslash, "backslash", "\\"),
    Name(Key.Semicolon, "semicolon", ";"),
    Name(Key.Apostrophe, "quote", "'"),
    Name(Key.LeftBracket, "bracketleft", "["),
    Name(Key.RightBracket, "bracketright", "]"),
    Name(Key.Minus, "minus", "-"),
    Name(Key.Equals, "equals", "="),
    Name(Key.Grave, "backquote", "`"),
  )

  private val all = letters + digits + functionKeys + others
  private val byKey = all.associateBy { it.key }
  private val byToken = all.associateBy { it.token }

  val functionKeySet: Set<Key> = functionKeys.mapTo(HashSet()) { it.key }

  fun supports(key: Key): Boolean = key in byKey

  fun token(key: Key): String = byKey.getValue(key).token

  fun keyFor(token: String): Key? = byToken[token]?.key

  fun label(key: Key, keymap: Keymap): String {
    val name = byKey[key] ?: return key.toString()
    return if (keymap == Keymap.Mac) name.macLabel else name.standardLabel
  }
}
