@file:OptIn(ExperimentalForeignApi::class)

package io.github.mataku.kdiffviewer.cli

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import platform.posix.AF_UNIX
import platform.posix.EINTR
import platform.posix.SOCK_STREAM
import platform.posix.close
import platform.posix.connect
import platform.posix.errno
import platform.posix.read
import platform.posix.sockaddr
import platform.posix.socket
import platform.posix.strerror
import platform.posix.write

// sockaddr_un differs per OS (macOS has sun_len and a 104-byte sun_path, Linux a 16-bit family and 108 bytes),
// and platform.posix does not expose it on every target, so the address is written as raw bytes.
internal expect val SOCKADDR_UN_SIZE: Int
internal expect val SUN_PATH_CAPACITY: Int

internal expect fun writeSockaddrHeader(raw: CPointer<UByteVar>)

internal expect fun suppressSigpipe(fd: Int)

private const val SUN_PATH_OFFSET = 2
private const val READ_CHUNK = 65536

internal fun <T> withUnixAddress(path: String, block: (CPointer<sockaddr>, UInt) -> T): T {
  val bytes = path.encodeToByteArray()
  if (bytes.size >= SUN_PATH_CAPACITY) throw TransportException("socket path is too long")
  return memScoped {
    val raw = allocArray<UByteVar>(SOCKADDR_UN_SIZE)
    for (i in 0 until SOCKADDR_UN_SIZE) raw[i] = 0u
    writeSockaddrHeader(raw)
    for (i in bytes.indices) raw[SUN_PATH_OFFSET + i] = bytes[i].toUByte()
    block(raw.reinterpret(), SOCKADDR_UN_SIZE.toUInt())
  }
}

private fun errnoMessage(prefix: String): String = "$prefix: ${strerror(errno)?.toKString()}"

internal fun unixRequest(socketPath: String, line: String): String {
  val fd = socket(AF_UNIX, SOCK_STREAM, 0)
  if (fd < 0) throw TransportException(errnoMessage("socket"))
  try {
    suppressSigpipe(fd)
    val connected = withUnixAddress(socketPath) { addr, size -> connect(fd, addr, size) }
    if (connected != 0) throw TransportException(errnoMessage("connect"))
    writeAll(fd, (line + "\n").encodeToByteArray())
    return readLine(fd)
  } finally {
    close(fd)
  }
}

private fun writeAll(fd: Int, data: ByteArray) {
  var offset = 0
  data.usePinned { pinned ->
    while (offset < data.size) {
      val written = write(fd, pinned.addressOf(offset), (data.size - offset).convert())
      if (written < 0) {
        if (errno == EINTR) continue
        throw TransportException(errnoMessage("write"))
      }
      offset += written.toInt()
    }
  }
}

private fun readLine(fd: Int): String {
  var acc = ByteArray(0)
  val chunk = ByteArray(READ_CHUNK)
  while (true) {
    val count = chunk.usePinned { read(fd, it.addressOf(0), chunk.size.convert()) }.toInt()
    if (count < 0) {
      if (errno == EINTR) continue
      throw TransportException(errnoMessage("read"))
    }
    if (count == 0) throw TransportException("EOF")
    val newline = (0 until count).firstOrNull { chunk[it] == '\n'.code.toByte() }
    if (newline != null) {
      return (acc + chunk.copyOfRange(0, newline)).decodeToString()
    }
    acc += chunk.copyOfRange(0, count)
  }
}
