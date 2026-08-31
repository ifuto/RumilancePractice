package com.rumilance.practice.practice;

import com.rumilance.practice.config.ConfigService;
import com.rumilance.practice.database.repository.PracticeLayoutRepository;
import com.rumilance.practice.lobby.LobbyService;
import com.rumilance.practice.model.PracticeRoom;
import com.rumilance.practice.session.PlayerStateManager;
import com.rumilance.practice.state.PlayerState;
import com.rumilance.practice.util.AsyncExecutor;
import com.rumilance.practice.util.Cuboid;
import com.rumilance.practice.util.ItemSerializer;
import com.rumilance.practice.util.LocationUtil;
import com.rumilance.practice.util.SafeTeleport;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Practice room store, draft flow, and live session orchestration.
 * IDs are case-sensitive (never lower-cased).
 */
public final class PracticeService {

    private final Plugin plugin;
    private final ConfigService configService;
    private final PlayerStateManager stateManager;
    private final LobbyService lobbyService;
    private final PracticeLayoutRepository layoutRepository;
    private final AsyncExecutor asyncExecutor;
    private final PracticeCloneService cloneService;

    private final Map<String, PracticeRoom> rooms = new LinkedHashMap<>();
    private final Map<String, PracticeDraft> drafts = new ConcurrentHashMap<>();
    private final Map<UUID, PracticeSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> preferredDurations = new ConcurrentHashMap<>();
    /** Ignore leave-on-exit until this epoch millis (join / teleport settle). */
    private final Map<UUID, Long> joinGraceUntilMs = new ConcurrentHashMap<>();

    private volatile java.util.function.BiConsumer<Player, PracticeSession> openLayoutGui;
    private volatile java.util.function.BiConsumer<Player, PracticeSession> openMaceGui;
    private volatile java.util.function.BiConsumer<Player, PracticeSession> openBotGui;

    private BukkitTask dailyPurgeTask;
    private BukkitTask maceAiTask;

    public PracticeService(Plugin plugin, ConfigService configService, PlayerStateManager stateManager,
                           LobbyService lobbyService, PracticeLayoutRepository layoutRepository,
                           AsyncExecutor asyncExecutor, PracticeCloneService cloneService) {
        this.plugin = plugin;
        this.configService = configService;
        this.stateManager = stateManager;
        this.lobbyService = lobbyService;
        this.layoutRepository = layoutRepository;
        this.asyncExecutor = asyncExecutor;
        this.cloneService = cloneService;
        reload();
    }

    public PracticeCloneService cloneService() {
        return cloneService;
    }

    public void setOpenLayoutGui(java.util.function.BiConsumer<Player, PracticeSession> openLayoutGui) {
        this.openLayoutGui = openLayoutGui;
    }

    public void setOpenMaceGui(java.util.function.BiConsumer<Player, PracticeSession> openMaceGui) {
        this.openMaceGui = openMaceGui;
    }

    public void setOpenBotGui(java.util.function.BiConsumer<Player, PracticeSession> openBotGui) {
        this.openBotGui = openBotGui;
    }

    public void start() {
        purgeLayoutsAsync();
        dailyPurgeTask = Bukkit.getScheduler().runTaskTimer(plugin, this::purgeLayoutsAsync,
                20L * 60L * 60L * 24L, 20L * 60L * 60L * 24L);
        maceAiTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickMaceBots, 1L, 1L);
    }

    public void stop() {
        if (dailyPurgeTask != null) {
            dailyPurgeTask.cancel();
            dailyPurgeTask = null;
        }
        if (maceAiTask != null) {
            maceAiTask.cancel();
            maceAiTask = null;
        }
        for (UUID id : List.copyOf(sessions.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                leave(player, false);
            } else {
                cleanupSession(id);
            }
        }
        if (cloneService != null) {
            cloneService.releaseAll();
        }
    }

    public void reload() {
        rooms.clear();
        FileConfiguration yaml = configService.practices();
        ConfigurationSection root = yaml.getConfigurationSection("practices");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection entry = root.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }
            // Prefer explicit id field; fall back to section key (case preserved by Yaml).
            String id = entry.getString("id", key);
            try {
                PracticeType type = PracticeType.parse(entry.getString("type", "ANKER"));
                String world = entry.getString("world", "world");
                Cuboid region = Cuboid.of(world,
                        entry.getInt("min.x"), entry.getInt("min.y"), entry.getInt("min.z"),
                        entry.getInt("max.x"), entry.getInt("max.y"), entry.getInt("max.z"));
                String spawn = entry.getString("spawn", "");
                boolean enabled = entry.getBoolean("enabled", false);
                rooms.put(id, new PracticeRoom(id, type, world, region, spawn, enabled));
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load practice room '" + id + "'", e);
            }
        }
    }

    private void persistAll() {
        FileConfiguration yaml = configService.practices();
        yaml.set("practices", null);
        for (PracticeRoom room : rooms.values()) {
            String path = "practices." + room.id();
            yaml.set(path + ".id", room.id());
            yaml.set(path + ".type", room.type().name());
            yaml.set(path + ".world", room.world());
            yaml.set(path + ".enabled", room.enabled());
            yaml.set(path + ".min.x", room.region().minX());
            yaml.set(path + ".min.y", room.region().minY());
            yaml.set(path + ".min.z", room.region().minZ());
            yaml.set(path + ".max.x", room.region().maxX());
            yaml.set(path + ".max.y", room.region().maxY());
            yaml.set(path + ".max.z", room.region().maxZ());
            Location spawn = LocationUtil.deserialize(room.serializedSpawn());
            yaml.set(path + ".spawn", room.serializedSpawn());
            yaml.set(path + ".spawn-x", spawn.getX());
            yaml.set(path + ".spawn-y", spawn.getY());
            yaml.set(path + ".spawn-z", spawn.getZ());
            yaml.set(path + ".spawn-yaw", spawn.getYaw());
            yaml.set(path + ".spawn-pitch", spawn.getPitch());
        }
        configService.save(ConfigService.PRACTICES);
    }

    public Optional<PracticeRoom> get(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(rooms.get(id));
    }

    public List<PracticeRoom> all() {
        return List.copyOf(rooms.values());
    }

    public List<PracticeRoom> enabled() {
        List<PracticeRoom> list = new ArrayList<>();
        for (PracticeRoom room : rooms.values()) {
            if (room.enabled()) {
                list.add(room);
            }
        }
        return list;
    }

    public PracticeDraft createDraft(String id, PracticeType type) {
        PracticeDraft draft = new PracticeDraft(id, type);
        drafts.put(id, draft);
        return draft;
    }

    public Optional<PracticeDraft> draft(String id) {
        return Optional.ofNullable(drafts.get(id));
    }

    public List<String> draftIds() {
        return List.copyOf(drafts.keySet());
    }

    public boolean applySelection(String id, Cuboid region) {
        PracticeDraft draft = drafts.get(id);
        if (draft != null) {
            draft.setRegion(region);
            return true;
        }
        PracticeRoom room = rooms.get(id);
        if (room != null) {
            PracticeRoom updated = room.withRegion(region);
            rooms.put(id, updated);
            persistAll();
            refreshSchematic(updated);
            return true;
        }
        return false;
    }

    public boolean setP1(String id, Location location) {
        String serialized = LocationUtil.serialize(location);
        PracticeDraft draft = drafts.get(id);
        if (draft != null) {
            draft.setSerializedSpawn(serialized);
            if (draft.region() != null) {
                draft.setRegion(padRegionForSpawn(draft.region(), location));
            }
            return true;
        }
        PracticeRoom room = rooms.get(id);
        if (room != null) {
            PracticeRoom updated = room.withRegion(padRegionForSpawn(room.region(), location)).withSpawn(serialized);
            rooms.put(id, updated);
            persistAll();
            refreshSchematic(updated);
            return true;
        }
        return false;
    }

    /** Ensure selection covers spawn + footing adjustments from SafeTeleport. */
    private static Cuboid padRegionForSpawn(Cuboid region, Location spawn) {
        Cuboid with = region.including(spawn);
        return Cuboid.of(with.worldName(),
                with.minX(), Math.min(with.minY(), spawn.getBlockY() - 2), with.minZ(),
                with.maxX(), Math.max(with.maxY(), spawn.getBlockY() + 3), with.maxZ());
    }

    public Optional<String> saveDraft(String id) {
        PracticeDraft draft = drafts.get(id);
        if (draft == null) {
            return Optional.of("No draft. /practice draft <Name> <ANKER|MACE>");
        }
        if (draft.region() == null) {
            return Optional.of("Apply a selection first: /practice selection apply " + id);
        }
        if (draft.serializedSpawn() == null || draft.serializedSpawn().isBlank()) {
            return Optional.of("Set spawn first: /practice p1 " + id);
        }
        Location spawnLoc = LocationUtil.deserialize(draft.serializedSpawn());
        Cuboid region = draft.region();
        if (spawnLoc.getWorld() == null) {
            World w = Bukkit.getWorld(region.worldName());
            if (w != null) {
                spawnLoc.setWorld(w);
            }
        }
        region = padRegionForSpawn(region, spawnLoc);
        PracticeRoom room = new PracticeRoom(draft.id(), draft.type(), region.worldName(),
                region, draft.serializedSpawn(), false);
        rooms.put(room.id(), room);
        drafts.remove(id);
        persistAll();
        refreshSchematic(room);
        return Optional.empty();
    }

    private void refreshSchematic(PracticeRoom room) {
        if (cloneService == null || !cloneService.isAvailable() || room == null) {
            return;
        }
        cloneService.ensureSchematic(room).whenComplete((ok, err) -> {
            if (err != null || !Boolean.TRUE.equals(ok)) {
                plugin.getLogger().log(Level.WARNING,
                        "[Practice] Failed to refresh schematic for '" + room.id() + "'", err);
            }
        });
    }

    public boolean setEnabled(String id, boolean enabled) {
        PracticeRoom room = rooms.get(id);
        if (room == null) {
            return false;
        }
        rooms.put(id, room.withEnabled(enabled));
        persistAll();
        return true;
    }

    public boolean delete(String id) {
        if (rooms.remove(id) == null && drafts.remove(id) == null) {
            return false;
        }
        persistAll();
        return true;
    }

    public Optional<PracticeSession> session(UUID playerId) {
        return Optional.ofNullable(sessions.get(playerId));
    }

    public boolean isInPractice(UUID playerId) {
        return sessions.containsKey(playerId);
    }

    public void join(Player player, String practiceId) {
        PracticeRoom room = rooms.get(practiceId);
        if (room == null || !room.enabled()) {
            player.sendMessage(Component.text("Practice room not found or disabled.", NamedTextColor.RED));
            return;
        }
        if (sessions.containsKey(player.getUniqueId())) {
            player.sendMessage(Component.text("Already in a practice room. /prac leave", NamedTextColor.RED));
            return;
        }
        PlayerState state = stateManager.getState(player.getUniqueId());
        if (state != PlayerState.LOBBY && state != PlayerState.OPENING_GUI) {
            player.sendMessage(Component.text("Join practice from the lobby.", NamedTextColor.RED));
            return;
        }
        Location spawn = LocationUtil.deserialize(room.serializedSpawn());
        if (spawn.getWorld() == null) {
            World world = Bukkit.getWorld(room.world());
            if (world == null) {
                player.sendMessage(Component.text("Practice world is not loaded.", NamedTextColor.RED));
                return;
            }
            spawn.setWorld(world);
        }
        // Keep spawn inside the playable cuboid (fixes instant leave after TP).
        if (!room.region().contains(spawn) && !room.region().containsHorizontal(spawn)) {
            Cuboid padded = padRegionForSpawn(room.region(), spawn);
            room = room.withRegion(padded);
            rooms.put(room.id(), room);
            persistAll();
            refreshSchematic(room);
        } else if (!room.region().contains(spawn)) {
            Cuboid padded = padRegionForSpawn(room.region(), spawn);
            room = room.withRegion(padded);
            rooms.put(room.id(), room);
            persistAll();
            refreshSchematic(room);
        }
        try {
            PlayerState target = room.type() == PracticeType.MACE
                    ? PlayerState.PRACTICE_ACTIVE
                    : PlayerState.PRACTICE_WAIT;
            stateManager.transition(player.getUniqueId(), target);
        } catch (Exception e) {
            player.sendMessage(Component.text("Cannot enter practice right now.", NamedTextColor.RED));
            return;
        }

        PracticeSession session = new PracticeSession(player.getUniqueId(), room.id(), room.type());
        int preferred = preferredDurations.getOrDefault(player.getUniqueId(), 10);
        session.setDurationSeconds(preferred);
        sessions.put(player.getUniqueId(), session);
        joinGraceUntilMs.put(player.getUniqueId(), System.currentTimeMillis() + 8000L);

        purgeLayoutsAsync();
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);

        final PracticeRoom joinedRoom = room;
        if (cloneService != null && cloneService.isAvailable()) {
            player.sendMessage(Component.text("Preparing practice room...", NamedTextColor.GRAY));
            cloneService.pasteCopy(joinedRoom, all()).whenComplete((opt, err) ->
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!player.isOnline()) {
                            if (opt != null && opt.isPresent()) {
                                cloneService.release(opt.get().instanceId());
                            }
                            cleanupSession(player.getUniqueId());
                            return;
                        }
                        PracticeSession live = sessions.get(player.getUniqueId());
                        if (live != session) {
                            if (opt != null && opt.isPresent()) {
                                cloneService.release(opt.get().instanceId());
                            }
                            return;
                        }
                        if (err != null || opt == null || opt.isEmpty()) {
                            cleanupSession(player.getUniqueId());
                            try {
                                stateManager.resetToLobby(player.getUniqueId());
                            } catch (Exception ignored) {
                            }
                            lobbyService.sendToLobby(player);
                            player.sendMessage(Component.text(
                                    "Could not prepare a practice copy. Try again.",
                                    NamedTextColor.RED));
                            return;
                        }
                        PracticeCloneService.PracticeCopy copy = opt.get();
                        session.setCloneInstanceId(copy.instanceId());
                        session.setActiveRegion(copy.region());
                        session.setActiveSpawn(copy.spawn());
                        finishJoinTeleport(player, session, joinedRoom, copy.spawn());
                    }));
            return;
        }

        if (cloneService != null) {
            cloneService.logFallbackOnce();
        }
        session.setActiveRegion(joinedRoom.region());
        session.setActiveSpawn(spawn);
        finishJoinTeleport(player, session, joinedRoom, spawn);
    }

    private void finishJoinTeleport(Player player, PracticeSession session, PracticeRoom joinedRoom, Location spawn) {
        if (spawn == null || spawn.getWorld() == null) {
            abortJoin(player, session, "Practice spawn is invalid.");
            return;
        }
        SafeTeleport.teleport(player, spawn).whenComplete((ok, err) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) {
                        cleanupSession(player.getUniqueId());
                        return;
                    }
                    PracticeSession live = sessions.get(player.getUniqueId());
                    if (live != session) {
                        return;
                    }
                    if (err != null || !Boolean.TRUE.equals(ok)) {
                        abortJoin(player, session, "Could not teleport into the practice room (unsafe spawn).");
                        return;
                    }
                    joinGraceUntilMs.put(player.getUniqueId(), System.currentTimeMillis() + 2000L);
                    if (joinedRoom.type() == PracticeType.ANKER) {
                        giveWaitHotbar(player, session);
                        player.sendMessage(Component.text(
                                "Joined practice: " + joinedRoom.displayName(), NamedTextColor.GREEN));
                    } else {
                        giveMaceLoadout(player, session);
                        spawnMaceBot(player, session, joinedRoom);
                        player.sendMessage(Component.text(
                                "Joined mace practice: " + joinedRoom.displayName(), NamedTextColor.GREEN));
                    }
                }));
    }

    private void abortJoin(Player player, PracticeSession session, String message) {
        cleanupSession(player.getUniqueId());
        try {
            stateManager.resetToLobby(player.getUniqueId());
        } catch (Exception ignored) {
        }
        lobbyService.sendToLobby(player);
        if (message != null) {
            player.sendMessage(Component.text(message, NamedTextColor.RED));
        }
    }

    public boolean isInJoinGrace(UUID playerId) {
        Long until = joinGraceUntilMs.get(playerId);
        return until != null && System.currentTimeMillis() < until;
    }

    public void leave(Player player, boolean announce) {
        leave(player, announce, true);
    }

    /**
     * Bookkeeping-only leave used when a player is pulled straight into a match: tears down the
     * practice room (timer / mace bot / clone) and resets state, but does NOT teleport to the
     * lobby — the match flow teleports them to an arena next, so a lobby teleport would race it.
     */
    public void leaveSilentlyForMatch(Player player) {
        leave(player, false, false);
    }

    private void leave(Player player, boolean announce, boolean returnToLobby) {
        PracticeSession session = sessions.remove(player.getUniqueId());
        joinGraceUntilMs.remove(player.getUniqueId());
        if (session == null) {
            if (announce) {
                player.sendMessage(Component.text("Not in a practice room.", NamedTextColor.RED));
            }
            return;
        }
        preferredDurations.put(player.getUniqueId(), session.durationSeconds());
        session.cancelTimer();
        removeMaceBot(session);
        UUID cloneId = session.cloneInstanceId();
        stateManager.resetToLobby(player.getUniqueId());
        if (returnToLobby && player.isOnline()) {
            lobbyService.sendToLobby(player);
        }
        if (cloneId != null && cloneService != null) {
            cloneService.release(cloneId);
        }
        if (announce) {
            player.sendMessage(Component.text("Left practice.", NamedTextColor.YELLOW));
        }
    }

    public void onQuit(UUID playerId) {
        joinGraceUntilMs.remove(playerId);
        PracticeSession session = sessions.remove(playerId);
        if (session != null) {
            preferredDurations.put(playerId, session.durationSeconds());
            session.cancelTimer();
            removeMaceBot(session);
            UUID cloneId = session.cloneInstanceId();
            if (cloneId != null && cloneService != null) {
                cloneService.release(cloneId);
            }
        }
        stateManager.remove(playerId);
    }

    private void cleanupSession(UUID playerId) {
        joinGraceUntilMs.remove(playerId);
        PracticeSession session = sessions.remove(playerId);
        if (session != null) {
            session.cancelTimer();
            removeMaceBot(session);
            UUID cloneId = session.cloneInstanceId();
            if (cloneId != null && cloneService != null) {
                cloneService.release(cloneId);
            }
        }
    }

    public void giveWaitHotbar(Player player, PracticeSession session) {
        player.getInventory().clear();
        player.getInventory().setItem(0, PracticeItems.durationClock(session.durationSeconds()));
        player.getInventory().setItem(1, PracticeItems.layoutSword());
        player.getInventory().setItem(4, PracticeItems.startDye());
        player.getInventory().setHeldItemSlot(4);
    }

    public void giveMaceLoadout(Player player, PracticeSession session) {
        player.getInventory().clear();
        player.getInventory().setItem(0, PracticeItems.buildMace(
                session.maceDensity(), session.maceBreach(), session.maceWindBurst()));
        player.getInventory().setItem(7, PracticeItems.maceSettings());
        player.getInventory().setItem(8, PracticeItems.botSettings(session.botShieldRaised()));
        equipPlayerMaceArmor(player);
    }

    public void refreshMaceItem(Player player, PracticeSession session) {
        player.getInventory().setItem(0, PracticeItems.buildMace(
                session.maceDensity(), session.maceBreach(), session.maceWindBurst()));
        player.getInventory().setItem(8, PracticeItems.botSettings(session.botShieldRaised()));
        applyBotShield(session);
    }

    private static void equipPlayerMaceArmor(Player player) {
        player.getInventory().setHelmet(new ItemStack(Material.NETHERITE_HELMET));
        player.getInventory().setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
        player.getInventory().setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS));
        player.getInventory().setBoots(new ItemStack(Material.NETHERITE_BOOTS));
    }

    public void handleWaitInteract(Player player, PracticeSession session, String action) {
        if (session.phase() != PracticeSession.Phase.WAIT) {
            return;
        }
        switch (action) {
            case PracticeItems.ACTION_DURATION -> {
                session.cycleDuration();
                preferredDurations.put(player.getUniqueId(), session.durationSeconds());
                player.getInventory().setItem(0, PracticeItems.durationClock(session.durationSeconds()));
                player.sendActionBar(Component.text("Duration: " + session.durationSeconds() + "s",
                        NamedTextColor.GOLD));
            }
            case PracticeItems.ACTION_LAYOUT -> {
                if (openLayoutGui != null) {
                    openLayoutGui.accept(player, session);
                }
            }
            case PracticeItems.ACTION_START -> beginAnkerCountdown(player, session);
            default -> {
            }
        }
    }

    public void handleMaceInteract(Player player, PracticeSession session, String action) {
        switch (action) {
            case PracticeItems.ACTION_MACE_SETTINGS -> {
                if (openMaceGui != null) {
                    openMaceGui.accept(player, session);
                }
            }
            case PracticeItems.ACTION_BOT_SETTINGS -> {
                if (openBotGui != null) {
                    openBotGui.accept(player, session);
                }
            }
            default -> {
            }
        }
    }

    private void beginAnkerCountdown(Player player, PracticeSession session) {
        if (session.phase() != PracticeSession.Phase.WAIT) {
            return;
        }
        session.setPhase(PracticeSession.Phase.COUNTDOWN);
        session.setPlaceBlocked(true);
        session.cancelTimer();
        final int[] remaining = {5};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || sessions.get(player.getUniqueId()) != session) {
                session.cancelTimer();
                return;
            }
            if (remaining[0] > 0) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
                player.showTitle(Title.title(
                        Component.text(String.valueOf(remaining[0]), NamedTextColor.YELLOW)
                                .decorate(TextDecoration.BOLD),
                        Component.text("Get ready", NamedTextColor.GRAY),
                        Title.Times.times(Duration.ZERO, Duration.ofMillis(800), Duration.ofMillis(100))));
                remaining[0]--;
                return;
            }
            session.cancelTimer();
            startAnkerActive(player, session);
        }, 0L, 20L);
        session.setTimerTask(task);
    }

    private void startAnkerActive(Player player, PracticeSession session) {
        PracticeRoom room = get(session.practiceId()).orElse(null);
        if (room == null) {
            player.sendMessage(Component.text("Practice room missing.", NamedTextColor.RED));
            returnToWaitSafely(player, session);
            return;
        }
        session.setPlaceBlocked(true);
        if (cloneService == null || !cloneService.isAvailable()) {
            // Shared TP fallback: still try a template re-paste when FAWE is present later;
            // without FAWE we cannot restore terrain — proceed with the run.
            beginAnkerActiveNow(player, session);
            return;
        }
        player.sendMessage(Component.text("Resetting practice terrain...", NamedTextColor.GRAY));
        cloneService.repaste(session, room).whenComplete((ok, err) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline() || sessions.get(player.getUniqueId()) != session) {
                        return;
                    }
                    if (err != null || !Boolean.TRUE.equals(ok)) {
                        player.sendMessage(Component.text(
                                "Could not reset the practice terrain. Returning to wait.",
                                NamedTextColor.RED));
                        returnToWaitSafely(player, session);
                        return;
                    }
                    beginAnkerActiveNow(player, session);
                }));
    }

    private void returnToWaitSafely(Player player, PracticeSession session) {
        session.cancelTimer();
        session.setPhase(PracticeSession.Phase.WAIT);
        session.setPlaceBlocked(false);
        try {
            if (stateManager.getState(player.getUniqueId()) == PlayerState.PRACTICE_ACTIVE
                    || stateManager.getState(player.getUniqueId()) == PlayerState.PRACTICE_WAIT) {
                // stay / return to WAIT
                if (stateManager.getState(player.getUniqueId()) == PlayerState.PRACTICE_ACTIVE) {
                    stateManager.transition(player.getUniqueId(), PlayerState.PRACTICE_WAIT);
                }
            }
        } catch (Exception ignored) {
        }
        giveWaitHotbar(player, session);
    }

    private void beginAnkerActiveNow(Player player, PracticeSession session) {
        try {
            if (stateManager.getState(player.getUniqueId()) == PlayerState.PRACTICE_WAIT) {
                stateManager.transition(player.getUniqueId(), PlayerState.PRACTICE_ACTIVE);
            }
        } catch (Exception ignored) {
        }
        session.setPhase(PracticeSession.Phase.ACTIVE);
        session.setPlaceBlocked(false);
        session.resetAnkerStats();
        session.setActiveEndsAtMs(System.currentTimeMillis() + session.durationSeconds() * 1000L);
        player.setGameMode(GameMode.SURVIVAL);
        applyAnkerLayout(player, session);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f);
        player.showTitle(Title.title(
                Component.text("START", NamedTextColor.GREEN).decorate(TextDecoration.BOLD),
                Component.text(session.durationSeconds() + "s", NamedTextColor.GRAY),
                Title.Times.times(Duration.ZERO, Duration.ofSeconds(1), Duration.ofMillis(200))));

        final int[] left = {session.durationSeconds()};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || sessions.get(player.getUniqueId()) != session) {
                session.cancelTimer();
                return;
            }
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f, 1f);
            if (left[0] <= 1) {
                session.cancelTimer();
                endAnkerRun(player, session);
                return;
            }
            left[0]--;
            player.sendActionBar(Component.text("Time: " + left[0] + "s", NamedTextColor.AQUA));
        }, 20L, 20L);
        session.setTimerTask(task);
    }

    public void applyAnkerLayout(Player player, PracticeSession session) {
        purgeLayoutsAsync();
        asyncExecutor.runAsync(() -> {
            try {
                Optional<PracticeLayoutRepository.LayoutRow> row =
                        layoutRepository.find(player.getUniqueId(), session.practiceId(), session.layoutKey());
                if (row.isEmpty()) {
                    row = layoutRepository.findLastUsed(player.getUniqueId(), session.practiceId());
                }
                ItemStack[] items;
                if (row.isPresent()) {
                    items = ItemSerializer.fromBase64(row.get().contentsBase64());
                    layoutRepository.touch(player.getUniqueId(), session.practiceId(), row.get().layoutKey());
                    session.setLayoutKey(row.get().layoutKey());
                } else {
                    items = PracticeItems.defaultLayout(session.layoutKey());
                }
                ItemStack[] finalItems = items;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    player.getInventory().clear();
                    for (int i = 0; i < finalItems.length && i < 36; i++) {
                        if (finalItems[i] != null) {
                            player.getInventory().setItem(i, finalItems[i].clone());
                        }
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed loading practice layout", e);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    player.getInventory().clear();
                    ItemStack[] def = PracticeItems.defaultLayout(session.layoutKey());
                    for (int i = 0; i < def.length; i++) {
                        player.getInventory().setItem(i, def[i]);
                    }
                });
            }
        });
    }

    public void saveLayout(Player player, PracticeSession session, String layoutKey, ItemStack[] contents) {
        session.setLayoutKey(layoutKey);
        String base64 = ItemSerializer.toBase64(contents);
        asyncExecutor.runAsync(() -> {
            try {
                layoutRepository.upsert(player.getUniqueId(), session.practiceId(), layoutKey, base64);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed saving practice layout", e);
            }
        });
        player.sendMessage(Component.text("Layout saved: " + layoutKey, NamedTextColor.GREEN));
    }

    private void endAnkerRun(Player player, PracticeSession session) {
        PracticeAnkerStats stats = session.ankerStats();
        giveStatsBook(player, stats);
        try {
            if (stateManager.getState(player.getUniqueId()) == PlayerState.PRACTICE_ACTIVE) {
                stateManager.transition(player.getUniqueId(), PlayerState.PRACTICE_WAIT);
            }
        } catch (Exception ignored) {
        }
        session.setPhase(PracticeSession.Phase.WAIT);
        session.setPlaceBlocked(false);
        giveWaitHotbar(player, session);
        player.showTitle(Title.title(
                Component.text("DONE", NamedTextColor.GOLD).decorate(TextDecoration.BOLD),
                Component.text("Check your book", NamedTextColor.GRAY),
                Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ofMillis(300))));
    }

    private void giveStatsBook(Player player, PracticeAnkerStats stats) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        meta.setTitle("Practice Results");
        meta.setAuthor("Rumilance");
        String page = """
                §6Practice Results
                §0
                Clicks: %d
                Avg CPS: %.2f
                §0
                Explode→Place: %.0f ms
                Place→Charge: %.0f ms
                Charge→Explode: %.0f ms
                """.formatted(
                stats.clicks(),
                stats.avgCps(),
                stats.avgExplodeToPlaceMs(),
                stats.avgPlaceToChargeMs(),
                stats.avgChargeToExplodeMs());
        meta.addPages(Component.text(page));
        book.setItemMeta(meta);
        player.getInventory().addItem(book);
    }

    public void onAnkerPlace(Player player, PracticeSession session, Block block) {
        if (session.phase() != PracticeSession.Phase.ACTIVE) {
            return;
        }
        Material type = block.getType();
        if (type == Material.RESPAWN_ANCHOR || type == Material.GLOWSTONE) {
            session.ankerStats().recordPlace(System.currentTimeMillis());
            session.ankerStats().recordClick(System.currentTimeMillis());
        }
    }

    public void onAnkerInteract(Player player, PracticeSession session, Block block, boolean rightClick) {
        if (session.phase() != PracticeSession.Phase.ACTIVE || block == null) {
            return;
        }
        session.ankerStats().recordClick(System.currentTimeMillis());
    }

    /** Better charge/explode tracking using anchor charges if available. */
    public void onAnchorChargeOrExplode(PracticeSession session, boolean charged, boolean exploded) {
        if (session.phase() != PracticeSession.Phase.ACTIVE) {
            return;
        }
        long now = System.currentTimeMillis();
        if (charged) {
            session.ankerStats().recordCharge(now);
        }
        if (exploded) {
            session.ankerStats().recordExplode(now);
        }
    }

    private void spawnMaceBot(Player player, PracticeSession session, PracticeRoom room) {
        removeMaceBot(session);
        Location base = session.activeSpawn();
        if (base == null) {
            base = LocationUtil.deserialize(room.serializedSpawn());
        } else {
            base = base.clone();
        }
        if (base.getWorld() == null) {
            World world = Bukkit.getWorld(room.world());
            if (world == null) {
                return;
            }
            base.setWorld(world);
        }
        Location botLoc = base.clone().add(player.getLocation().getDirection().setY(0).normalize().multiply(3));
        botLoc.setY(base.getY());
        if (botLoc.getWorld() == null) {
            return;
        }
        Mannequin bot = botLoc.getWorld().spawn(botLoc, Mannequin.class, m -> {
            m.setImmovable(true);
            m.setGravity(true);
            m.setSilent(true);
            m.setCanPickupItems(false);
            m.setRemoveWhenFarAway(false);
            m.setPersistent(false);
            m.setCollidable(true);
            m.customName(Component.text("Mace Bot", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
            m.setCustomNameVisible(true);
            m.setProfile(ResolvableProfile.resolvableProfile(player.getPlayerProfile()));
            if (m.getAttribute(Attribute.MAX_HEALTH) != null) {
                m.getAttribute(Attribute.MAX_HEALTH).setBaseValue(20.0d);
            }
            m.setHealth(20.0d);
            equipMaceBot(m, session.botShieldRaised());
        });
        session.setMaceBot(bot);
    }

    private static void equipMaceBot(Mannequin bot, boolean shieldUp) {
        EntityEquipment eq = bot.getEquipment();
        if (eq == null) {
            return;
        }
        eq.setHelmet(new ItemStack(Material.NETHERITE_HELMET));
        eq.setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
        eq.setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS));
        eq.setBoots(new ItemStack(Material.NETHERITE_BOOTS));
        eq.setItemInMainHand(new ItemStack(Material.NETHERITE_SWORD));
        eq.setItemInOffHand(shieldUp ? new ItemStack(Material.SHIELD) : null);
        eq.setHelmetDropChance(0f);
        eq.setChestplateDropChance(0f);
        eq.setLeggingsDropChance(0f);
        eq.setBootsDropChance(0f);
        eq.setItemInMainHandDropChance(0f);
        eq.setItemInOffHandDropChance(0f);
    }

    public void applyBotShield(PracticeSession session) {
        Mannequin bot = session.maceBot();
        if (bot == null || !bot.isValid()) {
            return;
        }
        EntityEquipment eq = bot.getEquipment();
        if (eq != null) {
            eq.setItemInOffHand(session.botShieldRaised() ? new ItemStack(Material.SHIELD) : null);
        }
    }

    private void removeMaceBot(PracticeSession session) {
        Mannequin bot = session.maceBot();
        if (bot != null && bot.isValid()) {
            bot.remove();
        }
        session.setMaceBot(null);
    }

    private void tickMaceBots() {
        long now = System.currentTimeMillis();
        for (PracticeSession session : sessions.values()) {
            if (session.type() != PracticeType.MACE) {
                continue;
            }
            Mannequin bot = session.maceBot();
            Player player = Bukkit.getPlayer(session.playerId());
            if (bot == null || !bot.isValid() || player == null || !player.isOnline()) {
                continue;
            }
            if (now < session.botStunUntilMs()) {
                bot.setVelocity(new Vector(0, bot.getVelocity().getY(), 0));
                continue;
            }
            Location eye = bot.getEyeLocation();
            Location target = player.getLocation().add(0, 1.0, 0);
            Vector to = target.toVector().subtract(eye.toVector());
            if (to.lengthSquared() < 0.0001) {
                continue;
            }
            Location look = eye.clone().setDirection(to.normalize());
            bot.setRotation(look.getYaw(), look.getPitch());
        }
    }

    public void onMaceHitBot(PracticeSession session) {
        if (session.botShieldRaised()) {
            session.setBotStunUntilMs(System.currentTimeMillis() + 1000L);
        }
    }

    public boolean contains(PracticeRoom room, Location location) {
        if (room == null || location == null) {
            return false;
        }
        // XZ membership is enough for leave checks; SafeTeleport may nudge Y for footing.
        return room.region().containsHorizontal(location);
    }

    /** Uses the session's pasted cuboid when present (clone occupancy). */
    public boolean contains(PracticeSession session, PracticeRoom room, Location location) {
        if (location == null) {
            return false;
        }
        Cuboid region = session != null && session.activeRegion() != null
                ? session.activeRegion()
                : (room != null ? room.region() : null);
        if (region == null) {
            return false;
        }
        return region.containsHorizontal(location);
    }

    public Optional<PracticeRoom> roomAt(Location location) {
        if (location == null || location.getWorld() == null) {
            return Optional.empty();
        }
        for (PracticeRoom room : rooms.values()) {
            if (room.enabled() && room.world().equals(location.getWorld().getName())
                    && room.region().contains(location)) {
                return Optional.of(room);
            }
        }
        return Optional.empty();
    }

    private void purgeLayoutsAsync() {
        asyncExecutor.runAsync(() -> {
            try {
                layoutRepository.purgeOlderThanSevenDays();
            } catch (Exception e) {
                plugin.getLogger().log(Level.FINE, "practice_layouts purge failed", e);
            }
        });
    }
}
