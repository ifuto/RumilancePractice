package com.rumilance.practice.kit;

import java.util.List;

/**
 * Canonical English category ids plus load aliases for older Japanese YAML/DB keys.
 */
public final class CategoryKeys {

    public static final List<String> PRESET = List.of("Armor", "Gear", "Potions", "Consumables");
    public static final List<String> EKIT = List.of("Weapons/Armor", "Offhand", "Blocks", "Potions");

    private CategoryKeys() {
    }

    public static String canonicalPreset(String raw) {
        if (raw == null || raw.isBlank()) {
            return PRESET.getFirst();
        }
        return switch (raw) {
            case "防具", "Armor" -> "Armor";
            case "装備", "Gear" -> "Gear";
            case "ポーション", "Potions" -> "Potions";
            case "消耗品", "Consumables" -> "Consumables";
            default -> raw;
        };
    }

    public static String canonicalEkit(String raw) {
        if (raw == null || raw.isBlank()) {
            return EKIT.getFirst();
        }
        return switch (raw) {
            case "武器/防具", "Weapons/Armor" -> "Weapons/Armor";
            case "サブアイテム", "Offhand" -> "Offhand";
            case "ブロック", "Blocks" -> "Blocks";
            case "ポーション", "Potions" -> "Potions";
            default -> raw;
        };
    }

    /** YAML keys to try when loading a canonical preset category (English first). */
    public static List<String> presetLoadKeys(String canonical) {
        return switch (canonicalPreset(canonical)) {
            case "Armor" -> List.of("Armor", "防具");
            case "Gear" -> List.of("Gear", "装備");
            case "Potions" -> List.of("Potions", "ポーション");
            case "Consumables" -> List.of("Consumables", "消耗品");
            default -> List.of(canonical);
        };
    }

    /** YAML keys to try when loading a canonical original-kit category. */
    public static List<String> ekitLoadKeys(String canonical) {
        return switch (canonicalEkit(canonical)) {
            case "Weapons/Armor" -> List.of("Weapons/Armor", "武器/防具");
            case "Offhand" -> List.of("Offhand", "サブアイテム");
            case "Blocks" -> List.of("Blocks", "ブロック");
            case "Potions" -> List.of("Potions", "ポーション");
            default -> List.of(canonical);
        };
    }

    public static boolean isPresetPotion(String category) {
        return "Potions".equals(canonicalPreset(category));
    }

    public static boolean isEkitPotion(String category) {
        return "Potions".equals(canonicalEkit(category));
    }

    public static boolean isEkitWeapons(String category) {
        return "Weapons/Armor".equals(canonicalEkit(category));
    }
}
