package io.github.mataku.kdiffviewer.protocol

private const val REPLACEMENT_CHARACTER = '�'

fun isHiddenControl(c: Char): Boolean = when (c) {
  '\t' -> false
  in '\u0000'..'\u001F', '\u007F', in '\u0080'..'\u009F' -> true
  '؜', '‎', '‏', in '‪'..'‮', in '⁦'..'⁩' -> true
  else -> false
}

fun controlMarker(c: Char): Char = when {
  !isHiddenControl(c) -> c
  c <= '\u001F' -> '␀' + c.code
  c == '\u007F' -> '␡'
  else -> REPLACEMENT_CHARACTER
}

fun markControls(text: String): String {
  val first = text.indexOfFirst(::isHiddenControl)
  if (first < 0) return text
  val chars = text.toCharArray()
  for (index in first until chars.size) chars[index] = controlMarker(chars[index])
  return chars.concatToString()
}
