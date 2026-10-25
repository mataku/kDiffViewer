package io.github.mataku.kdiffviewer.core.git

import kotlinx.coroutines.flow.Flow

data class GitResult(val exitCode: Int, val stdout: String, val stderr: String)

class GitCommandFailedException(
  val args: List<String>,
  val exitCode: Int,
  val stderr: String,
) : Exception("git ${args.joinToString(" ")} failed with exit code $exitCode: $stderr")

interface GitCommandRunner {
  suspend fun run(workingDir: String, args: List<String>): GitResult

  fun streamLines(workingDir: String, args: List<String>): Flow<String>
}
