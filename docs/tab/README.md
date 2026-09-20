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
