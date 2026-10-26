package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals

class DiffTypographyTest {
  @Test
  fun lineNumberWidthScalesWithFontSize() {
    fun width(size: Int) = diffTypography(FontFamily.Monospace, size, 8, LightDiffColors).lineNumberWidth
    assertEquals(48.dp, width(12))
    assertEquals(96.dp, width(24))
    assertEquals(32.dp, width(8))
  }

  @Test
  fun stylesCarryFontAndColors() {
    val typography = diffTypography(FontFamily.Serif, 14, 4, LightDiffColors)
    assertEquals(FontFamily.Serif, typography.code.fontFamily)
    assertEquals(14.sp, typography.code.fontSize)
    assertEquals(LightDiffColors.text, typography.code.color)
    assertEquals(LightDiffColors.mutedText, typography.lineNumber.color)
    assertEquals(4, typography.tabWidth)
  }

  @Test
  fun headerPathUsesDiffFontAndSize() {
    val typography = diffTypography(FontFamily.Serif, 14, 4, LightDiffColors)
    assertEquals(FontFamily.Serif, typography.headerPath.fontFamily)
    assertEquals(14.sp, typography.headerPath.fontSize)
    assertEquals(FontWeight.SemiBold, typography.headerPath.fontWeight)
  }

  @Test
  fun headerStatsAreTabular() {
    val typography = diffTypography(FontFamily.Monospace, 12, 8, LightDiffColors)
    assertEquals("tnum", typography.headerAdditions.fontFeatureSettings)
    assertEquals("tnum", typography.headerDeletions.fontFeatureSettings)
  }
}
