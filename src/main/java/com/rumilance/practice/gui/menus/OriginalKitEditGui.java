package com.rumilance.practice.gui.menus;

import com.rumilance.practice.ekit.EkitItems;
import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.BottomInventoryClickHandler;
import com.rumilance.practice.gui.GuiDecorator;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.kit.KitLayoutEditor;
import com.rumilance.practice.originalkit.OriginalKitService;
import com.rumilance.practice.sound.SoundService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
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
        // The old chest-GUI editor is retired: editing now happens inside the physical original-kit
        // room (creative, after teleport). This closes any open GUI and sends the player there.
        player.closeInventory();
        service.enterRoomEditor(player, kitSlot, layout);
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return t(player, "gui.original-edit-title").color(UiTheme.PRIMARY);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        OriginalKitService.EditContext ctx = service.context(player.getUniqueId());
        if (ctx == null) {
            return;
        }
        renderTop(player, inventory, ctx);
        renderPlayerInventory(player, ctx);
    }

    private void renderTop(Player player, Inventory inventory, OriginalKitService.EditContext ctx) {
        ItemStack[] layout = ctx.layout;
        MenuScaffold.chrome(inventory);
        inventory.setItem(0, decorative());
        inventory.setItem(1, tagged(player, layout[36], "slot:36"));
        inventory.setItem(2, tagged(player, layout[37], "slot:37"));
        inventory.setItem(3, tagged(player, layout[38], "slot:38"));
        inventory.setItem(4, tagged(player, layout[39], "slot:39"));
        inventory.setItem(5, tagged(player, layout[40], "slot:40"));
        inventory.setItem(6, decorative());
        inventory.setItem(7, decorative());
        inventory.setItem(8, decorative());
        for (int inv = 9; inv <= 35; inv++) {
            inventory.setItem(inv, tagged(player, layout[inv], "slot:" + inv));
        }
        for (int hot = 0; hot < 9; hot++) {
            inventory.setItem(36 + hot, tagged(player, layout[hot], "slot:" + hot));
        }
        inventory.setItem(45, ItemBuilder.action(UiTheme.BACK,
                t(player, "menu.back"), "back"));
        for (int i = 0; i < EkitItems.CATEGORIES.size(); i++) {
            String cat = EkitItems.CATEGORIES.get(i);
            boolean selected = cat.equals(ctx.category);
            inventory.setItem(46 + i, GuiDecorator.button(tabMaterial(cat),
                    Component.text(cat, selected ? UiTheme.SUCCESS : UiTheme.VALUE),
                    "cat:" + cat, selected));
        }
        inventory.setItem(53, ItemBuilder.action(UiTheme.CONFIRM,
                t(player, "gui.save"), "save"));
    }

    private static Material tabMaterial(String category) {
        return switch (com.rumilance.practice.kit.CategoryKeys.canonicalEkit(category)) {
            case "Offhand" -> Material.WATER_BUCKET;
            case "Blocks" -> Material.OAK_PLANKS;
            case "Potions" -> Material.SPLASH_POTION;
            default -> Material.NETHERITE_CHESTPLATE;
        };
    }

    private ItemStack decorative() {
        return GuiDecorator.decorative(Material.GRAY_STAINED_GLASS_PANE, " ");
    }

    public static int layoutIndexForGuiSlot(int guiSlot) {
        if (guiSlot >= 1 && guiSlot <= 5) {
            return 35 + guiSlot; // 36-40
        }
        if (guiSlot >= 9 && guiSlot <= 35) {
            return guiSlot;
        }
        if (guiSlot >= 36 && guiSlot <= 44) {
            return guiSlot - 36;
        }
        return -1;
    }

    private ItemStack tagged(Player player, ItemStack stack, String action) {
        if (stack == null || stack.getType().isAir()) {
            return null;
        }
        ArrayList<Component> extra = new ArrayList<>();
        if (stack.getItemMeta() instanceof org.bukkit.inventory.meta.ArmorMeta) {
            extra.add(t(player, "gui.kit-trim-hint").color(UiTheme.MUTED)
                    .decoration(TextDecoration.ITALIC, false));
        }
        if (com.rumilance.practice.gui.KitAnvilRenameService.isRenameableTool(stack.getType())) {
            extra.add(t(player, "gui.kit-rename-hint").color(UiTheme.MUTED)
                    .decoration(TextDecoration.ITALIC, false));
        }
        return KitLayoutEditor.tagLayoutItem(stack, action, extra);
    }

    private void renderPlayerInventory(Player player, OriginalKitService.EditContext ctx) {
        PlayerInventory inv = player.getInventory();
        inv.clear();
        List<String> catItems = ekitItems.items(ctx.category);
        int start = ctx.page * ITEMS_PER_PAGE;
        // カテゴリアイテムはメインインベントリ（スロット 9-35、27枠）
        for (int i = 0; i < ITEMS_PER_PAGE; i++) {
            int idx = start + i;
            if (idx < catItems.size()) {
                inv.setItem(9 + i, ekitItems.displayItem(ctx.category, catItems.get(idx)).clone());
            }
        }
        // ホットバー（スロット 0-8）: 1番=前ページ / 2-4・6-8番=削除 / 5番=続行 / 9番=次ページ
        if (ctx.page > 0) {
            inv.setItem(0, pageButton(line(player, "gui.page-prev"), "prev"));
        }
        inv.setItem(1, deleteGlass(player));
        inv.setItem(2, deleteGlass(player));
        inv.setItem(3, deleteGlass(player));
        inv.setItem(4, GuiDecorator.button(Material.LIME_STAINED_GLASS_PANE,
                t(player, "gui.continue"), "continue"));
        inv.setItem(5, deleteGlass(player));
        inv.setItem(6, deleteGlass(player));
        inv.setItem(7, deleteGlass(player));
        if (start + ITEMS_PER_PAGE < catItems.size()) {
            inv.setItem(8, pageButton(line(player, "gui.page-next"), "next"));
        }
    }

    private ItemStack deleteGlass(Player player) {
        ItemStack stack = GuiDecorator.button(Material.BLUE_STAINED_GLASS_PANE,
                t(player, "gui.delete"), "delete");
        ItemMeta meta = stack.getItemMeta();
        meta.lore(List.of(Component.text(line(player, "gui.drop-delete"), UiTheme.MUTED)));
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack pageButton(String name, String action) {
        return GuiDecorator.button(Material.LIME_STAINED_GLASS_PANE,
                Component.text(name, UiTheme.SUCCESS), action);
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
                GuiSession live = registry.get(player.getUniqueId()).orElse(session);
                if (live != null) {
                    live.setNavigatingAway(true);
                }
                if (originalKitGui != null) {
                    originalKitGui.open(player);
                } else {
                    player.closeInventory();
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
        if (slot >= 9 && slot <= 35) {
            // メインインベントリ = カテゴリアイテム
            if (cursor != null && !cursor.getType().isAir()) {
                return;
            }
            List<String> catItems = ekitItems.items(ctx.category);
            int idx = ctx.page * ITEMS_PER_PAGE + (slot - 9);
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
            if (com.rumilance.practice.kit.CategoryKeys.isEkitWeapons(ctx.category) && enchantGui != null) {
                ctx.suppressRestore = true;
                enchantGui.open(player, item);
                return;
            }
            boolean placed = OriginalKitService.addToLayout(ctx, item);
            if (!placed) {
                player.sendMessage(t(player, "gui.kit-full"));
                return;
            }
            sounds.play(player, "select");
            renderTop(player, event.getView().getTopInventory(), ctx);
            return;
        }
        if (slot <= 8) {
            if (slot == 0) {
                ctx.page = Math.max(0, ctx.page - 1);
                renderPlayerInventory(player, ctx);
                return;
            }
            if (slot == 8) {
                List<String> catItems = ekitItems.items(ctx.category);
                if ((ctx.page + 1) * ITEMS_PER_PAGE < catItems.size()) {
                    ctx.page++;
                    renderPlayerInventory(player, ctx);
                }
                return;
            }
            if (slot == 4) {
                saveAndClose(player, ctx);
                return;
            }
            // 削除ガラス（ホットバー 1-3, 5-7）
            if (cursor != null && !cursor.getType().isAir()) {
                event.getView().setCursor(null);
                sounds.play(player, "delete");
                return;
            }
            if (ctx.selectedSlot != null) {
                ctx.layout[ctx.selectedSlot] = null;
                ctx.selectedSlot = null;
                sounds.play(player, "delete");
                renderTop(player, event.getView().getTopInventory(), ctx);
                return;
            }
            if (confirmGui != null) {
                ctx.suppressRestore = true;
                confirmGui.open(player,
                        t(player, "gui.delete-all-confirm").color(UiTheme.DANGER),
                        List.of(t(player, "gui.delete-all-lore").color(UiTheme.MUTED)),
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
