package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import io.github.mataku.kdiffviewer.app.TypedRef
import io.github.mataku.kdiffviewer.app.ViewerError
import io.github.mataku.kdiffviewer.app.defaultEntryLabel
import io.github.mataku.kdiffviewer.app.parseTypedRef
import io.github.mataku.kdiffviewer.app.pickerErrorText
import io.github.mataku.kdiffviewer.app.sectionBranches
import io.github.mataku.kdiffviewer.core.model.BaseSpec
import io.github.mataku.kdiffviewer.core.model.BranchRef
import io.github.mataku.kdiffviewer.core.model.HeadRef
import io.github.mataku.kdiffviewer.core.model.ResolvedBase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val PickerShape = RoundedCornerShape(8.dp)

@Composable
fun BasePicker(
  base: ResolvedBase.Commit,
  baseSpec: BaseSpec,
  head: HeadRef?,
  loadBranches: suspend () -> List<BranchRef>,
  onSwitch: suspend (BaseSpec) -> ViewerError?,
  textStyle: TextStyle,
  modifier: Modifier = Modifier,
) {
  var open by remember { mutableStateOf(false) }
  var tagHeight by remember { mutableStateOf(0) }
  val gap = with(LocalDensity.current) { 4.dp.roundToPx() }
  val uncommitted = base.label == "HEAD"
  Box(modifier = modifier) {
    MacPullDownTag(
      text = if (uncommitted) "Uncommitted changes" else base.label,
      onClick = { open = true },
      icon = if (uncommitted) null else KdvIcons.Branch,
      textStyle = textStyle,
      textModifier = Modifier.testTag("base-label"),
      modifier = Modifier.testTag("base-picker").onSizeChanged { tagHeight = it.height },
    )
    if (open) {
      Popup(
        offset = IntOffset(0, tagHeight + gap),
        onDismissRequest = { open = false },
        properties = PopupProperties(focusable = true),
      ) {
        BasePopupContent(
          base = base,
          baseSpec = baseSpec,
          head = head,
          loadBranches = loadBranches,
          onSwitch = onSwitch,
          textStyle = textStyle,
          close = { open = false },
        )
      }
    }
  }
}

@Composable
private fun BasePopupContent(
  base: ResolvedBase.Commit,
  baseSpec: BaseSpec,
  head: HeadRef?,
  loadBranches: suspend () -> List<BranchRef>,
  onSwitch: suspend (BaseSpec) -> ViewerError?,
  textStyle: TextStyle,
  close: () -> Unit,
) {
  val colors = LocalDiffColors.current
  val scope = rememberCoroutineScope()
  var query by remember { mutableStateOf("") }
  var errorText by remember { mutableStateOf<String?>(null) }
  var switching by remember { mutableStateOf(false) }
  val currentRef = (baseSpec as? BaseSpec.Ref)?.ref
  var branches by remember { mutableStateOf<Result<List<BranchRef>>?>(null) }
  val focusRequester = remember { FocusRequester() }
  LaunchedEffect(Unit) { focusRequester.requestFocus() }
  LaunchedEffect(Unit) {
    branches = try {
      Result.success(loadBranches())
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }
  val loaded = branches?.getOrNull()
  val sections = remember(loaded, head, query) { loaded?.let { sectionBranches(it, head, query) } }

  fun switchTo(spec: BaseSpec) {
    if (switching) return
    switching = true
    scope.launch {
      try {
        val error = onSwitch(spec)
        if (error == null) close() else errorText = pickerErrorText(error)
      } finally {
        switching = false
      }
    }
  }

  fun submit() {
    when (val typed = parseTypedRef(query)) {
      TypedRef.Empty -> Unit
      is TypedRef.Invalid -> errorText = typed.message
      is TypedRef.Valid -> switchTo(typed.spec)
    }
  }

  val status = when {
    branches == null -> "Loading branches…"

    loaded == null -> "Could not list branches"

    sections != null && sections.local.isEmpty() && sections.remote.isEmpty() && parseTypedRef(query) is TypedRef.Valid ->
      "Press Return to use \"${query.trim()}\" as the base"

    else -> null
  }

  Column(
    modifier = Modifier
      .testTag("base-popup")
      .titleBarClientRegion("base-popup")
      .width(280.dp)
      .shadow(12.dp, PickerShape, clip = false)
      .clip(PickerShape)
      .background(colors.background)
      .border(0.5.dp, colors.border, PickerShape)
      .onPreviewKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
          close()
          true
        } else {
          false
        }
      },
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      MacTextField(
        value = query,
        onValueChange = {
          query = it
          errorText = null
        },
        placeholder = "Branch or revision",
        modifier = Modifier
          .testTag("base-filter")
          .focusRequester(focusRequester)
          .onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown && (event.key == Key.Enter || event.key == Key.NumPadEnter)) {
              submit()
              true
            } else {
              false
            }
          },
      )
      errorText?.let { MacInlineError(it, Modifier.testTag("base-error")) }
    }
    LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
      item(key = "default") {
        BasePickerRow(
          text = defaultEntryLabel(base, baseSpec),
          tag = "base-default",
          checked = baseSpec == BaseSpec.Default,
          enabled = !switching,
          textStyle = textStyle,
        ) { switchTo(BaseSpec.Default) }
      }
      if (status != null) {
        item(key = "status") {
          BasicText(
            text = status,
            style = textStyle.copy(color = colors.mutedText),
            modifier = Modifier.testTag("base-list-status").fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp),
          )
        }
      }
      if (sections != null) {
        if (sections.local.isNotEmpty()) {
          item(key = "header/local") { SectionHeader("Local", "base-section-local", textStyle) }
          items(sections.local, key = { "local/${it.name}" }) { ref ->
            BasePickerRow(ref.name, "base-item-local-${ref.name}", ref.name == currentRef, !switching, textStyle) {
              switchTo(BaseSpec.Ref(ref.name))
            }
          }
        }
        if (sections.remote.isNotEmpty()) {
          item(key = "header/remote") { SectionHeader("Remote", "base-section-remote", textStyle) }
          items(sections.remote, key = { "remote/${it.name}" }) { ref ->
            BasePickerRow(ref.name, "base-item-remote-${ref.name}", ref.name == currentRef, !switching, textStyle) {
              switchTo(BaseSpec.Ref(ref.name))
            }
          }
        }
      }
    }
  }
}

@Composable
private fun SectionHeader(text: String, tag: String, textStyle: TextStyle) {
  val colors = LocalDiffColors.current
  BasicText(
    text = text,
    style = textStyle.copy(color = colors.mutedText),
    modifier = Modifier.testTag(tag).fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp),
  )
}

@Composable
private fun BasePickerRow(text: String, tag: String, checked: Boolean, enabled: Boolean, textStyle: TextStyle, onClick: () -> Unit) {
  val colors = LocalDiffColors.current
  Row(
    modifier = Modifier
      .testTag(tag)
      .fillMaxWidth()
      .clickable(enabled = enabled, onClick = onClick)
      .padding(horizontal = 10.dp, vertical = 5.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (checked) {
      Image(
        imageVector = KdvIcons.CheckCircle,
        contentDescription = null,
        modifier = Modifier.testTag("base-check").size(12.dp),
        colorFilter = ColorFilter.tint(colors.accent),
      )
    } else {
      Spacer(Modifier.size(12.dp))
    }
    Spacer(Modifier.width(6.dp))
    BasicText(
      text = text,
      style = textStyle,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f),
    )
  }
}
