package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.originalkit.OriginalKitService;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.util.GuiSlots;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

/**
 * First-edit choice for an original kit slot:
 * "新しくキットを作成する" (paper) or "公式キットをコピーする" (diamond).
 */
public final class EkitChoiceGui extends AbstractGui {

    private final OriginalKitService service;
    private final OriginalKitEditGui editGui;
    private final EkitCopyGui copyGui;

    public EkitChoiceGui(GuiSessionRegistry registry, SoundService sounds,
                         OriginalKitService service, OriginalKitEditGui editGui, EkitCopyGui copyGui) {
        super(registry, sounds, GuiType.EKIT_CHOICE, 3, false);
        this.service = service;
        this.editGui = editGui;
        this.copyGui = copyGui;
    }

    public void open(Player player, int kitSlot) {
        GuiSession session = registry.open(player.getUniqueId(), type(), rows);
        session.put("slot", kitSlot);
        PracticeGuiOpen.open(this, player, session);
        sounds.play(player, "gui-open");
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return Component.text("オリジナルキット作成", UiTheme.PRIMARY)
                .decoration(TextDecoration.ITALIC, false);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        MenuScaffold.chrome(inventory);
        inventory.setItem(GuiSlots.slot(1, 2), ItemBuilder.action(Material.PAPER,
                Component.text("新しくキットを作成する", UiTheme.SUCCESS), "create"));
        inventory.setItem(GuiSlots.slot(1, 6), ItemBuilder.action(Material.DIAMOND,
                Component.text("公式キットをコピーする", UiTheme.PRIMARY), "copy"));
        inventory.setItem(GuiSlots.slot(2, 4), ItemBuilder.action(UiTheme.CLOSE,
                Component.text("閉じる", UiTheme.DANGER), "close"));
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        Integer kitSlot = session.get("slot", Integer.class);
        if (kitSlot == null) {
            return;
        }
        switch (action) {
            case "create" -> {
                sounds.play(player, "select");
                service.markNavigating(player.getUniqueId());
                player.closeInventory();
                editGui.open(player, kitSlot, new org.bukkit.inventory.ItemStack[41]);
            }
            case "copy" -> {
                sounds.play(player, "gui-click");
                service.markNavigating(player.getUniqueId());
                player.closeInventory();
                copyGui.open(player, kitSlot);
            }
            case "close" -> {
                service.endEdit(player);
                player.closeInventory();
            }
            default -> {
            }
        }
    }
}
