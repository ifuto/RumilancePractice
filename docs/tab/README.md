# NEZNAMY/TAB integration

NARENA detects `TAB` through the soft dependency and delegates the player-list header/footer,
scoreboard teams, sorting and nametag ownership to TAB. It does not send its own scoreboard teams
while TAB is enabled, so TAB's sorting is not overwritten every scoreboard refresh.

The plugin registers and applies these TAB API placeholders automatically:

- `%rml_rankicon%` — rank badge for the placeholder owner.
- `%rel_rml_rankicon%` — viewer-aware rank badge; resource-pack viewers receive the glyph and
  pack-less viewers receive `OWNER`, `N+`, `N` or `PRO` text.

The rank prefix is also applied through TAB's `TabListFormatManager` at runtime. The external TAB
plugin still needs its normal `tablist-name-formatting.enabled: true` feature enabled; NARENA logs a
warning if that feature is disabled. TAB remains the owner of header/footer and sorting as
recommended by its API documentation.

## Recommended TAB settings

Copy the snippets in this directory into the matching files under `plugins/TAB/`, then run
`/tab reload` (or restart the server):

- `config.yml` — enables header/footer, tablist formatting and stable group/alphabetical sorting.
- `groups.yml` — fallback rank prefix and a safe default for players not covered by permissions.

Do not add NARENA's player names to `users.yml`; the plugin applies the per-viewer rank prefix
through the API. Keep `scoreboard.yml` in the NARENA data folder for the sidebar only.
