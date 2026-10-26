package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import io.github.mataku.kdiffviewer.app.TreeEntry
import io.github.mataku.kdiffviewer.app.TreeRow
import io.github.mataku.kdiffviewer.core.model.DiffStats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class FileTreePanelTest {
  private val entries = listOf(
    TreeEntry(TreeRow(0, "src", null), "src", false),
    TreeEntry(TreeRow(1, "a.kt", 0), null, false),
  )

  @Test
  fun directoryRowShowsChevronAndFolderIcon() = runComposeUiTest {
    setContent { FileTreePanel(entries, {}, {}) }
    onNodeWithTag("tree-chevron-0", useUnmergedTree = true).assertIsDisplayed()
    onNodeWithTag("tree-folder-icon-0", useUnmergedTree = true).assertIsDisplayed()
    onNodeWithTag("tree-file-icon-0", useUnmergedTree = true).assertDoesNotExist()
  }

  @Test
  fun fileRowShowsFileIconWithoutChevron() = runComposeUiTest {
    setContent { FileTreePanel(entries, {}, {}) }
    onNodeWithTag("tree-file-icon-1", useUnmergedTree = true).assertIsDisplayed()
    onNodeWithTag("tree-folder-icon-1", useUnmergedTree = true).assertDoesNotExist()
    onNodeWithTag("tree-chevron-1", useUnmergedTree = true).assertDoesNotExist()
  }

  @Test
  fun clickingDirectoryTogglesItsPath() = runComposeUiTest {
    val toggled = mutableListOf<String>()
    setContent { FileTreePanel(entries, {}, { toggled += it }) }
    onNodeWithTag("tree-dir-0").performClick()
    assertEquals(listOf("src"), toggled)
  }

  @Test
  fun wideTreeShowsFileStats() = runComposeUiTest {
    setContent { FileTreePanel(entries, {}, {}, statsOf = { DiffStats(3, 1) }, width = 280.dp) }
    onNodeWithTag("tree-additions-0", useUnmergedTree = true).assertTextEquals("+3")
    onNodeWithTag("tree-deletions-0", useUnmergedTree = true).assertTextEquals("−1")
  }

  @Test
  fun narrowTreeHidesFileStats() = runComposeUiTest {
    setContent { FileTreePanel(entries, {}, {}, statsOf = { DiffStats(3, 1) }, width = 200.dp) }
    onNodeWithTag("tree-additions-0", useUnmergedTree = true).assertDoesNotExist()
  }

  @Test
  fun fileWithoutLineChangesShowsNoStats() = runComposeUiTest {
    setContent { FileTreePanel(entries, {}, {}, statsOf = { DiffStats(0, 0) }, width = 280.dp) }
    onNodeWithTag("tree-additions-0", useUnmergedTree = true).assertDoesNotExist()
  }

  @Test
  fun treeRowGrowsWithFontSize() = runComposeUiTest {
    var size by mutableStateOf(13)
    setContent { KdvTheme(darkTheme = false, treeFontSize = size) { FileTreePanel(entries, {}, {}) } }
    val small = onNodeWithTag("tree-dir-0").getUnclippedBoundsInRoot().height
    size = 20
    waitForIdle()
    val large = onNodeWithTag("tree-dir-0").getUnclippedBoundsInRoot().height
    assertTrue(large > small, "$large should exceed $small")
  }
}
