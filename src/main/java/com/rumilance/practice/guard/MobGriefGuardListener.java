package com.rumilance.practice.guard;

import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;

/**
 * Replaces the old global {@code MOB_GRIEFING=false} rule with a surgical per-event guard.
 *
 * <p>MOB_GRIEFING is now left ON so creeper explosions carve terrain like vanilla (players
 * expect that; the terrain is revertible — FFA diffs, disposable duel arena copies, and
 * practice regions clear explosion block lists). This listener cancels the *non-explosion*
 * mob-griefing paths that rule used to suppress, so world/arena decor still survives:</p>
 *
 * <ul>
 *   <li>endermen picking up / placing blocks,</li>
 *   <li>sheep eating grass, foxes picking up items-holding behaviour on blocks, ravagers
 *       eating crops, silverfish burrowing, zombifying villager snapshots, wither flight
 *       trail (wither skeletons never spawn here anyway),</li>
 *   <li>every other {@link LivingEntity} block change that is not a player.</li>
 * </ul>
 *
 * <p>{@link FallingBlock} landings are deliberately untouched — they are entities too, and
 * cancelling them would litter item drops wherever reset flows (or future features) drop
 * sand/gravel.</p>
 */
public final class MobGriefGuardListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof Player) {
            return;
        }
        if (entity instanceof FallingBlock) {
            return;
        }
        if (entity instanceof LivingEntity) {
            event.setCancelled(true);
        }
    }
}
