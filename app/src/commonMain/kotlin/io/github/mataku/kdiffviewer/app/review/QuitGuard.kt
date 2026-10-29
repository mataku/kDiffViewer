package io.github.mataku.kdiffviewer.app.review

import androidx.compose.runtime.Immutable

@Immutable
data class QuitWarning(val pendingComments: Int, val agentWaiting: Boolean, val undeliveredReviews: Int)

fun quitWarningOf(snapshot: ReviewSnapshot): QuitWarning? {
  val undelivered = snapshot.submissions.count { it.number > snapshot.core.deliveredThrough }
  if (snapshot.pendingCount == 0 && !snapshot.waiting && undelivered == 0) return null
  return QuitWarning(snapshot.pendingCount, snapshot.waiting, undelivered)
}

fun QuitWarning.messageLines(): List<String> = buildList {
  if (pendingComments > 0) add(if (pendingComments == 1) "1 pending comment will be lost." else "$pendingComments pending comments will be lost.")
  if (agentWaiting) add("A coding agent is waiting for your review.")
  if (undeliveredReviews > 0) {
    add(if (undeliveredReviews == 1) "1 submitted review has not been picked up by the coding agent." else "$undeliveredReviews submitted reviews have not been picked up by the coding agent.")
  }
}
