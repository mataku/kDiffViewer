package io.github.mataku.kdiffviewer.app.settings

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

@Serializable
data class WindowState(
  val mainWidth: Int? = null,
  val mainHeight: Int? = null,
)

fun WindowState.mainSizeOrNull(min: DpSize, max: DpSize): DpSize? {
  val width = mainWidth ?: return null
  val height = mainHeight ?: return null
  return DpSize(
    width.dp.coerceIn(min.width, max.width),
    height.dp.coerceIn(min.height, max.height),
  )
}

object WindowStateCodec {
  private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    coerceInputValues = true
    explicitNulls = true
  }

  fun decode(text: String?): WindowState {
    if (text == null) return WindowState()
    return try {
      json.decodeFromString<WindowState>(text)
    } catch (_: SerializationException) {
      WindowState()
    } catch (_: IllegalArgumentException) {
      WindowState()
    }
  }

  fun encode(state: WindowState): String = json.encodeToString(state)
}
