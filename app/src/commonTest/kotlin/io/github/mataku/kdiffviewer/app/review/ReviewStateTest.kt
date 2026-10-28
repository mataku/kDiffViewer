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
import io.github.mataku.kdiffviewer.core.review.Side
import io.github.mataku.kdiffviewer.core.review.Submission
import io.github.mataku.kdiffviewer.core.review.SubmissionState
import io.github.mataku.kdiffviewer.core.review.anchorOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

  private fun ReviewState.addThread(file: FileDiff, lineIndex: Int, text: String = "fix", mode: DraftMode = DraftMode.Single) {
    val anchor = anchorOf(file, 0, lineIndex)!!
    startDraft(DraftTarget.NewThread(anchor, Placement.OnLine(LineLocation(0, 0, lineIndex))))
    updateDraft(text)
    submitDraft(mode)
  }

  private fun ReviewState.addReply(threadId: String, text: String, mode: DraftMode) {
    startDraft(DraftTarget.Reply(threadId))
    updateDraft(text)
    submitDraft(mode)
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
    state.submitDraft(DraftMode.Single)
    assertTrue(state.snapshot.value.threads.isEmpty())
  }

  @Test
  fun secondDraftDoesNotReplaceNonBlankDraft() = runTest(StandardTestDispatcher()) {
    val state = newState()
    val first = DraftTarget.NewThread(anchorOf(base, 0, 1)!!, Placement.OnLine(LineLocation(0, 0, 1)))
    state.startDraft(first)
    state.updateDraft("keep")
    assertFalse(state.startDraft(DraftTarget.NewThread(anchorOf(base, 0, 2)!!, Placement.OnLine(LineLocation(0, 0, 2)))))
    assertEquals(Draft(first, "keep"), state.snapshot.value.draft)
  }

  @Test
  fun typingKeepsPlacementsAndTarget() = runTest(StandardTestDispatcher()) {
    val state = newState()
    state.addThread(base, 0)
    val target = DraftTarget.NewThread(anchorOf(base, 0, 1)!!, Placement.OnLine(LineLocation(0, 0, 1)))
    state.startDraft(target)
    val before = state.snapshot.value
    state.updateDraft("abc")
    val after = state.snapshot.value
    assertTrue(before.placements === after.placements)
    assertEquals(target, after.draft?.target)
    assertEquals("abc", after.draft?.text)
  }

  @Test
  fun blankDraftIsReplaced() = runTest(StandardTestDispatcher()) {
    val state = newState()
    state.startDraft(DraftTarget.NewThread(anchorOf(base, 0, 1)!!, Placement.OnLine(LineLocation(0, 0, 1))))
    val second = DraftTarget.NewThread(anchorOf(base, 0, 2)!!, Placement.OnLine(LineLocation(0, 0, 2)))
    assertTrue(state.startDraft(second))
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
    state.submitDraft(DraftMode.Single)
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
    state.submitDraft(DraftMode.Single)
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
  fun newThreadDraftOnFileThatLeavesTheDiffBecomesOrphanAndKeepsText() = runTest(StandardTestDispatcher()) {
    val state = newState()
    val other = file("b.kt", "x", "y")
    state.onViewerState(ui(1, base, other))
    advanceUntilIdle()
    val target = DraftTarget.NewThread(Anchor.Lines("b.kt", Side.New, 2, 2, listOf("y")), Placement.OnLine(LineLocation(1, 0, 1)))
    state.startDraft(target)
    state.updateDraft("wip")
    state.onViewerState(ui(2, base))
    advanceUntilIdle()
    val draft = state.snapshot.value.draft!!
    assertEquals("wip", draft.text)
    assertEquals(Placement.Orphan, (draft.target as DraftTarget.NewThread).placement)
    assertEquals(2L, state.snapshot.value.placements.loadId)
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
    assertEquals(ReviewResult.Err(ReviewError.ThreadNotFound("t9")), state.resolveThread("t9", true))
    assertEquals(ReviewResult.Err(ReviewError.ThreadNotFound("t9")), state.reply("t9", Author.User, "x"))
  }

  @Test
  fun resolveFiltersThreads() = runTest(StandardTestDispatcher()) {
    val state = newState()
    state.addThread(base, 1)
    state.addThread(base, 2)
    val result = state.resolveThread("t1", true)
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
    state.submitDraft(DraftMode.Single)
    assertEquals(listOf("t1"), state.snapshot.value.placements.byFile[0]?.map { it.id })
    state.onViewerState(ui(2))
    advanceUntilIdle()
    assertEquals(listOf("t1"), state.snapshot.value.placements.orphans.map { it.id })
  }

  @Test
  fun agentCommentIsNotEditable() = runTest(StandardTestDispatcher()) {
    val state = newState()
    state.addThread(base, 1)
    state.reply("t1", Author.Agent("claude"), "hi")
    state.startDraft(DraftTarget.Edit("c2"))
    assertNull(state.snapshot.value.draft)
  }

  @Test
  fun repoSwitchDuringReanchorDiscardsResult() = runTest(StandardTestDispatcher()) {
    val state = newState()
    state.onViewerState(ui(1, base))
    state.onViewerState(ui(2, base, repo = RepoPath("/other"), status = LoadStatus.Loading))
    advanceUntilIdle()
    val snapshot = state.snapshot.value
    assertEquals(0L, snapshot.settledLoadId)
    assertEquals(0L, snapshot.placements.loadId)
  }

  @Test
  fun doneAfterFailedClearsLastLoadFailed() = runTest(StandardTestDispatcher()) {
    val state = newState()
    state.onViewerState(ui(1, status = LoadStatus.Failed(ViewerError.GitFailed("bad"))))
    assertTrue(state.snapshot.value.lastLoadFailed)
    state.onViewerState(ui(2, base))
    advanceUntilIdle()
    assertTrue(!state.snapshot.value.lastLoadFailed)
    assertEquals(2L, state.snapshot.value.settledLoadId)
  }

  @Test
  fun concurrentRepliesAndSubmitsLoseNothing() = runTest(StandardTestDispatcher()) {
    val state = newState()
    state.addThread(base, 1)
    withContext(Dispatchers.Default) {
      val repliers = (1..8).map { worker ->
        async { repeat(200) { state.reply("t1", Author.Agent("w$worker"), "r") } }
      }
      val drafter = async {
        repeat(100) {
          state.startDraft(DraftTarget.NewThread(anchorOf(base, 0, 2)!!, Placement.OnLine(LineLocation(0, 0, 2))))
          state.updateDraft("x")
          state.submitDraft(DraftMode.Single)
        }
      }
      (repliers + drafter).awaitAll()
    }
    val threads = state.snapshot.value.threads
    assertEquals(101, threads.size)
    assertEquals(101, threads.map { it.id }.toSet().size)
    val commentIds = threads.flatMap { t -> t.comments.map { it.id } }
    assertEquals(1 + 1600 + 100, commentIds.size)
    assertEquals(commentIds.size, commentIds.toSet().size)
  }

  @Test
  fun singleNewThreadCreatesSubmission() = runTest(StandardTestDispatcher()) {
    val state = newState()
    state.addThread(base, 1)
    val snapshot = state.snapshot.value
    assertEquals(listOf(Submission(1, SubmissionState.Comment, null, epoch, listOf("c1"))), snapshot.submissions)
    assertEquals(0, snapshot.pendingCount)
    assertFalse(snapshot.threads.single().comments.single().pending)
  }

  @Test
  fun reviewCommentIsPendingAndHiddenFromAgents() = runTest(StandardTestDispatcher()) {
    val state = newState()
    state.addThread(base, 1, mode = DraftMode.Review)
    val snapshot = state.snapshot.value
    assertEquals(1, snapshot.pendingCount)
    assertTrue(snapshot.submissions.isEmpty())
    assertTrue(snapshot.threads.single().comments.single().pending)
    assertTrue(state.threads(includeResolved = true).isEmpty())
    assertNull(state.agentThread("t1"))
  }

  @Test
  fun reviewReplyOnPublishedThreadHidden() = runTest(StandardTestDispatcher()) {
    val state = newState()
    state.addThread(base, 1)
    state.addReply("t1", "more", DraftMode.Review)
    assertEquals(listOf("c1"), state.threads(includeResolved = false).single().comments.map { it.id })
    assertEquals(listOf("c1", "c2"), state.snapshot.value.threads.single().comments.map { it.id })
  }

  @Test
  fun submitReviewPublishesAllPending() = runTest(StandardTestDispatcher()) {
    val state = newState()
    state.addThread(base, 0, mode = DraftMode.Review)
    state.addThread(base, 2, mode = DraftMode.Review)
    state.submitReview("  summary  ", approve = false)
    val snapshot = state.snapshot.value
    assertEquals(listOf(Submission(1, SubmissionState.Comment, "summary", epoch, listOf("c1", "c2"))), snapshot.submissions)
    assertEquals(0, snapshot.pendingCount)
    assertEquals(2, state.threads(includeResolved = false).size)
  }

  @Test
  fun approveWithNothingPending() = runTest(StandardTestDispatcher()) {
    val state = newState()
    state.submitReview(null, approve = true)
    assertEquals(listOf(Submission(1, SubmissionState.Approved, null, epoch, emptyList())), state.snapshot.value.submissions)
  }

  @Test
  fun submitWithNothingIsNoOp() = runTest(StandardTestDispatcher()) {
    val state = newState()
    state.submitReview("   ", approve = false)
    assertTrue(state.snapshot.value.submissions.isEmpty())
  }

  @Test
  fun summaryOnlySubmits() = runTest(StandardTestDispatcher()) {
    val state = newState()
    state.submitReview("ship it after tests", approve = false)
    assertEquals("ship it after tests", state.snapshot.value.submissions.single().summary)
  }

  @Test
  fun oversizedSummaryIsIgnored() = runTest(StandardTestDispatcher()) {
    val state = newState()
    state.addThread(base, 1, mode = DraftMode.Review)
    state.submitReview("x".repeat(MAX_REPLY_BODY_CHARS + 1), approve = false)
    assertTrue(state.snapshot.value.submissions.isEmpty())
    assertEquals(1, state.snapshot.value.pendingCount)
  }

  @Test
  fun editKeepsPending() = runTest(StandardTestDispatcher()) {
    val state = newState()
    state.addThread(base, 1, mode = DraftMode.Review)
    state.startDraft(DraftTarget.Edit("c1"))
    state.updateDraft("edited")
    state.submitDraft(DraftMode.Single)
    val comment = state.snapshot.value.threads.single().comments.single()
    assertEquals("edited", comment.body)
    assertTrue(comment.pending)
    assertTrue(state.snapshot.value.submissions.isEmpty())
  }

  @Test
  fun deletingPendingCommentUpdatesCount() = runTest(StandardTestDispatcher()) {
    val state = newState()
    state.addThread(base, 1, mode = DraftMode.Review)
    state.delete("c1")
    assertEquals(0, state.snapshot.value.pendingCount)
  }

  @Test
  fun agentReplyToPendingThreadIsNotFound() = runTest(StandardTestDispatcher()) {
    val state = newState()
    state.addThread(base, 1, mode = DraftMode.Review)
    assertIs<ReviewResult.Err>(state.reply("t1", Author.Agent("bot"), "done"))
    assertIs<ReviewResult.Err>(state.resolveThread("t1", true))
  }

  @Test
  fun singleReplyCreatesSubmission() = runTest(StandardTestDispatcher()) {
    val state = newState()
    state.addThread(base, 1)
    state.addReply("t1", "and this", DraftMode.Single)
    assertEquals(listOf(listOf("c1"), listOf("c2")), state.snapshot.value.submissions.map { it.commentIds })
  }

  @Test
  fun repositorySwitchClearsSubmissions() = runTest(StandardTestDispatcher()) {
    val state = newState()
    state.onViewerState(ui(1, base))
    state.addThread(base, 1)
    state.addThread(base, 2, mode = DraftMode.Review)
    state.onViewerState(ui(2, base, repo = RepoPath("/other"), status = LoadStatus.Loading))
    val snapshot = state.snapshot.value
    assertTrue(snapshot.submissions.isEmpty())
    assertEquals(0, snapshot.pendingCount)
    state.submitReview(null, approve = true)
    assertEquals("r1", state.snapshot.value.submissions.single().id)
  }

  private fun TestScope.openState(): ReviewState = newState().also { it.onViewerState(ui(1, base)) }

  @Test
  fun noRepository() = runTest(StandardTestDispatcher()) {
    assertEquals(WaitOutcome.NoRepository, newState().awaitSubmissions(null))
  }

  @Test
  fun queuedSubmissionReturnsImmediately() = runTest(StandardTestDispatcher()) {
    val state = openState()
    state.addThread(base, 1)
    val outcome = assertIs<WaitOutcome.Delivered>(state.awaitSubmissions(null))
    assertEquals(listOf("r1"), outcome.submissions.map { it.id })
    state.markDelivered(outcome.generation, 1)
    assertEquals(WaitOutcome.TimedOut, state.awaitSubmissions(10))
    assertFalse(state.snapshot.value.waiting)
  }

  @Test
  fun waitResumesOnSubmit() = runTest(StandardTestDispatcher()) {
    val state = openState()
    val waiter = async { state.awaitSubmissions(null) }
    runCurrent()
    assertTrue(state.snapshot.value.waiting)
    state.addThread(base, 1, mode = DraftMode.Review)
    runCurrent()
    assertFalse(waiter.isCompleted)
    state.submitReview(null, approve = true)
    val outcome = assertIs<WaitOutcome.Delivered>(waiter.await())
    assertEquals(SubmissionState.Approved, outcome.submissions.single().state)
    assertFalse(state.snapshot.value.waiting)
  }

  @Test
  fun cursorUnchangedWithoutMarkDelivered() = runTest(StandardTestDispatcher()) {
    val state = openState()
    state.addThread(base, 1)
    assertIs<WaitOutcome.Delivered>(state.awaitSubmissions(null))
    val again = assertIs<WaitOutcome.Delivered>(state.awaitSubmissions(null))
    assertEquals(listOf("r1"), again.submissions.map { it.id })
  }

  @Test
  fun deliveryReturnsEveryUndeliveredSubmission() = runTest(StandardTestDispatcher()) {
    val state = openState()
    state.addThread(base, 0)
    state.addThread(base, 1)
    val first = assertIs<WaitOutcome.Delivered>(state.awaitSubmissions(null))
    state.markDelivered(first.generation, 1)
    state.addThread(base, 2)
    val second = assertIs<WaitOutcome.Delivered>(state.awaitSubmissions(null))
    assertEquals(listOf("r2", "r3"), second.submissions.map { it.id })
  }

  @Test
  fun timeoutLeavesCursor() = runTest(StandardTestDispatcher()) {
    val state = openState()
    assertEquals(WaitOutcome.TimedOut, state.awaitSubmissions(1_000))
    assertEquals(0, state.snapshot.value.core.deliveredThrough)
    assertFalse(state.snapshot.value.waiting)
  }

  @Test
  fun secondWaiterSupersedesFirst() = runTest(StandardTestDispatcher()) {
    val state = openState()
    val first = async { state.awaitSubmissions(null) }
    runCurrent()
    val second = async { state.awaitSubmissions(null) }
    runCurrent()
    assertEquals(WaitOutcome.Superseded, first.await())
    assertTrue(state.snapshot.value.waiting)
    state.addThread(base, 1)
    assertIs<WaitOutcome.Delivered>(second.await())
  }

  @Test
  fun repositorySwitchFailsWaiter() = runTest(StandardTestDispatcher()) {
    val state = openState()
    val waiter = async { state.awaitSubmissions(null) }
    runCurrent()
    state.onViewerState(ui(2, base, repo = RepoPath("/other"), status = LoadStatus.Loading))
    assertEquals(WaitOutcome.RepositoryChanged, waiter.await())
    assertFalse(state.snapshot.value.waiting)
  }

  @Test
  fun cancelledWaitClearsWaiting() = runTest(StandardTestDispatcher()) {
    val state = openState()
    val waiter = async { state.awaitSubmissions(null) }
    runCurrent()
    assertTrue(state.snapshot.value.waiting)
    waiter.cancel()
    runCurrent()
    assertFalse(state.snapshot.value.waiting)
  }

  @Test
  fun markDeliveredIgnoresOldGeneration() = runTest(StandardTestDispatcher()) {
    val state = openState()
    state.addThread(base, 1)
    val outcome = assertIs<WaitOutcome.Delivered>(state.awaitSubmissions(null))
    state.onViewerState(ui(2, base, repo = RepoPath("/other"), status = LoadStatus.Loading))
    state.addThread(base, 1)
    state.markDelivered(outcome.generation, 1)
    assertEquals(0, state.snapshot.value.core.deliveredThrough)
  }

  @Test
  fun markDeliveredNeverLowersCursor() = runTest(StandardTestDispatcher()) {
    val state = openState()
    state.addThread(base, 0)
    state.addThread(base, 1)
    val outcome = assertIs<WaitOutcome.Delivered>(state.awaitSubmissions(null))
    state.markDelivered(outcome.generation, 2)
    state.markDelivered(outcome.generation, 1)
    assertEquals(2, state.snapshot.value.core.deliveredThrough)
  }
}
