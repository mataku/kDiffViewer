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
