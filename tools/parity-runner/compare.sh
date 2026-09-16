#!/usr/bin/env bash
# Fabric と Paper で同じシナリオを走らせ、bot の内部カウンタと状態を左右に並べて出す。
#
#   tools/parity-runner/compare.sh [scenario] [seconds]
#
# 既定: scenario=crystal_k10v11, seconds=25
# Fabric=25565 (RCON 25575) / Paper=25566 (RCON 25576)、どちらも verb は plugin 側。
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
RCON="$ROOT/tools/parity-runner/rcon.py"
SCEN="${1:-crystal_k10v11}"
SECS="${2:-25}"
FAB_RCON=25575
PAPER_RCON=25576
PAPER_PRE='quantum run '

# 読むカウンタ(個体別 → 全体の順)
COUNTERS=(
  c_q2_bin27 c_qa_bin27 c_q2_ctick c_qa_ctick
  c_bin27 c_botlogic c_movement c_look c_crystaltick c_crystaltick2
  c_canhit c_hit c_mode c_checktimer c_anchortick c_bin14
  c_spawncry c_placeobby c_chargeanchor c_placeanchor c_breakcry
  c_anchortest c_mark c_obbycheck
)
PLAYERS=(quantumbot qbot2)

# 片側を走らせて "<key>=<value>" を 1 行ずつ出す
run_side() {
  local rcon_port=$1 pre=$2
  python3 "$RCON" "$rcon_port" parity "${pre}function parity:load" >/dev/null 2>&1
  sleep 1
  local zero=()
  local c
  for c in "${COUNTERS[@]}" qbot2_death; do
    zero+=("${pre}scoreboard players set .${c} dbgc 0")
  done
  python3 "$RCON" "$rcon_port" parity "${zero[@]}" \
      "${pre}function parity:setup/${SCEN}" >/dev/null 2>&1
  sleep "$SECS"
  local reads=()
  for c in "${COUNTERS[@]}"; do reads+=("${pre}scoreboard players get .${c} dbgc"); done
  local p
  for p in "${PLAYERS[@]}"; do
    reads+=("${pre}execute as ${p} run data get entity @s Health"
            "${pre}execute as ${p} run data get entity @s Pos"
            "${pre}execute as ${p} run data get entity @s Inventory[0].id"
            "${pre}scoreboard players get ${p} crystal_timer"
            "${pre}scoreboard players get ${p} state"
            "${pre}scoreboard players get ${p} hitcd"
            "${pre}scoreboard players get ${p} death")
  done
  python3 "$RCON" "$rcon_port" parity "${reads[@]}" 2>&1
}

value_of() {  # value_of <raw> — "X has 123 [..]" / "X has the following entity data: 20.0f"
  sed 's/.* get //;s/ has /=/;s/ \[.*//;s/.* has the following entity data: /=/;s/ \[.*//;s/Can.t get value of /=UNSET:/;s/Unknown or incomplete command.*/ERR/'
}

FAB_RAW="$(run_side $FAB_RCON '')"
PAPER_RAW="$(run_side $PAPER_RCON "$PAPER_PRE")"
FAB_OUT="$(printf '%s\n' "$FAB_RAW" | value_of)"
PAPER_OUT="$(printf '%s\n' "$PAPER_RAW" | value_of)"

printf '%-22s | %-26s | %-26s\n' 'key' 'fabric' 'paper'
printf -- '-----------------------|----------------------------|----------------------------\n'
paste -d'\n' <(printf '%s\n' "$FAB_OUT") <(printf '%s\n' "$PAPER_OUT") | paste -d'|' - - \
  | awk -F'|' '{printf "%-22s | %-26s | %-26s\n", $1, $2, $3}'
