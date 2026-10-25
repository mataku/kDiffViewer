package io.github.mataku.kdiffviewer.app

import io.github.mataku.kdiffviewer.core.model.FileDiff
import io.github.mataku.kdiffviewer.core.model.displayPath

data class TreeRow(val depth: Int, val name: String, val fileIndex: Int?)

private class TreeNode {
  val directories = mutableMapOf<String, TreeNode>()
  val files = ArrayList<Pair<String, Int>>()
}

fun buildFileTree(files: List<FileDiff>): List<TreeRow> {
  val root = TreeNode()
  files.forEachIndexed { fileIndex, file ->
    val segments = file.displayPath.split('/')
    var node = root
    for (segment in segments.dropLast(1)) {
      node = node.directories.getOrPut(segment) { TreeNode() }
    }
    node.files += segments.last() to fileIndex
  }
  val rows = ArrayList<TreeRow>()
  fun emit(node: TreeNode, depth: Int) {
    for ((name, child) in node.directories.entries.sortedBy { it.key }) {
      rows += TreeRow(depth, name, null)
      emit(child, depth + 1)
    }
    for ((name, fileIndex) in node.files.sortedWith(compareBy({ it.first }, { it.second }))) {
      rows += TreeRow(depth, name, fileIndex)
    }
  }
  emit(root, 0)
  return rows
}
