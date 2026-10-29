package io.github.mataku.kdiffviewer.app.ui.mac

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mataku.kdiffviewer.app.generated.resources.Res
import io.github.mataku.kdiffviewer.app.generated.resources.app_icon
import io.github.mataku.kdiffviewer.app.ui.LocalDiffColors
import org.jetbrains.compose.resources.painterResource

const val ALERT_SHEET_TAG = "alert-sheet"
const val ALERT_ICON_TAG = "alert-icon"

private val SheetShape = RoundedCornerShape(12.dp)

@Composable
fun MacAlertSheet(
  title: String,
  messageLines: List<String>,
  confirmLabel: String,
  onConfirm: () -> Unit,
  onCancel: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = LocalDiffColors.current
  val focusRequester = remember { FocusRequester() }
  LaunchedEffect(Unit) { focusRequester.requestFocus() }
  Box(
    modifier = modifier
      .fillMaxSize()
      .pointerInput(Unit) {
        awaitEachGesture {
          while (true) {
            val event = awaitPointerEvent(PointerEventPass.Final)
            event.changes.forEach { it.consume() }
            if (event.changes.none { it.pressed }) break
          }
        }
      },
    contentAlignment = Alignment.Center,
  ) {
    Column(
      modifier = Modifier
        .testTag(ALERT_SHEET_TAG)
        .width(280.dp)
        .shadow(32.dp, SheetShape, clip = false)
        .clip(SheetShape)
        .background(colors.alertFill())
        .border(0.5.dp, colors.alertEdge(), SheetShape)
        .focusRequester(focusRequester)
        .onKeyEvent { event ->
          if (event.type == KeyEventType.KeyDown && (event.key == Key.Escape || event.key == Key.Enter || event.key == Key.NumPadEnter)) {
            onCancel()
            true
          } else {
            false
          }
        }
        .focusable()
        .padding(16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Image(painterResource(Res.drawable.app_icon), contentDescription = null, modifier = Modifier.size(48.dp).testTag(ALERT_ICON_TAG))
      BasicText(title, style = TextStyle(color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center))
      for (line in messageLines) {
        BasicText(line, style = TextStyle(color = colors.text, fontSize = 11.sp, textAlign = TextAlign.Center))
      }
      Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MacPushButton("Cancel", onCancel, Modifier.weight(1f), default = true)
        MacPushButton(confirmLabel, onConfirm, Modifier.weight(1f))
      }
    }
  }
}
