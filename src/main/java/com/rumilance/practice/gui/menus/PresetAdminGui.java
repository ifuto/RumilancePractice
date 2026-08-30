package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.FreeInventoryEdit;
import com.rumilance.practice.gui.GuiCloseHandler;
import com.rumilance.practice.gui.GuiDecorator;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.kit.PresetItems;
import com.rumilance.practice.sound.SoundService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * OP preset candidate editor — free chest editing across multiple pages.
 * Drop (Q) destroys the cursor/slot item instead of throwing it into the world.
 */
public final class PresetAdminGui extends AbstractGui implements FreeInventoryEdit, GuiCloseHandler {

    private static final String VIEW = "preset_admin_view";
    private static final String CAT = "preset_admin_cat";
    private static final String PAGE = "preset_admin_page";
    private static final int BACK_SLOT = 36;
    private static final int PREV_SLOT = 37;
    private static final int PAGE_SLOT = 39;
    private static final int NEXT_SLOT = 41;
    private static final int HINT_SLOT = 40;
    private static final int SAVE_SLOT = 42;
    private static final int CLOSE_SLOT = 44;

    private final PresetItems presetItems;
    private Consumer<Player> returnTo = p -> { };

    public PresetAdminGui(GuiSessionRegistry registry, SoundService sounds, PresetItems presetItems) {
        super(registry, sounds, GuiType.PRESET_ADMIN, 5, false);
        this.presetItems = presetItems;
    }

    public void setReturnTo(Consumer<Player> returnTo) {
        this.returnTo = returnTo == null ? p -> { } : returnTo;
    }

    public void openAdmin(Player player) {
        GuiSession session = registry.open(player.getUniqueId(), type(), rows);
        session.put(VIEW, "categories");
        PracticeGuiOpen.open(this, player, session);
        sounds.play(player, "gui-open");
    }

    public void openCategory(Player player, String category) {
        GuiSession session = registry.open(player.getUniqueId(), type(), rows);
        session.put(VIEW, "chest");
        session.put(CAT, category);
        session.put(PAGE, 0);
        PracticeGuiOpen.open(this, player, session);
        sounds.play(player, "gui-open");
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        if ("chest".equals(session.get(VIEW, String.class))) {
            String cat = session.get(CAT, String.class);
            int page = pageOf(session);
            String catName = cat == null ? "" : cat;
            return t(player, "gui.preset-admin-cat",
                    com.rumilance.practice.locale.MessageService.tags(
                            "cat", catName,
                            "page", String.valueOf(page + 1),
                            "pages", String.valueOf(PresetItems.MAX_PAGES)))
                    .color(UiTheme.PRIMARY);
        }
        return t(player, "gui.preset-admin-title").color(UiTheme.PRIMARY);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        if ("chest".equals(session.get(VIEW, String.class))) {
            renderChest(player, session, inventory);
        } else {
            renderCategories(player, inventory);
        }
    }

    private void renderCategories(Player player, Inventory inventory) {
        MenuScaffold.chrome(inventory);
        for (int i = 0; i < PresetItems.CATEGORIES.size(); i++) {
            String category = PresetItems.CATEGORIES.get(i);
            inventory.setItem(20 + i, GuiDecorator.button(categoryMaterial(category),
                    Component.text(category, UiTheme.PRIMARY)
                            .decoration(TextDecoration.ITALIC, false),
                    "cat:" + category, false));
        }
        inventory.setItem(CLOSE_SLOT, ItemBuilder.action(UiTheme.CLOSE,
                t(player, "menu.close"), "close"));
    }

    private void renderChest(Player player, GuiSession session, Inventory inventory) {
        MenuScaffold.chrome(inventory);
        String cat = session.get(CAT, String.class);
        if (cat == null) {
            return;
        }
        int page = pageOf(session);
        int base = page * PresetItems.SLOTS_PER_PAGE;
        Map<Integer, String> slots = presetItems.slots(cat);
        for (int local = 0; local < PresetItems.SLOTS_PER_PAGE; local++) {
            String entry = slots.get(base + local);
            if (entry != null) {
                inventory.setItem(local, presetItems.displayItem(cat, entry).clone());
            }
        }
        inventory.setItem(BACK_SLOT, ItemBuilder.action(UiTheme.BACK,
                t(player, "gui.preset-admin-back"), "back"));
        if (page > 0) {
            inventory.setItem(PREV_SLOT, GuiDecorator.button(Material.ORANGE_STAINED_GLASS_PANE,
                    t(player, "gui.page-prev"), "prev"));
        }
        inventory.setItem(PAGE_SLOT, GuiDecorator.button(Material.PAPER,
                t(player, "menu.page-of", com.rumilance.practice.locale.MessageService.tags(
                        "page", String.valueOf(page + 1),
                        "pages", String.valueOf(PresetItems.MAX_PAGES))),
                "noop"));
        if (page < PresetItems.MAX_PAGES - 1) {
            inventory.setItem(NEXT_SLOT, GuiDecorator.button(Material.LIME_STAINED_GLASS_PANE,
                    t(player, "gui.page-next"), "next"));
        }
        inventory.setItem(HINT_SLOT, GuiDecorator.button(Material.BOOK,
                t(player, "gui.preset-admin-free"), "noop"));
        ItemStack hint = inventory.getItem(HINT_SLOT);
        if (hint != null && hint.hasItemMeta()) {
            ItemMeta meta = hint.getItemMeta();
            meta.lore(List.of(
                    Component.text(line(player, "gui.preset-admin-free-1"), UiTheme.VALUE)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text(line(player, "gui.preset-admin-free-2"), UiTheme.WARNING)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text(line(player, "gui.preset-admin-free-3"), UiTheme.PRIMARY)
                            .decoration(TextDecoration.ITALIC, false)));
            hint.setItemMeta(meta);
        }
        inventory.setItem(SAVE_SLOT, ItemBuilder.action(UiTheme.CONFIRM,
                t(player, "gui.preset-admin-save-page"), "save"));
    }

    private static int pageOf(GuiSession session) {
        Integer page = session.get(PAGE, Integer.class);
        if (page == null) {
            return 0;
        }
        return Math.max(0, Math.min(PresetItems.MAX_PAGES - 1, page));
    }

    @Override
    public boolean isFreeEditActive(GuiSession session) {
        return "chest".equals(session.get(VIEW, String.class));
    }

    @Override
    public boolean isControlSlot(GuiSession session, int topSlot) {
        return topSlot >= PresetItems.SLOTS_PER_PAGE;
    }

    @Override
    public void persistFreeEdit(Player player, GuiSession session, Inventory top) {
        if (!isFreeEditActive(session)) {
            return;
        }
        String cat = session.get(CAT, String.class);
        if (cat == null) {
            return;
        }
        ItemStack[] snap = new ItemStack[PresetItems.SLOTS_PER_PAGE];
        for (int i = 0; i < PresetItems.SLOTS_PER_PAGE; i++) {
            snap[i] = top.getItem(i);
        }
        presetItems.replacePageFromInventory(cat, pageOf(session), snap);
    }

    @Override
    public void onGuiClose(Player player, GuiSession session, Inventory top, InventoryCloseEvent.Reason reason) {
        if (reason == InventoryCloseEvent.Reason.OPEN_NEW) {
            return;
        }
        if (isFreeEditActive(session)) {
            persistFreeEdit(player, session, top);
        }
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        if (action == null || "noop".equals(action)) {
            return;
        }
        if ("close".equals(action)) {
            if (isFreeEditActive(session)) {
                persistFreeEdit(player, session, inventory);
            }
            player.closeInventory();
            returnTo.accept(player);
            return;
        }
        if ("save".equals(action)) {
            persistFreeEdit(player, session, inventory);
            sounds.play(player, "select");
            player.sendMessage(t(player, "gui.preset-admin-saved",
                    com.rumilance.practice.locale.MessageService.tags(
                            "page", String.valueOf(pageOf(session) + 1))));
            refresh(player, session, inventory);
            return;
        }
        if ("back".equals(action)) {
            if (isFreeEditActive(session)) {
                persistFreeEdit(player, session, inventory);
            }
            session.put(VIEW, "categories");
            refresh(player, session, inventory);
            return;
        }
        if ("prev".equals(action)) {
            persistFreeEdit(player, session, inventory);
            session.put(PAGE, Math.max(0, pageOf(session) - 1));
            sounds.play(player, "gui-click");
            refresh(player, session, inventory);
            return;
        }
        if ("next".equals(action)) {
            persistFreeEdit(player, session, inventory);
            session.put(PAGE, Math.min(PresetItems.MAX_PAGES - 1, pageOf(session) + 1));
            sounds.play(player, "gui-click");
            refresh(player, session, inventory);
            return;
        }
        if (action.startsWith("cat:")) {
            session.put(VIEW, "chest");
            session.put(CAT, action.substring(4));
            session.put(PAGE, 0);
            refresh(player, session, inventory);
        }
    }

    private static Material categoryMaterial(String category) {
        return switch (com.rumilance.practice.kit.CategoryKeys.canonicalPreset(category)) {
            case "Armor" -> Material.NETHERITE_CHESTPLATE;
            case "Gear" -> Material.DIAMOND_SWORD;
            case "Potions" -> Material.SPLASH_POTION;
            default -> Material.GOLDEN_APPLE;
        };
    }
}
