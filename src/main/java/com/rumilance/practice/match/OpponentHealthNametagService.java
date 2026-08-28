package com.rumilance.practice.match;

import com.rumilance.practice.session.MatchSession;
import com.rumilance.practice.state.MatchState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.UUID;

/**
 * Shows opponent HP below nametags (scaled to 10 hearts) for active match participants.
 */
public final class OpponentHealthNametagService implements Listener {

    private static final String OBJECTIVE = "rp_opp_hp";
    private static final String TEAM_PREFIX = "rp_hp_";

    private final Plugin plugin;
    private final MatchRegistry matchRegistry;

    public OpponentHealthNametagService(Plugin plugin, MatchRegistry matchRegistry) {
        this.plugin = plugin;
        this.matchRegistry = matchRegistry;
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
        if (event.getModifiedType() == PotionEffectType.HEALTH_BOOST) {
            refreshFor(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clearViewer(event.getPlayer());
        for (Player online : Bukkit.getOnlinePlayers()) {
            refreshViewer(online);
        }
    }

    public void refreshViewer(Player viewer) {
        MatchSession session = matchRegistry.byPlayer(viewer.getUniqueId()).orElse(null);
        if (session == null || session.state() != MatchState.ACTIVE) {
            clearViewer(viewer);
            return;
        }
        Scoreboard board = viewer.getScoreboard();
        Objective objective = board.getObjective(OBJECTIVE);
        if (objective == null) {
            objective = board.registerNewObjective(
                    OBJECTIVE, Criteria.DUMMY, Component.text("❤", NamedTextColor.RED));
            objective.setDisplaySlot(DisplaySlot.BELOW_NAME);
        }
        for (UUID id : session.participants()) {
            if (id.equals(viewer.getUniqueId())) {
                continue;
            }
            Player target = Bukkit.getPlayer(id);
            if (target == null || !target.isOnline()) {
                continue;
            }
            applyEntry(board, objective, target);
        }
    }

    private void refreshFor(Player changed) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            MatchSession session = matchRegistry.byPlayer(viewer.getUniqueId()).orElse(null);
            if (session == null || session.state() != MatchState.ACTIVE) {
                continue;
            }
            if (!session.isParticipant(changed.getUniqueId())
                    || changed.getUniqueId().equals(viewer.getUniqueId())) {
                continue;
            }
            refreshViewer(viewer);
        }
    }

    private void applyEntry(Scoreboard board, Objective objective, Player target) {
        int hearts = scaledHearts(target);
        boolean boosted = target.hasPotionEffect(PotionEffectType.HEALTH_BOOST);
        NamedTextColor color = boosted ? NamedTextColor.YELLOW : NamedTextColor.RED;
        String entry = target.getName();
        objective.displayName(Component.text("❤", color));
        objective.getScore(entry).setScore(Math.max(0, hearts));

        String teamName = TEAM_PREFIX + entry;
        if (teamName.length() > 16) {
            teamName = teamName.substring(0, 16);
        }
        Team team = board.getTeam(teamName);
        if (team == null) {
            team = board.registerNewTeam(teamName);
        }
        if (!team.hasEntry(entry)) {
            team.addEntry(entry);
        }
        team.prefix(Component.text("❤", color));
        team.suffix(Component.text(" " + hearts + "/10", color));
    }

    private static int scaledHearts(Player player) {
        double max = Math.max(1.0d, player.getMaxHealth());
        double ratio = Math.min(1.0d, Math.max(0.0d, player.getHealth() / max));
        return (int) Math.round(ratio * 10.0d);
    }

    public void clearViewer(Player viewer) {
        if (viewer == null) {
            return;
        }
        Scoreboard board = viewer.getScoreboard();
        Objective objective = board.getObjective(OBJECTIVE);
        if (objective != null) {
            objective.unregister();
        }
        for (Team team : board.getTeams()) {
            if (team.getName().startsWith(TEAM_PREFIX)) {
                team.unregister();
            }
        }
    }
}
