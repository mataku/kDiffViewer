package io.github.mataku.kdiffviewer.app.ui

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
}
