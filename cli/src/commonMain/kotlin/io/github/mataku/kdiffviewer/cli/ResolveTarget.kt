package io.github.mataku.kdiffviewer.cli

import io.github.mataku.kdiffviewer.protocol.RegistryEntry

sealed interface Resolution {
  data class Found(val entry: RegistryEntry) : Resolution

  data object NoWindow : Resolution

  data class Ambiguous(val pids: List<Long>) : Resolution
}

fun resolveTarget(
  cwdReal: String,
  options: TargetOptions,
  entries: List<RegistryEntry>,
  realpath: (String) -> String?,
): Resolution {
  options.pid?.let { pid ->
    val entry = entries.firstOrNull { it.pid == pid }
    return if (entry != null) Resolution.Found(entry) else Resolution.NoWindow
  }
  val base = options.repo?.let { realpath(it) } ?: if (options.repo == null) cwdReal else return Resolution.NoWindow
  val matches = entries.filter { entry ->
    val root = entry.repoRoot?.trimEnd('/')
    root != null && (base == root || base.startsWith("$root/"))
  }
  if (matches.isEmpty()) return Resolution.NoWindow
  val longest = matches.maxOf { it.repoRoot!!.trimEnd('/').length }
  val best = matches.filter { it.repoRoot!!.trimEnd('/').length == longest }
  return if (best.size == 1) Resolution.Found(best.single()) else Resolution.Ambiguous(best.map { it.pid })
}
