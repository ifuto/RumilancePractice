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
 * Per-slot hub after picking a paper: build the loadout in the physical kit room, or open the
 * big settings screen for that slot. Only the plan-unlocked path reaches here, so both entries
 * are always available.
 */
public final class OriginalKitSlotMenuGui extends AbstractGui {

    private final OriginalKitService service;
    private OriginalKitSettingsGui settingsGui;

    public OriginalKitSlotMenuGui(GuiSessionRegistry registry, SoundService sounds,
                                  OriginalKitService service) {
        super(registry, sounds, GuiType.ORIGINAL_KIT_SLOT_MENU, 3, false);
        this.service = service;
    }

    public void setSettingsGui(OriginalKitSettingsGui settingsGui) {
        this.settingsGui = settingsGui;
    }

    public void open(Player player, int kitSlot) {
        GuiSession session = registry.open(player.getUniqueId(), type(), rows);
        session.put("slot", kitSlot);
        PracticeGuiOpen.open(this, player, session);
        sounds.play(player, "gui-open");
    }

    @Override
    protected com.rumilance.practice.gui.GuiFrame.Theme theme() {
        return com.rumilance.practice.gui.GuiFrame.Theme.YELLOW;
    }

    @Override
    protected Material titleIcon() {
        return Material.PAPER;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return t(player, "gui.original-slot-menu-title", com.rumilance.practice.locale.MessageService.tags(
                "slot", String.valueOf(slotOf(session) + 1))).color(UiTheme.PRIMARY);
    }

    private int slotOf(GuiSession session) {
        Integer slot = session.get("slot", Integer.class);
        return slot == null ? 22 : slot;
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        paintFrame(player, session, inventory);
        int kitSlot = slotOf(session);

        boolean saved = service.hasSaved(player.getUniqueId(), kitSlot);
        inventory.setItem(GuiSlots.slot(1, 3),
                ItemBuilder.of(saved ? Material.CRAFTING_TABLE : Material.CRAFTING_TABLE)
                        .name(t(player, "gui.original-edit-loadout").color(UiTheme.SUCCESS)
                                .decoration(TextDecoration.ITALIC, false))
                        .lore(UiTheme.divider(),
                                UiTheme.line(line(player, "gui.original-edit-loadout-lore")),
                                UiTheme.blank(),
                                UiTheme.hint(line(player, "gui.toggle-hint")))
                        .action("edit:" + kitSlot)
                        .build());
        inventory.setItem(GuiSlots.slot(1, 5),
                ItemBuilder.of(Material.COMPARATOR)
                        .name(t(player, "gui.original-open-settings").color(UiTheme.VALUE)
                                .decoration(TextDecoration.ITALIC, false))
                        .lore(UiTheme.divider(),
                                UiTheme.line(line(player, "gui.original-open-settings-lore")),
                                UiTheme.blank(),
                                UiTheme.hint(line(player, "gui.toggle-hint")))
                        .action("settings:" + kitSlot)
                        .build());

        MenuScaffold.returnButton(inventory, t(player, "menu.back"));
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        if (action == null) {
            return;
        }
        if (action.equals("close") || action.equals("back")) {
            sounds.play(player, "gui-back");
            player.closeInventory();
            return;
        }
        if (action.startsWith("edit:")) {
            int kitSlot = parseSlot(action);
            // Stash lobby inventory, then send to the physical room (creative after teleport).
            service.enterRoomEditor(player, kitSlot, service.loadLayout(player.getUniqueId(), kitSlot));
            return;
        }
        if (action.startsWith("settings:")) {
            int kitSlot = parseSlot(action);
            if (settingsGui != null) {
                settingsGui.open(player, kitSlot);
            }
        }
    }

    private static int parseSlot(String action) {
        int colon = action.indexOf(':');
        try {
            return Integer.parseInt(action.substring(colon + 1));
        } catch (NumberFormatException | StringIndexOutOfBoundsException e) {
            return 22;
        }
    }
}
