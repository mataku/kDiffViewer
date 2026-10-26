package io.github.mataku.kdiffviewer.app

import io.github.mataku.kdiffviewer.core.model.BaseSpec
import io.github.mataku.kdiffviewer.core.model.RepoPath
import kotlin.test.Test
import kotlin.test.assertEquals

class LaunchArgsTest {
  @Test
  fun noArgsMeansNoRepo() {
    assertEquals(LaunchArgs.None, parseLaunchArgs(emptyArray(), "/x"))
  }

  @Test
  fun relativeArgIsResolved() {
    assertEquals(LaunchArgs.Open(RepoPath("/a/r"), BaseSpec.Default), parseLaunchArgs(arrayOf("../r"), "/a/b"))
  }

  @Test
  fun absoluteArgIsKept() {
    assertEquals(LaunchArgs.Open(RepoPath("/r/s"), BaseSpec.Default), parseLaunchArgs(arrayOf("/r/./s"), "/a"))
  }

  @Test
  fun baseAfterPath() {
    assertEquals(LaunchArgs.Open(RepoPath("/a/r"), BaseSpec.Ref("develop")), parseLaunchArgs(arrayOf("../r", "--base", "develop"), "/a/b"))
  }

  @Test
  fun baseBeforePath() {
    assertEquals(LaunchArgs.Open(RepoPath("/r"), BaseSpec.Ref("HEAD~3")), parseLaunchArgs(arrayOf("--base", "HEAD~3", "/r"), "/a"))
  }

  @Test
  fun baseWithoutPathUsesCwd() {
    assertEquals(LaunchArgs.Open(RepoPath("/a"), BaseSpec.Ref("x")), parseLaunchArgs(arrayOf("--base", "x"), "/a"))
  }

  @Test
  fun missingBaseValue() {
    assertEquals(LaunchArgs.Invalid(RepoPath("/r"), "missing value for --base"), parseLaunchArgs(arrayOf("/r", "--base"), "/a"))
  }

  @Test
  fun dashRefIsRejected() {
    assertEquals(LaunchArgs.Invalid(RepoPath("/r"), "invalid --base '-x'"), parseLaunchArgs(arrayOf("/r", "--base", "-x"), "/a"))
  }

  @Test
  fun unknownOption() {
    assertEquals(LaunchArgs.Invalid(RepoPath("/r"), "unknown option '--foo'"), parseLaunchArgs(arrayOf("/r", "--foo"), "/a"))
  }

  @Test
  fun extraArgument() {
    assertEquals(LaunchArgs.Invalid(RepoPath("/r"), "unexpected argument '/s'"), parseLaunchArgs(arrayOf("/r", "/s"), "/a"))
  }
}
