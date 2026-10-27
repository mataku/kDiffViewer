package io.github.mataku.kdiffviewer.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ControlCharactersTest {
  @Test
  fun c0ControlsBecomeControlPictures() {
    assertEquals("a␛b␀c␡", markControls("a\u001Bb\u0000c\u007F"))
  }

  @Test
  fun tabAndNewlineAreMarkedOrKeptAsSpecified() {
    assertEquals("a\tb", markControls("a\tb"))
    assertEquals("a␊b", markControls("a\nb"))
  }

  @Test
  fun bidiAndC1ControlsBecomeReplacementCharacter() {
    val input = "x‮y⁦z؜‎‏\u0085"
    assertEquals("x�y�z����", markControls(input))
  }

  @Test
  fun cleanTextIsReturnedAsTheSameInstance() {
    val text = "plain ascii and 日本語"
    assertSame(text, markControls(text))
  }

  @Test
  fun lengthIsPreserved() {
    val input = "\u0001‪⁩\t\u009F"
    assertEquals(input.length, markControls(input).length)
  }
}
