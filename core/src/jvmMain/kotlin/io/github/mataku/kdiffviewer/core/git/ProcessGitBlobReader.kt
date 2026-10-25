package io.github.mataku.kdiffviewer.core.git

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class ProcessGitBlobReader(
  private val gitExecutable: String,
  private val workingDir: String,
) : GitBlobReader {
  private val mutex = Mutex()
  private var session: Session? = null
  private var broken = false

  @Volatile
  private var closed = false

  override suspend fun read(revision: String, path: String, maxBytes: Long): BlobEntry {
    if (path.hasLineBreak() || revision.hasLineBreak()) return BlobEntry.Missing
    return mutex.withLock {
      withContext(Dispatchers.IO + NonCancellable) {
        val active = obtainSession() ?: return@withContext BlobEntry.Missing
        try {
          active.read("$revision:$path", maxBytes)
        } catch (e: Throwable) {
          discard(active)
          if (active.answered || e !is IOException) throw e
          broken = true
          BlobEntry.Missing
        }
      }
    }
  }

  override fun close() {
    closed = true
    val active = synchronized(this) { session.also { session = null } }
    active?.destroy()
  }

  private fun obtainSession(): Session? = synchronized(this) {
    if (closed) throw IOException("Blob reader is closed")
    if (broken) return null
    session ?: try {
      start().also { session = it }
    } catch (e: IOException) {
      broken = true
      null
    }
  }

  private fun String.hasLineBreak(): Boolean = contains('\n') || contains('\r')

  private fun discard(active: Session) {
    synchronized(this) { if (session === active) session = null }
    active.destroy()
  }

  private fun start(): Session {
    val dir = File(workingDir)
    if (!dir.isDirectory) throw IOException("No such directory: $workingDir")
    val process = ProcessBuilder(gitExecutable, "cat-file", "--batch-command")
      .directory(dir)
      .redirectError(ProcessBuilder.Redirect.DISCARD)
      .start()
    return Session(process, BufferedInputStream(process.inputStream), process.outputStream)
  }

  private class Session(
    private val process: Process,
    private val input: InputStream,
    private val output: OutputStream,
  ) {
    var answered = false
      private set

    fun read(objectName: String, maxBytes: Long): BlobEntry {
      send("info $objectName\n")
      val infoLine = readLine()
      answered = true
      val header = parseHeader(infoLine) ?: return BlobEntry.Missing
      if (header.size > maxBytes || header.size > Int.MAX_VALUE) return BlobEntry.TooLarge
      send("contents $objectName\n")
      val contentsHeader = parseHeader(readLine())
      if (contentsHeader == null || contentsHeader.size != header.size) {
        throw IOException("Unexpected cat-file response for $objectName")
      }
      val bytes = input.readNBytes(header.size.toInt())
      if (bytes.size.toLong() != header.size || input.read() != '\n'.code) {
        throw IOException("Truncated cat-file response for $objectName")
      }
      return BlobEntry.Regular(bytes)
    }

    fun destroy() {
      try {
        output.close()
      } catch (_: IOException) {
      }
      process.destroyForcibly()
    }

    private fun send(command: String) {
      output.write(command.toByteArray(Charsets.UTF_8))
      output.flush()
    }

    private fun readLine(): String {
      val line = ByteArrayOutputStream()
      while (true) {
        val b = input.read()
        if (b < 0) throw IOException("git cat-file closed its output")
        if (b == '\n'.code) return line.toString(Charsets.UTF_8)
        line.write(b)
      }
    }

    private fun parseHeader(line: String): BlobHeader? {
      if (line.endsWith(" missing")) return null
      val parts = line.split(' ')
      if (parts.size != 3 || parts[1] != "blob") return null
      val size = parts[2].toLongOrNull() ?: return null
      return BlobHeader(size)
    }
  }

  private class BlobHeader(val size: Long)
}
