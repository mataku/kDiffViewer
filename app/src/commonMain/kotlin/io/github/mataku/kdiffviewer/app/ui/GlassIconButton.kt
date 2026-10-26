package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

private val CapsuleShape = RoundedCornerShape(percent = 50)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassIconButton(
  icon: ImageVector,
  contentDescription: String,
  tooltip: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = LocalDiffColors.current
  val interactionSource = remember { MutableInteractionSource() }
  val hovered by interactionSource.collectIsHoveredAsState()
  val pressed by interactionSource.collectIsPressedAsState()
  val fill = when {
    pressed -> colors.glassFillPressed
    hovered -> colors.glassFillHovered
    else -> colors.glassFill
  }
  TooltipBox(
    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
    tooltip = { PlainTooltip { Text(tooltip) } },
    state = rememberTooltipState(),
  ) {
    Box(
      modifier = modifier
        .clip(CapsuleShape)
        .background(fill)
        .border(1.dp, Brush.verticalGradient(listOf(colors.glassHighlight, colors.glassEdge)), CapsuleShape)
        .hoverable(interactionSource)
        .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
        .padding(horizontal = 12.dp, vertical = 6.dp),
      contentAlignment = Alignment.Center,
    ) {
      Image(
        imageVector = icon,
        contentDescription = contentDescription,
        modifier = Modifier.size(14.dp),
        colorFilter = ColorFilter.tint(colors.text),
      )
    }
  }
}
