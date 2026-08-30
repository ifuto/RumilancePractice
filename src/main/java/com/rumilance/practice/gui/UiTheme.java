package com.rumilance.practice.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;

/**
 * N Arena menu design tokens — dark slate panels with cyan accents (brand {@code #55FFFF}).
 */
public final class UiTheme {

    public static final Material BACKGROUND = Material.BLACK_STAINED_GLASS_PANE;
    public static final Material PANEL = Material.GRAY_STAINED_GLASS_PANE;
    public static final Material ACCENT = Material.CYAN_STAINED_GLASS_PANE;
    public static final Material ACCENT_SOFT = Material.LIGHT_BLUE_STAINED_GLASS_PANE;

    public static final Material CLOSE = Material.BARRIER;
    public static final Material BACK = Material.ARROW;
    public static final Material NEXT_PAGE = Material.SPECTRAL_ARROW;
    public static final Material PREV_PAGE = Material.SPECTRAL_ARROW;
    public static final Material CONFIRM = Material.LIME_DYE;
    public static final Material INFO = Material.NETHER_STAR;

    public static final Material TOGGLE_ON = Material.LIME_DYE;
    public static final Material TOGGLE_OFF = Material.RED_DYE;

    /** Brand cyan — matches Lunar Server Mappings primaryColor. */
    public static final TextColor PRIMARY = TextColor.color(0x55FFFF);
    public static final TextColor SECONDARY = TextColor.color(0x7DD3FC);
    public static final TextColor SUCCESS = TextColor.color(0x4ADE80);
    public static final TextColor DANGER = TextColor.color(0xF87171);
    public static final TextColor WARNING = TextColor.color(0xFBBF24);
    public static final TextColor MUTED = TextColor.color(0x94A3B8);
    public static final TextColor VALUE = NamedTextColor.WHITE;
    public static final TextColor HEADER = TextColor.color(0x22D3EE);

    private UiTheme() {
    }

    public static Component divider() {
        return Component.text(" ─────────── ", TextColor.color(0x334155))
                .decoration(TextDecoration.ITALIC, false);
    }

    public static Component blank() {
        return Component.empty();
    }

    public static Component labelValue(String label, String value) {
        return Component.text(label + " ", MUTED)
                .append(Component.text(value, VALUE))
                .decoration(TextDecoration.ITALIC, false);
    }

    public static Component line(String text) {
        return Component.text(text, MUTED).decoration(TextDecoration.ITALIC, false);
    }

    public static Component hint(String text) {
        return Component.text("▸ ", PRIMARY)
                .append(Component.text(text, MUTED))
                .decoration(TextDecoration.ITALIC, false);
    }

    /** Standard click CTA used across player menus. */
    public static Component clickToOpen() {
        return hint("Click");
    }

    public static Component status(String text, TextColor color) {
        return Component.text("● ", color)
                .append(Component.text(text, color))
                .decoration(TextDecoration.ITALIC, false);
    }

    /** Branded menu title prefix. */
    public static Component menuTitle(String text) {
        return Component.text(text, PRIMARY).decoration(TextDecoration.ITALIC, false);
    }
}
