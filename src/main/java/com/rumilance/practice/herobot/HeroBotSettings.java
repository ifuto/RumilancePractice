package com.rumilance.practice.herobot;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HeroBot's settings, ported from {@code hero.bane.herobot.HeroBotSettings} (herobot 1.21.11-1.7.6).
 *
 * <p>The reference mod keeps these in {@code config/herobot.json}; on Paper they live in the
 * plugin's {@code quantum.yml} ({@code herobot:} section) and are read at load time. The
 * defaults are the reference mod's defaults — the Quantum map only flips
 * {@code shieldStunning} (via {@code herobot shieldStunning true perm world} in
 * {@code quantum:options/toggles/stun_on.mcfunction}).</p>
 *
 * <p>Every field here exists because a map function can observe it:</p>
 * <ul>
 *   <li>{@code botPingToTicks} / ping — {@code quantum:options/set_ping} sets
 *       {@code .ping} and the map's {@code bot_ping} workflow feeds it to
 *       {@code player @a[tag=xlib_bot] ping $(ping)}; herobot converts ms to ticks with this
 *       divisor (25) and delays knockback/attacks by it.</li>
 *   <li>{@code botLagAttacks} / {@code botLagUses} — when true, herobot defers the actual
 *       attack/use by {@code delayTicks(1)} instead of applying it immediately.</li>
 *   <li>{@code shieldStunning} / {@code shieldStunningWindow} / {@code shieldDelayTicks} —
 *       the {@code quantum:shield/*} + {@code tempshield} logic in the map.</li>
 *   <li>{@code allowSpawningOfflinePlayers} — decided whether {@code /player <name> spawn}
 *       needs a resolvable game profile (Paper has no Mojang connection here, so this is what
 *       lets the runtime own every QuantumBOT instance).</li>
 * </ul>
 */
public final class HeroBotSettings {

    /** Milliseconds per tick the reference uses when converting a bot's ping (herobot default 25). */
    public static int botPingToTicks = 25;

    /** Defer attacks by the bot's ping (reference default false — the map runs with it off). */
    public static boolean botLagAttacks = false;

    /** Defer item uses by the bot's ping (reference default false). */
    public static boolean botLagUses = false;

    /** Reference default: a bot struck while blocking loses its shield window (see map's .stun). */
    public static boolean shieldStunning = false;

    /** Ticks after a shield-disable in which knockback is scaled to 0.4 (herobot default 3). */
    public static int shieldStunningWindow = 3;

    /** Vanilla shield disable delay override (herobot default 5 ticks). */
    public static int shieldDelayTicks = 5;

    /** Allow {@code /player <name> spawn} without a Mojang-resolvable profile. */
    public static boolean allowSpawningOfflinePlayers = true;

    /** Remove fake players when they die (reference default false). */
    public static boolean botLeaveOnDeath = false;

    /** Creative fake players fly with the creative flight model (reference default true). */
    public static boolean creativeNoClip = false;

    /**
     * Where in the server tick the bots are driven.
     *
     * <p>In the reference (Fabric mod) a bot <i>is</i> a {@code ServerPlayer} in the player list,
     * so its {@code doTick} runs from {@code PlayerList.tick()} — i.e. inside the tick, before the
     * server's {@code #minecraft:tick} function tag runs. Paper cannot inject at that exact point,
     * so the plugin drives the bots itself and the only question is which hook lands on the right
     * side of the function tick:</p>
     * <ul>
     *   <li>{@code tick-start} — Paper's {@code ServerTickStartEvent} (start of the tick, i.e.
     *       before the function tag). Same-tick visibility, like the reference.</li>
     *   <li>{@code scheduler} — {@code BukkitScheduler#runTaskTimer(1, 1)}: the heart of the
     *       scheduler sits at the <i>end</i> of the tick, so a function reading bot state sees the
     *       previous tick's — a systematic one-tick lag against the reference.</li>
     * </ul>
     */
    public static String tickPhase = "tick-start";

    /** Rules set with {@code perm} by {@code /herobot … perm world}, persisted on shutdown. */
    private static final Map<String, String> PERSISTED = new LinkedHashMap<>();

    private HeroBotSettings() {
    }

    /** Rule names as the reference exposes them: the field names, camelCase. */
    public static List<String> ruleNames() {
        return List.of("creativeNoClip", "allowSpawningOfflinePlayers", "botPingToTicks",
                "botLagAttacks", "botLagUses", "botLeaveOnDeath", "shieldStunning",
                "shieldStunningWindow", "shieldDelayTicks");
    }

    /**
     * {@code /herobot <rule> <value> [temp|perm [world|client]]} — the reference writes the value
     * straight into the annotated static field, so that is what happens here too. Rules the
     * datapack actually drives ({@code shieldStunning}) are the ones this port has; a rule name
     * the port does not know returns {@code false} so the caller can say "unknown rule" instead
     * of silently ignoring a typo.
     */
    public static boolean apply(String rule, String value, boolean permanent) {
        switch (rule) {
            case "creativeNoClip" -> creativeNoClip = boolOr(value, creativeNoClip);
            case "allowSpawningOfflinePlayers" ->
                    allowSpawningOfflinePlayers = boolOr(value, allowSpawningOfflinePlayers);
            case "botPingToTicks" -> botPingToTicks = intOr(value, botPingToTicks);
            case "botLagAttacks" -> botLagAttacks = boolOr(value, botLagAttacks);
            case "botLagUses" -> botLagUses = boolOr(value, botLagUses);
            case "botLeaveOnDeath" -> botLeaveOnDeath = boolOr(value, botLeaveOnDeath);
            case "shieldStunning" -> shieldStunning = boolOr(value, shieldStunning);
            case "shieldStunningWindow" -> shieldStunningWindow = intOr(value, shieldStunningWindow);
            case "shieldDelayTicks" -> shieldDelayTicks = intOr(value, shieldDelayTicks);
            default -> {
                return false;
            }
        }
        if (permanent) {
            PERSISTED.put(rule, value);
        }
        return true;
    }

    /** {@code /herobot <rule> reset}: back to the reference mod's default. */
    public static boolean resetOne(String rule) {
        switch (rule) {
            case "creativeNoClip" -> creativeNoClip = false;
            case "allowSpawningOfflinePlayers" -> allowSpawningOfflinePlayers = true;
            case "botPingToTicks" -> botPingToTicks = 25;
            case "botLagAttacks" -> botLagAttacks = false;
            case "botLagUses" -> botLagUses = false;
            case "botLeaveOnDeath" -> botLeaveOnDeath = false;
            case "shieldStunning" -> shieldStunning = false;
            case "shieldStunningWindow" -> shieldStunningWindow = 3;
            case "shieldDelayTicks" -> shieldDelayTicks = 5;
            default -> {
                return false;
            }
        }
        PERSISTED.remove(rule);
        return true;
    }

    /** The rules applied with {@code perm} that should survive a restart. */
    public static Map<String, String> persisted() {
        return Collections.unmodifiableMap(PERSISTED);
    }

    /**
     * Reads {@code herobot:} keys from the plugin's quantum.yml. Kept deliberately dumb (the
     * file is hand-edited by operators; a typo must not stop the plugin from enabling).
     */
    public static void loadFrom(File quantumYml, org.slf4j.Logger log) {
        if (quantumYml == null || !quantumYml.isFile()) {
            return;
        }
        try {
            String text = Files.readString(quantumYml.toPath(), StandardCharsets.UTF_8);
            boolean inHerobot = false;
            for (String rawLine : text.split("\r?\n")) {
                String line = rawLine.stripTrailing();
                if (line.isBlank() || line.stripLeading().startsWith("#")) {
                    continue;
                }
                int indent = line.length() - line.stripLeading().length();
                String trimmed = line.stripLeading();
                if (indent == 0) {
                    inHerobot = trimmed.startsWith("herobot:");
                    continue;
                }
                if (!inHerobot || !trimmed.contains(":")) {
                    continue;
                }
                String key = trimmed.substring(0, trimmed.indexOf(':')).trim();
                String value = trimmed.substring(trimmed.indexOf(':') + 1).trim();
                if (value.isEmpty()) {
                    continue;
                }
                switch (key) {
                    case "bot-ping-to-ticks" -> botPingToTicks = intOr(value, botPingToTicks);
                    case "bot-lag-attacks" -> botLagAttacks = boolOr(value, botLagAttacks);
                    case "bot-lag-uses" -> botLagUses = boolOr(value, botLagUses);
                    case "shield-stunning" -> shieldStunning = boolOr(value, shieldStunning);
                    case "shield-stunning-window" -> shieldStunningWindow = intOr(value, shieldStunningWindow);
                    case "shield-delay-ticks" -> shieldDelayTicks = intOr(value, shieldDelayTicks);
                    case "allow-spawning-offline-players" ->
                            allowSpawningOfflinePlayers = boolOr(value, allowSpawningOfflinePlayers);
                    case "bot-leave-on-death" -> botLeaveOnDeath = boolOr(value, botLeaveOnDeath);
                    case "creative-no-clip" -> creativeNoClip = boolOr(value, creativeNoClip);
                    case "tick-phase" -> tickPhase = value;
                    default -> {
                        // Unknown key: ignore (forward compatible with newer herobot configs).
                    }
                }
            }
            if (log != null) {
                log.info("[Quantum] herobot settings: pingTicks=" + botPingToTicks
                        + " shieldStunning=" + shieldStunning
                        + " shieldDelay=" + shieldDelayTicks
                        + " lagAttacks=" + botLagAttacks + " lagUses=" + botLagUses
                        + " tickPhase=" + tickPhase);
            }
        } catch (IOException e) {
            if (log != null) {
                log.warn("[Quantum] could not read quantum.yml herobot section: " + e.getMessage());
            }
        }
    }

    private static int intOr(String value, int fallback) {
        try {
            return Integer.parseInt(value.replace("\"", "").trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static boolean boolOr(String value, boolean fallback) {
        String v = value.replace("\"", "").trim();
        if (v.equalsIgnoreCase("true")) {
            return true;
        }
        if (v.equalsIgnoreCase("false")) {
            return false;
        }
        return fallback;
    }
}
