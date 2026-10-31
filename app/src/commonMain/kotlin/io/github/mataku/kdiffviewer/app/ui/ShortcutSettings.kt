package io.github.mataku.kdiffviewer.app.ui

import io.github.mataku.kdiffviewer.app.settings.Settings
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

fun Settings.shortcutBlock(keymap: Keymap): JsonObject? = shortcuts[keymap.settingsKey] as? JsonObject

fun Settings.resolveShortcuts(keymap: Keymap): ResolvedKeyboardShortcuts = resolveShortcuts(keymap, shortcutBlock(keymap))

/**
 * Binds [chord] to [id] for [keymap] only, leaving other platforms' blocks untouched; null removes the shortcut. Entries
 * that would collide with the new chord are removed so that a stale settings.json entry cannot void the binding just made.
 */
fun Settings.withShortcut(keymap: Keymap, id: ShortcutId, chord: Chord?): Settings {
  val block = shortcutBlock(keymap).orEmpty().filter { (name, value) ->
    name != id.settingsKey && (chord == null || value.storedChord(keymap) != chord)
  }.toMutableMap()
  if (chord != KeyboardShortcuts(keymap).defaults.getValue(id)) block[id.settingsKey] = JsonPrimitive(chord?.let(ChordSyntax::format) ?: "")
  return withBlock(keymap, block)
}

private fun JsonElement.storedChord(keymap: Keymap): Chord? = (this as? JsonPrimitive)?.takeIf { it.isString }?.let { ChordSyntax.parse(it.content, keymap) }

fun Settings.withDefaultShortcuts(keymap: Keymap): Settings = withBlock(keymap, emptyMap())

private fun Settings.withBlock(keymap: Keymap, block: Map<String, JsonElement>): Settings {
  val all = shortcuts.toMutableMap()
  if (block.isEmpty()) all.remove(keymap.settingsKey) else all[keymap.settingsKey] = JsonObject(block)
  return copy(shortcuts = JsonObject(all))
}
