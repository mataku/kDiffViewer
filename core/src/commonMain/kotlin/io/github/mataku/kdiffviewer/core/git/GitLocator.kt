package io.github.mataku.kdiffviewer.core.git

data class GitInfo(val path: String, val version: String)

sealed interface GitLocation {
  data class Found(val info: GitInfo, val configuredRejected: Boolean) : GitLocation

  data object NotFound : GitLocation
}

object GitLocator {
  private const val USR_BIN = "/usr/bin"

  val FALLBACK_PATHS: List<String> = listOf(
    "/opt/homebrew/bin/git",
    "/usr/local/bin/git",
    "/usr/bin/git",
  )

  fun locate(configured: String?, pathEnv: String?, probe: (String) -> GitInfo?): GitLocation {
    val fromPath = pathEnv.orEmpty()
      .split(':')
      .filter { it.isNotEmpty() && it.trimEnd('/') != USR_BIN }
      .map { "${it.trimEnd('/')}/git" }
    val candidates = (listOfNotNull(configured) + fromPath + FALLBACK_PATHS).distinct()
    for (candidate in candidates) {
      val info = probe(candidate) ?: continue
      return GitLocation.Found(info, configuredRejected = configured != null && candidate != configured)
    }
    return GitLocation.NotFound
  }
}
