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
 * Puts crystal self-damage back: on Paper the detonator is the crystal explosion's SOURCE
 * entity, and Paper explosions never damage their source (PaperMC/Paper#11167 family —
 * closed as intended). Vanilla would exempt only the crystal itself, so punching your own
 * crystal on this server dealt nothing — the core trade-off of crystal PvP vanished.
 *
 * <p>The fix: the player-caused hit on a crystal is cancelled (stopping Paper's
 * detonator-sourced blast) and the crystal is re-detonated with a <em>source-less</em>
 * {@code World#createExplosion}: nobody is exempt, so everyone in radius — detonator
 * included — takes the exact vanilla blast damage and knockback. Ownership is recorded in
 * {@link ExplosionSourceTracker} so kill attribution still lands on the detonator. Chain
 * detonations (a blast damaging another crystal) are converted the same way, owned by the
 * chain crystal's placer / last puncher.</p>
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
     * Paper blast whose source (the detonator) would otherwise be exempt; the crystal is
     * re-detonated source-less with the puncher as owner.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCrystalHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof EnderCrystal crystal)) {
            return;
        }
        java.util.UUID detonator = detonatorOf(event.getDamager());
        if (detonator == null) {
            return; // not player-caused: vanilla handles it
        }
        event.setCancelled(true);
        detonate(crystal, detonator);
    }

    /**
     * A blast (our converted source-less one, or any other explosion) damaging a crystal:
     * convert the chain detonation to source-less as well, owned by that crystal's placer
     * or last puncher.
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

    private java.util.UUID detonatorOf(Entity damager) {
        if (damager instanceof Player player) {
            return player.getUniqueId();
        }
        if (damager instanceof Projectile projectile
                && projectile.getShooter() instanceof Player player) {
            return player.getUniqueId();
        }
        if (damager instanceof org.bukkit.entity.TNTPrimed tnt
                && tnt.getSource() instanceof Player player) {
            return player.getUniqueId();
        }
        if (damager instanceof EnderCrystal chainCrystal
                && explosionSources.ownerOf(chainCrystal) != null) {
            // A crystal damaged directly by another crystal entity (chain edge case).
            return explosionSources.ownerOf(chainCrystal);
        }
        return null;
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
        // Source == null: Paper exempts only the source entity, and there is none — the
        // detonator takes their share, exactly like vanilla crystal PvP.
        world.createExplosion(at, CRYSTAL_POWER, false, BREAK_BLOCKS, null);
    }
}
