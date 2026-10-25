# kDiffViewer — Agent Guide

## Overview

kDiffViewer is a desktop git diff viewer built with Kotlin Multiplatform and Compose Desktop. It opens a local repository and shows everything that differs from `HEAD` (staged, unstaged, and untracked) in a GitHub-like unified view. macOS is the primary target; Linux comes later. Part of the goal is to find out how far KMP can go for a desktop GUI app, so logic belongs in shared Kotlin, not in platform code.

## Modules

```
core/   KMP library (jvm target). Diff model, streaming unified diff parser, untracked-file synthesis, GitDiffSource.
        commonMain holds all logic; jvmMain holds only OS-specific implementations (process spawning, file reading, git lookup).
app/    Compose Desktop app (jvm target). commonMain holds state holders and Compose UI; jvmMain holds main(), the folder picker, and packaging config.
scripts/ kdv launcher and the large-diff generator for manual performance checks.
```

- `core` must not depend on Compose.
- OS-specific work stays behind the interfaces in `core/commonMain` (`GitCommandRunner`, `WorkingTreeReader`) so that a Kotlin/Native target or another backend (e.g. libgit2) can be added by writing new implementations.
- `app` consumes `core` types; it does not run git itself.

## Commands

```sh
./gradlew check                        # all tests plus spotlessCheck (CI runs this)
./gradlew spotlessApply                # format; run before committing
./gradlew :app:run --args=/abs/path    # run against a repository
./gradlew :app:createDistributable     # build app/build/compose/binaries/main/app/kDiffViewer.app
scripts/generate-large-diff.sh <dir>   # create a large repository for manual scrolling checks
```

## Constraints

- The build requires an Eclipse Temurin JDK 25 toolchain (`JvmVendorSpec.ADOPTIUM` in both modules). Never package with Homebrew's OpenJDK and never set `compose.desktop.packaging.checkJdkVendor=false`: Homebrew's JDK links `libfontmanager.dylib` against Homebrew's harfbuzz and freetype, so the bundled runtime breaks when those formulae change ([Compose issue #3107](https://github.com/JetBrains/compose-multiplatform/issues/3107)). After packaging changes, check with `otool -L` that no dylib under the app's `Contents/runtime` references `/opt/homebrew` or `/usr/local`.
- git output must not depend on the user's git configuration. `GitDiffSource` passes `-c core.quotePath=false`, `--no-color`, `--no-ext-diff`, `--no-textconv`, `--src-prefix=a/`, `--dst-prefix=b/`, `-M`, and `--submodule=short`; keep those flags when touching the diff command, and add one when a new config can change the output format.
- Performance is a goal: diffs can have thousands of files and hundreds of thousands of rows. Keep `LazyColumn` rows keyed by index (the same path can appear twice), append rows incrementally, and avoid work proportional to the whole diff per batch.
- When choosing between designs, prefer the one with lower CPU cost and memory use. State each option's cost when proposing it (work per update, recomposition scope, retained memory), and do not pick a slower or heavier option for simplicity alone; if one is chosen anyway, record why.
- Formatting is 2-space indent via `.editorconfig` and ktlint (Spotless).

## Workflow

- Branch from `develop` and open pull requests against `develop`. CI runs on pushes to `develop` and on pull requests.
- Follow `.github/PULL_REQUEST_TEMPLATE.md`; PRs that change the UI need screenshots.
- Design specs and plans live under `docs/superpowers/`, which is gitignored and exists only on the owner's machine. The roadmap is the "Out of scope (future list)" section of `docs/superpowers/specs/2026-10-25-kdiffviewer-first-goal-design.md`.

## When unclear, ask

If requirements, scope, or approach are ambiguous, or a plan cannot be followed as written, stop and ask before proceeding. Surface what you observed, propose one or two options with trade-offs, and wait for direction.
