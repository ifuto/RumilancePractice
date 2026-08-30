package com.rumilance.practice.scoreboard;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable snapshot of {@code scoreboard.yml}. Reload by constructing a new instance.
 */
public final class ScoreboardConfig {

    public record ListStyle(int max, String empty, String entry, String more) {
        static ListStyle of(ConfigurationSection section, int defaultMax, String defaultEmpty, String defaultEntry) {
            if (section == null) {
                return new ListStyle(defaultMax, defaultEmpty, defaultEntry, "");
            }
            return new ListStyle(
                    Math.max(0, section.getInt("max", defaultMax)),
                    Objects.toString(section.getString("empty", defaultEmpty), defaultEmpty),
                    Objects.toString(section.getString("entry", defaultEntry), defaultEntry),
                    Objects.toString(section.getString("more", ""), "")
            );
        }
    }

    public record Layout(
            String title,
            boolean showHeaderRule,
            boolean showFooterRule,
            boolean showFooterLine,
            List<String> lines
    ) {
        static Layout of(ConfigurationSection section, String defaultTitle) {
            if (section == null) {
                return new Layout(defaultTitle, true, true, true, List.of());
            }
            List<String> lines = section.getStringList("lines");
            if (lines == null) {
                lines = List.of();
            }
            return new Layout(
                    Objects.toString(section.getString("title", defaultTitle), defaultTitle),
                    section.getBoolean("show-header-rule", true),
                    section.getBoolean("show-footer-rule", true),
                    section.getBoolean("show-footer-line", true),
                    List.copyOf(lines)
            );
        }
    }

    public record TabLayout(
            List<String> header,
            List<String> footer,
            List<String> omitHeaderIfBlank,
            String footerWhenNoSpectators
    ) {
        static TabLayout of(ConfigurationSection section) {
            if (section == null) {
                return new TabLayout(List.of(), List.of(), List.of(), "");
            }
            return new TabLayout(
                    List.copyOf(section.getStringList("header")),
                    List.copyOf(section.getStringList("footer")),
                    List.copyOf(section.getStringList("omit-header-line-if-blank")),
                    Objects.toString(section.getString("footer-when-no-spectators", ""), "")
            );
        }
    }

    private final boolean enabled;
    private final int updateIntervalTicks;
    private final int maxLines;
    private final long statsCacheMs;
    private final boolean tabHeaderFooter;
    private final String serverName;
    private final String serverIp;
    private final String headerRule;
    private final String footerRule;
    private final String footerLine;
    private final Map<String, String> modeLabels;
    private final String timeUnderMinute;
    private final String timeWithMinutes;
    private final String colorRed;
    private final String colorBlue;
    private final String spectatorsJoin;
    private final Map<String, ListStyle> lists;
    private final Map<String, Layout> layouts;
    private final TabLayout tabLobby;
    private final TabLayout tabMatch;

    public ScoreboardConfig(FileConfiguration yaml) {
        Objects.requireNonNull(yaml, "yaml");
        this.enabled = yaml.getBoolean("enabled", true);
        this.updateIntervalTicks = Math.max(5, Math.min(40, yaml.getInt("update-interval-ticks", 20)));
        this.maxLines = Math.max(1, Math.min(15, yaml.getInt("max-lines", 13)));
        this.statsCacheMs = Math.max(500L, yaml.getLong("stats-cache-ms", 3000L));
        this.tabHeaderFooter = yaml.getBoolean("tab-header-footer", true);

        ConfigurationSection brand = yaml.getConfigurationSection("branding");
        this.serverName = brand == null ? "N Arena"
                : Objects.toString(brand.getString("server-name", "N Arena"), "N Arena");
        this.serverIp = brand == null ? "play.example.com"
                : Objects.toString(brand.getString("server-ip", "play.example.com"), "play.example.com");
        this.headerRule = brand == null ? "<dark_aqua><strikethrough>────────────"
                : Objects.toString(brand.getString("header-rule", "<dark_aqua><strikethrough>────────────"), "");
        this.footerRule = brand == null ? "<dark_aqua><strikethrough>────────────"
                : Objects.toString(brand.getString("footer-rule", "<dark_aqua><strikethrough>────────────"), "");
        this.footerLine = brand == null ? "<aqua>{server_ip}"
                : Objects.toString(brand.getString("footer-line", "<aqua>{server_ip}"), "");

        Map<String, String> modes = new LinkedHashMap<>();
        ConfigurationSection modeSec = yaml.getConfigurationSection("mode-labels");
        if (modeSec != null) {
            for (String key : modeSec.getKeys(false)) {
                modes.put(key.toLowerCase(Locale.ROOT), Objects.toString(modeSec.getString(key), key));
            }
        }
        modes.putIfAbsent("ranked", "ランク");
        modes.putIfAbsent("unranked", "アンランク");
        modes.putIfAbsent("ffa", "FFA");
        modes.putIfAbsent("team", "チーム");
        this.modeLabels = Map.copyOf(modes);

        ConfigurationSection time = yaml.getConfigurationSection("time");
        this.timeUnderMinute = time == null ? "{s}s"
                : Objects.toString(time.getString("under-minute", "{s}s"), "{s}s");
        this.timeWithMinutes = time == null ? "{m}m{s}s"
                : Objects.toString(time.getString("with-minutes", "{m}m{s}s"), "{m}m{s}s");

        ConfigurationSection colors = yaml.getConfigurationSection("colors");
        this.colorRed = colors == null ? "<red>"
                : Objects.toString(colors.getString("red", "<red>"), "<red>");
        this.colorBlue = colors == null ? "<blue>"
                : Objects.toString(colors.getString("blue", "<blue>"), "<blue>");
        this.spectatorsJoin = Objects.toString(
                yaml.getString("spectators-join", "<gray>, <white>"), "<gray>, <white>");

        Map<String, ListStyle> listMap = new LinkedHashMap<>();
        ConfigurationSection listsSec = yaml.getConfigurationSection("lists");
        listMap.put("ffa_streak_top", ListStyle.of(
                listsSec == null ? null : listsSec.getConfigurationSection("ffa_streak_top"),
                5, "<dark_aqua>—", "<aqua>{rank}. <white>{name} <aqua>{streak}"));
        listMap.put("month_streak_top", ListStyle.of(
                listsSec == null ? null : listsSec.getConfigurationSection("month_streak_top"),
                8, "<dark_aqua>—", "<aqua>{rank}. <white>{name} <aqua>{streak}"));
        listMap.put("best_streak_top", ListStyle.of(
                listsSec == null ? null : listsSec.getConfigurationSection("best_streak_top"),
                3, "<dark_aqua>—", "<aqua>{rank}. <white>{name} <aqua>{streak}"));
        ListStyle specRed = ListStyle.of(
                listsSec == null ? null : listsSec.getConfigurationSection("spec_red"),
                2, "", "<red>{name} <white>{hearts} <yellow>T:{totems}");
        if (listsSec != null && listsSec.getConfigurationSection("spec_red") != null) {
            ConfigurationSection s = listsSec.getConfigurationSection("spec_red");
            specRed = new ListStyle(specRed.max(), specRed.empty(), specRed.entry(),
                    Objects.toString(s.getString("more", "<red>+{extra} more"), ""));
        }
        ListStyle specBlue = ListStyle.of(
                listsSec == null ? null : listsSec.getConfigurationSection("spec_blue"),
                2, "", "<blue>{name} <white>{hearts} <yellow>T:{totems}");
        if (listsSec != null && listsSec.getConfigurationSection("spec_blue") != null) {
            ConfigurationSection s = listsSec.getConfigurationSection("spec_blue");
            specBlue = new ListStyle(specBlue.max(), specBlue.empty(), specBlue.entry(),
                    Objects.toString(s.getString("more", "<blue>+{extra} more"), ""));
        }
        listMap.put("spec_red", specRed);
        listMap.put("spec_blue", specBlue);
        this.lists = Map.copyOf(listMap);

        String defaultTitle = "<aqua><bold>" + serverName;
        Map<String, Layout> layoutMap = new LinkedHashMap<>();
        ConfigurationSection layoutsSec = yaml.getConfigurationSection("layouts");
        for (String key : List.of("lobby", "queue", "match", "team_match", "spectate", "ffa", "ffa_spectate")) {
            layoutMap.put(key, Layout.of(
                    layoutsSec == null ? null : layoutsSec.getConfigurationSection(key),
                    defaultTitle));
        }
        this.layouts = Map.copyOf(layoutMap);

        ConfigurationSection tab = yaml.getConfigurationSection("tab");
        this.tabLobby = TabLayout.of(tab == null ? null : tab.getConfigurationSection("lobby"));
        this.tabMatch = TabLayout.of(tab == null ? null : tab.getConfigurationSection("match"));
    }

    public boolean enabled() {
        return enabled;
    }

    public int updateIntervalTicks() {
        return updateIntervalTicks;
    }

    public int maxLines() {
        return maxLines;
    }

    public long statsCacheMs() {
        return statsCacheMs;
    }

    public boolean tabHeaderFooter() {
        return tabHeaderFooter;
    }

    public String serverName() {
        return serverName;
    }

    public String serverIp() {
        return serverIp;
    }

    public String headerRule() {
        return headerRule;
    }

    public String footerRule() {
        return footerRule;
    }

    public String footerLine() {
        return footerLine;
    }

    public String modeLabel(String modeKey) {
        if (modeKey == null) {
            return "";
        }
        return modeLabels.getOrDefault(modeKey.toLowerCase(Locale.ROOT), modeKey);
    }

    public String formatTime(long seconds) {
        long secs = Math.max(0L, seconds);
        if (secs < 60L) {
            return timeUnderMinute.replace("{s}", Long.toString(secs));
        }
        return timeWithMinutes
                .replace("{m}", Long.toString(secs / 60L))
                .replace("{s}", Long.toString(secs % 60L));
    }

    public String colorRed() {
        return colorRed;
    }

    public String colorBlue() {
        return colorBlue;
    }

    public String spectatorsJoin() {
        return spectatorsJoin;
    }

    public ListStyle list(String key) {
        return lists.getOrDefault(key, new ListStyle(5, "—", "{rank}. {name} {streak}", ""));
    }

    public Layout layout(String key) {
        return layouts.getOrDefault(key, layouts.get("lobby"));
    }

    public TabLayout tabLobby() {
        return tabLobby;
    }

    public TabLayout tabMatch() {
        return tabMatch;
    }

    /** Builds the ordered sidebar body (without applying max — caller clips). */
    public List<String> expandLines(Layout layout, ScoreboardContext ctx) {
        List<String> out = new ArrayList<>();
        if (layout.showHeaderRule() && headerRule != null && !headerRule.isBlank()) {
            out.add(ScoreboardText.render(headerRule, ctx.vars()));
        }
        for (String raw : layout.lines()) {
            if (raw == null) {
                continue;
            }
            String trimmed = raw.trim();
            if (trimmed.startsWith("@")) {
                out.addAll(ctx.expandDirective(trimmed.substring(1), this));
                continue;
            }
            if (raw.isEmpty()) {
                out.add(" ");
                continue;
            }
            out.add(ScoreboardText.render(raw, ctx.vars()));
        }
        if (layout.showFooterRule() && footerRule != null && !footerRule.isBlank()) {
            out.add(ScoreboardText.render(footerRule, ctx.vars()));
        }
        if (layout.showFooterLine() && footerLine != null && !footerLine.isBlank()) {
            out.add(ScoreboardText.render(footerLine, ctx.vars()));
        }
        return out;
    }

    public String renderTitle(Layout layout, ScoreboardContext ctx) {
        return ScoreboardText.render(layout.title(), ctx.vars());
    }

    public List<String> renderTabLines(List<String> templates, ScoreboardContext ctx, List<String> omitIfBlank) {
        if (templates == null || templates.isEmpty()) {
            return List.of();
        }
        List<String> omit = omitIfBlank == null ? List.of() : omitIfBlank;
        List<String> out = new ArrayList<>();
        for (String line : templates) {
            if (line == null) {
                continue;
            }
            boolean skip = false;
            for (String token : omit) {
                if (token != null && line.contains(token)) {
                    String key = token.replace("{", "").replace("}", "").trim();
                    String val = ctx.vars().getOrDefault(key, "");
                    if (val == null || val.isBlank()) {
                        skip = true;
                        break;
                    }
                }
            }
            if (skip) {
                continue;
            }
            out.add(ScoreboardText.render(line, ctx.vars()));
        }
        return out;
    }
}
