package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TitleBarRegionsTest {
  @Test
  fun pointInsideRegisteredRegionIsClientArea() {
    val regions = TitleBarRegions()
    regions.update("reload", Rect(100f, 10f, 160f, 40f))
    assertTrue(regions.isClientArea(Offset(120f, 20f)))
    assertFalse(regions.isClientArea(Offset(50f, 20f)))
  }

  @Test
  fun removedRegionIsNoLongerClientArea() {
    val regions = TitleBarRegions()
    regions.update("reload", Rect(100f, 10f, 160f, 40f))
    regions.remove("reload")
    assertFalse(regions.isClientArea(Offset(120f, 20f)))
  }
}
