package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

private const val SIDEBAR_ANIMATION_MILLIS = 200

@Stable
class SidebarTransition(initiallyVisible: Boolean) {
  private val animatable = Animatable(if (initiallyVisible) 1f else 0f)

  var target by mutableStateOf(initiallyVisible)
    private set

  val progress: Float get() = animatable.value

  val treeComposed: Boolean by derivedStateOf { target || progress > 0f }

  val diffNarrow: Boolean by derivedStateOf { target && progress == 1f }

  suspend fun animateTo(visible: Boolean) {
    target = visible
    animatable.animateTo(if (visible) 1f else 0f, tween(SIDEBAR_ANIMATION_MILLIS, easing = FastOutSlowInEasing))
  }
}
