package io.github.mataku.kdiffviewer.core.git

import io.github.mataku.kdiffviewer.core.model.ChangeType
import io.github.mataku.kdiffviewer.core.model.DiffContent
import io.github.mataku.kdiffviewer.core.model.DiffLine
import io.github.mataku.kdiffviewer.core.model.FileDiff
import io.github.mataku.kdiffviewer.core.model.Hunk
import io.github.mataku.kdiffviewer.core.model.LineKind
import io.github.mataku.kdiffviewer.core.parse.splitLines

sealed interface UntrackedEntry {
    class Regular(val bytes: ByteArray) : UntrackedEntry
    data object TooLarge : UntrackedEntry
    data class Symlink(val target: String) : UntrackedEntry
    data object Unreadable : UntrackedEntry
}

const val UNTRACKED_SIZE_LIMIT: Long = 1_048_576

private const val BINARY_SNIFF_BYTES = 8000

fun synthesizeUntracked(path: String, entry: UntrackedEntry): FileDiff {
    val content = when (entry) {
        is UntrackedEntry.Regular -> regularContent(entry.bytes)
        UntrackedEntry.TooLarge -> DiffContent.TooLarge
        is UntrackedEntry.Symlink -> addedText(listOf(entry.target), endsWithNewline = false)
        UntrackedEntry.Unreadable -> DiffContent.Unparseable
    }
    return FileDiff(null, path, ChangeType.Added, isUntracked = true, content)
}

private fun regularContent(bytes: ByteArray): DiffContent {
    if (bytes.isEmpty()) return DiffContent.Text(emptyList())
    val sniffEnd = minOf(bytes.size, BINARY_SNIFF_BYTES)
    for (i in 0 until sniffEnd) {
        if (bytes[i] == 0.toByte()) return DiffContent.Binary
    }
    val lines = splitLines(bytes.decodeToString())
    return addedText(lines, endsWithNewline = bytes.last() == '\n'.code.toByte())
}

private fun addedText(lines: List<String>, endsWithNewline: Boolean): DiffContent.Text {
    val diffLines = ArrayList<DiffLine>(lines.size + 1)
    lines.forEachIndexed { index, text -> diffLines += DiffLine(LineKind.Added, text, null, index + 1) }
    if (!endsWithNewline) diffLines += DiffLine(LineKind.NoNewlineAtEof, "", null, null)
    return DiffContent.Text(listOf(Hunk(0, 0, 1, lines.size, "", diffLines)))
}
