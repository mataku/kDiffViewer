package io.github.mataku.kdiffviewer.app.ui

import io.github.mataku.kdiffviewer.app.ExpansionError
import kotlin.test.Test
import kotlin.test.assertEquals

class ExpansionErrorTextTest {
  @Test
  fun eachErrorHasItsText() {
    assertEquals("Could not read this file", ExpansionError.Unreadable.expansionErrorText())
    assertEquals("Too large to expand", ExpansionError.TooLarge.expansionErrorText())
    assertEquals("The file no longer matches this diff — reload to expand", ExpansionError.Stale.expansionErrorText())
  }
}
