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
  src/main/java/com/rumilance/practice/combat/CombatSyncListener.java
  src/main/java/com/rumilance/practice/listener/AdvancementBlockListener.java
  src/main/java/com/rumilance/practice/listener/SessionBootstrapListener.java
  src/main/java/com/rumilance/practice/lobby/LobbyListener.java
  # packetbot 全体 (PacketBot / PacketBotBody / PacketBotFactory / BotNames / FakePlayerConnection)
  # 1.76.28+ の BOT 多体化 / GUI 刷新 / PRO ランクで追加・変更したファイル。
  # delivery jar (1.76.27) にはこれらの新クラス/新定数が無いので必ず同コンパイルする。
  src/main/java/com/rumilance/practice/packetbot
  src/main/java/com/rumilance/practice/util/RealPlayers.java
  src/main/java/com/rumilance/practice/gui/MenuTile.java
  src/main/java/com/rumilance/practice/gui/menus/GameMenuGui.java
  src/main/java/com/rumilance/practice/gui/menus/BattleMenuGui.java
  src/main/java/com/rumilance/practice/gui/menus/PracticeBotSelectGui.java
  src/main/java/com/rumilance/practice/gui/menus/PlayersGui.java
  src/main/java/com/rumilance/practice/scoreboard/ScoreboardService.java
  src/main/java/com/rumilance/practice/rank/PlayerRank.java
  src/main/java/com/rumilance/practice/rank/RankService.java
  src/main/java/com/rumilance/practice/font/IconFontService.java
  src/main/java/com/rumilance/practice/font/RankIconNameTags.java
  src/main/java/com/rumilance/practice/command/SetRankCommand.java
  src/main/java/com/rumilance/practice/command/RankIconCommand.java
  src/main/java/com/rumilance/practice/gui/menus/AdminPlayerDataGui.java
  src/main/java/com/rumilance/practice/resourcepack/ResourcePackService.java
  src/main/java/com/rumilance/practice/bootstrap/FeatureBootstrap.java
  src/main/java/com/rumilance/practice/practice/PracticeService.java
  # キットの定義/適用/管理コマンド (kits.yml のスキーマや /kit の挙動を触るので必須。
  # ここを忘れると「ソースは直っているのに挙動が変わらない」= delivery jar の
  # 古いクラスが使われ続ける)。
  src/main/java/com/rumilance/practice/kit/KitService.java
  src/main/java/com/rumilance/practice/kit/KitLoadout.java
  src/main/java/com/rumilance/practice/model/KitItemEntry.java
  src/main/java/com/rumilance/practice/command/ArenaKitAdminCommand.java
  # オリジナルキット設定機能 (設定画面/合成キット/看板保存/体サイズ VC)。
  # ここを忘れると「ソースは直っているのに挙動が変わらない」= delivery jar の古いクラスが使われ続ける。
  src/main/java/com/rumilance/practice/model/OriginalKitSettings.java
  src/main/java/com/rumilance/practice/model/OriginalKitSnapshot.java
  src/main/java/com/rumilance/practice/database/SchemaMigrator.java
  src/main/java/com/rumilance/practice/database/repository/OriginalKitRepository.java
  src/main/java/com/rumilance/practice/originalkit/OriginalKitService.java
  src/main/java/com/rumilance/practice/originalkit/OriginalKitRoomService.java
  src/main/java/com/rumilance/practice/originalkit/OriginalKitRoomListener.java
  src/main/java/com/rumilance/practice/gui/GuiType.java
  src/main/java/com/rumilance/practice/gui/GuiListener.java
  src/main/java/com/rumilance/practice/gui/menus/OriginalKitGui.java
  src/main/java/com/rumilance/practice/gui/menus/OriginalKitSlotMenuGui.java
  src/main/java/com/rumilance/practice/gui/menus/OriginalKitSettingsGui.java
  src/main/java/com/rumilance/practice/gui/menus/TeamKitSelectGui.java
  src/main/java/com/rumilance/practice/item/SaveSignItem.java
  src/main/java/com/rumilance/practice/match/MatchService.java
  src/main/java/com/rumilance/practice/match/MatchListener.java
  src/main/java/com/rumilance/practice/combat/CombatSyncListener.java
  src/main/java/com/rumilance/practice/team/TeamService.java
  src/main/java/com/rumilance/practice/team/OriginalKitRef.java
  src/main/java/com/rumilance/practice/util/PlayerVitals.java
  # @NotNull/@Nullable の最小スタブ (annotations jar が無い環境用)
  tools/parity-runner/stubs/org/jetbrains/annotations
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
# リソース重ね書き: config.yml / lang/*.yml / quantum-pack/ を現在のソースに合わせる
# (plugin.yml は gradle の ${version} 置換済みが無いので jar 内のもので良し)。
chmod -R u+w /tmp/plugbuild/res 2>/dev/null || true   # tar 由来の read-only ディレクトリに負けない
rm -rf /tmp/plugbuild/res
mkdir -p /tmp/plugbuild/res/lang
cp "$ROOT/src/main/resources/config.yml" /tmp/plugbuild/res/
cp "$ROOT/src/main/resources/quantum.yml" /tmp/plugbuild/res/
cp "$ROOT"/src/main/resources/lang/*.yml /tmp/plugbuild/res/lang/
# jar 内蔵マップパック (QuantumRuntime.extractBundledPack が起動時に展開する)
if [ -d "$ROOT/src/main/resources/quantum-pack" ]; then
  cp -r "$ROOT/src/main/resources/quantum-pack" /tmp/plugbuild/res/
fi
( cd /tmp/plugbuild/res && "$JDK/bin/jar" uf "$OUT" . )
echo "built $OUT"
sha256sum "$OUT"
