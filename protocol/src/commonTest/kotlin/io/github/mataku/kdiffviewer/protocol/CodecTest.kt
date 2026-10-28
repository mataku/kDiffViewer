package io.github.mataku.kdiffviewer.protocol

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CodecTest {
  private val comment = CommentDto("c1", AuthorDto("agent", "claude"), "line1\nline2 🎉 \"q\"", "2026-10-26T00:00:00Z")
  private val thread = ThreadDto(
    id = "t1",
    path = "a/b.kt",
    side = "new",
    startLine = 3,
    endLine = 4,
    snapshot = listOf("x", "y"),
    resolved = false,
    outdated = true,
    comments = listOf(comment),
  )

  private inline fun <reified P : Any> requestRoundTrip(method: String, params: P): P {
    val line = Codec.encodeRequest(method, Codec.json.encodeToJsonElement(kotlinx.serialization.serializer<P>(), params) as JsonObject)
    assertFalse(line.contains('\n'))
    val request = Codec.decodeRequest(line)
    assertEquals(method, request.method)
    assertEquals(PROTOCOL_VERSION, request.v)
    return Codec.params(request)
  }

  private inline fun <reified R : Any> resultRoundTrip(result: R): R {
    val line = Codec.success(Codec.json.encodeToJsonElement(kotlinx.serialization.serializer<R>(), result))
    assertFalse(line.contains('\n'))
    val response = Codec.decodeResponse(line)
    assertTrue(response.ok)
    return Codec.result(response)
  }

  @Test
  fun threadListParams() {
    assertEquals(ThreadListParams(true), requestRoundTrip(Methods.THREAD_LIST, ThreadListParams(true)))
  }

  @Test
  fun threadReplyParams() {
    val p = ThreadReplyParams("t1", "hi", "user")
    assertEquals(p, requestRoundTrip(Methods.THREAD_REPLY, p))
  }

  @Test
  fun threadResolveParams() {
    val p = ThreadResolveParams("t1")
    assertEquals(p, requestRoundTrip(Methods.THREAD_RESOLVE, p))
  }

  @Test
  fun emptyParamsDecode() {
    val request = Codec.decodeRequest(Codec.encodeRequest(Methods.RELOAD))
    assertEquals(JsonObject(emptyMap()), request.params)
    assertEquals(ThreadListParams(false), Codec.params<ThreadListParams>(request))
  }

  @Test
  fun results() {
    assertEquals(ThreadListResult(listOf(thread)), resultRoundTrip(ThreadListResult(listOf(thread))))
    assertEquals(comment, resultRoundTrip(comment))
    assertEquals(thread, resultRoundTrip(thread))
    assertEquals(ReloadResult(5, 2), resultRoundTrip(ReloadResult(5, 2)))
    assertEquals(WindowInfoResult(1L, "/r", 1), resultRoundTrip(WindowInfoResult(1L, "/r", 1)))
    assertEquals(WindowInfoResult(1L, null, 1), resultRoundTrip(WindowInfoResult(1L, null, 1)))
  }

  @Test
  fun fileThreadKeepsNullLines() {
    val file = thread.copy(side = null, startLine = null, endLine = null)
    assertEquals(file, resultRoundTrip(file))
  }

  @Test
  fun failureDecodes() {
    val response = Codec.decodeResponse(Codec.failure(ErrorCodes.THREAD_NOT_FOUND, "No thread t9"))
    assertFalse(response.ok)
    assertNull(response.result)
    assertEquals(ErrorBody("thread_not_found", "No thread t9"), response.error)
  }

  @Test
  fun specialCharactersRoundTrip() {
    val body = "line1\nline2 🎉 \"q\""
    val line = Codec.encodeRequest(
      Methods.THREAD_REPLY,
      buildJsonObject {
        put("threadId", "t1")
        put("body", body)
      },
    )
    assertFalse(line.contains('\n'))
    assertEquals(body, Codec.params<ThreadReplyParams>(Codec.decodeRequest(line)).body)
  }

  @Test
  fun unknownKeysIgnored() {
    val response = Codec.decodeResponse("""{"ok":true,"result":{"files":1,"outdated":0,"extra":1},"future":"x"}""")
    assertEquals(ReloadResult(1, 0), Codec.result<ReloadResult>(response))
  }

  @Test
  fun replyAuthorDefaultsToAgent() {
    val request = Codec.decodeRequest("""{"v":1,"method":"thread.reply","params":{"threadId":"t1","body":"b"}}""")
    assertEquals("agent", Codec.params<ThreadReplyParams>(request).author)
  }

  @Test
  fun registryEntryRoundTrip() {
    val entry = RegistryEntry(7L, null, "/s.sock", "2026-10-26T00:00:00Z", 1)
    assertEquals(entry, Codec.json.decodeFromString<RegistryEntry>(Codec.json.encodeToString(entry)))
  }

  @Test
  fun reviewWaitParams() {
    assertEquals(ReviewWaitParams(540), requestRoundTrip(Methods.REVIEW_WAIT, ReviewWaitParams(540)))
    assertEquals(ReviewWaitParams(null), Codec.params<ReviewWaitParams>(Codec.decodeRequest(Codec.encodeRequest(Methods.REVIEW_WAIT))))
    assertEquals("review.wait", Methods.REVIEW_WAIT)
    assertEquals(86_400, MAX_WAIT_SECONDS)
  }

  @Test
  fun reviewWaitResult() {
    val review = ReviewDto("r2", "approved", "looks good", "2026-10-28T00:00:00Z", listOf("c1"))
    val result = ReviewWaitResult(timedOut = false, reviews = listOf(review), threads = listOf(thread))
    assertEquals(result, resultRoundTrip(result))
    assertEquals(ReviewDto("r1", "comment", null, "2026-10-28T00:00:00Z", emptyList()), resultRoundTrip(ReviewDto("r1", "comment", null, "2026-10-28T00:00:00Z", emptyList())))
    assertEquals(ReviewWaitResult(true, emptyList(), emptyList()), resultRoundTrip(ReviewWaitResult(true, emptyList(), emptyList())))
  }

  @Test
  fun reviewErrorCodes() {
    assertEquals("repository_changed", ErrorCodes.REPOSITORY_CHANGED)
    assertEquals("superseded", ErrorCodes.SUPERSEDED)
  }
}
