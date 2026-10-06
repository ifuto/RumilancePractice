package com.rumilance.practice.sight;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData;
import com.rumilance.practice.util.Cuboid;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * While a player is inside an FFA arena, chunk packets for chunks OUTSIDE the FFA region are
 * cancelled and remembered; when the player leaves the FFA, those chunks are refreshed so the
 * surroundings reappear. Inside the arena the vanilla chunk flow is untouched.
 *
 * <p>This is the strict version of 「FFAの外は見えない」: the per-player border + send-view
 * distance from {@link ViewControlService} already stops most surrounding terrain from being
 * sent, but a player standing at an arena edge can still receive neighbouring chunks (FFA
 * zones sit close together). This listener blocks those packets at the wire level.</p>
 *
 * <p>Built on PacketEvents (soft dependency): the bootstrap only constructs this service when
 * the plugin is present, so without it the feature silently stays off.</p>
 *
 * <p>The blocked chunks are remembered by coordinate rather than by cloning the packet: on
 * reveal the server regenerates and re-sends them with {@link World#refreshChunk(int, int)},
 * which always carries the current block data — a replayed snapshot could be stale.</p>
 */
public final class FfaChunkMaskService implements Listener, PacketListener {

    /** Safety cap on remembered chunks per player. */
    private static final int MAX_CACHED_PER_PLAYER = 6000;

    private final Plugin plugin;
    private final Map<UUID, Mask> masks = new ConcurrentHashMap<>();
    private final Map<UUID, List<long[]>> held = new ConcurrentHashMap<>();

    /** Per-player mask: the FFA cuboid the player currently occupies, in the arena's world. */
    private record Mask(World world, Cuboid region) {
        boolean coversChunk(int chunkX, int chunkZ) {
            long minBlockX = chunkX * 16L;
            long maxBlockX = minBlockX + 15L;
            long minBlockZ = chunkZ * 16L;
            long maxBlockZ = minBlockZ + 15L;
            return maxBlockX >= region.minX() && minBlockX <= region.maxX()
                    && maxBlockZ >= region.minZ() && minBlockZ <= region.maxZ();
        }
    }

    public FfaChunkMaskService(Plugin plugin) {
        this.plugin = plugin;
        PacketEvents.getAPI().getEventManager()
                .registerListener(this, PacketListenerPriority.LOWEST);
    }

    /** Registers the quit hook; call once from the bootstrap after construction. */
    public void init() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() != PacketType.Play.Server.CHUNK_DATA) {
            return;
        }
        Object receiver = event.getPlayer();
        if (!(receiver instanceof Player player)) {
            return;
        }
        Mask mask = masks.get(player.getUniqueId());
        if (mask == null || !player.getWorld().equals(mask.world())) {
            return;
        }
        int chunkX;
        int chunkZ;
        try {
            WrapperPlayServerChunkData chunk = new WrapperPlayServerChunkData(event);
            chunkX = chunk.getChunkX();
            chunkZ = chunk.getChunkZ();
        } catch (Throwable t) {
            return; // Never break the chunk pipeline over a read failure.
        }
        if (mask.coversChunk(chunkX, chunkZ)) {
            return;
        }
        if (player.hasPermission("rumilance.admin")) {
            return; // Admins keep full sight, same as ViewControlService.
        }
        List<long[]> cache = held.computeIfAbsent(player.getUniqueId(), id -> new ArrayList<>());
        synchronized (cache) {
            if (cache.size() < MAX_CACHED_PER_PLAYER) {
                cache.add(new long[]{chunkX, chunkZ});
            }
        }
        event.setCancelled(true);
    }

    /** Starts masking {@code player}'s view to {@code region} (their FFA arena). */
    public void mask(Player player, Cuboid region) {
        if (player == null || region == null || region.world() == null) {
            return;
        }
        masks.put(player.getUniqueId(), new Mask(region.world(), region));
    }

    /**
     * Stops masking and refreshes every chunk that was cancelled while masked, so the terrain
     * around the FFA reappears immediately. Safe to call for unmasked players.
     */
    public void reveal(UUID playerId) {
        if (playerId == null) {
            return;
        }
        masks.remove(playerId);
        List<long[]> cache = held.remove(playerId);
        if (cache == null || cache.isEmpty()) {
            return;
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return; // Offline: nothing to refresh, the list is dropped.
        }
        World world = player.getWorld();
        synchronized (cache) {
            for (long[] coords : cache) {
                try {
                    world.refreshChunk((int) coords[0], (int) coords[1]);
                } catch (Throwable ignored) {
                    // A failure on one chunk must not stop the rest.
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        masks.remove(id);
        held.remove(id); // No refresh for a disconnecting client.
    }
}
