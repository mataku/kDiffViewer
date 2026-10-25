package io.github.mataku.kdiffviewer.core.parse

import io.github.mataku.kdiffviewer.core.model.ChangeType
import io.github.mataku.kdiffviewer.core.model.DiffContent
import io.github.mataku.kdiffviewer.core.model.DiffLine
import io.github.mataku.kdiffviewer.core.model.FileDiff
import io.github.mataku.kdiffviewer.core.model.Hunk
import io.github.mataku.kdiffviewer.core.model.LineKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

object UnifiedDiffParser {
    private const val FILE_START = "diff --git "
    private val hunkHeader = Regex("""^@@ -(\d+)(?:,(\d+))? \+(\d+)(?:,(\d+))? @@(.*)$""")

    fun parse(lines: Flow<String>): Flow<FileDiff> = flow {
        var file: FileBuilder? = null
        lines.collect { line ->
            if (line.startsWith(FILE_START)) {
                file?.build()?.let { emit(it) }
                file = FileBuilder()
            } else {
                file?.accept(line)
            }
        }
        file?.build()?.let { emit(it) }
    }

    private class FileBuilder {
        private var oldPath: String? = null
        private var newPath: String? = null
        private val hunks = mutableListOf<Hunk>()
        private var hunk: HunkBuilder? = null

        fun accept(line: String) {
            val current = hunk
            if (current == null) {
                acceptHeader(line)
                return
            }
            val match = hunkHeader.matchEntire(line)
            if (match != null) {
                finishHunk()
                startHunk(match)
            } else {
                current.accept(line)
            }
        }

        private fun acceptHeader(line: String) {
            val match = hunkHeader.matchEntire(line)
            when {
                match != null -> startHunk(match)
                line.startsWith("--- ") -> oldPath = stripPrefix(line.removePrefix("--- "), "a/")
                line.startsWith("+++ ") -> newPath = stripPrefix(line.removePrefix("+++ "), "b/")
            }
        }

        private fun stripPrefix(path: String, prefix: String): String = path.removePrefix(prefix)

        private fun startHunk(match: MatchResult) {
            val (oldStart, oldCount, newStart, newCount, header) = match.destructured
            hunk = HunkBuilder(
                oldStart = oldStart.toInt(),
                oldCount = if (oldCount.isEmpty()) 1 else oldCount.toInt(),
                newStart = newStart.toInt(),
                newCount = if (newCount.isEmpty()) 1 else newCount.toInt(),
                header = header,
            )
        }

        private fun finishHunk() {
            hunk?.let { hunks += it.build() }
            hunk = null
        }

        fun build(): FileDiff {
            finishHunk()
            return FileDiff(
                oldPath = oldPath,
                newPath = newPath,
                change = ChangeType.Modified,
                isUntracked = false,
                content = DiffContent.Text(hunks.toList()),
            )
        }
    }

    private class HunkBuilder(
        private val oldStart: Int,
        private val oldCount: Int,
        private val newStart: Int,
        private val newCount: Int,
        private val header: String,
    ) {
        private val lines = mutableListOf<DiffLine>()
        private var oldNo = oldStart
        private var newNo = newStart

        fun accept(line: String) {
            val text = line.drop(1)
            when (line.firstOrNull()) {
                ' ' -> lines += DiffLine(LineKind.Context, text, oldNo++, newNo++)
                '-' -> lines += DiffLine(LineKind.Removed, text, oldNo++, null)
                '+' -> lines += DiffLine(LineKind.Added, text, null, newNo++)
                '\\' -> lines += DiffLine(LineKind.NoNewlineAtEof, text, null, null)
            }
        }

        fun build(): Hunk = Hunk(oldStart, oldCount, newStart, newCount, header, lines.toList())
    }
}
