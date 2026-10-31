package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.runtime.Immutable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Reads and writes chords as `ctrl+alt+shift+cmd+key`; `cmd` exists only on macOS. An empty string means no shortcut. */
object ChordSyntax {
  private const val CTRL = "ctrl"
  private const val ALT = "alt"
  private const val SHIFT = "shift"
  private const val CMD = "cmd"

  fun parse(text: String, keymap: Keymap): Chord? {
    val tokens = text.lowercase().split('+').map { it.trim() }
    if (tokens.any { it.isEmpty() }) return null
    val modifiers = tokens.dropLast(1)
    if (modifiers.toSet().size != modifiers.size) return null
    val allowed = if (keymap == Keymap.Mac) setOf(CTRL, ALT, SHIFT, CMD) else setOf(CTRL, ALT, SHIFT)
    if (!allowed.containsAll(modifiers)) return null
    val key = KeyNames.keyFor(tokens.last()) ?: return null
    return keymap.chord(key, ctrl = CTRL in modifiers, meta = CMD in modifiers, shift = SHIFT in modifiers, alt = ALT in modifiers)
  }

  fun format(chord: Chord): String = listOfNotNull(
    CTRL.takeIf { chord.ctrl },
    ALT.takeIf { chord.alt },
    SHIFT.takeIf { chord.shift },
    CMD.takeIf { chord.meta },
    KeyNames.token(chord.key),
  ).joinToString("+")
}

sealed interface ShortcutProblem {
  /** settings.json held text that is not a chord (a typo, an unknown key, or a non-string value). */
  data class Unreadable(val text: String) : ShortcutProblem

  /** The pressed key has no name in [KeyNames]. */
  data object UnsupportedKey : ShortcutProblem
  data object NoModifier : ShortcutProblem
  data object Reserved : ShortcutProblem
  data class UsedBy(val other: ShortcutId) : ShortcutProblem
}

fun ShortcutProblem.message(keymap: Keymap): String = when (this) {
  is ShortcutProblem.Unreadable -> "Not a valid shortcut: \"$text\""
  ShortcutProblem.UnsupportedKey -> "This key can't be used in a shortcut"
  ShortcutProblem.NoModifier -> if (keymap == Keymap.Mac) "Include ⌘ or ⌃" else "Include Ctrl"
  ShortcutProblem.Reserved -> "Reserved by the system or a menu item"
  is ShortcutProblem.UsedBy -> "Used by ${other.title}"
}

private val reservedSyntax = mapOf(
  Keymap.Mac to listOf(
    "cmd+q", "cmd+w", "cmd+h", "alt+cmd+h", "cmd+m", "cmd+tab", "cmd+space", "cmd+backquote",
    "ctrl+cmd+f", "ctrl+cmd+q", "shift+cmd+3", "shift+cmd+4", "shift+cmd+5",
    "cmd+x", "cmd+v", "cmd+a", "cmd+z", "shift+cmd+z",
    "cmd+left", "cmd+right", "shift+cmd+left", "shift+cmd+right", "shift+cmd+up", "shift+cmd+down", "cmd+backspace",
  ),
  Keymap.Standard to listOf(
    "ctrl+q", "ctrl+w", "ctrl+x", "ctrl+v", "ctrl+a", "ctrl+z", "ctrl+shift+z", "ctrl+y", "alt+f4",
    "ctrl+left", "ctrl+right", "ctrl+shift+left", "ctrl+shift+right", "ctrl+shift+home", "ctrl+shift+end",
    "ctrl+backspace", "ctrl+delete",
  ),
)

private val reserved: Map<Keymap, Set<Chord>> = reservedSyntax.mapValues { (keymap, texts) ->
  texts.mapTo(HashSet()) { requireNotNull(ChordSyntax.parse(it, keymap)) { it } }
}

/** Problems that do not depend on the other bindings. */
private fun KeyboardShortcuts.standaloneProblem(chord: Chord): ShortcutProblem? = when {
  !chord.ctrl && !chord.meta && chord.key !in KeyNames.functionKeySet -> ShortcutProblem.NoModifier
  chord in reserved.getValue(keymap) || chord in fixed -> ShortcutProblem.Reserved
  else -> null
}

/** Why [chord] cannot be bound to [id], or null when it can. */
fun KeyboardShortcuts.problemFor(id: ShortcutId, chord: Chord): ShortcutProblem? = standaloneProblem(chord)
  ?: bindings.entries.firstOrNull { (other, bound) -> other != id && bound == chord }?.let { ShortcutProblem.UsedBy(it.key) }

@Immutable
class ResolvedKeyboardShortcuts(val shortcuts: KeyboardShortcuts, val problems: Map<ShortcutId, ShortcutProblem>)

/**
 * Builds the shortcuts for [keymap] from its settings.json block. Each entry is checked on its own; a bad entry falls back
 * to its default and is reported in [ResolvedKeyboardShortcuts.problems] instead of discarding the others.
 */
fun resolveShortcuts(keymap: Keymap, stored: JsonObject?): ResolvedKeyboardShortcuts {
  val problems = LinkedHashMap<ShortcutId, ShortcutProblem>()
  val base = KeyboardShortcuts(keymap)
  val overrides = LinkedHashMap<ShortcutId, Chord?>()
  stored?.forEach { (name, value) ->
    val id = ShortcutId.fromSettingsKey(name) ?: return@forEach
    val text = value.chordText()
    if (value is JsonNull || text?.isBlank() == true) {
      overrides[id] = null
      return@forEach
    }
    val chord = text?.let { ChordSyntax.parse(it, keymap) }
    when {
      chord == null -> problems[id] = ShortcutProblem.Unreadable(text ?: value.toString())
      else -> base.standaloneProblem(chord)?.let { problems[id] = it } ?: overrides.put(id, chord)
    }
  }
  // Swaps are fine because only the final bindings are compared. Every override in a collision is dropped since none is
  // more intended than another; restoring a default can collide again, so repeat until stable (overrides only shrink).
  while (true) {
    val shortcuts = KeyboardShortcuts(keymap, overrides)
    val collisions = shortcuts.bindings.entries.filter { it.value != null }.groupBy({ it.value }, { it.key }).values.filter { it.size > 1 }
    val dropped = collisions.flatMap { ids -> ids.filter { it in overrides }.map { it to ids.first { other -> other != it } } }
    if (dropped.isEmpty()) return ResolvedKeyboardShortcuts(shortcuts, problems)
    dropped.forEach { (id, other) ->
      overrides.remove(id)
      problems[id] = ShortcutProblem.UsedBy(other)
    }
  }
}

private fun JsonElement.chordText(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content
