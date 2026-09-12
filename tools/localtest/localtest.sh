#!/usr/bin/env bash
# Local Bukkit-free test loop: stub-JUnit + ECJ (jdk4py JRE). Mirrors `./gradlew test`
# for every test class that does NOT depend (transitively) on Bukkit/Paper classes; CI
# remains the authority for the full suite (see .cursor/rules/always-build.mdc).
# Usage: tools/localtest/localtest.sh [--keep]
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$ROOT/build/localtest"
ECJ_JAR="$ROOT/tools/localtest/ecj.jar"

if ! command -v python3 >/dev/null || ! python3 -c 'import jdk4py' 2>/dev/null; then
  echo "jdk4py missing: pip3 install --break-system-packages jdk4py" >&2; exit 2
fi
JH="$(python3 -c 'import jdk4py; print(jdk4py.JAVA_HOME)')"
JAVA="$JH/bin/java"
[ -x "$JAVA" ] || { echo "no java at $JAVA" >&2; exit 2; }

rm -rf "$OUT"; mkdir -p "$OUT/classes" "$OUT/work"

FORBIDDEN='org\.bukkit|net\.kyori|com\.sk89q|org\.spigot|io\.papermc|com\.mojang'

# filter_imports <prefix> -> writes <prefix>-sources.txt of files not importing forbidden APIs,
# then iteratively drops files that still fail to compile (Bukkit-coupled deps).
filter_imports() {
  local in_file="$1" out_list="$2"
  : > "$out_list"
  while IFS= read -r f; do
    if grep -qE "^import .*($FORBIDDEN)" "$f"; then :; else echo "$f" >> "$out_list"; fi
  done < "$in_file"
}

ecj_compile() { # $1 = list ; $2 = extra classpath ; echoes raw ecj output
  "$JAVA" -jar "$ECJ_JAR" -21 -nowarn -maxProblems 200000 \
      ${2:+-cp "$2"} -d "$OUT/classes" @"$1" 2>&1 || true
}

compile_with_drops() { # $1 = list ; $2 = classpath ; $3 = label ; returns final list on stdout
  local list="$1" cp="$2" label="$3" pass bad tmp
  for pass in $(seq 1 40); do
    local msg out_file="$OUT/work/last-$label.log"
    msg="$(ecj_compile "$list" "$cp")"
    printf '%s\n' "$msg" > "$out_file"
    bad=$(printf '%s\n' "$msg" | (grep -oE 'ERROR in [^ ]+\.java' || true) | awk '{print $3}' | sort -u)
    if [ -z "$bad" ]; then
      grep -q ' problems ' "$out_file" || true
      echo "local-test: $label compiled clean (pass $pass)"; cat "$list"; return 0
    fi
    tmp="$OUT/work/keep-$label.tmp"; : > "$tmp"
    while IFS= read -r f; do
      echo "$bad" | grep -qxF "$f" || echo "$f" >> "$tmp"
    done < "$list"
    mv "$tmp" "$list"
  done
  echo "local-test: $label exhaustively pruned (see $out_file)"; cat "$list"
}

find "$ROOT/src/main/java" -name '*.java' ! -path '*/localtest/*' | sort > "$OUT/work/main-all.txt"
find "$ROOT/src/test/java" -name '*.java' | sort > "$OUT/work/test-all.txt"
find tools/localtest/stubs -name '*.java' | sort > "$OUT/work/stubs.txt"

filter_imports "$OUT/work/main-all.txt" "$OUT/work/main-prefilter.txt"
filter_imports "$OUT/work/test-all.txt" "$OUT/work/test-prefilter.txt"
PRE_M=$(($(wc -l < "$OUT/work/main-all.txt") - $(wc -l < "$OUT/work/main-prefilter.txt")))
PRE_T=$(($(wc -l < "$OUT/work/test-all.txt") - $(wc -l < "$OUT/work/test-prefilter.txt")))

cat "$OUT/work/stubs.txt" "$OUT/work/main-prefilter.txt" > "$OUT/work/pack1.txt"
compile_with_drops "$OUT/work/pack1.txt" "" "main" > "$OUT/work/main-final.txt"
MAIN_N=$(($(wc -l < "$OUT/work/main-final.txt") - $(wc -l < "$OUT/work/stubs.txt")))
echo "local-test: main kept=$MAIN_N (bukkit-import filter: $PRE_M, dep-drop: $(( $(wc -l < "$OUT/work/main-all.txt") - PRE_M - MAIN_N )))"

cp "$OUT/work/test-prefilter.txt" "$OUT/work/test-list.txt"
compile_with_drops "$OUT/work/test-list.txt" "$OUT/classes" "tests" > "$OUT/work/test-final.txt"
TEST_N=$(wc -l < "$OUT/work/test-final.txt")
echo "local-test: tests kept=$TEST_N (bukkit-import filter: $PRE_T, dep-drop: $(( $(wc -l < "$OUT/work/test-all.txt") - PRE_T - TEST_N )))"

echo tools/localtest/runner/LocalTestRunner.java > "$OUT/work/runner.txt"
ecj_compile "$OUT/work/runner.txt" "$OUT/classes" > /dev/null

# Resource files live on the classpath too (lang yml, config defaults, plugin.yml).
cp -R "$ROOT/src/main/resources/." "$OUT/classes/" 2>/dev/null || true

# Guard: resource-pack.sha1 in the bundled config MUST equal the shipped zip's SHA-1 —
# a mismatch makes every client reject the pack (FAILED_DOWNLOAD) and the previously-shipped
# build shipped exactly that silent breakage (stale hash after a pack rebuild).
PACK_ZIP="$ROOT/dist/RumilanceResourcePack.zip"
PACK_CFG="$ROOT/src/main/resources/config.yml"
if [ -f "$PACK_ZIP" ] && [ -f "$PACK_CFG" ]; then
  actual=$(sha1sum "$PACK_ZIP" | awk '{print $1}')
  cfg_sha=$(sed -n 's/^  sha1: *"\([0-9a-fA-F]*\)".*/\1/p' "$PACK_CFG" | head -1)
  if [ -n "$cfg_sha" ] && [ "$actual" != "$cfg_sha" ]; then
    echo "local-test: FAIL — resource-pack.sha1 (config.yml: $cfg_sha) != dist zip ($actual)" >&2
    exit 1
  fi
  echo "local-test: resource-pack.sha1 matches dist zip ($actual)"
fi

"$JAVA" -ea -cp "$OUT/classes" LocalTestRunner "$OUT/classes"
RC=$?
echo "local-test: exit=$RC"
[ "${1:-}" = "--keep" ] || rm -rf "$OUT/work"
exit $RC
