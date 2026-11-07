package io.github.mataku.kdiffviewer.app.markdown

import androidx.compose.runtime.Immutable
import io.github.mataku.kdiffviewer.protocol.controlMarker
import io.github.mataku.kdiffviewer.protocol.isHiddenControl

const val RENDER_SOURCE_LIMIT = 1_048_576

@Immutable
data class SourceBlock(
  val block: MdBlock,
  val startLine: Int,
  val endLine: Int,
  val indent: Int,
  val quoteDepth: Int,
  val marker: String?,
)

sealed interface SourceParse {
  class Blocks(val blocks: List<SourceBlock>) : SourceParse

  data object TooLarge : SourceParse

  data object Failed : SourceParse
}

internal fun listMarker(ordered: Boolean, start: Int, index: Int, checked: Boolean?): String = when {
  checked != null -> if (checked) "☑" else "☐"
  ordered -> "${start + index}."
  else -> "•"
}

internal fun MdBlock.withMarkedControls(): MdBlock = when (this) {
  is MdBlock.Paragraph -> copy(text = text.withMarkedControls())
  is MdBlock.Heading -> copy(text = text.withMarkedControls())
  is MdBlock.CodeBlock -> copy(code = markKeepingNewlines(code))
  else -> this
}

private fun MdInline.withMarkedControls(): MdInline = copy(text = markKeepingNewlines(text))

private fun markKeepingNewlines(text: String): String {
  val first = text.indexOfFirst { it != '\n' && isHiddenControl(it) }
  if (first < 0) return text
  val chars = text.toCharArray()
  for (index in first until chars.size) {
    if (chars[index] != '\n') chars[index] = controlMarker(chars[index])
  }
  return chars.concatToString()
}

internal class LineStarts(source: String) {
  private val starts: IntArray = buildList {
    add(0)
    source.forEachIndexed { index, char -> if (char == '\n') add(index + 1) }
  }.toIntArray()

  fun lineOf(offset: Int): Int {
    val search = starts.binarySearch(offset)
    return if (search >= 0) search + 1 else -search - 1
  }
}
