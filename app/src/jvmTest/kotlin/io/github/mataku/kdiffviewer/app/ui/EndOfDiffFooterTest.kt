package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
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
    setContent { KdvTheme(darkTheme = true) { EndOfDiffFooter() } }
    onNodeWithText("It fits. You're done.").assertIsDisplayed()
    val footer = onNodeWithTag("end-of-diff").fetchSemanticsNode().size
    assertTrue(footer.height > 0)
  }
}
