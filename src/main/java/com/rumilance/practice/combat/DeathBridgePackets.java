package com.rumilance.practice.combat;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.plugin.Plugin;

/**
 * Packet half of the death catch: for players with a {@link DeathBridge} plan, swallows the
 * "You died!" packet ({@code ClientboundPlayerCombatKillPacket}) so the client goes straight
 * from the killing blow to the respawn world with no death screen in between.
 *
 * <p>Failure here is fail-safe by construction: if ProtocolLib is absent or the adapter cannot
 * be installed, the server-side death/respawn still happened legitimately — the only visible
 * difference is the death screen flashing for a frame before the forced respawn kicks in.</p>
 */
final class DeathBridgePackets {

    private DeathBridgePackets() {
    }

    /** @return {@code true} when the suppressor is live */
    static boolean install(Plugin plugin) {
        if (plugin == null || plugin.getServer().getPluginManager().getPlugin("ProtocolLib") == null) {
            return false;
        }
        try {
            ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(
                    plugin, ListenerPriority.HIGHEST, PacketType.Play.Server.PLAYER_COMBAT_KILL) {
                @Override
                public void onPacketSending(PacketEvent event) {
                    if (event.getPlayer() != null
                            && DeathBridge.isPlanned(event.getPlayer().getUniqueId())) {
                        event.setCancelled(true);
                    }
                }
            });
            return true;
        } catch (Throwable t) {
            plugin.getLogger().warning("[N Arena][DeathBridge] ProtocolLib suppressor install failed: " + t);
            return false;
        }
    }
}
