package io.github.mataku.kdiffviewer.app.review

import io.github.mataku.kdiffviewer.app.LoadStatus
import io.github.mataku.kdiffviewer.app.ViewerError
import io.github.mataku.kdiffviewer.app.ViewerUiState
import io.github.mataku.kdiffviewer.core.model.ChangeType
import io.github.mataku.kdiffviewer.core.model.DiffContent
import io.github.mataku.kdiffviewer.core.model.DiffLine
import io.github.mataku.kdiffviewer.core.model.FileDiff
import io.github.mataku.kdiffviewer.core.model.Hunk
import io.github.mataku.kdiffviewer.core.model.LineKind
import io.github.mataku.kdiffviewer.core.model.RepoPath
import io.github.mataku.kdiffviewer.core.review.Anchor
import io.github.mataku.kdiffviewer.core.review.Author
import io.github.mataku.kdiffviewer.core.review.LineLocation
import io.github.mataku.kdiffviewer.core.review.Placement
import io.github.mataku.kdiffviewer.core.review.anchorOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ReviewStateTest {
  private val repo = RepoPath("/repo")
  private val epoch = Instant.fromEpochSeconds(1_000)

  private fun file(path: String, vararg lines: String) = FileDiff(
    path,
    path,
    ChangeType.Modified,
    false,
    DiffContent.Text(
      listOf(
        Hunk(1, lines.size, 1, lines.size, "", lines.mapIndexed { i, text -> DiffLine(LineKind.Context, text, i + 1, i + 1) }),
      ),
    ),
  )

  private fun ui(loadId: Long, vararg files: FileDiff, status: LoadStatus = LoadStatus.Done, repo: RepoPath? = this.repo) = ViewerUiState(repo = repo, status = status, files = files.toList(), loadId = loadId)

  private fun TestScope.newState() = ReviewState(this, StandardTestDispatcher(testScheduler), { epoch })

  private fun ReviewState.addThread(file: FileDiff, lineIndex: Int, text: String = "fix") {
    val anchor = anchorOf(file, 0, lineIndex)!!
    startDraft(DraftTarget.NewThread(anchor, Placement.OnLine(LineLocation(0, 0, lineIndex))))
    updateDraft(text)
    submitDraft()
  }

  private val base = file("a.kt", "a", "b", "c")

  @Test
  fun submitNewThreadPlacesOnLine() = runTest(StandardTestDispatcher()) {
    val state = newState()
    state.addThread(base, 1)
    val snapshot = state.snapshot.value
    val thread = snapshot.threads.single()
    assertEquals("t1", thread.id)
    assertEquals("c1", thread.comments.single().id)
    assertEquals(Author.User, thread.comments.single().author)
    assertEquals(listOf(thread), snapshot.placements.byLine[linePosition(0, 0, 1)])
    assertNull(snapshot.draft)
  }

  @Test
  fun blankDraftNotSubmitted() = runTest(StandardTestDispatcher()) {
    val state = newState()
    state.startDraft(DraftTarget.NewThread(anchorOf(base, 0, 1)!!, Placement.OnLine(LineLocation(0, 0, 1))))
    state.updateDraft("   ")
    state.submitDraft()
    assertTrue(state.snapshot.value.threads.isEmpty())
  }

  @Test
  fun secondDraftDoesNotReplaceNonBlankDraft() = runTest(StandardTestDispatcher()) {
    val state = newState()
    val first = DraftTarget.NewThread(anchorOf(base, 0, 1)!!, Placement.OnLine(LineLocation(0, 0, 1)))
    state.startDraft(first)
    state.updateDraft("keep")
    state.startDraft(DraftTarget.NewThread(anchorOf(base, 0, 2)!!, Placement.OnLine(LineLocation(0, 0, 2))))
    assertEquals(Draft(first, "keep"), state.snapshot.value.draft)
  }

  @Test
  fun blankDraftIsReplaced() = runTest(StandardTestDispatcher()) {
    val state = newState()
    state.startDraft(DraftTarget.NewThread(anchorOf(base, 0, 1)!!, Placement.OnLine(LineLocation(0, 0, 1))))
    val second = DraftTarget.NewThread(anchorOf(base, 0, 2)!!, Placement.OnLine(LineLocation(0, 0, 2)))
    state.startDraft(second)
    assertEquals(Draft(second, ""), state.snapshot.value.draft)
  }

  @Test
  fun doneLoadReanchorsAndSettles() = runTest(StandardTestDispatcher()) {
    val state = newState()
    state.onViewerState(ui(1, base))
    advanceUntilIdle()
    state.addThread(base, 1)
    val moved = file("a.kt", "x", "y", "a", "b", "c")
    state.onViewerState(ui(2, moved))
    advanceUntilIdle()
    val snapshot = state.snapshot.value
    assertEquals(2L, snapshot.settledLoadId)
    assertEquals(2L, snapshot.placements.loadId)
    assertEquals(listOf("t1"), snapshot.placements.byLine[linePosition(0, 0, 3)]?.map { it.id })
    assertTrue(snapshot.placements.byLine[linePosition(0, 0, 1)] == null)
    assertEquals(4, (snapshot.threads.single().anchor as Anchor.Lines).startLine)
  }

  @Test
  fun staleReanchorDiscarded() = runTest(StandardTestDispatcher()) {
    val state = newState()
    state.onViewerState(ui(1, base))
    advanceUntilIdle()
    state.addThread(base, 1)
    state.onViewerState(ui(2, file("a.kt", "x", "a", "b", "c")))
    state.onViewerState(ui(3, file("a.kt", "x", "y", "a", "b", "c")))
    advanceUntilIdle()
    val snapshot = state.snapshot.value
    assertEquals(3L, snapshot.placements.loadId)
    assertEquals(3L, snapshot.settledLoadId)
    assertEquals(listOf("t1"), snapshot.placements.byLine[linePosition(0, 0, 3)]?.map { it.id })
  }

  @Test
  fun sameLoadIsNotReanchoredTwice() = runTest(StandardTestDispatcher()) {
    val state = newState()
    state.onViewerState(ui(1, base))
    advanceUntilIdle()
    val before = state.snapshot.value
    state.onViewerState(ui(1, base))
    advanceUntilIdle()
    assertTrue(before === state.snapshot.value)
  }

  @Test
  fun failedLoadSettlesWithoutMovingThreads() = runTest(StandardTestDispatcher()) {
    val state = newState()
    state.onViewerState(ui(1, base))
    advanceUntilIdle()
    state.addThread(base, 1)
    val placementsBefore = state.snapshot.value.placements
    state.onViewerState(ui(2, status = LoadStatus.Failed(ViewerError.GitFailed("bad"))))
    advanceUntilIdle()
    val snapshot = state.snapshot.value
    assertEquals(2L, snapshot.settledLoadId)
    assertTrue(snapshot.lastLoadFailed)
    assertTrue(placementsBefore === snapshot.placements)
    assertEquals(1, snapshot.threads.size)
  }

  @Test
  fun awaitSettledReturnsCounts() = runTest(StandardTestDispatcher()) {
    val state = newState()
    state.onViewerState(ui(1, base))
    advanceUntilIdle()
    state.addThread(base, 1)
    state.addThread(base, 2)
    val settlement = async { state.awaitSettled(2) }
    runCurrent()
    assertTrue(settlement.isActive)
    state.onViewerState(ui(2, file("a.kt", "a", "q", "c")))
    advanceUntilIdle()
    assertEquals(LoadSettlement(false, 1, 1), settlement.await())
  }

  @Test
  fun awaitSettledReturnsImmediatelyWhenAlreadySettled() = runTest(StandardTestDispatcher()) {
    val state = newState()
    state.onViewerState(ui(3, base))
    advanceUntilIdle()
    assertEquals(LoadSettlement(false, 1, 0), state.awaitSettled(2))
  }

  @Test
  fun repoSwitchClearsEverything() = runTest(StandardTestDispatcher()) {
    val state = newState()
    state.onViewerState(ui(1, base))
    advanceUntilIdle()
    state.addThread(base, 1)
    state.startDraft(DraftTarget.NewThread(anchorOf(base, 0, 2)!!, Placement.OnLine(LineLocation(0, 0, 2))))
    state.onViewerState(ui(2, base, repo = RepoPath("/other"), status = LoadStatus.Loading))
    val snapshot = state.snapshot.value
    assertTrue(snapshot.threads.isEmpty())
    assertNull(snapshot.draft)
    assertTrue(snapshot.placements.byLine.isEmpty())
    state.addThread(base, 1)
    assertEquals("t1", state.snapshot.value.threads.single().id)
  }

  @Test
  fun agentReplyKeepsUserReplyDraft() = runTest(StandardTestDispatcher()) {
    val state = newState()
    state.addThread(base, 1)
    state.startDraft(DraftTarget.Reply("t1"))
    state.updateDraft("typing")
    val result = state.reply("t1", Author.Agent("claude"), "done")
    assertIs<ReviewResult.Ok<*>>(result)
    assertEquals(Draft(DraftTarget.Reply("t1"), "typing"), state.snapshot.value.draft)
    assertEquals(listOf("c1", "c2"), state.snapshot.value.threads.single().comments.map { it.id })
    state.submitDraft()
    assertEquals(listOf("c1", "c2", "c3"), state.snapshot.value.threads.single().comments.map { it.id })
    assertEquals("typing", state.snapshot.value.threads.single().comments.last().body)
  }

  @Test
  fun editDraftStartsWithBodyAndReplacesIt() = runTest(StandardTestDispatcher()) {
    val state = newState()
    state.addThread(base, 1, "old")
    state.startDraft(DraftTarget.Edit("c1"))
    assertEquals("old", state.snapshot.value.draft?.text)
    state.updateDraft("new")
    state.submitDraft()
    assertEquals("new", state.snapshot.value.threads.single().comments.single().body)
  }

  @Test
  fun reloadWithOpenNewThreadDraft() = runTest(StandardTestDispatcher()) {
    val state = newState()
    state.onViewerState(ui(1, base))
    advanceUntilIdle()
    state.startDraft(DraftTarget.NewThread(anchorOf(base, 0, 1)!!, Placement.OnLine(LineLocation(0, 0, 1))))
    state.updateDraft("wip")
    state.onViewerState(ui(2, file("a.kt", "x", "a", "b", "c")))
    advanceUntilIdle()
    val moved = state.snapshot.value.draft!!
    assertEquals("wip", moved.text)
    val target = moved.target as DraftTarget.NewThread
    assertEquals(Placement.OnLine(LineLocation(0, 0, 2)), target.placement)
    state.onViewerState(ui(3, file("a.kt", "x")))
    advanceUntilIdle()
    val gone = state.snapshot.value.draft!!
    assertEquals("wip", gone.text)
    assertEquals(Placement.OnFile(0), (gone.target as DraftTarget.NewThread).placement)
  }

  @Test
  fun threadSubmittedDuringReanchorIsPlacedAfterwards() = runTest(StandardTestDispatcher()) {
    val state = newState()
    state.onViewerState(ui(1, base))
    advanceUntilIdle()
    val moved = file("a.kt", "x", "a", "b", "c")
    state.onViewerState(ui(2, moved))
    state.addThread(base, 1)
    advanceUntilIdle()
    assertEquals(listOf("t1"), state.snapshot.value.placements.byLine[linePosition(0, 0, 2)]?.map { it.id })
  }

  @Test
  fun resolveUnknownThreadIsError() = runTest(StandardTestDispatcher()) {
    val state = newState()
    assertEquals(ReviewResult.Err(ReviewError.ThreadNotFound("t9")), state.setResolved("t9", true))
    assertEquals(ReviewResult.Err(ReviewError.ThreadNotFound("t9")), state.reply("t9", Author.User, "x"))
  }

  @Test
  fun resolveFiltersThreads() = runTest(StandardTestDispatcher()) {
    val state = newState()
    state.addThread(base, 1)
    state.addThread(base, 2)
    val result = state.setResolved("t1", true)
    assertTrue((result as ReviewResult.Ok).value.resolved)
    assertEquals(listOf("t2"), state.threads(includeResolved = false).map { it.id })
    assertEquals(listOf("t1", "t2"), state.threads(includeResolved = true).map { it.id })
  }

  @Test
  fun deleteLastCommentRemovesThread() = runTest(StandardTestDispatcher()) {
    val state = newState()
    state.addThread(base, 1)
    state.delete("c1")
    assertTrue(state.snapshot.value.threads.isEmpty())
    assertTrue(state.snapshot.value.placements.byLine.isEmpty())
  }

  @Test
  fun agentCommentNotDeletable() = runTest(StandardTestDispatcher()) {
    val state = newState()
    state.addThread(base, 1)
    state.reply("t1", Author.Agent("claude"), "hi")
    state.delete("c2")
    assertEquals(2, state.snapshot.value.threads.single().comments.size)
  }

  @Test
  fun fileAnchoredThreadsAreGroupedByFile() = runTest(StandardTestDispatcher()) {
    val state = newState()
    state.onViewerState(ui(1, base))
    advanceUntilIdle()
    state.startDraft(DraftTarget.NewThread(Anchor.File("a.kt"), Placement.OnFile(0)))
    state.updateDraft("file note")
    state.submitDraft()
    assertEquals(listOf("t1"), state.snapshot.value.placements.byFile[0]?.map { it.id })
    state.onViewerState(ui(2))
    advanceUntilIdle()
    assertEquals(listOf("t1"), state.snapshot.value.placements.orphans.map { it.id })
  }
}
