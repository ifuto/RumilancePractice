package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.DelayedButton;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.originalkit.OriginalKitService;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.util.ItemKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * Original kit paper grid (5x9). One paper = one original kit slot.
 * Locked slots are barrier blocks labelled with the tier that unlocks them.
 * Clicking a paper opens the per-slot {@link OriginalKitSlotMenuGui} (edit loadout / settings).
 */
public final class OriginalKitGui extends AbstractGui {

    private final OriginalKitService service;
    private OriginalKitSlotMenuGui slotMenuGui;

    public OriginalKitGui(GuiSessionRegistry registry, SoundService sounds, OriginalKitService service) {
        super(registry, sounds, GuiType.ORIGINAL_KIT, 5, false);
        this.service = service;
    }

    public void setSlotMenuGui(OriginalKitSlotMenuGui slotMenuGui) {
        this.slotMenuGui = slotMenuGui;
    }

    @Override
    protected com.rumilance.practice.gui.GuiFrame.Theme theme() {
        return com.rumilance.practice.gui.GuiFrame.Theme.YELLOW;
    }

    @Override
    protected Material titleIcon() {
        return Material.CHEST;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return t(player, "gui.original-title").color(UiTheme.PRIMARY);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        paintFrame(player, session, inventory);
        OriginalKitService.Plan plan = service.planOf(player);
        for (int slot = 0; slot < 45; slot++) {
            if (slot == 44) {
                continue; // 5行9列目は常に空
            }
            if (service.isSlotUnlocked(plan, slot)) {
                inventory.setItem(slot, paperItem(player, slot));
            } else {
                inventory.setItem(slot, barrierItem(player, plan, slot));
            }
        }
    }

    private ItemStack paperItem(Player player, int slot) {
        ItemStack stack = new ItemStack(Material.PAPER);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(t(player, "gui.original-title").color(UiTheme.WARNING));
        String saved = service.hasSaved(player.getUniqueId(), slot)
                ? line(player, "gui.saved") : line(player, "gui.unsaved");
        meta.lore(List.of(
                t(player, "gui.original-slot", com.rumilance.practice.locale.MessageService.tags(
                        "slot", String.valueOf(slot + 1), "saved", saved)).color(UiTheme.MUTED),
                t(player, "gui.original-click-edit").color(UiTheme.MUTED),
                t(player, "gui.kit-button-hint").color(UiTheme.MUTED)));
        meta.getPersistentDataContainer().set(ItemKeys.guiAction(), PersistentDataType.STRING,
                DelayedButton.wrap("paper:" + slot));
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack barrierItem(Player player, OriginalKitService.Plan plan, int slot) {
        ItemStack stack = new ItemStack(Material.BARRIER);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(t(player, "gui.original-locked").color(UiTheme.DANGER));
        meta.lore(List.of(Component.text(service.barrierLabel(plan, slot), UiTheme.MUTED)
                .decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(ItemKeys.guiAction(), PersistentDataType.STRING, "locked");
        stack.setItemMeta(meta);
        return stack;
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        if (action == null || action.startsWith("locked")) {
            return;
        }
        if (action.startsWith("paper:")) {
            int kitSlot;
            try {
                kitSlot = Integer.parseInt(action.substring(6));
            } catch (NumberFormatException e) {
                return;
            }
            if (slotMenuGui != null) {
                slotMenuGui.open(player, kitSlot);
            }
        }
    }
}
