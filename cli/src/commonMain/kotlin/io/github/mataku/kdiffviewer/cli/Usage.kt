package io.github.mataku.kdiffviewer.cli

private val COMMON_OPTIONS = "Common options: --repo <path>, --pid <pid>, -h/--help"

private val THREAD_LINES = listOf(
  "kdv thread list [--all] [--json]         Unresolved threads (all with --all)",
  "kdv thread reply <id> <body|-> [--author <name>]",
  "kdv thread resolve <id>",
)

fun usage(topic: String?): String {
  val lines = if (topic == "thread") {
    THREAD_LINES
  } else {
    listOf("kdv [path]                               Open path in kDiffViewer (no argument: current directory)") +
      THREAD_LINES +
      listOf(
        "kdv reload                               Reload the diff and re-anchor comments",
        "kdv windows                              Running windows (pid, repository)",
        "kdv agent-guide                          How a coding agent should use kdv",
      )
  }
  return (lines + COMMON_OPTIONS).joinToString("\n") + "\n"
}
