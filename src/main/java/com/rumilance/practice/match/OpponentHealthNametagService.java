package com.rumilance.practice.match;

import com.rumilance.practice.PluginIdentity;
import com.rumilance.practice.session.MatchSession;
import com.rumilance.practice.state.MatchState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Opponent HP as a {@link TextDisplay} floating above the nametag ({@code ♥8/10}). Yellow when
 * Health Boost or absorption is present.
 */
public final class OpponentHealthNametagService implements Listener {

    private static final double DISPLAY_LIFT = 0.35d;

    private final Plugin plugin;
    private final MatchRegistry matchRegistry;
    private final NamespacedKey heartMarker;
    private final Map<UUID, TextDisplay> heartDisplays = new ConcurrentHashMap<>();

    public OpponentHealthNametagService(Plugin plugin, MatchRegistry matchRegistry) {
        this.plugin = java.util.Objects.requireNonNull(plugin, "plugin");
        this.matchRegistry = matchRegistry;
        this.heartMarker = new NamespacedKey(PluginIdentity.PDC_NAMESPACE, "opp-heart");
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickPositions, 1L, 1L);
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

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        removeHeartDisplay(event.getPlayer().getUniqueId());
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
        for (UUID id : session.participants()) {
            Player target = Bukkit.getPlayer(id);
            if (target == null || !target.isOnline()) {
                continue;
            }
            applyDisplay(board, target);
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
            applyDisplay(viewer.getScoreboard(), changed);
        }
    }

    private void tickPositions() {
        for (Map.Entry<UUID, TextDisplay> entry : heartDisplays.entrySet()) {
            Player target = Bukkit.getPlayer(entry.getKey());
            TextDisplay display = entry.getValue();
            if (target == null || !target.isOnline() || display == null || !display.isValid()) {
                continue;
            }
            MatchSession session = matchRegistry.byPlayer(target.getUniqueId()).orElse(null);
            if (session == null || session.state() != MatchState.ACTIVE
                    || target.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            syncPosition(target, display);
        }
    }

    private void syncPosition(Player target, TextDisplay display) {
        Location feet = target.getLocation();
        Location dest = feet.clone().add(0.0d, target.getHeight() + DISPLAY_LIFT, 0.0d);
        dest.setYaw(feet.getYaw());
        dest.setPitch(0f);
        display.teleport(dest);
    }

    private void applyDisplay(Scoreboard board, Player target) {
        Team team = MatchTeamVisuals.fightTeamOf(board, target);
        if (team != null) {
            team.suffix(Component.empty());
        }
        if (target.getGameMode() == GameMode.SPECTATOR) {
            removeHeartDisplay(target.getUniqueId());
            return;
        }
        HeartSnapshot snap = heartSnapshot(target);
        TextDisplay display = heartDisplays.compute(target.getUniqueId(), (id, existing) -> {
            if (existing != null && existing.isValid()) {
                return existing;
            }
            return target.getWorld().spawn(target.getLocation(), TextDisplay.class, d -> {
                d.getPersistentDataContainer().set(heartMarker, PersistentDataType.BYTE, (byte) 1);
                d.setBillboard(Display.Billboard.FIXED);
                d.setSeeThrough(true);
                d.setDefaultBackground(false);
                d.setShadowed(false);
                d.setPersistent(true);
            });
        });
        display.text(Component.text("♥" + snap.hearts() + "/10", snap.color()));
        syncPosition(target, display);
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

    private void removeHeartDisplay(UUID playerId) {
        TextDisplay display = heartDisplays.remove(playerId);
        if (display != null && display.isValid()) {
            display.remove();
        }
    }

    public void clearViewer(Player viewer) {
        if (viewer == null) {
            return;
        }
        Scoreboard board = viewer.getScoreboard();
        org.bukkit.scoreboard.Objective objective = board.getObjective("rp_opp_hp");
        if (objective != null) {
            objective.unregister();
        }
        for (Team team : java.util.Set.copyOf(board.getTeams())) {
            if (team.getName().startsWith("rp_hp_")) {
                try {
                    team.unregister();
                } catch (IllegalStateException ignored) {
                    // gone
                }
            }
        }
        MatchSession session = matchRegistry.byPlayer(viewer.getUniqueId()).orElse(null);
        if (session == null) {
            for (UUID id : heartDisplays.keySet()) {
                removeHeartDisplay(id);
            }
        }
    }

    private record HeartSnapshot(int hearts, NamedTextColor color) {
    }
}
