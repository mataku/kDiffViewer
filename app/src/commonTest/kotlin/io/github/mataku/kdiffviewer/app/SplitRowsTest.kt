package io.github.mataku.kdiffviewer.app

import io.github.mataku.kdiffviewer.app.selection.context
import io.github.mataku.kdiffviewer.app.selection.hunk
import io.github.mataku.kdiffviewer.app.selection.rowsOf
import io.github.mataku.kdiffviewer.app.selection.textFile
import io.github.mataku.kdiffviewer.core.model.ChangeType
import io.github.mataku.kdiffviewer.core.model.DiffContent
import io.github.mataku.kdiffviewer.core.model.DiffLine
import io.github.mataku.kdiffviewer.core.model.FileDiff
import io.github.mataku.kdiffviewer.core.model.LineKind
import kotlin.test.Test
import kotlin.test.assertEquals

class SplitRowsTest {
  private fun removed(text: String, old: Int) = DiffLine(LineKind.Removed, text, old, null)

  private fun added(text: String, new: Int) = DiffLine(LineKind.Added, text, null, new)

  private fun noNewline() = DiffLine(LineKind.NoNewlineAtEof, "\\ No newline at end of file", null, null)

  private fun pairsOf(split: SplitRows): List<Pair<Int, Int>> = (0 until split.size).map { split.leftOf(it) to split.rightOf(it) }

  private fun pairsFor(vararg lines: DiffLine): List<Pair<Int, Int>> = pairsOf(splitRowsOf(rowsOf(textFile("a.txt", hunk(*lines)))))

  private val headers = listOf(0 to 0, 1 to 1)

  @Test
  fun contextIsBothSides() {
    assertEquals(headers + listOf(2 to 2, 3 to 3), pairsFor(context("a"), context("b")))
  }

  @Test
  fun removedThenAddedArePaired() {
    assertEquals(headers + listOf(2 to 4, 3 to -1), pairsFor(removed("a", 1), removed("b", 2), added("c", 1)))
  }

  @Test
  fun addedLongerThanRemoved() {
    assertEquals(headers + listOf(2 to 3, -1 to 4), pairsFor(removed("a", 1), added("b", 1), added("c", 2)))
  }

  @Test
  fun addedWithoutRemoved() {
    assertEquals(headers + listOf(-1 to 2), pairsFor(added("a", 1)))
  }

  @Test
  fun removedWithoutAdded() {
    assertEquals(headers + listOf(2 to -1), pairsFor(removed("a", 1)))
  }

  @Test
  fun interleavedRunsPairSeparately() {
    assertEquals(
      headers + listOf(2 to 3, 4 to 4, 5 to 6),
      pairsFor(removed("a", 1), added("b", 1), context("x"), removed("c", 3), added("d", 3)),
    )
  }

  @Test
  fun noNewlineFollowsItsSide() {
    assertEquals(headers + listOf(2 to 4, 3 to 5), pairsFor(removed("a", 1), noNewline(), added("b", 1), noNewline()))
    assertEquals(headers + listOf(2 to 2, 3 to 3), pairsFor(context("a"), noNewline()))
  }

  @Test
  fun placeholderAndHeadersAreFullWidth() {
    val binary = FileDiff("a.bin", "a.bin", ChangeType.Modified, false, DiffContent.Binary)
    assertEquals(listOf(0 to 0, 1 to 1), pairsOf(splitRowsOf(rowsOf(binary))))
  }

  @Test
  fun splitIndexOfIsInverse() {
    val rows = rowsOf(
      textFile("a.txt", hunk(removed("a", 1), noNewline(), added("b", 1), added("c", 2), context("x"), removed("d", 3))),
      textFile("b.txt", hunk(added("e", 1), noNewline())),
    )
    val split = splitRowsOf(rows)
    for (u in rows.indices) {
      val s = split.splitIndexOf(u)
      assertEquals(true, split.leftOf(s) == u || split.rightOf(s) == u, "row $u")
    }
  }

  @Test
  fun appendContinuesIndices() {
    val a = textFile("a.txt", hunk(removed("a", 1), added("b", 1), added("c", 2)))
    val b = textFile("b.txt", hunk(context("x"), removed("d", 2), noNewline()))
    val rowsA = rowsOf(a)
    val builder = SplitRowsBuilder()
    builder.append(rowsA, 0)
    val rowsAB = rowsOf(a, b)
    builder.append(rowsAB, rowsA.size)
    val incremental = builder.snapshot()
    val whole = splitRowsOf(rowsAB)
    assertEquals(pairsOf(whole), pairsOf(incremental))
    assertEquals(rowsAB.indices.map { whole.splitIndexOf(it) }, rowsAB.indices.map { incremental.splitIndexOf(it) })
  }
}
