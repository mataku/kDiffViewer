package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mataku.kdiffviewer.app.settings.Appearance

private val CardShape = RoundedCornerShape(8.dp)

private enum class RowKind { Context, Removed, Added }

private val PreviewRows = listOf(
  RowKind.Context to 0.7f,
  RowKind.Removed to 0.55f,
  RowKind.Removed to 0.4f,
  RowKind.Added to 0.65f,
  RowKind.Added to 0.5f,
)

@Composable
fun AppearanceCard(
  appearance: Appearance,
  selected: Boolean,
  onClick: () -> Unit,
) {
  val colors = LocalDiffColors.current
  Column(
    modifier = Modifier
      .testTag("appearance-${appearance.name}")
      .semantics { this.selected = selected }
      .clickable(role = Role.RadioButton, onClick = onClick),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Box(modifier = Modifier.testTag("appearance-preview-${appearance.name}").size(width = 120.dp, height = 78.dp).clip(CardShape)) {
      Canvas(modifier = Modifier.matchParentSize()) {
        when (appearance) {
          Appearance.Light -> drawDiffPreview(LightDiffColors)

          Appearance.Dark -> drawDiffPreview(DarkDiffColors)

          Appearance.Dim -> drawDiffPreview(DimDiffColors)

          Appearance.System -> {
            clipRect(left = 0f, right = size.width / 2) { drawDiffPreview(LightDiffColors) }
            clipRect(left = size.width / 2, right = size.width) { drawDiffPreview(DarkDiffColors) }
          }
        }
      }
      Box(
        modifier = Modifier.matchParentSize().border(
          width = if (selected) 2.dp else 1.dp,
          color = if (selected) colors.accent else colors.border,
          shape = CardShape,
        ),
      )
    }
    BasicText(
      text = appearance.name,
      style = TextStyle(
        color = colors.text,
        fontSize = 12.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
      ),
    )
  }
}

@Composable
fun DiffPreview(colors: DiffColors, modifier: Modifier = Modifier) {
  Canvas(modifier = modifier.size(width = 120.dp, height = 78.dp)) { drawDiffPreview(colors) }
}

private fun DrawScope.drawDiffPreview(colors: DiffColors) {
  val w = size.width
  val h = size.height
  drawRect(colors.background, size = size)
  val header = 14.dp.toPx()
  drawRect(colors.fileHeaderBackground, size = Size(w, header))
  val barRadius = CornerRadius(2.dp.toPx())
  val barHeight = 4.dp.toPx()
  drawRoundRect(
    colors.mutedText,
    topLeft = Offset(8.dp.toPx(), (header - barHeight) / 2),
    size = Size(36.dp.toPx(), barHeight),
    cornerRadius = barRadius,
  )
  val rowHeight = (h - header) / PreviewRows.size
  val gutter = 14.dp.toPx()
  PreviewRows.forEachIndexed { index, (kind, fraction) ->
    val top = header + index * rowHeight
    val (rowBg, gutterBg) = when (kind) {
      RowKind.Context -> colors.background to colors.fileHeaderBackground
      RowKind.Removed -> colors.removedBackground to colors.removedLineNumberBackground
      RowKind.Added -> colors.addedBackground to colors.addedLineNumberBackground
    }
    drawRect(rowBg, Offset(0f, top), Size(w, rowHeight))
    drawRect(gutterBg, Offset(0f, top), Size(gutter, rowHeight))
    val barColor = if (kind == RowKind.Context) colors.mutedText else colors.text
    drawRoundRect(
      barColor.copy(alpha = 0.55f),
      topLeft = Offset(gutter + 6.dp.toPx(), top + (rowHeight - barHeight) / 2),
      size = Size((w - gutter - 12.dp.toPx()) * fraction, barHeight),
      cornerRadius = barRadius,
    )
  }
}
