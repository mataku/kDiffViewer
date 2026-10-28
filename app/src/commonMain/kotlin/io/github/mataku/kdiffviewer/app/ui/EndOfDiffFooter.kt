package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mataku.kdiffviewer.app.generated.resources.Res
import io.github.mataku.kdiffviewer.app.generated.resources.box_cat_dark
import io.github.mataku.kdiffviewer.app.generated.resources.box_cat_light
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

private val ArtWidth = 180.dp
private const val CAPTION = "It fits. You're done."

internal fun endOfDiffArt(dark: Boolean): DrawableResource = if (dark) Res.drawable.box_cat_dark else Res.drawable.box_cat_light

@Composable
fun EndOfDiffFooter(modifier: Modifier = Modifier) {
  val colors = LocalDiffColors.current
  Box(
    modifier = modifier.fillMaxWidth().padding(top = 32.dp, bottom = 24.dp).testTag("end-of-diff"),
    contentAlignment = Alignment.TopCenter,
  ) {
    Column(
      modifier =
      Modifier.layout { m, c ->
        if (c.maxWidth < ArtWidth.roundToPx()) {
          layout(0, 0) {}
        } else {
          val p = m.measure(c)
          layout(p.width, p.height) { p.place(0, 0) }
        }
      },
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      // The drawable-dark qualifier is not used for this art.
      // The resources library resolves that qualifier with isSystemInDarkTheme(), which ignores the app's manual appearance setting.
      // Its override point, LocalSystemTheme, is @InternalComposeUiApi, so the app selects a named resource instead.
      Image(
        painter = painterResource(endOfDiffArt(LocalDarkTheme.current)),
        contentDescription = null,
        modifier = Modifier.width(ArtWidth).testTag("end-of-diff-art"),
      )
      Spacer(Modifier.height(8.dp))
      BasicText(text = CAPTION, style = TextStyle(color = colors.mutedText, fontSize = 13.sp))
    }
  }
}
