package io.github.mataku.kdiffviewer.app.highlight

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LanguageResolverTest {
  private var calls = 0

  private fun resolver() = LanguageResolver(
    LanguageId.entries.associateWith { id ->
      {
        calls++
        id.name
      }
    },
  )

  @Test
  fun mapsFileNamesToLanguages() = runTest {
    val r = resolver()
    assertEquals("Kotlin", r.languageFor("a/B.kt"))
    assertEquals("Kotlin", r.languageFor("build.gradle.kts"))
    assertEquals("Ruby", r.languageFor("x.rb"))
    assertEquals("Ruby", r.languageFor("lib/t.rake"))
    assertEquals("Ruby", r.languageFor("Gemfile"))
    assertEquals("Ruby", r.languageFor("a/Rakefile"))
    assertEquals("Go", r.languageFor("m.go"))
    assertEquals("Rust", r.languageFor("lib.rs"))
  }

  @Test
  fun unknownFilesHaveNoLanguage() = runTest {
    val r = resolver()
    assertNull(r.languageFor("README.md"))
    assertNull(r.languageFor("Makefile"))
    assertNull(r.languageFor("kt"))
    assertNull(r.languageFor("a.kt/notes.txt"))
  }

  @Test
  fun accessorIsCalledOncePerLanguage() = runTest {
    val r = resolver()
    repeat(3) { r.languageFor("x.kt") }
    assertEquals(1, calls)
  }
}
