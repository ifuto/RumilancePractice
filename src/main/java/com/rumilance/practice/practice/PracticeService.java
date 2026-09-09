package com.rumilance.practice.practice;

import com.rumilance.practice.config.ConfigService;
import com.rumilance.practice.database.repository.PracticeLayoutRepository;
import com.rumilance.practice.locale.MessageService;
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
import org.bukkit.event.entity.EntityDamageEvent;
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
    private final MessageService messages;

    private final Map<String, PracticeRoom> rooms = new LinkedHashMap<>();
    private final Map<String, PracticeDraft> drafts = new ConcurrentHashMap<>();
    /** Operator-set bot home position per room id (mace/sword/crystal bots). */
    private final Map<String, String> botSpawns = new LinkedHashMap<>();
    private final Map<UUID, PracticeSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> preferredDurations = new ConcurrentHashMap<>();
    /** Ignore leave-on-exit until this epoch millis (join / teleport settle). */
    private final Map<UUID, Long> joinGraceUntilMs = new ConcurrentHashMap<>();

    private volatile java.util.function.BiConsumer<Player, PracticeSession> openLayoutGui;
    private volatile java.util.function.BiConsumer<Player, PracticeSession> openMaceGui;
    private volatile java.util.function.BiConsumer<Player, PracticeSession> openBotGui;
    private volatile java.util.function.BiConsumer<Player, PracticeSession> openDifficultyGui;
    /** Admin kit binding per bot mode (Quantum's 5 fight modes -> server kits). */
    private final java.util.Map<PracticeType, String> botModeKits =
            new java.util.EnumMap<>(PracticeType.class);
    /** Admin map binding per bot mode: fights run in the room tied to the mode's kit. */
    private final java.util.Map<PracticeType, String> botModeRooms =
            new java.util.EnumMap<>(PracticeType.class);
    /** Saved per-player difficulty (serialized), keyed by UUID string. */
    private final java.util.Map<String, String> savedDifficulty = new java.util.LinkedHashMap<>();
    /** Per-type opt-in disruption toggles (Quantum options/toggles parity — OFF by default:
     * the map's cobweb & lava buckets are menu toggles that default to disabled). */
    private final java.util.Map<PracticeType, java.util.Map<String, Boolean>> practiceToggles =
            new java.util.EnumMap<>(PracticeType.class);
    private com.rumilance.practice.kit.KitService kitService;

    private BukkitTask dailyPurgeTask;
    private BukkitTask maceAiTask;

    public PracticeService(Plugin plugin, ConfigService configService, PlayerStateManager stateManager,
                           LobbyService lobbyService, PracticeLayoutRepository layoutRepository,
                           AsyncExecutor asyncExecutor, PracticeCloneService cloneService,
                           MessageService messages) {
        this.plugin = plugin;
        this.configService = configService;
        this.stateManager = stateManager;
        this.lobbyService = lobbyService;
        this.layoutRepository = layoutRepository;
        this.asyncExecutor = asyncExecutor;
        this.cloneService = cloneService;
        this.messages = messages;
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

    /** Message service access (GUI menus building practice items need it). */
    public MessageService messagesService() {
        return messages;
    }

    public void setOpenDifficultyGui(java.util.function.BiConsumer<Player, PracticeSession> gui) {
        this.openDifficultyGui = gui;
    }

    public void setKitService(com.rumilance.practice.kit.KitService kitService) {
        this.kitService = kitService;
    }

    // ------------------------------------------------------- bot mode kit binding (admin)

    /** Binds a server kit to a bot fight mode; {@code kitName} null clears the binding. */
    public boolean bindBotKit(PracticeType type, String kitName) {
        if (!type.botMode()) {
            return false;
        }
        if (kitName == null || kitName.isBlank()) {
            botModeKits.remove(type);
        } else {
            botModeKits.put(type, kitName);
        }
        persistBotBindings();
        return true;
    }

    public String botKitFor(PracticeType type) {
        return botModeKits.get(type);
    }

    /** Binds the practice room (map) a bot mode fights in; {@code roomId} null clears it. */
    public boolean bindBotRoom(PracticeType type, String roomId) {
        if (!type.botMode()) {
            return false;
        }
        if (roomId == null || roomId.isBlank()) {
            botModeRooms.remove(type);
        } else {
            botModeRooms.put(type, roomId);
        }
        persistBotBindings();
        return true;
    }

    public String botRoomFor(PracticeType type) {
        return botModeRooms.get(type);
    }

    public boolean kitExists(String kitName) {
        return kitService != null && kitService.get(kitName).isPresent();
    }

    private void persistBotBindings() {
        FileConfiguration yaml = configService.practices();
        yaml.set("bot-mode-kits", null);
        botModeKits.forEach((type, kit) ->
                yaml.set("bot-mode-kits." + type.name(), kit));
        yaml.set("bot-mode-rooms", null);
        botModeRooms.forEach((type, roomId) ->
                yaml.set("bot-mode-rooms." + type.name(), roomId));
        configService.save(ConfigService.PRACTICES);
    }

    private void persistToggles() {
        FileConfiguration yaml = configService.practices();
        yaml.set("practice-toggles", null);
        practiceToggles.forEach((type, kinds) -> {
            if (type == null || kinds == null) {
                return;
            }
            java.util.Map<String, Boolean> out = new java.util.LinkedHashMap<>();
            kinds.forEach((kind, value) -> {
                if (Boolean.TRUE.equals(value)) {
                    out.put(kind, true); // only persist enabled entries (false = default)
                }
            });
            if (!out.isEmpty()) {
                yaml.set("practice-toggles." + type.name(), out);
            }
        });
        configService.save(ConfigService.PRACTICES);
    }

    /** Toggleable disruption kinds (Quantum options/toggles — default OFF). */
    // ---- item-bounded bot abilities ("もってるアイテムだけ使う": botgear/neth stocks) ----

    /** Re-stocks the session bot from the Quantum botgear/neth hotbars (per-type). Called on
     * every spawn/reset — after this, chaos moves/potions/rails can ONLY fire while stock lasts. */
    private void stockBotInventory(PracticeSession session, PracticeType type) {
        if (session == null || type == null) {
            return;
        }
        java.util.Map<Material, Integer> stock = session.botStock();
        stock.clear();
        switch (type) {
            case SWORD -> {
                stock.put(Material.COBWEB, 64);
                stock.put(Material.LAVA_BUCKET, 8);
                stock.put(Material.NETHERITE_AXE, 1);
                stock.put(Material.WATER_BUCKET, 1);
                stock.put(Material.ENDER_PEARL, 16);
            }
            case NETHERITE_POT -> {
                stock.put(Material.COBWEB, 64);
                stock.put(Material.LAVA_BUCKET, 8);
                stock.put(Material.NETHERITE_AXE, 1);
                stock.put(Material.WATER_BUCKET, 1);
                stock.put(Material.ENDER_PEARL, 16);
                stock.put(Material.SPLASH_POTION, 99);
            }
            case CRYSTAL -> {
                stock.put(Material.END_CRYSTAL, 64);
                stock.put(Material.OBSIDIAN, 64);
                stock.put(Material.RESPAWN_ANCHOR, 16);
                stock.put(Material.GLOWSTONE, 64);
                stock.put(Material.WATER_BUCKET, 1);
                stock.put(Material.ENDER_PEARL, 16);
            }
            case CART -> {
                stock.put(Material.POWERED_RAIL, 99);
                stock.put(Material.TNT_MINECART, 99);
                stock.put(Material.OAK_LOG, 64);
            }
            case MACE -> {
                stock.put(Material.WIND_CHARGE, 99);
                stock.put(Material.ENDER_PEARL, 32);
                stock.put(Material.WATER_BUCKET, 1);
            }
            default -> { }
        }
    }

    /** Stock check for reusable tools (axe/bucket): presence only, not consumed. */
    private static boolean botHas(PracticeSession session, Material material) {
        Integer left = session.botStock().get(material);
        return left != null && left > 0;
    }

    /** World adapter for the A* engine: stand = solid floor + two free blocks of headroom. */
    private static BotPathFinder.Passable botWorldGrid(org.bukkit.World world) {
        return (x, y, z) -> {
            org.bukkit.block.Block feet = world.getBlockAt(x, y, z);
            org.bukkit.block.Block head = world.getBlockAt(x, y + 1, z);
            org.bukkit.block.Block floor = world.getBlockAt(x, y - 1, z);
            return !feet.getType().isOccluding() && !head.getType().isOccluding()
                    && floor.getType().isSolid();
        };
    }

    private static final long BOT_PATH_REFRESH_MS = 600L;

    /**
     * Direction to follow the current A* path toward the target (herobot-style steering):
     * recomputes at most every 600ms or when the goal moved 2+ blocks; returns {@code null}
     * when pathing is exhausted/unavailable so the caller falls back to direct chasing.
     * Jump-up waypoints get the vanilla hop velocity baked into the returned vector's Y.
     */
    private org.bukkit.util.Vector botPathDirection(PracticeSession session, Mannequin bot,
                                                    Location target, long now) {
        if (session == null || bot == null || target == null || target.getWorld() == null) {
            return null;
        }
        org.bukkit.World world = target.getWorld();
        Location bLoc = bot.getLocation();
        int gx = target.getBlockX();
        int gy = target.getBlockY();
        int gz = target.getBlockZ();
        int[] last = session.botPathLastGoal();
        boolean goalStale = !session.botPathGoalSet()
                || Math.abs(last[0] - gx) + Math.abs(last[1] - gy) + Math.abs(last[2] - gz) >= 2;
        if (now >= session.botPathRefreshMs() || goalStale) {
            session.setBotPathRefreshMs(now + BOT_PATH_REFRESH_MS);
            last[0] = gx;
            last[1] = gy;
            last[2] = gz;
            session.setBotPathGoalSet(true);
            session.setBotPath(BotPathFinder.find(botWorldGrid(world),
                    new BotPathFinder.Node(bLoc.getBlockX(), bLoc.getBlockY(), bLoc.getBlockZ()),
                    new BotPathFinder.Node(gx, gy, gz), 700));
        }
        java.util.List<BotPathFinder.Node> path = session.botPath();
        int idx = session.botPathIndex();
        while (idx < path.size()) {
            BotPathFinder.Node n = path.get(idx);
            double dx = n.x() + 0.5d - bLoc.getX();
            double dz = n.z() + 0.5d - bLoc.getZ();
            double dyy = n.y() - bLoc.getY();
            if (dx * dx + dz * dz < 0.3d * 0.3d && Math.abs(dyy) < 1.6d) {
                idx++;
            } else {
                break;
            }
        }
        if (idx >= path.size()) {
            session.setBotPathIndex(idx);
            return null;
        }
        session.setBotPathIndex(idx);
        BotPathFinder.Node n = path.get(idx);
        org.bukkit.util.Vector toward = new org.bukkit.util.Vector(
                n.x() + 0.5d - bLoc.getX(), 0, n.z() + 0.5d - bLoc.getZ());
        if (toward.lengthSquared() < 1.0e-6) {
            return null;
        }
        toward.normalize();
        if (n.y() > bLoc.getBlockY() && bot.isOnGround()) {
            toward.setY(0.45d); // hop the 1-block step the path says to climb
        } else {
            toward.setY(bot.getVelocity().getY());
        }
        return toward;
    }

    public static final java.util.Set<String> DISRUPTION_KINDS = java.util.Set.of("cobweb", "lava");

    /** Whether a disruption kind is enabled for a practice type (default OFF). */
    public boolean disruptionEnabled(PracticeType type, String kind) {
        java.util.Map<String, Boolean> map = practiceToggles.get(type);
        return map != null && Boolean.TRUE.equals(map.get(kind.toLowerCase(java.util.Locale.ROOT)));
    }

    /** Sets one disruption toggle for a type and persists it. False unsets (back to default). */
    public boolean setDisruption(PracticeType type, String kind, boolean value) {
        if (type == null || !DISRUPTION_KINDS.contains(kind == null ? "" : kind.toLowerCase(java.util.Locale.ROOT))) {
            return false;
        }
        String key = kind.toLowerCase(java.util.Locale.ROOT);
        java.util.Map<String, Boolean> map = practiceToggles.computeIfAbsent(type, t -> new java.util.HashMap<>());
        if (value) {
            map.put(key, true);
        } else {
            map.remove(key);
            if (map.isEmpty()) {
                practiceToggles.remove(type);
            }
        }
        persistToggles();
        return true;
    }

    private void persistDifficulties() {
        FileConfiguration yaml = configService.practices();
        yaml.set("bot-difficulty", null);
        savedDifficulty.forEach((uuid, ser) -> yaml.set("bot-difficulty." + uuid, ser));
        configService.save(ConfigService.PRACTICES);
    }

    /** Loads the player's saved difficulty into the session (or NORMAL). */
    public void applySavedDifficulty(PracticeSession session) {
        String ser = savedDifficulty.get(session.playerId().toString());
        session.setDifficulty(BotDifficulty.deserialize(ser));
    }

    /** Saves the player's difficulty choice (called from the difficulty GUI). */
    public void saveDifficulty(java.util.UUID playerId, BotDifficulty difficulty) {
        savedDifficulty.put(playerId.toString(), difficulty.serialize());
        persistDifficulties();
    }

    public void start() {
        purgeLayoutsAsync();
        dailyPurgeTask = Bukkit.getScheduler().runTaskTimer(plugin, this::purgeLayoutsAsync,
                20L * 60L * 60L * 24L, 20L * 60L * 60L * 24L);
        maceAiTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            tickMaceBots();
            tickCombatBots();
        }, 1L, 1L);
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
        botModeKits.clear();
        botModeRooms.clear();
        savedDifficulty.clear();
        FileConfiguration yaml = configService.practices();
        practiceToggles.clear();
        ConfigurationSection toggles = yaml.getConfigurationSection("practice-toggles");
        if (toggles != null) {
            for (String typeKey : toggles.getKeys(false)) {
                try {
                    PracticeType type = PracticeType.parse(typeKey);
                    ConfigurationSection sec = yaml.getConfigurationSection(
                            "practice-toggles." + typeKey);
                    if (sec != null) {
                        java.util.Map<String, Boolean> map = new java.util.HashMap<>();
                        for (String kind : sec.getKeys(false)) {
                            map.put(kind.toLowerCase(java.util.Locale.ROOT),
                                    sec.getBoolean(kind, false));
                        }
                        practiceToggles.put(type, map);
                    }
                } catch (Exception ignored) {
                }
            }
        }
        ConfigurationSection kits = yaml.getConfigurationSection("bot-mode-kits");
        if (kits != null) {
            for (String key : kits.getKeys(false)) {
                try {
                    botModeKits.put(PracticeType.parse(key), kits.getString(key, ""));
                } catch (Exception ignored) {
                }
            }
        }
        ConfigurationSection maps = yaml.getConfigurationSection("bot-mode-rooms");
        if (maps != null) {
            for (String key : maps.getKeys(false)) {
                try {
                    botModeRooms.put(PracticeType.parse(key), maps.getString(key, ""));
                } catch (Exception ignored) {
                }
            }
        }
        ConfigurationSection diffs = yaml.getConfigurationSection("bot-difficulty");
        if (diffs != null) {
            for (String key : diffs.getKeys(false)) {
                savedDifficulty.put(key, diffs.getString(key, ""));
            }
        }
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
                String botSpawn = entry.getString("bot-spawn", "");
                if (botSpawn != null && !botSpawn.isBlank()) {
                    botSpawns.put(id, botSpawn);
                } else {
                    botSpawns.remove(id);
                }
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
            String botSpawn = botSpawns.get(room.id());
            if (botSpawn != null && !botSpawn.isBlank()) {
                yaml.set(path + ".bot-spawn", botSpawn);
            }
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

    /**
     * Operator sets where the practice bot lives in this arena (works for draft or saved
     * room; any bot type — mace, sword, crystal). Pass {@code null} to clear.
     */
    public boolean setBotSpawn(String id, Location location) {
        String serialized = location == null ? null : LocationUtil.serialize(location);
        PracticeDraft draft = drafts.get(id);
        if (draft != null) {
            draft.setSerializedBotSpawn(serialized);
            return true;
        }
        PracticeRoom room = rooms.get(id);
        if (room != null) {
            if (serialized == null || serialized.isBlank()) {
                botSpawns.remove(id);
            } else {
                botSpawns.put(id, serialized);
            }
            persistAll();
            return true;
        }
        return false;
    }

    /** Serialized bot home for a room id, if the operator configured one. */
    public String botSpawnFor(String roomId) {
        PracticeDraft draft = drafts.get(roomId);
        if (draft != null && draft.serializedBotSpawn() != null && !draft.serializedBotSpawn().isBlank()) {
            return draft.serializedBotSpawn();
        }
        return botSpawns.get(roomId);
    }

    /**
     * Resolves the operator-configured bot home into a live location, mapping it through the
     * clone offset when the session runs on a pasted copy. {@code null} when unconfigured.
     */
    private Location resolveConfiguredBotSpawn(PracticeSession session, PracticeRoom room) {
        String configured = botSpawnFor(room.id());
        if (configured == null || configured.isBlank()) {
            return null;
        }
        Location cfg = LocationUtil.deserialize(configured);
        if (cfg.getWorld() == null) {
            cfg.setWorld(Bukkit.getWorld(room.world()));
        }
        Location roomSpawn = LocationUtil.deserialize(room.serializedSpawn());
        Location active = session.activeSpawn();
        if (cfg.getWorld() != null && roomSpawn != null && active != null) {
            cfg.add(active.toVector().subtract(roomSpawn.toVector()));
        }
        return cfg.getWorld() == null ? null : cfg;
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
        if (draft.serializedBotSpawn() != null && !draft.serializedBotSpawn().isBlank()) {
            botSpawns.put(room.id(), draft.serializedBotSpawn());
        }
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
        botSpawns.remove(id);
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
            player.sendMessage(messages.render(player, "practice.room-not-found"));
            return;
        }
        if (sessions.containsKey(player.getUniqueId())) {
            player.sendMessage(messages.render(player, "practice.already-in"));
            return;
        }
        if (isRoomBusy(practiceId)) {
            player.sendMessage(messages.render(player, "gui.practice-room-busy"));
            return;
        }
        PlayerState state = stateManager.getState(player.getUniqueId());
        if (state != PlayerState.LOBBY && state != PlayerState.OPENING_GUI) {
            player.sendMessage(messages.render(player, "practice.join-from-lobby"));
            return;
        }
        Location spawn = LocationUtil.deserialize(room.serializedSpawn());
        if (spawn.getWorld() == null) {
            World world = Bukkit.getWorld(room.world());
            if (world == null) {
                player.sendMessage(messages.render(player, "practice.world-not-loaded"));
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
            // Every room now opens in its lobby/wait phase; the fight starts on purpose.
            stateManager.transition(player.getUniqueId(), PlayerState.PRACTICE_WAIT);
        } catch (Exception e) {
            player.sendMessage(messages.render(player, "practice.cannot-enter"));
            return;
        }

        PracticeSession session = new PracticeSession(player.getUniqueId(), room.id(), room.type());
        int preferred = preferredDurations.getOrDefault(player.getUniqueId(), 10);
        session.setDurationSeconds(preferred);
        applySavedDifficulty(session);
        sessions.put(player.getUniqueId(), session);
        joinGraceUntilMs.put(player.getUniqueId(), System.currentTimeMillis() + 8000L);

        purgeLayoutsAsync();
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);

        final PracticeRoom joinedRoom = room;
        if (cloneService != null && cloneService.isAvailable()) {
            player.sendMessage(messages.render(player, "practice.preparing-room"));
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
                            player.sendMessage(messages.render(player, "practice.copy-failed"));
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
            abortJoin(player, session, "practice.spawn-invalid");
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
                        abortJoin(player, session, "practice.teleport-unsafe");
                        return;
                    }
                    joinGraceUntilMs.put(player.getUniqueId(), System.currentTimeMillis() + 2000L);
                    if (joinedRoom.type() == PracticeType.ANKER) {
                        giveWaitHotbar(player, session);
                        player.sendMessage(messages.render(player, "practice.joined",
                                MessageService.tags("name", joinedRoom.displayName())));
                    } else {
                        giveBotWaitHotbar(player, session);
                        String key = switch (joinedRoom.type()) {
                            case SWORD -> "practice.joined-sword";
                            case CRYSTAL -> "practice.joined-crystal";
                            case NETHERITE_POT -> "practice.joined-nethpot";
                            case CART -> "practice.joined-cart";
                            default -> "practice.joined-mace";
                        };
                        player.sendMessage(messages.render(player, key,
                                MessageService.tags("name", joinedRoom.displayName())));
                    }
                }));
    }

    private void abortJoin(Player player, PracticeSession session, String langKey) {
        cleanupSession(player.getUniqueId());
        try {
            stateManager.resetToLobby(player.getUniqueId());
        } catch (Exception ignored) {
        }
        lobbyService.sendToLobby(player);
        if (langKey != null) {
            player.sendMessage(messages.render(player, langKey));
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
                player.sendMessage(messages.render(player, "practice.not-in-room"));
            }
            return;
        }
        preferredDurations.put(player.getUniqueId(), session.durationSeconds());
        session.cancelTimer();
        removeMaceBot(session);
        removeCombatBot(session);
        UUID cloneId = session.cloneInstanceId();
        stateManager.resetToLobby(player.getUniqueId());
        if (returnToLobby && player.isOnline()) {
            lobbyService.sendToLobby(player);
        }
        if (cloneId != null && cloneService != null) {
            cloneService.release(cloneId);
        }
        if (announce) {
            player.sendMessage(messages.render(player, "practice.left"));
        }
    }

    public void onQuit(UUID playerId) {
        joinGraceUntilMs.remove(playerId);
        PracticeSession session = sessions.remove(playerId);
        if (session != null) {
            preferredDurations.put(playerId, session.durationSeconds());
            session.cancelTimer();
            removeMaceBot(session);
            removeCombatBot(session);
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
            removeCombatBot(session);
            UUID cloneId = session.cloneInstanceId();
            if (cloneId != null && cloneService != null) {
                cloneService.release(cloneId);
            }
        }
    }

    public void giveWaitHotbar(Player player, PracticeSession session) {
        player.getInventory().clear();
        player.getInventory().setItem(0, PracticeItems.durationClock(messages, player, session.durationSeconds()));
        player.getInventory().setItem(1, PracticeItems.layoutSword(messages, player));
        player.getInventory().setItem(4, PracticeItems.startDye(messages, player));
        player.getInventory().setHeldItemSlot(4);
    }

    /** Bot-room waiting kit: difficulty, start, bot shield toggle (limit is fixed at 10 min). */
    public void giveBotWaitHotbar(Player player, PracticeSession session) {
        player.getInventory().clear();
        player.getInventory().setItem(1, PracticeItems.botDifficulty(messages, player, session.difficulty()));
        player.getInventory().setItem(4, PracticeItems.startDye(messages, player));
        player.getInventory().setItem(8, PracticeItems.botSettings(messages, player, session.botShieldRaised()));
        player.getInventory().setHeldItemSlot(4);
    }

    public void giveMaceLoadout(Player player, PracticeSession session) {
        player.getInventory().clear();
        player.getInventory().setItem(0, PracticeItems.buildMace(messages, player,
                session.maceDensity(), session.maceBreach(), session.maceWindBurst()));
        player.getInventory().setItem(7, PracticeItems.maceSettings(messages, player));
        player.getInventory().setItem(8, PracticeItems.botSettings(messages, player, session.botShieldRaised()));
        equipPlayerMaceArmor(player);
    }

    public void refreshMaceItem(Player player, PracticeSession session) {
        player.getInventory().setItem(0, PracticeItems.buildMace(messages, player,
                session.maceDensity(), session.maceBreach(), session.maceWindBurst()));
        player.getInventory().setItem(8, PracticeItems.botSettings(messages, player, session.botShieldRaised()));
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
                player.getInventory().setItem(0, PracticeItems.durationClock(messages, player, session.durationSeconds()));
                player.sendActionBar(messages.render(player, "practice.duration-bar",
                        MessageService.tags("secs", String.valueOf(session.durationSeconds()))));
            }
            case PracticeItems.ACTION_LAYOUT -> {
                if (openLayoutGui != null) {
                    openLayoutGui.accept(player, session);
                }
            }
            case PracticeItems.ACTION_BOT_SETTINGS -> {
                if (openBotGui != null) {
                    openBotGui.accept(player, session);
                }
            }
            case PracticeItems.ACTION_DIFFICULTY -> {
                if (openDifficultyGui != null) {
                    openDifficultyGui.accept(player, session);
                }
            }
            case PracticeItems.ACTION_START -> {
                if (session.type() == PracticeType.ANKER) {
                    beginAnkerCountdown(player, session);
                } else {
                    beginBotCountdown(player, session);
                }
            }
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

    // ---------------------------------------------------------------- bot matches (ITEM 41+)

    public enum BotMatchResult {
        WIN, LOSE, DRAW
    }

    /** Bot fights are capped at ten minutes; no decision by then ends the match as a draw. */
    public static final long BOT_MATCH_LIMIT_SECONDS = 600L;

    // --- mace bot (Quantum parity: quantum:mace/tick, mace/lunge, mace/wind) ---------------
    /** Fall distance from which a mace hit counts as a smash attack (a normal jump qualifies). */
    private static final double MACE_SMASH_FALL_BLOCKS = 0.9d;
    /** Extra smash damage per block fallen past that, and the cap on the whole scale. */
    private static final double MACE_SMASH_PER_BLOCK = 0.35d;
    private static final double MACE_SMASH_MAX_SCALE = 3.0d;
    /** Lunge window: sprint-jump at the player from here and smash on the way down. */
    private static final double MACE_LUNGE_MIN_RANGE = 2.2d;
    private static final double MACE_LUNGE_MAX_RANGE = 5.0d;
    private static final double MACE_LUNGE_UP = 0.55d;
    private static final double MACE_LUNGE_FORWARD = 0.32d;
    /** Recovery after a committed smash: the mace swing is slow, so is the bot. */
    private static final long MACE_LAND_RECOVERY_MS = 350L;
    /** Wind-charge self-launch: needs room so the burst does not shove the player off a ledge. */
    private static final double MACE_WIND_MIN_RANGE = 4.0d;
    private static final long MACE_WIND_COOLDOWN_MS = 5200L;
    /** How long a swing into the bot's raised shield costs the player. */
    private static final long MACE_SHIELD_STUN_MS = 1000L;
    // --- Quantum-parity combat abilities (sword/crit, cobwebs, decisions...) ----------
    /** Sword bot jump-crit cadence: at fastest, every 5th swing window, scaled by the rung. */
    private static final long CRIT_MIN_INTERVAL_MS = 1400L;
    /** Escape pearl: bot wounded this badly (fraction of max HP) may pearl out. */
    private static final double ESCAPE_PEARL_HP_FRACTION = 0.35d;
    /** Cooldown for the escape pearl (map: pearlcd 20 ticks + spread reacquire time). */
    private static final long ESCAPE_PEARL_COOLDOWN_MS = 9000L;
    /** Golden apple: eaten below half HP, heals 40%, at most twice per bot life. */
    private static final long GAP_COOLDOWN_MS = 500L;
    private static final double GAP_HEAL_FRACTION = 0.4d;
    private static final int GAP_MAX_USES = 2;
    /** Cobweb trick: placed under the player, melts away after TTL. */
    private static final long COBWEB_COOLDOWN_MS = 6000L;
    private static final long COBWEB_TTL_MS = 8000L;
    /** Self water bucket: puts the bot out / washes webs, small soak visual. */
    private static final long WATER_COOLDOWN_MS = 9000L;
    private static final long WATER_TTL_MS = 1500L;
    /** Lava bucket under an airborne player (very short lived so rooms stay clean). */
    private static final long LAVA_COOLDOWN_MS = 8000L;
    private static final long LAVA_TTL_MS = 1800L;
    /** Axe swing that disables a raised player shield (vanilla-style 4s shield cooldown). */
    private static final long AXE_COOLDOWN_MS = 7000L;
    private static final int AXE_SHIELD_DISABLE_TICKS = 80;
    /** Sword bow: used beyond melee range. */
    private static final long BOW_COOLDOWN_MS = 3500L;
    /** Crystal crossbow sniping: mid range poke between combos. */
    private static final double CROSSBOW_MIN_RANGE = 5.5d;
    private static final double CROSSBOW_MAX_RANGE = 15.0d;
    /** Respawn-anchor mixup (g1gc): only from HARD upward, every other combo at most. */
    private static final long ANCHOR_MIN_COOLDOWN_MS = 6500L;
    /** Defensive block wall (crystal obsidian / cart oak log). */
    private static final long DEFENSE_BLOCK_COOLDOWN_MS = 9000L;
    private static final long DEFENSE_BLOCK_TTL_MS = 7000L;
    /** Mace far-pearl engage when the player kites beyond melee. */
    private static final long FAR_PEARL_COOLDOWN_MS = 8000L;
    private static final double FAR_PEARL_MIN_RANGE = 8.0d;
    private static final double FAR_PEARL_MAX_RANGE = 24.0d;
    /** Mace wind+forward burst (wind_pearl) and elytra-style rocket engages. */
    private static final long WIND_PEARL_COOLDOWN_MS = 9000L;
    private static final long ELYTRA_COOLDOWN_MS = 12000L;
    /** Generic pedestal / quick-block TTLs for the tracked-block reverter. */
    private static final long PEDESTAL_TTL_MS = 7000L;
    private static final long CART_RAIL_TTL_MS = 8000L;

    private void beginBotCountdown(Player player, PracticeSession session) {
        if (session.phase() != PracticeSession.Phase.WAIT) {
            return;
        }
        session.setPhase(PracticeSession.Phase.COUNTDOWN);
        session.setPlaceBlocked(true);
        session.cancelTimer();
        final int[] remaining = {5};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || player.isDead()
                    || sessions.get(player.getUniqueId()) != session
                    || session.phase() != PracticeSession.Phase.COUNTDOWN) {
                session.cancelTimer();
                return;
            }
            if (remaining[0] > 0) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
                player.showTitle(Title.title(
                        Component.text(String.valueOf(remaining[0]), NamedTextColor.YELLOW)
                                .decorate(TextDecoration.BOLD),
                        messages.render(player, "practice.countdown-sub"),
                        Title.Times.times(Duration.ZERO, Duration.ofMillis(800), Duration.ofMillis(100))));
                remaining[0]--;
                return;
            }
            session.cancelTimer();
            startBotMatch(player, session);
        }, 0L, 20L);
        session.setTimerTask(task);
    }

    private void startBotMatch(Player player, PracticeSession session) {
        if (session.phase() != PracticeSession.Phase.COUNTDOWN) {
            return;
        }
        PracticeRoom room = get(session.practiceId()).orElse(null);
        if (room == null) {
            player.sendMessage(messages.render(player, "practice.room-missing"));
            leave(player, true);
            return;
        }
        session.setPhase(PracticeSession.Phase.ACTIVE);
        session.setMatchStartMs(System.currentTimeMillis());
        session.setBotPops(0);
        giveBotLoadout(player, session, room);
        if (room.type() == PracticeType.MACE) {
            spawnMaceBot(player, session, room);
        } else {
            spawnCombatBot(player, session, room, room.type());
        }
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.4f);
        player.sendActionBar(messages.render(player, "practice.match-started",
                MessageService.tags("mode", modeName(player, room.type()))));
        Bukkit.getLogger().info("[N Arena][BotMatch] START player=" + player.getName()
                + " mode=" + room.type() + " room=" + room.id()
                + " difficulty=" + session.difficulty().preset());
        // Time limit: ten minutes without a decision ends the match as a draw.
        BukkitTask timeout = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            PracticeSession live = sessions.get(player.getUniqueId());
            if (live == session && live.phase() == PracticeSession.Phase.ACTIVE
                    && player.isOnline()) {
                endBotMatch(player, live, BotMatchResult.DRAW);
            }
        }, BOT_MATCH_LIMIT_SECONDS * 20L);
        session.setTimerTask(timeout);
    }

    private String modeName(Player player, PracticeType type) {
        String key = switch (type) {
            case SWORD -> "gui.room-type-sword";
            case CRYSTAL -> "gui.room-type-crystal";
            case NETHERITE_POT -> "gui.room-type-nethpot";
            case CART -> "gui.room-type-cart";
            default -> "gui.room-type-mace";
        };
        return messages.raw(player, key);
    }

    /**
     * Kit rules that apply to a practice session: the admin-bound server kit of the room's bot
     * mode, or {@code null} when the mode runs on its built-in gear (no kit rules -> defaults,
     * i.e. totems allowed). Used by the totem guarantee and the bed-explosion rule.
     */
    public com.rumilance.practice.model.KitDefinition kitOf(PracticeSession session) {
        if (session == null || kitService == null) {
            return null;
        }
        String boundKit = botModeKits.get(session.type());
        if (boundKit == null || boundKit.isBlank()) {
            return null;
        }
        return kitService.get(boundKit).orElse(null);
    }

    /** Re-applies the session's fight loadout (after a rescued death, a kit change, ...). */
    public void refreshBotLoadout(Player player, PracticeSession session) {
        if (player == null || session == null) {
            return;
        }
        PracticeRoom room = get(session.practiceId()).orElse(null);
        if (room == null) {
            return;
        }
        giveBotLoadout(player, session, room);
    }

    /** Player loadout: the admin-bound server kit wins, else the mode's built-in gear. */
    private void giveBotLoadout(Player player, PracticeSession session, PracticeRoom room) {
        String boundKit = botModeKits.get(room.type());
        if (boundKit != null && !boundKit.isBlank() && kitService != null) {
            var kitOpt = kitService.get(boundKit);
            if (kitOpt.isPresent()) {
                player.getInventory().clear();
                player.getInventory().setArmorContents(null);
                kitService.apply(player, kitOpt.get());
                player.getInventory().setItem(8,
                        PracticeItems.botSettings(messages, player, session.botShieldRaised()));
                return;
            }
            plugin.getLogger().warning("[N Arena] Bot kit binding '" + boundKit
                    + "' for mode " + room.type() + " not found; using default gear.");
        }
        switch (room.type()) {
            case MACE -> giveMaceLoadout(player, session);
            case SWORD -> giveSwordLoadout(player, session);
            case CRYSTAL -> giveCrystalLoadout(player, session);
            case NETHERITE_POT -> giveNethPotLoadout(player, session);
            case CART -> giveCartLoadout(player, session);
            default -> { }
        }
    }

    public void endBotMatch(Player player, PracticeSession session, BotMatchResult result) {
        if (session.phase() == PracticeSession.Phase.ENDED) {
            return;
        }
        session.setPhase(PracticeSession.Phase.ENDED);
        session.cancelTimer();
        long seconds = session.matchStartMs() <= 0 ? 0
                : (System.currentTimeMillis() - session.matchStartMs()) / 1000L;
        PracticeRoom room = get(session.practiceId()).orElse(null);
        String mode = room == null ? String.valueOf(session.type()) : room.type().name();
        String roomId = room == null ? "?" : room.id();
        Bukkit.getLogger().info("[N Arena][BotMatch] END player=" + player.getName()
                + " mode=" + mode + " room=" + roomId + " result=" + result
                + " pops=" + session.botPops() + " duration=" + seconds + "s"
                + " difficulty=" + session.difficulty().preset());
        NamedTextColor color = switch (result) {
            case WIN -> NamedTextColor.GREEN;
            case LOSE -> NamedTextColor.RED;
            case DRAW -> NamedTextColor.YELLOW;
        };
        String key = switch (result) {
            case WIN -> "practice.bot-result-win";
            case LOSE -> "practice.bot-result-lose";
            case DRAW -> "practice.bot-result-draw";
        };
        player.showTitle(Title.title(
                messages.render(player, key).color(color).decorate(TextDecoration.BOLD),
                messages.render(player, "practice.bot-result-sub",
                        MessageService.tags("secs", String.valueOf(seconds),
                                "pops", String.valueOf(session.botPops()))),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2500), Duration.ofMillis(400))));
        player.playSound(player.getLocation(),
                result == BotMatchResult.WIN ? Sound.UI_TOAST_CHALLENGE_COMPLETE
                        : Sound.ENTITY_VILLAGER_NO, 1f, 1f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                if (player.isDead()) {
                    // A defeated player is still on the death screen — respawn first, then home.
                    player.spigot().respawn();
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (player.isOnline()) {
                            leave(player, true);
                        }
                    });
                } else {
                    leave(player, true);
                }
            }
        }, 60L);
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
                        messages.render(player, "practice.countdown-sub"),
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
            player.sendMessage(messages.render(player, "practice.room-missing"));
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
        player.sendMessage(messages.render(player, "practice.resetting-terrain"));
        cloneService.repaste(session, room).whenComplete((ok, err) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline() || sessions.get(player.getUniqueId()) != session) {
                        return;
                    }
                    if (err != null || !Boolean.TRUE.equals(ok)) {
                        player.sendMessage(messages.render(player, "practice.reset-failed"));
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
                messages.render(player, "practice.start-title"),
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
            player.sendActionBar(messages.render(player, "practice.time-bar",
                    MessageService.tags("secs", String.valueOf(left[0]))));
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
        player.sendMessage(messages.render(player, "practice.layout-saved",
                MessageService.tags("key", layoutKey)));
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
                messages.render(player, "practice.done-title"),
                messages.render(player, "practice.check-book"),
                Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ofMillis(300))));
    }

    private void giveStatsBook(Player player, PracticeAnkerStats stats) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        String bookTitle = messages.raw(player, "practice.book-title");
        meta.setTitle(bookTitle);
        meta.setAuthor("N Arena");
        String page = """
                §6%s
                §0
                %s: %d
                %s: %.2f
                §0
                %s: %.0f ms
                %s: %.0f ms
                %s: %.0f ms
                """.formatted(
                bookTitle,
                messages.raw(player, "practice.book-clicks"),
                stats.clicks(),
                messages.raw(player, "practice.book-avg-cps"),
                stats.avgCps(),
                messages.raw(player, "practice.book-explode-place"),
                stats.avgExplodeToPlaceMs(),
                messages.raw(player, "practice.book-place-charge"),
                stats.avgPlaceToChargeMs(),
                messages.raw(player, "practice.book-charge-explode"),
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
        Location botLoc = resolveConfiguredBotSpawn(session, room);
        if (botLoc == null) {
            botLoc = base.clone().add(player.getLocation().getDirection().setY(0).normalize().multiply(3));
            botLoc.setY(base.getY());
        }
        if (botLoc.getWorld() == null) {
            return;
        }
        // The dummy is a fighter: it walks in, lunges and smashes, so it needs the same body
        // as every other bot - movable, and as tanky as the rung says.
        double maxHp = session.difficulty().botMaxHp();
        Mannequin bot = botLoc.getWorld().spawn(botLoc, Mannequin.class, m -> {
            m.setImmovable(false);
            m.setGravity(true);
            m.setSilent(true);
            m.setCanPickupItems(false);
            m.setRemoveWhenFarAway(false);
            m.setPersistent(false);
            m.setCollidable(true);
            m.customName(messages.render(player, "practice.mace-bot-name"));
            m.setCustomNameVisible(true);
            m.setProfile(ResolvableProfile.resolvableProfile(player.getPlayerProfile()));
            if (m.getAttribute(Attribute.MAX_HEALTH) != null) {
                m.getAttribute(Attribute.MAX_HEALTH).setBaseValue(maxHp);
            }
            m.setHealth(maxHp);
            equipMaceBot(m, session.botShieldRaised());
        });
        stockBotInventory(session, PracticeType.MACE);
        session.setMaceBot(bot);
        session.setBotHome(botLoc.clone());
        long now = System.currentTimeMillis();
        session.setBotNextAttackMs(now + 2000L);
        session.setBotNextLungeMs(now + 1200L);
        session.setBotNextWindMs(now + MACE_WIND_COOLDOWN_MS);
        session.setBotStrafeFlipMs(now + 1500L);
    }

    private void equipMaceBot(Mannequin bot, boolean shieldUp) {
        EntityEquipment eq = bot.getEquipment();
        if (eq == null) {
            return;
        }
        // Admin binding first: the dummy wears the same kit the player fights with.
        if (applyBoundBotKit(PracticeType.MACE, bot, eq, shieldUp, new ItemStack(Material.MACE))) {
            return;
        }
        eq.setHelmet(new ItemStack(Material.NETHERITE_HELMET));
        eq.setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
        eq.setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS));
        eq.setBoots(new ItemStack(Material.NETHERITE_BOOTS));
        // A mace, not a sword: the player is practising smash-attack trades, so the dummy has
        // to swing the weapon the mode is about.
        eq.setItemInMainHand(new ItemStack(Material.MACE));
        eq.setItemInOffHand(shieldUp ? new ItemStack(Material.SHIELD) : null);
        zeroDropChances(bot, eq);
    }

    /**
     * Equips a bot from the admin-bound server kit of its mode (same loadout the player
     * receives): armor pieces, first hotbar item as the main weapon (falling back to the
     * mode's iconic weapon) and the kit offhand (falling back to the shield state).
     *
     * @return {@code true} when a bound kit was found and applied, {@code false} when the
     *         caller should fall back to the built-in mode gear.
     */
    /** Zeroes equipment drop chances — only for Mob equipment. Paper's Mannequin equipment
     * rejects drop chances (CraftBukkit: "Cannot set drop chance for non-Mob entity"), and a
     * Mannequin never drops gear anyway. */
    private static void zeroDropChances(org.bukkit.entity.LivingEntity owner, EntityEquipment eq) {
        if (!(owner instanceof org.bukkit.entity.Mob)) {
            return;
        }
        eq.setHelmetDropChance(0f);
        eq.setChestplateDropChance(0f);
        eq.setLeggingsDropChance(0f);
        eq.setBootsDropChance(0f);
        eq.setItemInMainHandDropChance(0f);
        eq.setItemInOffHandDropChance(0f);
    }

    private boolean applyBoundBotKit(PracticeType type, org.bukkit.entity.LivingEntity owner,
                                     EntityEquipment eq, boolean shieldUp, ItemStack fallbackWeapon) {
        if (eq == null || kitService == null) {
            return false;
        }
        String bound = botModeKits.get(type);
        if (bound == null || bound.isBlank()) {
            return false;
        }
        com.rumilance.practice.model.KitDefinition kit;
        try {
            kit = kitService.get(bound).orElse(null);
        } catch (RuntimeException e) {
            kit = null;
        }
        if (kit == null) {
            return false;
        }
        eq.setHelmet(kitArmorPiece(kit, "helmet"));
        eq.setChestplate(kitArmorPiece(kit, "chestplate"));
        eq.setLeggings(kitArmorPiece(kit, "leggings"));
        eq.setBoots(kitArmorPiece(kit, "boots"));
        ItemStack main = kitFirstHotbarItem(kit);
        eq.setItemInMainHand(main != null ? main : fallbackWeapon);
        ItemStack off = kitEntryStack(kit, BOT_KIT_OFFHAND_SLOT);
        if (off != null) {
            eq.setItemInOffHand(off);
        } else {
            eq.setItemInOffHand(shieldUp ? new ItemStack(Material.SHIELD) : null);
        }
        zeroDropChances(owner, eq);
        return true;
    }

    /** Kit offhand slot convention (see KitService.OFFHAND_SLOT). */
    private static final int BOT_KIT_OFFHAND_SLOT =
            com.rumilance.practice.kit.KitService.OFFHAND_SLOT;
    /** Prefix marking an armor value that stores a full serialized item, not just a material. */
    private static final String BOT_KIT_ARMOR_DATA_PREFIX = "data:";

    /** One armor piece of a kit: plain material name, or a fully serialized stack ("data:"). */
    private static ItemStack kitArmorPiece(com.rumilance.practice.model.KitDefinition kit,
                                           String key) {
        String raw = kit.armor().get(key);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            if (raw.startsWith(BOT_KIT_ARMOR_DATA_PREFIX)) {
                return com.rumilance.practice.util.ItemSerializer.singleFromBase64(
                        raw.substring(BOT_KIT_ARMOR_DATA_PREFIX.length()));
            }
            return new ItemStack(Material.valueOf(raw));
        } catch (IllegalArgumentException | NullPointerException e) {
            return null; // unknown material / corrupt serialization -> keep the slot empty
        }
    }

    /** First non-air hotbar item (slots 0-8) of a kit, or {@code null} when there is none. */
    private static ItemStack kitFirstHotbarItem(com.rumilance.practice.model.KitDefinition kit) {
        ItemStack best = null;
        int bestSlot = Integer.MAX_VALUE;
        for (com.rumilance.practice.model.KitItemEntry entry : kit.items()) {
            if (entry.slot() < 0 || entry.slot() > 8 || entry.slot() >= bestSlot) {
                continue;
            }
            ItemStack stack = kitEntryStack(kit, entry.slot());
            if (stack != null && !stack.getType().isAir()) {
                best = stack;
                bestSlot = entry.slot();
            }
        }
        return best;
    }

    /** One stored kit item as an ItemStack (serialized data wins over the material name). */
    private static ItemStack kitEntryStack(com.rumilance.practice.model.KitDefinition kit,
                                           int slot) {
        for (com.rumilance.practice.model.KitItemEntry entry : kit.items()) {
            if (entry.slot() != slot) {
                continue;
            }
            try {
                if (entry.itemDataBase64() != null && !entry.itemDataBase64().isBlank()) {
                    ItemStack rebuilt =
                            com.rumilance.practice.util.ItemSerializer.singleFromBase64(
                                    entry.itemDataBase64());
                    if (rebuilt != null) {
                        return rebuilt;
                    }
                }
                Material material = Material.valueOf(entry.material());
                return new ItemStack(material, Math.max(1, entry.amount()));
            } catch (IllegalArgumentException | NullPointerException e) {
                return null;
            }
        }
        return null;
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

    /**
     * Mace bot (Quantum parity: {@code quantum:mace/tick} + {@code mace/lunge} + {@code mace/wind}).
     * The dummy fights back instead of standing still: it walks in, sprint-jumps (lunges) at the
     * player and lands SMASH attacks whose damage scales with the fall distance; from HARD upward
     * it also drops a wind charge at its own feet to buy height for a bigger smash. Timing, reach,
     * aim error, speed and regen all come from the rung, exactly like the other kits' bots.
     */
    private void tickMaceBots() {
        long now = System.currentTimeMillis();
        for (PracticeSession session : sessions.values()) {
            try {
                tickMaceBot(session, now);
            } catch (Throwable t) {
                safeEndBrokenBotSession(session, t);
            }
        }
    }

    private void tickMaceBot(PracticeSession session, long now) {
        {
            if (session.type() != PracticeType.MACE
                    || session.phase() != PracticeSession.Phase.ACTIVE) {
                return;
            }
            Player player = Bukkit.getPlayer(session.playerId());
            if (player == null || !player.isOnline() || player.isDead()) {
                return;
            }
            PracticeRoom room = get(session.practiceId()).orElse(null);
            if (room == null) {
                return;
            }
            Mannequin bot = session.maceBot();
            if (bot == null || !bot.isValid() || bot.isDead()) {
                // Chunk unloads or a stray kill must not leave an empty arena behind.
                spawnMaceBot(player, session, room);
                return;
            }
            if (now < session.botStunUntilMs()) {
                bot.setVelocity(new Vector(0, bot.getVelocity().getY(), 0));
                return;
            }
            BotDifficulty diff = session.difficulty();
            if (now - session.botLastDamagedMs() > BotDifficulty.REGEN_DELAY_MS
                    && diff.regenPerSecond() > 0) {
                healToward(bot, diff.botMaxHp(), diff.regenPerSecond() / 20.0d);
            }

            Location eye = bot.getEyeLocation();
            Location botLoc = bot.getLocation();
            Location target = player.getLocation().add(0, 1.0, 0);
            Vector to = target.toVector().subtract(eye.toVector());
            if (to.lengthSquared() < 0.0001) {
                return;
            }
            turnToward(bot, eye, to.clone().normalize(), diff);

            double dist = eye.distance(target);
            double reach = reachWithJitter(diff);
            double dx = target.getX() - botLoc.getX();
            double dz = target.getZ() - botLoc.getZ();
            double flat = Math.sqrt(dx * dx + dz * dz);
            boolean grounded = bot.isOnGround();
            double fall = bot.getFallDistance();

            // 1) SMASH: a mace hit landed while falling. Vanilla scales smash damage with the
            //    fall distance, and the map only commits a slam once it has real height.
            if (!grounded && fall >= MACE_SMASH_FALL_BLOCKS && dist <= reach + 0.75d
                    && now >= session.botNextAttackMs()) {
                botSwing(player, bot, diff, diff.attackDamage() * maceSmashScale(fall));
                session.setBotNextAttackMs(now + diff.attackIntervalMs() + MACE_LAND_RECOVERY_MS);
                return;
            }
            // 2) WIND CHARGE (HARD and up): blast itself skyward and smash on the way down.
            if (grounded && flat >= MACE_WIND_MIN_RANGE && now >= session.botNextWindMs()
                    && diff.preset().ordinal() >= BotDifficulty.Preset.HARD.ordinal()) {
                launchMaceWindCharge(session, bot);
                session.setBotNextWindMs(now + MACE_WIND_COOLDOWN_MS
                        + java.util.concurrent.ThreadLocalRandom.current().nextInt(1200));
                return;
            }
            PracticeSession.BotAbilityState maceAb = session.abilities();
            // 2b) WIND PEARL (Quantum parity: mace_new/wind_pearl, HARD and up): wind blast
            //     plus a forward shove, so the bot sails over the gap into a big smash.
            if (grounded && flat >= 4.5d && dist <= 9.0d && now >= maceAb.nextWindPearlMs()
                    && diff.preset().ordinal() >= BotDifficulty.Preset.HARD.ordinal()) {
                launchMaceWindCharge(session, bot);
                bot.setVelocity(new Vector(dx / flat * 0.55d, 0.35d, dz / flat * 0.55d));
                maceAb.nextWindPearlMs(now + WIND_PEARL_COOLDOWN_MS);
                return;
            }
            // 2c) FAR PEARL (Quantum parity: mace_new/far_pearl): blink to a kiting player.
            if (grounded && dist >= FAR_PEARL_MIN_RANGE && dist <= FAR_PEARL_MAX_RANGE
                    && now >= maceAb.nextFarPearlMs() && diff.attackDamage() > 0.0d && flat > 0.0001) {
                Vector toward = new Vector(dx / flat, 0, dz / flat);
                Location landing = findPearlLanding(session, botLoc, toward, dist - 2.5d);
                if (landing != null && session.botConsume(Material.ENDER_PEARL, 1)) {
                    maceAb.nextFarPearlMs(now + FAR_PEARL_COOLDOWN_MS);
                    pearlTeleportFx(bot, landing);
                    return;
                }
                maceAb.nextFarPearlMs(now + 1500L); // no safe spot: retry soon, don't spam scans
            }
            // 2d) ELYTRA (Quantum parity: mace_new/elytra, HARD and up): rocket up-forward,
            //     the descent falls straight into the SMASH branch above.
            if (grounded && dist > 7.0d && dist <= 20.0d && now >= maceAb.nextElytraMs()
                    && diff.preset().ordinal() >= BotDifficulty.Preset.HARD.ordinal() && flat > 0.0001) {
                bot.setVelocity(new Vector(dx / flat * 0.7d, 0.95d, dz / flat * 0.7d));
                if (bot.getWorld() != null) {
                    bot.getWorld().playSound(botLoc, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.9f, 1.1f);
                    bot.getWorld().spawnParticle(org.bukkit.Particle.CLOUD,
                            botLoc.add(0, 0.4, 0), 12, 0.3, 0.2, 0.3, 0.05d);
                }
                maceAb.nextElytraMs(now + ELYTRA_COOLDOWN_MS);
                return;
            }
            // 3) LUNGE: sprint-jump at the player (map: move forward + sprint + jump + attack).
            if (grounded && flat > 0.0001 && dist >= MACE_LUNGE_MIN_RANGE
                    && dist <= MACE_LUNGE_MAX_RANGE && now >= session.botNextLungeMs()) {
                bot.setVelocity(new Vector(dx / flat * MACE_LUNGE_FORWARD, MACE_LUNGE_UP,
                        dz / flat * MACE_LUNGE_FORWARD));
                bot.swingMainHand();
                session.setBotNextLungeMs(now + diff.attackIntervalMs() * 4L);
                return;
            }
            // 4) Plain melee when already inside reach with no height to smash from.
            if (grounded && dist <= reach && now >= session.botNextAttackMs()) {
                botSwing(player, bot, diff, diff.attackDamage());
                session.setBotNextAttackMs(now + diff.attackIntervalMs()
                        + java.util.concurrent.ThreadLocalRandom.current().nextInt(150));
                return;
            }
            // 5) Reposition: walk in (full speed past 4 blocks), orbit while the shield is up,
            //    and step up one-block ledges instead of grinding into them.
            if (grounded && flat > 0.0001) {
                Vector dir = new Vector(dx / flat, 0, dz / flat);
                // herobot-style steering: follow the A* path when it exists (obstacle escape),
                // hop one-block steps the path calls for.
                org.bukkit.util.Vector pathDir = (dist > 4.0d && !session.botShieldRaised())
                        ? botPathDirection(session, bot, player.getLocation(), now) : null;
                boolean pathHop = false;
                if (pathDir != null) {
                    pathHop = pathDir.getY() > 0.4d;
                    org.bukkit.util.Vector pd = pathDir.clone().setY(0);
                    if (pd.lengthSquared() > 0.0001) {
                        dir = pd.normalize();
                    }
                }
                Location ahead = botLoc.clone().add(dir.clone().multiply(0.9d));
                boolean ledge = ahead.getBlock().getType().isSolid()
                        && ahead.getBlock().getRelative(0, 1, 0).getType().isAir();
                if (now >= session.botStrafeFlipMs()) {
                    session.setBotStrafeDir(-session.botStrafeDir());
                    session.setBotStrafeFlipMs(now + 1500L
                            + java.util.concurrent.ThreadLocalRandom.current().nextInt(1500));
                }
                double speed = diff.moveSpeed() * (dist > 4.0d ? 1.0d : 0.55d);
                if (session.botShieldRaised()) {
                    speed *= 0.4d; // a raised shield walks, it does not sprint
                }
                Vector side = new Vector(-dir.getZ(), 0, dir.getX())
                        .multiply(diff.moveSpeed() * 0.5d * session.botStrafeDir());
                bot.setVelocity(dir.multiply(speed).add(side).setY(bot.getVelocity().getY()));
                if (ledge || pathHop) {
                    bot.setVelocity(bot.getVelocity().setY(0.45d));
                }
            }
        }
    }

    /**
     * Smash-attack damage scale: vanilla mace smashes hit harder the further the attacker fell
     * (Density and Breach then modify that again). Approximated linearly and capped, so a bot
     * launched by a wind charge hurts a lot but cannot one-shot a full-health player.
     */
    static double maceSmashScale(double fallDistance) {
        double extra = Math.max(0.0d, fallDistance - MACE_SMASH_FALL_BLOCKS) * MACE_SMASH_PER_BLOCK;
        return Math.min(MACE_SMASH_MAX_SCALE, 1.0d + extra);
    }

    /**
     * Map behaviour {@code quantum:mace/wind}: drop a wind charge at the bot's own feet and ride
     * the burst upward, trading the height for a bigger smash on the way down. Wind charges deal
     * no damage and break no blocks, so the burst only moves entities.
     */
    private void launchMaceWindCharge(PracticeSession session, Mannequin bot) {
        if (session != null && !session.botConsume(Material.WIND_CHARGE, 1)) {
            return; // no wind charges left in this bot's stock
        }
        World world = bot.getWorld();
        if (world == null) {
            return;
        }
        world.spawn(bot.getLocation().add(0, 0.35, 0), org.bukkit.entity.WindCharge.class,
                charge -> {
                    charge.setPersistent(false);
                    charge.setVelocity(new Vector(0, -0.35, 0));
                });
        bot.swingMainHand();
    }

    /**
     * The player swung into the mace bot's raised shield: whether that costs them a stun is the
     * rung's call, same rule the sword and netherite-pot bots use.
     */
    public void onMaceHitBot(PracticeSession session) {
        if (!session.botShieldRaised() || !session.difficulty().shieldStun()) {
            return;
        }
        session.setBotStunUntilMs(System.currentTimeMillis() + MACE_SHIELD_STUN_MS);
        Player player = Bukkit.getPlayer(session.playerId());
        if (player == null || !player.isOnline()) {
            return;
        }
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.SLOWNESS, 18, 1));
        player.sendActionBar(messages.render(player, "practice.bot-shield-stun"));
    }

    /**
     * Damage routing for the mace dummy: a raised shield reduces melee like every other bot, and
     * dropping it wins the match. Outside a live match it simply comes back at its home spot.
     */
    public boolean onMaceBotDamaged(Player player, PracticeSession session, EntityDamageEvent event) {
        Mannequin bot = session.maceBot();
        if (bot == null || !bot.isValid()) {
            return false;
        }
        BotDifficulty diff = session.difficulty();
        boolean explosion = event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                || event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION;
        if (session.botShieldRaised() && !explosion) {
            event.setDamage(event.getDamage() * (1.0d - diff.shieldReduction()));
        }
        session.setBotLastDamagedMs(System.currentTimeMillis());
        if (bot.getHealth() - event.getFinalDamage() > 0.5d) {
            return false;
        }
        event.setCancelled(true);
        session.incrementBotPops();
        player.playSound(bot.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.4f);
        if (session.phase() == PracticeSession.Phase.ACTIVE) {
            endBotMatch(player, session, BotMatchResult.WIN);
            return true;
        }
        player.sendActionBar(messages.render(player, "practice.bot-down",
                MessageService.tags("kills", String.valueOf(session.botPops()))));
        PracticeRoom room = get(session.practiceId()).orElse(null);
        if (room != null) {
            spawnMaceBot(player, session, room);
        }
        return true;
    }

    /**
     * One bot swing: the rung's aim error decides whether it connects (the map's "aim" score -
     * sloppy rungs whiff often, MASTER almost never does).
     */
    private void botSwing(Player player, Mannequin bot, BotDifficulty diff, double damage) {
        bot.swingMainHand();
        if (damage <= 0.0d) {
            return;
        }
        if (aimRoll().nextDouble(100.0d) < missChancePercent(diff)) {
            player.sendActionBar(messages.render(player, "practice.bot-miss"));
            return;
        }
        player.damage(damage, bot);
    }

    /**
     * Turns the bot toward {@code direction} by at most the degrees its rung allows per tick.
     * Neither vanilla nor the map snaps a head around instantly (the map caps it with
     * {@code max_rotation_per_tick}, 4 deg/tick on Intermediate), and an instant snap makes every
     * rung feel identical: a slow turner can be circled, a precise one tracks a strafing player.
     */
    private static void turnToward(Mannequin bot, Location eye, Vector direction, BotDifficulty diff) {
        Location look = eye.clone().setDirection(direction);
        double rate = turnRatePerTick(diff);
        Location self = bot.getLocation();
        bot.setRotation((float) stepAngle(self.getYaw(), look.getYaw(), rate),
                (float) stepValue(self.getPitch(),
                        Math.max(-89.0f, Math.min(89.0f, look.getPitch())), rate));
    }

    /** Turn rate in degrees per tick, derived from the rung's aim error: sloppy aim turns slow. */
    static double turnRatePerTick(BotDifficulty diff) {
        return Math.max(3.0d, 20.0d - diff.aimSpreadDegrees());
    }

    /** Yaw step taking the shortest way round, clamped to {@code maxStep} degrees. */
    static double stepAngle(double current, double target, double maxStep) {
        double delta = ((target - current + 540.0d) % 360.0d) - 180.0d;
        return current + clampStep(delta, maxStep);
    }

    /** Straight-line step (pitch), clamped to {@code maxStep} degrees. */
    static double stepValue(double current, double target, double maxStep) {
        return current + clampStep(target - current, maxStep);
    }

    static double clampStep(double value, double max) {
        return Math.max(-max, Math.min(max, value));
    }

    // ------------------------------------------------------------------ combat bots (ITEM 41)
    // Sword / Crystal practice bots, inspired by Quantum's PvP Practice map (HeroBot-style
    // fake-player sparring, re-implemented natively on Paper with Mannequin entities).

    /** Read-only view over all live sessions (region checks, bot routing). */
    public java.util.Collection<PracticeSession> activeSessions() {
        return java.util.List.copyOf(sessions.values());
    }

    /** All sessions currently running a combat bot (damage routing for the listener). */
    public java.util.Collection<PracticeSession> sessionsWithCombatBot() {
        java.util.List<PracticeSession> out = new java.util.ArrayList<>();
        for (PracticeSession session : sessions.values()) {
            if (session.combatBot() != null) {
                out.add(session);
            }
        }
        return out;
    }

    /** True while any live session occupies the given practice room (bot picker UI). */
    public boolean isRoomBusy(String practiceId) {
        for (PracticeSession session : sessions.values()) {
            if (session.practiceId().equals(practiceId)) {
                return true;
            }
        }
        return false;
    }

    /** Sword room loadout: sharp sword, shield, apples and full netherite (Quantum sword preset). */
    public void giveSwordLoadout(Player player, PracticeSession session) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItem(0, new ItemStack(Material.NETHERITE_SWORD));
        player.getInventory().setItem(1, new ItemStack(Material.SHIELD));
        player.getInventory().setItem(2, new ItemStack(Material.GOLDEN_APPLE, 8));
        player.getInventory().setItem(8, PracticeItems.botSettings(messages, player, session.botShieldRaised()));
        player.getInventory().setHelmet(new ItemStack(Material.NETHERITE_HELMET));
        player.getInventory().setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
        player.getInventory().setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS));
        player.getInventory().setBoots(new ItemStack(Material.NETHERITE_BOOTS));
    }

    /** Crystal room loadout: crystals + obsidian + totem; crystals keep refilling. */
    public void giveCrystalLoadout(Player player, PracticeSession session) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItem(0, new ItemStack(Material.END_CRYSTAL, 64));
        player.getInventory().setItem(1, new ItemStack(Material.OBSIDIAN, 16));
        player.getInventory().setItem(8, PracticeItems.botSettings(messages, player, session.botShieldRaised()));
        player.getInventory().setItemInOffHand(new ItemStack(Material.TOTEM_OF_UNDYING));
        player.getInventory().setHelmet(new ItemStack(Material.NETHERITE_HELMET));
        player.getInventory().setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
        player.getInventory().setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS));
        player.getInventory().setBoots(new ItemStack(Material.NETHERITE_BOOTS));
    }

    /** Netherite Pot loadout: sword, pots for sustain and burst, apples (map nethpot kit). */
    public void giveNethPotLoadout(Player player, PracticeSession session) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItem(0, new ItemStack(Material.NETHERITE_SWORD));
        player.getInventory().setItem(1, new ItemStack(Material.SPLASH_POTION, 16));
        player.getInventory().setItem(2, new ItemStack(Material.GOLDEN_APPLE, 8));
        player.getInventory().setItem(8, PracticeItems.botSettings(messages, player, session.botShieldRaised()));
        player.getInventory().setHelmet(new ItemStack(Material.NETHERITE_HELMET));
        player.getInventory().setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
        player.getInventory().setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS));
        player.getInventory().setBoots(new ItemStack(Material.NETHERITE_BOOTS));
    }

    /** Cart PvP loadout: bow + sword, like the map's TNT-minecart fighter. */
    public void giveCartLoadout(Player player, PracticeSession session) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItem(0, new ItemStack(Material.BOW));
        player.getInventory().setItem(1, new ItemStack(Material.NETHERITE_SWORD));
        player.getInventory().setItem(2, new ItemStack(Material.ARROW, 64));
        player.getInventory().setItem(3, new ItemStack(Material.GOLDEN_APPLE, 8));
        player.getInventory().setItem(8, PracticeItems.botSettings(messages, player, session.botShieldRaised()));
        player.getInventory().setHelmet(new ItemStack(Material.NETHERITE_HELMET));
        player.getInventory().setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
        player.getInventory().setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS));
        player.getInventory().setBoots(new ItemStack(Material.NETHERITE_BOOTS));
    }

    private void spawnCombatBot(Player player, PracticeSession session, PracticeRoom room,
                                PracticeType type) {
        removeCombatBot(session);
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
        // Operator-configured bot home first (/practice botpos); else 4 blocks ahead.
        Location botLoc = resolveConfiguredBotSpawn(session, room);
        if (botLoc == null) {
            botLoc = base.clone()
                    .add(player.getLocation().getDirection().setY(0).normalize().multiply(4));
            botLoc.setY(base.getY());
        }
        if (botLoc.getWorld() == null) {
            return;
        }
        String nameKey = switch (type) {
            case SWORD -> "practice.sword-bot-name";
            case CRYSTAL -> "practice.crystal-bot-name";
            case NETHERITE_POT -> "practice.nethpot-bot-name";
            case CART -> "practice.cart-bot-name";
            default -> "practice.sword-bot-name";
        };
        // Crystal bot dies to one combo (totem pops win the match); the rest tank by difficulty.
        double maxHp = type == PracticeType.CRYSTAL ? 20.0d : session.difficulty().botMaxHp();
        Mannequin bot = botLoc.getWorld().spawn(botLoc, Mannequin.class, m -> {
            m.setImmovable(false); // every fighter moves: orbit, chase, retreat
            m.setGravity(true);
            m.setSilent(true);
            m.setCanPickupItems(false);
            m.setRemoveWhenFarAway(false);
            m.setPersistent(false);
            m.setCollidable(true);
            m.customName(messages.render(player, nameKey));
            m.setCustomNameVisible(true);
            m.setProfile(ResolvableProfile.resolvableProfile(player.getPlayerProfile()));
            if (m.getAttribute(Attribute.MAX_HEALTH) != null) {
                m.getAttribute(Attribute.MAX_HEALTH).setBaseValue(maxHp);
            }
            m.setHealth(maxHp);
            equipCombatBot(m, type, session.botShieldRaised());
        });
        stockBotInventory(session, type);
        session.setCombatBot(bot);
        session.setBotHome(botLoc.clone());
        session.setBotNextAttackMs(System.currentTimeMillis() + 2000L);
        session.setBotStrafeFlipMs(System.currentTimeMillis() + 1500L);
    }

    private void equipCombatBot(Mannequin bot, PracticeType type, boolean shieldUp) {
        EntityEquipment eq = bot.getEquipment();
        if (eq == null) {
            return;
        }
        // Admin binding first: the dummy wears the same kit the player fights with.
        ItemStack fallbackWeapon = switch (type) {
            case SWORD, NETHERITE_POT -> new ItemStack(Material.NETHERITE_SWORD);
            case CART -> new ItemStack(Material.BOW);
            default -> new ItemStack(Material.END_CRYSTAL);
        };
        if (applyBoundBotKit(type, bot, eq, shieldUp, fallbackWeapon)) {
            return;
        }
        // Quantum botgear/neth parity: protection 4 netherite (legs blast-protection on cart),
        // feather-falling 4 + protection 4 boots; cart gets the power 5 / punch 1 / flame bow.
        ItemStack helmet = new ItemStack(Material.NETHERITE_HELMET);
        helmet.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.PROTECTION, 4);
        ItemStack chest = new ItemStack(Material.NETHERITE_CHESTPLATE);
        chest.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.PROTECTION, 4);
        ItemStack legs = new ItemStack(Material.NETHERITE_LEGGINGS);
        legs.addUnsafeEnchantment(type == PracticeType.CART
                ? org.bukkit.enchantments.Enchantment.BLAST_PROTECTION
                : org.bukkit.enchantments.Enchantment.PROTECTION, 4);
        ItemStack boots = new ItemStack(Material.NETHERITE_BOOTS);
        boots.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.FEATHER_FALLING, 4);
        boots.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.PROTECTION, 4);
        eq.setHelmet(helmet);
        eq.setChestplate(chest);
        eq.setLeggings(legs);
        eq.setBoots(boots);
        switch (type) {
            case SWORD, NETHERITE_POT -> {
                ItemStack sword = new ItemStack(Material.NETHERITE_SWORD);
                sword.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.SHARPNESS, 5);
                eq.setItemInMainHand(sword);
                // Quantum passive/main: shield down means a totem rides the offhand.
                eq.setItemInOffHand(type == PracticeType.NETHERITE_POT
                        ? new ItemStack(Material.SPLASH_POTION)
                        : (shieldUp ? new ItemStack(Material.SHIELD)
                                : new ItemStack(Material.TOTEM_OF_UNDYING)));
            }
            case CART -> {
                ItemStack bow = new ItemStack(Material.BOW);
                bow.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.POWER, 5);
                bow.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.PUNCH, 1);
                bow.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.FLAME, 1);
                bow.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.INFINITY, 1);
                eq.setItemInMainHand(bow);
                eq.setItemInOffHand(new ItemStack(Material.ARROW));
            }
            default -> { // CRYSTAL
                eq.setItemInMainHand(new ItemStack(Material.END_CRYSTAL));
                eq.setItemInOffHand(new ItemStack(Material.TOTEM_OF_UNDYING));
            }
        }
        zeroDropChances(bot, eq);
    }

    private void removeCombatBot(PracticeSession session) {
        clearBotArtifacts(session);
        Mannequin bot = session.combatBot();
        if (bot != null && bot.isValid()) {
            bot.remove();
        }
        session.setCombatBot(null);
    }

    /**
     * Combat-bot AI tick. Sword bots close in, strafe and swing; crystal bots hold position
     * and keep topping the player's crystal supply (Quantum "refill" toggle).
     */
    private void tickCombatBots() {
        long now = System.currentTimeMillis();
        for (PracticeSession session : sessions.values()) {
            try {
                tickCombatBot(session, now);
            } catch (Throwable t) {
                safeEndBrokenBotSession(session, t);
            }
        }
    }

    /** A broken bot session must not poison the shared AI tick loop: log and end it. */
    private void safeEndBrokenBotSession(PracticeSession session, Throwable t) {
        Player player = Bukkit.getPlayer(session.playerId());
        plugin.getLogger().log(java.util.logging.Level.SEVERE,
                "[N Arena] Bot AI tick failed for " + (player == null ? "offline-player" : player.getName())
                        + " — ending the session safely.", t);
        try {
            if (player != null && player.isOnline()) {
                leave(player, true);
            } else {
                sessions.remove(session.playerId(), session);
            }
        } catch (Throwable ignored) {
            // Last resort: drop the session reference so the next tick never retries it.
            sessions.remove(session.playerId(), session);
        }
    }

    private void tickCombatBot(PracticeSession session, long now) {
        {
            PracticeType type = session.type();
            if (!type.botMode() || type == PracticeType.MACE) {
                return;
            }
            if (session.phase() != PracticeSession.Phase.ACTIVE) {
                return;
            }
            Mannequin bot = session.combatBot();
            Player player = Bukkit.getPlayer(session.playerId());
            if (player == null || !player.isOnline() || player.isDead()) {
                return;
            }
            // Robustness for many concurrent bots: chunk unloads or stray damage can
            // despawn a mannequin — bring it back at home instead of leaving an empty arena.
            if (bot == null || !bot.isValid()) {
                PracticeRoom room = get(session.practiceId()).orElse(null);
                if (room != null) {
                    spawnCombatBot(player, session, room, type);
                }
                return;
            }
            // Bot-placed block reverts (pedestals, webs, lava, rails...) share one sweeper.
            revertAgedBotBlocks(session, now);
            Location eye = bot.getEyeLocation();
            Location target = player.getLocation().add(0, 1.0, 0);
            Vector to = target.toVector().subtract(eye.toVector());
            if (to.lengthSquared() < 0.0001) {
                return;
            }
            turnToward(bot, eye, to.normalize(), session.difficulty());

            if (type == PracticeType.CRYSTAL) {
                tickCrystalBot(player, session, bot, now);
                return;
            }
            if (type == PracticeType.CART) {
                tickCartBot(player, session, bot, now);
                return;
            }

            // --- sword & netherite-pot bots: chase, strafe, swing (difficulty-tuned) ---
            BotDifficulty diff = session.difficulty();
            PracticeSession.BotAbilityState ab = session.abilities();
            if (now - session.botLastDamagedMs() > BotDifficulty.REGEN_DELAY_MS
                    && diff.regenPerSecond() > 0) {
                healToward(bot, diff.botMaxHp(), diff.regenPerSecond() / 20.0d);
            }
            boolean blocking = session.botShieldRaised();
            double distSq = bot.getLocation().distanceSquared(player.getLocation());
            double dist = Math.sqrt(distSq);

            // Quantum passive layer: gap healing, water saves, escape pearls (map state3
            // passives run before the fight loop each tick).
            tickBotGap(session, bot, diff.botMaxHp(), now);
            tickBotWaterSave(session, bot, now);
            boolean escaped = tickEscapePearl(player, session, bot, diff.botMaxHp(), now);

            // Quantum disruption layer (cobwebs/fluid_main + shield/disable): webs at the
            // player's feet, lava under an airborne player, and axe swings that strip shields.
            tickSwordDisruption(player, session, bot, type, dist, now);

            if (escaped) {
                return; // just pearled out: re-aim next tick instead of swinging air
            }
            if (distSq > 2.2d * 2.2d) {
                Vector dir = to.setY(0);
                // herobot steering: prefer an A* path (wall/ledge escapes) over straight line.
                org.bukkit.util.Vector pathDir = (!blocking && dist > 4.0d)
                        ? botPathDirection(session, bot, player.getLocation(), now) : null;
                boolean pathHop = pathDir != null && pathDir.getY() > 0.4d;
                if (pathDir != null) {
                    org.bukkit.util.Vector pd = pathDir.clone().setY(0);
                    if (pd.lengthSquared() > 0.0001) {
                        dir = pd.normalize();
                    }
                }
                if (dir.lengthSquared() > 0.0001) {
                    dir.normalize();
                    // Strafe flips every 1.5-3s, mixing orbits into the approach.
                    if (now >= session.botStrafeFlipMs()) {
                        session.setBotStrafeDir(-session.botStrafeDir());
                        session.setBotStrafeFlipMs(now + 1500L + java.util.concurrent.ThreadLocalRandom.current().nextInt(1500));
                    }
                    Vector side = new Vector(-dir.getZ(), 0, dir.getX())
                            .multiply(diff.moveSpeed() * 0.66d * session.botStrafeDir());
                    Vector move = dir.multiply(blocking ? diff.moveSpeed() * 0.4d : diff.moveSpeed())
                            .add(side).setY(bot.getVelocity().getY());
                    // Obstacle hop (map bot_mech/jump): pressing forward but not moving means a
                    // one-block lip ahead — hop over it instead of grinding.
                    if (bot.isOnGround() && !blocking) {
                        Vector vel = bot.getVelocity();
                        if (pathHop || vel.getX() * vel.getX() + vel.getZ() * vel.getZ() < 0.05d * 0.05d) {
                            move.setY(0.42d);
                        }
                    }
                    bot.setVelocity(move);
                    // Long-range bow pressure (map sword bowcharge / passive bow): pokes while
                    // walking into melee range. (Fresh direction: dir/to were scaled in-place
                    // by the movement math above.)
                    if (!blocking && dist >= 8.0d && dist <= 18.0d && now >= ab.nextBowMs()
                            && diff.attackDamage() > 0.0d) {
                        botShootArrow(bot, diff, player.getLocation().toVector()
                                        .subtract(bot.getLocation().toVector()).setY(0),
                                diff.attackDamage() * 0.8d);
                        if (bot.getWorld() != null) {
                            bot.getWorld().playSound(bot.getLocation(),
                                    Sound.ENTITY_ARROW_SHOOT, 1.0f, 1.0f);
                        }
                        ab.nextBowMs(now + BOW_COOLDOWN_MS
                                + java.util.concurrent.ThreadLocalRandom.current().nextInt(800));
                    }
                }
            } else {
                bot.setVelocity(new Vector(0, bot.getVelocity().getY(), 0));
            }
            double reach = reachWithJitter(diff);
            if (!blocking && diff.attackDamage() > 0.0d && now >= session.botNextAttackMs()
                    && distSq <= reach * reach) {
                if (now >= ab.nextCritMs() && bot.isOnGround()
                        && !inCobweb(player.getLocation())) {
                    // Jump-crit (Quantum: sword/crit gated by sword/pcrit): hop now, connect on
                    // the way down; the map's crit replaces a plain hit.
                    swordJumpCrit(player, session, bot, diff, reach, now);
                } else {
                    // Map "aim": the higher the aim error the more swings whiff, so low rungs
                    // punish a player who stands still far less than MASTER / SURVIVAL MASTER.
                    botSwing(player, bot, diff, diff.attackDamage());
                }
                session.setBotNextAttackMs(now + diff.attackIntervalMs()
                        + java.util.concurrent.ThreadLocalRandom.current().nextInt(150));
            }
            if (type == PracticeType.NETHERITE_POT) {
                tickNethPotPotions(player, session, bot, now);
            }
        }
    }

    /**
     * Netherite-pot fighter extras (map "NETHERITE POT" mode): drinks a healing splash
     * when hurt, hurls harming splashes at a close-range player.
     */
    private void tickNethPotPotions(Player player, PracticeSession session, Mannequin bot, long now) {
        if (now < session.botPotionUntilMs()) {
            return;
        }
        if (!session.botConsume(Material.SPLASH_POTION, 1)) {
            return; // out of potions — no phantom healing/harming
        }
        BotDifficulty diff = session.difficulty();
        PracticeSession.BotAbilityState ab = session.abilities();
        double hp = bot.getHealth();
        if (hp < diff.botMaxHp() * 0.5d) {
            // Chug: instant heal + red sparkle (and a restock, like the map's pot_cd reset).
            healToward(bot, diff.botMaxHp(), 8.0d);
            ab.potUses(0);
            if (bot.getWorld() != null) {
                bot.getWorld().spawnParticle(org.bukkit.Particle.ENTITY_EFFECT,
                        bot.getLocation().add(0, 1.2, 0), 24, 0.4, 0.6, 0.4, 1.0d,
                        org.bukkit.Color.fromRGB(0xF82423));
            }
        } else if (bot.getLocation().distanceSquared(player.getLocation()) <= 4.5d * 4.5d
                && ab.potUses() < 2) {
            // Splash of harming at the player (map cap: two pots before a restock drink).
            BotDifficulty potDiff = session.difficulty();
            double potDamage = Math.max(1.0d, potDiff.attackDamage() * 0.6d);
            player.damage(potDamage, bot);
            ab.potUses(ab.potUses() + 1);
            player.sendActionBar(messages.render(player, "practice.bot-pot-hit"));
            if (player.getWorld() != null) {
                player.getWorld().spawnParticle(org.bukkit.Particle.ENTITY_EFFECT,
                        player.getLocation().add(0, 1, 0), 30, 0.4, 0.8, 0.4, 1.0d,
                        org.bukkit.Color.fromRGB(0x43075A));
            }
        } else {
            return; // nothing to drink or throw yet
        }
        session.setBotPotionUntilMs(now + diff.comboCooldownMs() + 1500L);
    }

    /**
     * Cart PvP fighter (map "TNT MINECART" mode): the bot kites with a bow and rolls
     * primed TNT at the player — our native take on rail-cart detonation practice.
     */
    private void tickCartBot(Player player, PracticeSession session, Mannequin bot, long now) {
        BotDifficulty diff = session.difficulty();
        PracticeSession.BotAbilityState ab = session.abilities();
        if (now - session.botLastDamagedMs() > BotDifficulty.REGEN_DELAY_MS
                && diff.regenPerSecond() > 0) {
            healToward(bot, diff.botMaxHp(), diff.regenPerSecond() / 20.0d);
        }
        // The map's cart fighter shares the sword passive suite: gap and pearl-outs under fire.
        tickBotGap(session, bot, diff.botMaxHp(), now);
        tickBotWaterSave(session, bot, now);
        tickEscapePearl(player, session, bot, diff.botMaxHp(), now);
        Location botLoc = bot.getLocation();
        double dist = botLoc.distance(player.getLocation());
        Vector dir = player.getLocation().toVector().subtract(botLoc.toVector()).setY(0);
        if (dir.lengthSquared() < 0.0001) {
            return;
        }
        dir.normalize();
        if (now >= session.botStrafeFlipMs()) {
            session.setBotStrafeDir(-session.botStrafeDir());
            session.setBotStrafeFlipMs(now + 1200L + java.util.concurrent.ThreadLocalRandom.current().nextInt(1600));
        }
        Vector side = new Vector(-dir.getZ(), 0, dir.getX())
                .multiply(diff.moveSpeed() * 0.75d * session.botStrafeDir());
        Vector move;
        if (dist < 4.0d) {
            move = dir.clone().multiply(-diff.moveSpeed()).add(side);  // keep bow range
        } else if (dist > 9.0d) {
            move = dir.clone().multiply(diff.moveSpeed()).add(side);
        } else {
            move = side;
        }
        bot.setVelocity(move.setY(bot.getVelocity().getY()));

        // Arrow volley (the map detonates TNT carts with arrows — we keep the bow pressure).
        // Full-draw speed like a player bow, damage and spread from the difficulty ladder.
        if (now >= session.botNextAttackMs() && diff.attackDamage() > 0.0d) {
            bot.swingMainHand();
            botShootArrow(bot, diff, dir, diff.attackDamage() * 0.7d);
            long jitter = java.util.concurrent.ThreadLocalRandom.current().nextInt(400);
            session.setBotNextAttackMs(now + Math.max(700L, diff.attackIntervalMs() * 2L) + jitter);
        }
        // Rolling TNT "cart" every combo cooldown (on a rail, like the map's cart tracks).
        if (now >= session.botNextCartMs() && session.botConsume(Material.TNT_MINECART, 1)) {
            org.bukkit.entity.TNTPrimed tnt = bot.getWorld().spawn(
                    botLoc.add(0, 1.1, 0), org.bukkit.entity.TNTPrimed.class, t -> {
                        t.setFuseTicks(26);
                        t.setYield(4.0f);
                        t.setSource(bot);
                    });
            tnt.setVelocity(dir.clone().normalize().multiply(0.85d).setY(0.18d));
            session.botTnt().add(tnt.getUniqueId());
            session.setBotNextCartMs(now + diff.comboCooldownMs());
            if (session.botConsume(Material.POWERED_RAIL, 1)) {
                placeTrackedBlock(session, tnt.getLocation().getBlock(),
                        Material.POWERED_RAIL, CART_RAIL_TTL_MS);
            }
        }
        // Defensive oak-log block when the player is on top of it (map: cart/defenseplace).
        if (dist < 3.5d && bot.getHealth() < diff.botMaxHp() * 0.6d
                && now >= ab.nextDefenseBlockMs()) {
            placeDefenseWall(session, bot, dir, Material.OAK_LOG, now);
        }
    }

    // ================= Quantum-parity combat abilities (shared helpers) =================

    /** Environment probe (Quantum parity: quantum:decisions/airborne): genuinely airborne. */
    private static boolean isAirborne(org.bukkit.entity.Entity entity) {
        return !entity.isOnGround() && entity.getFallDistance() > 0.4d;
    }

    /** True when the entity at {@code loc} is standing inside a cobweb. */
    private static boolean inCobweb(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return false;
        }
        if (loc.getBlock().getType() == Material.COBWEB) {
            return true;
        }
        return loc.clone().add(0, 1, 0).getBlock().getType() == Material.COBWEB;
    }

    /**
     * Tracks a bot-placed block for TTL reverts. Only claimed from air / replaceable blocks
     * and set without physics (fluids stay put; nothing spreads into the room).
     *
     * @return true when the placement happened
     */
    private boolean placeTrackedBlock(PracticeSession session, org.bukkit.block.Block block,
                                      Material type, long ttlMs) {
        if (block == null || (!block.getType().isAir() && !block.getBlockData().isReplaceable())) {
            return false;
        }
        block.setType(type, false);
        session.botPlacedBlocks().put(block,
                new PracticeSession.BotBlock(type, System.currentTimeMillis(), ttlMs));
        return true;
    }

    /** Vanilla-style arrow shot shared by the sword bow, cart bow and crystal crossbow. */
    private void botShootArrow(Mannequin bot, BotDifficulty diff, Vector flatDir,
                               double damage) {
        if (bot.getWorld() == null || flatDir.lengthSquared() < 0.0001) {
            return;
        }
        double spread = Math.toRadians(diff.aimSpreadDegrees());
        Vector arrowDir = flatDir.clone().setY(0.06d).normalize();
        if (spread > 0.0d) {
            arrowDir.rotateAroundY(aimRoll().nextDouble(-spread, spread));
        }
        org.bukkit.entity.Arrow arrow = bot.getWorld().spawnArrow(
                bot.getEyeLocation(), arrowDir.multiply(1.9d), 3.0f, 0.0f);
        arrow.setShooter(bot);
        arrow.setDamage(Math.max(1.0d, damage));
        bot.swingMainHand();
    }

    /**
     * Golden apple under pressure (Quantum parity: sword/passive/gap + pot/gap): below half
     * HP, chew a gap — 40% heal with the golden sparkle, at most twice per bot life so a
     * committed combo still finishes the bot.
     */
    private void tickBotGap(PracticeSession session, Mannequin bot, double maxHp, long now) {
        PracticeSession.BotAbilityState ab = session.abilities();
        if (now < ab.nextGapMs() || ab.gapUses() >= GAP_MAX_USES
                || bot.getHealth() >= maxHp * 0.5d) {
            return;
        }
        ab.gapUses(ab.gapUses() + 1);
        ab.nextGapMs(now + GAP_COOLDOWN_MS);
        healToward(bot, maxHp, maxHp * GAP_HEAL_FRACTION);
        if (bot.getWorld() != null) {
            bot.getWorld().playSound(bot.getLocation(), Sound.ENTITY_GENERIC_EAT, 1.0f, 1.1f);
            bot.getWorld().spawnParticle(org.bukkit.Particle.ENTITY_EFFECT,
                    bot.getLocation().add(0, 1.2, 0), 26, 0.4, 0.6, 0.4, 1.0d,
                    org.bukkit.Color.fromRGB(0xF5C72C));
        }
    }

    /**
     * Self water bucket (Quantum parity: cobwebs/water_main): douse the bot when ablaze,
     * wash cobwebs off itself. Leaves a brief soak block that the reverter clears.
     */
    private void tickBotWaterSave(PracticeSession session, Mannequin bot, long now) {
        PracticeSession.BotAbilityState ab = session.abilities();
        if (now < ab.nextWaterMs()) {
            return;
        }
        Location loc = bot.getLocation();
        if (!botHas(session, Material.WATER_BUCKET)) {
            return;
        }
        if (bot.getFireTicks() > 0) {
            bot.setFireTicks(0);
            placeTrackedBlock(session, loc.getBlock(), Material.WATER, WATER_TTL_MS);
            ab.nextWaterMs(now + WATER_COOLDOWN_MS);
            waterSplashFx(bot);
            return;
        }
        if (inCobweb(loc)) {
            org.bukkit.block.Block feet = loc.getBlock();
            org.bukkit.block.Block head = feet.getRelative(org.bukkit.block.BlockFace.UP);
            if (feet.getType() == Material.COBWEB) {
                feet.setType(Material.AIR, false);
            }
            if (head.getType() == Material.COBWEB) {
                head.setType(Material.AIR, false);
            }
            ab.nextWaterMs(now + WATER_COOLDOWN_MS);
            waterSplashFx(bot);
        }
    }

    private static void waterSplashFx(Mannequin bot) {
        if (bot.getWorld() == null) {
            return;
        }
        bot.getWorld().playSound(bot.getLocation(), Sound.ITEM_BUCKET_EMPTY, 0.8f, 1.2f);
        bot.getWorld().spawnParticle(org.bukkit.Particle.SPLASH,
                bot.getLocation().add(0, 0.8, 0), 24, 0.4, 0.5, 0.4, 0.1d);
    }

    /**
     * Escape pearl (Quantum parity: sword & crystal passive/escape/pearl): wounded and
     * cornered, the bot blinks backwards onto solid ground inside the room.
     *
     * @return true when the pearl happened this tick (caller should skip the attack loop)
     */
    private boolean tickEscapePearl(Player player, PracticeSession session, Mannequin bot,
                                    double maxHp, long now) {
        PracticeSession.BotAbilityState ab = session.abilities();
        if (now < ab.nextPearlMs() || session.difficulty().attackDamage() <= 0.0d
                || bot.getHealth() > maxHp * ESCAPE_PEARL_HP_FRACTION
                || bot.getLocation().distanceSquared(player.getLocation()) > 6.0d * 6.0d) {
            return false; // cornered only: nobody pearls away from a distant target (map <= 10)
        }
        Vector away = bot.getLocation().toVector().subtract(player.getLocation().toVector())
                .setY(0);
        if (away.lengthSquared() < 0.01d) {
            return false;
        }
        Location landing = findPearlLanding(session, bot.getLocation(), away.normalize(), 12.0d);
        if (landing == null) {
            ab.nextPearlMs(now + 1500L); // no safe spot: retry soon, don't spam scans
            return false;
        }
        ab.nextPearlMs(now + ESCAPE_PEARL_COOLDOWN_MS);
        if (!session.botConsume(Material.ENDER_PEARL, 1)) {
            return false;
        }
        pearlTeleportFx(bot, landing);
        return true;
    }

    /**
     * Finds a survivable pearl landing {@code preferred} blocks along {@code horizontal} from
     * {@code from}: inside the practice region, on solid ground, with two air blocks to stand
     * in. Falls back to shorter hops when the full leap leaves the room.
     */
    private Location findPearlLanding(PracticeSession session, Location from,
                                      Vector horizontal, double preferred) {
        PracticeRoom room = get(session.practiceId()).orElse(null);
        if (room == null || from.getWorld() == null) {
            return null;
        }
        double[] tries = {preferred, preferred - 2.0d, preferred - 4.0d, preferred / 2.0d};
        for (double dist : tries) {
            if (dist < 2.0d) {
                continue;
            }
            Location cand = from.clone().add(horizontal.clone().multiply(dist));
            for (int dy = 2; dy >= -8; dy--) {
                Location probe = cand.clone().add(0, dy, 0);
                if (!contains(session, room, probe)) {
                    continue;
                }
                org.bukkit.block.Block ground = probe.getBlock();
                if (!ground.getType().isSolid()) {
                    continue;
                }
                org.bukkit.block.Block feet = ground.getRelative(org.bukkit.block.BlockFace.UP);
                org.bukkit.block.Block head = feet.getRelative(org.bukkit.block.BlockFace.UP);
                if (feet.getType().isSolid() || head.getType().isSolid()
                        || feet.isLiquid() || head.isLiquid()) {
                    break; // ground found but no room to stand on this column
                }
                return feet.getLocation().add(0.5d, 0.0d, 0.5d)
                        .setDirection(from.getDirection());
            }
        }
        return null;
    }

    /** Pearl blink with the tell-tale purple trail at both ends. */
    private static void pearlTeleportFx(Mannequin bot, Location landing) {
        World world = bot.getWorld();
        Location from = bot.getLocation();
        if (world != null) {
            world.playSound(from, Sound.ENTITY_ENDER_PEARL_THROW, 1.0f, 1.0f);
            world.spawnParticle(org.bukkit.Particle.PORTAL, from.add(0, 1, 0), 40, 0.3, 0.6, 0.3, 0.6d);
        }
        bot.teleport(landing);
        bot.setVelocity(new Vector());
        if (world != null) {
            world.playSound(landing, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
            world.spawnParticle(org.bukkit.Particle.PORTAL, landing.clone().add(0, 1, 0), 40,
                    0.3, 0.6, 0.3, 0.6d);
        }
    }

    /**
     * Sword-kit disruption (Quantum parity: cobwebs/cobweb + cobwebs/empty_lava +
     * shield/disable): webs under the feet, lava where an airborne player will land, and an
     * axe swing that strips a raised shield.
     */
    private void tickSwordDisruption(Player player, PracticeSession session, Mannequin bot,
                                     PracticeType type, double dist, long now) {
        PracticeSession.BotAbilityState ab = session.abilities();
        BotDifficulty diff = session.difficulty();
        if (diff.attackDamage() <= 0.0d) {
            return; // NPC rung never fights dirty
        }
        // Cobweb at the player's feet — Quantum toggle, OFF unless the admin enabled it.
        if (disruptionEnabled(type, "cobweb")
                && now >= ab.nextCobwebMs() && dist <= 4.0d && !inCobweb(player.getLocation())) {
            if (session.botConsume(Material.COBWEB, 1)
                    && placeTrackedBlock(session, player.getLocation().getBlock(),
                            Material.COBWEB, COBWEB_TTL_MS)) {
                bot.swingMainHand();
                if (bot.getWorld() != null) {
                    bot.getWorld().playSound(player.getLocation(),
                            Sound.BLOCK_STONE_PLACE, 1.0f, 1.4f);
                }
                ab.nextCobwebMs(now + COBWEB_COOLDOWN_MS);
            }
        }
        // Lava bucket dropped where an airborne player comes down (fire-immune targets are
        // not worth the bucket, same as the map's predicate check).
        // Lava punish (stocked bucket) — Quantum toggle, OFF unless the admin enabled it.
        if (disruptionEnabled(type, "lava")
                && now >= ab.nextLavaMs() && dist <= 6.5d && isAirborne(player)
                && !player.isInWater()
                && !player.hasPotionEffect(org.bukkit.potion.PotionEffectType.FIRE_RESISTANCE)) {
            Location base = player.getLocation();
            for (int dy = 1; dy <= 5; dy++) {
                org.bukkit.block.Block column = base.clone().add(0, -dy, 0).getBlock();
                if (!column.getType().isSolid()) {
                    continue;
                }
                if (session.botConsume(Material.LAVA_BUCKET, 1)
                        && placeTrackedBlock(session, column.getRelative(
                                org.bukkit.block.BlockFace.UP), Material.LAVA, LAVA_TTL_MS)) {
                    if (bot.getWorld() != null) {
                        bot.getWorld().playSound(player.getLocation(),
                                Sound.ITEM_BUCKET_EMPTY_LAVA, 1.0f, 1.0f);
                    }
                    ab.nextLavaMs(now + LAVA_COOLDOWN_MS);
                }
                break; // first landing surface wins, lava or not
            }
        }
        // Axe swing that disables the player's raised shield (vanilla shield-cooldown trick).
        if ((type == PracticeType.SWORD || type == PracticeType.NETHERITE_POT)
                && now >= ab.nextAxeMs() && dist <= diff.reachBlocks() + 1.0d
                && player.isBlocking() && botHas(session, Material.NETHERITE_AXE)) {
            ab.nextAxeMs(now + AXE_COOLDOWN_MS);
            EntityEquipment eq = bot.getEquipment();
            if (eq != null) {
                eq.setItemInMainHand(new ItemStack(Material.NETHERITE_AXE));
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    EntityEquipment later = bot.getEquipment();
                    if (bot.isValid() && later != null) {
                        later.setItemInMainHand(new ItemStack(Material.NETHERITE_SWORD));
                    }
                }, 10L);
            }
            bot.swingMainHand();
            player.setCooldown(Material.SHIELD, AXE_SHIELD_DISABLE_TICKS);
            if (bot.getWorld() != null) {
                bot.getWorld().playSound(bot.getLocation(), Sound.ITEM_SHIELD_BREAK, 1.0f, 1.0f);
            }
            player.damage(Math.max(1.0d, diff.attackDamage() * 0.6d), bot);
        }
    }

    /**
     * Sword-kit jump crit (Quantum parity: sword/crit + scrit + combo/jumpreset): hop now,
     * connect on the way down for a 1.5x critical hit, then scrit backpedal — and from HARD
     * upward occasionally chain straight back in with a sprint jump-reset.
     */
    private void swordJumpCrit(Player player, PracticeSession session, Mannequin bot,
                               BotDifficulty diff, double reach, long now) {
        PracticeSession.BotAbilityState ab = session.abilities();
        ab.nextCritMs(now + Math.max(CRIT_MIN_INTERVAL_MS, diff.attackIntervalMs() * 5L)
                + java.util.concurrent.ThreadLocalRandom.current().nextInt(1200));
        Vector vel = bot.getVelocity();
        bot.setVelocity(new Vector(vel.getX(), 0.42d, vel.getZ()));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!bot.isValid() || !player.isOnline()
                    || session.phase() != PracticeSession.Phase.ACTIVE) {
                return;
            }
            double landedRange = reach + 1.0d;
            if (bot.getLocation().distanceSquared(player.getLocation())
                    > landedRange * landedRange) {
                return; // knocked away mid-jump: the crit whiffs with the swing
            }
            botSwing(player, bot, diff, diff.attackDamage() * 1.5d);
            if (player.getWorld() != null) {
                player.getWorld().spawnParticle(org.bukkit.Particle.CRIT,
                        player.getLocation().add(0, 1.0, 0), 18, 0.3, 0.5, 0.3, 0.4d);
                player.getWorld().playSound(player.getLocation(),
                        Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 1.0f);
            }
            // scrit: backpedal out of the trade (Quantum: sword/scrit).
            Vector away = bot.getLocation().toVector()
                    .subtract(player.getLocation().toVector()).setY(0);
            if (away.lengthSquared() > 0.01d) {
                away.normalize().multiply(0.38d);
                bot.setVelocity(new Vector(away.getX(), 0.28d, away.getZ()));
            }
            // combo/jumpreset (HARD and up): sometimes chains back in with a sprint-hop.
            if (diff.preset().ordinal() >= BotDifficulty.Preset.HARD.ordinal()
                    && System.currentTimeMillis() >= ab.nextJumpResetMs()
                    && java.util.concurrent.ThreadLocalRandom.current().nextDouble() < 0.35d) {
                ab.nextJumpResetMs(System.currentTimeMillis() + 1500L);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!bot.isValid() || !player.isOnline()) {
                        return;
                    }
                    Vector in = player.getLocation().toVector()
                            .subtract(bot.getLocation().toVector()).setY(0);
                    if (in.lengthSquared() > 0.01d) {
                        in.normalize().multiply(0.3d);
                        bot.setVelocity(new Vector(in.getX(), 0.42d, in.getZ()));
                    }
                }, 4L);
            }
        }, 6L);
    }

    /**
     * Respawn-anchor strike (Quantum parity: g1gc/anchor): charged anchor materialises beside
     * the player and is detonated a beat later — the overworld makes that a bomb.
     */
    private boolean launchAnchorStrike(Player player, PracticeSession session, Mannequin bot) {
        org.bukkit.block.Block foot = player.getLocation().getBlock();
        int[] dx = {1, -1, 0, 0};
        int[] dz = {0, 0, 1, -1};
        int start = java.util.concurrent.ThreadLocalRandom.current().nextInt(4);
        org.bukkit.block.Block spot = null;
        for (int k = 0; k < 4; k++) {
            int i = (start + k) % 4;
            org.bukkit.block.Block cand = foot.getRelative(dx[i], 0, dz[i]);
            if ((cand.getType().isAir() || cand.getBlockData().isReplaceable())
                    && cand.getRelative(org.bukkit.block.BlockFace.DOWN).getType().isSolid()) {
                spot = cand;
                break;
            }
        }
        if (spot == null) {
            return false;
        }
        if (!session.botConsume(Material.RESPAWN_ANCHOR, 1)
                || !session.botConsume(Material.GLOWSTONE, 4)) {
            return false;
        }
        org.bukkit.block.data.type.RespawnAnchor data =
                (org.bukkit.block.data.type.RespawnAnchor)
                        Material.RESPAWN_ANCHOR.createBlockData();
        data.setCharges(data.getMaximumCharges());
        if (!placeTrackedBlock(session, spot, Material.RESPAWN_ANCHOR, 3000L)) {
            return false;
        }
        spot.setBlockData(data, false);
        bot.swingMainHand();
        final org.bukkit.block.Block fuseBlock = spot;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (fuseBlock.getType() != Material.RESPAWN_ANCHOR) {
                return;
            }
            Location boom = fuseBlock.getLocation().add(0.5d, 0.5d, 0.5d);
            session.botPlacedBlocks().remove(fuseBlock);
            fuseBlock.setType(Material.AIR, false);
            if (boom.getWorld() != null) {
                boom.getWorld().createExplosion(boom, 5.0f, false, false, bot);
            }
        }, 8L);
        return true;
    }

    /**
     * Defensive block wall between bot and player (Quantum parity: crystal/passive/block,
     * cart/defenseplace) plus a backwards hop, buying the bot breathing room. Blocks melt
     * away via the tracked-block reverter.
     */
    private void placeDefenseWall(PracticeSession session, Mannequin bot, Vector dirFlat,
                                  Material type, long now) {
        PracticeSession.BotAbilityState ab = session.abilities();
        if (dirFlat.lengthSquared() < 0.0001) {
            return;
        }
        if (!session.botConsume(type, 2)) {
            return;
        }
        Vector toward = dirFlat.clone().setY(0).normalize();
        org.bukkit.block.Block feet = bot.getLocation().add(toward).getBlock();
        boolean placed = placeTrackedBlock(session, feet, type, DEFENSE_BLOCK_TTL_MS);
        if (type == Material.OBSIDIAN) {
            placed |= placeTrackedBlock(session,
                    feet.getRelative(org.bukkit.block.BlockFace.UP), type, DEFENSE_BLOCK_TTL_MS);
        }
        if (!placed) {
            return;
        }
        ab.nextDefenseBlockMs(now + DEFENSE_BLOCK_COOLDOWN_MS);
        bot.swingMainHand();
        bot.setVelocity(new Vector(-toward.getX() * 0.35d, 0.25d, -toward.getZ() * 0.35d));
        if (bot.getWorld() != null) {
            bot.getWorld().playSound(bot.getLocation(), Sound.BLOCK_STONE_PLACE, 1.0f, 1.0f);
        }
    }

    /** Per-swing reach: the ladder value with a small jitter so spacing is not pixel-perfect. */
    private static double reachWithJitter(BotDifficulty diff) {
        return diff.reachBlocks()
                + (java.util.concurrent.ThreadLocalRandom.current().nextDouble() * 0.5d - 0.25d);
    }

    /**
     * Swing accuracy from the ladder's aim error: a 0° aim (SURVIVAL MASTER) never whiffs, a 12°
     * aim (NPC) whiffs about a third of the time. Linear in between, capped so no rung is a
     * guaranteed hit or a guaranteed miss.
     */
    private static double missChancePercent(BotDifficulty diff) {
        return Math.max(0.0d, Math.min(35.0d, diff.aimSpreadDegrees() * 2.5d));
    }

    private static java.util.concurrent.ThreadLocalRandom aimRoll() {
        return java.util.concurrent.ThreadLocalRandom.current();
    }

    private static void healToward(Mannequin bot, double max, double step) {
        if (bot.getHealth() < max) {
            bot.setHealth(Math.min(max, bot.getHealth() + step));
        }
    }

    /** Keeps the crystal room stocked: top up when the player runs low. */
    private void refillCrystals(Player player) {
        int count = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null && item.getType() == Material.END_CRYSTAL) {
                count += item.getAmount();
            }
        }
        if (count < 16) {
            player.getInventory().addItem(new ItemStack(Material.END_CRYSTAL, 32));
        }
    }

    /**
     * Crystal-bot combat AI, modelled on the Quantum map's crystal fighter: it orbits at
     * range, sprints away while recovering after a hit, and runs full crystal combos —
     * obsidian pedestal down, crystal on top, detonate near the player.
     */
    private void tickCrystalBot(Player player, PracticeSession session, Mannequin bot, long now) {
        refillCrystals(player);
        BotDifficulty crystalDiff = session.difficulty();
        PracticeSession.BotAbilityState ab = session.abilities();
        // Regen between combos (the same 5s out-of-combat delay every other mode uses) so a
        // half-finished combo never leaves a dead-looking bot. Crystal fighters deliberately
        // keep the flat 20 HP body they spawn with; only the regen RATE comes from the ladder.
        if (now - session.botLastDamagedMs() > BotDifficulty.REGEN_DELAY_MS) {
            healToward(bot, 20.0d,
                    Math.max(0.25d, session.difficulty().regenPerSecond() / 8.0d));
        }
        // Crystal passives (map: crystal/passive/gap, water save, escape/pearl).
        tickBotGap(session, bot, 20.0d, now);
        tickBotWaterSave(session, bot, now);
        tickEscapePearl(player, session, bot, 20.0d, now);

        // --- movement: orbit at 3-6 blocks, retreat while recovering ---
        Location botLoc = bot.getLocation();
        double dist = botLoc.distance(player.getLocation());
        Vector dir = player.getLocation().toVector().subtract(botLoc.toVector()).setY(0);
        if (dir.lengthSquared() < 0.0001) {
            return;
        }
        dir.normalize();
        if (now >= session.botStrafeFlipMs()) {
            session.setBotStrafeDir(-session.botStrafeDir());
            session.setBotStrafeFlipMs(now + 1200L
                    + java.util.concurrent.ThreadLocalRandom.current().nextInt(1600));
        }
        Vector side = new Vector(-dir.getZ(), 0, dir.getX())
                .multiply(0.18d * session.botStrafeDir());
        Vector move;
        if (now < session.botRetreatUntilMs()) {
            move = dir.clone().multiply(-0.30d).add(side); // recovery: sprint away (map behaviour)
        } else if (dist < 3.0d) {
            move = dir.clone().multiply(-0.22d).add(side);  // too close: back off
        } else if (dist > 6.5d) {
            move = dir.clone().multiply(0.24d).add(side);   // too far: close in
        } else {
            move = side;                                    // sweet spot: orbit
        }
        bot.setVelocity(move.setY(bot.getVelocity().getY()));

        // --- passive pressure: crossbow poke mid-range (map: crystal/passive/crossbow) ---
        if (dist >= CROSSBOW_MIN_RANGE && dist <= CROSSBOW_MAX_RANGE
                && now >= ab.nextCrossbowMs() && crystalDiff.attackDamage() > 0.0d) {
            botShootArrow(bot, crystalDiff, dir, crystalDiff.attackDamage() * 0.6d);
            if (bot.getWorld() != null) {
                bot.getWorld().playSound(botLoc, Sound.ITEM_CROSSBOW_SHOOT, 1.0f, 1.0f);
            }
            ab.nextCrossbowMs(now + crystalDiff.comboCooldownMs() * 2L
                    + java.util.concurrent.ThreadLocalRandom.current().nextInt(400));
        }

        // --- defensive obsidian wall when hurt (map: crystal/passive/block) ---
        if (bot.getHealth() <= 10.0d && dist <= 4.0d && now >= ab.nextDefenseBlockMs()) {
            placeDefenseWall(session, bot, dir, Material.OBSIDIAN, now);
        }

        // --- attack: place a crystal combo near the player ---
        if (now >= session.botNextAttackMs() && dist <= 9.0d
                && session.botCrystals().size() < 2) {
            long combo = crystalDiff.comboCooldownMs();
            // g1gc anchor mixups (map: g1gc/anchor): from HARD upward, about half of the close
            // combos are respawn-anchor strikes instead of pedestal crystals.
            boolean anchorMix = combo <= 1800L && dist <= 6.0d && now >= ab.nextAnchorMs()
                    && java.util.concurrent.ThreadLocalRandom.current().nextDouble() < 0.5d;
            if (anchorMix && launchAnchorStrike(player, session, bot)) {
                ab.nextAnchorMs(now + Math.max(ANCHOR_MIN_COOLDOWN_MS, combo * 2L));
                session.setBotNextAttackMs(now + combo
                        + java.util.concurrent.ThreadLocalRandom.current().nextInt(400));
            } else if (launchCrystalAttack(player, session)) {
                // Map "crystal_cd / explosion_cd" rungs: combo speed IS the difficulty.
                session.setBotNextAttackMs(now + combo
                        + java.util.concurrent.ThreadLocalRandom.current().nextInt(400));
            } else {
                session.setBotNextAttackMs(now + 500L); // no valid spot: retry soon
            }
        }
    }

    /**
     * Bot crystal combo: obsidian pedestal in an air block beside the player, end crystal
     * on top, detonated a beat later. Pedestals are tracked and reverted, and the bot is
     * immune to the blasts of the crystals it placed itself.
     */
    private boolean launchCrystalAttack(Player player, PracticeSession session) {
        if (!session.botConsume(Material.OBSIDIAN, 1)
                || !session.botConsume(Material.END_CRYSTAL, 1)) {
            return false;
        }
        org.bukkit.block.Block foot = player.getLocation().getBlock();
        int[] dx = {1, -1, 0, 0};
        int[] dz = {0, 0, 1, -1};
        int start = java.util.concurrent.ThreadLocalRandom.current().nextInt(4);
        org.bukkit.block.Block spot = null;
        for (int k = 0; k < 4; k++) {
            int i = (start + k) % 4;
            org.bukkit.block.Block cand = foot.getRelative(dx[i], 0, dz[i]);
            org.bukkit.block.Block below = cand.getRelative(org.bukkit.block.BlockFace.DOWN);
            if ((cand.getType().isAir() || cand.getBlockData().isReplaceable())
                    && below.getType().isSolid()
                    && !below.getType().isAir()) {
                spot = cand;
                break;
            }
        }
        if (spot == null) {
            return false;
        }
        boolean pedestal = spot.getRelative(org.bukkit.block.BlockFace.DOWN).getType() != Material.OBSIDIAN
                && spot.getRelative(org.bukkit.block.BlockFace.DOWN).getType() != Material.BEDROCK;
        if (pedestal) {
            spot.setType(Material.OBSIDIAN, false);
            session.botPlacedBlocks().put(spot, new PracticeSession.BotBlock(
                    Material.OBSIDIAN, System.currentTimeMillis(), PEDESTAL_TTL_MS));
        }
        Location crystalLoc = spot.getLocation().add(0.5d, 1.0d, 0.5d);
        org.bukkit.entity.EnderCrystal crystal = spot.getWorld()
                .spawn(crystalLoc, org.bukkit.entity.EnderCrystal.class,
                        c -> c.setShowingBottom(false));
        session.botCrystals().add(crystal.getUniqueId());
        // One beat later: boom (if the crystal is still alive).
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (crystal.isValid()) {
                Location boom = crystal.getLocation();
                if (boom.getWorld() != null) {
                    boom.getWorld().createExplosion(boom, 6.0f, false, false, crystal);
                }
                crystal.remove();
            }
            session.botCrystals().remove(crystal.getUniqueId());
        }, 7L);
        return true;
    }

    /**
     * Bot-placed blocks melt away once their TTL expires (pedestals after a while, lava much
     * faster) so the arena stays clean. A block is only reverted when it still IS what the
     * bot placed — player edits are never rolled back.
     */
    private void revertAgedBotBlocks(PracticeSession session, long now) {
        var blocks = session.botPlacedBlocks();
        if (blocks.isEmpty()) {
            return;
        }
        var it = blocks.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            PracticeSession.BotBlock placed = entry.getValue();
            boolean aged = now - placed.atMs() > placed.ttlMs();
            if (!aged && blocks.size() <= 10) {
                break; // insertion-ordered: everything after is younger
            }
            org.bukkit.block.Block block = entry.getKey();
            if (block.getType() == placed.type()) {
                block.setType(Material.AIR, false);
            }
            it.remove();
        }
    }

    /** Removes the bot's crystals / TNT and reverts every block it placed. */
    private void clearBotArtifacts(PracticeSession session) {
        for (java.util.UUID crystalId : java.util.List.copyOf(session.botCrystals())) {
            org.bukkit.entity.Entity entity = Bukkit.getEntity(crystalId);
            if (entity != null && entity.isValid()) {
                entity.remove();
            }
        }
        session.botCrystals().clear();
        for (java.util.UUID tntId : java.util.List.copyOf(session.botTnt())) {
            org.bukkit.entity.Entity entity = Bukkit.getEntity(tntId);
            if (entity != null && entity.isValid()) {
                entity.remove();
            }
        }
        session.botTnt().clear();
        for (var entry : session.botPlacedBlocks().entrySet()) {
            if (entry.getKey().getType() == entry.getValue().type()) {
                entry.getKey().setType(Material.AIR, false);
            }
        }
        session.botPlacedBlocks().clear();
    }

    /**
     * Applies incoming damage to the combat bot and converts would-be-kills into practice
     * events: crystal bots POP (totem-style) and reset; sword bots stagger home and heal.
     *
     * @return true when the hit should be consumed (the bot handled its own "death")
     */
    public boolean onCombatBotDamaged(Player player, PracticeSession session,
                                      EntityDamageEvent event) {
        Mannequin bot = session.combatBot();
        if (bot == null || !bot.isValid()) {
            return false;
        }
        BotDifficulty diff = session.difficulty();
        // The bot never dies to its own combo weapons (crystals / TNT carts). World
        // #createExplosion(..., source) attributes the blast to the source entity, so the bot
        // itself shows up as the damager of its own combo — that must be ignored too.
        if (event instanceof org.bukkit.event.entity.EntityDamageByEntityEvent byEntity
                && (byEntity.getDamager() == null
                        || byEntity.getDamager().getUniqueId().equals(bot.getUniqueId())
                        || session.botCrystals().contains(byEntity.getDamager().getUniqueId())
                        || session.botTnt().contains(byEntity.getDamager().getUniqueId()))) {
            event.setCancelled(true);
            return true;
        }
        // The bots' own fire tricks (lava buckets) and stray flames must not pop them.
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause == EntityDamageEvent.DamageCause.FIRE
                || cause == EntityDamageEvent.DamageCause.FIRE_TICK
                || cause == EntityDamageEvent.DamageCause.LAVA
                || cause == EntityDamageEvent.DamageCause.HOT_FLOOR) {
            event.setCancelled(true);
            bot.setFireTicks(0);
            return true;
        }
        session.setBotLastDamagedMs(System.currentTimeMillis());
        boolean explosion = event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                || event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION;
        // Shield stance reduces incoming melee — and punishes mindless swinging with a stun,
        // exactly like the Quantum map's "shield stunning" toggle.
        if ((session.type() == PracticeType.SWORD || session.type() == PracticeType.NETHERITE_POT)
                && session.botShieldRaised() && !explosion) {
            event.setDamage(event.getDamage() * (1.0d - diff.shieldReduction()));
            if (diff.shieldStun()
                    && event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
                player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        org.bukkit.potion.PotionEffectType.SLOWNESS, 18, 1));
                player.sendActionBar(messages.render(player, "practice.bot-shield-stun"));
            }
        }
        if (bot.getHealth() - event.getFinalDamage() > 0.5d) {
            if (session.type() == PracticeType.CRYSTAL && explosion) {
                session.setBotRetreatUntilMs(System.currentTimeMillis() + 1200L);
            }
            return false;
        }
        event.setCancelled(true);
        boolean matchLive = session.phase() == PracticeSession.Phase.ACTIVE;
        if (session.type() == PracticeType.CRYSTAL) {
            session.incrementBotPops();
            player.playSound(bot.getLocation(), Sound.ITEM_TOTEM_USE, 1.0f, 1.0f);
            if (bot.getWorld() != null) {
                bot.getWorld().spawnParticle(org.bukkit.Particle.TOTEM_OF_UNDYING,
                        bot.getLocation().add(0, 1, 0), 80, 0.5, 1.0, 0.5, 0.4);
            }
            if (matchLive && session.botPops() >= diff.totemGoal()) {
                endBotMatch(player, session, BotMatchResult.WIN);
                return true;
            }
            player.sendActionBar(messages.render(player, "practice.bot-pop",
                    MessageService.tags("pops", session.botPops() + "/" + diff.totemGoal())));
            session.setBotRetreatUntilMs(System.currentTimeMillis() + 1200L);
            respawnCombatBot(player, session);
        } else {
            if (matchLive) {
                // One full takedown wins the match.
                player.playSound(bot.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.4f);
                endBotMatch(player, session, BotMatchResult.WIN);
                return true;
            }
            session.incrementBotPops();
            player.sendActionBar(messages.render(player, "practice.bot-down",
                    MessageService.tags("kills", String.valueOf(session.botPops()))));
            player.playSound(bot.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.4f);
            respawnCombatBot(player, session);
        }
        return true;
    }

    /** Puts the bot back at its home spot with full health after a pop / kill. */
    private void respawnCombatBot(Player player, PracticeSession session) {
        Mannequin bot = session.combatBot();
        if (bot == null) {
            return;
        }
        Location home = session.botHome();
        if (home != null && home.getWorld() != null) {
            bot.teleport(home);
        }
        if (bot.getAttribute(Attribute.MAX_HEALTH) != null) {
            bot.setHealth(bot.getAttribute(Attribute.MAX_HEALTH).getValue());
        }
        bot.setVelocity(new Vector());
        equipCombatBot(bot, session.type(), session.botShieldRaised());
        session.setBotLastDamagedMs(System.currentTimeMillis());
        session.setBotNextAttackMs(System.currentTimeMillis() + 1500L);
        session.abilities().resetConsumables(); // a fresh life restocks gaps and pots
    }

    /** Sword-bot shield stance toggle (shared GUI hook with the mace bot). */
    public void applyCombatBotShield(PracticeSession session) {
        Mannequin bot = session.combatBot();
        if (bot == null || !bot.isValid() || session.type() != PracticeType.SWORD) {
            return;
        }
        EntityEquipment eq = bot.getEquipment();
        if (eq != null) {
            eq.setItemInOffHand(session.botShieldRaised()
                    ? new ItemStack(Material.SHIELD)
                    : new ItemStack(Material.TOTEM_OF_UNDYING));
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
