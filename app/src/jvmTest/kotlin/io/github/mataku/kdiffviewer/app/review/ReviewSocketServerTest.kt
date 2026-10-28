package io.github.mataku.kdiffviewer.app.review

import io.github.mataku.kdiffviewer.protocol.Codec
import io.github.mataku.kdiffviewer.protocol.ErrorCodes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReviewSocketServerTest {
  private val dir: Path = Files.createTempDirectory(Path.of("/tmp"), "kdv")
  private val socket: Path = dir.resolve("s.sock")
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private var server: ReviewSocketServer? = null

  @AfterTest
  fun tearDown() {
    server?.close()
    scope.cancel()
    dir.toFile().deleteRecursively()
  }

  private fun startServer(
    maxRequestBytes: Int = 1_048_576,
    idleTimeoutMillis: Long = 10_000,
    handle: suspend (String) -> Reply = { Reply("echo:$it") },
  ): ReviewSocketServer = ReviewSocketServer(socket, scope, Dispatchers.IO, handle, maxRequestBytes, idleTimeoutMillis).also {
    it.start()
    server = it
  }

  private fun connect(): SocketChannel = SocketChannel.open(StandardProtocolFamily.UNIX).also { it.connect(UnixDomainSocketAddress.of(socket)) }

  private fun SocketChannel.send(text: String) {
    val buffer = ByteBuffer.wrap(text.encodeToByteArray())
    while (buffer.hasRemaining()) write(buffer)
  }

  private fun SocketChannel.readLine(): String? {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteBuffer.allocate(1)
    while (true) {
      buffer.clear()
      if (read(buffer) < 0) return if (out.size() == 0) null else out.toString(Charsets.UTF_8)
      val byte = buffer.get(0)
      if (byte == '\n'.code.toByte()) return out.toString(Charsets.UTF_8)
      out.write(byte.toInt())
    }
  }

  private fun roundTrip(request: String): String? = connect().use {
    it.send("$request\n")
    it.readLine()
  }

  @Test
  fun `echo handler round trips`() {
    startServer()
    assertEquals("echo:hello", roundTrip("hello"))
  }

  @Test
  fun `serves sequential clients`() {
    startServer()
    assertEquals("echo:one", roundTrip("one"))
    assertEquals("echo:two", roundTrip("two"))
  }

  @Test
  fun `idle client does not block others`() {
    startServer()
    connect().use {
      assertEquals("echo:fast", roundTrip("fast"))
    }
  }

  @Test
  fun `oversize request gets bad_request`() {
    startServer(maxRequestBytes = 16)
    val response = roundTrip("x".repeat(200))
    assertNotNull(response)
    val decoded = Codec.decodeResponse(response)
    assertFalse(decoded.ok)
    assertEquals(ErrorCodes.BAD_REQUEST, decoded.error?.code)
    assertEquals("Request too large", decoded.error?.message)
  }

  @Test
  fun `idle client is dropped without response`() {
    startServer(idleTimeoutMillis = 200)
    connect().use {
      val start = System.nanoTime()
      assertNull(it.readLine())
      assertTrue((System.nanoTime() - start) / 1_000_000 < 5_000)
    }
    assertEquals("echo:after", roundTrip("after"))
  }

  @Test
  fun `close deletes the socket file`() {
    val running = startServer()
    assertTrue(Files.exists(socket))
    running.close()
    assertFalse(Files.exists(socket))
  }

  @Test
  fun `socket file is owner only`() {
    startServer()
    assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(socket)))
  }

  @Test
  fun `slow handler does not block another connection`() = runBlocking {
    val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
    startServer { line ->
      if (line == "slow") gate.await()
      Reply("echo:$line")
    }
    val slow = connect()
    slow.send("slow\n")
    assertEquals("echo:fast", roundTrip("fast"))
    gate.complete(Unit)
    assertEquals("echo:slow", slow.readLine())
    slow.close()
  }

  @Test
  fun `client disconnect cancels the handler`() = runBlocking {
    val started = kotlinx.coroutines.CompletableDeferred<Unit>()
    val cancelled = kotlinx.coroutines.CompletableDeferred<Unit>()
    startServer {
      started.complete(Unit)
      try {
        kotlinx.coroutines.awaitCancellation()
      } finally {
        cancelled.complete(Unit)
      }
    }
    val client = connect()
    client.send("wait\n")
    kotlinx.coroutines.withTimeout(2_000) { started.await() }
    client.close()
    kotlinx.coroutines.withTimeout(2_000) { cancelled.await() }
  }

  @Test
  fun `slow handler still responds`() {
    startServer {
      kotlinx.coroutines.delay(300)
      Reply("late:$it")
    }
    assertEquals("late:x", roundTrip("x"))
  }

  @Test
  fun `onWritten runs after a successful write`() = runBlocking {
    val written = kotlinx.coroutines.CompletableDeferred<Unit>()
    startServer { Reply("ok") { written.complete(Unit) } }
    assertEquals("ok", roundTrip("x"))
    kotlinx.coroutines.withTimeout(2_000) { written.await() }
  }

  @Test
  fun `onWritten is skipped when the client is gone`() = runBlocking {
    val started = kotlinx.coroutines.CompletableDeferred<Unit>()
    val release = kotlinx.coroutines.CompletableDeferred<Unit>()
    var written = false
    val abandoned = kotlinx.coroutines.CompletableDeferred<Unit>()
    startServer {
      started.complete(Unit)
      kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { release.await() }
      Reply("late", onAbandoned = { abandoned.complete(Unit) }) { written = true }
    }
    val client = connect()
    client.send("x\n")
    kotlinx.coroutines.withTimeout(2_000) { started.await() }
    client.close()
    kotlinx.coroutines.delay(200)
    release.complete(Unit)
    kotlinx.coroutines.withTimeout(2_000) { abandoned.await() }
    assertFalse(written)
  }
}
