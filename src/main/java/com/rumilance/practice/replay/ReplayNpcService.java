package com.rumilance.practice.replay;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.PlayerInfoData;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Spawns packet-only fake players (client-side NPCs) for the replay viewer. The avatars are real
 * player entities rendered by the client — with the participant's own skin and player body — never
 * armour stands. Everything is per-viewer: only the replay operator sees them, and they never exist
 * on the server (no collision, no interaction, no world edits).
 *
 * <p>This requires ProtocolLib; if it is absent or a packet call fails the service reports itself
 * unavailable and replay playback degrades gracefully (no avatars).</p>
 */
public final class ReplayNpcService {

    /** Handle to a single spawned fake player, scoped to one viewer. */
    public static final class Avatar {
        final int entityId;
        final UUID profileId;
        final String name;

        Avatar(int entityId, UUID profileId, String name) {
            this.entityId = entityId;
            this.profileId = profileId;
            this.name = name;
        }

        public int entityId() {
            return entityId;
        }
    }

    private final Plugin plugin;
    private final Logger logger;
    private ProtocolManager protocol;
    private boolean available;
    // Client-only entity ids are drawn from a high, decreasing range to avoid colliding with real
    // server entity ids.
    private final AtomicInteger nextEntityId = new AtomicInteger(Integer.MAX_VALUE - 4096);

    public ReplayNpcService(Plugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    /** Hook ProtocolLib. Safe to call when ProtocolLib is missing — the service stays disabled. */
    public void init() {
        if (Bukkit.getPluginManager().getPlugin("ProtocolLib") == null) {
            logger.info("[Replay] ProtocolLib not found - replay NPC avatars disabled.");
            return;
        }
        try {
            this.protocol = ProtocolLibrary.getProtocolManager();
            this.available = true;
            logger.info("[Replay] Packet NPC avatars ready (ProtocolLib hooked).");
        } catch (Throwable t) {
            this.available = false;
            logger.log(Level.WARNING, "[Replay] Failed to initialise ProtocolLib NPCs; avatars disabled.", t);
        }
    }

    public boolean isAvailable() {
        return available;
    }

    /**
     * Spawns a fake player for {@code viewer} at {@code loc}. Uses the participant's live skin when
     * they are online (texture profile copied from the real player), otherwise a default skin.
     *
     * @return the avatar handle, or null if the service is unavailable / spawning failed.
     */
    public Avatar spawn(Player viewer, UUID profileId, String name, Location loc) {
        if (!available) {
            return null;
        }
        try {
            int entityId = nextEntityId.getAndDecrement();
            WrappedGameProfile profile = profileFor(profileId, name);

            // 1) Add to the viewer's tab list so the client knows the profile (and loads the skin).
            sendAddPlayer(viewer, profile, name);
            // 2) Spawn the player entity in the world.
            sendSpawn(viewer, entityId, profile.getUUID(), loc);
            // 3) Head/body yaw.
            sendHeadRotation(viewer, entityId, loc.getYaw());

            Avatar avatar = new Avatar(entityId, profileId, name);
            // 4) Remove from the tab list shortly after; the spawned entity keeps skin + nametag.
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                try {
                    sendRemovePlayer(viewer, profile);
                } catch (Throwable ignored) {
                }
            }, 30L);
            return avatar;
        } catch (Throwable t) {
            logger.log(Level.WARNING, "[Replay] Failed to spawn NPC avatar", t);
            return null;
        }
    }

    /** Teleports the fake player to {@code loc} (absolute), updating head yaw. */
    public void teleport(Player viewer, Avatar avatar, Location loc) {
        if (!available || avatar == null) {
            return;
        }
        try {
            com.comphenix.protocol.events.PacketContainer tp =
                    protocol.createPacket(PacketType.Play.Server.ENTITY_TELEPORT);
            tp.getIntegers().write(0, avatar.entityId);
            tp.getDoubles().write(0, loc.getX()).write(1, loc.getY()).write(2, loc.getZ());
            tp.getBytes().write(0, angleToByte(loc.getYaw())).write(1, angleToByte(loc.getPitch()));
            try {
                tp.getBooleans().write(0, false);
            } catch (Throwable ignored) {
                // onGround flag index differs across versions; ignore if absent.
            }
            send(viewer, tp);
            sendHeadRotation(viewer, avatar.entityId, loc.getYaw());
        } catch (Throwable t) {
            logger.log(Level.FINE, "[Replay] NPC teleport failed", t);
        }
    }

    /** Despawns the fake player for the viewer. */
    public void remove(Player viewer, Avatar avatar) {
        if (!available || avatar == null) {
            return;
        }
        try {
            com.comphenix.protocol.events.PacketContainer destroy =
                    protocol.createPacket(PacketType.Play.Server.ENTITY_DESTROY);
            try {
                destroy.getIntLists().write(0, List.of(avatar.entityId));
            } catch (Throwable older) {
                destroy.getIntegerArrays().write(0, new int[]{avatar.entityId});
            }
            send(viewer, destroy);
        } catch (Throwable t) {
            logger.log(Level.FINE, "[Replay] NPC destroy failed", t);
        }
    }

    // ---- packets ----

    private void sendAddPlayer(Player viewer, WrappedGameProfile profile, String name) {
        PlayerInfoData data = new PlayerInfoData(profile, 0,
                EnumWrappers.NativeGameMode.SURVIVAL, WrappedChatComponent.fromText(name));
        List<PlayerInfoData> list = new ArrayList<>();
        list.add(data);
        sendPlayerInfo(viewer, EnumWrappers.PlayerInfoAction.ADD_PLAYER, list);
    }

    private void sendRemovePlayer(Player viewer, WrappedGameProfile profile) {
        PlayerInfoData data = new PlayerInfoData(profile, 0,
                EnumWrappers.NativeGameMode.SURVIVAL, null);
        List<PlayerInfoData> list = new ArrayList<>();
        list.add(data);
        sendPlayerInfo(viewer, EnumWrappers.PlayerInfoAction.REMOVE_PLAYER, list);
    }

    /**
     * 1.20.5+ carries an EnumSet of actions on the Player Info Update packet; older servers
     * have a single action field. Try the modern accessor first, fall back to the legacy one.
     */
    private void sendPlayerInfo(Player viewer, EnumWrappers.PlayerInfoAction action,
                                List<PlayerInfoData> list) {
        com.comphenix.protocol.events.PacketContainer packet =
                protocol.createPacket(PacketType.Play.Server.PLAYER_INFO);
        try {
            packet.getPlayerInfoActions().write(0, java.util.EnumSet.of(action));
        } catch (Throwable legacy) {
            packet.getPlayerInfoAction().write(0, action);
        }
        try {
            packet.getPlayerInfoDataLists().write(0, list);
        } catch (Throwable alternateIndex) {
            // Some ProtocolLib revisions expose the data list at index 1 behind the action set.
            packet.getPlayerInfoDataLists().write(1, list);
        }
        send(viewer, packet);
    }

    /**
     * Spawns the player entity. Since 1.20.2 the dedicated Spawn Player packet was merged into
     * Spawn Entity ({@code NAMED_ENTITY_SPAWN} no longer exists in the protocol), so the modern
     * packet is sent first with the player entity type; the legacy packet remains as a fallback
     * for older server/ProtocolLib combinations.
     */
    private void sendSpawn(Player viewer, int entityId, UUID profileUuid, Location loc) {
        try {
            com.comphenix.protocol.events.PacketContainer packet =
                    protocol.createPacket(PacketType.Play.Server.SPAWN_ENTITY);
            packet.getIntegers().write(0, entityId);
            packet.getUUIDs().write(0, profileUuid);
            packet.getEntityTypeModifier().write(0, org.bukkit.entity.EntityType.PLAYER);
            packet.getDoubles().write(0, loc.getX()).write(1, loc.getY()).write(2, loc.getZ());
            // Rotations are packed angles: pitch, yaw, head yaw.
            packet.getBytes().write(0, angleToByte(loc.getPitch()))
                    .write(1, angleToByte(loc.getYaw()))
                    .write(2, angleToByte(loc.getYaw()));
            // Data field: 0 for players. Velocity shorts default to zero already.
            packet.getIntegers().writeSafely(1, 0);
            send(viewer, packet);
        } catch (Throwable modernFailed) {
            logger.log(Level.FINE, "[Replay] SPAWN_ENTITY failed, trying legacy spawn", modernFailed);
            com.comphenix.protocol.events.PacketContainer packet =
                    protocol.createPacket(PacketType.Play.Server.NAMED_ENTITY_SPAWN);
            packet.getIntegers().write(0, entityId);
            packet.getUUIDs().write(0, profileUuid);
            packet.getDoubles().write(0, loc.getX()).write(1, loc.getY()).write(2, loc.getZ());
            packet.getBytes().write(0, angleToByte(loc.getYaw())).write(1, angleToByte(loc.getPitch()));
            send(viewer, packet);
        }
    }

    private void sendHeadRotation(Player viewer, int entityId, float yaw) {
        com.comphenix.protocol.events.PacketContainer packet =
                protocol.createPacket(PacketType.Play.Server.ENTITY_HEAD_ROTATION);
        packet.getIntegers().write(0, entityId);
        packet.getBytes().write(0, angleToByte(yaw));
        send(viewer, packet);
    }

    private void send(Player viewer, com.comphenix.protocol.events.PacketContainer packet) {
        try {
            protocol.sendServerPacket(viewer, packet);
        } catch (Throwable t) {
            logger.log(Level.FINE, "[Replay] packet send failed", t);
        }
    }

    private WrappedGameProfile profileFor(UUID profileId, String name) {
        Player online = Bukkit.getPlayer(profileId);
        if (online != null) {
            // Copy the real, skin-bearing profile of the live participant.
            return WrappedGameProfile.fromPlayer(online);
        }
        // Offline: a profile with the real UUID + name but default skin (no fetched texture).
        // Use ProtocolLib's own wrapper (no direct authlib dependency, which is not on the
        // compile classpath).
        return new WrappedGameProfile(profileId, name == null ? "Replay" : name);
    }

    private static byte angleToByte(float angle) {
        return (byte) Math.floor(angle * 256.0F / 360.0F);
    }
}
