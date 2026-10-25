package io.github.mataku.kdiffviewer.core.git

sealed interface BlobEntry {
  class Regular(val bytes: ByteArray) : BlobEntry

  data object TooLarge : BlobEntry

  data object Missing : BlobEntry
}

interface GitBlobReader : AutoCloseable {
  suspend fun read(revision: String, path: String, maxBytes: Long): BlobEntry
}
