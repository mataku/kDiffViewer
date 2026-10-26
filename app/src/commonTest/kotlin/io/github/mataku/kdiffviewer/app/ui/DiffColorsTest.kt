package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

class DiffColorsTest {
  @Test
  fun lightBackgroundIsWhite() {
    assertEquals(Color(0xFFFFFFFF), LightDiffColors.background)
  }

  @Test
  fun accentIsSystemBlue() {
    assertEquals(Color(0xFF007AFF), LightDiffColors.accent)
    assertEquals(Color(0xFF0A84FF), DarkDiffColors.accent)
  }
}
