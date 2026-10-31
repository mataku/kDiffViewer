@file:OptIn(ExperimentalForeignApi::class)

package io.github.mataku.kdiffviewer.cli

import io.github.mataku.kdiffviewer.cli.spawn.kdv_spawn_detached
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.set
import platform.posix.X_OK
import platform.posix.access

// The app runs in its own process group and is not waited for, so closing the terminal that ran kdv does not
// send it SIGHUP through the shell's job control.
internal actual fun posixLaunchApp(app: String, path: String, base: String?): Boolean = memScoped {
  if (access(app, X_OK) != 0) return@memScoped false
  val arguments = listOf(app, path) + (base?.let { listOf("--base", it) } ?: emptyList())
  val argv = allocArray<CPointerVar<ByteVar>>(arguments.size + 1)
  arguments.forEachIndexed { index, value -> argv[index] = value.cstr.ptr }
  argv[arguments.size] = null
  kdv_spawn_detached(app, argv) == 0
}
