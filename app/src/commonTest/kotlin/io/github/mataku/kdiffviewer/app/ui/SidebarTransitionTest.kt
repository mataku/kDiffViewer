package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SidebarTransitionTest {
  private class SteppingClock : MonotonicFrameClock {
    private var nanos = 0L
    private val waiters = ArrayList<(Long) -> Unit>()

    override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
      waiters += { time -> cont.resumeWith(runCatching { onFrame(time) }) }
    }

    suspend fun frames(count: Int) {
      repeat(count) {
        yield()
        nanos += 16_000_000L
        val pending = waiters.toList()
        waiters.clear()
        pending.forEach { it(nanos) }
        yield()
      }
    }
  }

  @Test
  fun startsSettledAtInitialValue() {
    val shown = SidebarTransition(initiallyVisible = true)
    assertEquals(1f, shown.progress)
    assertTrue(shown.treeComposed)
    assertTrue(shown.diffNarrow)
    val hidden = SidebarTransition(initiallyVisible = false)
    assertEquals(0f, hidden.progress)
    assertFalse(hidden.treeComposed)
    assertFalse(hidden.diffNarrow)
  }

  @Test
  fun hidingWidensDiffImmediatelyAndRemovesTreeAtEnd() = runTest {
    val clock = SteppingClock()
    val transition = SidebarTransition(initiallyVisible = true)
    withContext(clock) {
      val job = launch(start = CoroutineStart.UNDISPATCHED) { transition.animateTo(false) }
      clock.frames(2)
      assertFalse(transition.diffNarrow)
      assertTrue(transition.treeComposed)
      assertTrue(transition.progress > 0f && transition.progress < 1f, "progress=${transition.progress}")
      clock.frames(30)
      job.join()
    }
    assertEquals(0f, transition.progress)
    assertFalse(transition.treeComposed)
  }

  @Test
  fun showingNarrowsDiffOnlyAtEnd() = runTest {
    val clock = SteppingClock()
    val transition = SidebarTransition(initiallyVisible = false)
    withContext(clock) {
      val job = launch(start = CoroutineStart.UNDISPATCHED) { transition.animateTo(true) }
      clock.frames(2)
      assertTrue(transition.treeComposed)
      assertFalse(transition.diffNarrow)
      clock.frames(30)
      job.join()
    }
    assertEquals(1f, transition.progress)
    assertTrue(transition.diffNarrow)
  }

  @Test
  fun reversalContinuesFromCurrentProgress() = runTest {
    val clock = SteppingClock()
    val transition = SidebarTransition(initiallyVisible = true)
    withContext(clock) {
      val hide = launch(start = CoroutineStart.UNDISPATCHED) { transition.animateTo(false) }
      clock.frames(5)
      val midway = transition.progress
      assertTrue(midway > 0f && midway < 1f, "midway=$midway")
      val show = launch(start = CoroutineStart.UNDISPATCHED) { transition.animateTo(true) }
      hide.join()
      clock.frames(1)
      assertTrue(transition.progress >= midway && transition.progress < 1f, "progress=${transition.progress} midway=$midway")
      clock.frames(30)
      show.join()
    }
    assertEquals(1f, transition.progress)
    assertTrue(transition.diffNarrow)
  }
}
