package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.kit.KitService;
import com.rumilance.practice.kit.KitLoadout;
import com.rumilance.practice.model.KitDefinition;
import com.rumilance.practice.locale.MessageService;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.util.GuiSlots;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Read-only preview of a kit's contents. The kit's items/armor are rendered into a 6-row menu
 * that mirrors the player's inventory layout (armor and off-hand across the top row, storage
 * in rows 2-4, hot-bar on row 5); chrome fills the rest. The preview is non-interactive and
 * only offers a back button.
 */
public final class KitPreviewGui extends AbstractGui {

    private final KitService kitService;

    public KitPreviewGui(GuiSessionRegistry registry, SoundService sounds, KitService kitService) {
        super(registry, sounds, GuiType.KIT_PREVIEW, 6, true);
        this.kitService = kitService;
    }

    @Override
    protected void configureSession(GuiSession session, Player player) {
        if (session.selectedKit() == null) {
            kitService.enabled().stream().findFirst().ifPresent(k -> session.setSelectedKit(k.name()));
        }
    }

    @Override
    protected com.rumilance.practice.gui.GuiFrame.Theme theme() {
        return com.rumilance.practice.gui.GuiFrame.Theme.YELLOW;
    }

    @Override
    protected Material titleIcon() {
        return Material.DIAMOND_SWORD;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        String kit = session.selectedKit() == null
                ? "Kit"
                : com.rumilance.practice.util.KitNames.pretty(session.selectedKit());
        return t(player, "gui.preview-title", MessageService.tags("kit", kit)).color(UiTheme.PRIMARY);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        paintFrame(player, session, inventory);

        KitDefinition kit = session.selectedKit() == null
                ? kitService.enabled().stream().findFirst().orElse(null)
                : kitService.get(session.selectedKit()).orElse(null);
        if (kit == null) {
            inventory.setItem(MenuScaffold.gridSlot(13),
                    ItemBuilder.of(Material.BARRIER)
                            .name(t(player, "gui.preview-none").color(UiTheme.DANGER))
                            .action("decorate")
                            .build());
            MenuScaffold.closeButton(inventory, t(player, "menu.close"));
            return;
        }

        // Render the EXACT loadout the match gives: older kits store armor in the `armor` map,
        // child kits created from the official editor store it in item slots 36..40. Both ways
        // are resolved by KitLoadout.fromOfficial (including full-NBT potion/armor stacks).
        ItemStack[] loadout = KitLoadout.fromOfficial(kit);
        inventory.setItem(GuiSlots.slot(0, 3), loadout[KitLoadout.OFFHAND]);
        inventory.setItem(GuiSlots.slot(0, 5), loadout[KitLoadout.HELMET]);
        inventory.setItem(GuiSlots.slot(0, 6), loadout[KitLoadout.CHEST]);
        inventory.setItem(GuiSlots.slot(0, 7), loadout[KitLoadout.LEGS]);
        inventory.setItem(GuiSlots.slot(0, 8), loadout[KitLoadout.BOOTS]);
        for (int slot = 9; slot < 36; slot++) {
            if (loadout[slot] != null) {
                inventory.setItem(slot, loadout[slot]);
            }
        }
        for (int slot = 0; slot < 9; slot++) {
            if (loadout[slot] != null) {
                inventory.setItem(GuiSlots.slot(4, slot), loadout[slot]);
            }
        }

        // Info panel on row 5 (close already placed by chrome; add a description book next to it).
        inventory.setItem(GuiSlots.slot(5, 2),
                ItemBuilder.of(Material.WRITTEN_BOOK)
                        .name(Component.text(com.rumilance.practice.util.KitNames.pretty(kit.name()), UiTheme.SECONDARY))
                        .lore(
                                UiTheme.divider(),
                                UiTheme.labelValue(line(player, "gui.preview-health"), String.valueOf((int) kit.maxHealth())),
                                UiTheme.labelValue(line(player, "gui.preview-regen"),
                                        kit.naturalHealthRegen() ? line(player, "menu.yes") : line(player, "menu.no")),
                                UiTheme.labelValue(line(player, "gui.preview-arena"), kit.hasFixedArena()
                                        ? com.rumilance.practice.util.KitNames.pretty(kit.arenaName())
                                        : line(player, "gui.queue-random")),
                                kit.timeoutSeconds() > 0
                                        ? UiTheme.labelValue(line(player, "gui.preview-timeout"), kit.timeoutSeconds() + "s")
                                        : UiTheme.line(line(player, "gui.preview-no-timeout"))
                        )
                        .action("decorate")
                        .build());

        MenuScaffold.closeButton(inventory, t(player, "menu.close"));
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        // The preview is read-only; chrome decorates already cancel clicks via GuiListener.
        if ("back".equals(action)) {
            sounds.play(player, "gui-back");
            player.closeInventory();
        } else if ("close".equals(action)) {
            sounds.play(player, "gui-back");
            player.closeInventory();
        }
    }
}
