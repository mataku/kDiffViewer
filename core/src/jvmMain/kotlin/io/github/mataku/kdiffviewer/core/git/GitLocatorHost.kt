package io.github.mataku.kdiffviewer.core.git

import java.io.File
import java.util.concurrent.TimeUnit

private const val VERSION_PREFIX = "git version"

fun probeGit(path: String, timeoutMillis: Long = 2_000): GitInfo? {
  val file = File(path)
  if (!file.isAbsolute || !file.isFile || !file.canExecute()) return null
  val process = try {
    ProcessBuilder(path, "--version")
      .redirectError(ProcessBuilder.Redirect.DISCARD)
      .start()
  } catch (_: java.io.IOException) {
    return null
  }
  process.outputStream.close()
  try {
    if (!process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)) {
      process.destroyForcibly()
      return null
    }
    if (process.exitValue() != 0) return null
    val firstLine = process.inputStream.bufferedReader().readLine() ?: return null
    return if (firstLine.startsWith(VERSION_PREFIX)) GitInfo(path, firstLine) else null
  } catch (e: InterruptedException) {
    process.destroyForcibly()
    throw e
  }
}

fun GitLocator.locateOnHost(configured: String?, pathEnv: String? = System.getenv("PATH")): GitLocation = locate(configured, pathEnv, ::probeGit)
