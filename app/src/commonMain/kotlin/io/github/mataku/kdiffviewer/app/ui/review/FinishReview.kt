package io.github.mataku.kdiffviewer.app.ui.review

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import io.github.mataku.kdiffviewer.app.review.MAX_REPLY_BODY_CHARS
import io.github.mataku.kdiffviewer.app.ui.LocalDiffColors
import io.github.mataku.kdiffviewer.app.ui.mac.MacPushButton
import io.github.mataku.kdiffviewer.app.ui.mac.MacTextArea
import io.github.mataku.kdiffviewer.app.ui.mac.MacTextField
import io.github.mataku.kdiffviewer.app.ui.titleBarClientRegion

enum class ReviewControls { Toolbar, BottomBar }

private val PopoverShape = RoundedCornerShape(8.dp)
private val CountShape = RoundedCornerShape(8.dp)
private val WaitingGreen = Color(0xFF34C759)

@Composable
fun FinishReviewButton(
  pendingCount: Int,
  waiting: Boolean,
  summary: String,
  onSummaryChange: (String) -> Unit,
  onSend: (approve: Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  var open by remember { mutableStateOf(false) }
  var buttonHeight by remember { mutableIntStateOf(0) }
  val gap = with(LocalDensity.current) { 4.dp.roundToPx() }
  Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
    if (waiting) {
      AgentWaiting()
      Spacer(Modifier.width(8.dp))
    }
    Box {
      Row(
        modifier = Modifier.onSizeChanged { buttonHeight = it.height },
        verticalAlignment = Alignment.CenterVertically,
      ) {
        MacPushButton(
          text = "Finish Review",
          onClick = { open = true },
          modifier = Modifier.testTag("finish-review-button").titleBarClientRegion("finish-review"),
        )
        if (pendingCount > 0) {
          Spacer(Modifier.width(6.dp))
          PendingCount(pendingCount)
        }
      }
      if (open) {
        Popup(
          alignment = Alignment.TopEnd,
          offset = IntOffset(0, buttonHeight + gap),
          onDismissRequest = { open = false },
          properties = PopupProperties(focusable = true),
        ) {
          val colors = LocalDiffColors.current
          Column(
            modifier = Modifier
              .testTag("finish-review-popover")
              .titleBarClientRegion("finish-review-popover")
              .width(320.dp)
              .shadow(12.dp, PopoverShape, clip = false)
              .clip(PopoverShape)
              .background(colors.background)
              .border(0.5.dp, colors.border, PopoverShape)
              .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                  open = false
                  true
                } else {
                  false
                }
              }
              .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            BasicText("Leave a summary (optional)", style = TextStyle(color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
            MacTextArea(
              value = summary,
              onValueChange = { onSummaryChange(it.take(MAX_REPLY_BODY_CHARS)) },
              modifier = Modifier.testTag("review-summary"),
            )
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
              PendingSummary(pendingCount, Modifier.weight(1f))
              SendButtons(pendingCount, summary) { approve ->
                onSend(approve)
                open = false
              }
            }
          }
        }
      }
    }
  }
}

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
  Row(
    modifier = modifier
      .testTag("review-bar")
      .fillMaxWidth()
      .background(colors.fileHeaderBackground)
      .padding(horizontal = 20.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    if (waiting) AgentWaiting()
    MacTextField(
      value = summary,
      onValueChange = { onSummaryChange(it.take(MAX_REPLY_BODY_CHARS)) },
      modifier = Modifier.testTag("review-summary"),
      containerModifier = Modifier.weight(1f),
      placeholder = "Leave a summary (optional)",
    )
    PendingSummary(pendingCount)
    SendButtons(pendingCount, summary, onSend)
  }
}

@Composable
private fun PendingSummary(pendingCount: Int, modifier: Modifier = Modifier) {
  BasicText(
    text = if (pendingCount == 1) "1 pending comment" else "$pendingCount pending comments",
    modifier = modifier,
    style = TextStyle(color = LocalDiffColors.current.mutedText, fontSize = 12.sp),
  )
}

@Composable
private fun SendButtons(pendingCount: Int, summary: String, onSend: (approve: Boolean) -> Unit) {
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    MacPushButton(text = "Approve", onClick = { onSend(true) })
    MacPushButton(text = "Submit", onClick = { onSend(false) }, enabled = pendingCount > 0 || summary.isNotBlank(), default = true)
  }
}

@Composable
private fun PendingCount(count: Int) {
  val colors = LocalDiffColors.current
  BasicText(
    text = count.toString(),
    modifier = Modifier
      .testTag("finish-review-count")
      .background(colors.accent, CountShape)
      .padding(horizontal = 6.dp, vertical = 1.dp),
    style = TextStyle(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
  )
}

@Composable
private fun AgentWaiting() {
  val colors = LocalDiffColors.current
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    Box(Modifier.size(8.dp).background(WaitingGreen, CircleShape))
    BasicText("Agent waiting", style = TextStyle(color = colors.mutedText, fontSize = 12.sp))
  }
}
