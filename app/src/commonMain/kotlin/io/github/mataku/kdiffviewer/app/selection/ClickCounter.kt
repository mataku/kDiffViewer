package io.github.mataku.kdiffviewer.app.selection

import androidx.compose.ui.geometry.Offset

class ClickCounter(private val timeoutMillis: Long, private val slop: Float) {
  private var count = 0
  private var lastTime = 0L
  private var lastPosition = Offset.Zero

  fun onPress(timeMillis: Long, position: Offset): Int {
    val continues = count in 1..2 &&
      timeMillis - lastTime <= timeoutMillis &&
      (position - lastPosition).getDistance() <= slop
    count = if (continues) count + 1 else 1
    lastTime = timeMillis
    lastPosition = position
    return count
  }
}
