package io.github.mataku.kdiffviewer.app.markdown

actual fun isStackOverflow(t: Throwable): Boolean = t is StackOverflowError
