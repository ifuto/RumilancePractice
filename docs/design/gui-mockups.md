# GUI mockups — 2026-10 (inventory-editor saves, v2 format)

The user designed the next-generation GUI chrome in an inventory editor. Frame language:
a per-screen accent-coloured pane ring, copper chains as side accents, light-gray/white
interior panes named 装飾, black panes for 存在しないマス (cells this screen does not use),
gray panes for 空き枠 (empty but usable seats). Shared helpers: `GuiMockups`.

## Mapping to plugin screens

| Mockup (save name)          | Plugin GUI            | Status |
|-----------------------------|-----------------------|--------|
| Duel Request GUI            | DuelRequestGui        | IMPLEMENTED (v1.92.8) |
| Party MAIN GUI              | TeamHubGui            | IMPLEMENTED (v1.92.9) |
| Party Config GUI            | TeamSettingsGui       | IMPLEMENTED (v1.92.9) |
| Danger Settings GUI         | TeamSettingsGui (danger mode) | IMPLEMENTED (v1.92.9) |
| Party setfunc-item-main GUI | TeamsBrowserGui       | IMPLEMENTED (v1.92.9) |
| Battle Mode GUI             | party battle select   | pending (needs a mode-select screen) |
| Party Start Battle GUI      | TeamKitSelectGui flow | pending |

Deviations (function set is larger than the mockups):
- Party MAIN: the owner's settings comparator and tournament tiles sit at (5,5)/(5,6);
  paging is global (11 RED / 12 BLUE / 4 unassigned seats per page, +N name tag at (5,3)
  = the mockup's "No more players" barrier); the unassigned strip overlays the light-gray
  separator column (col 4). The mockup's bottom-inventory "Set to X team" assignment panes
  are NOT implemented — side assignment stays click-to-cycle on member heads.
- Party Config: the mockup's BAN List cell carries Clear Sides (the plugin has no
  party-ban feature yet); Select-a-Map moved out (it lives in the battle-start flow);
  Player List opens the party hub.
- Browser: the writable book creates a PRIVATE party (the hero action); public/team
  create remain at (5,5)/(5,6).

## Duel Request layout (6 rows, 54 slots) — implemented

```
row0  black 存在しないマス ×9
row1  orange ring, opponent head centre (slot 13, keeps ping/record/KD lore)
row2  chain · 装飾 · BARREL "キット · <kit>" (20) · 装飾 · 白装飾 · 装飾 · MAP "マップ · <map>" (24) · 装飾 · chain
row3  chain · 装飾 · [combat-mode (28) when cross-platform] · 装飾 · DIAMOND_SWORD send (31) · 装飾 · 装飾 · 装飾 · chain
row4  chain · 装飾 · GOLDEN_APPLE "FT : <n>" (38) · 装飾 · 白装飾 · 装飾 · SLIME_BLOCK "KB: <kb>" (42) · 装飾 · chain
row5  orange ×9 (no cancel tile — Esc / /rpcancel dismiss)
```

Notes: send turns YELLOW_GLAZED_TERRACOTTA while pending. FT keeps glint(>0) and
click semantics; KB keeps glint(non-default) and the list picker. Actions are PDC-keyed,
so only cells moved — handlers untouched.

## Party MAIN GUI (target: TeamHubGui)

Top: red wool "Player : 13 Members" / owner head / blue wool "Player : 31 Members" /
ender pearl "Random Split". Side columns invite buttons (lime pane "Invite Player to Red"),
member heads grid with gray 空き枠 fillers, red/blue pane side decorations, spyglass
"See other team", page signs, Back/Next arrows. Bottom-inventory (main) carries the
"Set to X team" assignment panes (setfunc items).

## Party Config GUI (target: TeamConfigGui)

TNT "Friendly Fire : ON" · comparator "Team Settings" · lime dye "Public Party" ·
redstone block "Danger Settings" · name tag "Party ID : XXXX" · player head "Player List" ·
arrow "Back"; light-gray/white 装飾 frame with a red inner band around the danger tile.

## Danger Settings GUI (target: TeamSettingsGui)

Red ring; blaze rod "Transfer OWNER" · barrier "Disband Party" · oak sign "BAN List".

## Party browser (target: TeamsBrowserGui)

Yellow/white 装飾 frame; party heads ("<owner>'s Team") in the grid, gray panes
"No party available" when empty; clock "Update Data", page arrows, writable book
"Create a Party".
