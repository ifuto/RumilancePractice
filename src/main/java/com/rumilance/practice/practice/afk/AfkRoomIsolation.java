package com.rumilance.practice.practice.afk;

/**
 * Packet-level isolation of the private AFK rooms ("他の人がafkcしてるとこは見れない").
 *
 * <p>While a player owns an AFK crystal room, everything outside that room's footprint is
 * dropped before it reaches their client: chunk / block-change / block-entity / light packets
 * for foreign chunks, and every entity packet that is not inside the room — player packets
 * first of all, so another player's room (and its bot) can never be seen. The same filter also
 * hides an AFK player from viewers who are not in that room. {@code Player#hidePlayer} stays as
 * the Bukkit-level layer on top.</p>
 *
 * <p>Movement is <em>not</em> restricted by any of this: the room only caps where blocks may be
 * placed, never where the player may go.</p>
 *
 * <p>Needs ProtocolLib (soft-depend); without it the rooms simply stay Bukkit-hidden only. All
 * ProtocolLib types live in {@link AfkRoomIsolationPackets}, which is only class-loaded through
 * this guarded entry point.</p>
 */
public final class AfkRoomIsolation {

    private AfkRoomIsolation() {
    }

    /** Registers the packet filter when ProtocolLib is present; a silent no-op otherwise. */
    public static boolean register(org.bukkit.plugin.Plugin plugin, AfkRoomIsolationSource source) {
        if (plugin.getServer().getPluginManager().getPlugin("ProtocolLib") == null) {
            plugin.getLogger().info("[AfkRoom] ProtocolLib missing - room isolation falls back to hidePlayer only.");
            return false;
        }
        try {
            AfkRoomIsolationPackets.register(plugin, source);
            plugin.getLogger().info("[AfkRoom] Packet isolation active (block + player packets outside your room are dropped).");
            return true;
        } catch (Throwable t) {
            plugin.getLogger().warning("[AfkRoom] Packet isolation disabled: " + t);
            return false;
        }
    }
}
