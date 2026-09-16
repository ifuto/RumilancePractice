#!/usr/bin/env python3
"""世界のデータパックに、Bot vs Bot の挙動を測るためのカウンタを仕込む(冪等)。

    python3 tools/parity-runner/instrument_world.py <world-datapacks-dir> [...]

``<world-datapacks-dir>`` は ``<world>/datapacks`` (中に ``Practicebot/data/quantum/...`` がある)。

何を測っているか:

  .c_bin27 / .c_bin13         … 状態機械の分岐(bin/27, bin/13)が何回呼ばれたか
  .c_botlogic / .c_movement   … 同じく g1gc の各サブ tick
  .c_look / .c_decisions      … 視点更新・意思決定 tick
  .c_crystaltick / .c_crystaltick2 … crystal/tick(全体)と g1gc/crystal_tick(個別)
  .c_mode / .c_canhit / .c_hit / .c_newstats … mode 切替・当たり判定・ヒット・stats
  .c_q2_* / .c_qa_*           … 上記のうち qbot2 / quantumbot 由来のもの(個体別)
  .c_l1 .. .c_l17             … crystal/tick を行ごとに数えたもの(どの行で止まるかを見る)
  .c_spawncry 以降            … クリスタル設置系(生成・黒曜石・アンカー・chain)の到達数

すべて ``dbgc`` objective(ハーネスが作る)に加算するだけで、挙動には影響しない。
値を読みたいときは RCON で ``scoreboard players get .c_bin27 dbgc``。
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

# (ファイル, マーカー名, そのファイルの先頭に足す行)  ※既に入っていたら何もしない
COUNTERS = {
    'quantum/function/bin/27.mcfunction': [
        ('c_bin27', 'scoreboard players add .c_bin27 dbgc 1'),
        ('c_q2_bin27',
         'execute if entity @s[name=qbot2] run scoreboard players add .c_q2_bin27 dbgc 1'),
        ('c_qa_bin27',
         'execute if entity @s[name=quantumbot] run scoreboard players add .c_qa_bin27 dbgc 1'),
    ],
    'quantum/function/bin/13.mcfunction': [
        ('c_bin13', 'scoreboard players add .c_bin13 dbgc 1'),
    ],
    'quantum/function/g1gc/botlogic.mcfunction': [
        ('c_botlogic', 'scoreboard players add .c_botlogic dbgc 1'),
    ],
    'quantum/function/g1gc/movement.mcfunction': [
        ('c_movement', 'scoreboard players add .c_movement dbgc 1'),
    ],
    'quantum/function/g1gc/hit.mcfunction': [
        ('c_hit', 'scoreboard players add .c_hit dbgc 1'),
    ],
    'quantum/function/g1gc/can_hit.mcfunction': [
        ('c_canhit', 'scoreboard players add .c_canhit dbgc 1'),
    ],
    'quantum/function/g1gc/crystal_tick.mcfunction': [
        ('c_crystaltick2', 'scoreboard players add .c_crystaltick2 dbgc 1'),
        ('c_q2_ctick',
         'execute if entity @s[name=qbot2] run scoreboard players add .c_q2_ctick dbgc 1'),
        ('c_qa_ctick',
         'execute if entity @s[name=quantumbot] run scoreboard players add .c_qa_ctick dbgc 1'),
    ],
    'quantum/function/look.mcfunction': [
        ('c_look', 'scoreboard players add .c_look dbgc 1'),
    ],
    'quantum/function/decisions/tick.mcfunction': [
        ('c_decisions', 'scoreboard players add .c_decisions dbgc 1'),
    ],
    'quantum/function/init/mode.mcfunction': [
        ('c_mode', 'scoreboard players add .c_mode dbgc 1'),
    ],
    'quantum/function/crystal/tick.mcfunction': [
        ('c_crystaltick', 'scoreboard players add .c_crystaltick dbgc 1'),
    ],
    'quantum/function/g1gc/spawncrystal.mcfunction': [
        ('c_spawncry', 'scoreboard players add .c_spawncry dbgc 1'),
    ],
    'quantum/function/g1gc/placeobsidian.mcfunction': [
        ('c_placeobby', 'scoreboard players add .c_placeobby dbgc 1'),
    ],
    'quantum/function/g1gc/charge_anchor.mcfunction': [
        ('c_chargeanchor', 'scoreboard players add .c_chargeanchor dbgc 1'),
    ],
    'quantum/function/g1gc/place_anchor.mcfunction': [
        ('c_placeanchor', 'scoreboard players add .c_placeanchor dbgc 1'),
    ],
    'quantum/function/g1gc/breakcrystal.mcfunction': [
        ('c_breakcry', 'scoreboard players add .c_breakcry dbgc 1'),
    ],
    'quantum/function/g1gc/anchor_tick.mcfunction': [
        ('c_anchortick', 'scoreboard players add .c_anchortick dbgc 1'),
    ],
    'quantum/function/bin/14.mcfunction': [
        ('c_bin14', 'scoreboard players add .c_bin14 dbgc 1'),
    ],
    'xaniclelib/function/anchor/anchortest.mcfunction': [
        ('c_anchortest', 'scoreboard players add .c_anchortest dbgc 1'),
    ],
    'xaniclelib/function/check_timer.mcfunction': [
        ('c_checktimer', 'scoreboard players add .c_checktimer dbgc 1'),
    ],
    'xaniclelib/function/mark.mcfunction': [
        ('c_mark', 'scoreboard players add .c_mark dbgc 1'),
    ],
    'xaniclelib/function/self_crystal_check.mcfunction': [
        ('c_selfcry', 'scoreboard players add .c_selfcry dbgc 1'),
    ],
    'xaniclelib/function/obbycheck.mcfunction': [
        ('c_obbycheck', 'scoreboard players add .c_obbycheck dbgc 1'),
    ],
}

# crystal/tick は「どこまで進んだか」を 1 行ずつ数える(早期 return の位置を特定するため)
LINE_COUNTER = 'quantum/function/crystal/tick.mcfunction'
LINE_PREFIX = '.c_l'


def instrument(datapacks: Path) -> int:
    packs = sorted(p for p in datapacks.iterdir() if p.is_dir())
    touched = 0
    for pack in packs:
        data = pack / 'data'
        if not data.is_dir():
            continue
        for rel, entries in COUNTERS.items():
            path = data / rel
            if not path.is_file():
                continue
            text = path.read_text(errors='ignore')
            add = [line for marker, line in entries if f'{marker} dbgc' not in text]
            if add:
                path.write_text('\n'.join(add) + '\n' + text)
                touched += 1
        # 行カウンタ
        path = data / LINE_COUNTER
        if path.is_file():
            text = path.read_text(errors='ignore')
            if f'{LINE_PREFIX}1 dbgc' not in text:
                out, n = [], 0
                for line in text.split('\n'):
                    out.append(line)
                    if not line.strip() or line.lstrip().startswith('#'):
                        continue
                    n += 1
                    out.append(f'scoreboard players add {LINE_PREFIX}{n} dbgc 1')
                path.write_text('\n'.join(out))
                touched += 1
    return touched


def main(argv: list[str]) -> int:
    if len(argv) < 2:
        print(__doc__)
        return 2
    for arg in argv[1:]:
        datapacks = Path(arg)
        if not (datapacks / 'Practicebot').is_dir():
            print('!! Practicebot が無い: %s' % datapacks)
            continue
        print('instrumented %s (%d file(s) updated)' % (datapacks, instrument(datapacks)))
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
