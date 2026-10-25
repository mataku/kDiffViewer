package io.github.mataku.kdiffviewer.core.git

interface WorkingTreeReader {
  suspend fun read(root: String, relativePath: String, maxBytes: Long): UntrackedEntry
}
