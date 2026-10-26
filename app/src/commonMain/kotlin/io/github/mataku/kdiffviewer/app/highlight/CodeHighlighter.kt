package io.github.mataku.kdiffviewer.app.highlight

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.AnnotatedString
import io.github.mataku.compose.highlight.core.SyntaxTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

class CodeHighlighter<L>(
  private val languages: LanguageResolver<L>,
  private val highlight: (code: String, language: L, theme: SyntaxTheme) -> AnnotatedString,
  private val dispatcher: CoroutineDispatcher,
  private val onFailure: (Throwable) -> Unit = { it.printStackTrace() },
) {
  suspend fun highlight(code: String, info: String?, theme: SyntaxTheme): AnnotatedString? {
    val id = info?.let(::languageIdForInfo) ?: return null
    val language = languages.languageFor(id) ?: return null
    return try {
      withContext(dispatcher) { highlight(code, language, theme) }
    } catch (e: CancellationException) {
      throw e
    } catch (t: Throwable) {
      onFailure(t)
      null
    }
  }
}

val LocalCodeHighlighter = staticCompositionLocalOf<CodeHighlighter<*>?> { null }
