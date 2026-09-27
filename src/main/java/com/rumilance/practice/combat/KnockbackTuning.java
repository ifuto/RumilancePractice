package com.rumilance.practice.combat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Operator-adjustable knockback coefficients (horizontal ×, vertical ×).
 *
 * <p>Deliberately a *scaling factor on top of Paper's final knockback vector* — the vanilla
 * calculation is untouched, so every reduction that the game itself knows about (netherite's
 * {@code KNOCKBACK_RESISTANCE} attribute, explosion knockback resistance, sprint/enchant
 * strength rules, the grounded 0.4 hop) is computed by Paper and <em>then</em> multiplied.
 * Neutral by default (1.0 / 1.0) which behaves exactly like vanilla and short-circuits at the
 * event, so enabling the feature costs nothing until an operator sets knob values.</p>
 *
 * <p>Defaults come from {@code config.yml (knockback.horizontal/vertical)}; values changed at
 * runtime via {@code /kbf} are persisted as overrides in {@code plugins/n-arena/knockback.json}
 * and win over the config on the next boot (same pattern as pack-policy.yml).</p>
 */
public final class KnockbackTuning {

    /** Valid band for either multiplier — keeps typos from launching people to the moon. */
    public static final double MIN_FACTOR = 0.0d;
    public static final double MAX_FACTOR = 4.0d;
    public static final double NEUTRAL = 1.0d;

    private final Path file;
    private final double configHorizontal;
    private final double configVertical;
    private volatile Double overrideHorizontal;
    private volatile Double overrideVertical;

    /**
     * @param overrideFile    persistence location for runtime-set overrides (may not exist)
     * @param configHorizontal default from config.yml (used when no override is set)
     * @param configVertical   default from config.yml
     */
    public KnockbackTuning(Path overrideFile, double configHorizontal, double configVertical) {
        this.file = overrideFile;
        this.configHorizontal = clamp(configHorizontal, NEUTRAL);
        this.configVertical = clamp(configVertical, NEUTRAL);
        load();
    }

    /** Effective horizontal (X/Z) multiplier — runtime override wins over config. */
    public double horizontal() {
        Double o = overrideHorizontal;
        return o != null ? o : configHorizontal;
    }

    /** Effective vertical (Y) multiplier — runtime override wins over config. */
    public double vertical() {
        Double o = overrideVertical;
        return o != null ? o : configVertical;
    }

    /** True when both factors are exactly 1.0 → callers take the vanilla fast path. */
    public boolean isNeutral() {
        return horizontal() == NEUTRAL && vertical() == NEUTRAL;
    }

    /**
     * Scales a final knockback vector's components; pure math, Bukkit-free for testability.
     *
     * @return {@code {x*, y*, z*}} — x/z × horizontal, y × vertical
     */
    public double[] scale(double x, double y, double z) {
        double h = horizontal();
        return new double[]{x * h, y * vertical(), z * h};
    }

    /** Sets the horizontal override (clamped to {@link #MIN_FACTOR}–{@link #MAX_FACTOR}) and persists. */
    public void setHorizontal(double value) {
        this.overrideHorizontal = clamp(value, horizontal());
        save();
    }

    /** Sets the vertical override (clamped) and persists. */
    public void setVertical(double value) {
        this.overrideVertical = clamp(value, vertical());
        save();
    }

    /** Clears runtime overrides → values fall back to config.yml defaults. */
    public void resetOverrides() {
        this.overrideHorizontal = null;
        this.overrideVertical = null;
        save();
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
            String text = Files.readString(file, StandardCharsets.UTF_8);
            Double h = jsonNumber(text, "horizontal");
            Double v = jsonNumber(text, "vertical");
            this.overrideHorizontal = h;
            this.overrideVertical = v;
        } catch (IOException ignored) {
            // unreadable override file → run on config defaults this boot
        }
    }

    private void save() {
        if (file == null) {
            return;
        }
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.writeString(file,
                    "{\"horizontal\":" + overrideHorizontal + ",\"vertical\":" + overrideVertical + "}\n",
                    StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // persistence failure must never break combat
        }
    }

    /** Tiny targeted extractor (file shape is fixed by {@link #save()}; no full JSON parser needed). */
    static Double jsonNumber(String json, String key) {
        String marker = "\"" + key + "\":";
        int at = json.indexOf(marker);
        if (at < 0) {
            return null;
        }
        int from = at + marker.length();
        int end = from;
        while (end < json.length()) {
            char c = json.charAt(end);
            boolean digit = (c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.' || c == 'E' || c == 'e';
            if (!digit) {
                break;
            }
            end++;
        }
        String raw = json.substring(from, end);
        if (raw.isEmpty() || "null".equals(raw)) {
            return null;
        }
        try {
            double value = Double.parseDouble(raw);
            return clamp(value, NEUTRAL);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
