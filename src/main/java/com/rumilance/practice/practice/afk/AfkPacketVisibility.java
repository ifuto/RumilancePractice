package com.rumilance.practice.practice.afk;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.BlockPosition;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Per-viewer packet isolation for AFK rooms.
 *
 * <p>{@link Player#hidePlayer(Plugin, Player)} is useful for the entity lifecycle, but it does
 * not stop chunk/block packets. This listener closes that gap: while a player is in AFKC, block
 * packets for chunks/positions outside that player's own 100x100 room are cancelled, and
 * outgoing player-entity packets for players outside that room are cancelled. The listener is
 * optional and is only installed when ProtocolLib is present.</p>
 */
public final class AfkPacketVisibility {

    /** Horizontal block range visible to one AFK viewer. */
    public record Area(UUID worldId, int minX, int maxX, int minZ, int maxZ) {
        boolean contains(int x, int z) {
            return x >= minX && x < maxX && z >= minZ && z < maxZ;
        }

        boolean intersectsChunk(int chunkX, int chunkZ) {
            int chunkMinX = chunkX << 4;
            int chunkMinZ = chunkZ << 4;
            int chunkMaxX = chunkMinX + 15;
            int chunkMaxZ = chunkMinZ + 15;
            return chunkMaxX >= minX && chunkMinX < maxX
                    && chunkMaxZ >= minZ && chunkMinZ < maxZ;
        }
    }

    private final Plugin plugin;
    private final AfkCrystalManager sessions;
    private ProtocolManager protocol;
    private PacketAdapter listener;
    private boolean enabled;

    public AfkPacketVisibility(Plugin plugin, AfkCrystalManager sessions) {
        this.plugin = plugin;
        this.sessions = sessions;
    }

    public void start() {
        if (Bukkit.getPluginManager().getPlugin("ProtocolLib") == null) {
            plugin.getLogger().info("[AFKC] ProtocolLib not found; packet room isolation is unavailable.");
            return;
        }
        try {
            protocol = ProtocolLibrary.getProtocolManager();
            listener = new PacketAdapter(plugin, ListenerPriority.HIGHEST,
                    PacketType.Play.Server.MAP_CHUNK,
                    PacketType.Play.Server.BLOCK_CHANGE,
                    PacketType.Play.Server.MULTI_BLOCK_CHANGE,
                    PacketType.Play.Server.BLOCK_ACTION,
                    PacketType.Play.Server.BLOCK_BREAK_ANIMATION,
                    PacketType.Play.Server.TILE_ENTITY_DATA,
                    PacketType.Play.Server.NAMED_ENTITY_SPAWN,
                    PacketType.Play.Server.SPAWN_ENTITY,
                    PacketType.Play.Server.ENTITY_TELEPORT,
                    PacketType.Play.Server.REL_ENTITY_MOVE,
                    PacketType.Play.Server.REL_ENTITY_MOVE_LOOK,
                    PacketType.Play.Server.ENTITY_LOOK,
                    PacketType.Play.Server.ENTITY_HEAD_ROTATION,
                    PacketType.Play.Server.ENTITY_METADATA,
                    PacketType.Play.Server.ENTITY_EQUIPMENT) {
                @Override
                public void onPacketSending(PacketEvent event) {
                    filter(event);
                }
            };
            protocol.addPacketListener(listener);
            enabled = true;
            plugin.getLogger().info("[AFKC] ProtocolLib packet isolation enabled.");
        } catch (Throwable error) {
            enabled = false;
            plugin.getLogger().log(Level.WARNING,
                    "[AFKC] Failed to install ProtocolLib packet isolation; Bukkit visibility fallback remains active.",
                    error);
        }
    }

    public void stop() {
        if (protocol != null && listener != null) {
            try {
                protocol.removePacketListener(listener);
            } catch (Throwable ignored) {
            }
        }
        enabled = false;
        listener = null;
        protocol = null;
    }

    public boolean isEnabled() {
        return enabled;
    }

    private void filter(PacketEvent event) {
        Player viewer = event.getPlayer();
        if (viewer == null) {
            return;
        }
        Optional<Area> area = sessions.packetArea(viewer.getUniqueId());
        if (area.isEmpty() || !area.get().worldId().equals(viewer.getWorld().getUID())) {
            return;
        }
        PacketContainer packet = event.getPacket();
        PacketType type = event.getPacketType();
        Area room = area.get();
        try {
            if (type == PacketType.Play.Server.MAP_CHUNK) {
                int chunkX = packet.getIntegers().read(0);
                int chunkZ = packet.getIntegers().read(1);
                if (!room.intersectsChunk(chunkX, chunkZ)) {
                    event.setCancelled(true);
                }
                return;
            }
            if (isBlockPacket(type)) {
                BlockPosition position = blockPosition(packet, type);
                if (position == null || !room.contains(position.getX(), position.getZ())) {
                    event.setCancelled(true);
                }
                return;
            }
            // Only player entity packets are filtered. The AFK bot mannequin and particles in
            // the viewer's own room continue to arrive normally.
            if (isPlayerEntityPacket(type)) {
                int entityId = packet.getIntegers().read(0);
                Player target = findPlayer(viewer, entityId);
                if (target != null && (!room.worldId().equals(target.getWorld().getUID())
                        || !room.contains(target.getLocation().getBlockX(), target.getLocation().getBlockZ()))) {
                    event.setCancelled(true);
                }
            }
        } catch (Throwable error) {
            // A packet shape can change between ProtocolLib/server versions. Fail closed for
            // unknown block/player packets rather than leaking another AFK room to the viewer.
            if (isBlockPacket(type) || isPlayerEntityPacket(type)) {
                event.setCancelled(true);
            }
        }
    }

    private static boolean isBlockPacket(PacketType type) {
        return type == PacketType.Play.Server.BLOCK_CHANGE
                || type == PacketType.Play.Server.MULTI_BLOCK_CHANGE
                || type == PacketType.Play.Server.BLOCK_ACTION
                || type == PacketType.Play.Server.BLOCK_BREAK_ANIMATION
                || type == PacketType.Play.Server.TILE_ENTITY_DATA;
    }

    private static boolean isPlayerEntityPacket(PacketType type) {
        return type == PacketType.Play.Server.NAMED_ENTITY_SPAWN
                || type == PacketType.Play.Server.SPAWN_ENTITY
                || type == PacketType.Play.Server.ENTITY_TELEPORT
                || type == PacketType.Play.Server.REL_ENTITY_MOVE
                || type == PacketType.Play.Server.REL_ENTITY_MOVE_LOOK
                || type == PacketType.Play.Server.ENTITY_LOOK
                || type == PacketType.Play.Server.ENTITY_HEAD_ROTATION
                || type == PacketType.Play.Server.ENTITY_METADATA
                || type == PacketType.Play.Server.ENTITY_EQUIPMENT;
    }

    private static BlockPosition blockPosition(PacketContainer packet, PacketType type) {
        if (type == PacketType.Play.Server.MULTI_BLOCK_CHANGE) {
            // ProtocolLib represents the section coordinate as a BlockPosition. Its X/Z are
            // chunk coordinates, so convert them to a representative block in that chunk.
            BlockPosition section = packet.getSectionPositions().read(0);
            return section == null ? null : new BlockPosition(section.getX() << 4, 0, section.getZ() << 4);
        }
        return packet.getBlockPositionModifier().read(0);
    }

    private static Player findPlayer(Player viewer, int entityId) {
        for (Player player : viewer.getWorld().getPlayers()) {
            if (player.getEntityId() == entityId) {
                return player;
            }
        }
        return null;
    }
}
