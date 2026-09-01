package com.rumilance.practice.util;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.Vector;

import java.util.ArrayList;

/**
 * Resets combat leftovers (effects, absorption, cooldowns, fire) so kit apply / FFA respawn
 * / lobby return do not carry poison, gapple hearts, or pearl cooldown into the next life.
 *
 * <p>Does <strong>not</strong> mutate player {@code Attribute}s  Ehealth is refilled against
 * the player's current max only.</p>
 */
public final class PlayerVitals {

    private PlayerVitals() {
    }

    public static void clearCombatState(Player player) {
        if (player == null) {
            return;
        }
        player.setFireTicks(0);
        player.setFreezeTicks(0);
        player.setFallDistance(0f);
        player.setVelocity(new Vector());
        player.setArrowsInBody(0);
        try {
            player.setBeeStingersInBody(0);
        } catch (NoSuchMethodError ignored) {
            // Older Paper builds.
        }
        try {
            player.setVisualFire(false);
        } catch (NoSuchMethodError ignored) {
        }
        player.eject();
        player.setAbsorptionAmount(0f);
        player.setExhaustion(0f);
        player.setRemainingAir(player.getMaximumAir());
        player.clearActiveItem();
        for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) {
            player.removePotionEffect(effect.getType());
        }
        player.setCooldown(Material.ENDER_PEARL, 0);
        player.setCooldown(Material.CHORUS_FRUIT, 0);
        player.setCooldown(Material.SHIELD, 0);
        player.setCooldown(Material.ENDER_EYE, 0);
        try {
            player.setCooldown(Material.WIND_CHARGE, 0);
        } catch (NoSuchFieldError ignored) {
            // Pre-1.21.
        }
    }

    /**
     * Fake death: never the vanilla death screen. Inventory gone, effects gone, HP full.
     * Callers then either send the player to lobby or re-apply a kit.
     */
    public static void fakeDeathReset(Player player) {
        if (player == null) {
            return;
        }
        player.setGameMode(org.bukkit.GameMode.SURVIVAL);
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(null);
        player.setItemOnCursor(null);
        clearCombatState(player);
        resetMaxHealth(player);
    }

    /**
     * Refills HP to the player's current max health. Does not change attributes.
     * {@code preferredMax} is ignored for attribute writes  Ekept for call-site compatibility.
     */
    public static void setMaxHealth(Player player, double preferredMax) {
        refillHealth(player);
    }

    /** Heal to current max health without touching attributes. */
    public static void refillHealth(Player player) {
        if (player == null) {
            return;
        }
        double max = Math.max(1.0d, player.getMaxHealth());
        player.setHealth(max);
    }

    /** Reset a kit-raised MAX_HEALTH attribute back to the vanilla 20 and heal to full. */
    public static void resetMaxHealth(Player player) {
        if (player == null) {
            return;
        }
        try {
            org.bukkit.attribute.AttributeInstance maxAttr =
                    player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
            if (maxAttr != null) {
                maxAttr.setBaseValue(20.0d);
            }
        } catch (RuntimeException ignored) {
        }
        refillHealth(player);
    }

    /**
     * Duel / FFA start (and kit apply): full health, full hunger bar, zero hidden saturation.
     * Attribute values are left untouched.
     */
    public static void applyCombatStart(Player player, double maxHealth) {
        if (player == null) {
            return;
        }
        clearCombatState(player);
        refillHealth(player);
        player.setFoodLevel(20);
        player.setSaturation(0f);
        player.setExhaustion(0f);
    }
}
