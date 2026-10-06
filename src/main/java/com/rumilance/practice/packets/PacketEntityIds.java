package com.rumilance.practice.packets;

import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerAttachEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityAnimation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEffect;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityHeadLook;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMovement;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMoveAndRotation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRotation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityVelocity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerHurtAnimation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerRemoveEntityEffect;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnPlayer;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateAttributes;
import org.bukkit.entity.Player;

/**
 * Reads the entity an outbound PacketEvents packet is about.
 *
 * <p>Every isolation filter in this plugin (spectator bodies, AFK room walls) needs the same
 * thing: "which entity does this packet describe, and is it a player?". PacketEvents hands out
 * an <em>entity id</em>, not a Bukkit handle, so both halves live here — one place to fix when
 * the library renames a wrapper.</p>
 *
 * <p>Returns {@code -1} / {@code null} rather than throwing: a filter that cannot resolve the
 * entity must keep the packet, never drop it.</p>
 */
public final class PacketEntityIds {

    private PacketEntityIds() {
    }

    /** Entity id carried by this outbound packet, or -1 when it has none (or cannot be read). */
    public static int of(PacketSendEvent event) {
        PacketTypeCommon type = event.getPacketType();
        try {
            if (type == PacketType.Play.Server.SPAWN_PLAYER) {
                return new WrapperPlayServerSpawnPlayer(event).getEntityId();
            }
            if (type == PacketType.Play.Server.SPAWN_ENTITY) {
                return new WrapperPlayServerSpawnEntity(event).getEntityId();
            }
            if (type == PacketType.Play.Server.ENTITY_ANIMATION) {
                return new WrapperPlayServerEntityAnimation(event).getEntityId();
            }
            if (type == PacketType.Play.Server.ATTACH_ENTITY) {
                return new WrapperPlayServerAttachEntity(event).getEntityId();
            }
            if (type == PacketType.Play.Server.ENTITY_EFFECT) {
                return new WrapperPlayServerEntityEffect(event).getEntityId();
            }
            if (type == PacketType.Play.Server.REMOVE_ENTITY_EFFECT) {
                return new WrapperPlayServerRemoveEntityEffect(event).getEntityId();
            }
            if (type == PacketType.Play.Server.ENTITY_EQUIPMENT) {
                return new WrapperPlayServerEntityEquipment(event).getEntityId();
            }
            if (type == PacketType.Play.Server.ENTITY_HEAD_LOOK) {
                return new WrapperPlayServerEntityHeadLook(event).getEntityId();
            }
            if (type == PacketType.Play.Server.ENTITY_ROTATION) {
                return new WrapperPlayServerEntityRotation(event).getEntityId();
            }
            if (type == PacketType.Play.Server.ENTITY_METADATA) {
                return new WrapperPlayServerEntityMetadata(event).getEntityId();
            }
            if (type == PacketType.Play.Server.ENTITY_TELEPORT) {
                return new WrapperPlayServerEntityTeleport(event).getEntityId();
            }
            if (type == PacketType.Play.Server.ENTITY_VELOCITY) {
                return new WrapperPlayServerEntityVelocity(event).getEntityId();
            }
            if (type == PacketType.Play.Server.ENTITY_MOVEMENT) {
                return new WrapperPlayServerEntityMovement(event).getEntityId();
            }
            if (type == PacketType.Play.Server.ENTITY_RELATIVE_MOVE) {
                return new WrapperPlayServerEntityRelativeMove(event).getEntityId();
            }
            if (type == PacketType.Play.Server.ENTITY_RELATIVE_MOVE_AND_ROTATION) {
                return new WrapperPlayServerEntityRelativeMoveAndRotation(event).getEntityId();
            }
            if (type == PacketType.Play.Server.HURT_ANIMATION) {
                return new WrapperPlayServerHurtAnimation(event).getEntityId();
            }
            if (type == PacketType.Play.Server.SET_PASSENGERS) {
                return new WrapperPlayServerSetPassengers(event).getEntityId();
            }
            if (type == PacketType.Play.Server.UPDATE_ATTRIBUTES) {
                return new WrapperPlayServerUpdateAttributes(event).getEntityId();
            }
        } catch (Throwable t) {
            return -1; // unknown layout -> the caller keeps the packet
        }
        return -1;
    }

    /**
     * The player this outbound packet describes, or null. Resolved against the viewer's own
     * world, which is where every packet the viewer receives must live anyway.
     */
    public static Player player(Player viewer, PacketSendEvent event) {
        if (viewer == null) {
            return null;
        }
        int id = of(event);
        if (id < 0) {
            return null;
        }
        for (Player candidate : viewer.getWorld().getPlayers()) {
            if (candidate.getEntityId() == id) {
                return candidate;
            }
        }
        return null;
    }
}
