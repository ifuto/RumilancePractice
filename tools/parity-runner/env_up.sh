#!/usr/bin/env bash
# parity 検証環境をゼロから立て直す(サンドボックス再起動で /tmp が消えても 1 コマンドで復旧)。
#
#   tools/parity-runner/env_up.sh [--start]
#
# やること:
#   1. delivery ブランチから JDK / Fabric サーバー / Paper サーバー / 土台プラグイン jar を取り出す
#   2. Fabric を /tmp/mcref/mcserver、Paper を /tmp/testsrv に展開(ポート 25565 / 25566,
#      RCON 25575 / 25576, パスワード parity,コンソールは FIFO)
#   3. この repо のソースからプラグインをビルドして Paper に配置
#   4. 両ワールドに parity ハーネス データパックを配り、計測用カウンタを仕込む
#
# --start を付けると両サーバーを起動する(既に走っていれば何もしない)。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
DL=/tmp/dl
FAB=/tmp/mcref/mcserver
PAPER=/tmp/testsrv
JDK=/tmp/jdk21
START=0
[ "${1:-}" = "--start" ] && START=1

log() { echo "== $*"; }

# ---------------------------------------------------------------- 1. delivery blobs
if [ ! -f "$DL/RumilancePractice-1.76.27.jar" ] || [ ! -f "$DL/jdk21.tar.gz.part-00" ]; then
  log "delivery ブランチを取得"
  git -C "$ROOT" fetch origin '+refs/heads/*:refs/remotes/origin/*' --quiet
  mkdir -p "$DL"
  for spec in "java-env-delivery" "mc-server-delivery" "paper-server-delivery" "plugin-delivery"; do
    git -C "$ROOT" ls-tree -r --name-only "origin/$spec" | while read -r f; do
      out="$DL/$(basename "$f")"
      [ -f "$out" ] && continue
      git -C "$ROOT" cat-file blob "origin/$spec:$f" > "$out"
    done
  done
fi

# ---------------------------------------------------------------- 2. extract
if [ ! -x "$JDK/bin/java" ]; then
  log "JDK21 を展開"
  mkdir -p "$JDK" && cat "$DL"/jdk21.tar.gz.part-* | tar xz -C "$JDK" --strip-components=1
fi

if [ ! -f "$FAB/fabric-server-launch.jar" ]; then
  log "Fabric サーバーを展開"
  mkdir -p /tmp/mcref && cat "$DL"/mcserver.tar.gz.part-* | tar xz -C /tmp/mcref
fi

if [ ! -f "/tmp/papersrv/paper-run/server.jar" ]; then
  log "Paper サーバーを展開"
  mkdir -p /tmp/papersrv && cat "$DL"/paper-server.tar.gz.part-* | tar xz -C /tmp/papersrv
fi

if [ ! -d "$PAPER" ]; then
  log "Paper 実行ディレクトリを用意(ワールドは Fabric 側のコピー)"
  cp -a /tmp/papersrv/paper-run "$PAPER"
  cp -a "$FAB/QuantumMap" "$PAPER/QuantumMap"
  set_props() {  # set_props <file> <key> <value>
    local file=$1 key=$2 value=$3
    if grep -q "^$key=" "$file"; then sed -i "s|^$key=.*|$key=$value|" "$file";
    else echo "$key=$value" >> "$file"; fi
  }
  set_props "$PAPER/server.properties" level-name QuantumMap
  set_props "$PAPER/server.properties" server-port 25566
  set_props "$PAPER/server.properties" rcon.port 25576
  set_props "$PAPER/server.properties" rcon.password parity
  set_props "$PAPER/server.properties" enable-rcon true
  set_props "$PAPER/server.properties" max-players 10
  set_props "$PAPER/server.properties" difficulty easy
fi

set_props() { local file=$1 key=$2 value=$3
  if grep -q "^$key=" "$file"; then sed -i "s|^$key=.*|$key=$value|" "$file";
  else echo "$key=$value" >> "$file"; fi
}
log "Fabric の RCON / ポートを揃える"
set_props "$FAB/server.properties" enable-rcon true
set_props "$FAB/server.properties" rcon.port 25575
set_props "$FAB/server.properties" rcon.password parity
set_props "$FAB/server.properties" server-port 25565

# ---------------------------------------------------------------- 3. plugin
log "プラグインをビルド"
PLUGIN="$PAPER/plugins/RumilancePractice.jar"
mkdir -p "$PAPER/plugins"
JDK="$JDK" DELIVERY="$DL/RumilancePractice-1.76.27.jar" \
  PAPER_JAR="/tmp/papersrv/paper-run/versions/1.21.11/paper-1.21.11.jar" \
  "$ROOT/tools/parity-runner/build_plugin.sh" "$PLUGIN" >/tmp/build_plugin.log 2>&1 || {
    tail -30 /tmp/build_plugin.log; exit 1; }
tail -2 /tmp/build_plugin.log

# ---------------------------------------------------------------- 4. harness + counters
if [ ! -d "$ROOT/tools/parity-runner/datapack/parity" ]; then
  ( cd "$ROOT/tools/parity-runner" && python3 gen_pack.py )
fi
for world in "$FAB/QuantumMap" "$PAPER/QuantumMap"; do
  log "parity パックを配布: $world"
  python3 "$ROOT/tools/parity_runner.py" deploy "$world/datapacks" >/dev/null
  python3 "$ROOT/tools/parity-runner/instrument_world.py" "$world/datapacks"
done

# ---------------------------------------------------------------- 4.5 サーバーキット
# /tmp が消えるたびにキットと紐づけを作り直すと、その途中の状態で計測して偽の差を
# 出してしまう。検証済みの一式を fixtures から流し込んでおく(プラグインは boot 時に
# kits.yml / practices.yml / quantum.yml を読むので、起動前が正しいタイミング)。
if [ -f "$ROOT/tools/parity-runner/fixtures/parity-kits.yml" ]; then
  log "検証済みサーバーキットを適用 (fixtures/parity-*)"
  python3 "$ROOT/tools/parity-runner/server_preset.py" apply --dir "$PAPER" || \
    echo "  !! server_preset failed"
fi

# ---------------------------------------------------------------- 5. start scripts
write_start() {  # write_start <dir> <jar> <heap>
  cat > "$1/start.sh" <<EOF
#!/usr/bin/env bash
cd "$1"
rm -f console.in
mkfifo console.in
( while true; do sleep 86400; done ) > console.in &
while true; do
  $JDK/bin/java -Xmx$3 -Xms256M -jar $2 nogui < console.in >> console.log 2>&1
  echo "--- server exited, restarting in 5s" >> console.log
  sleep 5
done
EOF
  chmod +x "$1/start.sh"
}
write_start "$FAB" fabric-server-launch.jar 1100M
write_start "$PAPER" server.jar 1000M

if [ "$START" = 1 ]; then
  log "両サーバーを起動"
  rm -f "$FAB/QuantumMap/session.lock" "$PAPER/QuantumMap/session.lock"
  nohup "$FAB/start.sh" >/dev/null 2>&1 &
  nohup "$PAPER/start.sh" >/dev/null 2>&1 &
  echo "  起動中… (ログ: $FAB/console.log / $PAPER/console.log)"
fi
if [ "$START" = 1 ] && [ -f "$ROOT/tools/parity-runner/fixtures/parity-kits.yml" ]; then
  log "起動後のキット確認 (プラグインが初期化で上書きしていれば再適用)"
  for _ in $(seq 1 36); do
    if timeout 3 bash -c 'exec 3<>/dev/tcp/127.0.0.1/25576' 2>/dev/null; then break; fi
    sleep 5
  done
  sleep 20   # ワールド読込とプラグイン有効化を待つ
  if ! python3 "$ROOT/tools/parity-runner/rcon.py" 25576 parity 'botadmin' 2>/dev/null | grep -q "MACE BOT"; then
    echo "  botadmin の紐づけが見えない → 再適用して rumireload"
    python3 "$ROOT/tools/parity-runner/server_preset.py" apply --dir "$PAPER" >/dev/null
    python3 "$ROOT/tools/parity-runner/rcon.py" 25576 parity 'rumireload' >/dev/null 2>&1 || true
    sleep 5
    python3 "$ROOT/tools/parity-runner/rcon.py" 25576 parity 'botadmin' | head -5
  else
    echo "  紐づけ OK:"
    python3 "$ROOT/tools/parity-runner/rcon.py" 25576 parity 'botadmin' | head -5
  fi
fi

log "done"
