package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import io.github.mataku.kdiffviewer.core.model.DiffStats

fun statsText(stats: DiffStats, additionsStyle: TextStyle, deletionsStyle: TextStyle): AnnotatedString? {
  if (stats.additions <= 0 && stats.deletions <= 0) return null
  return buildAnnotatedString {
    if (stats.additions > 0) withStyle(additionsStyle.toSpanStyle()) { append("+${stats.additions}") }
    if (stats.additions > 0 && stats.deletions > 0) append(" ")
    if (stats.deletions > 0) withStyle(deletionsStyle.toSpanStyle()) { append("−${stats.deletions}") }
  }
}
