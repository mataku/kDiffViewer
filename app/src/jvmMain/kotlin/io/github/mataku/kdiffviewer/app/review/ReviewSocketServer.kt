package io.github.mataku.kdiffviewer.app.review

import io.github.mataku.kdiffviewer.protocol.Codec
import io.github.mataku.kdiffviewer.protocol.ErrorCodes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.ClosedChannelException
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext

class ReviewSocketServer(
  private val socket: Path,
  private val scope: CoroutineScope,
  private val ioContext: CoroutineContext,
  private val handle: suspend (String) -> Reply,
  private val maxRequestBytes: Int = 1_048_576,
  private val idleTimeoutMillis: Long = 10_000,
) {
  private var channel: ServerSocketChannel? = null
  private var acceptJob: Job? = null

  @Synchronized
  fun start() {
    check(channel == null) { "Server already started" }
    Files.deleteIfExists(socket)
    val server = ServerSocketChannel.open(StandardProtocolFamily.UNIX)
    try {
      server.bind(UnixDomainSocketAddress.of(socket))
      Files.setPosixFilePermissions(socket, PosixFilePermissions.fromString("rw-------"))
    } catch (e: Throwable) {
      server.close()
      Files.deleteIfExists(socket)
      throw e
    }
    channel = server
    acceptJob = scope.launch(ioContext) { acceptLoop(server) }
  }

  @Synchronized
  fun close() {
    val server = channel ?: return
    channel = null
    runCatching { server.close() }
    acceptJob?.cancel()
    acceptJob = null
    runCatching { Files.deleteIfExists(socket) }
  }

  private suspend fun acceptLoop(server: ServerSocketChannel) {
    while (scope.isActive && server.isOpen) {
      val client = try {
        server.accept()
      } catch (_: ClosedChannelException) {
        return
      } catch (_: java.io.IOException) {
        if (!server.isOpen) return
        continue
      }
      scope.launch(ioContext) { serve(client) }
    }
  }

  private suspend fun serve(client: SocketChannel) {
    client.use {
      val request = withTimeoutOrNull(idleTimeoutMillis) {
        runInterruptible { readLine(client) }
      } ?: return
      val reply = when (request) {
        is Request.TooLarge -> Reply(Codec.failure(ErrorCodes.BAD_REQUEST, "Request too large"))
        is Request.Line -> respond(client, request.text) ?: return
      }
      if (runInterruptible { writeLine(client, reply.line) }) reply.onWritten() else reply.onAbandoned()
    }
  }

  private suspend fun respond(client: SocketChannel, text: String): Reply? = coroutineScope {
    val produced = AtomicReference<Reply?>()
    // The handler's own return value is lost if it is cancelled after producing it, so keep it aside to release what it claimed.
    val handler = async { handle(text).also(produced::set) }
    // Not runInterruptible: interrupting a thread blocked in SocketChannel.read closes the channel, which would drop the response.
    // shutdownInput releases the blocked read instead, and the client never half-closes, so EOF means it went away.
    val watcher = launch(ioContext) {
      awaitEndOfInput(client)
      handler.cancel()
    }
    try {
      handler.await()
    } catch (e: CancellationException) {
      produced.get()?.onAbandoned()
      ensureActive()
      null
    } finally {
      runCatching { client.shutdownInput() }
      watcher.join()
    }
  }

  private fun awaitEndOfInput(client: SocketChannel) {
    val buffer = ByteBuffer.allocate(256)
    while (true) {
      buffer.clear()
      val read = try {
        client.read(buffer)
      } catch (_: java.io.IOException) {
        return
      }
      if (read < 0) return
    }
  }

  private sealed interface Request {
    data class Line(val text: String) : Request

    data object TooLarge : Request
  }

  private fun readLine(client: SocketChannel): Request? {
    val out = ByteArrayOutputStream()
    val buffer = ByteBuffer.allocate(8192)
    while (true) {
      buffer.clear()
      val read = try {
        client.read(buffer)
      } catch (_: java.io.IOException) {
        return null
      }
      if (read < 0) return null
      val bytes = buffer.array()
      for (i in 0 until read) {
        if (bytes[i] == NEWLINE) return Request.Line(out.toString(Charsets.UTF_8))
        if (out.size() >= maxRequestBytes) return Request.TooLarge
        out.write(bytes[i].toInt())
      }
    }
  }

  private fun writeLine(client: SocketChannel, text: String): Boolean {
    val buffer = ByteBuffer.wrap((text + "\n").encodeToByteArray())
    return try {
      while (buffer.hasRemaining()) client.write(buffer)
      true
    } catch (_: java.io.IOException) {
      false
    }
  }

  private companion object {
    const val NEWLINE = '\n'.code.toByte()
  }
}
