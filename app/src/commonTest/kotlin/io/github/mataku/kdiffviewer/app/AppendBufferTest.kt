package io.github.mataku.kdiffviewer.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AppendBufferTest {
  @Test
  fun snapshotHoldsItemsAppendedSoFar() {
    val buffer = AppendBuffer<Int>()
    buffer.addAll(listOf(1, 2))
    buffer.add(3)
    assertEquals(listOf(1, 2, 3), buffer.snapshot())
  }

  @Test
  fun emptyBufferSnapshotIsEmpty() {
    assertEquals(emptyList(), AppendBuffer<String>().snapshot())
  }

  @Test
  fun earlierSnapshotIsUnchangedByLaterAppendsIncludingGrowth() {
    val buffer = AppendBuffer<Int>()
    buffer.addAll(listOf(1, 2, 3))
    val before = buffer.snapshot()
    buffer.addAll((4..1000).toList())
    assertEquals(listOf(1, 2, 3), before)
    assertEquals((1..1000).toList(), buffer.snapshot())
  }

  @Test
  fun snapshotRejectsIndexOutsideItsSize() {
    val buffer = AppendBuffer<Int>()
    buffer.addAll(listOf(1, 2))
    val snapshot = buffer.snapshot()
    buffer.add(3)
    assertFailsWith<IndexOutOfBoundsException> { snapshot[2] }
    assertFailsWith<IndexOutOfBoundsException> { snapshot[-1] }
  }

  @Test
  fun snapshotsOfSameContentAreEqualRegardlessOfImplementation() {
    val buffer = AppendBuffer<Int>()
    buffer.addAll(listOf(1, 2))
    val first = buffer.snapshot()
    val second = buffer.snapshot()
    assertEquals(first, second)
    assertEquals(first.hashCode(), second.hashCode())
    assertEquals(listOf(1, 2), first)
    assertEquals(first, listOf(1, 2))
    assertEquals(listOf(1, 2).hashCode(), first.hashCode())
  }

  @Test
  fun snapshotsOfDifferentSizesAreNotEqual() {
    val buffer = AppendBuffer<Int>()
    buffer.add(1)
    val shorter = buffer.snapshot()
    buffer.add(2)
    assertNotEquals(shorter, buffer.snapshot())
    assertNotEquals<List<Int>>(listOf(9, 9), buffer.snapshot())
  }

  @Test
  fun equalitySkipsElementComparisonWhenSizesDiffer() {
    val buffer = AppendBuffer<Any>()
    buffer.add(ThrowingEquals())
    val shorter = buffer.snapshot()
    buffer.add(ThrowingEquals())
    assertTrue(shorter != buffer.snapshot())
    assertTrue(buffer.snapshot() != shorter)
  }

  @Test
  fun equalitySkipsElementComparisonForSnapshotsOfSameBufferAndSize() {
    val buffer = AppendBuffer<Any>()
    buffer.add(ThrowingEquals())
    assertTrue(buffer.snapshot() == buffer.snapshot())
  }

  private class ThrowingEquals {
    override fun equals(other: Any?): Boolean = throw AssertionError("element equals must not be called")

    override fun hashCode(): Int = 0
  }
}
