package io.github.mataku.kdiffviewer.app.review

import io.github.mataku.kdiffviewer.app.DiffViewerState
import io.github.mataku.kdiffviewer.core.git.DiffSource
import io.github.mataku.kdiffviewer.core.model.BaseSpec
import io.github.mataku.kdiffviewer.core.model.BranchRef
import io.github.mataku.kdiffviewer.core.model.FileDiff
import io.github.mataku.kdiffviewer.core.model.HeadRef
import io.github.mataku.kdiffviewer.core.model.RepoPath
import io.github.mataku.kdiffviewer.core.model.ResolvedBase
import io.github.mataku.kdiffviewer.core.review.Anchor
import io.github.mataku.kdiffviewer.core.review.Placement
import io.github.mataku.kdiffviewer.protocol.Codec
import io.github.mataku.kdiffviewer.protocol.PROTOCOL_VERSION
import io.github.mataku.kdiffviewer.protocol.RegistryEntry
import io.github.mataku.kdiffviewer.protocol.WindowInfoResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class ReviewIntegrationTest {
  private val dir: Path = Files.createTempDirectory(Path.of("/tmp"), "kdv")
  private val reviewScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
  private var integration: ReviewIntegration? = null

  @AfterTest
  fun tearDown() {
    integration?.close()
    reviewScope.cancel()
    dir.toFile().deleteRecursively()
  }

  private fun request(socket: Path, line: String): String = SocketChannel.open(StandardProtocolFamily.UNIX).use { channel ->
    channel.connect(UnixDomainSocketAddress.of(socket))
    channel.write(ByteBuffer.wrap("$line\n".encodeToByteArray()))
    val out = StringBuilder()
    val buffer = ByteBuffer.allocate(4096)
    while (!out.endsWith("\n")) {
      buffer.clear()
      if (channel.read(buffer) < 0) break
      out.append(String(buffer.array(), 0, buffer.position(), Charsets.UTF_8))
    }
    out.toString().trim()
  }

  @Test
  fun serverAnswersThreadListFromReviewState() {
    val review = ReviewState(reviewScope, Dispatchers.Unconfined) { Instant.fromEpochSeconds(0) }
    review.startDraft(DraftTarget.NewThread(Anchor.File("a.kt"), Placement.OnFile(0)))
    review.updateDraft("please fix")
    review.submitDraft(DraftMode.Single)
    val registry = SessionRegistry(dir, 4242L, { java.time.Instant.EPOCH }, ::processAlive)
    val handler = ReviewRequestHandler(review, { 1L }, { WindowInfoResult(4242L, null, PROTOCOL_VERSION) })
    val started = ReviewIntegration(registry, Dispatchers.IO, Dispatchers.IO, handler::handle, warn = {}).also { integration = it }
    assertTrue(started.start())
    val socket = dir.resolve("4242.sock")
    val response = request(socket, """{"v":1,"method":"thread.list","params":{}}""")
    assertTrue(response.contains("please fix"), response)
    assertEquals(1, Regex("\"id\":\"t1\"").findAll(response).count(), response)
    started.close()
    assertFalse(Files.exists(socket))
    assertFalse(Files.exists(dir.resolve("4242.json")))
  }

  @Test
  fun publishWritesRegistryEntry() {
    val registry = SessionRegistry(dir, 4243L, { java.time.Instant.EPOCH }, ::processAlive)
    val started = ReviewIntegration(registry, Dispatchers.IO, Dispatchers.IO, { Reply("{}") }, warn = {}).also { integration = it }
    assertTrue(started.start())
    started.publish(dir)
    assertTrue(Files.readString(dir.resolve("4243.json")).contains(dir.toRealPath().toString()))
  }

  private class RootedSource(private val root: RepoPath) : DiffSource {
    override suspend fun resolveBase(repo: RepoPath, spec: BaseSpec): ResolvedBase = ResolvedBase.EmptyTree
    override fun load(repo: RepoPath, base: ResolvedBase): Flow<FileDiff> = emptyFlow()

    override suspend fun head(repo: RepoPath): HeadRef? = null
    override suspend fun branches(repo: RepoPath): List<BranchRef> = emptyList()

    override suspend fun root(repo: RepoPath): RepoPath = root
  }

  @Test
  fun publishesTheRepositoryToplevelRatherThanTheOpenedDirectory() = runBlocking {
    val top = Files.createDirectories(dir.resolve("top"))
    val sub = Files.createDirectories(top.resolve("sub"))
    val viewer = DiffViewerState(RootedSource(RepoPath(top.toString())), reviewScope)
    val registry = SessionRegistry(dir, 4246L, { java.time.Instant.EPOCH }, ::processAlive)
    val started = ReviewIntegration(registry, Dispatchers.IO, Dispatchers.IO, { Reply("{}") }, warn = {}).also { integration = it }
    assertTrue(started.start())
    val follower = launch(Dispatchers.IO) { started.publishRoots(viewer.uiState) }
    viewer.open(RepoPath(sub.toString()))
    val expected = top.toRealPath().toString()
    withTimeout(5_000) {
      while (registry.repoRoot != expected) delay(10)
    }
    val entry = Codec.json.decodeFromString(RegistryEntry.serializer(), Files.readString(dir.resolve("4246.json")))
    assertEquals(expected, entry.repoRoot)
    follower.cancel()
  }

  @Test
  fun startAfterCloseDoesNothing() {
    val registry = SessionRegistry(dir, 4245L, { java.time.Instant.EPOCH }, ::processAlive)
    val closed = ReviewIntegration(registry, Dispatchers.IO, Dispatchers.IO, { Reply("{}") }, warn = {}).also { integration = it }
    closed.close()
    assertFalse(closed.start())
    closed.publish(dir)
    assertFalse(Files.exists(dir.resolve("4245.sock")))
    assertFalse(Files.exists(dir.resolve("4245.json")))
  }

  @Test
  fun tooLongSocketPathDisablesIntegrationWithWarning() {
    val long = Files.createTempDirectory(Path.of("/tmp"), "kdv").resolve("x".repeat(120))
    val warnings = mutableListOf<String>()
    val registry = SessionRegistry(long, 4244L, { java.time.Instant.EPOCH }, ::processAlive)
    val started = ReviewIntegration(registry, Dispatchers.IO, Dispatchers.IO, { Reply("{}") }, warn = { warnings += it })
    assertFalse(started.start())
    assertEquals(listOf("kdv integration disabled: socket path too long"), warnings)
    started.publish(dir)
    started.close()
    long.parent.toFile().deleteRecursively()
  }
}
