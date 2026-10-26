package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Immutable
class DiffTypography(
  val code: TextStyle,
  val lineNumber: TextStyle,
  val marker: TextStyle,
  val hunkHeader: TextStyle,
  val noNewline: TextStyle,
  val headerPath: TextStyle,
  val headerBadge: TextStyle,
  val headerAdditions: TextStyle,
  val headerDeletions: TextStyle,
  val lineNumberWidth: Dp,
  val tabWidth: Int,
)

fun diffTypography(
  fontFamily: FontFamily,
  fontSize: Int,
  tabWidth: Int,
  colors: DiffColors,
): DiffTypography {
  val base = TextStyle(fontFamily = fontFamily, fontSize = fontSize.sp)
  val muted = base.copy(color = colors.mutedText)
  return DiffTypography(
    code = base.copy(color = colors.text),
    lineNumber = muted.copy(textAlign = TextAlign.End),
    marker = base.copy(color = colors.text),
    hunkHeader = muted,
    noNewline = muted,
    headerPath = base.copy(color = colors.text, fontWeight = FontWeight.SemiBold),
    headerBadge = TextStyle(color = colors.mutedText, fontSize = 11.sp),
    headerAdditions = TextStyle(color = colors.addedText, fontSize = 12.sp, fontFeatureSettings = "tnum"),
    headerDeletions = TextStyle(color = colors.removedText, fontSize = 12.sp, fontFeatureSettings = "tnum"),
    lineNumberWidth = (48 * fontSize / 12f).dp,
    tabWidth = tabWidth,
  )
}

val LocalDiffTypography: ProvidableCompositionLocal<DiffTypography> =
  staticCompositionLocalOf { diffTypography(FontFamily.Monospace, 12, 8, LightDiffColors) }
