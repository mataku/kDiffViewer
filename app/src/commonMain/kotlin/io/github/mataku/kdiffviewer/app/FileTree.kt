package io.github.mataku.kdiffviewer.app

import androidx.compose.runtime.Immutable
import io.github.mataku.kdiffviewer.core.model.FileDiff
import io.github.mataku.kdiffviewer.core.model.displayPath

@Immutable
data class TreeRow(val depth: Int, val name: String, val fileIndex: Int?)

private class TreeNode {
  val directoryByName = HashMap<String, TreeNode>()
  val directories = ArrayList<Pair<String, TreeNode>>()
  val files = ArrayList<Pair<String, Int>>()
  var unsorted = false
}

private val fileOrder = compareBy<Pair<String, Int>>({ it.first }, { it.second })

class FileTreeBuilder {
  private val root = TreeNode()
  private var fileCount = 0
  private var flattenedCount = 0
  private var rows: List<TreeRow> = emptyList()

  fun add(files: List<FileDiff>) {
    for (file in files) insert(file.displayPath, fileCount++)
  }

  fun rowsIfDue(): List<TreeRow> {
    if (fileCount - flattenedCount >= maxOf(1, flattenedCount / 4)) flatten()
    return rows
  }

  fun rows(): List<TreeRow> {
    if (fileCount != flattenedCount) flatten()
    return rows
  }

  private fun insert(path: String, fileIndex: Int) {
    val segments = path.split('/')
    var node = root
    for (i in 0 until segments.lastIndex) {
      val name = segments[i]
      node = node.directoryByName[name] ?: TreeNode().also { child ->
        node.directoryByName[name] = child
        node.directories += name to child
        node.unsorted = true
      }
    }
    node.files += segments.last() to fileIndex
    node.unsorted = true
  }

  private fun flatten() {
    val result = ArrayList<TreeRow>(rows.size + (fileCount - flattenedCount) * 2)
    emit(root, 0, result)
    rows = result
    flattenedCount = fileCount
  }

  private fun emit(node: TreeNode, depth: Int, out: MutableList<TreeRow>) {
    if (node.unsorted) {
      node.directories.sortBy { it.first }
      node.files.sortWith(fileOrder)
      node.unsorted = false
    }
    for ((name, child) in node.directories) {
      out += TreeRow(depth, name, null)
      emit(child, depth + 1, out)
    }
    for ((name, fileIndex) in node.files) {
      out += TreeRow(depth, name, fileIndex)
    }
  }
}

fun buildFileTree(files: List<FileDiff>): List<TreeRow> = FileTreeBuilder().apply { add(files) }.rows()

fun filterTree(rows: List<TreeRow>, files: List<FileDiff>, query: String): List<TreeRow> {
  val needle = query.trim()
  if (needle.isEmpty()) return rows
  val result = ArrayList<TreeRow>()
  val pending = ArrayList<TreeRow>()
  val emitted = ArrayList<Boolean>()
  for (row in rows) {
    while (pending.size > row.depth) {
      pending.removeAt(pending.lastIndex)
      emitted.removeAt(emitted.lastIndex)
    }
    val fileIndex = row.fileIndex
    if (fileIndex == null) {
      pending += row
      emitted += false
      continue
    }
    val path = files.getOrNull(fileIndex)?.displayPath ?: continue
    if (path.contains(needle, ignoreCase = true)) {
      for (i in pending.indices) {
        if (!emitted[i]) {
          result += pending[i]
          emitted[i] = true
        }
      }
      result += row
    }
  }
  return result
}

@Immutable
data class TreeEntry(val row: TreeRow, val directoryPath: String?, val collapsed: Boolean)

fun visibleTree(rows: List<TreeRow>, collapsed: Set<String>): List<TreeEntry> {
  val result = ArrayList<TreeEntry>(rows.size)
  val ancestors = ArrayList<String>()
  var hiddenBelowDepth = Int.MAX_VALUE
  for (row in rows) {
    if (row.depth > hiddenBelowDepth) continue
    hiddenBelowDepth = Int.MAX_VALUE
    if (row.fileIndex != null) {
      result += TreeEntry(row, null, false)
      continue
    }
    while (ancestors.size > row.depth) ancestors.removeAt(ancestors.lastIndex)
    val path = if (row.depth == 0) row.name else "${ancestors.last()}/${row.name}"
    ancestors += path
    val isCollapsed = path in collapsed
    if (isCollapsed) hiddenBelowDepth = row.depth
    result += TreeEntry(row, path, isCollapsed)
  }
  return result
}
