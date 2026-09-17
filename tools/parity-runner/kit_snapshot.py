#!/usr/bin/env python3
"""参照(Fabric) と移植(Paper) の BOT インベントリを突き合わせてからサーバーキット化する。

    tools/parity-runner/kit_snapshot.py <scenario> <MODE> <kit-name> [bot]

やること:
  1. 両エンジンで `parity:setup/<scenario>` を回し、BOT にロードアウトを着せる
  2. 両エンジンの BOT の全スロット(ホットバー/インベントリ/防具/オフハンド)をダンプ
  3. **差分を表示**し、一致していれば Paper 側の BOT を `/kit create` でスナップショット
  4. `/botadmin <MODE> <kit-name>` で紐づける

「Paper の BOT をそのままキットにする」だけだと、Paper 側が参照と違うロードアウトの
ときに**間違ったキットを正**として保存してしまう(実際にやらかした)。ここでは必ず
先に両エンジンを比べ、食い違っていればキットを作らずに終わる。
"""
import re
import socket
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools' / 'parity-runner'))
import rcon  # noqa: E402

HOTBAR_AND_STORAGE = list(range(9))
EQUIP = ['head', 'chest', 'legs', 'feet']


def conn(port):
    sock = socket.create_connection(('127.0.0.1', port), timeout=10)
    sock.sendall(rcon.pack(1, 3, 'parity'))
    rcon.read_packet(sock)
    return sock


def cmd(sock, body, rid=7, timeout=25.0):
    try:
        return rcon.command(sock, rid, body, timeout).strip()
    except Exception:
        return ''


def _leaf(sock, bot, path):
    """1 つの葉だけを引く。

    Paper の `data get entity <bot> Inventory[{Slot:Nb}]` は長い components を `"..."` で
    **省略して返す**ため、丸ごと取って文字列比較すると「Paper にはエンチャントが無い」と
    誤判定する(実際に踏んだ)。必要な部分だけを個別に引けば省略されない。
    """
    body = cmd(sock, 'data get entity %s %s' % (bot, path))
    if not body or 'Found no elements' in body or 'Found no' in body:
        return None
    return body.split('entity data: ', 1)[-1].strip()


ENCH_CANDIDATES = [
    'protection', 'fire_protection', 'feather_falling', 'blast_protection', 'projectile_protection',
    'respiration', 'aqua_affinity', 'thorns', 'depth_strider', 'frost_walker', 'binding_curse',
    'soul_speed', 'swift_sneak', 'sharpness', 'smite', 'bane_of_arthropods', 'knockback',
    'fire_aspect', 'looting', 'sweeping_edge', 'efficiency', 'silk_touch', 'unbreaking',
    'fortune', 'power', 'punch', 'flame', 'infinity', 'loyalty', 'impaling', 'riptide',
    'channeling', 'multishot', 'quick_charge', 'piercing', 'density', 'breach', 'wind_burst',
    'mending', 'vanishing_curse', 'lunge',
]


def enchants_at(sock, bot, base):
    """エンチャント名→レベルの辞書。

    Paper の RCON 応答は ~150 文字で `...` に切られるので、`enchantments` を丸ごと引くと
    後半のエンチャントが**無いように見える** (実際に踏んだ: ブーツの unbreaking が消えたと
    誤判定)。ここでは候補キーを 1 つずつ引く — 応答は数字だけなので絶対に切られない。
    """
    found = {}
    for key in ENCH_CANDIDATES:
        val = _leaf(sock, bot, base + '.components."minecraft:enchantments"."minecraft:%s"' % key)
        if val is None:
            continue
        m = re.match(r'^(-?\d+)', val.strip())
        if m:
            found[key] = int(m.group(1))
    return found


def _strip_ub(text):
    return text.replace('+ub', '').replace('ub+', '').replace('+ub+', '+')


def _describe(name, amount, ench, unbr, maxstack, potion):
    out = name + (('x' + amount) if amount != '1' else '')
    extra = []
    if ench:
        extra.append(','.join('%s%d' % (k, v) for k, v in sorted(ench.items())))
    if unbr:
        extra.append('ub')
    if maxstack:
        extra.append('max' + maxstack)
    if potion:
        extra.append('potion=' + potion)
    return out + ('+' + '+'.join(extra) if extra else '')


def item_at(sock, bot, slot):
    """スロットの中身を短い文字列にする(空なら '-')。"""
    base = 'Inventory[{Slot:%db}]' % slot
    ident = _leaf(sock, bot, base + '.id')
    if ident is None:
        return '-'
    name = ident.strip().strip('"').replace('minecraft:', '')
    count = _leaf(sock, bot, base + '.count')
    amount = (count or '1').strip().strip('"')
    unbr = _leaf(sock, bot, base + '.components."minecraft:unbreakable"') is not None
    maxstack = _leaf(sock, bot, base + '.components."minecraft:max_stack_size"')
    potion = _leaf(sock, bot, base + '.components."minecraft:potion_contents".potion')
    return _describe(name, amount, enchants_at(sock, bot, base), unbr,
                     maxstack.strip() if maxstack else '', potion.strip().strip('"') if potion else '')


def equip_at(sock, bot, slot):
    base = 'equipment.%s' % slot
    ident = _leaf(sock, bot, base + '.id')
    if ident is None:
        return '-'
    name = ident.strip().strip('"').replace('minecraft:', '')
    unbr = _leaf(sock, bot, base + '.components."minecraft:unbreakable"') is not None
    return _describe(name, '1', enchants_at(sock, bot, base), unbr, '', '')


def dump(side, sock, bot, slots):
    return {('slot', s): item_at(sock, bot, s) for s in slots} | \
           {('equip', s): equip_at(sock, bot, s) for s in EQUIP}


def main(argv):
    if len(argv) < 4:
        print(__doc__)
        return 2
    scenario, mode, kit = argv[1], argv[2], argv[3]
    bot = argv[4] if len(argv) > 4 else 'quantumbot'
    slots = HOTBAR_AND_STORAGE
    socks = {'fabric': conn(25575), 'paper': conn(25576)}
    setups = {'fabric': 'function parity:setup/' + scenario,
              'paper': 'quantum run function parity:setup/' + scenario}
    dumpers = {'fabric': lambda b, s: dump('fabric', socks['fabric'], b, s),
               'paper': lambda b, s: dump('paper', socks['paper'], b, s)}

    print('setup: %s (両エンジン)' % scenario)
    for side, sock in socks.items():
        cmd(sock, setups[side])
    time.sleep(9)    # start_round(schedule 40t) まで待つ

    # ラウンド中は BOT が死んで respawn し、その瞬間だけロードアウトが戻る。
    # 1 回だけ見ると「その瞬間の偶然」を差として拾ってしまう(実際に totem の
    # knockback で踏んだ)ので、間を空けて 2 回取り、両方で食い違うものだけを差とする。
    fabric1 = dumpers['fabric'](bot, slots)
    paper1 = dumpers['paper'](bot, slots)
    time.sleep(5)
    fabric2 = dumpers['fabric'](bot, slots)
    paper2 = dumpers['paper'](bot, slots)

    diff, churn, transient = [], [], []
    for key, want in fabric1.items():
        f_stable = fabric1[key] == fabric2[key]
        p_stable = paper1[key] == paper2[key]
        if fabric1[key] == paper1[key]:
            continue
        # ホットバーの `ub` は「装備を読む瞬間にメインハンドだったアイテム」に付く
        # パックの副作用(quantum:miscellaneous/unbreakable が weapon.mainhand を舐める)。
        # どちらが選ばれているかは brain のタイミング次第で、装備そのものの差ではない。
        if (key[0] == 'slot' and key[1] < 9
                and _strip_ub(fabric1[key]) == _strip_ub(paper1[key])):
            transient.append((key, fabric1[key], paper1[key]))
            continue
        if f_stable and p_stable:
            diff.append((key, fabric1[key], paper1[key]))
        else:
            churn.append((key, fabric1[key], paper1[key], fabric2[key], paper2[key]))
    print('=== 参照(Fabric) vs 移植(Paper): %d スロット中 不一致 %d / 揺れ %d'
          % (len(fabric1), len(diff), len(churn)))
    for key, want, got in diff:
        print('   %-12s fabric=%-46s paper=%s' % ('%s %s' % key, want, got))
    for key, f1, p1, f2, p2 in churn:
        print('   (揺れ) %-9s fabric=%s→%s paper=%s→%s' % ('%s %s' % key, f1, f2, p1, p2))
    for key, f1, p1 in transient:
        print('   (過渡) %-9s ub の有無だけの差: fabric=%s paper=%s' % ('%s %s' % key, f1, p1))
    if diff:
        print('!! ロードアウトが違うのでキット化しない(先にこれを直す)')
        return 1
    if churn:
        print('   ※ 揺れは respawn/チェスト再読込の途中経過。キット化は進める')

    print('=== 一致。Paper の BOT をキット化して紐づける')
    print(' ', cmd(socks['paper'], 'kit create %s %s' % (kit, bot))[:120])
    print(' ', cmd(socks['paper'], 'botadmin %s %s' % (mode, kit))[:120])
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
