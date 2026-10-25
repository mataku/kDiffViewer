package io.github.mataku.kdiffviewer.app

import io.github.mataku.kdiffviewer.core.model.RepoPath
import io.github.mataku.kdiffviewer.core.model.displayPath

data class ScrollAnchor(val displayPath: String, val rowOffset: Int)

fun scrollAnchorOf(state: ViewerUiState, firstVisibleIndex: Int): ScrollAnchor? {
    val headers = state.fileHeaderRowIndex
    if (headers.isEmpty()) return null
    val search = headers.binarySearch(firstVisibleIndex)
    val fileIndex = if (search >= 0) search else -search - 2
    if (fileIndex < 0) return null
    return ScrollAnchor(state.files[fileIndex].displayPath, firstVisibleIndex - headers[fileIndex])
}

fun restoreTargetOf(anchor: ScrollAnchor, state: ViewerUiState): Int? {
    val fileIndex = state.files.indexOfFirst { it.displayPath == anchor.displayPath }
    if (fileIndex < 0) return null
    val header = state.fileHeaderRowIndex[fileIndex]
    val lastRow = state.fileHeaderRowIndex.getOrNull(fileIndex + 1)?.minus(1) ?: state.rows.lastIndex
    return minOf(header + anchor.rowOffset, lastRow)
}

class ScrollRestorer {
    private var loadId: Long? = null
    private var repo: RepoPath? = null
    private var lastAnchor: ScrollAnchor? = null
    private var pending: ScrollAnchor? = null

    fun next(state: ViewerUiState, firstVisibleIndex: Int): Int? {
        if (state.loadId != loadId) {
            pending = lastAnchor.takeIf { repo != null && state.repo == repo }
            lastAnchor = null
            loadId = state.loadId
            repo = state.repo
        }
        val anchor = pending
        if (anchor != null) {
            val target = restoreTargetOf(anchor, state)
            if (target != null || state.status != LoadStatus.Loading) pending = null
            return target
        }
        lastAnchor = scrollAnchorOf(state, firstVisibleIndex)
        return null
    }
}
