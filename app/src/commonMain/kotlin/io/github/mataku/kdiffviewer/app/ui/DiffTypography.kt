package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
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
  val controlMarker: SpanStyle,
  val lineNumberWidth: Dp,
  val tabWidth: Int,
)

private const val CODE_LINE_HEIGHT_RATIO = 1.5f

fun diffTypography(
  fontFamily: FontFamily,
  fontSize: Int,
  tabWidth: Int,
  colors: DiffColors,
): DiffTypography {
  val base = TextStyle(fontFamily = fontFamily, fontSize = fontSize.sp)
  val muted = base.copy(color = colors.mutedText)
  val row = TextStyle(
    lineHeight = (fontSize * CODE_LINE_HEIGHT_RATIO).sp,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
  )
  return DiffTypography(
    code = base.copy(color = colors.text).merge(row),
    lineNumber = muted.copy(textAlign = TextAlign.End).merge(row),
    marker = base.copy(color = colors.text, textAlign = TextAlign.Center).merge(row),
    hunkHeader = muted.merge(row),
    noNewline = muted.merge(row),
    headerPath = base.copy(color = colors.text, fontWeight = FontWeight.SemiBold),
    headerBadge = TextStyle(color = colors.mutedText, fontSize = 11.sp),
    headerAdditions = TextStyle(color = colors.addedText, fontSize = 12.sp, fontFeatureSettings = "tnum"),
    headerDeletions = TextStyle(color = colors.removedText, fontSize = 12.sp, fontFeatureSettings = "tnum"),
    controlMarker = SpanStyle(background = colors.controlMarkerBackground),
    lineNumberWidth = (48 * fontSize / 12f).dp,
    tabWidth = tabWidth,
  )
}

val DefaultDiffTypography: DiffTypography = diffTypography(FontFamily.Monospace, 12, 8, LightDiffColors)

val LocalDiffTypography: ProvidableCompositionLocal<DiffTypography> =
  staticCompositionLocalOf { DefaultDiffTypography }
