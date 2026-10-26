package io.github.mataku.kdiffviewer.app.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsCodecTest {
  @Test fun roundTripsAllFields() {
    val settings = Settings("/g", "Menlo", 14, 4, Appearance.Dark, treeFontSize = 16)
    assertEquals(settings, decodeSettings(SettingsCodec.encode(settings)))
  }

  @Test fun missingKeysUseDefaults() {
    assertEquals(Settings(), decodeSettings("{}"))
  }

  @Test fun unknownKeysAreIgnored() {
    assertEquals(13, decodeSettings("""{"future":1,"fontSize":13}""")!!.fontSize)
  }

  @Test fun unknownEnumValueFallsBackToDefault() {
    assertEquals(Settings(fontSize = 14), decodeSettings("""{"appearance":"Blue","fontSize":14}"""))
  }

  @Test fun outOfRangeValuesAreClamped() {
    assertEquals(8, decodeSettings("""{"fontSize":3}""")!!.fontSize)
    assertEquals(32, decodeSettings("""{"fontSize":99}""")!!.fontSize)
    assertEquals(8, decodeSettings("""{"tabWidth":3}""")!!.tabWidth)
    assertEquals(4, decodeSettings("""{"tabWidth":4}""")!!.tabWidth)
  }

  @Test fun blankStringsBecomeNull() {
    val decoded = decodeSettings("""{"gitPath":"  ","fontFamily":""}""")!!
    assertNull(decoded.gitPath)
    assertNull(decoded.fontFamily)
  }

  @Test fun wrongTypeFieldFallsBackAlone() {
    val result = SettingsCodec.decode("""{"fontSize":"big","tabWidth":4,"appearance":"Dark"}""")
    assertEquals(DecodeResult.Decoded(Settings(fontSize = 12, tabWidth = 4, appearance = Appearance.Dark), partial = true), result)
  }

  @Test fun notJsonIsInvalid() {
    assertEquals(DecodeResult.Invalid, SettingsCodec.decode("{"))
  }

  @Test fun nonObjectIsInvalid() {
    assertEquals(DecodeResult.Invalid, SettingsCodec.decode("[]"))
    assertEquals(DecodeResult.Invalid, SettingsCodec.decode("\"x\""))
  }

  @Test fun emptyStringIsInvalid() {
    assertEquals(DecodeResult.Invalid, SettingsCodec.decode(""))
  }

  @Test fun fullyValidIsNotPartial() {
    assertEquals(DecodeResult.Decoded(Settings(fontSize = 14), partial = false), SettingsCodec.decode("""{"fontSize":14}"""))
  }

  @Test fun unknownEnumIsNotPartial() {
    assertEquals(DecodeResult.Decoded(Settings(), partial = false), SettingsCodec.decode("""{"appearance":"Blue"}"""))
  }

  @Test fun outOfRangeIsNotPartial() {
    assertEquals(DecodeResult.Decoded(Settings(fontSize = 32), partial = false), SettingsCodec.decode("""{"fontSize":99}"""))
  }

  @Test fun treeFontSizeDefaultsTo13() {
    assertEquals(13, decodeSettings("{}")!!.treeFontSize)
  }

  @Test fun treeFontSizeIsClamped() {
    assertEquals(10, decodeSettings("""{"treeFontSize":5}""")!!.treeFontSize)
    assertEquals(20, decodeSettings("""{"treeFontSize":99}""")!!.treeFontSize)
  }

  @Test fun encodeWritesEveryField() {
    val text = SettingsCodec.encode(Settings())
    assertTrue(text.contains("\"gitPath\": null"), text)
    assertTrue(text.contains("\"tabWidth\": 8"), text)
  }
}
