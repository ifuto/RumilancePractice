#!/usr/bin/env python3
"""plugin_bot_report.py — プラグイン側 BOT 戦の実測レポートと参照(run8)との比較。

Paper の logs/latest.log(またはその抜粋)にある

    [N Arena][BotMatch] END player=… duration=<秒>s …
    [N Arena][BotMatch] trace (N events):
    [N Arena][BotMatch]   <0.1秒> <event>
    [N Arena][BotMatch] samples (N @0.1s):
    [N Arena][BotMatch]   <デシ秒> s p=… i=<item> …   (10 単位 = 1 秒)

を読み、参照側 qlog(docs/parity/fabric_normal_anchor_run8_400s.log.gz)から同じ統計を
出して並べる。標準ライブラリのみ。

    python3 tools/plugin_bot_report.py /tmp/paper-run/logs/latest.log \
        --ref docs/parity/fabric_normal_anchor_run8_400s.log.gz
"""
from __future__ import annotations

import gzip
import re
import statistics
import sys
from collections import Counter

TRACE = re.compile(r"\[N Arena\]\[BotMatch\]\s+([\d.]+)s\s+(.+)$")
SAMPLE = re.compile(r"\[N Arena\]\[BotMatch\]\s+([\d.]+)\s+s p=")
END = re.compile(r"\[N Arena\]\[BotMatch\] END .*duration=(\d+)s.*difficulty=(\w+)")


def read_lines(path: str):
    if path.endswith(".gz"):
        with gzip.open(path, "rt", errors="replace") as fh:
            yield from fh
    else:
        with open(path, "r", errors="replace") as fh:
            yield from fh


def plugin_stats(path: str) -> dict:
    events: list[tuple[float, str]] = []
    items: Counter[str] = Counter()
    samples = 0
    duration = None
    difficulty = "?"
    for line in read_lines(path):
        m = END.search(line)
        if m:
            duration = int(m.group(1))
            difficulty = m.group(2)
            continue
        m = SAMPLE.search(line)
        if m:
            samples += 1
            m2 = re.search(r"\bi=([A-Za-z_]+)", line)
            if m2:
                items[m2.group(1) or "-"] += 1
            continue
        m = TRACE.search(line)
        if m and not m.group(2).startswith("s p="):
            events.append((float(m.group(1)), m.group(2)))

    span = duration
    if span is None and events:
        span = events[-1][0]
    if span is None and samples:
        span = 0.0
    minutes = max(span, 1.0) / 60.0

    def count(*needles: str) -> int:
        return sum(1 for _, e in events if all(n in e for n in needles))

    def rate(n: int) -> float:
        return round(n / minutes, 1)

    def gaps(*needles: str) -> tuple[float, float]:
        ts = [t for t, e in events if all(n in e for n in needles)]
        if len(ts) < 3:
            return (0.0, 0.0)
        d = [ts[i + 1] - ts[i] for i in range(len(ts) - 1)]
        return (round(statistics.median(d), 2), round(min(d), 2))

    pearl = count("pearl")
    anchor = count("anchor place")
    charge = count("anchor charge")
    detonate = count("anchor detonate")
    crystal = count("crystal place")
    swings = count("swing")
    hits = count("hit ")
    gap_eat = count("gap eat")
    pops = count("totem POP")
    total_items = sum(items.values()) or 1

    return {
        "source": "plugin",
        "duration": span,
        "difficulty": difficulty,
        "events": len(events),
        "samples": samples,
        "pearl": (pearl, rate(pearl), gaps("pearl")[0]),
        "anchor": (anchor, rate(anchor), gaps("anchor place")[0]),
        "charge": (charge, rate(charge)),
        "detonate": (detonate, rate(detonate)),
        "crystal": (crystal, rate(crystal), gaps("crystal place")[0]),
        "swings": (swings, rate(swings)),
        "hits": (hits, rate(hits), gaps("hit ")[0]),
        "pops": pops,
        "gap_eat": gap_eat,
        "items": [(k, round(100.0 * v / total_items, 1)) for k, v in items.most_common()],
    }


def ref_stats(path: str) -> dict:
    """qlog(1行=1tick, 20 tick/s)から同じ統計を作る。"""
    rows = []
    for line in read_lines(path):
        body = line.strip()
        if not body or "t=" not in body:
            continue
        rows.append(dict(re.findall(r"(\w+)=([^\s]+)", body)))
    if not rows:
        raise SystemExit(f"no qlog rows in {path}")
    t0 = int(float(rows[0]["t"]))
    span = (int(float(rows[-1]["t"])) - t0) / 20.0
    minutes = span / 60.0

    def uses(key: str, thresh: int = 3) -> list[float]:
        vals = [int(float(r[key])) for r in rows if key in r]
        ts = [int(float(r["t"])) for r in rows if key in r]
        out = []
        for i in range(1, len(vals)):
            if vals[i] > vals[i - 1] + thresh:
                out.append((ts[i] - t0) / 20.0)
        return out

    def med(ts: list[float]) -> float:
        if len(ts) < 3:
            return 0.0
        return round(statistics.median([ts[i + 1] - ts[i] for i in range(len(ts) - 1)]), 2)

    def rate(n: int) -> float:
        return round(n / minutes, 1)

    pearl, anchor, charge = uses("pc"), uses("anc"), uses("chg")
    detonate, crystal, hitcd = uses("exp"), uses("ct"), uses("hit")
    items: Counter[str] = Counter(r["i"].replace("minecraft:", "") for r in rows if "i" in r)
    total = sum(items.values()) or 1
    return {
        "source": "reference",
        "duration": span,
        "difficulty": "INTERMEDIATE",
        "pearl": (len(pearl), rate(len(pearl)), med(pearl)),
        "anchor": (len(anchor), rate(len(anchor)), med(anchor)),
        "charge": (len(charge), rate(len(charge))),
        "detonate": (len(detonate), rate(len(detonate))),
        "crystal": (len(crystal), rate(len(crystal)), med(crystal)),
        "swings": (0, 0.0),
        "hits": (len(hitcd), rate(len(hitcd)), med(hitcd)),
        "pops": len(uses("tot")),
        "gap_eat": 0,
        "items": [(k, round(100.0 * v / total, 1)) for k, v in items.most_common()],
    }


def _fmt(vals) -> str:
    """count / per-minute / median gap — a short tuple just omits the later columns."""
    if not vals:
        return "-"
    out = f"{int(vals[0]):>4d}  {float(vals[1]):>6.1f}/min"
    if len(vals) > 2 and float(vals[2]) > 0:
        out += f"  ギャップ中央 {float(vals[2]):.2f}s"
    return out


def line(label: str, p, r) -> str:
    return f"{label:<18} 当側: {_fmt(p):<40} 参照: {_fmt(r)}"


def main() -> int:
    if len(sys.argv) < 2:
        print(__doc__)
        return 2
    path = sys.argv[1]
    ref_path = None
    if "--ref" in sys.argv:
        ref_path = sys.argv[sys.argv.index("--ref") + 1]
    p = plugin_stats(path)
    r = ref_stats(ref_path) if ref_path else None

    print(f"=== プラグイン側 BOT 戦 {path} ===")
    print(f"試合長 {p['duration']}s / difficulty {p['difficulty']} / trace {p['events']} 件 / "
          f"samples {p['samples']} 件")
    if r:
        print(f"参照 {ref_path} — 試合長 {r['duration']:.0f}s "
              f"(difficulty {r['difficulty']})")
    print()
    print("                   [回数  /min  ギャップ]")
    for key, label in (("pearl", "パール"), ("anchor", "アンカー設置"),
                       ("charge", "アンカーチャージ"), ("detonate", "アンカー起爆"),
                       ("crystal", "クリスタル設置"), ("hits", "剣ヒット"),
                       ("gap_eat", "金リンゴ"), ("pops", "トーテムPOP")):
        if key in ("gap_eat", "pops"):
            ps = f"{p[key]:>4d} ({p[key] / max(p['duration'], 1) * 60:5.1f}/min)"
            rs = f"{r[key]:>4d} ({r[key] / max(r['duration'], 1) * 60:5.1f}/min)" if r else "-"
            print(f"{label:<18} 当側: {ps}   参照: {rs}")
        else:
            print(line(label, p[key], r[key] if r else None))
    print()
    print("手持ちアイテム(サンプル)     当側                 参照")
    pm, rm = dict(p["items"]), dict(r["items"]) if r else {}
    for mat in sorted(set(pm) | set(rm), key=lambda k: -max(pm.get(k, 0), rm.get(k, 0))):
        print(f"  {mat:<22} {pm.get(mat, 0):5.1f}%            {rm.get(mat, 0):5.1f}%"
              if r else f"  {mat:<22} {pm.get(mat, 0):5.1f}%")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
