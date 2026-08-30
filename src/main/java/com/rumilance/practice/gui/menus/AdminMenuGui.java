package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.util.GuiSlots;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.function.Consumer;

/**
 * OP setup hub. Prefer this UI over typing admin subcommands for day-to-day kit/preset work.
 */
public final class AdminMenuGui extends AbstractGui {

    private Consumer<Player> openKitAdmin = p -> { };
    private Consumer<Player> openPresetAdmin = p -> { };
    private Consumer<Player> openEkitAdmin = p -> { };

    public AdminMenuGui(GuiSessionRegistry registry, SoundService sounds) {
        super(registry, sounds, GuiType.ADMIN_MENU, 6, false);
    }

    public void setOpenKitAdmin(Consumer<Player> openKitAdmin) {
        this.openKitAdmin = openKitAdmin == null ? p -> { } : openKitAdmin;
    }

    public void setOpenPresetAdmin(Consumer<Player> openPresetAdmin) {
        this.openPresetAdmin = openPresetAdmin == null ? p -> { } : openPresetAdmin;
    }

    public void setOpenEkitAdmin(Consumer<Player> openEkitAdmin) {
        this.openEkitAdmin = openEkitAdmin == null ? p -> { } : openEkitAdmin;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return t(player, "gui.admin-title").color(NamedTextColor.AQUA);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        MenuScaffold.chrome(inventory);

        inventory.setItem(GuiSlots.slot(0, 4), ItemBuilder.of(Material.NETHER_STAR)
                .name(t(player, "gui.admin-hub").color(NamedTextColor.AQUA))
                .lore(
                        UiTheme.divider(),
                        UiTheme.line(line(player, "gui.admin-hub-lore-1")),
                        UiTheme.line(line(player, "gui.admin-hub-lore-2"))
                )
                .glint(true)
                .action("decorate")
                .build());

        inventory.setItem(GuiSlots.slot(2, 2), ItemBuilder.of(Material.DIAMOND_SWORD)
                .name(t(player, "gui.admin-kits").color(NamedTextColor.AQUA))
                .lore(
                        UiTheme.divider(),
                        UiTheme.line(line(player, "gui.admin-kits-lore-1")),
                        UiTheme.line(line(player, "gui.admin-kits-lore-2")),
                        UiTheme.blank(),
                        UiTheme.hint(line(player, "menu.click"))
                )
                .action("kits")
                .build());

        inventory.setItem(GuiSlots.slot(2, 4), ItemBuilder.of(Material.CHEST)
                .name(t(player, "gui.admin-presets").color(NamedTextColor.YELLOW))
                .lore(
                        UiTheme.divider(),
                        UiTheme.line(line(player, "gui.admin-presets-lore-1")),
                        UiTheme.line(line(player, "gui.admin-presets-lore-2")),
                        UiTheme.blank(),
                        UiTheme.hint(line(player, "menu.click"))
                )
                .action("presets")
                .build());

        inventory.setItem(GuiSlots.slot(2, 6), ItemBuilder.of(Material.BOOK)
                .name(t(player, "gui.admin-original").color(NamedTextColor.GREEN))
                .lore(
                        UiTheme.divider(),
                        UiTheme.line(line(player, "gui.admin-original-lore")),
                        UiTheme.blank(),
                        UiTheme.hint(line(player, "menu.click"))
                )
                .action("ekitadmin")
                .build());

        inventory.setItem(GuiSlots.slot(4, 4), ItemBuilder.of(Material.BLAZE_ROD)
                .name(t(player, "gui.admin-wand").color(NamedTextColor.GOLD))
                .lore(
                        UiTheme.divider(),
                        UiTheme.line(line(player, "gui.admin-wand-lore-1")),
                        UiTheme.line(line(player, "gui.admin-wand-lore-2")),
                        UiTheme.line(line(player, "gui.admin-wand-lore-3"))
                )
                .action("noop")
                .build());

        inventory.setItem(GuiSlots.slot(5, 4), ItemBuilder.of(Material.BARRIER)
                .name(t(player, "menu.close").color(NamedTextColor.RED))
                .action("close")
                .build());
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        if (action == null || "noop".equals(action) || "decorate".equals(action)) {
            return;
        }
        switch (action) {
            case "close" -> player.closeInventory();
            case "kits" -> {
                sounds.play(player, "gui-click");
                openKitAdmin.accept(player);
            }
            case "presets" -> {
                sounds.play(player, "gui-click");
                openPresetAdmin.accept(player);
            }
            case "ekitadmin" -> {
                sounds.play(player, "gui-click");
                openEkitAdmin.accept(player);
            }
            default -> { }
        }
    }
}
