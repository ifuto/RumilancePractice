#!/usr/bin/env python3
"""ラウンドを開始して、両エンジンの状態を数秒おきに生で読む(ラウンドが成立しているかを見る)。

    python3 tools/parity-runner/watch_round.py [scenario] [seconds] [interval]

読むもの: .start / pari_round / .c_rounds(ラウンド開始回数) / 各BOTの hp・death・
hitcd・real_hitcd・state・tempcrit / 視点(yaw,pitch) / 手持ち。
"""
from __future__ import annotations

import socket
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools' / 'parity-runner'))
import rcon  # noqa: E402

PORTS = {'fabric': (25575, ''), 'paper': (25576, 'quantum run ')}
BOTS = ['quantumbot', 'qbot2']


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

    def val(self, text):
        r = self.cmd(text, prefix=False)
        if ' has ' in r:
            return r.split(' has ')[1].split(' ')[0]
        if "Can't get value" in r or 'Unknown' in r or 'No entity' in r:
            return '-'
        return r.strip()[:32]

    def close(self):
        self.sock.close()


def state(s: Side):
    out = {}
    for k in ('.start start', 'pari_round parity_t', '.c_rounds dbgc'):
        out[k] = s.val('scoreboard players get ' + k)
    for b in BOTS:
        out[b + '.hp'] = s.val('data get entity %s Health' % b).split('f')[0]
        for sc in ('death', 'hitcd', 'real_hitcd', 'state', 'tempcrit', 'toggles'):
            out['%s.%s' % (b[:4], sc)] = s.val('scoreboard players get %s %s' % (b, sc))
        out[b[:4] + '.rot'] = s.val('data get entity %s Rotation' % b)
        out[b[:4] + '.item'] = s.val('data get entity %s SelectedItem' % b)
    return out


def main():
    scenario = sys.argv[1] if len(sys.argv) > 1 else 'sword_k10v11'
    secs = float(sys.argv[2]) if len(sys.argv) > 2 else 45
    interval = float(sys.argv[3]) if len(sys.argv) > 3 else 3
    sides = {n: Side(n) for n in PORTS}
    for s in sides.values():
        s.cmd('scoreboard players set .c_rounds dbgc 0', prefix=False)
        s.cmd('function parity:setup/%s' % scenario, prefix=True)
    print('setup 送信 → %.0f 秒観測' % secs)
    t0 = time.time()
    keys = None
    while time.time() - t0 < secs:
        time.sleep(interval)
        rows = {n: state(s) for n, s in sides.items()}
        if keys is None:
            keys = list(rows['fabric'])
            print('  t  側  ' + ' '.join('%-12s' % k for k in keys))
        for n, r in rows.items():
            print('  %2.0f %-7s' % (time.time() - t0, n) + ' '.join('%-12s' % r[k] for k in keys))
    for s in sides.values():
        s.close()


if __name__ == '__main__':
    main()
