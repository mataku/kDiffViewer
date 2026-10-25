package io.github.mataku.kdiffviewer.core.git

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class FakeGitCommandRunner : GitCommandRunner {
  class Call(val workingDir: String, val args: List<String>)

  class Stream(val lines: List<String>, val exitCode: Int = 0, val stderr: String = "")

  val runResults = mutableMapOf<List<String>, GitResult>()
  val streams = mutableMapOf<List<String>, Stream>()
  val runCalls = mutableListOf<Call>()
  val streamCalls = mutableListOf<Call>()

  override suspend fun run(workingDir: String, args: List<String>): GitResult {
    runCalls += Call(workingDir, args)
    return runResults[args] ?: GitResult(0, "", "")
  }

  override fun streamLines(workingDir: String, args: List<String>): Flow<String> = flow {
    streamCalls += Call(workingDir, args)
    val stream = streams[args] ?: Stream(emptyList())
    stream.lines.forEach { emit(it) }
    if (stream.exitCode != 0) throw GitCommandFailedException(args, stream.exitCode, stream.stderr)
  }
}
