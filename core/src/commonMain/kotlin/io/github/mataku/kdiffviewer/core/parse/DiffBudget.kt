package io.github.mataku.kdiffviewer.core.parse

import io.github.mataku.kdiffviewer.core.model.DiffContent
import io.github.mataku.kdiffviewer.core.model.FileDiff

const val MAX_DIFF_FILES = 1_000
const val MAX_DIFF_LINES = 100_000

data class DiffLimits(val maxFiles: Int = MAX_DIFF_FILES, val maxLines: Int = MAX_DIFF_LINES)

class DiffBudget(private val limits: DiffLimits) {
  private var files = 0
  private var lines = 0
  private var closed = false

  fun admit(file: FileDiff): Boolean {
    if (closed) return false
    val fileLines = (file.content as? DiffContent.Text)?.hunks?.sumOf { it.lines.size } ?: 0
    if (files + 1 > limits.maxFiles || lines + fileLines > limits.maxLines) {
      closed = true
      return false
    }
    files += 1
    lines += fileLines
    return true
  }
}
