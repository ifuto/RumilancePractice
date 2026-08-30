package com.rumilance.practice.cosmetic;

import com.rumilance.practice.guard.PracticeGuards;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;

/**
 * Removes premium-only armor trims when a player's rank drops below VIP+.
 *
 * <p>NORM / VIP players may only keep the baseline trims; anything using a VIP+ material
 * (quartz / gold / diamond / amethyst) or the VIP+ patterns (silence / snout) is stripped
 * back to no trim, which is the vanilla smithing default.</p>
 */
public final class ArmorTrimReset {

    private ArmorTrimReset() {
    }

    /**
     * Strips a single item of any trim a non-VIP+ player is not allowed to keep.
     *
     * @return {@code true} when the item was modified.
     */
    public static boolean stripPremiumTrim(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        if (!(item.getItemMeta() instanceof ArmorMeta meta)) {
            return false;
        }
        ArmorTrim trim = meta.getTrim();
        if (trim == null) {
            return false;
        }
        String materialKey = key(trim.getMaterial());
        String patternKey = key(trim.getPattern());
        boolean materialOk = PracticeGuards.trimMaterialAllowed(false, materialKey);
        boolean patternOk = PracticeGuards.trimPatternAllowed(false, patternKey);
        if (materialOk && patternOk) {
            return false;
        }
        // Premium trim: reset the smithing template to default (no trim).
        meta.setTrim(null);
        item.setItemMeta(meta);
        return true;
    }

    /**
     * Scans every armor / held slot of {@code contents} and strips premium trims in place.
     *
     * @return the number of items that were reset.
     */
    public static int stripPremiumTrims(Iterable<ItemStack> contents) {
        if (contents == null) {
            return 0;
        }
        int changed = 0;
        for (ItemStack item : contents) {
            if (stripPremiumTrim(item)) {
                changed++;
            }
        }
        return changed;
    }

    private static String key(TrimMaterial material) {
        if (material == null) {
            return "";
        }
        try {
            return material.getKey().getKey();
        } catch (Throwable t) {
            return "";
        }
    }

    private static String key(TrimPattern pattern) {
        if (pattern == null) {
            return "";
        }
        try {
            return pattern.getKey().getKey();
        } catch (Throwable t) {
            return "";
        }
    }
}
