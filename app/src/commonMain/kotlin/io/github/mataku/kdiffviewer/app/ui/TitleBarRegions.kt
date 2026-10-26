package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.unit.dp

val TitleBarHeight = 52.dp

val ContentHorizontalMargin = 20.dp

class TitleBarRegions {
  private val regions = HashMap<String, Rect>()

  fun update(key: String, bounds: Rect) {
    regions[key] = bounds
  }

  fun remove(key: String) {
    regions.remove(key)
  }

  fun isClientArea(point: Offset): Boolean = regions.values.any { it.contains(point) }
}

val LocalTitleBarRegions: ProvidableCompositionLocal<TitleBarRegions?> = staticCompositionLocalOf { null }

fun Modifier.titleBarClientRegion(key: String): Modifier = this then TitleBarClientRegionElement(key)

private data class TitleBarClientRegionElement(val key: String) : ModifierNodeElement<TitleBarClientRegionNode>() {
  override fun create() = TitleBarClientRegionNode(key)

  override fun update(node: TitleBarClientRegionNode) {
    node.updateKey(key)
  }
}

private class TitleBarClientRegionNode(private var key: String) :
  Modifier.Node(),
  GlobalPositionAwareModifierNode,
  CompositionLocalConsumerModifierNode {
  private var regions: TitleBarRegions? = null
  private var bounds: Rect? = null

  override fun onAttach() {
    regions = currentValueOf(LocalTitleBarRegions)
  }

  override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
    val current = coordinates.boundsInWindow()
    bounds = current
    regions?.update(key, current)
  }

  override fun onDetach() {
    regions?.remove(key)
    regions = null
  }

  fun updateKey(newKey: String) {
    if (newKey == key) return
    regions?.remove(key)
    key = newKey
    bounds?.let { regions?.update(key, it) }
  }
}
