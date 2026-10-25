#!/bin/sh
set -eu
if [ $# -lt 1 ] || [ $# -gt 2 ]; then
    echo "usage: $0 <dir> [ext]" >&2
    exit 2
fi
dir="$1"
ext="${2:-txt}"
if [ -e "$dir" ]; then
    echo "error: $dir already exists" >&2
    exit 1
fi
mkdir -p "$dir"
cd "$dir"
git init -q
mkdir small
if [ "$ext" = "kt" ]; then
    awk -v ext="$ext" 'BEGIN { for (f = 1; f <= 2000; f++) { name = sprintf("small/file%04d.%s", f, ext); for (l = 1; l <= 50; l++) printf "val v%d = \"line %d of file %d\"\n", l, l, f > name; close(name) } }'
    awk 'BEGIN { for (l = 1; l <= 50000; l++) printf "val v%d = \"big line %d\"\n", l, l }' > "big.$ext"
else
    awk -v ext="$ext" 'BEGIN { for (f = 1; f <= 2000; f++) { name = sprintf("small/file%04d.%s", f, ext); for (l = 1; l <= 50; l++) print "line " l " of file " f > name; close(name) } }'
    awk 'BEGIN { for (l = 1; l <= 50000; l++) print "big line " l }' > "big.$ext"
fi
git add -A
git -c user.name=kdv -c user.email=kdv@example.com -c commit.gpgsign=false commit -q -m "initial"
if [ "$ext" = "kt" ]; then
    suffix=' + " modified"'
else
    suffix=' modified'
fi
for f in small/*."$ext" "big.$ext"; do
    awk -v suffix="$suffix" 'NR % 5 == 0 { print $0 suffix; next } { print }' "$f" > "$f.tmp"
    mv "$f.tmp" "$f"
done
mkdir untracked
if [ "$ext" = "kt" ]; then
    awk -v ext="$ext" 'BEGIN { for (f = 1; f <= 200; f++) { name = sprintf("untracked/new%03d.%s", f, ext); for (l = 1; l <= 50; l++) printf "val v%d = \"new line %d of file %d\"\n", l, l, f > name; close(name) } }'
else
    awk -v ext="$ext" 'BEGIN { for (f = 1; f <= 200; f++) { name = sprintf("untracked/new%03d.%s", f, ext); for (l = 1; l <= 50; l++) print "new line " l " of file " f > name; close(name) } }'
fi
