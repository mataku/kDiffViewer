package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import io.github.mataku.compose.highlight.core.HighlightSpan
import io.github.mataku.compose.highlight.core.SyntaxTheme
import io.github.mataku.kdiffviewer.core.model.Hunk
import io.github.mataku.kdiffviewer.core.model.RepoPath
import io.github.mataku.kdiffviewer.protocol.controlMarker
import io.github.mataku.kdiffviewer.protocol.isHiddenControl

private fun StringBuilder.appendExpanded(char: Char, tabWidth: Int) {
  if (char == '\t') {
    repeat(tabWidth - length % tabWidth) { append(' ') }
  } else {
    append(char)
  }
}

fun expandTabs(text: String, tabWidth: Int): String {
  if ('\t' !in text) return text
  return expandAll(text, tabWidth)
}

private fun expandAll(text: String, tabWidth: Int): String {
  val builder = StringBuilder(text.length + tabWidth)
  for (char in text) builder.appendExpanded(char, tabWidth)
  return builder.toString()
}

private const val HAS_TAB = 1
private const val HAS_CONTROL = 2

private fun scanSpecials(text: String): Int {
  var flags = 0
  for (char in text) {
    if (char == '\t') {
      flags = flags or HAS_TAB
    } else if (isHiddenControl(char)) {
      flags = flags or HAS_CONTROL
    }
  }
  return flags
}

private fun StringBuilder.appendMarked(
  text: String,
  tabWidth: Int,
  controlStyle: SpanStyle,
  offsets: IntArray?,
): List<AnnotatedString.Range<SpanStyle>> {
  val controls = ArrayList<AnnotatedString.Range<SpanStyle>>()
  var runStart = -1
  for (index in text.indices) {
    val char = text[index]
    offsets?.set(index, length)
    val hidden = isHiddenControl(char)
    if (hidden && runStart < 0) {
      runStart = length
    } else if (!hidden && runStart >= 0) {
      controls.add(AnnotatedString.Range(controlStyle, runStart, length))
      runStart = -1
    }
    appendExpanded(controlMarker(char), tabWidth)
  }
  offsets?.set(text.length, length)
  if (runStart >= 0) controls.add(AnnotatedString.Range(controlStyle, runStart, length))
  return controls
}

fun plainDisplayText(text: String, tabWidth: Int, controlStyle: SpanStyle): CharSequence {
  val flags = scanSpecials(text)
  if (flags == 0) return text
  if (flags == HAS_TAB) return expandAll(text, tabWidth)
  val builder = StringBuilder(text.length + tabWidth)
  val controls = builder.appendMarked(text, tabWidth, controlStyle, offsets = null)
  return AnnotatedString(builder.toString(), spanStyles = controls, paragraphStyles = emptyList())
}

fun displayToSource(text: String, displayOffset: Int, tabWidth: Int): Int {
  if ('\t' !in text) return displayOffset.coerceIn(0, text.length)
  var column = 0
  for (index in text.indices) {
    if (displayOffset <= column) return index
    if (text[index] == '\t') {
      val width = tabWidth - column % tabWidth
      if (displayOffset < column + width) {
        return if (displayOffset - column < width / 2.0) index else index + 1
      }
      column += width
    } else {
      column++
    }
  }
  return text.length
}

fun sourceToDisplay(text: String, sourceOffset: Int, tabWidth: Int): Int {
  val end = sourceOffset.coerceIn(0, text.length)
  if ('\t' !in text) return end
  var column = 0
  for (index in 0 until end) {
    column += if (text[index] == '\t') tabWidth - column % tabWidth else 1
  }
  return column
}

fun displayText(
  text: String,
  spans: List<HighlightSpan>,
  theme: SyntaxTheme,
  tabWidth: Int,
  controlStyle: SpanStyle,
): AnnotatedString {
  if (scanSpecials(text) == 0) {
    val ranges = resolveRanges(spans, theme) { it }
    return AnnotatedString(text, spanStyles = ranges, paragraphStyles = emptyList())
  }
  val builder = StringBuilder(text.length + tabWidth)
  val offsets = IntArray(text.length + 1)
  val controls = builder.appendMarked(text, tabWidth, controlStyle, offsets)
  val ranges = resolveRanges(spans, theme) { offsets[it] }
  ranges.addAll(controls)
  return AnnotatedString(builder.toString(), spanStyles = ranges, paragraphStyles = emptyList())
}

private inline fun resolveRanges(
  spans: List<HighlightSpan>,
  theme: SyntaxTheme,
  mapOffset: (Int) -> Int,
): ArrayList<AnnotatedString.Range<SpanStyle>> {
  val ranges = ArrayList<AnnotatedString.Range<SpanStyle>>(spans.size)
  for (span in spans) {
    val style = theme.resolve(span.captureName) ?: continue
    ranges.add(AnnotatedString.Range(style, mapOffset(span.start), mapOffset(span.end)))
  }
  return ranges
}

fun hunkHeaderLabel(hunk: Hunk): String {
  val range = "@@ -${hunk.oldStart},${hunk.oldCount} +${hunk.newStart},${hunk.newCount} @@"
  val functionName = hunk.header.trim()
  return if (functionName.isEmpty()) range else "$range $functionName"
}

fun repoName(repo: RepoPath): String {
  val trimmed = repo.value.trimEnd('/')
  return if (trimmed.isEmpty()) repo.value else trimmed.substringAfterLast('/')
}
