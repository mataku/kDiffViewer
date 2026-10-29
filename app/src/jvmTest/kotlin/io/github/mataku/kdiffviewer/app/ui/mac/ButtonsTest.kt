package io.github.mataku.kdiffviewer.app.ui.mac

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mataku.kdiffviewer.app.ui.KdvIcons
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ButtonsTest {
  @Test
  fun pullDownTagShowsTextAndClicks() = runComposeUiTest {
    var clicks = 0
    setContent { MacPullDownTag("origin/develop", { clicks++ }, Modifier.testTag("tag"), textStyle = TextStyle(fontSize = 12.sp), textModifier = Modifier.testTag("tag-text")) }
    onNodeWithTag("tag-text", useUnmergedTree = true).assertTextEquals("origin/develop")
    onNodeWithTag("tag").performClick()
    assertEquals(1, clicks)
  }

  @Test
  fun pullDownTagDrawsSmallChevron() = runComposeUiTest {
    setContent { MacPullDownTag("origin/develop", {}, textStyle = TextStyle(fontSize = 12.sp), textModifier = Modifier.testTag("tag-text")) }
    val chevron = onNodeWithTag("pull-down-chevron", useUnmergedTree = true)
    chevron.assertWidthIsEqualTo(7.dp).assertHeightIsEqualTo(4.dp)
    val text = onNodeWithTag("tag-text", useUnmergedTree = true).getUnclippedBoundsInRoot()
    assertEquals(text.right + 8.dp, chevron.getUnclippedBoundsInRoot().left)
  }

  @Test
  fun pullDownChevronSitsOneDpBelowTextCentre() = runComposeUiTest {
    setContent {
      CompositionLocalProvider(LocalDensity provides Density(2f)) {
        MacPullDownTag("origin/develop", {}, textStyle = TextStyle(fontSize = 12.sp), textModifier = Modifier.testTag("tag-text"))
      }
    }
    val text = onNodeWithTag("tag-text", useUnmergedTree = true).getUnclippedBoundsInRoot()
    val chevron = onNodeWithTag("pull-down-chevron", useUnmergedTree = true).getUnclippedBoundsInRoot()
    assertEquals((text.top + text.bottom) / 2 + 1.dp, (chevron.top + chevron.bottom) / 2)
  }

  @Test
  fun popupButtonDrawsUpDownChevrons() = runComposeUiTest {
    setContent { MacPopupButton("Menlo", {}) }
    onNodeWithTag("popup-chevrons", useUnmergedTree = true).assertWidthIsEqualTo(7.dp).assertHeightIsEqualTo(10.dp)
  }

  @Test
  fun toolbarButtonClicksAndHasControlSize() = runComposeUiTest {
    var clicks = 0
    setContent { MacToolbarButton(KdvIcons.SidebarCollapse, "Hide Sidebar", "Hide Sidebar (⌃⌘S)", { clicks++ }, Modifier.testTag("button")) }
    onNodeWithTag("button").performClick()
    onNodeWithTag("button").performClick()
    assertEquals(2, clicks)
    onNodeWithTag("button").assertWidthIsEqualTo(34.dp).assertHeightIsEqualTo(24.dp)
    onNodeWithContentDescription("Hide Sidebar").assertExists()
  }
}
