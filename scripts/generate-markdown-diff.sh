#!/bin/sh
set -eu
if [ $# -ne 1 ]; then
    echo "usage: $0 <dir>" >&2
    exit 2
fi
if [ -e "$1" ]; then
    echo "error: $1 already exists" >&2
    exit 1
fi
mkdir -p "$1/docs" "$1/src"
cd "$1"
git init -q -b main
commit() {
    git -c user.name=kdv -c user.email=kdv@example.com -c commit.gpgsign=false -c core.hooksPath=/dev/null commit -q -m "$1"
}

filler() {
    i=1
    while [ "$i" -le "$2" ]; do
        printf 'Paragraph %s of the %s section, with *emphasis*, `code`, and a [link](https://example.com).\n\n' "$i" "$1"
        i=$((i + 1))
    done
}

long_paragraph() {
    i=1
    while [ "$i" -le 12 ]; do
        if [ "$i" -eq "$1" ]; then
            echo "Long paragraph line $i, $2."
        else
            echo "Long paragraph line $i, which stays the same."
        fi
        i=$((i + 1))
    done
}

write_readme() {
    {
        echo "# Sample project"
        echo
        filler intro 12
        echo "## Features"
        echo
        echo "- Fast startup"
        echo "- Small footprint"
        echo "  - Nested detail"
        if [ "$1" = new ]; then
            echo "- Rendered Markdown in reviews"
        fi
        echo "- Plain text configuration"
        echo
        echo "1. First step"
        echo "2. Second step"
        echo
        echo "- [x] Done task"
        echo "- [ ] Open task"
        echo
        if [ "$1" = new ]; then
            echo "This paragraph was edited to read differently."
        else
            echo "This paragraph will be edited."
        fi
        echo
        filler middle 12
        if [ "$1" = old ]; then
            echo "This whole paragraph is deleted in the new version."
            echo "It spans two lines."
            echo
        fi
        echo "> A quote that stays."
        echo ">"
        if [ "$1" = new ]; then
            echo "> A quote line that changed."
        else
            echo "> A quote line that will change."
        fi
        echo
        echo '```kotlin'
        echo 'fun main() {'
        if [ "$1" = new ]; then
            echo '  println("hello, reviewer")'
        else
            echo '  println("hello")'
        fi
        echo '}'
        echo '```'
        echo
        echo "| Name | Value |"
        echo "|------|-------|"
        echo "| a    | 1     |"
        if [ "$1" = new ]; then
            echo "| b    | 2     |"
        fi
        echo
        if [ "$1" = new ]; then
            echo "<!-- a hidden note a reviewer should still see -->"
            echo
        fi
        if [ "$1" = new ]; then
            long_paragraph 6 "which changed in the middle"
        else
            long_paragraph 6 "which stays the same"
        fi
        echo
        filler closing 8
    } > README.md
}

write_readme old
printf '# Guide\n\nThe first guide paragraph.\n' > docs/guide.md
printf 'fun main() {\n  println("old")\n}\n' > src/App.kt
git add .
commit "Initial commit"
write_readme new
printf 'fun main() {\n  println("new")\n}\n' > src/App.kt
printf '# Guide\n\nThe first guide paragraph.\n\n## Staged section\n\nA staged paragraph.\n' > docs/guide.md
git add docs/guide.md
printf '# New document\n\nAn untracked file renders every block.\n\n- One\n- Two\n' > docs/new.md
