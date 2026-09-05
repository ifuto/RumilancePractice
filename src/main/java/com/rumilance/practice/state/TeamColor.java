package com.rumilance.practice.state;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Material;

import java.util.List;

/**
 * Team colors for duels and party battles.
 *
 * <p>Duels and the classic two-side party fight use {@link #RED} (participant index 0) and
 * {@link #BLUE} (index 1). Multi-team party battles (up to 3/5/7 teams depending on the
 * host's rank) use the colors in {@link #canonical(int)} order: RED, BLUE, GREEN, YELLOW,
 * AQUA, PURPLE, GOLD.</p>
 */
public enum TeamColor {
    RED(NamedTextColor.RED, Material.RED_WOOL, Color.fromRGB(0xC62828), "r"),
    BLUE(NamedTextColor.AQUA, Material.LIGHT_BLUE_WOOL, Color.fromRGB(0x1565C0), "b"),
    GREEN(NamedTextColor.GREEN, Material.LIME_WOOL, Color.fromRGB(0x2E7D32), "g"),
    YELLOW(NamedTextColor.YELLOW, Material.YELLOW_WOOL, Color.fromRGB(0xF9A825), "y"),
    AQUA(NamedTextColor.DARK_AQUA, Material.CYAN_WOOL, Color.fromRGB(0x00838F), "a"),
    PURPLE(NamedTextColor.LIGHT_PURPLE, Material.PURPLE_WOOL, Color.fromRGB(0x6A1B9A), "p"),
    GOLD(NamedTextColor.GOLD, Material.ORANGE_WOOL, Color.fromRGB(0xEF6C00), "o");

    /** Canonical battle order: the first N entries are the teams of an N-team battle. */
    private static final List<TeamColor> CANONICAL = List.of(values());

    /** Absolute maximum number of teams in one party battle (VIP+ host). */
    public static final int MAX_TEAMS = CANONICAL.size();

    private final NamedTextColor textColor;
    private final Material wool;
    private final Color leatherColor;
    private final String sortKey;

    TeamColor(NamedTextColor textColor, Material wool, Color leatherColor, String sortKey) {
        this.textColor = textColor;
        this.wool = wool;
        this.leatherColor = leatherColor;
        this.sortKey = sortKey;
    }

    /** The two-side opposite. Only meaningful for classic RED/BLUE fights. */
    public TeamColor opposite() {
        return this == RED ? BLUE : RED;
    }

    /** Chat / nametag text colour for this team. */
    public NamedTextColor textColor() {
        return textColor;
    }

    /** Wool block representing this team in GUIs (color toggles, team list). */
    public Material wool() {
        return wool;
    }

    /** Dye colour for team-coloured leather armor. */
    public Color leatherColor() {
        return leatherColor;
    }

    /** One-char key used to build unique, sorted scoreboard team names. */
    public String sortKey() {
        return sortKey;
    }

    /** Short capitalized label for messages ("RED", "BLUE", ...). */
    public String label() {
        return name();
    }

    /** The first {@code count} team colors in canonical battle order (clamped to 2..MAX_TEAMS). */
    public static List<TeamColor> canonical(int count) {
        int clamped = Math.max(2, Math.min(MAX_TEAMS, count));
        return CANONICAL.subList(0, clamped);
    }

    /** The nth team color in canonical order (index clamped into range). */
    public static TeamColor byIndex(int index) {
        return CANONICAL.get(Math.max(0, Math.min(CANONICAL.size() - 1, index)));
    }

    /** Parse a color name leniently (case-insensitive), or {@code null} when unknown. */
    public static TeamColor parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
