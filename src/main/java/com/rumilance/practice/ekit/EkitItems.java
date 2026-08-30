package com.rumilance.practice.ekit;

import com.rumilance.practice.config.ConfigService;
import com.rumilance.practice.kit.CategoryKeys;
import com.rumilance.practice.util.PotionRules;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads/persists the OrPlusGUI category item pools ({@code ekit-items.yml}).
 * Categories: Weapons/Armor, Offhand, Blocks, Potions (legacy Japanese keys still load).
 */
public final class EkitItems {

    public static final List<String> CATEGORIES = CategoryKeys.EKIT;

    private final ConfigService configService;
    private final Map<String, List<String>> items = new ConcurrentHashMap<>();

    public EkitItems(ConfigService configService) {
        this.configService = configService;
        reload();
    }

    public void reload() {
        items.clear();
        FileConfiguration yaml = configService.ekitItems();
        for (String category : CATEGORIES) {
            items.put(category, loadList(yaml, category));
        }
    }

    private static List<String> loadList(FileConfiguration yaml, String category) {
        for (String key : CategoryKeys.ekitLoadKeys(category)) {
            List<String> list = yaml.getStringList("categories." + key);
            if (list != null && !list.isEmpty()) {
                return new ArrayList<>(list);
            }
        }
        return new ArrayList<>();
    }

    public List<String> items(String category) {
        return List.copyOf(items.getOrDefault(CategoryKeys.canonicalEkit(category), List.of()));
    }

    public boolean isPotionCategory(String category) {
        return CategoryKeys.isEkitPotion(category);
    }

    /** Display item for a category entry (potion category -> representative splash potion). */
    public ItemStack displayItem(String category, String entry) {
        if (isPotionCategory(category)) {
            return PotionRules.buildPotion(entry, 1, false, "splash");
        }
        Material material = Material.matchMaterial(entry);
        return new ItemStack(material == null ? Material.STONE : material);
    }

    public void add(String category, String entry) {
        String canonical = CategoryKeys.canonicalEkit(category);
        items.computeIfAbsent(canonical, c -> new ArrayList<>()).add(entry);
        persist(canonical);
    }

    public void remove(String category, int index) {
        String canonical = CategoryKeys.canonicalEkit(category);
        List<String> list = items.get(canonical);
        if (list != null && index >= 0 && index < list.size()) {
            list.remove(index);
            persist(canonical);
        }
    }

    private void persist(String category) {
        FileConfiguration yaml = configService.ekitItems();
        for (String key : CategoryKeys.ekitLoadKeys(category)) {
            yaml.set("categories." + key, null);
        }
        yaml.set("categories." + category, items.getOrDefault(category, List.of()));
        configService.save(ConfigService.EKIT_ITEMS);
    }
}
