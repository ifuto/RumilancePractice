#!/usr/bin/env bash
# Paper 側プラグイン(NARENA + HeroBot 移植 + Quantum ランタイム)のビルド。
#
#   使い方:  tools/parity-runner/build_plugin.sh [出力先.jar]
#
# 方式: 納品済みプラグイン jar を「土台」にし、この repо で書き換えたソースだけを
# そのクラスパス上でコンパイルして上書きする(jar uf)。Gradle を使わないのは、
# サンドボックスに依存解決済みの Gradle/ネットワークが無いため。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
JDK="${JDK:-/tmp/jdk21}"
DELIVERY="${DELIVERY:-/tmp/dl/RumilancePractice-1.76.27.jar}"
PAPER_JAR="${PAPER_JAR:-/tmp/papersrv/paper-run/versions/1.21.11/paper-1.21.11.jar}"
LIBS_DIR="${LIBS_DIR:-/tmp/papersrv/paper-run/libraries}"
SERVER_LIBS_DIR="${SERVER_LIBS_DIR:-/tmp/papersrv/paper-run/plugins/.paper-remapped/libraries}"
OUT="${1:-/tmp/plugbuild/plugin.jar}"

# 書き換えたソース(これだけをコンパイルする)
SOURCES=(
  src/main/java/com/rumilance/practice/herobot
  src/main/java/com/rumilance/practice/quantum
  src/main/java/com/rumilance/practice/combat/CrystalSelfBlastListener.java
  src/main/java/com/rumilance/practice/combat/ExplosionSourceTracker.java
  src/main/java/com/rumilance/practice/listener/SessionBootstrapListener.java
  src/main/java/com/rumilance/practice/lobby/LobbyListener.java
  src/main/java/com/rumilance/practice/packetbot/PacketBot.java
  src/main/java/com/rumilance/practice/bootstrap/FeatureBootstrap.java
)

CP="$DELIVERY:$PAPER_JAR:$(find "$LIBS_DIR" -name '*.jar' 2>/dev/null | tr '\n' ':')$(find "$SERVER_LIBS_DIR" -name '*.jar' 2>/dev/null | tr '\n' ':')"

rm -rf /tmp/plugbuild/one
mkdir -p /tmp/plugbuild/one "$(dirname "$OUT")"

FILES=()
for s in "${SOURCES[@]}"; do
  if [ -d "$ROOT/$s" ]; then
    while IFS= read -r f; do FILES+=("$f"); done < <(find "$ROOT/$s" -name '*.java')
  else
    FILES+=("$ROOT/$s")
  fi
done
echo "compiling ${#FILES[@]} source file(s) ..."
"$JDK/bin/javac" -nowarn -proc:none -d /tmp/plugbuild/one -cp "$CP" "${FILES[@]}"

echo "classes: $(find /tmp/plugbuild/one -name '*.class' | wc -l)"
cp "$DELIVERY" "$OUT"
( cd /tmp/plugbuild/one && "$JDK/bin/jar" uf "$OUT" . )
echo "built $OUT"
sha256sum "$OUT"
