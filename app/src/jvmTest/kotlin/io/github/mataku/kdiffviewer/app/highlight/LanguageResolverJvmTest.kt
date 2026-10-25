package io.github.mataku.kdiffviewer.app.highlight

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LanguageResolverJvmTest {
  @Test
  fun linkageErrorMarksLanguageFailed() = runTest {
    var calls = 0
    val r = LanguageResolver<String>(
      mapOf(
        LanguageId.Kotlin to {
          calls++
          throw UnsatisfiedLinkError("no lib")
        },
      ),
    )
    assertNull(r.languageFor("a.kt"))
    assertNull(r.languageFor("a.kt"))
    assertEquals(1, calls)
  }

  @Test
  fun otherErrorsPropagate() = runTest {
    val r = LanguageResolver<String>(mapOf(LanguageId.Kotlin to { throw IllegalStateException("boom") }))
    assertFailsWith<IllegalStateException> { r.languageFor("a.kt") }
  }

  @Test
  fun realLanguagesResolve() = runTest {
    val r = LanguageResolver(defaultLanguageAccessors)
    for (name in listOf("a.kt", "a.rb", "a.go", "a.rs")) {
      assertNotNull(r.languageFor(name), name)
    }
  }
}
