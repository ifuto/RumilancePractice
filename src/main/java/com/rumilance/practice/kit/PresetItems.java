package com.rumilance.practice.kit;

import com.rumilance.practice.config.ConfigService;
import com.rumilance.practice.util.ItemSerializer;
import com.rumilance.practice.util.PotionRules;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * YAML-backed candidate pools for official-kit preset editors.
 *
 * <p>Each category maps a GUI slot (0..35) to either a legacy material / potion-effect key, or a
 * full NBT snapshot as {@code data:&lt;base64&gt;} (Paper {@code ItemStack.serializeAsBytes}).
 * Empty slots are omitted so OPs can leave gaps when arranging the player palette.</p>
 */
public final class PresetItems {

    public static final List<String> CATEGORIES = List.of("防具", "装備", "ポーション", "消耗品");
    /** Slots visible on one admin chest page (top 4 rows); the last row holds controls. */
    public static final int SLOTS_PER_PAGE = 36;
    /** How many pages the preset admin editor exposes. */
    public static final int MAX_PAGES = 5;
    /** Absolute slot capacity across all pages. */
    public static final int MAX_SLOTS = SLOTS_PER_PAGE * MAX_PAGES;
    public static final String DATA_PREFIX = "data:";

    private final ConfigService configService;
    private final Map<String, Map<Integer, String>> items = new ConcurrentHashMap<>();

    public PresetItems(ConfigService configService) {
        this.configService = configService;
        reload();
    }

    public void reload() {
        items.clear();
        FileConfiguration yaml = configService.presetItems();
        for (String category : CATEGORIES) {
            items.put(category, load(yaml, category));
        }
    }

    private Map<Integer, String> load(FileConfiguration yaml, String category) {
        Map<Integer, String> map = new TreeMap<>();
        String base = "categories." + category;
        if (yaml.isList(base)) {
            List<String> list = yaml.getStringList(base);
            for (int i = 0; i < list.size() && i < MAX_SLOTS; i++) {
                if (list.get(i) != null && !list.get(i).isBlank()) {
                    map.put(i, list.get(i));
                }
            }
            return map;
        }
        ConfigurationSection slots = yaml.getConfigurationSection(base + ".slots");
        if (slots != null) {
            for (String key : slots.getKeys(false)) {
                try {
                    int slot = Integer.parseInt(key);
                    String value = slots.getString(key);
                    if (slot >= 0 && slot < MAX_SLOTS && value != null && !value.isBlank()) {
                        map.put(slot, value);
                    }
                } catch (NumberFormatException ignored) {
                    // skip malformed keys
                }
            }
        }
        return map;
    }

    /** Slot -> entry map for a category, ordered by slot. */
    public Map<Integer, String> slots(String category) {
        return new TreeMap<>(items.getOrDefault(category, Map.of()));
    }

    /** Entry values in slot order (kept for callers that only need the list of candidates). */
    public List<String> items(String category) {
        return List.copyOf(slots(category).values());
    }

    public String entryAt(String category, int slot) {
        Map<Integer, String> map = items.get(category);
        return map == null ? null : map.get(slot);
    }

    public boolean isPotionCategory(String category) {
        return "ポーション".equals(category);
    }

    /** Places (or replaces) a candidate at an exact slot. */
    public void setSlot(String category, int slot, String value) {
        if (slot < 0 || slot >= MAX_SLOTS || value == null || value.isBlank()) {
            return;
        }
        items.computeIfAbsent(category, c -> new TreeMap<>()).put(slot, value);
        persist(category);
    }

    /** Serializes {@code item} (full NBT) and stores it at {@code slot}. */
    public void setSlotItem(String category, int slot, ItemStack item) {
        String encoded = encodeItem(item);
        if (encoded == null) {
            return;
        }
        setSlot(category, slot, encoded);
    }

    /**
     * Replaces the whole category from an inventory snapshot. Null/air slots become gaps.
     * Items are stored with full NBT via {@code data:} encoding.
     */
    public void replaceFromInventory(String category, ItemStack[] contents) {
        Map<Integer, String> map = new TreeMap<>();
        if (contents != null) {
            int limit = Math.min(MAX_SLOTS, contents.length);
            for (int i = 0; i < limit; i++) {
                String encoded = encodeItem(contents[i]);
                if (encoded != null) {
                    map.put(i, encoded);
                }
            }
        }
        items.put(category, map);
        persist(category);
    }

    /**
     * Replaces one admin-editor page ({@link #SLOTS_PER_PAGE} slots) while leaving other pages intact.
     */
    public void replacePageFromInventory(String category, int page, ItemStack[] contents) {
        int p = Math.max(0, Math.min(MAX_PAGES - 1, page));
        int base = p * SLOTS_PER_PAGE;
        Map<Integer, String> map = items.computeIfAbsent(category, c -> new TreeMap<>());
        for (int i = 0; i < SLOTS_PER_PAGE; i++) {
            map.remove(base + i);
        }
        if (contents != null) {
            int limit = Math.min(SLOTS_PER_PAGE, contents.length);
            for (int i = 0; i < limit; i++) {
                String encoded = encodeItem(contents[i]);
                if (encoded != null) {
                    map.put(base + i, encoded);
                }
            }
        }
        persist(category);
    }

    /** Removes any candidate at the given slot. */
    public void clearSlot(String category, int slot) {
        Map<Integer, String> map = items.get(category);
        if (map != null && map.remove(slot) != null) {
            persist(category);
        }
    }

    /** Adds a candidate at the first free slot (used by the /kit presetitem convenience command). */
    public int addAtFirstFree(String category, String value) {
        if (value == null || value.isBlank()) {
            return -1;
        }
        Map<Integer, String> map = items.computeIfAbsent(category, c -> new TreeMap<>());
        int slot = 0;
        while (slot < MAX_SLOTS && map.containsKey(slot)) {
            slot++;
        }
        if (slot >= MAX_SLOTS) {
            return -1;
        }
        map.put(slot, value);
        persist(category);
        return slot;
    }

    /** Adds a full-NBT item at the first free slot. */
    public int addItemAtFirstFree(String category, ItemStack item) {
        String encoded = encodeItem(item);
        if (encoded == null) {
            return -1;
        }
        return addAtFirstFree(category, encoded);
    }

    /** Removes the first slot holding {@code value}; returns true if something was removed. */
    public boolean removeValue(String category, String value) {
        Map<Integer, String> map = items.get(category);
        if (map == null || value == null) {
            return false;
        }
        Integer target = null;
        for (Map.Entry<Integer, String> e : map.entrySet()) {
            if (value.equalsIgnoreCase(e.getValue())) {
                target = e.getKey();
                break;
            }
        }
        if (target != null) {
            map.remove(target);
            persist(category);
            return true;
        }
        return false;
    }

    public ItemStack displayItem(String category, String entry) {
        if (entry == null || entry.isBlank()) {
            return new ItemStack(Material.STONE);
        }
        if (entry.startsWith(DATA_PREFIX)) {
            ItemStack decoded = ItemSerializer.singleFromBase64(entry.substring(DATA_PREFIX.length()));
            return decoded == null ? new ItemStack(Material.STONE) : decoded;
        }
        if (isPotionCategory(category)) {
            return PotionRules.buildPotion(entry, 1, false, "splash");
        }
        Material material = Material.matchMaterial(entry);
        return new ItemStack(material == null ? Material.STONE : material);
    }

    /** Encodes an item for YAML storage, preserving NBT. */
    public static String encodeItem(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }
        ItemStack one = item.clone();
        one.setAmount(Math.max(1, Math.min(64, one.getAmount())));
        String base64 = ItemSerializer.singleToBase64(one);
        return base64 == null ? null : DATA_PREFIX + base64;
    }

    private void persist(String category) {
        FileConfiguration yaml = configService.presetItems();
        yaml.set("categories." + category, null);
        Map<Integer, String> map = items.getOrDefault(category, Map.of());
        Map<String, Object> serialized = new LinkedHashMap<>();
        for (Map.Entry<Integer, String> e : new TreeMap<>(map).entrySet()) {
            serialized.put(String.valueOf(e.getKey()), e.getValue());
        }
        yaml.set("categories." + category + ".slots", serialized);
        configService.save(ConfigService.PRESET_ITEMS);
    }
}
