package io.github.mataku.kdiffviewer.core.git

import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ProcessGitBlobReaderTest {
  private val repo: File = newTempDir()
  private val readers = mutableListOf<ProcessGitBlobReader>()
  private val special = "a\r\nb\u0000c\n".toByteArray()
  private val small = "small\n".toByteArray()
  private val large = ByteArray(2048) { ('a' + it % 26).code.toByte() }

  private val head: String by lazy {
    runGit(repo, "init", "-q")
    File(repo, "special.bin").writeBytes(special)
    File(repo, "small.txt").writeBytes(small)
    File(repo, "large.txt").writeBytes(large)
    File(repo, "src").mkdirs()
    File(repo, "src/Main.kt").writeText("fun main() {}\n")
    runGit(repo, "add", "-A")
    runGit(repo, "commit", "-q", "-m", "init")
    runGit(repo, "rev-parse", "HEAD").trim()
  }

  private fun reader(): ProcessGitBlobReader = ProcessGitBlobReader(requireGit(), repo.path).also { readers += it }

  @AfterTest
  fun tearDown() {
    readers.forEach { it.close() }
    repo.deleteRecursively()
  }

  @Test
  fun regularBlobIsReadExactly() = runBlocking {
    val entry = reader().read(head, "special.bin", 1024)
    assertContentEquals(special, assertIs<BlobEntry.Regular>(entry).bytes)
  }

  @Test
  fun blobAboveLimitIsTooLarge() = runBlocking {
    assertEquals(BlobEntry.TooLarge, reader().read(head, "large.txt", 1024))
  }

  @Test
  fun readerStaysInSyncAfterTooLarge() = runBlocking {
    val reader = reader()
    assertEquals(BlobEntry.TooLarge, reader.read(head, "large.txt", 1024))
    assertContentEquals(small, assertIs<BlobEntry.Regular>(reader.read(head, "small.txt", 1024)).bytes)
  }

  @Test
  fun missingPathIsMissing() = runBlocking {
    assertEquals(BlobEntry.Missing, reader().read(head, "nope.kt", 1024))
  }

  @Test
  fun directoryIsMissing() = runBlocking {
    assertEquals(BlobEntry.Missing, reader().read(head, "src", 1024))
  }

  @Test
  fun pathWithNewlineIsMissing() = runBlocking {
    val reader = reader()
    assertEquals(BlobEntry.Missing, reader.read(head, "a\nb", 1024))
    assertContentEquals(small, assertIs<BlobEntry.Regular>(reader.read(head, "small.txt", 1024)).bytes)
  }

  @Test
  fun manySequentialReadsSucceed() = runBlocking {
    val reader = reader()
    repeat(200) {
      val (path, expected) = if (it % 2 == 0) "special.bin" to special else "small.txt" to small
      assertContentEquals(expected, assertIs<BlobEntry.Regular>(reader.read(head, path, 1024)).bytes)
    }
  }

  @Test
  fun closeIsIdempotent() = runBlocking {
    val reader = reader()
    reader.read(head, "small.txt", 1024)
    reader.close()
    reader.close()
  }
}
