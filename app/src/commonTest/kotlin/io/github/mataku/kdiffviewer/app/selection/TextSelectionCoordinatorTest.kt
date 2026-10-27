package io.github.mataku.kdiffviewer.app.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class FakeSource(text: String?) : SelectionSource {
  var text by mutableStateOf(text)
  var clearCount = 0
  override val hasSelection: Boolean get() = text != null
  override fun selectedText(): String? = text
  override fun clear() {
    clearCount++
    text = null
  }
}

private class FakeClipboard : ClipboardWriter {
  val writes = mutableListOf<String>()
  override fun write(text: String) {
    writes += text
  }
}

class TextSelectionCoordinatorTest {
  private val clipboard = FakeClipboard()
  private var copyOnSelect = false
  private val coordinator = TextSelectionCoordinator(clipboard) { copyOnSelect }

  @Test
  fun activatingAnotherSourceClearsPrevious() {
    val first = FakeSource("a")
    val second = FakeSource("b")
    coordinator.activate(first)
    coordinator.activate(second)
    assertEquals(1, first.clearCount)
    assertEquals(0, second.clearCount)
  }

  @Test
  fun activatingSameSourceDoesNotClear() {
    val first = FakeSource("a")
    coordinator.activate(first)
    coordinator.activate(first)
    assertEquals(0, first.clearCount)
  }

  @Test
  fun copyWritesActiveText() {
    coordinator.activate(FakeSource("hello"))
    assertTrue(coordinator.copy())
    assertEquals(listOf("hello"), clipboard.writes)
  }

  @Test
  fun copyWithNoSelectionWritesNothing() {
    assertFalse(coordinator.copy())
    coordinator.activate(FakeSource(null))
    assertFalse(coordinator.copy())
    coordinator.activate(FakeSource(""))
    assertFalse(coordinator.copy())
    assertEquals(emptyList(), clipboard.writes)
  }

  @Test
  fun copyOnSelectWritesOnlyWhenEnabled() {
    val source = FakeSource("x")
    coordinator.activate(source)
    coordinator.onSelectionFinished(source)
    assertEquals(emptyList(), clipboard.writes)
    copyOnSelect = true
    coordinator.onSelectionFinished(source)
    assertEquals(listOf("x"), clipboard.writes)
  }

  @Test
  fun copyOnSelectIgnoresInactiveSource() {
    copyOnSelect = true
    val first = FakeSource("a")
    val second = FakeSource("b")
    coordinator.activate(first)
    coordinator.activate(second)
    coordinator.onSelectionFinished(first)
    assertEquals(emptyList(), clipboard.writes)
  }

  @Test
  fun releaseForgetsActiveSource() {
    val source = FakeSource("a")
    coordinator.activate(source)
    assertTrue(coordinator.hasSelection)
    coordinator.release(source)
    assertFalse(coordinator.hasSelection)
    assertFalse(coordinator.copy())
  }
}
