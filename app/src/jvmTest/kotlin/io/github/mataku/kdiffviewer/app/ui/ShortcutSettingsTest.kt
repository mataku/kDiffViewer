package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.ui.input.key.Key
import io.github.mataku.kdiffviewer.app.settings.DecodeResult
import io.github.mataku.kdiffviewer.app.settings.Settings
import io.github.mataku.kdiffviewer.app.settings.SettingsCodec
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShortcutSettingsTest {
  private val linuxBlock = JsonObject(mapOf("reload" to JsonPrimitive("ctrl+shift+r")))
  private val base = Settings(shortcuts = JsonObject(mapOf("linux" to linuxBlock)))

  @Test
  fun writesOnlyTheHostPlatformBlock() {
    val updated = base.withShortcut(Keymap.Mac, ShortcutId.Reload, Keymap.Mac.chord(Key.R, meta = true, shift = true))
    assertEquals(JsonPrimitive("shift+cmd+r"), updated.shortcutBlock(Keymap.Mac)?.get("reload"))
    assertEquals(linuxBlock, updated.shortcutBlock(Keymap.Standard))
  }

  @Test
  fun bindingTheDefaultRemovesTheEntry() {
    val custom = base.withShortcut(Keymap.Mac, ShortcutId.Reload, Keymap.Mac.chord(Key.K, meta = true))
    val back = custom.withShortcut(Keymap.Mac, ShortcutId.Reload, AppShortcuts(Keymap.Mac).reload)
    assertNull(back.shortcutBlock(Keymap.Mac))
    assertEquals(base, back)
  }

  @Test
  fun bindingRemovesStaleEntriesWithTheSameChord() {
    val stale = Settings(shortcuts = JsonObject(mapOf("macos" to JsonObject(mapOf("togglePreview" to JsonPrimitive("cmd+k"), "scrollToTop" to JsonPrimitive("cmd+k"))))))
    val updated = stale.withShortcut(Keymap.Mac, ShortcutId.Reload, Keymap.Mac.chord(Key.K, meta = true))
    assertEquals(setOf("reload"), updated.shortcutBlock(Keymap.Mac)!!.keys)
    assertTrue(updated.resolveShortcuts(Keymap.Mac).problems.isEmpty())
  }

  @Test
  fun restoringDefaultsKeepsOtherPlatforms() {
    val custom = base.withShortcut(Keymap.Mac, ShortcutId.Reload, Keymap.Mac.chord(Key.K, meta = true))
    assertEquals(base, custom.withDefaultShortcuts(Keymap.Mac))
  }

  @Test
  fun codecRoundTripsShortcutsAndKeepsBadEntriesForPerEntryChecks() {
    val text = """{"fontSize": 14, "shortcuts": {"macos": {"reload": "comd+r", "togglePreview": 5}}}"""
    val decoded = SettingsCodec.decode(text) as DecodeResult.Decoded
    assertEquals(false, decoded.partial)
    assertEquals(setOf("reload", "togglePreview"), decoded.settings.shortcuts["macos"]!!.jsonObject.keys)
    assertEquals(decoded.settings, (SettingsCodec.decode(SettingsCodec.encode(decoded.settings)) as DecodeResult.Decoded).settings)
  }

  @Test
  fun nonObjectShortcutsIsDroppedAsPartial() {
    val decoded = SettingsCodec.decode("""{"fontSize": 14, "shortcuts": "cmd+r"}""") as DecodeResult.Decoded
    assertTrue(decoded.partial)
    assertEquals(14, decoded.settings.fontSize)
    assertEquals(JsonObject(emptyMap()), decoded.settings.shortcuts)
  }
}
