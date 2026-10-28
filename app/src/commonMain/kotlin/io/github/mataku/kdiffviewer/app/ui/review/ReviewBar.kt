package io.github.mataku.kdiffviewer.app.ui.review

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mataku.kdiffviewer.app.review.MAX_REPLY_BODY_CHARS
import io.github.mataku.kdiffviewer.app.ui.LocalDiffColors
import io.github.mataku.kdiffviewer.app.ui.mac.MacPushButton
import io.github.mataku.kdiffviewer.app.ui.mac.MacTextField

private val WaitingGreen = Color(0xFF34C759)

@Composable
fun ReviewBar(
  pendingCount: Int,
  waiting: Boolean,
  summary: String,
  onSummaryChange: (String) -> Unit,
  onSend: (approve: Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  if (pendingCount == 0 && !waiting) return
  val colors = LocalDiffColors.current
  val mutedStyle = remember(colors) { TextStyle(color = colors.mutedText, fontSize = 12.sp) }
  Row(
    modifier = modifier
      .testTag("review-bar")
      .fillMaxWidth()
      .background(colors.fileHeaderBackground)
      .padding(horizontal = 20.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    if (waiting) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).background(WaitingGreen, CircleShape))
        BasicText("Agent waiting", style = mutedStyle)
      }
    }
    MacTextField(
      value = summary,
      onValueChange = { onSummaryChange(it.take(MAX_REPLY_BODY_CHARS)) },
      modifier = Modifier.testTag("review-summary"),
      containerModifier = Modifier.weight(1f),
      placeholder = "Message to the agent (optional)",
    )
    BasicText(if (pendingCount == 1) "1 pending comment" else "$pendingCount pending comments", style = mutedStyle)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      MacPushButton(text = "Approve", onClick = { onSend(true) })
      MacPushButton(text = "Send to Agent", onClick = { onSend(false) }, enabled = pendingCount > 0 || summary.isNotBlank(), default = true)
    }
  }
}
