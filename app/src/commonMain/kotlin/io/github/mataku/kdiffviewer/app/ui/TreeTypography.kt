package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Immutable
class TreeTypography(
  val name: TextStyle,
  val directoryName: TextStyle,
  val additions: TextStyle,
  val deletions: TextStyle,
  val chevronSize: Dp,
  val iconSize: Dp,
  val indentPerDepth: Dp,
)

fun treeTypography(fontSize: Int, colors: DiffColors): TreeTypography {
  val scale = fontSize / 13f
  val name = TextStyle(color = colors.text, fontSize = fontSize.sp)
  val stat = TextStyle(fontSize = (fontSize - 2).sp)
  return TreeTypography(
    name = name,
    directoryName = name.copy(color = colors.mutedText),
    additions = stat.copy(color = colors.addedText),
    deletions = stat.copy(color = colors.removedText),
    chevronSize = 12.dp * scale,
    iconSize = 14.dp * scale,
    indentPerDepth = 12.dp * scale,
  )
}

val LocalTreeTypography: ProvidableCompositionLocal<TreeTypography> =
  staticCompositionLocalOf { treeTypography(13, LightDiffColors) }
