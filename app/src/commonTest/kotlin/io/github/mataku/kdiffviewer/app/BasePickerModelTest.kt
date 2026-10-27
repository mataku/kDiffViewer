package io.github.mataku.kdiffviewer.app

import io.github.mataku.kdiffviewer.core.model.BaseSpec
import io.github.mataku.kdiffviewer.core.model.BranchRef
import io.github.mataku.kdiffviewer.core.model.HeadRef
import io.github.mataku.kdiffviewer.core.model.ResolvedBase
import kotlin.test.Test
import kotlin.test.assertEquals

class BasePickerModelTest {
  private val branches = listOf(BranchRef("feature-x", false), BranchRef("origin/develop", true), BranchRef("main", false), BranchRef("origin/x", false), BranchRef("origin/x", true))

  @Test
  fun sectionBranchesSplitsAndExcludesTheCurrentBranch() {
    val s = sectionBranches(branches, HeadRef.Branch("feature-x"), "")
    assertEquals(listOf(BranchRef("main", false), BranchRef("origin/x", false)), s.local)
    assertEquals(listOf(BranchRef("origin/develop", true), BranchRef("origin/x", true)), s.remote)
  }

  @Test
  fun sectionBranchesExcludesNothingWithoutABranchHead() {
    assertEquals(3, sectionBranches(branches, HeadRef.Detached("abc"), "").local.size)
    assertEquals(3, sectionBranches(branches, null, "").local.size)
  }

  @Test
  fun sectionBranchesFiltersCaseInsensitivelyOnTrimmedText() {
    val s = sectionBranches(branches, null, " DEV ")
    assertEquals(emptyList(), s.local)
    assertEquals(listOf(BranchRef("origin/develop", true)), s.remote)
  }

  @Test
  fun defaultEntryLabelNamesTheTargetOnlyWhileDefaultIsActive() {
    val base = ResolvedBase.Commit("origin/develop", "m", "m")
    assertEquals("Default (origin/develop)", defaultEntryLabel(base, BaseSpec.Default))
    assertEquals("Default", defaultEntryLabel(ResolvedBase.Commit("x", "m", "m"), BaseSpec.Ref("x")))
  }

  @Test
  fun parseTypedRefTrimsRejectsOptionsAndIgnoresBlank() {
    assertEquals(TypedRef.Valid(BaseSpec.Ref("HEAD~3")), parseTypedRef("  HEAD~3 "))
    assertEquals(TypedRef.Invalid("Invalid revision '--x'"), parseTypedRef("--x"))
    assertEquals(TypedRef.Empty, parseTypedRef("   "))
  }

  @Test
  fun pickerErrorTextMatchesSpecCopy() {
    assertEquals("Unknown revision 'foo'", pickerErrorText(ViewerError.UnknownBase("foo")))
    assertEquals("No common ancestor between 'foo' and HEAD", pickerErrorText(ViewerError.NoCommonAncestor("foo")))
    assertEquals("fatal: a", pickerErrorText(ViewerError.GitFailed("fatal: a\nhint: b", 128)))
  }
}
