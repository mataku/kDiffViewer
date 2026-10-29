---
name: kdv-review
description: Run the kDiffViewer review loop through the kdv command. Use when the user asks to review changes in kDiffViewer or with kdv, to wait for their review, or to handle the review comments they left in kDiffViewer, and when a change is ready for the user to review in kDiffViewer. Not for GitHub pull request reviews or code reviews that do not involve kDiffViewer.
allowed-tools: Bash(kdv *)
---

# kDiffViewer review loop

Run `kdv agent-guide` first and follow its output for the rest of the task. It is the authoritative reference for kdv and matches the installed version, so prefer it over anything you remember about kdv.

If `kdv` is not found, do not search for it. Tell the user to install kDiffViewer and link `kdv` onto their PATH as described in https://github.com/mataku/kDiffViewer#install, and stop.

If `kdv agent-guide` exits with status 2, the installed kdv predates the guide. Tell the user to update kDiffViewer, and stop.
