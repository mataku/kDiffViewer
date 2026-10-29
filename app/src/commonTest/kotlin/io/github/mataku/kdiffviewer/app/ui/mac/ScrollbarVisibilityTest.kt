package io.github.mataku.kdiffviewer.app.ui.mac

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ScrollbarVisibilityTest {
  private fun TestScope.running(): ScrollbarVisibility {
    val visibility = ScrollbarVisibility(hideDelayMillis = 1_000)
    backgroundScope.launch { visibility.run() }
    runCurrent()
    return visibility
  }

  @Test
  fun hiddenUntilScrolled() = runTest {
    val visibility = running()
    advanceTimeBy(5_000)
    assertFalse(visibility.visible)
  }

  @Test
  fun scrollShowsThenHidesAfterTheDelay() = runTest {
    val visibility = running()
    visibility.onScroll()
    runCurrent()
    assertTrue(visibility.visible)
    advanceTimeBy(999)
    assertTrue(visibility.visible)
    advanceTimeBy(2)
    assertFalse(visibility.visible)
  }

  @Test
  fun eachScrollRestartsTheDelay() = runTest {
    val visibility = running()
    visibility.onScroll()
    runCurrent()
    advanceTimeBy(800)
    visibility.onScroll()
    runCurrent()
    advanceTimeBy(800)
    assertTrue(visibility.visible)
    advanceTimeBy(201)
    assertFalse(visibility.visible)
  }

  @Test
  fun heldStaysVisibleAndHidesAfterRelease() = runTest {
    val visibility = running()
    visibility.setHeld(true)
    runCurrent()
    assertTrue(visibility.visible)
    advanceTimeBy(5_000)
    assertTrue(visibility.visible)
    visibility.setHeld(false)
    runCurrent()
    advanceTimeBy(999)
    assertTrue(visibility.visible)
    advanceTimeBy(2)
    assertFalse(visibility.visible)
  }
}
