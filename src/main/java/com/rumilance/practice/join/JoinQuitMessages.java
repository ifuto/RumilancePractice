package com.rumilance.practice.join;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Join/quit lines: {@code [+] name} / {@code [-] name}. Plus is green, minus is red, the rest is white.
 */
public final class JoinQuitMessages {

    /** Players whose quit line is suppressed (kicks / bans leave via their own screen already). */
    private static final java.util.Set<java.util.UUID> SUPPRESSED_QUITS =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    private JoinQuitMessages() {
    }

    /**
     * Marks the player's upcoming quit message for suppression. Used before kicking/banning:
     * the player already sees the kick/ban screen, so a {@code [-] name} line would be noise.
     */
    public static void suppressQuit(java.util.UUID playerId) {
        SUPPRESSED_QUITS.add(playerId);
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
        if (SUPPRESSED_QUITS.remove(event.getPlayer().getUniqueId())) {
            event.quitMessage(null);
            return;
        }
        event.quitMessage(quit(event.getPlayer().getName()));
    }

    /** Kicked/banned players leave silently — no {@code [-] name} line in chat. */
    public static void apply(org.bukkit.event.player.PlayerKickEvent event) {
        SUPPRESSED_QUITS.add(event.getPlayer().getUniqueId());
        event.leaveMessage(null);
    }

    private static Component bracketed(String mark, NamedTextColor markColor) {
        return Component.text("[", NamedTextColor.WHITE)
                .append(Component.text(mark, markColor))
                .append(Component.text("]", NamedTextColor.WHITE));
    }
}
