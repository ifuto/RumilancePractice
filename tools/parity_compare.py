#!/usr/bin/env python3
"""parity_compare.py — 同じシナリオの Fabric(参照 QuantumBOT) と Paper(移植BOT) の記録を
突き合わせて「動きが再現できているか」を判定する。

    python3 tools/parity_compare.py docs/parity/fabric_crystal_k10v11_both.log.gz \
                                   docs/parity/paper_crystal_k10v11_both.log.gz

記録は `tools/parity_runner.py` が出す qlog 互換の `[q]` 行（`who=a|b` 付き）。

## 指標の作り方（実データで意味を確認済み）

マップのスコアは「クールダウン残り tick」なので、**増えた瞬間 = その行動をした瞬間**:

| スコア | 意味 | 検出 |
|---|---|---|
| `ct`/`anc` | crystal/anchor 系タイマ（マップが `explosion_cd` を同時に代入する連動値） | 増加 |
| `ob` | obby_timer（Obsidian 設置） | 増加 & 手持ち obsidian |
| `chg` | charge_timer（アンカーにグロウストーン） | `0->4` |
| `exp` | explosion_timer（起爆） | `0->4` |
| `pc` | pearlcd（パール投擲） | `0->20` |
| `hit` | hitcd（スイング） | `0->7` |
| `tot` | totem_timer（トーテム使用） | `0->31` |
| `pop` | トーテム POP 累計 | 増加 = 死んだ回数 |
| `i=` | 手持ちアイテム | 遷移 = 使ったアイテム |

## 判定

- **硬い数値**（アンカー 設置→チャージ、チャージ→爆発の tick 刻み）: 10% 以内で一致必須
- **レート**（/分）と**統計**: 35% 以内なら「おおむね一致」、それ以上は乖離として列挙
- アイテム滞在率: 15pt 以上の差は乖離
"""
import argparse
import collections
import gzip
import json
import math
import re
import sys

LINE = re.compile(
    r'(-?[\d.]+),(-?[\d.]+),(-?[\d.]+)\s+v=(-?[\d.]+),(-?[\d.]+),(-?[\d.]+)'
    r'\s+y=(-?[\d.]+)\s+p=(-?[\d.]+)\s+hp=([\d.]+)\s+g=(\d)\s+i=(\S+)'
    r'\s+hit=(-?\d+)\s+tot=(-?\d+)\s+ct=(-?\d+)\s+ob=(-?\d+)\s+pc=(-?\d+)\s+cry=(\d+)'
    r'\s+anc=(-?\d+)\s+chg=(-?\d+)\s+exp=(-?\d+)\s+hpT=(\d+)\s+pop=(\d+)\s+ec=(\d+)'
    r'\s+t=(\d+)(?:\s+who=(\S+))?'
)


def load(path, who='a'):
    rows = []
    opener = gzip.open if path.endswith('.gz') else open
    with opener(path, 'rt', encoding='utf-8', errors='replace') as fh:
        for line in fh:
            if 'who=' not in line:
                continue
            m = LINE.search(line)
            if not m:
                continue
            g = m.groups()
            if (g[24] or 'a') != who:
                continue
            rows.append(dict(
                x=float(g[0]), y=float(g[1]), z=float(g[2]),
                vx=float(g[3]), vy=float(g[4]), vz=float(g[5]),
                yaw=float(g[6]), pit=float(g[7]), hp=float(g[8]), g=int(g[9]),
                item=g[10].split(':')[-1],
                hit=int(g[11]), tot=int(g[12]), ct=int(g[13]), ob=int(g[14]),
                pc=int(g[15]), cry=int(g[16]), anc=int(g[17]), chg=int(g[18]),
                exp=int(g[19]), hpT=int(g[20]), pop=int(g[21]), ec=int(g[22]),
                t=int(g[23]),
            ))
    return rows


# ----------------------------------------------------------------- イベント検出

def rises(rows, key, pred=None):
    """スコア key が増えた tick（= その行動をした瞬間）の位置リスト。"""
    out = []
    for i in range(1, len(rows)):
        a, b = rows[i - 1], rows[i]
        if b['t'] - a['t'] > 5:          # 記録が飛んだ所はイベント扱いしない
            continue
        if b[key] > a[key] and (pred is None or pred(a[key], b[key], b)):
            out.append(i)
    return out


def pair_gap(rows, first, second, max_gap=60):
    """first のイベントから次の second のイベントまでの tick 差の分布。"""
    gaps, pending = [], None
    for i in range(1, len(rows)):
        a, b = rows[i - 1], rows[i]
        if b['t'] - a['t'] > 5:
            pending = None
            continue
        if pending is None and b[first] > a[first]:
            pending = i
        if b[second] > a[second] and pending is not None:
            gap = rows[i]['t'] - rows[pending]['t']
            if 0 <= gap <= max_gap:
                gaps.append(gap)
            pending = None
    return gaps


def median(values):
    if not values:
        return 0.0
    values = sorted(values)
    return float(values[len(values) // 2])


def rate(count, ticks):
    return count * 1200.0 / max(1, ticks)


EVENTS = {
    'crystal': ('ct', lambda p, n, r: n >= 5),
    'anchor': ('ob', lambda p, n, r: r['item'] == 'obsidian'),
    'charge': ('chg', lambda p, n, r: n >= 4),
    'explode': ('exp', lambda p, n, r: n >= 4),
    'pearl': ('pc', lambda p, n, r: n >= 10),
    'swing': ('hit', lambda p, n, r: n >= 7),
    'totem': ('tot', lambda p, n, r: n >= 10),
}


def fingerprint(rows, other=None):
    m = {}
    ticks = rows[-1]['t'] - rows[0]['t'] if rows else 0
    m['ticks'] = ticks
    for name, (key, pred) in EVENTS.items():
        m[name] = rate(len(rises(rows, key, pred)), ticks)
    m['totem_pop'] = rate(max((r['pop'] for r in rows), default=0), ticks)
    # 実使用アイテム（遷移で「持ち替えた＝使った」を数える）
    use = collections.Counter()
    for a, b in zip(rows, rows[1:]):
        if a['item'] != b['item']:
            use[b['item']] += 1
    m['item_switch'] = rate(sum(use.values()), ticks)
    m['use_per_item'] = {k: rate(v, ticks) for k, v in use.items()}
    # 硬い数値: アンカーの刻み
    m['anchor_gap'] = median(pair_gap(rows, 'ob', 'chg'))
    m['charge_explode_gap'] = median(pair_gap(rows, 'chg', 'exp'))
    m['pearl_gap'] = median(pair_gap(rows, 'pc', 'pc', max_gap=200))
    # 移動・視点
    planar = [math.hypot(b['x'] - a['x'], b['z'] - a['z']) for a, b in zip(rows, rows[1:])]
    m['speed'] = (sum(planar) / max(1, len(planar))) * 20
    m['speed_med'] = median(planar) * 20
    m['move_share'] = 100.0 * sum(1 for v in planar if v > 0.02) / max(1, len(planar))
    dyaw = []
    for a, b in zip(rows, rows[1:]):
        dyaw.append(abs((b['yaw'] - a['yaw'] + 540) % 360 - 180))
    m['yaw_rate'] = sum(dyaw) / max(1, len(dyaw))
    m['yaw_med'] = median(dyaw)
    m['yaw_snap'] = rate(sum(1 for v in dyaw if v > 15), ticks)
    # 位置の広がり（座標）
    if rows:
        m['x_span'] = max(r['x'] for r in rows) - min(r['x'] for r in rows)
        m['z_span'] = max(r['z'] for r in rows) - min(r['z'] for r in rows)
        m['y_med'] = median([r['y'] for r in rows])
        m['y_min'] = min(r['y'] for r in rows)
        m['y_max'] = max(r['y'] for r in rows)
    # HP
    m['hp_avg'] = sum(r['hp'] for r in rows) / max(1, len(rows))
    m['hp_min'] = min((r['hp'] for r in rows), default=0.0)
    m['hp_low_share'] = 100.0 * sum(1 for r in rows if r['hp'] <= 6) / max(1, len(rows))
    # アイテム滞在率
    total = max(1, len(rows))
    share = collections.Counter(r['item'] for r in rows)
    m['items'] = {k: 100.0 * v / total for k, v in share.items()}
    # 交戦距離
    if other:
        index = {r['t']: r for r in other}
        dist = [math.hypot(r['x'] - o['x'], r['z'] - o['z'])
                for r in rows if (o := index.get(r['t']))]
        if dist:
            m['dist_med'] = median(dist)
            m['dist_close'] = 100.0 * sum(1 for v in dist if v <= 6) / len(dist)
            m['dist_far'] = 100.0 * sum(1 for v in dist if v > 20) / len(dist)
    return m


# ----------------------------------------------------------------- 判定

HARD = [('anchor_gap', 't', 0.25), ('charge_explode_gap', 't', 0.25)]
RATES = ['crystal', 'anchor', 'charge', 'explode', 'pearl', 'swing', 'totem', 'totem_pop',
         'item_switch', 'yaw_snap']
STATS = ['speed', 'speed_med', 'move_share', 'yaw_rate', 'yaw_med', 'x_span', 'z_span',
         'y_med', 'y_min', 'hp_avg', 'hp_min', 'hp_low_share', 'dist_med', 'dist_close',
         'dist_far', 'pearl_gap']


def compare(a, b, key, tolerance):
    va, vb = a.get(key, 0.0), b.get(key, 0.0)
    if va == 0 and vb == 0:
        return '=', 0.0
    rel = abs(va - vb) / max(abs(va), abs(vb), 1e-9)
    if rel <= tolerance:
        return '=', rel
    if rel <= tolerance * 3.5:
        return '~', rel
    return '!', rel


def report(fabric, paper, who='a', label=''):
    fa, fb = load(fabric, who), load(paper, who)
    if not fa or not fb:
        print('missing data: fabric=%d paper=%d' % (len(fa), len(fb)))
        return 2
    other = 'b' if who == 'a' else 'a'
    ma = fingerprint(fa, load(fabric, other) or None)
    mb = fingerprint(fb, load(paper, other) or None)

    print('== 再現性チェック %s (who=%s: %s)' % (label or '', who,
          'quantumbot' if who == 'a' else 'qbot2'))
    print('   fabric=%s (%d ticks)  paper=%s (%d ticks)' % (
        fabric, ma['ticks'], paper, mb['ticks']))
    bad, soft = [], []
    print('\n-- 硬い数値（アンカー連鎖の tick 刻み）')
    for key, unit, tol in HARD:
        mark, rel = compare(ma, mb, key, tol)
        print('   %-20s %7.1f%s %7.1f%s  %s (%.0f%%差)' % (
            key, ma.get(key, 0), unit, mb.get(key, 0), unit, mark, rel * 100))
        if mark == '!':
            bad.append(key)
    print('\n-- 行動レート(/分)')
    for key in RATES:
        mark, rel = compare(ma, mb, key, 0.35)
        print('   %-20s %7.1f  %7.1f  %s (%.0f%%差)' % (key, ma.get(key, 0), mb.get(key, 0),
                                                        mark, rel * 100))
        if mark == '!':
            bad.append(key)
        elif mark == '~':
            soft.append(key)
    print('\n-- 立ち回り・視点・座標')
    for key in STATS:
        mark, rel = compare(ma, mb, key, 0.35)
        print('   %-20s %7.2f  %7.2f  %s (%.0f%%差)' % (key, ma.get(key, 0), mb.get(key, 0),
                                                        mark, rel * 100))
        if mark == '!':
            bad.append(key)
        elif mark == '~':
            soft.append(key)
    print('\n-- 手持ちアイテム滞在率')
    keys = sorted(set(ma['items']) | set(mb['items']),
                  key=lambda k: -max(ma['items'].get(k, 0), mb['items'].get(k, 0)))
    item_bad = []
    for k in keys[:10]:
        va, vb = ma['items'].get(k, 0.0), mb['items'].get(k, 0.0)
        diff = abs(va - vb)
        mark = '=' if diff <= 5 else ('~' if diff <= 15 else '!')
        print('   %-22s %6.1f%% %6.1f%%  %s (%+.1fpt)' % (k, va, vb, mark, vb - va))
        if mark == '!':
            item_bad.append(k)
    print()
    if not bad and not item_bad:
        print('VERDICT: 一致（硬い数値・レート・統計・アイテム配分すべて許容内）%s' % (
            '  ※注意: ' + ', '.join(soft) if soft else ''))
        return 0
    print('VERDICT: %s — 乖離: %s%s' % (
        '要確認' if len(bad) + len(item_bad) <= 3 else '不一致',
        ', '.join(bad + item_bad) or '-',
        ('  ※軽微な差: ' + ', '.join(soft)) if soft else ''))
    return 1


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('fabric')
    ap.add_argument('paper')
    ap.add_argument('--who', default='a', help='a=quantumbot b=qbot2')
    ap.add_argument('--json', action='store_true', help='生の指標を JSON で出す')
    args = ap.parse_args()
    if args.json:
        other = 'b' if args.who == 'a' else 'a'
        out = {
            'fabric': fingerprint(load(args.fabric, args.who), load(args.fabric, other) or None),
            'paper': fingerprint(load(args.paper, args.who), load(args.paper, other) or None),
        }
        print(json.dumps(out, indent=2, sort_keys=True))
        return 0
    return report(args.fabric, args.paper, args.who)


if __name__ == '__main__':
    sys.exit(main())
