package com.rumilance.practice.ban;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;

/**
 * Disconnect screen for /ban and /kick  Ebright aqua brand, clear status, minimal chrome.
 */
public final class BanScreens {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final TextColor CYAN = TextColor.color(0x5EEAD4);
    private static final TextColor BAN_RED = TextColor.color(0xFF6B6B);
    private static final TextColor KICK_GOLD = TextColor.color(0xFFD166);
    private static final TextColor MUTED = TextColor.color(0x94A3B8);

    private BanScreens() {
    }

    public static Component banned(String reason, String durationLabel) {
        return header()
                .append(Component.newline())
                .append(MM.deserialize("<gradient:#ff8a8a:#ff3b3b><bold>BANNED</bold></gradient>"))
                .append(Component.newline())
                .append(rule())
                .append(Component.newline())
                .append(label("Reason", reason, BAN_RED))
                .append(Component.newline())
                .append(label("Duration", durationLabel, BAN_RED))
                .append(Component.newline())
                .append(rule())
                .append(Component.newline())
                .append(Component.text("N Arena", CYAN));
    }

    public static Component kicked(String reason) {
        return header()
                .append(Component.newline())
                .append(MM.deserialize("<gradient:#ffe9a8:#ffd166><bold>KICKED</bold></gradient>"))
                .append(Component.newline())
                .append(rule())
                .append(Component.newline())
                .append(label("Reason", reason == null || reason.isBlank() ? "Kicked" : reason, KICK_GOLD))
                .append(Component.newline())
                .append(rule())
                .append(Component.newline())
                .append(Component.text("N Arena", CYAN));
    }

    public static Component motd() {
        return Component.text("N Arena", NamedTextColor.WHITE).decorate(TextDecoration.BOLD)
                .append(Component.newline())
                .append(Component.text("Practice  ", CYAN))
                .append(Component.text("·", MUTED))
                .append(Component.text("  Ranked  ", NamedTextColor.WHITE))
                .append(Component.text("·", MUTED))
                .append(Component.text("  FFA", CYAN));
    }

    private static Component header() {
        return Component.text("N Arena", CYAN).decorate(TextDecoration.BOLD)
                .append(Component.newline())
                .append(rule());
    }

    private static Component rule() {
        return Component.text("────────────", CYAN);
    }

    private static Component label(String key, String value, TextColor keyColor) {
        return Component.text(key, keyColor)
                .append(Component.text("  ·  ", MUTED))
                .append(Component.text(value, NamedTextColor.WHITE));
    }
}
