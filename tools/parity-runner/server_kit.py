#!/usr/bin/env python3
"""PvP サーバー本線: BOT のロードアウトは *サーバーキット* (`/botadmin`) から与える。

    python3 tools/parity-runner/server_kit.py [crystal|sword] [--apply] [--server DIR]

サンドボックスが再起動すると `/tmp` ごと環境が消えるので、キットの投入も 1 コマンドで
やり直せるようにしておく (`env_up.sh` から呼ばれる)。やること:

  1. `<server>/plugins/n-arena/kits.yml` にキットを upsert
     (クリスタルはパックの脳が切り替える hotbar の位置に合わせた正規配置)
  2. `<server>/plugins/NARENA/quantum.yml` の `bot.mode` を切り替え
     (マップが spawn する BOT にサーバーキットを着せる側の設定)
  3. `--apply` なら RCON で `/botadmin <MODE> <kit>` を打って紐づけを永続化

キットの中身は「参照パックのキットチェストと同義」であることが大事:
パリティ計測は両エンジンで同じ初期装備でないと成立しないため。
"""
from __future__ import annotations

import argparse
import re
import socket
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools' / 'parity-runner'))
import rcon  # noqa: E402

PAPER = Path('/tmp/testsrv')
RCON_PORT = 25576

# クリスタル: `quantum:g1gc/*` と `quantum:crystal/*` の `player @s hotbar N` から逆算した契約
#   hotbar 1=トーテム 2=黒曜石 3=エンドクリスタル 4=殴り(剣) 5=金リンゴ 7=パール 8=アンカー 9=グロウストーン
CRYSTAL_KIT = """
  # クリスタル戦の正規ロードアウト。参照パックの `quantum:botgear/dia` (mode 2) と同一にしてある
  # (スロット = inventory index。パックの脳が `player @s hotbar N` で切り替える位置):
  #   1=トーテム 2=黒曜石 3=エンドクリスタル 4=殴り(剣) 5=金リンゴ 6=斧 7=パール 8=アンカー 9=グロウストーン
  # エンチャントは `enchantments:` でそのまま書ける (kits.yml は Base64 の data: しか
  # 持てなかったので、剣の knockback:1 が落ちて間合いが崩れていた)。
  # 防具は `armor:` ではなく slot 36-39 のアイテムとして定義する (armor: はエンチャントを
  # 表現できないが、アイテム定義なら付く)。
  crystal:
    display-name: "Crystal"
    icon: END_CRYSTAL
    enabled: true
    items:
      - slot: 0
        material: TOTEM_OF_UNDYING
        amount: 1
      - slot: 1
        material: OBSIDIAN
        amount: 64
        enchantments: {knockback: 1}
      - slot: 2
        material: END_CRYSTAL
        amount: 64
        enchantments: {knockback: 1}
      - slot: 3
        material: DIAMOND_SWORD
        amount: 1
        enchantments: {sharpness: 5, knockback: 1}
      - slot: 4
        material: GOLDEN_APPLE
        amount: 64
      - slot: 5
        material: DIAMOND_AXE
        amount: 1
        enchantments: {sharpness: 5}
      - slot: 6
        material: ENDER_PEARL
        amount: 16
        enchantments: {knockback: 1}
      - slot: 7
        material: RESPAWN_ANCHOR
        amount: 64
        enchantments: {knockback: 1}
      - slot: 8
        material: GLOWSTONE
        amount: 64
        enchantments: {knockback: 1}
      - slot: 9
        material: TIPPED_ARROW
        amount: 99
      - slot: 10
        material: TIPPED_ARROW
        amount: 99
      - slot: 11
        material: WATER_BUCKET
        amount: 1
      - slot: 12
        material: WATER_BUCKET
        amount: 1
      - slot: 14
        material: WATER_BUCKET
        amount: 1
      - slot: 16
        material: LAVA_BUCKET
        amount: 1
      - slot: 36
        material: DIAMOND_HELMET
        amount: 1
        enchantments: {protection: 4, unbreaking: 3}
      - slot: 37
        material: DIAMOND_CHESTPLATE
        amount: 1
        enchantments: {protection: 4, unbreaking: 3}
      - slot: 38
        material: DIAMOND_LEGGINGS
        amount: 1
        enchantments: {blast_protection: 4, unbreaking: 3}
      - slot: 39
        material: DIAMOND_BOOTS
        amount: 1
        enchantments: {feather_falling: 4, protection: 4}
      - slot: 40
        material: TOTEM_OF_UNDYING
        amount: 1
"""

BINDINGS = {'crystal': ('CRYSTAL', 'crystal'), 'sword': ('SWORD', 'sword_only')}


def upsert_kit(text: str, name: str, block: str) -> str:
    """同名キットがあれば差し替え、無ければ `kits:` の直後に足す。"""
    pattern = re.compile(r'^  %s:\n(?:    .*\n|      .*\n)*' % re.escape(name), re.M)
    if pattern.search(text):
        return pattern.sub(block.lstrip('\n'), text, count=1)
    if re.search(r'^kits:\s*\{\}\s*$', text, re.M):
        return re.sub(r'^kits:\s*\{\}\s*$', 'kits:' + block, text, count=1, flags=re.M)
    if re.search(r'^kits:\s*$', text, re.M):
        return re.sub(r'^kits:\s*$', 'kits:', text, count=1, flags=re.M) + block.lstrip('\n')
    return text + '\nkits:' + block


def set_bot_mode(path: Path, mode: str) -> None:
    """`bot:` セクションの `mode:` を書き換える。

    出荷の quantum.yml には `bot:` が 2 つある (spawn 用と mode/kit 用) ので、
    «最後の» `bot:` ブロックを対象にする — YAML は後勝ちなので、実際に効くのはそちら。
    """
    lines = path.read_text().splitlines()
    starts = [i for i, line in enumerate(lines) if line.strip() == 'bot:' and not line.startswith(' ')]
    if not starts:
        lines.append('bot:')
        lines.append('  mode: %s' % mode)
        path.write_text('\n'.join(lines) + '\n')
        return
    start = starts[-1]
    end = len(lines)
    for i in range(start + 1, len(lines)):
        if lines[i].strip() and not lines[i].startswith(' '):
            end = i
            break
    for i in range(start + 1, end):
        if re.match(r'^\s+mode:\s*', lines[i]):
            lines[i] = '  mode: %s' % mode
            break
    else:
        lines.insert(start + 1, '  mode: %s' % mode)
    path.write_text('\n'.join(lines) + '\n')


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument('kit', nargs='?', default='crystal', choices=sorted(BINDINGS))
    ap.add_argument('--apply', action='store_true', help='RCON で /botadmin も打つ')
    ap.add_argument('--server', default=str(PAPER))
    args = ap.parse_args()

    server = Path(args.server)
    kits = server / 'plugins' / 'n-arena' / 'kits.yml'
    quantum = server / 'plugins' / 'NARENA' / 'quantum.yml'
    if not kits.exists():
        print('kits.yml がない: %s (サーバーを一度起動すると生成される)' % kits)
        return 1

    mode, kit_name = BINDINGS[args.kit]
    block = {'crystal': CRYSTAL_KIT}.get(args.kit)
    if block:
        kits.write_text(upsert_kit(kits.read_text(), kit_name, block))
        print('kits.yml に %s を upsert' % kit_name)
    if quantum.exists():
        set_bot_mode(quantum, mode)
        print('quantum.yml: bot.mode = %s' % mode)

    print('紐づけコマンド: /botadmin %s %s' % (mode, kit_name))
    if args.apply:
        s = socket.create_connection(('127.0.0.1', RCON_PORT), timeout=20)
        s.sendall(rcon.pack(1, 3, 'parity'))
        rcon.read_packet(s)
        print(rcon.command(s, 2, 'botadmin %s %s' % (mode, kit_name), 2).strip()[:200])
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
