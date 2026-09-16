#!/usr/bin/env python3
"""1 ラウンド中の Bot の内部状態を毎秒サンプルして左右に並べる。

    python3 tools/parity-runner/probe_bots.py [scenario] [seconds] [--side both]

実装は `parity:diag`(ワールドのパリティパックに入っている関数)1 発で
「両 Bot の位置・速度・HP・スコア + マーカー数」を **ログ 1 行** にまとめて出す。
1 サンプル = RCON 1 コマンドなので、毎秒サンプルしてもラウンドを乱さない。
"""
from __future__ import annotations

import argparse
import re
import sys
import time
from pathlib import Path
import importlib.util

ROOT = Path(__file__).resolve().parent.parent.parent
spec = importlib.util.spec_from_file_location('compare', ROOT / 'tools/parity-runner/compare.py')
compare = importlib.util.module_from_spec(spec)
spec.loader.exec_module(compare)

DIAG = re.compile(r'DIAG (.*)$')


class DiagSide(compare.Side):
    def diag(self, timeout: float = 1.5) -> dict[str, float | None] | None:
        """parity:diag を 1 回叩き、出てくる 2 行(A / B+マーカー)を合成して返す。"""
        offset = self._log_size()
        self._rcon('function parity:diag')
        texts: list[str] = []
        deadline = time.time() + timeout
        while time.time() < deadline and len(texts) < 2:
            for line in self._log_since(offset):
                m = DIAG.search(line)
                if m and m.group(1) not in texts:
                    texts.append(m.group(1))
            if len(texts) < 2:
                time.sleep(0.1)
        if not texts:
            return None
        out: dict[str, float | None] = {}
        for text in texts:
            out.update(fields(text))
        return out


def start_round(side: DiagSide, scenario: str) -> None:
    side.send(['function parity:load'])
    time.sleep(0.5)
    side.send(['scoreboard players set .start start 0'])
    side.send([f'function parity:setup/{scenario}'])


def safe(item: str) -> float | None:
    try:
        return float(item)
    except ValueError:
        return None


def fields(text: str) -> dict[str, float | None]:
    """DIAG の 1 行を平坦化する。

    1 行目: ``A=… | mk …`` / 2 行目: ``B=…``。区画の先頭トークンの名前で prefix を決める。
    """
    out: dict[str, float | None] = {}
    for part in (p.strip() for p in text.split('|')):
        first = part.split(' ', 1)[0].partition('=')[0]
        prefix = {'A': 'a', 'B': 'b'}.get(first, 'm')
        for token in part.split():
            key, _, value = token.partition('=')
            if not value:
                continue
            key = key if key in ('A', 'B') else key
            parts = value.split(',')
            if key == 'v' and len(parts) == 2:
                out[prefix + 'vx'], out[prefix + 'vz'] = safe(parts[0]), safe(parts[1])
            elif len(parts) == 3:
                out[prefix + 'x'], out[prefix + 'y'], out[prefix + 'z'] = \
                    safe(parts[0]), safe(parts[1]), safe(parts[2])
            else:
                out[prefix + key] = safe(value)
    return out


def speed(values, prefix) -> float:
    vx, vz = values.get(prefix + 'vx', 0.0) or 0.0, values.get(prefix + 'vz', 0.0) or 0.0
    return (vx * vx + vz * vz) ** 0.5 * 20.0


def probe(side: DiagSide, scenario: str, seconds: int, label: str) -> None:
    start_round(side, scenario)
    deadline = time.time() + 45
    while time.time() < deadline:
        time.sleep(2)
        v = side.diag()
        if v and (v.get('ast') or 0) >= 1:
            break
    print(f'--- {side.name} ({label})')
    start = time.time()
    while time.time() - start < seconds:
        v = side.diag()
        if v is None:
            print('t+%02ds (no diag)' % int(time.time() - start))
            continue
        dist = (((v.get('ax') or 0) - (v.get('bx') or 0)) ** 2
                + ((v.get('ay') or 0) - (v.get('by') or 0)) ** 2
                + ((v.get('az') or 0) - (v.get('bz') or 0)) ** 2) ** 0.5
        print('t+%02ds A(%.1f,%.1f,%.1f v=%.2f) B(%.1f,%.1f,%.1f v=%.2f) d=%.2f '
              'A{hp=%.1f ct=%.0f st=%.0f p1=%.0f hit=%.0f} B{hp=%.1f ct=%.0f st=%.0f p1=%.0f hit=%.0f} '
              'mk(loc=%.0f usable=%.0f all=%.0f)' % (
                  int(time.time() - start),
                  v.get('ax') or 0, v.get('ay') or 0, v.get('az') or 0, speed(v, 'a'),
                  v.get('bx') or 0, v.get('by') or 0, v.get('bz') or 0, speed(v, 'b'),
                  dist,
                  v.get('ahp') or 0, v.get('acrt') or 0, v.get('ast') or 0, v.get('ap1') or 0,
                  v.get('ahit') or 0,
                  v.get('bhp') or 0, v.get('bcrt') or 0, v.get('bst') or 0, v.get('bp1') or 0,
                  v.get('bhit') or 0,
                  v.get('mloc') or 0, v.get('musable') or 0, v.get('mmall') or 0), flush=True)
        time.sleep(1.0)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument('scenario', nargs='?', default='crystal_k10v11')
    parser.add_argument('seconds', nargs='?', type=int, default=15)
    parser.add_argument('--side', default='both', choices=('both', 'fabric', 'paper'))
    parser.add_argument('--fab-port', type=int, default=25575)
    parser.add_argument('--paper-port', type=int, default=25576)
    args = parser.parse_args()

    sides = []
    if args.side in ('both', 'fabric'):
        sides.append(DiagSide('fabric', args.fab_port,
                              Path('/tmp/mcref/mcserver/console.log'), ''))
    if args.side in ('both', 'paper'):
        sides.append(DiagSide('paper', args.paper_port,
                              Path('/tmp/testsrv/logs/latest.log'), 'quantum run '))
    for side in sides:
        probe(side, args.scenario, args.seconds, 'setup+開始待ち')
    return 0


if __name__ == '__main__':
    sys.exit(main())
