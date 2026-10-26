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
) {
  companion object {
    val Empty = ReviewSnapshot(emptyList(), ReviewPlacements.Empty, null, 0L, false)
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

  private var repo: RepoPath? = null
  private var threads: List<ReviewThread> = emptyList()
  private var placementById: Map<String, Placement> = emptyMap()
  private var placements = ReviewPlacements.Empty
  private var draft: Draft? = null
  private var placementsLoadId = 0L
  private var settledLoadId = 0L
  private var settledFiles = 0
  private var lastLoadFailed = false
  private var seenLoadId = 0L
  private var generation = 0
  private var nextThreadNumber = 1
  private var nextCommentNumber = 1

  fun onViewerState(ui: ViewerUiState) {
    if (ui.repo != repo) {
      reset(ui.repo)
    }
    when (ui.status) {
      LoadStatus.Done -> if (ui.loadId > seenLoadId) {
        seenLoadId = ui.loadId
        launchReanchor(ui.loadId, ui.files)
      }

      is LoadStatus.Failed -> if (ui.loadId > seenLoadId) {
        seenLoadId = ui.loadId
        settledLoadId = ui.loadId
        settledFiles = ui.files.size
        lastLoadFailed = true
        publish()
      }

      else -> Unit
    }
  }

  suspend fun awaitSettled(loadId: Long): LoadSettlement {
    val settled = snapshot.first { it.settledLoadId >= loadId }
    return LoadSettlement(settled.lastLoadFailed, settledFiles, settled.threads.count { it.outdated })
  }

  fun startDraft(target: DraftTarget) {
    val current = draft
    if (current != null && current.text.isNotBlank()) return
    val text = when (target) {
      is DraftTarget.NewThread -> ""

      is DraftTarget.Reply -> {
        if (threads.none { it.id == target.threadId }) return
        ""
      }

      is DraftTarget.Edit -> findComment(target.commentId)?.body ?: return
    }
    draft = Draft(target, text)
    publish()
  }

  fun updateDraft(text: String) {
    val current = draft ?: return
    if (current.text == text) return
    draft = current.copy(text = text)
    publish()
  }

  fun cancelDraft() {
    if (draft == null) return
    draft = null
    publish()
  }

  fun submitDraft() {
    val current = draft ?: return
    if (current.text.isBlank()) return
    when (val target = current.target) {
      is DraftTarget.NewThread -> {
        val thread = ReviewThread(
          id = "t${nextThreadNumber++}",
          anchor = target.anchor,
          comments = listOf(newComment(Author.User, current.text)),
          resolved = false,
          outdated = false,
        )
        threads = threads + thread
        placementById = placementById + (thread.id to target.placement)
      }

      is DraftTarget.Reply -> {
        val index = threads.indexOfFirst { it.id == target.threadId }
        if (index < 0) {
          draft = null
          publish()
          return
        }
        val comment = newComment(Author.User, current.text)
        replaceThread(index) { it.copy(comments = it.comments + comment) }
      }

      is DraftTarget.Edit -> {
        val index = threads.indexOfFirst { thread -> thread.comments.any { it.id == target.commentId } }
        if (index < 0) {
          draft = null
          publish()
          return
        }
        replaceThread(index) { thread ->
          thread.copy(comments = thread.comments.map { if (it.id == target.commentId) it.copy(body = current.text) else it })
        }
      }
    }
    draft = null
    rebuildPlacements()
    publish()
  }

  fun reply(threadId: String, author: Author, body: String): ReviewResult<Comment> {
    val index = threads.indexOfFirst { it.id == threadId }
    if (index < 0) return ReviewResult.Err(ReviewError.ThreadNotFound(threadId))
    val comment = newComment(author, body)
    replaceThread(index) { it.copy(comments = it.comments + comment) }
    rebuildPlacements()
    publish()
    return ReviewResult.Ok(comment)
  }

  fun setResolved(threadId: String, resolved: Boolean): ReviewResult<ReviewThread> {
    val index = threads.indexOfFirst { it.id == threadId }
    if (index < 0) return ReviewResult.Err(ReviewError.ThreadNotFound(threadId))
    replaceThread(index) { it.copy(resolved = resolved) }
    rebuildPlacements()
    publish()
    return ReviewResult.Ok(threads[index])
  }

  fun delete(commentId: String) {
    val index = threads.indexOfFirst { thread -> thread.comments.any { it.id == commentId && it.author == Author.User } }
    if (index < 0) return
    val thread = threads[index]
    val remaining = thread.comments.filter { it.id != commentId }
    if (remaining.isEmpty()) {
      threads = threads.filterIndexed { i, _ -> i != index }
      placementById = placementById - thread.id
    } else {
      replaceThread(index) { it.copy(comments = remaining) }
    }
    val dropsDraft = when (val target = draft?.target) {
      is DraftTarget.Edit -> target.commentId == commentId
      is DraftTarget.Reply -> remaining.isEmpty() && target.threadId == thread.id
      else -> false
    }
    if (dropsDraft) draft = null
    rebuildPlacements()
    publish()
  }

  fun threads(includeResolved: Boolean): List<ReviewThread> = if (includeResolved) threads else threads.filterNot { it.resolved }

  private fun reset(newRepo: RepoPath?) {
    generation++
    repo = newRepo
    threads = emptyList()
    placementById = emptyMap()
    placements = ReviewPlacements.Empty
    draft = null
    placementsLoadId = 0L
    settledLoadId = 0L
    settledFiles = 0
    lastLoadFailed = false
    seenLoadId = 0L
    nextThreadNumber = 1
    nextCommentNumber = 1
    publish()
  }

  private fun newComment(author: Author, body: String) = Comment("c${nextCommentNumber++}", author, body, now())

  private fun findComment(commentId: String): Comment? = threads.firstNotNullOfOrNull { thread -> thread.comments.firstOrNull { it.id == commentId } }

  private inline fun replaceThread(index: Int, transform: (ReviewThread) -> ReviewThread) {
    threads = threads.toMutableList().also { it[index] = transform(it[index]) }
  }

  private fun rebuildPlacements() {
    placements = buildPlacements(placementsLoadId, threads, placementById)
  }

  private fun publish() {
    mutableSnapshot.value = ReviewSnapshot(threads, placements, draft, settledLoadId, lastLoadFailed)
  }

  private class Reanchored(
    val threads: Map<String, Pair<ReviewThread, Placement>>,
    val draft: DraftTarget.NewThread?,
  )

  private fun launchReanchor(loadId: Long, files: List<FileDiff>) {
    val launchedGeneration = generation
    val inputThreads = threads
    val inputDraft = draft?.target as? DraftTarget.NewThread
    scope.launch {
      val result = withContext(reanchorContext) {
        Reanchored(
          inputThreads.associate { it.id to reanchor(it, files) },
          inputDraft?.let { target ->
            val (anchor, placement) = reanchor(target.anchor, files)
            DraftTarget.NewThread(anchor, placement)
          },
        )
      }
      if (launchedGeneration != generation || seenLoadId != loadId) return@launch
      val currentDraft = draft?.target as? DraftTarget.NewThread
      if (threads.any { it.id !in result.threads } || currentDraft != inputDraft) {
        launchReanchor(loadId, files)
        return@launch
      }
      apply(loadId, files.size, result)
    }
  }

  private fun apply(loadId: Long, fileCount: Int, result: Reanchored) {
    threads = threads.map { thread ->
      val reanchored = result.threads.getValue(thread.id).first
      thread.copy(anchor = reanchored.anchor, outdated = reanchored.outdated)
    }
    placementById = result.threads.mapValues { it.value.second }
    val current = draft
    val reanchoredDraft = result.draft
    if (current != null && reanchoredDraft != null) {
      draft = current.copy(target = reanchoredDraft)
    }
    placementsLoadId = loadId
    settledLoadId = maxOf(settledLoadId, loadId)
    settledFiles = fileCount
    lastLoadFailed = false
    rebuildPlacements()
    publish()
  }
}
