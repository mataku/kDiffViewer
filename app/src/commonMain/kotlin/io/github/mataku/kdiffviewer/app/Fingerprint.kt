package io.github.mataku.kdiffviewer.app

import io.github.mataku.kdiffviewer.core.model.DiffContent
import io.github.mataku.kdiffviewer.core.model.FileDiff

private const val FNV_OFFSET_BASIS = -0x340d631b7bdddcdbL
private const val FNV_PRIME = 0x100000001b3L
private const val SEPARATOR = 0x1F
private const val NULL_MARKER = 0x1E

private class Fnv64 {
  var value = FNV_OFFSET_BASIS
    private set

  fun add(int: Int) {
    for (shift in 0 until 32 step 8) {
      value = (value xor ((int ushr shift) and 0xFF).toLong()) * FNV_PRIME
    }
  }

  fun add(boolean: Boolean) = add(if (boolean) 1 else 0)

  fun addNullable(int: Int?) {
    if (int == null) add(NULL_MARKER) else add(int)
  }

  fun add(string: String?) {
    if (string == null) {
      add(NULL_MARKER)
      return
    }
    for (char in string) {
      val code = char.code
      value = (value xor (code and 0xFF).toLong()) * FNV_PRIME
      value = (value xor (code ushr 8).toLong()) * FNV_PRIME
    }
    add(SEPARATOR)
  }
}

fun FileDiff.fingerprint(): Long {
  val hash = Fnv64()
  hash.add(change.ordinal)
  hash.add(isUntracked)
  hash.add(oldPath)
  hash.add(newPath)
  when (val content = content) {
    is DiffContent.Text -> {
      hash.add(0)
      hash.add(content.hunks.size)
      for (hunk in content.hunks) {
        hash.add(hunk.oldStart)
        hash.add(hunk.oldCount)
        hash.add(hunk.newStart)
        hash.add(hunk.newCount)
        hash.add(hunk.header)
        hash.add(hunk.lines.size)
        for (line in hunk.lines) {
          hash.add(line.kind.ordinal)
          hash.addNullable(line.oldLineNo)
          hash.addNullable(line.newLineNo)
          hash.add(line.text)
        }
      }
    }

    DiffContent.Binary -> hash.add(1)

    DiffContent.TooLarge -> hash.add(2)

    DiffContent.Unparseable -> hash.add(3)

    is DiffContent.TooManyLines -> {
      hash.add(4)
      hash.add(content.stats.additions)
      hash.add(content.stats.deletions)
    }
  }
  return hash.value
}
