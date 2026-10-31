@file:OptIn(ExperimentalForeignApi::class, ObsoleteWorkersApi::class)

package io.github.mataku.kdiffviewer.cli

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.posix.AF_UNIX
import platform.posix.SOCK_STREAM
import platform.posix.accept
import platform.posix.bind
import platform.posix.close
import platform.posix.getpid
import platform.posix.listen
import platform.posix.read
import platform.posix.socket
import platform.posix.unlink
import platform.posix.write
import kotlin.concurrent.AtomicInt
import kotlin.native.concurrent.ObsoleteWorkersApi
import kotlin.native.concurrent.TransferMode
import kotlin.native.concurrent.Worker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private var counter = AtomicInt(0)

private fun listenOn(path: String): Int {
  unlink(path)
  val fd = socket(AF_UNIX, SOCK_STREAM, 0)
  check(withUnixAddress(path) { addr, size -> bind(fd, addr, size) } == 0)
  check(listen(fd, 1) == 0)
  return fd
}

private fun serveOnce(listenFd: Int, reply: String?): Worker {
  val worker = Worker.start()
  worker.execute(TransferMode.SAFE, { listenFd to reply }) { (fd, reply) ->
    val client = accept(fd, null, null)
    val buffer = ByteArray(4096)
    buffer.usePinned { read(client, it.addressOf(0), buffer.size.convert()) }
    if (reply != null) {
      val bytes = (reply + "\n").encodeToByteArray()
      bytes.usePinned { write(client, it.addressOf(0), bytes.size.convert()) }
    }
    close(client)
  }
  return worker
}

class PosixSocketTest {
  private fun socketPath() = "/tmp/kdv-test-${getpid()}-${counter.incrementAndGet()}.sock"

  @Test
  fun requestReturnsTheServerLine() {
    val path = socketPath()
    val fd = listenOn(path)
    val worker = serveOnce(fd, """{"ok":true,"result":{}}""")
    try {
      assertEquals("""{"ok":true,"result":{}}""", PosixPlatform.request(path, """{"v":1}"""))
    } finally {
      worker.requestTermination().result
      close(fd)
      unlink(path)
    }
  }

  @Test
  fun closeWithoutReplyIsATransportException() {
    val path = socketPath()
    val fd = listenOn(path)
    val worker = serveOnce(fd, null)
    try {
      assertFailsWith<TransportException> { PosixPlatform.request(path, """{"v":1}""") }
    } finally {
      worker.requestTermination().result
      close(fd)
      unlink(path)
    }
  }

  @Test
  fun connectingToAMissingSocketIsATransportException() {
    assertFailsWith<TransportException> { PosixPlatform.request(socketPath(), "{}") }
  }
}
