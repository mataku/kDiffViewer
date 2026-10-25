#!/bin/sh
set -eu
if [ $# -ne 1 ]; then
    echo "usage: $0 <dir>" >&2
    exit 2
fi
dir="$1"
if [ -e "$dir" ]; then
    echo "error: $dir already exists" >&2
    exit 1
fi
mkdir -p "$dir"
cd "$dir"
git init -q
mkdir small
awk 'BEGIN { for (f = 1; f <= 2000; f++) { name = sprintf("small/file%04d.txt", f); for (l = 1; l <= 50; l++) print "line " l " of file " f > name; close(name) } }'
awk 'BEGIN { for (l = 1; l <= 50000; l++) print "big line " l }' > big.txt
git add -A
git -c user.name=kdv -c user.email=kdv@example.com -c commit.gpgsign=false commit -q -m "initial"
for f in small/*.txt big.txt; do
    awk 'NR % 5 == 0 { print $0 " modified"; next } { print }' "$f" > "$f.tmp"
    mv "$f.tmp" "$f"
done
mkdir untracked
awk 'BEGIN { for (f = 1; f <= 200; f++) { name = sprintf("untracked/new%03d.txt", f); for (l = 1; l <= 50; l++) print "new line " l " of file " f > name; close(name) } }'
