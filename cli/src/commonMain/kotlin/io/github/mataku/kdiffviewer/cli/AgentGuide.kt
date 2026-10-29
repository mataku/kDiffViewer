package io.github.mataku.kdiffviewer.cli

fun agentGuide(): String = """
kdv agent guide

kdv talks to the kDiffViewer window that shows the current repository.
The user reviews your changes there and sends comments to you; kdv review wait delivers them.

Review loop

1. When a change is ready, open it for review and tell the user:

     kdv ./

2. Wait for the user's comments:

     kdv review wait --json

   It blocks until the user sends something from the app, then prints it and exits 0.
   If your harness can run a command in the background and notify you when it exits, run it that way and keep working or stay idle until it finishes.
   Otherwise run it in a loop with --timeout below your harness's limit for a foreground command (for example --timeout 540 for a 10-minute limit).
   Exit code 3 means the timeout passed and nothing was sent yet: run it again.

3. Handle every thread in the result: fix the code, then reply and resolve.
   Pass a reply body as a single quoted argument, or use - to read it from stdin for multi-line bodies:

     kdv thread reply <id> - <<'EOF'
     Renamed the helper and added a test.
     See the new case in FooTest.
     EOF

     kdv thread resolve <id>

4. Reload the diff so the user sees the new code next to your replies:

     kdv reload

   It re-reads the diff from disk, re-anchors the remaining comments to the new line numbers, and prints how many files the diff has and how many threads are outdated.

5. Go back to step 2.
   Stop when a review in the result has "state": "approved"; handle any threads that came with it first.

Without waiting

If the user tells you in chat that they left comments, list the open threads instead:

  kdv thread list --json

Add --all to include resolved threads when you need the history.

Opening a window

Open a whole branch for review with kdv ./; the diff starts at the merge-base with origin/HEAD.
For a branch stacked on another, pass the parent: kdv ./ --base <parent>.

Options

  --repo <path>    Use the window for that repository instead of the current directory.
  --pid <pid>      Use a specific window; needed when several windows show the same repository.
                   kdv windows lists the running windows with their pids.
  --author <name>  Author name on a reply (default: agent).
  --timeout <s>    Seconds kdv review wait blocks before exiting 3 (1 to 86400; default: no limit).

JSON shapes

kdv review wait --json prints:

  {
    "timedOut": false,
    "reviews": [
      {"id": "r2", "state": "comment", "summary": null, "submittedAt": "2026-10-28T00:00:00Z", "commentIds": ["c7"]}
    ],
    "threads": [ThreadDto, ...]
  }

state is "comment" or "approved"; summary is the optional message the user wrote to you when sending.
commentIds are the comments this review sent; threads holds every thread that contains one of them, with all of its comments for context.
Several reviews arrive together when the user sent more than one while you were busy.

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
Comments the user saved as pending (part of a review not yet sent) are never shown to you; a thread with only pending comments does not exist for kdv until the user sends it.

Outdated threads

outdated is true when the code the comment pointed at has changed or disappeared since the comment was written.
snapshot holds the lines as they were when the user commented, so use it to find the new location of the code.
Read the current file before acting on an outdated thread, and ask the user when the intent is unclear.

Exit codes: 0 success, 1 runtime failure (no window, window closed, unknown thread id, load failed, another kdv review wait took over), 2 usage error, 3 kdv review wait timed out.

Run kdv --help for every command and option.
""".trimIndent() + "\n"
