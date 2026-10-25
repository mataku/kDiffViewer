package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import io.github.mataku.compose.highlight.core.HighlightSpan
import io.github.mataku.compose.highlight.core.SyntaxTheme
import io.github.mataku.kdiffviewer.core.model.Hunk

private const val TAB_WIDTH = 8

fun expandTabs(text: String): String {
  if ('\t' !in text) return text
  val builder = StringBuilder(text.length + TAB_WIDTH)
  for (char in text) {
    if (char == '\t') {
      repeat(TAB_WIDTH - builder.length % TAB_WIDTH) { builder.append(' ') }
    } else {
      builder.append(char)
    }
  }
  return builder.toString()
}

fun displayText(
  text: String,
  spans: List<HighlightSpan>,
  theme: SyntaxTheme,
): AnnotatedString {
  if ('\t' !in text) return AnnotatedString(text, spanStyles = resolveRanges(spans, theme) { it }, paragraphStyles = emptyList())
  val builder = StringBuilder(text.length + TAB_WIDTH)
  val offsets = IntArray(text.length + 1)
  for (index in text.indices) {
    offsets[index] = builder.length
    val char = text[index]
    if (char == '\t') {
      repeat(TAB_WIDTH - builder.length % TAB_WIDTH) { builder.append(' ') }
    } else {
      builder.append(char)
    }
  }
  offsets[text.length] = builder.length
  return AnnotatedString(builder.toString(), spanStyles = resolveRanges(spans, theme) { offsets[it] }, paragraphStyles = emptyList())
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
