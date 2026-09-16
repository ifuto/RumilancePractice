#!/usr/bin/env python3
"""1 ラウンド回して、世界に刺さっているカウンタを両エンジンで読み比べる。

    python3 tools/parity-runner/count_round.py <tag> [scenario] [seconds] [warmup]

流れ:
  1. 両エンジンのカウンタを 0 に戻す
  2. pair.sh で 1 ラウンド(Fabric と Paper を同時に)
  3. ラウンド直後にカウンタを読む(Paper は保存前に落ちると巻き戻るので即読む)
"""
from __future__ import annotations

import socket
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools' / 'parity-runner'))
import rcon  # noqa: E402

PORTS = {'fabric': (25575, ''), 'paper': (25576, 'quantum run ')}

# 読むカウンタ(世界側に刺さっているもの)
COUNTERS = [
    'c_vsdispatch', 'c_look', 'c_movement', 'c_botlogic', 'c_bin27', 'c_bin13',
    'c_decisions', 'c_mode', 'c_hit', 'c_canhit',
    'c_bmlogic_qa', 'c_bmlogic_q2', 'c_bmcombo_qa', 'c_bmcombo_q2',
    'c_sp_main_qa', 'c_sp_main_q2',
    # クリスタル系(compare.sh と同じ顔ぶれ)
    'c_qa_bin27', 'c_q2_bin27', 'c_qa_ctick', 'c_q2_ctick', 'c_crystaltick', 'c_crystaltick2',
    'c_spawncry', 'c_placeobby', 'c_chargeanchor', 'c_placeanchor', 'c_breakcry',
    'c_canhit', 'c_hit', 'c_bin14', 'c_anchortick', 'c_checktimer', 'c_mark',
    'c_anchortest', 'c_obbycheck',
    # can_hit の条件別
    'c_cansee', 'c_hurt0', 'c_block', 'c_noloc', 'c_hitdec', 'c_hitdec_ok',
]
# スコア(ラウンドの状態そのもの)
SCORES = ['.start start', '.mode mode', '.tempaim aim', '.difficulty difficulty',
          'quantumbot death', 'qbot2 death', 'quantumbot kit', 'qbot2 kit']


class Side:
    def __init__(self, name):
        port, pre = PORTS[name]
        self.name, self.pre = name, pre
        self.sock = socket.create_connection(('127.0.0.1', port), timeout=30)
        self.sock.sendall(rcon.pack(1, 3, 'parity'))
        rcon.read_packet(self.sock)
        self.i = 1

    def cmd(self, text, prefix=True):
        self.i += 1
        return rcon.command(self.sock, self.i, (self.pre + text) if prefix else text, self.i)

    def value(self, text, prefix=True):
        r = self.cmd(text, prefix)
        if ' has ' in r:
            return r.split(' has ')[1].split(' ')[0]
        if 'Can\'t get value' in r or 'Unknown' in r or 'No entity' in r:
            return '0'
        return r.strip()[:40]

    def close(self):
        self.sock.close()


def main():
    tag = sys.argv[1] if len(sys.argv) > 1 else 'cnt1'
    scenario = sys.argv[2] if len(sys.argv) > 2 else 'sword_k10v11'
    secs = sys.argv[3] if len(sys.argv) > 3 else '45'
    warm = sys.argv[4] if len(sys.argv) > 4 else '20'

    sides = {n: Side(n) for n in PORTS}
    for s in sides.values():
        for c in COUNTERS:            # 1 コマンドずつ(まとめて 1 行にすると構文エラーで無効)
            s.cmd('scoreboard players set .%s dbgc 0' % c, prefix=False)
    print('カウンタを 0 にした')

    r = subprocess.run([str(ROOT / 'tools' / 'parity-runner' / 'pair.sh'), tag, scenario, secs, warm],
                       cwd=str(ROOT), capture_output=True, text=True)
    print(r.stdout.strip()[-400:])

    reads = {}
    for name, s in sides.items():
        reads[name] = {c: s.value('scoreboard players get .%s dbgc' % c, prefix=False) for c in COUNTERS}
        reads[name + '_scores'] = {k: s.value('scoreboard players get ' + k, prefix=False) for k in SCORES}
    print('\n%-16s %10s %10s %8s' % ('counter', 'fabric', 'paper', '比'))
    for c in COUNTERS:
        f, p = reads['fabric'][c], reads['paper'][c]
        try:
            fv, pv = int(f), int(p)
            ratio = '%.2f' % (pv / fv) if fv else ('-' if pv == 0 else 'inf')
        except ValueError:
            ratio = '?'
        print('%-16s %10s %10s %8s' % (c, f, p, ratio))
    print()
    for k in SCORES:
        print('%-24s fabric=%-8s paper=%s' % (k, reads['fabric_scores'][k], reads['paper_scores'][k]))
    for s in sides.values():
        s.close()


if __name__ == '__main__':
    main()
