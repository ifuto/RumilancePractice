package com.rumilance.practice.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;

/**
 * Central design tokens for every RumilancePractice menu: the filler/background materials,
 * accent colours, and a few ready-made divider/spacer components. Keeping these in one place
 * means a single tweak re-skins every GUI instead of editing colours scattered across 20+
 * menu classes.
 *
 * <p>The visual language is intentionally "dark + one accent": black glass panes fade the
 * inventory chrome into the background, while a single aqua/gold accent draws the eye to the
 * active/selected element. Bold is avoided everywhere (per the plugin's house style).</p>
 */
public final class UiTheme {

    // ---- Materials ------------------------------------------------------

    /** Empty, unclickable background tile filling every unused slot. */
    public static final Material BACKGROUND = Material.BLACK_STAINED_GLASS_PANE;
    /** Slightly lighter tile used to frame a content panel inside the menu. */
    public static final Material PANEL = Material.GRAY_STAINED_GLASS_PANE;
    /** Accent tile used for section headers / active highlights. */
    public static final Material ACCENT = Material.CYAN_STAINED_GLASS_PANE;

    public static final Material CLOSE = Material.BARRIER;
    public static final Material BACK = Material.ARROW;
    public static final Material NEXT_PAGE = Material.ARROW;
    public static final Material PREV_PAGE = Material.ARROW;
    public static final Material CONFIRM = Material.LIME_DYE;
    public static final Material INFO = Material.NETHER_STAR;

    public static final Material TOGGLE_ON = Material.LIME_DYE;
    public static final Material TOGGLE_OFF = Material.GRAY_DYE;

    // ---- Colours --------------------------------------------------------

    public static final TextColor PRIMARY = TextColor.color(0x55FFFF);   // aqua
    public static final TextColor SECONDARY = TextColor.color(0xFFAA00); // gold
    public static final TextColor SUCCESS = NamedTextColor.GREEN;
    public static final TextColor DANGER = NamedTextColor.RED;
    public static final TextColor WARNING = NamedTextColor.YELLOW;
    public static final TextColor MUTED = NamedTextColor.GRAY;
    public static final TextColor VALUE = NamedTextColor.WHITE;
    public static final TextColor HEADER = TextColor.color(0xFFD700); // gold, for headings

    private UiTheme() {
    }

    /** A horizontal rule used between lore sections ("─────────────"). */
    public static Component divider() {
        return Component.text(" ".repeat(18), NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.STRIKETHROUGH, true)
                .decoration(TextDecoration.ITALIC, false);
    }

    /** A blank lore line (kept explicit so call sites read clearly). */
    public static Component blank() {
        return Component.empty();
    }

    /** A muted label followed by a white value, e.g. {@code "Elo: 1234"}. */
    public static Component labelValue(String label, String value) {
        return Component.text(label + ": ", MUTED)
                .append(Component.text(value, VALUE))
                .decoration(TextDecoration.ITALIC, false);
    }

    /** A single muted lore line with no value. */
    public static Component line(String text) {
        return Component.text(text, MUTED).decoration(TextDecoration.ITALIC, false);
    }

    /** An accent-coloured hint line, e.g. "▶ Click to join". */
    public static Component hint(String text) {
        return Component.text("▶ ", PRIMARY)
                .append(Component.text(text, NamedTextColor.GRAY))
                .decoration(TextDecoration.ITALIC, false);
    }

    /** A coloured status word, e.g. green "● ONLINE". */
    public static Component status(String text, TextColor color) {
        return Component.text("● ", color)
                .append(Component.text(text, color))
                .decoration(TextDecoration.ITALIC, false);
    }
}
