package io.github.mataku.kdiffviewer.app.highlight

actual fun Throwable.isNativeLoadFailure(): Boolean = this is LinkageError

actual val availableProcessors: Int = Runtime.getRuntime().availableProcessors()
