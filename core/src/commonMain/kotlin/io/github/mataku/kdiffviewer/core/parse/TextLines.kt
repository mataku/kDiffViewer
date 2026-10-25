package io.github.mataku.kdiffviewer.core.parse

fun splitLines(text: String): List<String> {
    if (text.isEmpty()) return emptyList()
    val parts = text.split('\n')
    val lines = if (parts.last().isEmpty()) parts.dropLast(1) else parts
    return lines.map { it.removeSuffix("\r") }
}
