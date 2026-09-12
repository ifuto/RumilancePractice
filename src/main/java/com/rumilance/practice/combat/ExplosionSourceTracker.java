package com.rumilance.practice.combat;

import com.rumilance.practice.PluginIdentity;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/**
 * Resolves which player owns the end crystal / TNT that deals explosion damage, so blast
 * damage is attributed correctly for kill credit and outcome rulings.
 *
 * <p>Vanilla crystal PvP is <strong>self-damaging</strong>: detonating your own end crystal
 * hurts you too (the basis of crystal combos), and the damage event fired to the victim carries
 * the crystal as the damager with <em>no Bukkit attacker</em>. The previous code therefore (a)
 * never credited the crystal owner for an opponent kill, and (b) treated every crystal blast as
 * self-inflicted/environmental, which produced the wrong suicide/&ldquo;draw&rdquo; rulings.
 * This tracker stamps each placed crystal with its owner (and reads the live source of practice
 * TNT) so the damage handlers can attribute the blast.</p>
 *
 * <p>Crystal self-damage is never cancelled here — this only resolves ownership.</p>
 */
public final class ExplosionSourceTracker implements Listener {

    private final NamespacedKey ownerKey;

    /** How long a source-less blast remembers its owner (kill attribution window). */
    private static final long BLAST_OWNER_TTL_MS = 4_000L;
    /** How long a crystal remembers who last punched it (kill attribution fallback). */
    private static final long PUNCH_TTL_MS = 8_000L;

    /** Last puncher per crystal entity id — covers crystals whose placer PDC is lost
     * (e.g. pasted arena crystals). Attribution only; the blast itself is never touched. */
    private final java.util.Map<UUID, Punch> crystalPunchers = new java.util.concurrent.ConcurrentHashMap<>();

    private record Punch(java.util.UUID playerId, long atMillis) {
    }
    /** Source-less explosions (bed bombs, converted crystal blasts) remember their owner so
     * kill credit still works even though the damage event carries no damager entity. */
    private final java.util.Map<String, BlastOwner> blastOwners = new java.util.concurrent.ConcurrentHashMap<>();

    private record BlastOwner(java.util.UUID playerId, long atMillis,
                              org.bukkit.World world, double x, double y, double z) {
    }

    public ExplosionSourceTracker(Plugin plugin) {
        this.ownerKey = new NamespacedKey(PluginIdentity.PDC_NAMESPACE, "explosion_owner");
    }

    /** Record who is responsible for a source-less blast about to be created at {@code at}. */
    public void recordBlastOwner(org.bukkit.Location at, java.util.UUID playerId) {
        if (at == null || at.getWorld() == null || playerId == null) {
            return;
        }
        String key = at.getWorld().getName() + "|" + at.getBlockX() + "|" + at.getBlockY()
                + "|" + at.getBlockZ();
        blastOwners.put(key, new BlastOwner(playerId, System.currentTimeMillis(),
                at.getWorld(), at.getX(), at.getY(), at.getZ()));
        if (blastOwners.size() > 256) {
            long cutoff = System.currentTimeMillis() - BLAST_OWNER_TTL_MS;
            blastOwners.values().removeIf(o -> o.atMillis() < cutoff);
        }
    }

    /** Owner of the closest recent recorded blast to {@code near}, or null. */
    private java.util.UUID lookupBlastOwner(org.bukkit.Location near) {
        if (near == null || near.getWorld() == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        BlastOwner best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlastOwner owner : blastOwners.values()) {
            if (now - owner.atMillis() > BLAST_OWNER_TTL_MS) {
                continue;
            }
            if (!near.getWorld().equals(owner.world())) {
                continue;
            }
            double dx = near.getX() - owner.x();
            double dy = near.getY() - owner.y();
            double dz = near.getZ() - owner.z();
            double dist = dx * dx + dy * dy + dz * dz;
            // Biggest blast here is crystal power 6 -> damage radius ~7.8 blocks.
            if (dist <= 8.5d * 8.5d && dist < bestDist) {
                bestDist = dist;
                best = owner;
            }
        }
        return best == null ? null : best.playerId();
    }

    /** Remembers who last punched a crystal — vanilla's blast detonator (attribution only). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCrystalPunch(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof EnderCrystal crystal)) {
            return;
        }
        UUID puncher = null;
        if (event.getDamager() instanceof Player player) {
            puncher = player.getUniqueId();
        } else if (event.getDamager() instanceof org.bukkit.entity.Projectile projectile
                && projectile.getShooter() instanceof Player shooter) {
            puncher = shooter.getUniqueId();
        }
        if (puncher == null) {
            return;
        }
        crystalPunchers.put(crystal.getUniqueId(), new Punch(puncher, System.currentTimeMillis()));
        if (crystalPunchers.size() > 512) {
            long cutoff = System.currentTimeMillis() - PUNCH_TTL_MS;
            crystalPunchers.values().removeIf(p -> p.atMillis() < cutoff);
        }
    }

    /** Stamp an end crystal with the player who placed it. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCrystalPlace(EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof EnderCrystal crystal)) {
            return;
        }
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        crystal.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, player.getUniqueId().toString());
    }

    /**
     * Resolves the player responsible for an explosion damage event, or {@code null} for a plain
     * environmental hit (fall, void, fire...). Covers practice TNT ({@link TNTPrimed#getSource()})
     * and end crystals (owner PDC).
     */
    public UUID resolveExplosionSource(EntityDamageEvent event) {
        if (event == null) {
            return null;
        }
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause != EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                && cause != EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
            return null;
        }
        if (!(event instanceof EntityDamageByEntityEvent byEntity)) {
            // Source-less blasts (converted crystal blasts / bed bombs / guest TNT): ownership
            // was recorded where the blast was created.
            return lookupBlastOwner(event.getEntity().getLocation());
        }
        Entity damager = byEntity.getDamager();
        if (damager instanceof TNTPrimed tnt && tnt.getSource() instanceof Player source) {
            return source.getUniqueId();
        }
        if (damager instanceof EnderCrystal crystal) {
            return ownerOf(crystal);
        }
        if (damager instanceof Creeper creeper && creeper.getIgniter() instanceof Player igniter) {
            return igniter.getUniqueId();
        }
        return null;
    }

    public UUID ownerOf(Entity entity) {
        if (entity == null) {
            return null;
        }
        if (entity instanceof TNTPrimed tnt && tnt.getSource() instanceof Player source) {
            return source.getUniqueId();
        }
        String raw = entity.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        if (raw != null && !raw.isBlank()) {
            try {
                return UUID.fromString(raw);
            } catch (IllegalArgumentException ignored) {
                // fall through to the puncher fallback
            }
        }
        if (entity instanceof EnderCrystal crystal) {
            Punch punch = crystalPunchers.get(crystal.getUniqueId());
            if (punch != null && System.currentTimeMillis() - punch.atMillis() <= PUNCH_TTL_MS) {
                return punch.playerId();
            }
        }
        return null;
    }
}
