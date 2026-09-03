package com.rumilance.practice.match;

import com.rumilance.practice.session.MatchSession;
import com.rumilance.practice.state.MatchState;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

import java.util.UUID;

/**
 * Opponent HP rendered as a {@code <number>♥} line in the vanilla
 * <strong>below-name slot</strong> — the slot that rides the player entity exactly like the
 * nametag itself (same position, movement, sneak-hide and distance rules), on its own line
 * under the name, never in the TAB list.
 *
 * <p>Rendering uses per-score {@link NumberFormat#fixed(Component)} (Paper 1.20.3+): the whole
 * line is replaced by a styled component, so each fighter gets their own coloured readout —
 * the number in white (health capped at 20, absorption on top), and the {@code ♥} red by
 * default or yellow while absorption hearts are present. The objective display name stays
 * empty so nothing else is appended.</p>
 */
public final class OpponentHealthNametagService implements Listener {

    /** Objective name for the per-viewer below-name health score. */
    private static final String OBJECTIVE = "rp_hp";
    /** The health part of the readout never shows more than this (absorption adds on top). */
    private static final double HEALTH_CAP = 20.0d;

    private final Plugin plugin;
    private final MatchRegistry matchRegistry;

    public OpponentHealthNametagService(Plugin plugin, MatchRegistry matchRegistry) {
        this.plugin = java.util.Objects.requireNonNull(plugin, "plugin");
        this.matchRegistry = matchRegistry;
    }

    public void start() {
        // A 0.5s refresh keeps the line correct after respawns / edge cases; damage and heal
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
            applyTo(objective, target);
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
            applyTo(belowNameObjective(viewer.getScoreboard()), changed);
        }
    }

    /** Writes one fighter's {@code <number>♥} readout into the below-name slot. */
    private void applyTo(Objective objective, Player target) {
        Score score = objective.getScore(target.getName());
        if (target.getGameMode() == GameMode.SPECTATOR) {
            // Parked / eliminated: hide the line entirely instead of showing 0♥.
            score.setScore(0);
            score.numberFormat(NumberFormat.blank());
            return;
        }
        double absorption = Math.max(0.0d, target.getAbsorptionAmount());
        double health = Math.min(target.getHealth(), HEALTH_CAP);
        int shown = (int) Math.ceil(Math.max(0.0d, health) + absorption);
        // The integer score itself is never rendered (the fixed format replaces it); keep it
        // equal to the shown value anyway so sorting/debugging matches what players see.
        score.setScore(shown);
        Component heart = Component.text("\u2665",
                absorption > 0.0d ? NamedTextColor.YELLOW : NamedTextColor.RED);
        score.numberFormat(NumberFormat.fixed(
                Component.text(shown, NamedTextColor.WHITE).append(heart)));
    }

    /** Lazily creates (or reuses) the per-viewer below-name objective. */
    private Objective belowNameObjective(Scoreboard board) {
        Objective objective = board.getObjective(OBJECTIVE);
        if (objective == null) {
            objective = board.registerNewObjective(OBJECTIVE, Criteria.DUMMY, Component.empty());
            objective.setDisplaySlot(DisplaySlot.BELOW_NAME);
        }
        return objective;
    }

    public void clearViewer(Player viewer) {
        if (viewer == null) {
            return;
        }
        Scoreboard board = viewer.getScoreboard();
        // Remove the below-name objective so no readout lingers over heads (or in the death
        // screen / TAB) once the viewer leaves the fight.
        Objective objective = board.getObjective(OBJECTIVE);
        if (objective != null) {
            try {
                objective.unregister();
            } catch (IllegalStateException ignored) {
            }
        }
    }

}
