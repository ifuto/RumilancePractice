# NARENA internal TAB customization

NARENA does not depend on or delegate to an external TAB plugin. The player-list is built by
`ScoreboardService`, `TabVisibilityService` and the `TabFight*` services already inside this
plugin. That implementation provides:

- configurable header/footer and context-specific placeholders in `scoreboard.yml`;
- rank prefixes and deterministic rank sorting in `tab-layout.csv`;
- fight/team columns, spectator grouping and list-order packets without a third-party TAB API;
- pack-aware rank badges, with text fallbacks when a viewer has not accepted the resource pack.

`src/main/resources/tab-layout.csv` is copied to `plugins/n-arena/tab-layout.csv` on first start.
It is intentionally spreadsheet-friendly:

```text
context,priority,group,prefix,visible
all,400,admin,"§c§lADMIN §f",true
all,0,default,"§7",true
```

Higher `priority` groups appear first. The file can be edited without rebuilding the plugin; a
restart reloads it. Header/footer text and the full placeholder list remain in
`src/main/resources/scoreboard.yml`, because those values differ by lobby, queue, match, FFA and
spectator context.

## Smooth terrain test map

`/testarena spawn` opens a GUI with the two temporary 100 by 100 map definitions:

- Grass / Stone: layer 1 grass block, layers 2–3 dirt, layers 4–50 stone.
- Sand / Sandstone: layers 1–4 sand, layers 5–50 sandstone.

Both use smooth-step interpolation over a coarse height grid, with a maximum five-block height
range and no adjacent column jump larger than one block. `/testarena delete` removes the previous
map. Noise planning runs asynchronously; block changes are applied in small main-thread batches,
because Bukkit forbids unsafe async world writes.
