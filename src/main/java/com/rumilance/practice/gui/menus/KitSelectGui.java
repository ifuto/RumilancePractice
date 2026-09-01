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
    protected Component title(Player player, GuiSession session) {
        return t(player, "gui.kit-select-title").color(UiTheme.PRIMARY);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        MenuScaffold.chrome(inventory);
        MenuScaffold.header(inventory, 0, title(player, session));

        List<KitDefinition> kits = new ArrayList<>();
        kitService.enabled().forEach(kits::add);
        String current = session.selectedKit();
        int placed = 0;
        for (KitDefinition kit : kits) {
            if (placed >= MenuScaffold.gridPageSize()) {
                break;
            }
            Material mat = Material.matchMaterial(kit.icon());
            boolean selected = kit.name().equalsIgnoreCase(current);
            inventory.setItem(MenuScaffold.gridSlot(placed++),
                    ItemBuilder.of(mat == null ? Material.DIAMOND_SWORD : mat)
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
                            .build());
        }

        MenuScaffold.returnButton(inventory, t(player, "menu.back"));
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
