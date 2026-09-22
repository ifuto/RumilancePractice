package com.rumilance.practice.model;

/** Per-slot original-kit battle settings, edited in the {@code OriginalKitSettingsGui} and
 * stored alongside the kit's loadout in {@code original_kit_slots.settings_json}.
 *
 * <p>Every switch defaults to vanilla behaviour ("plugin hands off") and only deviates from
 * vanilla when the owner explicitly changes it, so an untouched original kit fights exactly
 * like a plain survival fight.</p>
 *
 * <p>The settings carry every rule that can actually be enforced by the match runtime, so the
 * settings screen never shows a switch that does nothing. Knockback is absent on purpose: the
 * server delegates knockback shaping to an external plugin (KnockBackSync), so there is no
 * in-plugin rule to flip for it.</p>
 */
public record OriginalKitSettings(
        boolean fallDamage,
        boolean totem,
        boolean pearl,
        boolean naturalRegen,
        boolean autoFood,
        boolean swordShieldBreak,
        boolean blockPlace,
        boolean blockBreak,
        boolean bedExplosion,
        boolean forceAdventure,
        double maxHealth,
        int timeoutSeconds,
        double bodyScale
) {

    public static final double DEFAULT_MAX_HEALTH = 20.0d;
    public static final double MIN_MAX_HEALTH = 2.0d;
    public static final double MAX_MAX_HEALTH = 100.0d;
    public static final int MIN_TIMEOUT = 0;
    public static final int MAX_TIMEOUT = 60 * 60 * 6; // 6 hours, effectively unlimited
    public static final double DEFAULT_BODY_SCALE = 1.0d;
    public static final double MIN_BODY_SCALE = 0.25d;
    public static final double MAX_BODY_SCALE = 4.0d;

    public OriginalKitSettings {
        maxHealth = clampHealth(maxHealth);
        timeoutSeconds = Math.max(MIN_TIMEOUT, Math.min(MAX_TIMEOUT, timeoutSeconds));
        bodyScale = clampScale(bodyScale);
    }

    /** The untouched setup: vanilla fall/totem/pearl/regen, no kit intervention at all. */
    public static OriginalKitSettings defaults() {
        return new OriginalKitSettings(
                true,  // fallDamage
                true,  // totem
                true,  // pearl
                true,  // naturalRegen
                false, // autoFood
                false, // swordShieldBreak
                false, // blockPlace
                false, // blockBreak
                false, // bedExplosion
                false, // forceAdventure
                DEFAULT_MAX_HEALTH,
                MIN_TIMEOUT,
                DEFAULT_BODY_SCALE
        );
    }

    /** Copy with one boolean switch flipped to {@code value}. */
    public OriginalKitSettings with(String key, boolean value) {
        return switch (key) {
            case "fallDamage" -> new OriginalKitSettings(value, totem, pearl, naturalRegen, autoFood,
                    swordShieldBreak, blockPlace, blockBreak, bedExplosion, forceAdventure,
                    maxHealth, timeoutSeconds, bodyScale);
            case "totem" -> new OriginalKitSettings(fallDamage, value, pearl, naturalRegen, autoFood,
                    swordShieldBreak, blockPlace, blockBreak, bedExplosion, forceAdventure,
                    maxHealth, timeoutSeconds, bodyScale);
            case "pearl" -> new OriginalKitSettings(fallDamage, totem, value, naturalRegen, autoFood,
                    swordShieldBreak, blockPlace, blockBreak, bedExplosion, forceAdventure,
                    maxHealth, timeoutSeconds, bodyScale);
            case "naturalRegen" -> new OriginalKitSettings(fallDamage, totem, pearl, value, autoFood,
                    swordShieldBreak, blockPlace, blockBreak, bedExplosion, forceAdventure,
                    maxHealth, timeoutSeconds, bodyScale);
            case "autoFood" -> new OriginalKitSettings(fallDamage, totem, pearl, naturalRegen, value,
                    swordShieldBreak, blockPlace, blockBreak, bedExplosion, forceAdventure,
                    maxHealth, timeoutSeconds, bodyScale);
            case "swordShieldBreak" -> new OriginalKitSettings(fallDamage, totem, pearl, naturalRegen, autoFood,
                    value, blockPlace, blockBreak, bedExplosion, forceAdventure,
                    maxHealth, timeoutSeconds, bodyScale);
            case "blockPlace" -> new OriginalKitSettings(fallDamage, totem, pearl, naturalRegen, autoFood,
                    swordShieldBreak, value, blockBreak, bedExplosion, forceAdventure,
                    maxHealth, timeoutSeconds, bodyScale);
            case "blockBreak" -> new OriginalKitSettings(fallDamage, totem, pearl, naturalRegen, autoFood,
                    swordShieldBreak, blockPlace, value, bedExplosion, forceAdventure,
                    maxHealth, timeoutSeconds, bodyScale);
            case "bedExplosion" -> new OriginalKitSettings(fallDamage, totem, pearl, naturalRegen, autoFood,
                    swordShieldBreak, blockPlace, blockBreak, value, forceAdventure,
                    maxHealth, timeoutSeconds, bodyScale);
            case "forceAdventure" -> new OriginalKitSettings(fallDamage, totem, pearl, naturalRegen, autoFood,
                    swordShieldBreak, blockPlace, blockBreak, bedExplosion, value,
                    maxHealth, timeoutSeconds, bodyScale);
            default -> this;
        };
    }

    public OriginalKitSettings withMaxHealth(double value) {
        return new OriginalKitSettings(fallDamage, totem, pearl, naturalRegen, autoFood,
                swordShieldBreak, blockPlace, blockBreak, bedExplosion, forceAdventure,
                value, timeoutSeconds, bodyScale);
    }

    public OriginalKitSettings withTimeoutSeconds(int value) {
        return new OriginalKitSettings(fallDamage, totem, pearl, naturalRegen, autoFood,
                swordShieldBreak, blockPlace, blockBreak, bedExplosion, forceAdventure,
                maxHealth, value, bodyScale);
    }

    public OriginalKitSettings withBodyScale(double value) {
        return new OriginalKitSettings(fallDamage, totem, pearl, naturalRegen, autoFood,
                swordShieldBreak, blockPlace, blockBreak, bedExplosion, forceAdventure,
                maxHealth, timeoutSeconds, value);
    }

    private static double clampScale(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return DEFAULT_BODY_SCALE;
        }
        return Math.max(MIN_BODY_SCALE, Math.min(MAX_BODY_SCALE, value));
    }

    private static double clampHealth(double value) {
        if (Double.isNaN(value)) {
            return DEFAULT_MAX_HEALTH;
        }
        return Math.max(MIN_MAX_HEALTH, Math.min(MAX_MAX_HEALTH, value));
    }

    // ---- lightweight text serialisation (no JSON library is shaded) ----

    /** Serialised form: {@code key=value;} pairs, fixed key order, enums-as-0/1. */
    public String serialize() {
        StringBuilder out = new StringBuilder(160);
        out.append("fallDamage=").append(fallDamage ? 1 : 0).append(';');
        out.append("totem=").append(totem ? 1 : 0).append(';');
        out.append("pearl=").append(pearl ? 1 : 0).append(';');
        out.append("naturalRegen=").append(naturalRegen ? 1 : 0).append(';');
        out.append("autoFood=").append(autoFood ? 1 : 0).append(';');
        out.append("swordShieldBreak=").append(swordShieldBreak ? 1 : 0).append(';');
        out.append("blockPlace=").append(blockPlace ? 1 : 0).append(';');
        out.append("blockBreak=").append(blockBreak ? 1 : 0).append(';');
        out.append("bedExplosion=").append(bedExplosion ? 1 : 0).append(';');
        out.append("forceAdventure=").append(forceAdventure ? 1 : 0).append(';');
        out.append("maxHealth=").append(maxHealth).append(';');
        out.append("timeoutSeconds=").append(timeoutSeconds).append(';');
        out.append("bodyScale=").append(bodyScale);
        return out.toString();
    }

    /**
     * Parses a {@link #serialize()} string back into settings. Unknown keys are ignored and
     * missing keys fall back to {@link #defaults()}, so old or hand-edited rows never break.
     * A blank / null string yields the untouched defaults.
     */
    public static OriginalKitSettings parse(String raw) {
        OriginalKitSettings settings = defaults();
        if (raw == null || raw.isBlank()) {
            return settings;
        }
        for (String entry : raw.split(";")) {
            if (entry.isBlank()) {
                continue;
            }
            int eq = entry.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = entry.substring(0, eq).trim();
            String value = entry.substring(eq + 1).trim();
            if (value.isEmpty()) {
                continue;
            }
            switch (key) {
                case "fallDamage" -> settings = settings.with(key, parseInt(value) != 0);
                case "totem" -> settings = settings.with(key, parseInt(value) != 0);
                case "pearl" -> settings = settings.with(key, parseInt(value) != 0);
                case "naturalRegen" -> settings = settings.with(key, parseInt(value) != 0);
                case "autoFood" -> settings = settings.with(key, parseInt(value) != 0);
                case "swordShieldBreak" -> settings = settings.with(key, parseInt(value) != 0);
                case "blockPlace" -> settings = settings.with(key, parseInt(value) != 0);
                case "blockBreak" -> settings = settings.with(key, parseInt(value) != 0);
                case "bedExplosion" -> settings = settings.with(key, parseInt(value) != 0);
                case "forceAdventure" -> settings = settings.with(key, parseInt(value) != 0);
                case "maxHealth" -> settings = settings.withMaxHealth(parseDouble(value));
                case "timeoutSeconds" -> settings = settings.withTimeoutSeconds(parseInt(value));
                case "bodyScale" -> settings = settings.withBodyScale(parseDouble(value));
                default -> {
                    // ignored — forward compatible with future settings
                }
            }
        }
        return settings;
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static double parseDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            // NaN flows into clampHealth / clampScale, which map it back to that field's
            // vanilla default (20 HP / 1.0 scale). Returning DEFAULT_MAX_HEALTH here wrongly
            // clamped a garbled bodyScale to MAX_BODY_SCALE (4.0) instead of 1.0.
            return Double.NaN;
        }
    }
}
