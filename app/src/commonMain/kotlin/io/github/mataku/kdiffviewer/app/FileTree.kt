package io.github.mataku.kdiffviewer.app

import io.github.mataku.kdiffviewer.core.model.FileDiff
import io.github.mataku.kdiffviewer.core.model.displayPath

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
