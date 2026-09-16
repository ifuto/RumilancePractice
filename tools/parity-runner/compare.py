#!/usr/bin/env python3
"""Fabric と Paper で同じシナリオを走らせ、Bot の内部カウンタ・状態を左右に並べる。

    python3 tools/parity-runner/compare.py [scenario] [seconds] [--fab-port N] [--paper-port N]

Fabric = 25565 / RCON 25575、Paper = 25566 / RCON 25576 が既定。Paper 側は
``quantum run `` を前置して実行し、コマンドの戻り値はサーバーログに出るので
ログも一緒に読む(この辺の経路差は docs/parity/README.md を参照)。

読み取りは 1 コマンドずつ(RCON 応答 → ログの順に探す)。まとめて投げると
どちらかの経路で取りこぼしが出て、値が 1 つずつズレるため。
"""
from __future__ import annotations

import argparse
import re
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
RCON = str(ROOT / 'tools' / 'parity-runner' / 'rcon.py')

COUNTERS = [
    'q2_bin27', 'qa_bin27', 'q2_ctick', 'qa_ctick',
    'bin27', 'botlogic', 'movement', 'look', 'crystaltick', 'crystaltick2',
    'canhit', 'hit', 'mode', 'checktimer', 'anchortick', 'bin14',
    'spawncry', 'placeobby', 'chargeanchor', 'placeanchor', 'breakcry',
    'anchortest', 'mark', 'obbycheck', 'rounds',
    'l1', 'l2', 'l3', 'l4', 'l5', 'l6', 'l7', 'l8', 'l9',
    'l10', 'l11', 'l12', 'l13', 'l14', 'l15', 'l16', 'l17',
]
PLAYERS = ['quantumbot', 'qbot2']
PLAYER_READS = [
    ('health', 'execute as %s run data get entity @s Health'),
    ('pos', 'execute as %s run data get entity @s Pos'),
    ('item', 'execute as %s run data get entity @s Inventory[0].id'),
    ('crystal_timer', 'scoreboard players get %s crystal_timer'),
    ('totem_timer', 'scoreboard players get %s totem_timer'),
    ('state', 'scoreboard players get %s state'),
    ('hitcd', 'scoreboard players get %s hitcd'),
    ('death', 'scoreboard players get %s death'),
]


def clean(line: str) -> str:
    line = re.sub(r'^\[[^\]]*\] \[[^\]]*\]: ', '', line)
    line = re.sub(r'^\[[^\]]* INFO\]: ', '', line)
    for code in ('§b', '§a', '§c', '§r', '§f', '§7'):
        line = line.replace(code, '')
    return line.strip()


def detect_value(text: str) -> str | None:
    if 'has the following entity data:' in text:
        return text.split('has the following entity data:', 1)[1].strip()
    m = re.search(r'\bhas (?:the following entity data: )?(.+?)(?: \[.*)?$', text)
    if m:
        return m.group(1).strip()
    m = re.search(r'\bnone is set', text)
    if m:
        return 'UNSET'
    return None


class Side:
    def __init__(self, name: str, rcon_port: int, log: Path, prefix: str):
        self.name = name
        self.rcon_port = rcon_port
        self.log = log
        self.prefix = prefix

    def _log_size(self) -> int:
        try:
            return self.log.stat().st_size
        except OSError:
            return 0

    def _log_since(self, offset: int) -> list[str]:
        try:
            with self.log.open('r', errors='ignore') as fh:
                fh.seek(offset)
                return [clean(l) for l in fh]
        except OSError:
            return []

    def _rcon(self, command: str) -> str:
        out = subprocess.run([sys.executable, RCON, str(self.rcon_port), 'parity',
                              self.prefix + command],
                             capture_output=True, text=True, timeout=60).stdout
        return clean(out.split('\n', 1)[0])

    def send(self, commands: list[str]) -> None:
        for command in commands:
            self._rcon(command)

    def read(self, command: str, settle: float = 0.25) -> str:
        """1 コマンド実行して値だけ返す。

        Fabric は応答がそのまま返るので RCON の応答だけを使う(ログを混ぜると、
        古い行を拾って「0 にした直後に 1 万が返る」ような嘘を読む — 実際に踏んだ)。
        Paper は `quantum run` を通すため応答がログに出る。こちらは今回の
        コマンドの対象名(スコア保持者/エンティティ名)を含む行だけを拾う。
        """
        subject = command.rsplit(' ', 2)[-2] if ' ' in command else command
        offset = self._log_size()
        reply = self._rcon(command)
        value = detect_value(reply)
        if value is not None and value != 'UNSET' and not reply.startswith('§'):
            return value
        if not self.prefix:
            return value if value is not None else '?'
        time.sleep(settle)
        for line in self._log_since(offset):
            if subject and subject not in line:
                continue
            value = detect_value(line)
            if value is not None:
                return value
        return value if value is not None else '?'


def measure(side: Side, scenario: str, seconds: int, settle: float = 0.25) -> dict[str, str]:
    """1 シナリオを走らせ、'カウンタを 0 にした時点から' の値を読む。

    実測で踏んだ罠:
      - start_round はカウンタを 0 にしない(計測側で 0 にする)。
      - ラウンド開始は setup の 2 秒後ではなく、マップの main_tick が
        xlib_target を見失って reset したあとの keepalive 経由になることがあり、
        十数秒かかることがある。そのため「.c_mode が実際に伸びたこと」を
        確認してから計測窓に入る(0 のまま測ると全部 0 になる)。
    """
    side.send(['function parity:load'])
    time.sleep(0.5)
    # 残っているラウンドを落として、新しいラウンドを立てさせる。
    side.send(['scoreboard players set .start start 0'])
    side.send([f'scoreboard players set .{c} dbgc 0' for c in COUNTERS])
    side.send([f'function parity:setup/{scenario}'])

    deadline = time.time() + 60
    while time.time() < deadline:
        time.sleep(2)
        try:
            running = int(side.read('scoreboard players get .c_mode dbgc', settle) or 0) > 100
        except ValueError:
            running = False
        if running:
            break

    time.sleep(seconds)
    out: dict[str, str] = {}
    for counter in COUNTERS:
        out[f'.{counter}'] = side.read(f'scoreboard players get .{counter} dbgc', settle)
    for player in PLAYERS:
        for label, template in PLAYER_READS:
            out[f'{player}.{label}'] = side.read(template % player, settle)
    return out


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument('scenario', nargs='?', default='crystal_k10v11')
    parser.add_argument('seconds', nargs='?', type=int, default=25)
    parser.add_argument('--fab-port', type=int, default=25575)
    parser.add_argument('--paper-port', type=int, default=25576)
    parser.add_argument('--fab-log', default='/tmp/mcref/mcserver/console.log')
    parser.add_argument('--paper-log', default='/tmp/testsrv/logs/latest.log')
    parser.add_argument('--settle', type=float, default=0.25)
    args = parser.parse_args()

    fabric = Side('fabric', args.fab_port, Path(args.fab_log), '')
    paper = Side('paper', args.paper_port, Path(args.paper_log), 'quantum run ')
    print(f'== scenario {args.scenario}  ({args.seconds}s)')
    fab = measure(fabric, args.scenario, args.seconds, args.settle)
    pap = measure(paper, args.scenario, args.seconds, args.settle)

    print(f'{"key":<24} {"fabric":>18} {"paper":>18}   diff')
    print('-' * 70)
    for key in list(dict.fromkeys(list(fab) + list(pap))):
        f, p = fab.get(key, '-'), pap.get(key, '-')
        try:
            fv, pv = float(f), float(p)
            if fv == pv:
                diff = '='
            elif fv == 0:
                diff = '×inf'
            else:
                diff = '×%.2f' % (pv / fv)
        except ValueError:
            diff = '=' if f == p else '≠'
        flag = '' if diff in ('=', '≠') else '  <-- 差'
        if diff == '≠' and not (f.startswith('[', 0) or p.startswith('[', 0)):
            flag = ''
        print(f'{key:<24} {str(f)[:18]:>18} {str(p)[:18]:>18}   {diff}{flag}')
    return 0


if __name__ == '__main__':
    sys.exit(main())
