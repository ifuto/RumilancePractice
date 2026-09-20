package com.rumilance.practice.rank;

import com.rumilance.practice.util.AsyncExecutor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Donor / staff ranks with lobby nametag styling.
 */
public final class RankService {

    private static final TextColor AQUA = NamedTextColor.AQUA;
    private static final TextColor BLUE = NamedTextColor.BLUE;

    private final Plugin plugin;
    private final RankRepository repository;
    private final AsyncExecutor asyncExecutor;
    private final Map<UUID, PlayerRank> cache = new ConcurrentHashMap<>();
    /** Last confirmed/local rank, retained across a reconnect so a transient DB outage is not a downgrade. */
    private final Map<UUID, PlayerRank> lastKnownRanks = new ConcurrentHashMap<>();
    /** Temporary ranks: uuid -> epoch millis at which the rank expires and reverts to NORM. */
    private final Map<UUID, Long> temporaryUntil = new ConcurrentHashMap<>();
    /**
     * Monotonic session/state versions. A load started for an old join must never overwrite a
     * rank set after it started, nor repopulate the cache after the player has quit.
     */
    private final Map<UUID, Long> stateVersions = new ConcurrentHashMap<>();
    /** Monotonic versions for permanent database writes, independent of join/load versions. */
    private final Map<UUID, Long> mutationVersions = new ConcurrentHashMap<>();
    /** Last permanent mutation version confirmed in the database. */
    private final Map<UUID, Long> persistedMutationVersions = new ConcurrentHashMap<>();
    /** Serialises writes for one UUID while still keeping database I/O off the main thread. */
    private final Map<UUID, Object> persistenceLocks = new ConcurrentHashMap<>();
    private boolean expirySchedulerStarted;
    /** Fired on the main thread whenever an online player's effective rank changes. */
    private volatile java.util.function.Consumer<Player> rankChangeListener;

    public RankService(Plugin plugin, RankRepository repository, AsyncExecutor asyncExecutor) {
        this.plugin = plugin;
        this.repository = repository;
        this.asyncExecutor = asyncExecutor;
    }

    /** Hook for cosmetics (e.g. armor-trim reset) when a player's rank changes at runtime. */
    public void setRankChangeListener(java.util.function.Consumer<Player> listener) {
        this.rankChangeListener = listener;
    }

    /** True when premium trims are no longer permitted (rank fell below VIP+). */
    private static boolean lostPremiumAccess(PlayerRank from, PlayerRank to) {
        boolean wasPremium = from != null && from.isVipPlusOrAbove();
        boolean isPremium = to != null && to.isVipPlusOrAbove();
        return wasPremium && !isPremium;
    }

    public PlayerRank get(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        return cache.getOrDefault(uuid,
                lastKnownRanks.getOrDefault(uuid, PlayerRank.NORM));
    }

    public PlayerRank get(Player player) {
        return get(player.getUniqueId());
    }

    public boolean isVipOrAbove(Player player) {
        return com.rumilance.practice.guard.PracticeGuards.effectiveVipOrAbove(
                get(player),
                player.hasPermission("rumilance.user.vip"),
                player.hasPermission("rumilance.user.vip_plus"),
                player.hasPermission("rumilance.admin"));
    }

    public boolean isVipPlusOrAbove(Player player) {
        return com.rumilance.practice.guard.PracticeGuards.effectiveVipPlusOrAbove(
                get(player),
                player.hasPermission("rumilance.user.vip_plus"),
                player.hasPermission("rumilance.admin"));
    }

    /**
     * Whether the player has the stored ADMIN rank. Permission nodes still grant admin
     * capabilities to command/feature guards, but they are deliberately not a rank badge:
     * servers often make every test operator an OP and that must not turn every name into
     * OWNER.
     */
    public boolean isAdmin(Player player) {
        return get(player) == PlayerRank.ADMIN;
    }

    public void load(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        long loadVersion = advance(stateVersions, uuid);
        long mutationVersionAtLoad = mutationVersions.getOrDefault(uuid, 0L);
        asyncExecutor.supplyAsync(() -> {
            try {
                // An absent row is a real NORM value. A database exception is not: returning
                // NORM for an I/O failure used to erase a perfectly good cached rank.
                return repository.find(uuid).orElse(PlayerRank.NORM);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load rank for " + uuid
                        + "; keeping the current cached rank", e);
                return null;
            }
        }).thenAccept(loadedRank -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!isCurrent(stateVersions, uuid, loadVersion)) {
                return;
            }
            PlayerRank rank = loadedRank;
            if (rank == null) {
                rank = cache.getOrDefault(uuid, lastKnownRanks.get(uuid));
                if (rank == null) {
                    return;
                }
            }
            long currentMutationVersion = mutationVersions.getOrDefault(uuid, 0L);
            long persistedMutationVersion = persistedMutationVersions.getOrDefault(uuid, 0L);
            if (mutationVersionAtLoad != currentMutationVersion
                    || persistedMutationVersion < currentMutationVersion) {
                // A /rank write is still newer than the row this SELECT could see. Keep the
                // in-memory command result instead of replacing it with an old DB value.
                PlayerRank local = cache.getOrDefault(uuid, lastKnownRanks.get(uuid));
                if (local == null) {
                    return;
                }
                rank = local;
            }
            Long expiresAt = temporaryUntil.get(uuid);
            if (expiresAt != null) {
                if (expiresAt > System.currentTimeMillis()) {
                    // A temporary grant is authoritative until it expires, including across
                    // reconnects. Never let the permanent DB row replace it during a join.
                    rank = cache.getOrDefault(uuid,
                            lastKnownRanks.getOrDefault(uuid, rank));
                } else {
                    temporaryUntil.remove(uuid, expiresAt);
                }
            }
            cache.put(uuid, rank);
            lastKnownRanks.put(uuid, rank);
            Player online = Bukkit.getPlayer(uuid);
            if (online != null) {
                applyNametag(online);
                // Strip premium trims whenever the loaded rank is below VIP+ (covers expiry
                // while offline, where the cache had no previous rank to compare).
                if (!rank.isVipPlusOrAbove()) {
                    fireRankChange(online);
                }
            }
        }));
    }

    private static long advance(Map<UUID, Long> versions, UUID uuid) {
        return versions.merge(uuid, 1L, Long::sum);
    }

    private static boolean isCurrent(Map<UUID, Long> versions, UUID uuid, long expected) {
        return versions.getOrDefault(uuid, 0L) == expected;
    }

    public void setRank(UUID uuid, PlayerRank rank) {
        setRank(uuid, rank, null);
    }

    /**
     * Sets a rank, optionally only for {@code duration} (a temporary donor rank granted via
     * {@code /rank <player> <rank> <duration>}). Temporary ranks are NOT persisted as the
     * permanent rank; when they expire the player reverts to {@link PlayerRank#NORM}.
     */
    public void setRank(UUID uuid, PlayerRank rank, java.time.Duration duration) {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(rank, "rank");
        advance(stateVersions, uuid);
        PlayerRank previous = get(uuid);
        boolean downgraded = lostPremiumAccess(previous, rank);
        cache.put(uuid, rank);
        lastKnownRanks.put(uuid, rank);
        if (duration != null && !duration.isZero() && !duration.isNegative()) {
            temporaryUntil.put(uuid, System.currentTimeMillis() + duration.toMillis());
            startExpiryScheduler();
            // Don't overwrite the stored permanent rank with a time-limited grant.
        } else {
            temporaryUntil.remove(uuid);
            long mutationVersion = advance(mutationVersions, uuid);
            persistPermanentRank(uuid, rank, mutationVersion);
        }
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            applyNametag(online);
            if (downgraded) {
                Bukkit.getScheduler().runTask(plugin, () -> fireRankChange(online));
            }
        }
    }

    private void persistPermanentRank(UUID uuid, PlayerRank rank, long mutationVersion) {
        asyncExecutor.runAsync(() -> {
            Object lock = persistenceLocks.computeIfAbsent(uuid, ignored -> new Object());
            synchronized (lock) {
                // If another /rank command superseded this write while it was queued, let the
                // newest queued write win instead of allowing the worker pool to reorder rows.
                if (!isCurrent(mutationVersions, uuid, mutationVersion)) {
                    return;
                }
                try {
                    repository.upsert(uuid, rank);
                    if (isCurrent(mutationVersions, uuid, mutationVersion)) {
                        persistedMutationVersions.put(uuid, mutationVersion);
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            // A player may have reconnected while the write was in flight. Once
                            // the row is confirmed, make the saved rank visible without waiting
                            // for another join/load cycle (but never replace an active temporary
                            // grant with its permanent backing rank).
                            if (!isCurrent(mutationVersions, uuid, mutationVersion)
                                    || temporaryUntil.containsKey(uuid)) {
                                return;
                            }
                            cache.put(uuid, rank);
                            Player online = Bukkit.getPlayer(uuid);
                            if (online != null) {
                                applyNametag(online);
                            }
                        });
                    }
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Failed to save rank for " + uuid, e);
                }
            }
        });
    }

    private void fireRankChange(Player player) {
        java.util.function.Consumer<Player> listener = rankChangeListener;
        if (listener != null) {
            try {
                listener.accept(player);
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "Rank change listener failed", e);
            }
        }
    }

    /** Reverts any expired temporary ranks once per second. */
    private synchronized void startExpiryScheduler() {
        if (expirySchedulerStarted) {
            return;
        }
        expirySchedulerStarted = true;
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (temporaryUntil.isEmpty()) {
                return;
            }
            long now = System.currentTimeMillis();
            for (Map.Entry<UUID, Long> e : temporaryUntil.entrySet()) {
                if (e.getValue() <= now && temporaryUntil.remove(e.getKey(), e.getValue())) {
                    UUID uuid = e.getKey();
                    advance(stateVersions, uuid);
                    Player online = Bukkit.getPlayer(uuid);
                    PlayerRank before = cache.get(uuid);
                    cache.put(uuid, PlayerRank.NORM);
                    lastKnownRanks.put(uuid, PlayerRank.NORM);
                    if (online != null) {
                        applyNametag(online);
                        if (lostPremiumAccess(before, PlayerRank.NORM)) {
                            fireRankChange(online);
                        }
                        online.sendMessage(Component.text(
                                "Your temporary rank has expired.", NamedTextColor.YELLOW));
                    }
                }
            }
        }, 20L, 20L);
    }

    public void unload(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        // Invalidate an in-flight join load before clearing the cache. The temporary grant is
        // intentionally retained so a reconnect before expiry does not lose it.
        advance(stateVersions, uuid);
        cache.remove(uuid);
    }

    public void applyNametag(Player player) {
        if (player == null) {
            return;
        }
        Component display = styledName(player.getName(), get(player));
        player.displayName(display);
        player.playerListName(display);
        player.customName(display);
        player.setCustomNameVisible(false);
    }

    /** Just the rank-styled name component (no side effects) — used by the TAB fight layout. */
    public Component styledComponentName(Player player) {
        if (player == null) {
            return Component.empty();
        }
        return styledName(player.getName(), get(player));
    }

    /**
     * Styled display name: plain white for NORM, aqua→blue gradient for ranks. Rank badges
     * come from the resource-pack icon font prefix (see IconFontService / RankIconNameTags);
     * players without the pack see the plain-text badges (N / N+ / OWNER) instead.
     */
    static Component styledName(String name, PlayerRank rank) {
        Objects.requireNonNull(name, "name");
        return switch (rank == null ? PlayerRank.NORM : rank) {
            case NORM -> Component.text(name, NamedTextColor.WHITE);
            case PRO -> Component.text(name, AQUA);
            case VIP -> gradientName(name, false);
            case VIP_PLUS, ADMIN -> gradientName(name, true);
        };
    }

    private static Component gradientName(String name, boolean bold) {
        if (name.isEmpty()) {
            return Component.empty();
        }
        int len = name.length();
        var builder = Component.text();
        for (int i = 0; i < len; i++) {
            float t = len == 1 ? 0f : (float) i / (len - 1);
            TextColor color = TextColor.lerp(t, AQUA, BLUE);
            var letter = Component.text(String.valueOf(name.charAt(i)), color);
            if (bold) {
                letter = letter.decorate(TextDecoration.BOLD);
            }
            builder.append(letter);
        }
        return builder.build();
    }
}
