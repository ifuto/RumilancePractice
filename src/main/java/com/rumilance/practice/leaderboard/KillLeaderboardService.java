package com.rumilance.practice.leaderboard;

import com.rumilance.practice.PluginIdentity;
import com.rumilance.practice.database.repository.DailyRankedStatsRepository;
import com.rumilance.practice.database.repository.PlayerRepository;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * The floating monthly-kill leaderboard placed by {@code /lbspawn kill}.
 *
 * <p>Static mode: one shared set of TextDisplay lines standing at the placement, yawed toward
 * the lobby spawn (no pitch). Personal mode: any player within {@link #TRIGGER_RADIUS} blocks
 * gets their OWN copy (hidden from everyone else via {@code hideEntity}/{@code showEntity})
 * that follows them in front of their view and always faces them — position and orientation
 * are per viewer. Leaving the radius hands the shared board back.</p>
 */
public final class KillLeaderboardService implements Listener {

    private static final String FILE = "leaderboards.yml";
    private static final String MARKER = "kill_lb";
    private static final String PERSONAL_MARKER = "kill_lb_personal";
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");
    /** Approaching closer than this switches the board into personal follow mode. */
    public static final double TRIGGER_RADIUS = 5.0;
    /** How many ranked players the board lists. */
    public static final int ROWS = 7;
    /** Stats refresh interval (millis). */
    private static final long REFRESH_MILLIS = 60_000L;
    /** Personal board hover distance in front of the viewer. */
    private static final double FOLLOW_DISTANCE = 2.5;

    private final Plugin plugin;
    private final DailyRankedStatsRepository dailyStatsRepository;
    private final PlayerRepository playerRepository;
    private final java.util.function.Supplier<Location> lobbySpawn;

    private Location base;
    private float yaw;
    private float scale = 1.2f;
    private final List<UUID> sharedDisplays = new ArrayList<>();
    private final Map<UUID, List<UUID>> personalDisplays = new LinkedHashMap<>();
    private final Map<UUID, Integer> personalLineVersion = new LinkedHashMap<>();

    private List<String> lines = List.of();
    private long linesBuiltAt;
    private int lineVersion;

    public KillLeaderboardService(Plugin plugin,
                                  DailyRankedStatsRepository dailyStatsRepository,
                                  PlayerRepository playerRepository,
                                  java.util.function.Supplier<Location> lobbySpawn) {
        this.plugin = plugin;
        this.dailyStatsRepository = dailyStatsRepository;
        this.playerRepository = playerRepository;
        this.lobbySpawn = lobbySpawn;
    }

    public NamespacedKey markerKey() {
        return new NamespacedKey(PluginIdentity.PDC_NAMESPACE, MARKER);
    }

    public NamespacedKey personalMarkerKey() {
        return new NamespacedKey(PluginIdentity.PDC_NAMESPACE, PERSONAL_MARKER);
    }

    // ------------------------------------------------------------------ lifecycle

    /** Loads the persisted placement, purges stray personal displays, spawns the board. */
    public void load() {
        purgeTagged(personalMarkerKey());
        File file = new File(PluginIdentity.dataFolder(plugin), FILE);
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("kill");
        if (section == null) {
            return;
        }
        World world = Bukkit.getWorld(section.getString("world", "world"));
        if (world == null) {
            return;
        }
        base = new Location(world, section.getDouble("x"), section.getDouble("y"), section.getDouble("z"));
        yaw = (float) section.getDouble("yaw", 0.0);
        scale = (float) section.getDouble("scale", 1.2);
        respawnShared();
    }

    public void disable() {
        despawnShared();
        personalDisplays.values().forEach(this::removeEntities);
        personalDisplays.clear();
        personalLineVersion.clear();
    }

    /** Places (or replaces) the board at the executor's feet, yawed toward the lobby spawn. */
    public void placeKillBoard(Location feet) {
        Location spawn = lobbySpawn.get();
        Location target = feet.clone();
        float facing = spawn == null ? target.getYaw()
                : yawFacing(target, spawn);
        base = new Location(feet.getWorld(), feet.getX(), feet.getY(), feet.getZ());
        yaw = facing;
        save();
        respawnShared();
    }

    public boolean isPlaced() {
        return base != null;
    }

    /** Removes the board entirely (shared + every personal copy). */
    public boolean remove() {
        if (base == null) {
            return false;
        }
        despawnShared();
        personalDisplays.values().forEach(this::removeEntities);
        personalDisplays.clear();
        personalLineVersion.clear();
        base = null;
        File file = new File(PluginIdentity.dataFolder(plugin), FILE);
        if (file.exists()) {
            file.delete();
        }
        return true;
    }

    // ------------------------------------------------------------------ per-tick follow behaviour

    /** Runs on a repeating task: stats refresh, viewer enter/leave, personal board tracking. */
    public void tick() {
        if (base == null || base.getWorld() == null) {
            return;
        }
        refreshLinesIfStale();

        Set<UUID> inRange = new HashSet<>();
        double radiusSq = TRIGGER_RADIUS * TRIGGER_RADIUS;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld() != base.getWorld()) {
                continue;
            }
            if (player.getLocation().distanceSquared(base) <= radiusSq) {
                inRange.add(player.getUniqueId());
            }
        }

        // Viewers who left (or went offline): hand the shared board back.
        for (UUID viewerId : new ArrayList<>(personalDisplays.keySet())) {
            if (!inRange.contains(viewerId)) {
                leave(viewerId);
            }
        }
        // Viewers in range: personal board visible; everyone else sees the shared one.
        for (Player player : Bukkit.getOnlinePlayers()) {
            boolean near = inRange.contains(player.getUniqueId());
            setSharedHidden(player, near);
            if (near) {
                trackPersonal(player);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        leave(event.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------ internals

    private void enter(Player player) {
        List<UUID> displays = new ArrayList<>();
        World world = base.getWorld();
        if (world == null) {
            return;
        }
        Location follow = followLocation(player);
        float faceYaw = player.getLocation().getYaw() + 180f;
        List<String> current = lines;
        for (int i = 0; i < current.size(); i++) {
            Location line = follow.clone().add(0, lineOffset(i), 0);
            line.setYaw(faceYaw);
            displays.add(spawnDisplay(world, line, current.get(i), PERSONAL_MARKER).getUniqueId());
        }
        personalDisplays.put(player.getUniqueId(), displays);
        personalLineVersion.put(player.getUniqueId(), lineVersion);
    }

    /** Moves / rebuilds the viewer's personal board so it keeps following them. */
    private void trackPersonal(Player player) {
        List<UUID> displays = personalDisplays.get(player.getUniqueId());
        if (displays == null || displays.isEmpty()) {
            enter(player);
            return;
        }
        Integer version = personalLineVersion.get(player.getUniqueId());
        if (version == null || version != lineVersion || displays.size() != lines.size()) {
            removeEntities(displays);
            enter(player);
            return;
        }
        Location follow = followLocation(player);
        float faceYaw = player.getLocation().getYaw() + 180f;
        for (int i = 0; i < displays.size(); i++) {
            Entity entity = Bukkit.getEntity(displays.get(i));
            if (!(entity instanceof TextDisplay display)) {
                continue;
            }
            Location line = follow.clone().add(0, lineOffset(i), 0);
            line.setYaw(faceYaw);
            line.setPitch(0f);
            display.teleport(line);
        }
    }

    private void leave(UUID viewerId) {
        List<UUID> displays = personalDisplays.remove(viewerId);
        personalLineVersion.remove(viewerId);
        if (displays != null) {
            removeEntities(displays);
        }
        Player player = Bukkit.getPlayer(viewerId);
        if (player != null && player.isOnline()) {
            setSharedHidden(player, false);
        }
    }

    /** Hides (or reveals) every shared display for one viewer. */
    private void setSharedHidden(Player player, boolean hidden) {
        for (UUID id : sharedDisplays) {
            Entity entity = Bukkit.getEntity(id);
            if (entity == null) {
                continue;
            }
            try {
                if (hidden) {
                    player.hideEntity(plugin, entity);
                } else {
                    player.showEntity(plugin, entity);
                }
            } catch (Throwable ignored) {
                // Viewer already gone / entity mid-remove: harmless.
            }
        }
    }

    /** Where the personal board hovers: in front of the viewer, just below eye level. */
    private Location followLocation(Player player) {
        Location eye = player.getEyeLocation();
        float yawRad = (float) Math.toRadians(eye.getYaw());
        double dx = -Math.sin(yawRad);
        double dz = Math.cos(yawRad);
        return new Location(player.getWorld(),
                eye.getX() + dx * FOLLOW_DISTANCE,
                eye.getY() - 0.6,
                eye.getZ() + dz * FOLLOW_DISTANCE);
    }

    private double lineOffset(int index) {
        // Board grows upward from the anchor point, bottom line first.
        return index * 0.34 * Math.max(0.5, scale);
    }

    private void respawnShared() {
        despawnShared();
        World world = base == null ? null : base.getWorld();
        if (world == null) {
            return;
        }
        refreshLinesIfStale();
        List<String> current = lines;
        for (int i = 0; i < current.size(); i++) {
            Location line = base.clone().add(0, 0.25 + lineOffset(i), 0);
            line.setYaw(yaw);
            line.setPitch(0f);
            sharedDisplays.add(spawnDisplay(world, line, current.get(i), MARKER).getUniqueId());
        }
    }

    private void despawnShared() {
        removeEntities(sharedDisplays);
        sharedDisplays.clear();
    }

    private void removeEntities(List<UUID> ids) {
        for (UUID id : ids) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) {
                entity.remove();
            }
        }
        ids.clear();
    }

    private TextDisplay spawnDisplay(World world, Location location, String miniMessage, String marker) {
        return world.spawn(location, TextDisplay.class, display -> {
            display.text(MiniMessage.miniMessage().deserialize(miniMessage));
            display.setBillboard(Display.Billboard.FIXED);
            display.setShadowed(false);
            display.setSeeThrough(false);
            display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            display.setAlignment(TextDisplay.TextAlignment.CENTER);
            float s = Math.max(0.25f, Math.min(16f, scale));
            display.setTransformation(new Transformation(
                    new Vector3f(0f, 0f, 0f),
                    new AxisAngle4f(0f, 0f, 0f, 1f),
                    new Vector3f(s, s, s),
                    new AxisAngle4f(0f, 0f, 0f, 1f)));
            display.setPersistent(true);
            display.setGravity(false);
            display.setInvulnerable(true);
            NamespacedKey key = new NamespacedKey(PluginIdentity.PDC_NAMESPACE, marker);
            display.getPersistentDataContainer().set(key, PersistentDataType.STRING, "kill");
        });
    }

    /** Rebuilds the leaderboard text once a minute from the monthly kill totals. */
    private void refreshLinesIfStale() {
        long now = System.currentTimeMillis();
        if (!lines.isEmpty() && now - linesBuiltAt < REFRESH_MILLIS) {
            return;
        }
        linesBuiltAt = now;
        List<String> built = new ArrayList<>();
        built.add("<gold><b>月間キルランキング</b></gold>");
        try {
            String month = LocalDate.now().format(MONTH);
            List<DailyRankedStatsRepository.MonthlyEntry> top =
                    dailyStatsRepository.topKillsOfMonth(month, ROWS);
            if (top.isEmpty()) {
                built.add("<gray>まだ記録がありません</gray>");
            }
            int rank = 0;
            for (DailyRankedStatsRepository.MonthlyEntry entry : top) {
                rank++;
                String name = resolveName(entry.playerId());
                double kd = entry.deaths() <= 0 ? entry.kills()
                        : (double) entry.kills() / entry.deaths();
                built.add("<yellow>#" + rank + "</yellow> <white>" + name + "</white>"
                        + " <gold>" + entry.kills() + " kills</gold>"
                        + " <gray>KD " + String.format(java.util.Locale.ROOT, "%.2f", kd) + "</gray>");
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[KillLB] failed loading monthly stats", e);
            if (built.size() == 1) {
                built.add("<gray>読み込みに失敗しました</gray>");
            }
        }
        lines = built;
        lineVersion++;
        // Re-render the shared board with fresh numbers.
        if (base != null && !sharedDisplays.isEmpty()) {
            respawnShared();
        }
    }

    private String resolveName(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            return online.getName();
        }
        try {
            return playerRepository.findByUuid(uuid)
                    .map(data -> data.username())
                    .orElse("???");
        } catch (Exception e) {
            return "???";
        }
    }

    /** Horizontal yaw from {@code from} toward {@code target} (pitch ignored). */
    private static float yawFacing(Location from, Location target) {
        Vector direction = new Vector(
                target.getX() - from.getX(), 0, target.getZ() - from.getZ());
        if (direction.lengthSquared() < 1.0E-6) {
            return from.getYaw();
        }
        Location helper = from.clone();
        helper.setDirection(direction);
        return helper.getYaw();
    }

    private void purgeTagged(NamespacedKey key) {
        for (World world : Bukkit.getWorlds()) {
            for (TextDisplay display : world.getEntitiesByClass(TextDisplay.class)) {
                if (display.getPersistentDataContainer().has(key, PersistentDataType.STRING)) {
                    display.remove();
                }
            }
        }
    }

    private void save() {
        if (base == null) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("kill.world", base.getWorld() == null ? "world" : base.getWorld().getName());
        yaml.set("kill.x", base.getX());
        yaml.set("kill.y", base.getY());
        yaml.set("kill.z", base.getZ());
        yaml.set("kill.yaw", yaw);
        yaml.set("kill.scale", scale);
        try {
            yaml.save(new File(PluginIdentity.dataFolder(plugin), FILE));
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "[KillLB] failed saving leaderboards.yml", e);
        }
    }
}
