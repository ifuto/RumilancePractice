package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.kit.KitService;
import com.rumilance.practice.model.KitDefinition;
import com.rumilance.practice.model.KitItemEntry;
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
    protected Component title(Player player, GuiSession session) {
        String kit = session.selectedKit() == null
                ? "Kit"
                : com.rumilance.practice.util.KitNames.pretty(session.selectedKit());
        return Component.text("Preview: " + kit, UiTheme.PRIMARY).decoration(TextDecoration.ITALIC, false);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        MenuScaffold.chrome(inventory);

        KitDefinition kit = session.selectedKit() == null
                ? kitService.enabled().stream().findFirst().orElse(null)
                : kitService.get(session.selectedKit()).orElse(null);
        if (kit == null) {
            inventory.setItem(MenuScaffold.gridSlot(13),
                    ItemBuilder.of(Material.BARRIER)
                            .name(Component.text("No kit selected", UiTheme.DANGER))
                            .action("decorate")
                            .build());
            MenuScaffold.closeButton(inventory);
            return;
        }

        MenuScaffold.header(inventory, 0, title(player, session));

        // Armor + off-hand across the top bar (slots 0..4 mapped to inventory slots).
        placeArmor(inventory, kit);

        // Storage items in rows 1-3 (inventory slots 9..35 => menu rows 1-3, same columns).
        for (KitItemEntry entry : kit.items()) {
            if (entry.slot() < 9 || entry.slot() > 35) {
                continue;
            }
            ItemStack stack = previewStack(entry);
            if (stack != null) {
                inventory.setItem(entry.slot(), stack);
            }
        }

        // Hot-bar preview on row 4 columns 0..8 (slots 36..44).
        for (KitItemEntry entry : kit.items()) {
            if (entry.slot() < 0 || entry.slot() > 8) {
                continue;
            }
            ItemStack stack = previewStack(entry);
            if (stack != null) {
                inventory.setItem(GuiSlots.slot(4, entry.slot()), stack);
            }
        }

        // Info panel on row 5 (close already placed by chrome; add a description book next to it).
        inventory.setItem(GuiSlots.slot(5, 2),
                ItemBuilder.of(Material.WRITTEN_BOOK)
                        .name(Component.text(com.rumilance.practice.util.KitNames.pretty(kit.name()), UiTheme.SECONDARY))
                        .lore(
                                UiTheme.divider(),
                                UiTheme.labelValue("Health", String.valueOf((int) kit.maxHealth())),
                                UiTheme.labelValue("Natural regen", kit.naturalHealthRegen() ? "Yes" : "No"),
                                UiTheme.labelValue("Arena", kit.hasFixedArena()
                                        ? com.rumilance.practice.util.KitNames.pretty(kit.arenaName()) : "Random"),
                                kit.timeoutSeconds() > 0
                                        ? UiTheme.labelValue("Timeout", kit.timeoutSeconds() + "s")
                                        : UiTheme.line("No timeout")
                        )
                        .action("decorate")
                        .build());

        inventory.setItem(GuiSlots.slot(5, 6),
                ItemBuilder.action(Material.ARROW,
                        Component.text("Back", UiTheme.WARNING), "back"));
        MenuScaffold.closeButton(inventory);
    }

    private void placeArmor(Inventory inventory, KitDefinition kit) {
        // Render a player-doll layout: helmet=5, chestplate=6, leggings=7, boots=8, offhand=4.
        placeArmorPiece(inventory, GuiSlots.slot(0, 5), kit.armor().get("helmet"));
        placeArmorPiece(inventory, GuiSlots.slot(0, 6), kit.armor().get("chestplate"));
        placeArmorPiece(inventory, GuiSlots.slot(0, 7), kit.armor().get("leggings"));
        placeArmorPiece(inventory, GuiSlots.slot(0, 8), kit.armor().get("boots"));
    }

    private void placeArmorPiece(Inventory inventory, int slot, String materialName) {
        if (materialName == null || materialName.isBlank()) {
            return;
        }
        // Full-NBT armor pieces are stored as "data:<base64>".
        if (materialName.startsWith("data:")) {
            ItemStack decoded = com.rumilance.practice.util.ItemSerializer
                    .singleFromBase64(materialName.substring("data:".length()));
            if (decoded != null) {
                inventory.setItem(slot, decoded);
                return;
            }
        }
        Material material = Material.matchMaterial(materialName);
        if (material == null || material.isAir()) {
            return;
        }
        inventory.setItem(slot, new ItemStack(material));
    }

    /** Preview stack with full NBT when available (enchant glint, potion colours, ...). */
    private static ItemStack previewStack(KitItemEntry entry) {
        if (entry.hasSerializedItem()) {
            ItemStack decoded = com.rumilance.practice.util.ItemSerializer
                    .singleFromBase64(entry.itemDataBase64());
            if (decoded != null) {
                return decoded;
            }
        }
        Material material = Material.matchMaterial(entry.material());
        if (material == null || material.isAir()) {
            return null;
        }
        return new ItemStack(material, Math.max(1, entry.amount()));
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
