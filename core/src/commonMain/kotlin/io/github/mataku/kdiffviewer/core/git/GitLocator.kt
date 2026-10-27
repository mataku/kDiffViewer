package io.github.mataku.kdiffviewer.core.git

data class GitInfo(val path: String, val version: String)

sealed interface GitLocation {
  data class Found(val info: GitInfo) : GitLocation

  data object NotFound : GitLocation

  data class ConfiguredUnavailable(val path: String) : GitLocation
}

object GitLocator {
  private const val USR_BIN = "/usr/bin"

  val FALLBACK_PATHS: List<String> = listOf(
    "/opt/homebrew/bin/git",
    "/usr/local/bin/git",
    "/usr/bin/git",
  )

  fun locate(configured: String?, pathEnv: String?, probe: (String) -> GitInfo?): GitLocation {
    if (configured != null) {
      return probe(configured)?.let(GitLocation::Found) ?: GitLocation.ConfiguredUnavailable(configured)
    }
    val fromPath = pathEnv.orEmpty()
      .split(':')
      .filter { it.startsWith("/") && it.trimEnd('/') != USR_BIN }
      .map { "${it.trimEnd('/')}/git" }
    for (candidate in (fromPath + FALLBACK_PATHS).distinct()) {
      val info = probe(candidate) ?: continue
      return GitLocation.Found(info)
    }
    return GitLocation.NotFound
  }
}
