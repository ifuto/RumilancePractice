package com.rumilance.practice.combat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Operator-adjustable knockback coefficients (horizontal ×, vertical ×), organised as
 * KBM-style profiles: a global factor, optional per-cause overrides
 * (ATTACK / SWEEP_ATTACK / EXPLOSION / …), and optional per-kit profiles (boxing duels may
 * run a weaker kb than nodebuff without touching other kits).
 *
 * <p>Deliberately a *scaling factor on top of Paper's final knockback vector* — the vanilla
 * calculation is untouched, so every reduction the game itself knows about (netherite's
 * {@code KNOCKBACK_RESISTANCE} attribute, explosion knockback resistance, sprint/enchant
 * strength rules, the grounded 0.4 hop) is computed by Paper and <em>then</em> multiplied.
 * Neutral by default: with no profile set, behaviour is byte-identical to vanilla and the
 * listener short-circuits.</p>
 *
 * <p>Precedence: kit profile &gt; cause override &gt; global. Runtime edits ({@code /kbf …})
 * persist to {@code plugins/n-arena/knockback.json} and win over {@code config.yml} defaults
 * on the next boot.</p>
 */
public final class KnockbackTuning {

    /** Valid band for either multiplier — keeps typos from launching people to the moon. */
    public static final double MIN_FACTOR = 0.0d;
    public static final double MAX_FACTOR = 4.0d;
    public static final double NEUTRAL = 1.0d;

    /** One coefficient pair. */
    public record Factor(double horizontal, double vertical) {

        static final Factor NEUTRAL_FACTOR = new Factor(NEUTRAL, NEUTRAL);

        boolean isNeutral() {
            return horizontal == NEUTRAL && vertical == NEUTRAL;
        }
    }

    private final Path file;
    private final double configHorizontal;
    private final double configVertical;
    private final Map<String, double[]> configCauses;   // from config.yml knockback.causes.*
    private final Map<String, double[]> configKits;     // from config.yml knockback.kits.*
    /** Runtime overrides: key of causes is the cause enum name, kits are lowercased kit ids. */
    private volatile Factor globalOverride;
    private final Map<String, Factor> causeOverrides = new LinkedHashMap<>();
    private final Map<String, Factor> kitOverrides = new LinkedHashMap<>();

    /**
     * @param overrideFile   persistence location for runtime overrides (may not exist)
     * @param configHorizontal / configVertical   global defaults from config.yml
     * @param configCauses   per-cause config defaults (key = cause enum name, {h, v}), never null
     * @param configKits     per-kit config defaults (key = kit id, {h, v}), never null
     */
    public KnockbackTuning(Path overrideFile, double configHorizontal, double configVertical,
                           Map<String, double[]> configCauses, Map<String, double[]> configKits) {
        this.file = overrideFile;
        this.configHorizontal = clamp(configHorizontal, NEUTRAL);
        this.configVertical = clamp(configVertical, NEUTRAL);
        this.configCauses = new LinkedHashMap<>(configCauses == null ? Map.of() : configCauses);
        this.configKits = new LinkedHashMap<>(configKits == null ? Map.of() : configKits);
        load();
    }

    /** Effective factors for an actual knockback event (kit &gt; cause &gt; global, override &gt; config). */
    public Factor effective(String causeName, String kitName) {
        if (kitName != null && !kitName.isEmpty()) {
            String key = kitName.toLowerCase(Locale.ROOT);
            Factor override = kitOverrides.get(key);
            if (override != null) {
                return override;
            }
            // config kits: case-insensitive lookup (config keys come as typed by the operator)
            for (Map.Entry<String, double[]> e : configKits.entrySet()) {
                if (e.getKey().equalsIgnoreCase(kitName)) {
                    return new Factor(e.getValue()[0], e.getValue()[1]);
                }
            }
        }
        if (causeName != null) {
            Factor override = causeOverrides.get(causeName);
            if (override != null) {
                return override;
            }
            double[] fromConfig = configCauses.get(causeName);
            if (fromConfig != null) {
                return new Factor(fromConfig[0], fromConfig[1]);
            }
        }
        Double goH = globalOverride != null ? globalOverride.horizontal() : null;
        Double goV = globalOverride != null ? globalOverride.vertical() : null;
        return new Factor(goH != null ? goH : configHorizontal, goV != null ? goV : configVertical);
    }

    /** Global factor only (status display / callers with no context). */
    public Factor global() {
        return effective(null, "");
    }

    /** True when the effective factor for this context is exactly 1.0/1.0. */
    public boolean isNeutralFor(String causeName, String kitName) {
        return effective(causeName, kitName).isNeutral();
    }

    /** Legacy global fast path for non-contextual callers. */
    public boolean isNeutral() {
        return global().isNeutral() && causeOverrides.isEmpty() && kitOverrides.isEmpty()
                && allNeutral(configCauses) && allNeutral(configKits);
    }

    private static boolean allNeutral(Map<String, double[]> map) {
        for (double[] pair : map.values()) {
            if (pair[0] != NEUTRAL || pair[1] != NEUTRAL) {
                return false;
            }
        }
        return true;
    }

    public double horizontal() {
        return global().horizontal();
    }

    public double vertical() {
        return global().vertical();
    }

    /** Scales a final knockback vector's components; pure math, Bukkit-free for testability. */
    public double[] scale(String causeName, String kitName, double x, double y, double z) {
        Factor f = effective(causeName, kitName);
        return new double[]{x * f.horizontal(), y * f.vertical(), z * f.horizontal()};
    }

    // ------------------------------------------------------------------ runtime edits (/kbf)

    public void setGlobal(double horizontal, double vertical) {
        this.globalOverride = new Factor(clamp(horizontal, horizontal()), clamp(vertical, vertical()));
        save();
    }

    public void setCause(String causeName, double horizontal, double vertical) {
        causeOverrides.put(causeName.toUpperCase(Locale.ROOT),
                new Factor(clamp(horizontal, NEUTRAL), clamp(vertical, NEUTRAL)));
        save();
    }

    public void setKit(String kitName, double horizontal, double vertical) {
        kitOverrides.put(kitName.toLowerCase(Locale.ROOT),
                new Factor(clamp(horizontal, NEUTRAL), clamp(vertical, NEUTRAL)));
        save();
    }

    /** Clears a single scope's runtime override; {@code scope} in {@code global|cause|kit}. */
    public boolean clear(String scope, String name) {
        boolean removed = switch (scope.toLowerCase(Locale.ROOT)) {
            case "global" -> {
                boolean had = globalOverride != null;
                globalOverride = null;
                yield had;
            }
            case "cause" -> causeOverrides.remove(name.toUpperCase(Locale.ROOT)) != null;
            case "kit" -> kitOverrides.remove(name.toLowerCase(Locale.ROOT)) != null;
            default -> false;
        };
        if (removed) {
            save();
        }
        return removed;
    }

    /** Clears every runtime override → falls back to config.yml defaults. */
    public void resetOverrides() {
        globalOverride = null;
        causeOverrides.clear();
        kitOverrides.clear();
        save();
    }

    /** Read-only views for the status command. */
    public Map<String, Factor> causeOverridesView() {
        return Map.copyOf(causeOverrides);
    }

    public Map<String, Factor> kitOverridesView() {
        return Map.copyOf(kitOverrides);
    }

    /** Parses an operator-supplied factor; on failure {@code IllegalArgumentException} carries a JP hint. */
    public static double parseFactor(String text) {
        if (text == null) {
            throw new IllegalArgumentException("数値を指定してください");
        }
        final double parsed;
        try {
            parsed = Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("数値として解釈できません: " + text);
        }
        if (!Double.isFinite(parsed) || parsed < MIN_FACTOR || parsed > MAX_FACTOR) {
            throw new IllegalArgumentException(
                    "係数は " + MIN_FACTOR + " 〜 " + MAX_FACTOR + " の範囲で指定してください");
        }
        return parsed;
    }

    private static double clamp(double raw, double fallback) {
        if (!Double.isFinite(raw) || raw < MIN_FACTOR || raw > MAX_FACTOR) {
            return fallback;
        }
        return raw;
    }

    // ------------------------------------------------------------------ persistence

    private void load() {
        if (file == null || !Files.isRegularFile(file)) {
            return;
        }
        try {
            Map<String, Object> json = JsonMini.parseObject(Files.readString(file, StandardCharsets.UTF_8));
            // Legacy v1 shape {"horizontal":h,"vertical":v} still honoured.
            if (json.containsKey("horizontal") || json.containsKey("vertical")) {
                Double h = num(json.get("horizontal"));
                Double v = num(json.get("vertical"));
                this.globalOverride = (h != null || v != null)
                        ? new Factor(clamp(h != null ? h : NEUTRAL, NEUTRAL),
                                clamp(v != null ? v : NEUTRAL, NEUTRAL)) : null;
            }
            Object global = json.get("global");
            if (global instanceof Map<?, ?> g) {
                this.globalOverride = factorOf(g);
            }
            fillOverrides(json.get("causes"), causeOverrides);
            fillOverrides(json.get("kits"), kitOverrides);
        } catch (IOException ignored) {
            // unreadable override file → run on config defaults this boot
        }
    }

    private static void fillOverrides(Object node, Map<String, Factor> out) {
        if (!(node instanceof Map<?, ?> map)) {
            return;
        }
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (e.getValue() instanceof Map<?, ?> f) {
                Factor factor = factorOf(f);
                if (factor != null) {
                    out.put(String.valueOf(e.getKey()), factor);
                }
            }
        }
    }

    private static Factor factorOf(Map<?, ?> map) {
        Double h = num(map.get("horizontal"));
        Double v = num(map.get("vertical"));
        if (h == null && v == null) {
            return null;
        }
        return new Factor(h != null ? clamp(h, NEUTRAL) : NEUTRAL,
                v != null ? clamp(v, NEUTRAL) : NEUTRAL);
    }

    private static Double num(Object o) {
        return o instanceof Number n ? n.doubleValue() : null;
    }

    private void save() {
        if (file == null) {
            return;
        }
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            if (globalOverride != null) {
                first = append(sb, first, "\"global\":", factorJson(globalOverride));
            }
            first = append(sb, first, "\"causes\":", overridesJson(causeOverrides));
            append(sb, first, "\"kits\":", overridesJson(kitOverrides));
            sb.append("}\n");
            Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // persistence failure must never break combat
        }
    }

    private static boolean append(StringBuilder sb, boolean first, String key, String value) {
        if (!first) {
            sb.append(',');
        }
        sb.append(key).append(value);
        return false;
    }

    private static String factorJson(Factor f) {
        return "{\"horizontal\":" + f.horizontal() + ",\"vertical\":" + f.vertical() + "}";
    }

    private static String overridesJson(Map<String, Factor> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Factor> e : map.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append(JsonMini.quote(e.getKey())).append(':').append(factorJson(e.getValue()));
        }
        return sb.append('}').toString();
    }

    /**
     * Minimal JSON (object/string/number/bool/null; arrays tolerated but unused) — enough to
     * round-trip our override file without pulling in a dependency or touching Bukkit yaml.
     */
    static final class JsonMini {

        private JsonMini() {
        }

        static String quote(String s) {
            StringBuilder sb = new StringBuilder("\"");
            for (char c : s.toCharArray()) {
                if (c == '"' || c == '\\') {
                    sb.append('\\');
                }
                sb.append(c);
            }
            return sb.append('"').toString();
        }

        static Map<String, Object> parseObject(String json) {
            int[] pos = {skipWs(json, 0)};
            Object value = parseValue(json, pos);
            return value instanceof Map ? cast(value) : Map.of();
        }

        @SuppressWarnings("unchecked")
        private static Map<String, Object> cast(Object v) {
            return (Map<String, Object>) v;
        }

        private static int skipWs(String s, int i) {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
            return i;
        }

        private static Object parseValue(String s, int[] pos) {
            pos[0] = skipWs(s, pos[0]);
            if (pos[0] >= s.length()) {
                return null;
            }
            char c = s.charAt(pos[0]);
            return switch (c) {
                case '{' -> parseObjectInner(s, pos);
                case '[' -> parseArrayInner(s, pos);
                case '"' -> parseStringValue(s, pos);
                case 't' -> { pos[0] += 4; yield Boolean.TRUE; }
                case 'f' -> { pos[0] += 5; yield Boolean.FALSE; }
                case 'n' -> { pos[0] += 4; yield null; }
                default -> parseNumber(s, pos);
            };
        }

        private static Map<String, Object> parseObjectInner(String s, int[] pos) {
            Map<String, Object> out = new LinkedHashMap<>();
            pos[0] = skipWs(s, pos[0] + 1);
            if (pos[0] < s.length() && s.charAt(pos[0]) == '}') {
                pos[0]++;
                return out;
            }
            while (pos[0] < s.length()) {
                String key = parseString(s, pos);
                pos[0] = skipWs(s, pos[0]);
                if (pos[0] < s.length() && s.charAt(pos[0]) == ':') {
                    pos[0]++;
                }
                out.put(key, parseValue(s, pos));
                pos[0] = skipWs(s, pos[0]);
                if (pos[0] < s.length() && s.charAt(pos[0]) == ',') {
                    pos[0] = skipWs(s, pos[0] + 1);
                    continue;
                }
                if (pos[0] < s.length() && s.charAt(pos[0]) == '}') {
                    pos[0]++;
                }
                break;
            }
            return out;
        }

        private static List<Object> parseArrayInner(String s, int[] pos) {
            List<Object> out = new ArrayList<>();
            pos[0] = skipWs(s, pos[0] + 1);
            while (pos[0] < s.length() && s.charAt(pos[0]) != ']') {
                out.add(parseValue(s, pos));
                pos[0] = skipWs(s, pos[0]);
                if (pos[0] < s.length() && s.charAt(pos[0]) == ',') {
                    pos[0] = skipWs(s, pos[0] + 1);
                } else {
                    break;
                }
            }
            pos[0] = Math.min(s.length(), pos[0] + 1);
            return out;
        }

        private static String parseStringValue(String s, int[] pos) {
            return parseString(s, pos);
        }

        private static String parseString(String s, int[] pos) {
            pos[0] = skipWs(s, pos[0]);
            StringBuilder sb = new StringBuilder();
            if (pos[0] < s.length() && s.charAt(pos[0]) == '"') {
                pos[0]++;
            }
            while (pos[0] < s.length()) {
                char c = s.charAt(pos[0]++);
                if (c == '"') {
                    break;
                }
                if (c == '\\' && pos[0] < s.length()) {
                    c = s.charAt(pos[0]++);
                }
                sb.append(c);
            }
            return sb.toString();
        }

        private static Object parseNumber(String s, int[] pos) {
            int start = pos[0];
            while (pos[0] < s.length()) {
                char c = s.charAt(pos[0]);
                boolean digit = (c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.' || c == 'E' || c == 'e';
                if (!digit) {
                    break;
                }
                pos[0]++;
            }
            if (start == pos[0]) {
                pos[0]++;
                return null;
            }
            try {
                return Double.parseDouble(s.substring(start, pos[0]));
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }
}
