package io.github.mataku.kdiffviewer.core.git

import io.github.mataku.kdiffviewer.core.model.TreePathOrder

internal class TreeOrderQueue<T> {
  private class Entry<T>(val segments: List<String>, val parentPrefix: String, val sequence: Long, val item: T)

  private val heap = ArrayList<Entry<T>>()
  private var nextSequence = 0L

  fun add(path: String, item: T) {
    heap += Entry(path.split('/'), path.substring(0, path.lastIndexOf('/') + 1), nextSequence++, item)
    siftUp(heap.lastIndex)
  }

  fun drainReady(lastStreamedPath: String): List<T> {
    val ready = ArrayList<T>()
    while (heap.isNotEmpty() && isReleased(heap[0].parentPrefix, lastStreamedPath)) ready += pop()
    return ready
  }

  fun drainAll(): List<T> {
    val all = ArrayList<T>(heap.size)
    while (heap.isNotEmpty()) all += pop()
    return all
  }

  private fun isReleased(parentPrefix: String, lastStreamedPath: String): Boolean = parentPrefix.isNotEmpty() && !lastStreamedPath.startsWith(parentPrefix) && lastStreamedPath > parentPrefix

  private fun pop(): T {
    val top = heap[0]
    val last = heap.removeAt(heap.lastIndex)
    if (heap.isNotEmpty()) {
      heap[0] = last
      siftDown(0)
    }
    return top.item
  }

  private fun less(a: Entry<T>, b: Entry<T>): Boolean {
    val byPath = TreePathOrder.compareSegments(a.segments, b.segments)
    return byPath < 0 || (byPath == 0 && a.sequence < b.sequence)
  }

  private fun siftUp(start: Int) {
    var child = start
    while (child > 0) {
      val parent = (child - 1) / 2
      if (!less(heap[child], heap[parent])) return
      swap(child, parent)
      child = parent
    }
  }

  private fun siftDown(start: Int) {
    var parent = start
    while (true) {
      val left = 2 * parent + 1
      if (left >= heap.size) return
      val right = left + 1
      val smaller = if (right < heap.size && less(heap[right], heap[left])) right else left
      if (!less(heap[smaller], heap[parent])) return
      swap(parent, smaller)
      parent = smaller
    }
  }

  private fun swap(i: Int, j: Int) {
    val tmp = heap[i]
    heap[i] = heap[j]
    heap[j] = tmp
  }
}
