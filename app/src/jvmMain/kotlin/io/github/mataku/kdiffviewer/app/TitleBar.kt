package io.github.mataku.kdiffviewer.app

import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import com.jetbrains.JBR
import io.github.mataku.kdiffviewer.app.ui.TitleBarRegions
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent

class TitleBarHitTester(
  private val regions: TitleBarRegions,
  private val scale: () -> Double,
  private val forceHitTest: (Boolean) -> Unit,
) : MouseAdapter() {
  override fun mousePressed(e: MouseEvent) = update(e)

  override fun mouseReleased(e: MouseEvent) = update(e)

  override fun mouseDragged(e: MouseEvent) = update(e)

  override fun mouseMoved(e: MouseEvent) = update(e)

  private fun update(e: MouseEvent) {
    val factor = scale()
    forceHitTest(regions.isClientArea(Offset((e.x * factor).toFloat(), (e.y * factor).toFloat())))
  }
}

fun installCustomTitleBar(window: ComposeWindow, height: Dp): TitleBarRegions? {
  if (!JBR.isWindowDecorationsSupported()) return null
  val decorations = JBR.getWindowDecorations()
  val titleBar = decorations.createCustomTitleBar()
  titleBar.height = height.value
  decorations.setCustomTitleBar(window, titleBar)
  val regions = TitleBarRegions()
  val tester = TitleBarHitTester(
    regions,
    scale = { window.graphicsConfiguration?.defaultTransform?.scaleX ?: 1.0 },
    forceHitTest = titleBar::forceHitTest,
  )
  window.addMouseListener(tester)
  window.addMouseMotionListener(tester)
  return regions
}
