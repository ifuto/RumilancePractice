package com.rumilance.practice.spectator;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * ProtocolLib side of {@link SpectatorViewIsolation}: while the receiver is spectating,
 * every outbound entity packet that describes another player in spectator gamemode is
 * cancelled before it leaves the server. The player-info packets that build the TAB entry
 * are deliberately not in the filter, so spectators stay listed in TAB while their bodies
 * never render (user spec 2026-10-04).
 *
 * <p>Fail-open like every isolation filter in this plugin: a packet whose entity cannot be
 * resolved is kept — dropping it might blind the viewer to their own spectated fighter.</p>
 */
final class SpectatorViewIsolationPackets {

    /**
     * Entity packets that describe a player. Players spawn via NAMED_ENTITY_SPAWN (never
     * SPAWN_ENTITY, which is for non-living/display entities), so the spawn cancel and the
     * follow-up state packets form the complete set: movement, rotation, look, metadata,
     * equipment, effects, attributes, animations and velocity.
     */
    private static final PacketType[] PLAYER_ENTITY_PACKETS = {
            PacketType.Play.Server.NAMED_ENTITY_SPAWN,
            PacketType.Play.Server.ANIMATION,
            PacketType.Play.Server.ATTACH_ENTITY,
            PacketType.Play.Server.ENTITY_EFFECT,
            PacketType.Play.Server.ENTITY_EQUIPMENT,
            PacketType.Play.Server.ENTITY_HEAD_ROTATION,
            PacketType.Play.Server.ENTITY_LOOK,
            PacketType.Play.Server.ENTITY_METADATA,
            PacketType.Play.Server.ENTITY_TELEPORT,
            PacketType.Play.Server.ENTITY_VELOCITY,
            PacketType.Play.Server.HURT_ANIMATION,
            PacketType.Play.Server.REL_ENTITY_MOVE,
            PacketType.Play.Server.REL_ENTITY_MOVE_LOOK,
            PacketType.Play.Server.REMOVE_ENTITY_EFFECT,
            PacketType.Play.Server.UPDATE_ATTRIBUTES,
    };

    private SpectatorViewIsolationPackets() {
    }

    static void register(Plugin plugin, SpectatorService service) {
        ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(
                plugin, ListenerPriority.HIGHEST, PLAYER_ENTITY_PACKETS) {
            @Override
            public void onPacketSending(PacketEvent event) {
                try {
                    if (drops(event, service)) {
                        event.setCancelled(true);
                    }
                } catch (Throwable ignored) {
                    // The isolation filter must never break normal packet flow.
                }
            }
        });
    }

    private static boolean drops(PacketEvent event, SpectatorService service) {
        Player viewer = event.getPlayer();
        if (viewer == null || event.isPlayerTemporary() || !service.isSpectating(viewer.getUniqueId())) {
            return false;
        }
        Entity entity;
        try {
            entity = event.getPacket().getEntityModifier(event).readSafely(0);
        } catch (Throwable t) {
            return false; // field layout unknown -> keep the packet
        }
        if (!(entity instanceof Player target)) {
            return false; // only players are blocked; items/crystals/armor stands stay
        }
        if (target.getUniqueId().equals(viewer.getUniqueId())) {
            return false; // your own entity is always yours to see
        }
        // The semi-transparent spectator body — a true spectator of another match/arena or
        // a parked fighter alike — is never rendered to a spectating client.
        return target.getGameMode() == org.bukkit.GameMode.SPECTATOR;
    }
}
