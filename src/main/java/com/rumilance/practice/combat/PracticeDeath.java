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

    public static void clearPendingHandTotem(Player player) {
        if (player != null) {
            pendingHandTotemUntil.remove(player.getUniqueId());
        }
    }

    /**
     * True when a totem sits in the main hand or the offhand right now — the only two slots
     * vanilla resurrects from. The pending-swap window
     * ({@link #markPendingHandTotem}) is deliberately NOT part of this check: it may only
     * keep a lethal hit alive for one extra evaluation, never invent a totem that is not in
     * a hand (a free pop with nothing consumed would be an exploit).
     */
    public static boolean hasTotemInHand(Player player) {
        if (player == null) {
            return false;
        }
        return isTotem(player.getInventory().getItemInOffHand())
                || isTotem(player.getInventory().getItemInMainHand());
    }

    public static boolean isHoldingTotem(Player player) {
        if (player == null) {
            return false;
        }
        if (hasTotemInHand(player)) {
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
     * <p>This is the ONLY totem path in practice combat: the pop is executed by the plugin, so
     * no other listener (void rescue, fake-death, explosion attribution, another plugin
     * cancelling the hit) can swallow it and leave a totem holder dead. A totem is only ever
     * consumed from a hand — {@link #hasTotemInHand} — so a pending swap window can never
     * grant a free pop.</p>
     *
     * @return {@code true} when a totem was popped
     */
    public static boolean tryPopTotem(Player player, KitDefinition kit, EntityDamageEvent event) {
        if (player == null || event == null) {
            return false;
        }
        if (!canPopTotem(player, kit)) {
            return false;
        }
        if (!wouldDie(player, event)) {
            return false;
        }
        // Cancel FIRST: nothing may apply the lethal amount while we pop (and no other
        // listener may see an uncancelled lethal and run its own death handling).
        event.setCancelled(true);
        event.setDamage(0);
        return popTotemNow(player);
    }

    /**
     * Event-less totem gate for the lethal handlers ({@code MatchService.handleLethal},
     * {@code FfaService.handleLethal}, practice death). A lethal outcome that reaches those
     * while the victim still holds a totem is popped here instead, so a "dead with a totem in
     * hand" state can never be turned into a loss / kill / death screen.
     *
     * @return {@code true} when a totem was popped and the caller must treat the victim as alive
     */
    public static boolean tryPopTotem(Player player, KitDefinition kit) {
        if (player == null || !canPopTotem(player, kit)) {
            return false;
        }
        return popTotemNow(player);
    }

    /** Kit/permission gate: is a totem pop allowed for this player at all? */
    public static boolean canPopTotem(Player player, KitDefinition kit) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE
                || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
            return false;
        }
        if (kit != null && !kit.totem()) {
            return false;
        }
        return hasTotemInHand(player);
    }

    /**
     * Consumes one held totem and applies the vanilla activation. Returns {@code false} when
     * there was nothing in a hand to consume (no free pops).
     */
    private static boolean popTotemNow(Player player) {
        if (!hasTotemInHand(player)) {
            return false;
        }
        pendingHandTotemUntil.remove(player.getUniqueId());
        consumeTotemFromHand(player);
        applyTotemActivation(player);
        return true;
    }

    /**
     * Lethal hit while holding a totem: cancel the hit and pop the totem ourselves.
     *
     * @deprecated deferring to vanilla was the source of "died with a totem in hand": vanilla
     *     only resurrects when the lethal damage reaches {@code LivingEntity#die}, and practice
     *     cancels / re-routes lethal damage all over the place (void rescue, fake-death,
     *     explosion self-damage one tick later, other plugins). Use {@link #tryPopTotem}.
     */
    @Deprecated
    public static boolean shouldDeferTotemToVanilla(Player player, KitDefinition kit, EntityDamageEvent event) {
        return tryPopTotem(player, kit, event);
    }

    /** @deprecated use {@link #tryPopTotem} */
    @Deprecated
    public static boolean letVanillaTotemPop(Player player, KitDefinition kit, EntityDamageEvent event) {
        return tryPopTotem(player, kit, event);
    }

    /**
     * Offhand first, then main hand -- same priority as vanilla. Public so the death failsafe
     * ({@link TotemGuardListener}) can consume the totem that saved a cancelled death.
     *
     * @return true when a totem was consumed
     */
    public static boolean consumeTotemFromHand(Player player) {
        if (player == null || !hasTotemInHand(player)) {
            return false;
        }
        PlayerInventory inventory = player.getInventory();
        ItemStack off = inventory.getItemInOffHand();
        if (isTotem(off)) {
            decrementOrClear(inventory, off, true);
            return true;
        }
        ItemStack main = inventory.getItemInMainHand();
        if (isTotem(main)) {
            decrementOrClear(inventory, main, false);
            return true;
        }
        return false;
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

    /**
     * Vanilla totem activation: 1 HP, no fire/freeze, Regeneration II 45s, Fire Resistance 40s,
     * Absorption IV 5s and the totem particle/sound. Public for the death failsafe.
     */
    public static void applyTotemEffects(Player player) {
        applyTotemActivation(player);
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
