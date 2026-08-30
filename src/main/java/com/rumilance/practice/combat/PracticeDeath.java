package com.rumilance.practice.combat;

import com.rumilance.practice.model.KitDefinition;
import org.bukkit.EntityEffect;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Practice deaths never use the vanilla death screen. A killing blow is cancelled, then
 * the victim is topped up and stripped  Eunless a totem of undying in hand absorbs it.
 *
 * <p>Totems are popped <strong>manually</strong> (not left to vanilla) so every lethal
 * {@link EntityDamageEvent.DamageCause} behaves the same and other listeners (void rescue,
 * fake-death) cannot swallow the pop.</p>
 */
public final class PracticeDeath {

    /** Vanilla totem: Regeneration II 45s, Fire Resistance 40s, Absorption IV 5s. */
    private static final int TOTEM_REGEN_TICKS = 45 * 20;
    private static final int TOTEM_FIRE_RES_TICKS = 40 * 20;
    private static final int TOTEM_ABSORPTION_TICKS = 5 * 20;
    /** Ignore MONITOR lethal fallback this long after a successful resurrect (ticks). */
    private static final long RESURRECT_GRACE_MS = 2500L;

    private static final java.util.concurrent.ConcurrentHashMap<java.util.UUID, Long> recentResurrectAt =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<java.util.UUID, Long> pendingHandTotemUntil =
            new java.util.concurrent.ConcurrentHashMap<>();

    private PracticeDeath() {
    }

    /** Mark a successful totem / {@link org.bukkit.event.entity.EntityResurrectEvent}. */
    public static void markResurrected(Player player) {
        if (player != null) {
            recentResurrectAt.put(player.getUniqueId(), System.currentTimeMillis());
            pendingHandTotemUntil.remove(player.getUniqueId());
        }
    }

    /**
     * True when a totem resurrect just happened  Ecallers must not treat HP&lt;=0 frames
     * or the next lethal as a practice fake-death (that was healing victims to full).
     */
    public static boolean isInResurrectGrace(Player player) {
        if (player == null) {
            return false;
        }
        Long at = recentResurrectAt.get(player.getUniqueId());
        if (at == null) {
            return false;
        }
        if (System.currentTimeMillis() - at > RESURRECT_GRACE_MS) {
            recentResurrectAt.remove(player.getUniqueId(), at);
            return false;
        }
        return true;
    }

    public static void clearResurrectGrace(Player player) {
        if (player != null) {
            recentResurrectAt.remove(player.getUniqueId());
        }
    }

    public static void markPendingHandTotem(Player player) {
        if (player != null) {
            pendingHandTotemUntil.put(player.getUniqueId(), System.currentTimeMillis() + 50L);
        }
    }

    public static boolean isHoldingTotem(Player player) {
        if (player == null) {
            return false;
        }
        if (isTotem(player.getInventory().getItemInOffHand())
                || isTotem(player.getInventory().getItemInMainHand())) {
            return true;
        }
        Long until = pendingHandTotemUntil.get(player.getUniqueId());
        if (until == null) {
            return false;
        }
        if (System.currentTimeMillis() > until) {
            pendingHandTotemUntil.remove(player.getUniqueId(), until);
            return false;
        }
        return true;
    }

    public static boolean isTotem(ItemStack stack) {
        return stack != null && stack.getType() == Material.TOTEM_OF_UNDYING;
    }

    /** Remaining HP after this hit, including absorption. */
    public static double remainingAfter(double health, double absorption, double finalDamage) {
        return health + absorption - finalDamage;
    }

    public static double remainingAfter(Player player, EntityDamageEvent event) {
        return remainingAfter(player.getHealth(), player.getAbsorptionAmount(), event.getFinalDamage());
    }

    public static boolean wouldDie(double health, double absorption, double finalDamage) {
        return remainingAfter(health, absorption, finalDamage) <= 0.0d;
    }

    public static boolean wouldDie(Player player, EntityDamageEvent event) {
        return wouldDie(player.getHealth(), player.getAbsorptionAmount(), event.getFinalDamage());
    }

    /**
     * When this hit would kill and the kit allows totems, consumes one from offhand (then
     * mainhand), cancels the damage, and applies vanilla totem effects. Works for every
     * {@link EntityDamageEvent.DamageCause}.
     *
     * <p>Prefer {@link #shouldDeferTotemToVanilla} for combat hits so enchantments
     * (e.g. mace Wind Burst) and {@link org.bukkit.event.entity.EntityResurrectEvent}
     * run through vanilla.</p>
     *
     * @return {@code true} when a totem was popped
     */
    public static boolean tryPopTotem(Player player, KitDefinition kit, EntityDamageEvent event) {
        if (player == null || event == null) {
            return false;
        }
        if (kit != null && !kit.totem()) {
            return false;
        }
        if (!wouldDie(player, event) || !isHoldingTotem(player)) {
            return false;
        }
        pendingHandTotemUntil.remove(player.getUniqueId());
        event.setCancelled(true);
        event.setDamage(0);
        consumeHeldTotem(player);
        applyTotemActivation(player);
        return true;
    }

    /**
     * Lethal hit while holding a totem: let vanilla consume it so attacker enchantments
     * (Wind Burst, etc.) and {@link org.bukkit.event.entity.EntityResurrectEvent} still fire.
     * Callers must skip {@link #tryPopTotem} and {@code handleLethal} for this tick, then
     * verify survival on {@link org.bukkit.event.EventPriority#MONITOR}.
     */
    public static boolean shouldDeferTotemToVanilla(Player player, KitDefinition kit, EntityDamageEvent event) {
        if (player == null || event == null) {
            return false;
        }
        if (kit != null && !kit.totem()) {
            return false;
        }
        return wouldDie(player, event) && isHoldingTotem(player);
    }

    /** @deprecated use {@link #tryPopTotem} */
    @Deprecated
    public static boolean letVanillaTotemPop(Player player, KitDefinition kit, EntityDamageEvent event) {
        return tryPopTotem(player, kit, event);
    }

    /** Offhand first, then main hand  Esame priority as vanilla. */
    static void consumeHeldTotem(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack off = inventory.getItemInOffHand();
        if (isTotem(off)) {
            decrementOrClear(inventory, off, true);
            return;
        }
        ItemStack main = inventory.getItemInMainHand();
        if (isTotem(main)) {
            decrementOrClear(inventory, main, false);
        }
    }

    private static void decrementOrClear(PlayerInventory inventory, ItemStack stack, boolean offhand) {
        int next = stack.getAmount() - 1;
        if (next <= 0) {
            if (offhand) {
                inventory.setItemInOffHand(null);
            } else {
                inventory.setItemInMainHand(null);
            }
        } else {
            stack.setAmount(next);
        }
    }

    static void applyTotemActivation(Player player) {
        markResurrected(player);
        player.setFireTicks(0);
        player.setFreezeTicks(0);
        // Hard cap  Enever leave the victim at max HP after a totem pop.
        double max = Math.max(1.0d, player.getMaxHealth());
        player.setHealth(Math.min(1.0d, max));
        player.setAbsorptionAmount(0.0d);
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, TOTEM_REGEN_TICKS, 1, false, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, TOTEM_FIRE_RES_TICKS, 0, false, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, TOTEM_ABSORPTION_TICKS, 3, false, true, true));
        player.playEffect(EntityEffect.TOTEM_RESURRECT);
    }
}
