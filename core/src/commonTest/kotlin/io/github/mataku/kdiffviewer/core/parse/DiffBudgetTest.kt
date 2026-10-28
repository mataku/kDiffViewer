package io.github.mataku.kdiffviewer.core.parse

import io.github.mataku.kdiffviewer.core.model.ChangeType
import io.github.mataku.kdiffviewer.core.model.DiffContent
import io.github.mataku.kdiffviewer.core.model.DiffLine
import io.github.mataku.kdiffviewer.core.model.DiffStats
import io.github.mataku.kdiffviewer.core.model.FileDiff
import io.github.mataku.kdiffviewer.core.model.Hunk
import io.github.mataku.kdiffviewer.core.model.LineKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiffBudgetTest {
  private fun file(content: DiffContent) = FileDiff("a.txt", "a.txt", ChangeType.Modified, false, content)

  private fun text(lines: Int) = file(
    DiffContent.Text(
      listOf(Hunk(1, lines, 1, lines, "", (1..lines).map { DiffLine(LineKind.Context, "line $it", it, it) })),
    ),
  )

  @Test
  fun admitsUpToFileLimit() {
    val budget = DiffBudget(DiffLimits(maxFiles = 2, maxLines = 100))
    assertTrue(budget.admit(text(1)))
    assertTrue(budget.admit(text(1)))
    assertFalse(budget.admit(text(1)))
  }

  @Test
  fun admitsExactlyLineLimit() {
    val budget = DiffBudget(DiffLimits(maxFiles = 10, maxLines = 5))
    assertTrue(budget.admit(text(3)))
    assertTrue(budget.admit(text(2)))
  }

  @Test
  fun rejectsFileThatCrossesLineLimit() {
    val budget = DiffBudget(DiffLimits(maxFiles = 10, maxLines = 5))
    assertTrue(budget.admit(text(3)))
    assertFalse(budget.admit(text(3)))
  }

  @Test
  fun nonTextCountsAsZeroLines() {
    val budget = DiffBudget(DiffLimits(maxFiles = 3, maxLines = 1))
    assertTrue(budget.admit(text(1)))
    assertTrue(budget.admit(file(DiffContent.Binary)))
    assertTrue(budget.admit(file(DiffContent.TooManyLines(DiffStats(50_000, 0)))))
  }

  @Test
  fun staysClosedAfterRejection() {
    val budget = DiffBudget(DiffLimits(maxFiles = 10, maxLines = 5))
    assertFalse(budget.admit(text(6)))
    assertFalse(budget.admit(text(1)))
  }

  @Test
  fun defaultsMatchSpec() {
    assertEquals(DiffLimits(1_000, 100_000), DiffLimits())
  }
}
