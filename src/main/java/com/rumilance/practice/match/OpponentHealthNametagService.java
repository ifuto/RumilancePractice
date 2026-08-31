package com.rumilance.practice.match;

import com.rumilance.practice.session.MatchSession;
import com.rumilance.practice.state.MatchState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.UUID;

/**
 * Opponent HP shown next to each fighter's nametag as {@code " <white>N<colored>♥"} — the
 * current health in white followed by a heart that is red normally and gold while the player
 * has absorption (vanilla gold hearts) or a Health Boost.
 *
 * <p>This is written to the per-player fight scoreboard team's SUFFIX (the same teams
 * {@link MatchTeamVisuals} creates for the red/blue nametag colours), so the text rides the
 * player entity like a nametag and only appears to viewers who are watching that match. No
 * below-name numeric/heart objective is used.</p>
 */
public final class OpponentHealthNametagService implements Listener {

    /** Legacy below-name objective name from older builds (unregistered if it lingers). */
    private static final String LEGACY_OBJECTIVE = "rp_hp";

    private final Plugin plugin;
    private final MatchRegistry matchRegistry;

    public OpponentHealthNametagService(Plugin plugin, MatchRegistry matchRegistry) {
        this.plugin = java.util.Objects.requireNonNull(plugin, "plugin");
        this.matchRegistry = matchRegistry;
    }

    public void start() {
        // 0.5s refresh covers respawns / edge cases; damage and heal events refresh instantly.
        Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, 10L, 10L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            refreshFor(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRegain(EntityRegainHealthEvent event) {
        if (event.getEntity() instanceof Player player) {
            refreshFor(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPotion(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (event.getModifiedType() == PotionEffectType.HEALTH_BOOST
                || event.getModifiedType() == PotionEffectType.ABSORPTION) {
            refreshFor(player);
        }
    }

    private void refreshAll() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            refreshViewer(viewer);
        }
    }

    public void refreshViewer(Player viewer) {
        MatchSession session = matchRegistry.byPlayer(viewer.getUniqueId()).orElse(null);
        Scoreboard board = viewer.getScoreboard();
        // Drop any old below-name objective so no duplicate HP display remains.
        Objective legacy = board.getObjective(LEGACY_OBJECTIVE);
        if (legacy != null) {
            try {
                legacy.unregister();
            } catch (IllegalStateException ignored) {
            }
        }
        if (session == null || session.state() != MatchState.ACTIVE) {
            return;
        }
        for (UUID id : session.participants()) {
            Player target = Bukkit.getPlayer(id);
            if (target == null || !target.isOnline()) {
                continue;
            }
            Team team = MatchTeamVisuals.fightTeamOf(board, target);
            if (team == null) {
                continue;
            }
            team.suffix(suffixFor(target));
        }
    }

    private void refreshFor(Player changed) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            MatchSession session = matchRegistry.byPlayer(viewer.getUniqueId()).orElse(null);
            if (session == null || session.state() != MatchState.ACTIVE) {
                continue;
            }
            if (!session.isParticipant(changed.getUniqueId())) {
                continue;
            }
            Team team = MatchTeamVisuals.fightTeamOf(viewer.getScoreboard(), changed);
            if (team != null) {
                if (changed.getGameMode() == GameMode.SPECTATOR) {
                    team.suffix(Component.empty());
                } else {
                    team.suffix(suffixFor(changed));
                }
            }
        }
    }

    /** Clears the HP suffix for a viewer leaving a fight. */
    public void clearViewer(Player viewer) {
        if (viewer == null) {
            return;
        }
        Scoreboard board = viewer.getScoreboard();
        Objective objective = board.getObjective(LEGACY_OBJECTIVE);
        if (objective != null) {
            try {
                objective.unregister();
            } catch (IllegalStateException ignored) {
            }
        }
        for (Team team : board.getTeams()) {
            if (MatchTeamVisuals.isFightTeam(team.getName())) {
                team.suffix(Component.empty());
            }
        }
    }

    /** Builds the nametag suffix: a space, the heart count in white, then a coloured heart. */
    private static Component suffixFor(Player player) {
        double max = 20.0d;
        var attr = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        if (attr != null) {
            max = attr.getValue();
        }
        double hearts = (player.getHealth() + player.getAbsorptionAmount()) / 2.0d;
        boolean boosted = player.getAbsorptionAmount() > 0.0d || max > 20.0d + 0.01d;
        NamedTextColor heartColor = boosted ? NamedTextColor.YELLOW : NamedTextColor.RED;
        return Component.text(" ", NamedTextColor.GRAY)
                .append(Component.text(formatHearts(hearts), NamedTextColor.WHITE)
                        .decoration(TextDecoration.BOLD, false))
                .append(Component.text(" ♥", heartColor));
    }

    private static String formatHearts(double hearts) {
        if (Math.abs(hearts - Math.rint(hearts)) < 0.05d) {
            return String.valueOf((int) Math.rint(hearts));
        }
        return String.format(java.util.Locale.ROOT, "%.1f", hearts);
    }
}
