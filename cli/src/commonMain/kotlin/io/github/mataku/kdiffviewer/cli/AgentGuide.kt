package io.github.mataku.kdiffviewer.cli

fun agentGuide(): String = """
kdv agent guide

kdv talks to the kDiffViewer window that shows the current repository.
Run it when the user says they left review comments in kDiffViewer.
The user hands off in chat: they do not notify you any other way.

Workflow

1. List the open threads as JSON:

     kdv thread list --json

2. Fix the code for each thread.
   Add --all to include resolved threads when you need the history.

3. Reply to the thread to say what you did.
   Pass the body as a single quoted argument, or use - to read it from stdin for multi-line bodies:

     kdv thread reply <id> - <<'EOF'
     Renamed the helper and added a test.
     See the new case in FooTest.
     EOF

4. Resolve the thread once it is handled:

     kdv thread resolve <id>

5. Run reload at the end:

     kdv reload

   It re-reads the diff from disk, re-anchors the remaining comments to the new line numbers, and prints how many files the diff has and how many threads are outdated.

Opening a window

Open a whole branch for review with kdv ./; the diff starts at the merge-base with origin/HEAD.
For a branch stacked on another, pass the parent: kdv ./ --base <parent>.

Options

  --repo <path>   Use the window for that repository instead of the current directory.
  --pid <pid>     Use a specific window; needed when several windows show the same repository.
  --author <name> Author name on a reply (default: agent).

JSON shape

kdv thread list --json prints {"threads": [ThreadDto, ...]}.
ThreadDto is:

  {
    "id": "t1",
    "path": "src/Foo.kt",
    "side": "new",
    "startLine": 10,
    "endLine": 12,
    "snapshot": ["the", "commented", "lines"],
    "resolved": false,
    "outdated": false,
    "comments": [
      {"id": "c1", "author": {"kind": "user"}, "body": "text", "createdAt": "2026-10-26T00:00:00Z"}
    ]
  }

side is "old" or "new".
File-level threads have no side, startLine, or endLine, and an empty snapshot.
author.kind is "user" or "agent"; agents also carry a name.

Outdated threads

outdated is true when the code the comment pointed at has changed or disappeared since the comment was written.
snapshot holds the lines as they were when the user commented, so use it to find the new location of the code.
Read the current file before acting on an outdated thread, and ask the user when the intent is unclear.

Exit codes: 0 success, 1 runtime failure (no window, unknown thread id, load failed), 2 usage error.
""".trimIndent() + "\n"
