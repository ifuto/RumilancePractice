package com.rumilance.practice.join;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Join/quit lines: {@code [+] name} / {@code [-] name}. Plus is green, minus is red, the rest is white.
 */
public final class JoinQuitMessages {

    private JoinQuitMessages() {
    }

    public static Component join(String playerName) {
        return bracketed("+", NamedTextColor.GREEN).append(Component.text(" " + playerName, NamedTextColor.WHITE));
    }

    public static Component quit(String playerName) {
        return bracketed("-", NamedTextColor.RED).append(Component.text(" " + playerName, NamedTextColor.WHITE));
    }

    public static void apply(PlayerJoinEvent event) {
        event.joinMessage(join(event.getPlayer().getName()));
    }

    public static void apply(PlayerQuitEvent event) {
        event.quitMessage(quit(event.getPlayer().getName()));
    }

    private static Component bracketed(String mark, NamedTextColor markColor) {
        return Component.text("[", NamedTextColor.WHITE)
                .append(Component.text(mark, markColor))
                .append(Component.text("]", NamedTextColor.WHITE));
    }
}
