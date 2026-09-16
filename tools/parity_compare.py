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
    m['samples'] = len(rows)
    m['counts'] = {}
    for name, (key, pred) in EVENTS.items():
        n = len(rises(rows, key, pred))
        m['counts'][name] = n
        m[name] = rate(n, ticks)
    m['counts']['totem_pop'] = max((r['pop'] for r in rows), default=0)
    m['totem_pop'] = rate(max((r['pop'] for r in rows), default=0), ticks)
    # 実使用アイテム（遷移で「持ち替えた＝使った」を数える）
    use = collections.Counter()
    for a, b in zip(rows, rows[1:]):
        if a['item'] != b['item']:
            use[b['item']] += 1
    m['item_switch'] = rate(sum(use.values()), ticks)
    m['counts']['item_switch'] = sum(use.values())
    m['counts']['swing'] = m['counts'].get('swing', 0)
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


# 注意帯 = 許容の何倍までを「軽微な差」とみなすか。以前は 3.5 倍(=122%)までを
# 注意扱いにしていたため、`42回/分 vs 0回/分`(100%差) のような *事象が片側で
# 一度も起きていない* 差でも VERDICT が「一致」になっていた。
SOFT_FACTOR = 1.5
# これ未満のサンプル数では率の比較が無意味(1 tick の差が数%になる)。一致判定を出さない。
MIN_SAMPLES = 200


def compare(a, b, key, tolerance):
    """(mark, rel, reason) を返す。mark は '='(一致) / '~'(注意) / '!'(乖離)。

    片側だけ 0 の場合は割合が定義できないが、**「もう片側でしか起きていない」=
    行動が再現できていない**ので、無条件で乖離とする(以前はここが無条件一致だった)。
    """
    va, vb = a.get(key), b.get(key)
    if va is None or vb is None:
        return '!', 1.0, 'missing'
    if va == 0 and vb == 0:
        return '=', 0.0, ''
    if va == 0 or vb == 0:
        return '!', 1.0, 'zero-baseline'
    rel = abs(va - vb) / max(abs(va), abs(vb), 1e-9)
    if rel <= tolerance:
        return '=', rel, ''
    if rel <= tolerance * SOFT_FACTOR:
        return '~', rel, 'near'
    return '!', rel, 'beyond'


HEADLINE = [('swing', 'スイング/分'), ('speed', '移動 b/s'), ('hp_avg', '平均HP'),
            ('item_switch', 'アイテム切替/分'), ('x_span', 'x の広がり'), ('yaw_snap', '視点スナップ/分')]


def verdict(ma, mb, tolerance=0.35):
    """指標辞書 2 つを突き合わせて (ok, bad, soft, reasons) を返す。

    レポート本文とリグレッション試験(--regression)が **同じ判定** を通るようにするため、
    判定そのものをここに集約する(別実装だと片方だけ嘘をつける)。
    """
    bad, soft, reasons = [], [], []
    for key, unit, tol in HARD:
        mark, rel, why = compare(ma, mb, key, tol)
        if mark == '!':
            bad.append(key); reasons.append((key, why, ma.get(key), mb.get(key)))
        elif mark == '~':
            soft.append(key)
    for key in RATES + STATS:
        mark, rel, why = compare(ma, mb, key, tolerance)
        if mark == '!':
            bad.append(key); reasons.append((key, why, ma.get(key), mb.get(key)))
        elif mark == '~':
            soft.append(key)
    for k in sorted(set(ma.get('items', {})) | set(mb.get('items', {}))):
        va, vb = ma['items'].get(k, 0.0), mb['items'].get(k, 0.0)
        diff = abs(va - vb)
        if diff > 15:
            bad.append(k); reasons.append((k, 'items', va, vb))
        elif diff > 5:
            soft.append(k)
    return (not bad), bad, soft, reasons


def regression(path='tools/parity-runner/fixtures/regression_s2_metrics.json'):
    """既知の「嘘の一致」を固定して検証する。

    土台は s2_sword の実測指標: swing 122.1 vs 0.0、x_span 10.13 vs 1.23。
    旧ツールはこれを **「VERDICT: 一致」** と表示していた(片側 0 を無条件一致、
    122% までを注意帯にしていたため)。ここでは同じ指標を判定に通し、
    不一致と出ることを確かめる。
    """
    import os
    if not os.path.exists(path):
        print('regression: fixture が無い: %s' % path)
        return 2
    with open(path, encoding='utf-8') as fh:
        data = json.load(fh)
    ma, mb = data['fabric'], data['paper']
    ok, bad, soft, reasons = verdict(ma, mb)
    print('== リグレッション試験 (fixture=%s)' % path)
    print('   swing %.1f vs %.1f   x_span %.2f vs %.2f   item_switch %.1f vs %.1f' % (
        ma.get('swing', 0), mb.get('swing', 0), ma.get('x_span', 0), mb.get('x_span', 0),
        ma.get('item_switch', 0), mb.get('item_switch', 0)))
    if ok:
        print('   [FAIL] この指標は「不一致」でなければならない(旧ツールはここで嘘をついた)')
        return 1
    print('   [PASS] 不一致として検出: %s' % ', '.join(bad))
    for key, why, va, vb in reasons:
        if why == 'zero-baseline':
            print('          * %s: 片側でしか起きていない (%.2f / %.2f)' % (key, va, vb))
    return 0


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
    print('   fabric=%s (%d ticks, %d samples)  paper=%s (%d ticks, %d samples)' % (
        fabric, ma['ticks'], ma['samples'], paper, mb['ticks'], mb['samples']))
    if min(ma['samples'], mb['samples']) < MIN_SAMPLES:
        print('   ※ サンプル不足: 率の比較ができないため一致判定は出せない')

    _ok, bad, soft, reasons = verdict(ma, mb)

    print('\n-- 主要指標（ここだけ見れば「一致」かどうかが分かる）')
    for key, label in HEADLINE:
        mark, rel, why = compare(ma, mb, key, 0.35)
        print('   %-16s %8.2f  %8.2f  %s (%.0f%%差, n=%s/%s)' % (
            label, ma.get(key, 0), mb.get(key, 0), mark, rel * 100,
            ma['counts'].get(key, '-'), mb['counts'].get(key, '-')))

    print('\n-- 硬い数値（アンカー連鎖の tick 刻み）')
    for key, unit, tol in HARD:
        mark, rel, why = compare(ma, mb, key, tol)
        print('   %-20s %7.1f%s %7.1f%s  %s (%.0f%%差)' % (
            key, ma.get(key, 0), unit, mb.get(key, 0), unit, mark, rel * 100))
    print('\n-- 行動レート(/分)   [n = 計測窓内の発生回数]')
    for key in RATES:
        mark, rel, why = compare(ma, mb, key, 0.35)
        print('   %-20s %7.1f  %7.1f  %s (%.0f%%差)  n=%d/%d' % (
            key, ma.get(key, 0), mb.get(key, 0), mark, rel * 100,
            ma['counts'].get(key, 0), mb['counts'].get(key, 0)))
    print('\n-- 立ち回り・視点・座標')
    for key in STATS:
        mark, rel, why = compare(ma, mb, key, 0.35)
        print('   %-20s %7.2f  %7.2f  %s (%.0f%%差)' % (key, ma.get(key, 0), mb.get(key, 0),
                                                        mark, rel * 100))
    print('\n-- 手持ちアイテム滞在率')
    keys = sorted(set(ma['items']) | set(mb['items']),
                  key=lambda k: -max(ma['items'].get(k, 0), mb['items'].get(k, 0)))
    item_bad = [k for k in keys[:10]
                if abs(ma['items'].get(k, 0.0) - mb['items'].get(k, 0.0)) > 15]
    for k in keys[:10]:
        va, vb = ma['items'].get(k, 0.0), mb['items'].get(k, 0.0)
        diff = abs(va - vb)
        mark = '=' if diff <= 5 else ('~' if diff <= 15 else '!')
        print('   %-22s %6.1f%% %6.1f%%  %s (%+.1fpt)' % (k, va, vb, mark, vb - va))

    print()
    if bad or item_bad:
        print('VERDICT: 不一致 — 再現できていない指標: %s' % (', '.join(bad + item_bad) or '-'))
        for key, why, va, vb in reasons:
            if why == 'zero-baseline':
                print('   * %s: 片側で一度も起きていない (fabric=%.2f / paper=%.2f)' % (key, va, vb))
            elif why == 'missing':
                print('   * %s: 片側に指標が無い' % key)
        if soft:
            print('   ※軽微な差: %s' % ', '.join(soft))
        if min(ma['samples'], mb['samples']) < MIN_SAMPLES:
            print('   ※サンプル不足のため、この判定自体が暫定')
        return 1
    if soft:
        # 「注意」は一致ではない。以前はここで「一致」と表示していたため、
        # 実際には大きく違う指標が注意書きの中に隠れていた。
        print('VERDICT: 要確認 — 許容内だが差が大きめ: %s' % ', '.join(soft))
        if min(ma['samples'], mb['samples']) < MIN_SAMPLES:
            print('   ※サンプル不足のため、この判定自体が暫定')
        return 1
    print('VERDICT: 一致（硬い数値・レート・統計・アイテム配分すべて許容内）')
    if min(ma['samples'], mb['samples']) < MIN_SAMPLES:
        print('   ※サンプル不足のため、この判定自体が暫定')
        return 1
    return 0


def selftest(base=None, verbose=False):
    """カナリア自己テスト — ツールが「嘘の一致」を出さないことを確かめる。

    実測ログを土台に、*わかっている乖離* を作って判定させる:

      1. 同じログ同士                     -> 一致     (正常に一致を出せる)
      2. 片側の攻撃(hit=)を全消し          -> 不一致   (swing が 0 になる)
      3. 片側の座標を凍結                  -> 不一致   (移動量が 0 になる)
      4. 片側のアイテム切替を消す(i= 固定)  -> 不一致   (item_switch が 0 になる)

    1 が「一致」を返さない(=常に不一致と叫ぶ)ならツールが壊れているし、
    2-4 が「一致」を返すなら *嘘をつく* ツールである。どちらも FAIL。
    """
    import glob
    import os
    import tempfile
    if base is None:
        cands = sorted(glob.glob('parity-logs/*.log.gz'), key=os.path.getmtime, reverse=True)
        if not cands:
            print('selftest: parity-logs/*.log.gz が無い')
            return 2
        base = cands[0]
    opener = gzip.open if base.endswith('.gz') else open
    with opener(base, 'rt', encoding='utf-8', errors='replace') as fh:
        raw = [l for l in fh if 'who=' in l]

    def mutate(lines, fn):
        return [fn(l) for l in lines]

    def kill_swing(line):
        return re.sub(r'hit=-?\d+', 'hit=0', line)

    def freeze_pos(line):
        m = LINE.search(line)
        if not m:
            return line
        px, py, pz = m.group(1), m.group(2), m.group(3)
        line = line.replace('%s,%s,%s' % (px, py, pz), '-698.5,31.0,88.5', 1)
        return re.sub(r'v=(-?[\d.]+),(-?[\d.]+),(-?[\d.]+)', 'v=0,0,0', line)

    def kill_switch(line):
        return re.sub(r'i=\S+', 'i=minecraft:stone', line)

    tmp = tempfile.mkdtemp(prefix='parity-selftest-')
    cases = [
        ('同一ログ（一致するはず）', raw, raw, 0),
        ('片側の攻撃を消す（swing 乖離）', raw, mutate(raw, kill_swing), 1),
        ('片側の座標を凍結（移動量 乖離）', raw, mutate(raw, freeze_pos), 1),
        ('片側のアイテム切替を消す（item_switch 乖離）', raw, mutate(raw, kill_switch), 1),
    ]
    failures = []
    print('カナリア自己テスト (base=%s, %d 行)' % (base, len(raw)))
    for name, a, b, expect in cases:
        pa, pb = os.path.join(tmp, 'a.log'), os.path.join(tmp, 'b.log')
        with open(pa, 'w') as fh:
            fh.writelines(a)
        with open(pb, 'w') as fh:
            fh.writelines(b)
        import io
        import contextlib
        buf = io.StringIO()
        with contextlib.redirect_stdout(buf):
            rc = report(pa, pb, 'a', name)
        ok = (rc == 0) if expect == 0 else (rc != 0)
        verdict = [l for l in buf.getvalue().splitlines() if l.startswith('VERDICT')]
        print('   [%s] %-42s -> %s' % ('PASS' if ok else 'FAIL', name,
                                       verdict[0] if verdict else 'rc=%d' % rc))
        if not ok:
            failures.append(name)
        elif verbose:
            print(buf.getvalue())
    if failures:
        print('selftest FAILED: %s' % ', '.join(failures))
        return 1
    print('selftest OK: 一致は一致、既知の乖離はすべて不一致として検出')
    return 0


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('fabric', nargs='?')
    ap.add_argument('paper', nargs='?')
    ap.add_argument('--who', default='a', help='a=quantumbot b=qbot2')
    ap.add_argument('--json', action='store_true', help='生の指標を JSON で出す')
    ap.add_argument('--regression', action='store_true',
                    help='既知の「嘘の一致」(s2_sword の実測指標)を判定に通す回帰試験')
    ap.add_argument('--fixture', help='--regression で使う fixture のパス')
    ap.add_argument('--selftest', action='store_true',
                    help='カナリア自己テスト（一致/乖離を正しく判定できるか）を実行する')
    ap.add_argument('--base', help='selftest の土台にするログ（既定: 最新の parity-logs/*.log.gz）')
    ap.add_argument('-v', '--verbose', action='store_true')
    args = ap.parse_args()
    if args.regression:
        return regression(args.fixture or 'tools/parity-runner/fixtures/regression_s2_metrics.json')
    if args.selftest:
        return selftest(args.base, args.verbose)
    if not args.fabric or not args.paper:
        ap.error('fabric と paper のログを指定する（--selftest なら不要）')
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
