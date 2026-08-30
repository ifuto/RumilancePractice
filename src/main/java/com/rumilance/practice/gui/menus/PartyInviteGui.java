package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.locale.MessageService;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.team.Team;
import com.rumilance.practice.team.TeamService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Skull list of online players for party invites. Click invites the target.
 */
public final class PartyInviteGui extends AbstractGui {

    private final TeamService teamService;
    private final MessageService messageService;
    private TeamHubGui teamHubGui;

    public PartyInviteGui(GuiSessionRegistry registry, SoundService sounds,
                          TeamService teamService, MessageService messageService) {
        super(registry, sounds, GuiType.PARTY_INVITE, 6, true);
        this.teamService = teamService;
        this.messageService = messageService;
    }

    public void setTeamHubGui(TeamHubGui teamHubGui) {
        this.teamHubGui = teamHubGui;
    }

    public void openFor(Player owner) {
        open(owner);
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return t(player, "party.invite-title").color(UiTheme.PRIMARY);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        MenuScaffold.chrome(inventory);
        Team team = teamService.teamOf(player.getUniqueId()).orElse(null);
        if (team == null || !team.isOwner(player.getUniqueId())) {
            inventory.setItem(MenuScaffold.gridSlot(13),
                    ItemBuilder.of(Material.BARRIER)
                            .name(t(player, "party.not-owner-short").color(UiTheme.DANGER))
                            .action("decorate")
                            .build());
            MenuScaffold.returnButton(inventory, t(player, "menu.back"));
            return;
        }

        List<Player> candidates = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }
            if (team.members().contains(online.getUniqueId())) {
                continue;
            }
            candidates.add(online);
        }
        candidates.sort(Comparator.comparing(p -> p.getName().toLowerCase()));

        int page = session.page();
        int pageSize = MenuScaffold.gridPageSize();
        int from = Math.min(page * pageSize, candidates.size());
        int to = Math.min(from + pageSize, candidates.size());
        int index = 0;
        for (int i = from; i < to; i++) {
            Player target = candidates.get(i);
            inventory.setItem(MenuScaffold.gridSlot(index++), skull(player, target));
        }
        if (candidates.isEmpty()) {
            inventory.setItem(MenuScaffold.gridSlot(13),
                    ItemBuilder.of(Material.BARRIER)
                            .name(t(player, "party.no-online").color(UiTheme.MUTED))
                            .action("decorate")
                            .build());
        }
        paintPaging(player, inventory, page, candidates.size());
        MenuScaffold.returnButton(inventory, t(player, "menu.back"));
    }

    private ItemStack skull(Player viewer, Player target) {
        return ItemBuilder.of(Material.PLAYER_HEAD)
                .name(Component.text(target.getName(), UiTheme.VALUE)
                        .decoration(TextDecoration.ITALIC, false))
                .skullOwner(target)
                .lore(
                        UiTheme.divider(),
                        UiTheme.hint(line(viewer, "party.invite-click"))
                )
                .action("invite:" + target.getUniqueId())
                .build();
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        if (action == null) {
            return;
        }
        if ("close".equals(action) || "back".equals(action)) {
            sounds.play(player, "gui-back");
            if (teamHubGui != null) {
                teamHubGui.open(player);
            } else {
                player.closeInventory();
            }
            return;
        }
        if ("page:prev".equals(action)) {
            session.setPage(Math.max(0, session.page() - 1));
            sounds.play(player, "gui-click");
            refresh(player, session, inventory);
            return;
        }
        if ("page:next".equals(action)) {
            session.setPage(session.page() + 1);
            sounds.play(player, "gui-click");
            refresh(player, session, inventory);
            return;
        }
        if (action.startsWith("invite:")) {
            UUID targetId;
            try {
                targetId = UUID.fromString(action.substring("invite:".length()));
            } catch (IllegalArgumentException e) {
                return;
            }
            Player target = Bukkit.getPlayer(targetId);
            if (target == null) {
                sounds.play(player, "error");
                return;
            }
            TeamService.Result r = teamService.invite(player, target.getName());
            if (r == TeamService.Result.OK) {
                sounds.play(player, "select");
            } else {
                sounds.play(player, "error");
                player.sendMessage(Component.text(teamService.errorMessage(player, r, target.getUniqueId()), UiTheme.DANGER)
                        .decoration(TextDecoration.ITALIC, false));
            }
            refresh(player, session, inventory);
        }
    }
}
