package com.rumilance.practice.cosmetic;

import com.rumilance.practice.model.RankedKitStats;
import com.rumilance.practice.model.PlayerSettings;
import com.rumilance.practice.settings.SettingsService;
import com.rumilance.practice.stats.StatsService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Owns the cosmetic title ladder and decides which titles a player has unlocked. The selected
 * title is persisted through {@link PlayerSettings} (the {@code selected_title} column added by
 * migration v14), while unlock status is computed from the player's lifetime ranked wins and
 * best Elo via {@link StatsService}.
 */
public final class TitleService {

    private static final Duration TITLE_FADE_IN = Duration.ZERO;
    private static final Duration TITLE_STAY = Duration.ofMillis(900);
    private static final Duration TITLE_FADE_OUT = Duration.ofMillis(400);

    private final SettingsService settingsService;
    private final StatsService statsService;
    private final List<KillTitle> titles;

    public TitleService(SettingsService settingsService, StatsService statsService) {
        this.settingsService = settingsService;
        this.statsService = statsService;
        this.titles = KillTitle.defaults();
    }

    /** @return every title in the ladder, ordered from the lowest to the highest tier. */
    public List<KillTitle> all() {
        return titles;
    }

    /** @return the title the player currently has selected (or {@link KillTitle#NONE}). */
    public KillTitle selected(Player player) {
        String id = settingsService.get(player).selectedTitle();
        return byId(id);
    }

    /** @return the highest-tier title a player has unlocked (always at least the Rookie tier). */
    public KillTitle bestUnlocked(UUID playerId) {
        int totalWins;
        int bestElo;
        try {
            List<RankedKitStats> kits = statsService.allKits(playerId);
            totalWins = kits.stream().mapToInt(RankedKitStats::wins).sum();
            bestElo = kits.stream().mapToInt(RankedKitStats::bestElo).max().orElse(1000);
        } catch (Exception e) {
            totalWins = 0;
            bestElo = 1000;
        }
        return titles.stream()
                .filter(t -> totalWins >= t.requiredWins() && bestElo >= t.requiredElo())
                .max(Comparator.comparingInt(t -> t.requiredWins() + t.requiredElo()))
                .orElse(KillTitle.NONE);
    }

    public boolean isUnlocked(UUID playerId, KillTitle title) {
        if (title == KillTitle.NONE) {
            return true;
        }
        int totalWins;
        int bestElo;
        try {
            List<RankedKitStats> kits = statsService.allKits(playerId);
            totalWins = kits.stream().mapToInt(RankedKitStats::wins).sum();
            bestElo = kits.stream().mapToInt(RankedKitStats::bestElo).max().orElse(1000);
        } catch (Exception e) {
            return false;
        }
        return totalWins >= title.requiredWins() && bestElo >= title.requiredElo();
    }

    /** Sets the active title for the player, provided they have unlocked it. */
    public boolean select(Player player, KillTitle title) {
        if (!isUnlocked(player.getUniqueId(), title)) {
            return false;
        }
        PlayerSettings settings = settingsService.get(player);
        settingsService.update(settings.withSelectedTitle(title.id()));
        return true;
    }

    public KillTitle byId(String id) {
        if (id == null || id.isBlank() || id.equalsIgnoreCase("none")) {
            return KillTitle.NONE;
        }
        return titles.stream()
                .filter(t -> t.id().equalsIgnoreCase(id))
                .findFirst()
                .orElse(KillTitle.NONE);
    }

    /** Broadcasts the player's selected kill title as a screen-wide title. */
    public void showKillTitle(Player player) {
        KillTitle title = selected(player);
        if (title == KillTitle.NONE) {
            return;
        }
        player.showTitle(Title.title(
                title.mainTitle(),
                title.subtitle("KILL!"),
                Title.Times.times(TITLE_FADE_IN, TITLE_STAY, TITLE_FADE_OUT)));
    }

    /** Broadcasts the player's selected win title as a screen-wide title. */
    public void showWinTitle(Player player) {
        KillTitle title = selected(player);
        if (title == KillTitle.NONE) {
            return;
        }
        player.showTitle(Title.title(
                title.mainTitle(),
                title.subtitle("VICTORY"),
                Title.Times.times(TITLE_FADE_IN, TITLE_STAY, TITLE_FADE_OUT)));
    }
}
