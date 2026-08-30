package com.rumilance.practice.listener;

import com.destroystokyo.paper.event.player.PlayerAdvancementCriterionGrantEvent;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Practice worlds should not grant vanilla advancements (or toast them). Recipe unlocks
 * are left alone so the recipe book still works.
 */
public final class AdvancementBlockListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCriterion(PlayerAdvancementCriterionGrantEvent event) {
        NamespacedKey key = event.getAdvancement().getKey();
        if (key != null && key.getKey().startsWith("recipes/")) {
            return;
        }
        event.setCancelled(true);
    }
}
