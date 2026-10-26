package io.github.mataku.kdiffviewer.app.markdown

private const val MAX_CONTAINER_DEPTH = 64
private const val INDENT_PER_LEVEL = 2

internal fun exceedsContainerDepth(source: String): Boolean {
  var i = 0
  val n = source.length
  while (i < n) {
    var end = source.indexOf('\n', i)
    if (end < 0) end = n
    if (lineDepth(source, i, end) > MAX_CONTAINER_DEPTH) return true
    i = end + 1
  }
  return false
}

private fun lineDepth(s: String, from: Int, end: Int): Int {
  var i = from
  var indent = 0
  while (i < end && (s[i] == ' ' || s[i] == '\t')) {
    indent += if (s[i] == '\t') 4 else 1
    i++
  }
  var markers = 0
  while (i < end) {
    val c = s[i]
    val next = when {
      c == '>' -> i + 1
      (c == '-' || c == '*' || c == '+') && i + 1 < end && s[i + 1] == ' ' -> i + 1
      c in '0'..'9' -> orderedMarkerEnd(s, i, end)
      else -> -1
    }
    if (next < 0) break
    markers++
    i = next
    while (i < end && (s[i] == ' ' || s[i] == '\t')) i++
  }
  return if (markers == 0) 0 else markers + indent / INDENT_PER_LEVEL
}

private fun orderedMarkerEnd(s: String, from: Int, end: Int): Int {
  var i = from
  while (i < end && s[i] in '0'..'9') i++
  return if (i > from && i + 1 < end && (s[i] == '.' || s[i] == ')') && s[i + 1] == ' ') i + 1 else -1
}
