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

    public RankService(Plugin plugin, RankRepository repository, AsyncExecutor asyncExecutor) {
        this.plugin = plugin;
        this.repository = repository;
        this.asyncExecutor = asyncExecutor;
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
            }
        }));
    }

    public void setRank(UUID uuid, PlayerRank rank) {
        Objects.requireNonNull(rank, "rank");
        cache.put(uuid, rank);
        asyncExecutor.runAsync(() -> {
            try {
                repository.upsert(uuid, rank);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to save rank for " + uuid, e);
            }
        });
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            applyNametag(online);
        }
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

    static Component styledName(String name, PlayerRank rank) {
        Objects.requireNonNull(name, "name");
        return switch (rank == null ? PlayerRank.NORM : rank) {
            case NORM -> Component.text(name, NamedTextColor.WHITE);
            case VIP -> Component.text()
                    .append(Component.text("N ", AQUA, TextDecoration.BOLD))
                    .append(gradientName(name, false))
                    .build();
            case VIP_PLUS -> Component.text()
                    .append(Component.text("N+ ", AQUA, TextDecoration.BOLD))
                    .append(gradientName(name, true))
                    .build();
            case ADMIN -> Component.text()
                    .append(Component.text("OWNER ", BLUE, TextDecoration.BOLD))
                    .append(gradientName(name, true))
                    .build();
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
