package io.github.mataku.kdiffviewer.app.ui

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
