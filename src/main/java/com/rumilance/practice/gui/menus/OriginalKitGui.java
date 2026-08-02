package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiDecorator;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.originalkit.OriginalKitService;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.util.GuiSlots;
import com.rumilance.practice.util.ItemKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
        return Component.text("オリジナルキット", NamedTextColor.WHITE);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        OriginalKitService.Plan plan = service.planOf(player);
        for (int slot = 0; slot < 45; slot++) {
            if (slot == 44) {
                continue; // 5行9列目は常に空
            }
            if (service.isSlotUnlocked(plan, slot)) {
                inventory.setItem(slot, paperItem(player, slot));
            } else {
                inventory.setItem(slot, barrierItem(plan, slot));
            }
        }
    }

    private ItemStack paperItem(Player player, int slot) {
        ItemStack stack = new ItemStack(Material.PAPER);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text("オリジナルキット", NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        String saved = service.hasSaved(player.getUniqueId(), slot) ? "保存済み" : "未作成";
        meta.lore(List.of(
                Component.text("スロット " + (slot + 1) + " (" + saved + ")", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("クリックして編集", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(ItemKeys.guiAction(), PersistentDataType.STRING, "paper:" + slot);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack barrierItem(OriginalKitService.Plan plan, int slot) {
        ItemStack stack = new ItemStack(Material.BARRIER);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text("ロック中", NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text(service.barrierLabel(plan, slot), NamedTextColor.GRAY)
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
                    Component.text("本当にこのキットを編集しますか？", NamedTextColor.RED),
                    List.of(Component.text("今月の残り編集可能回数 : " + service.remainingEditsLabel(player),
                            NamedTextColor.GRAY)),
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
