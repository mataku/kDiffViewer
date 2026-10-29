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
import kotlin.test.assertTrue

class FindMatchesTest {
  private fun file(path: String, vararg texts: String, header: String = "", noNewline: Boolean = false): FileDiff {
    val lines = texts.mapIndexed { index, text -> DiffLine(LineKind.Context, text, index + 1, index + 1) } +
      (if (noNewline) listOf(DiffLine(LineKind.NoNewlineAtEof, "foo", null, null)) else emptyList())
    return FileDiff(path, path, ChangeType.Modified, false, DiffContent.Text(listOf(Hunk(1, texts.size, 1, texts.size, header, lines))))
  }

  private fun key(query: String, loadId: Long = 1, generation: Long = 0) = FindKey(query, loadId, generation)

  private fun FindMatches.positions(): List<Pair<Int, Int>> = (0 until count).map { rowAt(it) to startAt(it) }

  @Test
  fun ignoresCase() {
    val rows = flattenRows(listOf(file("a.kt", "Foo foo FOO")))
    val matches = FindMatches.Empty.updatedFor(rows, key("foo"))
    assertEquals(listOf(2 to 0, 2 to 4, 2 to 8), matches.positions())
    assertEquals(3, matches.queryLength)
  }

  @Test
  fun nonOverlapping() {
    val rows = flattenRows(listOf(file("a.kt", "aaaa")))
    assertEquals(listOf(2 to 0, 2 to 2), FindMatches.Empty.updatedFor(rows, key("aa")).positions())
  }

  @Test
  fun skipsNonLineRowsAndNoNewlineMarker() {
    val rows = flattenRows(listOf(file("foo.kt", "bar", header = "fun foo()", noNewline = true)))
    assertEquals(0, FindMatches.Empty.updatedFor(rows, key("foo")).count)
  }

  @Test
  fun capsAtLimit() {
    val rows = flattenRows(listOf(file("a.kt", "x", "x", "x", "x", "x")))
    val matches = FindMatches.Empty.updatedFor(rows, key("x"), limit = 3)
    assertEquals(3, matches.count)
    assertTrue(matches.capped)
  }

  @Test
  fun appendScansOnlyNewRows() {
    val first = flattenRows(listOf(file("a.kt", "foo", "bar")))
    val before = FindMatches.Empty.updatedFor(first, key("foo"))
    val appended = flattenRows(listOf(file("a.kt", "zzz", "foo"), file("b.kt", "foo foo")))
    val after = before.updatedFor(appended, key("foo"))
    assertEquals(listOf(2 to 0, 6 to 0, 6 to 4), after.positions())
    assertEquals(appended.size, after.scannedRows)
  }

  @Test
  fun rebuildRescans() {
    val first = flattenRows(listOf(file("a.kt", "foo", "bar")))
    val before = FindMatches.Empty.updatedFor(first, key("foo"))
    val rebuilt = flattenRows(listOf(file("a.kt", "bar", "foo")))
    assertEquals(listOf(3 to 0), before.updatedFor(rebuilt, key("foo", generation = 1)).positions())
  }

  @Test
  fun newLoadRescans() {
    val first = flattenRows(listOf(file("a.kt", "foo", "bar")))
    val before = FindMatches.Empty.updatedFor(first, key("foo"))
    val reloaded = flattenRows(listOf(file("a.kt", "bar", "foo", "x")))
    assertEquals(listOf(3 to 0), before.updatedFor(reloaded, key("foo", loadId = 2)).positions())
  }

  @Test
  fun newQueryRescans() {
    val rows = flattenRows(listOf(file("a.kt", "foo", "bar")))
    val before = FindMatches.Empty.updatedFor(rows, key("foo"))
    assertEquals(listOf(3 to 0), before.updatedFor(rows, key("bar")).positions())
  }

  @Test
  fun cappedAppendDoesNotScan() {
    val first = flattenRows(listOf(file("a.kt", "x", "x", "x")))
    val before = FindMatches.Empty.updatedFor(first, key("x"), limit = 2)
    val appended = ThrowingRows(first.size + 10, first)
    val after = before.updatedFor(appended, key("x"), limit = 2)
    assertEquals(2, after.count)
    assertTrue(after.capped)
  }

  @Test
  fun rowLookups() {
    val rows = flattenRows(listOf(file("a.kt", "n", "foo foo", "n", "n", "n", "foo")))
    val matches = FindMatches.Empty.updatedFor(rows, key("foo"))
    assertEquals(listOf(3 to 0, 3 to 4, 7 to 0), matches.positions())
    assertEquals(0, matches.firstIndexOfRow(3))
    assertEquals(-1, matches.firstIndexOfRow(5))
    assertEquals(2, matches.firstIndexAtOrAfter(4))
    assertEquals(0, matches.firstIndexAtOrAfter(0))
    assertEquals(3, matches.firstIndexAtOrAfter(8))
  }

  @Test
  fun skipsFiles() {
    val rows = flattenRows(listOf(file("a.kt", "foo"), file("b.kt", "foo")))
    val matches = FindMatches.Empty.updatedFor(rows, key("foo"), skipFile = { it == 0 })
    assertEquals(listOf(5 to 0), matches.positions())
  }

  @Test
  fun skippedFilesDoNotUseTheCap() {
    val rows = flattenRows(listOf(file("a.kt", "x", "x", "x"), file("b.kt", "x")))
    val matches = FindMatches.Empty.updatedFor(rows, key("x"), limit = 2, skipFile = { it == 0 })
    assertEquals(1, matches.count)
    assertFalse(matches.capped)
  }

  @Test
  fun collapseChangeRescans() {
    val rows = flattenRows(listOf(file("a.kt", "foo"), file("b.kt", "foo")))
    val before = FindMatches.Empty.updatedFor(rows, key("foo"))
    val collapsed = setOf(CollapsedFile("a.kt", 1L))
    val after = before.updatedFor(rows, FindKey("foo", 1, 0, collapsed = collapsed), skipFile = { it == 0 })
    assertEquals(listOf(5 to 0), after.positions())
  }

  @Test
  fun emptyQueryIsEmpty() {
    val matches = FindMatches.Empty.updatedFor(ThrowingRows(5), key(""))
    assertEquals(0, matches.count)
    assertFalse(matches.capped)
  }

  @Test
  fun isForComparesLoadAndGeneration() {
    val matches = FindMatches.Empty.updatedFor(flattenRows(listOf(file("a.kt", "foo"))), key("foo", loadId = 3, generation = 2))
    assertTrue(matches.isFor(3, 2))
    assertFalse(matches.isFor(3, 3))
    assertFalse(matches.isFor(4, 2))
    assertFalse(FindMatches.Empty.isFor(0, 0))
  }

  private class ThrowingRows(override val size: Int, private val readable: List<DiffRow> = emptyList()) : AbstractList<DiffRow>() {
    override fun get(index: Int): DiffRow = readable.getOrNull(index) ?: throw AssertionError("row $index read")
  }
}
