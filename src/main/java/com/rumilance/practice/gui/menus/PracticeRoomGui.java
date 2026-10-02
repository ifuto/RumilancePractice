package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.model.PracticeRoom;
import com.rumilance.practice.practice.PracticeService;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.util.GuiSlots;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.List;

/**
 * Practice room browser — lists enabled practice rooms as clickable icons.
 * Click to join, "leave" button at the bottom.
 * Opened when {@code /prac} is used with no arguments.
 */
public final class PracticeRoomGui extends AbstractGui {

    private final PracticeService practiceService;

    public PracticeRoomGui(GuiSessionRegistry registry, SoundService sounds,
                           PracticeService practiceService) {
        super(registry, sounds, GuiType.PRACTICE_SELECT, 3, false);
        this.practiceService = practiceService;
    }

    @Override
    protected com.rumilance.practice.gui.GuiFrame.Theme theme() {
        return com.rumilance.practice.gui.GuiFrame.Theme.GREEN;
    }

    @Override
    protected Material titleIcon() {
        return Material.IRON_SWORD;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return Component.text("Practice Rooms", UiTheme.PRIMARY)
                .decoration(TextDecoration.ITALIC, false);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        paintFrame(player, session, inventory);

        List<PracticeRoom> rooms = practiceService.enabled();
        if (rooms.isEmpty()) {
            inventory.setItem(GuiSlots.slot(1, 4), ItemBuilder.of(Material.BARRIER)
                    .name(Component.text("No practice rooms available", NamedTextColor.RED))
                    .action("none").build());
            MenuScaffold.closeButton(inventory, t(player, "menu.close"));
            return;
        }

        int col = 1;
        int row = 1;
        for (PracticeRoom room : rooms) {
            if (col > 7) { col = 1; row++; }
            if (row > 2) break; // max 14 rooms

            Material icon = typeIcon(room.type());
            inventory.setItem(GuiSlots.slot(row, col), ItemBuilder.of(icon)
                    .name(Component.text(room.displayName(), UiTheme.VALUE)
                            .decoration(TextDecoration.ITALIC, false))
                    .lore(
                            UiTheme.divider(),
                            UiTheme.labelValue("Type", room.type().name()),
                            UiTheme.blank(),
                            UiTheme.hint(line(player, "menu.click"))
                    )
                    .action("join:" + room.id()).build());
            col++;
        }

        // Leave button
        inventory.setItem(GuiSlots.slot(2, 7), ItemBuilder.of(Material.RED_DYE)
                .name(Component.text("Leave", NamedTextColor.RED))
                .lore(UiTheme.divider(), UiTheme.hint(line(player, "menu.click")))
                .action("leave").build());

        MenuScaffold.closeButton(inventory, t(player, "menu.close"));
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory,
                            int slot, String action) {
        if ("close".equals(action)) {
            player.closeInventory();
            return;
        }
        if ("leave".equals(action)) {
            practiceService.leave(player, true);
            sounds.play(player, "gui-click");
            player.closeInventory();
            return;
        }
        if (action != null && action.startsWith("join:")) {
            String roomId = action.substring("join:".length());
            practiceService.join(player, roomId);
            sounds.play(player, "gui-click");
            player.closeInventory();
        }
    }

    private static Material typeIcon(com.rumilance.practice.practice.PracticeType type) {
        return switch (type) {
            case SWORD -> Material.IRON_SWORD;
            case CRYSTAL -> Material.END_CRYSTAL;
            case MACE -> Material.MACE;
            case NETHERITE_POT -> Material.SPLASH_POTION;
            case CART -> Material.MINECART;
            case ANKER -> Material.TOTEM_OF_UNDYING;
        };
    }
}