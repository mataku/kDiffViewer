package io.github.mataku.kdiffviewer.core.review

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class PublishedTest {
  private fun comment(id: String, pending: Boolean) = Comment(id, Author.User, "body", Instant.fromEpochSeconds(0), pending)

  private fun thread(vararg comments: Comment) = ReviewThread("t1", Anchor.File("a.kt"), comments.toList(), resolved = false, outdated = false)

  @Test
  fun publishedDropsPendingComments() {
    val published = thread(comment("c1", false), comment("c2", true)).published()
    assertEquals(listOf("c1"), published!!.comments.map { it.id })
  }

  @Test
  fun publishedIsNullForPendingThread() {
    assertNull(thread(comment("c1", true)).published())
  }

  @Test
  fun isPendingOnlyWhenAllPending() {
    assertTrue(thread(comment("c1", true), comment("c2", true)).isPending)
    assertFalse(thread(comment("c1", false), comment("c2", true)).isPending)
  }

  @Test
  fun reanchorKeepsPending() {
    val files = listOf(modified("a.kt", hunk(ctx("a", 1, 1))))
    val original = ReviewThread("t1", linesAnchor("a.kt", Side.New, 1, 1, "a"), listOf(comment("c1", true)), resolved = false, outdated = false)
    val (result, _) = reanchor(original, files)
    assertTrue(result.comments.single().pending)
  }

  @Test
  fun submissionIdUsesNumber() {
    assertEquals("r3", Submission(3, SubmissionState.Approved, null, Instant.fromEpochSeconds(0), emptyList()).id)
  }
}
