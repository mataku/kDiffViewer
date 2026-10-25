package io.github.mataku.kdiffviewer.app.highlight

import io.github.mataku.compose.highlight.api.Language
import io.github.mataku.compose.highlight.api.Languages
import io.github.mataku.compose.highlight.go.Go
import io.github.mataku.compose.highlight.kotlin.Kotlin
import io.github.mataku.compose.highlight.ruby.Ruby
import io.github.mataku.compose.highlight.rust.Rust
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class LanguageId { Kotlin, Ruby, Go, Rust }

fun languageIdFor(path: String): LanguageId? {
  val name = path.substringAfterLast('/')
  return when (name) {
    "Gemfile", "Rakefile" -> LanguageId.Ruby

    else -> when (name.substringAfterLast('.', "")) {
      "kt", "kts" -> LanguageId.Kotlin
      "rb", "rake" -> LanguageId.Ruby
      "go" -> LanguageId.Go
      "rs" -> LanguageId.Rust
      else -> null
    }
  }
}

class LanguageResolver<L>(
  private val accessors: Map<LanguageId, () -> L>,
  private val onFailure: (Throwable) -> Unit = { it.printStackTrace() },
) {
  private val mutex = Mutex()
  private val loaded = HashMap<LanguageId, L>()
  private val failed = HashSet<LanguageId>()

  suspend fun languageFor(path: String): L? {
    val id = languageIdFor(path) ?: return null
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
)
