package io.github.mataku.kdiffviewer.core.model

import io.github.mataku.kdiffviewer.core.git.UntrackedEntry
import io.github.mataku.kdiffviewer.core.git.synthesizeUntracked
import kotlin.test.Test
import kotlin.test.assertEquals

class HighlightRangesTest {
  private fun ctx(old: Int, new: Int) = DiffLine(LineKind.Context, "c", old, new)
  private fun rem(old: Int) = DiffLine(LineKind.Removed, "r", old, null)
  private fun add(new: Int) = DiffLine(LineKind.Added, "a", null, new)

  private fun hunk(vararg lines: DiffLine) = Hunk(1, 1, 1, 1, "", lines.toList())

  private fun diff(vararg hunks: Hunk) = FileDiff(
    "a.kt",
    "a.kt",
    ChangeType.Modified,
    isUntracked = false,
    DiffContent.Text(hunks.toList()),
  )

  @Test fun oldSideCoversOnlyRemovedRuns() {
    val file = diff(hunk(ctx(1, 1), rem(2), rem(3), ctx(4, 3), rem(5), add(4)))
    assertEquals(listOf(2..3, 5..5), file.highlightRanges(Side.Old))
  }

  @Test fun newSideCoversAddedAndContextRuns() {
    val file = diff(hunk(ctx(1, 1), rem(2), rem(3), ctx(4, 3), rem(5), add(4)))
    assertEquals(listOf(1..1, 3..4), file.highlightRanges(Side.New))
  }

  @Test fun runsDoNotCrossGaps() {
    val file = diff(
      hunk(add(1), add(2), add(3)),
      hunk(add(10), add(11), add(12)),
    )
    assertEquals(listOf(1..3, 10..12), file.highlightRanges(Side.New))
  }

  @Test fun pureAdditionHasNoOldRanges() {
    val file = diff(hunk(add(1), add(2)))
    assertEquals(emptyList(), file.highlightRanges(Side.Old))
  }

  @Test fun untrackedFileSelectsAllLines() {
    val file = synthesizeUntracked("a.kt", UntrackedEntry.Regular("x\ny\n".encodeToByteArray()))
    assertEquals(listOf(1..2), file.highlightRanges(Side.New))
    assertEquals(emptyList(), file.highlightRanges(Side.Old))
  }

  @Test fun noNewlineMarkerIsIgnored() {
    val file = diff(hunk(add(1), DiffLine(LineKind.NoNewlineAtEof, "", null, null)))
    assertEquals(listOf(1..1), file.highlightRanges(Side.New))
  }

  @Test fun nonTextContentHasNoRanges() {
    val file = FileDiff("a.bin", "a.bin", ChangeType.Modified, isUntracked = false, DiffContent.Binary)
    assertEquals(emptyList(), file.highlightRanges(Side.Old))
    assertEquals(emptyList(), file.highlightRanges(Side.New))
  }
}
