#!/usr/bin/env bash
# 1 ラウンドを Fabric(参照) と Paper(移植) で「同時に」走らせ、
#   parity-logs/<tag>_fabric.log.gz / parity-logs/<tag>_paper.log.gz を作る。
#
#   tools/parity-runner/pair.sh <tag> [scenario] [seconds] [warmup]
#
# 既定: scenario=sword_k10v11, seconds=45, warmup=20
# 判定は tools/parity_verify.py(または --noise 付きの parity_compare.py)で行う。
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TAG="${1:?使い方: pair.sh <tag> [scenario] [seconds] [warmup]}"
SCEN="${2:-sword_k10v11}"
SECS="${3:-45}"
WARM="${4:-20}"

FAB_RCON=25575
PAPER_RCON=25576

mkdir -p "$ROOT/parity-logs"
cd "$ROOT"

python3 tools/parity_runner.py run \
  --console /tmp/mcref/mcserver/console.in --log /tmp/mcref/mcserver/console.log \
  --side fabric --scenario "$SCEN" --seconds "$SECS" --warmup "$WARM" \
  --rcon-port "$FAB_RCON" --out "parity-logs/${TAG}_fabric.log.gz" \
  > "/tmp/${TAG}_fabric.out" 2>&1 &
FAB_PID=$!

python3 tools/parity_runner.py run \
  --console /tmp/testsrv/console.in --log /tmp/testsrv/console.log \
  --side paper --invoke 'quantum run ' --scenario "$SCEN" --seconds "$SECS" --warmup "$WARM" \
  --rcon-port "$PAPER_RCON" --out "parity-logs/${TAG}_paper.log.gz" \
  > "/tmp/${TAG}_paper.out" 2>&1 &
PAPER_PID=$!

wait "$FAB_PID" || echo "!! fabric 側が失敗した(/tmp/${TAG}_fabric.out)"
wait "$PAPER_PID" || echo "!! paper 側が失敗した(/tmp/${TAG}_paper.out)"

echo "--- fabric ---"; tail -3 "/tmp/${TAG}_fabric.out"
echo "--- paper ---";  tail -3 "/tmp/${TAG}_paper.out"
