package com.rumilance.practice.gui.menus;

import com.rumilance.practice.ekit.EkitItems;
import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.BottomInventoryClickHandler;
import com.rumilance.practice.gui.GuiDecorator;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.originalkit.OriginalKitService;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.util.ItemKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.List;

/**
 * OrPlusGUI: original kit editor (5 rows: armor / inventory / hotbar) + the player's
 * inventory filled with category tabs and items. Clicking a category item adds it to the
 * kit; weapons/armor open the enchant picker; potions open the potion picker.
 */
public final class OriginalKitEditGui extends AbstractGui implements BottomInventoryClickHandler {

    private static final int ITEMS_PER_PAGE = 27;

    private final OriginalKitService service;
    private final EkitItems ekitItems;
    private EnchantGui enchantGui;
    private PotionGui potionGui;
    private ConfirmGui confirmGui;
    private OriginalKitGui originalKitGui;

    public OriginalKitEditGui(GuiSessionRegistry registry, SoundService sounds,
                              OriginalKitService service, EkitItems ekitItems) {
        super(registry, sounds, GuiType.EKIT_EDIT, 6, false);
        this.service = service;
        this.ekitItems = ekitItems;
    }

    public void setEnchantGui(EnchantGui enchantGui) {
        this.enchantGui = enchantGui;
    }

    public void setPotionGui(PotionGui potionGui) {
        this.potionGui = potionGui;
    }

    public void setConfirmGui(ConfirmGui confirmGui) {
        this.confirmGui = confirmGui;
    }

    public void setOriginalKitGui(OriginalKitGui originalKitGui) {
        this.originalKitGui = originalKitGui;
    }

    public void open(Player player, int kitSlot, ItemStack[] layout) {
        service.beginEdit(player, kitSlot, layout);
        GuiSession session = registry.open(player.getUniqueId(), type(), rows);
        PracticeGuiOpen.open(this, player, session);
        sounds.play(player, "gui-open");
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return Component.text("オリジナルキット編集", NamedTextColor.WHITE);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        OriginalKitService.EditContext ctx = service.context(player.getUniqueId());
        if (ctx == null) {
            return;
        }
        renderTop(inventory, ctx);
        renderPlayerInventory(player, ctx);
    }

    private void renderTop(Inventory inventory, OriginalKitService.EditContext ctx) {
        ItemStack[] layout = ctx.layout;
        inventory.clear();
        inventory.setItem(0, decorative());
        inventory.setItem(1, tagged(layout[36], "slot:36"));
        inventory.setItem(2, tagged(layout[37], "slot:37"));
        inventory.setItem(3, tagged(layout[38], "slot:38"));
        inventory.setItem(4, tagged(layout[39], "slot:39"));
        inventory.setItem(5, tagged(layout[40], "slot:40"));
        inventory.setItem(6, decorative());
        inventory.setItem(7, decorative());
        inventory.setItem(8, decorative());
        for (int inv = 9; inv <= 35; inv++) {
            inventory.setItem(inv, tagged(layout[inv], "slot:" + inv));
        }
        for (int hot = 0; hot < 9; hot++) {
            inventory.setItem(36 + hot, tagged(layout[hot], "slot:" + hot));
        }
        inventory.setItem(45, GuiDecorator.button(Material.RED_DYE,
                Component.text("戻る", NamedTextColor.RED), "back"));
        for (int i = 0; i < EkitItems.CATEGORIES.size(); i++) {
            String cat = EkitItems.CATEGORIES.get(i);
            boolean selected = cat.equals(ctx.category);
            inventory.setItem(46 + i, GuiDecorator.button(tabMaterial(cat),
                    Component.text(cat, selected ? NamedTextColor.GREEN : NamedTextColor.WHITE),
                    "cat:" + cat));
        }
        inventory.setItem(53, GuiDecorator.button(Material.LIME_DYE,
                Component.text("保存", NamedTextColor.GREEN), "save"));
    }

    private static Material tabMaterial(String category) {
        return switch (category) {
            case "サブアイテム" -> Material.WATER_BUCKET;
            case "ブロック" -> Material.OAK_PLANKS;
            case "ポーション" -> Material.SPLASH_POTION;
            default -> Material.NETHERITE_CHESTPLATE;
        };
    }

    private ItemStack decorative() {
        return GuiDecorator.decorative(Material.GRAY_STAINED_GLASS_PANE, " ");
    }

    private ItemStack tagged(ItemStack stack, String action) {
        if (stack == null || stack.getType().isAir()) {
            ItemStack glass = GuiDecorator.decorative(Material.GRAY_STAINED_GLASS_PANE, " ");
            ItemMeta meta = glass.getItemMeta();
            meta.getPersistentDataContainer().set(ItemKeys.guiAction(), PersistentDataType.STRING, action);
            glass.setItemMeta(meta);
            return glass;
        }
        ItemStack copy = stack.clone();
        ItemMeta meta = copy.getItemMeta();
        meta.getPersistentDataContainer().set(ItemKeys.guiAction(), PersistentDataType.STRING, action);
        copy.setItemMeta(meta);
        return copy;
    }

    private void renderPlayerInventory(Player player, OriginalKitService.EditContext ctx) {
        PlayerInventory inv = player.getInventory();
        inv.clear();
        List<String> catItems = ekitItems.items(ctx.category);
        int start = ctx.page * ITEMS_PER_PAGE;
        for (int i = 0; i < ITEMS_PER_PAGE; i++) {
            int idx = start + i;
            if (idx < catItems.size()) {
                inv.setItem(i, ekitItems.displayItem(ctx.category, catItems.get(idx)).clone());
            }
        }
        if (ctx.page > 0) {
            inv.setItem(27, pageButton("前のページへ", "prev"));
        }
        inv.setItem(28, deleteGlass());
        inv.setItem(29, deleteGlass());
        inv.setItem(30, deleteGlass());
        inv.setItem(31, GuiDecorator.button(Material.LIME_STAINED_GLASS_PANE,
                Component.text("続行", NamedTextColor.GREEN), "continue"));
        inv.setItem(32, deleteGlass());
        inv.setItem(33, deleteGlass());
        inv.setItem(34, deleteGlass());
        if (start + ITEMS_PER_PAGE < catItems.size()) {
            inv.setItem(35, pageButton("次のページへ", "next"));
        }
    }

    private ItemStack deleteGlass() {
        ItemStack stack = GuiDecorator.button(Material.BLUE_STAINED_GLASS_PANE,
                Component.text("削除", NamedTextColor.WHITE), "delete");
        ItemMeta meta = stack.getItemMeta();
        meta.lore(List.of(Component.text("ここにドラッグでアイテムを削除", NamedTextColor.GRAY)));
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack pageButton(String name, String action) {
        return GuiDecorator.button(Material.LIME_STAINED_GLASS_PANE,
                Component.text(name, NamedTextColor.GREEN), action);
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        OriginalKitService.EditContext ctx = service.context(player.getUniqueId());
        if (ctx == null) {
            return;
        }
        switch (action) {
            case "back" -> {
                service.endEdit(player);
                player.closeInventory();
                if (originalKitGui != null) {
                    originalKitGui.open(player);
                }
            }
            case "save", "continue" -> saveAndClose(player, ctx);
            default -> {
                if (action != null && action.startsWith("cat:")) {
                    ctx.category = action.substring(4);
                    ctx.page = 0;
                    render(player, session, inventory);
                } else if (action != null && action.startsWith("slot:")) {
                    int slotIndex = Integer.parseInt(action.substring(5));
                    handleSlotPickup(player, ctx, slotIndex);
                    render(player, session, inventory);
                }
            }
        }
    }

    private void handleSlotPickup(Player player, OriginalKitService.EditContext ctx, int slotIndex) {
        InventoryView view = player.getOpenInventory();
        ItemStack cursor = view.getCursor();
        ItemStack current = ctx.layout[slotIndex];
        if (cursor == null || cursor.getType().isAir()) {
            if (current != null && !current.getType().isAir()) {
                view.setCursor(current.clone());
                ctx.layout[slotIndex] = null;
            }
        } else {
            ctx.layout[slotIndex] = cursor.clone();
            if (current != null && !current.getType().isAir()) {
                view.setCursor(current.clone());
            } else {
                view.setCursor(null);
            }
        }
    }

    private void saveAndClose(Player player, OriginalKitService.EditContext ctx) {
        service.saveLayout(player, ctx.slot, ctx.layout);
        service.endEdit(player);
        player.closeInventory();
    }

    @Override
    public void handleBottomClick(Player player, GuiSession session, InventoryClickEvent event) {
        OriginalKitService.EditContext ctx = service.context(player.getUniqueId());
        if (ctx == null) {
            return;
        }
        int slot = event.getSlot();
        ItemStack cursor = event.getCursor();
        if (slot <= 26) {
            if (cursor != null && !cursor.getType().isAir()) {
                return;
            }
            List<String> catItems = ekitItems.items(ctx.category);
            int idx = ctx.page * ITEMS_PER_PAGE + slot;
            if (idx >= catItems.size()) {
                return;
            }
            String entry = catItems.get(idx);
            if (ekitItems.isPotionCategory(ctx.category)) {
                if (potionGui != null) {
                    ctx.suppressRestore = true;
                    potionGui.open(player, entry);
                }
                return;
            }
            ItemStack item = ekitItems.displayItem(ctx.category, entry).clone();
            if ("武器/防具".equals(ctx.category) && enchantGui != null) {
                ctx.suppressRestore = true;
                enchantGui.open(player, item);
                return;
            }
            boolean placed = OriginalKitService.addToLayout(ctx, item);
            if (!placed) {
                player.sendMessage(Component.text("キットのインベントリが満杯です。", NamedTextColor.RED));
                return;
            }
            sounds.play(player, "select");
            renderTop(event.getView().getTopInventory(), ctx);
            return;
        }
        if (slot == 27) {
            ctx.page = Math.max(0, ctx.page - 1);
            renderPlayerInventory(player, ctx);
            return;
        }
        if (slot == 35) {
            List<String> catItems = ekitItems.items(ctx.category);
            if ((ctx.page + 1) * ITEMS_PER_PAGE < catItems.size()) {
                ctx.page++;
                renderPlayerInventory(player, ctx);
            }
            return;
        }
        if (slot == 31) {
            saveAndClose(player, ctx);
            return;
        }
        if (slot >= 28 && slot <= 34) {
            if (cursor != null && !cursor.getType().isAir()) {
                event.getView().setCursor(null);
                sounds.play(player, "delete");
                return;
            }
            if (ctx.selectedSlot != null) {
                ctx.layout[ctx.selectedSlot] = null;
                ctx.selectedSlot = null;
                sounds.play(player, "delete");
                renderTop(event.getView().getTopInventory(), ctx);
                return;
            }
            if (confirmGui != null) {
                ctx.suppressRestore = true;
                confirmGui.open(player,
                        Component.text("本当にすべて削除しますか？", NamedTextColor.RED),
                        List.of(Component.text("キット内のアイテムを全て削除します。", NamedTextColor.GRAY)),
                        p -> {
                            OriginalKitService.EditContext c = service.context(p.getUniqueId());
                            if (c != null) {
                                Arrays.fill(c.layout, null);
                                c.selectedSlot = null;
                            }
                            open(p, c == null ? 22 : c.slot, c == null ? new ItemStack[41] : c.layout);
                        },
                        p -> {
                            OriginalKitService.EditContext c = service.context(p.getUniqueId());
                            if (c != null) {
                                open(p, c.slot, c.layout);
                            }
                        });
            }
        }
    }
}
