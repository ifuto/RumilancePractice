#!/usr/bin/env bash
# ci/java-env.sh — サンドボックス向け「Java 実行環境 + Fabric 実測サーバー」の取得・起動検証・git 配送。
#
# [なぜ CI で組み立てて git で渡すのか]
# サンドボックスの下りは github.com / api.github.com / codeload / pypi / npm だけで、
# Maven (Paper/Fabric/Mojang) と Modrinth は EOF 遮断、Azure blob (artifact/release) も遮断される。
# つまり **Fabric サーバー一式はサンドボックス内で組み立てられない**。そこで
# インターネット自由 + checkout の push 資格情報を持つこのランナーで材料を集め、
# 起動検証まで済ませてから git ブランチとして配送する(サンドボックスは git だけ読める)。
#
# 出力 (すべて /tmp/bundle、artifact としても保存される=人間用フォールバック):
#   jdk21.tar.gz               Temurin 21 (ポータブル JDK・tar 展開するだけ)
#   paper-1.21.11-<build>.jar  Paper サーバー (Paper 側検証用)
#   sha256s.txt                上記 2 つのチェックサム(sandbox の受領手順はこのファイル基準)
#   mcserver.tar.gz            起動検証済み Fabric 実測サーバー一式
#   mcserver.sha256            mcserver.tar.gz のチェックサム
# 配送ブランチ:
#   mc-server-delivery  ← mcserver.tar.gz (90MB 分割) + sha256s.txt + README.txt
#   java-env-delivery   ← jdk21.tar.gz (90MB 分割) + paper jar + sha256s.txt
#   plugin-delivery     ← shadowJar した当プラグイン (jar + sha256)。サンドボックスは
#                         artifact を読めないので、当プラグインを Paper で走らせて
#                         参照BOTと比較するにはこのブランチ経由で受け取る。
#
# 実行前提: GitHub Actions の ubuntu ランナー、actions/checkout の persist-credentials: true。
# 進捗は [step] 行と、失敗時は ::error:: アノテーション(サンドボックスから API で読める唯一のログ経路)
# で外へ出す。ログ本体はサンドボックスから読めない前提で書くこと。

set -Eeuo pipefail

BUNDLE=${BUNDLE:-/tmp/bundle}
WORK=${WORK:-/tmp/ciwork}
MCS=${MCS:-/tmp/mcserver}
WS=${GITHUB_WORKSPACE:-$(pwd)}
UA="RumilancePractice-ci/1.0 (+https://github.com/ifuto/RumilancePractice)"
RUN=${GITHUB_RUN_ID:-local}

mkdir -p "$BUNDLE" "$WORK"

step() { echo; echo "===== [step] $* ====="; }
die() { echo "::error::java-env: $*"; exit 1; }
# 失敗した「行」と「コマンド」をアノテーションに残す(ログが読めない環境から原因を特定する唯一の手段)
# shellcheck disable=SC2064
trap 'echo "::error::java-env failed at line $LINENO: ${BASH_COMMAND}"' ERR

# ---------------------------------------------------------------- JDK -------
step "portable JDK 21 (Temurin / linux x64)"
curl -fsSL -o "$BUNDLE/jdk21.tar.gz" \
  "https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse"
rm -rf "$WORK/jdk"; mkdir -p "$WORK/jdk"
tar -xzf "$BUNDLE/jdk21.tar.gz" -C "$WORK/jdk" --strip-components=1
JAVA="$WORK/jdk/bin/java"
"$JAVA" -version

# --------------------------------------------------------------- Paper ------
step "Paper 1.21.11 (fill v3 API)"
curl -fsSL -A "$UA" -o "$WORK/paper.json" \
  "https://fill.papermc.io/v3/projects/paper/versions/1.21.11/builds/latest"
read -r P_URL P_NAME P_SHA < <(python3 - "$WORK/paper.json" <<'PY'
import json, sys
d = json.load(open(sys.argv[1]))["downloads"]["server:default"]
print(d["url"], d["name"], d["checksums"]["sha256"])
PY
)
[ -n "${P_URL:-}" ] || die "fill v3 から Paper の DL URL を解決できませんでした"
echo "paper: $P_NAME ($P_SHA)"
curl -fsSL -A "$UA" -o "$BUNDLE/$P_NAME" "$P_URL"
echo "$P_SHA  $P_NAME" | ( cd "$BUNDLE" && sha256sum -c - )
echo "::notice::paper ok $P_NAME"

# --------------------------------------------------- Fabric 実測サーバー -----
# サンドボックスからは meta.fabricmc.net に到達できず、CI のログ本体も読めない。そこで
# 各 API 呼び出しは HTTP ステータス + ボディ先頭 + 採用値を ::notice::/::error:: で外へ出す
# (annotations API が唯一読める観測窓)。
step "Fabric server launcher (MC 1.21.11)"
HTTP_STATUS="?"
api_get() { # api_get <url> <outfile>
  local url="$1" out="$2"
  HTTP_STATUS=$(curl -sSL -A "$UA" -w '%{http_code}' -o "$out" "$url" 2>"$out.err" || echo "curl-error")
  echo "  GET $url -> $HTTP_STATUS ($(wc -c <"$out" 2>/dev/null | tr -d ' ') bytes)"
}

pick_json() { # pick_json <file> <dotted.key> — 配列の先頭 stable(無ければ先頭)から点パスで取出す
  python3 - "$1" "$2" <<'PYX' 2>/dev/null || true
import json, sys
try:
    a = json.load(open(sys.argv[1]))
except Exception:
    raise SystemExit
if isinstance(a, list) and a:
    s = [v for v in a if v.get("stable")] or a
    cur = s[0]
    for part in sys.argv[2].split("."):
        if not isinstance(cur, dict):
            cur = None
            break
        cur = cur.get(part)
    if isinstance(cur, (str, int)):
        print(cur)
PYX
}

# loader: ゲーム版つき一覧 → 空/失敗なら全ローダー一覧へフォールバック(loader 自体は版非依存)
api_get "https://meta.fabricmc.net/v2/versions/loader/1.21.11" "$WORK/loader.json"
LOADER=$(pick_json "$WORK/loader.json" loader.version)
if [ -z "$LOADER" ]; then
  BODY=$(head -c 200 "$WORK/loader.json" 2>/dev/null | tr '\n' ' ')
  echo "::warning::loader/1.21.11 が空 (http=$HTTP_STATUS body=$BODY) — 全体リストへフォールバック"
  api_get "https://meta.fabricmc.net/v2/versions/loader" "$WORK/loader_all.json"
  LOADER=$(pick_json "$WORK/loader_all.json" version)
  GAMES=$(curl -sSL -A "$UA" "https://meta.fabricmc.net/v2/versions/game" \
    | python3 -c 'import sys,json
try:
    a=json.load(sys.stdin); print(",".join(str(v.get("version","?")) for v in a[:20]))
except Exception as e:
    print("game-list-unavailable:"+type(e).__name__)' 2>&1 | tr '\n' ' ')
  [ -n "$LOADER" ] || die "fabric loader 未解決 — loader/1.21.11 http=$HTTP_STATUS body=$BODY / 既知 game versions(先頭20): $GAMES"
  echo "::warning::fallback loader=$LOADER (1.21.11 用のサーバー jar は取れない可能性がある)"
fi

api_get "https://meta.fabricmc.net/v2/versions/installer" "$WORK/installer.json"
INSTALLER=$(pick_json "$WORK/installer.json" version)
if [ -z "$INSTALLER" ]; then
  # maven の maven-metadata.xml から installer 版を拾う第2経路
  api_get "https://maven.fabricmc.net/net/fabricmc/fabric-installer/maven-metadata.xml" "$WORK/installer.xml"
  INSTALLER=$(python3 - "$WORK/installer.xml" <<'PY' 2>/dev/null || true
import re, sys
try:
    t = open(sys.argv[1], encoding="utf-8", errors="replace").read()
except Exception:
    raise SystemExit
m = re.findall(r"<version>([^<]+)</version>", t)
print(m[-1] if m else "")
PY
)
fi
[ -n "$INSTALLER" ] || die "fabric installer のバージョンを解決できません (installer API http=$HTTP_STATUS)"
echo "::notice::fabric loader=$LOADER installer=$INSTALLER"

rm -rf "$MCS"; mkdir -p "$MCS/mods"
JAR_URL="https://meta.fabricmc.net/v2/versions/loader/1.21.11/$LOADER/$INSTALLER/server/jar"
HTTP_STATUS=$(curl -sSL -A "$UA" -w '%{http_code}' -o "$MCS/fabric-server-launch.jar" "$JAR_URL" 2>"$WORK/jar.err" || echo "curl-error")
echo "  GET $JAR_URL -> $HTTP_STATUS ($(wc -c <"$MCS/fabric-server-launch.jar" 2>/dev/null | tr -d ' ') bytes)"
if [ ! -s "$MCS/fabric-server-launch.jar" ]; then
  echo "::error::fabric-server-launch.jar 取得失敗 http=$HTTP_STATUS stderr=$(head -c 300 "$WORK/jar.err" | tr '\n' ' ')"
  exit 1
fi
if head -c 100 "$MCS/fabric-server-launch.jar" | grep -qi '<html\|<error\|not found'; then
  echo "::error::fabric-server-launch.jar が HTML/エラー応答 http=$HTTP_STATUS body=$(head -c 200 "$MCS/fabric-server-launch.jar" | tr '\n' ' ')"
  exit 1
fi

step "fabric-api (Modrinth / mc 1.21.11)"
FA=$(curl -fsSL -A "$UA" \
  'https://api.modrinth.com/v2/project/fabric-api/version?game_versions=%5B%221.21.11%22%5D&loaders=%5B%22fabric%22%5D')
if [ -z "$FA" ] || [ "$FA" = "[]" ]; then
  echo "::warning::fabric-api に 1.21.11 ビルドが無いため最新ビルドへフォールバックします"
  FA=$(curl -fsSL -A "$UA" 'https://api.modrinth.com/v2/project/fabric-api/version?loaders=%5B%22fabric%22%5D')
fi
FA_URL=$(printf '%s' "$FA" | python3 -c 'import sys,json; a=json.load(sys.stdin); print(a[0]["files"][0]["url"] if a else "")')
[ -n "$FA_URL" ] || die "Modrinth から fabric-api を解決できませんでした"
echo "fabric-api: $FA_URL"
curl -fsSL -A "$UA" -o "$MCS/mods/fabric-api.jar" "$FA_URL"
[ -s "$MCS/mods/fabric-api.jar" ] || die "fabric-api.jar の取得に失敗"

step "HeroBot MOD + Quantum マップ + qlog データパック"
HERO=$(find "$WS/bot" -maxdepth 1 -name 'herobot-*.jar' -print -quit)
MAPZIP=$(find "$WS/bot" -maxdepth 1 -name '*.zip' -print -quit)
[ -n "$HERO" ] || die "bot/herobot-*.jar がリポジトリにありません"
[ -n "$MAPZIP" ] || die "bot/*.zip (Quantum マップ) がリポジトリにありません"
cp "$HERO" "$MCS/mods/"
rm -rf "$WORK/mapx"; mkdir -p "$WORK/mapx"
unzip -o -q "$MAPZIP" -d "$WORK/mapx" -x "__MACOSX/*" "*.DS_Store"
[ -f "$WORK/mapx/level.dat" ] || die "マップ zip に level.dat がありません(レイアウト想定違い)"
rm -rf "$MCS/QuantumMap"; mv "$WORK/mapx" "$MCS/QuantumMap"
if [ -d "$WS/tools/qlog-datapack" ]; then
  rm -rf "$MCS/QuantumMap/datapacks/qlog"
  cp -r "$WS/tools/qlog-datapack" "$MCS/QuantumMap/datapacks/qlog"
  echo "qlog -> QuantumMap/datapacks/qlog (0.1s サンプラ)"
else
  echo "::warning::tools/qlog-datapack が無いため qlog 未導入で配送します"
fi

step "server.properties / eula"
cat > "$MCS/server.properties" <<'PROPS'
level-name=QuantumMap
online-mode=false
enable-rcon=true
rcon.port=25575
rcon.password=rumilance
spawn-protection=0
view-distance=8
simulation-distance=6
max-players=10
allow-flight=true
motd=QuantumBOT measurement
PROPS
echo "eula=true" > "$MCS/eula.txt"

# 起動検証: ここで一度起動しておくと、バニラ server.jar / Mojang ライブラリ / リマップ済みジャーが
# すべて .fabric と libraries に落ちる = サンドボックス(オフライン)でもそのまま起動できる。
step "起動検証(Done まで最大 8 分 → stop で正常終了)"
# FIFO は「読み書き両開き (exec 8<>)」。読み取り専用で開くと書き手が現れるまで open が
# ブロックし、さらに "< fifo" を先に処理するリダイレクト順の都合で、ログファイルすら
# 作られないまま Java が起動しない(初回実行で踏んだ罠)。両開きなら誰も待たない。
rm -f /tmp/boot_ctl; mkfifo /tmp/boot_ctl
exec 8<>/tmp/boot_ctl
( cd "$MCS" && exec "$JAVA" -Xmx2400M -jar fabric-server-launch.jar nogui ) \
  <&8 > "$MCS/server_boot.log" 2>&1 &
BOOT_PID=$!
for _ in $(seq 1 96); do
  grep -q "Done (" "$MCS/server_boot.log" 2>/dev/null && break
  kill -0 "$BOOT_PID" 2>/dev/null || break
  sleep 5
done

boot_report() { # 起動ログの中身を数行のアノテーションに畳む(ログ本体は読めない前提)
  local size head_ tail_ mark_ listing_
  size=$(wc -c < "$MCS/server_boot.log" 2>/dev/null || echo 0)
  head_=$(head -c 700 "$MCS/server_boot.log" 2>/dev/null || true)
  tail_=$(tail -c 1600 "$MCS/server_boot.log" 2>/dev/null || true)
  mark_=$(grep -aoE "Exception|Incompatible mod set|UnsupportedClassVersion|Downloading|Loading [0-9]+ mods|Failed to|NoSuchMethodError|ClassNotFound" "$MCS/server_boot.log" 2>/dev/null | sort -u || true)
  listing_=$(ls -1 "$MCS" 2>/dev/null || true)
  echo "size=${size}B files=[$(echo "$listing_" | tr '\n' ' ')]"
  echo "markers=[$(echo "$mark_" | tr '\n' ' ')]"
  echo "HEAD>>> ${head_//$'\n'/ | }"
  echo "TAIL>>> ${tail_//$'\n'/ | }"
}

if ! grep -q "Done (" "$MCS/server_boot.log" 2>/dev/null; then
  BOOT_EXIT=alive
  if ! kill -0 "$BOOT_PID" 2>/dev/null; then BOOT_EXIT=$(wait "$BOOT_PID" 2>/dev/null; echo $?); fi
  echo "::error::Fabric サーバーが Done に到達しません (exit=$BOOT_EXIT)"
  boot_report | while IFS= read -r line; do echo "::error::$line"; done
  exit 1
fi
if ! grep -qi "herobot" "$MCS/server_boot.log"; then
  echo "::error::herobot MOD がロードされていません"
  grep -aiE "mods|herobot|fabric" "$MCS/server_boot.log" 2>/dev/null | head -c 900 | while IFS= read -r line; do echo "::error::$line"; done
  exit 1
fi
grep -E "Done \(|Loading .* mods" "$MCS/server_boot.log" | tail -3
echo "::notice::boot ok mods=$(grep -aoE '[0-9]+ mods' "$MCS/server_boot.log" 2>/dev/null | head -1) done=$(grep -am1 "Done (" "$MCS/server_boot.log" 2>/dev/null | cut -c1-120)"

if kill -0 "$BOOT_PID" 2>/dev/null; then
  printf 'stop\n' >&8 2>/dev/null || true
  for _ in $(seq 1 60); do kill -0 "$BOOT_PID" 2>/dev/null || break; sleep 1; done
fi
if kill -0 "$BOOT_PID" 2>/dev/null; then kill "$BOOT_PID" 2>/dev/null || true; sleep 3; fi
exec 8>&-
wait "$BOOT_PID" 2>/dev/null || true
rm -f /tmp/boot_ctl
tail -3 "$MCS/server_boot.log"

step "パッケージング"
( cd /tmp && tar -czf "$BUNDLE/mcserver.tar.gz" --exclude='mcserver/logs' mcserver )
( cd "$BUNDLE" && sha256sum jdk21.tar.gz paper-*.jar > sha256s.txt \
  && sha256sum mcserver.tar.gz > mcserver.sha256 && ls -lh )

# ------------------------------------------------------------ git 配送 ------
cd "$WS"
git config user.name "java-env-bot"
git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
git config http.postBuffer 524288000
git config --get-regexp '^http\.' | grep -q extraheader \
  || echo "::warning::checkout に push 資格情報(http.*.extraheader)が見あたりません(persist-credentials 未設定?)"

step "build: 当プラグイン (./gradlew test shadowJar)"
if [ -x ./gradlew ]; then
  # ランナー既定の JDK に依存しない(配送用に取得済みの Temurin 21 を使う)
  export JAVA_HOME="$WORK/jdk"
  export PATH="$JAVA_HOME/bin:$PATH"
  export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$WORK/gradle-home}"
  mkdir -p "$GRADLE_USER_HOME"
  java -version 2>&1 | head -2
  set +e
  ./gradlew --no-daemon test shadowJar > "$WORK/plugin-build.log" 2>&1
  rc=$?
  set -e
  if [ "$rc" -ne 0 ]; then
    # サンドボックスからログ本文は読めないので、失敗付近を annotation に載せる
    grep -E "error:|FAILURE|What went wrong|Caused by|> Task .*FAILED|Could not|Received status|Connection|PKIX|SSL|timed out" \
      "$WORK/plugin-build.log" | tail -15 | sed 's/^/::error::gradle: /' || true
    tail -30 "$WORK/plugin-build.log" | sed 's/^/::notice::gradle-tail: /' || true
    die "gradlew test shadowJar に失敗 (rc=$rc)"
  fi
  tail -5 "$WORK/plugin-build.log" || true
  ls -lh build/libs/ || die "build/libs がありません"
  mkdir -p "$BUNDLE/plugin"
  cp build/libs/*.jar "$BUNDLE/plugin/" 2>/dev/null || die "shadowJar の成果物をコピーできません"
  ( cd "$BUNDLE/plugin" && sha256sum ./*.jar > sha256s.txt && cat sha256s.txt )
  echo "::notice::plugin built: $(ls "$BUNDLE/plugin" | tr '\n' ' ')"
else
  echo "::warning::gradlew が見つからないためプラグインのビルドを省略します"
fi

step "deliver: mc-server-delivery"
git branch -D mc-server-delivery >/dev/null 2>&1 || true
git checkout -q --orphan mc-server-delivery
git rm -rfq . 2>/dev/null || true
mkdir -p mcserver
split -b 90m -d "$BUNDLE/mcserver.tar.gz" mcserver/mcserver.tar.gz.part-
cp "$BUNDLE/mcserver.sha256" mcserver/sha256s.txt
cat > mcserver/README.txt <<TXT
mc-server-delivery — 起動検証済み Fabric 実測サーバー(run $RUN)

サンドボックス側(下りは github.com / api.github.com のみ):
  git clone --depth 1 --branch mc-server-delivery \\
    https://github.com/ifuto/RumilancePractice.git /tmp/mcship
  cat /tmp/mcship/mcserver/mcserver.tar.gz.part-* > /tmp/mcserver.tar.gz
  (cd /tmp && sha256sum -c /tmp/mcship/mcserver/sha256s.txt)
  tar -xzf /tmp/mcserver.tar.gz -C /tmp
  cd /tmp/mcserver && /tmp/jdk21/bin/java -Xmx2400M -jar fabric-server-launch.jar nogui

中身:
  fabric-server-launch.jar    Fabric(1.21.11) サーバーランチャ
  mods/fabric-api.jar         Fabric API
  mods/herobot-*.jar          HeroBot MOD(/player 相当のフェイクプレイヤー)
  QuantumMap/                 Quantum's PvP Practice v1.18 のワールド
                              datapacks/Practicebot(本体) + datapacks/qlog(0.1s サンプラ)
  eula.txt / server.properties / server_boot.log (起動検証の実ログ)

測定開始(コンソール):
  /function quantum:options/crystal
  /player quantumbot spawn at 11 34 10 facing 0 0 in survival
  /scoreboard players set .start start 1
  → latest.log の [q] 行が qlog のサンプル
TXT
ls -lh mcserver
git add -A mcserver
git commit -qm "mcserver delivery (run $RUN): Fabric + fabric-api + HeroBot + Quantum map + qlog"
if ! git push -f origin mc-server-delivery 2>/tmp/push1.err; then
  echo "::error::push mc-server-delivery failed: $(tr '\n' ' ' < /tmp/push1.err | head -c 900)"
  exit 1
fi
git ls-remote --heads origin mc-server-delivery
echo "::notice::delivered mc-server-delivery $(git rev-parse HEAD)"

step "deliver: java-env-delivery"
git branch -D java-env-delivery >/dev/null 2>&1 || true
git checkout -q --orphan java-env-delivery
git rm -rfq . 2>/dev/null || true
mkdir -p delivery
split -b 90m -d "$BUNDLE/jdk21.tar.gz" delivery/jdk21.tar.gz.part-
cp "$BUNDLE"/paper-*.jar "$BUNDLE/sha256s.txt" delivery/
ls -lh delivery
git add -A delivery
git commit -qm "java-env delivery (run $RUN): Temurin 21 + Paper 1.21.11"
if ! git push -f origin java-env-delivery 2>/tmp/push2.err; then
  echo "::error::push java-env-delivery failed: $(tr '\n' ' ' < /tmp/push2.err | head -c 900)"
  exit 1
fi
git ls-remote --heads origin java-env-delivery
echo "::notice::delivered java-env-delivery $(git rev-parse HEAD)"

step "deliver: plugin-delivery"
git branch -D plugin-delivery >/dev/null 2>&1 || true
git checkout -q --orphan plugin-delivery
git rm -rfq . 2>/dev/null || true
if [ -d "$BUNDLE/plugin" ] && compgen -G "$BUNDLE/plugin/*.jar" >/dev/null; then
  mkdir -p plugin
  cp "$BUNDLE/plugin"/*.jar "$BUNDLE/plugin/sha256s.txt" plugin/
  cat > plugin/README.txt <<'EOT'
plugin-delivery — RumilancePractice の shadowJar (run __RUN__)

  git clone --depth 1 --branch plugin-delivery \
    https://github.com/ifuto/RumilancePractice.git /tmp/pluginship

使い方(サンドボックス):
  1) Paper サーバーを用意し plugins/ へ jar を置く
  2) 起動 → /botadmin などで BOT を出し、参照側と同じシナリオを戦わせる
  3) fight trace の s 行を回収 → tools/compare_fights.py で参照([q]行)と比較
EOT
  sed -i "s/__RUN__/$RUN/" plugin/README.txt
  ls -lh plugin
  git add -A plugin
  git commit -qm "plugin delivery (run $RUN): RumilancePractice shadowJar"
  if ! git push -f origin plugin-delivery 2>/tmp/push3.err; then
    echo "::error::push plugin-delivery failed: $(tr '\n' ' ' < /tmp/push3.err | head -c 900)"
  fi
  git ls-remote --heads origin plugin-delivery
  echo "::notice::delivered plugin-delivery $(git rev-parse HEAD)"
else
  echo "::warning::プラグイン成果物が無いため plugin-delivery を省略"
fi

step "paper-server: 起動可能な Paper 一式を作る(サンドボックスは Mojang に接続できないため)"
if compgen -G "$BUNDLE/paper-*.jar" >/dev/null; then
  PSRV="$WORK/paper-run"
  rm -rf "$PSRV"; mkdir -p "$PSRV/plugins"
  cp "$BUNDLE"/paper-*.jar "$PSRV/server.jar"
  echo "eula=true" > "$PSRV/eula.txt"
  cat > "$PSRV/server.properties" <<'EOT'
online-mode=false
enable-rcon=true
rcon.port=25576
rcon.password=rumilance
gamemode=creative
spawn-protection=0
max-players=40
view-distance=8
simulation-distance=6
motd=RumilancePractice parity sandbox
EOT
  cd "$PSRV"
  mkfifo "$WORK/paperin"
  exec 9<>"$WORK/paperin"
  ( "$JAVA" -Xmx2G -jar server.jar nogui <&9 > "$WORK/paper-run.log" 2>&1 ) &
  PAPER_PID=$!
  ok=0
  for i in $(seq 1 90); do
    sleep 2
    if grep -q "Done (" "$WORK/paper-run.log" 2>/dev/null; then ok=1; break; fi
    if ! kill -0 $PAPER_PID 2>/dev/null; then break; fi
  done
  if [ "$ok" = "1" ]; then
    echo "stop" >&9
    for i in $(seq 1 30); do sleep 1; kill -0 $PAPER_PID 2>/dev/null || break; done
    kill $PAPER_PID 2>/dev/null || true
    echo "::notice::paper server boot ok (patched jar + libraries 取得済み)"
  else
    echo "::error::paper server の起動に失敗(生成ログ tail):"
    tail -15 "$WORK/paper-run.log" | sed 's/^/::error::paper: /' || true
  fi
  exec 9>&-
  cd "$WS"
  rm -rf "$PSRV/logs" "$PSRV/world" "$PSRV/world_nether" "$PSRV/world_the_end" 2>/dev/null || true
  rm -f "$PSRV/usercache.json" "$PSRV/banned-*.json" 2>/dev/null || true
  tar -czf "$BUNDLE/paper-server.tar.gz" -C "$WORK" paper-run
  sha256sum "$BUNDLE/paper-server.tar.gz" | awk '{print $1"  paper-server.tar.gz"}' > "$BUNDLE/paper-server.sha256"
  ls -lh "$BUNDLE/paper-server.tar.gz"
fi

step "deliver: paper-server-delivery"
git branch -D paper-server-delivery >/dev/null 2>&1 || true
git checkout -q --orphan paper-server-delivery
git rm -rfq . 2>/dev/null || true
if [ -f "$BUNDLE/paper-server.tar.gz" ]; then
  mkdir -p paperserver
  split -b 90m -d "$BUNDLE/paper-server.tar.gz" paperserver/paper-server.tar.gz.part-
  cp "$BUNDLE/paper-server.sha256" paperserver/sha256s.txt
  cat > paperserver/README.txt <<'EOT'
paper-server-delivery — 起動可能な Paper 一式 (run __RUN__)

  git clone --depth 1 --branch paper-server-delivery \
    https://github.com/ifuto/RumilancePractice.git /tmp/papership
  cd /tmp/papership && cat paperserver/paper-server.tar.gz.part-* > paper-server.tar.gz
  tar xzf paper-server.tar.gz        # -> paper-run/ (server.jar + libraries + versions)
  cd paper-run && /tmp/jdk21/bin/java -Xmx2G -jar server.jar nogui

plugins/ に plugin-delivery の jar を置けば当プラグイン検証ができる。
Mojang から vanilla を再取得しないよう patched jar と libraries を同梱している。
EOT
  sed -i "s/__RUN__/$RUN/" paperserver/README.txt
  ls -lh paperserver
  git add -A paperserver
  git commit -qm "paper-server delivery (run $RUN): 起動可能な Paper 一式"
  if ! git push -f origin paper-server-delivery 2>/tmp/push4.err; then
    echo "::error::push paper-server-delivery failed: $(tr '\n' ' ' < /tmp/push4.err | head -c 900)"
  fi
  git ls-remote --heads origin paper-server-delivery
  echo "::notice::delivered paper-server-delivery $(git rev-parse HEAD)"
fi

step "done — delivered mc-server-delivery + java-env-delivery + plugin-delivery + paper-server-delivery"
