package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.mataku.kdiffviewer.app.DiffRow
import io.github.mataku.kdiffviewer.app.LoadStatus
import io.github.mataku.kdiffviewer.app.ViewerUiState
import io.github.mataku.kdiffviewer.app.find.FindKey
import io.github.mataku.kdiffviewer.app.find.FindMatches
import io.github.mataku.kdiffviewer.app.find.FindState
import io.github.mataku.kdiffviewer.app.flattenRows
import io.github.mataku.kdiffviewer.core.model.ChangeType
import io.github.mataku.kdiffviewer.core.model.DiffContent
import io.github.mataku.kdiffviewer.core.model.DiffLine
import io.github.mataku.kdiffviewer.core.model.FileDiff
import io.github.mataku.kdiffviewer.core.model.Hunk
import io.github.mataku.kdiffviewer.core.model.LineKind
import io.github.mataku.kdiffviewer.core.model.RepoPath
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class FindHighlightUiTest {
  private val file = FileDiff(
    "a.kt",
    "a.kt",
    ChangeType.Modified,
    false,
    DiffContent.Text(listOf(Hunk(1, 2, 1, 2, "", listOf(DiffLine(LineKind.Context, "foo", 1, 1), DiffLine(LineKind.Context, "\tfoo", 2, 2))))),
  )

  private fun stateOf(): ViewerUiState {
    val rows = flattenRows(listOf(file))
    val headers = rows.mapIndexedNotNull { index, row -> if (row is DiffRow.FileHeader) index else null }
    return ViewerUiState(RepoPath("/repo"), LoadStatus.Done, listOf(file), rows, headers, loadId = 1)
  }

  private fun findFor(ui: ViewerUiState, open: Boolean): FindState = FindState().apply {
    if (open) show()
    query = "foo"
    val matches = FindMatches.Empty.updatedFor(ui.rows, FindKey("foo", ui.loadId, ui.rowsGeneration))
    onMatches(matches, { true }, anchorRow = matches.rowAt(1))
  }

  private fun ComposeUiTest.render(find: FindState): PixelMap {
    val ui = stateOf()
    setContent {
      KdvTheme(colorTheme = ColorTheme.Light, tabWidth = 4) {
        Box(Modifier.size(800.dp, 400.dp)) { ViewerScreen(ui, {}, findState = find) }
      }
    }
    waitForIdle()
    return onNodeWithTag("diff-list").captureToImage().toPixelMap()
  }

  private fun PixelMap.columnsOf(color: Color): List<Int> {
    val columns = sortedSetOf<Int>()
    for (y in 0 until height) for (x in 0 until width) if (this[x, y] == color) columns += x
    return columns.toList()
  }

  @Test
  fun highlightsMatchOnTabbedLine() = runComposeUiTest {
    val pixels = render(findFor(stateOf(), open = true))
    val match = pixels.columnsOf(LightDiffColors.findMatchBackground)
    val current = pixels.columnsOf(LightDiffColors.findCurrentBackground)
    assertTrue(match.isNotEmpty(), "match highlight drawn")
    assertTrue(current.isNotEmpty(), "current highlight drawn")
    val matchWidth = match.last() - match.first() + 1
    val currentWidth = current.last() - current.first() + 1
    assertTrue(abs(matchWidth - currentWidth) <= 1, "same width: $matchWidth vs $currentWidth")
    val expectedShift = matchWidth * 4f / 3f
    val shift = current.first() - match.first()
    assertTrue(abs(shift - expectedShift) <= 2f, "shifted by four columns: $shift vs $expectedShift")
  }

  @Test
  fun closedFindDrawsNothing() = runComposeUiTest {
    val pixels = render(findFor(stateOf(), open = false))
    assertEquals(emptyList(), pixels.columnsOf(LightDiffColors.findMatchBackground))
    assertEquals(emptyList(), pixels.columnsOf(LightDiffColors.findCurrentBackground))
  }
}
