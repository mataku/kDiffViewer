package io.github.mataku.kdiffviewer.app.ui

import io.github.mataku.kdiffviewer.core.model.Hunk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class DisplayTextTest {
  @Test
  fun tabAtColumnZeroExpandsToEightSpaces() {
    assertEquals("        x", expandTabs("\tx"))
  }

  @Test
  fun tabMidLineExpandsToNextMultipleOfEight() {
    assertEquals("abc     d", expandTabs("abc\td"))
    assertEquals("abcdefgh        i", expandTabs("abcdefgh\ti"))
  }

  @Test
  fun multipleTabsEachAdvanceToNextStop() {
    assertEquals("a       b       c", expandTabs("a\tb\tc"))
    assertEquals("                x", expandTabs("\t\tx"))
  }

  @Test
  fun textWithoutTabsIsReturnedAsIs() {
    val text = "no tabs here"
    assertSame(text, expandTabs(text))
  }

  @Test
  fun hunkLabelShowsFullRangeFollowedByFunctionName() {
    assertEquals("@@ -1,2 +3,4 @@ fun main()", hunkHeaderLabel(Hunk(1, 2, 3, 4, " fun main()", emptyList())))
  }

  @Test
  fun hunkLabelWithoutFunctionNameIsJustTheRange() {
    assertEquals("@@ -0,0 +1,5 @@", hunkHeaderLabel(Hunk(0, 0, 1, 5, "", emptyList())))
  }
}
