package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals

class TreeTypographyTest {
  @Test
  fun defaultSizeMatchesTheOriginalTree() {
    val t = treeTypography(13, LightDiffColors)
    assertEquals(13.sp, t.name.fontSize)
    assertEquals(11.sp, t.additions.fontSize)
    assertEquals(11.sp, t.deletions.fontSize)
    assertEquals(12.dp, t.chevronSize)
    assertEquals(14.dp, t.iconSize)
    assertEquals(12.dp, t.indentPerDepth)
  }

  @Test
  fun largestSizeScalesEverything() {
    val t = treeTypography(20, LightDiffColors)
    assertEquals(20.sp, t.name.fontSize)
    assertEquals(18.sp, t.additions.fontSize)
    assertEquals(18.sp, t.deletions.fontSize)
    assertEquals(14.dp * (20 / 13f), t.iconSize)
    assertEquals(12.dp * (20 / 13f), t.chevronSize)
    assertEquals(12.dp * (20 / 13f), t.indentPerDepth)
  }

  @Test
  fun smallestSizeScalesDown() {
    val t = treeTypography(10, LightDiffColors)
    assertEquals(10.sp, t.name.fontSize)
    assertEquals(8.sp, t.additions.fontSize)
    assertEquals(14.dp * (10 / 13f), t.iconSize)
  }

  @Test
  fun colorsComeFromTheTheme() {
    val t = treeTypography(13, LightDiffColors)
    assertEquals(LightDiffColors.text, t.name.color)
    assertEquals(LightDiffColors.mutedText, t.directoryName.color)
    assertEquals(LightDiffColors.addedText, t.additions.color)
    assertEquals(LightDiffColors.removedText, t.deletions.color)
  }

  @Test
  fun statusStylesAreSemiboldTabularAndColoredPerKind() {
    val t = treeTypography(13, LightDiffColors)
    assertEquals(11.sp, t.statusModified.fontSize)
    assertEquals(FontWeight.SemiBold, t.statusModified.fontWeight)
    assertEquals("tnum", t.statusModified.fontFeatureSettings)
    assertEquals("tnum", t.additions.fontFeatureSettings)
    assertEquals("tnum", t.deletions.fontFeatureSettings)
    assertEquals(LightDiffColors.addedText, t.statusUntracked.color)
    assertEquals(LightDiffColors.addedText, t.statusAdded.color)
    assertEquals(LightDiffColors.modifiedText, t.statusModified.color)
    assertEquals(LightDiffColors.removedText, t.statusDeleted.color)
    assertEquals(LightDiffColors.renamedText, t.statusRenamed.color)
  }
}
