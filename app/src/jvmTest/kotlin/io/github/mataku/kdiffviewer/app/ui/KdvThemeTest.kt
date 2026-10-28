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
  fun sidebarBackgroundPerTheme() {
    assertEquals(Color(0xFFEFF2F5), LightDiffColors.sidebarBackground)
    assertEquals(DarkDiffColors.fileHeaderBackground, DarkDiffColors.sidebarBackground)
    assertEquals(DimDiffColors.fileHeaderBackground, DimDiffColors.sidebarBackground)
  }

  @Test
  fun darkThemeProvidesDarkDiffColors() = runComposeUiTest {
    var provided: DiffColors? = null
    setContent { KdvTheme(colorTheme = ColorTheme.Dark) { provided = LocalDiffColors.current } }
    waitForIdle()
    assertEquals(DarkDiffColors, provided)
  }

  @Test
  fun lightThemeProvidesLightDiffColors() = runComposeUiTest {
    var provided: DiffColors? = null
    setContent { KdvTheme(colorTheme = ColorTheme.Light) { provided = LocalDiffColors.current } }
    waitForIdle()
    assertEquals(LightDiffColors, provided)
  }

  @Test
  fun dimThemeProvidesDimColorsWithDarkSyntaxAndFlag() = runComposeUiTest {
    var colors: DiffColors? = null
    var syntax: SyntaxTheme? = null
    var dark: Boolean? = null
    setContent {
      KdvTheme(colorTheme = ColorTheme.Dim) {
        colors = LocalDiffColors.current
        syntax = LocalSyntaxTheme.current
        dark = LocalDarkTheme.current
      }
    }
    waitForIdle()
    assertEquals(DimDiffColors, colors)
    assertSame(DarkSyntaxTheme, syntax)
    assertEquals(true, dark)
  }

  @Test
  fun darkThemeAlignsMaterialColorsWithTokens() = runComposeUiTest {
    var background = Color.Unspecified
    var onBackground = Color.Unspecified
    setContent {
      KdvTheme(colorTheme = ColorTheme.Dark) {
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
      KdvTheme(colorTheme = ColorTheme.Dark) { dark = LocalSyntaxTheme.current }
      KdvTheme(colorTheme = ColorTheme.Light) { light = LocalSyntaxTheme.current }
    }
    waitForIdle()
    assertSame(DarkSyntaxTheme, dark)
    assertSame(LightSyntaxTheme, light)
  }

  @Test
  fun providesDarkThemeFlag() = runComposeUiTest {
    var dark: Boolean? = null
    var light: Boolean? = null
    setContent {
      KdvTheme(colorTheme = ColorTheme.Dark) { dark = LocalDarkTheme.current }
      KdvTheme(colorTheme = ColorTheme.Light) { light = LocalDarkTheme.current }
    }
    waitForIdle()
    assertEquals(true, dark)
    assertEquals(false, light)
  }
}
