package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Typeface
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle

actual fun resolveFontFamily(name: String?): FontFamily {
  if (name == null) return FontFamily.Monospace
  val typeface = FontMgr.default.matchFamilyStyle(name, FontStyle.NORMAL) ?: return FontFamily.Monospace
  if (!typeface.familyName.equals(name, ignoreCase = true)) return FontFamily.Monospace
  return FontFamily(Typeface(typeface))
}
