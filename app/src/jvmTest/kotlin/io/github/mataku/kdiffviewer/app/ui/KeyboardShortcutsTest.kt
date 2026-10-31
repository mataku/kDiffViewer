package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.ui.input.key.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KeyboardShortcutsTest {
  private val mac = KeyboardShortcuts(Keymap.Mac)
  private val standard = KeyboardShortcuts(Keymap.Standard)

  @Test
  fun macUsesCommand() {
    assertTrue(mac.reload!!.meta)
    assertFalse(mac.reload!!.ctrl)
    assertEquals("⌘R", mac.reload!!.label)
    assertEquals("⇧⌘G", mac.findPrevious.label)
  }

  @Test
  fun macSidebarIsControlCommandS() {
    val chord = mac.toggleSidebar!!
    assertEquals(Key.S, chord.key)
    assertTrue(chord.ctrl && chord.meta)
    assertEquals("⌃⌘S", chord.label)
  }

  @Test
  fun standardUsesControl() {
    assertTrue(standard.reload!!.ctrl)
    assertFalse(standard.reload!!.meta)
    assertEquals("Ctrl+R", standard.reload!!.label)
    assertEquals("Ctrl+Shift+G", standard.findPrevious.label)
  }

  @Test
  fun standardSidebarIsF9() {
    val chord = standard.toggleSidebar!!
    assertEquals(Key.F9, chord.key)
    assertFalse(chord.ctrl || chord.meta || chord.shift)
    assertEquals("F9", chord.label)
  }

  @Test
  fun noStandardShortcutUsesMeta() {
    (standard.fixed + standard.bindings.values.filterNotNull()).forEach { assertFalse(it.meta, it.label) }
  }

  @Test
  fun macLabelsForViewerAndComposer() {
    assertEquals("⌘↑", mac.scrollToTop!!.label)
    assertEquals("⌘↓", mac.scrollToBottom!!.label)
    assertEquals("⌘↩", mac.submitComment!!.label)
    assertEquals("⇧⌘↩", mac.addSingleComment!!.label)
    assertEquals("⇧⌘P", mac.togglePreview!!.label)
  }

  @Test
  fun standardLabelsForViewerAndComposer() {
    assertEquals("Ctrl+Home", standard.scrollToTop!!.label)
    assertEquals("Ctrl+End", standard.scrollToBottom!!.label)
    assertEquals("Ctrl+Enter", standard.submitComment!!.label)
    assertEquals("Ctrl+Shift+Enter", standard.addSingleComment!!.label)
  }

  @Test
  fun overridesReplaceOnlyTheirBinding() {
    val custom = Keymap.Mac.chord(Key.R, meta = true, shift = true)
    val shortcuts = KeyboardShortcuts(Keymap.Mac, mapOf(ShortcutId.Reload to custom))
    assertEquals(custom, shortcuts.reload)
    assertEquals(mac.toggleSidebar, shortcuts.toggleSidebar)
  }

  @Test
  fun removedShortcutIsUnbound() {
    val shortcuts = KeyboardShortcuts(Keymap.Mac, mapOf(ShortcutId.Reload to null))
    assertEquals(null, shortcuts.reload)
    assertEquals(mac.toggleSidebar, shortcuts.toggleSidebar)
  }

  @Test
  fun macLabelOrdersModifiersLikeMenus() {
    assertEquals("⌃⌥⇧⌘K", Keymap.Mac.chord(Key.K, ctrl = true, alt = true, shift = true, meta = true).label)
    assertEquals("Ctrl+Alt+Shift+K", Keymap.Standard.chord(Key.K, ctrl = true, alt = true, shift = true).label)
  }

  @Test
  fun chordEqualityIgnoresLabel() {
    assertEquals(Chord(Key.R, "x", meta = true), Chord(Key.R, "y", meta = true))
    assertTrue(Chord(Key.R, "x", meta = true) != Chord(Key.R, "x", meta = true, shift = true))
  }
}
