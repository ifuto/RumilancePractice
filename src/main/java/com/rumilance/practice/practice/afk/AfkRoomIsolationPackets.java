package com.rumilance.practice.practice.afk;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.BlockPosition;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/**
 * ProtocolLib side of {@link AfkRoomIsolation}: every outbound packet that describes something
 * outside the receiver's own AFK room is cancelled before it leaves the server.
 *
 * <ul>
 *   <li><b>block packets</b> — chunk (+light), single/multi block change, block-entity data and
 *       break animations are dropped unless the chunk/position overlaps the room footprint, so a
 *       neighbour's arena 132 blocks away never appears on the client;</li>
 *   <li><b>entity packets</b> — dropped when the entity is not inside the room; packets about
 *       <em>players</em> are dropped unconditionally (both ways: a room owner sees no other
 *       player, and nobody sees a room owner).</li>
 * </ul>
 *
 * <p>Rules are deliberately fail-open when a position cannot be read: dropping a packet we
 * cannot place would blind the player inside their own room, while a leaked neighbour update is
 * only cosmetic. Only class-loaded after the ProtocolLib presence check in the facade.</p>
 */
final class AfkRoomIsolationPackets {

    /** Chunk / block packets: filtered by position. */
    private static final PacketType[] BLOCK_PACKETS = {
            PacketType.Play.Server.MAP_CHUNK,
            PacketType.Play.Server.LIGHT_UPDATE,
            PacketType.Play.Server.BLOCK_CHANGE,
            PacketType.Play.Server.MULTI_BLOCK_CHANGE,
            PacketType.Play.Server.TILE_ENTITY_DATA,
            PacketType.Play.Server.BLOCK_BREAK_ANIMATION,
    };

    /** Entity packets: filtered by the entity's position (players are always dropped). */
    private static final PacketType[] ENTITY_PACKETS = {
            PacketType.Play.Server.SPAWN_ENTITY,
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

    private static final PacketType[] FILTERED = concat(BLOCK_PACKETS, ENTITY_PACKETS);

    private AfkRoomIsolationPackets() {
    }

    private static PacketType[] concat(PacketType[] a, PacketType[] b) {
        PacketType[] all = new PacketType[a.length + b.length];
        System.arraycopy(a, 0, all, 0, a.length);
        System.arraycopy(b, 0, all, a.length, b.length);
        return all;
    }

    static void register(Plugin plugin, AfkRoomIsolationSource source) {
        ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(
                plugin, ListenerPriority.HIGHEST, FILTERED) {
            @Override
            public void onPacketSending(PacketEvent event) {
                try {
                    if (drops(event, source)) {
                        event.setCancelled(true);
                    }
                } catch (Throwable ignored) {
                    // The isolation filter must never break normal packet flow.
                }
            }
        });
    }

    private static boolean drops(PacketEvent event, AfkRoomIsolationSource source) {
        Player receiver = event.getPlayer();
        if (receiver == null || event.isPlayerTemporary()) {
            return false;
        }
        UUID viewerId = receiver.getUniqueId();
        AfkRoomIsolationSource.Room room = source.roomOf(viewerId);
        if (isBlockPacket(event.getPacketType())) {
            // Only room owners are filtered; everybody else sees the world as usual.
            return room != null && !blockInsideRoom(event.getPacket(), event.getPacketType(), room);
        }
        return entityDropped(event, source, receiver, viewerId, room);
    }

    private static boolean isBlockPacket(PacketType type) {
        for (PacketType t : BLOCK_PACKETS) {
            if (t == type) {
                return true;
            }
        }
        return false;
    }

    private static boolean blockInsideRoom(PacketContainer packet, PacketType type,
                                           AfkRoomIsolationSource.Room room) {
        double cx = room.centerX();
        double cz = room.centerZ();
        int radius = room.floorRadius();
        if (type == PacketType.Play.Server.MAP_CHUNK || type == PacketType.Play.Server.LIGHT_UPDATE) {
            // Newer servers keep the position as a ChunkPos, older ones as two plain ints.
            com.comphenix.protocol.wrappers.ChunkCoordIntPair pair =
                    packet.getChunkCoordIntPairs().readSafely(0);
            if (pair != null) {
                return AfkRoomMath.chunkVisible(cx, cz, radius, pair.getChunkX(), pair.getChunkZ());
            }
            Integer chunkX = packet.getIntegers().readSafely(0);
            Integer chunkZ = packet.getIntegers().readSafely(1);
            if (chunkX == null || chunkZ == null) {
                return true;
            }
            return AfkRoomMath.chunkVisible(cx, cz, radius, chunkX, chunkZ);
        }
        if (type == PacketType.Play.Server.MULTI_BLOCK_CHANGE) {
            BlockPosition section = packet.getSectionPositions().readSafely(0);
            if (section == null) {
                return true;
            }
            return AfkRoomMath.sectionVisible(cx, cz, radius, section.getX(), section.getZ());
        }
        BlockPosition pos = packet.getBlockPositionModifier().readSafely(0);
        if (pos == null) {
            return true;
        }
        return AfkRoomMath.positionInside(cx, cz, radius, pos.getX() + 0.5d, pos.getZ() + 0.5d);
    }

    private static boolean entityDropped(PacketEvent event, AfkRoomIsolationSource source,
                                         Player receiver, UUID viewerId,
                                         AfkRoomIsolationSource.Room room) {
        Entity entity;
        try {
            entity = event.getPacket().getEntityModifier(event).readSafely(0);
        } catch (Throwable t) {
            return false; // field layout unknown -> keep the packet
        }
        if (entity instanceof Player target) {
            if (target.getUniqueId().equals(viewerId)) {
                return false; // your own entity is always yours to see
            }
            // Private rooms are single-player: no other player in, and no viewer sees a room
            // owner (belt and braces over Player#hidePlayer).
            return room != null || source.roomOf(target.getUniqueId()) != null;
        }
        if (room == null) {
            return false; // not isolated -> normal visibility
        }
        if (entity == null) {
            Integer id = event.getPacket().getIntegers().readSafely(0);
            return id != null && id != receiver.getEntityId();
        }
        if (!room.world().equals(entity.getWorld())) {
            return true;
        }
        Location loc = entity.getLocation();
        return !AfkRoomMath.positionInside(room.centerX(), room.centerZ(), room.floorRadius(),
                loc.getX(), loc.getZ());
    }
}
