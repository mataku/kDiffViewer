@file:OptIn(ExperimentalForeignApi::class)

package io.github.mataku.kdiffviewer.cli

import io.github.mataku.kdiffviewer.protocol.HostOs
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.cValuesOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.set
import kotlinx.cinterop.sizeOf
import platform.posix.AF_UNIX
import platform.posix.SOL_SOCKET
import platform.posix.setsockopt

private const val SO_NOSIGPIPE_OPTION = 0x1022

internal actual val hostOs: HostOs = HostOs.MacOs

internal actual fun posixDefaultApp(): String = "/Applications/kDiffViewer.app"

internal actual val SOCKADDR_UN_SIZE: Int = 106
internal actual val SUN_PATH_CAPACITY: Int = 104

internal actual fun writeSockaddrHeader(raw: CPointer<UByteVar>) {
  raw[0] = SOCKADDR_UN_SIZE.toUByte()
  raw[1] = AF_UNIX.toUByte()
}

internal actual fun suppressSigpipe(fd: Int) {
  setsockopt(fd, SOL_SOCKET, SO_NOSIGPIPE_OPTION, cValuesOf(1), sizeOf<IntVar>().convert())
}
