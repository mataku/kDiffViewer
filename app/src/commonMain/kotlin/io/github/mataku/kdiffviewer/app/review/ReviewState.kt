package io.github.mataku.kdiffviewer.app.review

import androidx.compose.runtime.Immutable
import io.github.mataku.kdiffviewer.app.LoadStatus
import io.github.mataku.kdiffviewer.app.ViewerUiState
import io.github.mataku.kdiffviewer.app.ui.review.ReviewActions
import io.github.mataku.kdiffviewer.core.model.FileDiff
import io.github.mataku.kdiffviewer.core.model.RepoPath
import io.github.mataku.kdiffviewer.core.review.Author
import io.github.mataku.kdiffviewer.core.review.Comment
import io.github.mataku.kdiffviewer.core.review.Placement
import io.github.mataku.kdiffviewer.core.review.ReviewThread
import io.github.mataku.kdiffviewer.core.review.Submission
import io.github.mataku.kdiffviewer.core.review.SubmissionState
import io.github.mataku.kdiffviewer.core.review.isPending
import io.github.mataku.kdiffviewer.core.review.published
import io.github.mataku.kdiffviewer.core.review.reanchor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.CoroutineContext
import kotlin.time.Instant

@Immutable
data class ReviewSnapshot(
  val threads: List<ReviewThread>,
  val placements: ReviewPlacements,
  val draft: Draft?,
  val settledLoadId: Long,
  val lastLoadFailed: Boolean,
  val core: ReviewCore = ReviewCore.Empty,
  val submissions: List<Submission> = emptyList(),
  val pendingCount: Int = 0,
  val waiting: Boolean = false,
) {
  companion object {
    val Empty = ReviewSnapshot(emptyList(), ReviewPlacements.Empty, null, 0L, false)
  }
}

@Immutable
data class ReviewCore(
  val repo: RepoPath?,
  val placementById: Map<String, Placement>,
  val settledFiles: Int,
  val seenLoadId: Long,
  val generation: Int,
  val nextThreadNumber: Int,
  val nextCommentNumber: Int,
  val nextSubmissionNumber: Int = 1,
  val deliveredThrough: Int = 0,
  val claimedThrough: Int = 0,
  val waiterToken: Int = 0,
) {
  companion object {
    val Empty = ReviewCore(null, emptyMap(), 0, 0L, 0, 1, 1)
  }
}

enum class DraftMode { Single, Review }

sealed interface WaitOutcome {
  data class Delivered(val submissions: List<Submission>, val generation: Int, val threads: List<ReviewThread>) : WaitOutcome

  data object TimedOut : WaitOutcome

  data object Superseded : WaitOutcome

  data object RepositoryChanged : WaitOutcome

  data object NoRepository : WaitOutcome
}

data class LoadSettlement(val failed: Boolean, val files: Int, val outdated: Int)

sealed interface ReviewError {
  data class ThreadNotFound(val id: String) : ReviewError
}

sealed interface ReviewResult<out T> {
  data class Ok<T>(val value: T) : ReviewResult<T>

  data class Err(val error: ReviewError) : ReviewResult<Nothing>
}

class ReviewState(
  private val scope: CoroutineScope,
  private val reanchorContext: CoroutineContext,
  private val now: () -> Instant,
) : ReviewActions {
  private val mutableSnapshot = MutableStateFlow(ReviewSnapshot.Empty)
  val snapshot: StateFlow<ReviewSnapshot> = mutableSnapshot.asStateFlow()

  private inline fun <R> mutate(block: (ReviewSnapshot) -> Pair<ReviewSnapshot, R>): R {
    while (true) {
      val current = mutableSnapshot.value
      val (next, result) = block(current)
      if (next === current || mutableSnapshot.compareAndSet(current, next)) return result
    }
  }

  fun onViewerState(ui: ViewerUiState) {
    val launch = mutate { current ->
      var next = current
      if (ui.repo != next.core.repo) {
        next = ReviewSnapshot(
          emptyList(),
          ReviewPlacements.Empty,
          null,
          0L,
          false,
          ReviewCore.Empty.copy(repo = ui.repo, generation = next.core.generation + 1),
        )
      }
      var launchReanchor = false
      when (ui.status) {
        LoadStatus.Done -> if (ui.loadId > next.core.seenLoadId) {
          next = next.copy(core = next.core.copy(seenLoadId = ui.loadId))
          launchReanchor = true
        }

        is LoadStatus.Failed -> if (ui.loadId > next.core.seenLoadId) {
          next = next.copy(
            settledLoadId = ui.loadId,
            lastLoadFailed = true,
            core = next.core.copy(seenLoadId = ui.loadId, settledFiles = ui.files.size),
          )
        }

        else -> Unit
      }
      next to launchReanchor
    }
    if (launch) launchReanchor(ui.loadId, ui.files)
  }

  suspend fun awaitSettled(loadId: Long): LoadSettlement {
    val settled = snapshot.first { it.settledLoadId >= loadId }
    return LoadSettlement(settled.lastLoadFailed, settled.core.settledFiles, settled.threads.count { it.outdated && !it.isPending })
  }

  override fun startDraft(target: DraftTarget): Boolean = mutate { current ->
    val existing = current.draft
    if (existing != null && existing.text.isNotBlank()) return@mutate current to false
    val text = when (target) {
      is DraftTarget.NewThread -> ""

      is DraftTarget.Reply -> {
        if (current.threads.none { it.id == target.threadId }) return@mutate current to false
        ""
      }

      is DraftTarget.Edit -> {
        val comment = current.findComment(target.commentId)
        if (comment == null || comment.author != Author.User) return@mutate current to false
        comment.body
      }
    }
    current.copy(draft = Draft(target, text)) to true
  }

  override fun updateDraft(text: String) {
    mutate { current ->
      val draft = current.draft ?: return@mutate current to Unit
      if (draft.text == text) return@mutate current to Unit
      current.copy(draft = draft.copy(text = text)) to Unit
    }
  }

  override fun cancelDraft() {
    mutate { current ->
      if (current.draft == null) current to Unit else current.copy(draft = null) to Unit
    }
  }

  override fun submitDraft(mode: DraftMode) {
    mutate { current ->
      val draft = current.draft ?: return@mutate current to Unit
      if (draft.text.isBlank()) return@mutate current to Unit
      val core = current.core
      val target = draft.target
      val pending = mode == DraftMode.Review || (target is DraftTarget.Reply && current.threads.any { it.id == target.threadId && it.isPending })
      val next = when (val target = draft.target) {
        is DraftTarget.NewThread -> {
          val comment = Comment("c${core.nextCommentNumber}", Author.User, draft.text, now(), pending)
          val thread = ReviewThread("t${core.nextThreadNumber}", target.anchor, listOf(comment), resolved = false, outdated = false)
          current.withThreads(
            current.threads + thread,
            core.copy(
              placementById = core.placementById + (thread.id to target.placement),
              nextThreadNumber = core.nextThreadNumber + 1,
              nextCommentNumber = core.nextCommentNumber + 1,
            ),
          )
        }

        is DraftTarget.Reply -> {
          val index = current.threads.indexOfFirst { it.id == target.threadId }
          if (index < 0) {
            current
          } else {
            val comment = Comment("c${core.nextCommentNumber}", Author.User, draft.text, now(), pending)
            current.withThreads(
              current.threads.replaced(index) { it.copy(comments = it.comments + comment) },
              core.copy(nextCommentNumber = core.nextCommentNumber + 1),
            )
          }
        }

        is DraftTarget.Edit -> {
          val index = current.threads.indexOfFirst { thread -> thread.comments.any { it.id == target.commentId } }
          if (index < 0) {
            current
          } else {
            current.withThreads(
              current.threads.replaced(index) { thread ->
                thread.copy(comments = thread.comments.map { if (it.id == target.commentId) it.copy(body = draft.text) else it })
              },
              core,
            )
          }
        }
      }
      val published = next !== current && draft.target !is DraftTarget.Edit && !pending
      val delivered = if (published) next.withSubmission(SubmissionState.Comment, null, listOf("c${core.nextCommentNumber}")) else next
      delivered.copy(draft = null) to Unit
    }
  }

  override fun submitReview(summary: String?, approve: Boolean) {
    mutate { current ->
      val text = summary?.trim()?.takeIf { it.isNotEmpty() }
      if (text != null && text.length > MAX_REPLY_BODY_CHARS) return@mutate current to Unit
      val pendingIds = current.threads.flatMap { thread -> thread.comments.filter { it.pending }.map { it.id } }
      if (pendingIds.isEmpty() && text == null && !approve) return@mutate current to Unit
      val threads = if (pendingIds.isEmpty()) {
        current.threads
      } else {
        current.threads.map { thread ->
          if (thread.comments.none { it.pending }) thread else thread.copy(comments = thread.comments.map { it.copy(pending = false) })
        }
      }
      val state = if (approve) SubmissionState.Approved else SubmissionState.Comment
      current.withThreads(threads, current.core).withSubmission(state, text, pendingIds) to Unit
    }
  }

  suspend fun awaitSubmissions(timeoutMillis: Long?): WaitOutcome {
    val entry = mutate { current ->
      if (current.core.repo == null) return@mutate current to null
      val token = current.core.waiterToken + 1
      current.copy(waiting = true, core = current.core.copy(waiterToken = token)) to (current.core.generation to token)
    } ?: return WaitOutcome.NoRepository
    val (generation, token) = entry
    try {
      val wait: suspend () -> WaitOutcome = {
        var outcome: WaitOutcome? = null
        while (outcome == null) {
          snapshot.first { it.core.generation != generation || it.core.waiterToken != token || it.submissions.any { s -> s.number > it.core.claimedThrough } }
          outcome = mutate { current -> current.claim(generation, token) }
        }
        outcome
      }
      return if (timeoutMillis == null) wait() else withTimeoutOrNull(timeoutMillis) { wait() } ?: WaitOutcome.TimedOut
    } finally {
      mutate { current ->
        if (current.core.generation == generation && current.core.waiterToken == token && current.waiting) {
          current.copy(waiting = false) to Unit
        } else {
          current to Unit
        }
      }
    }
  }

  private fun ReviewSnapshot.claim(generation: Int, token: Int): Pair<ReviewSnapshot, WaitOutcome?> {
    if (core.generation != generation) return this to WaitOutcome.RepositoryChanged
    if (core.waiterToken != token) return this to WaitOutcome.Superseded
    val claimed = submissions.filter { it.number > core.claimedThrough }
    if (claimed.isEmpty()) return this to null
    val commentIds = claimed.flatMapTo(HashSet()) { it.commentIds }
    val delivered = threads.filter { thread -> thread.comments.any { it.id in commentIds } }.mapNotNull { it.published() }
    return copy(core = core.copy(claimedThrough = claimed.last().number)) to WaitOutcome.Delivered(claimed, generation, delivered)
  }

  fun markDelivered(delivery: WaitOutcome.Delivered) {
    val through = delivery.submissions.last().number
    mutate { current ->
      if (current.core.generation != delivery.generation || through <= current.core.deliveredThrough) return@mutate current to Unit
      current.copy(core = current.core.copy(deliveredThrough = through, claimedThrough = maxOf(current.core.claimedThrough, through))) to Unit
    }
  }

  fun releaseDelivery(delivery: WaitOutcome.Delivered) {
    val from = delivery.submissions.first().number
    mutate { current ->
      if (current.core.generation != delivery.generation || current.core.claimedThrough < from) return@mutate current to Unit
      val claimed = maxOf(current.core.deliveredThrough, from - 1)
      if (claimed == current.core.claimedThrough) return@mutate current to Unit
      current.copy(core = current.core.copy(claimedThrough = claimed)) to Unit
    }
  }

  fun agentThread(threadId: String): ReviewThread? = snapshot.value.threads.firstOrNull { it.id == threadId }?.published()

  private fun ReviewSnapshot.withSubmission(state: SubmissionState, summary: String?, commentIds: List<String>): ReviewSnapshot {
    val number = core.nextSubmissionNumber
    return copy(
      submissions = submissions + Submission(number, state, summary, now(), commentIds),
      core = core.copy(nextSubmissionNumber = number + 1),
    )
  }

  fun reply(threadId: String, author: Author, body: String): ReviewResult<Comment> = mutate { current ->
    val index = current.agentThreadIndex(threadId)
    if (index < 0) return@mutate current to ReviewResult.Err(ReviewError.ThreadNotFound(threadId))
    val core = current.core
    val comment = Comment("c${core.nextCommentNumber}", author, body, now())
    val next = current.withThreads(
      current.threads.replaced(index) { it.copy(comments = it.comments + comment) },
      core.copy(nextCommentNumber = core.nextCommentNumber + 1),
    )
    next to ReviewResult.Ok(comment)
  }

  override fun setResolved(threadId: String, resolved: Boolean) {
    resolveThread(threadId, resolved)
  }

  fun resolveThread(threadId: String, resolved: Boolean): ReviewResult<ReviewThread> = mutate { current ->
    val index = current.agentThreadIndex(threadId)
    if (index < 0) return@mutate current to ReviewResult.Err(ReviewError.ThreadNotFound(threadId))
    val next = current.withThreads(current.threads.replaced(index) { it.copy(resolved = resolved) }, current.core)
    next to ReviewResult.Ok(next.threads[index].published()!!)
  }

  private fun ReviewSnapshot.agentThreadIndex(threadId: String): Int = threads.indexOfFirst { it.id == threadId && !it.isPending }

  override fun delete(commentId: String) {
    mutate { current ->
      val index = current.threads.indexOfFirst { thread -> thread.comments.any { it.id == commentId && it.author == Author.User } }
      if (index < 0) return@mutate current to Unit
      val thread = current.threads[index]
      val remaining = thread.comments.filter { it.id != commentId }
      val next = if (remaining.isEmpty()) {
        current.withThreads(
          current.threads.filterIndexed { i, _ -> i != index },
          current.core.copy(placementById = current.core.placementById - thread.id),
        )
      } else {
        current.withThreads(current.threads.replaced(index) { it.copy(comments = remaining) }, current.core)
      }
      val dropsDraft = when (val target = current.draft?.target) {
        is DraftTarget.Edit -> target.commentId == commentId
        is DraftTarget.Reply -> remaining.isEmpty() && target.threadId == thread.id
        else -> false
      }
      (if (dropsDraft) next.copy(draft = null) else next) to Unit
    }
  }

  fun threads(includeResolved: Boolean): List<ReviewThread> {
    val all = snapshot.value.threads.mapNotNull { it.published() }
    return if (includeResolved) all else all.filterNot { it.resolved }
  }

  private fun ReviewSnapshot.findComment(commentId: String): Comment? = threads.firstNotNullOfOrNull { thread -> thread.comments.firstOrNull { it.id == commentId } }

  private inline fun List<ReviewThread>.replaced(index: Int, transform: (ReviewThread) -> ReviewThread): List<ReviewThread> = toMutableList().also { it[index] = transform(it[index]) }

  private fun ReviewSnapshot.withThreads(threads: List<ReviewThread>, core: ReviewCore): ReviewSnapshot = copy(
    threads = threads,
    placements = buildPlacements(placements.loadId, threads, core.placementById),
    core = core,
    pendingCount = threads.sumOf { thread -> thread.comments.count { it.pending } },
  )

  private class Reanchored(
    val threads: Map<String, Pair<ReviewThread, Placement>>,
    val draft: DraftTarget.NewThread?,
  )

  private fun launchReanchor(loadId: Long, files: List<FileDiff>) {
    val input = mutableSnapshot.value
    val inputDraft = input.draft?.target as? DraftTarget.NewThread
    scope.launch {
      val result = withContext(reanchorContext) {
        Reanchored(
          input.threads.associate { it.id to reanchor(it, files) },
          inputDraft?.let { target ->
            val (anchor, placement) = reanchor(target.anchor, files)
            DraftTarget.NewThread(anchor, placement)
          },
        )
      }
      val retry = mutate { current ->
        if (current.core.generation != input.core.generation || current.core.seenLoadId != loadId) {
          return@mutate current to false
        }
        val currentDraft = current.draft?.target as? DraftTarget.NewThread
        if (current.threads.any { it.id !in result.threads } || currentDraft != inputDraft) {
          return@mutate current to true
        }
        val threads = current.threads.map { thread ->
          val reanchored = result.threads.getValue(thread.id).first
          thread.copy(anchor = reanchored.anchor, outdated = reanchored.outdated)
        }
        val core = current.core.copy(
          placementById = result.threads.mapValues { it.value.second },
          settledFiles = files.size,
        )
        val draft = current.draft
        val reanchoredDraft = result.draft
        current.copy(
          threads = threads,
          placements = buildPlacements(loadId, threads, core.placementById),
          draft = if (draft != null && reanchoredDraft != null) draft.copy(target = reanchoredDraft) else draft,
          settledLoadId = maxOf(current.settledLoadId, loadId),
          lastLoadFailed = false,
          core = core,
        ) to false
      }
      if (retry) launchReanchor(loadId, files)
    }
  }
}
