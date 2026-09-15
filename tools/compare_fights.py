#!/usr/bin/env python3
"""compare_fights.py — QuantumBOT(実測) vs RumilancePractice bot(実測) の数値比較。

Usage:
    python3 compare_fights.py <fabric_log> <ours_log> [--window 20]

<fabric_log> : Fabricサーバーの latest.log から `[q] ` 行を含むログ
<ours_log>   : Paperサーバーの latest.log から `[N Arena][BotMatch]` 行を含むログ

両ログを同じ軸(0.1sサンプル+イベント)に正規化し、
  - 期間・サンプル数
  - 平均/最大平面速度(一歩一歩の動きの品質)
  - 歩行ストライド(2サンプル間の平面移動量の分布)
  - 視点の変化量分布(スナップ回数=視線ジャンプ>15度)
  - 手持ちアイテムの遷移列
  - クリスタル設置間隔(ctサイクル / crystal placeイベント)
  - トーテムPOP回数と休止長
を抽出して並べる。数値完全一致ループの差分レポート。
"""
import gzip
import re
import sys
import statistics


def open_text(path):
    """`.gz` フィクスチャ(docs/parity)もそのまま読めるようにする。"""
    if path.endswith(".gz"):
        return gzip.open(path, "rt", encoding="utf-8", errors="replace")
    return open(path, encoding="utf-8", errors="replace")


def parse_fabric(path):
    samples = []
    for line in open_text(path):
        line = line.strip()
        m = re.search(r"\[q\] (.+)$", line) or re.match(
            r"^(-?[\d.]+,-?[\d.]+,-?[\d.]+ v=.*)$", line)
        if not m:
            continue
        body = m.group(1)
        fields = {}
        pos = re.match(r"(-?[\d.]+),(-?[\d.]+),(-?[\d.]+) ", body + " ")
        if not pos:
            continue
        fields["p"] = tuple(float(pos.group(i)) for i in (1, 2, 3))
        tm = re.search(r"\bt=(\d+)", body)
        if tm:
            fields["t"] = int(tm.group(1))
        for key, pat in [
            ("v", r"v=(-?[\d.]+),(-?[\d.]+),(-?[\d.]+)"),
            ("y", r"\by=(-?[\d.]+)"), ("pi", r"\bp=(-?[\d.]+)"),
            ("hp", r"hp=(-?[\d.]+)"), ("g", r"g=(\d)"),
            ("i", r"\bi=(\S+)"), ("hit", r"hit=(-?\d+)"),
            ("tot", r"tot=(-?\d+)"), ("ct", r"ct=(-?\d+)"),
            ("ob", r"ob=(-?\d+)"), ("pc", r"pc=(-?\d+)"),
            ("cry", r"cry=(\d+)"),
        ]:
            mm = re.search(pat, body)
            if mm:
                fields[key] = mm.groups() if mm.groups().__len__() > 1 else mm.group(1)
        samples.append(fields)
    return samples


def parse_ours(path):
    samples, events = [], []
    for line in open_text(path):
        m = re.search(r"\[N Arena\]\[BotMatch\]\s+(\d+) (s p=.+)$", line.strip())
        if m:
            body = m.group(2)
            fields = {}
            pm = re.search(r"p=(-?[\d.]+),(-?[\d.]+),(-?[\d.]+)", body)
            if pm:
                fields["p"] = tuple(float(pm.group(i)) for i in (1, 2, 3))
            tm = re.search(r"\bt=(\d+)", body)
            if tm:
                fields["t"] = int(tm.group(1))
            for key, pat in [("y", r"\by=(-?[\d.]+)"), ("pi", r"\bpi=(-?[\d.]+)"),
                             ("hp", r"\bhp=(-?[\d.]+)"), ("g", r"\bg=(\d)"), ("i", r"\bi=(\S+)")]:
                mm = re.search(pat, body)
                if mm:
                    fields[key] = mm.group(1)
            samples.append(fields)
            continue
        m = re.search(r"\[N Arena\]\[BotMatch\]\s+([\d.]+)s (.+)$", line.strip())
        if m and not m.group(2).startswith("s p="):
            events.append((float(m.group(1)), m.group(2)))
    return samples, events


def planar(a, b):
    return ((a[0] - b[0]) ** 2 + (a[2] - b[2]) ** 2) ** 0.5


def cadence(samples):
    """サンプル間のtick数(1=毎tickサンプラ / 2=0.1sサンプラ)。t が無ければ2。"""
    ts = [s["t"] for s in samples if "t" in s]
    if len(ts) > 2 and ts[-1] > ts[0]:
        return max(1, round((ts[-1] - ts[0]) / (len(ts) - 1)))
    return 2


def metrics(samples, kind):
    tps = cadence(samples)
    speeds, strides, looks = [], [], []
    items, item_seq = [], []
    prev = None
    for s in samples:
        if "p" not in s:
            continue
        if prev is not None:
            d = planar(s["p"], prev["p"])
            strides.append(d)
            speeds.append(d * 20.0 / tps)  # per second (実測刻みから換算)
            if "y" in s and "y" in prev:
                dy = abs(float(s["y"]) - float(prev["y"]))
                dy = min(dy, 360 - dy)
                looks.append(dy)
            if "i" in s and "i" in prev and s["i"] != prev["i"]:
                item_seq.append((prev["i"], s["i"]))
        prev = s
    out = {
        "samples": len(samples),
        "tick_per_sample": tps,
        "avg_speed": round(statistics.mean(speeds), 3) if speeds else 0,
        "max_speed": round(max(speeds), 3) if speeds else 0,
        "stride_med": round(statistics.median(strides), 3) if strides else 0,
        "look_jumps>15deg": sum(1 for d in looks if d > 15),
        "look_med": round(statistics.median(looks), 2) if looks else 0,
        "item_switches": len(item_seq),
        "item_seq": item_seq[:12],
    }
    if kind == "fabric":
        # ct(=crystal_timer) は設置で crystal_cd へ再装填され、以降は毎tick減る。
        # したがって「増加」= 設置イベント。間隔の最頻値が完了条件の硬い数値。
        cts = [(s["t"], int(s["ct"])) for s in samples if "ct" in s]
        places = [cts[i][0] for i in range(1, len(cts)) if cts[i][1] > cts[i - 1][1]]
        ivs = [places[i + 1] - places[i] for i in range(len(places) - 1)] if places else []
        out["crystal_places"] = len(places)
        if ivs:
            counts = {}
            for v in ivs:
                counts[v] = counts.get(v, 0) + 1
            mode = max(counts.items(), key=lambda kv: kv[1])
            out["crystal_interval_mode_t"] = mode[0]
            out["crystal_interval_mode_ratio"] = f"{mode[1]}/{len(ivs)}"
            out["crystal_interval_avg_t"] = round(statistics.mean(ivs), 2)
    return out


def ours_events(events):
    hits = [t for t, e in events if e.startswith("hit")]
    swings = [t for t, e in events if e.startswith("swing")]
    pops = [t for t, e in events if "totem POP" in e]
    eats = [t for t, e in events if "gap eat" in e]
    places = [t for t, e in events if e == "crystal place"]
    anchors = [t for t, e in events if e.startswith("anchor")]
    return {
        "swings": len(swings), "hits": len(hits),
        "first_hit_s": hits[0] if hits else None,
        "totem_pops": len(pops), "gap_eats": len(eats),
        "crystal_places": len(places),
        "place_intervals": [b - a for a, b in zip(places, places[1:])][:10],
        "anchor_events": len(anchors),
    }


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    if len(args) < 2:
        print(__doc__)
        sys.exit(1)
    fab = parse_fabric(args[0])
    ours_samples, ours_ev = parse_ours(args[1])
    print("=== QuantumBOT (fabric, qlog) ===")
    fm = metrics(fab, "fabric")
    for k, v in fm.items():
        print(f"  {k}: {v}")
    print("=== RumilancePractice bot (ours) ===")
    om = metrics(ours_samples, "ours")
    for k, v in om.items():
        print(f"  {k}: {v}")
    print("=== ours events ===")
    for k, v in ours_events(ours_ev).items():
        print(f"  {k}: {v}")
    print("=== side-by-side (first 20 samples) ===")
    print(f"{'t':>5} | {'fabric pos / look / item':<52} | ours pos / look / item")
    for i in range(min(20, len(fab), len(ours_samples))):
        f, o = fab[i], ours_samples[i]
        fs = f.get("p", ("?",)) and f"{f.get('p')} y={f.get('y')} {f.get('i', '?').replace('minecraft:', '')}"
        os_ = o.get("p", ("?",)) and f"{o.get('p')} y={o.get('y')} {o.get('i', '-')}"
        print(f"{i * 0.1:>4.1f} | {fs:<52} | {os_}")


if __name__ == "__main__":
    main()
