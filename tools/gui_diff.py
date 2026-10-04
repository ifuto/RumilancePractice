#!/usr/bin/env python3
"""Diff the live GUI renders against the docs/design/gui.json mockups.

Why this exists
---------------
A menu's grid cannot be read off the source: ``render()`` builds it with loops, helper
methods, conditionals and runtime state (kit lists, party membership, queue depth). The
plugin therefore renders each menu OFFSCREEN (``/guisnapshot all``) into
``plugins/n-arena/gui-snapshots/<TYPE>.json``, and this script compares those snapshots with
the mockup saves, cell by cell.

Usage
-----
    # 1. on the server (admin, headless is fine):
    #      /guisnapshot all
    # 2. copy plugins/n-arena/gui-snapshots/ next to this repo (or point --snapshots at it)
    python3 tools/gui_diff.py                     # human-readable report
    python3 tools/gui_diff.py --screen "KIT SELECT GUI"
    python3 tools/gui_diff.py --json out.json     # machine-readable
    python3 tools/gui_diff.py --strict            # content cells are errors, not notes

Exit code is 1 when any screen has a decoration mismatch (so CI can gate on it once the
snapshots are produced there), unless --no-fail is given.
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MOCKUPS = ROOT / "docs" / "design" / "gui.json"
MAP_FILE = ROOT / "tools" / "gui_screen_map.json"
DEFAULT_SNAPSHOTS = ROOT / "gui-snapshots"

DECOR_SUFFIXES = (
    "_stained_glass_pane",
    "_glass_pane",
    "_chain",
)


def is_decoration(item_id: str) -> bool:
    return item_id is not None and item_id.endswith(DECOR_SUFFIXES)


def short(item_id: str | None) -> str:
    if not item_id:
        return "·"
    name = item_id.split(":")[-1]
    for suffix in DECOR_SUFFIXES:
        if name.endswith(suffix):
            base = name[: -len(suffix)]
            return {
                "green": "GREEN", "gray": "GRAY", "light_gray": "LGRY", "white": "WHT",
                "black": "BLK", "red": "RED", "blue": "BLUE", "light_blue": "LBLU",
                "yellow": "YEL", "lime": "LIME", "orange": "ORNG", "": "GLAS",
            }.get(base, base[:4].upper())
    return name[:4].upper()


def cell_id(cell) -> str | None:
    return cell.get("id") if isinstance(cell, dict) else None


def pad(rows: list, size: int) -> list:
    out = list(rows)
    while len(out) < size:
        out.append(None)
    return out


def compare_screen(name: str, expected: dict, snapshot: dict, strict: bool):
    exp_container = expected.get("container", {}).get("items") or []
    got_container = snapshot.get("container") or []
    rows = snapshot.get("rows") or (len(exp_container) // 9)
    size = rows * 9
    exp_container = pad(exp_container, size)
    got_container = pad(got_container, size)

    problems = []          # decoration mismatches = must fix
    notes = []             # content mismatches = informational
    grid = []              # rendered rows for the report
    for r in range(rows):
        row = []
        for c in range(9):
            i = r * 9 + c
            e = cell_id(exp_container[i])
            g = cell_id(got_container[i])
            if e == g:
                row.append(short(g))
                continue
            row.append(f"[{short(e)}→{short(g)}]")
            line = f"({r},{c}) expected {short(e)} ({e}) but got {short(g)} ({g})"
            (problems if is_decoration(e) or is_decoration(g) else notes).append(line)
        grid.append(row)

    # Bottom (player inventory) rows — only for screens whose mockup defines them.
    exp_main = expected.get("main") or []
    if any(exp_main) and snapshot.get("bottomOwned"):
        got_main = pad(snapshot.get("main") or [], 36)
        exp_main = pad(exp_main, 36)
        for r in range(4):
            row = []
            for c in range(9):
                i = r * 9 + c
                e = cell_id(exp_main[i])
                g = cell_id(got_main[i])
                if e == g:
                    row.append(short(g))
                    continue
                row.append(f"[{short(e)}→{short(g)}]")
                line = f"bottom({i}) expected {short(e)} ({e}) but got {short(g)} ({g})"
                (problems if is_decoration(e) or is_decoration(g) else notes).append(line)
            grid.append(row)

    if len(exp_container) != size:
        notes.append(f"row count differs: mockup has {len(exp_container) // 9}, snapshot has {rows}")
    if strict:
        problems.extend(notes)
        notes = []
    return {"screen": name, "grid": grid, "problems": problems, "notes": notes}


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--snapshots", type=Path, default=DEFAULT_SNAPSHOTS,
                    help="directory holding <GuiType>.json snapshots (default: ./gui-snapshots)")
    ap.add_argument("--screen", action="append", help="only this gui.json screen name")
    ap.add_argument("--json", type=Path, help="write the report as JSON too")
    ap.add_argument("--strict", action="store_true", help="treat content mismatches as failures")
    ap.add_argument("--no-fail", action="store_true", help="always exit 0")
    args = ap.parse_args()

    if not MOCKUPS.is_file():
        print(f"missing mockups: {MOCKUPS}", file=sys.stderr)
        return 2
    if not args.snapshots.is_dir():
        print(f"missing snapshots: {args.snapshots}\n"
              f"  run '/guisnapshot all' on the server and copy plugins/n-arena/gui-snapshots/ here",
              file=sys.stderr)
        return 2
    mapping = json.loads(MAP_FILE.read_text()) if MAP_FILE.is_file() else {}

    saves = json.loads(MOCKUPS.read_text()).get("saves", [])
    report = []
    for save in saves:
        name = save.get("name")
        if args.screen and name not in args.screen:
            continue
        entry = mapping.get(name)
        if not entry:
            report.append({"screen": name, "grid": [], "problems": [],
                           "notes": ["no snapshot mapping in tools/gui_screen_map.json"]})
            continue
        snap_file = args.snapshots / f"{entry['file']}.json"
        if not snap_file.is_file():
            report.append({"screen": name, "grid": [], "problems": [],
                           "notes": [f"snapshot missing: {snap_file.name}"]})
            continue
        report.append(compare_screen(name, save, json.loads(snap_file.read_text()), args.strict))

    total_problems = 0
    for entry in report:
        problems, notes = entry["problems"], entry["notes"]
        total_problems += len(problems)
        status = "OK  " if not problems else "FAIL"
        print(f"=== {status} {entry['screen']}  "
              f"(decoration mismatches: {len(problems)}, notes: {len(notes)})")
        if entry["grid"]:
            for r, row in enumerate(entry["grid"]):
                print(f"   r{r}: " + " ".join(f"{c:<14}" for c in row))
        for line in problems:
            print(f"   ! {line}")
        for line in notes:
            print(f"   - {line}")
        print()

    if args.json:
        args.json.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n")
        print(f"wrote {args.json}")

    print(f"total decoration mismatches: {total_problems}")
    if total_problems and not args.no_fail:
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
