package com.rumilance.practice.gui.menus;

import com.rumilance.practice.ekit.EkitItems;
import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.BottomInventoryClickHandler;
import com.rumilance.practice.gui.GuiDecorator;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.util.GuiSlots;
import com.rumilance.practice.util.ItemKeys;
import com.rumilance.practice.util.PotionRules;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * /ekitadmin: chest-like category manager. Pick a category, then deposit items from the
 * player inventory (bottom) into the chest, or click chest items to withdraw them.
 */
public final class EkitAdminGui extends AbstractGui implements BottomInventoryClickHandler {

    private final EkitItems ekitItems;

    public EkitAdminGui(GuiSessionRegistry registry, SoundService sounds, EkitItems ekitItems) {
        super(registry, sounds, GuiType.EKIT_ADMIN, 6, false);
        this.ekitItems = ekitItems;
    }

    public void openAdmin(Player player) {
        GuiSession session = registry.open(player.getUniqueId(), type(), rows);
        session.put("admin_view", "categories");
        PracticeGuiOpen.open(this, player, session);
        sounds.play(player, "gui-open");
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        String view = session.get("admin_view", String.class);
        if ("chest".equals(view)) {
            String cat = session.get("admin_cat", String.class);
            return Component.text("カテゴリ: " + (cat == null ? "" : cat), NamedTextColor.WHITE);
        }
        return Component.text("オリジナルキット管理", NamedTextColor.WHITE);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        String view = session.get("admin_view", String.class);
        if ("chest".equals(view)) {
            renderChest(player, session, inventory);
        } else {
            renderCategories(inventory);
        }
    }

    private void renderCategories(Inventory inventory) {
        inventory.clear();
        String[][] cats = {
                {"武器/防具", "NETHERITE_CHESTPLATE"},
                {"サブアイテム", "WATER_BUCKET"},
                {"ブロック", "OAK_PLANKS"},
                {"ポーション", "SPLASH_POTION"}
        };
        for (int i = 0; i < cats.length; i++) {
            Material material = Material.matchMaterial(cats[i][1]);
            inventory.setItem(GuiSlots.slot(1, 2 + i), GuiDecorator.button(
                    material == null ? Material.STONE : material,
                    Component.text(cats[i][0], NamedTextColor.WHITE), "cat:" + cats[i][0]));
        }
        inventory.setItem(GuiSlots.slot(5, 4), GuiDecorator.button(Material.BARRIER,
                Component.text("閉じる", NamedTextColor.GRAY), "close"));
    }

    private void renderChest(Player player, GuiSession session, Inventory inventory) {
        inventory.clear();
        String cat = session.get("admin_cat", String.class);
        if (cat == null) {
            return;
        }
        List<String> entries = ekitItems.items(cat);
        for (int i = 0; i < 45; i++) {
            if (i < entries.size()) {
                ItemStack display = ekitItems.displayItem(cat, entries.get(i)).clone();
                ItemMeta meta = display.getItemMeta();
                meta.lore(List.of(Component.text("クリックで取り出し", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)));
                meta.getPersistentDataContainer().set(ItemKeys.guiAction(), PersistentDataType.STRING, "item:" + i);
                display.setItemMeta(meta);
                inventory.setItem(i, display);
            } else {
                ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
                ItemMeta meta = glass.getItemMeta();
                meta.displayName(Component.text(" ", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
                meta.lore(List.of(Component.text("アイテムをここにドロップして追加", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)));
                meta.getPersistentDataContainer().set(ItemKeys.guiAction(), PersistentDataType.STRING, "empty");
                glass.setItemMeta(meta);
                inventory.setItem(i, glass);
            }
        }
        inventory.setItem(45, GuiDecorator.button(Material.RED_DYE,
                Component.text("戻る", NamedTextColor.RED), "back"));
        inventory.setItem(53, GuiDecorator.button(Material.BARRIER,
                Component.text("閉じる", NamedTextColor.GRAY), "close"));
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        String view = session.get("admin_view", String.class);
        if (action == null) {
            return;
        }
        if ("close".equals(action)) {
            player.closeInventory();
            return;
        }
        if ("back".equals(action)) {
            session.put("admin_view", "categories");
            render(player, session, inventory);
            return;
        }
        if (action.startsWith("cat:")) {
            session.put("admin_view", "chest");
            session.put("admin_cat", action.substring(4));
            render(player, session, inventory);
            return;
        }
        if ("chest".equals(view) && action.startsWith("item:")) {
            int index = Integer.parseInt(action.substring(5));
            String cat = session.get("admin_cat", String.class);
            List<String> entries = ekitItems.items(cat);
            if (index >= 0 && index < entries.size()) {
                ItemStack taken = ekitItems.displayItem(cat, entries.get(index)).clone();
                ekitItems.remove(cat, index);
                sounds.play(player, "delete");
                player.getInventory().addItem(taken);
                render(player, session, inventory);
            }
            return;
        }
        if ("chest".equals(view) && "empty".equals(action)) {
            ItemStack cursor = player.getOpenInventory().getCursor();
            if (cursor != null && !cursor.getType().isAir()) {
                String cat = session.get("admin_cat", String.class);
                deposit(player, cat, cursor);
                player.getOpenInventory().setCursor(null);
                render(player, session, inventory);
            }
            return;
        }
    }

    @Override
    public void handleBottomClick(Player player, GuiSession session, InventoryClickEvent event) {
        String view = session.get("admin_view", String.class);
        if (!"chest".equals(view)) {
            return;
        }
        String cat = session.get("admin_cat", String.class);
        ItemStack current = event.getCurrentItem();
        if (current == null || current.getType().isAir()) {
            return;
        }
        ItemStack cursor = event.getCursor();
        if (cursor != null && !cursor.getType().isAir()) {
            return;
        }
        if (!deposit(player, cat, current)) {
            return;
        }
        // The click event is already cancelled by GuiListener, so mutating the event's
        // currentItem does NOT touch the real inventory. Modify the actual bottom
        // inventory directly instead.
        Inventory bottom = event.getView().getBottomInventory();
        if (current.getAmount() > 1) {
            current.setAmount(current.getAmount() - 1);
            bottom.setItem(event.getSlot(), current);
        } else {
            bottom.setItem(event.getSlot(), null);
        }
        sounds.play(player, "gui-click");
        render(player, session, event.getView().getTopInventory());
    }

    private boolean deposit(Player player, String cat, ItemStack item) {
        if (cat == null || item == null) {
            return false;
        }
        if (ekitItems.isPotionCategory(cat)) {
            String effect = PotionRules.effectOf(item);
            if (effect == null) {
                player.sendMessage(Component.text("このアイテムはポーションではありません。", NamedTextColor.RED));
                return false;
            }
            ekitItems.add(cat, effect);
        } else {
            ekitItems.add(cat, item.getType().name());
        }
        return true;
    }
}
