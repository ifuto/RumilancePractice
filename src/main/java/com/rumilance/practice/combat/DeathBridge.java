package com.rumilance.practice.combat;

import com.rumilance.practice.util.SafeTeleport;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;

import java.util.logging.Level;

/**
 * Death-catch bridge: the authoritative "died on the server, never saw a death screen" path.
 *
 * <p>Totems stay 100% vanilla: a totem pop is an {@link org.bukkit.event.entity.EntityResurrectEvent}
 * upstream of this bridge and is never touched. Only a REAL death — damage that survived every
 * totem — reaches {@link PlayerDeathEvent}, and the bridge cancels it right there using Paper's
 * cancel + revive-health contract: the player never actually dies, so there is no
 * combat-kill packet, no forced respawn, and no "Loading terrain" screen — not even for a
 * frame. The mode's ruling hook then runs on the living player.</p>
 *
 * <p>Static service: wiring one constructor parameter through three mode listeners is churn
 * this small bridge does not need (mirrors {@code PracticeDeath}). Mode death handlers that
 * {@link #plan} a death MUST register BEFORE this bridge (bootstrap calls {@link #start} last),
 * so the cancel lands after the plan within the same {@code HIGHEST} priority wave.</p>
 */
public final class DeathBridge implements Listener {

    /** Where to put the revived player + what to run right after (ruling, re-kit, ...). */
    public record RespawnPlan(Location respawnAt, Runnable onRevived) {
    }

    private static final DeathRegistry<RespawnPlan> REGISTRY = new DeathRegistry<>();

    private static volatile Plugin pluginRef;

    private DeathBridge() {
    }

    /** Registers the death listener. Call AFTER every mode death handler (see class javadoc). */
    public static void start(Plugin plugin) {
        pluginRef = plugin;
        Bukkit.getPluginManager().registerEvents(new DeathBridge(), plugin);
        plugin.getLogger().info("[N Arena][DeathBridge] death-catch armed (cancel+revive:"
                + " vanilla totems untouched, real deaths cancelled, no death/loading screen)");
    }

    /** Called by mode death handlers AFTER their ruling inputs are frozen. */
    public static void plan(Player victim, Location respawnAt, Runnable onRevived) {
        if (victim == null || onRevived == null) {
            return;
        }
        Location at = respawnAt != null && respawnAt.getWorld() != null
                ? respawnAt.clone() : victim.getLocation();
        REGISTRY.mark(victim.getUniqueId(), new RespawnPlan(at, onRevived));
    }

    /** True while a planned death is pending for this player. */
    public static boolean isPlanned(java.util.UUID playerId) {
        return REGISTRY.isMarked(playerId);
    }

    /**
     * The real-death catch: cancel the death (Paper revives the player with reviveHealth,
     * max health by default) and run hygiene + the mode ruling next tick on the living player.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!REGISTRY.isMarked(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        event.deathMessage(null);
        event.getDrops().clear();
        event.setKeepInventory(true);
        event.setShouldDropExperience(false);
        Plugin plugin = pluginRef;
        if (plugin == null) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> revivePlanned(player));
    }

    /** Next-tick: place the revived player and run the ruling (exactly-once via consume). */
    private void revivePlanned(Player player) {
        Plugin plugin = pluginRef;
        if (plugin == null || !player.isOnline()) {
            REGISTRY.clear(player.getUniqueId());
            return;
        }
        RespawnPlan plan = REGISTRY.consume(player.getUniqueId());
        if (plan == null) {
            return;
        }
        if (player.isDead() || player.getHealth() <= 0.0d) {
            // Defensive: another plugin un-cancelled the death. Fall back to the legacy
            // respawn path so the ruling still runs instead of stranding a 0-HP client.
            forceLegacyRespawn(player, plan);
            return;
        }
        if (plan.respawnAt() != null && plan.respawnAt().getWorld() != null) {
            SafeTeleport.teleport(player, plan.respawnAt());
        }
        player.setFallDistance(0f);
        player.setFireTicks(0);
        player.setFreezeTicks(0);
        player.setArrowsInBody(0);
        runRuling(player, plan);
    }

    /** Fallback for a death that could not be cancelled: vanilla respawn, then the ruling. */
    private void forceLegacyRespawn(Player player, RespawnPlan plan) {
        Plugin plugin = pluginRef;
        if (plugin == null) {
            return;
        }
        try {
            player.spigot().respawn();
        } catch (IllegalStateException | IllegalArgumentException e) {
            plugin.getLogger().fine("[N Arena][DeathBridge] legacy respawn unavailable: " + e.getMessage());
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                if (plan.respawnAt() != null && plan.respawnAt().getWorld() != null) {
                    SafeTeleport.teleport(player, plan.respawnAt());
                }
                runRuling(player, plan);
            }
        });
    }

    private void runRuling(Player player, RespawnPlan plan) {
        Plugin plugin = pluginRef;
        if (plugin == null || !player.isOnline()) {
            return;
        }
        try {
            plan.onRevived().run();
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING,
                    "[N Arena][DeathBridge] revive hook failed for " + player.getName(), e);
        }
    }

    /**
     * Legacy safety net: if a planned death slips through uncancelled (another plugin fought
     * the cancellation), the vanilla respawn still routes through the plan.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        RespawnPlan plan = REGISTRY.consume(player.getUniqueId());
        if (plan == null) {
            return;
        }
        if (plan.respawnAt() != null && plan.respawnAt().getWorld() != null) {
            event.setRespawnLocation(plan.respawnAt());
        }
        Plugin plugin = pluginRef;
        if (plugin == null) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            player.setFallDistance(0f);
            player.setFireTicks(0);
            player.setFreezeTicks(0);
            runRuling(player, plan);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        REGISTRY.clear(event.getPlayer().getUniqueId());
    }
}
