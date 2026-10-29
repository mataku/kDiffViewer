package io.github.mataku.kdiffviewer.core.review

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class ReanchorTest {
  private fun thread(anchor: Anchor, outdated: Boolean = false, resolved: Boolean = false) = ReviewThread(
    id = "t1",
    anchor = anchor,
    comments = listOf(Comment("c1", Author.Agent("bot"), "body", Instant.fromEpochSeconds(0))),
    resolved = resolved,
    outdated = outdated,
  )

  @Test
  fun rangeAnchorReanchorsToSamePlacement() {
    val file = modified("a.kt", hunk(ctx("a", 1, 1), removed("r1", 2), removed("r2", 3), added("n1", 2), added("n2", 3), ctx("z", 4, 4)))
    val r = anchorOfRange(file, 0, 0, 1, 5)!!
    assertEquals(r.anchor to Placement.OnLine(r.location), reanchor(r.anchor, listOf(file)))
  }

  @Test
  fun unchangedLineKeepsAnchor() {
    val files = listOf(modified("a.kt", hunk(ctx("a", 1, 1), ctx("b", 2, 2), ctx("c", 3, 3))))
    val anchor = linesAnchor("a.kt", Side.New, 3, 3, "c")
    val (result, placement) = reanchor(thread(anchor), files)
    assertEquals(anchor, result.anchor)
    assertEquals(Placement.OnLine(LineLocation(0, 0, 2)), placement)
    assertEquals(false, result.outdated)
  }

  @Test
  fun shiftedLineMoves() {
    val files = listOf(modified("a.kt", hunk(added("n1", 1), added("n2", 2), ctx("a", 1, 3), ctx("b", 2, 4))))
    val (result, placement) = reanchor(linesAnchor("a.kt", Side.New, 2, 2, "b"), files)
    assertEquals(linesAnchor("a.kt", Side.New, 4, 4, "b"), result)
    assertEquals(Placement.OnLine(LineLocation(0, 0, 3)), placement)
  }

  private fun braceFile() = listOf(
    modified(
      "a.kt",
      hunk(added("}", 3)),
      hunk(added("}", 10)),
      hunk(added("}", 20)),
    ),
  )

  @Test
  fun duplicateSnapshotPicksNearest() {
    val (result, placement) = reanchor(linesAnchor("a.kt", Side.New, 12, 12, "}"), braceFile())
    assertEquals(linesAnchor("a.kt", Side.New, 10, 10, "}"), result)
    assertEquals(Placement.OnLine(LineLocation(0, 1, 0)), placement)
  }

  @Test
  fun duplicateSnapshotTieGoesToSmallerLine() {
    val (result, _) = reanchor(linesAnchor("a.kt", Side.New, 15, 15, "}"), braceFile())
    assertEquals(linesAnchor("a.kt", Side.New, 10, 10, "}"), result)
  }

  @Test
  fun changedLineBecomesOutdated() {
    val files = listOf(modified("a.kt", hunk(ctx("z", 1, 1))))
    val anchor = linesAnchor("a.kt", Side.New, 1, 1, "a")
    val (result, placement) = reanchor(thread(anchor), files)
    assertEquals(true, result.outdated)
    assertEquals(anchor, result.anchor)
    assertEquals(Placement.OnFile(0), placement)
  }

  @Test
  fun outdatedThreadRecoversWhenLineReturns() {
    val files = listOf(modified("a.kt", hunk(ctx("a", 1, 1))))
    val (result, placement) = reanchor(thread(linesAnchor("a.kt", Side.New, 1, 1, "a"), outdated = true), files)
    assertEquals(false, result.outdated)
    assertEquals(Placement.OnLine(LineLocation(0, 0, 0)), placement)
  }

  @Test
  fun fileRemovedIsOrphan() {
    val files = listOf(modified("b.kt", hunk(ctx("a", 1, 1))))
    val (result, placement) = reanchor(thread(linesAnchor("a.kt", Side.New, 1, 1, "a")), files)
    assertEquals(Placement.Orphan, placement)
    assertEquals(true, result.outdated)
  }

  @Test
  fun fileThreadFollowsPath() {
    val other = modified("o.kt", hunk(ctx("a", 1, 1)))
    val files = listOf(modified("a.kt", hunk(ctx("a", 1, 1))))
    val (result, placement) = reanchor(thread(Anchor.File("a.kt")), files)
    assertEquals(Placement.OnFile(0), placement)
    assertEquals(false, result.outdated)
    val before = listOf(other, other, other, files[0])
    assertEquals(Placement.OnFile(3), reanchor(Anchor.File("a.kt"), before).second)
  }

  @Test
  fun fileThreadOrphanWhenPathGone() {
    val files = listOf(modified("b.kt", hunk(ctx("a", 1, 1))))
    val (result, placement) = reanchor(thread(Anchor.File("a.kt")), files)
    assertEquals(Placement.Orphan, placement)
    assertEquals(true, result.outdated)
  }

  @Test
  fun oldSideAnchorMatchesRemovedLines() {
    val files = listOf(modified("a.kt", hunk(ctx("a", 1, 1), removed("x", 2), added("y", 2))))
    val (result, placement) = reanchor(linesAnchor("a.kt", Side.Old, 2, 2, "x"), files)
    assertEquals(linesAnchor("a.kt", Side.Old, 2, 2, "x"), result)
    assertEquals(Placement.OnLine(LineLocation(0, 0, 1)), placement)
  }

  @Test
  fun rangeMatchesContiguousRunOnly() {
    val anchor = linesAnchor("a.kt", Side.New, 1, 2, "a", "b")
    val split = listOf(modified("a.kt", hunk(ctx("a", 1, 1), ctx("q", 2, 2), ctx("b", 3, 3))))
    val (splitResult, splitPlacement) = reanchor(thread(anchor), split)
    assertEquals(true, splitResult.outdated)
    assertEquals(Placement.OnFile(0), splitPlacement)
    val gap = listOf(modified("a.kt", hunk(ctx("a", 1, 1)), hunk(ctx("b", 5, 5))))
    assertEquals(true, reanchor(thread(anchor), gap).first.outdated)
    val joined = listOf(modified("a.kt", hunk(ctx("z", 1, 1), ctx("a", 2, 2), ctx("b", 3, 3))))
    val (joinedResult, joinedPlacement) = reanchor(anchor, joined)
    assertEquals(linesAnchor("a.kt", Side.New, 2, 3, "a", "b"), joinedResult)
    assertEquals(Placement.OnLine(LineLocation(0, 0, 2)), joinedPlacement)
  }

  @Test
  fun samePathDeletedAndUntracked() {
    val files = listOf(
      deletedFile("x.txt", hunk(removed("a", 1))),
      addedFile("x.txt", hunk(added("a", 1)), untracked = true),
    )
    assertEquals(Placement.OnLine(LineLocation(0, 0, 0)), reanchor(linesAnchor("x.txt", Side.Old, 1, 1, "a"), files).second)
    assertEquals(Placement.OnLine(LineLocation(1, 0, 0)), reanchor(linesAnchor("x.txt", Side.New, 1, 1, "a"), files).second)
  }

  @Test
  fun resolvedAndCommentsUntouched() {
    val files = listOf(modified("a.kt", hunk(ctx("z", 1, 1))))
    val original = thread(linesAnchor("a.kt", Side.New, 1, 1, "a"), resolved = true)
    val (result, _) = reanchor(original, files)
    assertEquals(true, result.resolved)
    assertEquals(original.comments, result.comments)
  }
}
