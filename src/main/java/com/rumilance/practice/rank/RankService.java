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
    /** Temporary ranks: uuid -> epoch millis at which the rank expires and reverts to NORM. */
    private final Map<UUID, Long> temporaryUntil = new ConcurrentHashMap<>();
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
        return cache.getOrDefault(uuid, PlayerRank.NORM);
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

    public boolean isAdmin(Player player) {
        return com.rumilance.practice.guard.PracticeGuards.effectiveAdmin(
                get(player),
                player.hasPermission("rumilance.admin"));
    }

    public void load(UUID uuid) {
        asyncExecutor.supplyAsync(() -> {
            try {
                return repository.find(uuid).orElse(PlayerRank.NORM);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load rank for " + uuid, e);
                return PlayerRank.NORM;
            }
        }).thenAccept(rank -> Bukkit.getScheduler().runTask(plugin, () -> {
            cache.put(uuid, rank);
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

    public void setRank(UUID uuid, PlayerRank rank) {
        setRank(uuid, rank, null);
    }

    /**
     * Sets a rank, optionally only for {@code duration} (a temporary donor rank granted via
     * {@code /rank <player> <rank> <duration>}). Temporary ranks are NOT persisted as the
     * permanent rank; when they expire the player reverts to {@link PlayerRank#NORM}.
     */
    public void setRank(UUID uuid, PlayerRank rank, java.time.Duration duration) {
        Objects.requireNonNull(rank, "rank");
        PlayerRank previous = cache.get(uuid);
        boolean downgraded = lostPremiumAccess(previous, rank);
        cache.put(uuid, rank);
        if (duration != null && !duration.isZero() && !duration.isNegative()) {
            temporaryUntil.put(uuid, System.currentTimeMillis() + duration.toMillis());
            startExpiryScheduler();
            // Don't overwrite the stored permanent rank with a time-limited grant.
        } else {
            temporaryUntil.remove(uuid);
            asyncExecutor.runAsync(() -> {
                try {
                    repository.upsert(uuid, rank);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Failed to save rank for " + uuid, e);
                }
            });
        }
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            applyNametag(online);
            if (downgraded) {
                Bukkit.getScheduler().runTask(plugin, () -> fireRankChange(online));
            }
        }
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
                if (e.getValue() <= now) {
                    temporaryUntil.remove(e.getKey());
                    Player online = Bukkit.getPlayer(e.getKey());
                    PlayerRank before = cache.get(e.getKey());
                    cache.put(e.getKey(), PlayerRank.NORM);
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

    /**
     * Styled display name: plain white for NORM, aqua→blue gradient for ranks. Rank badges
     * come from the resource-pack icon font prefix (see IconFontService / RankIconNameTags);
     * players without the pack see the plain-text badges (N / N+ / OWNER) instead.
     */
    static Component styledName(String name, PlayerRank rank) {
        Objects.requireNonNull(name, "name");
        return switch (rank == null ? PlayerRank.NORM : rank) {
            case NORM -> Component.text(name, NamedTextColor.WHITE);
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
