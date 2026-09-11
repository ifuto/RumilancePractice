package com.rumilance.practice.combat;

import com.rumilance.practice.util.PlayerVitals;
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
 * <p>Combat modes no longer PREDICT lethal damage (the old HP-0 catch diverged from vanilla's
 * real application — the suffocation class of bug). Instead they let vanilla actually kill the
 * player, then inside their {@link PlayerDeathEvent} handler register a {@link RespawnPlan}
 * here. This bridge revives the player on the next tick; {@code DeathBridgePackets} suppresses
 * {@code ClientboundPlayerCombatKillPacket} for planned players so the client never flashes the
 * death screen, vitals hygiene runs, and only THEN the mode's ruling hook executes on a live,
 * bona-fide resurrected player. A death is a one-shot server event, so the ruling cannot ever
 * double-fire and a "0 HP zombie the world refuses to kill" state is theoretically impossible.</p>
 *
 * <p>Static service: wiring one constructor parameter through three mode listeners is churn
 * this small bridge does not need (mirrors {@code PracticeDeath}).</p>
 */
public final class DeathBridge implements Listener {

    /** Where to respawn + what to run right after the revive lands (ruling, re-kit, ...). */
    public record RespawnPlan(Location respawnAt, Runnable onRevived) {
    }

    private static final DeathRegistry<RespawnPlan> REGISTRY = new DeathRegistry<>();
    private static volatile Plugin pluginRef;

    private DeathBridge() {
    }

    /** Registers the death/respawn listeners and arms packet suppression (best effort). */
    public static void start(Plugin plugin) {
        pluginRef = plugin;
        Bukkit.getPluginManager().registerEvents(new DeathBridge(), plugin);
        boolean suppressed = DeathBridgePackets.install(plugin);
        plugin.getLogger().info("[N Arena][DeathBridge] death-catch armed; death screen="
                + (suppressed ? "suppressed (player-combat-kill filtered)"
                        : "FALLBACK instant-respawn (ProtocolLib missing - one-frame flash possible)"));
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

    /** {@code DeathBridgePackets} consults this per outgoing combat-kill packet. */
    public static boolean isPlanned(java.util.UUID playerId) {
        return REGISTRY.isMarked(playerId);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!REGISTRY.isMarked(player.getUniqueId())) {
            return;
        }
        event.deathMessage(null);
        Plugin plugin = pluginRef;
        if (plugin != null) {
            Bukkit.getScheduler().runTask(plugin, () -> tryRespawn(player, 1));
        }
    }

    private void tryRespawn(Player player, int attempt) {
        Plugin plugin = pluginRef;
        if (plugin == null || !player.isOnline()) {
            REGISTRY.clear(player.getUniqueId());
            return;
        }
        if (!REGISTRY.isMarked(player.getUniqueId())) {
            return;
        }
        if (player.isDead()) {
            try {
                player.spigot().respawn();
            } catch (IllegalStateException | IllegalArgumentException e) {
                plugin.getLogger().fine("[N Arena][DeathBridge] respawn already handled: " + e.getMessage());
            }
        }
        // Fail-survivable ladder: a dropped client tick can leave the revive pending; retry a
        // couple of times, then give up quietly (vanilla death flow remains consistent anyway).
        if (attempt < 3) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && player.isDead() && REGISTRY.isMarked(player.getUniqueId())) {
                    tryRespawn(player, attempt + 1);
                }
            }, 2L);
        }
    }

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
            PlayerVitals.fakeDeathReset(player);
            player.setFallDistance(0f);
            player.setFireTicks(0);
            player.setFreezeTicks(0);
            player.setArrowsInBody(0);
            try {
                plan.onRevived().run();
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING,
                        "[N Arena][DeathBridge] revive hook failed for " + player.getName(), e);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        REGISTRY.clear(event.getPlayer().getUniqueId());
    }
}
