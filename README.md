# kDiffViewer

kDiffViewer is a macOS desktop viewer for the git diff of a working tree, built with Kotlin Multiplatform and Compose Desktop. It shows a GitHub-like unified diff with a file tree and streams large diffs in as they load.

Syntax highlighting covers Kotlin, Ruby, Go, Rust, Swift, Python, Java, JavaScript, TypeScript, and Markdown.

## Requirements

- JDK 25 to run Gradle; the build and the bundled runtime use the JetBrains Runtime 25 toolchain, which Gradle downloads automatically when it is not installed. The custom title bar needs JBR
- Homebrew's OpenJDK is not suitable for packaging because it links font libraries from Homebrew into the bundled runtime ([Compose issue #3107](https://github.com/JetBrains/compose-multiplatform/issues/3107))
- git 2.36 or later at runtime (blobs are read through `git cat-file --batch-command`)

## Run from source

```sh
./gradlew :app:run --args=/absolute/path/to/repo
./gradlew :app:run --args=..
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

## Coding agents

Review comments left in the app can be read and answered from the terminal through `kdv thread`. Run `kdv agent-guide` for the instructions to give a coding agent.

## Development

`./gradlew check` runs all tests, and CI runs the same on pushes to develop and on pull requests.

## License

MIT
