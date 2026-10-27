package io.github.mataku.kdiffviewer.app.selection

import io.github.mataku.kdiffviewer.app.DiffRow
import io.github.mataku.kdiffviewer.app.flattenRows
import io.github.mataku.kdiffviewer.core.model.ChangeType
import io.github.mataku.kdiffviewer.core.model.DiffContent
import io.github.mataku.kdiffviewer.core.model.DiffLine
import io.github.mataku.kdiffviewer.core.model.FileDiff
import io.github.mataku.kdiffviewer.core.model.Hunk
import io.github.mataku.kdiffviewer.core.model.LineKind

internal fun context(text: String) = DiffLine(LineKind.Context, text, 1, 1)

internal fun hunk(vararg lines: DiffLine) = Hunk(1, lines.size, 1, lines.size, "", lines.toList())

internal fun textFile(path: String, vararg hunks: Hunk) = FileDiff(path, path, ChangeType.Modified, false, DiffContent.Text(hunks.toList()))

internal fun rowsOf(vararg files: FileDiff): List<DiffRow> = flattenRows(files.toList())
