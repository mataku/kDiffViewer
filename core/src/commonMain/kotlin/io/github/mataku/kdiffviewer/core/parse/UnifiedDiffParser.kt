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
    private const val DEV_NULL = "/dev/null"
    private val hunkHeader = Regex("""^@@ -(\d+)(?:,(\d+))? \+(\d+)(?:,(\d+))? @@(.*)$""")

    fun parse(lines: Flow<String>): Flow<FileDiff> = flow {
        var file: FileBuilder? = null
        lines.collect { line ->
            if (line.startsWith(FILE_START)) {
                file?.build()?.let { emit(it) }
                file = FileBuilder(parseDiffGitHeaderPaths(line.removePrefix(FILE_START)))
            } else {
                file?.accept(line)
            }
        }
        file?.build()?.let { emit(it) }
    }

    private fun parseMarkerPath(raw: String, prefix: String): String? {
        val path = raw.removeSuffix("\t")
        if (path == DEV_NULL) return null
        return unquoteGitPath(path).removePrefix(prefix)
    }

    private class FileBuilder(private val headerPaths: Pair<String, String>?) {
        private var renameFrom: String? = null
        private var renameTo: String? = null
        private var minusSeen = false
        private var minusPath: String? = null
        private var plusSeen = false
        private var plusPath: String? = null
        private var newFile = false
        private var deletedFile = false
        private var binary = false
        private var failed = false
        private val hunks = mutableListOf<Hunk>()
        private var hunk: HunkBuilder? = null

        fun accept(line: String) {
            if (failed) return
            val current = hunk
            if (current == null) {
                acceptHeader(line)
                return
            }
            val match = hunkHeader.matchEntire(line)
            if (match != null) {
                if (!current.isComplete) {
                    failed = true
                    return
                }
                finishHunk()
                startHunk(match)
            } else if (!current.accept(line)) {
                failed = true
            }
        }

        private fun acceptHeader(line: String) {
            val match = hunkHeader.matchEntire(line)
            when {
                match != null -> startHunk(match)
                line.startsWith("--- ") -> {
                    minusSeen = true
                    minusPath = parseMarkerPath(line.removePrefix("--- "), "a/")
                }
                line.startsWith("+++ ") -> {
                    plusSeen = true
                    plusPath = parseMarkerPath(line.removePrefix("+++ "), "b/")
                }
                line.startsWith("new file mode") -> newFile = true
                line.startsWith("deleted file mode") -> deletedFile = true
                line.startsWith("rename from ") -> renameFrom = unquoteGitPath(line.removePrefix("rename from "))
                line.startsWith("rename to ") -> renameTo = unquoteGitPath(line.removePrefix("rename to "))
                line.startsWith("Binary files ") || line.startsWith("GIT binary patch") -> binary = true
            }
        }

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

        fun build(): FileDiff? {
            if (hunk?.isComplete == false) failed = true
            finishHunk()
            val change = when {
                newFile || (minusSeen && minusPath == null) -> ChangeType.Added
                deletedFile || (plusSeen && plusPath == null) -> ChangeType.Deleted
                renameFrom != null -> ChangeType.Renamed
                else -> ChangeType.Modified
            }
            var oldPath = renameFrom ?: if (minusSeen) minusPath else headerPaths?.first
            var newPath = renameTo ?: if (plusSeen) plusPath else headerPaths?.second
            if (change == ChangeType.Added) oldPath = null
            if (change == ChangeType.Deleted) newPath = null
            if (oldPath == null && newPath == null) return null
            val content = when {
                failed -> DiffContent.Unparseable
                binary -> DiffContent.Binary
                else -> DiffContent.Text(hunks.toList())
            }
            return FileDiff(
                oldPath = oldPath,
                newPath = newPath,
                change = change,
                isUntracked = false,
                content = content,
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
        private var oldRemaining = oldCount
        private var newRemaining = newCount

        val isComplete: Boolean
            get() = oldRemaining == 0 && newRemaining == 0

        fun accept(line: String): Boolean {
            if (line.isEmpty()) {
                if (isComplete) return true
                return addContext("")
            }
            val text = line.drop(1)
            return when (line[0]) {
                ' ' -> addContext(text)
                '-' -> {
                    if (oldRemaining == 0) return false
                    oldRemaining--
                    lines += DiffLine(LineKind.Removed, text, oldNo++, null)
                    true
                }
                '+' -> {
                    if (newRemaining == 0) return false
                    newRemaining--
                    lines += DiffLine(LineKind.Added, text, null, newNo++)
                    true
                }
                '\\' -> {
                    if (lines.isEmpty()) return false
                    lines += DiffLine(LineKind.NoNewlineAtEof, text, null, null)
                    true
                }
                else -> false
            }
        }

        private fun addContext(text: String): Boolean {
            if (oldRemaining == 0 || newRemaining == 0) return false
            oldRemaining--
            newRemaining--
            lines += DiffLine(LineKind.Context, text, oldNo++, newNo++)
            return true
        }

        fun build(): Hunk = Hunk(oldStart, oldCount, newStart, newCount, header, lines.toList())
    }
}
