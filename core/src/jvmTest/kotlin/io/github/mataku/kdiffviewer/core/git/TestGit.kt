package io.github.mataku.kdiffviewer.core.git

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.TimeUnit

fun requireGit(): String = checkNotNull((GitLocator.locateOnHost(null) as? GitLocation.Found)?.info?.path) { "git executable not found on host" }

fun newTempDir(): File = Files.createTempDirectory("kdv-test").toFile().canonicalFile

fun runGit(dir: File, vararg args: String): String {
  val command = listOf(
    requireGit(),
    "-c",
    "user.name=t",
    "-c",
    "user.email=t@example.com",
    "-c",
    "commit.gpgsign=false",
  ) + args
  val builder = ProcessBuilder(command).directory(dir).redirectErrorStream(true)
  builder.environment()["GIT_CONFIG_GLOBAL"] = "/dev/null"
  builder.environment()["GIT_CONFIG_NOSYSTEM"] = "1"
  val process = builder.start()
  val output = process.inputStream.readBytes().decodeToString()
  check(process.waitFor(30, TimeUnit.SECONDS)) { "git timed out: $args" }
  check(process.exitValue() == 0) { "git ${args.toList()} failed: $output" }
  return output
}

fun writeScript(dir: File, name: String, body: String): File {
  val file = File(dir, name)
  file.writeText("#!/bin/sh\n$body\n")
  Files.setPosixFilePermissions(file.toPath(), PosixFilePermissions.fromString("rwxr-xr-x"))
  return file
}
