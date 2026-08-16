package com.rumilance.practice.lobby;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;

/**
 * One-shot startup sweep that removes leftover "floating text" entities from every loaded
 * world: {@link TextDisplay}s and invisible, named, marker-style {@link ArmorStand}s (the
 * classic hologram trick). These frequently linger after crashes or from other plugins'
 * abandoned holograms; clearing them once at boot keeps the lobby clean. Runs a tick after
 * enable so worlds are fully loaded, and never touches armor stands that look like real
 * builds (visible, or carrying equipment).
 */
public final class FloatingTextCleanup {

    private FloatingTextCleanup() {
    }

    /** Schedules the sweep for one tick after enable; logs how many entities were removed. */
    public static void scheduleSweep(Plugin plugin) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            org.bukkit.NamespacedKey wallTextKey = new org.bukkit.NamespacedKey(
                    plugin, com.rumilance.practice.decor.WallTextService.MARKER);
            int removed = 0;
            for (World world : Bukkit.getWorlds()) {
                for (Entity entity : world.getEntities()) {
                    if (isFloatingText(entity, wallTextKey)) {
                        entity.remove();
                        removed++;
                    }
                }
            }
            if (removed > 0) {
                plugin.getLogger().info("Removed " + removed + " leftover floating-text entities (holograms).");
            }
        });
    }

    private static boolean isFloatingText(Entity entity, org.bukkit.NamespacedKey wallTextKey) {
        if (entity instanceof TextDisplay display) {
            // Plugin-managed wall texts are respawned intentionally — never sweep them.
            return !display.getPersistentDataContainer()
                    .has(wallTextKey, org.bukkit.persistence.PersistentDataType.STRING);
        }
        if (entity instanceof ArmorStand stand) {
            // Hologram signature: invisible + a custom name shown + no gravity. Anything with
            // equipment (real decorative stands) is left alone.
            boolean hologramLike = !stand.isVisible()
                    && stand.isCustomNameVisible()
                    && stand.customName() != null
                    && !stand.hasGravity();
            if (!hologramLike) {
                return false;
            }
            for (org.bukkit.inventory.ItemStack item : stand.getEquipment().getArmorContents()) {
                if (item != null && !item.getType().isAir()) {
                    return false;
                }
            }
            return stand.getEquipment().getItemInMainHand().getType().isAir()
                    && stand.getEquipment().getItemInOffHand().getType().isAir();
        }
        return false;
    }
}
