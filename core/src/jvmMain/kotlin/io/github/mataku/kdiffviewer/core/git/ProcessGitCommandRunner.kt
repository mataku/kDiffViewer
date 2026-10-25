package io.github.mataku.kdiffviewer.core.git

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.io.Reader

class ProcessGitCommandRunner(private val gitExecutable: String) : GitCommandRunner {
    override suspend fun run(workingDir: String, args: List<String>): GitResult = withContext(Dispatchers.IO) {
        val process = try {
            start(workingDir, args)
        } catch (e: IOException) {
            return@withContext GitResult(-1, "", e.message ?: "Failed to start git")
        }
        guarded(process) {
            val stderr = async { process.errorStream.readBytes().decodeToString() }
            val stdout = process.inputStream.readBytes().decodeToString()
            GitResult(process.waitFor(), stdout, stderr.await())
        }
    }

    override fun streamLines(workingDir: String, args: List<String>): Flow<String> = flow {
        val process = try {
            start(workingDir, args)
        } catch (e: IOException) {
            throw GitCommandFailedException(args, -1, e.message ?: "Failed to start git")
        }
        guarded(process) {
            val stderr = async { process.errorStream.readBytes().decodeToString() }
            InputStreamReader(process.inputStream, Charsets.UTF_8).use { reader ->
                reader.forEachLine { emit(it) }
            }
            val errorText = stderr.await()
            val exitCode = process.waitFor()
            if (exitCode != 0) throw GitCommandFailedException(args, exitCode, errorText)
        }
    }.flowOn(Dispatchers.IO)

    private fun start(workingDir: String, args: List<String>): Process {
        val dir = File(workingDir)
        if (!dir.isDirectory) throw IOException("No such directory: $workingDir")
        return ProcessBuilder(listOf(gitExecutable) + args)
            .directory(dir)
            .start()
            .also { it.outputStream.close() }
    }

    private suspend fun <T> guarded(process: Process, block: suspend CoroutineScope.() -> T): T = try {
        coroutineScope {
            val watcher = launch {
                try {
                    awaitCancellation()
                } finally {
                    process.destroyForcibly()
                }
            }
            try {
                block()
            } finally {
                watcher.cancel()
            }
        }
    } finally {
        if (process.isAlive) process.destroyForcibly()
    }
}

private suspend inline fun Reader.forEachLine(emit: (String) -> Unit) {
    val buffer = CharArray(8192)
    val line = StringBuilder()
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        for (i in 0 until count) {
            val c = buffer[i]
            if (c == '\n') {
                emit(line.dropTrailingCarriageReturn())
                line.setLength(0)
            } else {
                line.append(c)
            }
        }
    }
    if (line.isNotEmpty()) emit(line.dropTrailingCarriageReturn())
}

private fun StringBuilder.dropTrailingCarriageReturn(): String =
    if (isNotEmpty() && this[length - 1] == '\r') substring(0, length - 1) else toString()
