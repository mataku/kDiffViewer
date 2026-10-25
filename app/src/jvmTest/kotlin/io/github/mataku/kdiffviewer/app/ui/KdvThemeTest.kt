package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.mataku.compose.highlight.core.LocalSyntaxTheme
import io.github.mataku.compose.highlight.core.SyntaxTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

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

  @Test
  fun darkThemeProvidesDarkSyntaxTheme() = runComposeUiTest {
    var dark: SyntaxTheme? = null
    var light: SyntaxTheme? = null
    setContent {
      KdvTheme(darkTheme = true) { dark = LocalSyntaxTheme.current }
      KdvTheme(darkTheme = false) { light = LocalSyntaxTheme.current }
    }
    waitForIdle()
    assertSame(DarkSyntaxTheme, dark)
    assertSame(LightSyntaxTheme, light)
  }
}
