package io.github.mataku.kdiffviewer.app.highlight

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LanguageResolverJvmTest {
  private val failures = mutableListOf<Throwable>()

  private fun <L> resolver(accessors: Map<LanguageId, () -> L>) = LanguageResolver(accessors) { failures += it }

  @Test
  fun linkageErrorMarksLanguageFailed() = runTest {
    var calls = 0
    val r = resolver<String>(
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
  fun nativeLoadFailureIsReportedOncePerLanguage() = runTest {
    val r = resolver<String>(mapOf(LanguageId.Kotlin to { throw UnsatisfiedLinkError("no lib") }))
    r.languageFor("a.kt")
    r.languageFor("b.kt")
    assertEquals(1, failures.size)
    assertTrue(failures[0] is UnsatisfiedLinkError)
  }

  @Test
  fun classInitializationFailuresMarkLanguageFailed() = runTest {
    var calls = 0
    val r = resolver<String>(
      mapOf(
        LanguageId.Kotlin to {
          calls++
          if (calls == 1) throw ExceptionInInitializerError("init") else throw NoClassDefFoundError("gone")
        },
      ),
    )
    assertNull(r.languageFor("a.kt"))
    assertNull(r.languageFor("a.kt"))
    assertEquals(1, calls)
    assertTrue(ExceptionInInitializerError("init").isNativeLoadFailure())
    assertTrue(NoClassDefFoundError("gone").isNativeLoadFailure())
  }

  @Test
  fun failedLanguageDoesNotAffectOthers() = runTest {
    val r = resolver(
      mapOf(
        LanguageId.Kotlin to { throw UnsatisfiedLinkError("no lib") },
        LanguageId.Ruby to { "ruby" },
      ),
    )
    assertNull(r.languageFor("a.kt"))
    assertEquals("ruby", r.languageFor("a.rb"))
  }

  @Test
  fun otherErrorsPropagate() = runTest {
    val r = resolver<String>(mapOf(LanguageId.Kotlin to { throw IllegalStateException("boom") }))
    assertFailsWith<IllegalStateException> { r.languageFor("a.kt") }
    assertEquals(emptyList(), failures)
  }

  @Test
  fun realLanguagesResolve() = runTest {
    val r = resolver(defaultLanguageAccessors)
    for (name in listOf("a.kt", "a.rb", "a.go", "a.rs", "a.swift", "a.py", "a.java", "a.js", "a.ts", "a.md")) {
      assertNotNull(r.languageFor(name), name)
    }
  }
}
