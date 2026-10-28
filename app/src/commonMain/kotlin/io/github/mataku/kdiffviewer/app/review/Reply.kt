package io.github.mataku.kdiffviewer.app.review

class Reply(val line: String, val onAbandoned: () -> Unit = {}, val onWritten: () -> Unit = {})
