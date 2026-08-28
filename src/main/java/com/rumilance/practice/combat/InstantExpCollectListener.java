package com.rumilance.practice.combat;

import com.destroystokyo.paper.event.player.PlayerPickupExperienceEvent;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ExpBottleEvent;
import org.bukkit.event.player.PlayerExpCooldownChangeEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thrown XP bottles drop orbs as usual, then the thrower can vacuum them with no
 * per-second pickup cap. Orbs that are too far still wait until the player is in range.
 */
public final class InstantExpCollectListener implements Listener {

    /** Ticks after a bottle throw/orb pickup during which bottle-orb cooldown is skipped. */
    private static final int BOTTLE_WINDOW_TICKS = 60;

    private final Map<UUID, Integer> bottleWindowUntil = new ConcurrentHashMap<>();

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBottle(ExpBottleEvent event) {
        if (event.getEntity().getShooter() instanceof Player player) {
            player.setExpCooldown(0);
            openWindow(player);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPickup(PlayerPickupExperienceEvent event) {
        ExperienceOrb orb = event.getExperienceOrb();
        if (orb.getSpawnReason() != ExperienceOrb.SpawnReason.EXP_BOTTLE) {
            return;
        }
        Player player = event.getPlayer();
        player.setExpCooldown(0);
        openWindow(player);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onCooldown(PlayerExpCooldownChangeEvent event) {
        if (event.getReason() != PlayerExpCooldownChangeEvent.ChangeReason.PICKUP_ORB) {
            return;
        }
        Integer until = bottleWindowUntil.get(event.getPlayer().getUniqueId());
        if (until == null) {
            return;
        }
        int now = event.getPlayer().getServer().getCurrentTick();
        if (now <= until) {
            event.setNewCooldown(0);
        } else {
            bottleWindowUntil.remove(event.getPlayer().getUniqueId());
        }
    }

    private void openWindow(Player player) {
        bottleWindowUntil.put(player.getUniqueId(), player.getServer().getCurrentTick() + BOTTLE_WINDOW_TICKS);
    }
}
