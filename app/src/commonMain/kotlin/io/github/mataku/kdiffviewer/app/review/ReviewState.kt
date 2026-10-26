package io.github.mataku.kdiffviewer.app.review

import androidx.compose.runtime.Immutable
import io.github.mataku.kdiffviewer.app.LoadStatus
import io.github.mataku.kdiffviewer.app.ViewerUiState
import io.github.mataku.kdiffviewer.core.model.FileDiff
import io.github.mataku.kdiffviewer.core.model.RepoPath
import io.github.mataku.kdiffviewer.core.review.Author
import io.github.mataku.kdiffviewer.core.review.Comment
import io.github.mataku.kdiffviewer.core.review.Placement
import io.github.mataku.kdiffviewer.core.review.ReviewThread
import io.github.mataku.kdiffviewer.core.review.reanchor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
) {
  companion object {
    val Empty = ReviewCore(null, emptyMap(), 0, 0L, 0, 1, 1)
  }
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
) {
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
    return LoadSettlement(settled.lastLoadFailed, settled.core.settledFiles, settled.threads.count { it.outdated })
  }

  fun startDraft(target: DraftTarget) {
    mutate { current ->
      val existing = current.draft
      if (existing != null && existing.text.isNotBlank()) return@mutate current to Unit
      val text = when (target) {
        is DraftTarget.NewThread -> ""

        is DraftTarget.Reply -> {
          if (current.threads.none { it.id == target.threadId }) return@mutate current to Unit
          ""
        }

        is DraftTarget.Edit -> {
          val comment = current.findComment(target.commentId)
          if (comment == null || comment.author != Author.User) return@mutate current to Unit
          comment.body
        }
      }
      current.copy(draft = Draft(target, text)) to Unit
    }
  }

  fun updateDraft(text: String) {
    mutate { current ->
      val draft = current.draft ?: return@mutate current to Unit
      if (draft.text == text) return@mutate current to Unit
      current.copy(draft = draft.copy(text = text)) to Unit
    }
  }

  fun cancelDraft() {
    mutate { current ->
      if (current.draft == null) current to Unit else current.copy(draft = null) to Unit
    }
  }

  fun submitDraft() {
    mutate { current ->
      val draft = current.draft ?: return@mutate current to Unit
      if (draft.text.isBlank()) return@mutate current to Unit
      val core = current.core
      val next = when (val target = draft.target) {
        is DraftTarget.NewThread -> {
          val comment = Comment("c${core.nextCommentNumber}", Author.User, draft.text, now())
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
            val comment = Comment("c${core.nextCommentNumber}", Author.User, draft.text, now())
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
      next.copy(draft = null) to Unit
    }
  }

  fun reply(threadId: String, author: Author, body: String): ReviewResult<Comment> = mutate { current ->
    val index = current.threads.indexOfFirst { it.id == threadId }
    if (index < 0) return@mutate current to ReviewResult.Err(ReviewError.ThreadNotFound(threadId))
    val core = current.core
    val comment = Comment("c${core.nextCommentNumber}", author, body, now())
    val next = current.withThreads(
      current.threads.replaced(index) { it.copy(comments = it.comments + comment) },
      core.copy(nextCommentNumber = core.nextCommentNumber + 1),
    )
    next to ReviewResult.Ok(comment)
  }

  fun setResolved(threadId: String, resolved: Boolean): ReviewResult<ReviewThread> = mutate { current ->
    val index = current.threads.indexOfFirst { it.id == threadId }
    if (index < 0) return@mutate current to ReviewResult.Err(ReviewError.ThreadNotFound(threadId))
    val next = current.withThreads(current.threads.replaced(index) { it.copy(resolved = resolved) }, current.core)
    next to ReviewResult.Ok(next.threads[index])
  }

  fun delete(commentId: String) {
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
    val all = snapshot.value.threads
    return if (includeResolved) all else all.filterNot { it.resolved }
  }

  private fun ReviewSnapshot.findComment(commentId: String): Comment? = threads.firstNotNullOfOrNull { thread -> thread.comments.firstOrNull { it.id == commentId } }

  private inline fun List<ReviewThread>.replaced(index: Int, transform: (ReviewThread) -> ReviewThread): List<ReviewThread> = toMutableList().also { it[index] = transform(it[index]) }

  private fun ReviewSnapshot.withThreads(threads: List<ReviewThread>, core: ReviewCore): ReviewSnapshot = copy(
    threads = threads,
    placements = buildPlacements(placements.loadId, threads, core.placementById),
    core = core,
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
