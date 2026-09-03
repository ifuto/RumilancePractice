#!/usr/bin/env python3
"""
Rumilance — custom shield injector for the server resource pack.

Adds ONE high-resolution custom shield artwork to `resourcepack/` for a given
Custom Model Data value (the same value you assign in-game with
`/urank shield <player> <cmd>` or the Custom Shield admin GUI).

Usage:
    python3 tools/add_custom_shield.py <cmd> <image.png> [--pack-root resourcepack]

What it does:
  1. Copies <image.png> to assets/rumilance/textures/shield/shield_<cmd>.png
     (recommended size: 512x512 for a crisp "god-tier" look; the vanilla shield
     UV map is used, so lay the art out like the vanilla shield texture).
  2. Creates assets/rumilance/models/item/shield_<cmd>.json (+ a _blocking twin)
     using the vanilla shield display transforms.
  3. Rewrites assets/minecraft/models/item/shield.json with a custom_model_data
     override for this cmd (existing overrides are preserved; the registry is
     kept inside the file under the "_rumilance_shields" key, which Minecraft
     ignores).

Afterwards rebuild the pack zip (tools/build_resourcepack or your usual method),
re-upload it, and the player holding that model data gets the artwork shield in
every match.
"""

import argparse
import json
import shutil
import sys
from pathlib import Path

# Vanilla item/shield.json display transforms (unchanged since 1.9).
DISPLAY = {
    "thirdperson_righthand": {"rotation": [0, 90, 0], "translation": [10, 6, -4], "scale": [1, 1, 1]},
    "thirdperson_lefthand": {"rotation": [0, 90, 0], "translation": [10, 6, 12], "scale": [1, 1, 1]},
    "firstperson_righthand": {"rotation": [0, 180, 5], "translation": [-10, 2, -10], "scale": [1.25, 1.25, 1.25]},
    "firstperson_lefthand": {"rotation": [0, 180, 5], "translation": [10, 0, -10], "scale": [1.25, 1.25, 1.25]},
    "gui": {"rotation": [15, -25, -5], "translation": [2, 3, 0], "scale": [0.65, 0.65, 0.65]},
    "fixed": {"rotation": [0, 180, 0], "translation": [-2, 4, -5], "scale": [0.5, 0.5, 0.5]},
    "ground": {"rotation": [0, 0, 0], "translation": [4, 4, 2], "scale": [0.25, 0.25, 0.25]},
}

# Vanilla item/shield_blocking.json display transforms.
DISPLAY_BLOCKING = {
    "thirdperson_righthand": {"rotation": [45, 135, 0], "translation": [3.51, 11, -2], "scale": [1, 1, 1]},
    "thirdperson_lefthand": {"rotation": [45, 135, 0], "translation": [13.51, 3, 5], "scale": [1, 1, 1]},
    "firstperson_righthand": {"rotation": [0, 180, -5], "translation": [-15, 5, -11], "scale": [1.25, 1.25, 1.25]},
    "firstperson_lefthand": {"rotation": [0, 180, -5], "translation": [5, 5, -11], "scale": [1.25, 1.25, 1.25]},
    "gui": {"rotation": [15, -25, -5], "translation": [2, 3, 0], "scale": [0.65, 0.65, 0.65]},
}

REGISTRY_KEY = "_rumilance_shields"


def model_for(texture: str, blocking_model: str) -> dict:
    return {
        "parent": "builtin/entity",
        "gui_light": "front",
        "textures": {
            "shield_base": texture,
            "shield_base_nopattern": texture,
            "particle": "block/dark_oak_planks",
        },
        "display": DISPLAY,
        "overrides": [
            {"predicate": {"blocking": 1}, "model": blocking_model},
        ],
    }


def model_blocking(texture: str) -> dict:
    m = model_for(texture, "")
    m.pop("overrides", None)
    m["display"] = DISPLAY_BLOCKING
    return m


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("cmd", type=int, help="Custom Model Data value (assigned via /urank shield)")
    ap.add_argument("image", help="Shield artwork PNG (vanilla shield UV layout)")
    ap.add_argument("--pack-root", default="resourcepack", help="Resource pack root (default: resourcepack)")
    args = ap.parse_args()

    if args.cmd <= 0:
        print("cmd must be a positive integer", file=sys.stderr)
        return 1
    image = Path(args.image)
    if not image.exists():
        print(f"image not found: {image}", file=sys.stderr)
        return 1

    root = Path(args.pack_root)
    textures = root / "assets" / "rumilance" / "textures" / "shield"
    models = root / "assets" / "rumilance" / "models" / "item"
    vanilla_item_models = root / "assets" / "minecraft" / "models" / "item"
    for d in (textures, models, vanilla_item_models):
        d.mkdir(parents=True, exist_ok=True)

    # 1. texture
    dest_png = textures / f"shield_{args.cmd}.png"
    shutil.copyfile(image, dest_png)
    texture_ref = f"rumilance:shield/shield_{args.cmd}"

    # 2. models
    blocking_model_id = f"rumilance:item/shield_{args.cmd}_blocking"
    (models / f"shield_{args.cmd}.json").write_text(
        json.dumps(model_for(texture_ref, blocking_model_id), indent=2) + "\n")
    (models / f"shield_{args.cmd}_blocking.json").write_text(
        json.dumps(model_blocking(texture_ref), indent=2) + "\n")

    # 3. vanilla shield.json with overrides (created on first use)
    shield_json = vanilla_item_models / "shield.json"
    if shield_json.exists():
        data = json.loads(shield_json.read_text())
    else:
        data = {
            "parent": "builtin/entity",
            "gui_light": "front",
            "textures": {
                "shield_base": "item/shield_base",
                "shield_base_nopattern": "item/shield_base_nopattern",
                "particle": "block/dark_oak_planks",
            },
            "display": DISPLAY,
            "overrides": [
                {"predicate": {"blocking": 1}, "model": "item/shield_blocking"},
            ],
        }

    registry = data.get(REGISTRY_KEY, {})
    registry[str(args.cmd)] = f"rumilance:item/shield_{args.cmd}"
    data[REGISTRY_KEY] = registry

    overrides = [o for o in data.get("overrides", []) if "blocking" in o.get("predicate", {})]
    for cmd_key in sorted(registry, key=int):
        overrides.append({
            "predicate": {"custom_model_data": int(cmd_key)},
            "model": registry[cmd_key],
        })
    data["overrides"] = overrides
    shield_json.write_text(json.dumps(data, indent=2) + "\n")

    print(f"OK  shield cmd={args.cmd}")
    print(f"    texture: {dest_png}")
    print(f"    models:  {models / f'shield_{args.cmd}.json'} (+blocking)")
    print(f"    item:    {shield_json}")
    print("Now rebuild the pack zip and re-upload it.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
