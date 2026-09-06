package com.rumilance.practice.leaderboard;

import com.rumilance.practice.PluginIdentity;
import com.rumilance.practice.database.repository.AnnualStreakRepository;
import com.rumilance.practice.database.repository.DailyRankedStatsRepository;
import com.rumilance.practice.database.repository.PlayerRepository;
import com.rumilance.practice.locale.MessageService;
import net.kyori.adventure.text.Component;
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
import org.bukkit.event.world.EntitiesLoadEvent;
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
 * <p>The board is ONE text display: title line at the very top, ranks descending downward,
 * rendered on a translucent black background panel ({@code setBackgroundColor} ARGB — the
 * vanilla text-display backdrop).</p>
 *
 * <p><b>Player tracking is left to the client.</b> Every viewer inside
 * {@link #TRIGGER_RADIUS} gets a PRIVATE copy with {@link Display.Billboard#VERTICAL}:
 * the client turns the display toward its own player every frame (smooth vanilla
 * animation, no server-side easing). Several players near one board therefore each see the
 * board facing themselves — one duplicate per nearby viewer, position never moves. Outside
 * the radius the shared copy stands at its saved yaw.</p>
 */
public final class KillLeaderboardService implements Listener {

    private static final String FILE = "leaderboards.yml";
    private static final String MARKER = "kill_lb";
    private static final String PERSONAL_MARKER = "kill_lb_personal";
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");
    /** Approaching closer than this switches the board into personal face-the-player mode. */
    public static final double TRIGGER_RADIUS = 7.0;
    /** How many players each board lists. */
    public static final int ROWS = 7;
    /** Stats refresh interval (millis). */
    private static final long REFRESH_MILLIS = 60_000L;
    /** Translucent black backdrop behind the board text. */
    private static final Color BACKDROP = Color.fromARGB(0x60, 0x00, 0x00, 0x00);
    /** Vanilla text metrics in pixels: glyph height and line advance. */
    private static final double GLYPH_PX = 8.0;
    private static final double LINE_ADVANCE_PX = 9.0;

    /** Medal colours for the top three ranks. */
    private static final String[] RANK_COLORS = {"<gold>", "<#E8E8E8>", "<#FF9A3D>"};

    private final Plugin plugin;
    private final DailyRankedStatsRepository dailyStatsRepository;
    private final AnnualStreakRepository annualStreakRepository;
    private final PlayerRepository playerRepository;
    private final MessageService messageService;
    private final java.util.function.Supplier<Location> lobbySpawn;

    private final Map<String, Board> boards = new LinkedHashMap<>();
    /** (type + viewer) -> personal copy state. */
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
        UUID sharedDisplay;
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

    private static final class ViewerState {
        String locale;
        int lineVersion;
        UUID display;
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
            removeEntity(board.sharedDisplay);
            board.sharedDisplay = null;
        }
        for (ViewerState state : viewers.values()) {
            removeEntity(state.display);
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
        removeEntity(board.sharedDisplay);
        board.sharedDisplay = null;
        board.base = null;
        save();
        return true;
    }

    // ------------------------------------------------------------------ per-tick behaviour

    /** Runs on the repeating task: stats refresh + viewer enter/leave (no animation steps). */
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
            for (Player player : Bukkit.getOnlinePlayers()) {
                String key = viewerKey(board, player.getUniqueId());
                ViewerState state = viewers.get(key);
                boolean near = inRange.contains(player.getUniqueId());
                if (near && state == null) {
                    enter(board, player);
                } else if (!near && state != null) {
                    viewers.remove(key);
                    removeEntity(state.display);
                    setSharedHidden(board, player, false);
                } else if (near && state != null) {
                    // Rebuild the personal copy when stats refresh or the locale changed.
                    String locale = messageService.resolveLocale(player);
                    if (state.lineVersion != board.lineVersion || !locale.equals(state.locale)) {
                        removeEntity(state.display);
                        state.locale = locale;
                        spawnPersonal(board, player.getUniqueId(), state);
                    }
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
                removeEntity(state.display);
            }
        }
    }

    /**
     * Deduplicates boards. Displays are persistent, so the server also saves them into the
     * chunk and respawns them on chunk load — independently of this service respawning the
     * same board from {@code leaderboards.yml}. Depending on load ordering that leaves two
     * overlapping boards at one spot. Whenever a chunk's entities become available, drop any
     * managed display this service is not currently tracking.
     */
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        NamespacedKey sharedKey = markerKey(MARKER);
        NamespacedKey personalKey = markerKey(PERSONAL_MARKER);
        Set<UUID> tracked = new HashSet<>();
        for (Board board : boards.values()) {
            if (board.sharedDisplay != null) {
                tracked.add(board.sharedDisplay);
            }
        }
        for (ViewerState state : viewers.values()) {
            if (state.display != null) {
                tracked.add(state.display);
            }
        }
        for (Entity entity : event.getEntities()) {
            if (!(entity instanceof TextDisplay display)) {
                continue;
            }
            var pdc = display.getPersistentDataContainer();
            boolean ours = pdc.has(sharedKey, PersistentDataType.STRING)
                    || pdc.has(personalKey, PersistentDataType.STRING);
            if (ours && !tracked.contains(display.getUniqueId())) {
                display.remove();
            }
        }
    }

    // ------------------------------------------------------------------ viewer copies

    private void enter(Board board, Player player) {
        ViewerState state = new ViewerState();
        state.locale = messageService.resolveLocale(player);
        spawnPersonal(board, player.getUniqueId(), state);
        viewers.put(viewerKey(board, player.getUniqueId()), state);
        setSharedHidden(board, player, true);
    }

    /**
     * Spawns the viewer's private copy at the board's position. Billboard VERTICAL lets the
     * CLIENT rotate it toward that viewer every frame — the vanilla tracking animation, no
     * server-side easing or teleports.
     */
    private void spawnPersonal(Board board, UUID playerId, ViewerState state) {
        World world = board.base.getWorld();
        if (world == null) {
            return;
        }
        List<String> lines = linesFor(board, state.locale);
        Location at = panelPivot(board, lines.size());
        at.setYaw(board.yaw);
        at.setPitch(0f);
        TextDisplay display = world.spawn(at, TextDisplay.class, d -> configure(d,
                String.join("\n", lines), PERSONAL_MARKER, board.scale, Display.Billboard.VERTICAL));
        state.display = display.getUniqueId();
        state.lineVersion = board.lineVersion;
    }

    private void clearViewersOf(Board board) {
        String prefix = board.type + ":";
        for (Map.Entry<String, ViewerState> entry : Map.copyOf(viewers).entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                viewers.remove(entry.getKey());
                removeEntity(entry.getValue().display);
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
        Entity entity = board.sharedDisplay == null ? null : Bukkit.getEntity(board.sharedDisplay);
        if (entity == null) {
            return;
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

    private static String viewerKey(Board board, UUID playerId) {
        return board.type + ":" + playerId;
    }

    // ------------------------------------------------------------------ shared display

    private void respawnShared(Board board) {
        removeEntity(board.sharedDisplay);
        board.sharedDisplay = null;
        World world = board.base == null ? null : board.base.getWorld();
        if (world == null) {
            return;
        }
        List<String> lines = linesFor(board, messageService.localeService().defaultLocale());
        Location at = panelPivot(board, lines.size());
        at.setYaw(board.yaw);
        at.setPitch(0f);
        TextDisplay display = world.spawn(at, TextDisplay.class, d -> configure(d,
                String.join("\n", lines), MARKER, board.scale, Display.Billboard.FIXED));
        board.sharedDisplay = display.getUniqueId();
        board.lastSharedContent = String.join("\n", lines);
        // Shared text changed -> force personal copies to rebuild on their next tick step.
        board.lineVersion++;
    }

    /**
     * The panel's pivot: the text display grows DOWNWARD from its position, so the pivot sits
     * at the top of the panel, placing the panel's bottom edge at the classic {@code +0.25}
     * board offset.
     */
    private Location panelPivot(Board board, int lineCount) {
        double scale = Math.max(0.25, Math.min(16, board.scale));
        double height = ((lineCount - 1) * LINE_ADVANCE_PX + GLYPH_PX) / 16.0 * scale;
        return board.base.clone().add(0, 0.25 + height, 0);
    }

    private void removeEntity(UUID id) {
        if (id == null) {
            return;
        }
        Entity entity = Bukkit.getEntity(id);
        if (entity != null) {
            entity.remove();
        }
    }

    private void configure(TextDisplay display, String miniMessage, String marker, float scale,
                           Display.Billboard billboard) {
        Component text;
        try {
            text = MiniMessage.miniMessage().deserialize(miniMessage);
        } catch (RuntimeException e) {
            text = Component.text(miniMessage);
        }
        display.text(text);
        display.setBillboard(billboard);
        display.setShadowed(true);
        display.setSeeThrough(false);
        display.setBackgroundColor(BACKDROP);
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
        if (board.sharedDisplay != null) {
            // Only respawn when the displayed numbers actually changed (no per-minute flicker).
            List<String> fresh = linesFor(board, messageService.localeService().defaultLocale());
            String joined = String.join("\n", fresh);
            if (joined.equals(board.lastSharedContent)) {
                return;
            }
            respawnShared(board);
            // The fresh shared entity is visible by default: re-hide it from every viewer who
            // currently has a personal copy up.
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
