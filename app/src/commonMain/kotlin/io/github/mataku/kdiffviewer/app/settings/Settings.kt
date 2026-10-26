package io.github.mataku.kdiffviewer.app.settings

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

@Serializable
enum class Appearance { System, Light, Dark }

@Serializable
data class Settings(
  val gitPath: String? = null,
  val fontFamily: String? = null,
  val fontSize: Int = 12,
  val tabWidth: Int = 8,
  val appearance: Appearance = Appearance.System,
)

fun Settings.normalized(): Settings = copy(
  gitPath = gitPath?.takeIf { it.isNotBlank() },
  fontFamily = fontFamily?.takeIf { it.isNotBlank() },
  fontSize = fontSize.coerceIn(8, 32),
  tabWidth = if (tabWidth in setOf(2, 4, 8)) tabWidth else 8,
)

object SettingsCodec {
  private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    coerceInputValues = true
    prettyPrint = true
    explicitNulls = true
  }

  fun decode(text: String): Settings? = try {
    json.decodeFromString<Settings>(text).normalized()
  } catch (_: SerializationException) {
    null
  } catch (_: IllegalArgumentException) {
    null
  }

  fun encode(settings: Settings): String = json.encodeToString(settings)
}
