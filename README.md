# kDiffViewer

kDiffViewer is a macOS desktop viewer for the git diff of a working tree, built with Kotlin Multiplatform and Compose Desktop. It shows a GitHub-like unified diff with a file tree and streams large diffs in as they load.

## Requirements

- JDK 21 to build (the Compose JDK vendor check is disabled in `gradle.properties`, so any JDK 21 including Homebrew OpenJDK works)
- git at runtime

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

## License

MIT
