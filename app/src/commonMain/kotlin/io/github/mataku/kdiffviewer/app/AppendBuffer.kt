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

class IntAppendBuffer {
  private var items = IntArray(INITIAL_CAPACITY)
  var size = 0
    private set

  fun add(value: Int) {
    ensureCapacity(size + 1)
    items[size++] = value
  }

  operator fun set(index: Int, value: Int) {
    if (index !in 0 until size) throw IndexOutOfBoundsException("index: $index, size: $size")
    items[index] = value
  }

  fun snapshot(): IntSnapshot = IntSnapshot(items, size)

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

class IntSnapshot internal constructor(
  private val items: IntArray,
  val size: Int,
) {
  operator fun get(index: Int): Int {
    if (index !in 0 until size) throw IndexOutOfBoundsException("index: $index, size: $size")
    return items[index]
  }

  override fun equals(other: Any?): Boolean {
    if (other === this) return true
    if (other !is IntSnapshot || other.size != size) return false
    if (other.items === items) return true
    for (i in 0 until size) if (items[i] != other.items[i]) return false
    return true
  }

  override fun hashCode(): Int {
    var result = 1
    for (i in 0 until size) result = 31 * result + items[i]
    return result
  }
}
