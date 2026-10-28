package io.github.mataku.kdiffviewer.app.highlight

import io.github.mataku.compose.highlight.api.Language
import io.github.mataku.compose.highlight.api.Languages
import io.github.mataku.compose.highlight.go.Go
import io.github.mataku.compose.highlight.java.Java
import io.github.mataku.compose.highlight.javascript.Javascript
import io.github.mataku.compose.highlight.kotlin.Kotlin
import io.github.mataku.compose.highlight.markdown.Markdown
import io.github.mataku.compose.highlight.python.Python
import io.github.mataku.compose.highlight.ruby.Ruby
import io.github.mataku.compose.highlight.rust.Rust
import io.github.mataku.compose.highlight.swift.Swift
import io.github.mataku.compose.highlight.tsx.Tsx
import io.github.mataku.compose.highlight.typescript.Typescript
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class LanguageId { Kotlin, Ruby, Go, Rust, Swift, Python, Java, JavaScript, TypeScript, Tsx, Markdown }

fun languageIdFor(path: String): LanguageId? {
  val name = path.substringAfterLast('/')
  return when (name) {
    "Gemfile", "Rakefile", "Fastfile", "Brewfile" -> LanguageId.Ruby

    else -> when (name.substringAfterLast('.', "")) {
      "kt", "kts" -> LanguageId.Kotlin
      "rb", "rake", "gemspec", "ru" -> LanguageId.Ruby
      "go" -> LanguageId.Go
      "rs" -> LanguageId.Rust
      "swift" -> LanguageId.Swift
      "py" -> LanguageId.Python
      "java" -> LanguageId.Java
      "js", "jsx", "mjs", "cjs" -> LanguageId.JavaScript
      "ts" -> LanguageId.TypeScript
      "tsx" -> LanguageId.Tsx
      "md" -> LanguageId.Markdown
      else -> null
    }
  }
}

fun languageIdForInfo(info: String): LanguageId? {
  val word = info.trim().substringBefore(' ').substringBefore('\t').lowercase()
  return when (word) {
    "kotlin", "kt", "kts" -> LanguageId.Kotlin
    "ruby", "rb" -> LanguageId.Ruby
    "go", "golang" -> LanguageId.Go
    "rust", "rs" -> LanguageId.Rust
    "swift" -> LanguageId.Swift
    "python", "py" -> LanguageId.Python
    "java" -> LanguageId.Java
    "javascript", "js", "jsx" -> LanguageId.JavaScript
    "typescript", "ts" -> LanguageId.TypeScript
    "tsx" -> LanguageId.Tsx
    "markdown", "md" -> LanguageId.Markdown
    else -> null
  }
}

class LanguageResolver<L>(
  private val accessors: Map<LanguageId, () -> L>,
  private val onFailure: (Throwable) -> Unit = { it.printStackTrace() },
) {
  private val mutex = Mutex()
  private val loaded = HashMap<LanguageId, L>()
  private val failed = HashSet<LanguageId>()

  suspend fun languageFor(path: String): L? = languageIdFor(path)?.let { languageFor(it) }

  suspend fun languageFor(id: LanguageId): L? {
    val accessor = accessors[id] ?: return null
    return mutex.withLock {
      if (id in failed) return@withLock null
      loaded[id]?.let { return@withLock it }
      try {
        accessor().also { loaded[id] = it }
      } catch (t: Throwable) {
        if (!t.isNativeLoadFailure()) throw t
        failed += id
        onFailure(t)
        null
      }
    }
  }
}

val defaultLanguageAccessors: Map<LanguageId, () -> Language> = mapOf(
  LanguageId.Kotlin to { Languages.Kotlin },
  LanguageId.Ruby to { Languages.Ruby },
  LanguageId.Go to { Languages.Go },
  LanguageId.Rust to { Languages.Rust },
  LanguageId.Swift to { Languages.Swift },
  LanguageId.Python to { Languages.Python },
  LanguageId.Java to { Languages.Java },
  LanguageId.JavaScript to { Languages.Javascript },
  LanguageId.TypeScript to { Languages.Typescript },
  LanguageId.Tsx to { Languages.Tsx },
  LanguageId.Markdown to { Languages.Markdown },
)
