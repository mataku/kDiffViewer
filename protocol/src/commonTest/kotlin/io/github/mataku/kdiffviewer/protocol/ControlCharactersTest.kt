package io.github.mataku.kdiffviewer.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ControlCharactersTest {
  @Test
  fun c0ControlsBecomeControlPictures() {
    assertEquals("a\u241Bb\u2400c\u2421", markControls("a\u001Bb\u0000c\u007F"))
  }

  @Test
  fun tabAndNewlineAreMarkedOrKeptAsSpecified() {
    assertEquals("a\tb", markControls("a\tb"))
    assertEquals("a\u240Ab", markControls("a\nb"))
  }

  @Test
  fun bidiAndC1ControlsBecomeReplacementCharacter() {
    val input = "x\u202Ey\u2066z\u061C\u200E\u200F\u0085"
    assertEquals("x\uFFFDy\uFFFDz\uFFFD\uFFFD\uFFFD\uFFFD", markControls(input))
  }

  @Test
  fun cleanTextIsReturnedAsTheSameInstance() {
    val text = "plain ascii and 日本語"
    assertSame(text, markControls(text))
  }

  @Test
  fun lengthIsPreserved() {
    val input = "\u0001\u202A\u2069\t\u009F"
    assertEquals(input.length, markControls(input).length)
  }
}
