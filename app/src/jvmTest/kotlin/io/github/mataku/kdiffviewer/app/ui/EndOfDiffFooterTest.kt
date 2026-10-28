package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.mataku.kdiffviewer.app.generated.resources.Res
import io.github.mataku.kdiffviewer.app.generated.resources.box_cat_dark
import io.github.mataku.kdiffviewer.app.generated.resources.box_cat_light
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class EndOfDiffFooterTest {
  @Test
  fun picksArtByTheme() {
    assertEquals(Res.drawable.box_cat_dark, endOfDiffArt(dark = true))
    assertEquals(Res.drawable.box_cat_light, endOfDiffArt(dark = false))
  }

  @Test
  fun rendersCaptionAndImage() = runComposeUiTest {
    setContent { KdvTheme(colorTheme = ColorTheme.Dark) { EndOfDiffFooter() } }
    onNodeWithText("It fits. You're done.").assertIsDisplayed()
    val footer = onNodeWithTag("end-of-diff").fetchSemanticsNode().size
    assertTrue(footer.height > 0)
  }

  @Test
  fun artIsOneHundredEightyDpWide() = runComposeUiTest {
    setContent { KdvTheme(colorTheme = ColorTheme.Light) { EndOfDiffFooter() } }
    onNodeWithTag("end-of-diff-art").assertWidthIsEqualTo(180.dp)
  }

  @Test
  fun artKeepsTheImageAspectRatio() = runComposeUiTest {
    setContent { KdvTheme(colorTheme = ColorTheme.Light) { EndOfDiffFooter() } }
    onNodeWithTag("end-of-diff-art").assertHeightIsEqualTo((180f * 172f / 270f).dp)
  }

  @Test
  fun artAndCaptionHiddenWhenFooterIsNarrowerThanArt() = runComposeUiTest {
    setContent { KdvTheme(colorTheme = ColorTheme.Light) { Box(Modifier.width(179.dp)) { EndOfDiffFooter() } } }
    onNodeWithTag("end-of-diff-art").assertIsNotDisplayed()
    onNodeWithText("It fits. You're done.").assertIsNotDisplayed()
  }

  @Test
  fun artShownWhenFooterFitsArt() = runComposeUiTest {
    setContent { KdvTheme(colorTheme = ColorTheme.Light) { Box(Modifier.width(180.dp)) { EndOfDiffFooter() } } }
    onNodeWithTag("end-of-diff-art").assertIsDisplayed()
  }
}
