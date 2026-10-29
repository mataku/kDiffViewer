package io.github.mataku.kdiffviewer.app.find

import io.github.mataku.kdiffviewer.app.CollapsedFile
import io.github.mataku.kdiffviewer.app.DiffRow
import io.github.mataku.kdiffviewer.app.flattenRows
import io.github.mataku.kdiffviewer.core.model.ChangeType
import io.github.mataku.kdiffviewer.core.model.DiffContent
import io.github.mataku.kdiffviewer.core.model.DiffLine
import io.github.mataku.kdiffviewer.core.model.FileDiff
import io.github.mataku.kdiffviewer.core.model.Hunk
import io.github.mataku.kdiffviewer.core.model.LineKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FindStateTest {
  private fun file(path: String, vararg texts: String): FileDiff {
    val lines = texts.mapIndexed { index, text -> DiffLine(LineKind.Context, text, index + 1, index + 1) }
    return FileDiff(path, path, ChangeType.Modified, false, DiffContent.Text(listOf(Hunk(1, texts.size, 1, texts.size, "", lines))))
  }

  private val rows = flattenRows(listOf(file("a.kt", "foo", "x"), file("b.kt", "x", "foo"), file("c.kt", "foo")))
  private val fooRows = rows.indices.filter { (rows[it] as? DiffRow.Line)?.line?.text == "foo" }

  private fun matches(
    source: List<DiffRow> = rows,
    query: String = "foo",
    loadId: Long = 1,
    generation: Long = 0,
    collapsed: Set<CollapsedFile> = emptySet(),
    skipFile: (Int) -> Boolean = { false },
  ) = FindMatches.Empty.updatedFor(source, FindKey(query, loadId, generation, collapsed = collapsed), skipFile = skipFile)

  private fun openState(query: String = "foo"): FindState = FindState().apply {
    show()
    this.query = query
    setLive(1, 0)
  }

  private fun FindState.currentRow(): Int = matches.rowAt(current)

  @Test
  fun newQueryStartsAtAnchor() {
    val state = openState()
    val revealed = state.onMatches(matches(), anchorRow = fooRows[0] + 1)
    assertEquals(1, state.current)
    assertEquals(fooRows[1], revealed)
  }

  @Test
  fun anchorPastLastWraps() {
    val state = openState()
    state.onMatches(matches(), anchorRow = rows.size)
    assertEquals(0, state.current)
  }

  @Test
  fun stepWraps() {
    val state = openState()
    state.onMatches(matches(), anchorRow = fooRows[2])
    assertEquals(fooRows[0], state.step(forward = true))
    assertEquals(0, state.current)
    assertEquals(fooRows[2], state.step(forward = false))
    assertEquals(2, state.current)
  }

  @Test
  fun stepWithoutMatchesIsNull() {
    val state = openState("zzz")
    state.onMatches(matches(query = "zzz"), anchorRow = 0)
    assertNull(state.step(forward = true))
    assertEquals(-1, state.current)
  }

  @Test
  fun stepIgnoresStaleMatches() {
    val state = openState()
    state.onMatches(matches(), anchorRow = 0)
    state.setLive(2, 0)
    assertNull(state.step(forward = true))
    assertEquals(0, state.current)
  }

  @Test
  fun pendingQueryIsNotLive() {
    val state = openState()
    state.onMatches(matches(), anchorRow = 0)
    assertTrue(state.isLive)
    state.query = "fooo"
    assertFalse(state.isLive)
    assertNull(state.step(forward = true))
  }

  @Test
  fun pendingQueryKeepsPreviousCountAndHighlights() {
    val state = openState()
    state.onMatches(matches(), anchorRow = 0)
    state.query = "fooo"
    assertEquals("1 of 3", state.countText())
    assertTrue(state.isPaintable)
    state.setLive(2, 0)
    assertFalse(state.isPaintable)
  }

  @Test
  fun firstQueryShowsNoCountUntilResultsArrive() {
    val state = openState()
    assertNull(state.countText())
  }

  @Test
  fun appendKeepsCurrent() {
    val prefix = flattenRows(listOf(file("a.kt", "foo", "x"), file("b.kt", "x", "foo")))
    val state = openState()
    val first = matches(prefix)
    state.onMatches(first, anchorRow = fooRows[1])
    assertEquals(fooRows[1], state.currentRow())
    val revealed = state.onMatches(first.updatedFor(rows, FindKey("foo", 1, 0)), anchorRow = 0)
    assertNull(revealed)
    assertEquals(fooRows[1], state.currentRow())
    assertEquals(3, state.matches.count)
  }

  @Test
  fun keepsCurrentAmongSameRowMatches() {
    val twice = flattenRows(listOf(file("a.kt", "foo foo")))
    val state = openState()
    state.onMatches(matches(twice), anchorRow = 0)
    state.step(forward = true)
    assertEquals(1, state.current)
    state.onMatches(matches(twice, generation = 1), anchorRow = 0)
    assertEquals(1, state.current)
  }

  @Test
  fun rebuildKeepsPosition() {
    val state = openState()
    state.onMatches(matches(), anchorRow = fooRows[1])
    val shifted = flattenRows(listOf(file("a.kt", "foo", "x", "x"), file("b.kt", "x", "x", "foo"), file("c.kt", "foo")))
    state.onMatches(matches(shifted, generation = 1), anchorRow = 0)
    val shiftedFoo = shifted.indices.filter { (shifted[it] as? DiffRow.Line)?.line?.text == "foo" }
    assertEquals(shiftedFoo[1], state.currentRow())
  }

  @Test
  fun collapseMovesCurrent() {
    val state = openState()
    state.onMatches(matches(), anchorRow = fooRows[1])
    state.onMatches(matches(collapsed = setOf(CollapsedFile("b.kt", 0L)), skipFile = { it == 1 }), anchorRow = 0)
    assertEquals(2, state.matches.count)
    assertEquals(fooRows[2], state.currentRow())
  }

  @Test
  fun expandingAfterAllCollapsedPicksAMatch() {
    val state = openState()
    state.onMatches(matches(collapsed = setOf(CollapsedFile("all", 0L)), skipFile = { true }), anchorRow = 0)
    assertEquals("No results", state.countText())
    state.onMatches(matches(), anchorRow = fooRows[1])
    assertEquals("2 of 3", state.countText())
  }

  @Test
  fun reopenStartsAtAnchor() {
    val state = openState()
    state.onMatches(matches(), anchorRow = fooRows[0])
    state.close()
    assertFalse(state.open)
    state.show()
    val revealed = state.onMatches(matches(), anchorRow = fooRows[1] + 1)
    assertEquals(fooRows[2], revealed)
    assertEquals(2, state.current)
  }

  @Test
  fun dismissClearsThenCloses() {
    val state = openState("x")
    state.dismiss()
    assertEquals("", state.query)
    assertTrue(state.open)
    state.dismiss()
    assertFalse(state.open)
  }

  @Test
  fun showRequestsFocus() {
    val state = FindState()
    state.show()
    state.show()
    assertTrue(state.open)
    assertEquals(2, state.focusRequests)
  }

  @Test
  fun queryIsTruncated() {
    val state = FindState()
    state.query = "a".repeat(1_500)
    assertEquals(1_000, state.query.length)
  }

  @Test
  fun liveTracksLoadAndGeneration() {
    val state = openState()
    state.onMatches(matches(), anchorRow = 0)
    assertTrue(state.isLive)
    state.setLive(1, 1)
    assertFalse(state.isLive)
  }

  @Test
  fun countTexts() {
    val state = FindState()
    state.setLive(1, 0)
    assertNull(state.countText())
    state.show()
    state.query = "zzz"
    state.onMatches(matches(query = "zzz"), anchorRow = 0)
    assertEquals("No results", state.countText())
    state.query = "foo"
    state.onMatches(matches(), anchorRow = fooRows[1])
    assertEquals("2 of 3", state.countText())
    val many = flattenRows(listOf(file("m.kt", *Array(4) { "x" })))
    state.query = "x"
    state.onMatches(FindMatches.Empty.updatedFor(many, FindKey("x", 1, 0), limit = 3), anchorRow = 0)
    assertEquals("1 of 3+", state.countText())
  }

  @Test
  fun groupsThousands() {
    assertEquals("10,000", groupThousands(10_000))
    assertEquals("999", groupThousands(999))
    assertEquals("1,234,567", groupThousands(1_234_567))
  }
}
