#!/bin/sh
set -eu
if [ $# -ne 1 ]; then
    echo "usage: $0 <dir>" >&2
    exit 2
fi
branch=feature/cat-facts
base=develop
root="$(git -C "$(dirname "$0")" rev-parse --show-toplevel)"
if [ -e "$1" ]; then
    echo "error: $1 already exists" >&2
    exit 1
fi
parent="$(dirname "$1")"
if [ ! -d "$parent" ]; then
    echo "error: $parent does not exist" >&2
    exit 1
fi
dir="$(cd "$parent" && pwd)/$(basename "$1")"
if git -C "$root" show-ref --verify --quiet "refs/heads/$branch"; then
    echo "error: branch $branch already exists" >&2
    exit 1
fi
git -C "$root" worktree add -q -b "$branch" "$dir" "$base"
cd "$dir"
commit() {
    git -c user.name=kdv -c user.email=kdv@example.com -c commit.gpgsign=false -c core.hooksPath=/dev/null commit -q -m "$1"
}

main=app/src/jvmMain/kotlin/io/github/mataku/kdiffviewer/app/Main.kt
model=core/src/commonMain/kotlin/io/github/mataku/kdiffviewer/core/model
test=core/src/commonTest/kotlin/io/github/mataku/kdiffviewer/core/model
for f in README.md "$main"; do
    if [ ! -f "$f" ]; then
        echo "error: $f is missing on $base" >&2
        exit 1
    fi
done
if ! grep -q '^## License$' README.md || ! grep -q 'DpSize(800.dp, 600.dp)' "$main"; then
    echo "error: README.md or $main no longer has the lines this script edits" >&2
    exit 1
fi

awk '/^## License$/ { while ((getline line < "/dev/stdin") > 0) print line; print "" } { print }' README.md > README.md.tmp <<'EOF'
## Cats

kDiffViewer has no opinion about cats, but its maintainers do. This section collects what they have learned while reviewing diffs with a cat nearby.

### Naming a cat

A good cat name is short enough to call across the room and soft enough to say at three in the morning. Two syllables work best; Mochi, Biscuit, and Noodle all pass this test.

### When the cat sits on the keyboard

- Save your work first; cats do not wait for you to finish a sentence.
- Offer a warm laptop charger or a folded towel as a replacement seat.
- Do not review a diff the cat has typed into. It will not pass CI.

### Feeding schedule

Cats prefer meals at the same time every day, and they will remind you a few minutes early, every day, without fail.
EOF
mv README.md.tmp README.md

mkdir -p "$model"
cat > "$model/CatNames.kt" <<'EOF'
package io.github.mataku.kdiffviewer.core.model

data class Cat(val name: String, val ageInMonths: Int, val favoriteSpot: String)

object CatNames {
  private val titles = listOf("Sir", "Lady", "Captain", "Professor")
  private val names = listOf("Mochi", "Biscuit", "Pixel", "Noodle", "Pepper")

  fun suggest(seed: Int): String {
    val index = seed.mod(titles.size * names.size)
    return "${titles[index % titles.size]} ${names[index / titles.size]}"
  }

  fun isKitten(cat: Cat): Boolean = cat.ageInMonths < 12
}
EOF
git add README.md "$model/CatNames.kt"
commit "Add a Cats section and cat name suggestions"

cat > "$model/CatNames.kt" <<'EOF'
package io.github.mataku.kdiffviewer.core.model

data class Cat(val name: String, val ageInMonths: Int, val favoriteSpot: String)

object CatNames {
  private val titles = listOf("Sir", "Lady", "Captain", "Professor")
  private val names = listOf("Mochi", "Biscuit", "Pixel", "Noodle", "Pepper", "Tofu")

  fun suggest(seed: Int): String {
    val index = seed.mod(titles.size * names.size)
    return "${titles[index % titles.size]} ${names[index / titles.size]}"
  }

  fun isKitten(cat: Cat): Boolean = cat.ageInMonths < 12

  fun describe(cat: Cat): String = when {
    isKitten(cat) -> "${cat.name} is a kitten who naps on the ${cat.favoriteSpot}."
    cat.favoriteSpot == "keyboard" -> "${cat.name} reviews every diff personally."
    else -> "${cat.name} is ${cat.ageInMonths / 12} and guards the ${cat.favoriteSpot}."
  }
}
EOF
git add "$model/CatNames.kt"

sed 's/DpSize(800\.dp, 600\.dp)/DpSize(960.dp, 640.dp)/' "$main" > "$main.tmp"
mv "$main.tmp" "$main"
cat >> "$main" <<'EOF'

private fun hello(name: String = "Mochi") {
  println("Hello, $name! Please get off the keyboard.")
}
EOF

mkdir -p "$test"
cat > "$test/CatNamesTest.kt" <<'EOF'
package io.github.mataku.kdiffviewer.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CatNamesTest {
  @Test
  fun suggestCombinesATitleAndAName() {
    assertEquals("Sir Mochi", CatNames.suggest(0))
  }

  @Test
  fun aCatUnderOneYearIsAKitten() {
    assertTrue(CatNames.isKitten(Cat("Pixel", ageInMonths = 4, favoriteSpot = "sofa")))
  }

  @Test
  fun aKeyboardCatReviewsDiffs() {
    val cat = Cat("Noodle", ageInMonths = 36, favoriteSpot = "keyboard")
    assertEquals("Noodle reviews every diff personally.", CatNames.describe(cat))
  }
}
EOF

echo "Created $dir on $branch. Open it with: kdv $dir --base $base"
echo "Remove it with: git worktree remove --force $dir && git branch -D $branch"
