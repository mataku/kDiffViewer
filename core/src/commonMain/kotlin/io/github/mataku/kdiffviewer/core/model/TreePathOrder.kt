package io.github.mataku.kdiffviewer.core.model

object TreePathOrder : Comparator<String> {
  override fun compare(a: String, b: String): Int = compareSegments(a.split('/'), b.split('/'))

  internal fun compareSegments(a: List<String>, b: List<String>): Int {
    for (i in 0 until minOf(a.size, b.size)) {
      val aIsDirectory = i < a.lastIndex
      val bIsDirectory = i < b.lastIndex
      if (aIsDirectory != bIsDirectory) return if (aIsDirectory) -1 else 1
      val byName = a[i].compareTo(b[i])
      if (byName != 0) return byName
    }
    return a.size.compareTo(b.size)
  }
}
