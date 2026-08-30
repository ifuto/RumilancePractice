package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.originalkit.OriginalKitService;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.util.GuiSlots;
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
 */
public final class OriginalKitGui extends AbstractGui {

    private final OriginalKitService service;
    private ConfirmGui confirmGui;
    private OriginalKitEditGui editGui;
    private EkitChoiceGui choiceGui;

    public OriginalKitGui(GuiSessionRegistry registry, SoundService sounds, OriginalKitService service) {
        super(registry, sounds, GuiType.ORIGINAL_KIT, 5, false);
        this.service = service;
    }

    public void setConfirmGui(ConfirmGui confirmGui) {
        this.confirmGui = confirmGui;
    }

    public void setEditGui(OriginalKitEditGui editGui) {
        this.editGui = editGui;
    }

    public void setChoiceGui(EkitChoiceGui choiceGui) {
        this.choiceGui = choiceGui;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return t(player, "gui.original-title").color(UiTheme.PRIMARY);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        MenuScaffold.chrome(inventory);
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
                t(player, "gui.original-click-edit").color(UiTheme.MUTED)));
        meta.getPersistentDataContainer().set(ItemKeys.guiAction(), PersistentDataType.STRING, "paper:" + slot);
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
            int kitSlot = Integer.parseInt(action.substring(6));
            OriginalKitService.Plan plan = service.planOf(player);
            if (service.canEditWithoutConfirm(plan) || confirmGui == null) {
                startEdit(player, kitSlot);
                return;
            }
            confirmGui.open(
                    player,
                    t(player, "gui.original-confirm").color(UiTheme.DANGER),
                    List.of(t(player, "gui.original-remaining",
                            com.rumilance.practice.locale.MessageService.tags(
                                    "n", service.remainingEditsLabel(player)))),
                    p -> startEdit(p, kitSlot),
                    p -> originalGuiOpen(p)
            );
        }
    }

    private void originalGuiOpen(Player player) {
        open(player);
    }

    private void startEdit(Player player, int kitSlot) {
        service.stashInventory(player);
        if (!service.hasSaved(player.getUniqueId(), kitSlot)) {
            if (choiceGui != null) {
                choiceGui.open(player, kitSlot);
            }
            return;
        }
        if (editGui != null) {
            editGui.open(player, kitSlot, service.loadLayout(player.getUniqueId(), kitSlot));
        }
    }
}
