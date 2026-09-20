package com.rumilance.practice.practice.afk;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.rumilance.practice.practice.afk.AfkCrystalManager.AfkRegion;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.BoundingBox;

import java.util.UUID;
import java.util.function.Function;
import java.util.logging.Level;

/**
 * ProtocolLib packet isolation for the AFK rooms (AFK BOT Crystal + AFK practice). Every player
 * gets their own private arena carved far out in the void; the arenas sit close enough that a
 * neighbour's live block edits, entities and player body could otherwise stream into an AFK
 * player's client. The moment a player starts an AFK session, this listener drops every
 * outbound world packet whose position falls OUTSIDE that player's own arena region — block
 * updates, multi-block section edits and entity/player spawns from any other arena are simply
 * never sent (afkcし始めたら自分の範囲内以外のブロックのパケット・プレイヤーのパケットは遮断される).
 *
 * <p>Everything degrades gracefully: without ProtocolLib the listener is never created (the
 * bootstrap guards on the plugin being present), and any packet whose position cannot be
 * resolved on this server version is left untouched rather than risking the player's own view.
 * Packets to non-AFK players are never inspected.</p>
 */
public final class AfkPacketIsolator {

    private final Plugin plugin;
    private final ProtocolManager protocol;
    // Resolves the querying player's own arena region, or null when they are not in a session.
    private final Function<UUID, AfkRegion> regionResolver;
    private boolean registered;

    public AfkPacketIsolator(Plugin plugin, Function<UUID, AfkRegion> regionResolver) {
        this.plugin = plugin;
        this.protocol = ProtocolLibrary.getProtocolManager();
        this.regionResolver = regionResolver;
    }

    /** Registers the outbound packet listener. Safe to call once. */
    public void start() {
        if (registered) {
            return;
        }
        registered = true;
        protocol.addPacketListener(new PacketAdapter(plugin, ListenerPriority.NORMAL,
                PacketType.Play.Server.BLOCK_CHANGE,
                PacketType.Play.Server.MULTI_BLOCK_CHANGE,
                PacketType.Play.Server.BLOCK_ACTION,
                PacketType.Play.Server.SPAWN_ENTITY,
                PacketType.Play.Server.NAMED_ENTITY_SPAWN,
                PacketType.Play.Server.SPAWN_ENTITY_EXPERIENCE_ORB) {
            @Override
            public void onPacketSending(PacketEvent event) {
                handle(event);
            }
        });
        plugin.getLogger().info("[AFK] Packet isolation active (ProtocolLib) — AFK players only "
                + "see their own arena.");
    }

    private void handle(PacketEvent event) {
        Player viewer = event.getPlayer();
        if (viewer == null) {
            return;
        }
        AfkRegion region = regionResolver.apply(viewer.getUniqueId());
        if (region == null) {
            return; // not in an AFK session — normal streaming
        }
        // A packet from a different world is definitionally outside the arena.
        if (viewer.getWorld() != null && !viewer.getWorld().getUID().equals(region.worldId())) {
            event.setCancelled(true);
            return;
        }
        try {
            PacketType type = event.getPacketType();
            if (type == PacketType.Play.Server.BLOCK_CHANGE
                    || type == PacketType.Play.Server.BLOCK_ACTION) {
                BlockPosition pos = event.getPacket().getBlockPositionModifier().readSafely(0);
                if (pos != null && outside(region.bounds(), pos.getX(), pos.getY(), pos.getZ())) {
                    event.setCancelled(true);
                }
            } else if (type == PacketType.Play.Server.MULTI_BLOCK_CHANGE) {
                if (multiBlockOutside(event.getPacket(), region.bounds())) {
                    event.setCancelled(true);
                }
            } else {
                // Entity / player spawns carry an absolute double position.
                Double x = event.getPacket().getDoubles().readSafely(0);
                Double y = event.getPacket().getDoubles().readSafely(1);
                Double z = event.getPacket().getDoubles().readSafely(2);
                if (x != null && y != null && z != null && outside(region.bounds(), x, y, z)) {
                    event.setCancelled(true);
                }
            }
        } catch (Throwable t) {
            // Never break the viewer's own arena view over an unknown packet shape.
            if (plugin.getLogger().isLoggable(Level.FINE)) {
                plugin.getLogger().log(Level.FINE, "[AFK] packet isolation skipped a packet", t);
            }
        }
    }

    /**
     * A MULTI_BLOCK_CHANGE carries a whole 16x16x16 chunk-section of edits. If that entire
     * section lies outside the arena bounds, the packet is dropped. If it overlaps the arena
     * it is kept (the arena's own edits must always reach the client).
     */
    private boolean multiBlockOutside(PacketContainer packet, BoundingBox bounds) {
        BlockPosition section;
        try {
            section = packet.getSectionPositions().readSafely(0);
        } catch (Throwable t) {
            return false; // shape unknown on this version — keep the packet
        }
        if (section == null) {
            return false;
        }
        int minX = section.getX() << 4;
        int minY = section.getY() << 4;
        int minZ = section.getZ() << 4;
        BoundingBox sectionBox = new BoundingBox(minX, minY, minZ, minX + 16, minY + 16, minZ + 16);
        return !bounds.overlaps(sectionBox);
    }

    private static boolean outside(BoundingBox bounds, double x, double y, double z) {
        return !bounds.contains(x, y, z);
    }

    /** Convenience: register only when ProtocolLib is installed. */
    public static AfkPacketIsolator createIfAvailable(Plugin plugin,
                                                      Function<UUID, AfkRegion> regionResolver) {
        if (Bukkit.getPluginManager().getPlugin("ProtocolLib") == null) {
            return null;
        }
        try {
            AfkPacketIsolator isolator = new AfkPacketIsolator(plugin, regionResolver);
            isolator.start();
            return isolator;
        } catch (LinkageError | RuntimeException e) {
            plugin.getLogger().log(Level.WARNING,
                    "[AFK] ProtocolLib detected but packet isolation failed to initialise; "
                            + "AFK rooms fall back to entity-visibility hiding only.", e);
            return null;
        }
    }
}
