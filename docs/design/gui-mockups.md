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
| Battle Mode GUI             | PartyBattleModeGui (new)      | IMPLEMENTED (v1.92.10) |
| Party Start Battle GUI      | PartyStartBattleGui (new)     | IMPLEMENTED (v1.92.10) |

Mode semantics (user-confirmed): **Party Fight** = the classic party battle
(existing flow, unchanged); **Party FFA** = today's Private FFA
(PartyFfaService) — START with the FFA mode selected sends every online,
lobby-idle party member into a fresh party-ffa zone; /partyffa (or
/partyffa leave) exits, and a standalone zone is released when the last
participant leaves. The hub START button now opens the launchpad; its
Select-a-Kit keeps the legacy behaviour (kit pick starts the fight).

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

## Admin suite refresh (target: AdminMenuGui + 5 new screens) — implemented (v1.92.23)

The /practiceadmin surface became the GUI face of every subcommand. These screens were
laid out in-repo (not user-mockup'd) and keep the classic `paintFrame` PURPLE chrome
(perimeter pane ring + title icon), matching the pre-existing AdminMenuGui /
AdminPlayerDataGui style. Interior = rows 1–4, cols 1–7 (28 tiles); back/close at (5,4);
page arrows via MenuScaffold.

### AdminMenuGui (6 rows)

- row1 content: (1,1) Kits → KitAdminGui · (1,2) Presets · (1,3) Original kits → EkitAdmin ·
  (1,5) Sign item · (1,6) Player data editor (chat lookup)
- row2 live: (2,1) Live matches · (2,3) FFA settings · (2,5) Arena source · (2,7) Queues & maps
- row3 players/world: (3,1) Players · (3,2) Broadcast (chat input) · (3,3) Default KB profile ·
  (3,4) Alt flags · (3,5) Pack policy · (3,6) Time/weather cycle · (3,7) Floating items
  (click=queue / right=sword-ffa / shift=removeall)
- row4 switches: (4,1) Maintenance toggle · (4,2) Ranked queue (click=on/off,
  shift=auto-unlock) · (4,3) TNT reset ALL ranked stats (shift) · (4,4) Cleanup (shift) ·
  (4,5) Reload (shift) · (4,6) FFA command gate · (4,7) Status (live lore)

One-shot tiles run through a command bridge (`PracticeAdminCommand.dispatch`) so GUI and
typed command can never drift.

### New screens (all 6 rows, PURPLE, paged grids)

| Screen | Purpose | Actions |
|--------|---------|---------|
| AdminPlayersGui | online players as heads + spyglass chat lookup | click=data editor, right=kick, shift=force-end |
| AdminMatchesGui | live matches with mode/state/kit/participants | click=force-end (draw) |
| AdminToggleGui  | kit queues + arena maps, one dye tile each | click=enable/disable (`toggle` bridge) |
| KbDefaultGui    | kb/*.json profiles + OFF tile, current glints | click=set default (`kbdefault` bridge) |
| AltFlagsGui     | open alt-detection flags (admin-private)     | click=dismiss + lift pair restriction |
| AdminStatsGui   | **W/L editor (WL = wins & losses)** per ranked-kit row | click=type exact "12 8", right=+1 win, shift=+1 loss, TNT=reset player (PT untouched) |

### AdminPlayerDataGui v2 (player-linked data, all editable)

row1: rank (click promote / shift demote, live via RankService) · settings (click locale
reset / shift sounds / right scoreboard) · chat whitelist (click=chat-input add or
`clear`, shift=clear now) · ekits (click=this player / shift=everyone) · original kits
(shift=delete this player's slots) · name color (click=clear) · ranked stats (click=W/L
editor / shift=reset everyone). row2: punishments (click=lift all + cache evict) · kick ·
force-end · TNT FULL WIPE (shift = rank→NORM + stats + ekits + originals + WL + locale +
color + punishments). Chat whitelist here is the per-player chat filter
(`PlayerSettings.chatWhitelist()`), the same set edited from SettingsGui.

## Kit variants (K1..K4) — saves v2 mockups (2026-10-04)

> 注: ユーザー貼付の saves v2 JSON (10 GUI分) はchat compactで消失したため、この節は
> その要約 (Cell配置・アイテム・ヘッダ) から復元した仕様記録。JSONそのものは未保存。

### 機能要件

- キットごとに K1..K4 のレイアウトを設定できる (LT Vanilla の番号キットと同じ感覚。GUIは別物)。
- K選択は 本 (WRITABLE_BOOK) = Active / 紙 (PAPER) = Not Active。キットごとに1スロットがActive。
- FFAに /k 等のコマンドは作らない — **Active Only** (試合/FFA開始時、active Kのレイアウトのみ適用)。
- Crystal FFA の KIT1..9 (/#v<n>・/k) は既存のまま別系統 (#k と #v のキー空間は分離)。

### KIT EDIT GUI (EditKitGui 6-row 再設計)

row0: 防具4 (helmet/chest/legs/boots, cols0-3) + gray装飾 (col4) + 盾OFF HAND (col5) +
gray装飾 (col6) + lime_wool SAVE (col7) + yellow_wool RESET (col8)。
row1: 緑pane装飾のみ — **Backボタンは不要 (2026-10-04確定)**。Save で保存して
KIT SELECT GUI の元ページへ戻る、Esc で閉じる。BACK が残るのは special flow のみ
(crystal KIT1..9 picker / admin共有キット / 閲覧モード)。
rows2-4: キット中身27 (player inv 9-35)。 row5: HotBar9 (inv 0-8)。

### KIT SELECT GUI (EkitSelectGui カテゴリ画面)

row4 構成 (2026-10-04 確定): col0/4/7 = lime装飾、col1 = **代入セル**、col2/3/5/6 = K1〜K4 chips。
- 編集したいキットをグリッドでクリック → **ui.button.click** と同時にキットのアイテムが代入セルに代入。
- **未代入** (何も選んでいない) は代入セルと 本/紙 の部分が**ガラス** (gray stained glass)。
- **代入後** は Active な K が本 (WRITABLE_BOOK, glint)、Active でない K が紙 (PAPER)。
- chip **右クリック** = ui.button.click と同時にその K 番号の KIT EDIT GUI を編集開始。
- chip **左クリック** = ui.button.click と同時にそのスロットを Active にする。
- chip **Shift+クリック** = そのスロットの中身リセット (保存行削除→公式レイアウトに戻る)。
main36ベース: slot19-25 (row2) にキットグリッド、row5にBack barrier。実装: グリッド rows1-3
(21枠/頁)。キット選択で lastKitTracker も更新 (前回の KIT と共有)。
※ /setfunc の機能アイテム右クリック オープン時も ui.button.click を鳴らす (menu compass 含む)。

### MAIN KIT SELECTER (EkitSelectGui 選択画面)

container54: 緑pane + weathered copper chain装飾枠、MAIN KITS (wild trim) (2,2) /
SUB KITS (bolt trim) (2,6) / **前回の KIT** (3,4)。中央の sword ボタンは前回選択した
キットへのショートカット (2026-10-04確定、ffaEnabled一覧ではない) — LastSelectedKitTracker
(queue/duel/kit picker 共有) を使用、クリックで active K を開く。キット未選択なら
gray_dye の非活性タイル。main36 = null (2択画面に下段不要)。

### 既存7GUI (saves v2 正式デザイン・実装指示は明言なし)

Duel Request (Opponent head + Select a Kit barrel + Select a Map map + Send Request sword +
FT:∞ gapple + Select a KB slime + 黒「存在しないマス」枠) / Battle Mode (Party Fight sword /
Party FFA end_crystal) / Party Start Battle (Select a Kit / Battle Mode iron axe / Map /
Start Battle sword) / Danger Settings (Transfer OWNER blaze_rod / Disband Party barrier /
BAN List oak_sign) / Party Config (Friendly Fire TNT / Team Settings comparator / Public
Party lime_dye / Danger Settings redstone_block / Party ID name_tag / Player List head) /
Party MAIN (red/blue wool人数 + owner head + Random Split pearl + Invite + member head
グリッド + Page/spyglass See other team) / Party setfunc-item-main (partyリスト: head
PlayerK's Team 等 + No party available + Update Data clock + Back/Next + Create a Party)。
