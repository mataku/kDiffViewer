package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class DiffColors(
  val background: Color,
  val text: Color,
  val mutedText: Color,
  val border: Color,
  val fileHeaderBackground: Color,
  val addedBackground: Color,
  val addedLineNumberBackground: Color,
  val removedBackground: Color,
  val removedLineNumberBackground: Color,
  val hunkBackground: Color,
  val addedText: Color,
  val removedText: Color,
  val modifiedText: Color,
  val renamedText: Color,
  val glassFill: Color,
  val glassFillHovered: Color,
  val glassFillPressed: Color,
  val glassHighlight: Color,
  val glassEdge: Color,
  val accent: Color,
)

val LightDiffColors = DiffColors(
  background = Color(0xFFFFFFFF),
  text = Color(0xFF1F2328),
  mutedText = Color(0xFF656D76),
  border = Color(0xFFD0D7DE),
  fileHeaderBackground = Color(0xFFF6F8FA),
  addedBackground = Color(0xFFE6FFEC),
  addedLineNumberBackground = Color(0xFFCCFFD8),
  removedBackground = Color(0xFFFFEBE9),
  removedLineNumberBackground = Color(0xFFFFD7D5),
  hunkBackground = Color(0xFFDDF4FF),
  addedText = Color(0xFF1A7F37),
  removedText = Color(0xFFD1242F),
  modifiedText = Color(0xFF9A6700),
  renamedText = Color(0xFF0969DA),
  glassFill = Color(0xB3FFFFFF),
  glassFillHovered = Color(0xFFFFFFFF),
  glassFillPressed = Color(0xFFE6EAEF),
  glassHighlight = Color(0xFFFFFFFF),
  glassEdge = Color(0x26000000),
  accent = Color(0xFF007AFF),
)

val DarkDiffColors = DiffColors(
  background = Color(0xFF0D1117),
  text = Color(0xFFE6EDF3),
  mutedText = Color(0xFF848D97),
  border = Color(0xFF30363D),
  fileHeaderBackground = Color(0xFF161B22),
  addedBackground = Color(0xFF12261E),
  addedLineNumberBackground = Color(0xFF1C4328),
  removedBackground = Color(0xFF25171C),
  removedLineNumberBackground = Color(0xFF542426),
  hunkBackground = Color(0xFF111D2E),
  addedText = Color(0xFF3FB950),
  removedText = Color(0xFFF85149),
  modifiedText = Color(0xFFD29922),
  renamedText = Color(0xFF58A6FF),
  glassFill = Color(0x14FFFFFF),
  glassFillHovered = Color(0x24FFFFFF),
  glassFillPressed = Color(0x33FFFFFF),
  glassHighlight = Color(0x40FFFFFF),
  glassEdge = Color(0x0FFFFFFF),
  accent = Color(0xFF0A84FF),
)

val LocalDiffColors: ProvidableCompositionLocal<DiffColors> = staticCompositionLocalOf { LightDiffColors }
