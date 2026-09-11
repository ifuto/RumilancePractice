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
 * <p>Its companion listener rewrites the outgoing respawn packet's keep-data byte while the
 * bridge revive is pending, so the client keeps its chunks and skips the "Loading terrain"
 * screen (1.20.2+ seamless respawn).</p>
 *
 * <p>Failure here is fail-safe by construction: if ProtocolLib is absent or the adapter cannot
 * be installed, the server-side death/respawn still happened legitimately — the only visible
 * difference is the death screen and a loading-terrain flash for a frame before the forced
 * respawn kicks in.</p>
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
            // Seamless-respawn half: the bridge's forced respawn sends ClientboundRespawnPacket
            // with dataToKeep=0, which makes the 1.20.2+ client drop its chunk state and flash
            // "Loading terrain". The keep-data byte is the last byte of the packet
            // (1=keep attributes, 2=keep entity data); setting both on a same-dimension respawn
            // makes the client swap location without any loading screen.
            ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(
                    plugin, ListenerPriority.HIGHEST, PacketType.Play.Server.RESPAWN) {
                @Override
                public void onPacketSending(PacketEvent event) {
                    if (event.getPlayer() == null
                            || !DeathBridge.wantsSeamlessRespawn(event.getPlayer().getUniqueId())) {
                        return;
                    }
                    try {
                        var bytes = event.getPacket().getBytes();
                        int size = bytes.size();
                        if (size > 0) {
                            bytes.write(size - 1, (byte) 0x03);
                        }
                    } catch (Throwable t) {
                        plugin.getLogger().fine(
                                "[N Arena][DeathBridge] respawn keep-data patch skipped: " + t);
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
