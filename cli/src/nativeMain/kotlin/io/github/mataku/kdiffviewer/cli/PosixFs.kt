@file:OptIn(ExperimentalForeignApi::class)

package io.github.mataku.kdiffviewer.cli

import io.github.mataku.kdiffviewer.protocol.Codec
import io.github.mataku.kdiffviewer.protocol.RegistryEntry
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.pointed
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import platform.posix.EINTR
import platform.posix.EPERM
import platform.posix.O_RDONLY
import platform.posix.PATH_MAX
import platform.posix.close
import platform.posix.closedir
import platform.posix.errno
import platform.posix.getcwd
import platform.posix.getenv
import platform.posix.kill
import platform.posix.open
import platform.posix.opendir
import platform.posix.read
import platform.posix.readdir
import platform.posix.realpath

internal fun posixCwd(): String {
  val buffer = ByteArray(PATH_MAX)
  return buffer.usePinned { getcwd(it.addressOf(0), buffer.size.convert())?.toKString() } ?: "."
}

internal fun posixEnv(name: String): String? = getenv(name)?.toKString()

internal fun posixRealpath(path: String): String? {
  val buffer = ByteArray(PATH_MAX)
  return buffer.usePinned { realpath(path, it.addressOf(0))?.toKString() }
}

internal fun posixIsAlive(pid: Long): Boolean = kill(pid.toInt(), 0) == 0 || errno == EPERM

internal fun readFd(fd: Int): String {
  var acc = ByteArray(0)
  val chunk = ByteArray(65536)
  while (true) {
    val count = chunk.usePinned { read(fd, it.addressOf(0), chunk.size.convert()) }.toInt()
    if (count < 0) {
      if (errno == EINTR) continue
      return acc.decodeToString()
    }
    if (count == 0) return acc.decodeToString()
    acc += chunk.copyOfRange(0, count)
  }
}

internal fun readFile(path: String): String? {
  val fd = open(path, O_RDONLY)
  if (fd < 0) return null
  try {
    return readFd(fd)
  } finally {
    close(fd)
  }
}

internal fun posixListRegistry(dir: String): List<RegistryEntry> {
  val handle = opendir(dir) ?: return emptyList()
  val names = mutableListOf<String>()
  try {
    while (true) {
      val entry = readdir(handle) ?: break
      val name = entry.pointed.d_name.toKString()
      if (name.endsWith(".json")) names += name
    }
  } finally {
    closedir(handle)
  }
  return names.mapNotNull { name ->
    val text = readFile("$dir/$name") ?: return@mapNotNull null
    try {
      Codec.json.decodeFromString(RegistryEntry.serializer(), text)
    } catch (e: Exception) {
      null
    }
  }
}
