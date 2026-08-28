package com.rumilance.practice.kit;

import com.rumilance.practice.util.ItemSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Official kit-editor helpers: keep the in-memory rearrange across GUI re-renders, and compare
 * layouts by material+amount so players may only shuffle, not add/remove items.
 */
public final class KitLayoutContents {

    private KitLayoutContents() {
    }

    /**
     * Prefer the session's already-swapped layout. Reloading from disk on every render used to
     * wipe rearranges before Save could see them.
     */
    public static ItemStack[] retainOrLoad(ItemStack[] sessionLayout, ItemStack[] freshlyLoaded) {
        if (com.rumilance.practice.guard.PracticeGuards.hasValidEditorLayout(sessionLayout)) {
            return sessionLayout;
        }
        return freshlyLoaded;
    }

    public static boolean sameContents(ItemStack[] a, ItemStack[] b) {
        return fingerprints(a).equals(fingerprints(b));
    }

    /** Same as {@link #sameContents} but ignores display names and armor trim metadata. */
    public static boolean sameContentsIgnoringCosmetics(ItemStack[] a, ItemStack[] b) {
        return cosmeticFingerprints(a).equals(cosmeticFingerprints(b));
    }

    private static List<String> cosmeticFingerprints(ItemStack[] items) {
        if (items == null) {
            return List.of();
        }
        ItemStack[] stripped = items.clone();
        for (int i = 0; i < stripped.length; i++) {
            stripped[i] = stripCosmeticMeta(stripped[i]);
        }
        return fingerprints(stripped);
    }

    private static ItemStack stripCosmeticMeta(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return item;
        }
        ItemStack copy = item.clone();
        ItemMeta meta = copy.getItemMeta();
        if (meta == null) {
            return copy;
        }
        meta.displayName(null);
        if (meta instanceof org.bukkit.inventory.meta.ArmorMeta armorMeta) {
            armorMeta.setTrim(null);
            copy.setItemMeta(armorMeta);
        } else {
            copy.setItemMeta(meta);
        }
        return copy;
    }

    private static List<String> fingerprints(ItemStack[] items) {
        List<String> list = new ArrayList<>();
        if (items == null) {
            return list;
        }
        for (ItemStack item : items) {
            String fp = fingerprint(item);
            if (fp != null) {
                list.add(fp);
            }
        }
        list.sort(Comparator.naturalOrder());
        return list;
    }

    private static String fingerprint(ItemStack item) {
        if (item == null || item.getType().isAir() || isPlaceholder(item)) {
            return null;
        }
        String encoded = ItemSerializer.singleToBase64(item);
        if (encoded != null && !encoded.isBlank()) {
            return encoded;
        }
        String enchants = item.getEnchantments().entrySet().stream()
                .sorted(Comparator.comparing(e -> e.getKey().getKey().toString()))
                .map(e -> e.getKey().getKey() + "=" + e.getValue())
                .collect(Collectors.joining(","));
        return item.getType().name() + ":" + item.getAmount() + ":{" + enchants + "}";
    }

    /** @deprecated use {@link #fingerprints(ItemStack[])} internally */
    public static List<String> canonicalize(ItemStack[] items) {
        return fingerprints(items);
    }

    /** Empty GUI panes must never become real kit items. */
    public static boolean isPlaceholder(ItemStack item) {
        if (item == null) {
            return false;
        }
        Material type = item.getType();
        return type == Material.GRAY_STAINED_GLASS_PANE
                || type == Material.CYAN_STAINED_GLASS_PANE
                || type == Material.LIGHT_GRAY_STAINED_GLASS_PANE
                || type == Material.WHITE_STAINED_GLASS_PANE
                || type == Material.LIGHT_BLUE_STAINED_GLASS_PANE
                || type == Material.BLACK_STAINED_GLASS_PANE;
    }

    public static void stripPlaceholders(ItemStack[] items) {
        if (items == null) {
            return;
        }
        for (int i = 0; i < items.length; i++) {
            if (isPlaceholder(items[i])) {
                items[i] = null;
            }
        }
    }
}
