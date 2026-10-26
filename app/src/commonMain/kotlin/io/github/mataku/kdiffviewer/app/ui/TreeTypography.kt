package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mataku.kdiffviewer.app.GitFileStatus

@Immutable
class TreeTypography(
  val name: TextStyle,
  val directoryName: TextStyle,
  val additions: TextStyle,
  val deletions: TextStyle,
  val statusUntracked: TextStyle,
  val statusAdded: TextStyle,
  val statusModified: TextStyle,
  val statusDeleted: TextStyle,
  val statusRenamed: TextStyle,
  val chevronSize: Dp,
  val iconSize: Dp,
  val indentPerDepth: Dp,
) {
  fun statusStyle(status: GitFileStatus): TextStyle = when (status) {
    GitFileStatus.Untracked -> statusUntracked
    GitFileStatus.Added -> statusAdded
    GitFileStatus.Modified -> statusModified
    GitFileStatus.Deleted -> statusDeleted
    GitFileStatus.Renamed -> statusRenamed
  }
}

fun treeTypography(fontSize: Int, colors: DiffColors): TreeTypography {
  val scale = fontSize / 13f
  val name = TextStyle(color = colors.text, fontSize = fontSize.sp)
  val stat = TextStyle(fontSize = (fontSize - 2).sp, fontFeatureSettings = "tnum")
  val status = stat.copy(fontWeight = FontWeight.SemiBold)
  return TreeTypography(
    name = name,
    directoryName = name.copy(color = colors.mutedText),
    additions = stat.copy(color = colors.addedText),
    deletions = stat.copy(color = colors.removedText),
    statusUntracked = status.copy(color = colors.addedText),
    statusAdded = status.copy(color = colors.addedText),
    statusModified = status.copy(color = colors.modifiedText),
    statusDeleted = status.copy(color = colors.removedText),
    statusRenamed = status.copy(color = colors.renamedText),
    chevronSize = 12.dp * scale,
    iconSize = 14.dp * scale,
    indentPerDepth = 12.dp * scale,
  )
}

val LocalTreeTypography: ProvidableCompositionLocal<TreeTypography> =
  staticCompositionLocalOf { treeTypography(13, LightDiffColors) }
