#!/bin/bash
# Captures the README screenshots on a macOS CI runner; see .github/workflows/screenshot.yml.
# Usage: bash scripts/ci/screenshot.sh <work dir>
# Expects a built app, Cica and oxipng installed, and a local develop branch. Writes <work dir>/shots.
set -euo pipefail

if [ $# -ne 1 ]; then
  echo "usage: $0 <work dir>" >&2
  exit 2
fi
root="$(cd "$(dirname "$0")/../.." && pwd)"
work="$1"
shots="$work/shots"
sample="$work/cats"
app="$root/app/build/compose/binaries/main/app/kDiffViewer.app"
kdv="$app/Contents/app/resources/kdv"
mkdir -p "$shots"

ui() { swift "$root/scripts/ci/ui.swift" "$@"; }

echo "::group::Register Cica"
ui register "$HOME"/Library/Fonts/Cica-*.ttf
for _ in $(seq 1 15); do
  [ "$(ui font Cica)" = true ] && break
  sleep 2
done
[ "$(ui font Cica)" = true ]
echo "::endgroup::"

echo "::group::Add a 2x virtual display"
clang -fobjc-arc -framework Foundation -framework CoreGraphics "$root/scripts/ci/vdisplay.m" -o "$work/vdisplay"
nohup "$work/vdisplay" > "$work/vdisplay.log" 2>&1 &
sleep 8
cat "$work/vdisplay.log"
ui displays
# Fields: id, main, x, y, width, height, pixel width, pixel height.
ui displays | awk '$2 == "main" { ok = ($7 == $5 * 2) } END { exit !ok }'
echo "::endgroup::"

echo "::group::Open the sample"
settings="$HOME/Library/Application Support/kDiffViewer"
mkdir -p "$settings"
printf '%s\n' '{"appearance":"Dim","fontFamily":"Cica"}' > "$settings/settings.json"
printf '%s\n' '{"mainWidth":1360,"mainHeight":880,"treeWidth":260}' > "$settings/window-state.json"
defaults write -g AppleInterfaceStyle -string Dark
"$root/scripts/generate-screenshot-diff.sh" "$sample"
KDV_APP="$app" "$kdv" "$sample" --base develop
for _ in $(seq 1 60); do
  "$kdv" windows | grep -qv '^No windows' && break
  sleep 2
done
"$kdv" windows
sleep 5
echo "::endgroup::"

echo "::group::Capture the diff"
read -r id x y w h < <(ui window)
echo "window $id at $x,$y size ${w}x$h"
screencapture -x -l "$id" "$shots/dim-screen.png"
echo "::endgroup::"

echo "::group::Comment and capture the review"
# Offsets inside the 1360x880 window: the add-comment button of new line 115, the review bar's field and Send to Agent.
ui move $((x + 700)) $((y + 388))
sleep 0.5
ui move $((x + 384)) $((y + 388))
sleep 0.5
ui click $((x + 384)) $((y + 388))
sleep 1
printf '%s' 'Mochi as the default name feels arbitrary. Can we take it from `CatNames.suggest` instead?' | pbcopy
ui key 9 cmd
sleep 0.5
ui key 36 cmd
sleep 1
ui click $((x + 300)) $((y + h - 20))
sleep 0.5
printf '%s' 'What is this branch for? Give me a short overview before I keep reading.' | pbcopy
ui key 9 cmd
sleep 0.5
ui click $((x + w - 60)) $((y + h - 20))
sleep 1
(
  cd "$sample"
  "$kdv" thread list
  "$kdv" thread reply t2 'This branch adds `CatNames`, which builds a title-and-name pair from a seed and describes a cat by age and favorite spot, plus its tests. It also enlarges the default window and adds a README section. Start with `CatNames.kt`; the `Main.kt` change is only the window size and a greeting.'
  "$kdv" thread reply t1 'Good point. I will call `CatNames.suggest(0)` there instead of hard-coding Mochi.'
)
sleep 2
screencapture -x -l "$id" "$shots/agent-review.png"
echo "::endgroup::"

echo "::group::Compress"
oxipng -o 6 --strip safe "$shots"/*.png
echo "::endgroup::"
