package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import io.github.mataku.compose.highlight.core.HighlightSpan
import io.github.mataku.compose.highlight.core.SyntaxTheme
import io.github.mataku.kdiffviewer.core.model.Hunk
import io.github.mataku.kdiffviewer.core.model.RepoPath

private fun StringBuilder.appendExpanded(char: Char, tabWidth: Int) {
  if (char == '\t') {
    repeat(tabWidth - length % tabWidth) { append(' ') }
  } else {
    append(char)
  }
}

fun expandTabs(text: String, tabWidth: Int): String {
  if ('\t' !in text) return text
  val builder = StringBuilder(text.length + tabWidth)
  for (char in text) builder.appendExpanded(char, tabWidth)
  return builder.toString()
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
): AnnotatedString {
  if ('\t' !in text) {
    val ranges = resolveRanges(spans, theme) { it }
    return AnnotatedString(text, spanStyles = ranges, paragraphStyles = emptyList())
  }
  val builder = StringBuilder(text.length + tabWidth)
  val offsets = IntArray(text.length + 1)
  for (index in text.indices) {
    offsets[index] = builder.length
    builder.appendExpanded(text[index], tabWidth)
  }
  offsets[text.length] = builder.length
  val ranges = resolveRanges(spans, theme) { offsets[it] }
  return AnnotatedString(builder.toString(), spanStyles = ranges, paragraphStyles = emptyList())
}

private inline fun resolveRanges(
  spans: List<HighlightSpan>,
  theme: SyntaxTheme,
  mapOffset: (Int) -> Int,
): List<AnnotatedString.Range<SpanStyle>> {
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
