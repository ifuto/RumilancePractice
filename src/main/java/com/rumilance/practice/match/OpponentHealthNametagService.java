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
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.UUID;

/**
 * Opponent HP rendered as the <strong>suffix of the player's nametag team</strong>, so it sits
 * in exactly the same place and follows exactly the same movement/rules as the normal MCID
 * nametag (and TAB entry) — no floating TextDisplay entity that lags or detaches.
 *
 * <p>The suffix ({@code <sp>♥8/10}, red normally, yellow with absorption/health-boost) is
 * written onto the per-player fight team created by {@link MatchTeamVisuals}; it is cleared
 * when the match stops being active.</p>
 */
public final class OpponentHealthNametagService implements Listener {

    private final Plugin plugin;
    private final MatchRegistry matchRegistry;

    public OpponentHealthNametagService(Plugin plugin, MatchRegistry matchRegistry) {
        this.plugin = java.util.Objects.requireNonNull(plugin, "plugin");
        this.matchRegistry = matchRegistry;
    }

    public void start() {
        // Nametag suffixes ride the player entity and need no per-tick position sync; a slow
        // refresh keeps health correct after edge cases (respawns, team re-creation).
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
        if (session == null || session.state() != MatchState.ACTIVE) {
            clearSuffixes(board);
            return;
        }
        for (UUID id : session.participants()) {
            Player target = Bukkit.getPlayer(id);
            if (target != null && target.isOnline()) {
                applySuffix(board, target);
            }
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
            applySuffix(viewer.getScoreboard(), changed);
        }
    }

    private void applySuffix(Scoreboard board, Player target) {
        Team team = MatchTeamVisuals.fightTeamOf(board, target);
        if (team == null) {
            return;
        }
        if (target.getGameMode() == GameMode.SPECTATOR) {
            team.suffix(Component.empty());
            return;
        }
        HeartSnapshot snap = heartSnapshot(target);
        team.suffix(Component.text(" ", NamedTextColor.WHITE)
                .append(Component.text("♥" + snap.hearts() + "/10", snap.color())
                        .decoration(TextDecoration.BOLD, false)));
    }

    private void clearSuffixes(Scoreboard board) {
        if (board == null) {
            return;
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            Team team = MatchTeamVisuals.fightTeamOf(board, online);
            if (team != null && team.suffix() != null && !team.suffix().equals(Component.empty())) {
                team.suffix(Component.empty());
            }
        }
    }

    private static HeartSnapshot heartSnapshot(Player player) {
        double max = Math.max(1.0d, player.getMaxHealth());
        double current = player.getHealth() + player.getAbsorptionAmount();
        int hearts = (int) Math.round((current / max) * 10.0d);
        hearts = Math.max(0, Math.min(14, hearts));
        boolean yellow = player.hasPotionEffect(PotionEffectType.HEALTH_BOOST)
                || player.getAbsorptionAmount() > 0.01d;
        NamedTextColor color = yellow ? NamedTextColor.YELLOW : NamedTextColor.RED;
        return new HeartSnapshot(hearts, color);
    }

    public void clearViewer(Player viewer) {
        if (viewer != null) {
            clearSuffixes(viewer.getScoreboard());
        }
    }

    private record HeartSnapshot(int hearts, NamedTextColor color) {
    }
}
