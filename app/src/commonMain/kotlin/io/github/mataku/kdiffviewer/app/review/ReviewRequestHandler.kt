package io.github.mataku.kdiffviewer.app.review

import io.github.mataku.kdiffviewer.core.review.Author
import io.github.mataku.kdiffviewer.protocol.Codec
import io.github.mataku.kdiffviewer.protocol.CommentDto
import io.github.mataku.kdiffviewer.protocol.ErrorCodes
import io.github.mataku.kdiffviewer.protocol.MAX_WAIT_SECONDS
import io.github.mataku.kdiffviewer.protocol.Methods
import io.github.mataku.kdiffviewer.protocol.PROTOCOL_VERSION
import io.github.mataku.kdiffviewer.protocol.ReloadResult
import io.github.mataku.kdiffviewer.protocol.RequestEnvelope
import io.github.mataku.kdiffviewer.protocol.ReviewWaitParams
import io.github.mataku.kdiffviewer.protocol.ReviewWaitResult
import io.github.mataku.kdiffviewer.protocol.ThreadDto
import io.github.mataku.kdiffviewer.protocol.ThreadListParams
import io.github.mataku.kdiffviewer.protocol.ThreadListResult
import io.github.mataku.kdiffviewer.protocol.ThreadReplyParams
import io.github.mataku.kdiffviewer.protocol.ThreadResolveParams
import io.github.mataku.kdiffviewer.protocol.WindowInfoResult
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement

const val MAX_REPLY_BODY_CHARS = 65_536

private const val DEFAULT_AGENT = "agent"

class ReviewRequestHandler(
  private val review: ReviewState,
  private val reload: suspend () -> Long?,
  private val info: () -> WindowInfoResult,
) {
  suspend fun handle(line: String): Reply = try {
    val request = Codec.decodeRequest(line)
    if (request.v != PROTOCOL_VERSION) {
      Reply(Codec.failure(ErrorCodes.UNSUPPORTED_VERSION, "Unsupported protocol version ${request.v}, expected $PROTOCOL_VERSION"))
    } else if (request.method == Methods.REVIEW_WAIT) {
      waitForReview(Codec.params(request))
    } else {
      Reply(dispatch(request))
    }
  } catch (e: CancellationException) {
    throw e
  } catch (e: SerializationException) {
    Reply(Codec.failure(ErrorCodes.BAD_REQUEST, e.message ?: "Malformed request"))
  } catch (e: IllegalArgumentException) {
    Reply(Codec.failure(ErrorCodes.BAD_REQUEST, e.message ?: "Malformed request"))
  } catch (e: Exception) {
    Reply(Codec.failure(ErrorCodes.BAD_REQUEST, e.message ?: "Request failed"))
  }

  private suspend fun waitForReview(params: ReviewWaitParams): Reply {
    val seconds = params.timeoutSeconds
    if (seconds != null && seconds !in 1..MAX_WAIT_SECONDS) {
      return Reply(Codec.failure(ErrorCodes.BAD_REQUEST, "timeoutSeconds must be between 1 and $MAX_WAIT_SECONDS"))
    }
    return when (val outcome = review.awaitSubmissions(seconds?.let { it * 1_000L })) {
      is WaitOutcome.Delivered -> {
        val commentIds = outcome.submissions.flatMapTo(HashSet()) { it.commentIds }
        val threads = review.snapshot.value.threads
          .filter { thread -> thread.comments.any { it.id in commentIds } }
          .mapNotNull { review.agentThread(it.id)?.toDto() }
        val result = ReviewWaitResult(false, outcome.submissions.map { it.toDto() }, threads)
        Reply(ok(Codec.json.encodeToJsonElement(ReviewWaitResult.serializer(), result))) {
          review.markDelivered(outcome.generation, outcome.submissions.last().number)
        }
      }

      WaitOutcome.TimedOut -> Reply(ok(Codec.json.encodeToJsonElement(ReviewWaitResult.serializer(), ReviewWaitResult(true, emptyList(), emptyList()))))

      WaitOutcome.Superseded -> Reply(Codec.failure(ErrorCodes.SUPERSEDED, "Another kdv review wait replaced this one"))

      WaitOutcome.RepositoryChanged -> Reply(Codec.failure(ErrorCodes.REPOSITORY_CHANGED, "The window opened another repository"))

      WaitOutcome.NoRepository -> Reply(Codec.failure(ErrorCodes.NO_REPOSITORY, "No repository is open"))
    }
  }

  private suspend fun dispatch(request: RequestEnvelope): String = when (request.method) {
    Methods.WINDOW_INFO -> ok(Codec.json.encodeToJsonElement(WindowInfoResult.serializer(), info()))

    Methods.THREAD_LIST -> {
      val params = Codec.params<ThreadListParams>(request)
      val threads = review.threads(params.all).map { it.toDto() }
      ok(Codec.json.encodeToJsonElement(ThreadListResult.serializer(), ThreadListResult(threads)))
    }

    Methods.THREAD_REPLY -> {
      val params = Codec.params<ThreadReplyParams>(request)
      if (params.body.length > MAX_REPLY_BODY_CHARS) {
        Codec.failure(ErrorCodes.BAD_REQUEST, "Reply body exceeds $MAX_REPLY_BODY_CHARS characters")
      } else if (params.body.isBlank()) {
        Codec.failure(ErrorCodes.BAD_REQUEST, "Reply body is blank")
      } else {
        when (val result = review.reply(params.threadId, Author.Agent(params.author.ifBlank { DEFAULT_AGENT }), params.body)) {
          is ReviewResult.Ok -> ok(Codec.json.encodeToJsonElement(CommentDto.serializer(), result.value.toDto()))
          is ReviewResult.Err -> reviewFailure(result.error)
        }
      }
    }

    Methods.THREAD_RESOLVE -> {
      val params = Codec.params<ThreadResolveParams>(request)
      when (val result = review.resolveThread(params.threadId, true)) {
        is ReviewResult.Ok -> ok(Codec.json.encodeToJsonElement(ThreadDto.serializer(), result.value.toDto()))
        is ReviewResult.Err -> reviewFailure(result.error)
      }
    }

    Methods.RELOAD -> {
      val loadId = reload()
      if (loadId == null) {
        Codec.failure(ErrorCodes.NO_REPOSITORY, "No repository is open")
      } else {
        val settlement = review.awaitSettled(loadId)
        if (settlement.failed) {
          Codec.failure(ErrorCodes.LOAD_FAILED, "Reloading the diff failed")
        } else {
          ok(Codec.json.encodeToJsonElement(ReloadResult.serializer(), ReloadResult(settlement.files, settlement.outdated)))
        }
      }
    }

    else -> Codec.failure(ErrorCodes.BAD_REQUEST, "Unknown method ${request.method}")
  }

  private fun ok(result: JsonElement): String = Codec.success(result)

  private fun reviewFailure(error: ReviewError): String = when (error) {
    is ReviewError.ThreadNotFound -> Codec.failure(ErrorCodes.THREAD_NOT_FOUND, "No thread ${error.id}")
  }
}
