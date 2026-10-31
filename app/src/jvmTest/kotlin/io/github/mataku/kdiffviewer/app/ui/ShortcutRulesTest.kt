package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.ui.input.key.Key
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShortcutRulesTest {
  private val mac = AppShortcuts(Keymap.Mac)
  private val standard = AppShortcuts(Keymap.Standard)

  private fun macChord(text: String) = requireNotNull(ChordSyntax.parse(text, Keymap.Mac)) { text }

  private fun block(vararg entries: Pair<String, String>): JsonObject = JsonObject(entries.associate { (k, v) -> k to JsonPrimitive(v) })

  @Test
  fun parsesAndFormatsInCanonicalOrder() {
    val chord = macChord(" Shift + CMD + r ")
    assertEquals(Keymap.Mac.chord(Key.R, meta = true, shift = true), chord)
    assertEquals("shift+cmd+r", ChordSyntax.format(chord))
    assertEquals("ctrl+alt+shift+cmd+f5", ChordSyntax.format(macChord("cmd+shift+alt+ctrl+f5")))
  }

  @Test
  fun rejectsMalformedText() {
    listOf("comd+r", "cmd+", "+r", "cmd+cmd+r", "cmd+escape", "cmd+r+shift", "").forEach {
      assertNull(ChordSyntax.parse(it, Keymap.Mac), it)
    }
  }

  @Test
  fun cmdIsMacOnly() {
    assertNull(ChordSyntax.parse("cmd+r", Keymap.Standard))
    assertEquals(Keymap.Standard.chord(Key.R, ctrl = true, shift = true), ChordSyntax.parse("ctrl+shift+r", Keymap.Standard))
  }

  @Test
  fun requiresCommandOrControlExceptForFunctionKeys() {
    assertEquals(ShortcutProblem.NoModifier, mac.problemFor(ShortcutId.Reload, macChord("shift+r")))
    assertEquals(ShortcutProblem.NoModifier, mac.problemFor(ShortcutId.Reload, macChord("alt+r")))
    assertNull(mac.problemFor(ShortcutId.Reload, macChord("f5")))
    assertNull(mac.problemFor(ShortcutId.Reload, macChord("ctrl+r")))
  }

  @Test
  fun rejectsSystemAndMenuShortcuts() {
    listOf("cmd+q", "cmd+w", "cmd+c", "cmd+comma", "shift+cmd+g", "cmd+v", "shift+cmd+4").forEach {
      assertEquals(ShortcutProblem.Reserved, mac.problemFor(ShortcutId.Reload, macChord(it)), it)
    }
    assertEquals(ShortcutProblem.Reserved, standard.problemFor(ShortcutId.Reload, ChordSyntax.parse("ctrl+q", Keymap.Standard)!!))
    assertEquals(ShortcutProblem.Reserved, standard.problemFor(ShortcutId.Reload, ChordSyntax.parse("alt+f4", Keymap.Standard)!!))
  }

  @Test
  fun rejectsAChordAnotherShortcutUses() {
    assertEquals(ShortcutProblem.UsedBy(ShortcutId.ToggleSidebar), mac.problemFor(ShortcutId.Reload, mac.toggleSidebar))
    assertNull(mac.problemFor(ShortcutId.Reload, mac.reload))
  }

  @Test
  fun resolvesEachEntryOnItsOwn() {
    val resolved = resolveShortcuts(Keymap.Mac, block("reload" to "comd+r", "toggleSidebar" to "ctrl+cmd+b", "unknownAction" to "cmd+k"))
    assertEquals(mac.reload, resolved.shortcuts.reload)
    assertEquals(macChord("ctrl+cmd+b"), resolved.shortcuts.toggleSidebar)
    assertEquals(mapOf<ShortcutId, ShortcutProblem>(ShortcutId.Reload to ShortcutProblem.Unreadable("comd+r")), resolved.problems)
  }

  @Test
  fun nonStringValueIsUnreadable() {
    val resolved = resolveShortcuts(Keymap.Mac, buildJsonObject { put("reload", 5) })
    assertEquals(ShortcutProblem.Unreadable("5"), resolved.problems[ShortcutId.Reload])
    assertEquals(mac.reload, resolved.shortcuts.reload)
  }

  @Test
  fun reservedAndModifierlessEntriesFallBack() {
    val resolved = resolveShortcuts(Keymap.Mac, block("reload" to "cmd+q", "togglePreview" to "p"))
    assertEquals(ShortcutProblem.Reserved, resolved.problems[ShortcutId.Reload])
    assertEquals(ShortcutProblem.NoModifier, resolved.problems[ShortcutId.TogglePreview])
    assertEquals(mac.bindings, resolved.shortcuts.bindings)
  }

  @Test
  fun swapIsValid() {
    val resolved = resolveShortcuts(Keymap.Mac, block("reload" to "ctrl+cmd+s", "toggleSidebar" to "cmd+r"))
    assertTrue(resolved.problems.isEmpty())
    assertEquals(mac.toggleSidebar, resolved.shortcuts.reload)
    assertEquals(mac.reload, resolved.shortcuts.toggleSidebar)
  }

  @Test
  fun overrideCollidingWithADefaultFallsBack() {
    val resolved = resolveShortcuts(Keymap.Mac, block("reload" to "ctrl+cmd+s"))
    assertEquals(ShortcutProblem.UsedBy(ShortcutId.ToggleSidebar), resolved.problems[ShortcutId.Reload])
    assertEquals(mac.reload, resolved.shortcuts.reload)
  }

  @Test
  fun collidingOverridesAreBothDropped() {
    val resolved = resolveShortcuts(Keymap.Mac, block("reload" to "cmd+k", "togglePreview" to "cmd+k"))
    assertEquals(ShortcutProblem.UsedBy(ShortcutId.TogglePreview), resolved.problems[ShortcutId.Reload])
    assertEquals(ShortcutProblem.UsedBy(ShortcutId.Reload), resolved.problems[ShortcutId.TogglePreview])
    assertEquals(mac.bindings, resolved.shortcuts.bindings)
  }

  @Test
  fun restoredDefaultCanCascade() {
    // sidebar loses to the scroll collision, and its restored ⌃⌘S then collides with reload's override.
    val resolved = resolveShortcuts(Keymap.Mac, block("toggleSidebar" to "cmd+k", "scrollToTop" to "cmd+k", "reload" to "ctrl+cmd+s"))
    assertEquals(mac.bindings, resolved.shortcuts.bindings)
    assertEquals(setOf(ShortcutId.ToggleSidebar, ShortcutId.ScrollToTop, ShortcutId.Reload), resolved.problems.keys)
  }
}
