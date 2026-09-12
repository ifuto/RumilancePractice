package com.rumilance.practice.combat;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * Makes your own end crystals hurt you again — the way true vanilla plays crystal PvP.
 *
 * <p>On this Paper build a player-caused explosion exempts its source entity from blast damage
 * ({@code PaperMC/Paper#11167} mechanism family), so punching your own crystal - self-blast
 * being the core trade-off of crystal PvP - dealt the detonator nothing. Bukkit offers no way
 * to change the source of the vanilla crystal explosion, so instead the damaging hit on the
 * crystal is cancelled and the crystal is detonated here with a <em>source-less</em> explosion:
 * nobody is exempt (every blast hurts everyone in radius, detonator included), and ownership
 * is recorded in {@link ExplosionSourceTracker} so kill attribution still lands on the
 * detonator. Chain detonations (a blast damaging another crystal) are converted the same way,
 * owned by the chain crystal's placer.</p>
 *
 * <p>Blast parameters mirror vanilla: power 6, no fire, blocks break.</p>
 */
public final class CrystalSelfBlastListener implements Listener {

    /** Vanilla end-crystal blast power. */
    private static final float CRYSTAL_POWER = 6.0f;
    /** Vanilla end crystals break blocks (practice maps rely on it, anchored obsidian meta). */
    private static final boolean BREAK_BLOCKS = true;

    private final ExplosionSourceTracker explosionSources;

    public CrystalSelfBlastListener(ExplosionSourceTracker explosionSources) {
        this.explosionSources = explosionSources;
    }

    /**
     * The punch (or projectile hit) that detonates a crystal: cancelling stops the vanilla
     * source-exempt blast; the crystal is re-detonated source-less with the puncher as owner.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCrystalHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof EnderCrystal crystal)) {
            return;
        }
        java.util.UUID detonator = null;
        Entity damager = event.getDamager();
        if (damager instanceof Player player) {
            detonator = player.getUniqueId();
        } else if (damager instanceof Projectile projectile
                && projectile.getShooter() instanceof Player player) {
            detonator = player.getUniqueId();
        } else if (damager instanceof org.bukkit.entity.TNTPrimed tnt
                && tnt.getSource() instanceof Player player) {
            detonator = player.getUniqueId();
        } else if (damager instanceof EnderCrystal chainCrystal
                && explosionSources.ownerOf(chainCrystal) != null) {
            // A crystal damaged directly by another crystal entity (chain edge case).
            detonator = explosionSources.ownerOf(chainCrystal);
        }
        if (detonator == null) {
            return; // not player-caused: vanilla handles it
        }
        event.setCancelled(true);
        detonate(crystal, detonator);
    }

    /**
     * A blast (our converted source-less one, or any other explosion) damaging a crystal:
     * convert the chain detonation to source-less as well, owned by that crystal's placer.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCrystalChainDamaged(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent
                || !(event.getEntity() instanceof EnderCrystal crystal)) {
            return;
        }
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause != EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                && cause != EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
            return;
        }
        event.setCancelled(true);
        detonate(crystal, explosionSources.ownerOf(crystal));
    }

    /** Source-less re-detonation at the crystal's exact position (vanilla blast origin). */
    private void detonate(EnderCrystal crystal, java.util.UUID owner) {
        if (crystal.isDead() || !crystal.isValid()) {
            return;
        }
        Location at = crystal.getLocation();
        World world = at.getWorld();
        crystal.remove();
        if (world == null) {
            return;
        }
        if (owner != null) {
            explosionSources.recordBlastOwner(at, owner);
        }
        world.createExplosion(at, CRYSTAL_POWER, false, BREAK_BLOCKS, null);
    }
}
