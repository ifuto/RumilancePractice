package com.rumilance.practice.combat;

import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;

/**
 * Vanilla marks every player-fired arrow {@code OWNER_ONLY}: the shooter can collect it from
 * the ground, everyone else walks over it in vain. On a practice server arrows are shared
 * ammunition — in duels, FFA and bot fights the landed arrow is restock, and "why can't I
 * pick up THEIR arrow" reads as a bug. So every arrow (and thrown trident) is switched to
 * {@link AbstractArrow.PickupStatus#ALLOWED} the moment it is launched, making it collectible
 * by anyone, exactly like a mob-/dispenser-fired arrow already is.
 *
 * <p>Covers bots and NPC players too (the events fire for any shooter); nothing else about the
 * arrow is touched — damage normalization stays in {@link PaperCombatCompatListener}.</p>
 */
public final class ArrowPickupListener implements Listener {

    /** Bows and crossbows: mark the fired arrow free-for-all pickup. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShootBow(EntityShootBowEvent event) {
        if (event.getProjectile() instanceof AbstractArrow arrow) {
            arrow.setPickupStatus(AbstractArrow.PickupStatus.ALLOWED);
        }
    }

    /** Tridents never fire {@link EntityShootBowEvent}; they launch straight from the hand. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLaunch(PlayerLaunchProjectileEvent event) {
        if (event.getProjectile() instanceof Trident trident) {
            trident.setPickupStatus(AbstractArrow.PickupStatus.ALLOWED);
        }
    }
}
