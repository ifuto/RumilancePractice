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
import com.rumilance.practice.state.TeamColor;
import com.rumilance.practice.team.Team;
import com.rumilance.practice.team.TeamService;
import com.rumilance.practice.util.GuiSlots;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.List;

/**
 * Kit chooser for a team battle. Opened from the {@link TeamHubGui} "Start Battle" button —
 * clicking a kit immediately launches the RED-vs-BLUE match (no queue). Only the owner of a
 * split-ready team can start; anyone else gets bounced back to the hub.
 */
public final class TeamKitSelectGui extends AbstractGui {

    private final TeamService teamService;
    private final KitService kitService;
    private final com.rumilance.practice.locale.MessageService messageService;

    public TeamKitSelectGui(GuiSessionRegistry registry, SoundService sounds,
                            TeamService teamService, KitService kitService) {
        this(registry, sounds, teamService, kitService, null);
    }

    public TeamKitSelectGui(GuiSessionRegistry registry, SoundService sounds,
                            TeamService teamService, KitService kitService,
                            com.rumilance.practice.locale.MessageService messageService) {
        super(registry, sounds, GuiType.TEAM_KIT_SELECT, 6, true);
        this.teamService = teamService;
        this.kitService = kitService;
        this.messageService = messageService;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return Component.text("Party Battle — Pick a Kit", UiTheme.PRIMARY)
                .decoration(TextDecoration.ITALIC, false);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        MenuScaffold.chrome(inventory);
        MenuScaffold.header(inventory, 0, title(player, session));

        Team team = teamService.teamOf(player.getUniqueId()).orElse(null);
        if (team != null) {
            int red = team.side(TeamColor.RED).size();
            int blue = team.side(TeamColor.BLUE).size();
            inventory.setItem(GuiSlots.slot(5, 1),
                    ItemBuilder.of(Material.RED_WOOL, Math.max(1, red))
                            .name(Component.text("RED " + red + " vs BLUE " + blue, UiTheme.VALUE))
                            .lore(UiTheme.line("Uneven ratios are allowed."))
                            .action("decorate").build());
        }

        List<KitDefinition> kits = kitService.enabled();
        int index = 0;
        for (KitDefinition kit : kits) {
            if (index >= MenuScaffold.gridPageSize()) {
                break;
            }
            inventory.setItem(MenuScaffold.gridSlot(index++),
                    ItemBuilder.of(ItemBuilder.materialOr(kit.icon(), Material.DIAMOND_SWORD))
                            .nameMini(kit.prettyDisplayName())
                            .lore(UiTheme.divider(),
                                    UiTheme.labelValue("Arena", kit.hasFixedArena()
                                            ? com.rumilance.practice.util.KitNames.pretty(kit.arenaName()) : "Random"),
                                    UiTheme.blank(),
                                    UiTheme.hint("Click to start the team battle"))
                            .action("kit:" + kit.name())
                            .build());
        }

        inventory.setItem(GuiSlots.slot(5, 7),
                ItemBuilder.of(Material.ARROW)
                        .name(Component.text("Back to Team", UiTheme.WARNING))
                        .action("back").build());
        MenuScaffold.closeButton(inventory);
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        switch (action) {
            case "close" -> {
                sounds.play(player, "gui-back");
                player.closeInventory();
            }
            case "back" -> {
                sounds.play(player, "gui-back");
                player.closeInventory();
                player.performCommand("team");
            }
            default -> {
                if (action.startsWith("kit:")) {
                    String kitId = action.substring("kit:".length());
                    player.closeInventory();
                    TeamService.Result r = teamService.start(player, kitId);
                    sounds.play(player, r == TeamService.Result.OK ? "match-found" : "error");
                    if (r != TeamService.Result.OK) {
                        player.sendMessage(Component.text(startError(r), UiTheme.DANGER)
                                .decoration(TextDecoration.ITALIC, false));
                    }
                }
            }
        }
    }

    private static String startError(TeamService.Result r) {
        return switch (r) {
            case NOT_IN_TEAM -> "You are not in a team.";
            case NOT_OWNER -> "Only the team owner can start a battle.";
            case TOO_SMALL -> "Need at least 2 members.";
            case UNBALANCED -> "Everyone must be assigned to RED or BLUE first.";
            case KIT_NOT_FOUND -> "That kit is not available.";
            case NO_ARENA -> "No arena is free right now — try again shortly.";
            default -> "Could not start: " + r.name();
        };
    }
}
