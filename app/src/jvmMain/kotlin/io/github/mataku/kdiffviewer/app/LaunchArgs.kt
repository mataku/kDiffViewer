package io.github.mataku.kdiffviewer.app

import io.github.mataku.kdiffviewer.core.model.RepoPath
import java.io.File

fun parseLaunchArgs(args: Array<String>, cwd: String): RepoPath? {
  val first = args.firstOrNull() ?: return null
  val file = File(first)
  val resolved = if (file.isAbsolute) file else File(cwd, first)
  return RepoPath(resolved.toPath().normalize().toString())
}
