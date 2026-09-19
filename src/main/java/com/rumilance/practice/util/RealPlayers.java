package com.rumilance.practice.util;

import com.rumilance.practice.packetbot.PacketBot;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;

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

    private RealPlayers() {
    }

    /** True when the player is one of the plugin's fake bot players. */
    public static boolean isBot(Player player) {
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
