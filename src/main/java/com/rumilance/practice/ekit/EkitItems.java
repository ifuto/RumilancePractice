package com.rumilance.practice.ekit;

import com.rumilance.practice.config.ConfigService;
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
 * Categories: 武器/防具, サブアイテム, ブロック, ポーション.
 */
public final class EkitItems {

    public static final List<String> CATEGORIES = List.of("武器/防具", "サブアイテム", "ブロック", "ポーション");

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
            items.put(category, new ArrayList<>(yaml.getStringList("categories." + category)));
        }
    }

    public List<String> items(String category) {
        return List.copyOf(items.getOrDefault(category, List.of()));
    }

    public boolean isPotionCategory(String category) {
        return "ポーション".equals(category);
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
        items.computeIfAbsent(category, c -> new ArrayList<>()).add(entry);
        persist(category);
    }

    public void remove(String category, int index) {
        List<String> list = items.get(category);
        if (list != null && index >= 0 && index < list.size()) {
            list.remove(index);
            persist(category);
        }
    }

    private void persist(String category) {
        FileConfiguration yaml = configService.ekitItems();
        yaml.set("categories." + category, items.getOrDefault(category, List.of()));
        configService.save(ConfigService.EKIT_ITEMS);
    }
}
