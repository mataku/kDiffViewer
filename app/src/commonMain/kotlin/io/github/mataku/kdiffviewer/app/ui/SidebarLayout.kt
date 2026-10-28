package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints

@Composable
fun SidebarLayout(
  transition: SidebarTransition,
  tree: @Composable () -> Unit,
  divider: @Composable () -> Unit,
  diff: @Composable () -> Unit,
  modifier: Modifier = Modifier,
) {
  val sidebarLayer = Modifier.graphicsLayer { translationX = -(1f - transition.progress) * transition.slideWidthPx }
  Layout(
    contents = listOf(
      { if (transition.treeComposed) Box(sidebarLayer) { tree() } },
      { if (transition.treeComposed) Box(sidebarLayer) { divider() } },
      {
        Box(Modifier.graphicsLayer { translationX = if (transition.diffNarrow) 0f else transition.progress * transition.slideWidthPx }) {
          diff()
        }
      },
    ),
    modifier = modifier.clipToBounds(),
  ) { (trees, dividers, diffs), constraints ->
    val loose = constraints.copy(minWidth = 0)
    val treePlaceable = trees.firstOrNull()?.measure(loose)
    val dividerPlaceable = dividers.firstOrNull()?.measure(loose)
    val slide = (treePlaceable?.width ?: 0) + (dividerPlaceable?.width ?: 0)
    transition.slideWidthPx = slide.toFloat()
    val narrow = transition.diffNarrow
    val diffWidth = if (narrow) (constraints.maxWidth - slide).coerceAtLeast(0) else constraints.maxWidth
    val diffPlaceable = diffs.firstOrNull()?.measure(Constraints.fixed(diffWidth, constraints.maxHeight))
    layout(constraints.maxWidth, constraints.maxHeight) {
      treePlaceable?.place(0, 0)
      diffPlaceable?.place(if (narrow) slide else 0, 0)
      dividerPlaceable?.place(treePlaceable?.width ?: 0, 0)
    }
  }
}
