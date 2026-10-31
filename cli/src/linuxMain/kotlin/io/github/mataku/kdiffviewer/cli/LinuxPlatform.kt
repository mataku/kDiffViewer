@file:OptIn(ExperimentalForeignApi::class)

package io.github.mataku.kdiffviewer.cli

import io.github.mataku.kdiffviewer.protocol.HostOs
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.set
import platform.posix.AF_UNIX
import platform.posix.SIGPIPE
import platform.posix.SIG_IGN
import platform.posix.signal

private const val INSTALL_DIR = "/opt/kdiffviewer"
private const val LAUNCHER = "bin/kDiffViewer"

internal actual val hostOs: HostOs = HostOs.Linux

// The packaged kdv lives at <install dir>/lib/app/resources/kdv, so the launcher is found relative to it;
// /usr/bin/kdv is a symlink, which realpath resolves.
internal actual fun posixDefaultApp(): String {
  val installDir = posixRealpath("/proc/self/exe")?.substringBeforeLast("/lib/app/resources/kdv", "")?.ifEmpty { null } ?: INSTALL_DIR
  return "$installDir/$LAUNCHER"
}

internal actual val SOCKADDR_UN_SIZE: Int = 110
internal actual val SUN_PATH_CAPACITY: Int = 108

// sun_family is a little-endian 16-bit value on x64 and arm64.
internal actual fun writeSockaddrHeader(raw: CPointer<UByteVar>) {
  raw[0] = AF_UNIX.toUByte()
  raw[1] = 0u
}

// Linux has no SO_NOSIGPIPE; ignoring the signal makes a write to a closed socket fail with EPIPE instead.
internal actual fun suppressSigpipe(fd: Int) {
  signal(SIGPIPE, SIG_IGN)
}
