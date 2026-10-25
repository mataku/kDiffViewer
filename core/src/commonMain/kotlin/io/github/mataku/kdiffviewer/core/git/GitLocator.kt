package io.github.mataku.kdiffviewer.core.git

object GitLocator {
    val FALLBACK_PATHS: List<String> = listOf(
        "/opt/homebrew/bin/git",
        "/usr/local/bin/git",
        "/usr/bin/git",
    )

    fun locate(pathEnv: String?, isExecutable: (String) -> Boolean): String? {
        val fromPath = pathEnv.orEmpty()
            .split(':')
            .filter { it.isNotEmpty() }
            .map { "${it.trimEnd('/')}/git" }
        return (fromPath + FALLBACK_PATHS).firstOrNull(isExecutable)
    }
}
