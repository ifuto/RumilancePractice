package com.rumilance.practice.ban;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;

/**
 * Broadcast lines for /ban and /kick  Ebright, modern aqua frame with clear accents.
 */
public final class BanAnnounce {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final TextColor FRAME = TextColor.color(0x5EEAD4);
    private static final TextColor BAN_ACCENT = TextColor.color(0xFF6B6B);
    private static final TextColor KICK_ACCENT = TextColor.color(0xFFD166);
    private static final TextColor MUTED = TextColor.color(0x94A3B8);
    private static final String RULE = "━━━━━━━━━━━━━━━━━━━━━━━━━━━━";

    private BanAnnounce() {
    }

    public static Component ban(String playerName, String reason, String durationLabel) {
        return Component.text(RULE, FRAME)
                .append(Component.newline())
                .append(badge("BAN", "#ff8a8a", "#ff3b3b", BAN_ACCENT))
                .append(Component.text("  ", NamedTextColor.WHITE))
                .append(Component.text(playerName, NamedTextColor.WHITE).decorate(TextDecoration.BOLD))
                .append(Component.text("  has been banned", MUTED))
                .append(Component.newline())
                .append(row("Reason", reason, BAN_ACCENT))
                .append(Component.newline())
                .append(row("Duration", durationLabel, BAN_ACCENT))
                .append(Component.newline())
                .append(Component.text(RULE, FRAME));
    }

    public static Component kick(String playerName) {
        return Component.text(RULE, FRAME)
                .append(Component.newline())
                .append(badge("KICK", "#ffe9a8", "#ffd166", KICK_ACCENT))
                .append(Component.text("  ", NamedTextColor.WHITE))
                .append(Component.text(playerName, NamedTextColor.WHITE).decorate(TextDecoration.BOLD))
                .append(Component.text("  has been kicked", MUTED))
                .append(Component.newline())
                .append(Component.text(RULE, FRAME));
    }

    private static Component row(String key, String value, TextColor accent) {
        return Component.text("  " + key, accent)
                .append(Component.text("  ·  ", MUTED))
                .append(Component.text(value, NamedTextColor.WHITE));
    }

    private static Component badge(String inner, String from, String to, TextColor innerColor) {
        Component open = MM.deserialize("<gradient:" + from + ":" + to + ">[</gradient>");
        Component close = MM.deserialize("<gradient:" + to + ":" + from + ">]</gradient>");
        return open.append(Component.text(inner, innerColor).decorate(TextDecoration.BOLD)).append(close);
    }
}
