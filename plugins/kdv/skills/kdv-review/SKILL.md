---
name: kdv-review
description: Run the kDiffViewer review loop through the kdv command. Use when the user asks to review changes in kDiffViewer or with kdv, to wait for their review, or to handle the review comments they left in kDiffViewer. Do not start it on your own when a change is ready unless the user asked for a kDiffViewer review. Not for GitHub pull request reviews or code reviews that do not involve kDiffViewer.
---

# kDiffViewer review loop

Run `kdv agent-guide` first and follow its output for the rest of the task. It is the authoritative reference for kdv and matches the installed version, so prefer it over anything you remember about kdv.

If `kdv` is not found, do not search for it. Tell the user to install kDiffViewer and link `kdv` onto their PATH as described in https://github.com/mataku/kDiffViewer#install, and stop.
