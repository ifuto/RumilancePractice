package com.rumilance.practice.combat;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Single source of truth for human kill attribution.
 *
 * <p>{@link Player#getLastDamageCause()} is not enough for a PvP server: a player can be
 * knocked into the void, die to fall damage after a crystal blast, or be killed by a
 * source-less converted explosion. This service resolves the exact source first and keeps
 * a short, world-bound last-attacker ledger as a fallback for terminal environmental damage.
 * The mode handlers still decide whether that UUID is a valid opponent; this class only
 * answers "which player caused the damage?".</p>
 *
 * <p>It is deliberately independent of match/FFA state. That keeps one attribution rule for
 * melee, projectiles, TNT, creepers, tameable attackers, end crystals and the practice
 * source-less crystal blast conversion.</p>
 */
public final class DamageAttributionService implements Listener {

    /** Same order as the FFA combat tag; enough time for a knockback/void death to resolve. */
    private static final long LAST_ATTACKER_TTL_MS = 30_000L;
    private static final double LOCATION_MAX_DISTANCE_SQUARED = 96.0d * 96.0d;

    private record RecentAttack(UUID attackerId, UUID worldId, Location victimLocation, long atMillis) {
    }

    private final ExplosionSourceTracker explosionSources;
    private final Map<UUID, RecentAttack> recent = new ConcurrentHashMap<>();

    public DamageAttributionService(ExplosionSourceTracker explosionSources) {
        this.explosionSources = explosionSources;
    }

    /**
     * Resolves the player directly responsible for this exact event. A null result means
     * environmental damage or an entity whose ownership is not a player.
     */
    public UUID resolve(EntityDamageEvent event) {
        if (event == null) {
            return null;
        }
        if (explosionSources != null) {
            UUID blastOwner = explosionSources.resolveExplosionSource(event);
            if (blastOwner != null) {
                return blastOwner;
            }
        }
        if (!(event instanceof EntityDamageByEntityEvent by)) {
            return null;
        }
        return playerId(by.getDamager());
    }

    /** Records the final, uncancelled player damage event for environmental-death fallback. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        UUID attackerId = resolve(event);
        if (attackerId == null) {
            return;
        }
        Location location = victim.getLocation();
        UUID worldId = location.getWorld() == null ? null : location.getWorld().getUID();
        recent.put(victim.getUniqueId(), new RecentAttack(
                attackerId, worldId, location.clone(), System.currentTimeMillis()));
        if (recent.size() > 4096) {
            long cutoff = System.currentTimeMillis() - LAST_ATTACKER_TTL_MS;
            recent.entrySet().removeIf(entry -> entry.getValue().atMillis() < cutoff);
        }
    }

    /**
     * Resolves the killer for a real death. Exact event source always wins; only terminal
     * environmental damage may inherit a recent player attacker. Direct unresolved entity
     * damage and unresolved explosions intentionally do not inherit, preventing a random mob
     * or bed blast from being credited to a previous PvP hit.
     */
    public UUID resolveForDeath(Player victim, EntityDamageEvent terminalEvent) {
        UUID exact = resolve(terminalEvent);
        if (exact != null) {
            return exact;
        }
        if (victim == null || (terminalEvent != null
                && !inheritsRecentAttacker(terminalEvent.getCause()))) {
            return null;
        }
        RecentAttack attack = recent.get(victim.getUniqueId());
        if (attack == null || System.currentTimeMillis() - attack.atMillis() > LAST_ATTACKER_TTL_MS) {
            return null;
        }
        Location now = victim.getLocation();
        UUID worldId = now.getWorld() == null ? null : now.getWorld().getUID();
        if (attack.worldId() != null && !attack.worldId().equals(worldId)) {
            return null;
        }
        if (attack.victimLocation() != null && now.distanceSquared(attack.victimLocation())
                > LOCATION_MAX_DISTANCE_SQUARED) {
            return null;
        }
        return attack.attackerId();
    }

    public void clear(UUID victimId) {
        if (victimId != null) {
            recent.remove(victimId);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clear(event.getPlayer().getUniqueId());
    }

    /** An ender-pearl teleport is self-caused; do not credit a previous opponent to its fall. */
    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL) {
            clear(event.getPlayer().getUniqueId());
        }
    }

    /** Environmental damage that can be the terminal result of a player-caused hit. */
    private static boolean inheritsRecentAttacker(EntityDamageEvent.DamageCause cause) {
        if (cause == null) {
            return false;
        }
        return switch (cause) {
            case VOID, FALL, FALLING_BLOCK, FIRE, FIRE_TICK, LAVA, HOT_FLOOR,
                 DROWNING, FREEZE, SUFFOCATION, CRAMMING, STARVATION, DRYOUT -> true;
            default -> false;
        };
    }

    /** Resolves player ownership through Bukkit's common indirect-damager shapes. */
    private static UUID playerId(Entity damager) {
        if (damager == null) {
            return null;
        }
        UUID direct = CombatAttacker.playerId(damager);
        if (direct != null) {
            return direct;
        }
        if (damager instanceof Tameable tameable && tameable.isTamed()
                && tameable.getOwner() instanceof Player owner) {
            return owner.getUniqueId();
        }
        // Optional Paper entity APIs (EvokerFangs#getOwner, AreaEffectCloud#getSource,
        // newer owned projectile wrappers) vary between supported server builds. Reflection
        // keeps the plugin binary-compatible while still crediting them when available.
        for (String methodName : new String[]{"getOwner", "getSource", "getShooter"}) {
            UUID owner = reflectedPlayerId(damager, methodName);
            if (owner != null) {
                return owner;
            }
        }
        return null;
    }

    private static UUID reflectedPlayerId(Entity entity, String methodName) {
        try {
            Method method = entity.getClass().getMethod(methodName);
            Object value = method.invoke(entity);
            if (value instanceof Player player) {
                return player.getUniqueId();
            }
            if (value instanceof Entity owner) {
                return CombatAttacker.playerId(owner);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // This entity type does not expose that ownership API on this Paper build.
        }
        return null;
    }
}
