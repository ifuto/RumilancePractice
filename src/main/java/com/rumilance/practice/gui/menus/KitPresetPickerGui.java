package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.kit.KitLayoutEditor;
import com.rumilance.practice.kit.PresetItems;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.util.GuiSlots;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

/** Player-facing picker for {@link PresetItems} candidates when editing a preset-enabled kit. */
public final class KitPresetPickerGui extends AbstractGui {

    private final PresetItems presetItems;
    private EditKitGui editKitGui;

    public KitPresetPickerGui(GuiSessionRegistry registry, SoundService sounds, PresetItems presetItems) {
        super(registry, sounds, GuiType.KIT_PRESET_PICKER, 6, false);
        this.presetItems = presetItems;
    }

    public void setEditKitGui(EditKitGui editKitGui) {
        this.editKitGui = editKitGui;
    }

    public void open(Player player, String kitId, ItemStack[] parentLayout) {
        GuiSession session = registry.open(player.getUniqueId(), type(), rows);
        session.setSelectedKit(kitId);
        session.put("category", PresetItems.CATEGORIES.getFirst());
        if (parentLayout != null) {
            session.put("parent_layout", parentLayout.clone());
        }
        PracticeGuiOpen.open(this, player, session);
        sounds.play(player, "gui-open");
    }

    public void openForCategory(Player player, String kitId, String category) {
        open(player, kitId, (ItemStack[]) null);
        registry.get(player.getUniqueId()).ifPresent(s -> s.put("category", category));
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return Component.text("Preset: " + session.get("category", String.class), UiTheme.PRIMARY)
                .decoration(TextDecoration.ITALIC, false);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        MenuScaffold.chrome(inventory);
        String category = session.get("category", String.class);
        int tab = 0;
        for (String cat : PresetItems.CATEGORIES) {
            if (tab >= 4) {
                break;
            }
            boolean active = cat.equals(category);
            inventory.setItem(GuiSlots.slot(0, 1 + tab), ItemBuilder.of(active ? Material.LIME_DYE : Material.GRAY_DYE)
                    .name(Component.text(cat, active ? UiTheme.SUCCESS : UiTheme.MUTED))
                    .action("cat:" + cat)
                    .build());
            tab++;
        }
        Map<Integer, String> slots = presetItems.slots(category);
        int index = 0;
        for (Map.Entry<Integer, String> entry : slots.entrySet()) {
            if (index >= 28) {
                break;
            }
            ItemStack display = presetItems.displayItem(category, entry.getValue());
            if (display == null) {
                continue;
            }
            int row = 1 + index / 7;
            int col = 1 + index % 7;
            ItemStack icon = display.clone();
            icon.editMeta(meta -> meta.getPersistentDataContainer().set(
                    com.rumilance.practice.util.ItemKeys.guiAction(),
                    org.bukkit.persistence.PersistentDataType.STRING,
                    "pick:" + entry.getKey()));
            inventory.setItem(GuiSlots.slot(row, col), icon);
            index++;
        }
        inventory.setItem(GuiSlots.slot(5, 4), ItemBuilder.action(UiTheme.BACK,
                Component.text("Back to editor", UiTheme.WARNING), "back"));
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        if (action == null) {
            return;
        }
        if ("back".equals(action)) {
            sounds.play(player, "gui-click");
            if (editKitGui != null && session.selectedKit() != null) {
                editKitGui.openKitEditor(player, session.selectedKit());
            } else {
                player.closeInventory();
            }
            return;
        }
        if (action.startsWith("cat:")) {
            session.put("category", action.substring(4));
            sounds.play(player, "gui-click");
            render(player, session, inventory);
            return;
        }
        if (action.startsWith("pick:")) {
            String category = session.get("category", String.class);
            int slotIndex = Integer.parseInt(action.substring(5));
            String entry = presetItems.entryAt(category, slotIndex);
            if (entry == null) {
                return;
            }
            ItemStack item = presetItems.displayItem(category, entry);
            if (item == null) {
                return;
            }
            ItemStack[] layout = session.get("parent_layout", ItemStack[].class);
            if (layout == null) {
                layout = new ItemStack[41];
            } else {
                layout = layout.clone();
            }
            if (!KitLayoutEditor.addToLayout(layout, item)) {
                sounds.play(player, "error");
                player.sendMessage(Component.text("Kit inventory is full.", UiTheme.DANGER));
                return;
            }
            sounds.play(player, "select");
            if (editKitGui != null && session.selectedKit() != null) {
                editKitGui.reopenWithLayout(player, session.selectedKit(), layout);
            }
        }
    }
}
