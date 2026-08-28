package com.rumilance.practice.gui.menus;

import com.rumilance.practice.arena.ArenaTemplateStore;
import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.kit.KitService;
import com.rumilance.practice.model.ArenaTemplate;
import com.rumilance.practice.model.KitDefinition;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.team.Team;
import com.rumilance.practice.team.TeamService;
import com.rumilance.practice.util.NameDisplay;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.List;

/**
 * Party Fight map picker — lists arenas flagged {@code party} with their icon blocks.
 */
public final class PartyMapSelectGui extends AbstractGui {

    private final TeamService teamService;
    private final ArenaTemplateStore arenaStore;
    private final KitService kitService;
    private TeamHubGui teamHubGui;

    public PartyMapSelectGui(GuiSessionRegistry registry, SoundService sounds,
                             TeamService teamService, ArenaTemplateStore arenaStore,
                             KitService kitService) {
        super(registry, sounds, GuiType.PARTY_MAP, 6, true);
        this.teamService = teamService;
        this.arenaStore = arenaStore;
        this.kitService = kitService;
    }

    public void setTeamHubGui(TeamHubGui teamHubGui) {
        this.teamHubGui = teamHubGui;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return Component.text("Party Map", UiTheme.PRIMARY).decoration(TextDecoration.ITALIC, false);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        MenuScaffold.chrome(inventory);
        Team team = teamService.teamOf(player.getUniqueId()).orElse(null);
        String current = team == null ? null : team.selectedArena();

        inventory.setItem(MenuScaffold.gridSlot(0), ItemBuilder.of(Material.ENDER_EYE)
                .name(Component.text("Random", UiTheme.PRIMARY))
                .lore(UiTheme.hint("Clear fixed party map"))
                .glint(current == null || current.isBlank())
                .action("map:random")
                .build());

        List<ArenaTemplate> maps = partyPool(session.get("kit_id", String.class));
        int index = 1;
        for (ArenaTemplate t : maps) {
            if (index >= MenuScaffold.gridPageSize() - 1) {
                break;
            }
            Material icon = Material.matchMaterial(t.iconMaterial() == null ? "" : t.iconMaterial());
            if (icon == null || icon.isAir()) {
                icon = Material.GRASS_BLOCK;
            }
            boolean selected = t.name().equals(current);
            inventory.setItem(MenuScaffold.gridSlot(index++), ItemBuilder.of(icon)
                    .name(Component.text(NameDisplay.pretty(t.name()), UiTheme.VALUE)
                            .decoration(TextDecoration.ITALIC, false))
                    .lore(
                            UiTheme.divider(),
                            UiTheme.labelValue("Id", t.name()),
                            selected
                                    ? UiTheme.status("SELECTED", UiTheme.SUCCESS)
                                    : UiTheme.hint("Click to select")
                    )
                    .glint(selected)
                    .action("map:" + t.name())
                    .build());
        }
        MenuScaffold.backButton(inventory);
        MenuScaffold.closeButton(inventory);
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        if ("close".equals(action)) {
            player.closeInventory();
            return;
        }
        if ("back".equals(action)) {
            if (teamHubGui != null) {
                teamHubGui.open(player);
            } else {
                player.closeInventory();
            }
            return;
        }
        if (action != null && action.startsWith("map:")) {
            String map = action.substring("map:".length());
            TeamService.Result r = teamService.setSelectedArena(player,
                    "random".equalsIgnoreCase(map) ? null : map);
            sounds.play(player, r == TeamService.Result.OK ? "gui-click" : "error");
            refresh(player, session, inventory);
        }
    }

    private List<ArenaTemplate> partyPool(String kitId) {
        List<String> kitPool = kitId == null ? List.of()
                : kitService.get(kitId).map(KitDefinition::partyArenas).orElse(List.of());
        List<ArenaTemplate> allParty = arenaStore.partyArenas();
        if (kitPool.isEmpty()) {
            return allParty;
        }
        return allParty.stream()
                .filter(t -> kitPool.contains(t.name().toLowerCase(java.util.Locale.ROOT)))
                .toList();
    }
}
