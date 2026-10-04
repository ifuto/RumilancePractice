package com.rumilance.practice.util;

import com.rumilance.practice.packetbot.PacketBot;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/**
 * The real-player view of the server. Practice bots are real {@link PacketBot fake
 * ServerPlayer}s (vanilla pipeline, skins, combat) but they are <b>not players of this
 * server</b> — each one belongs to the user who summoned it. Anything player-facing (online
 * counts, player lists, scoreboards) must therefore count only real players; use this.
 *
 * <p>Internal gameplay loops (broadcasts, damage routing, arena bookkeeping) may keep
 * iterating {@code Bukkit.getOnlinePlayers()} — bots receiving a broadcast is harmless, and
 * bot sessions need the bot present in those loops.</p>
 */
public final class RealPlayers {

    /**
     * Fake players that must be treated as real ones: the {@code /testplayer} spawns. They are
     * {@link PacketBot}s under the hood (so they can stand in a lobby, hold a kit and fight),
     * but their whole purpose is to impersonate a real opponent for operator smoke tests — so
     * they belong in the player list, the online count and every duel picker.
     */
    private static final java.util.Set<UUID> HONORARY = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private RealPlayers() {
    }

    /** Starts counting a fake player as a real one (see {@link #HONORARY}). */
    public static void include(UUID playerId) {
        if (playerId != null) {
            HONORARY.add(playerId);
        }
    }

    /** Stops counting a fake player as a real one. */
    public static void exclude(UUID playerId) {
        if (playerId != null) {
            HONORARY.remove(playerId);
        }
    }

    /** True when the player is one of the plugin's fake bot players. */
    public static boolean isBot(Player player) {
        if (player == null) {
            return false;
        }
        if (HONORARY.contains(player.getUniqueId())) {
            return false;
        }
        return PacketBot.isBot(player);
    }

    /** Online players excluding every live bot (the number users should see). */
    public static List<Player> online() {
        List<Player> out = new java.util.ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!isBot(player)) {
                out.add(player);
            }
        }
        return out;
    }

    /** Real online player count (the number shown in menus and scoreboards). */
    public static int count() {
        int n = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!isBot(player)) {
                n++;
            }
        }
        return n;
    }
}
