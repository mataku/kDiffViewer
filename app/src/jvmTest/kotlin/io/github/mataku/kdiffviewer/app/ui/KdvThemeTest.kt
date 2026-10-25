package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class KdvThemeTest {
  @Test
  fun darkThemeProvidesDarkDiffColors() = runComposeUiTest {
    var provided: DiffColors? = null
    setContent { KdvTheme(darkTheme = true) { provided = LocalDiffColors.current } }
    waitForIdle()
    assertEquals(DarkDiffColors, provided)
  }

  @Test
  fun lightThemeProvidesLightDiffColors() = runComposeUiTest {
    var provided: DiffColors? = null
    setContent { KdvTheme(darkTheme = false) { provided = LocalDiffColors.current } }
    waitForIdle()
    assertEquals(LightDiffColors, provided)
  }

  @Test
  fun darkThemeAlignsMaterialColorsWithTokens() = runComposeUiTest {
    var background = Color.Unspecified
    var onBackground = Color.Unspecified
    setContent {
      KdvTheme(darkTheme = true) {
        background = MaterialTheme.colorScheme.background
        onBackground = MaterialTheme.colorScheme.onBackground
      }
    }
    waitForIdle()
    assertEquals(DarkDiffColors.background, background)
    assertEquals(DarkDiffColors.text, onBackground)
  }
}
