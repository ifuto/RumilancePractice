package com.rumilance.practice.kit;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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

    /**
     * True when both layouts have the same combat items (material, amount, enchants, potions).
     * Armor trim, display name, lore, and editor PDC are ignored so cosmetic-only edits can save.
     */
    public static boolean sameContentsIgnoringCosmetics(ItemStack[] a, ItemStack[] b) {
        return fingerprints(a).equals(fingerprints(b));
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
        StringBuilder sb = new StringBuilder(item.getType().name());
        sb.append(':').append(item.getAmount());
        item.getEnchantments().entrySet().stream()
                .sorted(Comparator.comparing(e -> e.getKey().getKey().toString()))
                .forEach(e -> sb.append('|').append(e.getKey().getKey()).append('=').append(e.getValue()));
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof org.bukkit.inventory.meta.EnchantmentStorageMeta book) {
            book.getStoredEnchants().entrySet().stream()
                    .sorted(Comparator.comparing(e -> e.getKey().getKey().toString()))
                    .forEach(e -> sb.append("|stored=")
                            .append(e.getKey().getKey()).append('=').append(e.getValue()));
        }
        if (meta instanceof org.bukkit.inventory.meta.PotionMeta potion) {
            sb.append("|potion=").append(potion.getBasePotionType());
            potion.getCustomEffects().stream()
                    .sorted(Comparator.comparing(e -> e.getType().getKey().toString()))
                    .forEach(e -> sb.append("|fx=")
                            .append(e.getType().getKey())
                            .append('@').append(e.getAmplifier())
                            .append('/').append(e.getDuration()));
        }
        return sb.toString();
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
