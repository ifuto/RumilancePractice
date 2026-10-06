package com.rumilance.practice.spectator;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.rumilance.practice.packets.PacketEntityIds;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Set;

/**
 * PacketEvents side of {@link SpectatorViewIsolation}: while the receiver is spectating, every
 * outbound entity packet that describes another player in spectator gamemode is cancelled before
 * it leaves the server. The player-info packets that build the TAB entry are deliberately not in
 * the filter, so spectators stay listed in TAB while their bodies never render (user spec
 * 2026-10-04).
 *
 * <p>Fail-open like every isolation filter in this plugin: a packet whose entity cannot be
 * resolved is kept — dropping it might blind the viewer to their own spectated fighter.</p>
 */
final class SpectatorViewIsolationPackets implements PacketListener {

    /**
     * Entity packets that describe a player. Players spawn via {@code SPAWN_PLAYER} (never
     * {@code SPAWN_ENTITY}, which is for non-living/display entities), so the spawn cancel and
     * the follow-up state packets form the complete set: movement, rotation, look, metadata,
     * equipment, effects, attributes, animations and velocity.
     */
    private static final Set<PacketType.Play.Server> PLAYER_ENTITY_PACKETS = Set.of(
            PacketType.Play.Server.SPAWN_PLAYER,
            PacketType.Play.Server.ENTITY_ANIMATION,
            PacketType.Play.Server.ATTACH_ENTITY,
            PacketType.Play.Server.ENTITY_EFFECT,
            PacketType.Play.Server.ENTITY_EQUIPMENT,
            PacketType.Play.Server.ENTITY_HEAD_LOOK,
            PacketType.Play.Server.ENTITY_ROTATION,
            PacketType.Play.Server.ENTITY_METADATA,
            PacketType.Play.Server.ENTITY_TELEPORT,
            PacketType.Play.Server.ENTITY_VELOCITY,
            PacketType.Play.Server.ENTITY_MOVEMENT,
            PacketType.Play.Server.ENTITY_RELATIVE_MOVE,
            PacketType.Play.Server.ENTITY_RELATIVE_MOVE_AND_ROTATION,
            PacketType.Play.Server.HURT_ANIMATION,
            PacketType.Play.Server.REMOVE_ENTITY_EFFECT,
            PacketType.Play.Server.UPDATE_ATTRIBUTES);

    private final SpectatorService service;

    private SpectatorViewIsolationPackets(SpectatorService service) {
        this.service = service;
    }

    static void register(Plugin plugin, SpectatorService service) {
        PacketEvents.getAPI().getEventManager()
                .registerListener(new SpectatorViewIsolationPackets(service),
                        PacketListenerPriority.HIGHEST);
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        try {
            if (drops(event, service)) {
                event.setCancelled(true);
            }
        } catch (Throwable ignored) {
            // The isolation filter must never break normal packet flow.
        }
    }

    private static boolean drops(PacketSendEvent event, SpectatorService service) {
        if (!PLAYER_ENTITY_PACKETS.contains(event.getPacketType())) {
            return false;
        }
        Object receiver = event.getPlayer();
        if (!(receiver instanceof Player viewer)) {
            return false;
        }
        if (!service.isSpectating(viewer.getUniqueId())) {
            return false;
        }
        Player target = PacketEntityIds.player(viewer, event);
        if (target == null) {
            return false; // unresolvable -> keep the packet (also covers items, crystals, stands)
        }
        if (target.getUniqueId().equals(viewer.getUniqueId())) {
            return false; // your own entity is always yours to see
        }
        // The semi-transparent spectator body — a true spectator of another match/arena or a
        // parked fighter alike — is never rendered to a spectating client.
        return target.getGameMode() == org.bukkit.GameMode.SPECTATOR;
    }
}
