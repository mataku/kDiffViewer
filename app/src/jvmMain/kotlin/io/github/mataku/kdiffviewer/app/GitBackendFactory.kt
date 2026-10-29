package io.github.mataku.kdiffviewer.app

import io.github.mataku.compose.highlight.api.Language
import io.github.mataku.compose.highlight.core.highlight
import io.github.mataku.compose.highlight.core.highlightLines
import io.github.mataku.kdiffviewer.app.highlight.CodeHighlighter
import io.github.mataku.kdiffviewer.app.highlight.HighlightPipeline
import io.github.mataku.kdiffviewer.app.highlight.LanguageResolver
import io.github.mataku.kdiffviewer.app.highlight.LineHighlighter
import io.github.mataku.kdiffviewer.app.highlight.defaultLanguageAccessors
import io.github.mataku.kdiffviewer.app.settings.GitBackend
import io.github.mataku.kdiffviewer.core.git.FileSystemWorkingTreeReader
import io.github.mataku.kdiffviewer.core.git.GitContentSource
import io.github.mataku.kdiffviewer.core.git.GitDiffSource
import io.github.mataku.kdiffviewer.core.git.GitInfo
import io.github.mataku.kdiffviewer.core.git.ProcessGitBlobReader
import io.github.mataku.kdiffviewer.core.git.ProcessGitCommandRunner
import kotlinx.coroutines.Dispatchers

class GitBackendFactory(private val highlightEnabled: Boolean) {
  private val languages = LanguageResolver(defaultLanguageAccessors)
  private val highlighter = LineHighlighter(::highlightLines)

  val codeHighlighter: CodeHighlighter<Language>? =
    if (highlightEnabled) CodeHighlighter(languages, ::highlight, Dispatchers.Default) else null

  fun create(info: GitInfo): GitBackend {
    val runner = ProcessGitCommandRunner(info.path)
    val reader = FileSystemWorkingTreeReader()
    val pipeline: HighlightPipeline<Language>? = if (highlightEnabled) {
      HighlightPipeline(
        contentSource = GitContentSource(runner, { root -> ProcessGitBlobReader(runner.gitExecutable, root) }, reader),
        languages = languages,
        highlighter = highlighter,
        dispatcher = Dispatchers.Default,
      )
    } else {
      null
    }
    return GitBackend(info, GitDiffSource(runner, reader), pipeline, reader)
  }
}
