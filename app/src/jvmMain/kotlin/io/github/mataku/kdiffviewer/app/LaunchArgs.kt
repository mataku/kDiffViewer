package io.github.mataku.kdiffviewer.app

import io.github.mataku.kdiffviewer.core.model.BaseSpec
import io.github.mataku.kdiffviewer.core.model.RepoPath
import java.io.File

sealed interface LaunchArgs {
  data object None : LaunchArgs

  data class Open(val repo: RepoPath, val base: BaseSpec) : LaunchArgs

  data class Invalid(val repo: RepoPath, val message: String) : LaunchArgs
}

fun parseLaunchArgs(args: Array<String>, cwd: String): LaunchArgs {
  if (args.isEmpty()) return LaunchArgs.None
  var path: String? = null
  var base: BaseSpec = BaseSpec.Default
  var error: String? = null
  var i = 0
  while (i < args.size && error == null) {
    val arg = args[i]
    when {
      arg == "--base" -> {
        val value = args.getOrNull(i + 1)
        when {
          value == null -> error = "missing value for --base"
          value.startsWith("-") -> error = "invalid --base '$value'"
          else -> base = BaseSpec.Ref(value)
        }
        i++
      }

      arg.startsWith("-") -> error = "unknown option '$arg'"

      path == null -> path = arg

      else -> error = "unexpected argument '$arg'"
    }
    i++
  }
  val repo = resolve(path ?: cwd, cwd)
  return if (error != null) LaunchArgs.Invalid(repo, error) else LaunchArgs.Open(repo, base)
}

private fun resolve(path: String, cwd: String): RepoPath {
  val file = File(path)
  val resolved = if (file.isAbsolute) file else File(cwd, path)
  return RepoPath(resolved.toPath().normalize().toString())
}
