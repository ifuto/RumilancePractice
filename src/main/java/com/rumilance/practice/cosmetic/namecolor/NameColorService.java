package com.rumilance.practice.cosmetic.namecolor;

import com.rumilance.practice.rank.PlayerRank;
import com.rumilance.practice.rank.RankService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * VIP+ custom name colors: a single color or a two-color gradient applied to the player's
 * chat name, tab-list entry and overhead name. Changes are rate-limited to once every
 * {@link #COOLDOWN}.
 */
public final class NameColorService {

    /** How often a VIP+ player may change their name color. */
    public static final Duration COOLDOWN = Duration.ofDays(3);

    private final NameColorRepository repository;
    private final RankService rankService;
    private final Logger logger;
    private final Map<UUID, NameColorSelection> cache = new ConcurrentHashMap<>();

    public NameColorService(NameColorRepository repository, RankService rankService, Logger logger) {
        this.repository = repository;
        this.rankService = rankService;
        this.logger = logger;
    }

    /** Cached selection (never null). Cache misses load synchronously — the table is tiny. */
    public NameColorSelection selection(UUID uuid) {
        NameColorSelection cached = cache.get(uuid);
        if (cached != null) {
            return cached;
        }
        try {
            NameColorSelection loaded = repository.find(uuid)
                    .orElse(NameColorSelection.DEFAULT);
            cache.put(uuid, loaded);
            return loaded;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed loading name color for " + uuid, e);
            return NameColorSelection.DEFAULT;
        }
    }

    /** True when the player currently paints their name (VIP+ not required to KEEP an old one). */
    public boolean hasCustomColor(UUID uuid) {
        return selection(uuid).active();
    }

    public boolean isVipPlus(UUID uuid) {
        PlayerRank rank = rankService == null ? PlayerRank.NORM : rankService.get(uuid);
        return rank != null && rank.isVipPlusOrAbove();
    }

    /** True when the 3-day cooldown allows another change right now. */
    public boolean canChange(UUID uuid) {
        long changedAt = selection(uuid).changedAtMillis();
        return changedAt <= 0L || System.currentTimeMillis() - changedAt >= COOLDOWN.toMillis();
    }

    /** Remaining cooldown for messages, or empty when a change is allowed. */
    public Optional<Duration> remainingCooldown(UUID uuid) {
        long changedAt = selection(uuid).changedAtMillis();
        if (changedAt <= 0L) {
            return Optional.empty();
        }
        long elapsed = System.currentTimeMillis() - changedAt;
        long remaining = COOLDOWN.toMillis() - elapsed;
        return remaining <= 0L ? Optional.empty() : Optional.of(Duration.ofMillis(remaining));
    }

    /**
     * Saves a new selection (VIP+ and cooldown enforced). Returns true when stored; the caller
     * is expected to have checked {@link #isVipPlus}/{@link #canChange} for messaging.
     */
    public boolean save(UUID uuid, NameColorSelection selection) {
        NameColorSelection stamped = selection.withChangedAt(System.currentTimeMillis());
        cache.put(uuid, stamped);
        try {
            repository.upsert(uuid, stamped);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed saving name color for " + uuid, e);
            return false;
        }
        return true;
    }

    /** Drops the cached entry (e.g. on quit). */
    public void unload(UUID uuid) {
        cache.remove(uuid);
    }

    /** The player's name styled with their selection (plain when none/inactive). */
    public Component styledName(Player player) {
        NameColorSelection selection = selection(player.getUniqueId());
        if (!selection.active()) {
            return Component.text(player.getName());
        }
        if (selection.mode() == NameColorSelection.Mode.SOLID) {
            TextColor color = TextColor.fromHexString("#" + selection.primaryHex());
            return Component.text(player.getName(), color == null ? net.kyori.adventure.text.format.NamedTextColor.WHITE : color);
        }
        // Gradient via MiniMessage: <gradient:#A:#B>name</gradient>
        String mini = "<gradient:#" + selection.primaryHex() + ":#" + selection.secondaryHex() + ">"
                + player.getName() + "</gradient>";
        return MiniMessage.miniMessage().deserialize(mini);
    }

    /** Applies the selection to tab list + overhead display name (null resets to vanilla). */
    public void applyToPlayer(Player player) {
        NameColorSelection selection = selection(player.getUniqueId());
        if (selection.active()) {
            Component styled = styledName(player);
            player.playerListName(styled);
            player.displayName(styled);
        } else {
            player.playerListName(null);
            player.displayName(null);
        }
    }
}
