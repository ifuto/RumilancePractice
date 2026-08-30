package com.rumilance.practice.ffa;

import com.rumilance.practice.config.ConfigService;
import com.rumilance.practice.config.RuntimeFlags;
import com.rumilance.practice.database.repository.FfaStatsRepository;
import com.rumilance.practice.kit.KitLayoutCache;
import com.rumilance.practice.kit.KitService;
import com.rumilance.practice.locale.MessageService;
import com.rumilance.practice.lobby.LobbyService;
import com.rumilance.practice.model.KitDefinition;
import com.rumilance.practice.queue.QueueService;
import com.rumilance.practice.session.PlayerStateManager;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.state.PlayerState;
import com.rumilance.practice.util.AsyncExecutor;
import com.rumilance.practice.util.Cuboid;
import com.rumilance.practice.util.LocationUtil;
import com.rumilance.practice.util.PlayerVitals;
import com.rumilance.practice.util.SafeTeleport;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Item;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
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
            boolean enabled,
            int resetIntervalSeconds,
            String iconMaterial
    ) {
        public FfaArena {
            resetIntervalSeconds = Math.max(0, resetIntervalSeconds);
            if (iconMaterial == null || iconMaterial.isBlank()) {
                iconMaterial = "IRON_SWORD";
            }
        }

        public FfaArena withResetInterval(int seconds) {
            return new FfaArena(id, kitId, world, region, spawn, enabled, Math.max(0, seconds), iconMaterial);
        }

        public FfaArena withEnabled(boolean value) {
            return new FfaArena(id, kitId, world, region, spawn, value, resetIntervalSeconds, iconMaterial);
        }

        public FfaArena withKit(String kit) {
            return new FfaArena(id, kit, world, region, spawn, enabled, resetIntervalSeconds, iconMaterial);
        }

        public FfaArena withRegion(Cuboid newRegion) {
            return new FfaArena(id, kitId, newRegion.worldName(), newRegion, spawn, enabled,
                    resetIntervalSeconds, iconMaterial);
        }

        public FfaArena withSpawn(Location newSpawn) {
            return new FfaArena(id, kitId, world, region, newSpawn.clone(), enabled,
                    resetIntervalSeconds, iconMaterial);
        }

        public FfaArena withId(String newId) {
            return new FfaArena(newId, kitId, world, region, spawn, enabled, resetIntervalSeconds, iconMaterial);
        }

        public FfaArena withIconMaterial(String material) {
            return new FfaArena(id, kitId, world, region, spawn, enabled, resetIntervalSeconds, material);
        }
    }

    public record FfaStats(int kills, int deaths) {
    }

    public record StreakRank(UUID playerId, int streak) {
    }

    private record BlockChange(Location location, String previousData) {
    }

    private record CombatTag(UUID attackerId, long untilMillis) {
    }

    private static final long COMBAT_MS = 30_000L;

    private final Plugin plugin;
    private final ConfigService configService;
    private final KitService kitService;
    private final KitLayoutCache layoutCache;
    private final LobbyService lobbyService;
    private final PlayerStateManager stateManager;
    private final FfaStatsRepository ffaStatsRepository;
    private final AsyncExecutor asyncExecutor;
    private final RuntimeFlags runtimeFlags;
    private final MessageService messageService;
    private final SoundService soundService;
    /** Optional per-player border/view-distance control (null = feature off). */
    private volatile com.rumilance.practice.sight.ViewControlService viewControl;

    public void setViewControl(com.rumilance.practice.sight.ViewControlService viewControl) {
        this.viewControl = viewControl;
    }

    private FfaSpawnIndex spawnIndex;
    private QueueService queueService;
    private com.rumilance.practice.team.TeamService teamService;
    private java.util.function.Consumer<org.bukkit.entity.Player> hubReturn;

    public void setSpawnIndex(FfaSpawnIndex spawnIndex) {
        this.spawnIndex = spawnIndex;
    }

    public FfaSpawnIndex spawnIndex() {
        return spawnIndex;
    }

    public void setQueueService(QueueService queueService) {
        this.queueService = queueService;
    }

    public void setTeamService(com.rumilance.practice.team.TeamService teamService) {
        this.teamService = teamService;
    }

    public void setHubReturn(java.util.function.Consumer<org.bukkit.entity.Player> hubReturn) {
        this.hubReturn = hubReturn;
    }
    private final Map<String, FfaArena> arenas = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerArena = new ConcurrentHashMap<>();
    private final Map<UUID, FfaStats> sessionStats = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> killStreaks = new ConcurrentHashMap<>();
    private final Map<UUID, CombatTag> combatUntil = new ConcurrentHashMap<>();
    private final Map<String, Boolean> resetting = new ConcurrentHashMap<>();
    private final Map<String, List<BlockChange>> blockDiffs = new ConcurrentHashMap<>();
    /** Per-arena countdown deadline (millis); absent or 0 = timer inactive. */
    private final Map<String, Long> nextResetAtMillis = new ConcurrentHashMap<>();
    /** Last observed remaining seconds, used to fire warn thresholds once each. */
    private final Map<String, Integer> lastResetRemaining = new ConcurrentHashMap<>();
    private static final int[] RESET_WARN_AT = {300, 240, 180, 120, 60, 30, 5, 4, 3, 2, 1};
    private BukkitTask combatTask;

    public FfaService(
            Plugin plugin,
            ConfigService configService,
            KitService kitService,
            KitLayoutCache layoutCache,
            LobbyService lobbyService,
            PlayerStateManager stateManager,
            FfaStatsRepository ffaStatsRepository,
            AsyncExecutor asyncExecutor,
            RuntimeFlags runtimeFlags,
            MessageService messageService,
            SoundService soundService
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
        this.messageService = messageService;
        this.soundService = soundService;
        reload();
        combatTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            tickCombat();
            tickResets();
        }, 20L, 20L);
    }

    public void shutdown() {
        if (combatTask != null) {
            combatTask.cancel();
            combatTask = null;
        }
        combatUntil.clear();
        nextResetAtMillis.clear();
        lastResetRemaining.clear();
    }

    public void reload() {
        arenas.clear();
        nextResetAtMillis.clear();
        lastResetRemaining.clear();
        FileConfiguration yaml = configService.ffa();
        ConfigurationSection section = yaml.getConfigurationSection("arenas");
        if (section == null) {
            return;
        }
        int globalDefault = yaml.getInt("settings.reset-interval-seconds", 0);
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
            int interval = entry.contains("reset-interval-seconds")
                    ? entry.getInt("reset-interval-seconds", 0)
                    : globalDefault;
            FfaArena arena = new FfaArena(
                    id,
                    entry.getString("kit", "nodebuff"),
                    world,
                    region,
                    spawn,
                    entry.getBoolean("enabled", true),
                    interval,
                    entry.getString("icon", "IRON_SWORD")
            );
            arenas.put(id, arena);
            armResetTimer(arena, false);
        }
    }

    public List<FfaArena> list() {
        return List.copyOf(arenas.values());
    }

    /** Live arena definitions (same contents as {@link #list()}). */
    public java.util.Collection<FfaArena> arenasView() {
        return java.util.Collections.unmodifiableCollection(arenas.values());
    }

    /** Players currently inside any FFA arena. */
    public java.util.Set<UUID> occupantIds() {
        return java.util.Set.copyOf(playerArena.keySet());
    }

    /** Preferred respawn/teleport destination for an FFA occupant. */
    public Location spawnDestination(Player player) {
        String arenaId = playerArena.get(player.getUniqueId());
        if (arenaId == null) {
            return null;
        }
        FfaArena arena = arenas.get(arenaId);
        if (arena == null || arena.spawn() == null) {
            return null;
        }
        return LocationUtil.safeTeleportLocation(arena.spawn());
    }

    /** Re-applies per-player border / view distance for the player's current FFA arena. */
    public void applySight(Player player) {
        if (viewControl == null) {
            return;
        }
        String arenaId = playerArena.get(player.getUniqueId());
        if (arenaId == null) {
            return;
        }
        FfaArena arena = arenas.get(arenaId);
        if (arena != null && arena.region() != null) {
            viewControl.applyRegion(player, arena.region());
        }
    }

    public Optional<FfaArena> get(String id) {
        return Optional.ofNullable(findArena(id));
    }

    private FfaArena findArena(String id) {
        if (id == null) {
            return null;
        }
        FfaArena exact = arenas.get(id);
        if (exact != null) {
            return exact;
        }
        // Legacy lowercased keys from pre-1.7.0 stores.
        return arenas.get(id.toLowerCase());
    }

    public boolean join(Player player, String arenaId) {
        if (runtimeFlags.maintenance() && !player.hasPermission("rumilance.admin")) {
            messageService.send(player, "ffa.maintenance");
            return false;
        }
        if (teamService != null && teamService.teamOf(player.getUniqueId()).isPresent()) {
            messageService.send(player, "party.solo-only");
            return false;
        }
        FfaArena arena = findArena(arenaId);
        if (arena == null || !arena.enabled() || Boolean.TRUE.equals(resetting.get(arena.id()))) {
            messageService.send(player, "ffa.unavailable");
            return false;
        }
        PlayerState state = stateManager.getState(player.getUniqueId());
        if (state != PlayerState.LOBBY && state != PlayerState.OPENING_GUI) {
            messageService.send(player, "ffa.cannot-join");
            return false;
        }
        KitDefinition kit = kitService.get(arena.kitId()).orElse(null);
        if (kit == null) {
            messageService.send(player, "ffa.kit-missing");
            return false;
        }
        try {
            stateManager.transition(player.getUniqueId(), PlayerState.FFA);
        } catch (Exception e) {
            return false;
        }
        playerArena.put(player.getUniqueId(), arena.id());
        sessionStats.put(player.getUniqueId(), new FfaStats(0, 0));
        killStreaks.put(player.getUniqueId(), 0);
        combatUntil.remove(player.getUniqueId());
        player.setCanPickupItems(true);
        Location dest = pickSpawn(arena, player.getUniqueId());
        if (dest == null || dest.getWorld() == null) {
            leave(player);
            messageService.send(player, "ffa.unavailable");
            return false;
        }
        SafeTeleport.teleport(player, LocationUtil.safeTeleportLocation(dest))
                .whenComplete((ok, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline() || !isInFfa(player.getUniqueId())) {
                        return;
                    }
                    if (error != null || !Boolean.TRUE.equals(ok)) {
                        leave(player);
                        messageService.send(player, "ffa.teleport-failed");
                        return;
                    }
                    applyKit(player, kit);
                    player.setCanPickupItems(true);
                    if (viewControl != null) {
                        viewControl.applyRegion(player, arena.region());
                    }
                    messageService.send(player, "ffa.joined", MessageService.tags("arena", arena.id()));
                }));
        return true;
    }

    public void leave(Player player) {
        UUID id = player.getUniqueId();
        playerArena.remove(id);
        sessionStats.remove(id);
        killStreaks.remove(id);
        combatUntil.remove(id);
        stateManager.resetToLobby(id);
        if (hubReturn != null) {
            hubReturn.accept(player);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && hubReturn != null) {
                    hubReturn.accept(player);
                }
            }, 8L);
        } else {
            lobbyService.sendToLobby(player);
        }
    }

    public Optional<String> arenaOf(UUID player) {
        return Optional.ofNullable(playerArena.get(player));
    }

    public boolean isInFfa(UUID player) {
        return playerArena.containsKey(player);
    }

    /** True when {@code location} lies inside any enabled FFA arena region. */
    public boolean isInFfaRegion(Location location) {
        if (location == null) {
            return false;
        }
        for (FfaArena arena : arenas.values()) {
            if (arena.enabled() && arena.region() != null && arena.region().contains(location)) {
                return true;
            }
        }
        return false;
    }

    public FfaStats stats(UUID player) {
        return sessionStats.getOrDefault(player, new FfaStats(0, 0));
    }

    public int killStreak(UUID player) {
        return killStreaks.getOrDefault(player, 0);
    }

    public List<StreakRank> topKillStreaks(int limit) {
        return topKillStreaks(null, limit);
    }

    /** When {@code arenaId} is set, only streaks of players currently in that FFA. */
    public List<StreakRank> topKillStreaks(String arenaId, int limit) {
        int cap = Math.max(0, limit);
        return killStreaks.entrySet().stream()
                .filter(entry -> {
                    String in = playerArena.get(entry.getKey());
                    if (in == null || entry.getValue() <= 0) {
                        return false;
                    }
                    return arenaId == null || arenaId.equalsIgnoreCase(in);
                })
                .sorted(Comparator.<Map.Entry<UUID, Integer>>comparingInt(Map.Entry::getValue).reversed())
                .limit(cap)
                .map(entry -> new StreakRank(entry.getKey(), entry.getValue()))
                .toList();
    }

    public void tagCombat(UUID victimId, UUID attackerId) {
        if (victimId == null || attackerId == null || victimId.equals(attackerId)) {
            return;
        }
        if (!playerArena.containsKey(victimId) || !playerArena.containsKey(attackerId)) {
            return;
        }
        long until = System.currentTimeMillis() + COMBAT_MS;
        combatUntil.put(victimId, new CombatTag(attackerId, until));
        combatUntil.put(attackerId, new CombatTag(victimId, until));
    }

    public boolean inCombat(UUID playerId) {
        CombatTag tag = combatUntil.get(playerId);
        return tag != null && tag.untilMillis() > System.currentTimeMillis();
    }

    /**
     * Quit while combat-tagged: count as a death for the quitter and a kill for the last attacker.
     *
     * @return true when combat credit was applied
     */
    public boolean creditCombatLogout(Player player) {
        UUID victimId = player.getUniqueId();
        String arenaId = playerArena.get(victimId);
        CombatTag tag = combatUntil.remove(victimId);
        if (arenaId == null || tag == null || tag.untilMillis() <= System.currentTimeMillis()) {
            combatUntil.remove(victimId);
            return false;
        }
        addDeath(victimId);
        killStreaks.put(victimId, 0);
        asyncExecutor.execute(() -> {
            try {
                ffaStatsRepository.addDeath(victimId, arenaId);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed persisting FFA combat-logout death", e);
            }
        });
        UUID killerId = tag.attackerId();
        if (killerId != null && !killerId.equals(victimId) && playerArena.containsKey(killerId)) {
            addKill(killerId);
            int streak = killStreaks.merge(killerId, 1, Integer::sum);
            Player killer = Bukkit.getPlayer(killerId);
            if (killer != null) {
                // Full kit refill on a confirmed kill (someone other than yourself).
                restoreKit(killer);
                FfaStats s = stats(killerId);
                killer.sendActionBar(Component.text("Kills: " + s.kills() + " Deaths: " + s.deaths(),
                        NamedTextColor.GOLD));
                if (streak > 0 && streak % 5 == 0) {
                    killer.sendMessage(Component.text(streak + " kill streak!", NamedTextColor.GOLD));
                }
            }
            asyncExecutor.execute(() -> {
                try {
                    ffaStatsRepository.addKill(killerId, arenaId);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Failed persisting FFA combat-logout kill", e);
                }
            });
        }
        return true;
    }

    private void tickCombat() {
        if (combatUntil.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, CombatTag> entry : combatUntil.entrySet()) {
            CombatTag tag = entry.getValue();
            if (tag.untilMillis() <= now) {
                combatUntil.remove(entry.getKey(), tag);
                continue;
            }
            Player online = Bukkit.getPlayer(entry.getKey());
            if (online == null || !online.isOnline()) {
                continue;
            }
            int seconds = (int) Math.max(1L, (tag.untilMillis() - now + 999L) / 1000L);
            online.sendActionBar(Component.text("Combat : " + seconds + "s", NamedTextColor.RED));
        }
    }

    public void handleLethal(Player victim, UUID killerId) {
        String arenaId = playerArena.get(victim.getUniqueId());
        if (arenaId == null) {
            return;
        }
        soundService.play(victim, "death");
        combatUntil.remove(victim.getUniqueId());
        killStreaks.put(victim.getUniqueId(), 0);
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
            int streak = killStreaks.merge(killerId, 1, Integer::sum);
            Player killer = Bukkit.getPlayer(killerId);
            if (killer != null) {
                double hp = killer.getHealth();
                double max = com.rumilance.practice.combat.KillFeed.maxHealth(killer);
                FfaStats s = stats(killerId);
                restoreKit(killer);
                com.rumilance.practice.combat.KillFeed.broadcast(killer, victim, null, hp, max, null);
                killer.sendActionBar(Component.text("Kills: " + s.kills() + " Deaths: " + s.deaths(),
                        NamedTextColor.GOLD));
                if (streak > 0 && streak % 5 == 0) {
                    killer.sendMessage(Component.text(streak + " kill streak!", NamedTextColor.GOLD));
                }
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
        // Wipe the previous life's items BEFORE re-applying the kit. Without this reset,
        // the fake-death flow (damage cancelled, no vanilla death screen) leaves the old
        // kit on the player and KitLoadout#give was the only thing clearing it — a window
        // in which looted/dropped items from the arena floor survived the respawn into
        // the next life (and could later be carried back to the lobby).
        com.rumilance.practice.util.PlayerVitals.fakeDeathReset(player);
        player.setHealth(player.getMaxHealth());
        player.setFireTicks(0);
        player.setCanPickupItems(true);
        if (kit != null) {
            applyKit(player, kit);
        }
        teleportIntoArena(player, arena);
        player.setCanPickupItems(true);
    }

    /** Spawn coordinate for the vanilla respawn event — kit apply happens in {@link #respawn}. */
    public Location respawnLocation(Player player) {
        String arenaId = playerArena.get(player.getUniqueId());
        if (arenaId == null) {
            return null;
        }
        FfaArena arena = arenas.get(arenaId);
        if (arena == null) {
            return null;
        }
        return pickSpawn(arena, player.getUniqueId());
    }

    private void teleportIntoArena(Player player, FfaArena arena) {
        Location dest = pickSpawn(arena, player.getUniqueId());
        if (dest == null || dest.getWorld() == null) {
            return;
        }
        // SafeTeleport clears the stale per-player border before the move; re-apply THIS
        // arena's region border/view only after the teleport landed, so a wiped border can
        // never leave the FFA player with no (or the lobby's) wall.
        SafeTeleport.teleport(player, LocationUtil.safeTeleportLocation(dest))
                .whenComplete((ok, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline() && isInFfa(player.getUniqueId())) {
                        applySight(player);
                    }
                }));
    }

    private Location pickSpawn(FfaArena arena, UUID joining) {
        List<Location> occupied = new ArrayList<>();
        for (Map.Entry<UUID, String> entry : playerArena.entrySet()) {
            if (!entry.getValue().equals(arena.id()) || entry.getKey().equals(joining)) {
                continue;
            }
            Player other = Bukkit.getPlayer(entry.getKey());
            if (other != null) {
                occupied.add(other.getLocation());
            }
        }
        if (spawnIndex != null) {
            Location picked = spawnIndex.pick(arena, occupied);
            if (picked != null) {
                return picked;
            }
        }
        return arena.spawn();
    }

    public void recordBlockChange(UUID playerId, Location location, String previousData) {
        String arenaId = playerArena.get(playerId);
        if (arenaId == null) {
            return;
        }
        recordBlockChangeForArena(arenaId, location, previousData);
    }

    /** Records a change by location (explosions) for whichever enabled FFA arena contains it. */
    public void recordBlockChangeAt(Location location, String previousData) {
        if (location == null || previousData == null) {
            return;
        }
        for (FfaArena arena : arenas.values()) {
            if (!arena.enabled() || arena.region() == null || !arena.region().contains(location)) {
                continue;
            }
            recordBlockChangeForArena(arena.id(), location, previousData);
            return;
        }
    }

    private void recordBlockChangeForArena(String arenaId, Location location, String previousData) {
        List<BlockChange> list = blockDiffs.computeIfAbsent(arenaId, id -> new ArrayList<>());
        synchronized (list) {
            for (BlockChange existing : list) {
                if (sameBlock(existing.location(), location)) {
                    return;
                }
            }
            if (list.size() < 50_000) {
                list.add(new BlockChange(location.clone(), previousData));
            }
        }
    }

    private static boolean sameBlock(Location a, Location b) {
        if (a == null || b == null || a.getWorld() == null || b.getWorld() == null) {
            return false;
        }
        return a.getWorld().equals(b.getWorld())
                && a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }

    public void create(String id, Cuboid region, Location spawn, String kitId) {
        FfaArena arena = new FfaArena(id, kitId, region.worldName(), region, spawn.clone(), false, 0, "IRON_SWORD");
        arenas.put(arena.id(), arena);
        persist(arena);
        armResetTimer(arena, false);
    }

    public enum RenameResult {
        OK, NOT_FOUND, TARGET_EXISTS
    }

    public RenameResult rename(String oldId, String newId) {
        if (oldId == null || newId == null || newId.isBlank()) {
            return RenameResult.NOT_FOUND;
        }
        FfaArena existing = findArena(oldId);
        if (existing == null) {
            return RenameResult.NOT_FOUND;
        }
        if (!existing.id().equals(newId) && arenas.containsKey(newId)) {
            return RenameResult.TARGET_EXISTS;
        }
        arenas.remove(existing.id());
        List<BlockChange> diffs = blockDiffs.remove(existing.id());
        Long nextAt = nextResetAtMillis.remove(existing.id());
        Integer lastRem = lastResetRemaining.remove(existing.id());
        FfaArena renamed = existing.withId(newId);
        arenas.put(newId, renamed);
        if (diffs != null) {
            blockDiffs.put(newId, diffs);
        }
        if (nextAt != null) {
            nextResetAtMillis.put(newId, nextAt);
        }
        if (lastRem != null) {
            lastResetRemaining.put(newId, lastRem);
        }
        for (Map.Entry<UUID, String> e : playerArena.entrySet()) {
            if (existing.id().equals(e.getValue())) {
                e.setValue(newId);
            }
        }
        configService.ffa().set("arenas." + existing.id(), null);
        persist(renamed);
        return RenameResult.OK;
    }

    public boolean updateRegion(String id, Cuboid region) {
        FfaArena existing = findArena(id);
        if (existing == null) {
            return false;
        }
        FfaArena updated = existing.withRegion(region);
        arenas.put(updated.id(), updated);
        persist(updated);
        return true;
    }

    public boolean updateSpawn(String id, Location spawn) {
        FfaArena existing = findArena(id);
        if (existing == null) {
            return false;
        }
        FfaArena updated = existing.withSpawn(spawn);
        arenas.put(updated.id(), updated);
        persist(updated);
        return true;
    }

    public boolean updateKit(String id, String kitId) {
        FfaArena existing = findArena(id);
        if (existing == null) {
            return false;
        }
        FfaArena updated = existing.withKit(kitId);
        arenas.put(updated.id(), updated);
        persist(updated);
        return true;
    }

    public boolean updateIcon(String id, String material) {
        FfaArena existing = findArena(id);
        if (existing == null || material == null || material.isBlank()) {
            return false;
        }
        FfaArena updated = existing.withIconMaterial(material.toUpperCase(java.util.Locale.ROOT));
        arenas.put(updated.id(), updated);
        persist(updated);
        return true;
    }

    public void setEnabled(String id, boolean enabled) {
        FfaArena existing = findArena(id);
        if (existing == null) {
            return;
        }
        FfaArena updated = existing.withEnabled(enabled);
        arenas.put(updated.id(), updated);
        persist(updated);
    }

    public void delete(String id) {
        FfaArena existing = findArena(id);
        if (existing == null) {
            return;
        }
        arenas.remove(existing.id());
        blockDiffs.remove(existing.id());
        nextResetAtMillis.remove(existing.id());
        lastResetRemaining.remove(existing.id());
        configService.ffa().set("arenas." + existing.id(), null);
        configService.save(ConfigService.FFA);
    }

    /** Current per-arena reset interval (0 = off). */
    public int resetIntervalSeconds(String arenaId) {
        FfaArena arena = findArena(arenaId);
        return arena == null ? 0 : arena.resetIntervalSeconds();
    }

    /** Sets and persists the periodic reset interval for one arena. */
    public boolean setResetIntervalSeconds(String arenaId, int seconds) {
        FfaArena existing = findArena(arenaId);
        if (existing == null) {
            return false;
        }
        FfaArena updated = existing.withResetInterval(seconds);
        arenas.put(updated.id(), updated);
        persist(updated);
        armResetTimer(updated, true);
        return true;
    }

    private void armResetTimer(FfaArena arena, boolean restartNow) {
        if (arena == null) {
            return;
        }
        lastResetRemaining.put(arena.id(), Integer.MAX_VALUE);
        if (arena.resetIntervalSeconds() <= 0) {
            nextResetAtMillis.remove(arena.id());
            return;
        }
        if (restartNow || !nextResetAtMillis.containsKey(arena.id())) {
            nextResetAtMillis.put(arena.id(),
                    System.currentTimeMillis() + arena.resetIntervalSeconds() * 1000L);
        }
    }

    private void tickResets() {
        long now = System.currentTimeMillis();
        for (FfaArena arena : arenas.values()) {
            if (arena.resetIntervalSeconds() <= 0) {
                continue;
            }
            Long deadline = nextResetAtMillis.get(arena.id());
            if (deadline == null || deadline <= 0L) {
                armResetTimer(arena, true);
                continue;
            }
            int remaining = (int) Math.max(0L, (deadline - now + 999L) / 1000L);
            if (remaining <= 0) {
                performScheduledReset(arena);
                continue;
            }
            int prev = lastResetRemaining.getOrDefault(arena.id(), Integer.MAX_VALUE);
            lastResetRemaining.put(arena.id(), remaining);
            for (int at : RESET_WARN_AT) {
                if (prev > at && remaining <= at) {
                    announceResetWarning(arena, at);
                }
            }
        }
    }

    private void performScheduledReset(FfaArena arena) {
        lastResetRemaining.put(arena.id(), Integer.MAX_VALUE);
        nextResetAtMillis.put(arena.id(),
                System.currentTimeMillis() + arena.resetIntervalSeconds() * 1000L);
        reset(arena.id(), true);
    }

    private void announceResetWarning(FfaArena arena, int remainingSeconds) {
        String timeLabel = remainingSeconds >= 60 && remainingSeconds % 60 == 0
                ? FfaResetTimes.format(remainingSeconds)
                : remainingSeconds + (remainingSeconds == 1 ? " second" : " seconds");
        Component message = Component.text("⚠ ", NamedTextColor.YELLOW)
                .append(Component.text(arena.id() + " FFA will reset in ", NamedTextColor.WHITE))
                .append(Component.text(timeLabel + ".", NamedTextColor.YELLOW));
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(message);
            if (stateManager.getState(player.getUniqueId()) == PlayerState.EDITING_KIT) {
                continue;
            }
            soundService.play(player, "ffa-reset-warn");
        }
    }

    private void announceResetOpen(FfaArena arena) {
        Component message = Component.text(arena.id() + " FFA is now open !", NamedTextColor.GREEN);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(message);
            if (stateManager.getState(player.getUniqueId()) == PlayerState.EDITING_KIT) {
                continue;
            }
            soundService.play(player, "ffa-open");
        }
    }

    public void reset(String id) {
        reset(id, false);
    }

    /**
     * @param announceOpen when true (periodic timer), broadcast open + LEVEL_UP after terrain restore
     */
    public void reset(String id, boolean announceOpen) {
        FfaArena arena = findArena(id);
        if (arena == null) {
            return;
        }
        resetting.put(arena.id(), true);
        cleanupEntities(arena);
        java.util.Set<UUID> occupants = new java.util.LinkedHashSet<>();
        for (Map.Entry<UUID, String> entry : playerArena.entrySet()) {
            if (entry.getValue().equals(arena.id())) {
                occupants.add(entry.getKey());
            }
        }
        Cuboid region = arena.region();
        World world = region == null ? Bukkit.getWorld(arena.world()) : region.world();
        if (world != null && region != null) {
            for (Player player : world.getPlayers()) {
                if (region.contains(player.getLocation())) {
                    occupants.add(player.getUniqueId());
                }
            }
        }
        for (UUID uuid : occupants) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.showTitle(Title.title(
                        messageService.render(messageService.resolveLocale(player), "ffa.repairing-title"),
                        Component.empty(),
                        Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ofMillis(200))));
                leave(player);
            }
        }
        List<BlockChange> diffs = blockDiffs.remove(arena.id());
        Runnable finish = () -> {
            resetting.put(arena.id(), false);
            if (announceOpen) {
                announceResetOpen(arena);
            }
        };
        if (diffs != null && !diffs.isEmpty()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                // Undo in reverse so stacked place/break/explosion diffs restore correctly.
                for (int i = diffs.size() - 1; i >= 0; i--) {
                    BlockChange change = diffs.get(i);
                    World blockWorld = change.location().getWorld();
                    if (blockWorld == null) {
                        continue;
                    }
                    try {
                        BlockData data = Bukkit.createBlockData(change.previousData());
                        change.location().getBlock().setBlockData(data, false);
                    } catch (IllegalArgumentException ignored) {
                        // skip corrupt entries
                    }
                }
                finish.run();
            });
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, finish, 40L);
        }
    }

    private void cleanupEntities(FfaArena arena) {
        if (arena == null || arena.region() == null) {
            return;
        }
        World world = Bukkit.getWorld(arena.world());
        if (world == null) {
            return;
        }
        Cuboid region = arena.region();
        for (Entity entity : world.getEntities()) {
            if (!(entity instanceof Player) && region.contains(entity.getLocation())
                    && (entity instanceof EnderCrystal
                    || entity instanceof TNTPrimed
                    || entity instanceof ExplosiveMinecart
                    || entity instanceof FallingBlock
                    || entity instanceof Item)) {
                entity.remove();
            }
        }
    }

    private void restoreKit(Player player) {
        String arenaId = playerArena.get(player.getUniqueId());
        if (arenaId == null) {
            return;
        }
        FfaArena arena = arenas.get(arenaId);
        if (arena == null) {
            return;
        }
        kitService.get(arena.kitId()).ifPresent(kit -> applyKit(player, kit));
    }

    private void applyKit(Player player, KitDefinition kit) {
        layoutCache.loadSyncIfAbsent(player.getUniqueId(), kit.name());
        ItemStack[] layout = layoutCache.get(player.getUniqueId(), kit.name()).orElse(null);
        kitService.apply(player, kit, layout);
        PlayerVitals.applyCombatStart(player, kit.maxHealth());
        if (kit.totem()) {
            com.rumilance.practice.guard.PracticeGuards.enforceTotemCap(player, 14);
        }
        player.setCanPickupItems(true);
    }

    private void persist(FfaArena arena) {
        String path = "arenas." + arena.id();
        FileConfiguration yaml = configService.ffa();
        yaml.set(path + ".kit", arena.kitId());
        yaml.set(path + ".world", arena.world());
        yaml.set(path + ".enabled", arena.enabled());
        yaml.set(path + ".reset-interval-seconds", arena.resetIntervalSeconds());
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
        yaml.set(path + ".icon", arena.iconMaterial());
        configService.save(ConfigService.FFA);
    }
}
