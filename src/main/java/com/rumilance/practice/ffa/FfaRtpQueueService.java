package com.rumilance.practice.ffa;

import com.rumilance.practice.locale.MessageService;
import com.rumilance.practice.util.LocationUtil;
import com.rumilance.practice.util.SafeTeleport;
import com.rumilance.practice.util.SpawnFooting;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Random-teleport duel queue inside an FFA arena ({@code settings.rtpqueue}).
 *
 * <p>Players in the arena run {@code /rtpqueue} to join/leave. As soon as two players are
 * queued, both are teleported to the SAME randomly chosen, everyone-else-avoided standing
 * spot inside the arena (the same candidate source FFA spawning uses), placed a few blocks
 * apart on real ground, facing each other — combat starts immediately in normal FFA mode.</p>
 */
public final class FfaRtpQueueService {

    /** Horizontal separation between the two matched players, in blocks. */
    private static final int[][] PAIR_OFFSETS = {
            {4, 0}, {-4, 0}, {0, 4}, {0, -4}, {3, 3}, {-3, -3}, {3, -3}, {-3, 3}
    };

    public enum JoinResult {
        OK, NOT_IN_FFA, ARENA_DISABLED, ALREADY_QUEUED
    }

    private final FfaService ffaService;
    private final FfaSpawnIndex spawnIndex;
    private final MessageService messages;
    /** arenaId -> waiting player ids (order = join order). */
    private final Map<String, List<UUID>> queues = new HashMap<>();

    /**
     * @param spawnIndex may be {@code null}; matching then falls back to the live locator
     */
    public FfaRtpQueueService(FfaService ffaService, FfaSpawnIndex spawnIndex, MessageService messages) {
        this.ffaService = ffaService;
        this.spawnIndex = spawnIndex;
        this.messages = messages;
    }

    /** {@code /rtpqueue} toggle: joins when not queued, leaves when queued. */
    public void toggle(Player player) {
        if (isQueued(player.getUniqueId())) {
            leave(player);
            return;
        }
        join(player);
    }

    public boolean join(Player player) {
        UUID id = player.getUniqueId();
        String arenaId = arenaIdOf(id);
        if (arenaId == null) {
            messages.send(player, "rtpqueue.not-in-ffa");
            return false;
        }
        FfaService.FfaArena arena = ffaService.find(arenaId).orElse(null);
        if (arena == null || !arena.enabled() || !arena.rtpQueueEnabled()) {
            messages.send(player, "rtpqueue.disabled");
            return false;
        }
        List<UUID> queue = queues.computeIfAbsent(arenaId, a -> new ArrayList<>());
        if (queue.contains(id)) {
            messages.send(player, "rtpqueue.already");
            return false;
        }
        queue.add(id);
        messages.send(player, "rtpqueue.joined",
                Placeholder.unparsed("count", String.valueOf(queue.size())));
        if (queue.size() >= 2) {
            tryMatch(arena, queue);
        }
        return true;
    }

    public boolean leave(Player player) {
        UUID id = player.getUniqueId();
        boolean removed = false;
        for (List<UUID> queue : queues.values()) {
            removed |= queue.remove(id);
        }
        if (removed) {
            messages.send(player, "rtpqueue.left");
        } else {
            messages.send(player, "rtpqueue.not-queued");
        }
        return removed;
    }

    public boolean isQueued(UUID id) {
        for (List<UUID> queue : queues.values()) {
            if (queue.contains(id)) {
                return true;
            }
        }
        return false;
    }

    public int queueSize(String arenaId) {
        List<UUID> queue = queues.get(arenaId);
        return queue == null ? 0 : queue.size();
    }

    /** Removes the player from every queue (quit, left the arena, forced out). */
    public void cancelAll(UUID id) {
        if (id == null) {
            return;
        }
        java.util.Iterator<Map.Entry<String, List<UUID>>> it = queues.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, List<UUID>> entry = it.next();
            entry.getValue().remove(id);
            if (entry.getValue().isEmpty()) {
                it.remove();
            }
        }
    }

    private void tryMatch(FfaService.FfaArena arena, List<UUID> queue) {
        UUID first = queue.remove(0);
        UUID second = queue.remove(0);
        Player a = Bukkit.getPlayer(first);
        Player b = Bukkit.getPlayer(second);
        if (a == null || b == null
                || !arena.id().equals(arenaIdOf(first)) || !arena.id().equals(arenaIdOf(second))) {
            // A stale entrant: requeue the still-valid one.
            if (a != null && arena.id().equals(arenaIdOf(first))) {
                queue.add(0, first);
            }
            if (b != null && arena.id().equals(arenaIdOf(second))) {
                queue.add(0, second);
            }
            return;
        }
        teleportPair(arena, a, b);
        messages.send(a, "rtpqueue.matched", Placeholder.unparsed("player", b.getName()));
        messages.send(b, "rtpqueue.matched", Placeholder.unparsed("player", a.getName()));
    }

    /**
     * Moves both players to one random spot far from other occupants, grounded, a few blocks
     * apart, facing each other. Reuses the same safe-spawn pipeline as normal FFA spawning.
     */
    void teleportPair(FfaService.FfaArena arena, Player a, Player b) {
        List<Location> occupied = new ArrayList<>();
        for (UUID id : ffaService.occupantIds()) {
            if (id.equals(a.getUniqueId()) || id.equals(b.getUniqueId())) {
                continue;
            }
            if (!arena.id().equals(arenaIdOf(id))) {
                continue;
            }
            Player other = Bukkit.getPlayer(id);
            if (other != null) {
                occupied.add(other.getLocation());
            }
        }
        Location anchor = spawnIndex != null ? spawnIndex.pick(arena, occupied) : null;
        if (anchor == null || anchor.getWorld() == null) {
            anchor = FfaSpawnLocator.find(arena, occupied);
        }
        if (anchor == null || anchor.getWorld() == null) {
            Location configured = arena.spawn();
            if (configured != null) {
                anchor = SpawnFooting.standClear(configured);
                if (anchor == null) {
                    int minY = arena.region() != null && arena.region().world() != null
                            ? Math.max(arena.region().world().getMinHeight(), arena.region().minY())
                            : configured.getWorld() != null ? configured.getWorld().getMinHeight() : 0;
                    anchor = SpawnFooting.standClearDeep(configured, minY);
                }
            }
        }
        if (anchor == null || anchor.getWorld() == null) {
            return; // No safe spot in the whole arena: keep both where they are.
        }
        if (arena.region() != null) {
            // Same border-edge floating fix: clamped X/Z must regain a standable Y.
            Location standable = SpawnFooting.standableWithin(anchor, arena.region());
            if (standable != null) {
                anchor = standable;
            } else {
                anchor = LocationUtil.safeTeleportLocation(anchor, arena.region());
            }
        }
        Location placeA = anchor;
        Location placeB = null;
        for (int[] offset : PAIR_OFFSETS) {
            Location probe = anchor.clone().add(offset[0], 0, offset[1]);
            if (arena.region() != null && !arena.region().containsHorizontal(probe.getBlockX(), probe.getBlockZ())) {
                continue;
            }
            Location found = SpawnFooting.standClear(probe);
            if (found == null && arena.region() != null) {
                int minY = Math.max(arena.region().world().getMinHeight(), arena.region().minY());
                found = SpawnFooting.standClearDeep(
                        LocationUtil.safeTeleportLocation(probe, arena.region()), minY);
            }
            if (found != null && (arena.region() == null
                    || arena.region().containsHorizontal(found.getBlockX(), found.getBlockZ()))) {
                placeB = found;
                break;
            }
        }
        if (placeB == null) {
            placeB = anchor;
        }
        face(placeA, placeB);
        face(placeB, placeA);
        SafeTeleport.teleport(a, placeA);
        SafeTeleport.teleport(b, placeB);
    }

    /** Sets the position's yaw/pitch so a player standing there faces {@code toward}. */
    private static void face(Location at, Location toward) {
        Vector dir = toward.toVector().subtract(at.toVector());
        if (dir.lengthSquared() < 1.0e-6d) {
            return;
        }
        dir.setY(0).normalize();
        double yaw = Math.toDegrees(Math.atan2(-dir.getX(), dir.getZ()));
        at.setYaw((float) yaw);
        at.setPitch(0f);
    }

    private String arenaIdOf(UUID id) {
        return ffaService.arenaOf(id).map(a -> a.toLowerCase(Locale.ROOT)).orElse(null);
    }
}
