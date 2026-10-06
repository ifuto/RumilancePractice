package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiFrame;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.util.GuiSlots;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.function.Consumer;

/**
 * The "Arena / FFA" section of {@code /admin}.
 *
 * <p>Three doors, a column apart: the arena registry (browse, copy, set the source), the FFA
 * arena settings (per-arena toggles such as the FreeHit guard and LFF), and the kit editor's
 * admin side. Destinations are injected, so the page stays usable if one of them is not wired.</p>
 */
public final class ArenaFfaGui extends AbstractGui {

    private Consumer<Player> openArenas = NOOP;
    private Consumer<Player> openFfaSettings = NOOP;
    private Consumer<Player> openKitAdmin = NOOP;
    private Consumer<Player> onBack = NOOP;

    private static final Consumer<Player> NOOP = player -> { };

    public ArenaFfaGui(GuiSessionRegistry registry, SoundService sounds) {
        super(registry, sounds, GuiType.ARENA_FFA_ADMIN, 6, false);
    }

    public void setOpenArenas(Consumer<Player> opener) {
        this.openArenas = orNoop(opener);
    }

    public void setOpenFfaSettings(Consumer<Player> opener) {
        this.openFfaSettings = orNoop(opener);
    }

    public void setOpenKitAdmin(Consumer<Player> opener) {
        this.openKitAdmin = orNoop(opener);
    }

    public void setOnBack(Consumer<Player> onBack) {
        this.onBack = orNoop(onBack);
    }

    @Override
    protected GuiFrame.Theme theme() {
        return GuiFrame.Theme.GREEN;
    }

    @Override
    protected Material titleIcon() {
        return Material.GRASS_BLOCK;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return Component.text("Arena / FFA", UiTheme.HEADER);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        paintFrame(player, session, inventory);
        inventory.setItem(GuiSlots.slot(2, 1), tile(Material.CHEST, "Arenas",
                "Browse the arena registry, make copies, set the source.", "arenas"));
        inventory.setItem(GuiSlots.slot(2, 4), tile(Material.COMPARATOR, "FFA Settings",
                "Per-arena FFA toggles: bot, FreeHit guard, LFF.", "ffa"));
        inventory.setItem(GuiSlots.slot(2, 7), tile(Material.DIAMOND_SWORD, "Kits",
                "Kit editor — layouts, presets and inner kits.", "kits"));
        inventory.setItem(GuiSlots.slot(5, 4), ItemBuilder.of(UiTheme.BACK)
                .name(Component.text("Back", UiTheme.WARNING))
                .action("back")
                .build());
    }

    private org.bukkit.inventory.ItemStack tile(Material material, String name, String lore,
                                                String action) {
        return ItemBuilder.of(material)
                .name(Component.text(name, UiTheme.HEADER))
                .lore(UiTheme.divider(),
                        UiTheme.line(lore),
                        UiTheme.blank(),
                        UiTheme.hint("Click to open"))
                .action(action)
                .build();
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot,
                            String action, org.bukkit.event.inventory.ClickType clickType) {
        if ("back".equals(action) || "close".equals(action)) {
            sounds.play(player, "gui-back");
            player.closeInventory();
            if ("back".equals(action)) {
                onBack.accept(player);
            }
            return;
        }
        Consumer<Player> target = switch (action == null ? "" : action) {
            case "arenas" -> openArenas;
            case "ffa" -> openFfaSettings;
            case "kits" -> openKitAdmin;
            default -> null;
        };
        if (target == null) {
            return;
        }
        sounds.play(player, "gui-click");
        player.closeInventory();
        target.accept(player);
    }

    private static Consumer<Player> orNoop(Consumer<Player> opener) {
        return opener == null ? NOOP : opener;
    }
}
