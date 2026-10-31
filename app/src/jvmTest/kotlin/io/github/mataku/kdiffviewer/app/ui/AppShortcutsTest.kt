package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.ui.input.key.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppShortcutsTest {
  private val mac = AppShortcuts(Keymap.Mac)
  private val standard = AppShortcuts(Keymap.Standard)

  @Test
  fun macUsesCommand() {
    assertTrue(mac.reload.meta)
    assertFalse(mac.reload.ctrl)
    assertEquals("⌘R", mac.reload.label)
    assertEquals("⇧⌘G", mac.findPrevious.label)
  }

  @Test
  fun macSidebarIsControlCommandS() {
    val chord = mac.toggleSidebar
    assertEquals(Key.S, chord.key)
    assertTrue(chord.ctrl && chord.meta)
    assertEquals("⌃⌘S", chord.label)
  }

  @Test
  fun standardUsesControl() {
    assertTrue(standard.reload.ctrl)
    assertFalse(standard.reload.meta)
    assertEquals("Ctrl+R", standard.reload.label)
    assertEquals("Ctrl+Shift+G", standard.findPrevious.label)
  }

  @Test
  fun standardSidebarIsF9() {
    val chord = standard.toggleSidebar
    assertEquals(Key.F9, chord.key)
    assertFalse(chord.ctrl || chord.meta || chord.shift)
    assertEquals("F9", chord.label)
  }

  @Test
  fun noStandardShortcutUsesMeta() {
    with(standard) {
      (listOf(open, settings, copy, find, findNext, findPrevious, toggleSidebar, reload, submitComment, addSingleComment, togglePreview) + scrollToTop + scrollToBottom)
        .forEach { assertFalse(it.meta, it.label) }
    }
  }

  @Test
  fun macLabelsForViewerAndComposer() {
    assertEquals(listOf("⌘↑", "⌃Home"), mac.scrollToTop.map { it.label })
    assertEquals(listOf("⌘↓", "⌃End"), mac.scrollToBottom.map { it.label })
    assertEquals("⌘↩", mac.submitComment.label)
    assertEquals("⇧⌘↩", mac.addSingleComment.label)
    assertEquals("⇧⌘P", mac.togglePreview.label)
  }

  @Test
  fun standardLabelsForViewerAndComposer() {
    assertEquals(listOf("Ctrl+Home"), standard.scrollToTop.map { it.label })
    assertEquals(listOf("Ctrl+End"), standard.scrollToBottom.map { it.label })
    assertEquals("Ctrl+Enter", standard.submitComment.label)
    assertEquals("Ctrl+Shift+Enter", standard.addSingleComment.label)
  }

  @Test
  fun referenceListsOnlyAppSpecificShortcuts() {
    val listed = mac.reference.flatMap { group -> group.entries.flatMap { it.chords } }
    with(mac) {
      listOf(reload, toggleSidebar, submitComment, addSingleComment, togglePreview).forEach { assertTrue(it in listed, it.label) }
      (scrollToTop + scrollToBottom).forEach { assertTrue(it in listed, it.label) }
      listOf(open, settings, copy, find, findNext, findPrevious).forEach { assertFalse(it in listed, it.label) }
    }
  }
}
