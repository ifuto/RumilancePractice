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
import com.rumilance.practice.sound.SoundService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * /ekit entry: click a kit to edit immediately. Original kits sit on the bottom-left.
 */
public final class EkitSelectGui extends AbstractGui {

    private final KitService kitService;
    private EditKitGui editKitGui;
    private OriginalKitGui originalKitGui;

    public EkitSelectGui(GuiSessionRegistry registry, SoundService sounds, KitService kitService) {
        super(registry, sounds, GuiType.EKIT_SELECT, 6, false);
        this.kitService = kitService;
    }

    public void setEditKitGui(EditKitGui editKitGui) {
        this.editKitGui = editKitGui;
    }

    public void setOriginalKitGui(OriginalKitGui originalKitGui) {
        this.originalKitGui = originalKitGui;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return t(player, "gui.kit-edit-title").color(UiTheme.PRIMARY);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        MenuScaffold.chrome(inventory);
        MenuScaffold.header(inventory, 0, title(player, session));

        List<KitDefinition> kits = kitService.enabled();
        int shown = Math.min(kits.size(), MenuScaffold.gridPageSize());
        for (int i = 0; i < shown; i++) {
            inventory.setItem(MenuScaffold.gridSlot(i), kitIcon(player, kits.get(i)));
        }
        if (kits.isEmpty()) {
            inventory.setItem(MenuScaffold.gridSlot(13),
                    ItemBuilder.of(Material.BARRIER)
                            .name(t(player, "gui.kit-none").color(UiTheme.MUTED))
                            .lore(UiTheme.line(line(player, "gui.kit-none-lore")))
                            .action("decorate")
                            .build());
        }

        inventory.setItem(com.rumilance.practice.util.GuiSlots.slot(5, 1), originalPaper(player));
        paintNav(player, session, inventory);
    }

    private ItemStack kitIcon(Player player, KitDefinition kit) {
        Material material = Material.matchMaterial(kit.icon());
        return ItemBuilder.of(material == null ? Material.DIAMOND_SWORD : material)
                .name(MiniMessage.miniMessage().deserialize(kit.prettyDisplayName())
                        .decoration(TextDecoration.ITALIC, false))
                .lore(UiTheme.divider(),
                        UiTheme.line(line(player, "gui.kit-edit-hint")),
                        UiTheme.blank(),
                        UiTheme.hint(line(player, "menu.click")))
                .action("kit:" + kit.name())
                .build();
    }

    private ItemStack originalPaper(Player player) {
        return ItemBuilder.of(Material.PAPER)
                .name(t(player, "gui.original-kit").color(UiTheme.DANGER))
                .lore(UiTheme.divider(),
                        UiTheme.line(line(player, "gui.original-kit-lore")),
                        UiTheme.blank(),
                        UiTheme.hint(line(player, "menu.click")))
                .action("original")
                .build();
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        if ("close".equals(action) || "back".equals(action)) {
            sounds.play(player, "gui-back");
            player.closeInventory();
            return;
        }
        if ("original".equals(action)) {
            sounds.play(player, "select");
            session.setNavigatingAway(true);
            if (originalKitGui != null) {
                originalKitGui.open(player);
            }
            return;
        }
        if (action != null && action.startsWith("kit:")) {
            String kitId = action.substring(4);
            sounds.play(player, "select");
            session.setNavigatingAway(true);
            if (editKitGui != null) {
                editKitGui.openKitEditor(player, kitId);
            }
        }
    }
}
