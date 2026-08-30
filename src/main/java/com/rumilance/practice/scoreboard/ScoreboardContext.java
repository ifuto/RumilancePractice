package com.rumilance.practice.scoreboard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Per-tick placeholder bag + expandable {@code @directive} lists for a sidebar layout.
 */
public final class ScoreboardContext {

    private final Map<String, String> vars = new LinkedHashMap<>();
    private final Map<String, Function<ScoreboardConfig, List<String>>> directives = new LinkedHashMap<>();

    public ScoreboardContext put(String key, String value) {
        vars.put(key.toLowerCase(Locale.ROOT), value == null ? "" : value);
        return this;
    }

    public ScoreboardContext put(String key, int value) {
        return put(key, Integer.toString(value));
    }

    public ScoreboardContext put(String key, long value) {
        return put(key, Long.toString(value));
    }

    public ScoreboardContext putAll(Map<String, String> extra) {
        if (extra != null) {
            for (Map.Entry<String, String> e : extra.entrySet()) {
                put(e.getKey(), e.getValue());
            }
        }
        return this;
    }

    public ScoreboardContext directive(String name, Function<ScoreboardConfig, List<String>> expander) {
        directives.put(name.toLowerCase(Locale.ROOT), Objects.requireNonNull(expander, "expander"));
        return this;
    }

    public Map<String, String> vars() {
        return Collections.unmodifiableMap(vars);
    }

    public List<String> expandDirective(String name, ScoreboardConfig config) {
        Function<ScoreboardConfig, List<String>> fn = directives.get(name.toLowerCase(Locale.ROOT));
        if (fn == null) {
            return List.of();
        }
        List<String> lines = fn.apply(config);
        return lines == null ? List.of() : lines;
    }

    /** Renders a ranked streak list into already-legacy lines. */
    public static List<String> renderStreakList(
            ScoreboardConfig.ListStyle style,
            List<? extends StreakEntry> entries,
            Map<String, String> baseVars
    ) {
        if (entries == null || entries.isEmpty()) {
            String empty = ScoreboardText.render(style.empty(), baseVars);
            return empty.isBlank() ? List.of() : List.of(empty);
        }
        List<String> out = new ArrayList<>();
        int limit = Math.min(style.max(), entries.size());
        for (int i = 0; i < limit; i++) {
            StreakEntry e = entries.get(i);
            Map<String, String> row = new LinkedHashMap<>(baseVars);
            row.put("rank", Integer.toString(i + 1));
            row.put("name", e.name());
            row.put("streak", Integer.toString(e.streak()));
            out.add(ScoreboardText.render(style.entry(), row));
        }
        return out;
    }

    public static List<String> renderSpecList(
            ScoreboardConfig.ListStyle style,
            List<? extends SpecEntry> entries,
            Map<String, String> baseVars
    ) {
        if (entries == null || entries.isEmpty()) {
            String empty = ScoreboardText.render(style.empty(), baseVars);
            return empty.isBlank() ? List.of() : List.of(empty);
        }
        List<String> out = new ArrayList<>();
        int shown = Math.min(style.max(), entries.size());
        for (int i = 0; i < shown; i++) {
            SpecEntry e = entries.get(i);
            Map<String, String> row = new LinkedHashMap<>(baseVars);
            row.put("rank", Integer.toString(i + 1));
            row.put("name", e.name());
            row.put("hearts", e.hearts());
            row.put("totems", Integer.toString(e.totems()));
            out.add(ScoreboardText.render(style.entry(), row));
        }
        int extra = entries.size() - shown;
        if (extra > 0 && style.more() != null && !style.more().isBlank()) {
            Map<String, String> row = new LinkedHashMap<>(baseVars);
            row.put("extra", Integer.toString(extra));
            out.add(ScoreboardText.render(style.more(), row));
        }
        return out;
    }

    public record StreakEntry(String name, int streak) {
    }

    public record SpecEntry(String name, String hearts, int totems) {
    }
}
