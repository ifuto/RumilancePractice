#!/usr/bin/env python3
"""fight_timeline.py — qlog([q]) 1本を「何をいつ使ったか」のタイムライン図にする。

Usage:
    python3 tools/fight_timeline.py <qlog|latest.log>[.gz] <out.svg|out.png> [--title "..."]

描くもの:

  段1 手持ちアイテムの遷移(色つき帯)… 何を使って戦っているかが一目で分かる
  段2 クリスタル設置 / 爆発
  段3 アンカー 設置 / チャージ / 爆発 … 「勝手にアンカーを出した」証拠の段
  段4 BOT と相手の HP 推移 + トーテム POP

.svg は標準ライブラリだけで書ける(依存ゼロ)。.png は matplotlib (+日本語フォントが
必要なら japanize-matplotlib)を使う。イベント検出は tools/parity_report.py と同じ規則
(行動=そのアイテムを手に持った瞬間、累計カウンタの増加=POP/爆発)で、解釈は
tools/qlog-datapack/README.md を参照。
"""
import sys

sys.path.insert(0, __file__.rsplit("/", 1)[0])
from parity_report import load, rises  # noqa: E402

ITEM_COLOR = {
    "end_crystal": "#c46bf0",
    "obsidian": "#3b2f63",
    "respawn_anchor": "#f0a24b",
    "glowstone": "#ffd94a",
    "ender_pearl": "#31c5a1",
    "totem_of_undying": "#f2e96b",
    "netherite_sword": "#8b93a8",
    "diamond_sword": "#7fe0e6",
    "shield": "#b58a5a",
    "golden_apple": "#f6c85f",
    "enchanted_golden_apple": "#c07ef0",
    "air": "#1b2030",
}


def switches(rows, name):
    """そのアイテムを手に持った瞬間(=使った瞬間)のサンプル添字。"""
    return [i for i in range(1, len(rows))
            if rows[i]["item"] == name and rows[i - 1]["item"] != name]


def events(rows):
    """タイムラインに落とす行動イベント一式(添字は rows の位置)。"""
    return dict(
        ct_place=switches(rows, "end_crystal"),
        anc_place=switches(rows, "respawn_anchor"),
        chg=switches(rows, "glowstone"),
        # 爆発タイマ(exp)の再装填。アンカーはチャージの直後に必ず爆発するので
        # 「設置→チャージ」が揃った時点でアンカー1サイクル成立とみなせる。
        exp=[i for i in rises([r["exp"] for r in rows])],
        pop=[i for i in rises([r["pop"] for r in rows])],
        boom=[i for i in range(1, len(rows)) if rows[i]["ec"] < rows[i - 1]["ec"]],
    )


def segments(rows, key):
    """連続して同じ値の区間 [(start_tick, end_tick, value), ...]。"""
    out = []
    if not rows:
        return out
    start, prev = rows[0]["t"], rows[0][key]
    for r in rows[1:]:
        if r[key] != prev:
            out.append((start, r["t"], prev))
            start, prev = r["t"], r[key]
    out.append((start, rows[-1]["t"], prev))
    return out


def summary(title, rows, ev):
    span = rows[-1]["t"] - rows[0]["t"]
    return (f'{title} — {span / 20.0:.0f}s ({span} tick) / サンプル {len(rows)} / '
            f'アンカー 設置{len(ev["anc_place"])} チャージ{len(ev["chg"])} '
            f'爆発{len(ev["exp"])} / クリスタル 設置{len(ev["ct_place"])} '
            f'爆発{len(ev["boom"])} / トーテムPOP {len(ev["pop"])}')


# ------------------------------------------------------------------ SVG
def render_svg(dst, title, rows, ev):
    W, H, L, R = 1600, 520, 90, 40
    TOP, LINES = 300, 110
    t0, span = rows[0]["t"], max(1, rows[-1]["t"] - rows[0]["t"])
    span_s = span / 20.0

    def X(tick):
        return L + (tick - t0) / span * (W - L - R)

    def esc(s):
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    hps = [r["hp"] for r in rows] + [r["hpT"] / 10.0 for r in rows if r["hpT"]] + [20.0]

    def Y(hp):
        return TOP + LINES - (hp / max(hps)) * LINES

    items = segments(rows, "item")
    legend = sorted({v for _, _, v in items if v in ITEM_COLOR})

    out = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" '
        f'viewBox="0 0 {W} {H}" font-family="sans-serif">',
        f'<rect width="{W}" height="{H}" fill="#10141c"/>',
        f'<text x="{L}" y="30" fill="#e8eef8" font-size="19">{esc(title)}</text>',
        f'<text x="{L}" y="52" fill="#8fa0bb" font-size="13">'
        f'{esc(summary("", rows, ev).lstrip(" —"))}</text>',
    ]

    rows_y = {"item": (70, 34, "手持ちアイテム"), "crystal": (140, 30, "クリスタル"),
              "anchor": (178, 30, "アンカー")}
    for y, h, label in rows_y.values():
        out.append(f'<rect x="{L}" y="{y}" width="{W - L - R}" height="{h}" '
                   f'fill="#181f2b" stroke="#243044"/>')
        out.append(f'<text x="{L - 8}" y="{y + h * 0.7}" fill="#8fa0bb" font-size="12" '
                   f'text-anchor="end">{label}</text>')

    y, h, _ = rows_y["item"]
    for s, e, v in items:
        out.append(f'<rect x="{X(s):.1f}" y="{y}" width="{max(0.6, X(max(e, s + 1)) - X(s)):.1f}" '
                   f'height="{h}" fill="{ITEM_COLOR.get(v, "#39415a")}"/>')
    lx = L
    for name in legend:
        out.append(f'<rect x="{lx}" y="{y + h + 6}" width="10" height="10" '
                   f'fill="{ITEM_COLOR[name]}"/>')
        out.append(f'<text x="{lx + 14}" y="{y + h + 15}" fill="#c7d3e6" font-size="11">'
                   f'{name}</text>')
        lx += 22 + 7 * len(name)

    def marks(key, y, h, color, label):
        for i in ev[key]:
            out.append(f'<rect x="{X(rows[i]["t"]):.1f}" y="{y}" width="3" height="{h}" '
                       f'fill="{color}"/>')
        out.append(f'<text x="{L + 6}" y="{y - 4}" fill="{color}" font-size="11">'
                   f'{label} ×{len(ev[key])}</text>')

    y, h, _ = rows_y["crystal"]
    marks("ct_place", y, h, "#c46bf0", "設置")
    marks("boom", y, h, "#ff7ad9", "爆発")

    y, h, _ = rows_y["anchor"]
    marks("anc_place", y, h, "#6fa8ff", "設置")
    marks("chg", y, h, "#ffd94a", "チャージ")
    marks("exp", y, h, "#ff5f5f", "爆発タイマ再装填")

    out.append(f'<rect x="{L}" y="{TOP}" width="{W - L - R}" height="{LINES}" '
               f'fill="#0d1119" stroke="#243044"/>')
    out.append(f'<text x="{L - 8}" y="{TOP + 16}" fill="#8fa0bb" font-size="12" '
               f'text-anchor="end">HP</text>')
    for hp in (5, 10, 15, 20):
        out.append(f'<line x1="{L}" y1="{Y(hp):.1f}" x2="{W - R}" y2="{Y(hp):.1f}" '
                   f'stroke="#1d2635"/>')
        out.append(f'<text x="{L - 8}" y="{Y(hp) + 4:.1f}" fill="#5f6f8a" font-size="10" '
                   f'text-anchor="end">{hp}</text>')
    step = max(1, len(rows) // 900)
    out.append('<polyline points="' + " ".join(
        f"{X(r['t']):.1f},{Y(r['hp']):.1f}" for r in rows[::step])
        + '" fill="none" stroke="#4fa3ff" stroke-width="1.6"/>')
    tar = " ".join(f"{X(r['t']):.1f},{Y(r['hpT'] / 10.0):.1f}"
                   for r in rows[::step] if r["hpT"])
    if tar:
        out.append(f'<polyline points="{tar}" fill="none" stroke="#ff6b6b" stroke-width="1.6"/>')
    for i in ev["pop"]:
        out.append(f'<circle cx="{X(rows[i]["t"]):.1f}" cy="{Y(rows[i]["hp"]):.1f}" r="3.4" '
                   f'fill="none" stroke="#f2e96b" stroke-width="1.6"/>')
    out.append(f'<text x="{L + 6}" y="{TOP + LINES - 6}" fill="#c7d3e6" font-size="11">'
               f'青=BOT HP 赤=相手 HP 黄丸=トーテムPOP</text>')

    for k in range(11):
        xx, s = L + (W - L - R) * k / 10, span_s * k / 10
        out.append(f'<line x1="{xx:.1f}" y1="{H - 46}" x2="{xx:.1f}" y2="{H - 40}" '
                   f'stroke="#3a465e"/>')
        out.append(f'<text x="{xx:.1f}" y="{H - 26}" fill="#5f6f8a" font-size="11" '
                   f'text-anchor="middle">{s:.0f}s</text>')
    out.append("</svg>")
    with open(dst, "w", encoding="utf-8") as fh:
        fh.write("\n".join(out))


# ------------------------------------------------------------------ PNG
def render_png(dst, title, rows, ev):
    import matplotlib
    matplotlib.use("Agg")
    try:  # 日本語ラベルのために日本語フォントを拾えれば使う(無くても動く)
        import japanize_matplotlib  # noqa: F401
    except ImportError:
        pass
    import matplotlib.pyplot as plt

    t0 = rows[0]["t"]
    sec = [(r["t"] - t0) / 20.0 for r in rows]
    fig, axes = plt.subplots(
        4, 1, figsize=(16, 7.2), sharex=True, facecolor="#0f131b",
        gridspec_kw={"height_ratios": [1.1, 1.5, 1.5, 2.2], "hspace": 0.28})
    for ax in axes:
        ax.set_facecolor("#151b26")
        ax.tick_params(colors="#8fa0bb", labelsize=9)
        for s in ax.spines.values():
            s.set_color("#243044")
        ax.grid(axis="x", color="#1d2635", linewidth=0.6)

    runs, start, prev = [], sec[0], rows[0]["item"]
    for r, s in zip(rows[1:], sec[1:]):
        if r["item"] != prev:
            runs.append((start, s, prev))
            start, prev = s, r["item"]
    runs.append((start, sec[-1], prev))
    used = [k for k in ITEM_COLOR if any(v == k for _, _, v in runs)]
    ypos = {k: i for i, k in enumerate(used)}
    for a, b, v in runs:
        axes[0].broken_barh([(a, max(b - a, 0.05))], (ypos[v], 0.8),
                            facecolors=ITEM_COLOR.get(v, "#39415a"))
    axes[0].set_yticks([ypos[k] + 0.4 for k in used], used, color="#c7d3e6", fontsize=9)
    axes[0].set_ylim(-0.3, len(used))
    axes[0].set_title(title, color="#e8eef8", fontsize=13, loc="left")

    def bars(ax, key, color, y, label):
        ax.eventplot([[sec[i] for i in ev[key]]], colors=color, lineoffsets=y,
                     linelengths=0.34, linewidths=2.4)
        return f"{label} {len(ev[key])}"

    l1 = bars(axes[1], "ct_place", "#c46bf0", 0.7, "設置")
    l2 = bars(axes[1], "boom", "#ff7ad9", 0.2, "爆発")
    axes[1].set_yticks([0.7, 0.2], [l1, l2], color="#c7d3e6", fontsize=9)
    axes[1].set_ylim(-0.3, 1.2)

    l1 = bars(axes[2], "anc_place", "#6fa8ff", 0.85, "設置")
    l2 = bars(axes[2], "chg", "#ffd94a", 0.48, "チャージ")
    l3 = bars(axes[2], "exp", "#ff5f5f", 0.11, "爆発")
    axes[2].set_yticks([0.85, 0.48, 0.11], [l1, l2, l3], color="#c7d3e6", fontsize=9)
    axes[2].set_ylim(-0.2, 1.25)

    axes[3].plot(sec, [r["hp"] for r in rows], color="#4fa3ff", linewidth=1.3, label="BOT HP")
    axes[3].plot(sec, [r["hpT"] / 10.0 for r in rows], color="#ff6b6b", linewidth=1.3,
                 label="相手 HP")
    axes[3].scatter([sec[i] for i in ev["pop"]], [rows[i]["hp"] for i in ev["pop"]],
                    facecolors="none", edgecolors="#f2e96b", s=42, linewidths=1.4,
                    label="トーテムPOP", zorder=5)
    axes[3].set_ylim(0, 21)
    axes[3].set_xlim(0, sec[-1])
    axes[3].set_xlabel("秒", color="#8fa0bb", fontsize=10)
    axes[3].set_ylabel("HP", color="#8fa0bb", fontsize=10)
    axes[3].legend(facecolor="#151b26", edgecolor="#243044", labelcolor="#c7d3e6",
                   fontsize=9, loc="upper right", ncols=3)
    fig.savefig(dst, dpi=100, facecolor="#0f131b", bbox_inches="tight")
    plt.close(fig)


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    if len(args) < 2:
        print(__doc__)
        return 2
    src, dst = args[0], args[1]
    title = "QuantumBOT 実測タイムライン"
    for i, a in enumerate(sys.argv):
        if a == "--title" and i + 1 < len(sys.argv):
            title = sys.argv[i + 1]

    rows = load(src)
    if not rows:
        print("qlog行が見つかりません:", src)
        return 1
    ev = events(rows)
    if dst.endswith(".png"):
        render_png(dst, title, rows, ev)
    else:
        render_svg(dst, title, rows, ev)
    print(f"wrote {dst} — {summary(title, rows, ev)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
