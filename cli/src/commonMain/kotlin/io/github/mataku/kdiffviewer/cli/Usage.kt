package io.github.mataku.kdiffviewer.cli

private val COMMON_OPTIONS = "Common options: --repo <path>, --pid <pid>, -h/--help"

private val THREAD_LINES = listOf(
  "kdv thread list [--all] [--json]         Unresolved threads (all with --all)",
  "kdv thread reply <id> <body|-> [--author <name>]",
  "kdv thread resolve <id>",
)

private val REVIEW_LINES = listOf(
  "kdv review wait [--timeout <s>] [--json] Wait until the user sends comments from the app",
)

fun usage(topic: String?): String {
  val lines = if (topic == "thread") {
    THREAD_LINES
  } else if (topic == "review") {
    REVIEW_LINES
  } else {
    listOf(
      "kdv [path] [--base <ref>]                Open path in kDiffViewer (no argument: current directory)",
      "                                         Base defaults to the merge-base with origin/HEAD",
    ) +
      THREAD_LINES +
      REVIEW_LINES +
      listOf(
        "kdv reload                               Reload the diff and re-anchor comments",
        "kdv windows                              Running windows (pid, repository)",
        "kdv agent-guide                          How a coding agent should use kdv",
      )
  }
  return (lines + COMMON_OPTIONS).joinToString("\n") + "\n"
}
