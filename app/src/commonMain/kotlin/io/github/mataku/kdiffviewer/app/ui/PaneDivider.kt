package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

val PaneDividerWidth = 1.dp

@Composable
fun PaneDivider(onDrag: (Float) -> Unit, modifier: Modifier = Modifier) {
  val colors = LocalDiffColors.current
  val currentOnDrag by rememberUpdatedState(onDrag)
  val dragState = rememberDraggableState { delta -> currentOnDrag(delta) }
  Box(modifier = modifier.zIndex(1f).width(PaneDividerWidth).fillMaxHeight().background(colors.border)) {
    Box(
      modifier = Modifier
        .testTag("tree-divider")
        .titleBarClientRegion("tree-divider")
        .requiredWidth(8.dp)
        .fillMaxHeight()
        .pointerHoverIcon(HorizontalResizePointerIcon)
        .draggable(dragState, Orientation.Horizontal),
    )
  }
}
