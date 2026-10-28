package io.github.mataku.kdiffviewer.app.settings

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

@Serializable
enum class Appearance { System, Light, Dark, Dim }

@Serializable
data class Settings(
  val gitPath: String? = null,
  val fontFamily: String? = null,
  val fontSize: Int = 12,
  val tabWidth: Int = 8,
  val appearance: Appearance = Appearance.System,
  val treeFontSize: Int = 13,
  val copyOnSelect: Boolean = false,
)

fun Settings.normalized(): Settings = copy(
  gitPath = gitPath?.takeIf { it.isNotBlank() },
  fontFamily = fontFamily?.takeIf { it.isNotBlank() },
  fontSize = fontSize.coerceIn(8, 32),
  treeFontSize = treeFontSize.coerceIn(10, 20),
  tabWidth = if (tabWidth in setOf(2, 4, 8)) tabWidth else 8,
)

sealed interface DecodeResult {
  data class Decoded(val settings: Settings, val partial: Boolean) : DecodeResult
  data object Invalid : DecodeResult
}

object SettingsCodec {
  private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    coerceInputValues = true
    prettyPrint = true
    explicitNulls = true
  }

  fun decode(text: String): DecodeResult {
    try {
      return DecodeResult.Decoded(json.decodeFromString<Settings>(text).normalized(), partial = false)
    } catch (_: SerializationException) {
    } catch (_: IllegalArgumentException) {
    }
    val root = try {
      json.parseToJsonElement(text) as? JsonObject
    } catch (_: SerializationException) {
      null
    } catch (_: IllegalArgumentException) {
      null
    } ?: return DecodeResult.Invalid
    val kept = root.filter { (key, value) -> decodesAlone(key, value) }
    return DecodeResult.Decoded(json.decodeFromJsonElement<Settings>(JsonObject(kept)).normalized(), partial = true)
  }

  private fun decodesAlone(key: String, value: JsonElement): Boolean = try {
    json.decodeFromJsonElement<Settings>(JsonObject(mapOf(key to value)))
    true
  } catch (_: SerializationException) {
    false
  } catch (_: IllegalArgumentException) {
    false
  }

  fun encode(settings: Settings): String = json.encodeToString(settings)
}
