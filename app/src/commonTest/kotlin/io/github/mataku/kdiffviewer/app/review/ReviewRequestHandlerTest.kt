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
import io.github.mataku.kdiffviewer.core.review.LineLocation
import io.github.mataku.kdiffviewer.core.review.Placement
import io.github.mataku.kdiffviewer.core.review.anchorOf
import io.github.mataku.kdiffviewer.protocol.AuthorDto
import io.github.mataku.kdiffviewer.protocol.Codec
import io.github.mataku.kdiffviewer.protocol.CommentDto
import io.github.mataku.kdiffviewer.protocol.ErrorCodes
import io.github.mataku.kdiffviewer.protocol.Methods
import io.github.mataku.kdiffviewer.protocol.PROTOCOL_VERSION
import io.github.mataku.kdiffviewer.protocol.ReloadResult
import io.github.mataku.kdiffviewer.protocol.ReviewDto
import io.github.mataku.kdiffviewer.protocol.ReviewWaitResult
import io.github.mataku.kdiffviewer.protocol.ThreadDto
import io.github.mataku.kdiffviewer.protocol.ThreadListResult
import io.github.mataku.kdiffviewer.protocol.WindowInfoResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
private suspend fun ReviewRequestHandler.call(line: String): String = handle(line).line

class ReviewRequestHandlerTest {
  private val repo = RepoPath("/repo")
  private val epoch = Instant.fromEpochSeconds(1_000)
  private val info = WindowInfoResult(pid = 42, repoRoot = "/repo", protocolVersion = PROTOCOL_VERSION)
  private val base = FileDiff(
    "a.kt",
    "a.kt",
    ChangeType.Modified,
    false,
    DiffContent.Text(
      listOf(Hunk(1, 3, 1, 3, "", listOf("a", "b", "c").mapIndexed { i, t -> DiffLine(LineKind.Context, t, i + 1, i + 1) })),
    ),
  )

  private fun ui(loadId: Long, status: LoadStatus = LoadStatus.Done) = ViewerUiState(repo = repo, status = status, files = listOf(base), loadId = loadId)

  private class Fixture(val review: ReviewState, val handler: ReviewRequestHandler)

  private fun TestScope.fixture(reload: suspend () -> Long? = { null }): Fixture {
    val review = ReviewState(this, StandardTestDispatcher(testScheduler), { epoch })
    return Fixture(review, ReviewRequestHandler(review, reload, { info }))
  }

  private fun Fixture.addThread(lineIndex: Int, text: String = "fix") {
    review.onViewerState(ui(1))
    val anchor = anchorOf(base, 0, lineIndex)!!
    review.startDraft(DraftTarget.NewThread(anchor, Placement.OnLine(LineLocation(0, 0, lineIndex))))
    review.updateDraft(text)
    review.submitDraft(DraftMode.Single)
  }

  private fun request(method: String, vararg params: Pair<String, Any>, v: Int = PROTOCOL_VERSION): String {
    val obj = buildJsonObject {
      for ((k, value) in params) {
        when (value) {
          is Boolean -> put(k, value)
          is Int -> put(k, value)
          else -> put(k, value.toString())
        }
      }
    }
    return """{"v":$v,"method":"$method","params":$obj}"""
  }

  private fun errorCode(line: String): String? = Codec.decodeResponse(line).error?.code

  private inline fun <reified T> resultOf(line: String): T {
    val response = Codec.decodeResponse(line)
    assertTrue(response.ok, line)
    return Codec.result(response)
  }

  @Test
  fun windowInfoReturnsInfo() = runTest {
    val f = fixture()
    assertEquals(info, resultOf<WindowInfoResult>(f.handler.call(request(Methods.WINDOW_INFO))))
  }

  @Test
  fun threadListMapsThreads() = runTest(StandardTestDispatcher()) {
    val f = fixture()
    f.addThread(1)
    advanceUntilIdle()
    val result = resultOf<ThreadListResult>(f.handler.call(request(Methods.THREAD_LIST)))
    val thread = result.threads.single()
    assertEquals(
      ThreadDto(
        id = "t1",
        path = "a.kt",
        side = "new",
        startLine = 2,
        endLine = 2,
        snapshot = listOf("b"),
        resolved = false,
        outdated = false,
        comments = listOf(CommentDto("c1", AuthorDto("user"), "fix", epoch.toString())),
      ),
      thread,
    )
  }

  @Test
  fun threadListExcludesResolvedUnlessAll() = runTest(StandardTestDispatcher()) {
    val f = fixture()
    f.addThread(0)
    f.addThread(1)
    f.review.resolveThread("t1", true)
    assertEquals(listOf("t2"), resultOf<ThreadListResult>(f.handler.call(request(Methods.THREAD_LIST))).threads.map { it.id })
    assertEquals(listOf("t1", "t2"), resultOf<ThreadListResult>(f.handler.call(request(Methods.THREAD_LIST, "all" to true))).threads.map { it.id })
  }

  @Test
  fun threadReplyAppendsAgentComment() = runTest(StandardTestDispatcher()) {
    val f = fixture()
    f.addThread(0)
    val comment = resultOf<CommentDto>(f.handler.call(request(Methods.THREAD_REPLY, "threadId" to "t1", "body" to "done", "author" to "claude")))
    assertEquals(CommentDto("c2", AuthorDto("agent", "claude"), "done", epoch.toString()), comment)
    assertEquals(2, f.review.threads(true).single().comments.size)
  }

  @Test
  fun threadReplyDefaultsAuthorToAgent() = runTest(StandardTestDispatcher()) {
    val f = fixture()
    f.addThread(0)
    val comment = resultOf<CommentDto>(f.handler.call(request(Methods.THREAD_REPLY, "threadId" to "t1", "body" to "done")))
    assertEquals(AuthorDto("agent", "agent"), comment.author)
  }

  @Test
  fun threadReplyRejectsBlankBody() = runTest(StandardTestDispatcher()) {
    val f = fixture()
    f.addThread(0)
    val response = Codec.decodeResponse(f.handler.call(request(Methods.THREAD_REPLY, "threadId" to "t1", "body" to " \n")))
    assertFalse(response.ok)
    assertEquals(ErrorCodes.BAD_REQUEST, response.error?.code)
    assertEquals("Reply body is blank", response.error?.message)
    assertEquals(1, f.review.threads(true).single().comments.size)
  }

  @Test
  fun threadReplyMapsBlankAuthorToAgent() = runTest(StandardTestDispatcher()) {
    val f = fixture()
    f.addThread(0)
    val comment = resultOf<CommentDto>(f.handler.call(request(Methods.THREAD_REPLY, "threadId" to "t1", "body" to "done", "author" to " ")))
    assertEquals(AuthorDto("agent", "agent"), comment.author)
  }

  @Test
  fun bodyAtTheCapIsAccepted() = runTest(StandardTestDispatcher()) {
    val f = fixture()
    f.addThread(0)
    val response = Codec.decodeResponse(f.handler.call(request(Methods.THREAD_REPLY, "threadId" to "t1", "body" to "a".repeat(65_536))))
    assertTrue(response.ok)
  }

  @Test
  fun bodyOverTheCapIsBadRequest() = runTest(StandardTestDispatcher()) {
    val f = fixture()
    f.addThread(0)
    val response = Codec.decodeResponse(f.handler.call(request(Methods.THREAD_REPLY, "threadId" to "t1", "body" to "a".repeat(65_537))))
    assertFalse(response.ok)
    assertEquals(ErrorCodes.BAD_REQUEST, response.error?.code)
    assertEquals("Reply body exceeds 65536 characters", response.error?.message)
    assertEquals(1, f.review.threads(true).single().comments.size)
  }

  @Test
  fun deeplyNestedUnknownParamIsBadRequestNotOverflow() = runTest {
    val f = fixture()
    val nested = "[".repeat(100_000) + "]".repeat(100_000)
    val line = """{"v":$PROTOCOL_VERSION,"method":"${Methods.THREAD_LIST}","params":{"all":true,"x":$nested}}"""
    val response = Codec.decodeResponse(f.handler.call(line))
    assertFalse(response.ok)
    assertEquals(ErrorCodes.BAD_REQUEST, response.error?.code)
  }

  @Test
  fun threadReplyUnknownThread() = runTest {
    val f = fixture()
    val response = Codec.decodeResponse(f.handler.call(request(Methods.THREAD_REPLY, "threadId" to "t9", "body" to "x")))
    assertFalse(response.ok)
    assertEquals(ErrorCodes.THREAD_NOT_FOUND, response.error?.code)
    assertEquals("No thread t9", response.error?.message)
  }

  @Test
  fun threadResolveMarksResolved() = runTest(StandardTestDispatcher()) {
    val f = fixture()
    f.addThread(0)
    val thread = resultOf<ThreadDto>(f.handler.call(request(Methods.THREAD_RESOLVE, "threadId" to "t1")))
    assertTrue(thread.resolved)
    assertTrue(f.review.threads(true).single().resolved)
  }

  @Test
  fun threadResolveUnknownThread() = runTest {
    val f = fixture()
    val response = Codec.decodeResponse(f.handler.call(request(Methods.THREAD_RESOLVE, "threadId" to "t9")))
    assertEquals(ErrorCodes.THREAD_NOT_FOUND, response.error?.code)
    assertEquals("No thread t9", response.error?.message)
  }

  @Test
  fun reloadWithoutRepositoryFails() = runTest {
    val f = fixture { null }
    assertEquals(ErrorCodes.NO_REPOSITORY, errorCode(f.handler.call(request(Methods.RELOAD))))
  }

  @Test
  fun reloadReturnsAfterViewerReachesDone() = runTest(StandardTestDispatcher()) {
    lateinit var f: Fixture
    f = fixture { 2L }
    f.addThread(1)
    advanceUntilIdle()
    val deferred = async { f.handler.call(request(Methods.RELOAD)) }
    runCurrent()
    assertFalse(deferred.isCompleted)
    f.review.onViewerState(ui(2))
    advanceUntilIdle()
    assertTrue(deferred.isCompleted)
    assertEquals(ReloadResult(files = 1, outdated = 0), resultOf<ReloadResult>(deferred.await()))
  }

  @Test
  fun reloadReportsLoadFailed() = runTest(StandardTestDispatcher()) {
    val f = fixture { 2L }
    f.review.onViewerState(ui(1))
    advanceUntilIdle()
    val deferred = async { f.handler.call(request(Methods.RELOAD)) }
    runCurrent()
    f.review.onViewerState(ui(2, LoadStatus.Failed(ViewerError.GitFailed("boom"))))
    advanceUntilIdle()
    assertEquals(ErrorCodes.LOAD_FAILED, errorCode(deferred.await()))
  }

  @Test
  fun malformedJsonIsBadRequest() = runTest {
    val f = fixture()
    assertEquals(ErrorCodes.BAD_REQUEST, errorCode(f.handler.call("not json")))
    assertEquals(ErrorCodes.BAD_REQUEST, errorCode(f.handler.call("")))
    assertEquals(ErrorCodes.BAD_REQUEST, errorCode(f.handler.call("""{"method":"reload"}""")))
  }

  @Test
  fun badParamsIsBadRequest() = runTest {
    val f = fixture()
    assertEquals(ErrorCodes.BAD_REQUEST, errorCode(f.handler.call(request(Methods.THREAD_REPLY, "threadId" to "t1"))))
    assertEquals(ErrorCodes.BAD_REQUEST, errorCode(f.handler.call("""{"v":1,"method":"thread.list","params":{"all":"maybe"}}""")))
  }

  @Test
  fun unsupportedVersion() = runTest {
    val f = fixture()
    assertEquals(ErrorCodes.UNSUPPORTED_VERSION, errorCode(f.handler.call(request(Methods.WINDOW_INFO, v = 99))))
  }

  @Test
  fun unknownMethodIsBadRequest() = runTest {
    val f = fixture()
    assertEquals(ErrorCodes.BAD_REQUEST, errorCode(f.handler.call(request("nope"))))
  }

  @Test
  fun dependencyExceptionsBecomeFailuresNotThrows() = runTest {
    val review = ReviewState(this, StandardTestDispatcher(testScheduler), { epoch })
    val handler = ReviewRequestHandler(review, { null }, { error("boom") })
    val line = handler.call(request(Methods.WINDOW_INFO))
    assertFalse(Codec.decodeResponse(line).ok)
  }

  @Test
  fun waitReturnsReviewAndThreads() = runTest {
    val f = fixture()
    f.addThread(1)
    val result = resultOf<ReviewWaitResult>(f.handler.call(request(Methods.REVIEW_WAIT)))
    assertFalse(result.timedOut)
    assertEquals(listOf(ReviewDto("r1", "comment", null, epoch.toString(), listOf("c1"))), result.reviews)
    assertEquals(listOf("t1"), result.threads.map { it.id })
  }

  @Test
  fun waitReportsApprovedSummary() = runTest {
    val f = fixture()
    f.review.onViewerState(ui(1))
    f.review.submitReview("lgtm", approve = true)
    val review = resultOf<ReviewWaitResult>(f.handler.call(request(Methods.REVIEW_WAIT))).reviews.single()
    assertEquals("approved", review.state)
    assertEquals("lgtm", review.summary)
  }

  @Test
  fun waitTimeoutResult() = runTest {
    val f = fixture()
    f.review.onViewerState(ui(1))
    val result = resultOf<ReviewWaitResult>(f.handler.call(request(Methods.REVIEW_WAIT, "timeoutSeconds" to 1)))
    assertEquals(ReviewWaitResult(true, emptyList(), emptyList()), result)
  }

  @Test
  fun waitRejectsTimeoutOutOfRange() = runTest {
    val f = fixture()
    f.review.onViewerState(ui(1))
    assertEquals(ErrorCodes.BAD_REQUEST, errorCode(f.handler.call(request(Methods.REVIEW_WAIT, "timeoutSeconds" to 0))))
    assertEquals(ErrorCodes.BAD_REQUEST, errorCode(f.handler.call(request(Methods.REVIEW_WAIT, "timeoutSeconds" to 86_401))))
  }

  @Test
  fun waitWithoutRepository() = runTest {
    assertEquals(ErrorCodes.NO_REPOSITORY, errorCode(fixture().handler.call(request(Methods.REVIEW_WAIT))))
  }

  @Test
  fun waitSuperseded() = runTest {
    val f = fixture()
    f.review.onViewerState(ui(1))
    val first = async { f.handler.call(request(Methods.REVIEW_WAIT)) }
    runCurrent()
    val second = async { f.handler.call(request(Methods.REVIEW_WAIT)) }
    runCurrent()
    assertEquals(ErrorCodes.SUPERSEDED, errorCode(first.await()))
    second.cancel()
  }

  @Test
  fun waitRepositoryChanged() = runTest {
    val f = fixture()
    f.review.onViewerState(ui(1))
    val waiter = async { f.handler.call(request(Methods.REVIEW_WAIT)) }
    runCurrent()
    f.review.onViewerState(ViewerUiState(repo = RepoPath("/other"), status = LoadStatus.Loading, files = emptyList(), loadId = 2))
    assertEquals(ErrorCodes.REPOSITORY_CHANGED, errorCode(waiter.await()))
  }

  @Test
  fun waitSkipsDeletedThread() = runTest {
    val f = fixture()
    f.addThread(1)
    f.review.delete("c1")
    val result = resultOf<ReviewWaitResult>(f.handler.call(request(Methods.REVIEW_WAIT)))
    assertEquals(listOf("r1"), result.reviews.map { it.id })
    assertTrue(result.threads.isEmpty())
  }

  @Test
  fun waitExcludesPendingCommentsFromThreads() = runTest {
    val f = fixture()
    f.addThread(1)
    f.review.startDraft(DraftTarget.Reply("t1"))
    f.review.updateDraft("later")
    f.review.submitDraft(DraftMode.Review)
    val thread = resultOf<ReviewWaitResult>(f.handler.call(request(Methods.REVIEW_WAIT))).threads.single()
    assertEquals(listOf("c1"), thread.comments.map { it.id })
  }

  @Test
  fun onWrittenMovesCursor() = runTest {
    val f = fixture()
    f.addThread(1)
    val reply = f.handler.handle(request(Methods.REVIEW_WAIT))
    assertEquals(0, f.review.snapshot.value.core.deliveredThrough)
    reply.onWritten()
    assertEquals(1, f.review.snapshot.value.core.deliveredThrough)
  }
}
