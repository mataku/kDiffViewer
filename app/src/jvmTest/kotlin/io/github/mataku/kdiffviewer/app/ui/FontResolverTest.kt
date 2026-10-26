package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.ui.text.font.FontFamily
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class FontResolverTest {
  @Test
  fun nullIsMonospace() {
    assertEquals(FontFamily.Monospace, resolveFontFamily(null))
  }

  @Test
  fun missingFamilyFallsBackToMonospace() {
    assertEquals(FontFamily.Monospace, resolveFontFamily("NoSuchFont-kdv-test"))
  }

  @Test
  fun installedFamilyResolves() {
    assertNotEquals(FontFamily.Monospace, resolveFontFamily("Menlo"))
  }
}
