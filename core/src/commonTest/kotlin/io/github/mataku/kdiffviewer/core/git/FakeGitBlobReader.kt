package io.github.mataku.kdiffviewer.core.git

class FakeGitBlobReader(
  var entry: BlobEntry = BlobEntry.Missing,
  var failure: Throwable? = null,
) : GitBlobReader {
  data class Call(val revision: String, val path: String, val maxBytes: Long)

  val calls = mutableListOf<Call>()
  var closed = false

  override suspend fun read(revision: String, path: String, maxBytes: Long): BlobEntry {
    calls += Call(revision, path, maxBytes)
    failure?.let { throw it }
    return entry
  }

  override fun close() {
    closed = true
  }
}
