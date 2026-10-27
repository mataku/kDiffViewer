package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import io.github.mataku.kdiffviewer.app.GitFileStatus
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
  fun bothCountsSeparatedBySingleSpace() = runComposeUiTest {
    val stats = listOf(DiffStats(12, 3))
    setContent { FileTreePanel(entries, {}, {}, statsOf = { stats[0] }, width = 280.dp) }
    onNodeWithTag("tree-stats-0", useUnmergedTree = true).assertTextEquals("+12 −3")
  }

  @Test
  fun narrowTreeHidesFileStats() = runComposeUiTest {
    setContent { FileTreePanel(entries, {}, {}, statsOf = { DiffStats(3, 1) }, width = 200.dp) }
    onNodeWithTag("tree-stats-0", useUnmergedTree = true).assertDoesNotExist()
  }

  @Test
  fun fileWithoutLineChangesShowsNoStats() = runComposeUiTest {
    setContent { FileTreePanel(entries, {}, {}, statsOf = { DiffStats(0, 0) }, width = 280.dp) }
    onNodeWithTag("tree-stats-0", useUnmergedTree = true).assertDoesNotExist()
  }

  @Test
  fun zeroCountsAreHidden() = runComposeUiTest {
    var stats by mutableStateOf(DiffStats(40, 0))
    setContent { FileTreePanel(entries, {}, {}, statsOf = { stats }, width = 280.dp) }
    onNodeWithTag("tree-stats-0", useUnmergedTree = true).assertTextEquals("+40")
    stats = DiffStats(0, 25)
    waitForIdle()
    onNodeWithTag("tree-stats-0", useUnmergedTree = true).assertTextEquals("−25")
    stats = DiffStats(0, 0)
    waitForIdle()
    onNodeWithTag("tree-stats-0", useUnmergedTree = true).assertDoesNotExist()
  }

  @Test
  fun statsRightEdgesAlign() = runComposeUiTest {
    val two = listOf(
      TreeEntry(TreeRow(0, "a.kt", 0), null, false),
      TreeEntry(TreeRow(0, "b.kt", 1), null, false),
    )
    val stats = listOf(DiffStats(12, 3), DiffStats(0, 12345))
    setContent { FileTreePanel(two, {}, {}, statsOf = { stats[it] }, statusOf = { GitFileStatus.Modified }, width = 280.dp) }
    val first = onNodeWithTag("tree-stats-0", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    val second = onNodeWithTag("tree-stats-1", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    assertEquals(first.right, second.right)
  }

  @Test
  fun statusLetterShown() = runComposeUiTest {
    val five = GitFileStatus.entries.mapIndexed { i, _ -> TreeEntry(TreeRow(0, "f$i.kt", i), null, false) }
    setContent { FileTreePanel(five, {}, {}, statusOf = { GitFileStatus.entries[it] }, width = 280.dp) }
    val expected = listOf("U", "A", "M", "D", "R")
    expected.forEachIndexed { i, letter ->
      onNodeWithTag("tree-status-$i", useUnmergedTree = true).assertTextEquals(letter)
    }
  }

  @Test
  fun directoryShowsNoStatusLetter() = runComposeUiTest {
    val directories = listOf(TreeEntry(TreeRow(0, "src", null), "src", false))
    setContent { FileTreePanel(directories, {}, {}, statusOf = { GitFileStatus.Modified }, width = 280.dp) }
    onNodeWithTag("tree-status-0", useUnmergedTree = true).assertDoesNotExist()
  }

  @Test
  fun statusHiddenBelowStatsWidth() = runComposeUiTest {
    setContent { FileTreePanel(entries, {}, {}, statusOf = { GitFileStatus.Modified }, width = 200.dp) }
    onNodeWithTag("tree-status-0", useUnmergedTree = true).assertDoesNotExist()
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

  @Test
  fun searchFieldSitsBelowTopInsetAndAboveRows() = runComposeUiTest {
    setContent { FileTreePanel(entries, {}, {}, topInset = 30.dp) }
    val field = onNodeWithTag("tree-filter").fetchSemanticsNode().boundsInRoot
    val row = onNodeWithTag("tree-dir-0").fetchSemanticsNode().boundsInRoot
    assertTrue(field.top >= with(density) { 30.dp.toPx() }, "field=$field")
    assertTrue(row.top >= field.bottom, "row=$row field=$field")
  }

  @Test
  fun typingReportsQueryChanges() = runComposeUiTest {
    val changes = mutableListOf<String>()
    setContent { FileTreePanel(entries, {}, {}, onQueryChange = { changes += it }) }
    onNodeWithTag("tree-filter").performTextInput("ab")
    assertEquals("ab", changes.last())
  }

  @Test
  fun filteringHidesChevronsAndDisablesDirectoryToggle() = runComposeUiTest {
    val toggled = mutableListOf<String>()
    setContent { FileTreePanel(entries, {}, { toggled += it }, query = "a", filtering = true) }
    onNodeWithTag("tree-chevron-0", useUnmergedTree = true).assertDoesNotExist()
    onNodeWithTag("tree-folder-icon-0", useUnmergedTree = true).assertIsDisplayed()
    onNodeWithTag("tree-dir-0").assertHasNoClickAction()
    onNodeWithTag("tree-dir-0").performClick()
    assertTrue(toggled.isEmpty())
  }

  @Test
  fun filteringKeepsFileRowsClickable() = runComposeUiTest {
    val clicked = mutableListOf<Int>()
    setContent { FileTreePanel(entries, { clicked += it }, {}, query = "a", filtering = true) }
    onNodeWithTag("tree-file-0").performClick()
    assertEquals(listOf(0), clicked)
  }

  @Test
  fun noMatchLineShownOnlyWhileFiltering() = runComposeUiTest {
    var filtering by mutableStateOf(true)
    setContent { FileTreePanel(emptyList(), {}, {}, query = if (filtering) "zzz" else "", filtering = filtering) }
    onNodeWithTag("tree-empty").assertTextEquals("No matching files")
    filtering = false
    waitForIdle()
    onNodeWithTag("tree-empty").assertDoesNotExist()
  }
}
