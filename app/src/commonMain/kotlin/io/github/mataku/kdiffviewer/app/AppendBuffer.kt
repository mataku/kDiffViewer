package io.github.mataku.kdiffviewer.app

class AppendBuffer<T> {
  private var items: Array<Any?> = arrayOfNulls(INITIAL_CAPACITY)
  private var size = 0

  fun add(item: T) {
    ensureCapacity(size + 1)
    items[size++] = item
  }

  fun addAll(newItems: Collection<T>) {
    ensureCapacity(size + newItems.size)
    for (item in newItems) items[size++] = item
  }

  fun snapshot(): List<T> = AppendOnlyView(items, size)

  private fun ensureCapacity(required: Int) {
    if (required <= items.size) return
    var capacity = items.size * 2
    while (capacity < required) capacity *= 2
    items = items.copyOf(capacity)
  }

  private companion object {
    const val INITIAL_CAPACITY = 16
  }
}

private class AppendOnlyView<T>(
  private val items: Array<Any?>,
  override val size: Int,
) : AbstractList<T>(),
  RandomAccess {
  @Suppress("UNCHECKED_CAST")
  override fun get(index: Int): T {
    if (index !in 0 until size) throw IndexOutOfBoundsException("index: $index, size: $size")
    return items[index] as T
  }

  override fun equals(other: Any?): Boolean {
    if (other === this) return true
    if (other is AppendOnlyView<*> && other.items === items && other.size == size) return true
    if (other !is List<*> || other.size != size) return false
    return super.equals(other)
  }

  override fun hashCode(): Int = super.hashCode()
}
