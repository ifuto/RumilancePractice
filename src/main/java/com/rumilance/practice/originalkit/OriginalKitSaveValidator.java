package com.rumilance.practice.originalkit;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.block.ShulkerBox;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Strict validation applied whenever an original kit is saved. The rules mirror the operator
 * spec for the physical kit-building room:
 *
 * <ul>
 *   <li>No <strong>lingering/ambient potion effects</strong> on the player when saving.</li>
 *   <li>Command blocks (3) and command-block minecarts (3) → reject AND kick (hard exploit).</li>
 *   <li>Admin/technical items (debug stick, light block, structure blocks, barrier, jigsaw,
 *       command items, spawners, etc.) → reject AND ban.</li>
 *   <li>TNT max 1 stack (64). Spawn eggs: only CREEPER allowed, max 2 stacks (128).</li>
 *   <li>Item frames forbidden. Minecarts other than TNT-minecart forbidden.</li>
 *   <li>Placeable blocks restricted to an allow-list; shulker boxes max 2.</li>
 *   <li>Impossible enchantment levels and impossible potion effects/durations/combinations rejected.</li>
 * </ul>
 *
 * <p>The result distinguishes a plain rejection (return to editing) from a kick/ban escalation.</p>
 */
public final class OriginalKitSaveValidator {

    public enum Severity { OK, REJECT, KICK, BAN }

    public record Result(Severity severity, String reason) {
        public boolean ok() {
            return severity == Severity.OK;
        }
    }

    private OriginalKitSaveValidator() {
    }

    // Blocks the kit rooms allow players to build with (placeable). Everything else placeable is NG.
    private static final Set<String> ALLOWED_BLOCKS = Set.of(
            "OAK_PLANKS", "OAK_SLAB", "OAK_STAIRS", "OAK_FENCE", "OAK_FENCE_GATE",
            "OAK_BUTTON", "OAK_PRESSURE_PLATE", "OAK_DOOR", "OAK_TRAPDOOR", "OAK_SIGN",
            "COBBLESTONE", "COBBLESTONE_SLAB", "COBBLESTONE_STAIRS", "COBBLESTONE_WALL",
            "COBBLESTONE_STAIRS",
            "COBWEB",
            "OBSIDIAN",
            "OAK_LOG", "OAK_WOOD", "STRIPPED_OAK_LOG", "STRIPPED_OAK_WOOD",
            "RESPAWN_ANCHOR",
            "GLOWSTONE",
            "RAIL", "POWERED_RAIL", "DETECTOR_RAIL", "ACTIVATOR_RAIL",
            "TNT"
    );

    // Technical / admin items that must never be saved — escalation is BAN.
    private static final Set<String> BANNED_ITEMS = Set.of(
            "DEBUG_STICK",
            "LIGHT", "LIGHT_BLOCK",
            "STRUCTURE_BLOCK", "STRUCTURE_VOID",
            "JIGSAW",
            "BARRIER",
            "COMMAND_BLOCK", "CHAIN_COMMAND_BLOCK", "REPEATING_COMMAND_BLOCK",
            "COMMAND_BLOCK_MINECART", "CHAIN_COMMAND_BLOCK_MINECART", "REPEATING_COMMAND_BLOCK_MINECART",
            "MINECART_WITH_COMMAND_BLOCK",
            "SPAWNER",
            "KNOWLEDGE_BOOK",
            "COMMAND_BLOCK_SPAWN_EGG",
            "BEDROCK",
            "END_PORTAL_FRAME",
            "REINFORCED_DEEPSLATE",
            "BUDDING_AMETHYST",
            "FROGSPAWN",
            "INFESTED_COBBLESTONE"
    );

    // Command items (hard exploit) → KICK.
    private static final Set<String> KICK_ITEMS = Set.of(
            "COMMAND_BLOCK", "CHAIN_COMMAND_BLOCK", "REPEATING_COMMAND_BLOCK",
            "COMMAND_BLOCK_MINECART",
            "CHAIN_COMMAND_BLOCK_MINECART",
            "REPEATING_COMMAND_BLOCK_MINECART"
    );

    private static final int TNT_MAX = 64;
    private static final int CREEPER_EGG_MAX = 128;
    private static final int SHULKER_MAX = 2;

    public static Result validate(java.util.Collection<ItemStack> contents) {
        int tnt = 0;
        int creeperEggs = 0;
        int shulkers = 0;

        for (ItemStack item : contents) {
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            String type = item.getType().name();

            // --- Hard exploits: kick first (overrides ban list ordering). ---
            if (KICK_ITEMS.contains(type)) {
                return new Result(Severity.KICK, "Illegal command-block item in kit: " + type);
            }
            // --- Admin/technical items: ban. ---
            if (BANNED_ITEMS.contains(type)) {
                return new Result(Severity.BAN, "Forbidden admin item in kit: " + type);
            }

            // --- Spawn eggs: only creeper, capped at 2 stacks. ---
            if (type.endsWith("_SPAWN_EGG")) {
                if (!"CREEPER_SPAWN_EGG".equals(type)) {
                    return new Result(Severity.REJECT, "Only creeper spawn eggs are allowed (found "
                            + pretty(type) + ").");
                }
                creeperEggs += item.getAmount();
                if (creeperEggs > CREEPER_EGG_MAX) {
                    return new Result(Severity.REJECT, "Creeper spawn eggs limited to 2 stacks (128).");
                }
                continue;
            }

            // --- TNT cap. ---
            if (type.equals("TNT")) {
                tnt += item.getAmount();
                if (tnt > TNT_MAX) {
                    return new Result(Severity.REJECT, "TNT is limited to 1 stack (64).");
                }
                continue;
            }

            // --- Item frames forbidden. ---
            if (type.equals("ITEM_FRAME") || type.equals("GLOW_ITEM_FRAME")) {
                return new Result(Severity.REJECT, "Item frames are not allowed in kits.");
            }

            // --- Minecarts: only TNT minecart. ---
            if (type.endsWith("_MINECART") && !type.equals("TNT_MINECART")) {
                return new Result(Severity.REJECT, "Only TNT minecarts are allowed (found " + pretty(type) + ").");
            }

            // --- Shulker cap + content validation. ---
            if (isShulker(item.getType())) {
                shulkers += item.getAmount();
                if (shulkers > SHULKER_MAX) {
                    return new Result(Severity.REJECT, "Shulker boxes are limited to 2.");
                }
                Result inside = validateShulker(item);
                if (!inside.ok()) {
                    return inside;
                }
                continue;
            }

            // --- Placeable blocks must be on the allow-list. ---
            if (item.getType().isBlock() && !ALLOWED_BLOCKS.contains(type)) {
                // Equipment / functional blocks the player wears or uses as a kit are fine
                // (e.g. shields, armor). Only reject placeable blocks not in the list.
                if (isPlaceableBlock(item.getType())) {
                    return new Result(Severity.REJECT, "This block is not allowed in a kit: " + pretty(type) + ".");
                }
            }

            // --- Enchantment sanity. ---
            Result ench = checkEnchants(item);
            if (!ench.ok()) {
                return ench;
            }

            // --- Potion sanity. ---
            if (item.getItemMeta() instanceof PotionMeta potion) {
                Result pot = checkPotion(potion);
                if (!pot.ok()) {
                    return pot;
                }
            }
        }
        return new Result(Severity.OK, null);
    }

    private static Result validateShulker(ItemStack shulker) {
        if (!(shulker.getItemMeta() instanceof BlockStateMeta bsm)
                || !(bsm.getBlockState() instanceof ShulkerBox box)) {
            return new Result(Severity.OK, null);
        }
        java.util.List<ItemStack> inner = new java.util.ArrayList<>();
        for (ItemStack it : box.getInventory().getContents()) {
            if (it != null && it.getType() != Material.AIR) {
                inner.add(it);
            }
        }
        // Nested shulkers are impossible in vanilla; block them explicitly.
        for (ItemStack it : inner) {
            if (isShulker(it.getType())) {
                return new Result(Severity.REJECT, "Nested shulker boxes are not allowed.");
            }
        }
        return validate(inner);
    }

    private static Result checkEnchants(ItemStack item) {
        try {
            for (var entry : item.getEnchantments().entrySet()) {
                Enchantment ench = entry.getKey();
                int level = entry.getValue();
                if (ench == null) {
                    continue;
                }
                int max = Math.max(1, ench.getMaxLevel());
                // Vanilla survival caps enchantments; a level above max (allowing the standard
                // +1 anvil overshoot, i.e. max+1) is impossible and rejected.
                if (level > max + 1 || level < 1) {
                    return new Result(Severity.REJECT, "Impossible enchantment level: "
                            + ench.getKey().getKey() + " " + level);
                }
            }
        } catch (RuntimeException e) {
            return new Result(Severity.REJECT, "Could not read enchantments on " + pretty(item.getType()));
        }
        return new Result(Severity.OK, null);
    }

    private static Result checkPotion(PotionMeta potion) {
        try {
            Set<String> seen = new HashSet<>();
            for (PotionEffect effect : potion.getCustomEffects()) {
                PotionEffectType type = effect.getType();
                if (type == null) {
                    continue;
                }
                String key = type.getKey().getKey();
                if (!seen.add(key)) {
                    return new Result(Severity.REJECT, "Duplicate potion effect: " + key);
                }
                int amp = effect.getAmplifier();
                // Amplifier above 9 (level X) is not legitimately obtainable.
                if (amp > 9 || amp < 0) {
                    return new Result(Severity.REJECT, "Impossible potion level: " + key + " " + (amp + 1));
                }
                int durSec = effect.getDuration() / 20;
                // ~10 minute cap for crafted potions; longer is unobtainable without commands.
                if (durSec > 600) {
                    return new Result(Severity.REJECT, "Potion effect too long: " + key + " (" + durSec + "s)");
                }
                // Some effects never appear together legitimately (e.g. instant heal + harm).
                if (conflictingEffects(seen)) {
                    return new Result(Severity.REJECT, "Impossible potion effect combination.");
                }
            }
        } catch (RuntimeException e) {
            return new Result(Severity.REJECT, "Could not read potion effects.");
        }
        return new Result(Severity.OK, null);
    }

    private static boolean conflictingEffects(Set<String> seen) {
        boolean heal = seen.contains("instant_health");
        boolean harm = seen.contains("instant_damage");
        boolean strength = seen.contains("strength");
        boolean weakness = seen.contains("weakness");
        boolean speed = seen.contains("speed");
        boolean slow = seen.contains("slowness");
        boolean luck = seen.contains("luck");
        boolean unluck = seen.contains("unluck");
        return (heal && harm) || (strength && weakness) || (speed && slow) || (luck && unluck);
    }

    /** True if the player currently carries ambient potion effects (must clear before saving). */
    public static boolean hasResidualEffects(org.bukkit.entity.Player player) {
        for (PotionEffect effect : player.getActivePotionEffects()) {
            // Ambient = beacon/ambient particles; but spec says ANY residual potion blocks save.
            if (effect != null) {
                return true;
            }
        }
        return false;
    }

    private static boolean isShulker(Material material) {
        String n = material.name();
        return n.endsWith("SHULKER_BOX");
    }

    /**
     * Placeable blocks are solid block materials. Armor/tool/consumable items return false even
     * though Material.isBlock() can be true for some wearable blocks (e.g. skulls) — those are
     * handled as equipment and allowed.
     */
    private static boolean isPlaceableBlock(Material material) {
        if (!material.isBlock()) {
            return false;
        }
        String n = material.name();
        // Wearable/equipment blocks are kit gear, not building blocks.
        if (n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS")
                || n.endsWith("_BOOTS") || n.equals("TURTLE_HELMET") || n.equals("SHIELD")) {
            return false;
        }
        return true;
    }

    private static String pretty(String type) {
        return type.toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private static String pretty(Material material) {
        return pretty(material.name());
    }

    public static Component feedback(Result result) {
        NamedTextColor color = switch (result.severity()) {
            case KICK -> NamedTextColor.RED;
            case BAN -> NamedTextColor.DARK_RED;
            default -> NamedTextColor.RED;
        };
        return Component.text(result.reason() == null ? "" : result.reason(), color);
    }

    // Suppress unused warnings for EntityType import kept for future spawn-egg mapping.
    @SuppressWarnings("unused")
    private static EntityType eggTypeHint() {
        return EntityType.CREEPER;
    }
}
