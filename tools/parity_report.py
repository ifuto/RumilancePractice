#!/usr/bin/env python3
"""parity_report.py — qlog([q]) 1本から「硬い数値」と「統計」を抽出する。

Usage:
    python3 tools/parity_report.py <qlog|latest.log>[.gz]

qlog 行の定義は tools/qlog-datapack/README.md を参照。旧フォーマット
(2tick毎・hpT/pop/ec/t 無し)も自動判別して読む。単位はすべて tick。

出力は docs/bot-combat-parity.md の完了条件(硬い数値=一致必須 / 統計=±10%目安)
にそのまま並べられる形にしている。
"""
import collections
import gzip
import math
import re
import sys

LINE = re.compile(
    r'(?:\[q\]\s+)?(-?[\d.]+),(-?[\d.]+),(-?[\d.]+)\s+v=(-?[\d.]+),(-?[\d.]+),(-?[\d.]+)'
    r'\s+y=(-?[\d.]+)\s+p=(-?[\d.]+)\s+hp=([\d.]+)\s+g=(\d)\s+i=(\S+)'
    r'(?:\s+hit=(-?\d+)\s+tot=(-?\d+)\s+ct=(-?\d+)\s+ob=(-?\d+)\s+pc=(-?\d+)\s+cry=(\d+))?'
    r'(?:\s+anc=(-?\d+)\s+chg=(-?\d+)\s+exp=(-?\d+))?'
    r'(?:\s+hpT=(\d+)\s+pop=(\d+)\s+ec=(\d+)\s+t=(\d+))?'
)


def load(path):
    opener = gzip.open if path.endswith(".gz") else open
    out = []
    with opener(path, "rt", encoding="utf-8", errors="replace") as fh:
        for line in fh:
            m = LINE.search(line)
            if not m:
                continue
            g = m.groups()
            rec = dict(
                x=float(g[0]), y=float(g[1]), z=float(g[2]),
                vx=float(g[3]), vy=float(g[4]), vz=float(g[5]),
                yaw=float(g[6]), pit=float(g[7]), hp=float(g[8]), g=int(g[9]),
                item=g[10].replace("minecraft:", ""),
                hit=int(g[11] or 0), tot=int(g[12] or 0), ct=int(g[13] or 0),
                ob=int(g[14] or 0), pc=int(g[15] or 0), cry=int(g[16] or 0),
                anc=int(g[17] or 0), chg=int(g[18] or 0), exp=int(g[19] or 0),
                hpT=int(g[20] or 0), pop=int(g[21] or 0), ec=int(g[22] or 0),
                t=int(g[23] or 0),
            )
            out.append(rec)
    return out


def ticks_per_sample(rows):
    """サンプル間の tick 数(1=毎tick, 2=0.1sサンプラ)。"""
    if len(rows) < 2 or rows[-1]["t"] == 0:
        return 1
    return max(1, round((rows[-1]["t"] - rows[0]["t"]) / (len(rows) - 1)))


def rises(values):
    """単調減少カウンタ(ct/totem_timer)の再装填・累計カウンタ(pop/ec)の増加を検出する。"""
    return [i for i in range(1, len(values)) if values[i] > values[i - 1]]


def fmt_dist(counter, unit="t"):
    items = sorted(counter.items())
    return " ".join(f"{k}{unit}×{v}" for k, v in items[:8])


def main():
    path = sys.argv[1] if len(sys.argv) > 1 else "latest.log"
    rows = load(path)
    if not rows:
        print("qlog行が見つかりません:", path)
        return 1
    tps = ticks_per_sample(rows)
    span = rows[-1]["t"] - rows[0]["t"] if rows[-1]["t"] else len(rows) * tps
    print(f"# qlog 実測レポート: {path}")
    print(f"- サンプル {len(rows)} 行 / 期間 {span} tick ({span / 20:.1f} s) / 粒度 {tps}tick")

    # ---- 硬い数値1: クリスタル設置間隔 --------------------------------------
    ct = [r["ct"] for r in rows]
    # クリスタル設置 = ct再装填のうち、その時 end_crystal を手にしているもの
    # (アンカーの place/charge も crystal_timer を charge_cd へ再装填するため)
    all_rises = [i for i in rises(ct)]
    places = [rows[i]["t"] for i in all_rises if rows[i]["item"] == "end_crystal"]
    skipped = len(all_rises) - len(places)
    intervals = [places[i + 1] - places[i] for i in range(len(places) - 1)]
    print(f"\n## 硬い数値1: クリスタル設置 (ct>前回)")
    print(f"- 設置 {len(places)} 回" + (f"(アンカー由来の再装填 {skipped} 回は除外)" if skipped else ""))
    if intervals:
        c = collections.Counter(intervals)
        main_iv = c.most_common(1)[0][0]
        print(f"- 間隔: 最頻 {main_iv}t ({c[main_iv]}/{len(intervals)}) / 平均 {sum(intervals) / len(intervals):.2f}t"
              f" / 中央値 {sorted(intervals)[len(intervals) // 2]}t / 範囲 {min(intervals)}-{max(intervals)}t")
        print(f"- 分布: {fmt_dist(c)}")

    # ---- 硬い数値2: 設置→爆発 ----------------------------------------------
    spawn = [rows[i]["t"] for i in rises([r["ec"] for r in rows])]
    boom = [rows[i]["t"] for i in range(1, len(rows))
            if rows[i]["ec"] < rows[i - 1]["ec"]]
    print(f"\n## 硬い数値2: クリスタルの寿命 (ec 0->1 / 1->0)")
    print(f"- 出現 {len(spawn)} 回 / 消滅 {len(boom)} 回")
    lat = []
    for b in boom:
        prev = [s for s in spawn if s < b]
        if prev:
            lat.append(b - prev[-1])
    if lat:
        c = collections.Counter(lat)
        print(f"- 設置→消滅: 最頻 {c.most_common(1)[0][0]}t ({fmt_dist(c)})")

    # ---- 硬い数値3: トーテムPOP休止 ----------------------------------------
    pops = [rows[i]["t"] for i in rises([r["pop"] for r in rows])]
    tot_pause = []
    for p in pops:
        after = [r for r in rows if r["t"] >= p]
        n = 0
        for r in after:
            if r["tot"] > 0:
                n += 1
            elif n:
                break
        tot_pause.append(n)
    print(f"\n## 硬い数値3: トーテムPOP")
    print(f"- POP {len(pops)} 回" + (f" / 休止(totem_timer>0): {tot_pause}" if pops else ""))
    print(f"- totem_timer>0 サンプル: {sum(1 for r in rows if r['tot'] > 0)}")

    # ---- 硬い数値4: 近接 swing 間隔 -----------------------------------------
    swings = [rows[i]["t"] for i in range(1, len(rows))
              if rows[i]["hit"] >= 6 and rows[i - 1]["hit"] < 6]
    si = [swings[i + 1] - swings[i] for i in range(len(swings) - 1)]
    print(f"\n## 硬い数値4: 近接スイング (hitcd 立ち上がり)")
    if si:
        c = collections.Counter(si)
        print(f"- スイング {len(swings)} 回 / 間隔: 最頻 {c.most_common(1)[0][0]}t ({fmt_dist(c)})")
    else:
        print("- 検出なし(この窓ではクリスタルのみ)")

    # ---- 硬い数値5: アイテム遷移 -------------------------------------------
    seq = []
    for r in rows:
        if not seq or seq[-1][0] != r["item"]:
            seq.append([r["item"], r["t"], r["t"]])
        else:
            seq[-1][2] = r["t"]
    print(f"\n## 硬い数値5: アイテム遷移 ({len(seq)} 回)")
    print("- " + " → ".join(f"{a}({c - b + 1}t)" for a, b, c in seq[:16]))

    # ---- 硬い数値6: 金リンゴ(HP80%=16 以下で開始) ---------------------------
    gaps = [r for r in rows if r["item"] == "golden_apple"]
    print(f"\n## 硬い数値6: 金リンゴ")
    if gaps:
        print(f"- 保持サンプル {len(gaps)} / 開始HP {gaps[0]['hp']} / HP範囲 "
              f"{min(r['hp'] for r in gaps):.1f}-{max(r['hp'] for r in gaps):.1f}")
    else:
        print("- なし(この窓では未使用)")

    # ---- 硬い数値7: アンカー(リスポーンアンカー) ---------------------------
    # 手に持った瞬間で検出する方がタイマ増加より確実(アンカーはcrystal_timerも再装填する)
    def item_switches(name):
        return [rows[i]["t"] for i in range(1, len(rows))
                if rows[i]["item"] == name and rows[i - 1]["item"] != name]

    print("\n## 硬い数値7: アンカー(リスポーンアンカー)")
    a_place = item_switches("respawn_anchor")
    a_charge = item_switches("glowstone")
    a_exp = [rows[i]["t"] for i in range(1, len(rows)) if rows[i]["exp"] > rows[i - 1]["exp"]]
    if not (a_place or a_charge):
        print("- この窓ではアンカー未使用")
    else:
        print(f"- 設置 {len(a_place)} 回 / チャージ(グロウストーン) {len(a_charge)} 回")
        lat = []
        for c in a_charge:
            prev = [p for p in a_place if p < c]
            if prev:
                lat.append(c - prev[-1])
        if lat:
            c = collections.Counter(lat)
            print(f"- 設置→チャージ: 最頻 {c.most_common(1)[0][0]}t 分布 {fmt_dist(c)}")
        cyc = [a_place[i + 1] - a_place[i] for i in range(len(a_place) - 1)]
        if cyc:
            c = collections.Counter(cyc)
            print(f"- 設置サイクル(設置→次設置): 最小 {min(cyc)}t (理論値=anchor_cd+charge_cd+explosion_cd) "
                  f"最頻 {c.most_common(1)[0][0]}t")
        lat2 = []
        for e in a_exp:
            prev = [x for x in a_charge if x < e]
            if prev:
                lat2.append(e - prev[-1])
        if lat2:
            c = collections.Counter(lat2)
            print(f"- チャージ→爆発: 最頻 {c.most_common(1)[0][0]}t 分布 {fmt_dist(c)}")
        # 使用アイテムの時間配分(混合具合)
        c = collections.Counter(r["item"] for r in rows)
        top = ", ".join(f"{k} {v / len(rows) * 100:.1f}%" for k, v in c.most_common(6))
        print(f"- アイテム時間配分: {top}")

    # ---- 統計: 移動 ---------------------------------------------------------
    step = max(1, tps)
    hs, stride = [], []
    for i in range(step, len(rows)):
        a, b = rows[i - step], rows[i]
        d = math.hypot(b["x"] - a["x"], b["z"] - a["z"])
        stride.append(d)
        hs.append(d * 20 / step)
    mv = [s for s in hs if s > 0.05]
    print(f"\n## 統計1: 平面移動")
    print(f"- 速度(b/s): 平均 {sum(hs) / len(hs):.2f} / 移動サンプルのみ 平均 "
          f"{sum(mv) / len(mv) if mv else 0:.2f} 最大 {max(hs):.2f} / 移動割合 "
          f"{len(mv) / len(hs) * 100:.1f}%")
    print(f"- ストライド(1サンプル平面移動): 平均 {sum(stride) / len(stride):.4f} b")

    # ---- 統計: 視点 ---------------------------------------------------------
    dyaw = [abs(((rows[i]["yaw"] - rows[i - 1]["yaw"] + 180) % 360) - 180) for i in range(1, len(rows))]
    dpit = [abs(rows[i]["pit"] - rows[i - 1]["pit"]) for i in range(1, len(rows))]
    big = [d for d in dyaw if d > 15]
    print(f"\n## 統計2: 視点")
    print(f"- yaw変化: 平均 {sum(dyaw) / len(dyaw):.2f}°/sample 最大 {max(dyaw):.1f}° / "
          f"スナップ(>15°) {len(big)} 回 / スナップ平均量 {sum(big) / len(big) if big else 0:.1f}°")
    print(f"- pitch変化: 平均 {sum(dpit) / len(dpit):.2f}°/sample 最大 {max(dpit):.1f}°")

    # ---- 統計: 体力 ---------------------------------------------------------
    print(f"\n## 統計3: 体力")
    print(f"- BOT hp: 平均 {sum(r['hp'] for r in rows) / len(rows):.2f} 最小 "
          f"{min(r['hp'] for r in rows):.1f} 最大 {max(r['hp'] for r in rows):.1f}")
    if any(r["hpT"] for r in rows):
        last = [r["hpT"] for r in rows if r["hpT"]]
        print(f"- 相手 hp(×10): 最小 {min(last)} 最大 {max(last)}")
    print(f"- 設置率(cry=1): {sum(1 for r in rows if r['cry']) / len(rows) * 100:.1f}%"
          f" / クリスタル近接(ec>0): {sum(1 for r in rows if r['ec']) / len(rows) * 100:.1f}%")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
