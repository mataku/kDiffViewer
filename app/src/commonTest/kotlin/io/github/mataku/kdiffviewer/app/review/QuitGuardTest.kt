package io.github.mataku.kdiffviewer.app.review

import io.github.mataku.kdiffviewer.core.review.Submission
import io.github.mataku.kdiffviewer.core.review.SubmissionState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class QuitGuardTest {
  private fun submission(number: Int) = Submission(number, SubmissionState.Comment, null, Instant.fromEpochSeconds(0), emptyList())

  @Test
  fun nothingAtRiskNeedsNoWarning() {
    assertNull(quitWarningOf(ReviewSnapshot.Empty))
  }

  @Test
  fun pendingCommentsWarn() {
    assertEquals(QuitWarning(pendingComments = 2, agentWaiting = false, undeliveredReviews = 0), quitWarningOf(ReviewSnapshot.Empty.copy(pendingCount = 2)))
  }

  @Test
  fun waitingAgentWarns() {
    assertEquals(QuitWarning(pendingComments = 0, agentWaiting = true, undeliveredReviews = 0), quitWarningOf(ReviewSnapshot.Empty.copy(waiting = true)))
  }

  @Test
  fun submittedReviewsNotYetDeliveredWarn() {
    val snapshot = ReviewSnapshot.Empty.copy(
      submissions = listOf(submission(1), submission(2), submission(3)),
      core = ReviewCore.Empty.copy(deliveredThrough = 1),
    )
    assertEquals(QuitWarning(pendingComments = 0, agentWaiting = false, undeliveredReviews = 2), quitWarningOf(snapshot))
  }

  @Test
  fun deliveredReviewsNeedNoWarning() {
    val snapshot = ReviewSnapshot.Empty.copy(
      submissions = listOf(submission(1), submission(2)),
      core = ReviewCore.Empty.copy(deliveredThrough = 2),
    )
    assertNull(quitWarningOf(snapshot))
  }

  @Test
  fun messageListsEachRiskInOrder() {
    assertEquals(
      listOf(
        "2 pending comments will be lost.",
        "A coding agent is waiting for your review.",
        "1 submitted review has not been picked up by the coding agent.",
      ),
      QuitWarning(pendingComments = 2, agentWaiting = true, undeliveredReviews = 1).messageLines(),
    )
  }

  @Test
  fun messageUsesSingularAndPlural() {
    assertEquals(
      listOf("1 pending comment will be lost.", "3 submitted reviews have not been picked up by the coding agent."),
      QuitWarning(pendingComments = 1, agentWaiting = false, undeliveredReviews = 3).messageLines(),
    )
  }
}
