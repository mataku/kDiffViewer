package io.github.mataku.kdiffviewer.core.review

import io.github.mataku.kdiffviewer.core.model.ChangeType
import io.github.mataku.kdiffviewer.core.model.DiffContent
import io.github.mataku.kdiffviewer.core.model.DiffLine
import io.github.mataku.kdiffviewer.core.model.FileDiff
import io.github.mataku.kdiffviewer.core.model.Hunk
import io.github.mataku.kdiffviewer.core.model.LineKind

fun ctx(text: String, old: Int, new: Int) = DiffLine(LineKind.Context, text, old, new)

fun added(text: String, new: Int) = DiffLine(LineKind.Added, text, null, new)

fun removed(text: String, old: Int) = DiffLine(LineKind.Removed, text, old, null)

fun hunk(vararg lines: DiffLine) = Hunk(1, 1, 1, 1, "@@", lines.toList())

fun modified(path: String, vararg hunks: Hunk) = FileDiff(path, path, ChangeType.Modified, false, DiffContent.Text(hunks.toList()))

fun addedFile(path: String, vararg hunks: Hunk, untracked: Boolean = false) = FileDiff(null, path, ChangeType.Added, untracked, DiffContent.Text(hunks.toList()))

fun deletedFile(path: String, vararg hunks: Hunk) = FileDiff(path, null, ChangeType.Deleted, false, DiffContent.Text(hunks.toList()))

fun linesAnchor(path: String, side: Side, start: Int, end: Int, vararg snapshot: String) = Anchor.Lines(path, side, start, end, snapshot.toList())
