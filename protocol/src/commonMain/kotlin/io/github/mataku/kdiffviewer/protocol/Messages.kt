package io.github.mataku.kdiffviewer.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

const val PROTOCOL_VERSION = 1

const val MAX_WAIT_SECONDS = 86_400

@Serializable
data class RequestEnvelope(
  val v: Int,
  val method: String,
  val params: JsonObject = JsonObject(emptyMap()),
)

@Serializable
data class ResponseEnvelope(
  val ok: Boolean,
  val result: JsonElement? = null,
  val error: ErrorBody? = null,
)

@Serializable
data class ErrorBody(val code: String, val message: String)

object Methods {
  const val WINDOW_INFO = "window.info"
  const val THREAD_LIST = "thread.list"
  const val THREAD_REPLY = "thread.reply"
  const val THREAD_RESOLVE = "thread.resolve"
  const val RELOAD = "reload"
  const val REVIEW_WAIT = "review.wait"
}

object ErrorCodes {
  const val THREAD_NOT_FOUND = "thread_not_found"
  const val NO_REPOSITORY = "no_repository"
  const val BAD_REQUEST = "bad_request"
  const val UNSUPPORTED_VERSION = "unsupported_version"
  const val LOAD_FAILED = "load_failed"
  const val REPOSITORY_CHANGED = "repository_changed"
  const val SUPERSEDED = "superseded"
}

@Serializable
data class AuthorDto(val kind: String, val name: String? = null)

@Serializable
data class CommentDto(
  val id: String,
  val author: AuthorDto,
  val body: String,
  val createdAt: String,
)

@Serializable
data class ThreadDto(
  val id: String,
  val path: String,
  val side: String? = null,
  val startLine: Int? = null,
  val endLine: Int? = null,
  val snapshot: List<String>,
  val resolved: Boolean,
  val outdated: Boolean,
  val comments: List<CommentDto>,
)

@Serializable
data class ThreadListParams(val all: Boolean = false)

@Serializable
data class ThreadListResult(val threads: List<ThreadDto>)

@Serializable
data class ThreadReplyParams(
  val threadId: String,
  val body: String,
  val author: String = "agent",
)

@Serializable
data class ThreadResolveParams(val threadId: String)

@Serializable
data class ReloadResult(val files: Int, val outdated: Int)

@Serializable
data class ReviewWaitParams(val timeoutSeconds: Int? = null)

@Serializable
data class ReviewDto(
  val id: String,
  val state: String,
  val summary: String? = null,
  val submittedAt: String,
  val commentIds: List<String>,
)

@Serializable
data class ReviewWaitResult(
  val timedOut: Boolean,
  val reviews: List<ReviewDto>,
  val threads: List<ThreadDto>,
)

@Serializable
data class WindowInfoResult(
  val pid: Long,
  val repoRoot: String? = null,
  val protocolVersion: Int,
)

@Serializable
data class RegistryEntry(
  val pid: Long,
  val repoRoot: String? = null,
  val socket: String,
  val startedAt: String,
  val protocolVersion: Int,
)
