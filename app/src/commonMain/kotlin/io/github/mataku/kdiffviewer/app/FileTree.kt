package io.github.mataku.kdiffviewer.app

import androidx.compose.runtime.Immutable
import io.github.mataku.kdiffviewer.core.model.FileDiff
import io.github.mataku.kdiffviewer.core.model.displayPath
import io.github.mataku.kdiffviewer.protocol.markControls

@Immutable
data class TreeRow(val depth: Int, val name: String, val fileIndex: Int?)

private class TreeNode {
  val directoryByName = HashMap<String, TreeNode>()
  val directories = ArrayList<Pair<String, TreeNode>>()
  val files = ArrayList<Pair<String, Int>>()
  var unsorted = false
}

const val MAX_TREE_DEPTH = 4096

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
    val directoryCount = minOf(segments.lastIndex, MAX_TREE_DEPTH - 1)
    var node = root
    for (i in 0 until directoryCount) {
      val name = segments[i]
      node = node.directoryByName[name] ?: TreeNode().also { child ->
        node.directoryByName[name] = child
        node.directories += markControls(name) to child
        node.unsorted = true
      }
    }
    val fileName = if (directoryCount == segments.lastIndex) segments.last() else segments.subList(directoryCount, segments.size).joinToString("/")
    node.files += markControls(fileName) to fileIndex
    node.unsorted = true
  }

  private fun flatten() {
    val result = ArrayList<TreeRow>(rows.size + (fileCount - flattenedCount) * 2)
    emit(result)
    rows = result
    flattenedCount = fileCount
  }

  private fun emit(out: MutableList<TreeRow>) {
    val nodes = arrayListOf(sorted(root))
    val nextDirectories = arrayListOf(0)
    while (nodes.isNotEmpty()) {
      val depth = nodes.lastIndex
      val node = nodes[depth]
      val next = nextDirectories[depth]
      if (next < node.directories.size) {
        val (name, child) = node.directories[next]
        nextDirectories[depth] = next + 1
        out += TreeRow(depth, name, null)
        nodes += sorted(child)
        nextDirectories += 0
        continue
      }
      for ((name, fileIndex) in node.files) {
        out += TreeRow(depth, name, fileIndex)
      }
      nodes.removeAt(depth)
      nextDirectories.removeAt(depth)
    }
  }

  private fun sorted(node: TreeNode): TreeNode {
    if (node.unsorted) {
      node.directories.sortBy { it.first }
      node.files.sortWith(fileOrder)
      node.unsorted = false
    }
    return node
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

fun compactTree(rows: List<TreeRow>): List<TreeRow> {
  val compactable = BooleanArray(rows.size)
  val childCounts = IntArray(rows.size)
  val parents = ArrayList<Int>()
  for ((index, row) in rows.withIndex()) {
    while (parents.isNotEmpty() && rows[parents.last()].depth >= row.depth) parents.removeAt(parents.lastIndex)
    if (parents.isNotEmpty()) childCounts[parents.last()]++
    if (row.fileIndex == null) parents += index
  }
  var anyCompactable = false
  for (index in 0 until rows.lastIndex) {
    if (rows[index].fileIndex == null && childCounts[index] == 1 && rows[index + 1].fileIndex == null) {
      compactable[index] = true
      anyCompactable = true
    }
  }
  if (!anyCompactable) return rows
  val result = ArrayList<TreeRow>(rows.size)
  val shiftDepths = ArrayList<Int>()
  val shifts = ArrayList<Int>()
  var index = 0
  while (index < rows.size) {
    val row = rows[index]
    while (shiftDepths.isNotEmpty() && shiftDepths.last() >= row.depth) {
      shiftDepths.removeAt(shiftDepths.lastIndex)
      shifts.removeAt(shifts.lastIndex)
    }
    val shift = shifts.lastOrNull() ?: 0
    if (!compactable[index]) {
      result += if (shift == 0) row else row.copy(depth = row.depth - shift)
      index++
      continue
    }
    val name = StringBuilder(row.name)
    var last = index
    while (compactable[last]) {
      last++
      name.append('/').append(rows[last].name)
    }
    result += TreeRow(row.depth - shift, name.toString(), null)
    shiftDepths += rows[last].depth
    shifts += shift + (last - index)
    index = last + 1
  }
  return result
}

@Immutable
class DirectoryPath(val parent: DirectoryPath?, val name: String) {
  private val length: Int = if (parent == null) name.length else parent.length + 1 + name.length
  private val hash: Int

  init {
    var h = 0
    if (parent != null) h = 31 * parent.hash + '/'.code
    for (c in name) h = 31 * h + c.code
    hash = h
  }

  override fun hashCode(): Int = hash

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is DirectoryPath || length != other.length || hash != other.hash) return false
    val mine = ReverseChars(this)
    val theirs = ReverseChars(other)
    while (mine.hasNext()) {
      if (mine.next() != theirs.next()) return false
    }
    return true
  }

  override fun toString(): String {
    val chars = CharArray(length)
    var end = length
    var node: DirectoryPath? = this
    while (node != null) {
      val start = end - node.name.length
      node.name.toCharArray(chars, start)
      if (start > 0) chars[start - 1] = '/'
      end = start - 1
      node = node.parent
    }
    return chars.concatToString()
  }

  private class ReverseChars(private var node: DirectoryPath) {
    private var index = node.name.length

    fun hasNext(): Boolean = index > 0 || node.parent != null

    fun next(): Char {
      if (index == 0) {
        node = node.parent!!
        index = node.name.length
        return '/'
      }
      return node.name[--index]
    }
  }
}

@Immutable
data class TreeEntry(val row: TreeRow, val directoryPath: DirectoryPath?, val collapsed: Boolean)

fun visibleTree(rows: List<TreeRow>, collapsed: Set<String>): List<TreeEntry> {
  val result = ArrayList<TreeEntry>(rows.size)
  val ancestors = ArrayList<DirectoryPath>()
  val collapsedPaths = collapsed.mapTo(HashSet(collapsed.size)) { DirectoryPath(null, it) }
  var hiddenBelowDepth = Int.MAX_VALUE
  for (row in rows) {
    if (row.depth > hiddenBelowDepth) continue
    hiddenBelowDepth = Int.MAX_VALUE
    if (row.fileIndex != null) {
      result += TreeEntry(row, null, false)
      continue
    }
    while (ancestors.size > row.depth) ancestors.removeAt(ancestors.lastIndex)
    val path = DirectoryPath(if (row.depth == 0) null else ancestors.last(), row.name)
    ancestors += path
    val isCollapsed = path in collapsedPaths
    if (isCollapsed) hiddenBelowDepth = row.depth
    result += TreeEntry(row, path, isCollapsed)
  }
  return result
}
