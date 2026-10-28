package io.github.mataku.kdiffviewer.cli

data class TargetOptions(val repo: String? = null, val pid: Long? = null)

sealed interface Command {
  data class Open(val path: String?, val base: String? = null) : Command

  data class Help(val topic: String?) : Command

  data class ThreadList(val all: Boolean, val json: Boolean, val target: TargetOptions) : Command

  data class ThreadReply(val id: String, val body: String, val author: String, val target: TargetOptions) : Command

  data class ThreadResolve(val id: String, val target: TargetOptions) : Command

  data class ReviewWait(val timeoutSeconds: Int?, val json: Boolean, val target: TargetOptions) : Command

  data class Reload(val target: TargetOptions) : Command

  data class Windows(val target: TargetOptions) : Command

  data class AgentGuide(val target: TargetOptions) : Command
}

sealed interface ParseResult {
  data class Ok(val command: Command) : ParseResult

  data class UsageError(val message: String, val topic: String?) : ParseResult
}
