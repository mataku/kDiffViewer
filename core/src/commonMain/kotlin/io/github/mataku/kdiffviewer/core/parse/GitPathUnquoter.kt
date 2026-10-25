package io.github.mataku.kdiffviewer.core.parse

fun unquoteGitPath(raw: String): String {
  if (raw.length < 2 || !raw.startsWith('"') || !raw.endsWith('"')) return raw
  val inner = raw.substring(1, raw.length - 1)
  val out = StringBuilder()
  val pending = mutableListOf<Byte>()
  fun flush() {
    if (pending.isNotEmpty()) {
      out.append(pending.toByteArray().decodeToString())
      pending.clear()
    }
  }
  var i = 0
  while (i < inner.length) {
    val c = inner[i]
    if (c != '\\' || i + 1 >= inner.length) {
      flush()
      out.append(c)
      i++
      continue
    }
    val next = inner[i + 1]
    if (next in '0'..'7') {
      var end = i + 1
      while (end < inner.length && end < i + 4 && inner[end] in '0'..'7') end++
      pending += inner.substring(i + 1, end).toInt(8).toByte()
      i = end
      continue
    }
    flush()
    val simple = when (next) {
      'a' -> '\u0007'
      'b' -> '\b'
      'f' -> '\u000C'
      'n' -> '\n'
      'r' -> '\r'
      't' -> '\t'
      'v' -> '\u000B'
      else -> next
    }
    out.append(simple)
    i += 2
  }
  flush()
  return out.toString()
}

fun parseDiffGitHeaderPaths(rest: String): Pair<String, String>? {
  if (rest.startsWith('"')) {
    val end = closingQuoteIndex(rest) ?: return null
    val first = rest.substring(0, end + 1)
    if (rest.length <= end + 2 || rest[end + 1] != ' ') return null
    val second = rest.substring(end + 2)
    return pairOf(unquoteGitPath(first), unquoteGitPath(second))
  }
  if (rest.endsWith('"')) {
    val split = rest.indexOf(" \"")
    if (split < 0) return null
    return pairOf(rest.substring(0, split), unquoteGitPath(rest.substring(split + 1)))
  }
  val pathLength = (rest.length - 5) / 2
  if (rest.length >= 5 && (rest.length - 5) % 2 == 0 && rest.startsWith("a/")) {
    val old = rest.substring(2, 2 + pathLength)
    val separator = rest.substring(2 + pathLength, 5 + pathLength)
    val new = rest.substring(5 + pathLength)
    if (separator == " b/" && old == new) return old to new
  }
  val split = rest.indexOf(" b/")
  if (split < 0) return null
  return pairOf(rest.substring(0, split), rest.substring(split + 1))
}

private fun pairOf(oldRaw: String, newRaw: String): Pair<String, String>? {
  if (!oldRaw.startsWith("a/") || !newRaw.startsWith("b/")) return null
  return oldRaw.removePrefix("a/") to newRaw.removePrefix("b/")
}

private fun closingQuoteIndex(text: String): Int? {
  var i = 1
  while (i < text.length) {
    when (text[i]) {
      '\\' -> i++
      '"' -> return i
    }
    i++
  }
  return null
}
