package io.github.mataku.kdiffviewer.core.review

import kotlin.time.Instant

enum class SubmissionState { Comment, Approved }

data class Submission(
  val number: Int,
  val state: SubmissionState,
  val summary: String?,
  val submittedAt: Instant,
  val commentIds: List<String>,
) {
  val id: String get() = "r$number"
}
