package com.rumilance.practice.match;

import com.rumilance.practice.session.MatchSession;
import com.rumilance.practice.state.MatchState;
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
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.UUID;

/**
 * Opponent HP rendered as the vanilla <strong>below-the-namename</strong> score (the same
 * slot the built-in health objective uses), NOT as text appended to the nametag suffix.
 *
 * <p>A per-viewer {@link DisplaySlot#BELOW_NAME} objective ({@code rp_hp}) carries a score of
 * {@code 0..10} hearts for every opponent they fight. Because it is the native health slot,
 * the number rides the player entity in the same way as a nametag (position, movement,
 * tracking, hide/show rules) but is drawn on its own line beneath the name instead of being
 * crammed into the name column. It never appears as nametag suffix text or in the TAB list.</p>
 */
public final class OpponentHealthNametagService implements Listener {

    /** Objective name for the per-viewer below-name hearts score. */
    private static final String OBJECTIVE = "rp_hp";

    private final Plugin plugin;
    private final MatchRegistry matchRegistry;

    public OpponentHealthNametagService(Plugin plugin, MatchRegistry matchRegistry) {
        this.plugin = java.util.Objects.requireNonNull(plugin, "plugin");
        this.matchRegistry = matchRegistry;
    }

    public void start() {
        // A 0.5s refresh keeps hearts correct after respawns / edge cases; damage and heal
        // events refresh instantly in between.
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
            Objective stale = board.getObjective(OBJECTIVE);
            if (stale != null) {
                stale.unregister();
            }
            return;
        }
        Objective objective = belowNameObjective(board);
        for (UUID id : session.participants()) {
            Player target = Bukkit.getPlayer(id);
            if (target == null || !target.isOnline()) {
                continue;
            }
            if (target.getGameMode() == GameMode.SPECTATOR) {
                objective.getScore(target.getName()).setScore(0);
                continue;
            }
            int hearts = hearts(target);
            objective.getScore(target.getName()).setScore(hearts);
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
            Objective objective = belowNameObjective(viewer.getScoreboard());
            if (changed.getGameMode() == GameMode.SPECTATOR) {
                objective.getScore(changed.getName()).setScore(0);
            } else {
                objective.getScore(changed.getName()).setScore(hearts(changed));
            }
        }
    }

    /** Lazily creates (or reuses) the per-viewer below-name hearts objective. */
    private Objective belowNameObjective(Scoreboard board) {
        Objective objective = board.getObjective(OBJECTIVE);
        if (objective == null) {
            objective = board.registerNewObjective(OBJECTIVE, Criteria.DUMMY,
                    net.kyori.adventure.text.Component.text("hp"));
            objective.setDisplaySlot(DisplaySlot.BELOW_NAME);
            // DUMMY renders in red by default; vanilla health uses red numbers.
        }
        return objective;
    }

    public void clearViewer(Player viewer) {
        if (viewer == null) {
            return;
        }
        Scoreboard board = viewer.getScoreboard();
        // Remove the below-name objective entirely so no hearts linger over heads (or in the
        // death screen / TAB) once the viewer leaves the fight.
        Objective objective = board.getObjective(OBJECTIVE);
        if (objective != null) {
            objective.unregister();
        }
    }

    private static int hearts(Player player) {
        double max = Math.max(1.0d, player.getMaxHealth());
        double current = player.getHealth() + player.getAbsorptionAmount();
        int hearts = (int) Math.round((current / max) * 10.0d);
        return Math.max(0, Math.min(14, hearts));
    }
}
