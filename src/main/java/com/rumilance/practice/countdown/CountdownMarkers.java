package com.rumilance.practice.countdown;

import com.rumilance.practice.session.MatchSession;
import com.rumilance.practice.locale.MessageService;
import com.rumilance.practice.sound.SoundService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * The floating Ready / Leave blocks of the pre-match countdown.
 *
 * <p>Two blocks hang in front of every fighter's <em>arena spawn</em>
 * ({@link CountdownAnchor}), so diagonal spawns are correct too: five blocks ahead of the
 * spawn, one to the right is the emerald block (Ready), one to the left the redstone block
 * (Leave), both at eye height. The anchor is the teleport destination, not the player's
 * current position, so the pair is where the fighter is being pinned. They spin slowly on the
 * spot — {@link #SPIN_DEGREES_PER_TICK} is one turn every four seconds, which reads as
 * "idle" rather than "machinery".</p>
 *
 * <p>Looking at a block makes it glow (green / red) and swaps the action bar for
 * {@code 1/2 - Ready ✓} or {@code 0/2 - Leave ☓}, where the leading number is 1 when the
 * opponent has already pressed Ready. While not looking, an opponent who is Ready shows
 * {@code <name> is ready ✓}. Right-clicking the emerald block readies you; a left click
 * while looking at the redstone block leaves the match. Both Ready skips the countdown.</p>
 */
public final class CountdownMarkers implements Listener {

    /** One turn every 4 seconds: slow enough to look like it is hovering. */
    private static final float SPIN_DEGREES_PER_TICK = 1.5f;
    private static final float BLOCK_SCALE = 0.9f;
    /** Gaze test: within this many blocks ... */
    private static final double GAZE_RANGE = 6.0;
    /** ... and within this cone half-angle (degrees). */
    private static final double GAZE_ANGLE_DEGREES = 14.0;
    /** How often the "<name> is ready ✓" line repeats while not looking at a block. */
    private static final long READY_NOTICE_TICKS = 10L;

    /**
     * Fallback palette, identical to the {@code countdown.*} lang values: brand-cyan/slate
     * panels, success green for Ready, danger red for Leave, amber for the opponent's name.
     */
    private static final TextColor READY = TextColor.color(0x4ADE80);
    private static final TextColor LEAVE = TextColor.color(0xF87171);
    private static final TextColor MUTED = TextColor.color(0x94A3B8);
    private static final TextColor SEPARATOR = TextColor.color(0x334155);
    private static final TextColor NAME = TextColor.color(0xFBBF24);

    private enum Kind {
        READY, LEAVE
    }

    private static final class Marker {
        final BlockDisplay display;
        final UUID owner;
        final Kind kind;
        float angle;

        Marker(BlockDisplay display, UUID owner, Kind kind) {
            this.display = display;
            this.owner = owner;
            this.kind = kind;
        }
    }

    private static final class MarkerSet {
        final List<Marker> markers = new ArrayList<>();
        final Set<UUID> ready = ConcurrentHashMap.newKeySet();
        long tick;
    }

    private final Plugin plugin;
    private final Map<UUID, MarkerSet> sets = new ConcurrentHashMap<>();
    /** Called with the match id once BOTH fighters are ready (bootstrap wires the skip). */
    private volatile Consumer<UUID> bothReadyHandler;
    /** Called with the player who clicked the redstone block (bootstrap wires /leave). */
    private volatile Consumer<Player> leaveHandler;
    /** Localised action bar lines; null falls back to the built-in palette. */
    private volatile MessageService messageService;
    /** Plays the Ready click sound; null makes the click silent (bootstrap wires it). */
    private volatile SoundService soundService;

    public CountdownMarkers(Plugin plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void setBothReadyHandler(Consumer<UUID> handler) {
        this.bothReadyHandler = handler;
    }

    public void setSoundService(SoundService soundService) {
        this.soundService = soundService;
    }

    public void setLeaveHandler(Consumer<Player> handler) {
        this.leaveHandler = handler;
    }

    public void setMessageService(MessageService messages) {
        this.messageService = messages;
    }

    /**
     * Spawns one Ready/Leave pair per participant. The pair is derived from
     * {@code spawnAnchors} — the arena spawn each fighter is being teleported onto, with the
     * yaw they will face — so the blocks belong to the arena's start layout instead of to
     * wherever the player happened to be standing when the countdown began. A participant
     * without an anchor falls back to their live position.
     */
    public void spawn(MatchSession session, java.util.Map<UUID, Location> spawnAnchors) {
        if (session == null) {
            return;
        }
        remove(session.id());
        MarkerSet set = new MarkerSet();
        for (UUID id : session.participants()) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) {
                continue;
            }
            Location at = spawnAnchors == null ? null : spawnAnchors.get(id);
            if (at == null || at.getWorld() == null) {
                at = player.getLocation();
            }
            World world = at.getWorld() == null ? player.getWorld() : at.getWorld();
            CountdownAnchor.Pair pair = CountdownAnchor.pair(
                    at.getX(), at.getY(), at.getZ(), at.getYaw());
            set.markers.add(spawnBlock(world,
                    new Location(world, pair.readyX(), pair.readyY(), pair.readyZ()),
                    Material.EMERALD_BLOCK, id, Kind.READY));
            set.markers.add(spawnBlock(world,
                    new Location(world, pair.leaveX(), pair.leaveY(), pair.leaveZ()),
                    Material.REDSTONE_BLOCK, id, Kind.LEAVE));
        }
        if (!set.markers.isEmpty()) {
            sets.put(session.id(), set);
        }
    }

    /** Spawns the pair from the players' current positions (no known spawn anchor). */
    public void spawn(MatchSession session) {
        spawn(session, java.util.Map.of());
    }

    /** Removes a match's markers. Safe to call when there are none. */
    public void remove(UUID matchId) {
        MarkerSet set = sets.remove(matchId);
        if (set == null) {
            return;
        }
        for (Marker marker : set.markers) {
            try {
                marker.display.remove();
            } catch (RuntimeException ignored) {
                // A marker in an unloaded chunk is already gone.
            }
        }
    }

    /** Removes every marker (shutdown / reload). */
    public void removeAll() {
        for (UUID matchId : new ArrayList<>(sets.keySet())) {
            remove(matchId);
        }
    }

    /** この試合で指定の人が Ready を押したか(HUD の ✓/☓ 用)。 */
    public boolean isReady(UUID matchId, UUID player) {
        MarkerSet set = sets.get(matchId);
        return set != null && player != null && set.ready.contains(player);
    }

    /** True when both fighters of this match have pressed Ready. */
    public boolean allReady(MatchSession session) {
        if (session == null) {
            return false;
        }
        MarkerSet set = sets.get(session.id());
        if (set == null || session.participants().size() < 2) {
            return false;
        }
        for (UUID id : session.participants()) {
            if (!set.ready.contains(id)) {
                return false;
            }
        }
        return true;
    }

    private Marker spawnBlock(World world, Location at, Material material, UUID owner, Kind kind) {
        BlockDisplay display = world.spawn(at, BlockDisplay.class, entity -> {
            entity.setBlock(material.createBlockData());
            entity.setBillboard(Display.Billboard.FIXED);
            entity.setGravity(false);
            entity.setInvulnerable(true);
            entity.setSilent(true);
            entity.setPersistent(false);
            entity.setViewRange(0.6f);
            entity.setBrightness(new Display.Brightness(15, 15));
            entity.setInterpolationDelay(0);
            entity.setInterpolationDuration(2);
            entity.setTransformation(transformation(0f));
        });
        return new Marker(display, owner, kind);
    }

    /**
     * Centred, spinning transform. The spin goes into {@code leftRotation} and the centring
     * offset is that same rotation applied to the half-size, so the block turns around its
     * own middle instead of swinging around a corner.
     */
    private static Transformation transformation(float angleDegrees) {
        Quaternionf spin = new Quaternionf().rotationY((float) Math.toRadians(angleDegrees));
        Vector3f half = new Vector3f(BLOCK_SCALE * 0.5f, BLOCK_SCALE * 0.5f, BLOCK_SCALE * 0.5f);
        Vector3f offset = spin.transform(new Vector3f(half)).mul(-1f);
        return new Transformation(offset, spin,
                new Vector3f(BLOCK_SCALE, BLOCK_SCALE, BLOCK_SCALE), new Quaternionf());
    }

    /** Every tick: spin the blocks, glow the one being looked at, drive the action bar. */
    private void tick() {
        for (Map.Entry<UUID, MarkerSet> entry : sets.entrySet()) {
            MarkerSet set = entry.getValue();
            set.tick++;
            boolean showReadyNotice = set.tick % READY_NOTICE_TICKS == 0;
            for (Marker marker : set.markers) {
                marker.angle = (marker.angle + SPIN_DEGREES_PER_TICK) % 360f;
                try {
                    marker.display.setTransformation(transformation(marker.angle));
                } catch (RuntimeException ignored) {
                    // Marker already removed (chunk unload / match cleanup race).
                }
            }
            for (UUID id : readyOwners(set)) {
                Player player = Bukkit.getPlayer(id);
                if (player != null) {
                    updateViewer(player, set, showReadyNotice);
                }
            }
        }
    }

    private static List<UUID> readyOwners(MarkerSet set) {
        List<UUID> owners = new ArrayList<>();
        for (Marker marker : set.markers) {
            if (!owners.contains(marker.owner)) {
                owners.add(marker.owner);
            }
        }
        return owners;
    }

    private void updateViewer(Player player, MarkerSet set, boolean showReadyNotice) {
        Marker gazed = gazedMarker(player, set);
        for (Marker marker : set.markers) {
            if (marker.owner.equals(player.getUniqueId())) {
                boolean lit = gazed == marker;
                if (marker.display.isGlowing() != lit) {
                    marker.display.setGlowing(lit);
                }
                if (lit) {
                    marker.display.setGlowColorOverride(marker.kind == Kind.READY
                            ? Color.LIME : Color.RED);
                }
            }
        }
        if (gazed != null) {
            UUID opponent = opponentOf(player.getUniqueId(), set);
            int readyCount = opponent != null && set.ready.contains(opponent) ? 1 : 0;
            player.sendActionBar(gazeLine(player, gazed, readyCount));
            return;
        }
        if (showReadyNotice) {
            UUID opponent = opponentOf(player.getUniqueId(), set);
            if (opponent != null && set.ready.contains(opponent) && !set.ready.contains(player.getUniqueId())) {
                Player other = Bukkit.getPlayer(opponent);
                if (other != null) {
                    player.sendActionBar(opponentReadyLine(player, other));
                }
            }
        }
    }

    /**
     * The line shown while looking at a block: the opponent's readiness, then the action of
     * the block being looked at. The leading number turns green once it is 1.
     */
    private Component gazeLine(Player player, Marker marker, int readyCount) {
        String key = marker.kind == Kind.READY ? "countdown.gaze-ready" : "countdown.gaze-leave";
        String number = (readyCount > 0 ? "<color:#4ADE80>" : "<color:#94A3B8>") + readyCount + "</color>";
        // 数値は「色付きマークアップ」を値にしているので unparsed だとそのまま表示される。
        Component rendered = render(player, key, MessageService.tagsParsed("n", number));
        if (rendered != null) {
            return rendered;
        }
        TextColor accent = marker.kind == Kind.READY ? READY : LEAVE;
        return Component.text(String.valueOf(readyCount), readyCount > 0 ? READY : MUTED)
                .append(Component.text("/2 ", MUTED))
                .append(Component.text("· ", SEPARATOR))
                .append(Component.text(marker.kind == Kind.READY ? "Ready ✓" : "Leave ☓", accent)
                        .decorate(TextDecoration.BOLD));
    }

    /** The line shown when the opponent pressed Ready and this player is not looking anywhere. */
    private Component opponentReadyLine(Player player, Player other) {
        MessageService messages = messageService;
        if (messages != null) {
            try {
                String template = messages.raw(player, "countdown.opponent-ready");
                if (template != null && template.contains("<name>")) {
                    // プレースホルダ解決を待たずに名前を差し込む。プレイヤー名は
                    // [A-Za-z0-9_] のみだが、念のため山括弧は落としてから入れる。
                    String safe = other.getName().replace("<", "").replace(">", "");
                    return net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                            .deserialize(template.replace("<name>", safe));
                }
            } catch (RuntimeException ignored) {
                // キー欠落やパース失敗は下のフォールバックが拾う
            }
        }
        Component rendered = render(player, "countdown.opponent-ready",
                MessageService.tags("name", other.getName()));
        if (rendered != null) {
            return rendered;
        }
        return Component.text(other.getName(), NAME)
                .append(Component.text(" is ", MUTED))
                .append(Component.text("Ready ✓", READY).decorate(TextDecoration.BOLD));
    }

    private Component render(Player player, String key, TagResolver... resolvers) {
        MessageService messages = messageService;
        if (messages == null) {
            return null;
        }
        try {
            return messages.render(player, key, resolvers);
        } catch (RuntimeException ignored) {
            return null; // missing key: the fallback line still shows
        }
    }

    /** The marker of this viewer they are currently looking at, or null. */
    private Marker gazedMarker(Player player, MarkerSet set) {
        Location eye = player.getEyeLocation();
        Vector look = eye.getDirection().normalize();
        double cosLimit = Math.cos(Math.toRadians(GAZE_ANGLE_DEGREES));
        Marker best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Marker marker : set.markers) {
            if (!marker.owner.equals(player.getUniqueId())) {
                continue;
            }
            Location at = marker.display.getLocation();
            if (!at.getWorld().equals(eye.getWorld())) {
                continue;
            }
            Vector to = at.toVector().subtract(eye.toVector());
            double distance = to.length();
            if (distance > GAZE_RANGE || distance < 0.001) {
                continue;
            }
            if (to.normalize().dot(look) < cosLimit) {
                continue;
            }
            if (distance < bestDistance) {
                best = marker;
                bestDistance = distance;
            }
        }
        return best;
    }

    private UUID opponentOf(UUID playerId, MarkerSet set) {
        for (Marker marker : set.markers) {
            if (!marker.owner.equals(playerId)) {
                return marker.owner;
            }
        }
        return null;
    }

    /** Right-click the emerald block: Ready. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof BlockDisplay display)) {
            return;
        }
        Marker marker = markerOf(display);
        if (marker == null || marker.kind != Kind.READY
                || !marker.owner.equals(event.getPlayer().getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        markReady(marker, event.getPlayer());
    }

    /** Left click while looking at the redstone block: Leave. */
    @EventHandler(priority = EventPriority.LOW)
    public void onLeftClick(PlayerInteractEvent event) {
        Action action = event.getAction();
        boolean left = action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;
        boolean right = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        if (!left && !right) {
            return;
        }
        Player player = event.getPlayer();
        for (MarkerSet set : sets.values()) {
            Marker gazed = gazedMarker(player, set);
            if (gazed == null || !gazed.owner.equals(player.getUniqueId())) {
                continue;
            }
            if (gazed.kind == Kind.READY) {
                // 向いてるまま左右どちらでも Ready。
                event.setCancelled(true);
                markReady(gazed, player);
                return;
            }
            if (gazed.kind == Kind.LEAVE && left) {
                event.setCancelled(true);
                leave(player);
                return;
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Nothing per-player is held; the whole set goes with the match. Kept explicit so a
        // future per-player marker cannot leak.
        Iterator<MarkerSet> it = sets.values().iterator();
        while (it.hasNext()) {
            MarkerSet set = it.next();
            set.ready.remove(event.getPlayer().getUniqueId());
        }
    }

    private void markReady(Marker marker, Player player) {
        MarkerSet set = sets.entrySet().stream()
                .filter(e -> e.getValue().markers.contains(marker))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
        if (set == null) {
            return;
        }
        boolean alreadyReady = set.ready.contains(player.getUniqueId());
        set.ready.add(player.getUniqueId());
        UUID matchId = matchIdOf(set);
        if (matchId == null) {
            return;
        }
        if (allReady(sessionOf(matchId))) {
            // Second Ready: this press starts the fight, so it skips the Ready click sound —
            // beginFight() plays the match-start sting for everyone instead.
            Consumer<UUID> handler = bothReadyHandler;
            if (handler != null) {
                remove(matchId);
                handler.accept(matchId);
            }
            return;
        }
        // First Ready (opponent still waiting): the anvil "land" thud confirms the press.
        // Re-clicking the block while already Ready stays silent.
        if (!alreadyReady) {
            SoundService sounds = soundService;
            if (sounds != null) {
                sounds.play(player, "ready");
            }
        }
    }

    private void leave(Player player) {
        Consumer<Player> handler = leaveHandler;
        if (handler != null) {
            handler.accept(player);
        }
    }

    private UUID matchIdOf(MarkerSet set) {
        for (Map.Entry<UUID, MarkerSet> entry : sets.entrySet()) {
            if (entry.getValue() == set) {
                return entry.getKey();
            }
        }
        return null;
    }

    private MatchSession sessionOf(UUID matchId) {
        return sessionLookup == null ? null : sessionLookup.apply(matchId);
    }

    /** Resolves a match id back to its session (bootstrap wires the registry). */
    private volatile java.util.function.Function<UUID, MatchSession> sessionLookup;

    public void setSessionLookup(java.util.function.Function<UUID, MatchSession> lookup) {
        this.sessionLookup = lookup;
    }

    private Marker markerOf(BlockDisplay display) {
        for (MarkerSet set : sets.values()) {
            for (Marker marker : set.markers) {
                if (marker.display.getUniqueId().equals(display.getUniqueId())) {
                    return marker;
                }
            }
        }
        return null;
    }
}
