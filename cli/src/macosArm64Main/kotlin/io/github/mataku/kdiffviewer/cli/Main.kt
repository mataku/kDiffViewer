@file:OptIn(ExperimentalForeignApi::class)

package io.github.mataku.kdiffviewer.cli

import io.github.mataku.kdiffviewer.protocol.RegistryEntry
import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.fputs
import platform.posix.stderr
import kotlin.system.exitProcess

object MacPlatform : CliPlatform {
  override fun cwd(): String = posixCwd()

  override fun home(): String = posixEnv("HOME") ?: ""

  override fun env(name: String): String? = posixEnv(name)

  override fun realpath(path: String): String? = posixRealpath(path)

  override fun listRegistry(dir: String): List<RegistryEntry> = posixListRegistry(dir)

  override fun isAlive(pid: Long): Boolean = posixIsAlive(pid)

  override fun request(socket: String, line: String): String = unixRequest(socket, line)

  override fun readStdin(): String = readFd(0)

  override fun launchApp(app: String, path: String): Boolean = posixLaunchApp(app, path)
}

fun main(args: Array<String>) {
  exitProcess(run(args.toList(), MacPlatform, ::println, { fputs(it + "\n", stderr) }))
}
