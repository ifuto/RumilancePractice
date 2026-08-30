package com.rumilance.practice.kit;

import com.rumilance.practice.model.KitDefinition;
import com.rumilance.practice.model.KitItemEntry;
import com.rumilance.practice.util.ItemSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.List;

/**
 * Builds a 41-slot loadout (0-35 storage, 36 helmet, 37 chest, 38 legs, 39 boots, 40 offhand)
 * and applies it without using Bukkit raw slots 36-39 ({@code setItem(38)} is the chestplate
 * slot — that is how splash potions end up "worn" on the torso).
 */
public final class KitLoadout {

    public static final int SIZE = 41;
    public static final int HELMET = 36;
    public static final int CHEST = 37;
    public static final int LEGS = 38;
    public static final int BOOTS = 39;
    public static final int OFFHAND = 40;

    private KitLoadout() {
    }

    public static ItemStack[] fromOfficial(KitDefinition kit) {
        ItemStack[] layout = new ItemStack[SIZE];
        if (kit == null) {
            return layout;
        }
        for (KitItemEntry entry : kit.items()) {
            ItemStack stack = resolveEntry(entry);
            if (stack == null) {
                continue;
            }
            int slot = entry.slot();
            if (slot >= 0 && slot < 36) {
                layout[slot] = stack;
            } else if (slot == OFFHAND) {
                layout[OFFHAND] = stack;
            } else if (slot >= HELMET && slot <= BOOTS) {
                if (armorSlotAccepts(slot, stack.getType().name())) {
                    layout[slot] = stack;
                } else {
                    placeStorage(layout, stack);
                }
            } else {
                placeStorage(layout, stack);
            }
        }
        layout[HELMET] = firstNonNull(layout[HELMET], armorItem(kit.armor().get("helmet")));
        layout[CHEST] = firstNonNull(layout[CHEST], armorItem(kit.armor().get("chestplate")));
        layout[LEGS] = firstNonNull(layout[LEGS], armorItem(kit.armor().get("leggings")));
        layout[BOOTS] = firstNonNull(layout[BOOTS], armorItem(kit.armor().get("boots")));
        return layout;
    }

    /**
     * Player layout overlay. Non-preset kits always keep every official stack (missing
     * potions / armor are filled back). Armor slots never receive potions or editor panes.
     */
    public static ItemStack[] resolve(KitDefinition kit, ItemStack[] custom) {
        ItemStack[] official = fromOfficial(kit);
        KitLayoutContents.stripPlaceholders(official);
        if (!usable(custom) || isEffectivelyEmpty(custom)) {
            return sanitize(official);
        }
        ItemStack[] out = copy41(custom);
        KitLayoutContents.stripPlaceholders(out);
        if (isEffectivelyEmpty(out)) {
            return sanitize(official);
        }
        if (kit == null || !kit.presetEnabled()) {
            fillMissingFromOfficial(out, official);
        }
        return sanitize(out);
    }

    public static void give(PlayerInventory inventory, ItemStack[] loadout) {
        if (inventory == null || loadout == null) {
            return;
        }
        inventory.clear();
        inventory.setArmorContents(new ItemStack[4]);
        inventory.setItemInOffHand(null);
        ItemStack[] safe = sanitize(copy41(loadout));
        for (int i = 0; i < 36; i++) {
            if (safe[i] != null && !safe[i].getType().isAir()) {
                inventory.setItem(i, safe[i].clone());
            }
        }
        inventory.setHelmet(cloneOrNull(safe[HELMET]));
        inventory.setChestplate(cloneOrNull(safe[CHEST]));
        inventory.setLeggings(cloneOrNull(safe[LEGS]));
        inventory.setBoots(cloneOrNull(safe[BOOTS]));
        inventory.setItemInOffHand(cloneOrNull(safe[OFFHAND]));
        // Paper 1.21+: leftover body/saddle items also render on the torso.
        clearEquipment(inventory, EquipmentSlot.BODY);
        clearEquipment(inventory, EquipmentSlot.SADDLE);
    }

    public static boolean armorSlotAccepts(int slot, String materialName) {
        if (materialName == null || materialName.isBlank()) {
            return false;
        }
        String name = materialName.toUpperCase();
        return switch (slot) {
            case HELMET -> name.endsWith("_HELMET") || name.equals("CARVED_PUMPKIN")
                    || name.endsWith("_HEAD") || name.endsWith("_SKULL");
            case CHEST -> name.endsWith("_CHESTPLATE") || name.equals("ELYTRA");
            case LEGS -> name.endsWith("_LEGGINGS");
            case BOOTS -> name.endsWith("_BOOTS");
            default -> false;
        };
    }

    public static ItemStack[] sanitize(ItemStack[] layout) {
        ItemStack[] out = copy41(layout);
        KitLayoutContents.stripPlaceholders(out);
        for (int slot = HELMET; slot <= BOOTS; slot++) {
            ItemStack worn = out[slot];
            if (worn == null || worn.getType().isAir()) {
                out[slot] = null;
                continue;
            }
            if (!armorSlotAccepts(slot, worn.getType().name())) {
                ItemStack misplaced = worn;
                out[slot] = null;
                placeStorage(out, misplaced);
            }
        }
        return out;
    }

    static boolean usable(ItemStack[] custom) {
        return custom != null && custom.length >= 36;
    }

    static boolean isEffectivelyEmpty(ItemStack[] items) {
        if (items == null) {
            return true;
        }
        for (ItemStack item : items) {
            if (item != null && !item.getType().isAir() && !KitLayoutContents.isPlaceholder(item)) {
                return false;
            }
        }
        return true;
    }

    static void fillMissingFromOfficial(ItemStack[] out, ItemStack[] official) {
        // Rearrange-only: keep the player's slot preference, always re-clone official NBT
        // so enchant/meta never "disappear" and extras from stale layouts are dropped.
        ItemStack[] rebuilt = new ItemStack[SIZE];
        boolean[] usedOfficial = new boolean[SIZE];
        for (int i = 0; i < SIZE; i++) {
            ItemStack have = i < out.length ? out[i] : null;
            if (isEmpty(have)) {
                continue;
            }
            int match = indexOfUnusedMatch(official, have, usedOfficial);
            if (match >= 0) {
                usedOfficial[match] = true;
                // Keep the saved layout stack (trim / custom NBT) — only fill empty slots from official.
                rebuilt[i] = have.clone();
            } else {
                rebuilt[i] = have.clone();
            }
        }
        for (int i = 0; i < SIZE; i++) {
            ItemStack want = i < official.length ? official[i] : null;
            if (isEmpty(want) || usedOfficial[i]) {
                continue;
            }
            if (isEmpty(rebuilt[i]) && (i < 36 || i == OFFHAND
                    || armorSlotAccepts(i, want.getType().name()))) {
                rebuilt[i] = want.clone();
                usedOfficial[i] = true;
            } else {
                placeStorage(rebuilt, want.clone());
            }
        }
        System.arraycopy(rebuilt, 0, out, 0, SIZE);
    }

    /** Prefer material + amount + enchantment map equality over loose isSimilar. */
    private static int indexOfUnusedMatch(ItemStack[] official, ItemStack have, boolean[] used) {
        int soft = -1;
        for (int i = 0; i < official.length && i < used.length; i++) {
            if (used[i] || isEmpty(official[i])) {
                continue;
            }
            ItemStack want = official[i];
            if (want.getType() != have.getType() || want.getAmount() != have.getAmount()) {
                continue;
            }
            if (want.getEnchantments().equals(have.getEnchantments())) {
                return i;
            }
            if (soft < 0) {
                soft = i;
            }
        }
        return soft;
    }

    private static void clearEquipment(PlayerInventory inventory, EquipmentSlot slot) {
        try {
            inventory.setItem(slot, null);
        } catch (IllegalArgumentException ignored) {
            // Slot not usable on this entity / API snapshot.
        }
    }

    private static void placeStorage(ItemStack[] layout, ItemStack stack) {
        if (stack == null) {
            return;
        }
        for (int i = 0; i < 36; i++) {
            if (isEmpty(layout[i])) {
                layout[i] = stack;
                return;
            }
        }
        if (isEmpty(layout[OFFHAND])) {
            layout[OFFHAND] = stack;
        }
    }

    private static boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir() || KitLayoutContents.isPlaceholder(item);
    }

    private static ItemStack[] copy41(ItemStack[] source) {
        ItemStack[] out = new ItemStack[SIZE];
        if (source == null) {
            return out;
        }
        int n = Math.min(SIZE, source.length);
        for (int i = 0; i < n; i++) {
            out[i] = source[i] == null ? null : source[i].clone();
        }
        return out;
    }

    private static ItemStack cloneOrNull(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || KitLayoutContents.isPlaceholder(stack)) {
            return null;
        }
        return stack.clone();
    }

    private static ItemStack firstNonNull(ItemStack a, ItemStack b) {
        return a != null ? a : b;
    }

    static ItemStack resolveEntry(KitItemEntry entry) {
        if (entry == null) {
            return null;
        }
        if (entry.hasSerializedItem()) {
            ItemStack decoded = ItemSerializer.singleFromBase64(entry.itemDataBase64());
            if (decoded != null && !decoded.getType().isAir()) {
                return decoded;
            }
        }
        Material material = Material.matchMaterial(entry.material());
        if (material == null || material.isAir()) {
            return null;
        }
        return new ItemStack(material, Math.max(1, entry.amount()));
    }

    static ItemStack armorItem(String value) {
        if (value == null || value.isBlank() || "null".equalsIgnoreCase(value)) {
            return null;
        }
        if (value.startsWith("data:")) {
            ItemStack decoded = ItemSerializer.singleFromBase64(value.substring("data:".length()));
            if (decoded != null && !decoded.getType().isAir()) {
                return decoded;
            }
            return null;
        }
        Material material = Material.matchMaterial(value);
        return material == null || material.isAir() ? null : new ItemStack(material);
    }
}
