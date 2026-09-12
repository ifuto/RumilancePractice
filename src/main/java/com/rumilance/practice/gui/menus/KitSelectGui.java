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
import com.rumilance.practice.util.KitNames;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class KitSelectGui extends AbstractGui {

    private final KitService kitService;
    private DuelRequestGui duelRequestGui;

    public KitSelectGui(GuiSessionRegistry registry, SoundService sounds, KitService kitService) {
        super(registry, sounds, GuiType.KIT_SELECT, 6, true);
        this.kitService = kitService;
    }

    public void setDuelRequestGui(DuelRequestGui duelRequestGui) {
        this.duelRequestGui = duelRequestGui;
    }

    public void openFor(Player player, GuiSession parent) {
        GuiSession session = registry.open(player.getUniqueId(), type(), rows);
        session.setRanked(parent.ranked());
        session.setTargetPlayer(parent.targetPlayer());
        session.setSelectedKit(parent.selectedKit());
        session.setSelectedMap(parent.selectedMap());
        session.setBestOf(parent.bestOf());
        session.setFromBattleMenu(parent.fromBattleMenu());
        PracticeGuiOpen.open(this, player, session);
        sounds.play(player, "gui-open");
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
        return t(player, "gui.kit-select-title").color(UiTheme.PRIMARY);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        paintFrame(player, session, inventory);

        // Two labelled sections: row 1 = Main Kits (azalea/nature header), row 2 = Sub Kits
        // (bolted iron-trapdoor header); rows 3-4 continue the Main line-up when it is long.
        List<KitDefinition> main = kitService.enabled(com.rumilance.practice.model.KitCategory.MAIN);
        List<KitDefinition> sub = kitService.enabled(com.rumilance.practice.model.KitCategory.SUB);
        String current = session.selectedKit();

        int cell = 0;
        inventory.setItem(MenuScaffold.gridSlot(cell++),
                com.rumilance.practice.gui.KitSections.header(
                        com.rumilance.practice.model.KitCategory.MAIN, main.size(),
                        line(player, "gui.kit-click-select")));
        for (KitDefinition kit : main) {
            if (cell >= 7) {
                break; // row 1: header column + up to 6 main kits
            }
            inventory.setItem(MenuScaffold.gridSlot(cell++), kitIcon(player, session, kit, current));
        }
        if (!sub.isEmpty()) {
            inventory.setItem(MenuScaffold.gridSlot(cell++),
                    com.rumilance.practice.gui.KitSections.header(
                            com.rumilance.practice.model.KitCategory.SUB, sub.size(),
                            line(player, "gui.kit-click-select")));
            for (KitDefinition kit : sub) {
                if (cell >= 14) {
                    break; // row 2: header column + up to 6 sub kits
                }
                inventory.setItem(MenuScaffold.gridSlot(cell++), kitIcon(player, session, kit, current));
            }
        }
        for (KitDefinition kit : main.subList(Math.min(6, main.size()), main.size())) {
            if (cell >= MenuScaffold.gridPageSize()) {
                break; // rows 3-4 continue the main line-up
            }
            inventory.setItem(MenuScaffold.gridSlot(cell++), kitIcon(player, session, kit, current));
        }

        MenuScaffold.returnButton(inventory, t(player, "menu.back"));
    }

    private ItemStack kitIcon(Player player, GuiSession session, KitDefinition kit, String current) {
        Material mat = Material.matchMaterial(kit.icon());
        boolean selected = kit.name().equalsIgnoreCase(current);
        return ItemBuilder.of(mat == null ? Material.DIAMOND_SWORD : mat)
                .name(Component.text(KitNames.pretty(kit.name()),
                        selected ? UiTheme.SUCCESS : UiTheme.VALUE))
                .lore(
                        UiTheme.divider(),
                        UiTheme.line(kit.prettyDisplayName()),
                        UiTheme.blank(),
                        selected
                                ? UiTheme.status(line(player, "gui.kit-selected"), UiTheme.SUCCESS)
                                : UiTheme.hint(line(player, "gui.kit-click-select"))
                )
                .glint(selected)
                .action("pick:" + kit.name())
                .build();
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        if ("back".equals(action) || "close".equals(action)) {
            sounds.play(player, "gui-back");
            returnToDuel(player, session);
            return;
        }
        if (action != null && action.startsWith("pick:")) {
            session.setSelectedKit(action.substring(5));
            sounds.play(player, "select");
            returnToDuel(player, session);
        }
    }

    private void returnToDuel(Player player, GuiSession session) {
        Player target = session.targetPlayer() == null ? null : org.bukkit.Bukkit.getPlayer(session.targetPlayer());
        if (target == null || duelRequestGui == null) {
            player.closeInventory();
            return;
        }
        String kit = session.selectedKit();
        String map = session.selectedMap();
        int bestOf = session.bestOf();
        boolean ranked = session.ranked();
        boolean fromBattle = session.fromBattleMenu();
        player.closeInventory();
        // Pass the choices into openFor so they are applied to the new session BEFORE render.
        duelRequestGui.openFor(player, target, ranked, kit, map, bestOf);
        registry.get(player.getUniqueId()).ifPresent(s -> s.setFromBattleMenu(fromBattle));
    }
}
