package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import io.github.mataku.compose.highlight.core.HighlightSpan
import io.github.mataku.compose.highlight.core.SyntaxTheme
import io.github.mataku.kdiffviewer.core.model.Hunk
import io.github.mataku.kdiffviewer.core.model.RepoPath

private const val TAB_WIDTH = 8

private fun StringBuilder.appendExpanded(char: Char) {
  if (char == '\t') {
    repeat(TAB_WIDTH - length % TAB_WIDTH) { append(' ') }
  } else {
    append(char)
  }
}

fun expandTabs(text: String): String {
  if ('\t' !in text) return text
  val builder = StringBuilder(text.length + TAB_WIDTH)
  for (char in text) builder.appendExpanded(char)
  return builder.toString()
}

fun displayText(
  text: String,
  spans: List<HighlightSpan>,
  theme: SyntaxTheme,
): AnnotatedString {
  if ('\t' !in text) {
    val ranges = resolveRanges(spans, theme) { it }
    return AnnotatedString(text, spanStyles = ranges, paragraphStyles = emptyList())
  }
  val builder = StringBuilder(text.length + TAB_WIDTH)
  val offsets = IntArray(text.length + 1)
  for (index in text.indices) {
    offsets[index] = builder.length
    builder.appendExpanded(text[index])
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
