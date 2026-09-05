package com.rumilance.practice.leaderboard;

import com.rumilance.practice.PluginIdentity;
import com.rumilance.practice.database.repository.AnnualStreakRepository;
import com.rumilance.practice.database.repository.DailyRankedStatsRepository;
import com.rumilance.practice.database.repository.PlayerRepository;
import com.rumilance.practice.locale.MessageService;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Floating leaderboards placed with {@code /lbspawn} ({@code kill} = monthly kills + KD,
 * {@code streak} = annual best win streak).
 *
 * <p>Every viewer within {@link #TRIGGER_RADIUS} blocks gets a PRIVATE copy of the board at
 * the board's own position (hidden from everyone else via {@code hideEntity}/{@code showEntity})
 * that turns to face them — orientation is per viewer, position never moves. Entering the
 * radius eases the board from its lobby-spawn yaw toward the player; leaving eases it back
 * before the shared board is handed back. Both transitions use cubic ease-out.</p>
 */
public final class KillLeaderboardService implements Listener {

    private static final String FILE = "leaderboards.yml";
    private static final String MARKER = "kill_lb";
    private static final String PERSONAL_MARKER = "kill_lb_personal";
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");
    /** Approaching closer than this switches the board into personal face-the-player mode. */
    public static final double TRIGGER_RADIUS = 5.0;
    /** How many players each board lists. */
    public static final int ROWS = 7;
    /** Stats refresh interval (millis). */
    private static final long REFRESH_MILLIS = 60_000L;
    /** Seconds the ease-out rotation takes (entering / leaving the radius). */
    private static final double EASE_SECONDS = 0.6;
    /** Seconds between service ticks (matches the bootstrap timer). */
    private static final double TICK_SECONDS = 3.0 / 20.0;

    /** Medal colours for the top three ranks. */
    private static final String[] RANK_COLORS = {"<gold>", "<#E8E8E8>", "<#FF9A3D>"};

    private final Plugin plugin;
    private final DailyRankedStatsRepository dailyStatsRepository;
    private final AnnualStreakRepository annualStreakRepository;
    private final PlayerRepository playerRepository;
    private final MessageService messageService;
    private final java.util.function.Supplier<Location> lobbySpawn;

    private final Map<String, Board> boards = new LinkedHashMap<>();
    /** (type + viewer) -> personal animation state. */
    private final Map<String, ViewerState> viewers = new HashMap<>();

    public KillLeaderboardService(Plugin plugin,
                                  DailyRankedStatsRepository dailyStatsRepository,
                                  AnnualStreakRepository annualStreakRepository,
                                  PlayerRepository playerRepository,
                                  MessageService messageService,
                                  java.util.function.Supplier<Location> lobbySpawn) {
        this.plugin = plugin;
        this.dailyStatsRepository = dailyStatsRepository;
        this.annualStreakRepository = annualStreakRepository;
        this.playerRepository = playerRepository;
        this.messageService = messageService;
        this.lobbySpawn = lobbySpawn;
        boards.put("kill", new Board("kill"));
        boards.put("streak", new Board("streak"));
    }

    private NamespacedKey markerKey(String marker) {
        return new NamespacedKey(PluginIdentity.PDC_NAMESPACE, marker);
    }

    // ------------------------------------------------------------------ board state

    private static final class Board {
        final String type;
        Location base;
        float yaw;
        float scale = 1.2f;
        final List<UUID> sharedDisplays = new ArrayList<>();
        /** Cached lines per locale + freshness stamp. */
        final Map<String, List<String>> linesByLocale = new HashMap<>();
        long linesBuiltAt;
        int lineVersion;
        /** Joined default-locale lines currently on display (skip rebuilds when unchanged). */
        String lastSharedContent = "";

        Board(String type) {
            this.type = type;
        }
    }

    private enum Phase { ENTERING, TRACKING, EXITING }

    private static final class ViewerState {
        Phase phase;
        double progress;
        float fromYaw;
        float currentYaw;
        String locale;
        int lineVersion;
        final List<UUID> displays = new ArrayList<>();
    }

    // ------------------------------------------------------------------ lifecycle

    public void load() {
        purgeTagged(PERSONAL_MARKER);
        File file = new File(PluginIdentity.dataFolder(plugin), FILE);
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (Board board : boards.values()) {
            ConfigurationSection section = yaml.getConfigurationSection(board.type);
            if (section == null) {
                continue;
            }
            World world = Bukkit.getWorld(section.getString("world", "world"));
            if (world == null) {
                continue;
            }
            board.base = new Location(world, section.getDouble("x"), section.getDouble("y"),
                    section.getDouble("z"));
            board.yaw = (float) section.getDouble("yaw", 0.0);
            board.scale = (float) section.getDouble("scale", 1.2);
            respawnShared(board);
        }
    }

    public void disable() {
        for (Board board : boards.values()) {
            removeEntities(board.sharedDisplays);
        }
        for (ViewerState state : viewers.values()) {
            removeEntities(state.displays);
        }
        viewers.clear();
    }

    /** Places (or replaces) a board at the executor's feet, yawed toward the lobby spawn. */
    public void place(String type, Location feet) {
        Board board = boards.get(type);
        if (board == null) {
            return;
        }
        Location spawn = lobbySpawn.get();
        float facing = spawn == null ? feet.getYaw() : yawFacing(feet, spawn);
        clearViewersOf(board);
        board.base = new Location(feet.getWorld(), feet.getX(), feet.getY(), feet.getZ());
        board.yaw = facing;
        save();
        respawnShared(board);
    }

    public boolean remove(String type) {
        Board board = boards.get(type);
        if (board == null || board.base == null) {
            return false;
        }
        clearViewersOf(board);
        removeEntities(board.sharedDisplays);
        board.base = null;
        save();
        return true;
    }

    // ------------------------------------------------------------------ per-tick behaviour

    /** Runs on the repeating task: stats refresh, viewer enter/leave, eased rotation. */
    public void tick() {
        for (Board board : boards.values()) {
            if (board.base == null || board.base.getWorld() == null) {
                continue;
            }
            refreshLinesIfStale(board);

            Set<UUID> inRange = new HashSet<>();
            double radiusSq = TRIGGER_RADIUS * TRIGGER_RADIUS;
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getWorld() != board.base.getWorld()) {
                    continue;
                }
                if (player.getLocation().distanceSquared(board.base) <= radiusSq) {
                    inRange.add(player.getUniqueId());
                }
            }
            // Viewer lifecycle.
            for (Player player : Bukkit.getOnlinePlayers()) {
                String key = viewerKey(board, player.getUniqueId());
                ViewerState state = viewers.get(key);
                boolean near = inRange.contains(player.getUniqueId());
                if (near && state == null) {
                    enter(board, player);
                } else if (!near && state != null && state.phase != Phase.EXITING) {
                    beginExit(board, player.getUniqueId(), state);
                }
            }
            // Advance animations.
            for (Player player : Bukkit.getOnlinePlayers()) {
                ViewerState state = viewers.get(viewerKey(board, player.getUniqueId()));
                if (state != null) {
                    animate(board, player, state);
                }
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        for (Board board : boards.values()) {
            ViewerState state = viewers.remove(viewerKey(board, id));
            if (state != null) {
                removeEntities(state.displays);
            }
        }
    }

    // ------------------------------------------------------------------ viewer animation

    private void enter(Board board, Player player) {
        String locale = messageService.resolveLocale(player);
        ViewerState state = new ViewerState();
        state.phase = Phase.ENTERING;
        state.progress = 0.0;
        state.fromYaw = board.yaw;
        state.currentYaw = board.yaw;
        state.locale = locale;
        spawnPersonal(board, player.getUniqueId(), state, board.yaw);
        viewers.put(viewerKey(board, player.getUniqueId()), state);
        setSharedHidden(board, player, true);
    }

    private void beginExit(Board board, UUID playerId, ViewerState state) {
        state.phase = Phase.EXITING;
        state.progress = 0.0;
        state.fromYaw = state.currentYaw;
    }

    /** Advances one animation step and applies the resulting yaw. */
    private void animate(Board board, Player player, ViewerState state) {
        switch (state.phase) {
            case ENTERING -> {
                float target = yawFacing(board.base, player.getLocation());
                state.progress += TICK_SECONDS / EASE_SECONDS;
                if (state.progress >= 1.0) {
                    state.currentYaw = target;
                    state.phase = Phase.TRACKING;
                } else {
                    state.currentYaw = angleLerp(state.fromYaw, target, easeOutCubic(state.progress));
                }
            }
            case TRACKING -> {
                float target = yawFacing(board.base, player.getLocation());
                // Gentle pursuit so head movement stays smooth without a full re-ease.
                float diff = shortestAngleDiff(state.currentYaw, target);
                state.currentYaw = Math.abs(diff) < 0.5f ? target : state.currentYaw + diff * 0.35f;
                // Rebuild the personal copy when stats refresh changed the lines.
                if (state.lineVersion != board.lineVersion
                        || !messageService.resolveLocale(player).equals(state.locale)) {
                    float keep = state.currentYaw;
                    removeEntities(state.displays);
                    state.locale = messageService.resolveLocale(player);
                    spawnPersonal(board, player.getUniqueId(), state, keep);
                }
            }
            case EXITING -> {
                state.progress += TICK_SECONDS / EASE_SECONDS;
                if (state.progress >= 1.0) {
                    viewers.remove(viewerKey(board, player.getUniqueId()));
                    removeEntities(state.displays);
                    setSharedHidden(board, player, false);
                    return;
                }
                state.currentYaw = angleLerp(state.fromYaw, board.yaw, easeOutCubic(state.progress));
            }
        }
        applyYaw(state, state.currentYaw);
    }

    private void applyYaw(ViewerState state, float yaw) {
        for (UUID id : state.displays) {
            Entity entity = Bukkit.getEntity(id);
            if (entity == null) {
                continue;
            }
            Location location = entity.getLocation();
            location.setYaw(yaw);
            location.setPitch(0f);
            entity.teleport(location);
        }
    }

    /** Spawns the viewer's private copy at the board's own position with the given yaw. */
    private void spawnPersonal(Board board, UUID playerId, ViewerState state, float yaw) {
        World world = board.base.getWorld();
        if (world == null) {
            return;
        }
        List<String> lines = linesFor(board, state.locale);
        for (int i = 0; i < lines.size(); i++) {
            Location line = board.base.clone().add(0, 0.25 + lineOffset(board, i), 0);
            line.setYaw(yaw);
            line.setPitch(0f);
            state.displays.add(
                    spawnDisplay(world, line, lines.get(i), PERSONAL_MARKER, board.scale).getUniqueId());
        }
        state.lineVersion = board.lineVersion;
    }

    private void clearViewersOf(Board board) {
        String prefix = board.type + ":";
        for (Map.Entry<String, ViewerState> entry : Map.copyOf(viewers).entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                viewers.remove(entry.getKey());
                removeEntities(entry.getValue().displays);
                String raw = entry.getKey().substring(prefix.length());
                try {
                    Player player = Bukkit.getPlayer(UUID.fromString(raw));
                    if (player != null) {
                        setSharedHidden(board, player, false);
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    private void setSharedHidden(Board board, Player player, boolean hidden) {
        for (UUID id : board.sharedDisplays) {
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
            }
        }
    }

    private static String viewerKey(Board board, UUID playerId) {
        return board.type + ":" + playerId;
    }

    // ------------------------------------------------------------------ shared displays

    private void respawnShared(Board board) {
        removeEntities(board.sharedDisplays);
        World world = board.base == null ? null : board.base.getWorld();
        if (world == null) {
            return;
        }
        List<String> lines = linesFor(board, messageService.localeService().defaultLocale());
        for (int i = 0; i < lines.size(); i++) {
            Location line = board.base.clone().add(0, 0.25 + lineOffset(board, i), 0);
            line.setYaw(board.yaw);
            line.setPitch(0f);
            board.sharedDisplays.add(
                    spawnDisplay(world, line, lines.get(i), MARKER, board.scale).getUniqueId());
        }
        board.lastSharedContent = String.join("\n", lines);
        // Shared text changed -> force personal copies to rebuild on their next animate step.
        board.lineVersion++;
    }

    private double lineOffset(Board board, int index) {
        return index * 0.34 * Math.max(0.5, board.scale);
    }

    /** Removes the listed entities and clears the list. */
    private void removeEntities(List<UUID> ids) {
        for (UUID id : List.copyOf(ids)) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) {
                entity.remove();
            }
        }
        ids.clear();
    }

    private TextDisplay spawnDisplay(World world, Location location, String miniMessage,
                                     String marker, float scale) {
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
            display.getPersistentDataContainer()
                    .set(markerKey(marker), PersistentDataType.STRING, "lb");
        });
    }

    // ------------------------------------------------------------------ line building

    private List<String> linesFor(Board board, String locale) {
        List<String> cached = board.linesByLocale.get(locale);
        return cached == null || cached.isEmpty()
                ? buildLines(board, locale) : cached;
    }

    /** Rebuilds every locale's cached lines once per refresh window. */
    private void refreshLinesIfStale(Board board) {
        long now = System.currentTimeMillis();
        if (!board.linesByLocale.isEmpty() && now - board.linesBuiltAt < REFRESH_MILLIS) {
            return;
        }
        board.linesBuiltAt = now;
        for (String locale : List.copyOf(board.linesByLocale.keySet())) {
            board.linesByLocale.put(locale, buildLines(board, locale));
        }
        if (!board.sharedDisplays.isEmpty()) {
            // Only respawn when the displayed numbers actually changed (no per-minute flicker).
            List<String> fresh = linesFor(board, messageService.localeService().defaultLocale());
            String joined = String.join("\n", fresh);
            if (joined.equals(board.lastSharedContent)) {
                return;
            }
            rebuildShared(board);
            // Fresh shared entities are visible by default: re-hide them from every viewer
            // who currently has a personal copy up.
            String prefix = board.type + ":";
            for (String key : viewers.keySet()) {
                if (!key.startsWith(prefix)) {
                    continue;
                }
                try {
                    Player player = Bukkit.getPlayer(UUID.fromString(key.substring(prefix.length())));
                    if (player != null) {
                        setSharedHidden(board, player, true);
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    /** Re-renders shared displays with fresh numbers (viewer hide state re-applied next tick). */
    private void rebuildShared(Board board) {
        World world = board.base == null ? null : board.base.getWorld();
        if (world == null) {
            return;
        }
        removeEntities(board.sharedDisplays);
        List<String> lines = linesFor(board, messageService.localeService().defaultLocale());
        for (int i = 0; i < lines.size(); i++) {
            Location line = board.base.clone().add(0, 0.25 + lineOffset(board, i), 0);
            line.setYaw(board.yaw);
            line.setPitch(0f);
            board.sharedDisplays.add(
                    spawnDisplay(world, line, lines.get(i), MARKER, board.scale).getUniqueId());
        }
        board.lastSharedContent = String.join("\n", lines);
        board.lineVersion++;
    }

    private List<String> buildLines(Board board, String locale) {
        List<String> out = new ArrayList<>();
        try {
            if ("kill".equals(board.type)) {
                out.add(raw(locale, "lb.kill-title"));
                String month = LocalDate.now().format(MONTH);
                List<DailyRankedStatsRepository.MonthlyEntry> top =
                        dailyStatsRepository.topKillsOfMonth(month, ROWS);
                if (top.isEmpty()) {
                    out.add(raw(locale, "lb.no-records"));
                }
                int rank = 0;
                for (DailyRankedStatsRepository.MonthlyEntry entry : top) {
                    rank++;
                    double kd = entry.deaths() <= 0 ? entry.kills()
                            : (double) entry.kills() / entry.deaths();
                    String row = raw(locale, "lb.kill-row")
                            .replace("{name}", resolveName(entry.playerId()))
                            .replace("{kills}", String.valueOf(entry.kills()))
                            .replace("{kd}", String.format(java.util.Locale.ROOT, "%.2f", kd));
                    out.add(rankPrefix(rank) + row);
                }
            } else {
                out.add(raw(locale, "lb.streak-title"));
                List<AnnualStreakRepository.StreakEntry> top =
                        annualStreakRepository.topBestStreaks(ROWS);
                if (top.isEmpty()) {
                    out.add(raw(locale, "lb.no-records"));
                }
                int rank = 0;
                for (AnnualStreakRepository.StreakEntry entry : top) {
                    rank++;
                    String row = raw(locale, "lb.streak-row")
                            .replace("{name}", resolveName(entry.playerId()))
                            .replace("{streak}", String.valueOf(entry.bestStreak()));
                    out.add(rankPrefix(rank) + row);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[Leaderboard] failed building " + board.type, e);
            if (out.size() <= 1) {
                out.add(raw(locale, "lb.no-records"));
            }
        }
        board.linesByLocale.put(locale, out);
        return out;
    }

    private String raw(String locale, String key) {
        return messageService.localeService().rawMessage(locale, key);
    }

    /** Colored rank prefix: gold / silver / bronze medals for the top three. */
    private static String rankPrefix(int rank) {
        String color = rank <= RANK_COLORS.length ? RANK_COLORS[rank - 1] : "<gray>";
        return color + rank + ".</" + closingTag(color) + "> ";
    }

    private static String closingTag(String openTag) {
        // "<gold>" -> "gold", "<#E8E8E8>" -> "#E8E8E8"
        return openTag.substring(1, openTag.length() - 1);
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

    // ------------------------------------------------------------------ math helpers

    private static double easeOutCubic(double t) {
        double clamped = Math.max(0.0, Math.min(1.0, t));
        double inv = 1.0 - clamped;
        return 1.0 - inv * inv * inv;
    }

    /** Shortest signed angle difference {@code target - from} in degrees (-180..180]. */
    private static float shortestAngleDiff(float from, float target) {
        float diff = (target - from) % 360f;
        if (diff > 180f) {
            diff -= 360f;
        } else if (diff < -180f) {
            diff += 360f;
        }
        return diff;
    }

    private static float angleLerp(float from, float target, double t) {
        return from + shortestAngleDiff(from, target) * (float) t;
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

    // ------------------------------------------------------------------ persistence

    private void purgeTagged(String marker) {
        NamespacedKey key = markerKey(marker);
        for (World world : Bukkit.getWorlds()) {
            for (TextDisplay display : world.getEntitiesByClass(TextDisplay.class)) {
                if (display.getPersistentDataContainer().has(key, PersistentDataType.STRING)) {
                    display.remove();
                }
            }
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Board board : boards.values()) {
            if (board.base == null) {
                continue;
            }
            String path = board.type;
            yaml.set(path + ".world", board.base.getWorld() == null ? "world" : board.base.getWorld().getName());
            yaml.set(path + ".x", board.base.getX());
            yaml.set(path + ".y", board.base.getY());
            yaml.set(path + ".z", board.base.getZ());
            yaml.set(path + ".yaw", board.yaw);
            yaml.set(path + ".scale", board.scale);
        }
        try {
            yaml.save(new File(PluginIdentity.dataFolder(plugin), FILE));
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "[Leaderboard] failed saving leaderboards.yml", e);
        }
    }
}
