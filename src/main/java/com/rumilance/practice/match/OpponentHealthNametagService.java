package com.rumilance.practice.match;

import com.rumilance.practice.session.MatchSession;
import com.rumilance.practice.state.MatchState;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
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
import org.bukkit.scoreboard.RenderType;
import org.bukkit.scoreboard.Scoreboard;

import java.util.UUID;

/**
 * Opponent HP rendered as the vanilla <strong>heart row under the nametag</strong> (the same
 * slot/look as the built-in below-name health display).
 *
 * <p>A per-viewer {@link DisplaySlot#BELOW_NAME} objective ({@code rp_hp}) uses
 * {@link RenderType#HEARTS} and carries the fighter's real (health + absorption) value in
 * half-hearts, so a row of hearts appears directly beneath each opponent's name: red hearts for
 * current health and gold for absorption. Because it is the native health slot it rides the
 * player entity like a nametag (position, movement, hide/show rules) but sits on its own line
 * under the name and never appears in the TAB list.</p>
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
                try {
                    stale.unregister();
                } catch (IllegalStateException ignored) {
                }
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
            objective.getScore(target.getName()).setScore(healthScore(target));
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
                objective.getScore(changed.getName()).setScore(healthScore(changed));
            }
        }
    }

    /** Lazily creates (or reuses) the per-viewer below-name hearts objective. */
    private Objective belowNameObjective(Scoreboard board) {
        Objective objective = board.getObjective(OBJECTIVE);
        if (objective == null) {
            // RenderType.HEARTS draws the vanilla heart row under the nametag. Each score point
            // is half a heart; we feed the real (health + absorption) value.
            objective = board.registerNewObjective(OBJECTIVE, Criteria.DUMMY,
                    net.kyori.adventure.text.Component.empty(), RenderType.HEARTS);
            objective.setDisplaySlot(DisplaySlot.BELOW_NAME);
        }
        return objective;
    }

    public void clearViewer(Player viewer) {
        if (viewer == null) {
            return;
        }
        Scoreboard board = viewer.getScoreboard();
        // Remove the below-name objective so no hearts linger over heads (or in the death
        // screen / TAB) once the viewer leaves the fight.
        Objective objective = board.getObjective(OBJECTIVE);
        if (objective != null) {
            try {
                objective.unregister();
            } catch (IllegalStateException ignored) {
            }
        }
    }

    /**
     * Heart value for the below-name objective: real health plus absorption, rounded to whole
     * half-hearts. Each point renders as half a heart, capped at the player's (possibly boosted)
     * maximum so the row never over-fills.
     */
    private static int healthScore(Player player) {
        double maxHp = 20.0d;
        AttributeInstance attr = player.getAttribute(Attribute.MAX_HEALTH);
        if (attr != null) {
            maxHp = attr.getValue();
        }
        double absorption = player.getAbsorptionAmount();
        double current = player.getHealth() + absorption;
        double cap = maxHp + absorption;
        int score = (int) Math.round(current);
        return Math.max(0, (int) Math.min(Math.ceil(cap), score));
    }
}
