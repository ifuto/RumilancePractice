#!/usr/bin/env python3
"""texture.py — [q] ログから「戦闘テクスチャ」指標を抽出して左右のエンジン差を探す。

    python3 tools/parity-runner/texture.py LOG1 [LOG2 ...] [--who a]

1 ファイル = 1 ラウンド。1 side (who) ごとに:

  swings        hitcd の増加 (0->7) = 攻撃判定回数
  connects      swing tick に相手 hp が落ちた回数 (同一 tick / +1 tick 許容)
  fall_swings   swing 時に自分が落下中 (vy <= -0.3)
  fall_connects fall_swings のうち相手 hp を落とした回数 (= スマッシュ接続)
  dmg_events    自分が被弾した回数 (hp低下 >0.5, 連続 tick は 1 回に圧縮)
  dmg_from_air  被弾直前に相手が落下中だった割合
  air_share     自分が空中にいた tick の割合 (vy != 0)
  rel_spd_med   swing 時の水平相対速度の中央値 (blk/tick)
  dist_med      swing 時の相手距離の中央値
  hp_taken      総被弾量

出力は markdown テーブル 1 行/ラウンド。--who a は「a の攻撃側指標 + a の被弾側指標」の両方を出す
(攻撃テクスチャと被弾テクスチャは別物なので)。
"""
import argparse
import gzip
import sys


def parse_line(line):
    d = {}
    for tok in line.split():
        if '=' in tok:
            k, v = tok.split('=', 1)
            d[k] = v
    return d


def load_round(path):
    """{who: [ (t, x,y,z, vx,vy,vz, yaw, hp, hit, pc, pop) ]} — t 昇順。"""
    rows = {'a': [], 'b': []}
    opener = gzip.open if path.endswith('.gz') else open
    with opener(path, 'rt', encoding='utf-8', errors='replace') as fh:
        for line in fh:
            if '[q]' not in line or 'who=' not in line:
                continue
            d = parse_line(line)
            try:
                who = d['who']
                x, y, z = (float(d['x1']) if False else 0.0, 0.0, 0.0)
                # pos は "x,y,z" トークン (キーなし) の先頭にある
            except KeyError:
                continue
            # [q] x,y,z v=.. y=.. p=.. hp=.. g=.. i=.. hit=.. tot=.. ct=.. ob=.. pc=.. cry=.. anc=.. chg=.. exp=.. hpT=.. pop=.. ec=.. t=.. who=..
            try:
                pos = line.split('[q] ', 1)[1].split(' ', 1)[0]
                px, py, pz = (float(v) for v in pos.split(','))
                vtok = d['v']
                vx, vy, vz = (float(v) for v in vtok.split(','))
                rows[who].append(dict(
                    t=int(d['t']), x=px, y=py, z=pz, vx=vx, vy=vy, vz=vz,
                    yaw=float(d['y']), hp=float(d['hp']), hit=int(d['hit']),
                    pc=int(d['pc'])))
            except (KeyError, ValueError, IndexError):
                continue
    for who in rows:
        rows[who].sort(key=lambda r: r['t'])
    return rows


def index_by_t(rows):
    return {r['t']: r for r in rows}


def texture(path, who):
    rows = load_round(path)
    if not rows.get(who):
        return None
    me = index_by_t(rows[who])
    other = index_by_t(rows.get('b' if who == 'a' else 'a') or [])

    swings = fall_swings = connects = fall_connects = 0
    dists = []
    rels = []
    air_ticks = 0
    prev = None
    for r in rows[who]:
        if prev is not None and prev['hit'] <= 2 and r['hit'] >= 5:
            swings += 1
            o = other.get(r['t'])
            if o is not None:
                dx, dz = r['x'] - o['x'], r['z'] - o['z']
                dists.append((dx * dx + dz * dz) ** 0.5)
                rels.append((((r['vx'] - o['vx']) ** 2 + (r['vz'] - o['vz']) ** 2) ** 0.5))
                hit = o['hp'] < prev_hp_other - 0.4 if (prev_hp_other := other.get(r['t'] - 1, {}).get('hp')) is not None else False
                hit1 = other.get(r['t'] + 1, {}).get('hp', 99) < (other.get(r['t'], {}).get('hp', 99)) - 0.4
                if hit or hit1:
                    connects += 1
                    if r['vy'] <= -0.3:
                        fall_connects += 1
            if r['vy'] <= -0.3:
                fall_swings += 1
        if abs(r['vy']) > 1e-6:
            air_ticks += 1
        prev = r

    # 被弾側: 自分の hp drop (相手の空中状態つき)
    dmg_events = 0
    dmg_from_air = 0
    hp_taken = 0.0
    cooldown = -9
    for i, r in enumerate(rows[who]):
        p = rows[who][i - 1] if i else None
        if p is not None and p['hp'] - r['hp'] > 0.5:
            if r['t'] - cooldown > 3:
                dmg_events += 1
                o = other.get(r['t'] - 1) or other.get(r['t'])
                if o is not None and o['vy'] <= -0.3:
                    dmg_from_air += 1
            hp_taken += p['hp'] - r['hp']
            cooldown = r['t']

    n = len(rows[who])
    med = lambda v: sorted(v)[len(v) // 2] if v else float('nan')
    return dict(
        swings=swings, connects=connects,
        fall_swings=fall_swings, fall_connects=fall_connects,
        dmg_events=dmg_events, dmg_from_air=dmg_from_air,
        hp_taken=round(hp_taken, 1),
        air_share=round(air_ticks / n * 100, 1) if n else 0,
        dist_med=round(med(dists), 2) if dists else float('nan'),
        rel_spd_med=round(med(rels), 2) if rels else float('nan'),
    )


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('logs', nargs='+')
    ap.add_argument('--who', default='a')
    args = ap.parse_args()
    print('| log | swings | connects | fall_sw | fall_conn | dmg_ev | dmg_air | hp_taken | air% | dist_med | rel_med |')
    print('|---|---|---|---|---|---|---|---|---|---|---|')
    for path in args.logs:
        m = texture(path, args.who)
        if m is None:
            print('| %s | (no data) |' % path)
            continue
        print('| %s | %d | %d | %d | %d | %d | %d | %s | %s | %s | %s |' % (
            path.split('/')[-1].replace('.log.gz', ''), m['swings'], m['connects'],
            m['fall_swings'], m['fall_connects'], m['dmg_events'], m['dmg_from_air'],
            m['hp_taken'], m['air_share'], m['dist_med'], m['rel_spd_med']))


if __name__ == '__main__':
    main()
