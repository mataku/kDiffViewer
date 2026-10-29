package io.github.mataku.kdiffviewer.app.ui.mac

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.mataku.kdiffviewer.app.ui.LocalDiffColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

const val SCROLLBAR_HIDE_DELAY_MILLIS = 1_000L
const val SCROLLBAR_TAG = "overlay-scrollbar"

@Stable
class ScrollbarVisibility(private val hideDelayMillis: Long = SCROLLBAR_HIDE_DELAY_MILLIS) {
  var visible by mutableStateOf(false)
    private set

  private val scrolls = MutableStateFlow(0L)
  private val held = MutableStateFlow(false)

  fun onScroll() {
    scrolls.value++
  }

  fun setHeld(value: Boolean) {
    held.value = value
  }

  suspend fun run() {
    combine(scrolls, held) { _, isHeld -> isHeld }.drop(1).collectLatest { isHeld ->
      visible = true
      if (isHeld) return@collectLatest
      delay(hideDelayMillis)
      visible = false
    }
  }
}

@Composable
fun MacOverlayScrollbar(listState: LazyListState, modifier: Modifier = Modifier) {
  val colors = LocalDiffColors.current
  val visibility = remember { ScrollbarVisibility() }
  val interactionSource = remember { MutableInteractionSource() }
  val hovered by interactionSource.collectIsHoveredAsState()
  val dragged by interactionSource.collectIsDraggedAsState()
  LaunchedEffect(hovered, dragged) { visibility.setHeld(hovered || dragged) }
  LaunchedEffect(listState) {
    launch { visibility.run() }
    snapshotFlow { listState.firstVisibleItemIndex.toLong() shl 32 or listState.firstVisibleItemScrollOffset.toLong() }
      .drop(1)
      .collect { visibility.onScroll() }
  }
  val alpha by animateFloatAsState(if (visibility.visible) 1f else 0f, tween(if (visibility.visible) 100 else 300))
  val thumb = if (colors.isDark) Color.White else Color.Black
  val style = remember(thumb) {
    ScrollbarStyle(
      minimalHeight = 24.dp,
      thickness = 8.dp,
      shape = RoundedCornerShape(4.dp),
      hoverDurationMillis = 150,
      unhoverColor = thumb.copy(alpha = 0.35f),
      hoverColor = thumb.copy(alpha = 0.55f),
    )
  }
  VerticalScrollbar(
    adapter = rememberScrollbarAdapter(listState),
    modifier = modifier
      .testTag(SCROLLBAR_TAG)
      .fillMaxHeight()
      .padding(vertical = 2.dp)
      .graphicsLayer { this.alpha = alpha },
    style = style,
    interactionSource = interactionSource,
  )
}
