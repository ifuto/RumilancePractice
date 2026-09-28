package com.rumilance.practice.ui;

import com.rumilance.practice.state.TeamColor;
import net.kyori.adventure.text.format.TextColor;

/**
 * Shared UI palette for team-couple colors (client-visible text).
 *
 * <p>2026-09-28 user direction: standard vanilla blue ({@code #5555FF}) must not be used —
 * take a blue that is a touch brighter instead, and soften the TAB team's blue/red (the old
 * Material-dark tones {@code 0x1565C0} / {@code 0xC62828} felt too dense). Unlike the head
 * numberer, the TAB grid and chat lines allow full RGB; the above-head / glow colour stays on
 * the legacy 16-color team packet and therefore keeps its named colours (protocol limit).</p>
 */
public final class UiColors {

    /** Team blue: a slightly brighter blue than the vanilla {@code #5555FF}. */
    public static final TextColor TEAM_BLUE = TextColor.color(0x6C86FF);
    /** Team red: softer than the old deep {@code 0xC62828}, still unmistakably red. */
    public static final TextColor TEAM_RED = TextColor.color(0xFF6A5E);

    private UiColors() {
    }

    /**
     * Text colour of one battle team in rich-RGB spots (TAB grid rows/headers, kill feed,
     * result screens). Classic teams get the bright palette; the other colours of multi-team
     * battles keep their own dye tone.
     */
    public static TextColor textOf(TeamColor color) {
        if (color == null) {
            return net.kyori.adventure.text.format.NamedTextColor.WHITE;
        }
        return switch (color) {
            case RED -> TEAM_RED;
            case BLUE -> TEAM_BLUE;
            default -> TextColor.color(color.leatherColor().asRGB());
        };
    }

    /** Faded variant used by the fight TAB's death-dot (55% tint toward white). */
    public static TextColor fadedOf(TeamColor color) {
        if (color == null) {
            return net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY;
        }
        int rgb = textOf(color).value();
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        return TextColor.color(fade(r) << 16 | fade(g) << 8 | fade(b));
    }

    private static int fade(int channel) {
        return channel + (255 - channel) * 55 / 100;
    }
}
