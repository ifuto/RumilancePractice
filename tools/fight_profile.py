#!/usr/bin/env python3
"""fight_profile.py — 戦闘BOTの行動を「距離バンド別」に集計して参照と並べる。

同じBOTを同じ土俵で比べるための道具。相手(プレイヤー役)との距離ごとに
何をしているか(パール / アンカー / クリスタル / 剣)を出すので、
「近接に張り付いているから件数が違う」のか「ロジックが違う」のかを切り分けられる。

使い方:

  参照側(qlog。d2/d3/d6/d9 は qlog データパックが記録する距離バンド):
    python3 tools/fight_profile.py ref /tmp/ref_dist_run.log

  プラグイン側([N Arena][BotMatch] の samples/trace。相手の位置を渡す):
    python3 tools/fight_profile.py plugin /tmp/paper-run/logs/latest.log \
        --dummy 0.5,64,0.5

どちらも標準ライブラリのみ。参照側の距離バンドが無い古いログでは
d 列が無いぶんだけ「距離不明」として集計する。
"""
from __future__ import annotations

import gzip
import re
import sys
from collections import Counter, defaultdict

BANDS = ("<=2", "<=3", "<=6", "<=9", ">9")


def _open(path: str):
    if path.endswith(".gz"):
        return gzip.open(path, "rt", errors="replace")
    return open(path, "r", errors="replace")


# --------------------------------------------------------------------- reference
def read_ref(path: str):
    """qlog 1行=1tick → (tick, band, item, actions) のリスト。"""
    rows = []
    seen = set()
    for line in _open(path):
        if "t=" not in line:
            continue
        d = dict(re.findall(r"(\w+)=([^\s]+)", line))
        t = d.get("t")
        if t is None or t in seen:
            continue
        seen.add(t)
        band = ">9"
        for key, name in (("d2", "<=2"), ("d3", "<=3"), ("d6", "<=6"), ("d9", "<=9")):
            if d.get(key) == "1":
                band = name
                break
        rows.append({
            "t": int(float(t)),
            "band": band,
            "item": d.get("i", "").replace("minecraft:", ""),
            "pc": int(float(d.get("pc", 0))),
            "anc": int(float(d.get("anc", 0))),
            "hit": int(float(d.get("hit", 0))),
            "raw": d,
        })
    return rows


def ref_profile(rows):
    if not rows:
        raise SystemExit("no qlog rows")
    span = (rows[-1]["t"] - rows[0]["t"]) / 20.0
    actions = defaultdict(list)  # name -> [tick]
    prev = None
    for r in rows:
        if r["pc"] == 20 and (prev is None or prev["pc"] != 20):
            actions["pearl throw"].append(r["t"])
        if r["hit"] == 7 and (prev is None or prev["hit"] != 7):
            actions["melee swing"].append(r["t"])
        if prev is not None and r["anc"] > prev["anc"] + 3:
            actions["anchor place/charge"].append(r["t"])
        if r["item"] != (prev or {}).get("item"):
            if r["item"] == "respawn_anchor":
                actions["anchor place"].append(r["t"])
            elif r["item"] == "glowstone":
                actions["anchor charge"].append(r["t"])
            elif r["item"] == "end_crystal":
                actions["crystal place"].append(r["t"])
            elif r["item"] == "diamond_sword":
                actions["sword out"].append(r["t"])
        prev = r
    dwell = Counter(r["band"] for r in rows)
    band_of = {r["t"]: r["band"] for r in rows}
    with_band = {k: [(t, band_of[t]) for t in v] for k, v in actions.items()}
    return span, dwell, with_band


# ------------------------------------------------------------------------ plugin
SAMPLE = re.compile(r"\[N Arena\]\[BotMatch\]\s+([\d.]+)\s+s p=(-?[\d.]+),(-?[\d.]+),(-?[\d.]+)")
TRACE = re.compile(r"\[N Arena\]\[BotMatch\]\s+([\d.]+)s\s+(.+)$")
END = re.compile(r"\[N Arena\]\[BotMatch\] END .*duration=(\d+)s")


def plugin_profile(path: str, dummy):
    samples = []          # (t, x, y, z)
    events = []           # (t, text)
    duration = None
    for line in _open(path):
        m = END.search(line)
        if m:
            duration = int(m.group(1))
            continue
        m = SAMPLE.search(line)
        if m:
            # The sampler writes DECISECONDS since session start (10 units = 1 s), while the
            # event trace writes seconds with one decimal. Same clock, different unit — fold
            # it here or every per-action distance band is matched against the wrong instant.
            t = float(m.group(1)) / 10.0
            samples.append((t, float(m.group(2)), float(m.group(3)), float(m.group(4))))
            continue
        m = TRACE.search(line)
        if m and not m.group(2).startswith("s p="):
            events.append((float(m.group(1)), m.group(2)))
    if duration is None and samples:
        duration = samples[-1][0]
    if not samples and not events:
        raise SystemExit(f"no [N Arena][BotMatch] data in {path}")

    dx, dy, dz = dummy
    band_at = []
    for t, x, y, z in samples:
        d = ((x - dx) ** 2 + (y - dy) ** 2 + (z - dz) ** 2) ** 0.5
        band = "<=2" if d <= 2 else "<=3" if d <= 3 else "<=6" if d <= 6 else "<=9" if d <= 9 else ">9"
        band_at.append((t, band))
    band_of = {}
    for t, band in band_at:
        band_of[round(t, 1)] = band
    actions = defaultdict(list)
    for t, text in events:
        key = ("anchor place" if text.startswith("anchor place") else
               "anchor charge" if text.startswith("anchor charge") else
               "anchor detonate" if text.startswith("anchor detonate") else
               "crystal place" if text.startswith("crystal place") else
               "crystal detonate" if text.startswith("crystal detonate") else
               "pearl throw" if text.startswith("pearl") else
               "melee swing" if text.startswith("swing") else
               "melee hit" if text.startswith("hit") else
               "totem pop" if "POP" in text else text)
        # 一番近いサンプルの距離バンドに寄せる(サンプルは 0.1 s 間隔)
        approx = min(band_of, key=lambda s: abs(s - t)) if band_of else None
        actions[key].append((t, band_of.get(approx, "?")))
    dwell = Counter(band for _, band in band_at)
    return duration, dwell, actions


def report(kind: str, path: str, dummy=None):
    if kind == "ref":
        rows = read_ref(path)
        span, dwell, actions = ref_profile(rows)
        t0 = rows[0]["t"] / 20.0
        times = {k: [t / 20.0 - t0 for t, _ in v] for k, v in actions.items()}
        bands = {k: [b for _, b in v] for k, v in actions.items()}
    else:
        span, dwell, actions = plugin_profile(path, dummy)
        times = {k: [t for t, _ in v] for k, v in actions.items()}
        bands = {k: [b for _, b in v] for k, v in actions.items()}

    n = sum(dwell.values()) or 1
    print(f"=== {path} ({kind}) ===  長さ {span:.0f}s")
    print("距離滞在: " + "  ".join(f"{b}:{100 * dwell.get(b, 0) / n:.1f}%" for b in BANDS))
    order = ["pearl throw", "anchor place", "anchor charge", "anchor detonate",
             "crystal place", "crystal detonate", "melee swing", "melee hit", "totem pop"]
    print(f"{'行動':<18}{'回数':>6}{'/min':>8}   距離バンド内訳")
    for name in order:
        ts = times.get(name, [])
        if not ts:
            continue
        c = Counter(bands.get(name, []))
        tot = len(ts) or 1
        dist = "  ".join(f"{b}:{100 * c.get(b, 0) / tot:3.0f}%" for b in BANDS if c.get(b))
        gaps = [round(ts[i + 1] - ts[i], 2) for i in range(len(ts) - 1)]
        med = sorted(gaps)[len(gaps) // 2] if gaps else 0.0
        print(f"{name:<18}{len(ts):>6}{len(ts) / (span / 60):>8.1f}   中央ギャップ {med:5.2f}s | {dist}")
    known = set(order)
    for name, ts in times.items():
        if name in known:
            continue
        print(f"{name:<18}{len(ts):>6}{len(ts) / (span / 60):>8.1f}   (その他)")


def main() -> int:
    if len(sys.argv) < 3 or sys.argv[1] not in ("ref", "plugin"):
        print(__doc__)
        return 2
    kind, path = sys.argv[1], sys.argv[2]
    dummy = None
    if "--dummy" in sys.argv:
        dummy = tuple(float(v) for v in sys.argv[sys.argv.index("--dummy") + 1].split(","))
    report(kind, path, dummy)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
