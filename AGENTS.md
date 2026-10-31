# kDiffViewer — Agent Guide

## Overview

kDiffViewer is a desktop git diff viewer built with Kotlin Multiplatform and Compose Desktop. It opens a local repository and shows everything that differs from a base commit (committed, staged, unstaged, and untracked changes) in a GitHub-like unified view. The base is the merge-base of `HEAD` and the `origin/HEAD` target (falling back to `HEAD`), or the merge-base with a ref given by `--base <ref>`. macOS is the primary target; Linux (x64 and aarch64) builds and runs without the `kdv` command, which is macOS-only for now. Part of the goal is to find out how far KMP can go for a desktop GUI app, so logic belongs in shared Kotlin, not in platform code.

## Modules

```
core/   KMP library (jvm target). Diff model, streaming unified diff parser, untracked-file synthesis, GitDiffSource.
        commonMain holds all logic; jvmMain holds only OS-specific implementations (process spawning, file reading, git lookup).
protocol/ KMP library (jvm + macosArm64). Wire types shared by the app and the CLI, and the control-character marking both print.
cli/    Kotlin/Native executable (macosArm64) producing `kdv`, which talks to running app windows over a Unix socket.
app/    Compose Desktop app (jvm target). commonMain holds state holders and Compose UI; jvmMain holds main(), the windows and menu bar (they need Compose Desktop's Window and MenuBar), the AWT and JBR integrations (Desktop handlers, title bar, clipboard, folder picker), the objects that need the JVM (the kdv socket, process handles), and packaging config.
        Bundled images live only under commonMain/composeResources/ (density-qualified PNG); app/art/ holds the SVG originals they are exported from (including app_icon.svg) and is not packaged; app/art/kDiffViewer.icon is the Icon Composer original of the app icon; app/packaging/ holds what packaging uses: kDiffViewer.icns (fallback for macOS 15 and earlier, and the Dock icon for `:app:run`) app-content/Resources/Assets.car (compiled from the .icon, copied into the bundle's Contents/Resources), and kDiffViewer.png (the Linux icon, exported from app_icon.svg at 512 px). All are committed, so the build does not need Xcode.
scripts/ large-diff generator for manual performance checks, and a small cat-themed diff generator for screenshots.
plugins/ kdv/ is the Claude Code / Codex / npx skills plugin with the kdv-review agent skill; .claude-plugin/marketplace.json lists it.
```

- Only `protocol` and `cli` have native targets; `core` and `app` stay jvm-only.
- `core` must not depend on Compose. `app/compose-stability.conf` tells the Compose compiler that the classes in `core.model` and `core.review` are stable, so keep them immutable: `val` properties only, and read-only collections that are never mutated after construction.
- OS-specific work stays behind the interfaces in `core/commonMain` (`GitCommandRunner`, `WorkingTreeReader`) so that a Kotlin/Native target or another backend (e.g. libgit2) can be added by writing new implementations.
- `app` consumes `core` types; it does not run git itself.
- `plugins/kdv/skills/kdv-review/SKILL.md` stays thin: it only runs `kdv agent-guide`. Document kdv usage in `AgentGuide.kt`, not in the skill, so the guide always matches the installed `kdv`.

## Commands

```sh
./gradlew check                        # all tests plus spotlessCheck (CI runs this)
./gradlew spotlessApply                # format; run before committing
./gradlew :app:run --args=/abs/path    # run against a repository
./gradlew :app:createDistributable     # build app/build/compose/binaries/main/app/kDiffViewer.app (kDiffViewer/ on Linux)
scripts/generate-large-diff.sh <dir> [ext]   # create a large repository for manual scrolling checks; ext defaults to txt, kt writes Kotlin sources
scripts/generate-screenshot-diff.sh <dir>    # create a worktree of develop on feature/cat-facts with a small committed, staged, unstaged, and untracked diff for screenshots
xcrun actool app/art/kDiffViewer.icon --compile <out> --app-icon kDiffViewer --platform macosx --target-device mac --minimum-deployment-target 26.0 --output-partial-info-plist <out>/partial.plist   # regenerate Assets.car after editing the icon
```

## Constraints

- The build and the bundled runtime use the JetBrains Runtime (JBR) 25 toolchain (`JvmVendorSpec.JETBRAINS` in both modules; the foojay resolver in `settings.gradle.kts` downloads it when missing). The app relies on the JBR API (`com.jetbrains:jbr-api`) for the custom title bar, and falls back to the system title bar when `JBR.isWindowDecorationsSupported()` is false. Never package with Homebrew's OpenJDK and never set `compose.desktop.packaging.checkJdkVendor=false`: Homebrew's JDK links `libfontmanager.dylib` against Homebrew's harfbuzz and freetype, so the bundled runtime breaks when those formulae change ([Compose issue #3107](https://github.com/JetBrains/compose-multiplatform/issues/3107)). After packaging changes, check with `otool -L` that no dylib under the app's `Contents/runtime` references `/opt/homebrew` or `/usr/local`.
- git output must not depend on the user's git configuration. `GitDiffSource` passes `-c core.quotePath=false`, `-c core.fsmonitor=false` (also on `ls-files`, so a repository's configured fsmonitor hook never runs), `-c diff.orderFile=/dev/null` (files must arrive in path order for the tree-order sort), `--no-color`, `--no-ext-diff`, `--no-textconv`, `--src-prefix=a/`, `--dst-prefix=b/`, `-M`, `--submodule=short`, `--unified=3`, and `--inter-hunk-context=0` (so `diff.context` and `diff.interHunkContext` cannot change hunk shapes); keep those flags when touching the diff command, and add one when a new config can change the output format.
- Untrusted .git directories carry git's own command-execution risk through filters; the README says so, and this is one motivation for a libgit2 backend.
- Formatting is 2-space indent via `.editorconfig` and ktlint (Spotless); `no-unused-imports` is enabled, so `spotlessApply` removes unused imports.

## Performance, memory and security

Diffs can have thousands of files and hundreds of thousands of rows, so CPU and memory cost is a design input, not a later fix.

- Review cost at every stage without being asked: list each option's cost when proposing a design (work per update, recomposition scope, retained memory), put a "Performance, memory and security review" section in every plan, and have task and final reviews check it. Do not pick a slower or heavier option for simplicity alone; if one is chosen anyway, record why.
- Keep the EDT free of blocking work (process spawns, file I/O, font enumeration); run it on `Dispatchers.IO` or before `application {}`.
- `LazyColumn` diff rows: key by index (the same path can appear twice), append incrementally, and add no per-row allocation (build `TextStyle`s once and provide them through a static CompositionLocal), no per-row test tags or semantics nodes, and complete `remember` keys.
- Avoid work proportional to the whole diff per batch, and avoid eager composition of large lists (e.g. Material `DropdownMenu`); use lazy lists.
- Reuse expensive resources across swaps (grammars, highlighters), and release window-scoped resources when the window closes.
- Inputs that do not change state must be no-ops (no reload, no probe, no write); conflate bursts of writes.
- Review security at every stage as well: the security part of each plan's review names every input boundary (what user-controlled text reaches a git argv, stdin, a file path, or the clipboard), confirms that spawns stay shell-free with user input placed after `--end-of-options` and option-like values rejected, and confirms that output does not depend on the user's git configuration; task and final reviews check it.

## macOS UI

- Follow macOS conventions (Apple HIG) over Material. Material3 is theme plumbing here; do not adopt its visual idioms (outlined/filled text fields, floating labels, red error borders, snackbars, FABs).
- Build buttons, popup buttons, segmented controls, steppers, text fields and search fields from the `Mac*` composables in `app/src/commonMain/kotlin/io/github/mataku/kdiffviewer/app/ui/mac/` (one file per control family); add a missing control there instead of reaching for a Material widget. `app/ui/` holds screens and composables with no platform idiom; `app/ui/mac/` holds the macOS-idiom controls.
- Validation feedback goes inline next to the control as a small icon plus one short line of text; do not block with alerts for input errors.
- Settings live under the application menu (`Settings…`, ⌘,) and apply immediately; no Save/Apply buttons.
- Use standard macOS shortcuts and menu placement, and follow the system appearance by default.
- When proposing UI, say which macOS app or system panel the pattern matches (System Settings, Xcode, Finder). If none does, ask before building it.

## Workflow

- Branch from `develop` and open pull requests against `develop`. CI runs on pushes to `develop` and on pull requests, on the `xcode-27` runner (macOS 27, arm64) and in a separate `linux` job on `ubuntu-24.04`. The macOS jobs also cover the `kdv` CLI and the native `protocol` target, which Linux cannot build; the `linux` job runs `check` and `:app:createDistributable` so Linux regressions in the app show up.
- Follow `.github/PULL_REQUEST_TEMPLATE.md`; PRs that change the UI need screenshots.

## When unclear, ask

If requirements, scope, or approach are ambiguous, or a plan cannot be followed as written, stop and ask before proceeding. Surface what you observed, propose one or two options with trade-offs, and wait for direction.
