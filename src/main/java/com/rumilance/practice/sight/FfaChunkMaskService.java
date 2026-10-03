package com.rumilance.practice.sight;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
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
 * cancelled and cached; when the player leaves the FFA, the cached packets are replayed so the
 * surroundings reappear exactly as the server originally rendered them. Inside the arena the
 * vanilla chunk flow is untouched.
 *
 * <p>This is the strict version of "FFAの外は見えない": the per-player border + send-view
 * distance from {@link ViewControlService} already stops most surrounding terrain from being
 * sent, but a player standing at an arena edge can still receive neighbouring chunks (FFA
 * zones sit close together). This listener blocks those packets at the wire level.</p>
 *
 * <p>Requires ProtocolLib (soft dependency): the bootstrap only constructs this service when
 * the plugin is present, so without it the feature silently stays off.</p>
 */
public final class FfaChunkMaskService implements Listener {

    /** Safety cap on cached packets per player (~ a full 32-radius view is ~4k; be generous). */
    private static final int MAX_CACHED_PER_PLAYER = 6000;

    private final Plugin plugin;
    private final ProtocolManager protocol;
    private final Map<UUID, Mask> masks = new ConcurrentHashMap<>();
    private final Map<UUID, List<PacketContainer>> held = new ConcurrentHashMap<>();

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
        this.protocol = ProtocolLibrary.getProtocolManager();
        protocol.addPacketListener(new PacketAdapter(plugin, ListenerPriority.LOWEST,
                PacketType.Play.Server.MAP_CHUNK) {
            @Override
            public void onPacketSending(PacketEvent event) {
                Player player = event.getPlayer();
                if (player == null) {
                    return;
                }
                Mask mask = masks.get(player.getUniqueId());
                if (mask == null || !player.getWorld().equals(mask.world())) {
                    return;
                }
                int chunkX;
                int chunkZ;
                try {
                    chunkX = event.getPacket().getIntegers().read(0);
                    chunkZ = event.getPacket().getIntegers().read(1);
                } catch (Exception e) {
                    return; // Never break the chunk pipeline over a read failure.
                }
                if (mask.coversChunk(chunkX, chunkZ)) {
                    return;
                }
                if (player.hasPermission("rumilance.admin")) {
                    return; // Admins keep full sight, same as ViewControlService.
                }
                PacketContainer clone = event.getPacket().deepClone();
                List<PacketContainer> cache = held.computeIfAbsent(player.getUniqueId(),
                        id -> new ArrayList<>());
                synchronized (cache) {
                    if (cache.size() < MAX_CACHED_PER_PLAYER) {
                        cache.add(clone);
                    }
                }
                event.setCancelled(true);
            }
        });
    }

    /** Registers the quit hook; call once from the bootstrap after construction. */
    public void init() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /** Starts masking {@code player}'s view to {@code region} (their FFA arena). */
    public void mask(Player player, Cuboid region) {
        if (player == null || region == null || region.world() == null) {
            return;
        }
        masks.put(player.getUniqueId(), new Mask(region.world(), region));
    }

    /**
     * Stops masking and replays every chunk packet that was cancelled while masked, so the
     * terrain around the FFA reappears immediately. Safe to call for unmasked players.
     */
    public void reveal(UUID playerId) {
        if (playerId == null) {
            return;
        }
        masks.remove(playerId);
        List<PacketContainer> cache = held.remove(playerId);
        if (cache == null || cache.isEmpty()) {
            return;
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return; // Offline: nothing to replay, the cache is dropped.
        }
        synchronized (cache) {
            for (PacketContainer packet : cache) {
                try {
                    protocol.sendServerPacket(player, packet);
                } catch (Exception ignored) {
                    // A replay failure on one chunk packet must not stop the rest.
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        masks.remove(id);
        held.remove(id); // No replay for a disconnecting client.
    }
}
