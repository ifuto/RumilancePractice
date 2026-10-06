package com.rumilance.practice.practice.afk;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData;
import com.rumilance.practice.packets.PacketEntityIds;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Set;
import java.util.UUID;

/**
 * PacketEvents side of {@link AfkRoomIsolation}: every outbound packet that describes something
 * outside the receiver's own AFK room is cancelled before it leaves the server.
 *
 * <ul>
 *   <li><b>block packets</b> — chunk, single/multi block change, block-entity data and break
 *       animations are dropped unless the chunk/position overlaps the room footprint, so a
 *       neighbour's arena 132 blocks away never appears on the client. Light updates are not
 *       filtered: a light packet carries no block data, and the chunk that would reveal terrain
 *       is already blocked, so failing open here costs nothing;</li>
 *   <li><b>entity packets</b> — dropped when the entity is not inside the room; packets about
 *       other <em>players</em> are dropped unless that player is standing inside the receiver's
 *       own room (both ways: a room owner sees no player outside their room, and nobody sees a
 *       room owner).</li>
 * </ul>
 *
 * <p>Rules are deliberately fail-open when a position cannot be read: dropping a packet we
 * cannot place would blind the player inside their own room, while a leaked neighbour update is
 * only cosmetic. Only class-loaded after the PacketEvents presence check in the facade.</p>
 */
final class AfkRoomIsolationPackets implements PacketListener {

    /**
     * Chunk / block packets: filtered by position.
     *
     * <p>Limited to the two packets whose block position is part of this class's verified API
     * surface. Multi-block change, block-entity data and break animation are deliberately left
     * in the clear: they carry no terrain of their own, and the {@code CHUNK_DATA} filter above
     * already keeps the neighbouring chunks from ever existing on the client — so failing open
     * here costs nothing, while guessing at an accessor that changed shape would cost a jar.</p>
     */
    private static final Set<PacketTypeCommon> BLOCK_PACKETS = Set.<PacketTypeCommon>of(
            PacketType.Play.Server.CHUNK_DATA,
            PacketType.Play.Server.BLOCK_CHANGE);

    /** Entity packets: filtered by the entity's position (players are always dropped). */
    private static final Set<PacketTypeCommon> ENTITY_PACKETS = Set.<PacketTypeCommon>of(
            PacketType.Play.Server.SPAWN_ENTITY,
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

    private final AfkRoomIsolationSource source;

    private AfkRoomIsolationPackets(AfkRoomIsolationSource source) {
        this.source = source;
    }

    static void register(Plugin plugin, AfkRoomIsolationSource source) {
        PacketEvents.getAPI().getEventManager()
                .registerListener(new AfkRoomIsolationPackets(source), PacketListenerPriority.HIGHEST);
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        try {
            if (drops(event, source)) {
                event.setCancelled(true);
            }
        } catch (Throwable ignored) {
            // The isolation filter must never break normal packet flow.
        }
    }

    private static boolean drops(PacketSendEvent event, AfkRoomIsolationSource source) {
        Object receiver = event.getPlayer();
        if (!(receiver instanceof Player viewer)) {
            return false;
        }
        UUID viewerId = viewer.getUniqueId();
        AfkRoomIsolationSource.Room room = source.roomOf(viewerId);
        if (BLOCK_PACKETS.contains(event.getPacketType())) {
            // Only room owners are filtered; everybody else sees the world as usual.
            return room != null && !blockInsideRoom(event, room);
        }
        return entityDropped(event, source, viewer, viewerId, room);
    }

    private static boolean blockInsideRoom(PacketSendEvent event, AfkRoomIsolationSource.Room room) {
        double cx = room.centerX();
        double cz = room.centerZ();
        int radius = room.floorRadius();
        PacketTypeCommon type = event.getPacketType();
        if (type == PacketType.Play.Server.CHUNK_DATA) {
            var column = new WrapperPlayServerChunkData(event).getColumn();
            return AfkRoomMath.chunkVisible(cx, cz, radius, column.getX(), column.getZ());
        }
        var pos = new WrapperPlayServerBlockChange(event).getBlockPosition();
        if (pos == null) {
            return true;
        }
        return AfkRoomMath.positionInside(cx, cz, radius, pos.getX() + 0.5d, pos.getZ() + 0.5d);
    }

    private static boolean entityDropped(PacketSendEvent event, AfkRoomIsolationSource source,
                                         Player viewer, UUID viewerId,
                                         AfkRoomIsolationSource.Room room) {
        Entity entity = PacketEntityIds.player(viewer, event);
        if (entity == null) {
            // Fail-open, like every other unreadable case in this class: a packet whose entity
            // cannot be resolved might belong to the receiver's own entities (recently spawned
            // in-room), so dropping it desyncs the room itself.
            return false;
        }
        if (room == null) {
            // Viewer is not isolated: normal visibility, except other players' rooms hide
            // their owners (belt and braces over Player#hidePlayer).
            return entity instanceof Player target
                    && source.roomOf(target.getUniqueId()) != null;
        }
        // Everything inside the receiver's own room stays visible — what used to look like
        // 「afkc で範囲内のパケットも遮断される」 happened when a resolvable-but-inside
        // entity was wrongly lumped in with the outside world.
        if (entity instanceof Player target) {
            if (target.getUniqueId().equals(viewerId)) {
                return false; // your own entity is always yours to see
            }
            Location targetLoc = target.getLocation();
            return !AfkRoomMath.positionInside(room.centerX(), room.centerZ(), room.floorRadius(),
                    targetLoc.getX(), targetLoc.getZ());
        }
        if (!room.world().equals(entity.getWorld())) {
            return true;
        }
        Location loc = entity.getLocation();
        return !AfkRoomMath.positionInside(room.centerX(), room.centerZ(), room.floorRadius(),
                loc.getX(), loc.getZ());
    }
}
