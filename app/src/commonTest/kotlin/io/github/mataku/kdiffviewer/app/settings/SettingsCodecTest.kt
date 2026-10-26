package io.github.mataku.kdiffviewer.app.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsCodecTest {
  @Test fun roundTripsAllFields() {
    val settings = Settings("/g", "Menlo", 14, 4, Appearance.Dark)
    assertEquals(settings, SettingsCodec.decode(SettingsCodec.encode(settings)))
  }

  @Test fun missingKeysUseDefaults() {
    assertEquals(Settings(), SettingsCodec.decode("{}"))
  }

  @Test fun unknownKeysAreIgnored() {
    assertEquals(13, SettingsCodec.decode("""{"future":1,"fontSize":13}""")!!.fontSize)
  }

  @Test fun unknownEnumValueFallsBackToDefault() {
    assertEquals(Settings(fontSize = 14), SettingsCodec.decode("""{"appearance":"Blue","fontSize":14}"""))
  }

  @Test fun outOfRangeValuesAreClamped() {
    assertEquals(8, SettingsCodec.decode("""{"fontSize":3}""")!!.fontSize)
    assertEquals(32, SettingsCodec.decode("""{"fontSize":99}""")!!.fontSize)
    assertEquals(8, SettingsCodec.decode("""{"tabWidth":3}""")!!.tabWidth)
    assertEquals(4, SettingsCodec.decode("""{"tabWidth":4}""")!!.tabWidth)
  }

  @Test fun blankStringsBecomeNull() {
    val decoded = SettingsCodec.decode("""{"gitPath":"  ","fontFamily":""}""")!!
    assertNull(decoded.gitPath)
    assertNull(decoded.fontFamily)
  }

  @Test fun malformedJsonReturnsNull() {
    assertNull(SettingsCodec.decode("{"))
    assertNull(SettingsCodec.decode("""{"fontSize":"big"}"""))
  }

  @Test fun encodeWritesEveryField() {
    val text = SettingsCodec.encode(Settings())
    assertTrue(text.contains("\"gitPath\": null"), text)
    assertTrue(text.contains("\"tabWidth\": 8"), text)
  }
}
