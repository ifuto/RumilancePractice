package com.rumilance.practice.util;

import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Vanilla-respecting enchantment rules for the OrPlusGUI enchant picker:
 * item applicability ({@code canEnchantItem}), level range, and the vanilla
 * impossible-combination conflict table.
 */
public final class EnchantmentRules {

    private static final Map<String, Set<String>> CONFLICTS = new HashMap<>();

    static {
        conflict("protection", "blast_protection", "fire_protection", "projectile_protection");
        conflict("blast_protection", "protection", "fire_protection", "projectile_protection");
        conflict("fire_protection", "protection", "blast_protection", "projectile_protection");
        conflict("projectile_protection", "protection", "blast_protection", "fire_protection");
        conflict("sharpness", "smite", "bane_of_arthropods");
        conflict("smite", "sharpness", "bane_of_arthropods");
        conflict("bane_of_arthropods", "sharpness", "smite");
        conflict("fortune", "silk_touch");
        conflict("silk_touch", "fortune");
        conflict("depth_strider", "frost_walker");
        conflict("frost_walker", "depth_strider");
        conflict("infinity", "mending");
        conflict("mending", "infinity");
        conflict("channeling", "riptide");
        conflict("riptide", "channeling", "loyalty");
        conflict("loyalty", "riptide");
        conflict("multishot", "piercing");
        conflict("piercing", "multishot");
        conflict("luck_of_the_sea", "lure");
        conflict("lure", "luck_of_the_sea");
    }

    private static void conflict(String key, String... others) {
        CONFLICTS.put(key, Set.of(others));
    }

    private EnchantmentRules() {
    }

    /** All enchantments that can be applied to the item, sorted by key. */
    public static List<Enchantment> applicable(ItemStack item) {
        List<Enchantment> out = new ArrayList<>();
        for (Enchantment enchantment : Registry.ENCHANTMENT) {
            try {
                if (enchantment.canEnchantItem(item)) {
                    out.add(enchantment);
                }
            } catch (Exception ignored) {
                // skip enchantments that cannot be evaluated for this item
            }
        }
        out.sort(Comparator.comparing(e -> e.getKey().getKey()));
        return out;
    }

    public static boolean conflicts(Enchantment a, Enchantment b) {
        if (a == null || b == null || a.equals(b)) {
            return false;
        }
        Set<String> set = CONFLICTS.get(a.getKey().getKey());
        return set != null && set.contains(b.getKey().getKey());
    }

    /** Vanilla validation: level range, item applicability, conflict with existing enchants. */
    public static boolean canApply(ItemStack item, Enchantment enchantment, int level, Map<Enchantment, Integer> existing) {
        if (enchantment == null) {
            return false;
        }
        if (level < 1 || level > enchantment.getMaxLevel()) {
            return false;
        }
        try {
            if (!enchantment.canEnchantItem(item)) {
                return false;
            }
        } catch (Exception ignored) {
            return false;
        }
        if (existing != null) {
            for (Enchantment other : existing.keySet()) {
                if (conflicts(enchantment, other)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Short Japanese label for common combat enchantments; falls back to the key. */
    public static String label(Enchantment enchantment) {
        if (enchantment == null) {
            return "?";
        }
        String key = enchantment.getKey().getKey();
        return switch (key) {
            case "sharpness" -> "ダメージ増加";
            case "smite" -> "アンデッド特効";
            case "bane_of_arthropods" -> "虫特効";
            case "knockback" -> "ノックバック";
            case "fire_aspect" -> "火属性";
            case "looting" -> "ドロップ増加";
            case "sweeping_edge" -> "範囲ダメージ増加";
            case "power" -> "パワー";
            case "punch" -> "パンチ";
            case "flame" -> "フレイム";
            case "infinity" -> "無限";
            case "piercing" -> "貫通";
            case "multishot" -> "マルチショット";
            case "quick_charge" -> "クイックチャージ";
            case "unbreaking" -> "耐久力";
            case "mending" -> "修繕";
            case "protection" -> "ダメージ軽減";
            case "blast_protection" -> "爆発耐性";
            case "fire_protection" -> "火炎耐性";
            case "projectile_protection" -> "飛び道具耐性";
            case "feather_falling" -> "落下耐性";
            case "respiration" -> "水中呼吸";
            case "aqua_affinity" -> "水中採掘";
            case "depth_strider" -> "水中歩行";
            case "frost_walker" -> "氷渡り";
            case "thorns" -> "棘の鎧";
            case "soul_speed" -> "ソウルスピード";
            case "swift_sneak" -> "スニーク速度";
            case "efficiency" -> "効率強化";
            case "silk_touch" -> "シルクタッチ";
            case "fortune" -> "幸運";
            case "lure" -> "宝釣り";
            case "luck_of_the_sea" -> "海の幸";
            case "loyalty" -> "忠誠";
            case "channeling" -> "召雷";
            case "riptide" -> "激流";
            case "impaling" -> "水生特効";
            default -> key;
        };
    }
}
