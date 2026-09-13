package com.rumilance.practice.ffa;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.potion.PotionEffect;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.List;
import java.util.Locale;

/**
 * Crystal FFA quality-of-life commands — usable only while OUT of combat, and only in an
 * FFA arena whose kit is THE declared crystal FFA kit (the KIT1..9 system only exists
 * there). Every command in this family:
 *
 * <ul>
 *   <li>{@code /regear} — tops storage + hotbar back up to the last selected KIT slot's
 *       contents; equipment (armor/shield) and totems are NOT replenished,</li>
 *   <li>{@code /repair} — full durability on the held item,</li>
 *   <li>{@code /heal} — max health, fire out, every potion effect removed,</li>
 *   <li>{@code /k1} .. {@code /k9} — replace the inventory with that KIT slot's contents
 *       and remember it as the player's crystal FFA loadout.</li>
 * </ul>
 *
 * <p>The admin FFA command gate ({@code /practiceadmin ffacommand}) is independent: when
 * it is ON, these labels must be whitelisted like any other command.</p>
 */
public final class FfaCrystalCommands implements CommandExecutor, TabCompleter {

    private static final Component NOT_IN_FFA = Component
            .text("This command only works in FFA.", NamedTextColor.RED);
    private static final Component NOT_CRYSTAL = Component
            .text("This command only works in the crystal FFA.", NamedTextColor.RED);
    private static final Component IN_COMBAT = Component
            .text("You can't use commands while in combat.", NamedTextColor.RED);

    private final FfaService ffaService;

    public FfaCrystalCommands(FfaService ffaService) {
        this.ffaService = ffaService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (!ffaService.isInFfa(player.getUniqueId())) {
            player.sendMessage(NOT_IN_FFA);
            return true;
        }
        if (ffaService.crystalFfaKitOf(player) == null) {
            player.sendMessage(NOT_CRYSTAL);
            return true;
        }
        if (ffaService.inCombat(player.getUniqueId())) {
            player.sendMessage(IN_COMBAT);
            return true;
        }
        switch (name) {
            case "regear" -> ffaService.regearCrystal(player);
            case "repair" -> repair(player);
            case "heal" -> heal(player);
            default -> {
                // k1..k9 — the digits after the 'k' are the KIT slot.
                String digits = name.substring(1);
                try {
                    int variant = Integer.parseInt(digits);
                    ffaService.applyCrystalVariant(player, variant);
                } catch (NumberFormatException e) {
                    // unreachable via plugin.yml labels
                }
            }
        }
        return true;
    }

    /** Held item back to full durability (main hand). */
    private void repair(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || held.getType().isAir()) {
            return;
        }
        if (held.getItemMeta() instanceof Damageable damageable) {
            damageable.setDamage(0);
            held.setItemMeta(damageable);
            ffaService.playSelect(player);
        }
    }

    /** Max health, fire out, every potion effect (positive or negative) removed. */
    private void heal(Player player) {
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        player.setHealth(maxHealth == null ? 20.0d : maxHealth.getValue());
        player.setFireTicks(0);
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        ffaService.playSelect(player);
    }

    /** No arguments anywhere in this family — swallow vanilla's player-name completion. */
    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender,
                                                         @NotNull Command command,
                                                         @NotNull String alias,
                                                         @NotNull String[] args) {
        return List.of();
    }
}
