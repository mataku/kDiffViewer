# kDiffViewer

kDiffViewer is a macOS desktop viewer for the git diff of a working tree, built with Kotlin Multiplatform and Compose Desktop. It shows a GitHub-like unified diff with a file tree and streams large diffs in as they load.

## Requirements

- JDK 25 to build; packaging uses the Eclipse Temurin 25 toolchain (another non-Homebrew distribution also works if you adjust the toolchain vendor)
- Homebrew's OpenJDK is not suitable for packaging because it links font libraries from Homebrew into the bundled runtime ([Compose issue #3107](https://github.com/JetBrains/compose-multiplatform/issues/3107))
- git 2.36 or later at runtime (blobs are read through `git cat-file --batch-command`)

## Run from source

```sh
./gradlew :app:run --args=/absolute/path/to/repo
```

## Build the app

```sh
./gradlew :app:createDistributable
```

The app is created at `app/build/compose/binaries/main/app/kDiffViewer.app`. Copy it to `/Applications`.

## Install the `kdv` launcher

Copy `scripts/kdv` onto your `PATH`, then run `kdv [path]` to open a repository (defaults to the current directory). Set `KDV_APP` to override the app location (default `/Applications/kDiffViewer.app`).

## Development

`./gradlew check` runs all tests, and CI runs the same on pushes to develop and on pull requests.

## License

MIT
