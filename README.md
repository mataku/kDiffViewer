# kDiffViewer

kDiffViewer is a macOS desktop viewer for the git diff of a branch and its working tree, built with Kotlin Multiplatform and Compose Desktop. It shows a GitHub-like unified diff with a file tree that has a filter field to narrow it by path, and streams large diffs in as they load.

Syntax highlighting covers Kotlin, Ruby, Go, Rust, Swift, Python, Java, JavaScript (including JSX), TypeScript (including TSX), Markdown, JSON, YAML, HTML, CSS, XML, TOML, and shell scripts (Bash).

## Requirements

- JDK 25 to run Gradle; the build and the bundled runtime use the JetBrains Runtime 25 toolchain, which Gradle downloads automatically when it is not installed. The custom title bar needs JBR
- Homebrew's OpenJDK is not suitable for packaging because it links font libraries from Homebrew into the bundled runtime ([Compose issue #3107](https://github.com/JetBrains/compose-multiplatform/issues/3107))
- git 2.36 or later at runtime (blobs are read through `git cat-file --batch-command`)

## Run from source

```sh
./gradlew :app:run --args=/absolute/path/to/repo
./gradlew :app:run --args=..
./gradlew :app:run --args="/absolute/path/to/repo --base develop"
```

Gradle runs the app with `app/` as its working directory, so a relative path is resolved from there, not from where you ran `./gradlew`. Any directory inside a repository shows the whole repository's diff; `--args=..` opens this repository with its own name in the header, while `--args=./` shows the same diff under the name `app`.

## Build the app

```sh
./gradlew :app:createDistributable
```

The app is created at `app/build/compose/binaries/main/app/kDiffViewer.app`. Copy it to `/Applications`.

## Install the `kdv` command

The app bundles the `kdv` command. Link it onto your `PATH`:

```sh
ln -s /Applications/kDiffViewer.app/Contents/app/resources/kdv /usr/local/bin/kdv
```

If `/usr/local/bin` is not writable by your user, run the command with `sudo`, or link `kdv` into another directory on your `PATH` (for example `~/.local/bin`).

`kdv ./repo` opens a repository in kDiffViewer, and `kdv` with no argument opens the current directory. Bare words are commands (`thread`, `reload`, `windows`, `agent-guide`), so write relative paths as `./repo`. Run `kdv --help` for the full list.

## Choosing the base

The diff shows everything since the branch point plus staged, unstaged, and untracked changes, like a pull request that also includes uncommitted work. The base is the merge-base of `HEAD` and the branch `origin/HEAD` points at (usually the repository's default branch, such as `origin/main`); without `origin/HEAD` it is `HEAD`, which shows only uncommitted changes. The toolbar shows the base next to the current branch.

Pass `--base <ref>` to compare against the merge-base with another ref, for example the parent of a stacked branch or a few commits back:

```sh
kdv ./ --base feature-a-base
kdv ./ --base HEAD~3
kdv ./ --base HEAD
```

`--base HEAD` shows only uncommitted changes.

The base can also be changed from the toolbar: click the base tag to open a popup that lists local and remote branches and accepts a typed revision.

## Opening untrusted repositories

kDiffViewer runs git, and git reads the repository's own `.git/config` and `.gitattributes`, so a directory that was not cloned (a zip, a tarball, a copied `.git`, a nested submodule) can run commands through git's clean filters, exactly as `git status` would. kDiffViewer disables the fsmonitor hook and never runs external diff, textconv or hooks, but it cannot disable filters. Open such directories only after inspecting `.git/config` and `.gitattributes`.

## Review comments

Comment bodies render as GitHub-style Markdown: headings, emphasis, lists, task lists, block quotes, links, and code blocks, with fenced code in the bundled languages syntax-highlighted. Tables and images are not rendered. The comment composer has Write / Preview; press ⌘⇧P to switch between them.

## Selecting and copying

Drag over the diff to select code; double-click selects a word, triple-click a line, and Shift-click extends the selection. Copy with ⌘C, Edit ▸ Copy, or the right-click menu. Only the code text is copied, without line numbers or `+`/`-` markers. Text in comment bodies can be selected and copied the same way. To copy as soon as a selection is made, turn on Copy on select in Settings (off by default).

## Coding agents

Review comments left in the app can be read and answered from the terminal through `kdv thread`. Run `kdv agent-guide` for the instructions to give a coding agent.

A coding agent can wait for your review with `kdv review wait`, which blocks until you send comments from the app. In the composer, Add single comment (⌘⇧↩) sends the comment right away, while Start a review (⌘↩) keeps it pending, hidden from the agent. While comments are pending or an agent is waiting, a bar under the diff sends them with Send to Agent, optionally with a message, or tells the agent you are done with Approve.

## Development

`./gradlew check` runs all tests, and CI runs the same on pushes to develop and on pull requests.

## License

MIT
