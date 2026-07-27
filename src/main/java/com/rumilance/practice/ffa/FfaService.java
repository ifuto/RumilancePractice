package com.rumilance.practice.ffa;

import com.rumilance.practice.config.ConfigService;
import com.rumilance.practice.config.RuntimeFlags;
import com.rumilance.practice.database.repository.FfaStatsRepository;
import com.rumilance.practice.kit.KitLayoutCache;
import com.rumilance.practice.kit.KitService;
import com.rumilance.practice.lobby.LobbyService;
import com.rumilance.practice.model.KitDefinition;
import com.rumilance.practice.session.PlayerStateManager;
import com.rumilance.practice.state.PlayerState;
import com.rumilance.practice.util.AsyncExecutor;
import com.rumilance.practice.util.Cuboid;
import com.rumilance.practice.util.LocationUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Separate FFA arenas. Never affects ranked Elo/stats.
 */
public final class FfaService {

    public record FfaArena(
            String id,
            String kitId,
            String world,
            Cuboid region,
            Location spawn,
            boolean enabled
    ) {
    }

    public record FfaStats(int kills, int deaths) {
    }

    private record BlockChange(Location location, String previousData) {
    }

    private final Plugin plugin;
    private final ConfigService configService;
    private final KitService kitService;
    private final KitLayoutCache layoutCache;
    private final LobbyService lobbyService;
    private final PlayerStateManager stateManager;
    private final FfaStatsRepository ffaStatsRepository;
    private final AsyncExecutor asyncExecutor;
    private final RuntimeFlags runtimeFlags;
    private final Map<String, FfaArena> arenas = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerArena = new ConcurrentHashMap<>();
    private final Map<UUID, FfaStats> sessionStats = new ConcurrentHashMap<>();
    private final Map<String, Boolean> resetting = new ConcurrentHashMap<>();
    private final Map<String, List<BlockChange>> blockDiffs = new ConcurrentHashMap<>();

    public FfaService(
            Plugin plugin,
            ConfigService configService,
            KitService kitService,
            KitLayoutCache layoutCache,
            LobbyService lobbyService,
            PlayerStateManager stateManager,
            FfaStatsRepository ffaStatsRepository,
            AsyncExecutor asyncExecutor,
            RuntimeFlags runtimeFlags
    ) {
        this.plugin = plugin;
        this.configService = configService;
        this.kitService = kitService;
        this.layoutCache = layoutCache;
        this.lobbyService = lobbyService;
        this.stateManager = stateManager;
        this.ffaStatsRepository = ffaStatsRepository;
        this.asyncExecutor = asyncExecutor;
        this.runtimeFlags = runtimeFlags;
        reload();
    }

    public void reload() {
        arenas.clear();
        FileConfiguration yaml = configService.ffa();
        ConfigurationSection section = yaml.getConfigurationSection("arenas");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(id);
            if (entry == null) {
                continue;
            }
            String world = entry.getString("world", "world");
            Cuboid region = Cuboid.of(world,
                    entry.getInt("pos1.x"), entry.getInt("pos1.y"), entry.getInt("pos1.z"),
                    entry.getInt("pos2.x"), entry.getInt("pos2.y"), entry.getInt("pos2.z"));
            Location spawn = new Location(Bukkit.getWorld(world),
                    entry.getDouble("spawn.x", 0.5),
                    entry.getDouble("spawn.y", 65),
                    entry.getDouble("spawn.z", 0.5),
                    (float) entry.getDouble("spawn.yaw", 0),
                    (float) entry.getDouble("spawn.pitch", 0));
            arenas.put(id.toLowerCase(), new FfaArena(
                    id.toLowerCase(),
                    entry.getString("kit", "nodebuff"),
                    world,
                    region,
                    spawn,
                    entry.getBoolean("enabled", true)
            ));
        }
    }

    public List<FfaArena> list() {
        return List.copyOf(arenas.values());
    }

    public Optional<FfaArena> get(String id) {
        return Optional.ofNullable(arenas.get(id.toLowerCase()));
    }

    public boolean join(Player player, String arenaId) {
        if (runtimeFlags.maintenance() && !player.hasPermission("rumilance.admin")) {
            player.sendMessage(Component.text("Practice is in maintenance mode.", NamedTextColor.RED));
            return false;
        }
        FfaArena arena = arenas.get(arenaId.toLowerCase());
        if (arena == null || !arena.enabled() || Boolean.TRUE.equals(resetting.get(arena.id()))) {
            player.sendMessage(Component.text("FFA unavailable.", NamedTextColor.RED));
            return false;
        }
        PlayerState state = stateManager.getState(player.getUniqueId());
        if (state != PlayerState.LOBBY && state != PlayerState.OPENING_GUI) {
            player.sendMessage(Component.text("Cannot join FFA now.", NamedTextColor.RED));
            return false;
        }
        KitDefinition kit = kitService.get(arena.kitId()).orElse(null);
        if (kit == null) {
            player.sendMessage(Component.text("FFA kit missing.", NamedTextColor.RED));
            return false;
        }
        try {
            stateManager.transition(player.getUniqueId(), PlayerState.FFA);
        } catch (Exception e) {
            return false;
        }
        playerArena.put(player.getUniqueId(), arena.id());
        sessionStats.put(player.getUniqueId(), new FfaStats(0, 0));
        if (arena.spawn().getWorld() != null) {
            player.teleport(LocationUtil.safeTeleportLocation(arena.spawn(), player));
        }
        applyKit(player, kit);
        player.sendMessage(Component.text("Joined FFA: " + arena.id(), NamedTextColor.GREEN));
        return true;
    }

    public void leave(Player player) {
        playerArena.remove(player.getUniqueId());
        sessionStats.remove(player.getUniqueId());
        stateManager.resetToLobby(player.getUniqueId());
        lobbyService.sendToLobby(player);
    }

    public Optional<String> arenaOf(UUID player) {
        return Optional.ofNullable(playerArena.get(player));
    }

    public boolean isInFfa(UUID player) {
        return playerArena.containsKey(player);
    }

    public FfaStats stats(UUID player) {
        return sessionStats.getOrDefault(player, new FfaStats(0, 0));
    }

    public void handleLethal(Player victim, UUID killerId) {
        String arenaId = playerArena.get(victim.getUniqueId());
        if (arenaId == null) {
            return;
        }
        addDeath(victim.getUniqueId());
        asyncExecutor.execute(() -> {
            try {
                ffaStatsRepository.addDeath(victim.getUniqueId(), arenaId);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed persisting FFA death", e);
            }
        });
        if (killerId != null && !killerId.equals(victim.getUniqueId()) && playerArena.containsKey(killerId)) {
            addKill(killerId);
            Player killer = Bukkit.getPlayer(killerId);
            if (killer != null) {
                FfaStats s = stats(killerId);
                killer.sendActionBar(Component.text("Kills: " + s.kills() + " Deaths: " + s.deaths(),
                        NamedTextColor.GOLD));
            }
            asyncExecutor.execute(() -> {
                try {
                    ffaStatsRepository.addKill(killerId, arenaId);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Failed persisting FFA kill", e);
                }
            });
        }
        respawn(victim);
    }

    public void addKill(UUID killer) {
        sessionStats.compute(killer, (id, cur) -> {
            FfaStats s = cur == null ? new FfaStats(0, 0) : cur;
            return new FfaStats(s.kills() + 1, s.deaths());
        });
    }

    public void addDeath(UUID victim) {
        sessionStats.compute(victim, (id, cur) -> {
            FfaStats s = cur == null ? new FfaStats(0, 0) : cur;
            return new FfaStats(s.kills(), s.deaths() + 1);
        });
    }

    public void respawn(Player player) {
        String arenaId = playerArena.get(player.getUniqueId());
        if (arenaId == null) {
            return;
        }
        FfaArena arena = arenas.get(arenaId);
        if (arena == null) {
            return;
        }
        KitDefinition kit = kitService.get(arena.kitId()).orElse(null);
        player.setHealth(player.getMaxHealth());
        player.setFireTicks(0);
        if (arena.spawn().getWorld() != null) {
            player.teleport(LocationUtil.safeTeleportLocation(arena.spawn(), player));
        }
        if (kit != null) {
            applyKit(player, kit);
        }
    }

    public void recordBlockChange(UUID playerId, Location location, String previousData) {
        String arenaId = playerArena.get(playerId);
        if (arenaId == null) {
            return;
        }
        // Keep compressed diffs only (location + previous BlockData string).
        List<BlockChange> list = blockDiffs.computeIfAbsent(arenaId, id -> new ArrayList<>());
        synchronized (list) {
            if (list.size() < 50_000) {
                list.add(new BlockChange(location.clone(), previousData));
            }
        }
    }

    public void create(String id, Cuboid region, Location spawn, String kitId) {
        FfaArena arena = new FfaArena(id.toLowerCase(), kitId, region.worldName(), region, spawn.clone(), false);
        arenas.put(arena.id(), arena);
        persist(arena);
    }

    public boolean updateRegion(String id, Cuboid region) {
        FfaArena existing = arenas.get(id.toLowerCase());
        if (existing == null) {
            return false;
        }
        FfaArena updated = new FfaArena(existing.id(), existing.kitId(), region.worldName(), region,
                existing.spawn(), existing.enabled());
        arenas.put(updated.id(), updated);
        persist(updated);
        return true;
    }

    public boolean updateSpawn(String id, Location spawn) {
        FfaArena existing = arenas.get(id.toLowerCase());
        if (existing == null) {
            return false;
        }
        FfaArena updated = new FfaArena(existing.id(), existing.kitId(), existing.world(),
                existing.region(), spawn.clone(), existing.enabled());
        arenas.put(updated.id(), updated);
        persist(updated);
        return true;
    }

    public boolean updateKit(String id, String kitId) {
        FfaArena existing = arenas.get(id.toLowerCase());
        if (existing == null) {
            return false;
        }
        FfaArena updated = new FfaArena(existing.id(), kitId.toLowerCase(), existing.world(),
                existing.region(), existing.spawn(), existing.enabled());
        arenas.put(updated.id(), updated);
        persist(updated);
        return true;
    }

    public void setEnabled(String id, boolean enabled) {
        FfaArena existing = arenas.get(id.toLowerCase());
        if (existing == null) {
            return;
        }
        FfaArena updated = new FfaArena(existing.id(), existing.kitId(), existing.world(),
                existing.region(), existing.spawn(), enabled);
        arenas.put(updated.id(), updated);
        persist(updated);
    }

    public void delete(String id) {
        arenas.remove(id.toLowerCase());
        blockDiffs.remove(id.toLowerCase());
        configService.ffa().set("arenas." + id.toLowerCase(), null);
        configService.save(ConfigService.FFA);
    }

    public void reset(String id) {
        FfaArena arena = arenas.get(id.toLowerCase());
        if (arena == null) {
            return;
        }
        resetting.put(arena.id(), true);
        List<UUID> occupants = new ArrayList<>();
        for (Map.Entry<UUID, String> entry : playerArena.entrySet()) {
            if (entry.getValue().equals(arena.id())) {
                occupants.add(entry.getKey());
            }
        }
        for (UUID uuid : occupants) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.showTitle(Title.title(
                        Component.text("FFAは修復中です", NamedTextColor.RED),
                        Component.empty(),
                        Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ofMillis(200))));
                leave(player);
            }
        }
        List<BlockChange> diffs = blockDiffs.remove(arena.id());
        if (diffs != null && !diffs.isEmpty()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (BlockChange change : diffs) {
                    World world = change.location().getWorld();
                    if (world == null) {
                        continue;
                    }
                    try {
                        BlockData data = Bukkit.createBlockData(change.previousData());
                        change.location().getBlock().setBlockData(data, false);
                    } catch (IllegalArgumentException ignored) {
                        // skip corrupt entries
                    }
                }
                resetting.put(arena.id(), false);
            });
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, () -> resetting.put(arena.id(), false), 40L);
        }
    }

    private void applyKit(Player player, KitDefinition kit) {
        layoutCache.loadSyncIfAbsent(player.getUniqueId(), kit.name());
        ItemStack[] layout = layoutCache.get(player.getUniqueId(), kit.name()).orElse(null);
        kitService.apply(player, kit, layout);
    }

    private void persist(FfaArena arena) {
        String path = "arenas." + arena.id();
        FileConfiguration yaml = configService.ffa();
        yaml.set(path + ".kit", arena.kitId());
        yaml.set(path + ".world", arena.world());
        yaml.set(path + ".enabled", arena.enabled());
        yaml.set(path + ".pos1.x", arena.region().minX());
        yaml.set(path + ".pos1.y", arena.region().minY());
        yaml.set(path + ".pos1.z", arena.region().minZ());
        yaml.set(path + ".pos2.x", arena.region().maxX());
        yaml.set(path + ".pos2.y", arena.region().maxY());
        yaml.set(path + ".pos2.z", arena.region().maxZ());
        yaml.set(path + ".spawn.x", arena.spawn().getX());
        yaml.set(path + ".spawn.y", arena.spawn().getY());
        yaml.set(path + ".spawn.z", arena.spawn().getZ());
        yaml.set(path + ".spawn.yaw", arena.spawn().getYaw());
        yaml.set(path + ".spawn.pitch", arena.spawn().getPitch());
        configService.save(ConfigService.FFA);
    }
}
