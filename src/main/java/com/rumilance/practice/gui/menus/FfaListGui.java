package com.rumilance.practice.gui.menus;

import com.rumilance.practice.ffa.FfaService;
import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiDecorator;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
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

public final class FfaListGui extends AbstractGui {

    private final FfaService ffaService;

    public FfaListGui(GuiSessionRegistry registry, SoundService sounds, FfaService ffaService) {
        super(registry, sounds, GuiType.FFA_LIST, 4, false);
        this.ffaService = ffaService;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return Component.text("FFA Arenas", NamedTextColor.GREEN).decorate(TextDecoration.BOLD);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        GuiDecorator.decorateBorder(inventory, false);
        int index = 0;
        for (FfaService.FfaArena arena : ffaService.list()) {
            if (index >= 14) {
                break;
            }
            ItemStack icon = new ItemStack(arena.enabled() ? Material.IRON_SWORD : Material.BARRIER);
            ItemMeta meta = icon.getItemMeta();
            meta.displayName(Component.text(arena.id(), NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Component.text("Kit: " + arena.kitId(), NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text(arena.enabled() ? "Click to join" : "Disabled",
                                    arena.enabled() ? NamedTextColor.GREEN : NamedTextColor.RED)
                            .decoration(TextDecoration.ITALIC, false)
            ));
            meta.getPersistentDataContainer().set(ItemKeys.guiAction(), PersistentDataType.STRING,
                    "ffa:" + arena.id());
            icon.setItemMeta(meta);
            inventory.setItem(GuiSlots.slot(1 + index / 7, 1 + index % 7), icon);
            index++;
        }
        inventory.setItem(GuiSlots.slot(3, 4), GuiDecorator.button(Material.RED_STAINED_GLASS_PANE,
                Component.text("Close", NamedTextColor.RED), "close"));
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        if ("close".equals(action)) {
            player.closeInventory();
            return;
        }
        if (action.startsWith("ffa:")) {
            player.closeInventory();
            ffaService.join(player, action.substring(4));
        }
    }
}
