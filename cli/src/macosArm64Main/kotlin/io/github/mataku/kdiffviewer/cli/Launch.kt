@file:OptIn(ExperimentalForeignApi::class)

package io.github.mataku.kdiffviewer.cli

import io.github.mataku.kdiffviewer.cli.spawn.kdv_environ
import io.github.mataku.kdiffviewer.cli.spawn.posix_spawn
import io.github.mataku.kdiffviewer.cli.spawn.posix_spawn_file_actions_addopen
import io.github.mataku.kdiffviewer.cli.spawn.posix_spawn_file_actions_destroy
import io.github.mataku.kdiffviewer.cli.spawn.posix_spawn_file_actions_init
import io.github.mataku.kdiffviewer.cli.spawn.posix_spawn_file_actions_tVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.set
import kotlinx.cinterop.value
import platform.posix.EINTR
import platform.posix.O_WRONLY
import platform.posix.errno
import platform.posix.pid_tVar
import platform.posix.waitpid

internal fun posixLaunchApp(app: String, path: String, base: String?): Boolean = memScoped {
  val arguments = listOf("open", "-n", "-a", app, "--args", path) + (base?.let { listOf("--base", it) } ?: emptyList())
  val argv = allocArray<kotlinx.cinterop.CPointerVar<kotlinx.cinterop.ByteVar>>(arguments.size + 1)
  arguments.forEachIndexed { index, value -> argv[index] = value.cstr.ptr }
  argv[arguments.size] = null
  val pid = alloc<pid_tVar>()
  val actions = alloc<posix_spawn_file_actions_tVar>()
  posix_spawn_file_actions_init(actions.ptr)
  posix_spawn_file_actions_addopen(actions.ptr, 1, "/dev/null", O_WRONLY, 0u)
  posix_spawn_file_actions_addopen(actions.ptr, 2, "/dev/null", O_WRONLY, 0u)
  val spawned = posix_spawn(pid.ptr, "/usr/bin/open", actions.ptr, null, argv, kdv_environ())
  posix_spawn_file_actions_destroy(actions.ptr)
  if (spawned != 0) return@memScoped false
  val status = alloc<IntVar>()
  while (waitpid(pid.value, status.ptr, 0) < 0) {
    if (errno != EINTR) return@memScoped false
  }
  status.value == 0
}
