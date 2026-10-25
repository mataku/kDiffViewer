package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class DiffColors(
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
)

val LightDiffColors = DiffColors(
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
)

val LocalDiffColors: ProvidableCompositionLocal<DiffColors> = staticCompositionLocalOf { LightDiffColors }
