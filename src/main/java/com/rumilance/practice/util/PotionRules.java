package com.rumilance.practice.util;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.List;
import java.util.Map;

/**
 * Vanilla-survival-respecting potion options for the OrPlusGUI potion picker.
 */
public final class PotionRules {

    public record Option(String effectKey, String display, int maxLevel, boolean extendable) {
    }

    private static final List<Option> OPTIONS = List.of(
            new Option("speed", "スピード", 2, true),
            new Option("slowness", "鈍化", 1, true),
            new Option("strength", "力", 2, true),
            new Option("instant_health", "回復", 2, false),
            new Option("instant_damage", "負傷", 2, false),
            new Option("jump_boost", "跳躍", 2, true),
            new Option("regeneration", "再生", 2, true),
            new Option("fire_resistance", "耐火", 1, true),
            new Option("water_breathing", "水中呼吸", 1, true),
            new Option("invisibility", "透明化", 1, true),
            new Option("night_vision", "暗視", 1, true),
            new Option("weakness", "弱体化", 1, true),
            new Option("poison", "毒", 2, true),
            new Option("slow_falling", "低速落下", 1, true),
            new Option("turtle_master", "亀の達人", 2, true)
    );

    private static final Map<String, String> POTION_TYPE_TO_EFFECT = Map.ofEntries(
            Map.entry("SWIFTNESS", "speed"),
            Map.entry("SLOWNESS", "slowness"),
            Map.entry("STRENGTH", "strength"),
            Map.entry("HEALING", "instant_health"),
            Map.entry("HARMING", "instant_damage"),
            Map.entry("LEAPING", "jump_boost"),
            Map.entry("REGENERATION", "regeneration"),
            Map.entry("FIRE_RESISTANCE", "fire_resistance"),
            Map.entry("WATER_BREATHING", "water_breathing"),
            Map.entry("INVISIBILITY", "invisibility"),
            Map.entry("NIGHT_VISION", "night_vision"),
            Map.entry("WEAKNESS", "weakness"),
            Map.entry("POISON", "poison"),
            Map.entry("SLOW_FALLING", "slow_falling"),
            Map.entry("TURTLE_MASTER", "turtle_master")
    );

    private PotionRules() {
    }

    public static List<Option> options() {
        return OPTIONS;
    }

    public static Option option(String effectKey) {
        for (Option option : OPTIONS) {
            if (option.effectKey().equals(effectKey)) {
                return option;
            }
        }
        return OPTIONS.get(0);
    }

    public static String variantLabel(String variant) {
        return switch (variant) {
            case "splash" -> "スプラッシュ";
            case "lingering" -> "残留";
            default -> "飲用";
        };
    }

    /** Builds a functional potion item (base water + custom effect), vanilla-ish stats. */
    public static ItemStack buildPotion(String effectKey, int level, boolean extended, String variant) {
        Material material = switch (variant) {
            case "splash" -> Material.SPLASH_POTION;
            case "lingering" -> Material.LINGERING_POTION;
            default -> Material.POTION;
        };
        ItemStack stack = new ItemStack(material);
        PotionMeta meta = (PotionMeta) stack.getItemMeta();
        meta.setBasePotionType(PotionType.WATER);
        PotionEffectType type = Registry.POTION_EFFECT_TYPE.get(NamespacedKey.minecraft(effectKey));
        if (type != null) {
            int duration = durationTicks(effectKey, level, extended);
            meta.addCustomEffect(new PotionEffect(type, duration, Math.max(0, level - 1), false, true, true), true);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    private static int durationTicks(String effectKey, int level, boolean extended) {
        boolean instant = effectKey.equals("instant_health") || effectKey.equals("instant_damage");
        if (instant) {
            return 1;
        }
        int seconds = switch (effectKey) {
            case "slowness", "weakness", "poison" -> 90;
            case "regeneration" -> 45;
            case "turtle_master" -> 20;
            default -> 180;
        };
        if (extended) {
            seconds = switch (effectKey) {
                case "slowness", "weakness" -> 240;
                case "regeneration", "poison" -> 120;
                case "turtle_master" -> 40;
                default -> 480;
            };
        }
        if (level >= 2) {
            seconds = switch (effectKey) {
                case "speed", "strength", "jump_boost" -> 90;
                case "regeneration", "poison" -> 22;
                case "turtle_master" -> 40;
                default -> seconds;
            };
        }
        return seconds * 20;
    }

    /** Extracts the effect key from a potion item (for /ekitadmin deposit). */
    public static String effectOf(ItemStack item) {
        if (item == null || !(item.getItemMeta() instanceof PotionMeta meta)) {
            return null;
        }
        if (!meta.getCustomEffects().isEmpty()) {
            return meta.getCustomEffects().get(0).getType().getKey().getKey();
        }
        try {
            PotionType base = meta.getBasePotionType();
            if (base != null) {
                return POTION_TYPE_TO_EFFECT.get(base.name());
            }
        } catch (Exception ignored) {
            // fall through
        }
        return null;
    }
}
