#!/usr/bin/env python3
"""Print the docs/design/gui.json mockups as readable 9-wide grids.

This is the "what should it look like" half of the audit.  The other half — what the
code actually renders — cannot be produced without a running server, so
``tools/gui_diff.py`` compares against snapshots taken by ``/guisnapshot all``.
Use this script when you want to read the mockup itself.

Usage
-----
    python3 tools/gui_grid.py                      # all 10 saves
    python3 tools/gui_grid.py --screen "KIT EDIT"  # substring match on the name
    python3 tools/gui_grid.py --bottom             # also print the main[36] rows
    python3 tools/gui_grid.py --compact            # 5-char cells, fits a narrow screen
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MOCKUPS = ROOT / "docs" / "design" / "gui.json"

DECOR_SUFFIXES = ("_stained_glass_pane", "_glass_pane", "_chain")

_PANE_COLORS = {
    "green": "GREEN", "gray": "GRAY", "light_gray": "LGRY", "white": "WHT",
    "black": "BLK", "red": "RED", "blue": "BLUE", "light_blue": "LBLU",
    "yellow": "YEL", "lime": "LIME", "orange": "ORNG", "pink": "PINK",
    "magenta": "MAG", "purple": "PURP", "cyan": "CYAN", "brown": "BROW",
    "": "GLAS", "weathered_copper": "CHN", "copper": "CHN",
}

# Non-pane items get a short, stable nickname so grids stay readable.
_ITEMS = {
    "diamond_helmet": "HELM", "diamond_chestplate": "CHST",
    "diamond_leggings": "LEGS", "diamond_boots": "BOOT",
    "diamond_sword": "DSWD", "diamond_axe": "DAXE", "iron_axe": "IAXE",
    "iron_sword": "ISWD", "golden_sword": "GSWD", "stone_sword": "SSWD",
    "bow": "BOW", "arrow": "AROW", "crossbow": "XBOW", "shield": "SHLD",
    "ender_pearl": "PEAR", "end_crystal": "CRYS", "obsidian": "OBSI",
    "golden_apple": "GAPP", "enchanted_golden_apple": "EGAP",
    "potion": "POTN", "splash_potion": "SPOT", "lingering_potion": "LPOT",
    "milk_bucket": "MILK", "water_bucket": "WBUC", "lava_bucket": "LBUC",
    "cooked_beef": "BEEF", "steak": "BEEF", "bread": "BRED",
    "book": "BOOK", "written_book": "WBOO", "enchanted_book": "EBOO",
    "paper": "PAPE", "map": "MAP", "filled_map": "FMAP",
    "barrier": "BARR", "player_head": "HEAD", "skeleton_skull": "SKUL",
    "emerald": "EMER", "diamond": "DIAM", "gold_ingot": "GOLD",
    "iron_ingot": "IRON", "netherite_ingot": "NETH",
    "redstone": "RSTN", "comparator": "CMPR", "repeater": "REPT",
    "lever": "LEVR", "hopper": "HOPP", "chest": "CHES",
    "ender_chest": "ECHE", "shulker_box": "SHUL",
    "clock": "CLOC", "compass": "COMP", "name_tag": "NTAG",
    "anvil": "ANVL", "crafting_table": "CRAF", "furnace": "FURN",
    "blast_furnace": "BFUR", "smithing_table": "SMIT",
    "white_bed": "BED", "red_bed": "BED", "blue_bed": "BED",
    "tnt": "TNT", "flint_and_steel": "FLNT", "fire_charge": "FIRE",
    "cobweb": "COBW", "soul_sand": "SOUL", "slime_block": "SLIM",
    "hay_block": "HAY", "ladder": "LADD", "scaffolding": "SCAF",
    "experience_bottle": "XP", "ender_eye": "EYE",
    "firework_star": "STAR", "firework_rocket": "FWK",
    "lightning_rod": "ROD", "bell": "BELL", "beacon": "BEAC",
    "totem_of_undying": "TOTM", "elytra": "ELYT",
    "torch": "TORC", "soul_torch": "STOR", "lantern": "LANT",
    "redstone_torch": "RTOR", "redstone_lamp": "RLMP",
    "white_wool": "WOOL", "white_concrete": "CONC",
    "iron_bars": "BARS", "iron_door": "DOOR",
    "fishing_rod": "FISH", "carrot_on_a_stick": "CROS",
    "warped_fungus_on_a_stick": "WFOS", "saddle": "SADL",
    "leather_helmet": "LHELM", "leather_chestplate": "LCHST",
    "leather_leggings": "LLEGS", "leather_boots": "LBOOT",
    "chainmail_helmet": "CHLM", "chainmail_chestplate": "CHCH",
    "chainmail_leggings": "CHLG", "chainmail_boots": "CHBT",
    "iron_helmet": "IHELM", "iron_chestplate": "ICHST",
    "iron_leggings": "ILEGS", "iron_boots": "IBOOT",
    "golden_helmet": "GHELM", "golden_chestplate": "GCHST",
    "golden_leggings": "GLEGS", "golden_boots": "GBOOT",
    "netherite_helmet": "NHELM", "netherite_chestplate": "NCHST",
    "netherite_leggings": "NLEGS", "netherite_boots": "NBOOT",
    "netherite_sword": "NSWD", "netherite_axe": "NAXE",
    "trident": "TRID", "mace": "MACE", "wind_charge": "WIND",
    "spectral_arrow": "SARW", "tipped_arrow": "TARW",
    "snowball": "SNOW", "egg": "EGG", "slime_ball": "SLMB",
}


def short(item_id):
    if not item_id:
        return "·"
    name = item_id.split(":")[-1]
    for suffix in DECOR_SUFFIXES:
        if name.endswith(suffix):
            base = name[: -len(suffix)]
            return _PANE_COLORS.get(base, base[:4].upper())
    return _ITEMS.get(name, name[:4].upper())


def cell_id(cell):
    return cell.get("id") if isinstance(cell, dict) else None


def grid_lines(items, first_index=0, width=9, cell_w=13):
    out = []
    for r in range((len(items) + width - 1) // width):
        row = []
        for c in range(width):
            i = r * width + c
            cell = items[i] if i < len(items) else None
            if isinstance(cell, dict):
                label = short(cell.get("id"))
                n = cell.get("count", 1)
                if n and n > 1:
                    label = f"{label}x{n}"
            else:
                label = "·"
            row.append(f"{label:<{cell_w}}")
        out.append(f"  {first_index + r:>2}| " + " ".join(row))
    return out


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--screen", action="append", help="only names containing this")
    ap.add_argument("--bottom", action="store_true", help="also print main[36]")
    ap.add_argument("--compact", action="store_true", help="narrower cells")
    ap.add_argument("--names", action="store_true", help="list custom_name for each cell")
    args = ap.parse_args()

    if not MOCKUPS.is_file():
        print(f"missing mockups: {MOCKUPS}", file=sys.stderr)
        return 2

    saves = json.loads(MOCKUPS.read_text()).get("saves", [])
    cell_w = 6 if args.compact else 13
    for save in saves:
        name = save.get("name", "?")
        if args.screen and not any(s.lower() in name.lower() for s in args.screen):
            continue
        items = save.get("container", {}).get("items") or []
        # Drop trailing all-null rows so 3-row and 6-row chests both read naturally.
        while items and not items[-1]:
            items.pop()
        print(f"=== {name}   ({len(items) // 9} rows x 9)")
        print("     " + " ".join(f"{c:<{cell_w}}" for c in range(9)))
        for line in grid_lines(items, cell_w=cell_w):
            print(line)
        if args.names:
            for i, cell in enumerate(items):
                if isinstance(cell, dict):
                    comp = cell.get("components") or {}
                    nm = comp.get("minecraft:custom_name")
                    if nm:
                        print(f"     ({i // 9},{i % 9}) {short(cell.get('id')):<6} {nm}")
        if args.bottom:
            main_items = save.get("main") or []
            if any(main_items):
                print("    -- main[36] (player inventory) --")
                for line in grid_lines(main_items, cell_w=cell_w):
                    print(line)
        print()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
