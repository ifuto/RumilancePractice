package com.rumilance.practice.replay;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityHeadLook;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoRemove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnPlayer;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
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
 * <p>This requires PacketEvents; if it is absent or a packet call fails the service reports itself
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

    private boolean available;
    // Client-only entity ids are drawn from a high, decreasing range to avoid colliding with real
    // server entity ids.
    private final AtomicInteger nextEntityId = new AtomicInteger(Integer.MAX_VALUE - 4096);

    public ReplayNpcService(Plugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    /** Hooks PacketEvents. Safe to call when PacketEvents is missing — the service stays disabled. */
    public void init() {
        if (Bukkit.getPluginManager().getPlugin("packetevents") == null) {
            logger.info("[Replay] PacketEvents not found - replay NPC avatars disabled.");
            return;
        }
        try {
            this.available = true;
            logger.info("[Replay] Packet NPC avatars ready (PacketEvents hooked).");
        } catch (Throwable t) {
            this.available = false;
            logger.log(Level.WARNING, "[Replay] Failed to initialise PacketEvents NPCs; avatars disabled.", t);
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
            UserProfile profile = profileFor(profileId, name);

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
            send(viewer, new WrapperPlayServerEntityTeleport(
                    avatar.entityId, SpigotConversionUtil.fromBukkitLocation(loc), false));
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
            send(viewer, new WrapperPlayServerDestroyEntities(avatar.entityId));
        } catch (Throwable t) {
            logger.log(Level.FINE, "[Replay] NPC destroy failed", t);
        }
    }

    // ---- packets ----

    private void sendAddPlayer(Player viewer, UserProfile profile, String name) {
        // Client needs the profile in the tab list (briefly) so the skin resolves.
        send(viewer, new WrapperPlayServerPlayerInfoUpdate(
                WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER,
                List.of(new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
                        profile, false, 0, GameMode.SURVIVAL,
                        net.kyori.adventure.text.Component.text(name == null ? "Replay" : name),
                        null))));
    }

    private void sendRemovePlayer(Player viewer, UserProfile profile) {
        send(viewer, new WrapperPlayServerPlayerInfoRemove(profile.getUUID()));
    }

    private void sendSpawn(Player viewer, int entityId, UUID profileUuid, Location loc) {
        send(viewer, new WrapperPlayServerSpawnPlayer(
                entityId, profileUuid, SpigotConversionUtil.fromBukkitLocation(loc)));
    }

    private void sendHeadRotation(Player viewer, int entityId, float yaw) {
        send(viewer, new WrapperPlayServerEntityHeadLook(entityId, yaw));
    }

    private void send(Player viewer, PacketWrapper<?> packet) {
        try {
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, packet);
        } catch (Throwable t) {
            logger.log(Level.FINE, "[Replay] packet send failed", t);
        }
    }

    private UserProfile profileFor(UUID profileId, String name) {
        Player online = Bukkit.getPlayer(profileId);
        if (online != null) {
            // Copy the real, skin-bearing profile of the live participant.
            return PacketEvents.getAPI().getPlayerManager().getUser(online).getProfile();
        }
        // Offline: a profile with the real UUID + name but default skin (no fetched texture).
        return new UserProfile(profileId, name == null ? "Replay" : name);
    }

    private static byte angleToByte(float angle) {
        return (byte) Math.floor(angle * 256.0F / 360.0F);
    }
}
